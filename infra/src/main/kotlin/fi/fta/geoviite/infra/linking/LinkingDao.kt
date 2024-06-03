package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryAlignmentLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryElementLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryKmPostLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.GeometrySwitchLinkStatus
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIndexedId
import fi.fta.geoviite.infra.util.getIntArray
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getSrid
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * MissingLayoutSwitchLinking contains info about a situation where:
 *
 * A geometry switch is linked to track layout alignments via
 * links between segments and geometry elements but segments do not contain
 * links to any track layout switches.
 */
data class MissingLayoutSwitchLinking(
    val planSrid: Srid,
    val planId: IntId<GeometryPlan>,
    val geometrySwitchId: IntId<GeometrySwitch>,
    val locationTrackIds: List<RowVersion<LocationTrack>>,
)

data class MissingLayoutSwitchLinkingRowData(
    val planSrid: Srid,
    val planId: IntId<GeometryPlan>,
    val geometrySwitchId: IntId<GeometrySwitch>,
    val locationTrackId: RowVersion<LocationTrack>,
)

@Transactional(readOnly = true)
@Component
class LinkingDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchPlanLinkStatus(layoutContext: LayoutContext, planId: IntId<GeometryPlan>): GeometryPlanLinkStatus {
        logger.daoAccess(
            AccessType.FETCH,
            GeometryPlanLinkStatus::class,
            "layoutContext" to layoutContext,
            "planId" to planId,
        )

        return GeometryPlanLinkStatus(
            planId,
            fetchAlignmentLinkStatus(layoutContext, planId = planId),
            fetchSwitchLinkStatus(layoutContext, planId = planId),
            fetchKmPostLinkStatus(layoutContext, planId = planId),
        )
    }

    private fun fetchAlignmentLinkStatus(
        layoutContext: LayoutContext,
        planId: IntId<GeometryPlan>,
    ): List<GeometryAlignmentLinkStatus> {
        val sql = """
          select
            element.alignment_id,
            element.element_index,
            bool_or(case 
              when location_track.row_id is not null or reference_track_number.row_id is not null then true 
              else false 
            end) as is_linked,
            array_agg(distinct location_track.official_id) filter ( where location_track.official_id is not null ) as location_track_ids,
            array_agg(distinct reference_line.official_id) filter ( where reference_line.official_id is not null ) as reference_line_ids  
          from geometry.alignment geometry_alignment
            join geometry.element on geometry_alignment.id = element.alignment_id
            left join layout.segment_version
              on element.alignment_id = segment_version.geometry_alignment_id
                and element.element_index = segment_version.geometry_element_index
            left join layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) location_track
              on location_track.alignment_id = segment_version.alignment_id
                and location_track.alignment_version = segment_version.alignment_version
                and location_track.state != 'DELETED'
            left join layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id) reference_line
              on reference_line.alignment_id = segment_version.alignment_id
                and reference_line.alignment_version = segment_version.alignment_version
            left join layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id) reference_track_number
              on reference_line.track_number_id = reference_track_number.row_id
                and reference_track_number.state != 'DELETED'
            where geometry_alignment.plan_id = :plan_id
          group by element.alignment_id, element.element_index
          order by element.alignment_id, element.element_index;
        """.trimIndent()
        val params = mapOf(
            "plan_id" to planId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )

        val elements = jdbcTemplate.query(sql, params) { rs, _ ->
            val alignmentId = rs.getIntId<GeometryAlignment>("alignment_id")
            val geometryElementLinkStatus = GeometryElementLinkStatus(
                id = rs.getIndexedId("alignment_id", "element_index"),
                isLinked = rs.getBoolean("is_linked"),
                linkedLocationTrackIds = rs.getIntIdArray("location_track_ids"),
                linkedReferenceLineIds = rs.getIntIdArray("reference_line_ids"),
            )
            alignmentId to geometryElementLinkStatus
        }
        return elements
            .groupBy({ (alignmentId, _) -> alignmentId }, { (_, element) -> element })
            .map { (alignmentId, elements) -> GeometryAlignmentLinkStatus(alignmentId, elements) }
    }

    private fun fetchKmPostLinkStatus(
        layoutContext: LayoutContext,
        planId: IntId<GeometryPlan>,
    ): List<GeometryKmPostLinkStatus> {
        val sql = """
           select
              geometry_km_post.id,
              array_agg(km_post.official_id) as km_post_id_list
              from geometry.km_post geometry_km_post
              join layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id)
                as km_post on geometry_km_post.id = km_post.geometry_km_post_id
              where
               plan_id=:plan_id
              group by geometry_km_post.id
        """.trimIndent()
        val params = mapOf(
            "plan_id" to planId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            GeometryKmPostLinkStatus(
                id = rs.getIntId("id"),
                linkedKmPosts = rs.getIntArray("km_post_id_list").map { id -> IntId(id) },
            )
        }
    }

    private fun fetchSwitchLinkStatus(
        layoutContext: LayoutContext,
        planId: IntId<GeometryPlan>,
    ): List<GeometrySwitchLinkStatus> {
        val sql = """
        select
            switch.id,
            bool_or(case 
              when segment_version.switch_id is not null 
               and layout_switch.row_id is not null 
               and location_track.row_id is not null
              then true 
              else false 
            end) as is_linked
          from geometry.switch
            left join geometry.element
                      on switch.id = element.switch_id
            left join layout.segment_version
                      on segment_version.geometry_element_index = element.element_index
                        and segment_version.geometry_alignment_id = element.alignment_id
            left join
              layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id)
                location_track on location_track.alignment_id = segment_version.alignment_id
                        and location_track.alignment_version = segment_version.alignment_version
                        and location_track.state != 'DELETED'
            left join lateral layout.switch_in_layout_context(:publication_state::layout.publication_state,
                                                              :design_id,
                                                              segment_version.switch_id)
              layout_switch on layout_switch.state_category != 'NOT_EXISTING'
          where switch.plan_id = :plan_id
          group by switch.id;
        """.trimIndent()
        val params = mapOf(
            "plan_id" to planId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            GeometrySwitchLinkStatus(
                id = rs.getIntId("id"),
                isLinked = rs.getBoolean("is_linked"),
            )
        }
    }

    fun getMissingLayoutSwitchLinkings(
        bbox: BoundingBox,
        geometrySwitchId: IntId<GeometrySwitch>? = null,
    ): List<MissingLayoutSwitchLinking> {
        logger.daoAccess(
            AccessType.FETCH, MissingLayoutSwitchLinking::class,
            "bbox" to bbox, "geometrySwitchId" to geometrySwitchId
        )
        val sql = """
            select
              location_track.row_id as location_track_id,
              location_track.row_version as location_track_version,
              gswitch.id as geometry_switch_id,
              plan.srid,
              plan.id as plan_id
            from layout.segment_version
            inner join layout.location_track_in_layout_context('DRAFT', null) location_track
              on location_track.alignment_id = segment_version.alignment_id
                and location_track.alignment_version = segment_version.alignment_version
                and location_track.state != 'DELETED'
            inner join geometry.element
              on element.alignment_id = segment_version.geometry_alignment_id 
              and element.element_index = segment_version.geometry_element_index
            inner join geometry.switch gswitch
              on gswitch.id = element.switch_id
            inner join geometry.plan plan
              on plan.id = gswitch.plan_id
              and plan.srid is not null
            where
              -- prefilter with geom switch id if provided
              (
                cast(:geometry_switch_id as int) is null or 
                element.switch_id = :geometry_switch_id
              )
              and location_track.row_id is not null
              -- find geom switches that miss linking information
              and element.switch_id in (
                  select
                    distinct e.switch_id
                  from layout.alignment
                    inner join layout.segment_version s on alignment.id = s.alignment_id 
                      and alignment.version = s.alignment_version
                    inner join layout.segment_geometry sg on s.geometry_id = sg.id
                    inner join geometry.element e
                      on e.alignment_id = s.geometry_alignment_id 
                        and e.element_index = s.geometry_element_index
                  left join layout.switch_in_layout_context('DRAFT', null) switch 
                    on switch.official_id = s.switch_id
                  where
                    postgis.st_intersects(
                      postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                      sg.bounding_box
                    )
                    and e.switch_id is not null
                    and (s.switch_id is null or switch.state_category != 'NOT_EXISTING')
              )
        """.trimIndent()

        val params = mapOf(
            "geometry_switch_id" to geometrySwitchId,
            "layout_srid" to LAYOUT_SRID.code,
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y
        )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                MissingLayoutSwitchLinkingRowData(
                    planId = rs.getIntId("plan_id"),
                    planSrid = rs.getSrid("srid"),
                    geometrySwitchId = rs.getIntId("geometry_switch_id"),
                    locationTrackId = rs.getRowVersion("location_track_id", "location_track_version"),
                )
            }
            .groupBy { it.geometrySwitchId }
            .map { (geometryId, groupedRowData) ->
                MissingLayoutSwitchLinking(
                    planId = groupedRowData.first().planId,
                    geometrySwitchId = geometryId,
                    planSrid = groupedRowData.first().planSrid,
                    locationTrackIds = groupedRowData.map { rowData -> rowData.locationTrackId }.distinct(),
                )
            }
    }

    fun getSwitchBoundsFromTracks(
        layoutContext: LayoutContext,
        switchId: IntId<TrackLayoutSwitch>,
    ): BoundingBox? {
        val sql = """ 
            select 
               case 
                 when segment_version.switch_id = :switch_id and segment_version.switch_start_joint_number is not null then 1
                 when location_track.topology_start_switch_id = :switch_id and segment_version.segment_index = 0 then 1
                 else 0
               end as start_is_joint,
               case 
                 when segment_version.switch_id = :switch_id and segment_version.switch_end_joint_number is not null then 1
                 when location_track.topology_end_switch_id = :switch_id and segment_version.segment_index = alignment.segment_count-1 then 1
                 else 0
               end as end_is_joint,
               postgis.st_x(postgis.st_startpoint(segment_geometry.geometry)) as start_x,
               postgis.st_y(postgis.st_startpoint(segment_geometry.geometry)) as start_y,
               postgis.st_x(postgis.st_endpoint(segment_geometry.geometry)) as end_x,
               postgis.st_y(postgis.st_endpoint(segment_geometry.geometry)) as end_y
            from layout.alignment
              inner join layout.segment_version on alignment.id = segment_version.alignment_id
                and alignment.version = segment_version.alignment_version
              inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
              inner join layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) location_track 
                on location_track.alignment_id = alignment.id and location_track.alignment_version = alignment.version
            where (
                segment_version.switch_id = :switch_id
                or (segment_version.segment_index = 0 and location_track.topology_start_switch_id = :switch_id)
                or (segment_version.segment_index = alignment.segment_count-1 and location_track.topology_end_switch_id = :switch_id)
              )
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )
        val allPoints = jdbcTemplate.query(sql, params) { rs, _ ->
            val start =
                if (rs.getBoolean("start_is_joint")) rs.getPoint("start_x", "start_y")
                else null
            val end =
                if (rs.getBoolean("end_is_joint")) rs.getPoint("end_x", "end_y")
                else null
            listOfNotNull(start, end)
        }.flatten()
        return boundingBoxAroundPointsOrNull(allPoints)
    }
}
