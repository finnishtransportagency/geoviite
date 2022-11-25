package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_TRACK_NUMBER
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.TrackNumberPublishCandidate
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class LayoutTrackNumberDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<TrackLayoutTrackNumber>(jdbcTemplateParam, DbTable.LAYOUT_TRACK_NUMBER) {

    fun fetchExternalIdToIdMapping(): Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>> {
        val sql = "select id, external_id from layout.track_number where external_id is not null"
        val result: List<Pair<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>>> =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
                rs.getOid<TrackLayoutTrackNumber>("external_id") to rs.getIntId("id")
            }
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, result.map { r -> r.second })
        return result.associate { it }
    }

    fun getTrackNumberToIdMapping(): Map<TrackNumber, IntId<TrackLayoutTrackNumber>> {
        val sql = "select id, number from layout.track_number"
        val result: List<Pair<TrackNumber, IntId<TrackLayoutTrackNumber>>> =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
                rs.getTrackNumber("number") to rs.getIntId("id")
            }
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, result.map { r -> r.second })
        return result.associate { it }
    }

    @Cacheable(CACHE_LAYOUT_TRACK_NUMBER, sync = true)
    override fun fetch(version: RowVersion<TrackLayoutTrackNumber>): TrackLayoutTrackNumber {
        val sql = """
            select 
              official_id, 
              official_version,
              draft_id,
              draft_version,
              external_id, 
              number, 
              description,
              state 
            from layout.track_number_publication_view
            where row_id = :id
        """.trimIndent()
        val params = mapOf("id" to version.id.intValue)
        val trackNumber = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutTrackNumber(
                id = rs.getIntId("official_id"),
                number = rs.getTrackNumber("number"),
                description = rs.getFreeText("description"),
                state = rs.getEnum("state"),
                externalId = rs.getOidOrNull("external_id"),
                dataType = DataType.STORED,
                draft = rs.getIntIdOrNull<TrackLayoutTrackNumber>("draft_id")?.let { id -> Draft(id) },
                version = rs.getVersion("official_version", "draft_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, trackNumber.id)
        return trackNumber
    }

    @Transactional
    override fun insert(newItem: TrackLayoutTrackNumber): RowVersion<TrackLayoutTrackNumber> {
        verifyDraftableInsert(newItem)
        val sql = """
            insert into layout.track_number(
              external_id, 
              number, 
              description, 
              state, 
              draft, 
              draft_of_track_number_id
            ) 
            values (
              :external_id, 
              :number, 
              :description, 
              :state::layout.state,
              :draft, 
              :draft_of_track_number_id
            ) 
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "external_id" to newItem.externalId,
            "number" to newItem.number,
            "description" to newItem.description,
            "state" to newItem.state.name,
            "draft" to (newItem.draft != null),
            "draft_of_track_number_id" to draftOfId(newItem)?.intValue,
        )
        jdbcTemplate.setUser()
        val idAndVersion = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion<TrackLayoutTrackNumber>("id", "version")
        } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, TrackLayoutTrackNumber::class, idAndVersion)
        return idAndVersion
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutTrackNumber): RowVersion<TrackLayoutTrackNumber> {
        val rowId = toDbId(updatedItem.draft?.draftRowId ?: updatedItem.id)
        val sql = """
            update layout.track_number
            set
              external_id = :external_id,
              number = :number,
              description = :description,
              state = :state::layout.state,
              draft = :draft,
              draft_of_track_number_id = :draft_of_track_number_id
            where id = :id
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "external_id" to updatedItem.externalId,
            "number" to updatedItem.number,
            "description" to updatedItem.description,
            "state" to updatedItem.state.name,
            "draft" to (updatedItem.draft != null),
            "draft_of_track_number_id" to draftOfId(updatedItem)?.intValue,
        )
        jdbcTemplate.setUser()
        val result: RowVersion<TrackLayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to get new version for Track Layout TrackNumber")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutTrackNumber::class, rowId)
        return result
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<TrackNumberPublishCandidate> {
        val sql = """
          select
            track_number_version.id,
            track_number_version.change_time,
            track_number_version.number
          from publication.track_number published_track_number
            left join layout.track_number_version
              on published_track_number.track_number_id = track_number_version.id
                and published_track_number.track_number_version = track_number_version.version
          where publication_id = :id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "id" to publicationId.intValue
            )
        ) { rs, _ ->
            TrackNumberPublishCandidate(
                id = rs.getIntId("id"),
                draftChangeTime = rs.getInstant("change_time"),
                number = rs.getTrackNumber("number")
            )
        }.also { logger.daoAccess(AccessType.FETCH, Publication::class, publicationId) }
    }

    fun findVersions(number: TrackNumber, publishType: PublishType): List<RowVersion<TrackLayoutTrackNumber>> {
        val sql = """
            select row_id, row_version
            from layout.track_number_publication_view
            where :number = number
              and :publication_state = any(publication_states)
        """.trimIndent()
        val params = mapOf(
            "number" to number,
            "publication_state" to publishType.name,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }
}
