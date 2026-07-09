package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationInDesign
import fi.fta.geoviite.infra.publication.validationVersions
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.DesignOfficialContextData
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.geocodingContextCacheKey
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingDaoIT
@Autowired
constructor(
    val geocodingDao: GeocodingDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val trackNumberService: LayoutTrackNumberService,
) : DBTestBase() {

    @Test
    fun trackNumberWithoutKmPostsHasAContext() {
        val id = mainOfficialContext.createLayoutTrackNumber().id
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, id))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, id))
    }

    @Test
    fun `Validation geocoding cache keys are calculated correctly`() {
        val tnOfficialVersion = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnOfficialVersion.id
        val tnDraft = testDBService.createDraft(tnOfficialVersion)

        val kmPost1Official = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val kmPost1Draft = testDBService.createDraft(kmPost1Official)
        val kmPost2OnlyDraft = mainDraftContext.save(kmPost(tnId, KmNumber(2)))
        val kmPost3OnlyOfficial = mainOfficialContext.save(kmPost(tnId, KmNumber(3)))

        // Add a deleted post - should not appear in results
        mainOfficialContext.save(kmPost(tnId, KmNumber(4), state = LayoutState.DELETED))

        val officialKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                kmPostVersions = listOf(kmPost1Official, kmPost3OnlyOfficial),
            ),
            officialKey,
        )

        val draftKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.draft, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnDraft,
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
                validationVersions(trackNumbers = listOf(tnDraft), kmPosts = listOf(kmPost1Draft, kmPost2OnlyDraft)),
            ),
        )

        // Publishing partial combines official with requested draft parts
        assertEquals(
            officialKey.copy(trackNumberVersion = tnDraft),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(trackNumbers = listOf(tnDraft))),
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
        val kmp1MainV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val kmp2MainV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(2)))

        // Add some draft changes as well. These shouldn't affect the results
        testDBService.createDraft(tnMainV1)
        testDBService.createDraft(kmp1MainV1)
        mainDraftContext.save(kmPost(tnId, KmNumber(10)))

        // Add some design changes
        val tnDesignV1 = officialDesignContext.copyFrom(tnMainV1)
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
                assertEquals(geocodingContextCacheKey(tnMainV1, kmp1MainV1, kmp2MainV1), key)
            }

        val designKeyV1 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnDesignV1, kmp1DesignV1, kmp2MainV1, kmp3DesignV1), key)
            }

        // --- Version 2

        // Update the official stuff
        val tnMainV2 = testDBService.update(tnMainV1)
        val kmp1MainV2 = testDBService.update(kmp1MainV1)
        val kmp4MainV2 = mainOfficialContext.save(kmPost(tnId, KmNumber(4)))
        // Add a deleted post - should not appear in results
        mainOfficialContext.save(kmPost(tnId, KmNumber(5), state = LayoutState.DELETED))

        // Update the design stuff
        val tnDesignV2 = testDBService.update(tnDesignV1)
        // Also delete one kmpost in the design -> it should be removed from the key
        testDBService.update(kmp1DesignV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        val kmp3DesignV2 = testDBService.update(kmp3DesignV1)

        val version2Time = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val mainKeyV2 =
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnMainV2, kmp1MainV2, kmp2MainV1, kmp4MainV2), key)
            }

        val designKeyV2 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnDesignV2, kmp2MainV1, kmp3DesignV2, kmp4MainV2), key)
            }

        // --- Version 3

        // Transition one design-only km-post to main
        val kmp3MainV3 = mainOfficialContext.moveFrom(kmp3DesignV2)
        // Mark kmp2 deleted
        testDBService.update(kmp2MainV1) { kmp -> kmp.copy(state = LayoutState.DELETED) }
        // Delete some design-rows (should result in using main ones)
        kmPostDao.deleteRow(kmp1DesignV1.rowId)

        val version3Time = testDBService.layoutChangeTime()

        val mainKeyV3 =
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnMainV2, kmp1MainV2, kmp3MainV3, kmp4MainV2), key)
            }
        val designKeyV3 =
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId).also { key ->
                assertEquals(geocodingContextCacheKey(tnDesignV2, kmp1MainV2, kmp3MainV3, kmp4MainV2), key)
            }

        // Verify fetching each key with time
        assertEquals(mainKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version1Time))
        assertEquals(designKeyV1, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version1Time))
        assertEquals(mainKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version2Time))
        assertEquals(designKeyV2, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version2Time))
        assertEquals(mainKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, version3Time))
        assertEquals(designKeyV3, geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, version3Time))
    }

    // A deleted track number is likely borked for geocoding and should never be used.
    // The issues are (at least) that:
    // - DELETED track numbers are not validated in publication to be intact
    // - There is no way to differentiate between km-posts that are deleted because the track number was deleted and
    //   ones that were already deleted before that
    @Test
    fun `No cache keys are returned for deleted TrackNumbers`() {
        val tnV1 =
            mainOfficialContext.createLayoutTrackNumber(
                geometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
            )
        val tnId = tnV1.id
        val kmpV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val dbTimeAfterInit = testDBService.getDbTime()
        assertEquals(
            geocodingContextCacheKey(tnV1, kmpV1),
            geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId),
        )

        testDBService.update(tnV1) { tn -> tn.copy(state = LayoutState.DELETED) }
        val dbTimeAfterDelete = testDBService.getDbTime()
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId))

        // Verify moment fetches as well
        assertEquals(
            geocodingContextCacheKey(tnV1, kmpV1),
            geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, dbTimeAfterInit),
        )
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, dbTimeAfterDelete))
    }

    @Test
    fun `Validation versions work with DELETED track number being restored`() {
        // Create a valid initial state with an existing cache key
        val tnOfficial = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnOfficial.id
        val initialKey = geocodingDao.getLayoutGeocodingContextCacheKey(MainLayoutContext.official, tnId)
        assertEquals(geocodingContextCacheKey(tnOfficial), initialKey)

        // Delete the track number, resulting in the cache key disappearing
        mainOfficialContext.mutate(tnId) { it.copy(state = LayoutState.DELETED) }
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions()))

        // Create a draft version that restores the track number to IN_USE state, restoring the cache key
        val restored = mainDraftContext.mutate(tnId) { it.copy(state = LayoutState.IN_USE) }!!
        assertEquals(
            geocodingContextCacheKey(restored),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(trackNumbers = listOf(restored))),
        )

        // Without draft-override, the result should still be null
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions()))
    }

    @Test
    fun `Cancelled design rows are excluded from moment cache keys`() {
        val designBranch = testDBService.createDesignBranch()
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)

        val tnMain = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnMain.id
        val kmp1Main = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))

        // The design edits one of main's km posts and adds one of its own
        val tnDesign = officialDesignContext.copyFrom(tnMain)
        val kmp1Design = officialDesignContext.copyFrom(kmp1Main)
        val kmp2DesignOnly = officialDesignContext.save(kmPost(tnId, KmNumber(2)))

        val beforeCancellation = testDBService.layoutChangeTime()
        Thread.sleep(1) // Ensure the cancellations get a new changetime so that moment-fetch makes sense
        assertEquals(
            geocodingContextCacheKey(tnDesign, kmp1Design, kmp2DesignOnly),
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, beforeCancellation),
        )

        cancelDesignOfficial(kmp1Design)
        cancelDesignOfficial(kmp2DesignOnly)
        val afterKmPostCancellation = testDBService.layoutChangeTime()
        Thread.sleep(1)

        // The edited km post falls back to the main row it was hiding, while the design-only one is gone entirely
        assertEquals(
            geocodingContextCacheKey(tnDesign, kmp1Main),
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, afterKmPostCancellation),
        )

        cancelDesignOfficial(tnDesign)
        val afterTrackNumberCancellation = testDBService.layoutChangeTime()

        assertEquals(
            geocodingContextCacheKey(tnMain, kmp1Main),
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, afterTrackNumberCancellation),
        )
        // The moment fetch should agree with the layout context fetch, which has always skipped cancellations
        assertEquals(
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch.official, tnId),
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, afterTrackNumberCancellation),
        )
        // Main is unaffected by any of this, and the earlier moment still sees the design's own rows
        assertEquals(
            geocodingContextCacheKey(tnMain, kmp1Main),
            geocodingDao.getLayoutGeocodingContextCacheKey(LayoutBranch.main, tnId, afterTrackNumberCancellation),
        )
        assertEquals(
            geocodingContextCacheKey(tnDesign, kmp1Design, kmp2DesignOnly),
            geocodingDao.getLayoutGeocodingContextCacheKey(designBranch, tnId, beforeCancellation),
        )
    }

    @Test
    fun `Cancelled design rows are excluded from validation version cache keys`() {
        val designBranch = testDBService.createDesignBranch()
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)
        val target = PublicationInDesign(designBranch)

        val tnMain = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnMain.id
        val kmp1Main = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))

        // The design edits one of main's km posts and adds one of its own
        val tnDesign = officialDesignContext.copyFrom(tnMain)
        val kmp1Design = officialDesignContext.copyFrom(kmp1Main)
        val kmp2DesignOnly = officialDesignContext.save(kmPost(tnId, KmNumber(2)))

        // Publishing nothing is the same as the design's official state
        assertEquals(
            geocodingContextCacheKey(tnDesign, kmp1Design, kmp2DesignOnly),
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(target = target)),
        )

        val kmp1Cancellation = kmPostService.cancel(designBranch, kmp1Design.id)!!
        val kmp2Cancellation = kmPostService.cancel(designBranch, kmp2DesignOnly.id)!!
        val tnCancellation = trackNumberService.cancel(designBranch, tnId)!!

        // Cancellation is marked by designAssetState, so a cancellation candidate still looks IN_USE
        assertEquals(LayoutState.IN_USE, kmPostDao.fetch(kmp1Cancellation).state)

        // Publishing the km post cancellations: kmp1 reverts to its main row, kmp2 has none and disappears
        assertEquals(
            geocodingContextCacheKey(tnDesign, kmp1Main),
            geocodingDao.getLayoutGeocodingContextCacheKey(
                tnId,
                validationVersions(kmPosts = listOf(kmp1Cancellation, kmp2Cancellation), target = target),
            ),
        )

        // Publishing the track number cancellation as well reverts it to its main row
        assertEquals(
            geocodingContextCacheKey(tnMain, kmp1Main),
            geocodingDao.getLayoutGeocodingContextCacheKey(
                tnId,
                validationVersions(
                    trackNumbers = listOf(tnCancellation),
                    kmPosts = listOf(kmp1Cancellation, kmp2Cancellation),
                    target = target,
                ),
            ),
        )
    }

    @Test
    fun `Cancelling creation of a new design track number yields no validation cache key`() {
        val designBranch = testDBService.createDesignBranch()
        val officialDesignContext = testDBService.testContext(designBranch, OFFICIAL)
        val target = PublicationInDesign(designBranch)

        // Create a track number directly in the design — it has no main official counterpart
        val tnDesignOnly = officialDesignContext.createLayoutTrackNumber()
        val tnId = tnDesignOnly.id

        // Before cancellation, the design track number has a valid cache key
        assertNotNull(
            geocodingDao.getLayoutGeocodingContextCacheKey(tnId, validationVersions(target = target)),
        )

        // Cancel the creation of this track number
        val tnCancellation = trackNumberService.cancel(designBranch, tnId)!!

        // The cancellation resolves to null because there is no main official row to fall back to
        assertNull(
            geocodingDao.getLayoutGeocodingContextCacheKey(
                tnId,
                validationVersions(trackNumbers = listOf(tnCancellation), target = target),
            ),
        )
    }

    /**
     * Marks a design-official row cancelled, as publishing a cancellation does: the row stays in place, non-deleted and
     * IN_USE, and only its designAssetState says that it no longer applies.
     */
    private inline fun <reified T : LayoutAsset<T>> cancelDesignOfficial(
        version: LayoutRowVersion<T>
    ): LayoutRowVersion<T> =
        testDBService.update(version) { asset ->
            asset.withContext(
                (asset.contextData as DesignOfficialContextData<T>).copy(designAssetState = DesignAssetState.CANCELLED)
            )
        }
}
