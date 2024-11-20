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
import fi.fta.geoviite.infra.util.getDaoResponse
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val KM_POST_CACHE_SIZE = 10000L

@Transactional(readOnly = true)
@Component
class LayoutKmPostDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<TrackLayoutKmPost>(
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
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<TrackLayoutKmPost> =
        fetchVersions(layoutContext, includeDeleted, trackNumberId, bbox).map { r -> fetch(r.rowVersion) }

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<LayoutDaoResponse<TrackLayoutKmPost>> {
        val sql =
            """
            select km_post.official_id, km_post.row_id, km_post.row_version 
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
            rs.getDaoResponse("official_id", "row_id", "row_version")
        }
    }

    fun fetchVersionsForPublication(
        target: ValidationTarget,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<LayoutDaoResponse<TrackLayoutKmPost>>> {
        if (trackNumberIds.isEmpty()) return emptyMap()
        val sql =
            """
            select km_post.track_number_id, km_post.official_id, km_post.row_id, km_post.row_version
            from (
              select * from layout.km_post_in_layout_context(:candidate_state::layout.publication_state, :candidate_design_id)
                where official_id in (:km_post_ids_to_publish)
              union all
              select * from layout.km_post_in_layout_context(:base_state::layout.publication_state, :base_design_id)
                where (official_id in (:km_post_ids_to_publish)) is distinct from true
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
                val trackNumberId = rs.getIntId<TrackLayoutTrackNumber>("track_number_id")
                val version = rs.getDaoResponse<TrackLayoutKmPost>("official_id", "row_id", "row_version")
                trackNumberId to version
            }
        return trackNumberIds.associateWith { trackNumberId ->
            versions.filter { (tnId, _) -> tnId == trackNumberId }.map { (_, kmPostVersions) -> kmPostVersions }
        }
    }

    fun fetchVersion(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): LayoutRowVersion<TrackLayoutKmPost>? {
        val sql =
            """
            select km_post.row_id, km_post.row_version 
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
                rs.getLayoutRowVersion<TrackLayoutKmPost>("row_id", "row_version")
            }
        return result.firstOrNull()
    }

    override fun fetchInternal(version: LayoutRowVersion<TrackLayoutKmPost>): TrackLayoutKmPost {
        val sql =
            """
            select 
              id as row_id,
              version as row_version,
              official_row_id,
              design_row_id,
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
              state
            from layout.km_post_version
            where id = :id 
              and version = :version
              and deleted = false
        """
                .trimIndent()
        val params = mapOf("id" to version.rowId.intValue, "version" to version.version)
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutKmPost(rs) }).also {
            logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, version)
        }
    }

    private fun getKmPostGkLocation(rs: ResultSet): TrackLayoutKmPostGkLocation? {
        val location = rs.getGeometryPointOrNull("gk_point_x", "gk_point_y", "gk_srid")
        val locationSource = rs.getEnumOrNull<KmPostGkLocationSource>("gk_location_source")
        val locationConfirmed = rs.getBoolean("gk_location_confirmed")

        return if (location != null && locationSource != null)
            TrackLayoutKmPostGkLocation(location, locationSource, locationConfirmed)
        else null
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select 
              id as row_id,
              version as row_version,
              official_row_id, 
              design_row_id,
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
              state
            from layout.km_post
        """
                .trimIndent()

        val posts = jdbcTemplate.query(sql) { rs, _ -> getLayoutKmPost(rs) }.associateBy(TrackLayoutKmPost::version)
        logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, posts.keys)
        cache.putAll(posts)
        return posts.size
    }

    private fun getLayoutKmPost(rs: ResultSet): TrackLayoutKmPost {

        return TrackLayoutKmPost(
            trackNumberId = rs.getIntId("track_number_id"),
            kmNumber = rs.getKmNumber("km_number"),
            gkLocation = getKmPostGkLocation(rs),
            state = rs.getEnum("state"),
            sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
            contextData =
                rs.getLayoutContextData(
                    "official_row_id",
                    "design_row_id",
                    "design_id",
                    "row_id",
                    "row_version",
                    "draft",
                    "cancelled",
                ),
        )
    }

    @Transactional
    override fun insert(newItem: TrackLayoutKmPost): LayoutDaoResponse<TrackLayoutKmPost> {
        val trackNumberId =
            toDbId(requireNotNull(newItem.trackNumberId) { "KM post not linked to TrackNumber: kmPost=$newItem" })
        val sql =
            """
            insert into layout.km_post(
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
              official_row_id,
              design_row_id,
              design_id
            )
            values (
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
              :official_row_id,
              :design_row_id,
              :design_id
            )
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId.intValue,
                "geometry_km_post_id" to newItem.sourceId?.let(::toDbId)?.intValue,
                "km_number" to newItem.kmNumber.toString(),
                "layout_x" to newItem.layoutLocation?.x,
                "layout_y" to newItem.layoutLocation?.y,
                "layout_srid" to LAYOUT_SRID.code,
                "gk_x" to newItem.gkLocation?.location?.x,
                "gk_y" to newItem.gkLocation?.location?.y,
                "gk_srid" to newItem.gkLocation?.location?.srid?.code,
                "gk_location_confirmed" to (newItem.gkLocation?.confirmed ?: false),
                "gk_location_source" to newItem.gkLocation?.source?.name,
                "state" to newItem.state.name,
                "draft" to newItem.contextData.isDraft,
                "cancelled" to newItem.isCancelled,
                "official_row_id" to newItem.contextData.officialRowId?.intValue,
                "design_row_id" to newItem.contextData.designRowId?.intValue,
                "design_id" to newItem.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<TrackLayoutKmPost> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: throw IllegalStateException("Failed to generate ID for new km-post")
        logger.daoAccess(AccessType.INSERT, TrackLayoutKmPost::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutKmPost): LayoutDaoResponse<TrackLayoutKmPost> {
        val trackNumberId =
            toDbId(
                requireNotNull(updatedItem.trackNumberId) { "KM post not linked to TrackNumber: kmPost=$updatedItem" }
            )
        val rowId =
            requireNotNull(updatedItem.contextData.rowId) {
                "Cannot update a row that doesn't have a DB ID: kmPost=$updatedItem"
            }
        val sql =
            """
            update layout.km_post 
            set
              track_number_id = :track_number_id, 
              geometry_km_post_id = :geometry_km_post_id, 
              km_number = :km_number, 
              layout_location = postgis.st_point(:layout_x, :layout_y, :layout_srid),
              gk_location = postgis.st_point(:gk_x, :gk_y, :gk_srid),
              gk_location_confirmed = :gk_location_confirmed,
              gk_location_source = :gk_location_source::layout.gk_location_source,
              state = :state::layout.state,
              draft = :draft,
              cancelled = :cancelled,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id
            where id = :km_post_id
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        val params =
            mapOf(
                "km_post_id" to rowId.intValue,
                "track_number_id" to trackNumberId.intValue,
                "geometry_km_post_id" to updatedItem.sourceId?.let(::toDbId)?.intValue,
                "km_number" to updatedItem.kmNumber.toString(),
                "layout_x" to updatedItem.layoutLocation?.x,
                "layout_y" to updatedItem.layoutLocation?.y,
                "layout_srid" to LAYOUT_SRID.code,
                "gk_x" to updatedItem.gkLocation?.location?.x,
                "gk_y" to updatedItem.gkLocation?.location?.y,
                "gk_srid" to updatedItem.gkLocation?.location?.srid?.code,
                "gk_location_confirmed" to (updatedItem.gkLocation?.confirmed ?: false),
                "gk_location_source" to updatedItem.gkLocation?.source?.name,
                "state" to updatedItem.state.name,
                "draft" to updatedItem.isDraft,
                "cancelled" to updatedItem.isCancelled,
                "official_row_id" to updatedItem.contextData.officialRowId?.intValue,
                "design_row_id" to updatedItem.contextData.designRowId?.intValue,
                "design_id" to updatedItem.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<TrackLayoutKmPost> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: throw IllegalStateException("Failed to generate ID for new row version of updated km-post")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutKmPost::class, response)
        return response
    }

    fun fetchOnlyDraftVersions(
        branch: LayoutBranch,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
    ): List<LayoutRowVersion<TrackLayoutKmPost>> {
        val sql =
            """
            select id, version
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
                rs.getLayoutRowVersion<TrackLayoutKmPost>("id", "version")
            }
            .also { ids -> logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids) }
    }

    fun fetchNextWithLocationAfter(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        state: LayoutState,
    ): LayoutRowVersion<TrackLayoutKmPost>? {
        val sql =
            """
            select row_id, row_version
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
            rs.getLayoutRowVersion("row_id", "row_version")
        }
    }
}
