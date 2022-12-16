package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.linking.PublicationVersions
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.DaoBase
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class GeocodingContextCacheKey (
    val trackNumberVersion: RowVersion<TrackLayoutTrackNumber>,
    val referenceLineVersion: RowVersion<ReferenceLine>,
    val kmPostVersions: List<RowVersion<TrackLayoutKmPost>>,
 )

@Transactional(readOnly = true)
@Component
class GeocodingDao(
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val switchDao: LayoutSwitchDao,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    fun getGeocodingContextCacheKey(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContextCacheKey? {
        val trackNumberVersion = trackNumberDao.fetchVersion(trackNumberId, publishType)
        val referenceLineVersion = referenceLineDao.fetchVersion(publishType, trackNumberId)
        return if (trackNumberVersion != null && referenceLineVersion != null) {
            val kmPostVersions = kmPostDao.fetchVersions(publishType, false, trackNumberId)
                .sortedBy { p -> p.id.intValue }
            GeocodingContextCacheKey(trackNumberVersion, referenceLineVersion, kmPostVersions)
        } else null
    }


    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ): GeocodingContextCacheKey? {
        val trackNumberVersion = publicationVersions.findTrackNumber(trackNumberId)?.draftVersion
            ?: trackNumberDao.fetchVersion(trackNumberId, OFFICIAL)
        // We have to fetch the actual objects (reference line & km-post) here to check references
        // However, when this is done, the objects are needed elsewhere as well -> they should always be in cache
        val referenceLineVersion = publicationVersions.referenceLines
            .find { v -> referenceLineDao.fetch(v.draftVersion).trackNumberId == trackNumberId }?.draftVersion
            ?: referenceLineDao.fetchVersion(OFFICIAL, trackNumberId)
        return if (trackNumberVersion != null && referenceLineVersion != null) {
            val officialKmPosts = kmPostDao.fetchVersions(OFFICIAL, false, trackNumberId)
                .filter { version -> !publicationVersions.containsKmPost(version.id) }
            val draftKmPosts = publicationVersions.kmPosts.filter { draftPost ->
                kmPostDao.fetch(draftPost.draftVersion).trackNumberId == trackNumberId
            }.map { v -> v.draftVersion }
            val kmPostVersions = (officialKmPosts + draftKmPosts).sortedBy { p -> p.id.intValue }
            GeocodingContextCacheKey(trackNumberVersion, referenceLineVersion, kmPostVersions)
        } else null
    }

    @Cacheable(CACHE_GEOCODING_CONTEXTS, sync = true)
    fun getGeocodingContext(key: GeocodingContextCacheKey): GeocodingContext? {
        logger.daoAccess(AccessType.FETCH, GeocodingContext::class, "cacheKey" to key)
        val trackNumber = trackNumberDao.fetch(key.trackNumberVersion)
        val referenceLine = referenceLineDao.fetch(key.referenceLineVersion)
        val alignment = referenceLine.alignmentVersion?.let(alignmentDao::fetch)
            ?: throw IllegalStateException("DB ReferenceLine should have an alignment")
        // If the tracknumber is deleted or reference line has no geometry, we cannot geocode.
        if (!trackNumber.exists || alignment.segments.isEmpty()) return null
        val kmPosts = key.kmPostVersions.map(kmPostDao::fetch).filter { post -> post.exists }
        return GeocodingContext.create(trackNumber, referenceLine, alignment, kmPosts)
    }
}
