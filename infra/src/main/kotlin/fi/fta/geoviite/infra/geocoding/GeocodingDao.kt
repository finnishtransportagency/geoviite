package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutRowVersionArray
import fi.fta.geoviite.infra.util.getLayoutRowVersionOrNull
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.queryNotNull
import fi.fta.geoviite.infra.util.queryOptional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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

    fun listLayoutGeocodingContextCacheKeys(layoutContext: LayoutContext): List<LayoutGeocodingContextCacheKey> =
        getLayoutGeocodingContextCacheKeysInternal(layoutContext, null)

    fun getLayoutGeocodingContextCacheKey(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): LayoutGeocodingContextCacheKey? =
        getOptional(trackNumberId, getLayoutGeocodingContextCacheKeysInternal(layoutContext, trackNumberId))

    private fun getLayoutGeocodingContextCacheKeysInternal(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>?,
    ): List<LayoutGeocodingContextCacheKey> {
        // language=SQL
        val sql =
            """
            select
              tn.id as tn_id,
              tn.design_id as tn_design_id,
              tn.draft as tn_draft,
              tn.version as tn_version,
              rl.id as rl_id,
              rl.design_id as rl_design_id,
              rl.draft as rl_draft,
              rl.version as rl_version,
              kmp_ids,
              kmp_design_ids,
              kmp_drafts,
              kmp_versions
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id) tn
              left join
                layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id)
                  rl on rl.track_number_id = tn.id
              left join lateral (
                select
                  coalesce(array_agg(kmp.id order by id), '{}') as kmp_ids,
                  coalesce(array_agg(kmp.design_id order by id), '{}') as kmp_design_ids,
                  coalesce(array_agg(kmp.draft order by id), '{}') as kmp_drafts,
                  coalesce(array_agg(kmp.version order by id), '{}') as kmp_versions
                  from layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id) kmp
                  where kmp.track_number_id = tn.id and kmp.state = 'IN_USE'
              ) kmp on (true)
            where ((:tn_id::int is null and tn.state != 'DELETED') or :tn_id = tn.id)
        """
                .trimIndent()
        val params =
            mapOf(
                "tn_id" to trackNumberId?.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate.queryNotNull(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    fun getLayoutGeocodingContextCacheKey(
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContextCacheKey? {
        // language=SQL
        val sql =
            """
            with
              tn_versions as (
                select distinct on (id, is_design)
                  id, design_id, version, deleted, case when design_id is not null then 0 else 1 end as is_design
                from layout.track_number_version
                where id = :tn_id
                  and draft = false
                  and (design_id is null or design_id = :design_id)
                  and change_time <= :moment
                order by id, is_design, version desc
              ),
              tn as (
                select id, design_id, version
                from tn_versions
                where deleted = false
                order by is_design
                limit 1
              ),
              rl_versions as (
                select distinct on (id, is_design)
                  id, design_id, version, deleted, case when design_id is not null then 0 else 1 end as is_design
                from layout.reference_line_version
                where track_number_id = :tn_id
                  and draft = false
                  and (design_id is null or design_id = :design_id)
                  and change_time <= :moment
                order by id, is_design, version desc
              ),
              rl as (
                select id, design_id, version
                from rl_versions
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
              rl.id as rl_id,
              rl.design_id as rl_design_id,
              false as rl_draft,
              rl.version as rl_version,
              kmp_ids,
              kmp_design_ids,
              kmp_drafts,
              kmp_versions
            from tn
              left join rl on true
              left join lateral (
                select
                  coalesce(array_agg(kmp.id order by kmp.id, kmp.version)
                           filter (where kmp.id is not null and kmp.state = 'IN_USE'), '{}') as kmp_ids,
                  coalesce(array_agg(kmp.design_id order by kmp.id, kmp.version)
                           filter (where kmp.id is not null and kmp.state = 'IN_USE'), '{}') as kmp_design_ids,
                  coalesce(array_agg(kmp.draft order by kmp.id, kmp.version)
                           filter (where kmp.id is not null and kmp.state = 'IN_USE'), '{}') as kmp_drafts,
                  coalesce(array_agg(kmp.version order by kmp.id, kmp.version)
                           filter (where kmp.id is not null and kmp.state = 'IN_USE'), '{}') as kmp_versions
                from kmp
              )
               kmp on true
        """
                .trimIndent()
        val params =
            mapOf(
                "tn_id" to trackNumberId.intValue,
                "moment" to Timestamp.from(moment),
                "design_id" to branch.designId?.intValue,
            )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    private fun toGeocodingContextCacheKey(rs: ResultSet): LayoutGeocodingContextCacheKey? {
        val tnVersion =
            rs.getLayoutRowVersionOrNull<TrackLayoutTrackNumber>("tn_id", "tn_design_id", "tn_draft", "tn_version")
        val rlVersion = rs.getLayoutRowVersionOrNull<ReferenceLine>("rl_id", "rl_design_id", "rl_draft", "rl_version")
        return if (tnVersion == null || rlVersion == null) {
            null
        } else
            LayoutGeocodingContextCacheKey(
                trackNumberId = rs.getIntId("tn_id"),
                trackNumberVersion = tnVersion,
                referenceLineVersion = rlVersion,
                kmPostVersions = rs.getLayoutRowVersionArray("kmp_ids", "kmp_design_ids", "kmp_drafts", "kmp_versions"),
            )
    }

    fun getLayoutGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): GeocodingContextCacheKey? {
        val base = getLayoutGeocodingContextCacheKey(versions.target.baseContext, trackNumberId)
        val trackNumberVersion = versions.findTrackNumber(trackNumberId) ?: base?.trackNumberVersion
        // We have to fetch the actual objects (reference line & km-post) here to check references
        // However, when this is done, the objects are needed elsewhere as well -> they should
        // always be in cache
        val referenceLineVersion =
            versions.referenceLines.find { v -> referenceLineDao.fetch(v).trackNumberId == trackNumberId }
                ?: base?.referenceLineVersion
        return if (trackNumberVersion != null && referenceLineVersion != null) {
            val validationKmPostsParticipatingInGeocoding =
                versions.kmPosts
                    .map { version -> version to kmPostDao.fetch(version) }
                    .filter { it.second.trackNumberId == trackNumberId }
            val participatingValidationKmPostIds = validationKmPostsParticipatingInGeocoding.map { it.first.id }.toSet()
            val otherKmPostsOnTrack =
                base?.kmPostVersions?.filter { v -> !participatingValidationKmPostIds.contains(v.id) } ?: listOf()

            val kmPostVersions =
                listOf(
                        validationKmPostsParticipatingInGeocoding
                            .filter { it.second.state == LayoutState.IN_USE }
                            .map { it.first },
                        otherKmPostsOnTrack,
                    )
                    .flatten()
                    .sortedBy { p -> p.id.intValue }
            LayoutGeocodingContextCacheKey(trackNumberId, trackNumberVersion, referenceLineVersion, kmPostVersions)
        } else null
    }
}
