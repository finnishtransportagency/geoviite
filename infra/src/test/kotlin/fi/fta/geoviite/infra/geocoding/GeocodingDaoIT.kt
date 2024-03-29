package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.*
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
) : DBTestBase() {

    @Test
    fun trackNumberWithoutReferenceLineHasNoContext() {
        val id = trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false)).id
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(DRAFT, id))
        assertNull(geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, id))
    }

    @Test
    fun trackNumberWithoutKmPostsHasAContext() {
        val id = trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false)).id
        val alignmentVersion = alignmentDao.insert(alignment())
        referenceLineDao.insert(referenceLine(id, alignmentVersion = alignmentVersion, draft = false))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(DRAFT, id))
        assertNotNull(geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, id))
    }

    @Test
    fun cacheKeysAreCalculatedCorrectly() {
        val tnOfficialResponse = trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false))
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
        val kmPostTwoOnlyDraftVersion = kmPostService.saveDraft(kmPost(tnId, KmNumber(2), draft = true)).rowVersion
        val kmPostThreeOnlyOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(3), draft = false)).rowVersion

        // Add a deleted post - should not appear in results
        kmPostDao.insert(kmPost(tnId, KmNumber(4), state = LayoutState.DELETED, draft = false))

        val officialKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficialVersion,
                kmPostVersions = listOf(kmPostOneOfficialVersion, kmPostThreeOnlyOfficialVersion)
            ),
            officialKey,
        )

        val draftKey = geocodingDao.getLayoutGeocodingContextCacheKey(DRAFT, tnId)!!
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
    fun cacheKeysAreCorrectlyFetchedByMoment() {
        val tnOfficialResponse = trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false))
        val tnId = tnOfficialResponse.id
        val tnOfficialVersion = tnOfficialResponse.rowVersion
        val alignmentVersion = alignmentDao.insert(alignment())
        val rlOfficialVersion = referenceLineDao.insert(
            referenceLine(tnId, alignmentVersion = alignmentVersion, draft = false)
        ).rowVersion
        val kmPostOneOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(1), draft = false)).rowVersion

        val originalTime = kmPostDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that later objects get a new changetime so that moment-fetch makes sense

        val originalKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficialVersion,
                kmPostVersions = listOf(kmPostOneOfficialVersion)
            ),
            originalKey,
        )

        // Add some draft changes as well. These shouldn't affect the results
        trackNumberDao.insert(asMainDraft(trackNumberDao.fetch(tnOfficialVersion)))
        createDraftReferenceLine(rlOfficialVersion)
        kmPostService.saveDraft(kmPost(tnId, KmNumber(10), draft = true))

        // Update the official stuff
        val updatedTrackNumberVersion = updateTrackNumber(tnOfficialVersion)
        val updatedReferenceLineVersion = updateReferenceLine(rlOfficialVersion)
        val updatedKmPostOneOfficialVersion = updateKmPost(kmPostOneOfficialVersion)
        val kmPostTwoOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(2), draft = false)).rowVersion
        // Add a deleted post - should not appear in results
        kmPostDao.insert(kmPost(tnId, KmNumber(3), state = LayoutState.DELETED, draft = false))

        val updatedTime = kmPostDao.fetchChangeTime()

        val updatedKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, tnId)!!
        assertEquals(
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = updatedTrackNumberVersion,
                referenceLineVersion = updatedReferenceLineVersion,
                kmPostVersions = listOf(updatedKmPostOneOfficialVersion, kmPostTwoOfficialVersion)
            ),
            updatedKey,
        )

        // Verify fetching each key with time
        assertEquals(originalKey, geocodingDao.getLayoutGeocodingContextCacheKey(tnId, originalTime))
        assertEquals(updatedKey, geocodingDao.getLayoutGeocodingContextCacheKey(tnId, updatedTime))
    }

    private fun updateTrackNumber(version: RowVersion<TrackLayoutTrackNumber>): RowVersion<TrackLayoutTrackNumber> {
        val original = trackNumberDao.fetch(version)
        return trackNumberDao.update(original.copy(description = original.description+"_update")).rowVersion
    }

    private fun updateReferenceLine(version: RowVersion<ReferenceLine>): RowVersion<ReferenceLine> {
        val original = referenceLineDao.fetch(version)
        return referenceLineDao.update(original.copy(startAddress = original.startAddress + 1.0)).rowVersion
    }

    private fun updateKmPost(version: RowVersion<TrackLayoutKmPost>): RowVersion<TrackLayoutKmPost> {
        val original = kmPostDao.fetch(version)
        return kmPostDao.update(
            original.copy(location = original.location!!.copy(x = original.location!!.x + 1.0))
        ).rowVersion
    }

    private fun createDraftReferenceLine(officialVersion: RowVersion<ReferenceLine>): RowVersion<ReferenceLine> {
        val original = referenceLineDao.fetch(officialVersion)
        assertFalse(original.isDraft)
        return referenceLineDao.insert(
            asMainDraft(original).copy(alignmentVersion = alignmentService.duplicate(original.alignmentVersion!!))
        ).rowVersion
    }
}
