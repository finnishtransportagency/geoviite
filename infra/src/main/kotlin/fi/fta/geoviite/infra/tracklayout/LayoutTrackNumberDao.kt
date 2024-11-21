package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
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
        fetchVersions(layoutContext, false, trackNumber).map(::fetch)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        number: TrackNumber?,
    ): List<LayoutRowVersion<TrackLayoutTrackNumber>> {
        val sql =
            """
            select id, design_id, draft, version
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
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    override fun fetchInternal(version: LayoutRowVersion<TrackLayoutTrackNumber>): TrackLayoutTrackNumber {
        val sql =
            """
            -- Draft vs Official reference line might duplicate the row, but results will be the same. Just pick one.
            select distinct on (tn.id, tn.version)
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.cancelled,
              tn.external_id,
              tn.number,
              tn.description,
              tn.state,
              tn.origin_design_id,
              rl.id reference_line_id,
              official_tn.id is not null as has_official
            from layout.track_number_version tn
              -- TrackNumber reference line identity should never change, so we can join version 1
              left join layout.reference_line_version rl on rl.track_number_id = tn.id and rl.version = 1
                and rl.layout_context_id = tn.layout_context_id
              left join layout.track_number official_tn on official_tn.id = tn.id
                and (official_tn.design_id is null or official_tn.design_id = tn.design_id)
                and not official_tn.draft
            where tn.id = :id
              and tn.layout_context_id = :layout_context_id
              and tn.version = :version
              and tn.deleted = false
            order by tn.id, tn.version, rl.id
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutTrackNumber(rs) }).also {
            logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, version)
        }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            -- Draft vs Official reference line might duplicate the row, but results will be the same. Just pick one.
            select distinct on (tn.id)
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.external_id, 
              tn.number, 
              tn.description,
              tn.state,
              tn.cancelled,
              rl.id as reference_line_id,
              official_tn.id is not null as has_official,
              tn.origin_design_id
            from layout.track_number tn
              left join layout.reference_line rl on rl.track_number_id = tn.id
                and rl.layout_context_id = tn.layout_context_id
              left join layout.track_number official_tn on official_tn.id = tn.id
                and (official_tn.design_id is null or official_tn.design_id = tn.design_id)
                and not official_tn.draft
            order by tn.id, rl.id
        """
                .trimIndent()
        val trackNumbers =
            jdbcTemplate.query(sql) { rs, _ -> getLayoutTrackNumber(rs) }.associateBy(TrackLayoutTrackNumber::version)
        logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, trackNumbers.keys)
        cache.putAll(trackNumbers)
        return trackNumbers.size
    }

    private fun getLayoutTrackNumber(rs: ResultSet): TrackLayoutTrackNumber =
        TrackLayoutTrackNumber(
            number = rs.getTrackNumber("number"),
            description = rs.getString("description").let(::TrackNumberDescription),
            state = rs.getEnum("state"),
            externalId = rs.getOidOrNull("external_id"),
            // TODO: GVT-2442 This should be non-null but we have a lot of tests that produce broken
            // data
            referenceLineId = rs.getIntIdOrNull("reference_line_id"),
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

    @Transactional
    override fun save(item: TrackLayoutTrackNumber): LayoutRowVersion<TrackLayoutTrackNumber> {
        val id = item.id as? IntId ?: createId()

        // language=sql
        val sql =
            """
            insert into layout.track_number(layout_context_id,
                                            id,
                                            external_id,
                                            number,
                                            description,
                                            state,
                                            draft,
                                            cancelled,
                                            design_id)
              values
                (:layout_context_id,
                 :id,
                 :external_id,
                 :number,
                 :description,
                 :state::layout.state,
                 :draft,
                 :cancelled,
                 :design_id)
              on conflict (id, layout_context_id) do update
                set external_id = excluded.external_id,
                    number = excluded.number,
                    description = excluded.description,
                    state = excluded.state,
                    cancelled = excluded.cancelled
              returning id, design_id, draft, version;
        """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "external_id" to item.externalId,
                "number" to item.number,
                "description" to item.description,
                "state" to item.state.name,
                "draft" to item.isDraft,
                "cancelled" to item.isCancelled,
                "design_id" to item.contextData.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutRowVersion<TrackLayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, TrackLayoutTrackNumber::class, response)
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
    ): Map<TrackNumber, List<LayoutRowVersion<TrackLayoutTrackNumber>>> {
        return if (numbers.isEmpty()) {
            emptyMap()
        } else {
            val sql =
                """
                select id, design_id, draft, version, number
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
                jdbcTemplate.query<Pair<TrackNumber, LayoutRowVersion<TrackLayoutTrackNumber>>>(sql, params) { rs, _ ->
                    val daoResponse =
                        rs.getLayoutRowVersion<TrackLayoutTrackNumber>("id", "design_id", "draft", "version")
                    val name = rs.getString("number").let(::TrackNumber)
                    name to daoResponse
                }
            // Ensure that the result contains all asked-for numbers, even if there are no matches
            numbers.associateWith { n -> found.filter { (number, _) -> number == n }.map { (_, v) -> v } }
        }
    }
}
