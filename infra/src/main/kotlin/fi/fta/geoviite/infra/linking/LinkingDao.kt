package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryAlignmentLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryElementLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryKmPostLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometrySwitchLinkStatus
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIndexedId
import fi.fta.geoviite.infra.util.getIntArray
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class LinkingDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchPlanLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): List<GeometryPlanLinkStatus> {
        logger.daoAccess(
            AccessType.FETCH,
            GeometryPlanLinkStatus::class,
            "layoutContext" to layoutContext,
            "planIds" to planIds,
        )

        val alignmentStatuses = fetchAlignmentLinkStatuses(layoutContext, planIds = planIds)
        val switchStatuses = fetchSwitchLinkStatuses(layoutContext, planIds = planIds)
        val kmPostStatuses = fetchKmPostLinkStatuses(layoutContext, planIds = planIds)

        return planIds.map { planId ->
            GeometryPlanLinkStatus(
                planId,
                alignmentStatuses.getOrDefault(planId, listOf()),
                switchStatuses.getOrDefault(planId, listOf()),
                kmPostStatuses.getOrDefault(planId, listOf()),
            )
        }
    }

    private fun fetchAlignmentLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): Map<IntId<GeometryPlan>, List<GeometryAlignmentLinkStatus>> {
        val sql =
            """
          select
            plan_id,
            element.alignment_id,
            element.element_index,
            bool_or(case 
              when location_track.id is not null or reference_track_number.id is not null then true
              else false 
            end) as is_linked,
            array_agg(distinct location_track.id) filter ( where location_track.id is not null ) as location_track_ids,
            array_agg(distinct reference_line.id) filter ( where reference_line.id is not null ) as reference_line_ids
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
              on reference_line.track_number_id = reference_track_number.id
                and reference_track_number.state != 'DELETED'
            where geometry_alignment.plan_id in (:plan_ids)
          group by plan_id, element.alignment_id, element.element_index
          order by plan_id, element.alignment_id, element.element_index;
        """
                .trimIndent()
        val params =
            mapOf(
                "plan_ids" to planIds.map { it.intValue },
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )

        val elements =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val planId = rs.getIntId<GeometryPlan>("plan_id")
                val alignmentId = rs.getIntId<GeometryAlignment>("alignment_id")
                val geometryElementLinkStatus =
                    GeometryElementLinkStatus(
                        id = rs.getIndexedId("alignment_id", "element_index"),
                        isLinked = rs.getBoolean("is_linked"),
                        linkedLocationTrackIds = rs.getIntIdArray("location_track_ids"),
                        linkedReferenceLineIds = rs.getIntIdArray("reference_line_ids"),
                    )
                Triple(planId, alignmentId, geometryElementLinkStatus)
            }
        return elements
            .groupBy { (planId) -> planId }
            .mapValues { byPlan ->
                byPlan.value.groupBy({ (_, alignmentId, _) -> alignmentId }, { (_, _, element) -> element }).map {
                    (alignmentId, elements) ->
                    GeometryAlignmentLinkStatus(alignmentId, elements)
                }
            }
    }

    private fun fetchKmPostLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): Map<IntId<GeometryPlan>, List<GeometryKmPostLinkStatus>> {
        val sql =
            """
           select
              plan_id,
              geometry_km_post.id,
              array_agg(km_post.id) as km_post_id_list
              from geometry.km_post geometry_km_post
              left join layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id)
                as km_post on geometry_km_post.id = km_post.geometry_km_post_id
              where plan_id in (:plan_ids)
              group by geometry_km_post.id
        """
                .trimIndent()
        val params =
            mapOf(
                "plan_ids" to planIds.map { it.intValue },
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                rs.getIntId<GeometryPlan>("plan_id") to
                    GeometryKmPostLinkStatus(
                        id = rs.getIntId("id"),
                        linkedKmPosts = rs.getIntArray("km_post_id_list").map { id -> IntId(id) },
                    )
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun fetchSwitchLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): Map<IntId<GeometryPlan>, List<GeometrySwitchLinkStatus>> {
        val sql =
            """
            with all_switch_links_in_plans as (
              select
                switch.id as geometry_switch_id,
                segment_version.alignment_id,
                segment_version.alignment_version,
                segment_version.switch_id as layout_switch_id
                from geometry.switch
                  join geometry.element on element.switch_id = switch.id
                  join layout.segment_version on segment_version.geometry_element_index = element.element_index
                  and segment_version.geometry_alignment_id = element.alignment_id
                  and segment_version.switch_id is not null
                where switch.plan_id in (:plan_ids)
            ),
              with_ok_layout_alignment as (
                select unnest(geometry_switch_ids) as geometry_switch_id,
                       unnest(layout_switch_ids) as layout_switch_id
                  from (
                    select array_agg(geometry_switch_id) as geometry_switch_ids,
                           array_agg(layout_switch_id) as layout_switch_ids
                      from all_switch_links_in_plans link
                      group by alignment_id, alignment_version
                      having exists(select *
                                      from layout.location_track_in_layout_context(
                                          :publication_state::layout.publication_state, :design_id) location_track
                                      where location_track.state != 'DELETED'
                                        and location_track.alignment_id = link.alignment_id
                                        and location_track.alignment_version = link.alignment_version)
                  ) alignment_checked
              ),
              with_ok_layout_switch as (
                select distinct unnest(geometry_switch_ids) as geometry_switch_id
                  from (
                    select array_agg(geometry_switch_id) as geometry_switch_ids
                      from with_ok_layout_alignment link
                      group by layout_switch_id
                      having exists(select *
                                      from layout.switch_in_layout_context(:publication_state::layout.publication_state,
                                                                           :design_id)
                                      where state_category != 'NOT_EXISTING' and id = layout_switch_id)
                  ) switch_checked
              )
            select plan_id, id, exists(select * from with_ok_layout_switch ok where ok.geometry_switch_id = switch.id) as is_linked
              from geometry.switch
              where plan_id in (:plan_ids);
        """
                .trimIndent()

        val params =
            mapOf(
                "plan_ids" to planIds.map { it.intValue },
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                rs.getIntId<GeometryPlan>("plan_id") to
                    GeometrySwitchLinkStatus(id = rs.getIntId("id"), isLinked = rs.getBoolean("is_linked"))
            }
            .groupBy({ it.first }, { it.second })
    }
}
