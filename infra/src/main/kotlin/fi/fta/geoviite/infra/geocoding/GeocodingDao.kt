package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
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
import fi.fta.geoviite.infra.util.getIntArrayOrNull
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getOptional
import fi.fta.geoviite.infra.util.getRowVersionOrNull
import fi.fta.geoviite.infra.util.queryNotNull
import fi.fta.geoviite.infra.util.queryOptional
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

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
        //language=SQL
        val sql = """
            select
              tn.row_id as tn_row_id,
              tn.row_version as tn_row_version,
              rl.row_id as rl_row_id,
              rl.row_version as rl_row_version,
              array_agg(kmp.row_id order by kmp.row_id, kmp.row_version) 
                filter (where kmp.row_id is not null) as kmp_row_ids,
              array_agg(kmp.row_version order by kmp.row_id, kmp.row_version) 
                filter (where kmp.row_id is not null) as kmp_row_versions
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id) tn
              left join
                layout.reference_line_in_layout_context(:publication_state::layout.publication_state, :design_id)
                  rl on rl.track_number_id = tn.official_id
              left join layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id)
                kmp on kmp.track_number_id = tn.official_id
                and kmp.state = 'IN_USE'
            where (:tn_id::int is null or :tn_id = tn.official_id)
            group by tn.row_id, tn.row_version, rl.row_id, rl.row_version
        """.trimIndent()
        val params = mapOf(
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
        //language=SQL
        val sql = """
            with 
              tn_main as (
                select id, version, deleted, design_id
                from layout.track_number_version
                where id = :tn_id
                  and draft = false
                  and design_id is null
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              tn_design as (
                select id, version, deleted, design_id
                from layout.track_number_version
                where coalesce(official_row_id, id) = :tn_id
                  and draft = false
                  and design_id = :design_id
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              tn as (
                select id, version
                from (select * from tn_main union all select * from tn_design) tmp
                where deleted = false
                order by case when design_id is not null then 0 else 1 end
                limit 1
              ),
              rl_main as (
                select id, version, deleted, design_id
                from layout.reference_line_version
                where track_number_id = :tn_id
                  and draft = false
                  and design_id is null
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              rl_design as (
                select id, version, deleted, design_id
                from layout.reference_line_version
                where track_number_id = :tn_id
                  and draft = false
                  and design_id = :design_id
                  and change_time <= :moment
                order by version desc
                limit 1
              ),
              rl as (
                select id, version, design_id
                from (select * from rl_main union all select * from rl_design) tmp
                where deleted = false
                order by case when design_id is not null then 0 else 1 end
                limit 1
              ),
              kmp_main as (
                select distinct on (id)
                  id, version, state, deleted, design_id, id as official_id
                from layout.km_post_version
                where track_number_id = :tn_id
                  and draft = false
                  and design_id is null
                  and change_time <= :moment
                order by id, version desc
              ),
              kmp_design as (
                select distinct on (id)
                  id, version, state, deleted, design_id, coalesce(official_row_id, id) as official_id
                from layout.km_post_version
                where track_number_id = :tn_id
                  and draft = false
                  and design_id = :design_id
                  and change_time <= :moment
                order by id, version desc
              ),
              kmp as (
                select distinct on (official_id) id, version, state
                from (select * from kmp_main union all select * from kmp_design) tmp
                where deleted = false
                order by official_id, case when design_id is not null then 0 else 1 end
              )
            select
              tn.id as tn_row_id,
              tn.version as tn_row_version,
              rl.id as rl_row_id,
              rl.version as rl_row_version,
              array_agg(kmp.id order by kmp.id, kmp.version) 
                filter (where kmp.id is not null and kmp.state = 'IN_USE') as kmp_row_ids,
              array_agg(kmp.version order by kmp.id, kmp.version) 
                filter (where kmp.id is not null and kmp.state = 'IN_USE') as kmp_row_versions
            from tn
              left join rl on true 
              left join kmp on true
            group by tn.id, tn.version, rl.id, rl.version
        """.trimIndent()
        val params = mapOf(
            "tn_id" to trackNumberId.intValue,
            "moment" to Timestamp.from(moment),
            "design_id" to branch.designId?.intValue,
        )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> toGeocodingContextCacheKey(rs) }
    }

    private fun toGeocodingContextCacheKey(rs: ResultSet): LayoutGeocodingContextCacheKey? {
        val tnVersion = rs.getRowVersionOrNull<TrackLayoutTrackNumber>("tn_row_id", "tn_row_version")
        val rlVersion = rs.getRowVersionOrNull<ReferenceLine>("rl_row_id", "rl_row_version")
        return if (tnVersion == null || rlVersion == null) {
            null
        } else LayoutGeocodingContextCacheKey(
            trackNumberVersion = tnVersion,
            referenceLineVersion = rlVersion,
            kmPostVersions = toRowVersions(
                ids = rs.getIntIdArray("kmp_row_ids"),
                versions = rs.getIntArrayOrNull("kmp_row_versions") ?: listOf(),
            ),
        )
    }

    fun getLayoutGeocodingContextCacheKey(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): GeocodingContextCacheKey? {
        val official = getLayoutGeocodingContextCacheKey(versions.branch.official, trackNumberId)
        val trackNumberVersion =
            versions.findTrackNumber(trackNumberId)?.validatedAssetVersion ?: official?.trackNumberVersion
        // We have to fetch the actual objects (reference line & km-post) here to check references
        // However, when this is done, the objects are needed elsewhere as well -> they should always be in cache
        val referenceLineVersion = versions.referenceLines
            .find { v -> referenceLineDao.fetch(v.validatedAssetVersion).trackNumberId == trackNumberId }
            ?.validatedAssetVersion
            ?: official?.referenceLineVersion
        return if (trackNumberVersion != null && referenceLineVersion != null) {
            val mainOrDesignOfficialIdsWithDraftKmPosts =
                versions.kmPosts.map { v -> kmPostDao.fetch(v.validatedAssetVersion) }.flatMap { draft ->
                    listOfNotNull(draft.contextData.designRowId, draft.contextData.officialRowId)
                }
            val officialKmPosts =
                official?.kmPostVersions?.filter { v -> !mainOrDesignOfficialIdsWithDraftKmPosts.contains(v.id) }
                    ?: listOf()
            val draftKmPosts = versions.kmPosts.filter { draftPost ->
                val draft = kmPostDao.fetch(draftPost.validatedAssetVersion)
                draft.trackNumberId == trackNumberId && draft.state == LayoutState.IN_USE
            }.map { v -> v.validatedAssetVersion }
            val kmPostVersions = (officialKmPosts + draftKmPosts).sortedBy { p -> p.id.intValue }
            LayoutGeocodingContextCacheKey(trackNumberVersion, referenceLineVersion, kmPostVersions)
        } else null
    }

    private fun <T> toRowVersions(ids: List<IntId<T>>, versions: List<Int>) = ids
        .also { check(it.size == versions.size) { "Unmatched row-versions: ids=$ids versions=$versions" } }
        .mapIndexed { index, id -> RowVersion(id, versions[index]) }
}
