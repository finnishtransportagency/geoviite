package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutKmPostDaoIT @Autowired constructor(
    private val kmPostDao: LayoutKmPostDao,
): ITTestBase() {

    @Test
    fun kmPostsAreStoredAndLoadedOk() {
        val trackNumberId = insertOfficialTrackNumber()
        val post1 = TrackLayoutKmPost(KmNumber(123), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val post2 = TrackLayoutKmPost(KmNumber(125), Point(125.6, 236.7), NOT_IN_USE, trackNumberId, null)
        val post3 = TrackLayoutKmPost(KmNumber(124), Point(124.5, 235.6), PLANNED, trackNumberId, null)
        insertAndVerify(post1)
        insertAndVerify(post2)
        insertAndVerify(post3)
        val allPosts = fetchTrackNumberKmPosts(OFFICIAL, trackNumberId)
        assertEquals(3, allPosts.size)
        assertMatches(post1, allPosts[0])
        assertMatches(post2, allPosts[2]) // The order should be by post-number, not insert order
        assertMatches(post3, allPosts[1])
    }

    @Test
    fun checkingIfKmPostIsOfficialWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val post1 = TrackLayoutKmPost(KmNumber(234), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val officialId = kmPostDao.insert(post1).id

        assertEquals(officialId, kmPostDao.fetchOfficialVersion(officialId)?.id)

        val post2 = TrackLayoutKmPost(KmNumber(432), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val draftId = kmPostDao.insert(draft(post2)).id

        assertNull(kmPostDao.fetchOfficialVersion(draftId)?.id)
    }

    @Test
    fun kmPostVersioningWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val tempPost = kmPost(trackNumberId, KmNumber(1), Point(1.0, 1.0))
        val insertVersion = kmPostDao.insert(tempPost).rowVersion
        val inserted = kmPostDao.fetch(insertVersion)
        assertMatches(tempPost, inserted)
        assertEquals(VersionPair(insertVersion, null), kmPostDao.fetchVersionPair(insertVersion.id))

        val tempDraft1 = draft(inserted).copy(location = Point(2.0, 2.0))
        val draftVersion1 = kmPostDao.insert(tempDraft1).rowVersion
        val draft1 = kmPostDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), kmPostDao.fetchVersionPair(insertVersion.id))

        val tempDraft2 = draft1.copy(location = Point(3.0, 3.0))
        val draftVersion2 = kmPostDao.update(tempDraft2).rowVersion
        val draft2 = kmPostDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), kmPostDao.fetchVersionPair(insertVersion.id))

        kmPostDao.deleteDraft(insertVersion.id)
        assertEquals(VersionPair(insertVersion, null), kmPostDao.fetchVersionPair(insertVersion.id))

        assertEquals(inserted, kmPostDao.fetch(insertVersion))
        assertEquals(draft1, kmPostDao.fetch(draftVersion1))
        assertEquals(draft2, kmPostDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { kmPostDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun fetchVersionsForPublicationReturnsDraftsOnlyForPublishableSet() {
        val trackNumberId = insertOfficialTrackNumber()
        val (postOneId, postOneOfficial) = kmPostDao.insert(kmPost(trackNumberId, KmNumber(1)))
        val postOneDraft = kmPostDao.insert(draft(kmPostDao.fetch(postOneOfficial))).rowVersion
        val (postTwoId, postTwoOfficial) = kmPostDao.insert(kmPost(trackNumberId, KmNumber(2)))
        val postTwoDraft = kmPostDao.insert(draft(kmPostDao.fetch(postTwoOfficial))).rowVersion
        val (postThreeId, postThreeOnlyDraft) = kmPostDao.insert(draft(kmPost(trackNumberId, KmNumber(3))))
        val postFourOnlyOfficial = kmPostDao.insert(kmPost(trackNumberId, KmNumber(4))).rowVersion

        val versionsEmpty = kmPostDao.fetchVersionsForPublication(trackNumberId, listOf())
        val versionsOnlyOne = kmPostDao.fetchVersionsForPublication(trackNumberId, listOf(postOneId))
        val versionsOneAndThree = kmPostDao.fetchVersionsForPublication(trackNumberId, listOf(postOneId, postThreeId))

        assertEquals(setOf(postOneOfficial, postTwoOfficial, postFourOnlyOfficial), versionsEmpty.toSet())
        assertEquals(setOf(postOneDraft, postTwoOfficial, postFourOnlyOfficial), versionsOnlyOne.toSet())
        assertEquals(setOf(postOneDraft, postTwoOfficial, postThreeOnlyDraft, postFourOnlyOfficial), versionsOneAndThree.toSet())
    }

    @Test
    fun listingKmPostVersionsWorks() {
        val tnId = insertOfficialTrackNumber()
        val officialVersion = insertOfficial(tnId, 1).rowVersion
        val undeletedDraftVersion = insertDraft(tnId, 2).rowVersion
        val deleteStateDraftVersion = insertDraft(tnId, 3, DELETED).rowVersion
        val (deletedDraftId, deletedDraftVersion) = insertDraft(tnId, 4)
        kmPostDao.deleteDraft(deletedDraftId)

        val official = kmPostDao.fetchVersions(OFFICIAL, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = kmPostDao.fetchVersions(DRAFT, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = kmPostDao.fetchVersions(DRAFT, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun fetchOfficialVersionByMomentWorks() {
        val tnId = insertOfficialTrackNumber()
        val beforeCreationTime = kmPostDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val firstVersion = insertOfficial(tnId, 1).rowVersion
        val firstVersionTime = kmPostDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val updatedVersion = updateOfficial(firstVersion).rowVersion
        val updatedVersionTime = kmPostDao.fetchChangeTime()

        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(firstVersion.id, beforeCreationTime))
        assertEquals(firstVersion, kmPostDao.fetchOfficialVersionAtMoment(firstVersion.id, firstVersionTime))
        assertEquals(updatedVersion, kmPostDao.fetchOfficialVersionAtMoment(firstVersion.id, updatedVersionTime))
    }

    @Test
    fun findingKmPostsByTrackNumberWorksForOfficial() {
        val tnId = insertOfficialTrackNumber()
        val officialTrackVersion1 = insertOfficial(tnId, 1).rowVersion
        val officialTrackVersion2 = insertOfficial(tnId, 2).rowVersion
        val draftTrackVersion = insertDraft(tnId, 3).rowVersion

        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2).toSet(),
            kmPostDao.fetchVersions(OFFICIAL, false, tnId).toSet(),
        )
        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2, draftTrackVersion),
            kmPostDao.fetchVersions(DRAFT, false, tnId),
        )
    }

    @Test
    fun findingKmPostsByTrackNumberWorksForDraft() {
        val tnId = insertOfficialTrackNumber()
        val tnId2 = insertOfficialTrackNumber()
        val undeletedDraftVersion = insertDraft(tnId, 1).rowVersion
        val deleteStateDraftVersion = insertDraft(tnId, 2, DELETED).rowVersion
        val changeTrackNumberOriginal = insertOfficial(tnId, 3).rowVersion
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal, tnId2).rowVersion
        val deletedDraftVersion = insertDraft(tnId, 4).rowVersion
        kmPostDao.deleteDraft(deletedDraftVersion.id)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            kmPostDao.fetchVersions(OFFICIAL, false, tnId),
        )
        assertEquals(
            listOf(undeletedDraftVersion),
            kmPostDao.fetchVersions(DRAFT, false, tnId),
        )

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            kmPostDao.fetchVersions(DRAFT, true, tnId).toSet(),
        )
        assertEquals(
            listOf(changeTrackNumberChanged),
            kmPostDao.fetchVersions(DRAFT, true, tnId2),
        )
    }

    private fun insertOfficial(tnId: IntId<TrackLayoutTrackNumber>, kmNumber: Int): DaoResponse<TrackLayoutKmPost> {
        val post = kmPost(tnId, KmNumber(kmNumber))
        return kmPostDao.insert(post.copy(draft = null))
    }

    private fun insertDraft(
        tnId: IntId<TrackLayoutTrackNumber>,
        kmNumber: Int,
        state: LayoutState = IN_USE,
    ): DaoResponse<TrackLayoutKmPost> {
        val post = kmPost(tnId, KmNumber(kmNumber))
        return kmPostDao.insert(draft(post).copy(state = state))
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: RowVersion<TrackLayoutKmPost>,
        newTrackNumber: IntId<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutKmPost> {
        val track = kmPostDao.fetch(trackVersion)
        assertNull(track.draft)
        return kmPostDao.insert(draft(track).copy(trackNumberId = newTrackNumber))
    }

    private fun updateOfficial(originalVersion: RowVersion<TrackLayoutKmPost>): DaoResponse<TrackLayoutKmPost> {
        val original = kmPostDao.fetch(originalVersion)
        assertNull(original.draft)
        return kmPostDao.update(original.copy(location = original.location!!.copy(x = original.location!!.x + 1.0)))
    }

    fun insertAndVerify(post: TrackLayoutKmPost) {
        val rowVersion = kmPostDao.insert(post).rowVersion
        assertMatches(post, kmPostDao.fetch(rowVersion))
    }

    private fun fetchTrackNumberKmPosts(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        return kmPostDao.fetchVersions(publishType, false, trackNumberId, null).map(kmPostDao::fetch)
    }
}
