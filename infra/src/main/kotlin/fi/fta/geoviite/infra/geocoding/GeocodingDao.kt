package fi.fta.geoviite.infra.geocoding

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.configuration.ManualCacheStatsProvider
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getLayoutRowVersionArray
import fi.fta.geoviite.infra.util.getLayoutRowVersionOrNull
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.queryNotNull
import fi.fta.geoviite.infra.util.queryOptional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.jvm.optionals.getOrNull
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class GeocodingDao(
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val switchDao: LayoutSwitchDao,
    val alignmentDao: LayoutAlignmentDao,
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam), ManualCacheStatsProvider {

    private data class MomentCacheKey(
        val branch: LayoutBranch,
        val trackNumberId: IntId<LayoutTrackNumber>,
        val moment: Instant,
    )

    private val momentCacheKeyCache: Cache<MomentCacheKey, Optional<LayoutGeocodingContextCacheKey>> =
        Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(layoutCacheDuration).recordStats().build()

    override fun cacheStats() = mapOf("geocoding-moment-key" to momentCacheKeyCache.stats())

    fun listLayoutGeocodingContextCacheKeys(layoutContext: LayoutContext): List<LayoutGeocodingContextCacheKey> =
        getLayoutGeocodingContextCacheKeysInternal(layoutContext, null)

    fun getLayoutGeocodingContextCacheKey(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        includeDeleted: Boolean = false,
    ): LayoutGeocodingContextCacheKey? =
        getOptional(
            trackNumberId,
            getLayoutGeocodingContextCacheKeysInternal(layoutContext, trackNumberId, includeDeleted),
        )

    private fun getLayoutGeocodingContextCacheKeysInternal(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>?,
        includeDeleted: Boolean = false,
    ): List<LayoutGeocodingContextCacheKey> {
        // language=SQL
        val sql =
            """
            select
              tn.id as tn_id,
              tn.design_id as tn_design_id,
              tn.draft as tn_draft,
              tn.version as tn_version,
              kmp_ids,
              kmp_design_ids,
              kmp_drafts,
              kmp_versions
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id) tn
              left join lateral (
                select
                  coalesce(array_agg(kmp.id order by id), '{}') as kmp_ids,
                  coalesce(array_agg(kmp.design_id order by id), '{}') as kmp_design_ids,
                  coalesce(array_agg(kmp.draft order by id), '{}') as kmp_drafts,
                  coalesce(array_agg(kmp.version order by id), '{}') as kmp_versions
                  from layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id) kmp
                  where kmp.track_number_id = tn.id and kmp.state = 'IN_USE'
              ) kmp on (true)
            where (:tn_id::int is null or :tn_id = tn.id)
              and (:include_deleted or tn.state != 'DELETED')
            """
                .trimIndent()
        val params =
            mapOf(
                "tn_id" to trackNumberId?.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        return jdbcTemplate.queryNotNull(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    fun getLayoutGeocodingContextCacheKey(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): LayoutGeocodingContextCacheKey? =
        momentCacheKeyCache
            .get(MomentCacheKey(branch, trackNumberId, moment)) {
                // language=SQL
                val sql =
                    """
                    with
                      tn_versions as (
                        select distinct on (id, is_design)
                          id, design_id, version, state, deleted, case when design_id is not null then 0 else 1 end as is_design
                        from layout.track_number_version
                        where id = :tn_id
                          and draft = false
                          and (design_id is null or design_id = :design_id)
                          and change_time <= :moment
                        order by id, is_design, version desc
                      ),
                      tn as (
                        select id, design_id, version, state
                        from tn_versions
                        where deleted = false
                        order by is_design
                        limit 1
                      ),
                      kmp_versions as (
                        select distinct on (id, is_design)
                          id, design_id, version, state, deleted, case when design_id is not null then 0 else 1 end as is_design
                        from layout.km_post_version
                        where track_number_id = :tn_id
                          and draft = false
                          and (design_id is null or design_id = :design_id)
                          and change_time <= :moment
                        order by id, is_design, version desc
                      ),
                      kmp as (
                        select distinct on (id) id, design_id, false as draft, version, state
                        from kmp_versions
                        where deleted = false
                        order by id, is_design
                      )
                    select
                      tn.id as tn_id,
                      tn.design_id as tn_design_id,
                      false as tn_draft,
                      tn.version as tn_version,
                      kmp_ids,
                      kmp_design_ids,
                      kmp_drafts,
                      kmp_versions
                    from tn
                      left join lateral (
                        select
                          coalesce(
                            array_agg(kmp.id order by kmp.id, kmp.version)
                              filter (where kmp.id is not null and kmp.state = 'IN_USE'),
                            '{}'
                          ) as kmp_ids,
                          coalesce(
                            array_agg(kmp.design_id order by kmp.id, kmp.version)
                              filter (where kmp.id is not null and kmp.state = 'IN_USE'),
                            '{}'
                          ) as kmp_design_ids,
                          coalesce(
                            array_agg(kmp.draft order by kmp.id, kmp.version)
                              filter (where kmp.id is not null and kmp.state = 'IN_USE'),
                            '{}'
                          ) as kmp_drafts,
                          coalesce(
                            array_agg(kmp.version order by kmp.id, kmp.version)
                              filter (where kmp.id is not null and kmp.state = 'IN_USE'),
                            '{}'
                          ) as kmp_versions
                        from kmp
                      )
                       kmp on true
                       where tn.state != 'DELETED'
                    """
                        .trimIndent()
                val params =
                    mapOf(
                        "tn_id" to trackNumberId.intValue,
                        "moment" to Timestamp.from(moment),
                        "design_id" to branch.designId?.intValue,
                    )
                Optional.ofNullable(jdbcTemplate.queryOptional(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) })
            }
            .getOrNull()

    private fun toGeocodingContextCacheKey(rs: ResultSet): LayoutGeocodingContextCacheKey? =
        rs.getLayoutRowVersionOrNull<LayoutTrackNumber>("tn_id", "tn_design_id", "tn_draft", "tn_version")?.let {
            tnVersion ->
            LayoutGeocodingContextCacheKey(
                trackNumberVersion = tnVersion,
                kmPostVersions = rs.getLayoutRowVersionArray("kmp_ids", "kmp_design_ids", "kmp_drafts", "kmp_versions"),
            )
        }

    fun getLayoutGeocodingContextCacheKey(
        trackNumberId: IntId<LayoutTrackNumber>,
        versions: ValidationVersions,
    ): LayoutGeocodingContextCacheKey? {
        // Normally, if a TrackNumber is deleted, it is not considered to have a geocoding context, but we still need
        // the base-version if the validation versions overrides it with a non-deleted one
        val base = getLayoutGeocodingContextCacheKey(versions.target.baseContext, trackNumberId, includeDeleted = true)

        // Since we included deleted versions, we now need to verify the existence for the final TrackNumber
        // as DELETED TrackNumbers don't have geocoding contexts
        return (versions.findTrackNumber(trackNumberId) ?: base?.trackNumberVersion)
            ?.takeIf { tnVersion -> trackNumberDao.fetch(tnVersion).exists }
            ?.let { tnVersion ->
                val validationKmPostsParticipatingInGeocoding =
                    kmPostDao.fetchManyByVersion(versions.kmPosts).filter { it.value.trackNumberId == trackNumberId }
                val participatingValidationKmPostIds =
                    validationKmPostsParticipatingInGeocoding.map { it.key.id }.toSet()
                val nonOverriddenBaseKmPosts =
                    base?.kmPostVersions?.filter { v -> !participatingValidationKmPostIds.contains(v.id) } ?: listOf()
                val kmPostVersions =
                    listOf(
                            validationKmPostsParticipatingInGeocoding
                                .filter { it.value.state == LayoutState.IN_USE }
                                .map { it.key },
                            nonOverriddenBaseKmPosts,
                        )
                        .flatten()
                        .sortedBy { p -> p.id.intValue }

                return LayoutGeocodingContextCacheKey(tnVersion, kmPostVersions)
            }
    }
}
