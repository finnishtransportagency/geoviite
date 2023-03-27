package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.PI


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
    fun saveReferenceLineLinking(linkingParameters: LinkingParameters<ReferenceLine>): IntId<ReferenceLine> {
        val referenceLineId = linkingParameters.layoutInterval.alignmentId
        logger.serviceCall(
            "Save ReferenceLine linking: " +
                    "geometryAlignmentId=${linkingParameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)

        val segments = createLinkedSegments(
            linkingParameters.geometryPlanId,
            linkingParameters.geometryInterval,
            layoutAlignment,
            linkingParameters.layoutInterval,
        )
        val alignment = tryCreateLinkedAlignment(layoutAlignment, segments)
        return referenceLineService.saveDraft(referenceLine, alignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(linkingParameters: LinkingParameters<LocationTrack>): IntId<LocationTrack> {
        val locationTrackId = linkingParameters.layoutInterval.alignmentId

        logger.serviceCall(
            "Save LocationTrack linking: " +
                    "geometryAlignmentId=${linkingParameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)

        val segments = createLinkedSegments(
            linkingParameters.geometryPlanId,
            linkingParameters.geometryInterval,
            layoutAlignment,
            linkingParameters.layoutInterval,
        )
        val newAlignment = tryCreateLinkedAlignment(layoutAlignment, segments)
        val newLocationTrack = updateTopology(locationTrack, layoutAlignment, newAlignment)

        return locationTrackService.saveDraft(newLocationTrack, newAlignment).id
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

        logger.serviceCall(
            "Save empty ReferenceLine linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)

        val newAlignment = createAlignment(layoutAlignment, parameters.geometryPlanId, parameters.geometryInterval)

        return referenceLineService.saveDraft(referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(parameters: EmptyAlignmentLinkingParameters<LocationTrack>): IntId<LocationTrack> {
        val locationTrackId = parameters.layoutAlignmentId

        logger.serviceCall(
            "Save empty LocationTrack linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)

        val newAlignment = createAlignment(layoutAlignment, parameters.geometryPlanId, parameters.geometryInterval)
        val newLocationTrack = updateTopology(locationTrack, layoutAlignment, newAlignment)

        return locationTrackService.saveDraft(newLocationTrack, newAlignment).id
    }

    private fun createAlignment(
        layoutAlignment: LayoutAlignment,
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
    ): LayoutAlignment {
        val mapAlignment = getAlignmentLayout(planId, geometryInterval.alignmentId)
        val geometrySegments = createAlignmentGeometry(mapAlignment, geometryInterval.mRange)
        return tryCreateLinkedAlignment(layoutAlignment, geometrySegments)
    }

    private fun <T> createLinkedSegments(
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
        layoutAlignment: LayoutAlignment,
        layoutInterval: LayoutInterval<T>,
    ): List<LayoutSegment> {
        val geometryAlignment = getAlignmentLayout(planId, geometryInterval.alignmentId)
        val segments = if (layoutInterval.mRange.min == layoutInterval.mRange.max) {
            extendAlignmentWithGeometry(
                layoutAlignment = layoutAlignment,
                geometryAlignment = geometryAlignment,
                layoutM = layoutInterval.mRange.min,
                geometryMInterval = geometryInterval.mRange,
            )
        } else {
            replaceTrackLayoutGeometry(
                geometryAlignment = geometryAlignment,
                layoutAlignment = layoutAlignment,
                layoutMInterval = layoutInterval.mRange,
                geometryMInterval = geometryInterval.mRange,
            )
        }
        return segments
    }

    private fun getAlignmentLayout(
        planId: IntId<GeometryPlan>,
        alignmentId: IntId<GeometryAlignment>,
    ): MapAlignment<GeometryAlignment> {
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
        interval: LayoutInterval<ReferenceLine>,
    ): IntId<ReferenceLine> {
        logger.serviceCall(
            "updateReferenceLineGeometry",
            "referenceLineId" to referenceLineId, "interval" to interval
        )

        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)
        val updatedAlignment = cutAlignment(alignment, interval.mRange)

        return referenceLineService.saveDraft(referenceLine, updatedAlignment).id
    }

    @Transactional
    fun updateLocationTrackGeometry(
        locationTrackId: IntId<LocationTrack>,
        interval: LayoutInterval<LocationTrack>,
    ): IntId<LocationTrack> {
        logger.serviceCall(
            "updateLocationTrackGeometry",
            "locationTrackId" to locationTrackId, "interval" to interval
        )

        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        val updatedAlignment = cutAlignment(alignment, interval.mRange)
        val updatedLocationTrack = updateTopology(locationTrack, alignment, updatedAlignment)

        return locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment).id
    }

    private fun cutAlignment(alignment: LayoutAlignment, mRange: Range<Double>): LayoutAlignment {
        val cutSegments = sliceSegments(alignment.segments, mRange, ALIGNMENT_LINKING_SNAP)
        val newSegments = removeSwitches(cutSegments, getSwitchIdsOutside(alignment.segments, mRange))
        return tryCreateLinkedAlignment(alignment, newSegments)
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

    private fun tryCreateLinkedAlignment(
        original: LayoutAlignment,
        newSegments: List<LayoutSegment>,
    ): LayoutAlignment {
        newSegments.forEachIndexed { index, segment ->
            newSegments.getOrNull(index - 1)?.let { previous ->
                val diff = angleDiffRads(previous.endDirection, segment.startDirection)
                if (diff > PI / 2) throw LinkingFailureException(
                    message = "Linked geometry has over 90 degree angles between segments: " +
                            "segment=${segment.id} m=${previous.endM} angle=${radsToDegrees(diff)}",
                    localizedMessageKey = "segments-sharp-angle",
                )
            }
        }
        return try {
            original.withSegments(newSegments)
        } catch (e: IllegalArgumentException) {
            logger.warn("Linking selection produces invalid alignment: ${e.message}")
            throw LinkingFailureException(
                message = "Linking selection produces invalid alignment",
                cause = e,
                localizedMessageKey = "alignment-geometry"
            )
        }
    }

}
