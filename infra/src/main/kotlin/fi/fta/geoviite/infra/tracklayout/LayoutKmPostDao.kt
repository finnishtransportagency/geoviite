package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationTarget
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getGeometryPointOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getKmNumber
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import fi.fta.geoviite.infra.util.toDbId
import java.sql.ResultSet
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val KM_POST_CACHE_SIZE = 10000L

@Component
class LayoutKmPostDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutKmPost, NoParams>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_KM_POST,
        cacheEnabled,
        KM_POST_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutKmPost> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.km_post_external_id",
        "layout.km_post_external_id_version",
    ),
    IExternallyIdentifiedLayoutAssetDao<LayoutKmPost> {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LayoutKmPost>) = NoParams.instance

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null, null)

    @Transactional(readOnly = true)
    fun list(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<LayoutKmPost> = fetchVersions(layoutContext, includeDeleted, trackNumberId, bbox).map(::fetch)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<LayoutRowVersion<LayoutKmPost>> {
        val sql =
            """
                select id, design_id, draft, version
                from layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id) km_post
                where (:include_deleted = true or km_post.state != 'DELETED')
                  and (:track_number_id::int is null or track_number_id = :track_number_id)
                  and (:polygon_wkt::varchar is null or postgis.st_intersects(
                    km_post.layout_location,
                    postgis.st_polygonfromtext(:polygon_wkt::varchar, :map_srid)
                  ))
                order by km_post.track_number_id, km_post.km_number
            """
                .trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "track_number_id" to trackNumberId?.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "polygon_wkt" to bbox?.let(BoundingBox::polygonFromCorners)?.toWkt(),
                "map_srid" to LAYOUT_SRID.code,
            ),
        ) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    fun fetchVersionsForPublication(
        target: ValidationTarget,
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
        kmPostIdsToPublish: List<IntId<LayoutKmPost>>,
    ): Map<IntId<LayoutTrackNumber>, List<LayoutRowVersion<LayoutKmPost>>> {
        if (trackNumberIds.isEmpty()) return emptyMap()
        val sql =
            """
                select km_post.track_number_id, km_post.id, km_post.design_id, km_post.draft, km_post.version
                from (
                  select * from layout.km_post_in_layout_context(:candidate_state::layout.publication_state, :candidate_design_id)
                    where id in (:km_post_ids_to_publish)
                  union all
                  select * from layout.km_post_in_layout_context(:base_state::layout.publication_state, :base_design_id)
                    where (id in (:km_post_ids_to_publish)) is distinct from true
                  ) km_post
                where track_number_id in (:track_number_ids)
                order by km_post.track_number_id, km_post.km_number
            """
                .trimIndent()
        val params =
            mapOf(
                "track_number_ids" to trackNumberIds.map { id -> id.intValue },
                // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is
                // distinct from true" checks
                // explicitly for false or null, since "foo in (null)" in SQL is null
                "km_post_ids_to_publish" to (kmPostIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) }),
            ) + target.sqlParameters()
        val versions =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val trackNumberId = rs.getIntId<LayoutTrackNumber>("track_number_id")
                val version = rs.getLayoutRowVersion<LayoutKmPost>("id", "design_id", "draft", "version")
                trackNumberId to version
            }
        return trackNumberIds.associateWith { trackNumberId ->
            versions.filter { (tnId, _) -> tnId == trackNumberId }.map { (_, kmPostVersions) -> kmPostVersions }
        }
    }

    // TODO: GVT-3143 do we want to do modification API as well? If not, these can be removed
    fun getOfficialAtMoment(
        branch: LayoutBranch,
        moment: Instant,
        trackNumberId: IntId<LayoutTrackNumber>,
        kmNumber: KmNumber,
    ): LayoutKmPost? = getOfficialVersionsAtMoment(branch, trackNumberId, moment, kmNumber).firstOrNull()?.let(::fetch)

    fun listOfficialAtMoment(
        branch: LayoutBranch,
        moment: Instant,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
    ): List<LayoutKmPost> = getOfficialVersionsAtMoment(branch, trackNumberId, moment, kmNumber = null).let(::fetchMany)

    private fun getOfficialVersionsAtMoment(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>?,
        moment: Instant,
        kmNumber: KmNumber?,
    ): List<LayoutRowVersion<LayoutKmPost>> {
        val sql =
            """
            select
              id,
              layout_context_id,
              version
              from layout.km_post_at(:moment)
              where (:track_number_id::int is null or track_number_id = :track_number_id::int)
                and (:km_number::varchar is null or km_number = :km_number::varchar)
                and design_id is not distinct from :design_id
                and draft = false
            """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId?.intValue,
                "moment" to moment,
                "design_id" to branch.designId?.intValue,
                "km_number" to kmNumber?.toString(),
            )
        logger.daoAccess(
            AccessType.VERSION_FETCH,
            LayoutKmPost::class,
            trackNumberId ?: "all_track_numbers",
            kmNumber ?: "all_kms",
        )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getLayoutRowVersion("id", "layout_context_id", "version") }
    }

    fun fetchVersion(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): LayoutRowVersion<LayoutKmPost>? {
        val sql =
            """
                select id, design_id, draft, version
                from layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id) km_post
                where (:include_deleted or km_post.state != 'DELETED')
                  and km_post.track_number_id = :track_number_id
                  and km_post.km_number = :km_number
            """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId.intValue,
                "km_number" to kmNumber.toString(),
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        val result =
            jdbcTemplate.query(sql, params) { rs, _ ->
                rs.getLayoutRowVersion<LayoutKmPost>("id", "design_id", "draft", "version")
            }
        return result.firstOrNull()
    }

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<LayoutKmPost>>
    ): Map<LayoutRowVersion<LayoutKmPost>, LayoutKmPost> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
                select
                  kpv.id,
                  kpv.version,
                  kpv.design_id,
                  kpv.draft,
                  kpv.design_asset_state,
                  kpv.track_number_id,
                  kpv.geometry_km_post_id,
                  kpv.km_number,
                  postgis.st_x(kpv.layout_location) as layout_point_x, postgis.st_y(kpv.layout_location) as layout_point_y,
                  postgis.st_x(kpv.gk_location) as gk_point_x, postgis.st_y(kpv.gk_location) as gk_point_y,
                  postgis.st_srid(kpv.gk_location) as gk_srid,
                  kpv.gk_location_source,
                  kpv.gk_location_confirmed,
                  kpv.state,
                  kpv.origin_design_id
                from layout.km_post_version kpv
                  inner join lateral
                    (
                      select
                        unnest(:ids) id,
                        unnest(:layout_context_ids) layout_context_id,
                        unnest(:versions) version
                    ) args on args.id = kpv.id and args.layout_context_id = kpv.layout_context_id and args.version = kpv.version
                  where kpv.deleted = false
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getLayoutKmPost(rs) }
            .associateBy { kmp -> kmp.getVersionOrThrow() }
            .also { logger.daoAccess(FETCH, LayoutKmPost::class, versions) }
    }

    private fun getKmPostGkLocation(rs: ResultSet): LayoutKmPostGkLocation? {
        val location = rs.getGeometryPointOrNull("gk_point_x", "gk_point_y", "gk_srid")
        val locationSource = rs.getEnumOrNull<KmPostGkLocationSource>("gk_location_source")
        val locationConfirmed = rs.getBoolean("gk_location_confirmed")

        return if (location != null && locationSource != null)
            LayoutKmPostGkLocation(location, locationSource, locationConfirmed)
        else null
    }

    override fun preloadCache(): Int {
        val sql =
            """
                select
                  kp.id,
                  kp.design_id,
                  kp.draft,
                  kp.version,
                  kp.design_asset_state,
                  kp.track_number_id,
                  kp.geometry_km_post_id,
                  kp.km_number,
                  postgis.st_x(kp.layout_location) as layout_point_x, postgis.st_y(kp.layout_location) as layout_point_y,
                  postgis.st_x(kp.gk_location) as gk_point_x, postgis.st_y(kp.gk_location) as gk_point_y,
                  postgis.st_srid(kp.gk_location) as gk_srid,
                  kp.gk_location_source,
                  kp.gk_location_confirmed,
                  kp.state,
                  kp.origin_design_id
                from layout.km_post kp
            """
                .trimIndent()

        val kmPosts =
            jdbcTemplate
                .query(sql) { rs, _ -> getLayoutKmPost(rs) }
                .associateBy { kmPost -> requireNotNull(kmPost.version) }

        logger.daoAccess(FETCH, LayoutKmPost::class, kmPosts.keys)
        cache.putAll(kmPosts)

        return kmPosts.size
    }

    private fun getLayoutKmPost(rs: ResultSet): LayoutKmPost {
        return LayoutKmPost(
            trackNumberId = rs.getIntId("track_number_id"),
            kmNumber = rs.getKmNumber("km_number"),
            gkLocation = getKmPostGkLocation(rs),
            state = rs.getEnum("state"),
            sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )
    }

    @Transactional fun save(item: LayoutKmPost): LayoutRowVersion<LayoutKmPost> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: LayoutKmPost, params: NoParams): LayoutRowVersion<LayoutKmPost> {
        val id = item.id as? IntId ?: createId()
        val trackNumberId =
            toDbId(requireNotNull(item.trackNumberId) { "KM post not linked to TrackNumber: kmPost=$item" })
        val sql =
            """
                insert into layout.km_post(
                  layout_context_id,
                  id,
                  track_number_id,
                  geometry_km_post_id,
                  km_number,
                  layout_location,
                  gk_location,
                  gk_location_confirmed,
                  gk_location_source,
                  state,
                  draft,
                  design_asset_state,
                  design_id,
                  origin_design_id
                )
                values (
                  :layout_context_id,
                  :id,
                  :track_number_id,
                  :geometry_km_post_id,
                  :km_number,
                  postgis.st_point(:layout_x, :layout_y, :layout_srid),
                  postgis.st_point(:gk_x, :gk_y, :gk_srid),
                  :gk_location_confirmed,
                  :gk_location_source::layout.gk_location_source,
                  :state::layout.state,
                  :draft,
                  :design_asset_state::layout.design_asset_state,
                  :design_id,
                  :origin_design_id
                )
                on conflict (id, layout_context_id) do update
                  set track_number_id = excluded.track_number_id,
                      geometry_km_post_id = excluded.geometry_km_post_id,
                      km_number = excluded.km_number,
                      layout_location = excluded.layout_location,
                      gk_location = excluded.gk_location,
                      gk_location_confirmed = excluded.gk_location_confirmed,
                      gk_location_source = excluded.gk_location_source,
                      state = excluded.state,
                      design_asset_state = excluded.design_asset_state,
                      origin_design_id = excluded.origin_design_id
                returning version
            """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "track_number_id" to trackNumberId.intValue,
                "geometry_km_post_id" to item.sourceId?.let(::toDbId)?.intValue,
                "km_number" to item.kmNumber.toString(),
                "layout_x" to item.layoutLocation?.x,
                "layout_y" to item.layoutLocation?.y,
                "layout_srid" to LAYOUT_SRID.code,
                "gk_x" to item.gkLocation?.location?.x,
                "gk_y" to item.gkLocation?.location?.y,
                "gk_srid" to item.gkLocation?.location?.srid?.code,
                "gk_location_confirmed" to (item.gkLocation?.confirmed ?: false),
                "gk_location_source" to item.gkLocation?.source?.name,
                "state" to item.state.name,
                "draft" to item.contextData.isDraft,
                "design_asset_state" to item.designAssetState?.name,
                "design_id" to item.contextData.designId?.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
            )

        jdbcTemplate.setUser()
        val response: LayoutRowVersion<LayoutKmPost> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                LayoutRowVersion(id, item.layoutContext, rs.getInt("version"))
            } ?: throw IllegalStateException("Failed to save new km-post")
        logger.daoAccess(AccessType.INSERT, LayoutKmPost::class, response)
        return response
    }

    fun fetchNextWithLocationAfter(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        kmNumber: KmNumber,
        state: LayoutState,
    ): LayoutRowVersion<LayoutKmPost>? {
        val sql =
            """
                select id, design_id, draft, version
                from layout.km_post_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where track_number_id = :track_number_id
                  and state = :state::layout.state
                  and layout_location is not null
                  and km_number > :km_number
                order by km_number asc
                limit 1
            """
                .trimIndent()
        return jdbcTemplate.queryOptional(
            sql,
            mapOf(
                "track_number_id" to trackNumberId.intValue,
                "state" to state.name,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "km_number" to kmNumber.toString(),
            ),
        ) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }
}
