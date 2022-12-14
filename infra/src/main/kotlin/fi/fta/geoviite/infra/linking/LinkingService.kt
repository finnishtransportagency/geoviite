package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangulationDao
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryService
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

fun getReversedTrackPointUpdateType(trackPointUpdateType: LocationTrackPointUpdateType): LocationTrackPointUpdateType =
    if (trackPointUpdateType == LocationTrackPointUpdateType.END_POINT) LocationTrackPointUpdateType.START_POINT
    else LocationTrackPointUpdateType.END_POINT


fun removeLinkingToSwitchFromSegments(
    switchId: DomainId<TrackLayoutSwitch>,
    segments: List<LayoutSegment>,
): List<LayoutSegment> {
    return segments.map { segment ->
        if (segment.switchId == switchId) segment.copy(
            switchId = null,
            startJointNumber = null,
            endJointNumber = null
        )
        else segment
    }
}

fun getSwitchId(
    segments: List<LayoutSegment>,
    trackPointUpdateType: LocationTrackPointUpdateType,
): DomainId<TrackLayoutSwitch>? {
    return if (trackPointUpdateType == LocationTrackPointUpdateType.START_POINT) segments.first().switchId else segments.last().switchId
}

@Service
class LinkingService @Autowired constructor(
    private val geometryService: GeometryService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val layoutKmPostService: LayoutKmPostService,
    private val linkingDao: LinkingDao,
    private val kkJtoETRSTriangulationDao: KKJtoETRSTriangulationDao
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

        val geometrySegments = createLinkedSegments(parameters.geometryPlanId, parameters.geometryInterval)
        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, referenceLineId)

        val alignment = tryCreateLinkedAlignment(layoutAlignment, geometrySegments)
        return referenceLineService.saveDraft(referenceLine, alignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(parameters: EmptyAlignmentLinkingParameters<LocationTrack>): IntId<LocationTrack> {
        val locationTrackId = parameters.layoutAlignmentId

        logger.serviceCall(
            "Save empty LocationTrack linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val geometrySegments = createLinkedSegments(parameters.geometryPlanId, parameters.geometryInterval)
        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        val newAlignment = tryCreateLinkedAlignment(layoutAlignment, geometrySegments)
        val newLocationTrack = updateTopology(locationTrack, layoutAlignment, newAlignment)

        return locationTrackService.saveDraft(newLocationTrack, newAlignment).id
    }

    private fun <T> createLinkedSegments(
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
        layoutAlignment: LayoutAlignment,
        layoutInterval: LayoutInterval<T>,
    ): List<LayoutSegment> {
        val geometryAlignment = getAlignmentLayout(planId, geometryInterval.alignmentId)

        val segments = if (layoutInterval.start.point == layoutInterval.end.point) {
            extendAlignmentWithGeometry(
                layoutAlignment,
                geometryAlignment,
                layoutInterval.start.point,
                geometryInterval,
            )
        } else {
            replaceTrackLayoutGeometry(
                geometryAlignment,
                layoutAlignment,
                layoutInterval,
                geometryInterval,
            )
        }
        segments.forEachIndexed { index, segment ->
            segments.getOrNull(index - 1)?.let { previous ->
                val diff = angleDiffRads(previous.endDirection(), segment.startDirection())
                if (diff > PI / 2) throw LinkingFailureException(
                    message = "Linked geometry has over 90 degree angles between segments: " +
                            "segment=${segment.id} angle=${radsToDegrees(diff)}",
                    localizedMessageKey = "segments-sharp-angle",
                )
            }
        }
        return segments
    }

    private fun createLinkedSegments(
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
    ): List<LayoutSegment> {
        val geometryAlignment = getAlignmentLayout(planId, geometryInterval.alignmentId)

        val fromGeometryPoint = geometryInterval.start.point
        val toGeometryPoint = geometryInterval.end.point

        val geometryIndexRange = getSegmentIndexRange(geometryAlignment, geometryInterval)

        return getSegmentsBetweenPoints(
            geometryIndexRange.start,
            geometryIndexRange.endInclusive,
            getSegmentsWithoutSwitchInformation(geometryAlignment.segments),
            fromGeometryPoint,
            toGeometryPoint,
            0.0,
        )
    }

    private fun getAlignmentLayout(
        planId: IntId<GeometryPlan>,
        alignmentId: IntId<GeometryAlignment>,
    ): MapAlignment<GeometryAlignment> {
        val (geometryPlan, transformationError) = geometryService.getTrackLayoutPlan(planId)
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
        val updatedAlignment = cutAlignment(alignment, interval.start, interval.end)

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
        val updatedAlignment = cutAlignment(alignment, interval.start, interval.end)
        val updatedLocationTrack = updateTopology(locationTrack, alignment, updatedAlignment)

        return locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment).id
    }

    private fun cutAlignment(alignment: LayoutAlignment, from: IntervalLayoutPoint, to: IntervalLayoutPoint) =
        alignment.withSegments(
            getSegmentsBetweenPoints(
                alignment.getSegmentIndex(from.segmentId)
                    ?: throw LinkingFailureException("Segment (from) not found: alignment=${alignment.id} segment=${from.segmentId}"),
                alignment.getSegmentIndex(to.segmentId)
                    ?: throw LinkingFailureException("Segment (to) not found: alignment=${alignment.id} segment=${to.segmentId}"),
                alignment.segments,
                from.point,
                to.point,
                0.0,
            )
        )

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

        val transformation = Transformation.possiblyKKJToETRSTransform(kmPostSrid, LAYOUT_SRID, kkJtoETRSTriangulationDao.fetchTriangulationNetwork())

        val layoutKmPost = layoutKmPostService.getDraft(kmPostLinkingParameters.layoutKmPostId)

        val newLocationInLayoutSpace =
            transformation.transform(geometryKmPost.location)
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
