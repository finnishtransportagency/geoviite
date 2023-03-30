package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutKmPostServiceIT @Autowired constructor(
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
): ITTestBase() {

    @Test
    fun nearbyKmPostsAreReturnedInOrder() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPost1 = kmPostService.get(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                )
            ).rowVersion
        )
        val kmPost2 = kmPostService.get(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(2),
                    location = Point(2.0, 1.0),
                )
            ).rowVersion
        )
        val kmPost3 = kmPostService.get(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(3),
                    location = null,
                )
            ).rowVersion
        )

        kmPostDao.insert(
            kmPost(
                trackNumberId = insertOfficialTrackNumber(),
                km = KmNumber(4),
                location = null,
            )
        )

        val actual = kmPostService.listNearbyOnTrackPaged(
            OFFICIAL, Point(0.0, 0.0), trackNumberId, 0, null
        )
        val expected = listOf(kmPost3, kmPost1, kmPost2)
        assertEquals(expected, actual)
    }

    @Test
    fun findsKmPostAtKmNumber() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPost = kmPostService.get(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                )
            ).rowVersion
        )

        val result = kmPostService.getByKmNumber(OFFICIAL, trackNumberId, kmPost.kmNumber)
        assertNotNull(result)
        assertMatches(kmPost, result)
    }

    @Test
    fun doesntFindKmPostAtWrongKmNumber() {
        val trackNumberId = insertOfficialTrackNumber()
        kmPostDao.insert(kmPost(
            trackNumberId = trackNumberId,
            km = KmNumber(1),
            location = Point(1.0, 1.0),
        ))

        assertNull(kmPostService.getByKmNumber(OFFICIAL, trackNumberId, KmNumber(2)))
    }

    @Test
    fun doesntFindKmPostOnWrongTrack() {
        val trackNumber1Id = insertOfficialTrackNumber()
        val trackNumber2Id = insertOfficialTrackNumber()
        val kmPost = kmPostService.get(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumber1Id,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                )
            ).rowVersion
        )

        assertNull(kmPostService.getByKmNumber(OFFICIAL, trackNumber2Id, kmPost.kmNumber))
    }

    @Test
    fun draftKmPostIsDeletedOk() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPost = TrackLayoutKmPost(KmNumber(7654), Point(123.4, 234.5), LayoutState.IN_USE, trackNumberId, null)

        val draftId = kmPostService.saveDraft(draft(kmPost)).id
        val draftFromDb = kmPostService.getDraft(draftId)

        assertEquals(1, kmPostService.list(DRAFT) { k -> k == draftFromDb }.size)

        kmPostService.deleteUnpublishedDraft(draftId)

        assertEquals(0, kmPostService.list(DRAFT) { k -> k == draftFromDb }.size)
    }

    @Test
    fun kmPostIdIsReturnedWhenAddingNewKmPost() {
        val trackNumberId = insertDraftTrackNumber()
        val kmPost = TrackLayoutKmPostSaveRequest(
            kmNumber = someKmNumber(),
            state = LayoutState.IN_USE,
            trackNumberId = trackNumberId,
        )
        val kmPostId = kmPostService.insertKmPost(kmPost)

        val fetchedKmPost = kmPostService.getDraft(kmPostId)
        assertNull(kmPostService.getOfficial(kmPostId))

        assertEquals(DataType.STORED, fetchedKmPost.dataType)
        assertEquals(kmPost.kmNumber, fetchedKmPost.kmNumber)
        assertEquals(kmPost.state, fetchedKmPost.state)
        assertEquals(kmPost.trackNumberId, fetchedKmPost.trackNumberId)
    }
}
