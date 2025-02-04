package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.RatkoPlanItemId
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getTrackNumber
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val TRACK_NUMBER_CACHE_SIZE = 1000L

@Transactional(readOnly = true)
@Component
class LayoutTrackNumberDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutTrackNumber, Unit>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER,
        cacheEnabled,
        TRACK_NUMBER_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutTrackNumber> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.track_number_external_id",
        "layout.track_number_external_id",
    ) {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LayoutTrackNumber>) = Unit

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null)

    fun list(layoutContext: LayoutContext, trackNumber: TrackNumber): List<LayoutTrackNumber> =
        fetchVersions(layoutContext, false, trackNumber).map(::fetch)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        number: TrackNumber?,
    ): List<LayoutRowVersion<LayoutTrackNumber>> {
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

    override fun fetchInternal(version: LayoutRowVersion<LayoutTrackNumber>): LayoutTrackNumber {
        val sql =
            """
            select
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.cancelled,
              tn.number,
              tn.description,
              tn.state,
              -- Track number reference line identity never changes, so any instance whatsoever is fine
              (select id from layout.reference_line_version rl where rl.track_number_id = tn.id limit 1) reference_line_id,
              exists (select * from layout.track_number official_tn
                      where official_tn.id = tn.id
                        and (official_tn.design_id is null or official_tn.design_id = tn.design_id)
                        and not official_tn.draft) has_official,
              tn.origin_design_id
            from layout.track_number_version tn
            where tn.id = :id
              and tn.layout_context_id = :layout_context_id
              and tn.version = :version
              and tn.deleted = false
            order by tn.id, tn.version
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutTrackNumber(rs) }).also {
            logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, version)
        }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select
              tn.id,
              tn.version,
              tn.design_id,
              tn.draft,
              tn.number, 
              tn.description,
              tn.state,
              tn.cancelled,
              -- Track number reference line identity never changes, so any instance whatsoever is fine
              (select id from layout.reference_line_version rl where rl.track_number_id = tn.id limit 1) reference_line_id,
              exists (select * from layout.track_number official_tn
                      where official_tn.id = tn.id
                        and (official_tn.design_id is null or official_tn.design_id = tn.design_id)
                        and not official_tn.draft) has_official,
              tn.origin_design_id
            from layout.track_number tn
            order by tn.id
        """
                .trimIndent()
        val trackNumbers =
            jdbcTemplate.query(sql) { rs, _ -> getLayoutTrackNumber(rs) }.associateBy(LayoutTrackNumber::version)
        logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, trackNumbers.keys)
        cache.putAll(trackNumbers)
        return trackNumbers.size
    }

    private fun getLayoutTrackNumber(rs: ResultSet): LayoutTrackNumber =
        LayoutTrackNumber(
            number = rs.getTrackNumber("number"),
            description = rs.getString("description").let(::TrackNumberDescription),
            state = rs.getEnum("state"),
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

    @Transactional fun save(item: LayoutTrackNumber): LayoutRowVersion<LayoutTrackNumber> = save(item, Unit)

    @Transactional
    override fun save(item: LayoutTrackNumber, saveParams: Unit): LayoutRowVersion<LayoutTrackNumber> {
        val id = item.id as? IntId ?: createId()

        // language=sql
        val sql =
            """
            insert into layout.track_number(layout_context_id,
                                            id,
                                            number,
                                            description,
                                            state,
                                            draft,
                                            cancelled,
                                            design_id,
                                            origin_design_id)
              values
                (:layout_context_id,
                 :id,
                 :number,
                 :description,
                 :state::layout.state,
                 :draft,
                 :cancelled,
                 :design_id,
                 :origin_design_id)
              on conflict (id, layout_context_id) do update
                set number = excluded.number,
                    description = excluded.description,
                    state = excluded.state,
                    cancelled = excluded.cancelled,
                    origin_design_id = excluded.origin_design_id
              returning id, design_id, draft, version;
        """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "number" to item.number,
                "description" to item.description,
                "state" to item.state.name,
                "draft" to item.isDraft,
                "cancelled" to item.isCancelled,
                "design_id" to item.contextData.designId?.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
            )
        jdbcTemplate.setUser()
        val response: LayoutRowVersion<LayoutTrackNumber> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to generate ID for new TrackNumber")
        logger.daoAccess(AccessType.INSERT, LayoutTrackNumber::class, response)
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
    ): Map<TrackNumber, List<LayoutRowVersion<LayoutTrackNumber>>> {
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
                jdbcTemplate.query<Pair<TrackNumber, LayoutRowVersion<LayoutTrackNumber>>>(sql, params) { rs, _ ->
                    val daoResponse = rs.getLayoutRowVersion<LayoutTrackNumber>("id", "design_id", "draft", "version")
                    val name = rs.getString("number").let(::TrackNumber)
                    name to daoResponse
                }
            // Ensure that the result contains all asked-for numbers, even if there are no matches
            numbers.associateWith { n -> found.filter { (number, _) -> number == n }.map { (_, v) -> v } }
        }
    }

    @Transactional
    fun savePlanItemId(id: IntId<LayoutTrackNumber>, branch: DesignBranch, planItemId: RatkoPlanItemId) {
        jdbcTemplate.setUser()
        savePlanItemIdInExistingTransaction(branch, id, planItemId)
    }

    @Transactional
    fun insertExternalId(id: IntId<LayoutTrackNumber>, branch: LayoutBranch, oid: Oid<LayoutTrackNumber>) {
        jdbcTemplate.setUser()
        insertExternalIdInExistingTransaction(branch, id, oid)
    }
}
