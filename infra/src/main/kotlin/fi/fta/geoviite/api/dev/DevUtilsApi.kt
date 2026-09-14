package fi.fta.geoviite.api.dev

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import fi.fta.geoviite.infra.aspects.AtLeastOneProfile
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.degreesToRads
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.ContextCache
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackSpatialCache
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

const val BASE_PATH = "/geoviite"
const val DEV_API_TAG = "dev"
private const val SKIP_PARAM = "skip"
private const val TAKE_PARAM = "take"
private const val MINIMUM_DIRECTIONAL_PROXIMITY_LENGTH = 20.0
private const val MAX_DIRECTION_DIFFERENCE_DEGREES = 15.0
private const val DISTANCE_FROM_REFERENCE_LINE_TOLERANCE = 0.1
private val MINIMUM_DIRECTION_COSINE = cos(degreesToRads(MAX_DIRECTION_DIFFERENCE_DEGREES))

@AtLeastOneProfile("dev")
@GeoviiteExtApiController(["$BASE_PATH/dev/utils"])
class DevUtilsApi(
    private val layoutTrackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
) {

    /**
     * Lists the location tracks that each reference line follows, as `tracknumber;name;oid` rows.
     *
     * Going through every reference line takes long enough that the run is better done in batches. [SKIP_PARAM] and
     * [TAKE_PARAM] select a slice of the reference lines, which are ordered by track number so that the slices stay
     * stable between requests and batches can simply be concatenated.
     */
    @GetMapping(
        value = ["/pituusmittauslinjatjasijaintiraiteet"],
        produces = ["text/plain"],
    )
    fun findReferenceLinesAndLocationTracks(
        @RequestParam(SKIP_PARAM, required = false, defaultValue = "0") skip: Int,
        @RequestParam(TAKE_PARAM, required = false) take: Int?,
    ): ResponseEntity<String> {
        if (skip < 0) return badRequest("Parameter '$SKIP_PARAM' must not be negative, but was $skip")
        if (take != null && take < 0) return badRequest("Parameter '$TAKE_PARAM' must not be negative, but was $take")

        val layoutContext = MainLayoutContext.official
        val spatialCache = locationTrackSpatialCache.get(layoutContext)
        val result = StringBuilder()

        layoutTrackNumberService
            .listWithGeometries(layoutContext)
            .sortedWith(compareBy({ (ltn, _) -> ltn.number }, { (ltn, _) -> ltn.id.toString() }))
            .asSequence()
            .drop(skip)
            .let { referenceLines -> take?.let(referenceLines::take) ?: referenceLines }
            .forEach { (ltn, referenceLine) ->
                val locationTracks =
                    findLocationTracksIntersectingReferenceLine(
                        referenceLine = referenceLine,
                        spatialCache = spatialCache,
                        tolerance = DISTANCE_FROM_REFERENCE_LINE_TOLERANCE,
                    )
                val oids =
                    locationTrackService.getExternalIds(
                        layoutContext.branch,
                        locationTracks.map { locationTrack -> locationTrack.id as IntId },
                    )
                locationTracks.forEach { locationTrack ->
                    result.append(ltn.number.value).append(';').append(locationTrack.name).append(';')
                    result.append(oids[locationTrack.id] ?: "").append('\n')
                }
            }

        return ResponseEntity.ok().contentType(MediaType("text", "plain")).body(result.toString())
    }
}

private fun badRequest(message: String): ResponseEntity<String> =
    ResponseEntity.badRequest().contentType(MediaType("text", "plain")).body("$message\n")

/**
 * Finds the location tracks that the reference line actually follows.
 *
 * A track qualifies when it stays within [tolerance] of the reference line, in a matching direction, over an unbroken
 * stretch of at least [MINIMUM_DIRECTIONAL_PROXIMITY_LENGTH]. Where several tracks are coincident with the reference
 * line (typically around switches), each reference line edge is owned by the track whose unbroken coincident run is
 * longest, so a short track that merely shares geometry with the real one is not reported.
 */
private fun findLocationTracksIntersectingReferenceLine(
    referenceLine: IAlignment<*>,
    spatialCache: ContextCache,
    tolerance: Double,
): List<LocationTrack> = selectOwningTracks(collectReferenceEdgeMatches(referenceLine, spatialCache, tolerance))

private fun collectReferenceEdgeMatches(
    referenceLine: IAlignment<*>,
    spatialCache: ContextCache,
    tolerance: Double,
): ReferenceEdgeMatches {
    val candidatesByTrackId = mutableMapOf<DomainId<LocationTrack>, TrackProximityCandidate>()
    val edgeLengths = mutableListOf<Double>()
    val matchesPerEdge = mutableListOf<List<TrackEdgeMatch>>()
    val runLengths = mutableListOf<Double>()

    referenceLine.segments.forEach { referenceSegment ->
        val candidates =
            spatialCache.getTracksWithSegmentsInBoundingBox(referenceSegment.boundingBox + tolerance).map {
                (track, geometry) ->
                candidatesByTrackId.getOrPut(track.id) { TrackProximityCandidate(track, geometry) }
            }

        referenceSegment.segmentPoints.forEachConsecutive { referenceStart, referenceEnd ->
            val edgeIndex = edgeLengths.size
            val edgeLength = lineLength(referenceStart, referenceEnd)
            val edgeBoundingBox = boundingBoxAroundPoints(listOf(referenceStart, referenceEnd)) + tolerance

            val matches = candidates.mapNotNull { candidate ->
                candidate.matchDistance(edgeBoundingBox, referenceStart, referenceEnd, tolerance)?.let { distance ->
                    val runIndex =
                        if (candidate.lastMatchedEdgeIndex == edgeIndex - 1) candidate.currentRunIndex
                        else runLengths.size.also { runLengths.add(0.0) }
                    candidate.lastMatchedEdgeIndex = edgeIndex
                    candidate.currentRunIndex = runIndex
                    runLengths[runIndex] += edgeLength
                    TrackEdgeMatch(candidate, distance, runIndex)
                }
            }

            edgeLengths.add(edgeLength)
            matchesPerEdge.add(matches)
        }
    }
    return ReferenceEdgeMatches(edgeLengths.toDoubleArray(), matchesPerEdge, runLengths.toDoubleArray())
}

private fun selectOwningTracks(edgeMatches: ReferenceEdgeMatches): List<LocationTrack> {
    val owningTracks = mutableListOf<LocationTrack>()
    val reportedTrackIds = mutableSetOf<DomainId<LocationTrack>>()
    var currentOwner: TrackProximityCandidate? = null
    var ownedLength = 0.0

    edgeMatches.matchesPerEdge.forEachIndexed { edgeIndex, matches ->
        val owner =
            matches
                .maxWithOrNull(
                    compareBy<TrackEdgeMatch> { edgeMatches.runLengths[it.runIndex] }
                        .thenByDescending(TrackEdgeMatch::distance)
                )
                ?.candidate

        ownedLength =
            when {
                owner == null -> 0.0
                owner === currentOwner -> ownedLength + edgeMatches.edgeLengths[edgeIndex]
                else -> edgeMatches.edgeLengths[edgeIndex]
            }
        currentOwner = owner

        if (
            owner != null && ownedLength >= MINIMUM_DIRECTIONAL_PROXIMITY_LENGTH && reportedTrackIds.add(owner.track.id)
        ) {
            owningTracks.add(owner.track)
        }
    }
    return owningTracks
}

private fun TrackProximityCandidate.matchDistance(
    edgeBoundingBox: BoundingBox,
    referenceStart: IPoint,
    referenceEnd: IPoint,
    tolerance: Double,
): Double? =
    geometry.segments
        .asSequence()
        .filter { trackSegment -> trackSegment.boundingBox.intersects(edgeBoundingBox) }
        .flatMap { trackSegment ->
            trackSegment.segmentPoints.zipWithNext().asSequence().map { (trackStart, trackEnd) ->
                lineSegmentDistanceWithin(referenceStart, referenceEnd, trackStart, trackEnd, tolerance)
            }
        }
        .filterNotNull()
        .minOrNull()

private fun lineSegmentDistanceWithin(
    referenceStart: IPoint,
    referenceEnd: IPoint,
    trackStart: IPoint,
    trackEnd: IPoint,
    tolerance: Double,
): Double? =
    if (
        !directionsMatch(referenceStart, referenceEnd, trackStart, trackEnd) ||
            lineBoundingBoxDistanceSquared(referenceStart, referenceEnd, trackStart, trackEnd) > tolerance * tolerance
    ) {
        null
    } else {
        lineSegmentDistanceSquared(referenceStart, referenceEnd, trackStart, trackEnd)
            .takeIf { distanceSquared -> distanceSquared <= tolerance * tolerance }
            ?.let(::sqrt)
    }

private fun lineSegmentDistanceSquared(
    firstStart: IPoint,
    firstEnd: IPoint,
    secondStart: IPoint,
    secondEnd: IPoint,
): Double =
    if (lineIntersection(firstStart, firstEnd, secondStart, secondEnd)?.linesIntersect() == true) 0.0
    else
        min(
            min(
                pointDistanceSquaredToLine(firstStart, firstEnd, secondStart),
                pointDistanceSquaredToLine(firstStart, firstEnd, secondEnd),
            ),
            min(
                pointDistanceSquaredToLine(secondStart, secondEnd, firstStart),
                pointDistanceSquaredToLine(secondStart, secondEnd, firstEnd),
            ),
        )

private fun pointDistanceSquaredToLine(lineStart: IPoint, lineEnd: IPoint, point: IPoint): Double {
    val lineX = lineEnd.x - lineStart.x
    val lineY = lineEnd.y - lineStart.y
    val pointX = point.x - lineStart.x
    val pointY = point.y - lineStart.y
    val lineLengthSquared = lineX * lineX + lineY * lineY
    val linePosition = ((pointX * lineX + pointY * lineY) / lineLengthSquared).coerceIn(0.0, 1.0)
    val distanceX = pointX - linePosition * lineX
    val distanceY = pointY - linePosition * lineY
    return distanceX * distanceX + distanceY * distanceY
}

private fun lineBoundingBoxDistanceSquared(
    firstStart: IPoint,
    firstEnd: IPoint,
    secondStart: IPoint,
    secondEnd: IPoint,
): Double =
    boundingBoxDistanceSquared(
        min(firstStart.x, firstEnd.x),
        maxOf(firstStart.x, firstEnd.x),
        min(firstStart.y, firstEnd.y),
        maxOf(firstStart.y, firstEnd.y),
        min(secondStart.x, secondEnd.x),
        maxOf(secondStart.x, secondEnd.x),
        min(secondStart.y, secondEnd.y),
        maxOf(secondStart.y, secondEnd.y),
    )

private fun boundingBoxDistanceSquared(
    firstMinX: Double,
    firstMaxX: Double,
    firstMinY: Double,
    firstMaxY: Double,
    secondMinX: Double,
    secondMaxX: Double,
    secondMinY: Double,
    secondMaxY: Double,
): Double {
    val distanceX = maxOf(firstMinX - secondMaxX, secondMinX - firstMaxX, 0.0)
    val distanceY = maxOf(firstMinY - secondMaxY, secondMinY - firstMaxY, 0.0)
    return distanceX * distanceX + distanceY * distanceY
}

private fun directionsMatch(
    firstStart: IPoint,
    firstEnd: IPoint,
    secondStart: IPoint,
    secondEnd: IPoint,
): Boolean {
    val firstX = firstEnd.x - firstStart.x
    val firstY = firstEnd.y - firstStart.y
    val secondX = secondEnd.x - secondStart.x
    val secondY = secondEnd.y - secondStart.y
    val directionDotProduct = firstX * secondX + firstY * secondY
    val directionMagnitudeProduct = sqrt((firstX * firstX + firstY * firstY) * (secondX * secondX + secondY * secondY))
    return kotlin.math.abs(directionDotProduct) >= MINIMUM_DIRECTION_COSINE * directionMagnitudeProduct
}

private class TrackProximityCandidate(val track: LocationTrack, val geometry: DbLocationTrackGeometry) {
    var lastMatchedEdgeIndex: Int = Int.MIN_VALUE
    var currentRunIndex: Int = -1
}

private class TrackEdgeMatch(
    val candidate: TrackProximityCandidate,
    val distance: Double,
    val runIndex: Int,
)

private class ReferenceEdgeMatches(
    val edgeLengths: DoubleArray,
    val matchesPerEdge: List<List<TrackEdgeMatch>>,
    val runLengths: DoubleArray,
)

private fun <T> List<T>.forEachConsecutive(action: (T, T) -> Unit) =
    zipWithNext().forEach { (first, second) -> action(first, second) }
