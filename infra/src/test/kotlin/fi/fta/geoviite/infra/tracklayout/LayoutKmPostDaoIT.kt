package fi.fta.geoviite.infra.tracklayout

import daoResponseToValidationVersion
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState.DELETED
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.tracklayout.LayoutState.NOT_IN_USE
import fi.fta.geoviite.infra.tracklayout.LayoutState.PLANNED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
) : DBTestBase() {

    @Test
    fun kmPostsAreStoredAndLoadedOk() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val post1 = kmPost(
            trackNumberId = trackNumberId,
            km = KmNumber(123),
            location = Point(123.4, 234.5),
            state = IN_USE,
            draft = false,
        )
        val post2 = kmPost(
            trackNumberId = trackNumberId,
            km = KmNumber(125),
            location = Point(125.6, 236.7),
            state = NOT_IN_USE,
            draft = false,
        )
        val post3 = kmPost(
            trackNumberId = trackNumberId,
            km = KmNumber(124),
            location = Point(124.5, 235.6),
            state = PLANNED,
            draft = false,
        )
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
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val officialId = kmPostDao.insert(kmPost(trackNumberId, KmNumber(234), draft = false)).id
        assertEquals(officialId, kmPostDao.fetchVersion(MainLayoutContext.official, officialId)?.id)

        val draftId = kmPostDao.insert(kmPost(trackNumberId, KmNumber(432), draft = true)).id
        assertNull(kmPostDao.fetchVersion(MainLayoutContext.official, draftId))
        assertEquals(draftId, kmPostDao.fetchVersion(MainLayoutContext.draft, draftId)?.id)
    }

    @Test
    fun kmPostVersioningWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val tempPost = kmPost(trackNumberId, KmNumber(1), Point(1.0, 1.0), draft = false)
        val insertVersion = kmPostDao.insert(tempPost).rowVersion
        val inserted = kmPostDao.fetch(insertVersion)
        assertMatches(tempPost, inserted, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        val tempDraft1 = asMainDraft(inserted).copy(location = Point(2.0, 2.0))
        val draftVersion1 = kmPostDao.insert(tempDraft1).rowVersion
        val draft1 = kmPostDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(draftVersion1, kmPostDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        val tempDraft2 = draft1.copy(location = Point(3.0, 3.0))
        val draftVersion2 = kmPostDao.update(tempDraft2).rowVersion
        val draft2 = kmPostDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(draftVersion2, kmPostDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        kmPostDao.deleteDraft(LayoutBranch.main, insertVersion.id)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, insertVersion.id))
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.draft, insertVersion.id))

        assertEquals(inserted, kmPostDao.fetch(insertVersion))
        assertEquals(draft1, kmPostDao.fetch(draftVersion1))
        assertEquals(draft2, kmPostDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { kmPostDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun fetchVersionsForPublicationReturnsDraftsOnlyForPublishableSet() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val postOneOfficial = kmPostDao.insert(kmPost(trackNumberId, KmNumber(1), draft = false))
        val postOneDraft = kmPostDao.insert(asMainDraft(kmPostDao.fetch(postOneOfficial.rowVersion)))
        val postTwoOfficial = kmPostDao.insert(kmPost(trackNumberId, KmNumber(2), draft = false))
        kmPostDao.insert(asMainDraft(kmPostDao.fetch(postTwoOfficial.rowVersion)))
        val postThreeOnlyDraft = kmPostDao.insert(kmPost(trackNumberId, KmNumber(3), draft = true))
        val postFourOnlyOfficial = kmPostDao.insert(kmPost(trackNumberId, KmNumber(4), draft = false))

        val versionsEmpty = kmPostDao.fetchVersionsForPublication(
            LayoutBranch.main,
            listOf(trackNumberId),
            listOf(),
        )[trackNumberId]!!
        val versionsOnlyOne = kmPostDao.fetchVersionsForPublication(
            LayoutBranch.main,
            listOf(trackNumberId),
            listOf(postOneOfficial.id),
        )[trackNumberId]!!
        val versionsOneAndThree = kmPostDao.fetchVersionsForPublication(
            LayoutBranch.main,
            listOf(trackNumberId),
            listOf(postOneOfficial.id, postThreeOnlyDraft.id),
        )[trackNumberId]!!

        assertEquals(validationVersions(postOneOfficial, postTwoOfficial, postFourOnlyOfficial), versionsEmpty.toSet())
        assertEquals(validationVersions(postOneDraft, postTwoOfficial, postFourOnlyOfficial), versionsOnlyOne.toSet())
        assertEquals(
            validationVersions(postOneDraft, postTwoOfficial, postThreeOnlyDraft, postFourOnlyOfficial),
            versionsOneAndThree.toSet(),
        )
    }
    private fun <T> validationVersions(vararg daoResponses: DaoResponse<T>): Set<ValidationVersion<T>> =
        setOf(*daoResponses.map(::daoResponseToValidationVersion).toTypedArray())

    @Test
    fun listingKmPostVersionsWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val officialVersion = insertOfficial(tnId, 1).rowVersion
        val undeletedDraftVersion = insertDraft(tnId, 2).rowVersion
        val deleteStateDraftVersion = insertDraft(tnId, 3, DELETED).rowVersion
        val (deletedDraftId, deletedDraftVersion) = insertDraft(tnId, 4)
        kmPostDao.deleteDraft(LayoutBranch.main, deletedDraftId)

        val official = kmPostDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = kmPostDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = kmPostDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun `Finding KM-Post versions by moment works across designs and main branch`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)

        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val v0Time = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val (kmPost1Id, kmPost1MainV1) = mainOfficialContext.insert(kmPost(tnId, KmNumber(1)))
        val (kmPost2Id, kmPost2DesignV1) = designOfficialContext.insert(kmPost(tnId, KmNumber(2)))
        val (kmPost3Id, kmPost3DesignV1) = designOfficialContext.insert(kmPost(tnId, KmNumber(3)))
        val v1Time = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val kmPost1MainV2 = testDBService.update(kmPost1MainV1).rowVersion
        val kmPost1DesignV2 = designOfficialContext.copyFrom(kmPost1MainV1, officialRowId = kmPost1MainV1.id).rowVersion
        val kmPost2DesignV2 = testDBService.update(kmPost2DesignV1).rowVersion
        kmPostDao.deleteRow(kmPost3DesignV1.id)
        val v2Time = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        kmPostDao.deleteRow(kmPost1DesignV2.id)
        // Fake publish: update the design as a main-official
        val kmPost2MainV3 = mainOfficialContext.moveFrom(kmPost2DesignV2).rowVersion
        val v3Time = kmPostDao.fetchChangeTime()

        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost1Id, v0Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost2Id, v0Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost3Id, v0Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost1Id, v0Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost2Id, v0Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost3Id, v0Time))

        assertEquals(kmPost1MainV1, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost1Id, v1Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost2Id, v1Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost3Id, v1Time))
        assertEquals(kmPost1MainV1, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost1Id, v1Time))
        assertEquals(kmPost2DesignV1, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost2Id, v1Time))
        assertEquals(kmPost3DesignV1, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost3Id, v1Time))

        assertEquals(kmPost1MainV2, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost1Id, v2Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost2Id, v2Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost3Id, v2Time))
        assertEquals(kmPost1DesignV2, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost1Id, v2Time))
        assertEquals(kmPost2DesignV2, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost2Id, v2Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost3Id, v2Time))

        assertEquals(kmPost1MainV2, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost1Id, v3Time))
        assertEquals(kmPost2MainV3, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost2Id, v3Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(LayoutBranch.main, kmPost3Id, v3Time))
        assertEquals(kmPost1MainV2, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost1Id, v3Time))
        assertEquals(kmPost2MainV3, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost2Id, v3Time))
        assertEquals(null, kmPostDao.fetchOfficialVersionAtMoment(designBranch, kmPost3Id, v3Time))
    }

    @Test
    fun findingKmPostsByTrackNumberWorksForOfficial() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val officialTrackVersion1 = insertOfficial(tnId, 1).rowVersion
        val officialTrackVersion2 = insertOfficial(tnId, 2).rowVersion
        val draftTrackVersion = insertDraft(tnId, 3).rowVersion

        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2).toSet(),
            kmPostDao.fetchVersions(MainLayoutContext.official, false, tnId).toSet(),
        )
        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2, draftTrackVersion),
            kmPostDao.fetchVersions(MainLayoutContext.draft, false, tnId),
        )
    }

    @Test
    fun findingKmPostsByTrackNumberWorksForDraft() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val tnId2 = mainOfficialContext.createLayoutTrackNumber().id
        val undeletedDraftVersion = insertDraft(tnId, 1).rowVersion
        val deleteStateDraftVersion = insertDraft(tnId, 2, DELETED).rowVersion
        val changeTrackNumberOriginal = insertOfficial(tnId, 3).rowVersion
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal, tnId2).rowVersion
        val deletedDraftVersion = insertDraft(tnId, 4).rowVersion
        kmPostDao.deleteDraft(LayoutBranch.main, deletedDraftVersion.id)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            kmPostDao.fetchVersions(MainLayoutContext.official, false, tnId),
        )
        assertEquals(
            listOf(undeletedDraftVersion),
            kmPostDao.fetchVersions(MainLayoutContext.draft, false, tnId),
        )

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            kmPostDao.fetchVersions(MainLayoutContext.draft, true, tnId).toSet(),
        )
        assertEquals(
            listOf(changeTrackNumberChanged),
            kmPostDao.fetchVersions(MainLayoutContext.draft, true, tnId2),
        )
    }

    private fun insertOfficial(tnId: IntId<TrackLayoutTrackNumber>, kmNumber: Int): DaoResponse<TrackLayoutKmPost> {
        return kmPostDao.insert(kmPost(tnId, KmNumber(kmNumber), draft = false))
    }

    private fun insertDraft(
        tnId: IntId<TrackLayoutTrackNumber>,
        kmNumber: Int,
        state: LayoutState = IN_USE,
    ): DaoResponse<TrackLayoutKmPost> {
        return kmPostDao.insert(kmPost(tnId, KmNumber(kmNumber), state = state, draft = true))
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: RowVersion<TrackLayoutKmPost>,
        newTrackNumber: IntId<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutKmPost> {
        val track = kmPostDao.fetch(trackVersion)
        assertFalse(track.isDraft)
        return kmPostDao.insert(asMainDraft(track).copy(trackNumberId = newTrackNumber))
    }

    private fun updateOfficial(originalVersion: RowVersion<TrackLayoutKmPost>): DaoResponse<TrackLayoutKmPost> {
        val original = kmPostDao.fetch(originalVersion)
        assertFalse(original.isDraft)
        return kmPostDao.update(original.copy(location = original.location!!.copy(x = original.location!!.x + 1.0)))
    }

    fun insertAndVerify(post: TrackLayoutKmPost) {
        val rowVersion = kmPostDao.insert(post).rowVersion
        val fromDb = kmPostDao.fetch(rowVersion)
        assertEquals(DataType.TEMP, post.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(post, fromDb, contextMatch = false)
    }

    private fun fetchTrackNumberKmPosts(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        return kmPostDao
            .fetchVersions(MainLayoutContext.of(publicationState), false, trackNumberId, null)
            .map(kmPostDao::fetch)
    }
}
