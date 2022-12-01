package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.linking.PublishDao
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
@Disabled
class RatkoServiceIT @Autowired constructor(
    val publishDao: PublishDao,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val ratkoService: RatkoService,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
): ITTestBase() {
    @Test
    fun startRatkoPublish() {
        ratkoService.pushChangesToRatko(getCurrentUserName())
    }

    @Test
    @Disabled
    fun changeSetTest() {
        val trackNumber = trackNumber().copy()
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        val alignmentVersion = alignmentDao.insert(alignment())
        val trackVersion =
            locationTrackDao.insert(locationTrack(trackNumberId = trackNumberId).copy(alignmentVersion = alignmentVersion))
        val draft = locationTrackService.getDraft(trackVersion.id).let { orig ->
            orig.copy(name = AlignmentName("${orig.name}-draft"))
        }
        val locationTracks = listOf(
            locationTrackService.publish(
                locationTrackService.saveDraft(draft, alignmentDao.fetch(alignmentVersion)).id
            )
        )
        publishDao.createPublish(listOf(), listOf(), locationTracks, listOf(), listOf())

        ratkoService.pushChangesToRatko(getCurrentUserName())
    }
}
