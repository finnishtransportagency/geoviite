package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.geometryElements
import fi.fta.geoviite.infra.geometry.infraModelFile
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.linking.NodeTrackConnections
import fi.fta.geoviite.infra.math.MultiPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.IMPORTED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.CONNECTION
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MAIN
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MATH
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAlignmentDaoIT
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
) : DBTestBase() {

    @BeforeEach
    fun setUp() {
        initUser()
        testDBService.clearLayoutTables()
    }

    @Test
    fun alignmentsAreStoredAndLoadedOk() {
        (0..20)
            .map { seed -> referenceLineGeometryWithZAndCant(seed) }
            .forEach { referenceLineGeometry -> insertAndVerify(referenceLineGeometry) }
    }

    @Test
    fun alignmentsWithoutProfileOrCantIsStoredAndLoadedOk() {
        (0..20)
            .map { seed -> referenceLineGeometryWithoutZAndCant(seed) }
            .forEach { referenceLineGeometry -> insertAndVerify(referenceLineGeometry) }
    }

    @Test
    fun mixedNullsInHeightAndCantWork() {
        val points =
            listOf(
                SegmentPoint(x = 10.0, y = 20.0, z = 1.0, cant = 0.1, m = 0.0),
                SegmentPoint(x = 10.0, y = 21.0, z = null, cant = null, m = 1.0),
                SegmentPoint(x = 10.0, y = 22.0, z = 1.5, cant = 0.2, m = 2.0),
            )
        val version = insertAndVerify(referenceLineGeometry(segment(points)))
        val fromDb = alignmentDao.fetch(version)
        assertEquals(1, fromDb.segments.size)
        assertEquals(points, fromDb.segments[0].segmentPoints)

        val points2 =
            listOf(
                SegmentPoint(x = 11.0, y = 22.0, z = 1.0, cant = null, m = 0.0),
                SegmentPoint(x = 11.0, y = 23.0, z = null, cant = 0.3, m = 1.0),
            )

        val updatedVersion = updateAndVerify(version, fromDb.copy(segments = listOf(segment(points2))))
        val updatedFromDb = alignmentDao.fetch(updatedVersion)

        assertEquals(1, updatedFromDb.segments.size)
        assertEquals(points2, updatedFromDb.segments[0].segmentPoints)
    }

    @Test
    fun `alignment segment plan metadata search works`() {
        val points = arrayOf(Point(10.0, 10.0), Point(10.0, 11.0))
        val points2 = arrayOf(Point(10.0, 11.0), Point(10.0, 12.0))
        val points3 = arrayOf(Point(10.0, 12.0), Point(10.0, 13.0))
        val points4 = arrayOf(Point(10.0, 13.0), Point(10.0, 14.0))
        val points5 = arrayOf(Point(10.0, 14.0), Point(10.0, 15.0))

        val trackNumber = testDBService.getUnusedTrackNumber()
        val planVersion =
            geometryDao.insertPlan(
                plan =
                    plan(
                        trackNumber = trackNumber,
                        alignments =
                            listOf(
                                geometryAlignment(
                                    name = "test-alignment-name",
                                    elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                                )
                            ),
                    ),
                file = infraModelFile("testfile.xml"),
                boundingBoxInLayoutCoordinates = null,
            )
        val plan = geometryDao.fetchPlan(planVersion)
        val geometryAlignment = plan.alignments.first()
        val geometryElement = geometryAlignment.elements.first()
        val segments =
            listOf(
                segment(points = points, source = PLAN, sourceId = geometryElement.id),
                segment(points = points2, source = PLAN, sourceId = geometryElement.id),
                segment(points = points3, source = GENERATED),
                segment(points = points4, source = GENERATED),
                segment(points = points5, source = PLAN, sourceId = geometryElement.id),
            )

        val tnVersion = insertAndVerify(referenceLineGeometry(segments))
        val trackVersion =
            locationTrackDao.save(
                locationTrack(mainOfficialContext.createLayoutTrackNumber().id),
                trackGeometryOfSegments(segments),
            )

        val alignmentMetadatas = alignmentDao.fetchTrackNumberSegmentMetadata(tnVersion, null, null)
        val trackMetadatas = alignmentDao.fetchLocationTrackSegmentMetadata(trackVersion, null, null)

        for (metaDatas in listOf(alignmentMetadatas, trackMetadatas)) {
            assertEquals(3, metaDatas.size)

            assertApproximatelyEquals(points.first(), metaDatas[0].startPoint!!)
            assertApproximatelyEquals(points2.last(), metaDatas[0].endPoint!!)
            assertEquals(true, metaDatas[0].isLinked)
            assertEquals(planVersion.id, metaDatas[0].planId)
            assertEquals(plan.fileName, metaDatas[0].fileName)
            assertEquals(geometryAlignment.name, metaDatas[0].alignmentName)

            assertApproximatelyEquals(points3.first(), metaDatas[1].startPoint!!)
            assertApproximatelyEquals(points4.last(), metaDatas[1].endPoint!!)
            assertEquals(false, metaDatas[1].isLinked)
            assertEquals(null, metaDatas[1].planId)
            assertEquals(null, metaDatas[1].fileName)
            assertEquals(null, metaDatas[1].alignmentName)

            assertApproximatelyEquals(points5.first(), metaDatas[2].startPoint!!)
            assertApproximatelyEquals(points5.last(), metaDatas[2].endPoint!!)
            assertEquals(true, metaDatas[2].isLinked)
            assertEquals(planVersion.id, metaDatas[2].planId)
            assertEquals(plan.fileName, metaDatas[2].fileName)
            assertEquals(geometryAlignment.name, metaDatas[2].alignmentName)
        }
    }

    @Test
    fun `alignment hasProfile fetch works`() {
        val points = arrayOf(Point(10.0, 10.0), Point(10.0, 11.0))
        val points2 = arrayOf(Point(10.0, 11.0), Point(10.0, 12.0))
        val points3 = arrayOf(Point(10.0, 12.0), Point(10.0, 13.0))
        val points4 = arrayOf(Point(10.0, 13.0), Point(10.0, 14.0))
        val points5 = arrayOf(Point(10.0, 14.0), Point(10.0, 15.0))
        val points6 = arrayOf(Point(10.0, 15.0), Point(10.0, 16.0))

        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = mainOfficialContext.save(trackNumber(trackNumber)).id
        val planVersion =
            geometryDao.insertPlan(
                plan =
                    plan(
                        trackNumber = trackNumber,
                        alignments =
                            listOf(
                                geometryAlignment(
                                    name = "test-alignment-name",
                                    elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                                    profile =
                                        GeometryProfile(
                                            PlanElementName("profile"),
                                            listOf(
                                                VIPoint(PlanElementName("point1"), Point(0.0, 0.0)),
                                                VIPoint(PlanElementName("point2"), Point(1.0, 1.0)),
                                            ),
                                        ),
                                ),
                                geometryAlignment(
                                    name = "alignment-missing-profile",
                                    elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                                ),
                            ),
                        coordinateSystemName = CoordinateSystemName("testcrs"),
                        verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                    ),
                file = infraModelFile("testfile.xml"),
                boundingBoxInLayoutCoordinates = null,
            )
        val plan = geometryDao.fetchPlan(planVersion)
        val geometryAlignment = plan.alignments.first()
        val geometryElement = geometryAlignment.elements.first()
        val geometryAlignmentWithCrsButNoProfile = plan.alignments[1]
        val geometryElementWithCrsButNoProfile = geometryAlignmentWithCrsButNoProfile.elements.first()

        val planVersionWithoutCrs =
            geometryDao.insertPlan(
                plan =
                    plan(
                        trackNumber = trackNumber,
                        alignments =
                            listOf(
                                geometryAlignment(
                                    name = "test-alignment-name-2",
                                    elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                                )
                            ),
                        coordinateSystemName = null,
                        verticalCoordinateSystem = null,
                    ),
                file = infraModelFile("testfile2.xml"),
                boundingBoxInLayoutCoordinates = null,
            )
        val planWithoutCrs = geometryDao.fetchPlan(planVersionWithoutCrs)
        val geometryAlignmentWithoutCrs = planWithoutCrs.alignments.first()
        val geometryElementWithoutCrs = geometryAlignmentWithoutCrs.elements.first()

        val geometry =
            trackGeometryOfSegments(
                segment(points = points, source = PLAN, sourceId = geometryElement.id),
                segment(points = points2, source = IMPORTED),
                segment(points = points3, source = GENERATED),
                segment(points = points4, source = PLAN, sourceId = geometryElementWithoutCrs.id),
                segment(points = points5, source = PLAN, sourceId = geometryElement.id),
                segment(points = points6, source = PLAN, sourceId = geometryElementWithCrsButNoProfile.id),
            )
        val id = locationTrackDao.save(locationTrack(trackNumberId, draft = false), geometry).id

        val profileInfo = alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, listOf(id))
        assertEquals(6, profileInfo.size)
        assertEquals(listOf(true, false, false, false, true, false), profileInfo.map { it.hasProfile })

        val onlyProfileless = alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, listOf(id), false)
        assertEquals(profileInfo.slice(1..3) + profileInfo[5], onlyProfileless)

        val onlyProfileful = alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, listOf(id), true)

        assertEquals(2, onlyProfileful.size)
        assertEquals(listOf(profileInfo[0], profileInfo[4]), onlyProfileful)
    }

    @Test
    fun `ReferenceLineGeometry is saved and loaded correctly`() {
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber())
        val geometry1 = referenceLineGeometryWithZAndCant(123, 5)
        val insertVersion = mainDraftContext.save(trackNumber, geometry1)
        val insertedGeometry = alignmentDao.fetch(insertVersion)
        assertEquals(insertVersion, insertedGeometry.trackNumberVersion)
        assertMatches(geometry1, insertedGeometry)

        val geometry2 = referenceLineGeometryWithZAndCant(456, 7)
        val updateVersion = mainDraftContext.save(trackNumberDao.fetch(insertVersion), geometry2)
        assertNotEquals(updateVersion, insertVersion)
        val updatedGeometry = alignmentDao.fetch(updateVersion)
        assertEquals(updateVersion, updatedGeometry.trackNumberVersion)
        assertMatches(geometry2, updatedGeometry)
    }

    @Test
    fun `LocationTrackGeometry is saved and loaded correctly`() {
        val track = locationTrack(mainDraftContext.createLayoutTrackNumber().id)
        val switch1Id = mainDraftContext.save(switch()).id
        val switch2Id = mainDraftContext.save(switch()).id
        val switch3Id = mainDraftContext.save(switch()).id
        val geometry1 =
            trackGeometry(
                edgesBetweenNodes(
                    listOf(
                        PlaceHolderNodeConnection,
                        NodeConnection.switch(null, SwitchLink(switch1Id, MAIN, JointNumber(1))),
                        NodeConnection.switch(
                            SwitchLink(switch1Id, CONNECTION, JointNumber(2)),
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                        ),
                        NodeConnection.switch(SwitchLink(switch2Id, CONNECTION, JointNumber(2)), null),
                        PlaceHolderNodeConnection,
                    ),
                    edgeLength = 100.0,
                    edgeSegments = 3,
                )
            )
        val insertVersion = mainDraftContext.save(track, geometry1)
        val insertedGeometry = alignmentDao.fetch(insertVersion)
        assertEquals(insertVersion, insertedGeometry.trackRowVersion)
        assertMatches(geometry1, insertedGeometry)

        val geometry2 =
            trackGeometry(
                edgesBetweenNodes(
                    listOf(
                        NodeConnection.switch(
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                            SwitchLink(switch1Id, MAIN, JointNumber(1)),
                        ),
                        NodeConnection.switch(
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                        ),
                        NodeConnection.switch(
                            SwitchLink(switch1Id, CONNECTION, JointNumber(2)),
                            SwitchLink(switch3Id, CONNECTION, JointNumber(2)),
                        ),
                    ),
                    edgeLength = 50.0,
                    edgeSegments = 1,
                )
            )
        val updateVersion = mainDraftContext.save(locationTrackDao.fetch(insertVersion), geometry2)
        val updatedGeometry = alignmentDao.fetch(updateVersion)
        assertEquals(updateVersion, updatedGeometry.trackRowVersion)
        assertMatches(geometry2, updatedGeometry)
    }

    @Test
    fun `Nearby node fetching works`() {
        val track1Start = Point(100.0, 100.0)
        val crossingPoint = Point(100.0, 200.0)
        val track1End = Point(110.0, 300.0)
        val track2Start = Point(200.0, 200.0)
        val track3End = Point(120.0, 400.0)

        val switchLink1 = switchLinkYV(mainOfficialContext.save(switch()).id, 1)
        val switchLink2 = switchLinkYV(mainOfficialContext.save(switch()).id, 2)
        val switchLink3 = switchLinkYV(mainOfficialContext.save(switch()).id, 3)

        assertEquals(
            emptyList(),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.official, MultiPoint(track1Start), 1.0),
        )
        assertEquals(
            emptyList(),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.draft, MultiPoint(track1Start), 1.0),
        )

        val (track1, geometry1) =
            testDBService.fetchLocationTrackWithGeometry(
                mainOfficialContext.save(
                    locationTrack(mainOfficialContext.createLayoutTrackNumber().id),
                    trackGeometry(
                        edge(endInnerSwitch = switchLink1, segments = listOf(segment(track1Start, crossingPoint))),
                        edge(
                            startInnerSwitch = switchLink1,
                            endOuterSwitch = switchLink2,
                            segments = listOf(segment(crossingPoint, track1End)),
                        ),
                    ),
                )
            )
        val (track2, geometry2) =
            testDBService.fetchLocationTrackWithGeometry(
                mainOfficialContext.save(
                    locationTrack(mainOfficialContext.createLayoutTrackNumber().id),
                    trackGeometry(
                        edge(endInnerSwitch = switchLink3, segments = listOf(segment(track2Start, crossingPoint)))
                    ),
                )
            )
        val (track3, _) =
            testDBService.fetchLocationTrackWithGeometry(
                mainDraftContext.save(
                    locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                    trackGeometry(
                        edge(startInnerSwitch = switchLink2, segments = listOf(segment(track1End, track3End)))
                    ),
                )
            )

        // Only track1 has something at this point (the track start)
        assertEquals(
            listOf(NodeTrackConnections(geometry1.nodes[0], setOf(track1.getVersionOrThrow()))),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.official, MultiPoint(track1Start), 1.0),
        )
        // Both track1 & track 2 go through the crossing point
        assertEquals(
            listOf(
                NodeTrackConnections(geometry1.nodes[1], setOf(track1.getVersionOrThrow())),
                NodeTrackConnections(geometry2.nodes[1], setOf(track2.getVersionOrThrow())),
            ),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.official, MultiPoint(crossingPoint), 1.0),
        )
        // In official context, only track 1 is at the end-point
        assertEquals(
            listOf(NodeTrackConnections(geometry1.nodes[2], setOf(track1.getVersionOrThrow()))),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.official, MultiPoint(track1End), 1.0),
        )
        // In draft context, also track 3 starts from the same node
        assertEquals(
            listOf(
                NodeTrackConnections(geometry1.nodes[2], setOf(track1.getVersionOrThrow(), track3.getVersionOrThrow()))
            ),
            alignmentDao.getNodeConnectionsNear(MainLayoutContext.draft, MultiPoint(track1End), 1.0),
        )
    }

    @Test
    fun `database roundtrip maintains EdgeKey equality`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val planVersion =
            geometryDao.insertPlan(plan(alignments = listOf(geometryAlignment(geometryElements()))), testFile(), null)

        val planAlignmentId = geometryDao.fetchPlan(planVersion).alignments[0].id as IntId<GeometryAlignment>
        val geometry =
            trackGeometryOfSegments(
                // segments with diverse data
                segment(Point(0.0, 0.0), Point(14.0, 15.0))
                    .copy(
                        sourceId = IndexedId(planAlignmentId.intValue, 0),
                        // sourceStartMs have exactly 6 decimals in the schema, and we don't care about having other
                        // numbers of decimals survive roundtrips
                        sourceStartM = BigDecimal("123.534631"),
                        source = PLAN,
                    ),
                segment(Point(14.0001, 14.9999), Point(15.0, 15.0)).copy(source = GENERATED),
                segment(Point(15.0, 15.0), Point(18.0, 10.0)),
                segment(Point(18.0, 10.0), Point(19.0, 10.0)).copy(sourceStartM = BigDecimal("0.000000")),
            )
        val track = mainOfficialContext.save(locationTrack(trackNumber), geometry).id
        val geometryWithTrackId = geometry.withLocationTrackId(track)
        val loadedGeometry = mainOfficialContext.fetchLocationTrackWithGeometry(track)!!.second
        assertEquals(geometryWithTrackId.edges[0].contentKey, loadedGeometry.edges[0].contentKey)
    }

    @Test
    fun `database roundtrip maintains EdgeKey equality for random segments`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id

        val rng = Random(123)

        repeat(10 /*_000_000 */) {
            val geometry =
                trackGeometryOfSegments(
                    segment(
                        Point(rng.nextFloat() * 100.0, rng.nextFloat() * 100.0),
                        Point(rng.nextFloat() * 100.0, rng.nextFloat() * 100.0),
                    )
                )
            val track = mainOfficialContext.save(locationTrack(trackNumber), geometry).id
            val geometryWithTrackId = geometry.withLocationTrackId(track)
            val loadedGeometry = mainOfficialContext.fetchLocationTrackWithGeometry(track)!!.second
            assertEquals(geometryWithTrackId.edges[0].contentKey, loadedGeometry.edges[0].contentKey)
        }
    }

    fun edgesBetweenNodes(
        nodes: List<NodeConnection> = listOf(PlaceHolderNodeConnection, PlaceHolderNodeConnection),
        edgeLength: Double = 10.0,
        edgeSegments: Int = 1,
    ): List<TmpLayoutEdge> =
        nodes.zipWithNext().mapIndexed { edgeIndex, (startNode, endNode) ->
            val segmentLength = edgeLength / edgeSegments
            val segments =
                (0 until edgeSegments).map { segmentIndex ->
                    val startPoint = Point(edgeIndex * edgeLength + segmentIndex * segmentLength, 0.0)
                    val endPoint = startPoint + Point(segmentLength, 0.0)
                    segment(startPoint, endPoint)
                }
            TmpLayoutEdge(startNode.flipPort(), endNode, segments)
        }

    private fun referenceLineGeometryWithZAndCant(seed: Int, segmentCount: Int = 20): TmpReferenceLineGeometry =
        referenceLineGeometry(segmentsWithZAndCant(seed, segmentCount))

    private fun referenceLineGeometryWithoutZAndCant(seed: Int, segmentCount: Int = 20): TmpReferenceLineGeometry =
        referenceLineGeometry(segmentsWithoutZAndCant(seed, segmentCount))

    private fun segmentsWithZAndCant(alignmentSeed: Int, count: Int): List<LayoutSegment> =
        (0..count).map { segmentSeed -> segmentWithZAndCant(alignmentSeed + segmentSeed) }

    private fun segmentsWithoutZAndCant(alignmentSeed: Int, count: Int): List<LayoutSegment> =
        (0..count).map { segmentSeed -> segmentWithoutZAndCant(alignmentSeed + segmentSeed) }

    private fun segmentWithoutZAndCant(segmentSeed: Int) =
        segment(
            points(
                count = 10,
                x = (segmentSeed * 10).toDouble()..(segmentSeed * 10 + 10.0),
                y = (segmentSeed * 10).toDouble()..(segmentSeed * 10 + 10.0),
            ),
            source = PLAN,
        )

    private fun segmentWithZAndCant(segmentSeed: Int) =
        segment(
            points(
                count = 20,
                x = (segmentSeed * 10).toDouble()..(segmentSeed * 10 + 10.0),
                y = (segmentSeed * 10).toDouble()..(segmentSeed * 10 + 10.0),
                z = segmentSeed.toDouble()..segmentSeed + 20.0,
                cant = segmentSeed.toDouble()..segmentSeed + 20.0,
            ),
            source = PLAN,
        )

    fun insertAndVerify(geometry: TmpReferenceLineGeometry): LayoutRowVersion<LayoutTrackNumber> {
        val rowVersion = trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber()), geometry)
        assertNull(rowVersion.previous())
        assertMatches(geometry, alignmentDao.fetch(rowVersion))
        return rowVersion
    }

    fun updateAndVerify(
        tnVersion: LayoutRowVersion<LayoutTrackNumber>,
        geometry: ReferenceLineGeometry,
    ): LayoutRowVersion<LayoutTrackNumber> {
        val rowVersion = trackNumberDao.save(trackNumberDao.fetch(tnVersion), geometry)
        assertNotNull(rowVersion.previous())
        assertEquals(geometry.id, rowVersion.id)
        assertMatches(geometry, alignmentDao.fetch(rowVersion))
        return rowVersion
    }
}
