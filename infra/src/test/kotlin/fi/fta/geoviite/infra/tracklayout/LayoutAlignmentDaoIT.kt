package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.infraModelFile
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.IMPORTED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.CONNECTION
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MAIN
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MATH
import fi.fta.geoviite.infra.util.getIntId
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAlignmentDaoIT
@Autowired
constructor(
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
) : DBTestBase() {

    @BeforeEach
    fun setUp() {
        initUser()
        jdbc.execute("truncate layout.alignment cascade") { it.execute() }
    }

    @Test
    fun alignmentsAreStoredAndLoadedOk() {
        (0..20).map { seed -> alignmentWithZAndCant(seed) }.forEach { alignment -> insertAndVerify(alignment) }
    }

    @Test
    fun alignmentsWithoutProfileOrCantIsStoredAndLoadedOk() {
        (0..20).map { alignmentSeed -> alignmentWithoutZAndCant(alignmentSeed) }.forEach { a -> insertAndVerify(a) }
    }

    @Test
    fun alignmentUpdateWorks() {
        val orig = alignmentWithoutZAndCant(1, 10)

        val insertedVersion = insertAndVerify(orig)
        val afterInsert = alignmentDao.fetch(insertedVersion)

        val updated = afterInsert.copy(segments = segmentsWithoutZAndCant(2, 5))
        val updatedVersion = updateAndVerify(updated)
        assertEquals(insertedVersion.id, updatedVersion.id)
        assertEquals(insertedVersion.next(), updatedVersion)
        val afterUpdate = alignmentDao.fetch(updatedVersion)
        assertMatches(updated, afterUpdate)

        val updated2 = afterUpdate.copy(segments = segmentsWithoutZAndCant(3, 15))
        val updatedVersion2 = updateAndVerify(updated2)
        assertEquals(insertedVersion.id, updatedVersion2.id)
        assertEquals(updatedVersion.next(), updatedVersion2)
        val afterUpdate2 = alignmentDao.fetch(updatedVersion2)
        assertMatches(updated2, afterUpdate2)

        // Verify that versioned fetch works
        assertEquals(afterInsert, alignmentDao.fetch(insertedVersion))
        assertEquals(afterUpdate, alignmentDao.fetch(updatedVersion))
        assertEquals(afterUpdate2, alignmentDao.fetch(updatedVersion2))
        assertDbGeometriesHaveCorrectMValues()
    }

    @Test
    fun alignmentDeleteWorks() {
        val insertedVersion = insertAndVerify(alignmentWithZAndCant(4, 5))
        val alignmentBeforeDelete = alignmentDao.fetch(insertedVersion)
        val deletedId = alignmentDao.delete(insertedVersion.id)
        assertEquals(insertedVersion.id, deletedId)
        assertFalse(alignmentDao.fetchVersions().any { rv -> rv.id == deletedId })
        assertEquals(alignmentBeforeDelete, alignmentDao.fetch(insertedVersion))
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(insertedVersion.next()) }
        assertEquals(0, getDbSegmentCount(deletedId))
    }

    @Test
    fun deletingOrphanedAlignmentsWorks() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val alignmentOrphan = alignment(someSegment())
        val trackGeometry = trackGeometryOfSegments(someSegment())
        val alignmentReferenceLine = alignment(someSegment())

        val orphanAlignmentVersion = alignmentDao.insert(alignmentOrphan)
        val trackVersion =
            locationTrackDao.save(locationTrack(trackNumberId = trackNumberId, draft = false), trackGeometry)
        val referenceLineAlignmentVersion = alignmentDao.insert(alignmentReferenceLine)
        referenceLineDao.save(
            referenceLine(
                trackNumberId = trackNumberId,
                alignment = alignmentReferenceLine,
                alignmentVersion = referenceLineAlignmentVersion,
                draft = false,
            )
        )

        val orphanAlignmentBeforeDelete = alignmentDao.fetch(orphanAlignmentVersion)
        assertMatches(alignmentOrphan, orphanAlignmentBeforeDelete)
        assertMatches(trackGeometry, alignmentDao.fetch(trackVersion))
        assertMatches(alignmentReferenceLine, alignmentDao.fetch(referenceLineAlignmentVersion))

        alignmentDao.deleteOrphanedAlignments()

        assertEquals(orphanAlignmentBeforeDelete, alignmentDao.fetch(orphanAlignmentVersion))
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(orphanAlignmentVersion.next()) }
        assertMatches(trackGeometry, alignmentDao.fetch(trackVersion))
        assertMatches(alignmentReferenceLine, alignmentDao.fetch(referenceLineAlignmentVersion))
    }

    @Test
    fun mixedNullsInHeightAndCantWork() {
        val points =
            listOf(
                SegmentPoint(x = 10.0, y = 20.0, z = 1.0, cant = 0.1, m = 0.0),
                SegmentPoint(x = 10.0, y = 21.0, z = null, cant = null, m = 1.0),
                SegmentPoint(x = 10.0, y = 22.0, z = 1.5, cant = 0.2, m = 2.0),
            )
        val alignment = alignment(segment(points))
        val version = alignmentDao.insert(alignment)
        val fromDb = alignmentDao.fetch(version)
        assertEquals(1, fromDb.segments.size)
        assertEquals(points, fromDb.segments[0].segmentPoints)

        val points2 =
            listOf(
                SegmentPoint(x = 11.0, y = 22.0, z = 1.0, cant = null, m = 0.0),
                SegmentPoint(x = 11.0, y = 23.0, z = null, cant = 0.3, m = 1.0),
            )

        val updatedVersion = alignmentDao.update(fromDb.copy(segments = listOf(segment(points2))))
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

        val alignmentVersion = alignmentDao.insert(alignment(segments))
        val trackVersion =
            locationTrackDao.save(
                locationTrack(mainOfficialContext.createLayoutTrackNumber().id),
                trackGeometryOfSegments(segments),
            )

        val alignmentMetadatas = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(alignmentVersion, null, null)
        val trackMetadatas = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(trackVersion, null, null)

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

        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()
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
        locationTrackDao.save(locationTrack(trackNumberId, draft = false), geometry)

        val boundingBox = boundingBoxAroundPoints((points + points2 + points3 + points4 + points5).toList())
        val profileInfo = alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, boundingBox)
        assertEquals(6, profileInfo.size)
        assertEquals(listOf(true, false, false, false, true, false), profileInfo.map { it.hasProfile })

        val onlyProfileless =
            alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, boundingBox, false)
        assertEquals(profileInfo.slice(1..3) + profileInfo[5], onlyProfileless)

        val onlyProfileful = alignmentDao.fetchLocationTrackProfileInfos(MainLayoutContext.official, boundingBox, true)

        assertEquals(2, onlyProfileful.size)
        assertEquals(listOf(profileInfo[0], profileInfo[4]), onlyProfileful)
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
                        PlaceHolderEdgeNode,
                        EdgeNode.switch(null, SwitchLink(switch1Id, MAIN, JointNumber(1))),
                        EdgeNode.switch(
                            SwitchLink(switch1Id, CONNECTION, JointNumber(2)),
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                        ),
                        EdgeNode.switch(SwitchLink(switch2Id, CONNECTION, JointNumber(2)), null),
                        PlaceHolderEdgeNode,
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
                        EdgeNode.switch(
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                            SwitchLink(switch1Id, MAIN, JointNumber(1)),
                        ),
                        EdgeNode.switch(
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                        ),
                        EdgeNode.switch(
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
        val switchLink1 = switchLinkYV(IntId(1), 1)
        val switchLink2 = switchLinkYV(IntId(2), 2)
        val switchLink3 = switchLinkYV(IntId(3), 3)

        assertEquals(emptyList(), alignmentDao.getNodeConnectionsNear(track1Start, MainLayoutContext.official, 1.0))
        assertEquals(emptyList(), alignmentDao.getNodeConnectionsNear(track1Start, MainLayoutContext.draft, 1.0))

        val (track1, geometry1) =
            testDBService.fetchWithGeometry(
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
            testDBService.fetchWithGeometry(
                mainOfficialContext.save(
                    locationTrack(mainOfficialContext.createLayoutTrackNumber().id),
                    trackGeometry(
                        edge(endInnerSwitch = switchLink3, segments = listOf(segment(track2Start, crossingPoint)))
                    ),
                )
            )
        val (track3, _) =
            testDBService.fetchWithGeometry(
                mainDraftContext.save(
                    locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                    trackGeometry(
                        edge(startInnerSwitch = switchLink2, segments = listOf(segment(track1End, track3End)))
                    ),
                )
            )

        // Only track1 has something at this point (the track start)
        assertEquals(
            listOf(NodeConnection(geometry1.nodes[0], listOf(track1.versionOrThrow))),
            alignmentDao.getNodeConnectionsNear(track1Start, MainLayoutContext.official),
        )
        // Both track1 & track 2 go through the crossing point
        assertEquals(
            listOf(
                NodeConnection(geometry1.nodes[1], listOf(track1.versionOrThrow)),
                NodeConnection(geometry2.nodes[1], listOf(track2.versionOrThrow)),
            ),
            alignmentDao.getNodeConnectionsNear(crossingPoint, MainLayoutContext.official),
        )
        // In official context, only track 1 is at the end-point
        assertEquals(
            listOf(NodeConnection(geometry1.nodes[2], listOf(track1.versionOrThrow))),
            alignmentDao.getNodeConnectionsNear(track1End, MainLayoutContext.official),
        )
        // In draft context, also track 3 starts from the same node
        assertEquals(
            listOf(NodeConnection(geometry1.nodes[2], listOf(track1.versionOrThrow, track3.versionOrThrow))),
            alignmentDao.getNodeConnectionsNear(track1End, MainLayoutContext.draft),
        )
    }

    fun edgesBetweenNodes(
        nodes: List<EdgeNode> = listOf(PlaceHolderEdgeNode, PlaceHolderEdgeNode),
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

    private fun alignmentWithZAndCant(alignmentSeed: Int, segmentCount: Int = 20): LayoutAlignment =
        alignment(segmentsWithZAndCant(alignmentSeed, segmentCount))

    private fun alignmentWithoutZAndCant(alignmentSeed: Int, segmentCount: Int = 20): LayoutAlignment =
        alignment(segmentsWithoutZAndCant(alignmentSeed, segmentCount))

    private fun segmentsWithZAndCant(alignmentSeed: Int, count: Int): List<LayoutSegment> =
        (0..count).map { seed -> segmentWithZAndCant(alignmentSeed + seed) }

    private fun segmentsWithoutZAndCant(alignmentSeed: Int, count: Int): List<LayoutSegment> =
        (0..count).map { seed -> segmentWithoutZAndCant(alignmentSeed + seed) }

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

    fun insertAndVerify(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val rowVersion = alignmentDao.insert(alignment)
        assertNull(rowVersion.previous())
        assertMatches(alignment, alignmentDao.fetch(rowVersion))
        assertEquals(alignment.segments.size, getDbSegmentCount(rowVersion.id))
        return rowVersion
    }

    fun updateAndVerify(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val rowVersion = alignmentDao.update(alignment)
        assertNotNull(rowVersion.previous())
        assertEquals(alignment.id, rowVersion.id)
        assertMatches(alignment, alignmentDao.fetch(rowVersion))
        assertEquals(alignment.segments.size, getDbSegmentCount(rowVersion.id))
        return rowVersion
    }

    fun getDbSegmentCount(alignmentId: IntId<LayoutAlignment>): Int =
        jdbc.queryForObject(
            """
                select count(*) 
                from layout.alignment inner join layout.segment_version 
                  on alignment.id = segment_version.alignment_id and alignment.version = segment_version.alignment_version
                where alignment_id = :id
                """,
            mapOf("id" to alignmentId.intValue),
        ) { rs, _ ->
            rs.getInt("count")
        } ?: 0

    private fun assertDbGeometriesHaveCorrectMValues() {
        val sql =
            """
           select id, postgis.st_astext(geometry) as geom, postgis.st_length(geometry) as length
           from layout.segment_geometry
           where postgis.st_m(postgis.st_startpoint(geometry)) <> 0.0
             or abs(postgis.st_m(postgis.st_endpoint(geometry)) - postgis.st_length(geometry))/postgis.st_length(geometry) > 0.01;
        """
                .trimIndent()
        val geometriesWithInvalidMValues =
            jdbc.query(sql, mapOf<String, Any>()) { rs, _ ->
                Triple(rs.getIntId<SegmentGeometry>("id"), rs.getDouble("length"), rs.getString("geom"))
            }
        assertTrue(
            geometriesWithInvalidMValues.isEmpty(),
            "All geometries should have m-values at 0.0-length: violations=$geometriesWithInvalidMValues",
        )
    }
}
