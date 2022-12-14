package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_TRACK_NUMBER
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublishedTrackNumber
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class LayoutTrackNumberDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<TrackLayoutTrackNumber>(jdbcTemplateParam, DbTable.LAYOUT_TRACK_NUMBER) {

    override fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean) =
        fetchVersions(publicationState, includeDeleted, null)

    fun fetchVersions(
        publicationState: PublishType,
        includeDeleted: Boolean,
        number: TrackNumber?,
    ): List<RowVersion<TrackLayoutTrackNumber>> {
        val sql = """
            select row_id, row_version
            from layout.track_number_publication_view
            where :publication_state = any(publication_states)
              and (:number::varchar is null or :number = number)
              and (:include_deleted = true or state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
            "number" to number,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

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
              id as row_id,
              version as row_version,
              coalesce(draft_of_track_number_id, id) official_id, 
              case when draft then id end as draft_id,
              external_id, 
              number, 
              description,
              state 
            from layout.track_number_version
            where id = :id
              and version = :version
              and deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        val trackNumber = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutTrackNumber(
                id = rs.getIntId("official_id"),
                number = rs.getTrackNumber("number"),
                description = rs.getFreeText("description"),
                state = rs.getEnum("state"),
                externalId = rs.getOidOrNull("external_id"),
                dataType = DataType.STORED,
                draft = rs.getIntIdOrNull<TrackLayoutTrackNumber>("draft_id")?.let { id -> Draft(id) },
                version = rs.getRowVersion("row_id", "row_version"),
            )
        })
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, trackNumber.id)
        return trackNumber
    }

    @Transactional
    override fun insert(newItem: TrackLayoutTrackNumber): DaoResponse<TrackLayoutTrackNumber> {
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
            returning 
              coalesce(draft_of_track_number_id, id) as official_id,
              id as row_id,
              version as row_version
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
        val response: DaoResponse<TrackLayoutTrackNumber> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, TrackLayoutTrackNumber::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutTrackNumber): DaoResponse<TrackLayoutTrackNumber> {
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
            returning 
              coalesce(draft_of_track_number_id, id) as official_id,
              id as row_id,
              version as row_version
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
        val response: DaoResponse<TrackLayoutTrackNumber> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Track Layout TrackNumber")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutTrackNumber::class, response)
        return response
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<PublishedTrackNumber> {
        val sql = """
          select
            ptn.track_number_id as id,
            ptn.track_number_version as version,
            tn.number,
            layout.infer_operation_from_state_transition(
              tn.old_state, 
              tn.state
            ) as operation
          from publication.track_number ptn
            left join layout.track_number_change_view tn
              on ptn.track_number_id = tn.id
                and ptn.track_number_version = tn.version
          where publication_id = :publication_id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            PublishedTrackNumber(
                version = rs.getRowVersion("id", "version"),
                number = rs.getTrackNumber("number"),
                operation = rs.getEnum("operation")
            )
        }.also { trackNumbers ->
            logger.daoAccess(
                AccessType.FETCH,
                PublishedTrackNumber::class,
                trackNumbers.map { it.version })
        }
    }
}
