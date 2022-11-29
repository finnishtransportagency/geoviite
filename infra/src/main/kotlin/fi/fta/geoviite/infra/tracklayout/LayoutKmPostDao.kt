package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_KM_POST
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.create2DPolygonString
import fi.fta.geoviite.infra.linking.KmPostPublishCandidate
import fi.fta.geoviite.infra.linking.Publication
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

    fun fetchVersions(
        publishType: PublishType,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        bbox: BoundingBox? = null,
    ): List<RowVersion<TrackLayoutKmPost>> {
        val sql = """
            select km_post.row_id, km_post.row_version 
            from layout.km_post_publication_view km_post
            where :publication_state = any(km_post.publication_states)
              and km_post.state != 'DELETED'
              and (:track_number_id::int is null or track_number_id = :track_number_id)
              and (:polygon_wkt::varchar is null or postgis.st_intersects(
                km_post.location,
                postgis.st_polygonfromtext(:polygon_wkt::varchar, :map_srid)
              ))
            order by km_post.track_number_id, km_post.km_number
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "draft" to (publishType == PublishType.DRAFT),
            "publication_state" to publishType.name,
            "polygon_wkt" to bbox?.let { b -> create2DPolygonString(b.polygonFromCorners) },
            "map_srid" to LAYOUT_SRID.code,
        )) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersion(
        publishType: PublishType,
        trackNumberId: IntId<LayoutTrackNumber>,
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
              official_id, 
              official_version,
              draft_id,
              draft_version,
              track_number_id,
              geometry_km_post_id,
              km_number,
              postgis.st_x(location) as point_x, postgis.st_y(location) as point_y,
              state
            from layout.km_post_publication_view
            where row_id = :id
        """.trimIndent()
        val params = mapOf("id" to version.id.intValue)
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
                version = rs.getVersion("official_version", "draft_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, post.id)
        return post
    }

    @Transactional
    override fun insert(newItem: TrackLayoutKmPost): RowVersion<TrackLayoutKmPost> {
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
            returning id, version
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
        val rowVersion: RowVersion<TrackLayoutKmPost> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to generate ID for new km-post")
        logger.daoAccess(AccessType.INSERT, TrackLayoutKmPost::class, rowVersion)
        return rowVersion
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutKmPost): RowVersion<TrackLayoutKmPost> {
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
            returning id, version
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
        val rowVersion: RowVersion<TrackLayoutKmPost> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to generate ID for new km-post")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutKmPost::class, rowId)
        return rowVersion
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<KmPostPublishCandidate> {
        val sql = """
          select
            km_post_version.id,
            km_post_version.change_time,
            km_post_version.track_number_id,
            km_number
          from publication.km_post published_km_post
            left join layout.km_post_version
              on published_km_post.km_post_id = km_post_version.id
                and published_km_post.km_post_version = km_post_version.version
            left join layout.track_number
              on km_post_version.track_number_id = track_number.id
          where publication_id = :id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "id" to publicationId.intValue,
            )
        ) { rs, _ ->
            KmPostPublishCandidate(
                id = rs.getIntId("id"),
                draftChangeTime = rs.getInstant("change_time"),
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number")
            )
        }.also { logger.daoAccess(AccessType.FETCH, Publication::class, publicationId) }
    }
}
