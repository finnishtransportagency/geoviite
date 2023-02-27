package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_ALIGNMENT
import fi.fta.geoviite.infra.geometry.create2DPolygonString
import fi.fta.geoviite.infra.geometry.createPostgis3DMLineString
import fi.fta.geoviite.infra.geometry.parse3DMLineString
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_ALIGNMENT
import net.postgis.jdbc.PGgeometry
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

@Transactional(readOnly = true)
@Component
class LayoutAlignmentDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

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
              postgis.st_astext(geometry) as geometry_wkt,
              resolution,
              case 
                when height_values is null then null 
                else array_to_string(height_values, ',', 'null') 
              end as height_values,
              case 
                when cant_values is null then null 
                else array_to_string(cant_values, ',', 'null') 
              end as cant_values,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number,
              start,
              length,
              source
            from layout.segment_version 
            where alignment_id = :alignment_id
              and alignment_version = :alignment_version
              and deleted = false
            order by alignment_id, segment_index
        """.trimIndent()
        val params = mapOf(
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutSegment(
                id = rs.getIndexedId("alignment_id", "segment_index"),
                sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                sourceStart = rs.getDoubleOrNull("source_start"),
                points = getSegmentPoints(rs, "geometry_wkt", "height_values", "cant_values"),
                resolution = rs.getInt("resolution"),
                switchId = rs.getIntIdOrNull("switch_id"),
                startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                start = rs.getDouble("start"),
                source = rs.getEnum("source"),
            )
        }
    }

    fun fetchSegmentGeometriesAndPlanMetadata(
        alignmentId: IntId<LayoutAlignment>,
        boundingBox: BoundingBox?
    ): List<SegmentGeometryAndMetadata> {
        val sql = """
            with first as (
              select segment_index
              from layout.segment
              where (
                :use_bounding_box = false
                  or postgis.st_intersects(postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                  segment.geometry
                )
              ) and segment.alignment_id = :id
              order by segment_index
              limit 1
            ),
              last as (
                select segment_index
                  from layout.segment
                  where (
                        :use_bounding_box = false
                      or postgis.st_intersects(postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                                               segment.geometry
                          )
                    ) and segment.alignment_id = :id
                  order by segment_index desc
                  limit 1
              ),
              segment_points as (
                select        case
                                when :use_bounding_box = true 
                                    and (layout.segment.segment_index = first.segment_index
                                    or layout.segment.segment_index = last.segment_index)
                                  then postgis.st_intersection(postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid), segment.geometry)
                                else segment.geometry
                              end as geometry, layout.segment.segment_index
                from layout.segment
                  full outer join first on true
                  full outer join last on true
                where segment.alignment_id = :id
              )
            select layout.segment.segment_index,
                   plan.id as plan_id,
                   plan_file.name as filename,
                   layout.initial_import_metadata.plan_file_name,
                   segment.source,
                   layout.segment.alignment_id,
              case
                when segment.height_values is null then null
                else array_to_string(segment.height_values, ',', 'null')
              end as height_values,
              case
                when segment.cant_values is null then null
                else array_to_string(segment.cant_values, ',', 'null')
              end as cant_values,
              postgis.st_x(postgis.st_startpoint(segment_points.geometry)) as start_x,
              postgis.st_y(postgis.st_startpoint(segment_points.geometry)) as start_y,
              postgis.st_x(postgis.st_endpoint(segment_points.geometry)) as end_x,
              postgis.st_y(postgis.st_endpoint(segment_points.geometry)) as end_y
              from layout.alignment
                left join layout.segment on alignment.id = segment.alignment_id
                left join geometry.alignment on segment.geometry_alignment_id = geometry.alignment.id
                left join geometry.plan on geometry.alignment.plan_id = plan.id
                left join geometry.plan_file on geometry.plan.id = geometry.plan_file.plan_id
                left join layout.initial_segment_metadata on layout.segment.alignment_id = initial_segment_metadata.alignment_id
                  and layout.segment.segment_index = initial_segment_metadata.segment_index
                left join layout.initial_import_metadata on initial_segment_metadata.metadata_id = initial_import_metadata.id
                full outer join first on true
                full outer join last on true
                inner join segment_points on layout.segment.segment_index = segment_points.segment_index
              where layout.alignment.id = :id
                and layout.segment.segment_index >= first.segment_index
                and layout.segment.segment_index <= last.segment_index
              order by segment.segment_index
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
                segmentId = rs.getIndexedId<LayoutSegment>("alignment_id","segment_index")
            )
        }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, alignmentId)
        return result
    }

    fun fetchMetadata(alignmentId: IntId<LayoutAlignment>): List<LayoutSegmentMetadata> {
        //language=SQL
        val sql = """
            select
              postgis.st_x(postgis.st_startpoint(segment.geometry)) as start_point_x,
              postgis.st_y(postgis.st_startpoint(segment.geometry)) as start_point_y,
              postgis.st_x(postgis.st_endpoint(segment.geometry)) as end_point_x,
              postgis.st_y(postgis.st_endpoint(segment.geometry)) as end_point_y,
              alignment.name as alignment_name,
              plan.plan_time,
              plan.measurement_method,
              plan.srid,
              plan_file.name as file_name
            from layout.segment
              left join geometry.alignment on alignment.id = segment.geometry_alignment_id
              left join geometry.plan on alignment.plan_id = plan.id
              left join geometry.plan_file on plan_file.plan_id = plan.id
            where segment.alignment_id = :alignment_id
            order by segment.alignment_id, segment.segment_index
        """.trimIndent()

        val params = mapOf(
            "alignment_id" to alignmentId.intValue,
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
            val sql = """
              insert into layout.segment(
                alignment_id,
                alignment_version,
                segment_index,
                geometry_alignment_id,
                geometry_element_index,
                resolution,
                geometry,
                height_values,
                cant_values,
                switch_id,
                switch_start_joint_number,
                switch_end_joint_number,
                start,
                length,
                source_start,
                source
              )
              values(
                :alignment_id,
                :alignment_version,
                :segment_index,
                :geometry_alignment_id,
                :geometry_element_index,
                :resolution,
                postgis.st_setsrid(:line_string::postgis.geometry, :srid),
                string_to_array(:height_values, ',', 'null')::decimal[],
                string_to_array(:cant_values, ',', 'null')::decimal[],
                :switch_id,
                :switch_start_joint_number,
                :switch_end_joint_number,
                :start,
                :length,
                :source_start,
                :source::layout.geometry_source
              )
              on conflict (alignment_id, segment_index) do update
              set
                alignment_version = excluded.alignment_version,
                geometry_alignment_id = excluded.geometry_alignment_id,
                geometry_element_index = excluded.geometry_element_index,
                resolution = excluded.resolution,
                geometry = excluded.geometry,
                height_values = excluded.height_values,
                cant_values = excluded.cant_values,
                switch_id = excluded.switch_id,
                switch_start_joint_number = excluded.switch_start_joint_number,
                switch_end_joint_number = excluded.switch_end_joint_number,
                start = excluded.start,
                length = excluded.length,
                source_start = excluded.source_start,
                source = excluded.source
              """.trimIndent()
            val params = segments.mapIndexed { i, s ->
                mapOf(
                    "alignment_id" to alignmentId.id.intValue,
                    "alignment_version" to alignmentId.version,
                    "segment_index" to i,
                    "geometry_alignment_id" to if (s.sourceId is IndexedId) s.sourceId.parentId else null,
                    "geometry_element_index" to if (s.sourceId is IndexedId) s.sourceId.index else null,
                    "resolution" to s.resolution,
//                "line_string" to create3DMLineString(s.points),
                    "line_string" to PGgeometry(createPostgis3DMLineString(s.points)),
                    "srid" to LAYOUT_SRID.code,
                    "height_values" to createListString(s.points) { p -> p.z },
                    "cant_values" to createListString(s.points) { p -> p.cant },
                    "switch_id" to if (s.switchId is IntId) s.switchId.intValue else null,
                    "switch_start_joint_number" to s.startJointNumber?.intValue,
                    "switch_end_joint_number" to s.endJointNumber?.intValue,
                    "start" to s.start,
                    "length" to s.length,
                    "source_start" to s.sourceStart,
                    "source" to s.source.name,
                )
            }.toTypedArray()
            jdbcTemplate.batchUpdate(sql, params)
        }

        if (alignmentId.version > 1) {
            val sqlDelete = """ 
              delete from layout.segment 
              where alignment_id = :alignment_id 
                and segment.alignment_version < :alignment_version  
            """.trimIndent()

            val paramsDelete = mapOf(
                "alignment_id" to alignmentId.id.intValue,
                "alignment_version" to alignmentId.version,
            )
            jdbcTemplate.update(sqlDelete, paramsDelete)
        }
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
