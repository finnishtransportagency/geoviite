package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.util.RowVersion
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAlignmentDaoIT @Autowired constructor(
    private val alignmentDao: LayoutAlignmentDao,
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
        val version = insertAndVerify(orig)
        val updatedVersion = updateAndVerify(orig.copy(
            id = version.id,
            segments = segmentsWithoutZAndCant(2, 5)
        ))
        assertEquals(version.id, updatedVersion.id)
        assertEquals(version.version + 1, updatedVersion.version)
        val updatedVersion2 = updateAndVerify(orig.copy(
            id = version.id,
            segments = segmentsWithoutZAndCant(3, 15)
        ))
        assertEquals(version.id, updatedVersion2.id)
        assertEquals(version.version + 2, updatedVersion2.version)
    }

    @Test
    fun alignmentDeleteWorks() {
        val insertedVersion = insertAndVerify(alignmentWithZAndCant(4, 5))
        val deletedId = alignmentDao.delete(insertedVersion.id)
        assertEquals(insertedVersion.id, deletedId)
        assertFalse(alignmentDao.fetchVersions().any { rv -> rv.id == deletedId })
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(insertedVersion) }
        assertEquals(0, getDbSegmentCount(deletedId))
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

    private fun alignmentWithZAndCant(alignmentSeed: Int, segmentCount: Int = 20) =
        alignment(segmentsWithZAndCant(alignmentSeed, segmentCount))

    private fun alignmentWithoutZAndCant(alignmentSeed: Int, segmentCount: Int = 20) =
        alignment(segmentsWithoutZAndCant(alignmentSeed, segmentCount))

    private fun segmentsWithZAndCant(alignmentSeed: Int, count: Int) =
        fixStartDistances((0..count).map { seed -> segmentWithZAndCant(alignmentSeed + seed) })

    private fun segmentsWithoutZAndCant(alignmentSeed: Int, count: Int) =
        fixStartDistances((0..count).map { seed -> segmentWithoutZAndCant(alignmentSeed + seed) })

    private fun segmentWithoutZAndCant(segmentSeed: Int) =
        segment(segmentSeed, points(
            count = 10,
            x = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            y = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
        ))

    private fun segmentWithZAndCant(segmentSeed: Int) =
        segment(segmentSeed, points(
            count = 20,
            x = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            y = (segmentSeed*10).toDouble()..(segmentSeed*10 + 10.0),
            z = segmentSeed.toDouble()..segmentSeed + 20.0,
            cant = segmentSeed.toDouble()..segmentSeed + 20.0,
        ))

    private fun segment(segmentSeed: Int, points: List<LayoutPoint>) = LayoutSegment(
        points = points,
        sourceId = null,
        sourceStart = null,
        resolution = 1,
        start = segmentSeed * 0.1,
        switchId = null,
        startJointNumber = null,
        endJointNumber = null,
        source = GeometrySource.PLAN,
    )

    fun insertAndVerify(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val rowVersion = alignmentDao.insert(alignment)
        assertMatches(alignment, alignmentDao.fetch(rowVersion))
        assertEquals(alignment.segments.size, getDbSegmentCount(rowVersion.id))
        return rowVersion
    }

    fun updateAndVerify(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val rowVersion = alignmentDao.update(alignment)
        assertEquals(alignment.id, rowVersion.id)
        assertMatches(alignment, alignmentDao.fetch(rowVersion))
        assertEquals(alignment.segments.size, getDbSegmentCount(rowVersion.id))
        return rowVersion
    }

    fun getDbSegmentCount(alignmentId: IntId<LayoutAlignment>): Int =
        jdbc.queryForObject(
            "select count(*) from layout.segment where alignment_id = :id",
            mapOf("id" to alignmentId.intValue),
        ) { rs, _ -> rs.getInt("count") } ?: 0
}
