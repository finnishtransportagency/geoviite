package fi.fta.geoviite.infra.geometry

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.planCacheDuration
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.toTrackLayout
import fi.fta.geoviite.infra.util.LocalizationKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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

const val GEOMETRY_PLAN_CACHE_SIZE = 100L

@Service
class PlanLayoutCache(
    private val geometryDao: GeometryDao,
    private val heightTriangleDao: HeightTriangleDao,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val trackNumberDao: LayoutTrackNumberDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val validHeightTriangulationArea by lazy {
        heightTriangleDao.fetchTriangulationNetworkBounds().also { bbox ->
            logger.info("Plan layout transformation height triangulation area: $bbox")
        }
    }

    private val cache: Cache<PlanLayoutCacheKey, Pair<GeometryPlanLayout?, TransformationError?>> =
        Caffeine.newBuilder().maximumSize(GEOMETRY_PLAN_CACHE_SIZE).expireAfterAccess(planCacheDuration).build()

    fun getPlanLayout(
        planVersion: RowVersion<GeometryPlan>,
        includeGeometryData: Boolean = true,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        return prepareGetPlanLayout(planVersion, includeGeometryData)()
    }

    fun prepareGetPlanLayout(
        planVersion: RowVersion<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): () -> Pair<GeometryPlanLayout?, TransformationError?> {
        logger.serviceCall(
            "getPlanLayout",
            "rowVersion" to planVersion,
            "includeGeometryData" to includeGeometryData,
        )
        val geometryPlan = geometryDao.fetchPlan(planVersion)
        return prepareTransformToLayoutPlan(planVersion, geometryPlan, includeGeometryData, pointListStepLength)
    }

    fun transformToLayoutPlan(
        geometryPlan: GeometryPlan,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> =
        prepareTransformToLayoutPlan(null, geometryPlan, includeGeometryData, pointListStepLength)()

    private fun prepareTransformToLayoutPlan(
        planVersion: RowVersion<GeometryPlan>?,
        geometryPlan: GeometryPlan,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): () -> Pair<GeometryPlanLayout?, TransformationError?> {
        val srid = geometryPlan.units.coordinateSystemSrid
        val planToLayoutTransformation = if (srid != null) coordinateTransformationService.getTransformation(
            srid, LAYOUT_SRID
        ) else null
        if (planToLayoutTransformation == null) {
            logger.warn("Not converting plan to layout as there is no SRID: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            return { null to TransformationError("srid-missing", geometryPlan.units) }
        }
        val polygon = getBoundingPolygonPointsFromAlignments(geometryPlan.alignments, planToLayoutTransformation)

        if (polygon.isEmpty()) {
            logger.warn("Not converting plan to layout as bounds could not be resolved: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            return { null to TransformationError("bounds-resolution-failed", geometryPlan.units) }
        } else if (!polygon.all { point -> validHeightTriangulationArea.contains(point) }) {
            logger.warn("Not converting plan to layout as bounds are outside height triangulation network: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            return { null to TransformationError("bounds-outside-finland", geometryPlan.units) }
        }
        val heightTriangles = heightTriangleDao.fetchTriangles(polygon)
        val trackNumberId = geometryPlan.trackNumber
            ?.let { trackNumberDao.list(MainLayoutContext.official, geometryPlan.trackNumber) }
            ?.firstOrNull()
            ?.id as? IntId

        fun transform() = transformToLayoutPlan(
            geometryPlan,
            trackNumberId,
            includeGeometryData,
            pointListStepLength,
            planToLayoutTransformation,
            heightTriangles,
            logger
        )
        // caching is optional because some callers just want the transformation, but don't have a saved plan
        return if (planVersion == null) ::transform else { ->
            cache.get(PlanLayoutCacheKey(planVersion, includeGeometryData)) { transform() }
        }
    }
}

data class PlanLayoutCacheKey(
    val planVersion: RowVersion<GeometryPlan>,
    val includeGeometryData: Boolean,
)

private fun transformToLayoutPlan(
    geometryPlan: GeometryPlan,
    trackNumberId: IntId<TrackLayoutTrackNumber>?,
    includeGeometryData: Boolean,
    pointListStepLength: Int,
    planToLayoutTransformation: Transformation,
    heightTriangles: List<HeightTriangle>,
    logger: Logger,
): Pair<GeometryPlanLayout?, TransformationError?> =
    try {
        toTrackLayout(
            geometryPlan = geometryPlan,
            trackNumberId = trackNumberId,
            heightTriangles = heightTriangles,
            planToLayout = planToLayoutTransformation,
            includeGeometryData = includeGeometryData,
            pointListStepLength = pointListStepLength,
        ) to null
    } catch (e: CoordinateTransformationException) {
        logger.warn("Could not convert plan coordinates: " +
            "id=${geometryPlan.id} " +
            "srid=${geometryPlan.units.coordinateSystemSrid} " +
            "file=${geometryPlan.fileName}",
            e,
        )
        null to TransformationError("coordinate-transformation-failed", geometryPlan.units)
    } catch (e: Exception) {
        logger.warn("Failed to convert plan to layout form: " +
            "id=${geometryPlan.id} " +
            "srid=${geometryPlan.units.coordinateSystemSrid} " +
            "file=${geometryPlan.fileName}",
            e,
        )
        null to TransformationError("plan-transformation-failed", geometryPlan.units)
    }
