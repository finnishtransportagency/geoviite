package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentService
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.geocodingContextCacheKey
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import validationVersions

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingDaoIT
@Autowired
constructor(
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
        referenceLineDao.save(referenceLine(id, alignmentVersion = alignmentVersion, draft = false))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, id))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, id))
    }

    @Test
    fun `Validation geocoding cache keys are calculated correctly`() {
        val tnOfficialVersion = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnOfficialVersion.id
        val tnDraft = testDBService.createDraft(tnOfficialVersion)

        val rlOfficial = mainOfficialContext.saveReferenceLine(referenceLineAndAlignment(tnId))
        val rlDraft = testDBService.createDraft(rlOfficial)

        val kmPost1Official = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val kmPost1Draft = testDBService.createDraft(kmPost1Official)
        val kmPost2OnlyDraft = mainDraftContext.save(kmPost(tnId, KmNumber(2)))
        val kmPost3OnlyOfficial = mainOfficialContext.save(kmPost(tnId, KmNumber(3)))

        // Add a deleted post - should not appear in results
        mainOfficialContext.save(kmPost(tnId, KmNumber(4), state = LayoutState.DELETED))

        val officialKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberId = tnId,
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficial,
                kmPostVersions = listOf(kmPost1Official, kmPost3OnlyOfficial),
            ),
            officialKey,
        )

        val draftKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberId = tnId,
                trackNumberVersion = tnDraft,
                referenceLineVersion = rlDraft,
                kmPostVersions = listOf(kmPost1Draft, kmPost2OnlyDraft, kmPost3OnlyOfficial),
            ),
            draftKey,
        )

        // Publishing nothing is the same as regular official
        assertEquals(officialKey, geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions()))

        // Publishing everything is the same as regular draft
        assertEquals(
            draftKey,
            geocodingDao.getLayoutGeocodingContextCacheKey(
                tnId,
                validationVersions(
                    trackNumbers = listOf(tnDraft),
                    referenceLines = listOf(rlDraft),
                    kmPosts = listOf(kmPost1Draft, kmPost2OnlyDraft),
                ),
            ),
        )

        // Publishing partial combines official with requested draft parts
        assertEquals(
            officialKey.copy(trackNumberVersion = tnDraft),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(trackNumbers = listOf(tnDraft))),
        )
        assertEquals(
            officialKey.copy(referenceLineVersion = rlDraft),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(referenceLines = listOf(rlDraft))),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPost1Draft, kmPost2OnlyDraft, kmPost3OnlyOfficial)),
            geocodingDao.getLayoutGeocodingContextCacheKey(
                tnId,
                validationVersions(kmPosts = listOf(kmPost1Draft, kmPost2OnlyDraft)),
            ),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPost1Draft, kmPost3OnlyOfficial)),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(kmPosts = listOf(kmPost1Draft))),
        )
    }

    @Test
    fun `Cache keys are correctly fetched by moment`() {
        val designBranch = testDBService.createDesignBranch()
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)

        // --- Version 1

        // First off, the main official versions for starting context
        val tnMainV1 = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnMainV1.id
        val rlMainV1 = mainOfficialContext.saveReferenceLine(referenceLineAndAlignment(tnId))
        val kmp1MainV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val kmp2MainV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(2)))

        // Add some draft changes as well. These shouldn't affect the results
        testDBService.createDraft(tnMainV1)
        testDBService.createDraft(rlMainV1)
        testDBService.createDraft(kmp1MainV1)
        mainDraftContext.save(kmPost(tnId, KmNumber(10)))

        // Add some design changes
        val tnDesignV1 = officialDesignContext.copyFrom(tnMainV1)
        val rlDesignV1 = officialDesignContext.copyFrom(rlMainV1)
        val kmp1DesignV1 = officialDesignContext.copyFrom(kmp1MainV1)
        val kmp3DesignV1 = officialDesignContext.save(kmPost(tnId, KmNumber(3)))

        // Design-draft changes should not affect results either
        testDBService.createDraft(tnDesignV1)
        testDBService.createDraft(kmp3DesignV1)
        designDraftContext.save(kmPost(tnId, KmNumber(11)))

        val version1Time = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val mainKeyV1 =
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnId, tnMainV1, rlMainV1, kmp1MainV1, kmp2MainV1), key)
            }

        val designKeyV1 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(
                    geocodingContextCacheKey(tnId, tnDesignV1, rlDesignV1, kmp1DesignV1, kmp2MainV1, kmp3DesignV1),
                    key,
                )
            }

        // --- Version 2

        // Update the official stuff
        val tnMainV2 = testDBService.update(tnMainV1)
        val rlMainV2 = testDBService.update(rlMainV1)
        val kmp1MainV2 = testDBService.update(kmp1MainV1)
        val kmp4MainV2 = mainOfficialContext.save(kmPost(tnId, KmNumber(4)))
        // Add a deleted post - should not appear in results
        mainOfficialContext.save(kmPost(tnId, KmNumber(5), state = LayoutState.DELETED))

        // Update the design stuff
        val tnDesignV2 = testDBService.update(tnDesignV1)
        val rlDesignV2 = testDBService.update(rlDesignV1)
        // Also delete one kmpost in the design -> it should be removed from the key
        testDBService.update(kmp1DesignV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        val kmp3DesignV2 = testDBService.update(kmp3DesignV1)

        val version2Time = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val mainKeyV2 =
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
                assertEquals(
                    geocodingContextCacheKey(tnId, tnMainV2, rlMainV2, kmp1MainV2, kmp2MainV1, kmp4MainV2),
                    key,
                )
            }

        val designKeyV2 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(
                    geocodingContextCacheKey(tnId, tnDesignV2, rlDesignV2, kmp2MainV1, kmp3DesignV2, kmp4MainV2),
                    key,
                )
            }

        // --- Version 3

        // Transition one design-only km-post to main
        val kmp3MainV3 = mainOfficialContext.moveFrom(kmp3DesignV2)
        // Mark kmp2 deleted
        testDBService.update(kmp2MainV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        // Delete some design-rows (should result in using main ones)
        referenceLineDao.deleteRow(rlDesignV2.rowId)
        kmPostDao.deleteRow(kmp1DesignV1.rowId)

        val version3Time = testDBService.layoutChangeTime()

        val mainKeyV3 =
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
                assertEquals(
                    geocodingContextCacheKey(tnId, tnMainV2, rlMainV2, kmp1MainV2, kmp3MainV3, kmp4MainV2),
                    key,
                )
            }
        val designKeyV3 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(
                    geocodingContextCacheKey(tnId, tnDesignV2, rlMainV2, kmp1MainV2, kmp3MainV3, kmp4MainV2),
                    key,
                )
            }

        // Verify fetching each key with time
        assertEquals(mainKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version1Time))
        assertEquals(designKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version1Time))
        assertEquals(mainKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version2Time))
        assertEquals(designKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version2Time))
        assertEquals(mainKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version3Time))
        assertEquals(designKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version3Time))
    }
}
