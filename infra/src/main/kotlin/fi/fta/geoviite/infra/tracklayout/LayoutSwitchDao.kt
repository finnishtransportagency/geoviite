package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getJointNumber
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getNullableDoubleArray
import fi.fta.geoviite.infra.util.getNullableEnumArray
import fi.fta.geoviite.infra.util.getNullableIntArray
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.setUser
import fi.fta.geoviite.infra.util.toDbId
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.plus
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val SWITCH_CACHE_SIZE = 10000L

@Suppress("SameParameterValue")
@Component
class LayoutSwitchDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutSwitch, NoParams>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_SWITCH,
        cacheEnabled,
        SWITCH_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutSwitch> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.switch_external_id",
        "layout.switch_external_id_version",
    ),
    IExternallyIdentifiedLayoutAssetDao<LayoutSwitch> {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LayoutSwitch>) = NoParams.instance

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<LayoutRowVersion<LayoutSwitch>> {
        val sql =
            """
                select id, design_id, draft, version
                from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where (:include_deleted = true or state_category != 'NOT_EXISTING')
            """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    fun fetchSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        val sql =
            """
                with
                  track_link as (
                    select distinct
                      ltve.location_track_id,
                      ltve.location_track_layout_context_id,
                      ltve.location_track_version,
                      np.switch_id,
                      np.switch_joint_number,
                      case
                        when np.node_id = edge.start_node_id then postgis.st_astext(postgis.st_startpoint(start_g.geometry))
                        when np.node_id = edge.end_node_id then postgis.st_astext(postgis.st_endpoint(end_g.geometry))
                      end as location
                      from layout.node_port np
                        inner join layout.edge edge on np.node_id = edge.start_node_id or np.node_id = edge.end_node_id
                        inner join layout.edge_segment start_segment on start_segment.edge_id = edge.id and start_segment.segment_index = 0
                        inner join layout.edge_segment end_segment on end_segment.edge_id = edge.id and end_segment.segment_index = edge.segment_count - 1
                        inner join layout.segment_geometry start_g on start_segment.geometry_id = start_g.id
                        inner join layout.segment_geometry end_g on end_segment.geometry_id = end_g.id
                        inner join layout.location_track_version_edge ltve on ltve.edge_id = edge.id
                        inner join layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
                                   on ltve.location_track_id = lt.id and
                                      ltve.location_track_layout_context_id::text =
                                      lt.layout_context_id::text and ltve.location_track_version = lt.version
                      where np.switch_id = :switch_id
                        and (np.node_id = edge.end_node_id or ltve.edge_index = 0)
                        and lt.state != 'DELETED'
                  )
                select
                  jv.switch_id,
                  jv.number,
                  jv.location_accuracy,
                  track_link.location_track_id,
                  postgis.st_x(track_link.location) as x,
                  postgis.st_y(track_link.location) as y
                  from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id) switch
                    left join layout.switch_version_joint jv
                    left join track_link on track_link.switch_id = jv.switch_id and track_link.switch_joint_number = jv.number
                         on switch.id = jv.switch_id
                           and switch.layout_context_id = jv.switch_layout_context_id
                           and switch.version = jv.switch_version
                  where switch.id = :switch_id
            """
                .trimIndent()
        val params =
            mapOf(
                "switch_id" to switchId.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )

        data class JointKey(val number: JointNumber, val locationAccuracy: LocationAccuracy?)

        val unmatchedJoints: MutableSet<JointKey> = mutableSetOf()
        val accurateMatches: MutableMap<JointKey, MutableMap<IntId<LocationTrack>, Point>> = mutableMapOf()

        jdbcTemplate.query(sql, params) { rs, _ ->
            val jointKey =
                JointKey(
                    number = JointNumber(rs.getInt("number")),
                    locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy"),
                )
            val locationTrackId = rs.getIntIdOrNull<LocationTrack>("location_track_id")
            if (locationTrackId != null) {
                val location = rs.getPoint("x", "y")
                accurateMatches.computeIfAbsent(jointKey) { mutableMapOf() }[locationTrackId] = location
            } else {
                unmatchedJoints.add(jointKey)
            }
        }
        return (unmatchedJoints + accurateMatches.keys).map { joint ->
            LayoutSwitchJointConnection(
                joint.number,
                accurateMatches[joint]?.entries?.map { e -> LayoutSwitchJointMatch(e.key, e.value) } ?: listOf(),
                joint.locationAccuracy,
            )
        }
    }

    @Transactional fun save(item: LayoutSwitch): LayoutRowVersion<LayoutSwitch> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: LayoutSwitch, params: NoParams): LayoutRowVersion<LayoutSwitch> {
        val id = item.id as? IntId ?: createId()

        val sql =
            """
                insert into
                  layout.switch(
                    layout_context_id,
                    id,
                    geometry_switch_id,
                    name,
                    switch_structure_id,
                    state_category,
                    trap_point,
                    owner_id,
                    operational_point_id,
                    draft,
                    design_asset_state,
                    design_id,
                    source,
                    draft_oid,
                    origin_design_id
                )
                values (
                  :layout_context_id,
                  :id,
                  :geometry_switch_id,
                  :name,
                  :switch_structure_id,
                  :state_category::layout.state_category,
                  :trap_point,
                  :owner_id,
                  :operational_point_id,
                  :draft,
                  :design_asset_state::layout.design_asset_state,
                  :design_id,
                  :source::layout.geometry_source,
                  :draft_oid,
                  :origin_design_id
                )
                on conflict (id, layout_context_id) do update set
                  geometry_switch_id = excluded.geometry_switch_id,
                  name = excluded.name,
                  switch_structure_id = excluded.switch_structure_id,
                  state_category = excluded.state_category,
                  trap_point = excluded.trap_point,
                  owner_id = excluded.owner_id,
                  operational_point_id = excluded.operational_point_id,
                  design_asset_state = excluded.design_asset_state,
                  source = excluded.source,
                  draft_oid = excluded.draft_oid,
                  origin_design_id = excluded.origin_design_id
                returning id, design_id, draft, version
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val response: LayoutRowVersion<LayoutSwitch> =
            jdbcTemplate.queryForObject(
                sql,
                mapOf(
                    "layout_context_id" to item.layoutContext.toSqlString(),
                    "id" to id.intValue,
                    "geometry_switch_id" to item.sourceId?.let(::toDbId)?.intValue,
                    "name" to item.name,
                    "switch_structure_id" to item.switchStructureId.intValue,
                    "state_category" to item.stateCategory.name,
                    "trap_point" to item.trapPoint,
                    "owner_id" to item.ownerId?.intValue,
                    "operational_point_id" to item.operationalPointId?.intValue,
                    "draft" to item.isDraft,
                    "design_asset_state" to item.designAssetState?.name,
                    "design_id" to item.contextData.designId?.intValue,
                    "source" to item.source.name,
                    "draft_oid" to item.draftOid?.toString(),
                    "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
                ),
            ) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to save switch")
        if (item.joints.isNotEmpty()) upsertJoints(response, item.joints)
        logger.daoAccess(INSERT, LayoutSwitch::class, response)
        return response
    }

    private fun upsertJoints(switchVersion: LayoutRowVersion<LayoutSwitch>, joints: List<LayoutSwitchJoint>) {
        if (joints.isNotEmpty()) {
            val sql =
                """
                    insert into layout.switch_version_joint(
                      switch_id,
                      switch_layout_context_id,
                      switch_version,
                      number,
                      location,
                      location_accuracy,
                      role
                      )
                    values (
                      :switch_id,
                      :switch_layout_context_id,
                      :switch_version,
                      :number,
                      postgis.st_setsrid(postgis.st_point(:location_x, :location_y), :srid),
                      :location_accuracy::common.location_accuracy,
                      :role::common.switch_joint_role
                      )
                """
                    .trimIndent()
            val params =
                joints
                    .map { joint ->
                        mapOf(
                            "switch_id" to switchVersion.id.intValue,
                            "switch_layout_context_id" to switchVersion.context.toSqlString(),
                            "switch_version" to switchVersion.version,
                            "number" to joint.number.intValue,
                            "location_x" to joint.location.x,
                            "location_y" to joint.location.y,
                            "srid" to LAYOUT_SRID.code,
                            "location_accuracy" to joint.locationAccuracy?.name,
                            "role" to joint.role.name,
                        )
                    }
                    .toTypedArray()
            jdbcTemplate.batchUpdate(sql, params)
        }
    }

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<LayoutSwitch>>
    ): Map<LayoutRowVersion<LayoutSwitch>, LayoutSwitch> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
                select
                  sv.id,
                  sv.version,
                  sv.design_id,
                  sv.draft,
                  sv.design_asset_state,
                  sv.geometry_switch_id,
                  sv.name,
                  sv.switch_structure_id,
                  sv.state_category,
                  sv.trap_point,
                  sv.owner_id,
                  sv.source,
                  sv.origin_design_id,
                  sv.draft_oid,
                  sv.operational_point_id,
                  coalesce(joint_numbers, '{}') as joint_numbers,
                  coalesce(joint_roles, '{}') as joint_roles,
                  coalesce(joint_x_values, '{}') as joint_x_values,
                  coalesce(joint_y_values, '{}') as joint_y_values,
                  coalesce(joint_location_accuracies, '{}') as joint_location_accuracies
                from layout.switch_version sv
                  inner join lateral
                    (
                      select
                        unnest(:ids) id,
                        unnest(:layout_context_ids) layout_context_id,
                        unnest(:versions) version
                    ) args on args.id = sv.id and args.layout_context_id = sv.layout_context_id and args.version = sv.version
                  left join lateral (
                    select
                      array_agg(jv.number order by jv.number) as joint_numbers,
                      array_agg(jv.role order by jv.number) as joint_roles,
                      array_agg(postgis.st_x(jv.location) order by jv.number) as joint_x_values,
                      array_agg(postgis.st_y(jv.location) order by jv.number) as joint_y_values,
                      array_agg(jv.location_accuracy order by jv.number) as joint_location_accuracies
                    from layout.switch_version_joint jv
                      where jv.switch_id = sv.id
                        and jv.switch_layout_context_id = sv.layout_context_id
                        and jv.switch_version = sv.version
                  ) jv on (true)
                where sv.deleted = false
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getLayoutSwitch(rs) }
            .associateBy { s -> s.getVersionOrThrow() }
            .also { logger.daoAccess(FETCH, LayoutSwitch::class, versions) }
    }

    override fun preloadCache(): Int {
        val sql =
            """
                select
                  s.id,
                  s.version,
                  s.design_id,
                  s.draft,
                  s.design_asset_state,
                  s.geometry_switch_id,
                  s.name,
                  s.switch_structure_id,
                  s.state_category,
                  s.trap_point,
                  s.owner_id,
                  s.source,
                  joint_numbers,
                  joint_roles,
                  joint_x_values,
                  joint_y_values,
                  joint_location_accuracies,
                  s.draft_oid,
                  s.origin_design_id,
                  s.operational_point_id
                from layout.switch s
                  left join lateral
                    (select coalesce(array_agg(jv.number order by jv.number), '{}') as joint_numbers,
                            coalesce(array_agg(jv.role order by jv.number), '{}') as joint_roles,
                            coalesce(array_agg(postgis.st_x(jv.location) order by jv.number), '{}') as joint_x_values,
                            coalesce(array_agg(postgis.st_y(jv.location) order by jv.number), '{}') as joint_y_values,
                            coalesce(array_agg(jv.location_accuracy order by jv.number), '{}') as joint_location_accuracies
                     from layout.switch_version_joint jv
                     where jv.switch_id = s.id
                       and jv.switch_layout_context_id = s.layout_context_id
                       and jv.switch_version = s.version
                    ) jv on (true)
            """
                .trimIndent()

        val switches =
            jdbcTemplate
                .query(sql) { rs, _ -> getLayoutSwitch(rs) }
                .associateBy { switch -> requireNotNull(switch.version) }

        logger.daoAccess(FETCH, LayoutSwitch::class, switches.keys)
        cache.putAll(switches)

        return switches.size
    }

    private fun getLayoutSwitch(rs: ResultSet): LayoutSwitch {
        val switchStructureId = rs.getIntId<SwitchStructure>("switch_structure_id")
        return LayoutSwitch(
            sourceId = rs.getIntIdOrNull("geometry_switch_id"),
            name = SwitchName(rs.getString("name")),
            switchStructureId = switchStructureId,
            stateCategory = rs.getEnum("state_category"),
            joints =
                parseJoints(
                    numbers = rs.getNullableIntArray("joint_numbers"),
                    jointRoles = rs.getNullableEnumArray<SwitchJointRole>("joint_roles"),
                    xValues = rs.getNullableDoubleArray("joint_x_values"),
                    yValues = rs.getNullableDoubleArray("joint_y_values"),
                    accuracies = rs.getNullableEnumArray<LocationAccuracy>("joint_location_accuracies"),
                ),
            trapPoint = rs.getBooleanOrNull("trap_point"),
            ownerId = rs.getIntId("owner_id"),
            source = rs.getEnum("source"),
            draftOid = rs.getOidOrNull("draft_oid"),
            operationalPointId = rs.getIntIdOrNull("operational_point_id"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )
    }

    private fun parseJoints(
        numbers: List<Int?>,
        jointRoles: List<SwitchJointRole?>,
        xValues: List<Double?>,
        yValues: List<Double?>,
        accuracies: List<LocationAccuracy?>,
    ): List<LayoutSwitchJoint> {
        require(numbers.size == xValues.size && numbers.size == yValues.size && numbers.size == accuracies.size) {
            "Joint piece arrays should be the same size: numbers=${numbers.size} xValues=${xValues.size} yValues=${yValues.size} accuracies=${accuracies.size}"
        }
        return (0..numbers.lastIndex).mapNotNull { i ->
            numbers[i]?.let(::JointNumber)?.let { jointNumber ->
                LayoutSwitchJoint(
                    number = jointNumber,
                    role = requireNotNull(jointRoles[i]) { "Joint should have a role: number=$jointNumber" },
                    location =
                        Point(
                            requireNotNull(xValues[i]) { "Joint should have an x-coordinate: number=$jointNumber" },
                            requireNotNull(yValues[i]) { "Joint should have an y-coordinate: number=$jointNumber" },
                        ),
                    locationAccuracy = accuracies[i],
                )
            }
        }
    }

    data class LocationTrackIdentifiers(
        val rowVersion: LayoutRowVersion<LocationTrack>,
        val externalId: Oid<LocationTrack>?,
    ) {
        val id: IntId<LocationTrack>
            get() = rowVersion.id
    }

    fun findLocationTracksLinkedToSwitch(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<LocationTrackIdentifiers> =
        findLocationTracksLinkedToSwitches(layoutContext, listOf(switchId))[switchId] ?: listOf()

    fun findLocationTracksLinkedToSwitches(
        layoutContext: LayoutContext,
        switchIds: List<IntId<LayoutSwitch>>,
    ): Map<IntId<LayoutSwitch>, List<LocationTrackIdentifiers>> {
        if (switchIds.isEmpty()) return emptyMap()
        val sql =
            """
            select distinct on (ltv_s.location_track_id, ltv_s.switch_id)
              lt.id,
              lt.layout_context_id,
              lt.version,
              ltv_s.switch_id,
              ext_id.external_id
              from layout.location_track_version_switch_view ltv_s
                inner join layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
                           on ltv_s.location_track_id = lt.id
                             and ltv_s.location_track_layout_context_id = lt.layout_context_id
                             and ltv_s.location_track_version = lt.version
                left join layout.location_track_external_id ext_id
                          on ltv_s.location_track_id = ext_id.id
                            and ext_id.layout_context_id = layout.layout_context_id(:design_id, false)
              where ltv_s.switch_id in (:switch_ids)
                and lt.state != 'DELETED';
            """
                .trimIndent()
        val params =
            mapOf(
                "switch_ids" to switchIds.map { it.intValue },
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                rs.getIntId<LayoutSwitch>("switch_id") to
                    LocationTrackIdentifiers(
                        rowVersion = rs.getLayoutRowVersion("id", "layout_context_id", "version"),
                        externalId = rs.getOidOrNull("external_id"),
                    )
            }
            .groupBy({ it.first }, { it.second })
            .also { logger.daoAccess(FETCH, "LocationTracks linked to switch", switchIds) }
    }

    fun findLocationTracksLinkedToSwitchAtMoment(
        layoutBranch: LayoutBranch,
        switchId: IntId<LayoutSwitch>,
        moment: Instant,
    ): List<LocationTrackIdentifiers> =
        findLocationTracksLinkedToSwitchesAtMoment(layoutBranch, listOf(switchId), moment)[switchId] ?: listOf()

    fun findLocationTracksLinkedToSwitchesAtMoment(
        layoutBranch: LayoutBranch,
        moment: Instant,
    ): Map<IntId<LayoutSwitch>, List<LocationTrackIdentifiers>> =
        findLocationTracksLinkedToSwitchesAtMoment(layoutBranch, moment)

    fun findLocationTracksLinkedToSwitchesAtMoment(
        layoutBranch: LayoutBranch,
        switchIds: Collection<IntId<LayoutSwitch>>?,
        moment: Instant,
    ): Map<IntId<LayoutSwitch>, List<LocationTrackIdentifiers>> {
        val sql =
            """
            select distinct
              lt_s.switch_id,
              lt.id,
              lt.layout_context_id,
              lt.version,
              location_track_external_id.external_id
              from layout.location_track_at(:moment) lt
                inner join layout.location_track_version_switch_view as lt_s
                          on lt.id = lt_s.location_track_id
                            and lt.layout_context_id = lt_s.location_track_layout_context_id
                            and lt.version = lt_s.location_track_version
                left join layout.location_track_external_id
                          on lt.id = location_track_external_id.id
                            and lt.layout_context_id = location_track_external_id.layout_context_id
              where not lt.draft
                and (lt.design_id is null or lt.design_id = :design_id::int)
                and not
                (:design_id::int is not null
                  and lt.design_id is null
                  and exists
                   (select *
                      from layout.location_track_at(:moment) overrider_lt
                      where overrider_lt.id = lt.id
                        and not overrider_lt.draft
                        and overrider_lt.design_id = :design_id
                   )
                  )
                and (:switch_ids::int[] is null or lt_s.switch_id = any(:switch_ids))
                and (not lt_s.is_outer_link or lt_s.switch_joint_role = 'MAIN')
            """
                .trimIndent()

        val params =
            mapOf(
                "design_id" to layoutBranch.designId?.intValue,
                "switch_ids" to switchIds?.map { it.intValue }?.toTypedArray(),
                "moment" to Timestamp.from(moment),
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val switchId = rs.getIntId<LayoutSwitch>("switch_id")
                val trackIds =
                    LocationTrackIdentifiers(
                        rowVersion = rs.getLayoutRowVersion("id", "layout_context_id", "version"),
                        externalId = rs.getOidOrNull("external_id"),
                    )
                switchId to trackIds
            }
            .groupBy({ it.first }, { it.second })
            .also { logger.daoAccess(FETCH, "LocationTracks linked to switch at moment", switchIds ?: "all") }
    }

    fun findNameDuplicates(
        context: LayoutContext,
        names: List<SwitchName>,
    ): Map<SwitchName, List<LayoutRowVersion<LayoutSwitch>>> =
        findFieldDuplicates(context, names, "name", "state_category != 'NOT_EXISTING'") { rs ->
            rs.getString("name").let(::SwitchName)
        }

    @Transactional(readOnly = true)
    fun findSwitchesRelatedToOperationalPoint(
        context: LayoutContext,
        operationalPointId: IntId<OperationalPoint>,
    ): List<SwitchWithOperationalPointPolygonInclusions> {
        val withinPolygon = getSwitchJointsWithinOperationalPointArea(context, operationalPointId)
        val around = getSwitchesAndOperationalPointInclusions(context, operationalPointId, withinPolygon)

        return (withinPolygon.map { it.switchId to it.operationalPoints + operationalPointId } +
                around.map { it.switchId to it.withinPolygon })
            .groupBy({ (switchId) -> switchId }, { (_, operationalPoints) -> operationalPoints })
            .map { (switchId, operationalPoints) ->
                SwitchWithOperationalPointPolygonInclusions(switchId, operationalPoints.flatten().distinct())
            }
    }

    private fun getSwitchJointsWithinOperationalPointArea(
        context: LayoutContext,
        id: IntId<OperationalPoint>,
    ): List<SwitchJointWithOverlappingOperationalPoints> {
        val sql =
            """
                with other_operational_points_overlapping_query_point as materialized (
                  select other_point.id, other_point.polygon
                    from layout.operational_point_in_layout_context(:publication_state::layout.publication_state,
                                                                    :design_id) query_point
                      join layout.operational_point_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id) other_point
                           on postgis.st_intersects(query_point.polygon, other_point.polygon) and query_point.id != other_point.id
                    where query_point.id = :operational_point_id
                )
                select switch_version_joint.switch_id, switch_version_joint.number, overlapping_operational_point_ids
                  from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id) switch
                    join layout.switch_version_joint
                         on switch_version_joint.switch_id = switch.id
                           and switch_version_joint.switch_layout_context_id = switch.layout_context_id
                           and switch_version_joint.switch_version = switch.version
                    join (
                    select *
                      from layout.operational_point_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id) operational_point
                      where operational_point.id = :operational_point_id
                  ) operational_point
                         on postgis.st_intersects(switch_version_joint.location, operational_point.polygon)
                    cross join lateral
                    (select coalesce(array_agg(op.id), '{}') as overlapping_operational_point_ids
                       from other_operational_points_overlapping_query_point op
                       where postgis.st_intersects(switch_version_joint.location, op.polygon))
                where switch.state_category != 'NOT_EXISTING';
            """
                .trimIndent()
        val params =
            mapOf(
                "operational_point_id" to id.intValue,
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            SwitchJointWithOverlappingOperationalPoints(
                rs.getIntId("switch_id"),
                rs.getJointNumber("number"),
                rs.getIntIdArray("overlapping_operational_point_ids"),
            )
        }
    }

    private fun getSwitchesAndOperationalPointInclusions(
        context: LayoutContext,
        operationalPointId: IntId<OperationalPoint>,
        jointsWithinPolygon: List<SwitchJointWithOverlappingOperationalPoints>,
    ): List<SwitchWithOperationalPointPolygonInclusions> {
        val sql =
            """
                select switch.id, operational_point_ids
                  from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id) switch
                    cross join lateral (
                    select coalesce(array_agg(distinct operational_point.id), '{}') as operational_point_ids
                      from layout.operational_point_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id) operational_point
                        join layout.switch_version_joint
                             on postgis.st_intersects(operational_point.polygon, switch_version_joint.location)
                      where switch_version_joint.switch_id = switch.id
                        and switch_version_joint.switch_layout_context_id = switch.layout_context_id
                        and switch_version_joint.switch_version = switch.version
                        and not exists (
                        select *
                          from unnest(:switch_joint_switch_ids, :switch_joint_switch_numbers) e(switch_id, number)
                          where switch_version_joint.switch_id = e.switch_id
                            and switch_version_joint.number = e.number
                      )
                    )
                  where ((switch.operational_point_id = :operational_point_id
                          or switch.id = any (:switch_ids)))
                    and switch.state_category != 'NOT_EXISTING';
            """
                .trimIndent()
        val params =
            mapOf(
                "operational_point_id" to operationalPointId.intValue,
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
                "switch_joint_switch_ids" to jointsWithinPolygon.map { it.switchId.intValue }.toTypedArray(),
                "switch_joint_switch_numbers" to jointsWithinPolygon.map { it.jointNumber.intValue }.toTypedArray(),
                "switch_ids" to jointsWithinPolygon.map { it.switchId.intValue }.distinct().toTypedArray(),
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            SwitchWithOperationalPointPolygonInclusions(rs.getIntId("id"), rs.getIntIdArray("operational_point_ids"))
        }
    }

    fun findSwitchesNearTrack(
        branch: LayoutBranch,
        trackVersion: LayoutRowVersion<LocationTrack>,
        maxDistance: Double = 1.0,
    ): List<IntId<LayoutSwitch>> {
        val sql =
            """
                select distinct switch.id as switch_id
                  from layout.edge_segment segment
                    inner join layout.location_track_version_edge lt_edge on lt_edge.edge_id = segment.edge_id
                    join layout.segment_geometry on segment.geometry_id = segment_geometry.id
                    join layout.switch_version_joint jv on
                      postgis.st_contains(postgis.st_expand(segment_geometry.bounding_box, :dist), jv.location)
                      and postgis.st_dwithin(segment_geometry.geometry, jv.location, :dist)
                    inner join layout.switch_in_layout_context('DRAFT', :design_id) switch
                      on jv.switch_id = switch.id
                        and jv.switch_layout_context_id = switch.layout_context_id
                        and jv.switch_version = switch.version
                  where lt_edge.location_track_id = :location_track_id
                    and lt_edge.location_track_layout_context_id = :location_track_layout_context_id
                    and lt_edge.location_track_version = :location_track_version
                    and switch.state_category != 'NOT_EXISTING';
            """
                .trimIndent()
        val params =
            mapOf(
                "location_track_id" to trackVersion.id.intValue,
                "location_track_layout_context_id" to trackVersion.context.toSqlString(),
                "location_track_version" to trackVersion.version,
                "dist" to maxDistance,
                "design_id" to branch.designId?.intValue,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> rs.getIntId<LayoutSwitch>("switch_id") }
            .also { results -> logger.daoAccess(FETCH, "Switches near alignment", results) }
    }

    @Transactional
    fun savePlanItemId(id: IntId<LayoutSwitch>, branch: DesignBranch, planItemId: RatkoPlanItemId) {
        jdbcTemplate.setUser()
        savePlanItemIdInExistingTransaction(branch, id, planItemId)
    }

    @Transactional
    fun insertExternalId(id: IntId<LayoutSwitch>, branch: LayoutBranch, oid: Oid<LayoutSwitch>) {
        jdbcTemplate.setUser()
        insertExternalIdInExistingTransaction(branch, id, oid)
    }
}

private data class SwitchJointWithOverlappingOperationalPoints(
    val switchId: IntId<LayoutSwitch>,
    val jointNumber: JointNumber,
    val operationalPoints: List<IntId<OperationalPoint>>,
)
