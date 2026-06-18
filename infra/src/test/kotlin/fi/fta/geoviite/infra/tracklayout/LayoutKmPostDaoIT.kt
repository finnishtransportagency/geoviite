package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.publication.PublicationInMain
import fi.fta.geoviite.infra.tracklayout.LayoutState.DELETED
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.tracklayout.LayoutState.NOT_IN_USE
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutKmPostDaoIT @Autowired constructor(private val kmPostDao: LayoutKmPostDao) : DBTestBase() {

    @Test
    fun kmPostsAreStoredAndLoadedOk() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val post1 =
            kmPost(
                trackNumberId = trackNumberId,
                km = KmNumber(123),
                gkLocation =
                    kmPostGkLocation(
                        GeometryPoint(25500000.0, 6675000.0, Srid(3879)),
                        KmPostGkLocationSource.FROM_GEOMETRY,
                        true,
                    ),
                state = IN_USE,
                draft = false,
            )
        val post2 =
            kmPost(
                trackNumberId = trackNumberId,
                km = KmNumber(125),
                gkLocation = kmPostGkLocation(GeometryPoint(25500005.0, 6675005.0, Srid(3879))),
                state = NOT_IN_USE,
                draft = false,
            )
        insertAndVerify(post1)
        insertAndVerify(post2)
        val allPosts = fetchTrackNumberKmPosts(OFFICIAL, trackNumberId)
        assertEquals(2, allPosts.size)
        assertMatches(post1, allPosts[0])
        assertMatches(post2, allPosts[1])
    }

    @Test
    fun `KMPost official and draft versions are fetched correctly`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val official = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(234)))
        assertEquals(official.id.intValue, official.id.intValue)
        assertEquals(official, kmPostDao.fetchVersion(MainLayoutContext.official, official.id))

        val draft = mainDraftContext.save(kmPost(trackNumberId, KmNumber(432)))
        assertNull(kmPostDao.fetchVersion(MainLayoutContext.official, draft.id))
        assertEquals(draft, kmPostDao.fetchVersion(MainLayoutContext.draft, draft.id))

        val alteredDraft = testDBService.createDraft(official)
        assertEquals(official.id, alteredDraft.id)
        assertEquals(official, kmPostDao.fetchVersion(MainLayoutContext.official, alteredDraft.id))
        assertEquals(alteredDraft, kmPostDao.fetchVersion(MainLayoutContext.draft, alteredDraft.id))
    }

    @Test
    fun `Multi-fetching object versions works correctly`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        val official1a = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(1), state = IN_USE))
        val official1b = testDBService.update(official1a) { kmp -> kmp.copy(state = NOT_IN_USE) }
        val official2a = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(2), state = IN_USE))
        val official2b = testDBService.update(official2a) { kmp -> kmp.copy(state = NOT_IN_USE) }
        val draft1a = mainDraftContext.save(kmPost(trackNumberId, KmNumber(3), state = IN_USE))
        val draft1b = testDBService.update(draft1a) { kmp -> kmp.copy(state = NOT_IN_USE) }
        val draft2a = mainDraftContext.save(kmPost(trackNumberId, KmNumber(4), state = IN_USE))
        val draft2b = testDBService.update(draft2a) { kmp -> kmp.copy(state = NOT_IN_USE) }

        val allVersions = listOf(official1a, official1b, official2a, official2b, draft1a, draft1b, draft2a, draft2b)
        // Verify that that individual version fetches return different objects as expected
        allVersions.forEach { version ->
            allVersions
                .filter { it != version }
                .forEach { otherVersion -> assertNotEquals(kmPostDao.fetch(version), kmPostDao.fetch(otherVersion)) }
        }

        // Verify that multi-fetches return the expected objects
        assertEquals(
            setOf(kmPostDao.fetch(official1a), kmPostDao.fetch(official2b), kmPostDao.fetch(draft2a)),
            kmPostDao.fetchMany(listOf(official1a, official2b, draft2a)).toSet(),
        )
        assertEquals(
            setOf(kmPostDao.fetch(official1b), kmPostDao.fetch(draft1b), kmPostDao.fetch(draft2b)),
            kmPostDao.fetchMany(listOf(official1b, draft1b, draft2b)).toSet(),
        )
    }

    @Test
    fun kmPostVersioningWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val tempPost = kmPost(trackNumberId, KmNumber(1), gkLocation = null)
        val insertVersion = mainOfficialContext.save(tempPost)
        val id = insertVersion.id
        val inserted = kmPostDao.fetch(insertVersion)
        assertMatches(tempPost, inserted, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft1 = asMainDraft(inserted).copy(kmNumber = KmNumber(2))
        val draftVersion1 = kmPostDao.save(tempDraft1)
        val draft1 = kmPostDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, kmPostDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft2 = draft1.copy(kmNumber = KmNumber(3))
        val draftVersion2 = kmPostDao.save(tempDraft2)
        val draft2 = kmPostDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2, contextMatch = false)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, kmPostDao.fetchVersion(MainLayoutContext.draft, id))

        kmPostDao.deleteDraft(LayoutBranch.main, id)
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, kmPostDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, kmPostDao.fetch(insertVersion))
        assertEquals(draft1, kmPostDao.fetch(draftVersion1))
        assertEquals(draft2, kmPostDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { kmPostDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun fetchVersionsForPublicationReturnsDraftsOnlyForPublishableSet() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val postOneOfficial = kmPostDao.save(kmPost(trackNumberId, KmNumber(1), draft = false))
        val postOneDraft = kmPostDao.save(asMainDraft(kmPostDao.fetch(postOneOfficial)))
        val postTwoOfficial = kmPostDao.save(kmPost(trackNumberId, KmNumber(2), draft = false))
        kmPostDao.save(asMainDraft(kmPostDao.fetch(postTwoOfficial)))
        val postThreeOnlyDraft = kmPostDao.save(kmPost(trackNumberId, KmNumber(3), draft = true))
        val postFourOnlyOfficial = kmPostDao.save(kmPost(trackNumberId, KmNumber(4), draft = false))

        val target = PublicationInMain
        val versionsEmpty =
            kmPostDao.fetchVersionsForPublication(target, listOf(trackNumberId), listOf())[trackNumberId]!!
        val versionsOnlyOne =
            kmPostDao
                .fetchVersionsForPublication(target, listOf(trackNumberId), listOf(postOneOfficial.id))[trackNumberId]!!
        val versionsOneAndThree =
            kmPostDao
                .fetchVersionsForPublication(
                    target,
                    listOf(trackNumberId),
                    listOf(postOneOfficial.id, postThreeOnlyDraft.id),
                )[trackNumberId]!!

        assertEquals(setOf(postOneOfficial, postTwoOfficial, postFourOnlyOfficial), versionsEmpty.toSet())
        assertEquals(setOf(postOneDraft, postTwoOfficial, postFourOnlyOfficial), versionsOnlyOne.toSet())
        assertEquals(
            setOf(postOneDraft, postTwoOfficial, postThreeOnlyDraft, postFourOnlyOfficial),
            versionsOneAndThree.toSet(),
        )
    }

    @Test
    fun listingKmPostVersionsWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val officialVersion = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val undeletedDraftVersion = mainDraftContext.save(kmPost(tnId, KmNumber(2)))
        val deleteStateDraftVersion = mainDraftContext.save(kmPost(tnId, KmNumber(3), state = DELETED))
        val deletedDraftVersion = mainDraftContext.save(kmPost(tnId, KmNumber(4)))
        kmPostDao.deleteDraft(LayoutBranch.main, deletedDraftVersion.id)

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

        val kmPost1MainV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val kmPost2DesignV1 = designOfficialContext.save(kmPost(tnId, KmNumber(2)))
        val kmPost3DesignV1 = designOfficialContext.save(kmPost(tnId, KmNumber(3)))
        val v1Time = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val kmPost1MainV2 = testDBService.update(kmPost1MainV1)
        val kmPost1DesignV2 = designOfficialContext.copyFrom(kmPost1MainV1)
        val kmPost2DesignV2 = testDBService.update(kmPost2DesignV1)
        kmPostDao.deleteRow(kmPost3DesignV1.rowId)
        val v2Time = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        kmPostDao.deleteRow(kmPost1DesignV2.rowId)
        // Fake publish: update the design as a main-official
        val kmPost2MainV3 = mainOfficialContext.moveFrom(kmPost2DesignV2)
        val v3Time = kmPostDao.fetchChangeTime()

        val kmPost1Id = kmPost1MainV1.id
        val kmPost2Id = kmPost2DesignV1.id
        val kmPost3Id = kmPost3DesignV1.id

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
        val officialTrackVersion1 = insertOfficial(tnId, 1)
        val officialTrackVersion2 = insertOfficial(tnId, 2)
        val draftTrackVersion = insertDraft(tnId, 3)

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
    fun `Finding KM-Post by TrackNumber works for drafts`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val tnId2 = mainOfficialContext.createLayoutTrackNumber().id
        val undeletedDraftVersion = mainDraftContext.save(kmPost(tnId, KmNumber(1)))
        val deleteStateDraftVersion = mainDraftContext.save(kmPost(tnId, KmNumber(2), state = DELETED))
        val changeTrackNumberOriginal = mainOfficialContext.save(kmPost(tnId, KmNumber(3)))
        val changeTrackNumberChanged =
            testDBService.createDraft(changeTrackNumberOriginal) { kmp -> kmp.copy(trackNumberId = tnId2) }
        val deletedDraftId = mainDraftContext.save(kmPost(tnId, KmNumber(4))).id
        kmPostDao.deleteDraft(LayoutBranch.main, deletedDraftId)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            kmPostDao.fetchVersions(MainLayoutContext.official, false, tnId),
        )
        assertEquals(listOf(undeletedDraftVersion), kmPostDao.fetchVersions(MainLayoutContext.draft, false, tnId))

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            kmPostDao.fetchVersions(MainLayoutContext.draft, true, tnId).toSet(),
        )
        assertEquals(listOf(changeTrackNumberChanged), kmPostDao.fetchVersions(MainLayoutContext.draft, true, tnId2))
    }

    private fun insertOfficial(tnId: IntId<LayoutTrackNumber>, kmNumber: Int): LayoutRowVersion<LayoutKmPost> {
        return kmPostDao.save(kmPost(tnId, KmNumber(kmNumber), draft = false))
    }

    private fun insertDraft(
        tnId: IntId<LayoutTrackNumber>,
        kmNumber: Int,
        state: LayoutState = IN_USE,
    ): LayoutRowVersion<LayoutKmPost> {
        return kmPostDao.save(kmPost(tnId, KmNumber(kmNumber), state = state, draft = true))
    }

    fun insertAndVerify(post: LayoutKmPost) {
        val rowVersion = kmPostDao.save(post)
        val fromDb = kmPostDao.fetch(rowVersion)
        assertEquals(DataType.TEMP, post.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(post, fromDb, contextMatch = false)
    }

    private fun fetchTrackNumberKmPosts(
        publicationState: PublicationState,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): List<LayoutKmPost> {
        return kmPostDao.fetchVersions(MainLayoutContext.of(publicationState), false, trackNumberId, null).map {
            rowVersion ->
            kmPostDao.fetch(rowVersion)
        }
    }
}
