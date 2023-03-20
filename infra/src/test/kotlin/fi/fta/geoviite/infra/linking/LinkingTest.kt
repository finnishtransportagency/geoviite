package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DZ
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test


class LinkingTest {

    @Test
    fun shouldReverseTrackPointUpdateType(){
        val reversedEndPoint = getReversedTrackPointUpdateType(LocationTrackPointUpdateType.END_POINT)
        val reversedStartPoint = getReversedTrackPointUpdateType(LocationTrackPointUpdateType.START_POINT)
        assertEquals(reversedEndPoint, LocationTrackPointUpdateType.START_POINT)
        assertEquals(reversedStartPoint, LocationTrackPointUpdateType.END_POINT)
    }

    @Test
    fun shouldUpdateLocationTrackEndPoint(){
        val switchId = IntId<TrackLayoutSwitch>(1711)
        val segments = getAlignmentEndPointSegmentsData(switchId)
        val actualSegments = removeLinkingToSwitchFromSegments(switchId, segments)
        assertEquals(null, actualSegments.last().switchId)
    }

    @Test
    fun shouldGetLastSegmentSwitchId(){
        val expectedSwitchId = IntId<TrackLayoutSwitch>(1711)
        val segments = getAlignmentEndPointSegmentsData(expectedSwitchId)
        assertEquals(expectedSwitchId, getSwitchId(segments, LocationTrackPointUpdateType.END_POINT))
    }

    @Test
    fun indexShouldBeReturnedForAGivenPointOnAList() {
        val point1 = LayoutPoint(0.0, 0.0, null, 0.0, null)
        val point2 = LayoutPoint(1.0, 0.0, null, 1.0, null)
        val point3 = LayoutPoint(2.0, 0.0, null, 2.0, null)

        val segmentPoints = listOf(point1, point2, point3)
        val segment = segment(segmentPoints)
        val point1Index = segment.getPointIndex(point1)
        val point2Index = segment.getPointIndex(point2)
        val point3Index = segment.getPointIndex(point3)
        val expectedIndex1 = 0
        val expectedIndex2 = 1
        val expectedIndex3 = 2

        assertEquals(expectedIndex1, point1Index)
        assertEquals(expectedIndex2, point2Index)
        assertEquals(expectedIndex3, point3Index)
    }

    @Test
    fun shouldReturnSegmentsBeforeNewModifiedGeometry() {
        val segments = createSegments()
        val startLayoutIndex = 1
        val segmentsBeforeStart = getSegmentsBeforeNewGeometry(segments, startLayoutIndex)
        assertEquals(segments[0].id, segmentsBeforeStart[0].id)
    }

    @Test
    fun shouldReturnSegmentsAfterNewModifiedGeometry() {
        val segments = createSegments()
        val endLayoutIndex = 1
        val segmentsAfterGeometry = getSegmentsAfterNewGeometry(segments, endLayoutIndex, 1.0)
        assertEquals(segments[2].id, segmentsAfterGeometry[0].id)
        assertEquals(1.0, segmentsAfterGeometry[0].start)
    }

    @Test
    fun shouldRemovePointsFromLayoutStartSegment() {
        val segments = createSegments()
        val startLayoutIndex = 1
        val toPoint = Point(x = 2776410.812373895, y = 8438660.427285915)
        val startSegmentToCut = segments[startLayoutIndex]
        val cutSegment = cutSegmentBeforePoint(startSegmentToCut, toPoint, 1.0)!!
        assertNotEquals(startSegmentToCut.points.size, cutSegment.points.size)
        assertEquals(1.0, cutSegment.start)
    }

    @Test
    fun shouldRemovePointsFromLayoutEndSegment() {
        val segments = createSegments()
        val endLayoutIndex = 1
        val fromPoint = Point(x = 2776304.1633397467, y = 8439435.393078482)
        val endSegmentToCut = segments[endLayoutIndex]
        val cutSegment = cutSegmentAfterPoint(endSegmentToCut, fromPoint, 1.0)!!
        assertNotEquals(endSegmentToCut.points.size, cutSegment.points.size)
        assertEquals(1.0, cutSegment.start)
    }

    @Test
    fun shouldReturnGeometrySegments() {
        val segments = createGeometrySegments()
        val geometryStart = Point(x = 385770.05828143685, y = 6672686.609479602)
        val startIndex = 0
        val endIndex = 0
        val geometryEnd = Point(x = 385770.0141087553, y = 6672688.60851115)
        val geometrySegments =
            getSegmentsBetweenPoints(startIndex, endIndex, segments, geometryStart, geometryEnd, 10.0)
        val expectedListSize = 1
        assertEquals(expectedListSize, geometrySegments.size)
    }

    @Test
    fun shouldReturnSegmentsBetweenIndicesWhenStartAndEndIndicesAreTheSame() {
        val segments = createSegments()
        val startLayoutIndex = 1
        val endLayoutIndex = 1

        val expectedId: DomainId<LayoutSegment> = segments[1].id
        val list = getSegmentsInRange(
            segments,
            startLayoutIndex,
            endLayoutIndex,
            1.0,
        )
        assertEquals(expectedId, list[0].id)
        assertEquals(1.0, list[0].start)
    }

    @Test
    fun shouldReturnSegmentsBetweenIndices() {
        val segments = createSegments()
        val startLayoutIndex = 1
        val endLayoutIndex = 2
        val list = getSegmentsInRange(
            segments,
            startIndex = startLayoutIndex,
            endIndex = endLayoutIndex,
            10.0,
        )
        val expectedIdList: List<DomainId<LayoutSegment>> = listOf(segments[1].id, segments[2].id)
        val actualIdList = list.map { segment -> segment.id }
        assertEquals(expectedIdList, actualIdList)
        val expectedStarts = listOf(10.0, 10.0 + segments[1].length)
        val actualStarts = list.map { segment -> segment.start }
        assertEquals(expectedStarts, actualStarts)
    }

    @Test
    fun shouldCalculateSourceLengthFromStartAndListOfPoints() {
        val segment = segment(
            points = toTrackLayoutPoints(Point(0.0, 10.0), Point(0.0, 15.0), Point(0.0, 25.0)),
            sourceStart = 6.5,
        )
        val expectedSourceLength = 6.5 + 5.0
        val actualSourceLength = segment.getSourceLengthAt(1)
        assertEquals(expectedSourceLength, actualSourceLength)
    }
}

fun createGeometrySegments(): List<LayoutSegment> {
    val segment0 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776456.1595348255, y = 8437996.456411341, z = 3.48),
            Point3DZ(x = 2776455.1985910204, y = 8438014.50907601, z = 3.48),
            Point3DZ(x = 2776452.7428242783, y = 8438060.643863278, z = 3.48),
            Point3DZ(x = 2776451.2479946334, y = 8438088.726048335, z = 3.48),
            Point3DZ(x = 2776449.9667029823, y = 8438112.796577362, z = 3.48),
            Point3DZ(x = 2776423.484795002, y = 8438610.271683251, z = 3.48),
            Point3DZ(x = 2776421.1354202335, y = 8438654.404152181, z = 3.48),
            Point3DZ(x = 2776421.028629801, y = 8438656.410179745, z = 3.48),
            Point3DZ(x = 2776412.912386186, y = 8438808.869863337, z = 3.48),
            Point3DZ(x = 2776412.8055912596, y = 8438810.87593271, z = 3.48),
            Point3DZ(x = 2776412.6987962765, y = 8438812.882002633, z = 3.48),
            Point3DZ(x = 2776412.5920012346, y = 8438814.88807309, z = 3.48),
            Point3DZ(x = 2776412.4852061337, y = 8438816.894144095, z = 3.48),
            Point3DZ(x = 2776412.378410975, y = 8438818.900215648, z = 3.48),
            Point3DZ(x = 2776409.601716367, y = 8438871.058266543, z = 3.48),
            Point3DZ(x = 2776409.4949196326, y = 8438873.064352756, z = 3.48),
            Point3DZ(x = 2776409.38812284, y = 8438875.07043951, z = 3.48),
            Point3DZ(x = 2776408.5337464004, y = 8438891.1191531, z = 3.48),
            Point3DZ(x = 2776408.4269490824, y = 8438893.125244746, z = 3.48),
            Point3DZ(x = 2776407.5813992294, y = 8438909.008119363, z = 3.48),
        ),
        sourceId = IndexedId(1,0),
        sourceStart = 0.0,
        start = 0.000,
        source = GeometrySource.PLAN,
    )
    val segment1 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776407.5813997965, y = 8438909.008120276, z = 3.48),
            Point3DZ(x = 2776407.472933042, y = 8438911.014126683, z = 3.48),
            Point3DZ(x = 2776407.3611286, y = 8438913.019949775, z = 3.48),
            Point3DZ(x = 2776407.2459867788, y = 8438915.025583971, z = 3.48),
            Point3DZ(x = 2776407.127507887, y = 8438917.031023694, z = 3.48),
            Point3DZ(x = 2776396.379156499, y = 8439020.907954335, z = 3.48),
            Point3DZ(x = 2776396.084509888, y = 8439022.895133724, z = 3.48),
            Point3DZ(x = 2776385.315009858, y = 8439084.225281067, z = 3.48),
            Point3DZ(x = 2776385.148949401, y = 8439085.044622159, z = 3.48),
        ),
        sourceId = IndexedId(1,1),
        sourceStart = 454.917,
        start = 454.917,
        source = GeometrySource.PLAN,
    )
    val segment2 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776385.1489478312, y = 8439085.044622496, z = 3.48),
            Point3DZ(x = 2776384.749231924, y = 8439087.013315797, z = 3.48),
            Point3DZ(x = 2776384.349515802, y = 8439088.982009606, z = 3.48),
            Point3DZ(x = 2776383.9497994673, y = 8439090.95070391, z = 3.48),
            Point3DZ(x = 2776383.5500829173, y = 8439092.919398718, z = 3.48),
            Point3DZ(x = 2776381.551496963, y = 8439102.762880312, z = 3.48),
            Point3DZ(x = 2776381.1517791306, y = 8439104.731578136, z = 3.48),
            Point3DZ(x = 2776380.752061084, y = 8439106.700276462, z = 3.48),
            Point3DZ(x = 2776380.352342824, y = 8439108.668975294, z = 3.48),
            Point3DZ(x = 2776379.95262435, y = 8439110.637674632, z = 3.48),
            Point3DZ(x = 2776379.552905662, y = 8439112.606374472, z = 3.48),
            Point3DZ(x = 2776379.153186761, y = 8439114.575074816, z = 3.48),
            Point3DZ(x = 2776378.753467644, y = 8439116.543775655, z = 3.48),
            Point3DZ(x = 2776378.353748315, y = 8439118.512476997, z = 3.48),
            Point3DZ(x = 2776377.9540287713, y = 8439120.481178844, z = 3.48),
            Point3DZ(x = 2776377.554309014, y = 8439122.449881202, z = 3.48),
            Point3DZ(x = 2776377.1545890425, y = 8439124.41858405, z = 3.48),
            Point3DZ(x = 2776376.754868858, y = 8439126.387287412, z = 3.48),
            Point3DZ(x = 2776373.956821581, y = 8439140.168224981, z = 3.48),
            Point3DZ(x = 2776373.5570996855, y = 8439142.13693236, z = 3.48),
            Point3DZ(x = 2776373.1573775755, y = 8439144.105640238, z = 3.48),
            Point3DZ(x = 2776372.757655252, y = 8439146.074348625, z = 3.48),
            Point3DZ(x = 2776372.357932714, y = 8439148.043057503, z = 3.48),
            Point3DZ(x = 2776371.958209963, y = 8439150.011766894, z = 3.48),
            Point3DZ(x = 2776371.768086158, y = 8439150.948162042, z = 3.48),
        ),
        sourceId = DomainId.create("IDX_1_2"),
        sourceStart = 543.333,
        start = 543.333,
        source = GeometrySource.PLAN,
    )
    val segment3 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776371.7680860236, y = 8439150.948162692, z = 3.48),
            Point3DZ(x = 2776371.3683628887, y = 8439152.916872809, z = 3.48),
            Point3DZ(x = 2776370.96863954, y = 8439154.88558343, z = 3.48),
            Point3DZ(x = 2776370.5689159785, y = 8439156.85429455, z = 3.48),
            Point3DZ(x = 2776370.169192203, y = 8439158.823006174, z = 3.48),
            Point3DZ(x = 2776369.769468213, y = 8439160.791718304, z = 3.48),
            Point3DZ(x = 2776369.369744009, y = 8439162.760430938, z = 3.48),
            Point3DZ(x = 2776368.9700195915, y = 8439164.729144074, z = 3.48),
            Point3DZ(x = 2776366.9713942953, y = 8439174.572717285, z = 3.48),
            Point3DZ(x = 2776366.5716685946, y = 8439176.541433426, z = 3.48),
            Point3DZ(x = 2776366.17194268, y = 8439178.510150082, z = 3.48000918787875),
            Point3DZ(x = 2776365.7722165515, y = 8439180.478867238, z = 3.480038358553429),
            Point3DZ(x = 2776365.372490208, y = 8439182.4475849, z = 3.4800875292276032),
            Point3DZ(x = 2776364.972763652, y = 8439184.416303057, z = 3.480156699908548),
            Point3DZ(x = 2776364.8836245984, y = 8439184.855327273, z = 3.480174852258642),
        ),
        sourceId = DomainId.create("IDX_1_3"),
        sourceStart = 576.809,
        start = 576.809,
        source = GeometrySource.PLAN,
    )
    val segment4 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776364.8836245756, y = 8439184.8553274, z = 3.480174852265918),
            Point3DZ(x = 2776364.483897758, y = 8439186.824046168, z = 3.480268482941028),
            Point3DZ(x = 2776364.084170726, y = 8439188.792765455, z = 3.480382113615633),
            Point3DZ(x = 2776363.6844434803, y = 8439190.761485234, z = 3.4805157442970085),
            Point3DZ(x = 2776363.2847160203, y = 8439192.730205515, z = 3.4806693749778788),
            Point3DZ(x = 2776362.884988348, y = 8439194.698926304, z = 3.480843005658244),
            Point3DZ(x = 2776362.4852604605, y = 8439196.667647596, z = 3.4810366363381036),
            Point3DZ(x = 2776362.085532359, y = 8439198.636369385, z = 3.481250267017458),
            Point3DZ(x = 2776361.685804044, y = 8439200.60509168, z = 3.4814838977108593),
            Point3DZ(x = 2776361.286075515, y = 8439202.573814474, z = 3.4817375283892034),
            Point3DZ(x = 2776360.8863467793, y = 8439204.542537779, z = 3.482011159081594),
            Point3DZ(x = 2776360.4866178227, y = 8439206.511261586, z = 3.4823047897734796),
            Point3DZ(x = 2776360.0868886523, y = 8439208.479985887, z = 3.48261842046486),
            Point3DZ(x = 2776359.6871592673, y = 8439210.448710693, z = 3.4829520511630108),
            Point3DZ(x = 2776359.287429669, y = 8439212.41743601, z = 3.4833056818606565),
            Point3DZ(x = 2776358.8876998574, y = 8439214.386161828, z = 3.483679312565073),
        ),
        sourceId = DomainId.create("IDX_1_4"),
        sourceStart = 594.032,
        start = 594.032,
        source = GeometrySource.PLAN,
    )
    val segment5 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776357.999099694, y = 8439218.762641229, z = 3.4845815409571514),
            Point3DZ(x = 2776357.599369296, y = 8439220.731368687, z = 3.4850196316765505),
            Point3DZ(x = 2776357.199638685, y = 8439222.70009664, z = 3.4854777224027202),
            Point3DZ(x = 2776356.7999078534, y = 8439224.668825107, z = 3.4859558131356607),
            Point3DZ(x = 2776356.400176815, y = 8439226.637554072, z = 3.486453903875372),
            Point3DZ(x = 2776356.000445563, y = 8439228.606283534, z = 3.486971994621854),
            Point3DZ(x = 2776355.600714096, y = 8439230.575013507, z = 3.4875100853823824),
            Point3DZ(x = 2776355.2009824156, y = 8439232.543743974, z = 3.4880681761496817),
            Point3DZ(x = 2776354.801250522, y = 8439234.51247495, z = 3.4886462669237517),
            Point3DZ(x = 2776354.505848507, y = 8439235.967367468, z = 3.489086327223049),
        ),
        sourceId = DomainId.create("IDX_1_5"),
        sourceStart = 611.255,
        start = 611.255,
        source = GeometrySource.PLAN,
    )
    val segment6 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776354.505848357, y = 8439235.967368213, z = 3.4890863274558797),
            Point3DZ(x = 2776354.1061159866, y = 8439237.936100043, z = 3.489699198260496),
            Point3DZ(x = 2776353.706383402, y = 8439239.904832372, z = 3.490332069071883),
            Point3DZ(x = 2776353.306650603, y = 8439241.873565206, z = 3.4909849398973165),
            Point3DZ(x = 2776352.906917591, y = 8439243.842298545, z = 3.491657810736797),
            Point3DZ(x = 2776352.507184365, y = 8439245.81103238, z = 3.492350681583048),
            Point3DZ(x = 2776352.107450925, y = 8439247.779766724, z = 3.4930635524506215),
            Point3DZ(x = 2776351.7077172706, y = 8439249.748501567, z = 3.493796423339518),
            Point3DZ(x = 2776351.307983403, y = 8439251.717236917, z = 3.4945492942424607),
            Point3DZ(x = 2776350.908249321, y = 8439253.685972761, z = 3.4953221651594504),
            Point3DZ(x = 2776350.5085150255, y = 8439255.654709114, z = 3.4961150360904867),
            Point3DZ(x = 2776350.1087805154, y = 8439257.623445973, z = 3.4969279070501216),
            Point3DZ(x = 2776349.7090457925, y = 8439259.592183335, z = 3.497760778023803),
        ),
        sourceId = DomainId.create("IDX_1_6"),
        sourceStart = 619.994,
        start = 619.994,
        source = GeometrySource.PLAN,
    )
    val segment7 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776347.6212278306, y = 8439269.874906845, z = 3.5024358908485738),
            Point3DZ(x = 2776347.221491889, y = 8439271.843647357, z = 3.5033932219812414),
            Point3DZ(x = 2776346.8217557324, y = 8439273.81238836, z = 3.5043705531352316),
            Point3DZ(x = 2776346.422019363, y = 8439275.781129884, z = 3.5053678843178204),
            Point3DZ(x = 2776346.0222827788, y = 8439277.749871897, z = 3.506385215529008),
            Point3DZ(x = 2776345.622545981, y = 8439279.718614414, z = 3.50742254677607),
            Point3DZ(x = 2776345.2228089687, y = 8439281.687357444, z = 3.5084798780590063),
            Point3DZ(x = 2776342.0249051745, y = 8439297.437319733, z = 3.517658529628534),
            Point3DZ(x = 2776341.625166238, y = 8439299.406067273, z = 3.518895861270721),
            Point3DZ(x = 2776341.2254270874, y = 8439301.37481533, z = 3.520153192948783),
            Point3DZ(x = 2776340.825687723, y = 8439303.343563877, z = 3.521430524677271),
            Point3DZ(x = 2776340.73654587, y = 8439303.78259461, z = 3.5217180967738386),
        ),
        sourceId = DomainId.create("IDX_1_7"),
        sourceStart = 637.217,
        start = 637.217,
        source = GeometrySource.PLAN,
    )
    val segment8 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776340.73654587, y = 8439303.78259461, z = 3.5217180967738386),
            Point3DZ(x = 2776340.3368062144, y = 8439305.751343763, z = 3.5230198885692516),
            Point3DZ(x = 2776339.9370663366, y = 8439307.720093438, z = 3.524341680400539),
            Point3DZ(x = 2776339.5373262526, y = 8439309.6888436, z = 3.525683472296805),
            Point3DZ(x = 2776339.137585955, y = 8439311.657594273, z = 3.5270452642434975),
            Point3DZ(x = 2776338.7378454357, y = 8439313.626345444, z = 3.5284270562551683),
            Point3DZ(x = 2776338.338104711, y = 8439315.595097119, z = 3.5298288483099896),
            Point3DZ(x = 2776337.9383637635, y = 8439317.563849295, z = 3.531250640437065),
            Point3DZ(x = 2776337.5386226107, y = 8439319.532601979, z = 3.532692432621843),
            Point3DZ(x = 2776337.229622805, y = 8439321.054446895, z = 3.533820642376668),
        ),
        sourceId = DomainId.create("IDX_1_8"),
        sourceStart = 654.440,
        start = 654.440,
        source = GeometrySource.PLAN,
    )
    val segment9 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776337.229622805, y = 8439321.054446895, z = 3.533820642376668),
            Point3DZ(x = 2776336.8298811913, y = 8439323.02320045, z = 3.5352978946684743),
            Point3DZ(x = 2776336.4301393614, y = 8439324.991954513, z = 3.5367951470179833),
            Point3DZ(x = 2776336.0303973197, y = 8439326.960709073, z = 3.5383123994324706),
            Point3DZ(x = 2776335.630655064, y = 8439328.929464135, z = 3.539849651919212),
            Point3DZ(x = 2776335.2309125927, y = 8439330.898219706, z = 3.541406904485484),
            Point3DZ(x = 2776334.8311699084, y = 8439332.866975779, z = 3.5429841571240104),
            Point3DZ(x = 2776334.431427011, y = 8439334.835732345, z = 3.544581409827515),
            Point3DZ(x = 2776334.031683899, y = 8439336.804489426, z = 3.546198662617826),
            Point3DZ(x = 2776331.233476134, y = 8439350.58580303, z = 3.5580794344641617),
            Point3DZ(x = 2776330.833731311, y = 8439352.554564124, z = 3.5598566879489226),
            Point3DZ(x = 2776330.433986274, y = 8439354.523325725, z = 3.5616539415204898),
            Point3DZ(x = 2776330.3448430994, y = 8439354.962359631, z = 3.562057456372713),
        ),
        sourceId = DomainId.create("IDX_1_9"),
        sourceStart = 663.213,
        start = 663.213,
        source = GeometrySource.PLAN,
    )
    val segment10 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776330.344843077, y = 8439354.962359756, z = 3.5620574564818526),
            Point3DZ(x = 2776329.9450977785, y = 8439356.931121973, z = 3.563879170178552),
            Point3DZ(x = 2776329.5453522657, y = 8439358.899884688, z = 3.56572088397661),
            Point3DZ(x = 2776329.145606539, y = 8439360.868647905, z = 3.567582597876026),
            Point3DZ(x = 2776328.7458605994, y = 8439362.837411623, z = 3.569464311884076),
            Point3DZ(x = 2776328.346114446, y = 8439364.806175858, z = 3.571366025993484),
            Point3DZ(x = 2776327.946368077, y = 8439366.774940582, z = 3.5732877402188024),
            Point3DZ(x = 2776327.5466214954, y = 8439368.743705815, z = 3.5752294545527548),
            Point3DZ(x = 2776327.1468746993, y = 8439370.71247155, z = 3.5771911690026172),
            Point3DZ(x = 2776326.74712769, y = 8439372.68123779, z = 3.579172883561114),
            Point3DZ(x = 2776326.347380466, y = 8439374.650004528, z = 3.5811745982500724),
            Point3DZ(x = 2776325.947633029, y = 8439376.618771765, z = 3.583196313054941),
            Point3DZ(x = 2776325.547885378, y = 8439378.587539518, z = 3.585238027982996),
            Point3DZ(x = 2776323.5491439123, y = 8439388.431385783, z = 3.5957466045729234),
            Point3DZ(x = 2776323.4599999157, y = 8439388.870421618, z = 3.5962269344599918),
        ),
        sourceId = DomainId.create("IDX_1_10"),
        sourceStart = 680.436,
        start = 680.436,
        source = GeometrySource.PLAN,
    )
    val segment11 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776323.459999893, y = 8439388.870421745, z = 3.596226934598235),
            Point3DZ(x = 2776323.060250948, y = 8439390.839192625, z = 3.598393110354664),
            Point3DZ(x = 2776322.660501789, y = 8439392.807964016, z = 3.6005792862561066),
            Point3DZ(x = 2776302.2730110995, y = 8439493.215971299, z = 3.7385945139976684),
            Point3DZ(x = 2776301.873250818, y = 8439495.184768837, z = 3.741820701463439),
            Point3DZ(x = 2776301.4734903225, y = 8439497.15356686, z = 3.7450668892415706),
            Point3DZ(x = 2776248.3034382937, y = 8439759.008185452, z = 3.798998117352312),
            Point3DZ(x = 2776165.1426870693, y = 8440168.543015104, z = 3.474323864102189),
            Point3DZ(x = 2776164.7428534054, y = 8440170.511985105, z = 3.4706729111348977),
            Point3DZ(x = 2776139.1530538998, y = 8440296.527111027, z = 6.113726702015657),
            Point3DZ(x = 2776138.753206326, y = 8440298.496113718, z = 6.123826699919621),
            Point3DZ(x = 2776135.814854398, y = 8440312.965667846, z = 6.198048188585655),
        ),
        sourceId = DomainId.create("IDX_1_11"),
        sourceStart = 697.659,
        start = 697.659,
        source = GeometrySource.PLAN,
    )
    val segment12 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776135.8148539574, y = 8440312.96566776, z = 6.198048188585655),
            Point3DZ(x = 2776135.415727328, y = 8440314.934821706, z = 6.208148186489619),
            Point3DZ(x = 2776135.018046088, y = 8440316.904269597, z = 6.218248184393583),
            Point3DZ(x = 2776134.621810464, y = 8440318.874010362, z = 6.228348182297547),
            Point3DZ(x = 2776134.2270206613, y = 8440320.844042929, z = 6.2384481802015115),
            Point3DZ(x = 2776130.3586898097, y = 8440340.560183682, z = 6.339448159241154),
            Point3DZ(x = 2776129.97981807, y = 8440342.533355823, z = 6.349548157145118),
            Point3DZ(x = 2776129.6023946777, y = 8440344.506806985, z = 6.359648155049082),
            Point3DZ(x = 2776129.226419838, y = 8440346.480536096, z = 6.369748152953046),
            Point3DZ(x = 2776129.133076116, y = 8440346.971781135, z = 6.372261739431404),
        ),
        sourceId = DomainId.create("IDX_1_12"),
        sourceStart = 1167.008,
        start = 1167.008,
        source = GeometrySource.PLAN,
    )
    val segment13 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776129.13307681, y = 8440346.971782582, z = 6.372261745583425),
            Point3DZ(x = 2776128.7569852434, y = 8440348.945489986, z = 6.382361743487389),
            Point3DZ(x = 2776128.3784908094, y = 8440350.918737015, z = 6.392461741391354),
            Point3DZ(x = 2776127.9975940506, y = 8440352.891520744, z = 6.402561739295318),
            Point3DZ(x = 2776127.614295539, y = 8440354.863838222, z = 6.412661737199282),
            Point3DZ(x = 2776116.8074534885, y = 8440405.970844438, z = 6.67526168270235),
            Point3DZ(x = 2776116.6585831176, y = 8440406.62303036, z = 6.678624487104474),
        ),
        sourceId = DomainId.create("IDX_1_13"),
        sourceStart = 1184.257,
        start = 1184.257,
        source = GeometrySource.PLAN,
    )
    val segment14 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776116.6585825584, y = 8440406.623029826, z = 6.678624491232095),
            Point3DZ(x = 2776116.2110613515, y = 8440408.581743697, z = 6.688724489136059),
            Point3DZ(x = 2776115.7635399, y = 8440410.540458055, z = 6.6988244870400235),
            Point3DZ(x = 2776115.3160182186, y = 8440412.49917291, z = 6.708924484943988),
            Point3DZ(x = 2776115.047505227, y = 8440413.67440147, z = 6.714984480665829),
        ),
        sourceId = DomainId.create("IDX_1_14"),
        sourceStart = 1214.590,
        start = 1214.590,
        source = GeometrySource.PLAN,
    )
    val segment15 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776115.047505227, y = 8440413.67440147, z = 6.714984480665829),
            Point3DZ(x = 2776114.599983201, y = 8440415.633117111, z = 6.725084478569793),
            Point3DZ(x = 2776114.152460937, y = 8440417.591833252, z = 6.735184476473758),
            Point3DZ(x = 2776113.704938434, y = 8440419.550549882, z = 6.745284474377722),
            Point3DZ(x = 2776113.257415694, y = 8440421.50926701, z = 6.755384472281686),
            Point3DZ(x = 2776112.8098927145, y = 8440423.467984624, z = 6.76548447018565),
            Point3DZ(x = 2776112.3623694973, y = 8440425.426702728, z = 6.7755844680896145),
            Point3DZ(x = 2776111.9148460347, y = 8440427.385421332, z = 6.785684465993579),
            Point3DZ(x = 2776111.467322341, y = 8440429.344140422, z = 6.795784463897543),
            Point3DZ(x = 2776111.0197984097, y = 8440431.30286, z = 6.805884461801507),
            Point3DZ(x = 2776110.5722742393, y = 8440433.261580076, z = 6.815984459705471),
            Point3DZ(x = 2776110.124749831, y = 8440435.220300639, z = 6.8260844576094355),
            Point3DZ(x = 2776109.677225184, y = 8440437.1790217, z = 6.8361844555134),
            Point3DZ(x = 2776109.2297003, y = 8440439.137743255, z = 6.846284453417364),
            Point3DZ(x = 2776108.7821751777, y = 8440441.096465295, z = 6.856384451321328),
            Point3DZ(x = 2776108.334649815, y = 8440443.055187827, z = 6.866484449225292),
            Point3DZ(x = 2776107.887124216, y = 8440445.013910847, z = 6.8765844471292565),
            Point3DZ(x = 2776107.4395983783, y = 8440446.972634373, z = 6.886684445033221),
            Point3DZ(x = 2776107.3398000845, y = 8440447.409429785, z = 6.888936744565805),
        ),
        sourceId = DomainId.create("IDX_1_15"),
        sourceStart = 1218.190,
        start = 1218.190,
        source = GeometrySource.PLAN,
    )
    val segment16 = segment(
        points = toTrackLayoutPoints(
            Point3DZ(x = 2776107.3398000547, y = 8440447.40942991, z = 6.888936745187633),
            Point3DZ(x = 2776106.892274038, y = 8440449.36815405, z = 6.899036743091597),
            Point3DZ(x = 2776106.444747776, y = 8440451.326878684, z = 6.909136740995561),
            Point3DZ(x = 2776105.997221282, y = 8440453.285603818, z = 6.919236738899525),
            Point3DZ(x = 2776105.54969455, y = 8440455.244329438, z = 6.929336736803488),
            Point3DZ(x = 2776105.10216758, y = 8440457.203055551, z = 6.939436734707452),
            Point3DZ(x = 2776104.6546403645, y = 8440459.16178216, z = 6.949536732611416),
            Point3DZ(x = 2776104.207112919, y = 8440461.12050925, z = 6.95963673051538),
            Point3DZ(x = 2776103.759585234, y = 8440463.079236839, z = 6.9697367284193446),
            Point3DZ(x = 2776103.3120573033, y = 8440465.037964921, z = 6.979836726323309),
            Point3DZ(x = 2776102.8645291417, y = 8440466.996693492, z = 6.989936724227273),
            Point3DZ(x = 2776102.4170007426, y = 8440468.955422554, z = 7.000036722131237),
            Point3DZ(x = 2776101.969472105, y = 8440470.91415211, z = 7.010136720035201),
            Point3DZ(x = 2776101.5219432223, y = 8440472.872882156, z = 7.020236717939166),
            Point3DZ(x = 2776101.0744141075, y = 8440474.831612704, z = 7.03033671584313),
            Point3DZ(x = 2776100.626884755, y = 8440476.79034373, z = 7.040436713747094),
            Point3DZ(x = 2776100.179355157, y = 8440478.749075256, z = 7.050536711651058),
            Point3DZ(x = 2776099.7318253284, y = 8440480.707807267, z = 7.060636709555022),
            Point3DZ(x = 2776099.6320262174, y = 8440481.144604262, z = 7.062889),
        ),
        sourceId = DomainId.create("IDX_1_16"),
        sourceStart = 1235.413,
        start = 1235.413,
        source = GeometrySource.PLAN,
    )
    return listOf(segment0, segment1, segment2,
        segment3, segment4, segment5, segment6, segment7, segment8, segment9,
        segment10, segment11, segment12, segment13, segment14, segment15, segment16).map { segment ->
        //temporary solution
        val newPoints = segment.points.map { p ->
            val transformedPoint = transformNonKKJCoordinate(Srid(3857), LAYOUT_SRID, Point(p.x, p.y))
            LayoutPoint(transformedPoint.x, transformedPoint.y, p.z, p.m, p.cant)
        }

        segment.copy(geometry = segment.geometry.withPoints(newPoints))
    }
}

fun createSegments(): List<LayoutSegment> {
    val segment1 = segment(
        points = toTrackLayoutPoints(
            Point(x = 2776446.184403898, y = 8437995.942963839),
            Point(x = 2776446.156804578, y = 8437996.46145657),
        ),
        start = 0.258,
        source = GeometrySource.PLAN,
    )
    val segment2 = segment(
        points = toTrackLayoutPoints(
            Point(x = 2776446.156804578, y = 8437996.46145657),
            Point(x = 2776446.078529614, y = 8437995.773646561),
            Point(x = 2776437.508153799, y = 8438158.937022803),
            Point(x = 2776416.685758276, y = 8438550.096516075),
            Point(x = 2776412.6278023897, y = 8438626.324872553),
            Point(x = 2776410.812373895, y = 8438660.427285915),
            Point(x = 2776410.7055833787, y = 8438662.433315111),
            Point(x = 2776365.323584978, y = 8439134.175320553),
            Point(x = 2776364.92386128, y = 8439136.144024964),
            Point(x = 2776328.947833393, y = 8439313.329562277),
            Point(x = 2776328.548090059, y = 8439315.298313286),
            Point(x = 2776304.1633397467, y = 8439435.393078482),
            Point(x = 2776303.7635831507, y = 8439437.3618607),
        ),
        start = 723.213,
        source = GeometrySource.PLAN,
    )
    val segment3 = segment(
        points = toTrackLayoutPoints(
            Point(x = 2776303.7635831507, y = 8439437.3618607),
            Point(x = 2776303.621062641, y = 8439438.063767185),
        ),
        start = 723.569,
        source = GeometrySource.PLAN,
    )

    val segment4 = segment(
        points = toTrackLayoutPoints(
            Point(x = 2776303.7635831507, y = 8439437.3618607),
            Point(x = 2776303.621062641, y = 8439438.063767185),
        ),
        start = 723.569,
        source = GeometrySource.PLAN,
    )
    return listOf(segment1, segment2, segment3, segment4)
}

private fun getAlignmentEndPointSegmentsData(switchId: IntId<TrackLayoutSwitch>): List<LayoutSegment> {
    return listOf(
        segment(
            points = toTrackLayoutPoints(
                Point(x = 495978.63098445226, y = 6719338.7833989635),
                Point(x = 496682.8319693883, y = 6718628.209313194),
            ),
            resolution = 100,
            start = 32392.19111,
            source = GeometrySource.PLAN,
        ),
        segment(
            points = toTrackLayoutPoints(
                Point(x = 496682.8319693883, y = 6718628.209313194),
                Point(x = 496893.92104687955, y = 6718415.210125355),
            ),
            resolution = 100,
            start = 33392.998464,
            source = GeometrySource.PLAN,
        ),
        segment(
            points = toTrackLayoutPoints(
                Point(x = 496893.92104687955, y = 6718415.210125355),
                Point(x = 496918.22153316956, y = 6718390.689744501),
            ),
            resolution = 100,
            switchId = switchId,
            startJointNumber = JointNumber(2),
            endJointNumber = null,
            start = 33692.997156,
            source = GeometrySource.PLAN,
        )
    )
}
