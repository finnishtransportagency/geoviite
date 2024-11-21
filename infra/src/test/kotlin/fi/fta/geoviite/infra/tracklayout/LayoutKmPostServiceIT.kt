package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.math.Point
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutKmPostServiceIT
@Autowired
constructor(
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
) : DBTestBase() {

    @Test
    fun nearbyKmPostsAreReturnedInOrder() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPost1 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(1),
                        roughLayoutLocation = Point(1.0, 1.0),
                        draft = false,
                    )
                )
            )
        val kmPost2 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(2),
                        roughLayoutLocation = Point(2.0, 1.0),
                        draft = false,
                    )
                )
            )
        val kmPost3 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(trackNumberId = trackNumberId, km = KmNumber(3), roughLayoutLocation = null, draft = false)
                )
            )

        kmPostDao.save(
            kmPost(
                trackNumberId = mainOfficialContext.createLayoutTrackNumber().id,
                km = KmNumber(4),
                roughLayoutLocation = null,
                draft = false,
            )
        )

        val actual =
            kmPostService.listNearbyOnTrackPaged(MainLayoutContext.official, Point(0.0, 0.0), trackNumberId, 0, null)
        val expected = listOf(kmPost3, kmPost1, kmPost2)
        assertEquals(expected, actual)
    }

    @Test
    fun findsKmPostAtKmNumber() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPost =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(1),
                        roughLayoutLocation = Point(1.0, 1.0),
                        draft = false,
                    )
                )
            )

        val result = kmPostService.getByKmNumber(MainLayoutContext.official, trackNumberId, kmPost.kmNumber, true)
        assertNotNull(result)
        assertMatches(kmPost, result)
    }

    @Test
    fun doesntFindKmPostAtWrongKmNumber() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        kmPostDao.save(
            kmPost(
                trackNumberId = trackNumberId,
                km = KmNumber(1),
                roughLayoutLocation = Point(1.0, 1.0),
                draft = false,
            )
        )

        assertNull(kmPostService.getByKmNumber(MainLayoutContext.official, trackNumberId, KmNumber(2), true))
    }

    @Test
    fun doesntFindKmPostOnWrongTrack() {
        val trackNumber1Id = mainOfficialContext.createLayoutTrackNumber().id
        val trackNumber2Id = mainOfficialContext.createLayoutTrackNumber().id
        val kmPost =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumber1Id,
                        km = KmNumber(1),
                        roughLayoutLocation = Point(1.0, 1.0),
                        draft = false,
                    )
                )
            )

        assertNull(kmPostService.getByKmNumber(MainLayoutContext.official, trackNumber2Id, kmPost.kmNumber, true))
    }

    @Test
    fun draftKmPostIsDeletedOk() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPost = kmPost(trackNumberId, KmNumber(7654), draft = true)

        val draftId = kmPostService.saveDraft(LayoutBranch.main, asMainDraft(kmPost)).id
        val draftFromDb = kmPostService.get(MainLayoutContext.draft, draftId)

        assertEquals(1, kmPostService.list(MainLayoutContext.draft) { k -> k == draftFromDb }.size)

        kmPostService.deleteDraft(LayoutBranch.main, draftId)

        assertEquals(0, kmPostService.list(MainLayoutContext.draft) { k -> k == draftFromDb }.size)
    }

    @Test
    fun kmPostIdIsReturnedWhenAddingNewKmPost() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val kmPost =
            TrackLayoutKmPostSaveRequest(
                kmNumber = someKmNumber(),
                state = LayoutState.IN_USE,
                trackNumberId = trackNumberId,
                gkLocation = null,
                sourceId = null,
            )
        val kmPostId = kmPostService.insertKmPost(LayoutBranch.main, kmPost)

        val fetchedKmPost = kmPostService.getOrThrow(MainLayoutContext.draft, kmPostId)
        assertNull(kmPostService.get(MainLayoutContext.official, kmPostId))

        assertEquals(DataType.STORED, fetchedKmPost.dataType)
        assertEquals(kmPost.kmNumber, fetchedKmPost.kmNumber)
        assertEquals(kmPost.state, fetchedKmPost.state)
        assertEquals(kmPost.trackNumberId, fetchedKmPost.trackNumberId)
    }

    @Test
    fun kmPostLengthMatchesTrackNumberService() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(0.0, 5.0), Point(1.0, 10.0), Point(3.0, 15.0), Point(4.0, 20.0))),
        )
        val kmPosts =
            listOf(
                kmPost(trackNumberId, KmNumber(1), Point(0.0, 3.0), draft = true),
                kmPost(trackNumberId, KmNumber(2), Point(0.0, 5.0), draft = true),
                kmPost(trackNumberId, KmNumber(3), null, draft = true),
                kmPost(trackNumberId, KmNumber(4), Point(0.0, 6.0), state = LayoutState.NOT_IN_USE, draft = true),
                kmPost(trackNumberId, KmNumber(5, "A"), Point(3.0, 14.0), draft = true),
                kmPost(trackNumberId, KmNumber(5, "AA"), Point(6.0, 18.0), draft = true),
            )
        val kmPostSaveResults = kmPosts.map { d -> kmPostService.saveDraft(LayoutBranch.main, d) }.map { it.id }

        // drop(1) because the track number km lengths include the section before the first km post
        val expected = trackNumberService.getKmLengths(MainLayoutContext.draft, trackNumberId)!!.drop(1)
        val actual =
            kmPostSaveResults.mapNotNull { kmPost ->
                kmPostService.getKmPostInfoboxExtras(MainLayoutContext.draft, kmPost).kmLength
            }
        assertEquals(expected.size, actual.size)
        expected.zip(actual) { e, a -> assertEquals(e.length.toDouble(), a, 0.001) }
    }
}
