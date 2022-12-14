package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_KM_POST
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.create2DPolygonString
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublishedKmPost
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_KM_POST
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class LayoutKmPostDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<TrackLayoutKmPost>(jdbcTemplateParam, LAYOUT_KM_POST) {

    override fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean) =
        fetchVersions(publicationState, includeDeleted, null, null)

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
        return jdbcTemplate.query(sql, mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "publicationState" to publicationState,
            "include_deleted" to includeDeleted,
            "publication_state" to publicationState.name,
            "polygon_wkt" to bbox?.let { b -> create2DPolygonString(b.polygonFromCorners) },
            "map_srid" to LAYOUT_SRID.code,
        )) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsForPublication(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmPostIdsToPublish: List<IntId<TrackLayoutKmPost>>,
    ): List<RowVersion<TrackLayoutKmPost>> {
        val sql = """
            select km_post.row_id, km_post.row_version
            from layout.km_post_publication_view km_post
            where (('DRAFT' = any(km_post.publication_states)
                     and km_post.official_id in (:km_post_ids_to_publish))
                   or ('OFFICIAL' = any(km_post.publication_states)
                         and (km_post.official_id in (:km_post_ids_to_publish) is distinct from true)))
              and track_number_id = :track_number_id
              and km_post.state != 'DELETED'
            order by km_post.track_number_id, km_post.km_number
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf(
            "track_number_id" to trackNumberId.intValue,
            // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is distinct from true" checks
            // explicitly for false or null, since "foo in (null)" in SQL is null
            "km_post_ids_to_publish" to (kmPostIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) })
        )) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersion(
        publicationState: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
    ): RowVersion<TrackLayoutKmPost>? {
        val sql = """
            select km_post.row_id, km_post.row_version 
            from layout.km_post_publication_view km_post
            where :publication_state = any(km_post.publication_states)
              and km_post.state != 'DELETED'
              and km_post.track_number_id = :track_number_id
              and km_post.km_number = :km_number
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "km_number" to kmNumber.toString(),
            "publication_state" to publicationState.name,
        )
        val result = jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion<TrackLayoutKmPost>("row_id", "row_version")
        }
        return result.firstOrNull()
    }

    @Cacheable(CACHE_LAYOUT_KM_POST, sync = true)
    override fun fetch(version: RowVersion<TrackLayoutKmPost>): TrackLayoutKmPost {
        val sql = """
            select 
              id as row_id,
              version as row_version,
              coalesce(draft_of_km_post_id, id) official_id, 
              case when draft then id end as draft_id,
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
        val post = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutKmPost(
                id = rs.getIntId("official_id"),
                dataType = DataType.STORED,
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                location = rs.getPointOrNull("point_x", "point_y"),
                state = rs.getEnum("state"),
                sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
                draft = rs.getIntIdOrNull<TrackLayoutKmPost>("draft_id")?.let { id -> Draft(id) },
                version = rs.getRowVersion("row_id", "row_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, post.id)
        return post
    }

    @Transactional
    override fun insert(newItem: TrackLayoutKmPost): DaoResponse<TrackLayoutKmPost> {
        verifyDraftableInsert(newItem.id, newItem.draft)

        val trackNumberId =
            if (newItem.trackNumberId is IntId) newItem.trackNumberId
            else throw IllegalArgumentException("KM post not linked to TrackNumber: tnId=${newItem.trackNumberId}")
        val sql = """
            insert into layout.km_post(
              track_number_id, 
              geometry_km_post_id, 
              km_number, 
              location, 
              state,
              draft,
              draft_of_km_post_id
            )
            values (
              :track_number_id, 
              :geometry_km_post_id, 
              :km_number,
              postgis.st_setsrid(postgis.st_point(:point_x, :point_y), :srid), 
              :state::layout.state,
              :draft,
              :draft_of_km_post_id
            )
            returning 
              coalesce(draft_of_km_post_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "geometry_km_post_id" to newItem.sourceId.let { if (it is IntId<GeometryKmPost>) it.intValue else null },
            "km_number" to newItem.kmNumber.toString(),
            "point_x" to newItem.location?.x,
            "point_y" to newItem.location?.y,
            "srid" to LAYOUT_SRID.code,
            "state" to newItem.state.name,
            "draft" to (newItem.draft != null),
            "draft_of_km_post_id" to draftOfId(newItem.id, newItem.draft)?.intValue,
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
        val rowId = toDbId(updatedItem.draft?.draftRowId ?: updatedItem.id)
        val trackNumberId =
            if (updatedItem.trackNumberId is IntId) updatedItem.trackNumberId
            else throw IllegalArgumentException("KM post not linked to TrackNumber: " +
                    "kmPostId=${updatedItem.id} tnId=${updatedItem.trackNumberId}")
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
              draft_of_km_post_id = :draft_of_km_post_id
            where id = :km_post_id
            returning 
              coalesce(draft_of_km_post_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "km_post_id" to rowId.intValue,
            "track_number_id" to trackNumberId.intValue,
            "geometry_km_post_id" to (updatedItem.sourceId as IntId<GeometryKmPost>?)?.intValue,
            "km_number" to updatedItem.kmNumber.toString(),
            "hasLocation" to (updatedItem.location != null),
            "point_x" to updatedItem.location?.x,
            "point_y" to updatedItem.location?.y,
            "srid" to LAYOUT_SRID.code,
            "state" to updatedItem.state.name,
            "draft" to (updatedItem.draft != null),
            "draft_of_km_post_id" to draftOfId(updatedItem.id, updatedItem.draft)?.intValue,
        )
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutKmPost> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new row version of updated km-post")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutKmPost::class, rowId)
        return response
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<PublishedKmPost> {
        val sql = """
            select
              published_km_post.km_post_id as id,
              published_km_post.km_post_version as version,
              layout.infer_operation_from_state_transition(km_post.old_state, km_post.state) as operation,
              km_post.km_number,
              km_post.track_number_id
            from publication.km_post published_km_post
            left join layout.km_post_change_view km_post
              on km_post.id = published_km_post.km_post_id
                and km_post.version = published_km_post.km_post_version
            where publication_id = :publication_id
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            PublishedKmPost(
                version = rs.getRowVersion("id", "version"),
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                operation = rs.getEnum("operation")
            )
        }.also { kmPosts -> logger.daoAccess(AccessType.FETCH, PublishedKmPost::class, kmPosts.map { it.version }) }
    }
}
