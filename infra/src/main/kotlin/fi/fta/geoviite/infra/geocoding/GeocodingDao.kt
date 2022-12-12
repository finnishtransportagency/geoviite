package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.queryOptional
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface GeocodingContextCacheKey {
    val trackNumberId: IntId<TrackLayoutTrackNumber>;
    val changeTime: Instant;
    val publishType: PublishType

    fun fetchVersions(kmPostDao: LayoutKmPostDao): List<RowVersion<TrackLayoutKmPost>>
}

data class OrdinaryGeocodingContextCacheKey(
    override val publishType: PublishType,
    override val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val changeTime: Instant,
) : GeocodingContextCacheKey {
    override fun fetchVersions(kmPostDao: LayoutKmPostDao): List<RowVersion<TrackLayoutKmPost>> =
        kmPostDao.fetchVersions(publishType, false, trackNumberId, null)
}

data class PublicationGeocodingContextCacheKey(
    override val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val changeTime: Instant,
    val kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>,
) : GeocodingContextCacheKey {
    override val publishType = PublishType.DRAFT
    override fun fetchVersions(kmPostDao: LayoutKmPostDao): List<RowVersion<TrackLayoutKmPost>> =
        kmPostDao.fetchVersionsForPublication(trackNumberId, kmPostIdsToPublish)
}

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
        kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>? = null,
    ): GeocodingContextCacheKey? {
        assert(!(publishType == PublishType.OFFICIAL && kmPostIdsToPublish != null)) {
            "Trying to get geocoding context cache key for in OFFICIAL mode but intending to publish km post ids"
        }
        // if the track has been saved at all, it will have a change time, and if not, it can't have any km posts
        // referencing it -> early exit is correct
        val maxChangeTimeOnTrack = getTrackNumberOrReferenceLineChangeTime(publishType, trackNumberId)
            ?: return null
        return if (kmPostIdsToPublish != null) {
            publicationGeocodingContextCacheKey(trackNumberId, kmPostIdsToPublish, maxChangeTimeOnTrack)
        } else {
            ordinaryGeocodingContextCacheKey(trackNumberId, publishType, maxChangeTimeOnTrack)
        }
    }

    private fun ordinaryGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publishType: PublishType,
        maxChangeTimeOnTrack: Instant
    ): OrdinaryGeocodingContextCacheKey {
        val sql = """
            select max(km_post.change_time) as change_time
            from layout.km_post_version km_post
            where km_post.track_number_id = :track_number_id
              and ((km_post.draft and :publication_type = 'DRAFT')
                    or (not km_post.draft and :publication_type = 'OFFICIAL'))
            """.trimIndent()
        val maxChangeTimeOnKmPosts = jdbcTemplate.queryOptional(
            sql, mapOf(
                "track_number_id" to trackNumberId.intValue,
                "publication_type" to publishType.name
            )
        ) { rs, _ ->
            rs.getInstantOrNull("change_time")
        }
        return OrdinaryGeocodingContextCacheKey(
            publishType,
            trackNumberId,
            maxOfOneNotNull(maxChangeTimeOnTrack, maxChangeTimeOnKmPosts)
        )
    }

    private fun publicationGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>,
        maxChangeTimeOnTrackOrReferenceLine: Instant
    ): PublicationGeocodingContextCacheKey {
        val sql = """
            select max(km_post.change_time) as change_time
            from layout.km_post_version km_post
            where km_post.track_number_id = :track_number_id
              and ((km_post.draft and coalesce(km_post.draft_of_km_post_id, km_post.id) in (:km_post_ids_to_publish))
                   or (not km_post.draft and (km_post.id in (:km_post_ids_to_publish)) is distinct from true))
            """.trimIndent()
        val maxChangeTimeOnKmPosts = jdbcTemplate.queryOptional(sql, mapOf(
            "track_number_id" to trackNumberId.intValue,
            // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is distinct from true" checks
            // explicitly for false or null, since "foo in (null)" in SQL is null
            "km_post_ids_to_publish" to (kmPostIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) })
        )
        ) { rs, _ ->
            rs.getInstantOrNull("change_time")
        }
        return PublicationGeocodingContextCacheKey(
            trackNumberId,
            maxOfOneNotNull(maxChangeTimeOnTrackOrReferenceLine, maxChangeTimeOnKmPosts),
            kmPostIdsToPublish
        )
    }

    private fun <T: Comparable<T>> maxOfOneNotNull(a: T, b: T?): T =
        b?.let { listOf(a, b).max() } ?: a

    fun getTrackNumberOrReferenceLineChangeTime(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>
    ): Instant? {
        val sql = """
            select greatest(
              (select max(track_number.change_time)
                 from layout.track_number_publication_view track_number
                 where track_number.official_id = :track_number_id
                   and :publication_state = any(track_number.publication_states)
              ),
              (select max(reference_line.change_time)
                 from layout.reference_line_publication_view reference_line
                 where reference_line.track_number_id = :track_number_id
                   and :publication_state = any(reference_line.publication_states)
              )
            ) as max_change_time
              """
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "publication_state" to publishType.name,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> rs.getInstantOrNull("max_change_time") }
    }

    @Cacheable(CACHE_GEOCODING_CONTEXTS, sync = true)
    fun getGeocodingContext(key: GeocodingContextCacheKey): GeocodingContext? {
        logger.daoAccess(AccessType.FETCH, GeocodingContext::class, "cacheKey" to key)
        return referenceLineDao.fetchVersion(key.publishType, key.trackNumberId)?.let { referenceLineVersion ->
            val trackNumber = trackNumberDao.fetch(trackNumberDao.fetchVersionOrThrow(key.trackNumberId, key.publishType))
            val referenceLine = referenceLineDao.fetch(referenceLineVersion)
            val alignment = referenceLine.alignmentVersion?.let(alignmentDao::fetch)
                ?: throw IllegalStateException("DB ReferenceLine should have an alignment")
            val kmPosts = key.fetchVersions(kmPostDao).map(kmPostDao::fetch)
            if (alignment.segments.isEmpty()) { // If reference line has no geometry, we cannot geocode.
                null
            } else {
                GeocodingContext.create(trackNumber, referenceLine, alignment, kmPosts)
            }
        }
    }
}
