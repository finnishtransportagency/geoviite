package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.linking.PublishRequest
import fi.fta.geoviite.infra.linking.PublishService
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingDaoIT @Autowired constructor(
    val geocodingDao: GeocodingDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val referenceLineService: ReferenceLineService,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val publishService: PublishService,
) : ITTestBase() {

    @Test
    fun changeTimePicksUpReferenceLineChange() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.1, 1.1)))
        val alignmentVersion = alignmentDao.insert(alignment)
        val referenceLineVersion = referenceLineDao.insert(
            ReferenceLine(
                trackNumberId = trackNumberId,
                startAddress = TrackMeter.ZERO,
                alignmentVersion = alignmentVersion,
                sourceId = null,
            )
        )
        val referenceLine = referenceLineDao.fetch(referenceLineVersion)
        assertChangeTimeProceedsWhen(trackNumberId, PublishType.DRAFT) {
            referenceLineService.saveDraft(referenceLine.copy(), alignment)
        }
    }

    @Test
    fun changeTimePicksUpEditedKmPost() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id

        val kmPostVersion = kmPostDao.insert(kmPost(trackNumberId, KmNumber(1)))
        val kmPost = kmPostDao.fetch(kmPostVersion)
        assertChangeTimeProceedsWhen(trackNumberId, PublishType.DRAFT) {
            kmPostService.updateKmPost(
                kmPostVersion.id, TrackLayoutKmPostSaveRequest(
                    kmNumber = KmNumber(1, "A"),
                    state = kmPost.state,
                    trackNumberId = kmPost.trackNumberId as IntId
                )
            )
        }
        assertChangeTimeProceedsWhen(trackNumberId, PublishType.OFFICIAL) {
            publishService.publishChanges(
                PublishRequest(
                    listOf(),
                    listOf(),
                    listOf(),
                    listOf(),
                    listOf(kmPostVersion.id)
                )
            )
        }
    }

    @Test
    fun changeTimePicksUpDeletedKmPostDraft() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val kmPostDraft =
            kmPostService.insertKmPost(TrackLayoutKmPostSaveRequest(KmNumber(1), LayoutState.IN_USE, trackNumberId))
        assertChangeTimeProceedsWhen(trackNumberId, PublishType.DRAFT) {
            kmPostService.deleteDraft(kmPostDraft)
        }
    }

    fun assertChangeTimeProceedsWhen(trackNumberId: IntId<TrackLayoutTrackNumber>, publishType: PublishType, block: () -> Unit) {
        val before = geocodingDao.getGeocodingContextCacheKey(publishType, trackNumberId)!!.changeTime
        block()
        val after = geocodingDao.getGeocodingContextCacheKey(publishType, trackNumberId)!!.changeTime
        assertTrue(before.isBefore(after), "on track number $trackNumberId, expected $before before $after")
    }
}
