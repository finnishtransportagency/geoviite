package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getFreeText
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getTrackNumber
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val TRACK_NUMBER_CACHE_SIZE = 1000L

@Transactional(readOnly = true)
@Component
class LayoutTrackNumberDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<TrackLayoutTrackNumber>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER,
        cacheEnabled,
        TRACK_NUMBER_CACHE_SIZE,
    ) {

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null)

    fun list(layoutContext: LayoutContext, trackNumber: TrackNumber): List<TrackLayoutTrackNumber> =
        fetchVersions(layoutContext, false, trackNumber).map { r -> fetch(r.rowVersion) }

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        number: TrackNumber?,
    ): List<LayoutDaoResponse<TrackLayoutTrackNumber>> {
        val sql =
            """
            select official_id, row_id, row_version
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where (:number::varchar is null or :number = number)
              and (:include_deleted = true or state != 'DELETED')
            order by number
        """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "number" to number,
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getDaoResponse("official_id", "row_id", "row_version") }
    }

    override fun fetchInternal(version: LayoutRowVersion<TrackLayoutTrackNumber>): TrackLayoutTrackNumber {
        val sql =
            """
            -- Draft vs Official reference line might duplicate the row, but results will be the same. Just pick one.
            select distinct on (tn.id, tn.version)
              tn.id as row_id,
              tn.version as row_version,
              tn.official_row_id,
              tn.design_row_id,
              tn.design_id,
              tn.draft,
              tn.external_id,
              tn.number,
              tn.description,
              tn.state,
              rl.official_id reference_line_id
            from layout.track_number_version tn
              -- TrackNumber reference line identity should never change, so we can join version 1
              left join layout.reference_line_version rl on rl.track_number_id = tn.official_id and rl.version = 1
            where tn.id = :id
              and tn.version = :version
              and tn.deleted = false
            order by tn.id, tn.version, rl.id
        """
                .trimIndent()
        val params = mapOf("id" to version.rowId.intValue, "version" to version.version)
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutTrackNumber(rs) }).also {
            logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, version)
        }
    }

    override fun preloadCache() {
        val sql =
            """
            -- Draft vs Official reference line might duplicate the row, but results will be the same. Just pick one.
            select distinct on (tn.id)
              tn.id as row_id,
              tn.version as row_version,
              tn.official_row_id,
              tn.design_row_id,
              tn.design_id,
              tn.draft,
              tn.external_id, 
              tn.number, 
              tn.description,
              tn.state,
              rl.official_id as reference_line_id
            from layout.track_number tn
              left join layout.reference_line rl on rl.track_number_id = tn.official_id
            order by tn.id, rl.id
        """
                .trimIndent()
        val trackNumbers =
            jdbcTemplate
                .query(sql, mapOf<String, Any>()) { rs, _ -> getLayoutTrackNumber(rs) }
                .associateBy(TrackLayoutTrackNumber::version)
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, trackNumbers.keys)
        cache.putAll(trackNumbers)
    }

    private fun getLayoutTrackNumber(rs: ResultSet): TrackLayoutTrackNumber =
        TrackLayoutTrackNumber(
            number = rs.getTrackNumber("number"),
            description = rs.getFreeText("description"),
            state = rs.getEnum("state"),
            externalId = rs.getOidOrNull("external_id"),
            // TODO: GVT-2442 This should be non-null but we have a lot of tests that produce broken
            // data
            referenceLineId = rs.getIntIdOrNull("reference_line_id"),
            contextData =
                rs.getLayoutContextData(
                    "official_row_id",
                    "design_row_id",
                    "design_id",
                    "row_id",
                    "row_version",
                    "draft",
                ),
        )

    @Transactional
    override fun insert(newItem: TrackLayoutTrackNumber): LayoutDaoResponse<TrackLayoutTrackNumber> {
        val sql =
            """
            insert into layout.track_number(
              external_id, 
              number, 
              description, 
              state, 
              draft, 
              official_row_id,
              design_row_id,
              design_id
            ) 
            values (
              :external_id, 
              :number, 
              :description, 
              :state::layout.state,
              :draft, 
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
                "external_id" to newItem.externalId,
                "number" to newItem.number,
                "description" to newItem.description,
                "state" to newItem.state.name,
                "draft" to newItem.isDraft,
                "official_row_id" to newItem.contextData.officialRowId?.intValue,
                "design_row_id" to newItem.contextData.designRowId?.intValue,
                "design_id" to newItem.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<TrackLayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, TrackLayoutTrackNumber::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutTrackNumber): LayoutDaoResponse<TrackLayoutTrackNumber> {
        val rowId =
            requireNotNull(updatedItem.contextData.rowId) {
                "Cannot update a row that doesn't have a DB ID: kmPost=$updatedItem"
            }
        val sql =
            """
            update layout.track_number
            set
              external_id = :external_id,
              number = :number,
              description = :description,
              state = :state::layout.state,
              draft = :draft,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id
            where id = :id
            returning 
              official_id,
              id as row_id,
              version as row_version
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to rowId.intValue,
                "external_id" to updatedItem.externalId,
                "number" to updatedItem.number,
                "description" to updatedItem.description,
                "state" to updatedItem.state.name,
                "draft" to updatedItem.isDraft,
                "official_row_id" to updatedItem.contextData.officialRowId?.intValue,
                "design_row_id" to updatedItem.contextData.designRowId?.intValue,
                "design_id" to updatedItem.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<TrackLayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getDaoResponse("official_id", "row_id", "row_version")
            } ?: throw IllegalStateException("Failed to get new version for Track Layout TrackNumber")
        logger.daoAccess(AccessType.UPDATE, TrackLayoutTrackNumber::class, response)
        return response
    }

    fun fetchTrackNumberNames(): List<TrackNumberAndChangeTime> {
        val sql =
            """
            select tn.id, tn.number, tn.change_time
            from layout.track_number_version tn
            where tn.draft = false
            order by tn.change_time
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ ->
                TrackNumberAndChangeTime(rs.getIntId("id"), rs.getTrackNumber("number"), rs.getInstant("change_time"))
            }
            .also { logger.daoAccess(AccessType.FETCH, "track_number_version") }
    }

    fun findNumberDuplicates(
        context: LayoutContext,
        numbers: List<TrackNumber>,
    ): Map<TrackNumber, List<LayoutDaoResponse<TrackLayoutTrackNumber>>> {
        return if (numbers.isEmpty()) {
            emptyMap()
        } else {
            val sql =
                """
                select official_id, row_id, row_version, number
                from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where number in (:numbers)
                  and state != 'DELETED'
            """
                    .trimIndent()
            val params =
                mapOf(
                    "numbers" to numbers,
                    "publication_state" to context.state.name,
                    "design_id" to context.branch.designId?.intValue,
                )
            val found =
                jdbcTemplate.query<Pair<TrackNumber, LayoutDaoResponse<TrackLayoutTrackNumber>>>(sql, params) { rs, _ ->
                    val daoResponse = rs.getDaoResponse<TrackLayoutTrackNumber>("official_id", "row_id", "row_version")
                    val name = rs.getString("number").let(::TrackNumber)
                    name to daoResponse
                }
            // Ensure that the result contains all asked-for numbers, even if there are no matches
            numbers.associateWith { n -> found.filter { (number, _) -> number == n }.map { (_, v) -> v } }
        }
    }
}
