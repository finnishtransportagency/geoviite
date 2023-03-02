package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_ALIGNMENT
import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.geography.create3DMLineString
import fi.fta.geoviite.infra.geography.parse3DMLineString
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_ALIGNMENT
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val GEOMETRY_CACHE_SIZE = 500000L

@Transactional(readOnly = true)
@Component
class LayoutAlignmentDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    private val segmentGeometryCache: Cache<IntId<SegmentGeometry>, SegmentGeometry> = Caffeine.newBuilder()
        .maximumSize(GEOMETRY_CACHE_SIZE)
        .build()

    fun fetchVersions() = fetchRowVersions<LayoutAlignment>(LAYOUT_ALIGNMENT)

    @Cacheable(CACHE_LAYOUT_ALIGNMENT, sync = true)
    fun fetch(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment {
        val sql = """
            select id, geometry_alignment_id
            from layout.alignment_version
            where id = :id 
              and version = :version
              and deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to alignmentVersion.id.intValue,
            "version" to alignmentVersion.version,
        )
        val alignment = getOne(alignmentVersion.id, jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutAlignment(
                dataType = DataType.STORED,
                id = rs.getIntId("id"),
                sourceId = rs.getIntIdOrNull("geometry_alignment_id"),
                segments = fetchSegments(alignmentVersion),
            )
        })
        logger.daoAccess(AccessType.FETCH, LayoutAlignment::class, alignment.id)
        return alignment
    }

    @Transactional
    fun insert(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val sql = """
            insert into layout.alignment(
                geometry_alignment_id,
                bounding_box,
                segment_count,
                length
            ) 
            values (
                :geometry_alignment_id,
                postgis.st_polygonfromtext(:polygon_string, 3067), 
                :segment_count,
                :length
            )
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "geometry_alignment_id" to if (alignment.sourceId is IntId) alignment.sourceId.intValue else null,
            "polygon_string" to alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
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
            if (alignment.id is IntId) alignment.id else throw IllegalArgumentException("Cannot update an alignment that isn't in DB already")
        val sql = """
            update layout.alignment 
            set 
                geometry_alignment_id = :geometry_alignment_id,
                bounding_box = postgis.st_polygonfromtext(:polygon_string, 3067),
                segment_count = :segment_count,
                length = :length
            where id = :id
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "id" to alignmentId.intValue,
            "geometry_alignment_id" to if (alignment.sourceId is IntId) alignment.sourceId.intValue else null,
            "polygon_string" to alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
            "segment_count" to alignment.segments.size,
            "length" to alignment.length,
        )
        jdbcTemplate.setUser()
        val result: RowVersion<LayoutAlignment> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get new version for Track Layout Alignment")
        logger.daoAccess(AccessType.UPDATE, LayoutAlignment::class, result.id)
        upsertSegments(result, alignment.segments)
        return result
    }

    @Transactional
    fun delete(id: IntId<LayoutAlignment>): IntId<LayoutAlignment> {
        val sql = "delete from layout.alignment where id = :id returning id"
        val params = mapOf("id" to id.intValue)
        jdbcTemplate.setUser()
        val deletedRowId = getOne(id, jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getIntId<LayoutAlignment>("id")
        })
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedRowId)
        return deletedRowId
    }

    @Transactional
    fun deleteOrphanedAlignments(): List<IntId<LayoutAlignment>> {
        val sql = """
           delete
           from layout.alignment alignment
           where not exists(select 1 from layout.reference_line where reference_line.alignment_id = alignment.id)
             and not exists(select 1 from layout.location_track where location_track.alignment_id = alignment.id)
           returning alignment.id
       """.trimIndent()
        jdbcTemplate.setUser()
        val deletedAlignments = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            rs.getIntId<LayoutAlignment>("id")
        }
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedAlignments)
        return deletedAlignments
    }

    private fun fetchSegments(alignmentVersion: RowVersion<LayoutAlignment>): List<LayoutSegment> {
        val sql = """
            select 
              alignment_id,
              segment_index,
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
        """.trimIndent()
        val params = mapOf(
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
        )

        val segmentResults = jdbcTemplate.query(sql, params) { rs, _ -> SegmentData(
            id = rs.getIndexedId("alignment_id", "segment_index"),
            sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
            sourceStart = rs.getDoubleOrNull("source_start"),
            switchId = rs.getIntIdOrNull("switch_id"),
            startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
            endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
            source = rs.getEnum("source"),
        ) to rs.getIntId<SegmentGeometry>("geometry_id") }

        val geometries = fetchSegmentGeometries(segmentResults.map { (_, geometryId) -> geometryId }.distinct())

        var start = 0.0
        return segmentResults.map { (data, geometryId) ->
            val segment = LayoutSegment(
                id = data.id,
                sourceId = data.sourceId,
                sourceStart = data.sourceStart,
                switchId = data.switchId,
                startJointNumber = data.startJointNumber,
                endJointNumber = data.endJointNumber,
                start = start,
                source = data.source,
                geometry = requireNotNull(geometries[geometryId]) {
                    "Fetching geometry failed for segment: id=${data.id} geometryId=$geometryId"
                },
            )
            start += segment.length
            segment
        }
    }

    fun fetchSegmentGeometriesAndPlanMetadata(
        alignmentId: IntId<LayoutAlignment>,
        boundingBox: BoundingBox?
    ): List<SegmentGeometryAndMetadata> {
        val sql = """
            with 
              segment_range as (
                select
                  alignment.id,
                  alignment.version,
                  min(segment_index) min_index,
                  max(segment_index) max_index
                from layout.alignment
                  inner join layout.segment_version on alignment.id = segment_version.alignment_id
                    and alignment.version = segment_version.alignment_version
                  inner join layout.segment_geometry on segment_geometry.id = segment_version.geometry_id
                where alignment.id = :id
                  and (
                    :use_bounding_box = false or postgis.st_intersects(
                      postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                      segment_geometry.geometry
                    )
                  ) 
                group by alignment.id
              ),
              segment_points as (
                select
                  segment_version.alignment_id,
                  segment_version.segment_index,
                  segment_version.source,
                  segment_version.geometry_alignment_id,
                  postgis.st_startpoint(
                    case 
                      when :use_bounding_box = true and segment_version.segment_index = min_index
                        then postgis.st_intersection(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid), segment_geometry.geometry)
                      else segment_geometry.geometry
                    end
                  ) as start,
                  postgis.st_endpoint(
                    case 
                      when :use_bounding_box = true and segment_index = max_index
                        then postgis.st_intersection(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid), segment_geometry.geometry)
                      else segment_geometry.geometry
                    end
                  ) as end
              from segment_range
                inner join layout.segment_version on segment_range.id = segment_version.alignment_id
                  and segment_range.version = segment_version.alignment_version
                  and segment_version.segment_index between segment_range.min_index and segment_range.max_index
                inner join layout.segment_geometry on segment_geometry.id = segment_version.geometry_id
              )
            select 
              segment_points.alignment_id,
              segment_points.segment_index,
              plan.id as plan_id,
              plan_file.name as filename,
              layout.initial_import_metadata.plan_file_name,
              segment_points.source,
              postgis.st_x(segment_points.start) as start_x,
              postgis.st_y(segment_points.start) as start_y,
              postgis.st_x(segment_points.end) as end_x,
              postgis.st_y(segment_points.end) as end_y
              from segment_points
                left join geometry.alignment on segment_points.geometry_alignment_id = geometry.alignment.id
                left join geometry.plan on geometry.alignment.plan_id = plan.id
                left join geometry.plan_file on geometry.plan.id = geometry.plan_file.plan_id
                left join layout.initial_segment_metadata 
                  on segment_points.alignment_id = initial_segment_metadata.alignment_id
                    and segment_points.segment_index = initial_segment_metadata.segment_index
                left join layout.initial_import_metadata 
                  on initial_segment_metadata.metadata_id = initial_import_metadata.id
              order by segment_points.segment_index
        """.trimIndent()
        val params = mapOf(
            "id" to alignmentId.intValue,
            "use_bounding_box" to (boundingBox != null),
            "x_min" to boundingBox?.min?.x,
            "y_min" to boundingBox?.min?.y,
            "x_max" to boundingBox?.max?.x,
            "y_max" to boundingBox?.max?.y,
            "layout_srid" to LAYOUT_SRID.code,
        )
        val result = jdbcTemplate.query(sql, params) { rs, _ ->
            SegmentGeometryAndMetadata(
                planId = rs.getIntIdOrNull("plan_id"),
                fileName = rs.getFileNameOrNull("filename") ?: rs.getFileNameOrNull("plan_file_name"),
                startPoint = rs.getPointOrNull("start_x", "start_y"),
                endPoint = rs.getPointOrNull("end_x", "end_y"),
                source = rs.getEnumOrNull<GeometrySource>("source"),
                segmentId = rs.getIndexedId("alignment_id","segment_index")
            )
        }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, alignmentId)
        return result
    }

    fun fetchMetadata(alignmentVersion: RowVersion<LayoutAlignment>): List<LayoutSegmentMetadata> {
        //language=SQL
        val sql = """
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
        """.trimIndent()

        val params = mapOf(
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutSegmentMetadata(
                startPoint = rs.getPoint("start_point_x", "start_point_y"),
                endPoint = rs.getPoint("end_point_x", "end_point_y"),
                alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                planTime = rs.getInstantOrNull("plan_time"),
                measurementMethod = rs.getEnumOrNull<MeasurementMethod>("measurement_method"),
                fileName = rs.getString("file_name")?.let(::FileName),
                originalSrid = rs.getSridOrNull("srid")
            )
        }
    }

    private fun upsertSegments(alignmentId: RowVersion<LayoutAlignment>, segments: List<LayoutSegment>) {
        if (segments.isNotEmpty()) {
            val newGeometryIds = insertSegmentGeometries(segments.mapNotNull { s ->
                if (s.geometry.id is StringId) s.geometry else null
            })
            //language=SQL
            val sqlIndexed = """
                insert into layout.segment_version(
                  alignment_id,
                  alignment_version,
                  segment_index,
                  geometry_alignment_id,
                  geometry_element_index,
                  switch_id,
                  switch_start_joint_number,
                  switch_end_joint_number,
                  source_start,
                  source,
                  geometry_id
                )
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?::layout.geometry_source, ?) 
              """.trimIndent()
            // This uses indexed parameters (rather than named ones),
            // since named parameter template's batch-method is considerably slower
            jdbcTemplate.batchUpdateIndexed(sqlIndexed, segments) { ps, (index, s) ->
                ps.setInt(1, alignmentId.id.intValue)
                ps.setInt(2, alignmentId.version)
                ps.setInt(3, index)
                ps.setNullableInt(4) { if (s.sourceId is IndexedId) s.sourceId.parentId else null }
                ps.setNullableInt(5) { if (s.sourceId is IndexedId) s.sourceId.index else null }
                ps.setNullableInt(6) { if (s.switchId is IntId) s.switchId.intValue else null }
                ps.setNullableInt(7, s.startJointNumber?.intValue)
                ps.setNullableInt(8, s.endJointNumber?.intValue)
                ps.setNullableDouble(9, s.sourceStart)
                ps.setString(10, s.source.name)
                val geometryId =
                    if (s.geometry.id is IntId) s.geometry.id
                    else requireNotNull(newGeometryIds[s.geometry.id]) { "SegmentGeometry not stored: id=${s.id}" }
                ps.setInt(11, geometryId.intValue)
            }
        }
    }

    // TODO: GVT-1691 batching this is a little tricky due to difficulty in mapping generated ids:
    //  There is no guarantee of result set order (though it's usually the insert order)
    //  If we could calculate the hash prior to saving we could use that to identify the mapping
    private fun insertSegmentGeometries(
        geometries: List<SegmentGeometry>,
    ): Map<StringId<SegmentGeometry>, IntId<SegmentGeometry>> {
        //language=SQL
        val sql = """
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
        """.trimIndent()
        return geometries.associate { geometry ->
            val params = mapOf(
                "resolution" to geometry.resolution,
                "line_string" to create3DMLineString(geometry.points),
                "srid" to LAYOUT_SRID.code,
                "height_values" to createListString(geometry.points) { p -> p.z },
                "cant_values" to createListString(geometry.points) { p -> p.cant },
            )
            jdbcTemplate.query(sql, params) { rs, _ ->
                geometry.id as StringId to rs.getIntId<SegmentGeometry>("id")
            }.first()
        }
    }

    private fun fetchSegmentGeometries(
        ids: List<IntId<SegmentGeometry>>,
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return segmentGeometryCache.getAll(ids) { fetchIds -> fetchSegmentGeometriesInternal(fetchIds) }
    }

    private fun fetchSegmentGeometriesInternal(
        ids: Set<IntId<SegmentGeometry>>,
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return if (ids.isNotEmpty()) {
            val sql = """
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
                """.trimIndent()
            val params = mapOf("ids" to ids.map(IntId<SegmentGeometry>::intValue))
            jdbcTemplate.query(sql, params) { rs, _ ->
                val id = rs.getIntId<SegmentGeometry>("id")
                id to SegmentGeometry(
                    id = id,
                    points = getSegmentPoints(rs, "geometry_wkt", "height_values", "cant_values"),
                    resolution = rs.getInt("resolution"),
                )
            }.associate { it }
        } else mapOf()
    }
}

fun getSegmentPoints(
    rs: ResultSet,
    geometryColumn: String,
    heightColumn: String,
    cantColumn: String,
): List<LayoutPoint> {
    val rawGeometry = rs.getString(geometryColumn)
    if (rawGeometry == null) return emptyList()
    val geometryValues = parse3DMLineString(rs.getString(geometryColumn))
    val heightValues = rs.getNullableDoubleListOrNullFromString(heightColumn)
    val cantValues = rs.getNullableDoubleListOrNullFromString(cantColumn)
    return geometryValues.mapIndexed { index, coordinate ->
        LayoutPoint(
            x = coordinate.x,
            y = coordinate.y,
            z = heightValues?.getOrNull(index),
            m = coordinate.m,
            cant = cantValues?.getOrNull(index)
        )
    }
}

private data class SegmentData(
    val id: IndexedId<LayoutSegment>,
    val sourceId: DomainId<GeometryElement>?,
    val sourceStart: Double?,
    val switchId: DomainId<TrackLayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    val source: GeometrySource,
)
