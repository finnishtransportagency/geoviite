package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val DELTA = 0.000001

val segment1 = segment(
    Point(x = 385757.97156912443, y = 6672279.269578109),
    Point(x = 385711.5357118625, y = 6672843.197750405),
    Point(x = 385626.95239453146, y = 6673453.008328755),
    Point(x = 385565.58700583136, y = 6673780.854820957),
    Point(x = 385493.5620447163, y = 6674327.448223977),
    Point(x = 385482.9024723013, y = 6674546.195134795),
    Point(x = 385424.6330188301, y = 6674981.630668635),
    Point(x = 385377.0231377738, y = 6675198.319291654),
    Point(x = 385273.0397969192, y = 6675558.0948048225),
    Point(x = 385216.6663371209, y = 6675701.1816949705),
    Point(x = 385081.84688330034, y = 6675970.204397376),
    startLength = 0.0,
)
val segment2 = segment(
    Point(x = 385081.84688330034, y = 6675970.204397376),
    Point(x = 385003.4008892781, y = 6676096.140209634),
    Point(x = 384824.43636700965, y = 6676330.8607562585),
    Point(x = 384723.9178387635, y = 6676439.645490625),
    Point(x = 384461.0552224484, y = 6676692.609084033),
    Point(x = 384298.7111343796, y = 6676836.7879430745),
    Point(x = 383962.4566625423, y = 6677106.147418647),
    Point(x = 383614.6358950054, y = 6677356.508651709),
    Point(x = 383257.66521149303, y = 6677624.701901384),
    Point(x = 383079.07071553095, y = 6677746.511026372),
    Point(x = 382959.97133348766, y = 6677823.621276415),
    Point(x = 382900.3670653631, y = 6677856.032651512),
    Point(x = 382761.2176702685, y = 6677923.467526838),
    Point(x = 382711.47467736073, y = 6677942.28533952),
    startLength = segment1.start + segment1.length
)
val segment3 = segment(
    Point(x = 382711.47467736073, y = 6677942.28533952),
    Point(x = 382616.9193291537, y = 6677973.124183675),
    Point(x = 382572.1069738545, y = 6677985.145215148),
    Point(x = 382487.4129008643, y = 6678002.390496888),
    Point(x = 382447.5311831734, y = 6678007.614747154),
    Point(x = 382239.6534347426, y = 6678014.43348008),
    Point(x = 381903.6613732627, y = 6678017.6224454),
    Point(x = 381500.8788447162, y = 6677988.736686744),
    Point(x = 381098.351281938, y = 6677939.405604937),
    Point(x = 380696.0786849283, y = 6677869.629199982),
    Point(x = 380075.3780522702, y = 6677812.131510135),
    Point(x = 379546.59970029286, y = 6677795.661380321),
    Point(x = 379109.7436289962, y = 6677820.218810541),
    startLength = segment2.start + segment2.length
)
val alignment = alignment(segment1, segment2, segment3)
val startAddress = TrackMeter(KmNumber(2), 150)
val referenceLine = referenceLine(IntId(1), alignment = alignment, startAddress = startAddress)
val addressPoints = listOf(
    GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN),
    GeocodingReferencePoint(KmNumber(3), BigDecimal.ZERO, alignment.length / 5, 0.0, WITHIN),
    GeocodingReferencePoint(KmNumber(4), BigDecimal.ZERO, 2 * alignment.length / 5, 0.0, WITHIN),
    GeocodingReferencePoint(KmNumber(5,"A"), BigDecimal.ZERO, 3 * alignment.length / 5, 0.0, WITHIN),
    GeocodingReferencePoint(KmNumber(5, "B"), BigDecimal.ZERO, 4 * alignment.length / 5, 0.0, WITHIN),
)
val trackNumber = trackNumber(TrackNumber("T001"))
val context = GeocodingContext(
    trackNumber,
    referenceLine,
    alignment,
    addressPoints,
    // test-data is inaccurate so allow more delta in validation
    projectionLineDistanceDeviation = 0.05,
    projectionLineMaxAngleDelta = PI / 16,
)

class GeocodingTest {

    @Test
    fun contextDistancesWorkForSegmentEnds() {
        for (segment in alignment.segments) {
            val startResult = context.getDistance(segment.points.first())
            assertEquals(WITHIN, startResult?.second)
            assertEquals(segment.start, startResult!!.first, DELTA)
            val endResult = context.getDistance(segment.points.last())
            assertEquals(WITHIN, endResult?.second)
            assertEquals(segment.start + segment.length, endResult!!.first, DELTA)
        }
    }

    @Test
    fun contextDistancesWorkAlongLine() {
        for (segment in alignment.segments) {
            val midMinusOne = toPoint(segment.points[1])
            val midPlusOne = toPoint(segment.points[2])
            val midPoint = (midMinusOne + midPlusOne) / 2.0

            val midDistance = context.getDistance(midPoint)!!.first
            assertTrue(context.getDistance(midMinusOne)!!.first < midDistance)
            assertTrue(context.getDistance(midPlusOne)!!.first > midDistance)

            val lineAngle = directionBetweenPoints(midMinusOne, midPlusOne)
            val offsetPoint = pointInDirection(midPoint, 10.0, lineAngle - PI / 2)
            assertEquals(midDistance, context.getDistance(offsetPoint)!!.first, DELTA)

            val offsetPoint2 = pointInDirection(midPoint, 20.0, lineAngle + PI / 2)
            assertEquals(midDistance, context.getDistance(offsetPoint2)!!.first, DELTA)
        }
    }

    @Test
    fun contextFindsAddressForDistance() {
        assertEquals(startAddress, context.getAddress(0.0, startAddress.decimalCount()))

        val lastPoint = addressPoints.last()
        val endLength = referenceLine.length
        assertEquals(
            TrackMeter(lastPoint.kmNumber, endLength - lastPoint.distance, 3),
            context.getAddress(endLength, 3)
        )

        for (ap in addressPoints) {
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 0.0, 3),
                context.getAddress(ap.distance, 3))
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 10.0, 3),
                context.getAddress(ap.distance + 10.0, 3))
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 100.0, 3),
                context.getAddress(ap.distance + 100.0, 3))
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 300.0, 3),
                context.getAddress(ap.distance + 300.0, 3))
        }
    }

    @Test
    fun projectionLinesAndReverseGeocodingAgree() {
        context.projectionLines.forEach { proj ->
            val decimals = proj.address.decimalCount()
            assertEquals(proj.address, context.getAddress(proj.projection.start, decimals)!!.first)
            val pointAside = linePointAtDistance(proj.projection, 1.0)
            assertEquals(proj.address, context.getAddress(pointAside, decimals)!!.first)
        }
    }

    @Test
    fun projectionIsFoundForAddress() {
        var projection = context.getProjectionLine(TrackMeter(KmNumber(5,"A"), 0))
        assertNotNull(projection)
        assertEquals(TrackMeter(KmNumber(5,"A"), 0), projection.address)

        projection = context.getProjectionLine(TrackMeter(KmNumber(5,"A"), 10))
        assertNotNull(projection)
        assertEquals(TrackMeter(KmNumber(5,"A"), 10), projection.address)

        projection = context.getProjectionLine(TrackMeter(KmNumber(5,"A"), 10.6, 1))
        assertNotNull(projection)
        assertEquals(TrackMeter(KmNumber(5,"A"), 10), projection.address)
    }

    @Test
    fun generatedSegmentsProjectWithSurroundingDirection() {
        val startSegment = segment(
            Point(-0.7, 0.0),
            Point(1.0, 2.0),
            Point(3.0, 4.0),
            startLength = 10.0,
            source = PLAN,
        )
        val connectSegment = segment(
            Point(3.0,4.0),
            Point(3.0,6.0),
            startLength = startSegment.start + startSegment.length,
            source = GENERATED,
        )
        val endSegment = segment(
            Point(3.0,6.0),
            Point(7.0,10.0),
            startLength = connectSegment.start + connectSegment.length,
            source = PLAN,
        )
        val startAddress = TrackMeter(KmNumber(2), 100)
        val ctx = GeocodingContext.create(
            trackNumber = trackNumber(TrackNumber("T001")),
            referenceLine = referenceLine(IntId(1), alignment, startAddress),
            referenceLineGeometry = alignment(startSegment, connectSegment, endSegment),
            kmPosts = listOf(),
        )

        val addressPoints = ctx.getAddressPoints(alignment(segment(
            Point(7.0, -2.0),
            Point(7.0, 8.0),
        )))
        assertNotNull(addressPoints)

        assertTrue((Point(7.0, -2.0)).isSame(addressPoints.startPoint.point, 0.0001))
        assertTrue((Point(7.0, 8.0)).isSame(addressPoints.endPoint.point, 0.0001))

        assertEquals(7, addressPoints.midPoints.size)
        addressPoints.midPoints.forEachIndexed { index, ap ->
            assertEquals(7.0, ap.point.x, 0.0001)
            val previous = addressPoints.midPoints.getOrNull(index-1) ?: addressPoints.startPoint
            val next = addressPoints.midPoints.getOrNull(index+1) ?: addressPoints.endPoint
            assertTrue(ap.point.y > previous.point.y)
            assertTrue(ap.point.y < next.point.y)
            // Verify that the given point also reverse geocodes to the same address
            assertEquals(ap.address, ctx.getAddress(ap.point, ap.address.decimalCount())!!.first)
        }
    }

    @Test
    fun getIntersectionWorks() {
        val projection1 = Line(Point(100.0, 100.0), Point(0.0, 200.0))
        val projection2 = Line(Point(60.0, 175.0), Point(80.0, 195.0))
        val projection3 = Line(Point(195.0, 200.0), Point(145.0, 250.0))
        val points = toTrackLayoutPoints(
            Point(0.0, 0.0),
            Point(20.0, 50.0),
            Point(20.0, 100.0),
            // This interval is where projection1 should hit at 50,150 = 0.5
            Point(80.0, 200.0),
            // This interval is where projection2 should hit at 90,205 = 0.25
            Point(120.0, 220.0),
            Point(140.0, 220.0),
            Point(160.0, 230.0),
            // This interval is where projection3 should hit at 170,225 = 0.5
            Point(180.0, 220.0),
        )
        val edges = points.mapIndexedNotNull { index: Int, point: LayoutPoint ->
            if (index == 0) null else PolyLineEdge(
                start = points[index - 1],
                end = point,
                segmentStart = 0.0,
                projectionDirection = directionBetweenPoints(points[index - 1], point),
            )
        }

        val intersection1 = getIntersection(projection1, edges)
        assertNotNull(intersection1)
        assertEquals(edges[2], intersection1.first)
        assertEquals(0.5, intersection1.second)

        val intersection2 = getIntersection(projection2, edges)
        assertNotNull(intersection2)
        assertEquals(edges[3], intersection2.first)
        assertEquals(0.25, intersection2.second)

        val intersection3 = getIntersection(projection3, edges)
        assertNotNull(intersection3)
        assertEquals(edges[6], intersection3.first)
        assertEquals(0.5, intersection3.second)
    }

    @Test
    fun addressIsFoundForProjectionLine() {
        val address = TrackMeter(KmNumber(4), 50)
        val projection = context.getProjectionLine(address)
        assertNotNull(projection)
        assertEquals(address, projection.address)
        val addressPoint = getProjectedAddressPoint(projection, alignment)
        assertNotNull(addressPoint)
        assertEquals(address, addressPoint.address)
        assertEquals(2 * referenceLine.length / 5 + 50.0, addressPoint.distance, DELTA)
    }

    @Test
    fun projectionLinesAreCreatedCorrectly() {
        val start = Point(385757.97, 6672279.26)
        // Points along rising Y-axis for understandable test calculations
        val alignment = alignment(segment(
            start,
            start + Point(0.0, 2.0),
            start + Point(0.0, 4.0),
            start + Point(0.0, 6.0),
        ))
        val referenceLine = referenceLine(trackNumberId = IntId(1), alignment = alignment, startAddress = startAddress)
        val projectionContext = GeocodingContext(
            trackNumber = trackNumber,
            referenceLine = referenceLine,
            referenceLineGeometry = alignment,
            listOf(
                GeocodingReferencePoint(KmNumber(2), BigDecimal("100.0"), 0.0, 0.0, WITHIN),
                GeocodingReferencePoint(KmNumber(3), BigDecimal("0.0"), 3.0, 0.0, WITHIN),
            )
        )
        // As lines go straight up, projections should go 100m to the left from there
        val projectionOffset = Point(-100.0, 0.0)
        fun projectionLine(point: Point): Line = Line(point, point + projectionOffset)

        assertProjectionLinesMatch(
            projectionContext.projectionLines,
            TrackMeter(2, 100) to projectionLine(start),
            TrackMeter(2, 101) to projectionLine(start + Point(0.0, 1.0)),
            TrackMeter(2, 102) to projectionLine(start + Point(0.0, 2.0)),
            TrackMeter(3, 0) to projectionLine(start + Point(0.0, 3.0)),
            TrackMeter(3, 1) to projectionLine(start + Point(0.0, 4.0)),
            TrackMeter(3, 2) to projectionLine(start + Point(0.0, 5.0)),
            TrackMeter(3, 3) to projectionLine(start + Point(0.0, 6.0)),
        )
    }

    @Test
    fun projectionsWorkOnVerticalVsDiagonal() {
        val start = Point(428123.459, 7208379.993)

        val verticalPoints = toTrackLayoutPoints((0..3).map { n ->
            Point3DM(start.x + 0.0, start.y + 3 * n.toDouble(), 3 * n.toDouble())
        })
        val verticalAlignment = alignment(segment(verticalPoints))
        val verticalReference = referenceLine(trackNumberId = IntId(1), alignment = verticalAlignment, startAddress = startAddress)
        val verticalContext = GeocodingContext(trackNumber, verticalReference, verticalAlignment, listOf(
            GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN)
        ))
        val diagonalLine = alignment(segment(
            start + Point(-52.5, 2.5),
            start + Point(-47.5, 7.5),
        ))

        val diagonalCenter = start + Point(-50.0, 5.0)
        assertEquals(5.0, verticalContext.getDistance(diagonalCenter)!!.first, 0.000001)
        assertEquals(startAddress + 5.0, verticalContext.getAddress(5.0, startAddress.decimalCount()))

        val projectionLine = verticalContext.getProjectionLine(startAddress + 5.0)
        assertEquals(startAddress + 5.0, projectionLine!!.address)
        assertApproximatelyEquals(start + Point(0.0, 5.0), projectionLine.projection.start, 0.000001)
        assertApproximatelyEquals(start + Point(-100.0, 5.0), projectionLine.projection.end, 0.000001)
        assertEquals(PI, projectionLine.projection.angle, 0.000001)

        val geocoded = getProjectedAddressPoint(projectionLine, diagonalLine)
        assertEquals(startAddress.floor(0) + 5, geocoded!!.address)
        assertApproximatelyEquals(diagonalCenter, geocoded.point, 0.000001)
    }

    @Test
    fun sublistForRange() {
        assertEquals(listOf(2, 3, 4), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..4), Integer::compare))
        assertEquals(listOf(2, 3   ), getSublistForRangeInOrderedList(listOf(1, 2, 3,    5), (2..4), Integer::compare))
        assertEquals(listOf(   3, 4), getSublistForRangeInOrderedList(listOf(1,    3, 4, 5), (2..4), Integer::compare))
        assertEquals(listOf(2, 3, 4, 5), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..5), Integer::compare))
        assertEquals(listOf(2, 3, 4, 5), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..6), Integer::compare))
        assertEquals(listOf(1, 2, 3, 4), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (1..4), Integer::compare))
        assertEquals(listOf(1, 2, 3, 4), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (0..4), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (0..0), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (6..6), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..0), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..1), Integer::compare))
    }

    private fun assertProjectionLinesMatch(result: List<ProjectionLine>, vararg expected: Pair<TrackMeter, Line>) {
        assertEquals(expected.size, result.size,
            "expectedSize=${expected.size} actualSize=${result.size} expected=$expected actual=$result")
        result.forEachIndexed { index, projectionLine ->
            val (address, line) = expected[index]
            assertEquals(address, projectionLine.address)
            assertEquals(line.start.x, projectionLine.projection.start.x, 2 * DELTA)
            assertEquals(line.start.y, projectionLine.projection.start.y, 2 * DELTA)
            assertEquals(line.end.x, projectionLine.projection.end.x, 2 * DELTA)
            assertEquals(line.end.y, projectionLine.projection.end.y, 2 * DELTA)
        }
    }

    private fun toPoint(layoutPoint: LayoutPoint) = Point(layoutPoint.x, layoutPoint.y)
}
