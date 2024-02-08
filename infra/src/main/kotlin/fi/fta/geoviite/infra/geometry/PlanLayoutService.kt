package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import org.springframework.stereotype.Service
import java.util.stream.Collectors

@Service
class PlanLayoutService(
    private val planLayoutCache: PlanLayoutCache,
    private val geometryDao: GeometryDao,
) {
    fun getLayoutPlan(
        planVersion: RowVersion<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> = handlePointListStepLength(
        planLayoutCache.getPlanLayout(planVersion, includeGeometryData), includeGeometryData, pointListStepLength
    )

    fun getLayoutPlan(
        geometryPlanId: IntId<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> =
        getLayoutPlan(geometryDao.fetchPlanVersion(geometryPlanId), includeGeometryData, pointListStepLength)

    fun getManyLayoutPlans(
        planIds: List<IntId<GeometryPlan>>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): List<Pair<GeometryPlanLayout?, TransformationError?>> = planIds
        .map { planId ->
            val planVersion = geometryDao.fetchPlanVersion(planId)
            planLayoutCache.prepareGetPlanLayout(planVersion, includeGeometryData)
        }
        .parallelStream()
        .map { process -> handlePointListStepLength(process(), includeGeometryData, pointListStepLength) }
        .collect(Collectors.toList())

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
