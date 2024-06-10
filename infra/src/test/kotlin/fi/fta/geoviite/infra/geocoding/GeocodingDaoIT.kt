package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
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
import fi.fta.geoviite.infra.tracklayout.geocodingContextCacheKey
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
        val designBranch = LayoutBranch.design(designDao.insert(layoutDesign()))
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)

        // --- Version 1

        // First off, the main official versions for starting context
        val (tnId, tnMainV1) = mainOfficialContext.createLayoutTrackNumber()
        val (rlId, rlMainV1) = mainOfficialContext.insert(referenceLineAndAlignment(tnId))
        val (kmp1Id, kmp1MainV1) = mainOfficialContext.insert(kmPost(tnId, KmNumber(1)))
        val (_, kmp2MainV1) = mainOfficialContext.insert(kmPost(tnId, KmNumber(2)))

        // Add some draft changes as well. These shouldn't affect the results
        mainDraftContext.copyFrom(tnMainV1, officialRowId = tnId)
        mainDraftContext.copyFrom(rlMainV1, officialRowId = rlId)
        mainDraftContext.copyFrom(kmp1MainV1, officialRowId = kmp1Id)
        mainDraftContext.insert(kmPost(tnId, KmNumber(10)))

        // Add some design changes
        val tnDesignV1 = officialDesignContext.copyFrom(tnMainV1, officialRowId = tnId).rowVersion
        val rlDesignV1 = officialDesignContext.copyFrom(rlMainV1, officialRowId = rlId).rowVersion
        val kmp1DesignV1 = officialDesignContext.copyFrom(kmp1MainV1, officialRowId = kmp1Id).rowVersion
        val kmp3DesignV1 = officialDesignContext.insert(kmPost(tnId, KmNumber(3))).rowVersion

        // Design-draft changes should not affect results either
        designDraftContext.copyFrom(tnDesignV1, officialRowId = tnId, designRowId = tnDesignV1.id)
        designDraftContext.copyFrom(kmp3DesignV1, officialRowId = null, designRowId = kmp3DesignV1.id)
        designDraftContext.insert(kmPost(tnId, KmNumber(11)))

        val version1Time = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val mainKeyV1 = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnMainV1, rlMainV1, kmp1MainV1, kmp2MainV1), key)
        }

        val designKeyV1 = geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnDesignV1, rlDesignV1, kmp1DesignV1, kmp2MainV1, kmp3DesignV1), key)
        }

        // --- Version 2

        // Update the official stuff
        val tnMainV2 = testDBService.update(tnMainV1).rowVersion
        val rlMainV2 = testDBService.update(rlMainV1).rowVersion
        val kmp1MainV2 = testDBService.update(kmp1MainV1).rowVersion
        val kmp4MainV2 = mainOfficialContext.insert(kmPost(tnId, KmNumber(4))).rowVersion
        // Add a deleted post - should not appear in results
        mainOfficialContext.insert(kmPost(tnId, KmNumber(5), state = LayoutState.DELETED))

        // Update the design stuff
        val tnDesignV2 = testDBService.update(tnDesignV1).rowVersion
        val rlDesignV2 = testDBService.update(rlDesignV1).rowVersion
        // Also delete one kmpost in the design -> it should be removed from the key
        testDBService.update(kmp1DesignV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        val kmp3DesignV2 = testDBService.update(kmp3DesignV1).rowVersion

        val version2Time = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val mainKeyV2 = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnMainV2, rlMainV2, kmp1MainV2, kmp2MainV1, kmp4MainV2), key)
        }

        val designKeyV2 = geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnDesignV2, rlDesignV2, kmp2MainV1, kmp3DesignV2, kmp4MainV2), key)
        }

        // --- Version 3

        // Transition one design-only km-post to main
        val kmp3MainV3 = mainOfficialContext.moveFrom(kmp3DesignV2).rowVersion
        // Mark kmp2 deleted
        testDBService.update(kmp2MainV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }.rowVersion
        // Delete some design-rows (should result in using main ones)
        referenceLineDao.deleteRow(rlDesignV2.id)
        kmPostDao.deleteRow(kmp1DesignV1.id)

        val version3Time = testDBService.layoutChangeTime()

        val mainKeyV3 = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnMainV2, rlMainV2, kmp1MainV2, kmp3MainV3, kmp4MainV2), key)
        }
        val designKeyV3 = geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
            assertEquals(geocodingContextCacheKey(tnDesignV2, rlMainV2, kmp1MainV2, kmp3MainV3, kmp4MainV2), key)
        }

        // Verify fetching each key with time
        assertEquals(mainKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version1Time))
        assertEquals(designKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version1Time))
        assertEquals(mainKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version2Time))
        assertEquals(designKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version2Time))
        assertEquals(mainKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version3Time))
        assertEquals(designKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version3Time))
    }

    private fun createDraftReferenceLine(officialVersion: RowVersion<ReferenceLine>): RowVersion<ReferenceLine> {
        val original = referenceLineDao.fetch(officialVersion)
        assertFalse(original.isDraft)
        return referenceLineDao.insert(
            asDraft(original.branch, original).copy(alignmentVersion = alignmentService.duplicate(original.alignmentVersion!!))
        ).rowVersion
    }
}
