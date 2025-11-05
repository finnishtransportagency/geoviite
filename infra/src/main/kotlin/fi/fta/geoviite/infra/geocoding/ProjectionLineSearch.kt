package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Intersection
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.GeocodingAlignmentM
import java.math.BigDecimal

fun <M : GeocodingAlignmentM<M>> getAddressInInterval(
    getMeterProjectionLine: (index: Int) -> ProjectionLine<M>?,
    queryCoordinate: IPoint,
    initialSurroundingProjectionLines: SurroundingProjectionLines<M>,
    decimals: Int,
): TrackMeter? {
    var surroundingProjectionLines = initialSurroundingProjectionLines
    var lastStepDirection: StepDirection? = null

    while (true) {
        val step = getAddressInIntervalStep(getMeterProjectionLine, queryCoordinate, surroundingProjectionLines)
        return when (step) {
            is NotFound -> null
            is Resolved -> TrackMeter(step.kmNumber, round(step.meters, decimals))
            is TakeStep<M> -> {
                // Mathematically, the line segment from a point to the nearest point to it on a smooth curve hits the
                // curve either perpendicularly or at an end. Floatingpointly, with line strings, it mightn't, and so we
                // may have to step around searching for it.
                if (lastStepDirection != null && step.takenStepDirection != lastStepDirection) null
                else {
                    lastStepDirection = step.takenStepDirection
                    surroundingProjectionLines = step.resultingSurroundingLines
                    continue
                }
            }
        }
    }
}

private sealed class AddressInIntervalSearchStepResult<M : GeocodingAlignmentM<M>>

private class NotFound<M : GeocodingAlignmentM<M>> : AddressInIntervalSearchStepResult<M>()

private data class Resolved<M : GeocodingAlignmentM<M>>(val kmNumber: KmNumber, val meters: BigDecimal) :
    AddressInIntervalSearchStepResult<M>() {
    constructor(address: TrackMeter) : this(address.kmNumber, address.meters)
}

private data class TakeStep<M : GeocodingAlignmentM<M>>(
    val takenStepDirection: StepDirection,
    val resultingSurroundingLines: SurroundingProjectionLines<M>,
) : AddressInIntervalSearchStepResult<M>()

private fun <M : GeocodingAlignmentM<M>> getAddressInIntervalStep(
    getMeterProjectionLine: (index: Int) -> ProjectionLine<M>?,
    queryCoordinate: IPoint,
    surroundingProjectionLines: SurroundingProjectionLines<M>,
): AddressInIntervalSearchStepResult<M> =
    when (surroundingProjectionLines) {
        is PastReferenceLineEnd -> NotFound()
        is ExactHit<*> -> Resolved(surroundingProjectionLines.projectionLine.address)
        is ProjectionLineInterval<M> ->
            getAddressInIntervalBetweenSurroundingLinesStep(
                getMeterProjectionLine,
                queryCoordinate,
                surroundingProjectionLines,
            )
    }

private fun <M : GeocodingAlignmentM<M>> getAddressInIntervalBetweenSurroundingLinesStep(
    getMeterProjectionLine: (index: Int) -> ProjectionLine<M>?,
    queryCoordinate: IPoint,
    interval: ProjectionLineInterval<M>,
): AddressInIntervalSearchStepResult<M> {
    val (left, right) = interval
    val (leftIntersection, rightIntersection) =
        getQueryLineIntersections(queryCoordinate, left, right) ?: return NotFound()

    // In the happy case, the surrounding projection lines haven't crossed, and the query line starts from
    // between them and goes right: Hence it's right of the left projection line, and left of the right one
    val isLeftOfLeftProjectionLine = leftIntersection.segment1Portion > 0
    val isRightOfRightProjectionLine = rightIntersection.segment1Portion < 0
    return if (isLeftOfLeftProjectionLine && isRightOfRightProjectionLine) {
        // surrounding projection lines have crossed
        NotFound()
    } else if (isLeftOfLeftProjectionLine || isRightOfRightProjectionLine) {
        // lines haven't crossed, but the query coordinate is on the same side of both of them
        val stepDirection = if (isLeftOfLeftProjectionLine) StepDirection.Backward else StepDirection.Forward
        val next = getNextSurroundingProjectionLines(getMeterProjectionLine, interval, stepDirection)
        if (next == null) NotFound() else TakeStep(stepDirection, next)
    } else {
        val proportion =
            -leftIntersection.segment1Portion / (rightIntersection.segment1Portion - leftIntersection.segment1Portion)
        // right address can be a km start, so must interpolate between distances rather than address meters
        val meterInterval = right.referenceLineM.distance - left.referenceLineM.distance
        val meters = left.address.meters.toDouble() + meterInterval * proportion
        Resolved(left.address.kmNumber, BigDecimal(meters))
    }
}

private fun <M : GeocodingAlignmentM<M>> getQueryLineIntersections(
    queryCoordinate: IPoint,
    left: ProjectionLine<M>,
    right: ProjectionLine<M>,
): Pair<Intersection, Intersection>? {
    val angle = directionBetweenPoints(left.projection.start, right.projection.start)
    val queryLine =
        Line(queryCoordinate, pointInDirection(queryCoordinate, distance = PROJECTION_LINE_LENGTH, direction = angle))
    return lineIntersection(queryLine, left.projection)?.let { leftIntersection ->
        lineIntersection(queryLine, right.projection)?.let { rightIntersection ->
            leftIntersection to rightIntersection
        }
    }
}

private fun <M : GeocodingAlignmentM<M>> getNextSurroundingProjectionLines(
    getMeterProjectionLine: (index: Int) -> ProjectionLine<M>?,
    here: SurroundingProjectionLines<M>,
    stepDirection: StepDirection,
): SurroundingProjectionLines<M>? {
    val (leftIndex, rightIndex) =
        when (here) {
            is PastReferenceLineEnd -> return null
            is ExactHit ->
                if (stepDirection == StepDirection.Backward) here.index - 1 to here.index
                else here.index to here.index + 1
            is ProjectionLineInterval -> here.leftIndex + stepDirection.diff to here.leftIndex + 1 + stepDirection.diff
        }
    return getMeterProjectionLine(leftIndex)?.let { left ->
        getMeterProjectionLine(rightIndex)?.let { right -> ProjectionLineInterval(left, right, leftIndex) }
    }
}
