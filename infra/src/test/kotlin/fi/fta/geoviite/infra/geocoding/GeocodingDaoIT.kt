package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentService
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asDraft
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import validationVersions

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingDaoIT @Autowired constructor(
    val geocodingDao: GeocodingDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val alignmentService: LayoutAlignmentService,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val designDao: LayoutDesignDao,
) : DBTestBase() {

    @Test
    fun trackNumberWithoutReferenceLineHasNoContext() {
        val id = mainOfficialContext.createLayoutTrackNumber().id
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, id))
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, id))
    }

    @Test
    fun trackNumberWithoutKmPostsHasAContext() {
        val id = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        referenceLineDao.insert(referenceLine(id, alignmentVersion = alignmentVersion, draft = false))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, id))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, id))
    }

    @Test
    fun cacheKeysAreCalculatedCorrectly() {
        val tnOfficialResponse = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnOfficialResponse.id
        val tnOfficialVersion = tnOfficialResponse.rowVersion
        val tnDraftVersion = trackNumberDao.insert(asMainDraft(trackNumberDao.fetch(tnOfficialVersion))).rowVersion

        val alignmentVersion = alignmentDao.insert(alignment())
        val rlOfficialVersion = referenceLineDao.insert(
            referenceLine(tnId, alignmentVersion = alignmentVersion, draft = false)
        ).rowVersion
        val rlDraftVersion = createDraftReferenceLine(rlOfficialVersion)

        val kmPostOneOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(1), draft = false)).rowVersion
        val kmPostOneDraftVersion = kmPostDao.insert(asMainDraft(kmPostDao.fetch(kmPostOneOfficialVersion))).rowVersion
        val kmPostTwoOnlyDraftVersion = kmPostService.saveDraft(
            LayoutBranch.main,
            kmPost(tnId, KmNumber(2), draft = true),
        ).rowVersion
        val kmPostThreeOnlyOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(3), draft = false)).rowVersion

        // Add a deleted post - should not appear in results
        kmPostDao.insert(kmPost(tnId, KmNumber(4), state = LayoutState.DELETED, draft = false))

        val officialKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficialVersion,
                kmPostVersions = listOf(kmPostOneOfficialVersion, kmPostThreeOnlyOfficialVersion)
            ),
            officialKey,
        )

        val draftKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnDraftVersion,
                referenceLineVersion = rlDraftVersion,
                kmPostVersions = listOf(kmPostOneDraftVersion, kmPostTwoOnlyDraftVersion, kmPostThreeOnlyOfficialVersion)
            ),
            draftKey,
        )

        // Publishing nothing is the same as regular official
        assertEquals(
            officialKey,
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions()),
        )

        // Publishing everything is the same as regular draft
        assertEquals(
            draftKey,
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(
                trackNumbers = listOf(tnId to tnDraftVersion),
                referenceLines = listOf(rlOfficialVersion.id to rlDraftVersion),
                kmPosts = listOf(
                    kmPostOneOfficialVersion.id to kmPostOneDraftVersion,
                    kmPostTwoOnlyDraftVersion.id to kmPostTwoOnlyDraftVersion,
                ),
            )),
        )

        // Publishing partial combines official with requested draft parts
        assertEquals(
            officialKey.copy(trackNumberVersion = tnDraftVersion),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(
                trackNumbers = listOf(tnId to tnDraftVersion),
            )),
        )
        assertEquals(
            officialKey.copy(referenceLineVersion = rlDraftVersion),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(
                referenceLines = listOf(rlOfficialVersion.id to rlDraftVersion),
            )),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPostOneDraftVersion, kmPostTwoOnlyDraftVersion, kmPostThreeOnlyOfficialVersion)),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(
                kmPosts = listOf(
                    kmPostOneOfficialVersion.id to kmPostOneDraftVersion,
                    kmPostTwoOnlyDraftVersion.id to kmPostTwoOnlyDraftVersion,
                ),
            )),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPostOneDraftVersion, kmPostThreeOnlyOfficialVersion)),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(
                kmPosts = listOf(kmPostOneOfficialVersion.id to kmPostOneDraftVersion),
            )),
        )
    }

    @Test
    fun `Cache keys are correctly fetched by moment`() {
        // First off, the main official versions for starting context
        val (tnId, tnOfficialVersion) = mainOfficialContext.createLayoutTrackNumber()
        val (rlId, rlOfficialVersion) = mainOfficialContext.insert(referenceLineAndAlignment(tnId))
        val (kmp1Id, kmp1OfficialVersion) = mainOfficialContext.insert(kmPost(tnId, KmNumber(1)))

        // Add some draft changes as well. These shouldn't affect the results
        mainDraftContext.copyFrom(tnOfficialVersion, officialRowId = tnId)
        mainDraftContext.copyFrom(rlOfficialVersion, officialRowId = rlId)
        mainDraftContext.copyFrom(kmp1OfficialVersion, officialRowId = kmp1Id)
        mainDraftContext.insert(kmPost(tnId, KmNumber(10)))

        // Add some design changes
        val designBranch = LayoutBranch.design(designDao.insert(layoutDesign()))
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)

        val tnDesignVersion = officialDesignContext.copyFrom(tnOfficialVersion, officialRowId = tnId).rowVersion
        val rlDesignVersion = officialDesignContext.copyFrom(rlOfficialVersion, officialRowId = rlId).rowVersion
        val kmp1DesignVersion = officialDesignContext.copyFrom(kmp1OfficialVersion, officialRowId = kmp1Id).rowVersion
        val kmp2DesignVersion = officialDesignContext.insert(kmPost(tnId, KmNumber(2))).rowVersion

        // Design-draft changes should not affect results either
        designDraftContext.copyFrom(tnDesignVersion, officialRowId = tnId, designRowId = tnDesignVersion.id)
        designDraftContext.copyFrom(rlDesignVersion, officialRowId = rlId, designRowId = rlDesignVersion.id)
        designDraftContext.copyFrom(kmp1DesignVersion, officialRowId = kmp1Id, designRowId = kmp1DesignVersion.id)
        designDraftContext.copyFrom(kmp2DesignVersion, officialRowId = null, designRowId = kmp2DesignVersion.id)
        designDraftContext.insert(kmPost(tnId, KmNumber(11)))

        val originalTime = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val originalKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficialVersion,
                kmPostVersions = listOf(kmp1OfficialVersion),
            ),
            originalKey,
        )

        val originalDesignKey = geocodingDao.getLayoutGeocodingContextCacheKey(LayoutContext.of(designBranch, OFFICIAL), tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnDesignVersion,
                referenceLineVersion = rlDesignVersion,
                kmPostVersions = listOf(kmp1DesignVersion, kmp2DesignVersion),
            ),
            originalDesignKey,
        )

        // Update the official stuff
        val updatedTrackNumberVersion = testDBService.update(tnOfficialVersion).rowVersion
        val updatedReferenceLineVersion = testDBService.update(rlOfficialVersion).rowVersion
        val updatedKmPost1Version = testDBService.update(kmp1OfficialVersion).rowVersion
        val kmPost2Version = mainOfficialContext.insert(kmPost(tnId, KmNumber(2))).rowVersion
        // Add a deleted post - should not appear in results
        mainOfficialContext.insert(kmPost(tnId, KmNumber(3), state = LayoutState.DELETED))

        // Update the design stuff
        val updatedDesignTrackNumberVersion = testDBService.update(tnDesignVersion).rowVersion
        val updatedDesignReferenceLineVersion = testDBService.update(rlDesignVersion).rowVersion
        // Also delete one kmpost in the design -> it should be removed from the key
        testDBService.update(kmp1DesignVersion) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        val updatedDesignKmPost2Version = testDBService.update(kmp2DesignVersion).rowVersion

        val updatedTime = kmPostDao.fetchChangeTime()

        val updatedKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = updatedTrackNumberVersion,
                referenceLineVersion = updatedReferenceLineVersion,
                kmPostVersions = listOf(updatedKmPost1Version, kmPost2Version),
            ),
            updatedKey,
        )

        val updatedDesignKey = geocodingDao.getLayoutGeocodingContextCacheKey(LayoutContext.of(designBranch, OFFICIAL), tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = updatedDesignTrackNumberVersion,
                referenceLineVersion = updatedDesignReferenceLineVersion,
                kmPostVersions = listOf(updatedDesignKmPost2Version, kmPost2Version),
            ),
            updatedDesignKey,
        )

        // Verify fetching each key with time
        assertEquals(originalKey, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, originalTime))
        assertEquals(originalDesignKey, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, originalTime))
        assertEquals(updatedKey, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, updatedTime))
        assertEquals(updatedDesignKey, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, updatedTime))
    }

    private fun createDraftReferenceLine(officialVersion: RowVersion<ReferenceLine>): RowVersion<ReferenceLine> {
        val original = referenceLineDao.fetch(officialVersion)
        assertFalse(original.isDraft)
        return referenceLineDao.insert(
            asDraft(original.branch, original).copy(alignmentVersion = alignmentService.duplicate(original.alignmentVersion!!))
        ).rowVersion
    }
}
