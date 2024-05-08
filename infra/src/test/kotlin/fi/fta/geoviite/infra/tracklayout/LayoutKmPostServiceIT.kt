package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
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
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
) : DBTestBase() {

    @Test
    fun nearbyKmPostsAreReturnedInOrder() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPost1 = kmPostDao.fetch(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                    draft = false,
                )
            ).rowVersion
        )
        val kmPost2 = kmPostDao.fetch(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(2),
                    location = Point(2.0, 1.0),
                    draft = false,
                )
            ).rowVersion
        )
        val kmPost3 = kmPostDao.fetch(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(3),
                    location = null,
                    draft = false,
                )
            ).rowVersion
        )

        kmPostDao.insert(
            kmPost(
                trackNumberId = insertOfficialTrackNumber(),
                km = KmNumber(4),
                location = null,
                draft = false,
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
        val kmPost = kmPostDao.fetch(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                    draft = false,
                )
            ).rowVersion
        )

        val result = kmPostService.getByKmNumber(OFFICIAL, trackNumberId, kmPost.kmNumber, true)
        assertNotNull(result)
        assertMatches(kmPost, result)
    }

    @Test
    fun doesntFindKmPostAtWrongKmNumber() {
        val trackNumberId = insertOfficialTrackNumber()
        kmPostDao.insert(
            kmPost(
                trackNumberId = trackNumberId,
                km = KmNumber(1),
                location = Point(1.0, 1.0),
                draft = false,
            )
        )

        assertNull(kmPostService.getByKmNumber(OFFICIAL, trackNumberId, KmNumber(2), true))
    }

    @Test
    fun doesntFindKmPostOnWrongTrack() {
        val trackNumber1Id = insertOfficialTrackNumber()
        val trackNumber2Id = insertOfficialTrackNumber()
        val kmPost = kmPostDao.fetch(
            kmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumber1Id,
                    km = KmNumber(1),
                    location = Point(1.0, 1.0),
                    draft = false,
                )
            ).rowVersion
        )

        assertNull(kmPostService.getByKmNumber(OFFICIAL, trackNumber2Id, kmPost.kmNumber, true))
    }

    @Test
    fun draftKmPostIsDeletedOk() {
        val trackNumberId = insertOfficialTrackNumber()
        val kmPost = kmPost(trackNumberId, KmNumber(7654), draft = true)

        val draftId = kmPostService.saveDraft(LayoutBranch.main, asMainDraft(kmPost)).id
        val draftFromDb = kmPostService.get(DRAFT, draftId)

        assertEquals(1, kmPostService.list(DRAFT) { k -> k == draftFromDb }.size)

        kmPostService.deleteDraft(draftId)

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
        val kmPostId = kmPostService.insertKmPost(LayoutBranch.main, kmPost)

        val fetchedKmPost = kmPostService.get(DRAFT, kmPostId)!!
        assertNull(kmPostService.get(OFFICIAL, kmPostId))

        assertEquals(DataType.STORED, fetchedKmPost.dataType)
        assertEquals(kmPost.kmNumber, fetchedKmPost.kmNumber)
        assertEquals(kmPost.state, fetchedKmPost.state)
        assertEquals(kmPost.trackNumberId, fetchedKmPost.trackNumberId)
    }

    @Test
    fun kmPostLengthMatchesTrackNumberService() {
        val trackNumberId = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId, draft = true),
            alignment(
                segment(
                    Point(0.0, 0.0),
                    Point(0.0, 5.0),
                    Point(1.0, 10.0),
                    Point(3.0, 15.0),
                    Point(4.0, 20.0),
                )
            )
        )
        val kmPosts = listOf(
            kmPost(trackNumberId, KmNumber(1), Point(0.0, 3.0), draft = true),
            kmPost(trackNumberId, KmNumber(2), Point(0.0, 5.0), draft = true),
            kmPost(trackNumberId, KmNumber(3), null, draft = true),
            kmPost(trackNumberId, KmNumber(4), Point(0.0, 6.0), state = LayoutState.NOT_IN_USE, draft = true),
            kmPost(trackNumberId, KmNumber(5), Point(0.0, 10.0), state = LayoutState.PLANNED, draft = true),
            kmPost(trackNumberId, KmNumber(6, "A"), Point(3.0, 14.0), draft = true),
            kmPost(trackNumberId, KmNumber(6, "AA"), Point(6.0, 18.0), draft = true),
        )
        val kmPostSaveResults = kmPosts
            .map { d -> kmPostService.saveDraft(LayoutBranch.main, d) }
            .map(DaoResponse<TrackLayoutKmPost>::id)

        // drop(1) because the track number km lengths include the section before the first km post
        val expected = trackNumberService.getKmLengths(DRAFT, trackNumberId)!!.drop(1)
        val actual = kmPostSaveResults.mapNotNull { kmPost -> kmPostService.getSingleKmPostLength(DRAFT, kmPost) }
        assertEquals(expected.size, actual.size)
        expected.zip(actual) { e, a -> assertEquals(e.length.toDouble(), a, 0.001) }
    }
}
