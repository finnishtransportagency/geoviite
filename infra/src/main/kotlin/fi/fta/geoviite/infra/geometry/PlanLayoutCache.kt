package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_GEOMETRY_PLAN_LAYOUT
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.toTrackLayout
import fi.fta.geoviite.infra.util.LocalizationKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service


private val maxTriangulationArea = BoundingBox(
    x = Range(70265.0, 732722.0),
    y = Range(6610378.0, 7780971.0),
)

const val INFRAMODEL_TRANSFORMATION_KEY_PARENT = "error.infra-model.transformation"

data class TransformationError(
    private val key: String,
    private val units: GeometryUnits,
): ValidationError {
    override val errorType = ErrorType.TRANSFORMATION_ERROR
    override val localizationKey = LocalizationKey("$INFRAMODEL_TRANSFORMATION_KEY_PARENT.$key")
    val srid = units.coordinateSystemSrid
    val coordinateSystemName = units.coordinateSystemName
}

@Service
class PlanLayoutCache(
    private val geometryDao: GeometryDao,
    private val heightTriangleDao: HeightTriangleDao,
    private val coordinateTransformationService: CoordinateTransformationService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Cacheable(CACHE_GEOMETRY_PLAN_LAYOUT, sync = true)
    fun getPlanLayout(
        planVersion: RowVersion<GeometryPlan>,
        includeGeometryData: Boolean = true,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        logger.serviceCall(
            "getPlanLayout",
            "rowVersion" to planVersion,
            "includeGeometryData" to includeGeometryData,
        )
        return transformToLayoutPlan(geometryDao.fetchPlan(planVersion), includeGeometryData)
    }

    fun transformToLayoutPlan(
        geometryPlan: GeometryPlan,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        logger.serviceCall(
            "getTrackLayoutPlan",
            "geometryPlanId" to geometryPlan.id,
            "includeGeometryData" to includeGeometryData,
        )
        val srid = geometryPlan.units.coordinateSystemSrid
        val planToLayoutTransformation = if (srid != null) coordinateTransformationService.getTransformation(srid,
            LAYOUT_SRID
        ) else null
        if (planToLayoutTransformation == null) {
            logger.warn("Not converting plan to layout as there is no SRID: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            return null to TransformationError("srid-missing", geometryPlan.units)
        }

        val polygon = getBoundingPolygonPointsFromAlignments(geometryPlan.alignments, planToLayoutTransformation)

        return if (polygon.isEmpty()) {
            logger.warn("Not converting plan to layout as bounds could not be resolved: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            null to TransformationError("bounds-resolution-failed", geometryPlan.units)
        } else if (!polygon.all { point -> maxTriangulationArea.contains(point) }) {
            logger.warn("Not converting plan to layout as bounds are outside Finnish borders: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            null to TransformationError("bounds-outside-finland", geometryPlan.units)
        } else try {
            toTrackLayout(
                geometryPlan = geometryPlan,
                heightTriangles = heightTriangleDao.fetchTriangles(polygon),
                planToLayout = planToLayoutTransformation,
                includeGeometryData = includeGeometryData,
                pointListStepLength = pointListStepLength,
            ) to null
        } catch (e: CoordinateTransformationException) {
            logger.warn("Could not convert plan coordinates: " +
                    "id=${geometryPlan.id} " +
                    "srid=${geometryPlan.units.coordinateSystemSrid} " +
                    "file=${geometryPlan.fileName}",
                e)
            null to TransformationError("coordinate-transformation-failed", geometryPlan.units)
        } catch (e: Exception) {
            logger.warn("Failed to convert plan to layout form: " +
                    "id=${geometryPlan.id} " +
                    "srid=${geometryPlan.units.coordinateSystemSrid} " +
                    "file=${geometryPlan.fileName}",
                e)
            null to TransformationError("plan-transformation-failed", geometryPlan.units)
        }
    }
}
