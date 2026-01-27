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
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getTrackNumber
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val TRACK_NUMBER_CACHE_SIZE = 1000L

@Component
class LayoutTrackNumberDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutTrackNumber, NoParams>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER,
        cacheEnabled,
        TRACK_NUMBER_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutTrackNumber> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.track_number_external_id",
        "layout.track_number_external_id",
    ),
    IExternallyIdentifiedLayoutAssetDao<LayoutTrackNumber> {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LayoutTrackNumber>) = NoParams.instance

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null)

    @Transactional(readOnly = true)
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

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<LayoutTrackNumber>>
    ): Map<LayoutRowVersion<LayoutTrackNumber>, LayoutTrackNumber> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
                select
                  tn.id,
                  tn.version,
                  tn.design_id,
                  tn.draft,
                  tn.design_asset_state,
                  tn.number,
                  tn.description,
                  tn.state,
                  -- Track number reference line identity never changes, so any instance whatsoever is fine
                  (select id from layout.reference_line_version rl where rl.track_number_id = tn.id limit 1) reference_line_id,
                  tn.origin_design_id
                from layout.track_number_version tn
                  inner join lateral
                    (
                      select
                        unnest(:ids) id,
                        unnest(:layout_context_ids) layout_context_id,
                        unnest(:versions) version
                    ) args on args.id = tn.id and args.layout_context_id = tn.layout_context_id and args.version = tn.version
                  where tn.deleted = false
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getLayoutTrackNumber(rs) }
            .associateBy { tn -> tn.getVersionOrThrow() }
            .also { logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, versions) }
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
                  tn.design_asset_state,
                  -- Track number reference line identity never changes, so any instance whatsoever is fine
                  (select id from layout.reference_line_version rl where rl.track_number_id = tn.id limit 1) reference_line_id,
                  tn.origin_design_id
                from layout.track_number tn
                order by tn.id
            """
                .trimIndent()

        val trackNumbers =
            jdbcTemplate
                .query(sql) { rs, _ -> getLayoutTrackNumber(rs) }
                .associateBy { trackNumber -> requireNotNull(trackNumber.version) }

        logger.daoAccess(AccessType.FETCH, LayoutTrackNumber::class, trackNumbers.keys)
        cache.putAll(trackNumbers)

        return trackNumbers.size
    }

    private fun getLayoutTrackNumber(rs: ResultSet): LayoutTrackNumber =
        LayoutTrackNumber(
            number = rs.getTrackNumber("number"),
            description = rs.getString("description").let(::TrackNumberDescription),
            state = rs.getEnum("state"),

            // TODO: GVT-2935 This should be non-null but we have tests that produce broken data
            // To fix this, we could use a similar model as LocationTrack+LocationTrackGeometry
            // There, they are save always as one, all the way from DAO.save
            referenceLineId = rs.getIntIdOrNull("reference_line_id"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )

    @Transactional
    fun save(item: LayoutTrackNumber): LayoutRowVersion<LayoutTrackNumber> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: LayoutTrackNumber, params: NoParams): LayoutRowVersion<LayoutTrackNumber> {
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
                                                design_asset_state,
                                                design_id,
                                                origin_design_id)
                  values
                    (:layout_context_id,
                     :id,
                     :number,
                     :description,
                     :state::layout.state,
                     :draft,
                     :design_asset_state::layout.design_asset_state,
                     :design_id,
                     :origin_design_id)
                  on conflict (id, layout_context_id) do update
                    set number = excluded.number,
                        description = excluded.description,
                        state = excluded.state,
                        design_asset_state = excluded.design_asset_state,
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
                "design_asset_state" to item.designAssetState?.name,
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

    fun fetchTrackNumberNames(layoutBranch: LayoutBranch): List<TrackNumberAndChangeTime> {
        // language=sql
        val sql =
            """
            select id, number, change_time
              from (
                select
                  id,
                  change_time,
                  number,
                      lag(number) over (partition by id order by change_time, design_id is not null, version) as prev_number
                  from (
                    select id, number, change_time, version, design_id
                      from layout.track_number_version tn
                      where not draft
                        and case
                              when :design_id::int is null then design_id is null
                              else (design_id = :design_id and design_asset_state != 'CANCELLED')
                                or (design_id is null
                                  and not exists (
                                    select *
                                      from layout.track_number_version overrider
                                      where overrider.design_id = :design_id
                                        and overrider.id = tn.id
                                        and not overrider.draft
                                        and not overrider.design_asset_state = 'CANCELLED'
                                        and not overrider.deleted
                                        and overrider.change_time <= tn.change_time
                                        and (overrider.expiry_time is null or overrider.expiry_time > tn.change_time)
                                  ))
                            end
                    union all
                    -- cancelling design rows returns us to the state in main, with the change occurring at the time of
                    -- the deletion
                    select
                      main_tn.id,
                      main_tn.number,
                      design_cancellation.change_time,
                      design_cancellation.version,
                      design_cancellation.design_id
                      from layout.track_number_version main_tn
                        join layout.track_number_version design_cancellation on main_tn.id = design_cancellation.id
                        and main_tn.change_time <= design_cancellation.change_time
                        and (main_tn.expiry_time is null or main_tn.expiry_time > design_cancellation.change_time)
                      where :design_id::int is not null
                        and not main_tn.draft
                        and not design_cancellation.draft
                        and main_tn.design_id is null
                        and design_cancellation.design_id = :design_id
                        and (design_cancellation.design_asset_state = 'CANCELLED' or design_cancellation.deleted)
                  ) all_versions
              ) change_order
              where number is distinct from prev_number
              order by id, change_time;
                          """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("design_id" to layoutBranch.designId?.intValue)) { rs, _ ->
                TrackNumberAndChangeTime(rs.getIntId("id"), rs.getTrackNumber("number"), rs.getInstant("change_time"))
            }
            .also { logger.daoAccess(AccessType.FETCH, "track_number_version") }
    }

    fun findNumberDuplicates(
        context: LayoutContext,
        numbers: List<TrackNumber>,
    ): Map<TrackNumber, List<LayoutRowVersion<LayoutTrackNumber>>> =
        findFieldDuplicates(context, numbers, "number") { rs -> rs.getString("number").let(::TrackNumber) }

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
