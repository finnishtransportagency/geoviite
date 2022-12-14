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
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

data class GeocodingContextCacheKey (
    val trackNumberVersion: RowVersion<TrackLayoutTrackNumber>,
    val referenceLineVersion: RowVersion<ReferenceLine>,
    val kmPostVersions: List<RowVersion<TrackLayoutKmPost>>,
) {
    init {
        kmPostVersions.forEachIndexed { index, version ->
            kmPostVersions.getOrNull(index+1)?.also { next ->
                require(next.id.intValue > version.id.intValue) {
                    "Cache key km-posts must be in order: " +
                            "index=$index " +
                            "trackNumberVersion=$trackNumberVersion " +
                            "kmPostVersion=$version " +
                            "nextKmPostVersion=$next"
                }
            }
        }
    }
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
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): GeocodingContextCacheKey? {
        //language=SQL
        val sql = """
            select
              tn.row_id tn_row_id,
              tn.row_version tn_row_version,
              rl.row_id rl_row_id,
              rl.row_version rl_row_version,
              array_agg(kmp.row_id order by kmp.row_id, kmp.row_version) 
                filter (where kmp.row_id is not null) kmp_row_ids,
              array_agg(kmp.row_version order by kmp.row_id, kmp.row_version) 
                filter (where kmp.row_id is not null) kmp_row_versions
            from layout.track_number_publication_view tn
              left join layout.reference_line_publication_view rl on rl.track_number_id = tn.official_id
                and :publication_state = any(rl.publication_states)
              left join layout.km_post_publication_view kmp on kmp.track_number_id = tn.official_id
                and :publication_state = any(kmp.publication_states)
                and kmp.state = 'IN_USE'
            where :publication_state = any(tn.publication_states)
              and :tn_id = tn.official_id
              and tn.state != 'DELETED'
            group by tn.row_id, tn.row_version, rl.row_id, rl.row_version
        """.trimIndent()
        val params = mapOf(
            "tn_id" to trackNumberId.intValue,
            "publication_state" to publicationState.name,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContextCacheKey? {
        //language=SQL
        val sql = """
            with 
              tn as (
                select id, version, state, deleted 
                from layout.track_number_version
                where id = :tn_id
                  and draft = false
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              rl as (
                select id, version, deleted
                from layout.reference_line_version
                where track_number_id = :tn_id
                  and draft = false
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              kmp as (
                select distinct on (id)
                  id, version, state, 
                  case when deleted or state != 'IN_USE' then true else false end as hide
                from layout.km_post_version
                where track_number_id = :tn_id
                  and draft = false
                  and change_time <= :moment
                order by id, version desc
              )
            select
              tn.id tn_row_id,
              tn.version tn_row_version,
              rl.id rl_row_id,
              rl.version rl_row_version,
              array_agg(kmp.id order by kmp.id, kmp.version) 
                filter (where kmp.id is not null and kmp.hide = false) kmp_row_ids,
              array_agg(kmp.version order by kmp.id, kmp.version) 
                filter (where kmp.id is not null and kmp.hide = false) kmp_row_versions
            from tn 
              left join rl on true 
              left join kmp on true
            where tn.deleted = false
              and tn.state != 'DELETED'
              and rl.deleted = false
            group by tn.id, tn.version, rl.id, rl.version
        """.trimIndent()
        val params = mapOf(
            "tn_id" to trackNumberId.intValue,
            "moment" to Timestamp.from(moment),
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    private fun toGeocodingContextCacheKey(rs: ResultSet): GeocodingContextCacheKey? {
        val tnVersion = rs.getRowVersionOrNull<TrackLayoutTrackNumber>("tn_row_id", "tn_row_version")
        val rlVersion = rs.getRowVersionOrNull<ReferenceLine>("rl_row_id", "rl_row_version")
        return if (tnVersion == null || rlVersion == null) {
            null
        } else GeocodingContextCacheKey(
            trackNumberVersion = tnVersion,
            referenceLineVersion = rlVersion,
            kmPostVersions = toRowVersions(
                ids = rs.getIntIdArray("kmp_row_ids"),
                versions = rs.getIntArrayOrNull("kmp_row_versions") ?: listOf(),
            ),
        )
    }

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: PublicationVersions,
    ): GeocodingContextCacheKey? {
        val official = getGeocodingContextCacheKey(OFFICIAL, trackNumberId)
        val trackNumberVersion = versions.findTrackNumber(trackNumberId)?.draftVersion ?: official?.trackNumberVersion
        // We have to fetch the actual objects (reference line & km-post) here to check references
        // However, when this is done, the objects are needed elsewhere as well -> they should always be in cache
        val referenceLineVersion = versions.referenceLines
            .find { v -> referenceLineDao.fetch(v.draftVersion).trackNumberId == trackNumberId }?.draftVersion
            ?: official?.referenceLineVersion
        return if (trackNumberVersion != null && referenceLineVersion != null) {
            val officialKmPosts = official?.kmPostVersions?.filter { v -> !versions.containsKmPost(v.id) } ?: listOf()
            val draftKmPosts = versions.kmPosts.filter { draftPost ->
                val draft = kmPostDao.fetch(draftPost.draftVersion)
                draft.trackNumberId == trackNumberId && draft.state == LayoutState.IN_USE
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
        val kmPosts = key.kmPostVersions.map(kmPostDao::fetch).sortedBy { post -> post.kmNumber }
        return GeocodingContext.create(trackNumber, referenceLine, alignment, kmPosts)
    }

    private fun <T> toRowVersions(ids: List<IntId<T>>, versions: List<Int>) =
        if (ids.size == versions.size) ids.mapIndexed { index, id -> RowVersion(id, versions[index]) }
        else throw IllegalStateException("Unmatched row-versions: ids=$ids versions=$versions")
}
