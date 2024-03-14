package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_KM_POST
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
) : LayoutAssetDao<TrackLayoutKmPost>(jdbcTemplateParam, LAYOUT_KM_POST, cacheEnabled, KM_POST_CACHE_SIZE) {

    override fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean) =
        fetchVersions(publicationState, includeDeleted, null, null)

    fun list(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<TrackLayoutKmPost> = fetchVersions(publicationState, includeDeleted, trackNumberId, bbox).map(::fetch)

    fun fetchVersions(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<RowVersion<TrackLayoutKmPost>> {
        val sql = """
            select km_post.row_id, km_post.row_version 
            from layout.km_post_publication_view km_post
            where :publication_state = any(km_post.publication_states)
              and (:include_deleted = true or km_post.state != 'DELETED')
              and (:track_number_id::int is null or track_number_id = :track_number_id)
              and (:polygon_wkt::varchar is null or postgis.st_intersects(
                km_post.location,
                postgis.st_polygonfromtext(:polygon_wkt::varchar, :map_srid)
              ))
            order by km_post.track_number_id, km_post.km_number
        """.trimIndent()
        return jdbcTemplate.query(
            sql, mapOf(
                "track_number_id" to trackNumberId?.intValue,
                "publicationState" to publicationState,
                "include_deleted" to includeDeleted,
                "publication_state" to publicationState.name,
                "polygon_wkt" to bbox?.let { b -> create2DPolygonString(b.polygonFromCorners) },
                "map_srid" to LAYOUT_SRID.code,
            )
        ) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsForPublication(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<ValidationVersion<TrackLayoutKmPost>>> {
        if (trackNumberIds.isEmpty()) return emptyMap()
        val sql = """
            select km_post.track_number_id, km_post.official_id, km_post.row_id, km_post.row_version
            from layout.km_post_publication_view km_post
            where (('DRAFT' = any(km_post.publication_states)
                     and km_post.official_id in (:km_post_ids_to_publish))
                   or ('OFFICIAL' = any(km_post.publication_states)
                         and (km_post.official_id in (:km_post_ids_to_publish) is distinct from true)))
              and track_number_id in (:track_number_ids)
              and km_post.state != 'DELETED'
            order by km_post.track_number_id, km_post.km_number
        """.trimIndent()
        val params = mapOf(
            "track_number_ids" to trackNumberIds.map { id -> id.intValue },
            // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is distinct from true" checks
            // explicitly for false or null, since "foo in (null)" in SQL is null
            "km_post_ids_to_publish" to (kmPostIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) }),
        )
        val versions = jdbcTemplate.query(sql, params) { rs, _ ->
            val trackNumberId = rs.getIntId<TrackLayoutTrackNumber>("track_number_id")
            val officialId = rs.getIntId<TrackLayoutKmPost>("official_id")
            val rowVersion = rs.getRowVersion<TrackLayoutKmPost>("row_id", "row_version")
            trackNumberId to ValidationVersion(officialId, rowVersion)
        }
        return trackNumberIds.associateWith { trackNumberId ->
            versions.filter { (tnId, _) -> tnId == trackNumberId }.map { (_, kmPostVersions) -> kmPostVersions }
        }
    }

    fun fetchVersion(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): RowVersion<TrackLayoutKmPost>? {
        val sql = """
            select km_post.row_id, km_post.row_version 
            from layout.km_post_publication_view km_post
            where :publication_state = any(km_post.publication_states)
              and (:include_deleted or km_post.state != 'DELETED')
              and km_post.track_number_id = :track_number_id
              and km_post.km_number = :km_number
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "km_number" to kmNumber.toString(),
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
        )
        val result = jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion<TrackLayoutKmPost>("row_id", "row_version")
        }
        return result.firstOrNull()
    }

    override fun fetchInternal(version: RowVersion<TrackLayoutKmPost>): TrackLayoutKmPost {
        val sql = """
            select 
              id as row_id,
              version as row_version,
              official_row_id, 
              draft,
              track_number_id,
              geometry_km_post_id,
              km_number,
              postgis.st_x(location) as point_x, postgis.st_y(location) as point_y,
              state
            from layout.km_post_version
            where id = :id 
              and version = :version
              and deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        return getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutKmPost(rs) }).also {
            logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, version)
        }
    }

    override fun preloadCache() {
        val sql = """
            select 
              id as row_id,
              version as row_version,
              official_row_id, 
              draft,
              track_number_id,
              geometry_km_post_id,
              km_number,
              postgis.st_x(location) as point_x, postgis.st_y(location) as point_y,
              state
            from layout.km_post
        """.trimIndent()

        val posts = jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ -> getLayoutKmPost(rs) }
            .associateBy(TrackLayoutKmPost::version)
        logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, posts.keys)
        cache.putAll(posts)
    }

    private fun getLayoutKmPost(rs: ResultSet): TrackLayoutKmPost = TrackLayoutKmPost(
        trackNumberId = rs.getIntId("track_number_id"),
        kmNumber = rs.getKmNumber("km_number"),
        location = rs.getPointOrNull("point_x", "point_y"),
        state = rs.getEnum("state"),
        sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
        version = rs.getRowVersion("row_id", "row_version"),
        contextData = rs.getLayoutContextData("official_row_id", "row_id", "draft"),
    )

    @Transactional
    override fun insert(newItem: TrackLayoutKmPost): DaoResponse<TrackLayoutKmPost> {
        val trackNumberId = toDbId(requireNotNull(newItem.trackNumberId) {
            "KM post not linked to TrackNumber: kmPost=$newItem"
        })
        val sql = """
            insert into layout.km_post(
              track_number_id, 
              geometry_km_post_id, 
              km_number, 
              location, 
              state,
              draft,
              official_row_id
            )
            values (
              :track_number_id, 
              :geometry_km_post_id, 
              :km_number,
              postgis.st_setsrid(postgis.st_point(:point_x, :point_y), :srid), 
              :state::layout.state,
              :draft,
              :official_row_id
            )
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "geometry_km_post_id" to newItem.sourceId?.let(::toDbId)?.intValue,
            "km_number" to newItem.kmNumber.toString(),
            "point_x" to newItem.location?.x,
            "point_y" to newItem.location?.y,
            "srid" to LAYOUT_SRID.code,
            "state" to newItem.state.name,
            "draft" to newItem.contextData.isDraft,
            "official_row_id" to newItem.contextData.officialRowId?.let(::toDbId)?.intValue,
        )
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutKmPost> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new km-post")
        logger.daoAccess(AccessType.INSERT, TrackLayoutKmPost::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutKmPost): DaoResponse<TrackLayoutKmPost> {
        val trackNumberId = toDbId(requireNotNull(updatedItem.trackNumberId) {
            "KM post not linked to TrackNumber: kmPost=$updatedItem"
        })
        val sql = """
            update layout.km_post 
            set
              track_number_id = :track_number_id, 
              geometry_km_post_id = :geometry_km_post_id, 
              km_number = :km_number, 
              location = case 
                when :hasLocation then postgis.st_setsrid(postgis.st_point(:point_x, :point_y), :srid) 
              end,
              state = :state::layout.state,
              draft = :draft,
              official_row_id = :official_row_id
            where id = :km_post_id
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "km_post_id" to toDbId(updatedItem.contextData.rowId).intValue,
            "track_number_id" to trackNumberId.intValue,
            "geometry_km_post_id" to updatedItem.sourceId?.let(::toDbId)?.intValue,
            "km_number" to updatedItem.kmNumber.toString(),
            "hasLocation" to (updatedItem.location != null),
            "point_x" to updatedItem.location?.x,
            "point_y" to updatedItem.location?.y,
            "srid" to LAYOUT_SRID.code,
            "state" to updatedItem.state.name,
            "draft" to updatedItem.isDraft,
            "official_row_id" to updatedItem.contextData.officialRowId?.let(::toDbId)?.intValue,
        )
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutKmPost> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new row version of updated km-post")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutKmPost::class, response)
        return response
    }

    fun fetchOnlyDraftVersions(includeDeleted: Boolean, trackNumberId: IntId<TrackLayoutTrackNumber>? = null): List<RowVersion<TrackLayoutKmPost>> {
        val sql = """
            select id, version
            from layout.km_post
            where draft
              and (:include_deleted or state != 'DELETED')
              and (:trackNumberId::int is null or track_number_id = :trackNumberId)
        """.trimIndent()
        return jdbcTemplate.query(
            sql, mapOf("include_deleted" to includeDeleted, "trackNumberId" to trackNumberId?.intValue)
        ) { rs, _ ->
            rs.getRowVersion<TrackLayoutKmPost>("id", "version")
        }.also { ids ->
            logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids)
        }
    }

    fun fetchNextWithLocationAfter(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        publicationState: PublishType,
        state: LayoutState,
    ): RowVersion<TrackLayoutKmPost>? {
        val sql = """
            select row_id, row_version
            from layout.km_post_publication_view
            where track_number_id = :track_number_id
              and state = :state::layout.state
              and :publication_state = any (publication_states)
              and location is not null
              and km_number > :km_number
            order by km_number asc
            limit 1
        """.trimIndent()
        return jdbcTemplate.queryOptional(
            sql, mapOf(
                "track_number_id" to trackNumberId.intValue,
                "state" to state.name,
                "publication_state" to publicationState.name,
                "km_number" to kmNumber.toString()
            )
        ) { rs, _ -> rs.getRowVersion("row_id", "row_version") }
    }
}
