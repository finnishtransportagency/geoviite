package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationTarget
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val KM_POST_CACHE_SIZE = 10000L

@Transactional(readOnly = true)
@Component
class LayoutKmPostDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutKmPost, Unit>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_KM_POST,
        cacheEnabled,
        KM_POST_CACHE_SIZE,
    ) {

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null, null)

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
                "polygon_wkt" to bbox?.let { b -> create2DPolygonString(b.polygonFromCorners) },
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
              and km_post.state != 'DELETED'
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

    override fun fetchInternal(version: LayoutRowVersion<LayoutKmPost>): LayoutKmPost {
        val sql =
            """
            select 
              id,
              version,
              design_id,
              draft,
              cancelled,
              track_number_id,
              geometry_km_post_id,
              km_number,
              postgis.st_x(layout_location) as layout_point_x, postgis.st_y(layout_location) as layout_point_y,
              postgis.st_x(gk_location) as gk_point_x, postgis.st_y(gk_location) as gk_point_y,
              postgis.st_srid(gk_location) as gk_srid,
              gk_location_source,
              gk_location_confirmed,
              state,
              origin_design_id,
              exists(select * from layout.km_post official_kp
                     where kpv.id = official_kp.id
                       and (official_kp.design_id is null or official_kp.design_id = kpv.design_id)
                       and not official_kp.draft) as has_official
            from layout.km_post_version kpv
            where id = :id 
              and version = :version
              and layout_context_id = :layout_context_id
              and deleted = false
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to version.id.intValue,
                "version" to version.version,
                "layout_context_id" to version.context.toSqlString(),
            )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutKmPost(rs) }).also {
            logger.daoAccess(AccessType.FETCH, LayoutKmPost::class, version)
        }
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
              kp.cancelled,
              kp.track_number_id,
              kp.geometry_km_post_id,
              kp.km_number,
              postgis.st_x(kp.layout_location) as layout_point_x, postgis.st_y(kp.layout_location) as layout_point_y,
              postgis.st_x(kp.gk_location) as gk_point_x, postgis.st_y(kp.gk_location) as gk_point_y,
              postgis.st_srid(kp.gk_location) as gk_srid,
              kp.gk_location_source,
              kp.gk_location_confirmed,
              kp.state,
              exists(select * from layout.km_post official_kp
                     where kp.id = official_kp.id
                       and (official_kp.design_id is null or official_kp.design_id = kp.design_id)
                       and not official_kp.draft) as has_official,
              kp.origin_design_id
            from layout.km_post kp
        """
                .trimIndent()

        val posts = jdbcTemplate.query(sql) { rs, _ -> getLayoutKmPost(rs) }.associateBy(LayoutKmPost::version)
        logger.daoAccess(AccessType.FETCH, LayoutKmPost::class, posts.keys)
        cache.putAll(posts)
        return posts.size
    }

    private fun getLayoutKmPost(rs: ResultSet): LayoutKmPost {
        return LayoutKmPost(
            trackNumberId = rs.getIntId("track_number_id"),
            kmNumber = rs.getKmNumber("km_number"),
            gkLocation = getKmPostGkLocation(rs),
            state = rs.getEnum("state"),
            sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
            contextData =
                rs.getLayoutContextData(
                    "id",
                    "design_id",
                    "draft",
                    "version",
                    "cancelled",
                    "has_official",
                    "origin_design_id",
                ),
        )
    }

    @Transactional fun save(item: LayoutKmPost): LayoutRowVersion<LayoutKmPost> = save(item, Unit)

    @Transactional
    override fun save(item: LayoutKmPost, params: Unit): LayoutRowVersion<LayoutKmPost> {
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
              cancelled,
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
              :cancelled,
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
                  cancelled = excluded.cancelled,
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
                "cancelled" to item.isCancelled,
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

    fun fetchOnlyDraftVersions(
        branch: LayoutBranch,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
    ): List<LayoutRowVersion<LayoutKmPost>> {
        val sql =
            """
            select id, design_id, draft, version
            from layout.km_post
            where draft
              and (:include_deleted or state != 'DELETED')
              and (:trackNumberId::int is null or track_number_id = :trackNumberId)
              and design_id is not distinct from :design_id
        """
                .trimIndent()
        return jdbcTemplate
            .query(
                sql,
                mapOf(
                    "include_deleted" to includeDeleted,
                    "trackNumberId" to trackNumberId?.intValue,
                    "design_id" to branch.designId?.intValue,
                ),
            ) { rs, _ ->
                rs.getLayoutRowVersion<LayoutKmPost>("id", "design_id", "draft", "version")
            }
            .also { ids -> logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids) }
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
