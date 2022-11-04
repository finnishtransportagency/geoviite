package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.RowVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.math.PI


const val LOCALIZATION_KEY_SHARP_ANGLE = "error.linking.segments-sharp-angle"

fun getLocationTrackEndpoints(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    bbox: BoundingBox,
): List<LocationTrackEndpoint> {
    return locationTracks.flatMap { (locationTrack, alignment) ->
        listOfNotNull(
            if (alignment.segments.isNotEmpty()
                && alignment.start != null
                && bbox.contains(alignment.start!!)
            ) {
                LocationTrackEndpoint(
                    locationTrack.id as IntId<LocationTrack>,
                    location = Point(alignment.start!!),
                    LocationTrackPointUpdateType.START_POINT,
                )
            } else null,
            if (alignment.segments.isNotEmpty()
                && alignment.end != null
                && bbox.contains(alignment.end!!)
            ) {
                LocationTrackEndpoint(
                    locationTrack.id as IntId<LocationTrack>,
                    location = Point(alignment.end!!),
                    LocationTrackPointUpdateType.END_POINT,
                )
            } else null
        )
    }
}

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

fun updateLocationTrackTypePoint(
    track: LocationTrack,
    trackPointUpdateType: LocationTrackPointUpdateType,
    point: EndPoint
): LocationTrack {
    return if (trackPointUpdateType == LocationTrackPointUpdateType.START_POINT) track.copy(startPoint = point)
    else track.copy(endPoint = point)
}

@Service
class LinkingService @Autowired constructor(
    private val geometryService: GeometryService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val layoutKmPostService: LayoutKmPostService,
    private val linkingDao: LinkingDao,
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

    fun updateEndPointLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        connectedLocationTrackId: IntId<LocationTrack>,
        endPointType: EndPointType,
        trackPointUpdateType: LocationTrackPointUpdateType,
    ): IntId<LocationTrack> {

        val (track, alignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)
        val (connectedLocationTrack, connectedAlignment) = locationTrackService.getWithAlignment(
            DRAFT,
            connectedLocationTrackId
        )

        val reversedTrackPointUpdateType = getReversedTrackPointUpdateType(trackPointUpdateType)
        val switchId = getSwitchId(alignment.segments, trackPointUpdateType)
        val connectedSwitchId = getSwitchId(connectedAlignment.segments, reversedTrackPointUpdateType)

        val segments = switchId?.let { removeLinkingToSwitchFromSegments(it, alignment.segments) }
        val connectedSegments =
            connectedSwitchId?.let { removeLinkingToSwitchFromSegments(it, connectedAlignment.segments) }

        locationTrackService.saveDraft(
            draft = updateLocationTrackTypePoint(
                track,
                trackPointUpdateType,
                EndPointLocationTrack(connectedLocationTrackId),
            ),
            alignment = alignment.copy(segments = segments ?: alignment.segments),
        )

        locationTrackService.saveDraft(
            draft = updateLocationTrackTypePoint(
                track = connectedLocationTrack,
                trackPointUpdateType = reversedTrackPointUpdateType,
                point = EndPointLocationTrack(locationTrackId),
            ),
            alignment = connectedAlignment.copy(segments = connectedSegments ?: connectedAlignment.segments)
        )

        return locationTrackId
    }


    fun updateEndPoint(
        locationTrackId: IntId<LocationTrack>,
        endPointType: EndPointType,
        trackPointUpdateType: LocationTrackPointUpdateType,
    ): IntId<LocationTrack> {
        val (track, alignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)
        val switchId = getSwitchId(alignment.segments, trackPointUpdateType)
        val segments = if (switchId != null) removeLinkingToSwitchFromSegments(
            switchId,
            alignment.segments
        ) else alignment.segments
        return locationTrackService.saveDraft(
            updateLocationTrackTypePoint(track, trackPointUpdateType, EndPointSimple(type = endPointType)),
            alignment.copy(segments = segments)
        ).id
    }


    fun saveReferenceLineLinking(linkingParameters: LinkingParameters<ReferenceLine>): RowVersion<ReferenceLine> {
        val referenceLineId = linkingParameters.layoutInterval.alignmentId
        logger.serviceCall(
            "Save ReferenceLine linking: " +
                    "geometryAlignmentId=${linkingParameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignment(DRAFT, referenceLineId)

        val segments = createLinkedSegments(
            linkingParameters.geometryPlanId,
            linkingParameters.geometryInterval,
            layoutAlignment,
            linkingParameters.layoutInterval,
        )
        return referenceLineService.saveDraft(referenceLine, tryCreateLinkedAlignment(layoutAlignment, segments))
    }

    fun saveLocationTrackLinking(linkingParameters: LinkingParameters<LocationTrack>): RowVersion<LocationTrack> {
        val locationTrackId = linkingParameters.layoutInterval.alignmentId

        logger.serviceCall(
            "Save LocationTrack linking: " +
                    "geometryAlignmentId=${linkingParameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)

        val segments = createLinkedSegments(
            linkingParameters.geometryPlanId,
            linkingParameters.geometryInterval,
            layoutAlignment,
            linkingParameters.layoutInterval,
        )

        val updatedLocationTrackEndPoint = locationTrack.copy(
            endPoint = if (layoutAlignment.end != segments.last().points.last()) null else locationTrack.endPoint,
            startPoint = if (layoutAlignment.start != segments.first().points.first()) null else locationTrack.startPoint
        )

        return locationTrackService.saveDraft(
            updatedLocationTrackEndPoint,
            tryCreateLinkedAlignment(layoutAlignment, segments)
        )
    }

    fun saveReferenceLineLinking(parameters: EmptyAlignmentLinkingParameters<ReferenceLine>): RowVersion<ReferenceLine> {
        val referenceLineId = parameters.layoutAlignmentId

        logger.serviceCall(
            "Save empty ReferenceLine linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "referenceLineId=$referenceLineId"
        )

        val geometrySegments = createLinkedSegments(parameters.geometryPlanId, parameters.geometryInterval)
        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignment(DRAFT, referenceLineId)

        return referenceLineService.saveDraft(
            referenceLine,
            tryCreateLinkedAlignment(layoutAlignment, geometrySegments),
        )
    }

    fun saveLocationTrackLinking(parameters: EmptyAlignmentLinkingParameters<LocationTrack>): RowVersion<LocationTrack> {
        val locationTrackId = parameters.layoutAlignmentId

        logger.serviceCall(
            "Save empty LocationTrack linking: " +
                    "geometryAlignmentId=${parameters.geometryInterval.alignmentId} " +
                    "locationTrackId=$locationTrackId"
        )

        val geometrySegments = createLinkedSegments(parameters.geometryPlanId, parameters.geometryInterval)
        val (locationTrack, layoutAlignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)

        return locationTrackService.saveDraft(
            locationTrack,
            tryCreateLinkedAlignment(layoutAlignment, geometrySegments),
        )
    }

    private fun <T> createLinkedSegments(
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
        layoutAlignment: LayoutAlignment,
        layoutInterval: LayoutInterval<T>,
    ): List<LayoutSegment> {
        val geometryAlignmentId = geometryInterval.alignmentId

        val geometryPlan = geometryService.getTrackLayoutPlan(planId).first
            ?: throw LinkingFailureException("Geometry plan not found: $planId")
        val geometryAlignment = geometryPlan.alignments.find { alignment -> alignment.id == geometryAlignmentId }
            ?: throw LinkingFailureException("Geometry alignment not found: $geometryAlignmentId")

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
                    localizedMessageKey = LOCALIZATION_KEY_SHARP_ANGLE,
                )
            }
        }
        return segments
    }

    private fun createLinkedSegments(
        planId: IntId<GeometryPlan>,
        geometryInterval: GeometryInterval,
    ): List<LayoutSegment> {
        val geometryAlignmentId = geometryInterval.alignmentId

        val geometryPlan = geometryService.getTrackLayoutPlan(planId).first
            ?: throw LinkingFailureException("Geometry plan not found: $planId")
        val geometryAlignment = geometryPlan.alignments.find { alignment -> alignment.id == geometryAlignmentId }
            ?: throw LinkingFailureException("Geometry alignment not found: $geometryAlignmentId")

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

    fun updateReferenceLineGeometry(
        referenceLineId: IntId<ReferenceLine>,
        interval: LayoutInterval<ReferenceLine>,
    ): RowVersion<ReferenceLine> {
        logger.serviceCall(
            "updateReferenceLineGeometry",
            "referenceLineId" to referenceLineId, "interval" to interval
        )

        val (referenceLine, alignment) = referenceLineService.getWithAlignment(DRAFT, referenceLineId)
        val updatedAlignment = cutAlignment(alignment, interval.start, interval.end)

        return referenceLineService.saveDraft(referenceLine, updatedAlignment)
    }

    fun updateLocationTrackGeometry(
        locationTrackId: IntId<LocationTrack>,
        interval: LayoutInterval<LocationTrack>,
    ): RowVersion<LocationTrack> {
        logger.serviceCall(
            "updateLocationTrackGeometry",
            "locationTrackId" to locationTrackId, "interval" to interval
        )

        val (locationTrack, alignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)
        val updatedAlignment = cutAlignment(alignment, interval.start, interval.end)

        val updatedLocationTrack = replaceLocationTrackEndPoint(locationTrack, alignment, updatedAlignment)

        return locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment)
    }

    private fun replaceLocationTrackEndPoint(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        newAlignment: LayoutAlignment
    ): LocationTrack {
        val startPoint =
            if (newAlignment.start == alignment.start) locationTrack.startPoint
            else null

        val endPoint =
            if (newAlignment.end == alignment.end) locationTrack.endPoint
            else null

        return locationTrack.copy(
            startPoint = startPoint,
            endPoint = endPoint,
        )
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

    fun getLocationTrackEndpoints(bbox: BoundingBox, publishType: PublishType): List<LocationTrackEndpoint> {
        logger.serviceCall("getLocationTrackEndpoints", "bbox" to bbox)
        return getLocationTrackEndpoints(locationTrackService.listWithAlignments(publishType), bbox)
    }

    fun saveKmPostLinking(kmPostLinkingParameters: KmPostLinkingParameters) {
        val geometryKmPost = geometryService.getKmPost(kmPostLinkingParameters.geometryKmPostId)
        val kmPostSrid = geometryService.getKmPostSrid(kmPostLinkingParameters.geometryKmPostId)
            ?: throw IllegalArgumentException("Cannot link a geometry km post with an unknown coordinate system!")
        requireNotNull(geometryKmPost.location) { "Cannot link a geometry km post without a location!" }

        val transformation = Transformation(kmPostSrid, LAYOUT_SRID)

        val layoutKmPost = layoutKmPostService.getDraft(kmPostLinkingParameters.layoutKmPostId)

        val newLocationInLayoutSpace =
            transformation.transform(geometryKmPost.location)
        val modifiedLayoutKmPost = layoutKmPost.copy(
            location = newLocationInLayoutSpace,
            sourceId = geometryKmPost.id
        )

        layoutKmPostService.saveDraft(modifiedLayoutKmPost)
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
                "Linking selection produces invalid alignment",
                e,
                "error.linking.alignment-geometry"
            )
        }
    }

}
