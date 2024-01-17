package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class SplitServiceIT @Autowired constructor(
    val splitService: SplitService,
    val splitDao: SplitDao,
    val switchDao: LayoutSwitchDao,
) : DBTestBase() {

    @Test
    fun `should find splits by source location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getSplit(splitId)

        val foundSplits = splitService.findUnfinishedSplits(listOf(split.locationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `should find splits by target location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getSplit(splitId)

        val foundSplits = splitService.findUnfinishedSplits(listOf(split.targetLocationTracks.first().locationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    private fun insertSplitWithTwoTracks(): IntId<Split> {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val endTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        return splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTargetSaveRequest(endTrack.id, 0..0)),
            listOf(relinkedSwitchId),
        )
    }
}
