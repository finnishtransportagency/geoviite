package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publicationVersions

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingDaoIT @Autowired constructor(
    val geocodingDao: GeocodingDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
) : ITTestBase() {

    @Test
    fun trackNumberWithoutReferenceLineHasNoContext() {
        val id = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        assertNull(geocodingDao.getGeocodingContextCacheKey(DRAFT, id))
        assertNull(geocodingDao.getGeocodingContextCacheKey(OFFICIAL, id))
    }

    @Test
    fun trackNumberWithoutKmPostsHasAContext() {
        val id = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val alignmentVersion = alignmentDao.insert(alignment())
        referenceLineDao.insert(referenceLine(id).copy(alignmentVersion = alignmentVersion))
        assertNotNull(geocodingDao.getGeocodingContextCacheKey(DRAFT, id))
        assertNotNull(geocodingDao.getGeocodingContextCacheKey(OFFICIAL, id))
    }

    @Test
    fun cacheKeysAreCalculatedCorrectly() {
        val tnOfficialVersion = trackNumberDao.insert(trackNumber(getUnusedTrackNumber()))
        val tnId = tnOfficialVersion.id
        val tnDraftVersion = trackNumberDao.insert(draft(trackNumberDao.fetch(tnOfficialVersion)))

        val alignmentVersion = alignmentDao.insert(alignment())
        val rlOfficialVersion = referenceLineDao.insert(referenceLine(tnId).copy(alignmentVersion = alignmentVersion))
        val rlDraftVersion = referenceLineDao.insert(draft(referenceLineDao.fetch(rlOfficialVersion)))

        val kmPostOneOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(1)))
        val kmPostOneDraftVersion = kmPostDao.insert(draft(kmPostDao.fetch(kmPostOneOfficialVersion)))
        val kmPostTwoOnlyDraftVersion = kmPostService.saveDraft(kmPost(tnId, KmNumber(2)))
        val kmPostThreeOnlyOfficialVersion = kmPostDao.insert(kmPost(tnId, KmNumber(3)))

        val officialKey = geocodingDao.getGeocodingContextCacheKey(OFFICIAL, tnOfficialVersion.id)!!
        assertEquals(
            GeocodingContextCacheKey(
                trackNumberVersion = tnOfficialVersion,
                referenceLineVersion = rlOfficialVersion,
                kmPostVersions = listOf(kmPostOneOfficialVersion, kmPostThreeOnlyOfficialVersion)
            ),
            officialKey,
        )

        val draftKey = geocodingDao.getGeocodingContextCacheKey(DRAFT, tnOfficialVersion.id)!!
        assertEquals(
            GeocodingContextCacheKey(
                trackNumberVersion = tnDraftVersion,
                referenceLineVersion = rlDraftVersion,
                kmPostVersions = listOf(kmPostOneDraftVersion, kmPostTwoOnlyDraftVersion, kmPostThreeOnlyOfficialVersion)
            ),
            draftKey,
        )

        // Publishing nothing is the same as regular official
        assertEquals(
            officialKey,
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions()),
        )

        // Publishing everything is the same as regular draft
        assertEquals(
            draftKey,
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions(
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
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions(
                trackNumbers = listOf(tnId to tnDraftVersion),
            )),
        )
        assertEquals(
            officialKey.copy(referenceLineVersion = rlDraftVersion),
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions(
                referenceLines = listOf(rlOfficialVersion.id to rlDraftVersion),
            )),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPostOneDraftVersion, kmPostTwoOnlyDraftVersion, kmPostThreeOnlyOfficialVersion)),
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions(
                kmPosts = listOf(
                    kmPostOneOfficialVersion.id to kmPostOneDraftVersion,
                    kmPostTwoOnlyDraftVersion.id to kmPostTwoOnlyDraftVersion,
                ),
            )),
        )
        assertEquals(
            officialKey.copy(kmPostVersions = listOf(kmPostOneDraftVersion, kmPostThreeOnlyOfficialVersion)),
            geocodingDao.getGeocodingContextCacheKey(tnOfficialVersion.id, publicationVersions(
                kmPosts = listOf(kmPostOneOfficialVersion.id to kmPostOneDraftVersion),
            )),
        )
    }
}
