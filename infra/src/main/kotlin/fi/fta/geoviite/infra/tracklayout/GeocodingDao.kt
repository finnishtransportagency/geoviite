package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.configuration.CACHE_GEOCODING_CONTEXTS
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.queryOptional
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class GeocodingContextCacheKey(
    val publishType: PublishType,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val changeTime: Instant,
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
        //language=SQL
        val sql = """
            select
              (select max(track_number.change_time)
                 from layout.track_number_publication_view track_number
                 where track_number.official_id = :track_number_id
                   and :publication_state = any(track_number.publication_states)
              ) as track_number_changed,
              (select max(reference_line.change_time)
                 from layout.reference_line_publication_view reference_line
                 where reference_line.track_number_id = :track_number_id
                   and :publication_state = any(reference_line.publication_states)
              ) as reference_line_changed,
              (select max(km_post.change_time)
                 from layout.km_post_version km_post
                 where km_post.track_number_id = :track_number_id
              ) as km_post_changed
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "publication_state" to publishType.name,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            val trackNumberChanged = rs.getInstantOrNull("track_number_changed")
            val referenceLineChanged = rs.getInstantOrNull("reference_line_changed")
            val kmPostsChanged = rs.getInstantOrNull("km_post_changed")
            listOfNotNull(trackNumberChanged, referenceLineChanged, kmPostsChanged).maxOrNull()
        }?.let { changeTime -> GeocodingContextCacheKey(publishType, trackNumberId, changeTime) }
    }

    @Cacheable(CACHE_GEOCODING_CONTEXTS, sync = true)
    fun getGeocodingContext(key: GeocodingContextCacheKey): GeocodingContext? {
        logger.daoAccess(AccessType.FETCH, GeocodingContext::class, "cacheKey" to key)
        return referenceLineDao.fetchVersion(key.publishType, key.trackNumberId)?.let { referenceLineVersion ->
            val trackNumber = trackNumberDao.fetch(trackNumberDao.fetchVersionOrThrow(key.trackNumberId, key.publishType))
            val referenceLine = referenceLineDao.fetch(referenceLineVersion)
            val alignment = referenceLine.alignmentVersion?.let(alignmentDao::fetch)
                ?: throw IllegalStateException("DB ReferenceLine should have an alignment")
            val kmPosts = kmPostDao.fetchVersions(key.publishType, key.trackNumberId).map(kmPostDao::fetch)
            if (alignment.segments.isEmpty()) { // If reference line has no geometry, we cannot geocode.
                null
            } else {
                GeocodingContext.create(trackNumber, referenceLine, alignment, kmPosts)
            }
        }
    }
}
