package fi.fta.geoviite.infra.geocoding

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DEFAULT_TRACK_METER_DECIMALS
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.GeocodingFailureException
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.AFTER
import fi.fta.geoviite.infra.math.IntersectType.BEFORE
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.Intersection
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleAvgRads
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.interpolateAngleRads
import fi.fta.geoviite.infra.math.interpolateToAlignmentPoint
import fi.fta.geoviite.infra.math.interpolateToPoint
import fi.fta.geoviite.infra.math.interpolateToSegmentPoint
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.AnyM
import fi.fta.geoviite.infra.tracklayout.GeocodingAlignmentM
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.abs
import fi.fta.geoviite.infra.tracklayout.segmentToAlignmentM
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.Left
import fi.fta.geoviite.infra.util.Right
import fi.fta.geoviite.infra.util.getIndexRangeForRangeInOrderedList
import fi.fta.geoviite.infra.util.processRights
import fi.fta.geoviite.infra.util.processSortedBy
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class AddressPoint<M : AnyM<M>>(val point: AlignmentPoint<M>, val address: TrackMeter) {
    fun isSame(other: AddressPoint<M>) = address.isSame(other.address) && point.isSame(other.point)

    fun withIntegerPrecision(): AddressPoint<M>? =
        if (address.hasIntegerPrecision()) {
            this
        } else if (address.matchesIntegerValue()) {
            AddressPoint(point = point, address = address.floor())
        } else {
            null
        }
}

data class AlignmentAddresses<M : AnyM<M>>(
    val startPoint: AddressPoint<M>,
    val endPoint: AddressPoint<M>,
    val startIntersect: IntersectType,
    val endIntersect: IntersectType,
    val midPoints: List<AddressPoint<M>>,
    val alignmentWalkFinished: Boolean,
) {
    @get:JsonIgnore
    val allPoints: List<AddressPoint<M>> by lazy { emptyList<AddressPoint<M>>() + startPoint + midPoints + endPoint }

    @get:JsonIgnore
    val integerPrecisionPoints: List<AddressPoint<M>> by lazy {
        // midPoints are even anyhow, so just drop zero decimals from start/end
        val start =
            startPoint.withIntegerPrecision()?.takeIf { s ->
                midPoints.isEmpty() || s.address < midPoints.first().address
            }
        val end =
            endPoint.withIntegerPrecision()?.takeIf { e ->
                (start == null || e.address > start.address) &&
                    (midPoints.isEmpty() || e.address > midPoints.last().address)
            }
        listOfNotNull(start) + midPoints + listOfNotNull(end)
    }
}

data class AlignmentStartAndEnd<T>(
    val id: IntId<T>,
    val start: AlignmentEndPoint?,
    val end: AlignmentEndPoint?,
    val staStart: Double?,
) {
    companion object {
        fun <T, M : AlignmentM<M>, G : GeocodingAlignmentM<G>> of(
            id: IntId<T>,
            alignment: IAlignment<M>,
            geocodingContext: GeocodingContext<G>?,
        ): AlignmentStartAndEnd<T> {
            val start =
                alignment.start?.let { p ->
                    AlignmentEndPoint(p, geocodingContext?.getAddress(p)?.let(::getAddressIfIWithin))
                }
            val end =
                alignment.end?.let { p ->
                    AlignmentEndPoint(p, geocodingContext?.getAddress(p)?.let(::getAddressIfIWithin))
                }
            return AlignmentStartAndEnd(id, start, end, (alignment as? PlanLayoutAlignment)?.staStart)
        }
    }
}

data class AlignmentEndPoint(val point: AlignmentPoint<*>, val address: TrackMeter?)

data class ProjectionLine<M : GeocodingAlignmentM<M>>(
    val address: TrackMeter,
    val projection: Line,
    val distance: LineM<M>,
    val referenceDirection: Double,
)

data class GeocodingReferencePoint<M : GeocodingAlignmentM<M>>(
    val kmNumber: KmNumber,
    val meters: BigDecimal,
    val distance: LineM<M>,
    val kmPostOffset: Double,
    val intersectType: IntersectType,
) {
    val distanceRounded = roundTo3Decimals(distance.distance)
    val address = TrackMeter(kmNumber, meters)
}

data class AddressAndM(
    val address: TrackMeter,
    val m: LineM<out GeocodingAlignmentM<*>>,
    val intersectType: IntersectType,
)

/**
 * Don't generate a meter that is shorter than this. Prevents an extra projection being generated when the KM changes on
 * an exact meter.
 */
private const val MIN_METER_LENGTH = 0.001

/**
 * Projection line validation parameter. They should be 1m apart from each other along reference line by definition.
 * This is the maximum deviation allowed - must accommodate some difference for turns.
 */
private const val PROJECTION_LINE_DISTANCE_DEVIATION = 0.05

/**
 * Projection line validation parameter. If the line makes rough turns, the projections will result in convoluted
 * addresses. This is the maximum angle-change between 2 projection points (1m on reference line).
 */
private const val PROJECTION_LINE_MAX_ANGLE_DELTA = PI / 16

private val logger: Logger = LoggerFactory.getLogger(GeocodingContext::class.java)

data class KmPostWithRejectedReason(val kmPost: LayoutKmPost, val rejectedReason: KmPostRejectedReason)

data class GeocodingContextCreateResult<M : GeocodingAlignmentM<M>>(
    val geocodingContext: GeocodingContext<M>,
    val rejectedKmPosts: List<KmPostWithRejectedReason>,
    val validKmPosts: List<LayoutKmPost>,
    val startPointRejectedReason: StartPointRejectedReason?,
)

enum class StartPointRejectedReason {
    TOO_LONG
}

enum class KmPostRejectedReason {
    TOO_FAR_APART,
    NO_LOCATION,
    IS_BEFORE_START_ADDRESS,
    INTERSECTS_BEFORE_REFERENCE_LINE,
    INTERSECTS_AFTER_REFERENCE_LINE,
    DUPLICATE,
}

enum class Resolution(val meters: Number) {
    ONE_METER(1),
    QUARTER_METER(BigDecimal("0.25")),
}

data class GeocodingContext<M : GeocodingAlignmentM<M>>(
    val trackNumber: TrackNumber,
    val referenceLineGeometry: IAlignment<M>,
    val referencePoints: List<GeocodingReferencePoint<M>>,
    val projectionLineDistanceDeviation: Double = PROJECTION_LINE_DISTANCE_DEVIATION,
    val projectionLineMaxAngleDelta: Double = PROJECTION_LINE_MAX_ANGLE_DELTA,
) {

    init {
        require(referenceLineGeometry.segments.isNotEmpty()) {
            "Cannot geocode with empty reference line geometry, trackNumber=${trackNumber}"
        }

        require(referencePoints.isNotEmpty()) { "Cannot geocode without reference points, trackNumber=${trackNumber}" }

        require(
            referencePoints
                .zipWithNext { a, b -> abs(b.distance.distance - a.distance.distance) }
                .all { TrackMeter.isMetersValid(it) }
        ) {
            "Reference points are too far apart from each other, trackNumber=${trackNumber}"
        }
    }

    private val polyLineEdges: List<PolyLineEdge<M>> by lazy { getPolyLineEdges(referenceLineGeometry) }

    val projectionLines: Map<Resolution, Lazy<List<ProjectionLine<M>>>> =
        enumValues<Resolution>().associateWith { resolution -> lazy { createProjectionLines(resolution) } }

    private fun createProjectionLines(resolution: Resolution): List<ProjectionLine<M>> {
        require(isSame(polyLineEdges.last().endM, referenceLineGeometry.length, LAYOUT_M_DELTA)) {
            "Polyline edges should cover the whole reference line geometry: " +
                "trackNumber=$trackNumber " +
                "referenceLineGeometry=$referenceLineGeometry " +
                "edgeMValues=${polyLineEdges.map { e -> e.startM..e.endM }}"
        }
        // TODO: GVT-1727 The validation claims to filter out bad projections, but we use the un-filtered here
        return createProjectionLines(referencePoints, polyLineEdges, resolution).also { lines ->
            validateProjectionLines(
                trackNumber,
                lines,
                projectionLineDistanceDeviation,
                projectionLineMaxAngleDelta,
                resolution,
            )
        }
    }

    val allKms: List<KmNumber> by lazy { referencePoints.map(GeocodingReferencePoint<M>::kmNumber).distinct() }
    val kmRange: Range<KmNumber>? by lazy {
        referencePoints.takeIf { it.isNotEmpty() }?.let { Range(it.first().kmNumber, it.last().kmNumber) }
    }

    val meterProjectionLineIntersections: List<Intersection?> by lazy {
        projectionLines.getValue(Resolution.ONE_METER).value.zipWithNext { left, right ->
            lineIntersection(left.projection, right.projection)
        }
    }

    val startAddress: TrackMeter = referencePoints.first().address

    val endAddress: TrackMeter? =
        (referenceLineGeometry.length.distance -
                referencePoints.last().let { p -> p.distance.distance - p.meters.toDouble() })
            .takeIf(TrackMeter::isMetersValid)
            ?.let { meters -> TrackMeter(referencePoints.last().kmNumber, meters, startAddress.decimalCount()) }

    fun preload() {
        // Preload the lazy properties
        polyLineEdges
        // Skip preloading projectionlines as the benefit is quite limited and it's costly at startup
        allKms
    }

    fun getProjectionLine(address: TrackMeter): ProjectionLine<M>? = getProjectionLineInContext(address)?.projection

    private fun getProjectionLineInContext(address: TrackMeter): ProjectionLineInContext<M>? =
        when (val surrounding = getSurroundingProjectionLines(address)) {
            is PastReferenceLineEnd -> null
            is ExactHit ->
                ProjectionLineInContext(useAddressPrecision(surrounding.projectionLine, address), surrounding.index)
            is ProjectionLineInterval ->
                ProjectionLineInContext(
                    interpolateProjectionLine(address, surrounding.left, surrounding.right),
                    surrounding.leftIndex,
                )
        }

    private fun getSurroundingProjectionLines(address: TrackMeter): SurroundingProjectionLines<M> {
        val extrapolatedStart = extrapolatedBeforeStartProjectionLine
        val extrapolatedEnd = extrapolatedAfterEndProjectionLine
        val projectionLines = projectionLines.getValue(Resolution.ONE_METER).value
        return if (projectionLines.isEmpty() || extrapolatedStart == null || extrapolatedEnd == null)
            PastReferenceLineEnd()
        else
            projectionLines
                .binarySearch { line -> line.address.compareTo(address) }
                .let { ix ->
                    if (ix == -1) ProjectionLineInterval(extrapolatedStart, projectionLines.first(), -1)
                    else if (ix == -(projectionLines.size + 1))
                        ProjectionLineInterval(projectionLines.last(), extrapolatedEnd, projectionLines.lastIndex)
                    else if (ix >= 0) ExactHit(projectionLines[ix], ix)
                    else ProjectionLineInterval(projectionLines[-(ix + 2)], projectionLines[-(ix + 1)], -(ix + 2))
                }
    }

    private fun getNextSurroundingProjectionLines(
        here: SurroundingProjectionLines<M>,
        stepDirection: StepDirection,
    ): SurroundingProjectionLines<M>? {
        val diff = stepDirection.diff
        val extrapolatedStart = extrapolatedBeforeStartProjectionLine
        val extrapolatedEnd = extrapolatedAfterEndProjectionLine
        val projectionLines = projectionLines.getValue(Resolution.ONE_METER).value

        return if (projectionLines.isEmpty() || extrapolatedStart == null || extrapolatedEnd == null)
            PastReferenceLineEnd()
        else
            when (here) {
                is PastReferenceLineEnd -> null
                is ExactHit -> {
                    val index = here.index
                    if (index == 0 && stepDirection == StepDirection.Backward) {
                        ProjectionLineInterval(extrapolatedStart, projectionLines.first(), -1)
                    } else if (index == projectionLines.lastIndex && stepDirection == StepDirection.Forward) {
                        ProjectionLineInterval(projectionLines.last(), extrapolatedEnd, index)
                    } else if (stepDirection == StepDirection.Forward) {
                        ProjectionLineInterval(projectionLines[index], projectionLines[index + 1], index)
                    } else {
                        ProjectionLineInterval(projectionLines[index - 1], projectionLines[index], index - 1)
                    }
                }
                is ProjectionLineInterval -> {
                    val leftIndex = here.leftIndex
                    if (leftIndex == -1 && stepDirection == StepDirection.Backward) {
                        null
                    } else if (leftIndex == 0 && stepDirection == StepDirection.Backward) {
                        ProjectionLineInterval(extrapolatedStart, projectionLines.first(), -1)
                    } else if (leftIndex >= projectionLines.lastIndex && stepDirection == StepDirection.Forward) {
                        null
                    } else if (leftIndex == projectionLines.lastIndex - 1 && stepDirection == StepDirection.Forward) {
                        ProjectionLineInterval(projectionLines.last(), extrapolatedEnd, projectionLines.lastIndex)
                    } else {
                        val ix = leftIndex + diff
                        ProjectionLineInterval(projectionLines[ix], projectionLines[ix + 1], ix)
                    }
                }
            }
    }

    val extrapolatedBeforeStartProjectionLine by lazy {
        if (projectionLines.isEmpty()) null
        else {
            val start = projectionLines.getValue(Resolution.ONE_METER).value.first()
            val startDir = referenceLineGeometry.segments.firstOrNull()?.startDirection ?: return@lazy null
            ProjectionLine<M>(
                start.address,
                start.projection.move(pointInDirection(distance = -1.0, direction = startDir)),
                LineM(-1.0),
                start.referenceDirection,
            )
        }
    }

    val extrapolatedAfterEndProjectionLine by lazy {
        if (projectionLines.isEmpty()) null
        else {
            val end = projectionLines.getValue(Resolution.ONE_METER).value.last()
            val endDir = referenceLineGeometry.segments.lastOrNull()?.endDirection ?: return@lazy null
            ProjectionLine(
                end.address,
                end.projection.move(pointInDirection(distance = 1.0, direction = endDir)),
                referenceLineGeometry.length + 1.0,
                end.referenceDirection,
            )
        }
    }

    companion object {
        fun <M : GeocodingAlignmentM<M>> create(
            trackNumber: TrackNumber,
            startAddress: TrackMeter,
            referenceLineGeometry: IAlignment<M>,
            kmPosts: List<LayoutKmPost>,
        ): GeocodingContextCreateResult<M> {
            val (validatedKmPosts, invalidKmPosts) = validateKmPosts(kmPosts, startAddress)

            val (validReferencePoints, kmPostsOutsideGeometry) =
                createReferencePoints(startAddress, validatedKmPosts, referenceLineGeometry)

            val validKmPosts =
                validatedKmPosts.filterNot { vkp -> kmPostsOutsideGeometry.any { kp -> kp.kmPost.id == vkp.id } }

            val startKmIsTooLong = startKmIsTooLong(startAddress, referenceLineGeometry, validReferencePoints)

            return GeocodingContextCreateResult(
                geocodingContext =
                    GeocodingContext(
                        trackNumber = trackNumber,
                        referenceLineGeometry = referenceLineGeometry,
                        referencePoints = validReferencePoints,
                    ),
                rejectedKmPosts = invalidKmPosts + kmPostsOutsideGeometry,
                validKmPosts = validKmPosts,
                startPointRejectedReason = if (startKmIsTooLong) StartPointRejectedReason.TOO_LONG else null,
            )
        }

        private fun <M : GeocodingAlignmentM<M>> startKmIsTooLong(
            startAddress: TrackMeter,
            referenceLineGeometry: IAlignment<M>,
            referencePoints: List<GeocodingReferencePoint<M>>,
        ): Boolean =
            if (referencePoints.isEmpty()) false
            else {
                val startMeters =
                    referencePoints[0].distance + startAddress.meters.setScale(0, RoundingMode.CEILING).toDouble()
                val length = if (referencePoints.size > 1) referencePoints[1].distance else referenceLineGeometry.length
                !TrackMeter.isMetersValid(startMeters + length)
            }

        private fun <M : GeocodingAlignmentM<M>> createReferencePoints(
            startAddress: TrackMeter,
            kmPosts: List<LayoutKmPost>,
            referenceLineGeometry: IAlignment<M>,
        ): Pair<List<GeocodingReferencePoint<M>>, List<KmPostWithRejectedReason>> {
            val kpReferencePoints =
                kmPosts.mapNotNull { post ->
                    post.layoutLocation?.let { location ->
                        toReferencePoint(location, post.kmNumber, referenceLineGeometry)
                    }
                }

            val firstPoint =
                GeocodingReferencePoint<M>(startAddress.kmNumber, startAddress.meters, LineM(0.0), 0.0, WITHIN)
            val referencePoints = listOf(firstPoint) + kpReferencePoints

            return validateReferencePoints(referencePoints, kmPosts)
        }

        private fun validateKmPosts(
            kmPosts: List<LayoutKmPost>,
            startAddress: TrackMeter,
        ): Pair<List<LayoutKmPost>, List<KmPostWithRejectedReason>> {
            val (withoutLocations, withLocations) = kmPosts.partition { it.layoutLocation == null }

            val (invalidStartAddresses, validKmPosts) =
                withLocations.partition { TrackMeter(it.kmNumber, BigDecimal.ZERO) <= startAddress }

            val duplicateKmPosts = kmPosts.groupBy { it.kmNumber }.filter { it.value.size > 1 }.values.flatten()

            val rejectedKmPosts =
                withoutLocations.map { it to KmPostRejectedReason.NO_LOCATION } +
                    invalidStartAddresses.map { it to KmPostRejectedReason.IS_BEFORE_START_ADDRESS } +
                    duplicateKmPosts.map { it to KmPostRejectedReason.DUPLICATE }

            return validKmPosts to rejectedKmPosts.map { (kp, reason) -> KmPostWithRejectedReason(kp, reason) }
        }

        private fun <M : GeocodingAlignmentM<M>> validateReferencePoints(
            referencePoints: List<GeocodingReferencePoint<M>>,
            kmPosts: List<LayoutKmPost>,
        ): Pair<List<GeocodingReferencePoint<M>>, List<KmPostWithRejectedReason>> {
            val (withinPoints, beforePoints, afterPoints) =
                referencePoints
                    .groupBy { it.intersectType }
                    .let { byIntersect ->
                        Triple(
                            byIntersect[WITHIN] ?: emptyList(),
                            byIntersect[BEFORE]?.map { it to KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE }
                                ?: emptyList(),
                            byIntersect[AFTER]?.map { it to KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE }
                                ?: emptyList(),
                        )
                    }

            val invalidIndex =
                withinPoints
                    .zipWithNext { a, b -> abs(b.distance - a.distance) }
                    .let { distances -> distances.indexOfFirst { !TrackMeter.isMetersValid(it) } }

            val (validPoints, invalidPoints) =
                if (invalidIndex == -1) withinPoints to emptyList()
                else {
                    val idx = invalidIndex + 1
                    withinPoints.take(idx) to listOf(withinPoints[idx] to KmPostRejectedReason.TOO_FAR_APART)
                }

            val rejectedKmPosts =
                (beforePoints + afterPoints + invalidPoints).map { (rp, reason) ->
                    val kp = kmPosts.first { k -> k.kmNumber == rp.kmNumber }

                    KmPostWithRejectedReason(kp, reason)
                }

            return validPoints.distinctBy { it.distance } to rejectedKmPosts
        }

        private fun <M : GeocodingAlignmentM<M>> toReferencePoint(
            location: IPoint,
            kmNumber: KmNumber,
            referenceLineGeometry: IAlignment<M>,
        ) =
            referenceLineGeometry.getClosestPointM(location)?.let { (distance, intersectType) ->
                val pointOnLine =
                    requireNotNull(referenceLineGeometry.getPointAtM(distance)) {
                        "Couldn't resolve distance to point on reference line: not continuous?"
                    }

                GeocodingReferencePoint(
                    kmNumber = kmNumber,
                    meters = BigDecimal.ZERO,
                    distance = distance,
                    kmPostOffset = lineLength(location, pointOnLine),
                    intersectType = intersectType,
                )
            }
    }

    fun getM(coordinate: IPoint) = referenceLineGeometry.getClosestPointM(coordinate)

    fun getAddressAndM(coordinate: IPoint, addressDecimals: Int = DEFAULT_TRACK_METER_DECIMALS): AddressAndM? =
        referenceLineGeometry.getClosestPointM(coordinate)?.let { (mValue, type) ->
            getAddress(coordinate, mValue, addressDecimals)?.let { address -> AddressAndM(address, mValue, type) }
        }

    fun getAddress(targetDistance: LineM<M>, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): TrackMeter? =
        referenceLineGeometry.getPointAtM(targetDistance)?.let { point -> getAddress(point, decimals) }?.first

    fun getAddress(coordinate: IPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): Pair<TrackMeter, IntersectType>? =
        getAddressAndM(coordinate, decimals)?.let { (address, _, type) -> address to type }

    private fun getAddress(
        queryCoordinate: IPoint,
        closestReferenceLineM: LineM<M>,
        decimals: Int = DEFAULT_TRACK_METER_DECIMALS,
    ): TrackMeter? {
        val addressPoint = findPreviousPoint(closestReferenceLineM)
        val meters =
            round(
                addressPoint.meters.toDouble() + closestReferenceLineM.distance - addressPoint.distance.distance,
                decimals,
            )
        return if (!TrackMeter.isMetersValid(meters)) null
        else
            getAddressInInterval(
                queryCoordinate,
                getSurroundingProjectionLines(TrackMeter(addressPoint.kmNumber, meters)),
                decimals,
            )
    }

    private tailrec fun getAddressInInterval(
        queryCoordinate: IPoint,
        surroundingProjectionLines: SurroundingProjectionLines<M>,
        decimals: Int,
        lastStepDirection: StepDirection? = null,
    ): TrackMeter? {
        return when (surroundingProjectionLines) {
            is PastReferenceLineEnd -> null
            is ExactHit<*> ->
                return surroundingProjectionLines.projectionLine.address.let { address ->
                    address.copy(meters = round(address.meters, decimals))
                }

            is ProjectionLineInterval<*> -> {
                val (left, right) = surroundingProjectionLines
                val angle = directionBetweenPoints(left.projection.start, right.projection.start)
                val queryLine =
                    Line(
                        queryCoordinate,
                        pointInDirection(queryCoordinate, distance = PROJECTION_LINE_LENGTH, direction = angle),
                    )
                val leftIntersection = lineIntersection(queryLine, left.projection) ?: return null
                val rightIntersection = lineIntersection(queryLine, right.projection) ?: return null

                // In the happy case, the surrounding projection lines haven't crossed, and the query line starts from
                // between them and goes right: Hence it's right of the left projection line, and left of the right one
                val isLeftOfLeftProjectionLine = leftIntersection.segment1Portion > 0
                val isRightOfRightProjectionLine = rightIntersection.segment1Portion < 0
                if (
                    (isLeftOfLeftProjectionLine && isRightOfRightProjectionLine) ||
                        (isRightOfRightProjectionLine && lastStepDirection == StepDirection.Backward) ||
                        (isLeftOfLeftProjectionLine && lastStepDirection == StepDirection.Forward)
                ) {
                    // either the surrounding projection lines have crossed, or we got turned around trying to find
                    // the right projection line interval
                    null
                } else if (isLeftOfLeftProjectionLine) {
                    val left = getNextSurroundingProjectionLines(surroundingProjectionLines, StepDirection.Backward)
                    if (left != null) {
                        getAddressInInterval(queryCoordinate, left, decimals, StepDirection.Backward)
                    } else null
                } else if (isRightOfRightProjectionLine) {
                    val right = getNextSurroundingProjectionLines(surroundingProjectionLines, StepDirection.Forward)
                    if (right != null) {
                        getAddressInInterval(queryCoordinate, right, decimals, StepDirection.Forward)
                    } else null
                } else {
                    val proportion =
                        -leftIntersection.segment1Portion /
                            (rightIntersection.segment1Portion - leftIntersection.segment1Portion)
                    // right address can be a km start, so must interpolate between distances rather than address meters
                    val meterInterval = (right.distance.distance - left.distance.distance).toBigDecimal()
                    val meters = left.address.meters + meterInterval * BigDecimal(proportion)
                    left.address.copy(meters = round(meters, decimals))
                }
            }
        }
    }

    val referenceLineAddresses by lazy { getAddressPoints(referenceLineGeometry) }

    fun <TargetM : AnyM<TargetM>> toAddressPoint(
        point: AlignmentPoint<TargetM>,
        decimals: Int = DEFAULT_TRACK_METER_DECIMALS,
    ) = getAddress(point, decimals)?.let { (address, intersectType) -> AddressPoint(point, address) to intersectType }

    fun <TargetM : AlignmentM<TargetM>> getAddressPoints(
        alignment: IAlignment<TargetM>,
        resolution: Resolution = Resolution.ONE_METER,
    ): AlignmentAddresses<TargetM>? {
        val startPoint = alignment.start?.let(::toAddressPoint)
        val endPoint = alignment.end?.let(::toAddressPoint)
        return if (startPoint != null && endPoint != null) {
            val midPoints =
                getMidPoints(
                    alignment,
                    (startPoint.first.address + MIN_METER_LENGTH)..(endPoint.first.address - MIN_METER_LENGTH),
                    resolution,
                )

            AlignmentAddresses(
                startPoint = startPoint.first,
                endPoint = endPoint.first,
                startIntersect = startPoint.second,
                endIntersect = endPoint.second,
                midPoints = midPoints.addressPoints.filterNotNull(),
                alignmentWalkFinished = midPoints.alignmentWalkFinished,
            )
        } else null
    }

    fun <TargetM : AlignmentM<TargetM>> getTrackLocation(
        alignment: IAlignment<TargetM>,
        address: TrackMeter,
    ): AddressPoint<TargetM>? {
        return getTrackLocations(alignment, listOf(address))[0]
    }

    fun <TargetM : AlignmentM<TargetM>> getTrackLocations(
        alignment: IAlignment<TargetM>,
        addresses: List<TrackMeter>,
    ): List<AddressPoint<TargetM>?> {
        val alignmentStart = alignment.start
        val alignmentEnd = alignment.end
        val startAddress = alignmentStart?.let(::getAddress)?.first
        val endAddress = alignmentEnd?.let(::getAddress)?.first
        return if (startAddress == null || endAddress == null) addresses.map { null }
        else getTrackLocations(alignment, addresses, alignmentStart, startAddress, alignmentEnd, endAddress)
    }

    private fun <TargetM : AlignmentM<TargetM>> getTrackLocations(
        alignment: IAlignment<TargetM>,
        addresses: List<TrackMeter>,
        alignmentStart: AlignmentPoint<TargetM>,
        startAddress: TrackMeter,
        alignmentEnd: AlignmentPoint<TargetM>,
        endAddress: TrackMeter,
        resolution: Resolution = Resolution.ONE_METER,
    ): List<AddressPoint<TargetM>?> =
        processRights(
            addresses,
            getProjectionLineForAddressInAlignment(alignmentStart, startAddress, alignmentEnd, endAddress),
        ) { projectionLines ->
            if (projectionLines.size < 10) projectionLines.map { pl -> getProjectedAddressPoint(pl, alignment) }
            else getManyTrackLocations(alignment, startAddress, endAddress, projectionLines, resolution)
        }

    private fun <TargetM : AnyM<TargetM>> getProjectionLineForAddressInAlignment(
        alignmentStart: AlignmentPoint<TargetM>,
        alignmentStartAddress: TrackMeter,
        alignmentEnd: AlignmentPoint<TargetM>,
        alignmentEndAddress: TrackMeter,
    ): (address: TrackMeter) -> Either<AddressPoint<TargetM>?, ProjectionLineInContext<M>> = { address ->
        if (address !in alignmentStartAddress..alignmentEndAddress) {
            Left(null)
        } else if (alignmentStartAddress.isSame(address)) {
            Left(AddressPoint(alignmentStart, alignmentStartAddress))
        } else if (alignmentEndAddress.isSame(address)) {
            Left(AddressPoint(alignmentEnd, alignmentEndAddress))
        } else {
            getProjectionLineInContext(address)?.let(::Right) ?: Left(null)
        }
    }

    private fun <TargetM : AlignmentM<TargetM>> getManyTrackLocations(
        alignment: IAlignment<TargetM>,
        alignmentStartAddress: TrackMeter,
        alignmentEndAddress: TrackMeter,
        projectionLinesWithinAlignment: List<ProjectionLineInContext<M>>,
        resolution: Resolution,
    ) =
        processSortedBy(
                projectionLinesWithinAlignment +
                    // add some extra projection lines to make sure we don't go out of sync when the track and reference
                    // line loop in on themselves
                    getProjectionLinesForRange(
                            (alignmentStartAddress + MIN_METER_LENGTH)..(alignmentEndAddress - MIN_METER_LENGTH),
                            resolution,
                        )
                        .filterIndexed { index, _ -> index % 10 == 0 },
                Comparator.comparing { it.projection.distance },
            ) { sortedLines ->
                getProjectedAddressPoints(sortedLines, alignment, meterProjectionLineIntersections).addressPoints
            }
            .take(projectionLinesWithinAlignment.size)

    fun <TargetM : AlignmentM<TargetM>> getStartAndEnd(
        alignment: IAlignment<TargetM>
    ): Pair<AddressPoint<TargetM>?, AddressPoint<TargetM>?> {
        val start = alignment.start?.let(::toAddressPoint)?.first
        val end = alignment.end?.let(::toAddressPoint)?.first
        return start to end
    }

    private fun <TargetM : AlignmentM<TargetM>> getMidPoints(
        alignment: IAlignment<TargetM>,
        range: ClosedRange<TrackMeter>,
        resolution: Resolution = Resolution.ONE_METER,
    ): AddressPointWalkResult<TargetM> =
        getProjectedAddressPoints(
            getProjectionLinesForRange(range, resolution),
            alignment,
            meterProjectionLineIntersections,
        )

    private fun getProjectionLinesForRange(
        range: ClosedRange<TrackMeter>,
        resolution: Resolution,
    ): List<ProjectionLineInContext<M>> =
        getIndexRangeForRangeInOrderedList(
                projectionLines.getValue(resolution).value,
                range.start,
                range.endInclusive,
            ) { p, e ->
                p.address.compareTo(e)
            }
            ?.let { indexRange ->
                projectionLines.getValue(resolution).value.subList(indexRange.first, indexRange.last + 1).mapIndexed {
                    index,
                    line ->
                    ProjectionLineInContext(line, indexRange.start + index)
                }
            } ?: listOf()

    fun getSwitchPoints(geometry: LocationTrackGeometry): List<AddressPoint<LocationTrackM>> =
        geometry.trackSwitchLinks
            .mapNotNull { link -> toAddressPoint(link.location, 3)?.first }
            .distinctBy { addressPoint -> addressPoint.address }

    private fun findPreviousPoint(targetDistance: LineM<M>): GeocodingReferencePoint<M> {
        val target = roundTo3Decimals(targetDistance) // Round to 1mm to work around small imprecision
        if (target < BigDecimal.ZERO) throw GeocodingFailureException("Cannot geocode with negative distance")
        return referencePoints.findLast { referencePoint -> referencePoint.distanceRounded <= target }
            ?: throw GeocodingFailureException("Target point is not within the reference line length")
    }

    fun cutRangeByKms(
        range: ClosedRange<TrackMeter>,
        kms: Set<KmNumber>,
        resolution: Resolution = Resolution.ONE_METER,
    ): List<ClosedRange<TrackMeter>> {
        if (projectionLines.getValue(resolution).value.isEmpty()) return listOf()
        val addressRanges = getKmRanges(kms).mapNotNull { kmRange -> toAddressRange(kmRange, resolution) }
        return splitRange(range, addressRanges)
    }

    private fun getKmRanges(kms: Set<KmNumber>): List<ClosedRange<KmNumber>> {
        val ranges: MutableList<ClosedRange<KmNumber>> = mutableListOf()
        var currentRange: ClosedRange<KmNumber>? = null
        allKms.forEach { kmNumber ->
            currentRange =
                if (kms.contains(kmNumber)) {
                    currentRange?.let { c -> c.start..kmNumber } ?: kmNumber..kmNumber
                } else {
                    currentRange?.let(ranges::add)
                    null
                }
        }
        currentRange?.let(ranges::add)
        return ranges
    }

    /**
     * Returns the inclusive range of addresses in the given range of km-numbers. Note: Since this is inclusive, it does
     * not include decimal meters after the last even meter, even though such addresses can be calculated
     */
    private fun toAddressRange(kmRange: ClosedRange<KmNumber>, resolution: Resolution): ClosedRange<TrackMeter>? {
        val projectionLinesResolution = projectionLines.getValue(resolution).value
        val startAddress = projectionLinesResolution.find { l -> l.address.kmNumber == kmRange.start }?.address
        val endAddress = projectionLinesResolution.findLast { l -> l.address.kmNumber == kmRange.endInclusive }?.address
        return if (startAddress != null && endAddress != null) startAddress..endAddress else null
    }
}

private fun <M : GeocodingAlignmentM<M>> useAddressPrecision(projectionLine: ProjectionLine<M>, address: TrackMeter) =
    if (address.decimalCount() == projectionLine.address.decimalCount()) projectionLine
    else {
        val lineAddress = projectionLine.address
        val lineMeters = lineAddress.meters
        projectionLine.copy(address = lineAddress.copy(meters = lineMeters.setScale(address.meters.scale())))
    }

private fun <M : GeocodingAlignmentM<M>> interpolateProjectionLine(
    address: TrackMeter,
    left: ProjectionLine<M>,
    right: ProjectionLine<M>,
): ProjectionLine<M> {
    val distancePastLeft = address.meters.toDouble() - left.address.meters.toDouble()
    val proportion = distancePastLeft / (right.distance.distance - left.distance.distance)
    val projectionStart = interpolateToPoint(left.projection.start, right.projection.start, proportion)
    val referenceDirection = interpolateAngleRads(left.referenceDirection, right.referenceDirection, proportion)
    val projectionDirection = referenceDirection + PI / 2
    val crossSection =
        Line(
            projectionStart,
            pointInDirection(projectionStart, distance = PROJECTION_LINE_LENGTH, direction = projectionDirection),
        )
    return ProjectionLine(address, crossSection, left.distance + distancePastLeft, referenceDirection)
}

fun splitRange(range: ClosedRange<TrackMeter>, splits: List<ClosedRange<TrackMeter>>): List<ClosedRange<TrackMeter>> =
    splits.mapNotNull { allowedRange ->
        if (range.start >= allowedRange.endInclusive || range.endInclusive <= allowedRange.start) null
        else maxOf(range.start, allowedRange.start)..minOf(range.endInclusive, allowedRange.endInclusive)
    }

fun <T, R : Comparable<R>> getSublistForRangeInOrderedList(
    things: List<T>,
    range: ClosedRange<R>,
    compare: (thing: T, rangeEnd: R) -> Int,
): List<T> =
    getIndexRangeForRangeInOrderedList(things, range.start, range.endInclusive, compare)?.let { indexRange ->
        things.subList(indexRange.first, indexRange.last + 1)
    } ?: listOf()

fun <TargetM : AlignmentM<TargetM>, M : GeocodingAlignmentM<M>> getProjectedAddressPoint(
    projectionInContext: ProjectionLineInContext<M>,
    alignment: IAlignment<TargetM>,
): AddressPoint<TargetM>? {
    val projection = projectionInContext.projection
    return getCollisionSegmentIndex(projection.projection, alignment)?.let { index ->
        val segment = alignment.segments[index]
        val m = alignment.segmentMValues[index].min
        val segmentEdges = getPolyLineEdges(segment, m, null, null)
        val edgeAndPortion = getIntersection(projection.projection, segmentEdges)
        // TODO check intersection isn't too far
        return edgeAndPortion?.let { (edge, portion) ->
            AddressPoint(point = edge.interpolateAlignmentPointAtPortion(portion), address = projection.address)
        }
    }
}

private data class AddressPointWalkResult<M : AnyM<M>>(
    val addressPoints: List<AddressPoint<M>?>,
    val alignmentWalkFinished: Boolean,
)

private data class AlignmentPointInterval<M : AnyM<M>>(val start: AlignmentPoint<M>, val end: AlignmentPoint<M>) {
    fun interpolateAlignmentPointAtPortion(proportion: Double): AlignmentPoint<M> =
        interpolateToAlignmentPoint(start, end, proportion)

    val referenceDirection: Double
        get() = directionBetweenPoints(start, end)
}

private fun <M : GeocodingAlignmentM<M>, TargetM : AlignmentM<TargetM>> getProjectedAddressPoints(
    projectionLines: List<ProjectionLineInContext<M>>,
    alignment: IAlignment<TargetM>,
    meterProjectionLineIntersections: List<Intersection?>,
): AddressPointWalkResult<TargetM> {
    val alignmentPoints = alignment.allAlignmentPoints.zipWithNext(::AlignmentPointInterval).toList()
    val walk = AlignmentWalk(alignmentPoints, meterProjectionLineIntersections)
    val addressPoints = projectionLines.map(walk::stepWith)
    return AddressPointWalkResult(addressPoints, walk.alignmentLooksValid)
}

private sealed class StepResult

private data object AddressDoesNotExistOnAlignment : StepResult()

private data object AddressFound : StepResult()

private data class ContinueStepping(val direction: StepDirection) : StepResult()

private enum class StepDirection(val diff: Int) {
    Forward(1),
    Backward(-1),
}

private class AlignmentWalk<TargetM : AnyM<TargetM>>(
    val alignmentEdges: List<AlignmentPointInterval<TargetM>>,
    val meterProjectionLineIntersections: List<Intersection?>,
) {
    var alignmentLooksValid = true
    private var edgeIndex = 0

    private val edge
        get() = alignmentEdges[edgeIndex]

    fun <M : GeocodingAlignmentM<M>> stepWith(projectionInContext: ProjectionLineInContext<M>): AddressPoint<TargetM>? {
        var lastStepDirection = 0
        val projection = projectionInContext.projection
        while (true) {
            val isEdgeAligned = angleDiffRads(edge.referenceDirection, projection.referenceDirection) <= PI / 2
            val intersection = intersection(edge, projection.projection)

            val stepResult =
                when (intersection.inSegment1) {
                    BEFORE -> stepInDirection(if (isEdgeAligned) StepDirection.Backward else StepDirection.Forward)
                    WITHIN -> AddressFound
                    AFTER -> stepInDirection(if (isEdgeAligned) StepDirection.Forward else StepDirection.Backward)
                }
            when (stepResult) {
                is AddressDoesNotExistOnAlignment -> return null
                is AddressFound -> {
                    if (
                        projectionInContext.intervalIndex >= 0 &&
                            projectionInContext.intervalIndex < meterProjectionLineIntersections.lastIndex
                    ) {
                        val intervalIntersection = meterProjectionLineIntersections[projectionInContext.intervalIndex]
                        if (projectionLinesHaveCrossed(intervalIntersection, intersection)) {
                            return null
                        }
                    }
                    val point = edge.interpolateAlignmentPointAtPortion(intersection.segment1Portion)
                    return AddressPoint(point, projection.address)
                }
                is ContinueStepping -> {
                    if (lastStepDirection == -stepResult.direction.diff) {
                        // alignment walk would loop
                        alignmentLooksValid = false
                        return null
                    }
                    lastStepDirection = stepResult.direction.diff
                    edgeIndex += stepResult.direction.diff
                    continue
                }
            }
        }
    }

    private fun stepInDirection(direction: StepDirection): StepResult =
        if (direction == StepDirection.Forward) {
            if (edgeIndex == alignmentEdges.lastIndex) AddressDoesNotExistOnAlignment else ContinueStepping(direction)
        } else {
            if (edgeIndex == 0) AddressDoesNotExistOnAlignment else ContinueStepping(direction)
        }
}

private fun projectionLinesHaveCrossed(intervalIntersection: Intersection?, intersection: Intersection): Boolean =
    intervalIntersection != null &&
        ((intervalIntersection.segment1Portion.sign == intersection.segment2Portion.sign &&
            (intervalIntersection.segment1Portion.absoluteValue < intersection.segment2Portion.absoluteValue)) ||
            (intervalIntersection.segment2Portion.sign == intersection.segment2Portion.sign &&
                intervalIntersection.segment2Portion.absoluteValue < intersection.segment2Portion.absoluteValue))

private fun <M : GeocodingAlignmentM<M>> createStartProjection(
    referencePoints: List<GeocodingReferencePoint<M>>,
    polyLineEdges: List<PolyLineEdge<M>>,
): ProjectionLine<M>? {
    val meters = referencePoints.first().meters
    return if (!TrackMeter.isMetersValid(meters)) null
    else {
        val address =
            TrackMeter(
                referencePoints.first().kmNumber,
                referencePoints.first().meters.setScale(DEFAULT_TRACK_METER_DECIMALS),
            )
        val projectionLine = polyLineEdges.first().crossSectionAt(LineM(0.0))
        ProjectionLine(address, projectionLine, LineM(0.0), polyLineEdges.first().referenceDirection)
    }
}

private fun <M : GeocodingAlignmentM<M>> createEndProjection(
    referencePoints: List<GeocodingReferencePoint<M>>,
    polyLineEdges: List<PolyLineEdge<M>>,
    referenceLineGeometryLength: LineM<M>,
): ProjectionLine<M>? {
    val meters = referenceLineGeometryLength - referencePoints.last().let { p -> p.distance - p.meters.toDouble() }
    return if (!TrackMeter.isMetersValid(meters)) null
    else {
        val address = TrackMeter(referencePoints.last().kmNumber, meters.distance, DEFAULT_TRACK_METER_DECIMALS)
        val projectionLine = polyLineEdges.last().crossSectionAt(referenceLineGeometryLength)
        ProjectionLine(address, projectionLine, referenceLineGeometryLength, polyLineEdges.last().referenceDirection)
    }
}

private fun <M : GeocodingAlignmentM<M>> createProjectionLines(
    addressPoints: List<GeocodingReferencePoint<M>>,
    edges: List<PolyLineEdge<M>>,
    resolution: Resolution,
): List<ProjectionLine<M>> {
    val endDistance = edges.lastOrNull()?.endM ?: LineM(0.0)
    val start = createStartProjection(addressPoints, edges)
    val end = createEndProjection(addressPoints, edges, endDistance)
    val midProjections =
        addressPoints.flatMapIndexed { index: Int, point: GeocodingReferencePoint<M> ->
            val minMeter =
                if (index == 0) point.meters.setScale(0, RoundingMode.FLOOR).toInt() + 1
                else point.meters.setScale(0, RoundingMode.CEILING).toInt()
            val maxDistance = (addressPoints.getOrNull(index + 1)?.distance?.minus(MIN_METER_LENGTH) ?: endDistance)
            val maxMeter = (point.meters.toDouble() + maxDistance.distance - point.distance.distance).toInt()

            // If the km posts or reference line are sufficiently broken to cause invalid track meters
            // somewhere, it's probably bad enough that looking up track addresses is not useful, so we
            // just bail out here.
            if (
                !TrackMeter.isMetersValid(minMeter.toBigDecimal()) || !TrackMeter.isMetersValid(maxMeter.toBigDecimal())
            ) {
                return listOf()
            }

            val projectionLineSteps =
                when (resolution.meters) {
                    is Int -> (minMeter..maxMeter step resolution.meters).toList()
                    is BigDecimal -> {
                        val maxMeterBigDecimals = BigDecimal(maxMeter)
                        generateSequence(BigDecimal(minMeter)) { currentMeter -> currentMeter + resolution.meters }
                            .takeWhile { currentMeter -> currentMeter <= maxMeterBigDecimals }
                            .toList()
                    }
                    else -> error("Unhandled resolution type")
                }

            projectionLineSteps.map { meter ->
                val distance = point.distance + (meter.toDouble() - point.meters.toDouble())
                val edge =
                    findEdge(distance, edges)
                        ?: throw GeocodingFailureException(
                            "Could not produce projection: " +
                                "km=${point.kmNumber} m=$meter distance=$distance " +
                                "endDistance=$endDistance refPointDistance=${point.distance} " +
                                "minMeter=$minMeter maxMeter=$maxMeter maxDistance=$maxDistance" +
                                "edges=${edges.filter { e -> e.startM in distance - 10.0..distance + 10.0 }}"
                        )
                val address =
                    when (meter) {
                        is Int -> TrackMeter(point.kmNumber, meter)
                        is BigDecimal -> TrackMeter(point.kmNumber, meter)
                        else -> error("Unhandled meter number type")
                    }
                ProjectionLine(address, edge.crossSectionAt(distance), distance, edge.referenceDirection)
            }
        }
    return listOf(listOfNotNull(start), midProjections, listOfNotNull(end)).flatten()
}

private fun <M : GeocodingAlignmentM<M>> validateProjectionLines(
    trackNumber: TrackNumber,
    lines: List<ProjectionLine<M>>,
    distanceDelta: Double,
    angleDelta: Double,
    resolution: Resolution,
): List<ProjectionLine<M>> {
    val distanceRange = (resolution.meters.toDouble() - distanceDelta)..(resolution.meters.toDouble() + distanceDelta)
    return lines.filterIndexed { index, line ->
        val previous = lines.getOrNull(index - 1)
        val distanceApprox =
            previous?.let { prev ->
                if (prev.address.kmNumber == line.address.kmNumber) {
                    lineLength(prev.projection.start, line.projection.start)
                } else null
            }
        val angleDiff = previous?.let { prev -> angleDiffRads(prev.projection.angle, line.projection.angle) }
        val isStartOrEndProjection = index == 1 || index == lines.lastIndex
        if (distanceApprox != null && distanceApprox !in distanceRange && !isStartOrEndProjection) {
            logger.warn(
                "Projection lines at un-even intervals: " +
                    "trackNumber=$trackNumber index=$index distance=$distanceApprox angleDiff=$angleDiff previous=$previous next=$line"
            )
        }
        if (angleDiff != null && angleDiff > angleDelta) {
            logger.error(
                "Projection lines turn unexpectedly: " +
                    "trackNumber=$trackNumber index=$index distance=$distanceApprox angleDiff=$angleDiff previous=$previous next=$line"
            )
        }
        (angleDiff == null || angleDiff <= angleDelta)
    }
}

private fun getCollisionSegmentIndex(projection: Line, alignment: IAlignment<*>): Int? {
    return alignment.segments
        .mapIndexedNotNull { index, s ->
            val intersection = lineIntersection(s.segmentStart, s.segmentEnd, projection.start, projection.end)
            if (intersection?.inSegment1 == WITHIN) intersection.relativeDistance2 to index else null
        }
        .minByOrNull { (distance, _) -> distance }
        ?.second
}

fun <M : AlignmentM<M>> getIntersection(
    projection: Line,
    edges: List<PolyLineEdge<M>>,
): Pair<PolyLineEdge<M>, Double>? {
    var intersection: Intersection? = null
    val collisionEdge =
        edges.getOrNull(
            edges.binarySearch { edge ->
                val edgeIntersection = intersection(edge, projection)
                when (edgeIntersection.inSegment1) {
                    BEFORE -> 1
                    AFTER -> -1
                    else -> {
                        intersection = edgeIntersection
                        0
                    }
                }
            }
        )
    return if (collisionEdge != null) {
        intersection?.let { i -> collisionEdge to i.segment1Portion }
    } else {
        null
    }
}

private fun <M : AlignmentM<M>> getPolyLineEdges(alignment: IAlignment<M>): List<PolyLineEdge<M>> {
    return alignment.segmentsWithM
        .flatMapIndexed { index, (segment, m) ->
            getPolyLineEdges(
                segment,
                m.min,
                alignment.segments.getOrNull(index - 1)?.endDirection,
                alignment.segments.getOrNull(index + 1)?.startDirection,
            )
        }
        .also { edges ->
            edges.forEachIndexed { index, edge ->
                val prev = edges.getOrNull(index - 1)
                if (prev != null) require(prev.endM == edge.startM) { "Edges not continuous: edges=$edges" }
            }
        }
}

private fun <M : AlignmentM<M>> getPolyLineEdges(
    segment: ISegment,
    startM: LineM<M>,
    prevDir: Double?,
    nextDir: Double?,
): List<PolyLineEdge<M>> {
    return segment.segmentPoints.mapIndexedNotNull { pointIndex: Int, point: SegmentPoint ->
        if (pointIndex == 0) null
        else {
            val previous = segment.segmentPoints[pointIndex - 1]
            // Edge direction
            val pointDirection =
                if (segment.source != GeometrySource.GENERATED) {
                    directionBetweenPoints(previous, point)
                } else if (prevDir == null || nextDir == null) {
                    // Generated connection segments can have a sideways offset, but the real line
                    // doesn't change direction. To compensate, we want to project with the
                    // direction
                    // of previous/next segments
                    prevDir ?: nextDir ?: directionBetweenPoints(previous, point)
                } else {
                    angleAvgRads(prevDir, nextDir)
                }
            PolyLineEdge(start = previous, end = point, segmentStart = startM, referenceDirection = pointDirection)
        }
    }
}

/**
 * Since segment start distance and m-values are stored with a limited precision, allow finding a segment with that much
 * delta-value. Otherwise, it's possible to get a distance-value "between segments"
 */
private fun <M : AlignmentM<M>> findEdge(
    distance: LineM<M>,
    all: List<PolyLineEdge<M>>,
    delta: Double = 0.000001,
): PolyLineEdge<M>? =
    all.getOrNull(
        all.binarySearch { edge ->
            if (edge.startM > distance + delta) 1 else if (edge.endM < distance - delta) -1 else 0
        }
    )

private fun intersection(edge: PolyLineEdge<*>, projection: Line) =
    lineIntersection(edge.start, edge.end, projection.start, projection.end)
        ?: throw GeocodingFailureException(
            "Projection line parallel to segment: edge=${edge.start}-${edge.end} projection=${projection.start}-${projection.end}"
        )

private fun intersection(edge: AlignmentPointInterval<*>, projection: Line) =
    lineIntersection(edge.start, edge.end, projection.start, projection.end)
        ?: throw GeocodingFailureException(
            "Projection line parallel to segment: edge=${edge.start}-${edge.end} projection=${projection.start}-${projection.end}"
        )

private fun getAddressIfIWithin(address: Pair<TrackMeter, IntersectType>): TrackMeter? =
    if (address.second == WITHIN) address.first else null

const val PROJECTION_LINE_LENGTH = 100.0

data class PolyLineEdge<M : AlignmentM<M>>(
    val start: SegmentPoint,
    val end: SegmentPoint,
    val segmentStart: LineM<M>,
    val referenceDirection: Double,
) {
    // Direction for projection lines from the edge: 90 degrees turned from edge direction
    val projectionDirection: Double
        get() = PI / 2 + referenceDirection

    val startM: LineM<M>
        get() = start.m.segmentToAlignmentM(segmentStart)

    val endM: LineM<M>
        get() = end.m.segmentToAlignmentM(segmentStart)

    val length: Double
        get() = end.m.distance - start.m.distance

    fun crossSectionAt(distance: LineM<M>) =
        interpolatePointAtM(distance).let { point ->
            Line(point, pointInDirection(point, distance = PROJECTION_LINE_LENGTH, direction = projectionDirection))
        }

    private fun interpolatePointAtM(m: LineM<M>): IPoint =
        if (m <= startM) start
        else if (m >= endM) end else interpolateToPoint(start, end, (m - startM).distance / length)

    fun interpolateAlignmentPointAtPortion(portion: Double): AlignmentPoint<M> =
        interpolateSegmentPointAtPortion(portion).toAlignmentPoint(segmentStart)

    fun interpolateSegmentPointAtPortion(portion: Double): SegmentPoint =
        if (portion <= 0.0) start else if (portion >= 1.0) end else interpolateToSegmentPoint(start, end, portion)
}

private sealed class SurroundingProjectionLines<M : GeocodingAlignmentM<M>>

private class PastReferenceLineEnd<M : GeocodingAlignmentM<M>>() : SurroundingProjectionLines<M>()

private data class ExactHit<M : GeocodingAlignmentM<M>>(val projectionLine: ProjectionLine<M>, val index: Int) :
    SurroundingProjectionLines<M>()

private data class ProjectionLineInterval<M : GeocodingAlignmentM<M>>(
    val left: ProjectionLine<M>,
    val right: ProjectionLine<M>,
    val leftIndex: Int,
) : SurroundingProjectionLines<M>()

// TODO privatise, and remove the tests that depend on calling getProjectedAddressPoint?
data class ProjectionLineInContext<M : GeocodingAlignmentM<M>>(
    val projection: ProjectionLine<M>,
    val intervalIndex: Int,
)
