package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.getIntId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAlignmentDaoIT @Autowired constructor(
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
): ITTestBase() {

    @Test
    fun alignmentsAreStoredAndLoadedOk() {
        (0..20)
            .map { seed -> alignmentWithZAndCant(seed) }
            .forEach { alignment -> insertAndVerify(alignment) }
    }

    @Test
    fun alignmentsWithoutProfileOrCantIsStoredAndLoadedOk() {
        (0..20)
            .map { alignmentSeed -> alignmentWithoutZAndCant(alignmentSeed) }
            .forEach { a -> insertAndVerify(a) }
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
        val trackNumberId = getUnusedTrackNumberId()

        val alignmentOrphan = alignment(someSegment())
        val alignmentLocationTrack = alignment(someSegment())
        val alignmentReferenceLine = alignment(someSegment())

        val orphanAlignmentVersion = alignmentDao.insert(alignmentOrphan)
        val locationTrackAlignmentVersion = alignmentDao.insert(alignmentLocationTrack)
        locationTrackDao.insert(locationTrack(trackNumberId, alignmentLocationTrack)
            .copy(alignmentVersion = locationTrackAlignmentVersion)
        )
        val referenceLineAlignmentVersion = alignmentDao.insert(alignmentReferenceLine)
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentReferenceLine)
            .copy(alignmentVersion = referenceLineAlignmentVersion)
        )

        val orphanAlignmentBeforeDelete = alignmentDao.fetch(orphanAlignmentVersion)
        assertMatches(alignmentOrphan, orphanAlignmentBeforeDelete)
        assertMatches(alignmentLocationTrack, alignmentDao.fetch(locationTrackAlignmentVersion))
        assertMatches(alignmentReferenceLine, alignmentDao.fetch(referenceLineAlignmentVersion))

        alignmentDao.deleteOrphanedAlignments()

        assertEquals(orphanAlignmentBeforeDelete, alignmentDao.fetch(orphanAlignmentVersion))
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(orphanAlignmentVersion.next()) }
        assertMatches(alignmentLocationTrack, alignmentDao.fetch(locationTrackAlignmentVersion))
        assertMatches(alignmentReferenceLine, alignmentDao.fetch(referenceLineAlignmentVersion))
    }

    @Test
    fun mixedNullsInHeightAndCantWork() {
        val points = listOf(
            LayoutPoint(
                x = 10.0,
                y = 20.0,
                z = 1.0,
                cant = 0.1,
                m = 0.0,
            ),
            LayoutPoint(
                x = 10.0,
                y = 21.0,
                z = null,
                cant = null,
                m = 1.0,
            ),
            LayoutPoint(
                x = 10.0,
                y = 22.0,
                z = 1.5,
                cant = 0.2,
                m = 2.0,
            ),
        )
        val alignment = alignment(segment(points))
        val version = alignmentDao.insert(alignment)
        val fromDb = alignmentDao.fetch(version)
        assertEquals(1, fromDb.segments.size)
        assertEquals(points, fromDb.segments[0].points)

        val points2 = listOf(
            LayoutPoint(
                x = 11.0,
                y = 22.0,
                z = 1.0,
                cant = null,
                m = 0.0,
            ),
            LayoutPoint(
                x = 11.0,
                y = 23.0,
                z = null,
                cant = 0.3,
                m = 1.0,
            ),
        )

        val updatedVersion = alignmentDao.update(fromDb.copy(segments = listOf(segment(points2))))
        val updatedFromDb = alignmentDao.fetch(updatedVersion)

        assertEquals(1, updatedFromDb.segments.size)
        assertEquals(points2, updatedFromDb.segments[0].points)
    }

    @Test
    fun `alignment segment plan metadata search works`() {
        val points = arrayOf(Point(10.0, 10.0), Point(10.0, 11.0))
        val points2 = arrayOf(Point(10.0, 11.0), Point(10.0, 12.0))
        val points3 = arrayOf(Point(10.0, 12.0), Point(10.0, 13.0))
        val points4 = arrayOf(Point(10.0, 13.0), Point(10.0, 14.0))
        val points5 = arrayOf(Point(10.0, 14.0), Point(10.0, 15.0))

        val trackNumberId = getUnusedTrackNumberId()
        val planVersion = geometryDao.insertPlan(
            plan = plan(
                trackNumberId = trackNumberId,
                alignments = listOf(
                    geometryAlignment(
                        name = "test-alignment-name",
                        elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                    )
                ) ),
            file = infraModelFile("testfile.xml"),
            boundingBoxInLayoutCoordinates = null,
        )
        val plan = geometryDao.fetchPlan(planVersion)
        val geometryAlignment = plan.alignments.first()
        val geometryElement = geometryAlignment.elements.first()
        val alignment = alignment(
            segment(points = points, source = GeometrySource.PLAN, sourceId = geometryElement.id),
            segment(points = points2, source = GeometrySource.PLAN, sourceId = geometryElement.id),
            segment(points = points3, source = GeometrySource.GENERATED),
            segment(points = points4, source = GeometrySource.GENERATED),
            segment(points = points5, source = GeometrySource.PLAN, sourceId = geometryElement.id),
        )
        val version = alignmentDao.insert(alignment)

        val segmentGeometriesAndPlanMetadatas = alignmentDao.fetchSegmentGeometriesAndPlanMetadata(version, null, null)
        assertEquals(3, segmentGeometriesAndPlanMetadatas.size)

        assertEquals(points.first(), segmentGeometriesAndPlanMetadatas[0].startPoint)
        assertEquals(points2.last(), segmentGeometriesAndPlanMetadatas[0].endPoint)
        assertEquals(true, segmentGeometriesAndPlanMetadatas[0].isLinked)
        assertEquals(planVersion.id, segmentGeometriesAndPlanMetadatas[0].planId)
        assertEquals(plan.fileName, segmentGeometriesAndPlanMetadatas[0].fileName)
        assertEquals(geometryAlignment.name, segmentGeometriesAndPlanMetadatas[0].alignmentName)

        assertEquals(points3.first(), segmentGeometriesAndPlanMetadatas[1].startPoint)
        assertEquals(points4.last(), segmentGeometriesAndPlanMetadatas[1].endPoint)
        assertEquals(false, segmentGeometriesAndPlanMetadatas[1].isLinked)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].planId)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].fileName)
        assertEquals(null, segmentGeometriesAndPlanMetadatas[1].alignmentName)

        assertEquals(points5.first(), segmentGeometriesAndPlanMetadatas[2].startPoint)
        assertEquals(points5.last(), segmentGeometriesAndPlanMetadatas[2].endPoint)
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

        val trackNumberId = getUnusedTrackNumberId()
        val planVersion = geometryDao.insertPlan(
            plan = plan(
                trackNumberId = trackNumberId,
                alignments = listOf(
                    geometryAlignment(
                        name = "test-alignment-name",
                        elements = listOf(line(Point(1.0, 1.0), Point(3.0, 3.0))),
                    )
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

        val planVersionWithoutCrs = geometryDao.insertPlan(
            plan = plan(
                trackNumberId = trackNumberId,
                alignments = listOf(
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

        val alignment = alignment(
            segment(points = points, source = GeometrySource.PLAN, sourceId = geometryElement.id),
            segment(points = points2, source = GeometrySource.IMPORTED),
            segment(points = points3, source = GeometrySource.GENERATED),
            segment(points = points4, source = GeometrySource.PLAN, sourceId = geometryElementWithoutCrs.id),
            segment(points = points5, source = GeometrySource.PLAN, sourceId = geometryElement.id),
        )
        val version = alignmentDao.insert(alignment)

        val profileInfo = alignmentDao.fetchSegmentPlanInfo(version)
        assertEquals(5, profileInfo.size)
        assertTrue(profileInfo[0].second)
        assertFalse(profileInfo[1].second)
        assertFalse(profileInfo[2].second)
        assertFalse(profileInfo[3].second)
        assertTrue(profileInfo[4].second)
    }

    private fun alignmentWithZAndCant(alignmentSeed: Int, segmentCount: Int = 20) =
        alignment(segmentsWithZAndCant(alignmentSeed, segmentCount))

    private fun alignmentWithoutZAndCant(alignmentSeed: Int, segmentCount: Int = 20) =
        alignment(segmentsWithoutZAndCant(alignmentSeed, segmentCount))

    private fun segmentsWithZAndCant(alignmentSeed: Int, count: Int) =
        fixSegmentStarts((0..count).map { seed -> segmentWithZAndCant(alignmentSeed + seed) })

    private fun segmentsWithoutZAndCant(alignmentSeed: Int, count: Int) =
        fixSegmentStarts((0..count).map { seed -> segmentWithoutZAndCant(alignmentSeed + seed) })

    private fun segmentWithoutZAndCant(segmentSeed: Int) =
        createSegment(segmentSeed, points(
            count = 10,
            x = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            y = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
        ))

    private fun segmentWithZAndCant(segmentSeed: Int) =
        createSegment(segmentSeed, points(
            count = 20,
            x = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            y = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            z = segmentSeed.toDouble()..segmentSeed + 20.0,
            cant = segmentSeed.toDouble()..segmentSeed + 20.0,
        ))

    private fun createSegment(segmentSeed: Int, points: List<LayoutPoint>) = segment(
        points = points,
        start = segmentSeed * 0.1,
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
        ) { rs, _ -> rs.getInt("count") } ?: 0

    private fun assertDbGeometriesHaveCorrectMValues() {
        val sql = """
           select id, postgis.st_astext(geometry) geom, postgis.st_length(geometry) length
           from layout.segment_geometry
           where postgis.st_m(postgis.st_startpoint(geometry)) <> 0.0
             or abs(postgis.st_m(postgis.st_endpoint(geometry)) - postgis.st_length(geometry))/postgis.st_length(geometry) > 0.01;
        """.trimIndent()
        val geometriesWithInvalidMValues = jdbc.query(sql, mapOf<String,Any>()) { rs, _ -> Triple(
            rs.getIntId<SegmentGeometry>("id"),
            rs.getDouble("length"),
            rs.getString("geom"),
        ) }
        assertTrue(
            geometriesWithInvalidMValues.isEmpty(),
            "All geometries should have m-values at 0.0-length: violations=$geometriesWithInvalidMValues",
        )
    }

}
