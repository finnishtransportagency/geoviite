package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.linePointAtDistance
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.alignmentFromPoints
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

const val DELTA = 0.000001

val segment1 =
    segment(
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
    )
val segment2 =
    segment(
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
    )
val segment3 =
    segment(
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
    )
val alignment = alignment(segment1, segment2, segment3)
val startAddress = TrackMeter(KmNumber(2), 150)
val addressPoints =
    listOf(
        GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN),
        GeocodingReferencePoint(KmNumber(3), BigDecimal.ZERO, alignment.length / 5, 0.0, WITHIN),
        GeocodingReferencePoint(KmNumber(4), BigDecimal.ZERO, 2 * alignment.length / 5, 0.0, WITHIN),
        GeocodingReferencePoint(KmNumber(5, "A"), BigDecimal.ZERO, 3 * alignment.length / 5, 0.0, WITHIN),
        GeocodingReferencePoint(KmNumber(5, "B"), BigDecimal.ZERO, 4 * alignment.length / 5, 0.0, WITHIN),
    )
val trackNumber: TrackNumber = TrackNumber("T001")
val kmPostLocations =
    (0..5).map { i ->
        val alignmentPoint = alignment.getPointAtM(i * alignment.length / 5)
        checkNotNull(alignmentPoint?.toPoint())
    }

val kmPosts =
    listOf(
        kmPost(
            trackNumberId = null,
            km = startAddress.kmNumber,
            roughLayoutLocation = kmPostLocations[0],
            draft = false,
        ),
        kmPost(trackNumberId = null, km = KmNumber(3), roughLayoutLocation = kmPostLocations[1], draft = false),
        kmPost(trackNumberId = null, km = KmNumber(4), roughLayoutLocation = kmPostLocations[2], draft = false),
        kmPost(trackNumberId = null, km = KmNumber(5, "A"), roughLayoutLocation = kmPostLocations[3], draft = false),
        kmPost(trackNumberId = null, km = KmNumber(5, "B"), roughLayoutLocation = kmPostLocations[4], draft = false),
    )
val context =
    GeocodingContext(
        trackNumber,
        alignment,
        addressPoints,
        // test-data is inaccurate so allow more delta in validation
        projectionLineDistanceDeviation = 0.05,
        projectionLineMaxAngleDelta = PI / 16,
    )

class GeocodingTest {

    @Test
    fun cutRangeByKmsCutsToReferenceLineEnds() {
        val endAddress = context.projectionLines.getValue(Resolution.ONE_METER).value.last().address
        assertEquals(
            listOf(startAddress..endAddress),
            context.cutRangeByKms((startAddress - 100.0)..(endAddress + 100.0), context.allKms.toSet()),
        )
        assertEquals(
            listOf(),
            context.cutRangeByKms((endAddress + 100.0)..(endAddress + 200.0), context.allKms.toSet()),
        )
    }

    @Test
    fun cutRangeByKmsSplitsOnMissingKm() {
        val endAddress = context.projectionLines.getValue(Resolution.ONE_METER).value.last().address
        val km3LastAddress = getLastAddress(KmNumber(3))!!
        val km5LastAddress = getLastAddress(KmNumber(5, "A"))!!
        assertEquals(
            listOf((startAddress + 100.0)..km3LastAddress, TrackMeter(KmNumber(5, "A"), 0)..km5LastAddress),
            context.cutRangeByKms(
                (startAddress + 100.0)..endAddress,
                setOf(startAddress.kmNumber, KmNumber(3), KmNumber(5, "A")),
            ),
        )

        assertEquals(
            listOf(TrackMeter(KmNumber(3), 0)..km3LastAddress, TrackMeter(KmNumber(5, "A"), 0)..(endAddress - 100.0)),
            context.cutRangeByKms(
                startAddress..(endAddress - 100.0),
                setOf(KmNumber(3), KmNumber(5, "A"), KmNumber(5, "B")),
            ),
        )
    }

    @Test
    fun contextDistancesWorkForSegmentEnds() {
        for ((segment, m) in alignment.segmentsWithM) {
            val startResult = context.getM(segment.segmentStart)
            val endResult = context.getM(segment.segmentEnd)
            assertEquals(WITHIN, startResult?.second)
            assertEquals(m.min, startResult!!.first, DELTA)
            assertEquals(WITHIN, endResult?.second)
            assertEquals(m.max, endResult!!.first, DELTA)
        }
    }

    @Test
    fun contextDistancesWorkAlongLine() {
        for (segment in alignment.segments) {
            val midMinusOne = segment.segmentPoints[1]
            val midPlusOne = segment.segmentPoints[2]
            val midPoint = (midMinusOne + midPlusOne) / 2.0

            val midDistance = context.getM(midPoint)!!.first
            assertTrue(context.getM(midMinusOne)!!.first < midDistance)
            assertTrue(context.getM(midPlusOne)!!.first > midDistance)

            val lineAngle = directionBetweenPoints(midMinusOne, midPlusOne)
            val offsetPoint = pointInDirection(midPoint, 10.0, lineAngle - PI / 2)
            assertEquals(midDistance, context.getM(offsetPoint)!!.first, DELTA)

            val offsetPoint2 = pointInDirection(midPoint, 20.0, lineAngle + PI / 2)
            assertEquals(midDistance, context.getM(offsetPoint2)!!.first, DELTA)
        }
    }

    @Test
    fun contextFindsAddressForDistance() {
        assertEquals(startAddress, context.getAddress(0.0, startAddress.decimalCount()))

        val lastPoint = addressPoints.last()
        val endLength: Double = alignment.length
        assertEquals(
            TrackMeter(lastPoint.kmNumber, endLength - lastPoint.distance, 3),
            context.getAddress(endLength, 3),
        )

        for (ap in addressPoints) {
            assertEquals(TrackMeter(ap.kmNumber, ap.meters.toDouble() + 0.0, 3), context.getAddress(ap.distance, 3))
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 10.0, 3),
                context.getAddress(ap.distance + 10.0, 3),
            )
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 100.0, 3),
                context.getAddress(ap.distance + 100.0, 3),
            )
            assertEquals(
                TrackMeter(ap.kmNumber, ap.meters.toDouble() + 300.0, 3),
                context.getAddress(ap.distance + 300.0, 3),
            )
        }
    }

    @Test
    fun projectionLinesAndReverseGeocodingAgree() {
        val projections =
            (listOf(context.startProjection) +
                context.projectionLines.getValue(Resolution.ONE_METER).value +
                listOf(context.endProjection))
        projections.forEachIndexed { index, proj ->
            assertNotNull(proj) // not a test assert, but they should in fact be not null
            if (index > 0)
                assertTrue(
                    projections[index - 1]!!.address <= proj.address,
                    "Projections should be in increasing order: index=$index prev=${projections[index-1]!!.address} next=${proj.address}",
                )
            val decimals = proj.address.decimalCount()
            assertEquals(proj.address, context.getAddress(proj.projection.start, decimals)!!.first)
            val pointAside = linePointAtDistance(proj.projection, 1.0)
            assertEquals(proj.address, context.getAddress(pointAside, decimals)!!.first)
        }
    }

    @Test
    fun projectionIsFoundForAddress() {
        listOf(
                TrackMeter(KmNumber(5, "A"), 0),
                TrackMeter(KmNumber(5, "A"), 10),
                TrackMeter(KmNumber(5, "A"), 10.6, 1),
                TrackMeter(KmNumber(5, "A"), 10.152, 3),
            )
            .forEach { address ->
                val projection = context.getProjectionLine(address)
                assertNotNull(projection)
                assertEquals(address, projection.address)
            }
    }

    @Test
    fun generatedSegmentsProjectWithSurroundingDirection() {
        val startSegment = segment(Point(-0.7, 0.0), Point(1.0, 2.0), Point(3.0, 4.0), source = PLAN)
        val connectSegment = segment(Point(3.0, 4.0), Point(3.0, 6.0), source = GENERATED)
        val endSegment = segment(Point(3.0, 6.0), Point(7.0, 10.0), source = PLAN)
        val startAddress = TrackMeter(KmNumber(2), 100)
        val ctx =
            GeocodingContext.create(
                    trackNumber = TrackNumber("T001"),
                    startAddress = startAddress,
                    referenceLineGeometry = alignment(startSegment, connectSegment, endSegment),
                    kmPosts = listOf(),
                )
                .geocodingContext

        val addressPoints = ctx.getAddressPoints(alignment(segment(Point(7.0, -2.0), Point(7.0, 8.0))))
        assertNotNull(addressPoints)

        assertTrue((Point(7.0, -2.0)).isSame(addressPoints.startPoint.point, 0.0001))
        assertTrue((Point(7.0, 8.0)).isSame(addressPoints.endPoint.point, 0.0001))

        assertEquals(7, addressPoints.midPoints.size)
        addressPoints.midPoints.forEachIndexed { index, ap ->
            assertEquals(7.0, ap.point.x, 0.0001)
            val previous = addressPoints.midPoints.getOrNull(index - 1) ?: addressPoints.startPoint
            val next = addressPoints.midPoints.getOrNull(index + 1) ?: addressPoints.endPoint
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
        val points1 =
            toSegmentPoints(
                Point(0.0, 0.0),
                Point(20.0, 50.0),
                Point(20.0, 100.0),
                // This interval is where projection1 should hit at 50,150 = 0.5
                Point(80.0, 200.0),
                // This interval is where projection2 should hit at 90,205 = 0.25
                Point(120.0, 220.0),
            )
        val points2 =
            toSegmentPoints(
                Point(140.0, 220.0),
                Point(160.0, 230.0),
                // This interval is where projection3 should hit at 170,225 = 0.5
                Point(180.0, 220.0),
            )
        val points = points1 + points2
        val edges =
            points.mapIndexedNotNull { index: Int, point: SegmentPoint ->
                if (index == 0) null
                else
                    PolyLineEdge(
                        start = points[index - 1],
                        end = point,
                        segmentStart = if (index <= points1.lastIndex) 0.0 else points1.last().m,
                        referenceDirection = directionBetweenPoints(points[index - 1], point),
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
    }

    @Test
    fun projectionLinesAreCreatedCorrectly() {
        val start = Point(385757.97, 6672279.26)
        // Points along rising Y-axis for understandable test calculations
        val alignment =
            alignmentFromPoints(start, start + Point(0.0, 2.0), start + Point(0.0, 4.0), start + Point(0.0, 6.0))
        val projectionContext =
            GeocodingContext(
                trackNumber = trackNumber,
                referenceLineGeometry = alignment,
                referencePoints =
                    listOf(
                        GeocodingReferencePoint(KmNumber(2), BigDecimal("100.0"), 0.0, 0.0, WITHIN),
                        GeocodingReferencePoint(KmNumber(3), BigDecimal("0.0"), 3.0, 0.0, WITHIN),
                    ),
            )
        // As lines go straight up, projections should go 100m to the left from there
        val projectionOffset = Point(-100.0, 0.0)
        fun projectionLine(point: Point): Line = Line(point, point + projectionOffset)

        // Cached projections for 1m lines
        assertProjectionLinesMatch(
            projectionContext.projectionLines.getValue(Resolution.ONE_METER).value,
            TrackMeter(2, 100) to projectionLine(start),
            TrackMeter(2, 101) to projectionLine(start + Point(0.0, 1.0)),
            TrackMeter(2, 102) to projectionLine(start + Point(0.0, 2.0)),
            TrackMeter(3, 0) to projectionLine(start + Point(0.0, 3.0)),
            TrackMeter(3, 1) to projectionLine(start + Point(0.0, 4.0)),
            TrackMeter(3, 2) to projectionLine(start + Point(0.0, 5.0)),
            TrackMeter(3, 3) to projectionLine(start + Point(0.0, 6.0)),
        )

        // Dynamically created projections between even meters
        listOf(
                TrackMeter(2, 101.000, 3) to start + Point(0.0, 1.0),
                TrackMeter(2, 101.1, 1) to start + Point(0.0, 1.1),
                TrackMeter(2, 101.92, 2) to start + Point(0.0, 1.92),
                TrackMeter(2, 101.456, 3) to start + Point(0.0, 1.456),
                TrackMeter(3, 0.12, 2) to start + Point(0.0, 3.12),
            )
            .forEach { (address, point) ->
                val projectionLine = projectionContext.getProjectionLine(address)
                assertNotNull(projectionLine, "Expected to find a projection line for address $address (was null)")
                assertProjectionLineMatches(projectionLine, address, projectionLine(point))
            }
    }

    @Test
    fun geocodingWorks() {
        // Straight horizontal reference line for understandable calc
        val context =
            createContext(
                geometryPoints = listOf(Point(0.0, 0.0), Point(10.0, 0.0)),
                startAddress = TrackMeter(1, "51.4"),
                referencePoints = listOf(TrackMeter(2, "0.0") to 5.0),
            )
        // Geocode on 45deg diagonal alignment above the reference line
        val trackAlignment = alignment(segment(Point(1.1, 1.1), Point(8.9, 8.9)))

        // Verify basic ends + 1m points
        val addressPoints = context.getAddressPoints(trackAlignment)
        assertAddressPoint(addressPoints?.startPoint, TrackMeter(1, "52.500"), trackAlignment.start!!)
        assertAddressPoint(addressPoints?.endPoint, TrackMeter(2, "3.900"), trackAlignment.end!!)
        listOf(
                TrackMeter(1, "53") to Point(1.6, 1.6),
                TrackMeter(1, "54") to Point(2.6, 2.6),
                TrackMeter(1, "55") to Point(3.6, 3.6),
                TrackMeter(1, "56") to Point(4.6, 4.6),
                TrackMeter(2, "0") to Point(5.0, 5.0),
                TrackMeter(2, "1") to Point(6.0, 6.0),
                TrackMeter(2, "2") to Point(7.0, 7.0),
                TrackMeter(2, "3") to Point(8.0, 8.0),
            )
            .forEachIndexed { index, (address, location) ->
                assertAddressPoint(addressPoints?.midPoints?.getOrNull(index), address, location)
            }

        // Verify individual, accurately requested points
        listOf(
                TrackMeter(1, "52.500") to Point(1.1, 1.1), // start
                TrackMeter(2, "3.900") to Point(8.9, 8.9), // end
                TrackMeter(1, "52.501") to Point(1.101, 1.101),
                TrackMeter(2, "3.899") to Point(8.899, 8.899),
                TrackMeter(1, "54.132") to Point(2.732, 2.732),
                TrackMeter(1, "56.1") to Point(4.7, 4.7),
                TrackMeter(2, "0.1") to Point(5.1, 5.1),
                TrackMeter(2, "3.12") to Point(8.12, 8.12),
            )
            .also { addressesAndLocations ->
                context.getTrackLocations(trackAlignment, addressesAndLocations.map { it.first }).zip(
                    addressesAndLocations
                ) { addressPoint, addressAndLocation ->
                    assertAddressPoint(addressPoint, addressAndLocation.first, addressAndLocation.second)
                }
            }
            .forEach { (address, location) ->
                assertAddressPoint(context.getTrackLocation(trackAlignment, address), address, location)
            }
    }

    @Test
    fun `Location track zigzag doesn't prevent calculating address points`() {
        val context =
            createContext(
                geometryPoints = listOf(Point(0.0, 0.0), Point(10.0, 0.0)),
                startAddress = TrackMeter(1, "100.0"),
                referencePoints = listOf(),
            )
        val trackAlignment =
            alignment(
                segment(Point(1.0, 1.0), Point(5.0, 1.0)),
                segment(Point(5.0, 1.0), Point(2.0, 1.1)), // zig-zag connector
                segment(Point(2.0, 1.1), Point(9.0, 1.1)),
            )
        val points = context.getAddressPoints(trackAlignment)!!

        assertEquals(TrackMeter(1, "0101.000".toBigDecimal()), points.startPoint.address)
        assertApproximatelyEquals(Point(x = 1.0, y = 1.0), points.startPoint.point)
        assertEquals(TrackMeter(1, "0109.000".toBigDecimal()), points.endPoint.address)
        assertApproximatelyEquals(Point(x = 9.0, y = 1.1), points.endPoint.point)

        assertEquals(
            listOf(
                TrackMeter(1, "0102".toBigDecimal()),
                TrackMeter(1, "0103".toBigDecimal()),
                TrackMeter(1, "0104".toBigDecimal()),
                TrackMeter(1, "0105".toBigDecimal()),
                TrackMeter(1, "0106".toBigDecimal()),
                TrackMeter(1, "0107".toBigDecimal()),
                TrackMeter(1, "0108".toBigDecimal()),
            ),
            points.midPoints.map { it.address },
        )
        assertApproximatelyEquals(Point(x = 2.0, y = 1.0), points.midPoints[0].point)
        assertApproximatelyEquals(Point(x = 3.0, y = 1.0), points.midPoints[1].point)
        assertApproximatelyEquals(Point(x = 4.0, y = 1.0), points.midPoints[2].point)
        assertApproximatelyEquals(Point(x = 5.0, y = 1.0), points.midPoints[3].point)
        assertApproximatelyEquals(Point(x = 6.0, y = 1.1), points.midPoints[4].point)
        assertApproximatelyEquals(Point(x = 7.0, y = 1.1), points.midPoints[5].point)
        assertApproximatelyEquals(Point(x = 8.0, y = 1.1), points.midPoints[6].point)
    }

    private fun assertAddressPoint(point: AddressPoint?, address: TrackMeter, location: IPoint) {
        assertNotNull(point, "Expected point at: address=$address location=$location")
        assertEquals(address, point.address)
        assertApproximatelyEquals(location, point.point, DELTA)
    }

    private fun createContext(
        geometryPoints: List<Point>,
        startAddress: TrackMeter,
        referencePoints: List<Pair<TrackMeter, Double>>,
    ): GeocodingContext {
        val alignment = alignment(segment(*geometryPoints.toTypedArray()))
        val startRefPoint =
            GeocodingReferencePoint(
                kmNumber = startAddress.kmNumber,
                meters = startAddress.meters,
                distance = 0.0,
                kmPostOffset = 0.0,
                intersectType = WITHIN,
            )
        val combinedReferencePoints =
            listOf(startRefPoint) +
                referencePoints.map { (address, distance) ->
                    GeocodingReferencePoint(
                        kmNumber = address.kmNumber,
                        meters = address.meters,
                        distance = distance,
                        kmPostOffset = 0.0,
                        intersectType = WITHIN,
                    )
                }

        return GeocodingContext(
            trackNumber = trackNumber,
            referenceLineGeometry = alignment,
            referencePoints = combinedReferencePoints,
        )
    }

    @Test
    fun projectionsWorkOnVerticalVsDiagonal() {
        val start = Point(428123.459, 7208379.993)

        val verticalPoints =
            toSegmentPoints((0..3).map { n -> Point3DM(start.x + 0.0, start.y + 3 * n.toDouble(), 3 * n.toDouble()) })
        val verticalAlignment = alignment(segment(verticalPoints))
        val verticalContext =
            GeocodingContext(
                trackNumber,
                verticalAlignment,
                listOf(
                    GeocodingReferencePoint(
                        kmNumber = startAddress.kmNumber,
                        meters = startAddress.meters,
                        distance = 0.0,
                        kmPostOffset = 0.0,
                        intersectType = WITHIN,
                    )
                ),
            )
        val diagonalLine = alignmentFromPoints(start + Point(-52.5, 2.5), start + Point(-47.5, 7.5))

        val diagonalCenter = start + Point(-50.0, 5.0)
        assertEquals(5.0, verticalContext.getM(diagonalCenter)!!.first, 0.000001)
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
        assertEquals(listOf(2, 3), getSublistForRangeInOrderedList(listOf(1, 2, 3, 5), (2..4), Integer::compare))
        assertEquals(listOf(3, 4), getSublistForRangeInOrderedList(listOf(1, 3, 4, 5), (2..4), Integer::compare))
        assertEquals(
            listOf(2, 3, 4, 5),
            getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..5), Integer::compare),
        )
        assertEquals(
            listOf(2, 3, 4, 5),
            getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..6), Integer::compare),
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (1..4), Integer::compare),
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (0..4), Integer::compare),
        )
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (0..0), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (6..6), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..0), Integer::compare))
        assertEquals(listOf(), getSublistForRangeInOrderedList(listOf(1, 2, 3, 4, 5), (2..1), Integer::compare))
    }

    @Test
    fun switchPointsAreFetchedCorrectly() {
        val start = Point(385757.97, 6672279.26)
        val referenceLineAlignment = alignment(segment(start, start + Point(0.0, 100.0)))
        val referenceLine =
            referenceLine(
                trackNumberId = IntId(1),
                alignment = referenceLineAlignment,
                startAddress = TrackMeter(10, 0),
                draft = false,
            )
        val testContext =
            GeocodingContext.create(
                    trackNumber = trackNumber,
                    startAddress = referenceLine.startAddress,
                    referenceLineGeometry = referenceLineAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext

        val result =
            testContext.getSwitchPoints(
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(IntId(1), 1),
                        segments = listOf(segment(start + Point(0.0, 1.0), start + Point(0.0, 5.5))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(1), 1),
                        endInnerSwitch = switchLinkYV(IntId(1), 5),
                        segments = listOf(segment(start + Point(0.0, 5.5), start + Point(0.0, 15.5))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(1), 5),
                        endInnerSwitch = switchLinkYV(IntId(1), 2),
                        segments =
                            listOf(
                                segment(start + Point(0.0, 15.5), start + Point(0.0, 25.5)),
                                segment(start + Point(0.0, 25.5), start + Point(0.0, 35.5)),
                                segment(start + Point(0.0, 35.5), start + Point(0.0, 45.5)),
                            ),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(1), 2),
                        endOuterSwitch = switchLinkYV(IntId(2), 1),
                        segments = listOf(segment(start + Point(0.0, 45.5), start + Point(0.0, 55.5))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(2), 1),
                        endInnerSwitch = switchLinkYV(IntId(2), 5),
                        segments = listOf(segment(start + Point(0.0, 55.5), start + Point(0.0, 65.5))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(2), 5),
                        endInnerSwitch = switchLinkYV(IntId(2), 2),
                        segments =
                            listOf(
                                segment(start + Point(0.0, 65.5), start + Point(0.0, 75.5)),
                                segment(start + Point(0.0, 75.5), start + Point(0.0, 85.5)),
                            ),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(2), 2),
                        segments = listOf(segment(start + Point(0.0, 85.5), start + Point(0.0, 95.5))),
                    ),
                )
            )

        assertEquals(
            listOf(
                start + Point(0.0, 5.5), // switch 1, joint 1
                start + Point(0.0, 15.5), // switch 1, joint 5
                start + Point(0.0, 45.5), // switch 1, joint 2
                start + Point(0.0, 55.5), // switch 2, joint 1
                start + Point(0.0, 65.5), // switch 2, joint 5
                start + Point(0.0, 85.5), // switch 2, joint 2
            ),
            result.map { p -> p.point.toPoint() },
        )

        result.forEachIndexed { index, jointPoint ->
            assertEquals(3, jointPoint.address.decimalCount())
            if (index > 0) {
                assertTrue(jointPoint.address > result[index - 1].address)
            }
        }
    }

    @Test
    fun `should throw an exception with empty reference line geometry`() {
        val startAlignment = LayoutAlignment(segments = emptyList())

        assertThrows<IllegalArgumentException>("Geocoding context was created with empty reference line") {
            GeocodingContext(
                trackNumber = TrackNumber("T001"),
                referenceLineGeometry = startAlignment,
                referencePoints = emptyList(),
            )
        }
    }

    @Test
    fun `should throw an exception when there are no reference points`() {
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        assertThrows<IllegalArgumentException>("Geocoding context was created without reference points") {
            GeocodingContext(
                trackNumber = TrackNumber("T001"),
                referenceLineGeometry = startAlignment,
                referencePoints = emptyList(),
            )
        }
    }

    @Test
    fun `should reject km posts without location`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val kmPost = kmPost(IntId(1), KmNumber(11), null, draft = false)

        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertTrue("Km post without location was not rejected") {
            result.rejectedKmPosts.all { kp ->
                kp.kmPost == kmPost && kp.rejectedReason == KmPostRejectedReason.NO_LOCATION
            }
        }

        assertTrue("Geocoding context contained km posts without location") { result.validKmPosts.isEmpty() }
    }

    @Test
    fun `should reject km posts that are before start address`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val kmPost = kmPost(IntId(1), KmNumber(10), Point(5.0, 0.0), draft = false)

        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertTrue("Km post with too small km number was not rejected") {
            result.rejectedKmPosts.all { kp ->
                kp.kmPost == kmPost && kp.rejectedReason == KmPostRejectedReason.IS_BEFORE_START_ADDRESS
            }
        }
    }

    @Test
    fun `should reject km posts that intersects before reference line`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val kmPost = kmPost(IntId(1), KmNumber(11), Point(-5.0, 0.0), draft = false)

        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertTrue("Km post that intersected before reference line was not rejected") {
            result.rejectedKmPosts.all { kp ->
                kp.kmPost == kmPost && kp.rejectedReason == KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE
            }
        }
    }

    @Test
    fun `should reject km posts that intersects after reference line`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val kmPost = kmPost(IntId(1), KmNumber(11), Point(50.0, 0.0), draft = false)

        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertTrue("Km post that intersected after reference line was not rejected") {
            result.rejectedKmPosts.all { kp ->
                kp.kmPost == kmPost && kp.rejectedReason == KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE
            }
        }
    }

    @Test
    fun `should reject km posts that are too far apart`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(40000.0, 0.0))))

        val kmPost = kmPost(IntId(1), KmNumber(11), Point(20000.0, 0.0), draft = false)

        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertTrue("Km post that had over 10000 m in distance was not rejected") {
            result.rejectedKmPosts.all { kp ->
                kp.kmPost == kmPost && kp.rejectedReason == KmPostRejectedReason.TOO_FAR_APART
            }
        }
    }

    @Test
    fun `should require start km to have sane length`() {
        val trackNumber = TrackNumber("T001")
        val startAlignment = LayoutAlignment(segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))))
        val kmPost = kmPost(IntId(1), KmNumber(1), Point(15.0, 0.0), draft = false)
        val result =
            GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = TrackMeter(KmNumber(0), 9990),
                referenceLineGeometry = startAlignment,
                kmPosts = listOf(kmPost),
            )

        assertEquals(null, result.geocodingContext.getAddress(Point(14.0, 0.0)))
        assertEquals(StartPointRejectedReason.TOO_LONG, result.startPointRejectedReason)
    }

    @Test
    fun `AddressPoint withIntegerPrecision works`() {
        val point = AlignmentPoint(0.0, 0.0, 0.0, 0.0, null)
        assertEquals(
            AddressPoint(point, TrackMeter("1234+1234")),
            AddressPoint(point, TrackMeter("1234+1234")).withIntegerPrecision(),
        )
        assertEquals(
            AddressPoint(point, TrackMeter("1234+1234")),
            AddressPoint(point, TrackMeter("1234+1234.000")).withIntegerPrecision(),
        )
        assertNull(AddressPoint(point, TrackMeter("1234+1234.123")).withIntegerPrecision())
    }

    @Test
    fun `getTrackLocation simply fails if projection lines would be invalid`() {
        val referenceLineAlignment = LayoutAlignment(listOf(segment(Point(0.0, 0.0), Point(15000.0, 0.0))))
        val result =
            GeocodingContext.create(
                trackNumber = TrackNumber("001"),
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = referenceLineAlignment,
                kmPosts = listOf(),
            )
        assertNull(result.geocodingContext.getProjectionLine(TrackMeter(KmNumber(0), 100)))
    }

    @Test
    fun `referenceLineAddresses is null if the end address would have invalid meters`() {
        val referenceLineAlignment = LayoutAlignment(listOf(segment(Point(0.0, 0.0), Point(15000.0, 0.0))))
        val result =
            GeocodingContext.create(
                trackNumber = TrackNumber("001"),
                startAddress = TrackMeter(KmNumber(10), 100),
                referenceLineGeometry = referenceLineAlignment,
                kmPosts = listOf(),
            )
        assertNull(result.geocodingContext.referenceLineAddresses)
    }

    @Test
    fun `geocoding survives contact with terrible zigzag alignment`() {
        val terribleZigzag = listOf(0.0, 5.0, 3.0, 7.0, 4.0, 10.0)
        val terribleZigzagAlignment =
            alignment(
                terribleZigzag.zipWithNext { start, end ->
                    segment(Point(start.toDouble(), 0.0), Point(end.toDouble(), 0.0))
                }
            )
        val risingZigzagAlignment =
            alignment(
                terribleZigzag.zip(terribleZigzag.indices).zipWithNext { (start, i), (end, _) ->
                    segment(Point(start.toDouble(), i * 1.0), Point(end.toDouble(), (i + 1) * 1.0))
                }
            )

        val context =
            GeocodingContext.create(
                    trackNumber = TrackNumber("001"),
                    startAddress = TrackMeter(KmNumber(10), 100),
                    referenceLineGeometry = terribleZigzagAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext

        // the zigzag is so horrible that what the implementation actually does on it doesn't matter very much; but
        // at least the start and end addresses ought to be reasonable
        val risingAddresses = context.getAddressPoints(risingZigzagAlignment)!!
        assertEquals(0.0, risingAddresses.startPoint.point.x)
        assertEquals(10.0, risingAddresses.endPoint.point.x)

        val terribleAddresses = context.getAddressPoints(terribleZigzagAlignment)!!
        assertEquals(0.0, terribleAddresses.startPoint.point.x)
        assertEquals(10.0, terribleAddresses.endPoint.point.x)
    }

    @Test
    fun `getTrackLocations() can handle reverse order hits with very close addresses`() {
        val referenceLineAlignment =
            alignment(
                segment(Point(0.0, 100.0), Point(10.0, 100.0)), // reference line is convex toward track
                segment(Point(10.0, 100.0), Point(20.0, 99.9)),
            )
        val locationTrackAlignment = alignment(segment(Point(0.0, 0.0), Point(20.0, 0.0)))

        val context =
            GeocodingContext.create(
                    trackNumber = TrackNumber("001"),
                    startAddress = TrackMeter(KmNumber(0), 0),
                    referenceLineGeometry = referenceLineAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext

        val overallAddresses = (0..20).map { meter -> TrackMeter(KmNumber(0), meter) }
        val singleOverall =
            overallAddresses.map { address -> context.getTrackLocation(locationTrackAlignment, address) }
        val multiOverall = context.getTrackLocations(locationTrackAlignment, overallAddresses)
        assertEquals(singleOverall, multiOverall)

        val aroundBumpAddresses =
            (-10..10).map { milli ->
                TrackMeter(KmNumber(0), BigDecimal("0.001") * milli.toBigDecimal() + 10.toBigDecimal())
            }

        val singleAroundBump =
            aroundBumpAddresses.map { address -> context.getTrackLocation(locationTrackAlignment, address) }
        val multiAroundBump = context.getTrackLocations(locationTrackAlignment, aroundBumpAddresses)
        assertEquals(singleAroundBump, multiAroundBump)
        // Track addresses can hit a location track out of order. Note that since we're checking only successive lines,
        // we only see one bump, even though in this case the bump is steep enough that actually *all* addresses after
        // the bump hit before all addresses before it
        assertEquals(
            listOf((0..9).map { true }, listOf(false), (0..8).map { true }).flatten(),
            singleAroundBump.zipWithNext { a, b -> a!!.point.m < b!!.point.m },
        )
    }

    @Test
    fun `getAddressPoints() handles overly convex reference line without crashing`() {
        val referenceLineAlignment =
            alignment(
                segment(Point(0.0, 99.0), Point(5.0, 100.0)),
                segment(Point(5.0, 100.0), Point(15.0, 100.0)),
                segment(Point(15.0, 100.0), Point(20.0, 99.0)),
            )
        val locationTrackAlignment = alignment(segment(Point(0.0, 0.0), Point(20.0, 0.0)))
        val context =
            GeocodingContext.create(
                    trackNumber = TrackNumber("001"),
                    startAddress = TrackMeter(KmNumber(0), 0),
                    referenceLineGeometry = referenceLineAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext
        val addressPoints = context.getAddressPoints(locationTrackAlignment)!!
        // the reference line is broken enough that we can't say much about it at all, but at least we can say this
        assertTrue(addressPoints.midPoints.all { it.address > addressPoints.startPoint.address })
        assertTrue(addressPoints.midPoints.all { it.address < addressPoints.endPoint.address })
    }

    @Test
    fun `a non-meter address on the same meter as a non-meter reference line start can be geocoded`() {
        val referenceLineAlignment = alignment(segment(Point(0.0, 0.0), Point(0.0, 10.0)))
        val locationTrackAlignment = alignment(segment(Point(0.0, 0.0), Point(0.0, 10.0)))
        val context =
            GeocodingContext.create(
                    trackNumber = TrackNumber("001"),
                    startAddress = TrackMeter(KmNumber(0), BigDecimal("0.1")),
                    referenceLineGeometry = referenceLineAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext
        assertEquals(
            0.1,
            context.getTrackLocation(locationTrackAlignment, TrackMeter(KmNumber(0), BigDecimal("0.2")))?.point?.m,
        )
    }

    @Test
    fun `generated zigzag in location track does not reverse walk`() {
        val referenceLineAlignment = alignment(segment(Point(0.0, 0.0), Point(0.0, 10.0)))
        val locationTrackAlignment =
            alignment(
                segment(Point(1.0, 5.0), Point(1.0, 6.0)),
                segment(2, 1.0, 0.0, 6.0, 4.0).copy(source = GENERATED),
                segment(Point(0.0, 4.0), Point(0.0, 8.0)),
            )
        val context =
            GeocodingContext.create(
                    trackNumber = TrackNumber("001"),
                    startAddress = TrackMeter(KmNumber(0), 0),
                    referenceLineGeometry = referenceLineAlignment,
                    kmPosts = listOf(),
                )
                .geocodingContext
        val result = context.getAddressPoints(locationTrackAlignment)!!
        assertEqualsRounded(
            listOf(
                // first midpoint hits right at the point where the zigzag starts
                AddressPoint(AlignmentPoint(1.0, 6.0, null, m = 1.0, null), TrackMeter("0000+0006")),
                // m-value: zigzag's diagonal is 1 meter tall and 2 meters wide, hence sqrt(1 + 4); plus 1 meter before
                // the turn, and 3 meters after
                AddressPoint(AlignmentPoint(0.0, 7.0, null, m = 4.0 + sqrt(5.0), null), TrackMeter("0000+0007")),
            ),
            result.midPoints,
        )
    }

    private fun assertEqualsRounded(
        expectedAddressPoints: List<AddressPoint>,
        actualAddressPoints: List<AddressPoint>,
    ) {
        assertEquals(expectedAddressPoints.size, actualAddressPoints.size)
        expectedAddressPoints.mapIndexed { i, expected ->
            val actual = actualAddressPoints[i]
            assertEquals(expected.address, actual.address, "address at index $i")
            assertApproximatelyEquals(actual.point, expected.point)
        }
    }

    private fun assertProjectionLinesMatch(result: List<ProjectionLine>, vararg expected: Pair<TrackMeter, Line>) {
        assertEquals(
            expected.size,
            result.size,
            "expectedSize=${expected.size} actualSize=${result.size} expected=$expected actual=$result",
        )
        result.forEachIndexed { index, projectionLine ->
            assertProjectionLineMatches(projectionLine, expected[index].first, expected[index].second)
        }
    }

    private fun assertProjectionLineMatches(projectionLine: ProjectionLine, address: TrackMeter, line: Line) {
        assertEquals(address, projectionLine.address)
        assertEquals(line.start.x, projectionLine.projection.start.x, 2 * DELTA)
        assertEquals(line.start.y, projectionLine.projection.start.y, 2 * DELTA)
        assertEquals(line.end.x, projectionLine.projection.end.x, 2 * DELTA)
        assertEquals(line.end.y, projectionLine.projection.end.y, 2 * DELTA)
    }

    private fun getLastAddress(kmNumber: KmNumber) =
        context.projectionLines
            .getValue(Resolution.ONE_METER)
            .value
            .findLast { l -> l.address.kmNumber == kmNumber }
            ?.address
}
