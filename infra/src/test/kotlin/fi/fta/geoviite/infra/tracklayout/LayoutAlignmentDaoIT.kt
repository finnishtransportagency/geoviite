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
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_END
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_START
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
        val alignment =
            alignment(
                segment(points = points, source = GeometrySource.PLAN, sourceId = geometryElement),
                segment(points = points2, source = GeometrySource.PLAN, sourceId = geometryElement),
                segment(points = points3, source = GeometrySource.GENERATED),
                segment(points = points4, source = GeometrySource.GENERATED),
                segment(points = points5, source = GeometrySource.PLAN, sourceId = geometryElement),
            )
        val version = alignmentDao.insert(alignment)

        val segmentGeometriesAndPlanMetadatas = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(version, null, null)
        assertEquals(3, segmentGeometriesAndPlanMetadatas.size)

        assertApproximatelyEquals(points.first(), segmentGeometriesAndPlanMetadatas[0].startPoint!!)
        assertApproximatelyEquals(points2.last(), segmentGeometriesAndPlanMetadatas[0].endPoint!!)
        assertEquals(true, segmentGeometriesAndPlanMetadatas[0].isLinked)
        assertEquals(planVersion.id, segmentGeometriesAndPlanMetadatas[0].planId)
        assertEquals(plan.fileName, segmentGeometriesAndPlanMetadatas[0].fileName)
        assertEquals(geometryAlignment.name, segmentGeometriesAndPlanMetadatas[0].alignmentName)

        assertApproximatelyEquals(points3.first(), segmentGeometriesAndPlanMetadatas[1].startPoint!!)
        assertApproximatelyEquals(points4.last(), segmentGeometriesAndPlanMetadatas[1].endPoint!!)
        assertEquals(false, segmentGeometriesAndPlanMetadatas[1].isLinked)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].planId)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].fileName)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].alignmentName)

        assertApproximatelyEquals(points5.first(), segmentGeometriesAndPlanMetadatas[2].startPoint!!)
        assertApproximatelyEquals(points5.last(), segmentGeometriesAndPlanMetadatas[2].endPoint!!)
        assertEquals(true, segmentGeometriesAndPlanMetadatas[2].isLinked)
        assertEquals(planVersion.id, segmentGeometriesAndPlanMetadatas[2].planId)
        assertEquals(plan.fileName, segmentGeometriesAndPlanMetadatas[2].fileName)
        assertEquals(geometryAlignment.name, segmentGeometriesAndPlanMetadatas[2].alignmentName)
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
                segment(points = points, source = GeometrySource.PLAN, sourceId = geometryElement),
                segment(points = points2, source = GeometrySource.IMPORTED),
                segment(points = points3, source = GeometrySource.GENERATED),
                segment(points = points4, source = GeometrySource.PLAN, sourceId = geometryElementWithoutCrs),
                segment(points = points5, source = GeometrySource.PLAN, sourceId = geometryElement),
                segment(points = points6, source = GeometrySource.PLAN, sourceId = geometryElementWithCrsButNoProfile),
            )
        locationTrackDao.save(locationTrack(trackNumberId, draft = false), geometry)

        val boundingBox = boundingBoxAroundPoints((points + points2 + points3 + points4 + points5).toList())
        val profileInfo =
            alignmentDao.fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(
                MainLayoutContext.official,
                boundingBox,
            )
        assertEquals(6, profileInfo.size)
        assertEquals(listOf(true, false, false, false, true, false), profileInfo.map { it.hasProfile })

        val onlyProfileless =
            alignmentDao.fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(
                MainLayoutContext.official,
                boundingBox,
                false,
            )
        assertEquals(profileInfo.slice(1..3) + profileInfo[5], onlyProfileless)

        val onlyProfileful =
            alignmentDao.fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(
                MainLayoutContext.official,
                boundingBox,
                true,
            )

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
                        LayoutNodeTemp(TRACK_START),
                        LayoutNodeSwitch(null, SwitchLink(switch1Id, MAIN, JointNumber(1))),
                        LayoutNodeSwitch(
                            SwitchLink(switch1Id, CONNECTION, JointNumber(2)),
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                        ),
                        LayoutNodeSwitch(SwitchLink(switch2Id, CONNECTION, JointNumber(2)), null),
                        LayoutNodeTemp(TRACK_END),
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
                        LayoutNodeSwitch(
                            SwitchLink(switch2Id, MAIN, JointNumber(1)),
                            SwitchLink(switch1Id, MAIN, JointNumber(1)),
                        ),
                        LayoutNodeSwitch(
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                            SwitchLink(switch1Id, MATH, JointNumber(5)),
                        ),
                        LayoutNodeSwitch(
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

    fun edgesBetweenNodes(
        nodes: List<LayoutNodeContent> = listOf(LayoutNodeTemp(TRACK_START), LayoutNodeTemp(TRACK_END)),
        edgeLength: Double = 10.0,
        edgeSegments: Int = 1,
    ): List<LayoutEdgeContent> =
        nodes.zipWithNext().mapIndexed { edgeIndex, (startNode, endNode) ->
            val segmentLength = edgeLength / edgeSegments
            val segments =
                (0 until edgeSegments).map { segmentIndex ->
                    //                    val interpolated = edgeIndex * edgeLength + segmentIndex * segmentLength
                    //                    val startPoint = Point(interpolated, interpolated)
                    //                    val endPoint = startPoint + Point(segmentLength, segmentLength)
                    val startPoint = Point(edgeIndex * edgeLength + segmentIndex * segmentLength, 0.0)
                    val endPoint = startPoint + Point(segmentLength, 0.0)
                    segment(startPoint, endPoint)
                }
            LayoutEdgeContent(startNode, endNode, segments)
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
            source = GeometrySource.PLAN,
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
            source = GeometrySource.PLAN,
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
