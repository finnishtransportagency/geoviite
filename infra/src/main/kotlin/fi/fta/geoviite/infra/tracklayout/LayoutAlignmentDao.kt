package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_ALIGNMENT
import java.sql.ResultSet
import java.util.stream.Collectors
import kotlin.math.abs
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

const val ALIGNMENT_CACHE_SIZE = 10000L
const val GEOMETRY_CACHE_SIZE = 500000L

data class MapSegmentProfileInfo<T>(
    val id: IntId<T>,
    val alignmentId: IndexedId<LayoutSegment>,
    val segmentStartM: Double,
    val segmentEndM: Double,
    val hasProfile: Boolean,
)

@Transactional(readOnly = true)
@Component
class LayoutAlignmentDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") val cacheEnabled: Boolean,
) : DaoBase(jdbcTemplateParam) {

    private val alignmentsCache: Cache<RowVersion<LayoutAlignment>, LayoutAlignment> =
        Caffeine.newBuilder().maximumSize(ALIGNMENT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val segmentGeometryCache: Cache<IntId<SegmentGeometry>, SegmentGeometry> =
        Caffeine.newBuilder().maximumSize(GEOMETRY_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    fun fetchVersions() = fetchRowVersions<LayoutAlignment>(LAYOUT_ALIGNMENT)

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun fetch(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment =
        if (cacheEnabled) alignmentsCache.get(alignmentVersion, ::fetchInternal) else fetchInternal(alignmentVersion)

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun fetchMany(versions: List<RowVersion<LayoutAlignment>>): Map<RowVersion<LayoutAlignment>, LayoutAlignment> =
        versions.associateWith(::fetch)

    private fun fetchInternal(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment {
        val sql =
            """
            select id, version
            from layout.alignment_version
            where id = :id 
              and version = :version
              and deleted = false
        """
                .trimIndent()
        val params = mapOf("id" to alignmentVersion.id.intValue, "version" to alignmentVersion.version)
        return getOne(
                alignmentVersion.id,
                jdbcTemplate.query(sql, params) { rs, _ ->
                    LayoutAlignment(
                        dataType = DataType.STORED,
                        id = rs.getIntId("id"),
                        segments = fetchSegments(alignmentVersion),
                    )
                },
            )
            .also { alignment -> logger.daoAccess(AccessType.FETCH, LayoutAlignment::class, alignment.id) }
    }

    fun preloadAlignmentCache(): Int {
        val sql =
            """
          select 
            a.id alignment_id,
            a.version alignment_version,
            sv.segment_index,
            sv.start,
            sv.geometry_alignment_id,
            sv.geometry_element_index,
            sv.source_start,
            sv.switch_id,
            sv.switch_start_joint_number,
            sv.switch_end_joint_number,
            sv.source,
            sv.geometry_id
          from layout.alignment a
            left join layout.segment_version sv on sv.alignment_id = a.id and sv.alignment_version = a.version
          order by a.id, sv.segment_index
        """
                .trimIndent()

        data class AlignmentData(val version: RowVersion<LayoutAlignment>)

        val alignmentAndSegment =
            jdbcTemplate.query(sql) { rs, _ ->
                val alignmentData = AlignmentData(version = rs.getRowVersion("alignment_id", "alignment_version"))
                val segmentId = rs.getIndexedIdOrNull<LayoutSegment>("alignment_id", "segment_index")
                val segment =
                    segmentId?.let { sId ->
                        SegmentData(
                            id = sId,
                            start = rs.getDouble("start"),
                            sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                            sourceStart = rs.getDoubleOrNull("source_start"),
                            switchId = rs.getIntIdOrNull("switch_id"),
                            startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                            endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                            source = rs.getEnum("source"),
                            rs.getIntId("geometry_id"),
                        )
                    }
                alignmentData to segment
            }
        val groupedByAlignment = alignmentAndSegment.groupBy({ (a, _) -> a }, { (_, s) -> s })
        val alignments =
            groupedByAlignment.entries
                .parallelStream()
                .map { (alignmentData, segmentDatas) ->
                    alignmentData.version to
                        LayoutAlignment(
                            id = alignmentData.version.id,
                            segments = createSegments(segmentDatas.filterNotNull()),
                        )
                }
                .collect(Collectors.toList())
                .associate { it }
        alignmentsCache.putAll(alignments)
        return alignments.size
    }

    @Transactional
    fun insert(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val sql =
            """
            insert into layout.alignment(
                bounding_box,
                segment_count,
                length
            ) 
            values (
                postgis.st_polygonfromtext(:polygon_string, 3067), 
                :segment_count,
                :length
            )
            returning id, version
        """
                .trimIndent()
        val params =
            mapOf(
                "polygon_string" to
                    alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
                "segment_count" to alignment.segments.size,
                "length" to alignment.length,
            )
        jdbcTemplate.setUser()
        val id: RowVersion<LayoutAlignment> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new Track Layout Alignment")
        upsertSegments(id, alignment.segments)
        logger.daoAccess(AccessType.INSERT, LayoutAlignment::class, id)
        return id
    }

    @Transactional
    fun update(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val alignmentId =
            if (alignment.id is IntId) alignment.id
            else throw IllegalArgumentException("Cannot update an alignment that isn't in DB already")
        val sql =
            """
            update layout.alignment 
            set 
                bounding_box = postgis.st_polygonfromtext(:polygon_string, 3067),
                segment_count = :segment_count,
                length = :length
            where id = :id
            returning id, version
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to alignmentId.intValue,
                "polygon_string" to
                    alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
                "segment_count" to alignment.segments.size,
                "length" to alignment.length,
            )
        jdbcTemplate.setUser()
        val result: RowVersion<LayoutAlignment> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to get new version for Track Layout Alignment")
        logger.daoAccess(AccessType.UPDATE, LayoutAlignment::class, result.id)
        upsertSegments(result, alignment.segments)
        return result
    }

    @Transactional
    fun delete(id: IntId<LayoutAlignment>): IntId<LayoutAlignment> {
        val sql = "delete from layout.alignment where id = :id returning id"
        val params = mapOf("id" to id.intValue)
        jdbcTemplate.setUser()
        val deletedRowId = getOne(id, jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutAlignment>("id") })
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedRowId)
        return deletedRowId
    }

    @Transactional
    fun deleteOrphanedAlignments(): List<IntId<LayoutAlignment>> {
        val sql =
            """
           delete
           from layout.alignment alignment
           where not exists(select 1 from layout.reference_line where reference_line.alignment_id = alignment.id)
             and not exists(select 1 from layout.location_track where location_track.alignment_id = alignment.id)
           returning alignment.id
       """
                .trimIndent()
        jdbcTemplate.setUser()
        val deletedAlignments =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ -> rs.getIntId<LayoutAlignment>("id") }
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedAlignments)
        return deletedAlignments
    }

    private fun fetchSegments(alignmentVersion: RowVersion<LayoutAlignment>): List<LayoutSegment> {
        val sql =
            """
            select 
              alignment_id,
              segment_index,
              start,
              geometry_alignment_id,
              geometry_element_index,
              source_start,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number,
              source,
              geometry_id
            from layout.segment_version 
            where alignment_id = :alignment_id
              and alignment_version = :alignment_version
            order by alignment_id, segment_index
        """
                .trimIndent()
        val params =
            mapOf("alignment_id" to alignmentVersion.id.intValue, "alignment_version" to alignmentVersion.version)

        return createSegments(
            jdbcTemplate.query(sql, params) { rs, _ ->
                SegmentData(
                    id = rs.getIndexedId("alignment_id", "segment_index"),
                    start = rs.getDouble("start"),
                    sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                    sourceStart = rs.getDoubleOrNull("source_start"),
                    switchId = rs.getIntIdOrNull("switch_id"),
                    startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                    endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                    source = rs.getEnum("source"),
                    geometryId = rs.getIntId("geometry_id"),
                )
            }
        )
    }

    private fun createSegments(segmentResults: List<SegmentData>): List<LayoutSegment> {
        val geometries = fetchSegmentGeometries(segmentResults.map { s -> s.geometryId }.distinct())

        var start = 0.0
        return segmentResults.map { data ->
            require(abs(start - data.start) < LAYOUT_M_DELTA) {
                "Segment start value does not match the calculated one: stored=${data.start} calc=$start"
            }
            val geometry =
                requireNotNull(geometries[data.geometryId]) {
                    "Fetching geometry failed for segment: id=${data.id} geometryId=$data.geometryId"
                }
            LayoutSegment(
                    id = data.id,
                    sourceId = data.sourceId,
                    sourceStart = data.sourceStart,
                    switchId = data.switchId,
                    startJointNumber = data.startJointNumber,
                    endJointNumber = data.endJointNumber,
                    source = data.source,
                    geometry = geometry,
                    startM = start,
                )
                .also { start += geometry.length }
        }
    }

    fun fetchSegmentGeometriesAndPlanMetadata(
        alignmentVersion: RowVersion<LayoutAlignment>,
        metadataExternalId: Oid<*>?,
        boundingBox: BoundingBox?,
    ): List<SegmentGeometryAndMetadata> {
        // language=SQL
        val sql =
            """
            with
              segment_range as (
                select
                  alignment_id,
                  alignment_version,
                  min(segment_index) as min_index,
                  max(segment_index) as max_index
                from layout.segment_version
                  inner join layout.segment_geometry on segment_geometry.id = segment_version.geometry_id
                where alignment_id = :alignment_id
                  and alignment_version = :alignment_version
                  and (
                      :use_bounding_box = false or postgis.st_intersects(
                        postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                        geometry
                      )
                  )
                group by alignment_id, alignment_version
              ),
              orig_metadata_plan as materialized (
                select
                  plan.id as plan_id,
                  plan_file.name as file_name
                from geometry.plan
                  inner join geometry.plan_file on plan.id = plan_file.plan_id
                  -- Ensure that the plan is from initial imports
                  inner join geometry.plan_version init_version 
                          on init_version.id = plan.id and init_version.version = 1 and init_version.change_user = 'IM_IMPORT'
                where plan.source = 'PAIKANNUSPALVELU'
              ),
              orig_metadata as (
                select
                  current_segment.alignment_id as current_alignment_id,
                  current_segment.alignment_version as current_alignment_version,
                  current_segment.segment_index as current_segment_index,
                  concat(metadata.plan_file_name, '.xml') as plan_file_name,
                  metadata.plan_alignment_name,
                  plan.plan_id

                from layout.initial_import_metadata metadata
                  inner join layout.initial_segment_metadata segment_metadata on
                    metadata.id = segment_metadata.metadata_id
                  inner join layout.segment_version segment on
                    segment.alignment_id = segment_metadata.alignment_id
                    and segment.alignment_version = 1
                    and segment.segment_index = segment_metadata.segment_index
                    and segment.source = 'IMPORTED'
                  inner join layout.segment_version current_segment on
                    current_segment.geometry_id = segment.geometry_id
                    and current_segment.alignment_id = :alignment_id
                    and current_segment.alignment_version = :alignment_version
                    and current_segment.geometry_alignment_id is null
                  left join layout.segment_geometry on segment.geometry_id = segment_geometry.id
                  left join orig_metadata_plan plan on plan.file_name = concat(metadata.plan_file_name, '.xml')

                where metadata.alignment_external_id = :external_id
              ),
              segments as (
                select
                  segment.alignment_id,
                  segment.alignment_version,
                  segment.segment_index,
                  segment.geometry_id,
                  segment.source,
                  segment.start,
                  geom_alignment.id as geom_alignment_id,
                  geom_alignment.id is not null as is_linked,
                  coalesce(plan_file.plan_id, orig_metadata.plan_id) as plan_id,
                  coalesce(plan_file.name, orig_metadata.plan_file_name) as file_name,
                  coalesce(geom_alignment.name, orig_metadata.plan_alignment_name) as alignment_name,
                  row_number() over (order by segment.segment_index) - row_number() over (
                    partition by
                      geom_alignment.id is not null,
                      coalesce(plan_file.plan_id, orig_metadata.plan_id),
                      coalesce(plan_file.name, orig_metadata.plan_file_name),
                      coalesce(geom_alignment.name, orig_metadata.plan_alignment_name)
                    order by segment.segment_index
                  ) as grp
                from layout.segment_version segment
                  left join geometry.alignment geom_alignment on segment.geometry_alignment_id = geom_alignment.id
                  left join geometry.plan_file on plan_file.plan_id = geom_alignment.plan_id
                  left join orig_metadata on
                    orig_metadata.current_alignment_id = segment.alignment_id
                    and orig_metadata.current_alignment_version = segment.alignment_version
                    and orig_metadata.current_segment_index = segment.segment_index
                where segment.alignment_id = :alignment_id
                  and segment.alignment_version = :alignment_version
              ),
              metadata_segments as (
                select
                  alignment_id,
                  alignment_version,
                  min(segment_index) as from_segment,
                  max(segment_index) as to_segment,
                  common.first(geometry_id order by segment_index) as from_geom_id,
                  common.last(geometry_id order by segment_index) as to_geom_id,
                  is_linked,
                  plan_id,
                  file_name,
                  geom_alignment_id,
                  alignment_name,
                  min(start) as start
                from segments
                group by alignment_id, alignment_version, is_linked, grp, plan_id, file_name, geom_alignment_id, alignment_name
              )
            select
              segment.*,
              postgis.st_x(postgis.st_startpoint(start_geom.geometry)) as start_x,
              postgis.st_y(postgis.st_startpoint(start_geom.geometry)) as start_y,
              postgis.st_m(postgis.st_startpoint(start_geom.geometry)) + segment.start as start_m,
              postgis.st_x(postgis.st_endpoint(end_geom.geometry)) as end_x,
              postgis.st_y(postgis.st_endpoint(end_geom.geometry)) as end_y,
              postgis.st_m(postgis.st_endpoint(end_geom.geometry)) + segment.start as end_m
            from segment_range range
              inner join metadata_segments segment on
                  range.alignment_id = segment.alignment_id and range.alignment_version = segment.alignment_version
              left join layout.segment_geometry start_geom on start_geom.id = from_geom_id
              left join layout.segment_geometry end_geom on end_geom.id = to_geom_id
            where range.max_index >= segment.from_segment
              and range.min_index <= segment.to_segment
            order by segment.from_segment, segment.to_segment
        """
                .trimIndent()
        val params =
            mapOf(
                "alignment_id" to alignmentVersion.id.intValue,
                "alignment_version" to alignmentVersion.version,
                "external_id" to metadataExternalId,
                "use_bounding_box" to (boundingBox != null),
                "x_min" to boundingBox?.min?.x,
                "y_min" to boundingBox?.min?.y,
                "x_max" to boundingBox?.max?.x,
                "y_max" to boundingBox?.max?.y,
                "layout_srid" to LAYOUT_SRID.code,
            )
        val result =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val fromSegment = rs.getInt("from_segment")
                val toSegment = rs.getInt("to_segment")
                val startPoint =
                    rs.getDoubleOrNull("start_m")?.let { m ->
                        rs.getPointOrNull("start_x", "start_y")?.let { point ->
                            AlignmentPoint(point.x, point.y, 0.0, m, 0.0)
                        }
                    }
                val endPoint =
                    rs.getDoubleOrNull("end_m")?.let { m ->
                        rs.getPointOrNull("end_x", "end_y")?.let { point ->
                            AlignmentPoint(point.x, point.y, 0.0, m, 0.0)
                        }
                    }
                SegmentGeometryAndMetadata(
                    planId = rs.getIntIdOrNull("plan_id"),
                    fileName = rs.getFileNameOrNull("file_name"),
                    alignmentId = rs.getIntIdOrNull("geom_alignment_id"),
                    alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                    startPoint = startPoint,
                    endPoint = endPoint,
                    isLinked = rs.getBoolean("is_linked"),
                    id = StringId("${alignmentVersion.id.intValue}_${fromSegment}_${toSegment}"),
                )
            }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, alignmentVersion)
        return result
    }

    fun fetchMetadata(alignmentVersion: RowVersion<LayoutAlignment>): List<LayoutSegmentMetadata> {
        // language=SQL
        val sql =
            """
            select
              postgis.st_x(postgis.st_startpoint(segment_geometry.geometry)) as start_point_x,
              postgis.st_y(postgis.st_startpoint(segment_geometry.geometry)) as start_point_y,
              postgis.st_x(postgis.st_endpoint(segment_geometry.geometry)) as end_point_x,
              postgis.st_y(postgis.st_endpoint(segment_geometry.geometry)) as end_point_y,
              alignment.name as alignment_name,
              plan.plan_time,
              plan.measurement_method,
              plan.srid,
              plan_file.name as file_name
            from layout.segment_version
              inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
              left join geometry.alignment on alignment.id = segment_version.geometry_alignment_id
              left join geometry.plan on alignment.plan_id = plan.id
              left join geometry.plan_file on plan_file.plan_id = plan.id
            where segment_version.alignment_id = :alignment_id 
              and segment_version.alignment_version = :alignment_version
            order by alignment.id, segment_version.segment_index
        """
                .trimIndent()

        val params =
            mapOf("alignment_id" to alignmentVersion.id.intValue, "alignment_version" to alignmentVersion.version)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutSegmentMetadata(
                startPoint = rs.getPoint("start_point_x", "start_point_y"),
                endPoint = rs.getPoint("end_point_x", "end_point_y"),
                alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                planTime = rs.getInstantOrNull("plan_time"),
                measurementMethod = rs.getEnumOrNull<MeasurementMethod>("measurement_method"),
                fileName = rs.getString("file_name")?.let(::FileName),
                originalSrid = rs.getSridOrNull("srid"),
            )
        }
    }

    fun <T> fetchProfileInfoForSegmentsInBoundingBox(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        hasProfileInfo: Boolean? = null,
    ): List<MapSegmentProfileInfo<T>> {
        // language=SQL
        val sql =
            """
            with applicable_alignment_segments as (
              select segment_version.alignment_id,
                     segment_version.alignment_version,
                     segment_version.segment_index,
                     segment_version.start,
                     postgis.st_m(postgis.st_endpoint(segment_geometry.geometry)) as max_m,
                     (plan.vertical_coordinate_system is not null)
                       and exists(select *
                                  from geometry.vertical_intersection vi
                                  where vi.alignment_id = alignment.id) as has_profile_info
                from layout.alignment layout_alignment
                  inner join layout.segment_version on
                      layout_alignment.id = segment_version.alignment_id and
                      layout_alignment.version = segment_version.alignment_version
                  inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                  left join geometry.alignment on alignment.id = segment_version.geometry_alignment_id
                  left join geometry.plan on alignment.plan_id = plan.id
                where postgis.st_intersects(
                    postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                    layout_alignment.bounding_box
                  )
                  and postgis.st_intersects(
                    postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                    segment_geometry.bounding_box
                  )
            )
            select official_id,
                   alignment_id,
                   unnest(segment_indices) as segment_index,
                   unnest(starts) as start,
                   unnest(max_ms) as max_m,
                   unnest(has_profile_infos) as has_profile_info
              from (
                select official_id,
                       alignment_id,
                       array_agg(segment_index) as segment_indices,
                       array_agg(start) as starts,
                       array_agg(max_m) as max_ms,
                       array_agg(has_profile_info) as has_profile_infos
                  from applicable_alignment_segments
                    join (
                    select *
                      from layout.location_track, layout.location_track_is_in_layout_context(
                          :publication_state::layout.publication_state,
                          :design_id, location_track)
                      where location_track.state != 'DELETED'
                  ) location_track using (alignment_id, alignment_version)
                  where ((:has_profile_info::boolean is null) or :has_profile_info = has_profile_info)
                  group by official_id, alignment_id, alignment_version
              ) grouped
              order by official_id, segment_index;
        """
                .trimIndent()

        val params =
            mapOf(
                "x_min" to bbox.min.x,
                "y_min" to bbox.min.y,
                "x_max" to bbox.max.x,
                "y_max" to bbox.max.y,
                "layout_srid" to LAYOUT_SRID.code,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "has_profile_info" to hasProfileInfo,
            )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val startM = rs.getDouble("start")
            MapSegmentProfileInfo(
                id = rs.getIntId("official_id"),
                alignmentId = rs.getIndexedId("alignment_id", "segment_index"),
                segmentStartM = startM,
                segmentEndM = startM + rs.getDouble("max_m"),
                hasProfile = rs.getBoolean("has_profile_info"),
            )
        }
    }

    private fun upsertSegments(alignmentId: RowVersion<LayoutAlignment>, segments: List<LayoutSegment>) {
        if (segments.isNotEmpty()) {
            val newGeometryIds =
                insertSegmentGeometries(
                    segments.mapNotNull { s -> if (s.geometry.id is StringId) s.geometry else null }
                )
            // language=SQL
            val sqlIndexed =
                """
                insert into layout.segment_version(
                  alignment_id,
                  alignment_version,
                  segment_index,
                  start,
                  geometry_alignment_id,
                  geometry_element_index,
                  switch_id,
                  switch_start_joint_number,
                  switch_end_joint_number,
                  source_start,
                  source,
                  geometry_id
                )
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::layout.geometry_source, ?) 
              """
                    .trimIndent()
            // This uses indexed parameters (rather than named ones),
            // since named parameter template's batch-method is considerably slower
            jdbcTemplate.batchUpdateIndexed(sqlIndexed, segments) { ps, (index, s) ->
                ps.setInt(1, alignmentId.id.intValue)
                ps.setInt(2, alignmentId.version)
                ps.setInt(3, index)
                ps.setDouble(4, s.startM)
                ps.setNullableInt(5) { if (s.sourceId is IndexedId) s.sourceId.parentId else null }
                ps.setNullableInt(6) { if (s.sourceId is IndexedId) s.sourceId.index else null }
                ps.setNullableInt(7) { if (s.switchId is IntId) s.switchId.intValue else null }
                ps.setNullableInt(8, s.startJointNumber?.intValue)
                ps.setNullableInt(9, s.endJointNumber?.intValue)
                ps.setNullableDouble(10, s.sourceStart)
                ps.setString(11, s.source.name)
                val geometryId =
                    if (s.geometry.id is IntId) s.geometry.id
                    else requireNotNull(newGeometryIds[s.geometry.id]) { "SegmentGeometry not stored: id=${s.id}" }
                ps.setInt(12, geometryId.intValue)
            }
        }
    }

    // Batching this is a little tricky due to difficulty in mapping generated ids:
    //  There is no guarantee of result set order (though it's usually the insert order)
    private fun insertSegmentGeometries(
        geometries: List<SegmentGeometry>
    ): Map<StringId<SegmentGeometry>, IntId<SegmentGeometry>> {
        require(geometries.all { geom -> geom.segmentStart.m == 0.0 }) {
            "Geometries in DB must be set to startM=0.0, so they remain valid if an earlier segment changes"
        }
        require(
            geometries.all { geom ->
                val calculatedLength = calculateDistance(geom.segmentPoints, LAYOUT_SRID)
                val maxDelta = calculatedLength * 0.01
                abs(calculatedLength - geom.segmentEnd.m) <= maxDelta
            }
        ) {
            "Geometries in DB should have (approximately) endM=length"
        }
        // language=SQL
        val sql =
            """
          insert into layout.segment_geometry(
            resolution,
            geometry,
            height_values,
            cant_values
          )
          values(
            :resolution,
            postgis.st_setsrid(:line_string::postgis.geometry, :srid),
            string_to_array(:height_values, ',', 'null')::decimal[],
            string_to_array(:cant_values, ',', 'null')::decimal[]
          )
          on conflict (hash) do update
          set resolution = segment_geometry.resolution -- no-op update so that returns clause works on conflict as well
          returning id
        """
                .trimIndent()
        return geometries.associate { geometry ->
            val params =
                mapOf(
                    "resolution" to geometry.resolution,
                    "line_string" to create3DMLineString(geometry.segmentPoints),
                    "srid" to LAYOUT_SRID.code,
                    "height_values" to createListString(geometry.segmentPoints) { p -> p.z },
                    "cant_values" to createListString(geometry.segmentPoints) { p -> p.cant },
                )
            jdbcTemplate
                .query(sql, params) { rs, _ -> geometry.id as StringId to rs.getIntId<SegmentGeometry>("id") }
                .first()
        }
    }

    private fun fetchSegmentGeometries(
        ids: List<IntId<SegmentGeometry>>
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return segmentGeometryCache.getAll(ids) { fetchIds -> fetchSegmentGeometriesInternal(fetchIds) }
    }

    private fun fetchSegmentGeometriesInternal(
        ids: Set<IntId<SegmentGeometry>>
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return if (ids.isNotEmpty()) {
            val sql =
                """
                  select 
                    id,
                    postgis.st_astext(geometry) as geometry_wkt,
                    resolution,
                    case 
                      when height_values is null then null 
                      else array_to_string(height_values, ',', 'null') 
                    end as height_values,
                    case 
                      when cant_values is null then null 
                      else array_to_string(cant_values, ',', 'null') 
                    end as cant_values
                  from layout.segment_geometry
                  where id in (:ids)
                """
                    .trimIndent()
            val params = mapOf("ids" to ids.map(IntId<SegmentGeometry>::intValue))
            val rowResults =
                jdbcTemplate.query(sql, params) { rs, _ ->
                    GeometryRowResult(
                        id = rs.getIntId("id"),
                        wktString = rs.getString("geometry_wkt"),
                        heightString = rs.getString("height_values"),
                        cantString = rs.getString("cant_values"),
                        resolution = rs.getInt("resolution"),
                    )
                }
            parseGeometries(rowResults)
        } else mapOf()
    }

    fun preloadSegmentGeometries(): Int {
        val sql =
            """
          select 
            geom.id,
            postgis.st_astext(geom.geometry) as geometry_wkt,
            geom.resolution,
            case 
              when geom.height_values is null then null 
              else array_to_string(geom.height_values, ',', 'null') 
            end as height_values,
            case 
              when geom.cant_values is null then null 
              else array_to_string(geom.cant_values, ',', 'null') 
            end as cant_values
          from layout.alignment a
            left join layout.segment_version sv on a.id = sv.alignment_id and a.version = sv.alignment_version
            left join layout.segment_geometry geom on geom.id = sv.geometry_id
          where geom.id is not null
          group by geom.id
        """
                .trimIndent()

        val rowResults =
            jdbcTemplate.query(sql) { rs, _ ->
                GeometryRowResult(
                    id = rs.getIntId("id"),
                    wktString = rs.getString("geometry_wkt"),
                    heightString = rs.getString("height_values"),
                    cantString = rs.getString("cant_values"),
                    resolution = rs.getInt("resolution"),
                )
            }
        segmentGeometryCache.putAll(parseGeometries(rowResults))
        return rowResults.size
    }
}

data class GeometryRowResult(
    val id: IntId<SegmentGeometry>,
    val wktString: String,
    val cantString: String?,
    val heightString: String?,
    val resolution: Int,
)

private fun parseGeometries(rowResults: List<GeometryRowResult>): Map<IntId<SegmentGeometry>, SegmentGeometry> =
    rowResults
        .parallelStream()
        .map { row ->
            SegmentGeometry(
                id = row.id,
                segmentPoints = parseSegmentPointsWkt(row.wktString, row.heightString, row.cantString),
                resolution = row.resolution,
            )
        }
        .collect(Collectors.toMap({ g -> g.id as IntId }, { it }))

private fun getSegmentPointsWkt(
    rs: ResultSet,
    geometryColumn: String,
    heightColumn: String? = null,
    cantColumn: String? = null,
): List<SegmentPoint> =
    rs.getString(geometryColumn)?.let { wktString ->
        parseSegmentPointsWkt(wktString, heightColumn?.let(rs::getString), cantColumn?.let(rs::getString))
    } ?: emptyList()

private fun parseSegmentPointsWkt(
    geometryWkt: String,
    heightString: String? = null,
    cantString: String? = null,
): List<SegmentPoint> {
    val heightValues = parseNullableDoubleList(heightString)
    val cantValues = parseNullableDoubleList(cantString)
    return parseSegmentPointLineString(geometryWkt, heightValues, cantValues)
}

fun parseSegmentPointLineString(
    lineString: String,
    heights: List<Double?>?,
    cants: List<Double?>?,
): List<SegmentPoint> =
    get3DMLineStringContent(lineString).let { pointStrings ->
        require(heights == null || heights.size == pointStrings.size) {
            "Height value count should match point count: points=${pointStrings.size} heights=${heights?.size}"
        }
        require(cants == null || cants.size == pointStrings.size) {
            "Cant value count should match point count: points=${pointStrings.size} cants=${cants?.size}"
        }
        pointStrings.mapIndexed { i, p -> parseSegmentPoint(p, heights?.get(i), cants?.get(i)) }
    }

private fun parseNullableDoubleList(listString: String?): List<Double?>? =
    listString?.split(",")?.map(String::toDoubleOrNull)

private data class SegmentData(
    val id: IndexedId<LayoutSegment>,
    val start: Double,
    val sourceId: IndexedId<GeometryElement>?,
    val sourceStart: Double?,
    val switchId: IntId<TrackLayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    val source: GeometrySource,
    val geometryId: IntId<SegmentGeometry>,
)
