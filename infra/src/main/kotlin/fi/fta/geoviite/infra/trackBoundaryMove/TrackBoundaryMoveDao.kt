package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutBranch
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class TrackBoundaryMoveDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun save(
        layoutBranch: LayoutBranch,
        shortenedLocationTrack: LayoutRowVersion<LocationTrack>,
        edgeRange: IntRange,
        lengthenedLocationTrack: LayoutRowVersion<LocationTrack>,
    ): IntId<TrackBoundaryMove> {
        val sql =
            """
            insert into publication.track_boundary_move(
              design_id,
              shortened_location_track_id,
              shortened_location_track_version,
              shortened_location_track_layout_context_id,
              source_start_edge_index,
              source_end_edge_index,
              lengthened_location_track_id,
              lengthened_location_track_version,
              lengthened_location_track_layout_context_id,
              publication_id
            )
            values (
              :design_id::int,
              :shortened_location_track_id,
              :shortened_location_track_version,
              :shortened_location_track_layout_context_id,
              :source_start_edge_index,
              :source_end_edge_index,
              :lengthened_location_track_id,
              :lengthened_location_track_version,
              :lengthened_location_track_layout_context_id,
              null
            )
            returning id
            """
                .trimIndent()

        jdbcTemplate.setUser()
        val params =
            mapOf(
                "design_id" to layoutBranch.designId?.intValue,
                "shortened_location_track_id" to shortenedLocationTrack.id.intValue,
                "shortened_location_track_version" to shortenedLocationTrack.version,
                "shortened_location_track_layout_context_id" to shortenedLocationTrack.context.toSqlString(),
                "source_start_edge_index" to edgeRange.first,
                "source_end_edge_index" to edgeRange.last,
                "lengthened_location_track_id" to lengthenedLocationTrack.id.intValue,
                "lengthened_location_track_version" to lengthenedLocationTrack.version,
                "lengthened_location_track_layout_context_id" to lengthenedLocationTrack.context.toSqlString(),
            )
        val id =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getIntId<TrackBoundaryMove>("id") }
                ?: error(
                    "Failed to save track boundary move: shortened=$shortenedLocationTrack lengthened=$lengthenedLocationTrack"
                )
        logger.daoAccess(AccessType.INSERT, TrackBoundaryMove::class, id)
        return id
    }

    fun get(id: IntId<TrackBoundaryMove>): TrackBoundaryMove? {
        val sql =
            """
            select
              id,
              version,
              design_id,
              shortened_location_track_id,
              shortened_location_track_version,
              shortened_location_track_layout_context_id,
              source_start_edge_index,
              source_end_edge_index,
              lengthened_location_track_id,
              lengthened_location_track_version,
              lengthened_location_track_layout_context_id,
              publication_id
            from publication.track_boundary_move
            where id = :id
            """
                .trimIndent()
        return jdbcTemplate
            .queryOptional(sql, mapOf("id" to id.intValue)) { rs, _ -> getTrackBoundaryMove(rs) }
            ?.also { logger.daoAccess(AccessType.FETCH, TrackBoundaryMove::class, id) }
    }

    fun getOrThrow(id: IntId<TrackBoundaryMove>) = requireNotNull(get(id))

    fun getUnpublished(): List<TrackBoundaryMove> {
        val sql =
            """
            select
              id,
              version,
              design_id,
              shortened_location_track_id,
              shortened_location_track_version,
              shortened_location_track_layout_context_id,
              source_start_edge_index,
              source_end_edge_index,
              lengthened_location_track_id,
              lengthened_location_track_version,
              lengthened_location_track_layout_context_id,
              publication_id
            from publication.track_boundary_move
            where publication_id is null
            """
                .trimIndent()
        return jdbcTemplate.query(sql) { rs, _ -> getTrackBoundaryMove(rs) }
    }

    @Transactional
    fun update(id: IntId<TrackBoundaryMove>, publicationId: IntId<Publication>?): RowVersion<TrackBoundaryMove> {
        val sql =
            """
            update publication.track_boundary_move
            set publication_id = coalesce(:publication_id, publication_id)
            where id = :id
            returning id, version
            """
                .trimIndent()

        val params = mapOf("publication_id" to publicationId?.intValue, "id" to id.intValue)

        jdbcTemplate.setUser()
        return getOne(id, jdbcTemplate.query(sql, params) { rs, _ -> rs.getRowVersion("id", "version") })
    }

    @Transactional
    fun delete(id: IntId<TrackBoundaryMove>) {
        val sql = "delete from publication.track_boundary_move where id = :id"

        jdbcTemplate.setUser()
        jdbcTemplate.update(sql, mapOf("id" to id.intValue)).also {
            logger.daoAccess(AccessType.DELETE, TrackBoundaryMove::class, id)
        }
    }
}

private fun getTrackBoundaryMove(rs: ResultSet) =
    TrackBoundaryMove(
        version = rs.getRowVersion("id", "version"),
        shortenedLocationTrack =
            rs.getLayoutRowVersion(
                "shortened_location_track_id",
                "shortened_location_track_layout_context_id",
                "shortened_location_track_version",
            ),
        edgeRange = IntRange(rs.getInt("source_start_edge_index"), rs.getInt("source_end_edge_index")),
        lengthenedLocationTrack =
            rs.getLayoutRowVersion(
                "lengthened_location_track_id",
                "lengthened_location_track_layout_context_id",
                "lengthened_location_track_version",
            ),
        publicationId = rs.getIntIdOrNull("publication_id"),
        branch = rs.getLayoutBranch("design_id"),
    )
