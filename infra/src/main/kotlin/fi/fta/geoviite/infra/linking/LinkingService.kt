package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


fun isAlignmentConnected(
    location: Point,
    locationTrackPointUpdateType: LocationTrackPointUpdateType,
    alignment: LayoutAlignment,
    distanceTolerance: Double,
): Boolean {

    val firstPoint = alignment.segments.first().points.first()
    val lastPoint = alignment.segments.last().points.last()

    return if (locationTrackPointUpdateType == LocationTrackPointUpdateType.END_POINT) {
        calculateDistance(LAYOUT_SRID, firstPoint, location) <= distanceTolerance
    } else {
        calculateDistance(LAYOUT_SRID, lastPoint, location) <= distanceTolerance
    }
}

@Service
class LinkingService @Autowired constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val layoutKmPostService: LayoutKmPostService,
    private val linkingDao: LinkingDao,
    private val coordinateTransformationService: CoordinateTransformationService
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getSuggestedAlignments(
        locationTrackId: IntId<LocationTrack>,
        location: Point,
        locationTrackPointUpdateType: LocationTrackPointUpdateType,
        bbox: BoundingBox,
    ): List<LocationTrack> {
        return locationTrackService.listNearWithAlignments(DRAFT, bbox)
            .filter { (first, _) -> first.id != locationTrackId }
            .filter { (_, second) -> isAlignmentConnected(location, locationTrackPointUpdateType, second, 2.0) }
            .map { (first, _) -> first }
    }

    @Transactional
    fun saveReferenceLineLinking(parameters: LinkingParameters<ReferenceLine>): IntId<ReferenceLine> {
        val referenceLineId = parameters.layoutInterval.alignmentId
        logger.serviceCall(
            "Save ReferenceLine linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)

        val newAlignment = linkGeometry(layoutAlignment, parameters)
        return referenceLineService.saveDraft(referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(parameters: LinkingParameters<LocationTrack>): IntId<LocationTrack> {
        val locationTrackId = parameters.layoutInterval.alignmentId
        logger.serviceCall(
            "Save LocationTrack linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)

        val newAlignment = linkGeometry(layoutAlignment, parameters)
        val newLocationTrack = updateTopology(locationTrack, layoutAlignment, newAlignment)

        return locationTrackService.saveDraft(newLocationTrack, newAlignment).id
    }

    private fun <T> linkGeometry(layoutAlignment: LayoutAlignment, parameters: LinkingParameters<T>): LayoutAlignment {
        val geometryInterval = parameters.geometryInterval
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)
        val layoutRange = parameters.layoutInterval.mRange
        val geometryRange = parameters.geometryInterval.mRange
        return linkLayoutGeometrySection(layoutAlignment, layoutRange, geometryAlignment, geometryRange)
    }

    private fun updateTopology(
        track: LocationTrack,
        oldAlignment: LayoutAlignment,
        newAlignment: LayoutAlignment,
    ): LocationTrack {
        val startChanged = startChanged(oldAlignment, newAlignment)
        val endChanged = endChanged(oldAlignment, newAlignment)
        return if (startChanged || endChanged) locationTrackService.updateTopology(
            track = track,
            alignment = newAlignment,
            startChanged = startChanged,
            endChanged = endChanged,
        )
        else track
    }

    private fun startChanged(oldAlignment: LayoutAlignment, newAlignment: LayoutAlignment) =
        !equalsXY(oldAlignment.start, newAlignment.start)

    private fun endChanged(oldAlignment: LayoutAlignment, newAlignment: LayoutAlignment) =
        !equalsXY(oldAlignment.end, newAlignment.end)

    private fun equalsXY(point1: IPoint?, point2: IPoint?) = point1?.x == point2?.x && point1?.y == point2?.y

    @Transactional
    fun saveReferenceLineLinking(parameters: EmptyAlignmentLinkingParameters<ReferenceLine>): IntId<ReferenceLine> {
        val referenceLineId = parameters.layoutAlignmentId
        val geometryInterval = parameters.geometryInterval

        logger.serviceCall(
            "Save empty ReferenceLine linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val newAlignment = replaceLayoutGeometry(layoutAlignment, geometryAlignment, geometryInterval.mRange)

        return referenceLineService.saveDraft(referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(parameters: EmptyAlignmentLinkingParameters<LocationTrack>): IntId<LocationTrack> {
        val locationTrackId = parameters.layoutAlignmentId
        val geometryInterval = parameters.geometryInterval

        logger.serviceCall(
            "Save empty LocationTrack linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val newAlignment = replaceLayoutGeometry(layoutAlignment, geometryAlignment, geometryInterval.mRange)
        val newLocationTrack = updateTopology(locationTrack, layoutAlignment, newAlignment)

        return locationTrackService.saveDraft(newLocationTrack, newAlignment).id
    }

    private fun getAlignmentLayout(planId: IntId<GeometryPlan>, alignmentId: IntId<GeometryAlignment>): PlanLayoutAlignment {
        val (geometryPlan, transformationError) = planLayoutService.getLayoutPlan(planId)
        if (geometryPlan == null) {
            throw LinkingFailureException("Could not create plan layout: plan=$planId error=$transformationError")
        }
        return geometryPlan.alignments.find { alignment -> alignment.id == alignmentId }
            ?: throw LinkingFailureException("Geometry alignment not found: plan=$planId alignment=$alignmentId")
    }

    @Transactional
    fun updateReferenceLineGeometry(
        referenceLineId: IntId<ReferenceLine>,
        mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        logger.serviceCall(
            "updateReferenceLineGeometry",
            "referenceLineId" to referenceLineId, "mRange" to mRange
        )

        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)
        val updatedAlignment = cutLayoutGeometry(alignment, mRange)

        return referenceLineService.saveDraft(referenceLine, updatedAlignment).id
    }

    @Transactional
    fun updateLocationTrackGeometry(
        locationTrackId: IntId<LocationTrack>,
        mRange: Range<Double>,
    ): IntId<LocationTrack> {
        logger.serviceCall(
            "updateLocationTrackGeometry",
            "locationTrackId" to locationTrackId, "mRange" to mRange
        )

        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        val updatedAlignment = cutLayoutGeometry(alignment, mRange)
        val updatedLocationTrack = updateTopology(locationTrack, alignment, updatedAlignment)

        return locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment).id
    }

    fun getGeometryPlanLinkStatus(planId: IntId<GeometryPlan>, publishType: PublishType): GeometryPlanLinkStatus {
        logger.serviceCall(
            "getGeometryPlanLinkStatus",
            "planId" to planId,
            "publishType" to publishType
        )
        return linkingDao.fetchPlanLinkStatus(planId = planId, publishType = publishType)
    }

    @Transactional
    fun saveKmPostLinking(kmPostLinkingParameters: KmPostLinkingParameters): DaoResponse<TrackLayoutKmPost> {
        val geometryKmPost = geometryService.getKmPost(kmPostLinkingParameters.geometryKmPostId)
        val kmPostSrid = geometryService.getKmPostSrid(kmPostLinkingParameters.geometryKmPostId)
            ?: throw IllegalArgumentException("Cannot link a geometry km post with an unknown coordinate system!")
        requireNotNull(geometryKmPost.location) { "Cannot link a geometry km post without a location!" }

        val layoutKmPost = layoutKmPostService.getDraft(kmPostLinkingParameters.layoutKmPostId)

        val newLocationInLayoutSpace =
            coordinateTransformationService.transformCoordinate(kmPostSrid, LAYOUT_SRID, geometryKmPost.location)
        val modifiedLayoutKmPost = layoutKmPost.copy(
            location = newLocationInLayoutSpace,
            sourceId = geometryKmPost.id
        )

        return layoutKmPostService.saveDraft(modifiedLayoutKmPost)
    }

}
