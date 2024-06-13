package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteDao
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getNullableDoubleArray
import fi.fta.geoviite.infra.util.getNullableEnumArray
import fi.fta.geoviite.infra.util.getNullableIntArray
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.setUser
import fi.fta.geoviite.infra.util.toDbId
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

const val SWITCH_CACHE_SIZE = 10000L

@Suppress("SameParameterValue")
@GeoviiteDao(readOnly = true)
class LayoutSwitchDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) : LayoutAssetDao<TrackLayoutSwitch>(jdbcTemplateParam, LayoutAssetTable.LAYOUT_ASSET_SWITCH, cacheEnabled, SWITCH_CACHE_SIZE) {

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<RowVersion<TrackLayoutSwitch>> {
        val sql = """
            select
              row_id,
              row_version
            from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where (:include_deleted = true or state_category != 'NOT_EXISTING')
        """.trimIndent()
        val params = mapOf(
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
            "include_deleted" to includeDeleted,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchSegmentSwitchJointConnections(
        layoutContext: LayoutContext,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<TrackLayoutSwitchJointConnection> {
        val sql = """
            select number, location_accuracy, location_track_id, postgis.st_x(point) x, postgis.st_y(point) y
              from layout.switch_in_layout_context(:publication_state::layout.publication_state,
                                                   :design_id,
                                                   :switch_id) switch
                join layout.switch_joint on switch.row_id = switch_joint.switch_id
                left join (
                  select lt.official_id as location_track_id, *
                    from layout.segment_version
                      cross join lateral
                        (select *
                         from layout.location_track_in_layout_context(:publication_state::layout.publication_state,
                                                                      :design_id) lt
                         where lt.alignment_id = segment_version.alignment_id
                           and lt.alignment_version = segment_version.alignment_version
                           and lt.state != 'DELETED'
                         offset 0) lt
                      join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                      cross join lateral
                      (select switch_start_joint_number as number, postgis.st_startpoint(geometry) as point
                         where switch_start_joint_number is not null
                       union all
                         select switch_end_joint_number, postgis.st_endpoint(geometry)
                           where switch_end_joint_number is not null) p
                  where switch_id = :switch_id
              ) segment_joint using (number)
            where state_category != 'NOT_EXISTING';
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )

        data class JointKey(
            val number: JointNumber,
            val locationAccuracy: LocationAccuracy?,
        )

        val unmatchedJoints: MutableSet<JointKey> = mutableSetOf()
        val accurateMatches: MutableMap<JointKey, MutableMap<IntId<LocationTrack>, Point>> = mutableMapOf()

        jdbcTemplate.query(sql, params) { rs, _ ->
            val jointKey = JointKey(
                number = JointNumber(rs.getInt("number")),
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy")
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
            TrackLayoutSwitchJointConnection(joint.number,
                accurateMatches[joint]?.entries?.map { e -> TrackLayoutSwitchJointMatch(e.key, e.value) } ?: listOf(),
                joint.locationAccuracy)
        }
    }

    @Transactional
    override fun insert(newItem: TrackLayoutSwitch): DaoResponse<TrackLayoutSwitch> {
        val sql = """
            insert into 
              layout.switch(
                external_id,
                geometry_switch_id,
                name,
                switch_structure_id,
                state_category,
                trap_point,
                owner_id,
                draft,
                official_row_id,
                design_row_id,
                design_id,
                source
            )
            values (
              :external_id, 
              :geometry_switch_id,
              :name,
              :switch_structure_id,
              :state_category::layout.state_category,
              :trap_point,
              :owner_id,
              :draft,
              :official_row_id,
              :design_row_id,
              :design_id,
              :source::layout.geometry_source
            )
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutSwitch> = jdbcTemplate.queryForObject(
            sql, mapOf(
                "external_id" to newItem.externalId,
                "geometry_switch_id" to newItem.sourceId?.let(::toDbId)?.intValue,
                "name" to newItem.name,
                "switch_structure_id" to newItem.switchStructureId.intValue,
                "state_category" to newItem.stateCategory.name,
                "trap_point" to newItem.trapPoint,
                "owner_id" to newItem.ownerId?.intValue,
                "draft" to newItem.isDraft,
                "official_row_id" to newItem.contextData.officialRowId?.let(::toDbId)?.intValue,
                "design_row_id" to newItem.contextData.designRowId?.let(::toDbId)?.intValue,
                "design_id" to newItem.contextData.designId?.let(::toDbId)?.intValue,
                "source" to newItem.source.name
            )
        ) { rs, _ -> rs.getDaoResponse("official_id", "row_id", "row_version") }
            ?: throw IllegalStateException("Failed to generate ID for new switch")
        if (newItem.joints.isNotEmpty()) upsertJoints(response.rowVersion, newItem.joints)

        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutSwitch): DaoResponse<TrackLayoutSwitch> {
        val sql = """
            update layout.switch
            set
              external_id = :external_id,
              geometry_switch_id = :geometry_switch_id,
              name = :name,
              switch_structure_id = :switch_structure_id,
              state_category = :state_category::layout.state_category,
              trap_point = :trap_point,
              draft = :draft,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id,
              owner_id = :owner_id
            where id = :id
            returning 
              coalesce(official_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "id" to toDbId(updatedItem.contextData.rowId).intValue,
            "external_id" to updatedItem.externalId,
            "geometry_switch_id" to updatedItem.sourceId?.let(::toDbId)?.intValue,
            "name" to updatedItem.name,
            "switch_structure_id" to updatedItem.switchStructureId.intValue,
            "state_category" to updatedItem.stateCategory.name,
            "trap_point" to updatedItem.trapPoint,
            "draft" to updatedItem.isDraft,
            "official_row_id" to updatedItem.contextData.officialRowId?.let(::toDbId)?.intValue,
            "design_row_id" to updatedItem.contextData.designRowId?.let(::toDbId)?.intValue,
            "design_id" to updatedItem.contextData.designRowId?.let(::toDbId)?.intValue,
            "owner_id" to updatedItem.ownerId?.intValue
        )
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutSwitch> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Track Layout Switch")

        upsertJoints(response.rowVersion, updatedItem.joints)

        return response
    }

    private fun upsertJoints(
        switchVersion: RowVersion<TrackLayoutSwitch>,
        joints: List<TrackLayoutSwitchJoint>,
    ) {
        if (joints.isNotEmpty()) {
            val sql = """
              insert into layout.switch_joint(
                switch_id,
                switch_version,
                number, 
                location, 
                location_accuracy
                )
              values (
                :switch_id,
                :switch_version,
                :number, 
                postgis.st_setsrid(postgis.st_point(:location_x, :location_y), :srid), 
                :location_accuracy::common.location_accuracy
                )
              on conflict (switch_id, number) do update
              set
                switch_version = excluded.switch_version,
                location = excluded.location,
                location_accuracy = excluded.location_accuracy
          """.trimIndent()
            val params = joints.map { joint ->
                mapOf(
                    "switch_id" to switchVersion.id.intValue,
                    "switch_version" to switchVersion.version,
                    "number" to joint.number.intValue,
                    "location_x" to joint.location.x,
                    "location_y" to joint.location.y,
                    "srid" to LAYOUT_SRID.code,
                    "location_accuracy" to joint.locationAccuracy?.name,
                )
            }.toTypedArray()
            jdbcTemplate.batchUpdate(sql, params)
        }

        if (switchVersion.version > 1) {
            val sqlDelete = """ 
              delete from layout.switch_joint 
              where switch_id = :switch_id 
                and switch_joint.switch_version < :switch_version  
            """.trimIndent()
            val paramsDelete = mapOf(
                "switch_id" to switchVersion.id.intValue,
                "switch_version" to switchVersion.version,
            )
            jdbcTemplate.update(sqlDelete, paramsDelete)
        }
    }

    override fun fetchInternal(version: RowVersion<TrackLayoutSwitch>): TrackLayoutSwitch {
        val sql = """
            select 
              sv.id as row_id,
              sv.version as row_version,
              sv.official_row_id,
              sv.design_row_id,
              sv.design_id,
              sv.draft,
              sv.geometry_switch_id, 
              sv.external_id, 
              sv.name, 
              sv.switch_structure_id,
              sv.state_category,
              sv.trap_point,
              sv.owner_id,
              sv.source ,
              array_agg(jv.number order by jv.number) as joint_numbers,
              array_agg(postgis.st_x(jv.location) order by jv.number) as joint_x_values,
              array_agg(postgis.st_y(jv.location) order by jv.number) as joint_y_values,
              array_agg(jv.location_accuracy order by jv.number) as joint_location_accuracies
            from layout.switch_version sv
              left join layout.switch_joint_version jv on jv.switch_id = sv.id and jv.switch_version = sv.version
            where sv.id = :id and sv.version = :version
              and sv.deleted = false
              and coalesce(jv.deleted,false) = false
            group by sv.id, sv.version
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        return getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ -> getLayoutSwitch(rs) })
    }

    override fun preloadCache() {
        val sql = """
            select 
              s.id as row_id,
              s.version as row_version,
              s.official_row_id,
              s.design_row_id,
              s.design_id,
              s.draft,
              s.geometry_switch_id, 
              s.external_id, 
              s.name, 
              s.switch_structure_id,
              s.state_category,
              s.trap_point,
              s.owner_id,
              s.source ,
              array_agg(jv.number order by jv.number) as joint_numbers,
              array_agg(postgis.st_x(jv.location) order by jv.number) as joint_x_values,
              array_agg(postgis.st_y(jv.location) order by jv.number) as joint_y_values,
              array_agg(jv.location_accuracy order by jv.number) as joint_location_accuracies
            from layout.switch s
              left join layout.switch_joint_version jv on jv.switch_id = s.id and jv.switch_version = s.version
            where coalesce(jv.deleted,false) = false
            group by s.id, s.version
        """.trimIndent()

        val switches = jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ -> getLayoutSwitch(rs) }
            .associateBy(TrackLayoutSwitch::version)

        cache.putAll(switches)
    }

    private fun getLayoutSwitch(rs: ResultSet): TrackLayoutSwitch {
        val switchStructureId = rs.getIntId<SwitchStructure>("switch_structure_id")
        val switchVersion = rs.getRowVersion<TrackLayoutSwitch>("row_id", "row_version")
        return TrackLayoutSwitch(
            externalId = rs.getOidOrNull("external_id"),
            sourceId = rs.getIntIdOrNull("geometry_switch_id"),
            name = SwitchName(rs.getString("name")),
            switchStructureId = switchStructureId,
            stateCategory = rs.getEnum("state_category"),
            joints = parseJoints(
                numbers = rs.getNullableIntArray("joint_numbers"),
                xValues = rs.getNullableDoubleArray("joint_x_values"),
                yValues = rs.getNullableDoubleArray("joint_y_values"),
                accuracies = rs.getNullableEnumArray<LocationAccuracy>("joint_location_accuracies"),
            ),
            trapPoint = rs.getBooleanOrNull("trap_point"),
            ownerId = rs.getIntIdOrNull("owner_id"),
            version = switchVersion,
            source = rs.getEnum("source"),
            contextData = rs.getLayoutContextData("official_row_id", "design_row_id", "design_id", "row_id", "draft"),
        )
    }

    private fun parseJoints(
        numbers: List<Int?>,
        xValues: List<Double?>,
        yValues: List<Double?>,
        accuracies: List<LocationAccuracy?>,
    ): List<TrackLayoutSwitchJoint> {
        require(numbers.size == xValues.size && numbers.size == yValues.size && numbers.size == accuracies.size) {
            "Joint piece arrays should be the same size: numbers=${numbers.size} xValues=${xValues.size} yValues=${yValues.size} accuracies=${accuracies.size}"
        }
        return (0..numbers.lastIndex).mapNotNull { i ->
            numbers[i]?.let(::JointNumber)?.let { jointNumber ->
                TrackLayoutSwitchJoint(
                    number = jointNumber,
                    location = Point(
                        requireNotNull(xValues[i]) { "Joint should have an x-coordinate: number=$jointNumber" },
                        requireNotNull(yValues[i]) { "Joint should have an y-coordinate: number=$jointNumber" },
                    ),
                    locationAccuracy = accuracies[i],
                )
            }
        }
    }

    data class LocationTrackIdentifiers(
        val rowVersion: RowVersion<LocationTrack>,
        val externalId: Oid<LocationTrack>?,
    )

    fun findLocationTracksLinkedToSwitch(
        layoutContext: LayoutContext,
        switchId: IntId<TrackLayoutSwitch>,
    ): List<LocationTrackIdentifiers> = findLocationTracksLinkedToSwitches(layoutContext, listOf(switchId))

    fun findLocationTracksLinkedToSwitches(
        layoutContext: LayoutContext,
        switchIds: List<IntId<TrackLayoutSwitch>>,
    ): List<LocationTrackIdentifiers> {
        if (switchIds.isEmpty()) return emptyList()

        val sql = """ 
            select 
              location_track.row_id,
              location_track.row_version,
              location_track.external_id
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) location_track
            where location_track.topology_start_switch_id in (:switch_ids)
              or location_track.topology_end_switch_id in (:switch_ids)
              or exists (select * from layout.segment_version sv
                           where sv.switch_id in (:switch_ids)
                             and sv.alignment_id = location_track.alignment_id
                             and sv.alignment_version = location_track.alignment_version)
        """.trimIndent()
        val params = mapOf(
            "switch_ids" to switchIds.map { it.intValue },
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrackIdentifiers(
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                externalId = rs.getOidOrNull("external_id"),
            )
        }
    }

    fun findLocationTracksLinkedToSwitchAtMoment(
        switchId: IntId<TrackLayoutSwitch>,
        topologyJointNumber: JointNumber,
        moment: Instant,
    ): List<LocationTrackIdentifiers> {
        val sql = """ 
            select distinct
              location_track.id, 
              location_track.version,
              location_track.external_id
            from layout.switch_at(:moment) switch
              inner join layout.location_track_at(:moment) location_track on not location_track.draft
              inner join layout.segment_version segment 
                on segment.alignment_id = location_track.alignment_id 
                  and segment.alignment_version = location_track.alignment_version
            where switch.id = :switch_id 
                and (segment.switch_id = :switch_id
                  or (location_track.topology_start_switch_id = :switch_id 
                    and location_track.topology_start_switch_joint_number = :topology_joint_number
                  )
                  or (location_track.topology_end_switch_id = :switch_id
                    and location_track.topology_end_switch_joint_number = :topology_joint_number
                  )
              )
        """.trimIndent()

        val params = mapOf(
            "switch_id" to switchId.intValue,
            "topology_joint_number" to topologyJointNumber.intValue,
            "moment" to Timestamp.from(moment),
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrackIdentifiers(
                rowVersion = rs.getRowVersion("id", "version"),
                externalId = rs.getOidOrNull("external_id"),
            )
        }
    }

    fun findOfficialNameDuplicates(names: List<SwitchName>): Map<SwitchName, List<RowVersion<TrackLayoutSwitch>>> {
        return if (names.isEmpty()) {
            emptyMap()
        } else {
            val sql = """
                select id, version, name
                from layout.switch
                where name in (:names)
                  and draft = false
                  and state_category != 'NOT_EXISTING'
            """.trimIndent()
            val params = mapOf("names" to names)
            val found = jdbcTemplate.query<Pair<SwitchName, RowVersion<TrackLayoutSwitch>>>(sql, params) { rs, _ ->
                val version = rs.getRowVersion<TrackLayoutSwitch>("id", "version")
                val name = rs.getString("name").let(::SwitchName)
                name to version
            }
            // Ensure that the result contains all asked-for names, even if there are no matches
            names.associateWith { n -> found.filter { (name, _) -> name == n }.map { (_, v) -> v } }
        }
    }

    fun findSwitchesNearAlignment(
        branch: LayoutBranch,
        alignmentVersion: RowVersion<LayoutAlignment>,
        maxDistance: Double = 1.0,
    ): List<IntId<TrackLayoutSwitch>> {
        val sql = """
            select distinct switch.official_id as switch_id
              from layout.segment_version
                join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                join layout.switch_joint on
                  postgis.st_contains(postgis.st_expand(segment_geometry.bounding_box, :dist), switch_joint.location)
                  and postgis.st_distance(segment_geometry.geometry, switch_joint.location) < :dist
                join layout.switch_in_layout_context('DRAFT', :design_id) switch on switch_joint.switch_id = switch.row_id
              where segment_version.alignment_id = :alignmentId
                and segment_version.alignment_version = :alignmentVersion
                and switch.state_category != 'NOT_EXISTING';
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "alignmentId" to alignmentVersion.id.intValue,
                "alignmentVersion" to alignmentVersion.version,
                "dist" to maxDistance,
                "design_id" to branch.designId?.intValue,
            )
        ) { rs, _ ->
            rs.getIntId<TrackLayoutSwitch>("switch_id")
        }
    }
}
