package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.MAX_GAP_BETWEEN_SEGMENT_ENDS

@GeoviiteService
class PlanLayoutService(private val planLayoutCache: PlanLayoutCache, private val geometryDao: GeometryDao) {
    fun getLayoutPlan(
        planVersion: RowVersion<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> =
        handlePointListStepLength(
            planLayoutCache.getPlanLayout(planVersion, includeGeometryData),
            includeGeometryData,
            pointListStepLength,
        )

    fun getLayoutPlan(
        geometryPlanId: IntId<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> =
        getLayoutPlan(geometryDao.fetchPlanVersion(geometryPlanId), includeGeometryData, pointListStepLength)

    fun checkElementsPreventingLinking(
        planId: IntId<GeometryPlan>,
        alignmentId: IntId<GeometryAlignment>,
    ): List<PlanElementName>? {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val planAlignment = plan.alignments.find { it.id == alignmentId } ?: return null
        val planLayout = getLayoutPlan(planVersion).first ?: return null
        val planLayoutAlignment = planLayout.alignments.find { it.id == alignmentId } ?: return null

        return planLayoutAlignment.segments
            .mapIndexed { index, segment -> segment to index }
            .zipWithNext { (prev), (next, index) ->
                index to prev.geometry.segmentEnd.isSame(next.geometry.segmentStart, MAX_GAP_BETWEEN_SEGMENT_ENDS)
            }
            .filter { (_, samePoint) -> !samePoint }
            .mapNotNull { (index) -> planAlignment.elements[index].name }
    }

    private fun handlePointListStepLength(
        layoutResult: Pair<GeometryPlanLayout?, TransformationError?>,
        includeGeometryData: Boolean,
        pointListStepLength: Int,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        val (layout, error) = layoutResult
        return if (layout != null && includeGeometryData && pointListStepLength > 0) {
            layout.withLayoutGeometry(pointListStepLength) to error
        } else layout to error
    }
}
