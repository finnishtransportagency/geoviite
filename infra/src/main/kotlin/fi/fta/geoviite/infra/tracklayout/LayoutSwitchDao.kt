package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val SWITCH_CACHE_SIZE = 10000L

@Suppress("SameParameterValue")
@Transactional(readOnly = true)
@Component
class LayoutSwitchDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LayoutSwitch>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_SWITCH,
        cacheEnabled,
        SWITCH_CACHE_SIZE,
    ),
    IExternalIdDao<LayoutSwitch> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.switch_external_id",
        "layout.switch_external_id_version",
    ) {

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

    fun fetchSegmentSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
    ): List<LayoutSwitchJointConnection> {
        val sql =
            """
            select number, location_accuracy, location_track_id, postgis.st_x(point) x, postgis.st_y(point) y
              from layout.switch_in_layout_context(:publication_state::layout.publication_state,
                                                   :design_id) switch
                join layout.switch_joint_version jv on switch.id = jv.switch_id
                  and switch.layout_context_id = jv.switch_layout_context_id
                  and switch.version = jv.switch_version
                left join (
                  select lt.id as location_track_id, *
                    from layout.segment_version
                      join
                        (select *
                         from layout.location_track_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id) lt
                         where lt.state != 'DELETED') lt
                           on lt.alignment_id = segment_version.alignment_id
                             and lt.alignment_version = segment_version.alignment_version 
                      join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                      cross join lateral
                      (select switch_start_joint_number as number, postgis.st_startpoint(geometry) as point
                         where switch_start_joint_number is not null
                       union all
                         select switch_end_joint_number, postgis.st_endpoint(geometry)
                           where switch_end_joint_number is not null) p
                  where switch_id = :switch_id
              ) segment_joint using (number)
            where state_category != 'NOT_EXISTING' and switch.id = :switch_id;
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

    @Transactional
    override fun save(item: LayoutSwitch): LayoutRowVersion<LayoutSwitch> {
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
                draft,
                cancelled,
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
              :draft,
              :cancelled,
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
              cancelled = excluded.cancelled,
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
                    "draft" to item.isDraft,
                    "cancelled" to item.isCancelled,
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
              insert into layout.switch_joint_version(
                switch_id,
                switch_layout_context_id,
                switch_version,
                number, 
                location, 
                location_accuracy
                )
              values (
                :switch_id,
                :switch_layout_context_id,
                :switch_version,
                :number, 
                postgis.st_setsrid(postgis.st_point(:location_x, :location_y), :srid), 
                :location_accuracy::common.location_accuracy
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
                        )
                    }
                    .toTypedArray()
            jdbcTemplate.batchUpdate(sql, params)
        }
    }

    override fun fetchInternal(version: LayoutRowVersion<LayoutSwitch>): LayoutSwitch {
        val sql =
            """
            select 
              sv.id,
              sv.version,
              sv.design_id,
              sv.draft,
              sv.cancelled,
              sv.geometry_switch_id, 
              sv.name, 
              sv.switch_structure_id,
              sv.state_category,
              sv.trap_point,
              sv.owner_id,
              sv.source,
              origin_design_id,
              sv.draft_oid,
              exists(select * from layout.switch official_sv
                     where official_sv.id = sv.id
                       and (official_sv.design_id is null or official_sv.design_id = sv.design_id)
                       and not official_sv.draft) as has_official,
              coalesce(joint_numbers, '{}') as joint_numbers,
              coalesce(joint_types, '{}') as joint_types,
              coalesce(joint_x_values, '{}') as joint_x_values,
              coalesce(joint_y_values, '{}') as joint_y_values,
              coalesce(joint_location_accuracies, '{}') as joint_location_accuracies
            from layout.switch_version sv
              left join lateral (
                select 
                  array_agg(jv.number order by jv.number) as joint_numbers,
                  array_agg(jv.type order by jv.number) as joint_types,
                  array_agg(postgis.st_x(jv.location) order by jv.number) as joint_x_values,
                  array_agg(postgis.st_y(jv.location) order by jv.number) as joint_y_values,
                  array_agg(jv.location_accuracy order by jv.number) as joint_location_accuracies
                from layout.switch_joint_version jv
                  where jv.switch_id = sv.id
                    and jv.switch_layout_context_id = sv.layout_context_id
                    and jv.switch_version = sv.version
              ) jv on (true)
            where sv.id = :id and sv.layout_context_id = :layout_context_id and sv.version = :version
              and sv.deleted = false
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutSwitch(rs) }).also {
            logger.daoAccess(FETCH, LayoutSwitch::class, version)
        }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select 
              s.id,
              s.version,
              s.design_id,
              s.draft,
              s.cancelled,
              s.geometry_switch_id, 
              s.name, 
              s.switch_structure_id,
              s.state_category,
              s.trap_point,
              s.owner_id,
              s.source,
              joint_numbers,
              joint_types,
              joint_x_values,
              joint_y_values,
              joint_location_accuracies,
              s.draft_oid,
              exists(select * from layout.switch official_sv
                     where official_sv.id = s.id
                       and (official_sv.design_id is null or official_sv.design_id = s.design_id)
                       and not official_sv.draft) as has_official,
              s.origin_design_id
            from layout.switch s
              left join lateral
                (select coalesce(array_agg(jv.number order by jv.number), '{}') as joint_numbers,
                        coalesce(array_agg(jv.type order by jv.number), '{}') as joint_types,
                        coalesce(array_agg(postgis.st_x(jv.location) order by jv.number), '{}') as joint_x_values,
                        coalesce(array_agg(postgis.st_y(jv.location) order by jv.number), '{}') as joint_y_values,
                        coalesce(array_agg(jv.location_accuracy order by jv.number), '{}') as joint_location_accuracies
                 from layout.switch_joint_version jv
                 where jv.switch_id = s.id
                   and jv.switch_layout_context_id = s.layout_context_id
                   and jv.switch_version = s.version
                ) jv on (true)
        """
                .trimIndent()

        val switches = jdbcTemplate.query(sql) { rs, _ -> getLayoutSwitch(rs) }.associateBy(LayoutSwitch::version)
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
                    jointTypes = rs.getNullableEnumArray<SwitchJointType>("joint_types"),
                    xValues = rs.getNullableDoubleArray("joint_x_values"),
                    yValues = rs.getNullableDoubleArray("joint_y_values"),
                    accuracies = rs.getNullableEnumArray<LocationAccuracy>("joint_location_accuracies"),
                ),
            trapPoint = rs.getBooleanOrNull("trap_point"),
            ownerId = rs.getIntIdOrNull("owner_id"),
            source = rs.getEnum("source"),
            draftOid = rs.getOidOrNull("draft_oid"),
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
    }

    private fun parseJoints(
        numbers: List<Int?>,
        jointTypes: List<SwitchJointType?>,
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
                    type = requireNotNull(jointTypes[i]) { "Joint should have a type: number=$jointNumber" },
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
                select switch_id,
                  (location_track).id,
                  (location_track).design_id,
                  (location_track).draft,
                  (location_track).version,
                  external_id
                  from (
                    select topology_start_switch_id as switch_id, location_track
                      from layout.location_track
                      where topology_start_switch_id = any (array [:switch_ids])
                    union
                    select topology_end_switch_id as switch_id, location_track
                      from layout.location_track
                      where topology_end_switch_id = any (array [:switch_ids])
                    union
                    select switch_id, location_track
                      from layout.location_track
                        join layout.segment_version using (alignment_id, alignment_version)
                      where switch_id = any (array [:switch_ids])
                  ) location_track
                    cross join lateral layout.location_track_is_in_layout_context(:publication_state::layout.publication_state,
                                                                                  :design_id, location_track)
                    left join layout.location_track_external_id ext_id
                      on (location_track).id = ext_id.id
                        and ext_id.layout_context_id = layout.layout_context_id(:design_id, false);
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
                        rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        externalId = rs.getOidOrNull("external_id"),
                    )
            }
            .groupBy({ it.first }, { it.second })
            .also { logger.daoAccess(FETCH, "LocationTracks linked to switch", switchIds) }
    }

    fun findLocationTracksLinkedToSwitchAtMoment(
        layoutBranch: LayoutBranch,
        switchId: IntId<LayoutSwitch>,
        topologyJointNumber: JointNumber,
        moment: Instant,
    ): List<LocationTrackIdentifiers> {
        assertMainBranch(layoutBranch)
        val sql =
            """ 
            select distinct
              location_track.id,
              location_track.design_id,
              location_track.draft,
              location_track.version,
              location_track_external_id.external_id
            from layout.switch_at(:moment) switch
              inner join layout.location_track_at(:moment) location_track on not location_track.draft
              inner join layout.segment_version segment 
                on segment.alignment_id = location_track.alignment_id 
                  and segment.alignment_version = location_track.alignment_version
              left join layout.location_track_external_id
                on location_track.id = location_track_external_id.id
                  and location_track.layout_context_id = location_track_external_id.layout_context_id
            where switch.id = :switch_id 
                and (segment.switch_id = :switch_id
                  or (location_track.topology_start_switch_id = :switch_id 
                    and location_track.topology_start_switch_joint_number = :topology_joint_number
                  )
                  or (location_track.topology_end_switch_id = :switch_id
                    and location_track.topology_end_switch_joint_number = :topology_joint_number
                  )
              )
        """
                .trimIndent()

        val params =
            mapOf(
                "switch_id" to switchId.intValue,
                "topology_joint_number" to topologyJointNumber.intValue,
                "moment" to Timestamp.from(moment),
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                LocationTrackIdentifiers(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    externalId = rs.getOidOrNull("external_id"),
                )
            }
            .also { logger.daoAccess(FETCH, "LocationTracks linked to switch at moment", switchId) }
    }

    fun findNameDuplicates(
        context: LayoutContext,
        names: List<SwitchName>,
    ): Map<SwitchName, List<LayoutRowVersion<LayoutSwitch>>> {
        return if (names.isEmpty()) {
            emptyMap()
        } else {
            val sql =
                """
                select id, design_id, draft, version, name
                from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where name in (:names)
                  and state_category != 'NOT_EXISTING'
            """
                    .trimIndent()
            val params =
                mapOf(
                    "names" to names,
                    "publication_state" to context.state.name,
                    "design_id" to context.branch.designId?.intValue,
                )
            val found =
                jdbcTemplate.query(sql, params) { rs, _ ->
                    val version = rs.getLayoutRowVersion<LayoutSwitch>("id", "design_id", "draft", "version")
                    val name = rs.getString("name").let(::SwitchName)
                    name to version
                }
            // Ensure that the result contains all asked-for names, even if there are no matches
            names
                .associateWith { n -> found.filter { (name, _) -> name == n }.map { (_, v) -> v } }
                .also { dups -> logger.daoAccess(FETCH, "Switch name duplicates", dups.keys) }
        }
    }

    fun findSwitchesNearAlignment(
        branch: LayoutBranch,
        alignmentVersion: RowVersion<LayoutAlignment>,
        maxDistance: Double = 1.0,
    ): List<IntId<LayoutSwitch>> {
        val sql =
            """
            select distinct switch.id as switch_id
              from layout.segment_version
                join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                join layout.switch_joint_version jv on
                  postgis.st_contains(postgis.st_expand(segment_geometry.bounding_box, :dist), jv.location)
                  and postgis.st_dwithin(segment_geometry.geometry, jv.location, :dist)
                inner join layout.switch_in_layout_context('DRAFT', :design_id) switch
                  on jv.switch_id = switch.id
                    and jv.switch_layout_context_id = switch.layout_context_id
                    and jv.switch_version = switch.version
              where segment_version.alignment_id = :alignmentId
                and segment_version.alignment_version = :alignmentVersion
                and switch.state_category != 'NOT_EXISTING';
        """
                .trimIndent()
        return jdbcTemplate
            .query(
                sql,
                mapOf(
                    "alignmentId" to alignmentVersion.id.intValue,
                    "alignmentVersion" to alignmentVersion.version,
                    "dist" to maxDistance,
                    "design_id" to branch.designId?.intValue,
                ),
            ) { rs, _ ->
                rs.getIntId<LayoutSwitch>("switch_id")
            }
            .also { results -> logger.daoAccess(FETCH, "Switches near alignment", results) }
    }

    @Transactional
    fun insertExternalId(id: IntId<LayoutSwitch>, branch: LayoutBranch, oid: Oid<LayoutSwitch>) {
        jdbcTemplate.setUser()
        insertExternalIdInExistingTransaction(branch, id, oid)
    }
}
