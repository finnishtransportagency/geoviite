package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_SWITCH
import fi.fta.geoviite.infra.dataImport.SwitchLinkingInfo
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_SWITCH
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Suppress("SameParameterValue")
@Transactional(readOnly = true)
@Component
class LayoutSwitchDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) :
    DraftableDaoBase<TrackLayoutSwitch>(jdbcTemplateParam, LAYOUT_SWITCH) {

    override fun fetchVersions(
        publicationState: PublishType,
        includeDeleted: Boolean,
    ): List<RowVersion<TrackLayoutSwitch>> {
        val sql = """
            select
              row_id,
              row_version
            from layout.switch_publication_view 
            where :publication_state = any(publication_states) 
              and (:include_deleted = true or state_category != 'NOT_EXISTING')
        """.trimIndent()
        val params = mapOf(
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchSegmentSwitchJointConnections(
        publicationState: PublishType,
        switchId: IntId<TrackLayoutSwitch>
    ): List<TrackLayoutSwitchJointConnection> {
        val sql = """
            with alignment as (
              select
                location_track.official_id,
                location_track.alignment_id,
                segment_version.segment_index,
                segment_version.switch_id,
                segment_geometry.geometry,
                segment_version.switch_start_joint_number,
                segment_version.switch_end_joint_number
              from layout.segment_version
                inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                inner join layout.location_track_publication_view location_track
                  on location_track.alignment_id = segment_version.alignment_id
                    and location_track.alignment_version = segment_version.alignment_version
                    and location_track.state != 'DELETED'
                    and :publication_state = any (location_track.publication_states)
            )
            select
              switch_joint.number as joint_number,
              switch_joint.location_accuracy as location_accuracy,
              alignment.alignment_id,
              alignment.segment_index,
              alignment.switch_start_joint_number,
              alignment.switch_end_joint_number,
              case
                when alignment.switch_start_joint_number = switch_joint.number then true
                when alignment.switch_end_joint_number = switch_joint.number then false
              end as matched_at_segment_start,
              postgis.st_x(postgis.st_startpoint(alignment.geometry)) as location_start_x,
              postgis.st_y(postgis.st_startpoint(alignment.geometry)) as location_start_y,
              postgis.st_x(postgis.st_endpoint(alignment.geometry)) as location_end_x,
              postgis.st_y(postgis.st_endpoint(alignment.geometry)) as location_end_y,
              alignment.official_id as location_track_id
            from layout.switch_joint
              inner join layout.switch_publication_view switch 
                on switch.row_id = switch_joint.switch_id
                  and switch.state_category != 'NOT_EXISTING'
              left join alignment
                on alignment.switch_id = switch.official_id
                      and (alignment.switch_start_joint_number = switch_joint.number
                        or alignment.switch_end_joint_number = switch_joint.number)
            where switch.official_id = :switch_id and :publication_state = any(switch.publication_states)
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_state" to publicationState.name,
        )

        data class JointKey(
            val number: JointNumber,
            val locationAccuracy: LocationAccuracy?,
        )

        val unmatchedJoints: MutableSet<JointKey> = mutableSetOf()
        val accurateMatches: MutableMap<JointKey, MutableMap<IntId<LocationTrack>, Point>> = mutableMapOf()

        jdbcTemplate.query(sql, params) { rs, _ ->
            val jointKey = JointKey(
                number = JointNumber(rs.getInt("joint_number")),
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy")
            )
            val locationTrackId = rs.getIntIdOrNull<LocationTrack>("location_track_id")
            if (locationTrackId != null) {
                val matchedAtStart = rs.getBoolean("matched_at_segment_start")
                val location =
                    if (matchedAtStart) rs.getPoint("location_start_x", "location_start_y")
                    else rs.getPoint("location_end_x", "location_end_y")
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
        verifyDraftableInsert(newItem.id, newItem.draft)

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
                draft_of_switch_id,
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
              :draft_of_switch_id,
              :source::layout.geometry_source
            )
            returning 
              coalesce(draft_of_switch_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutSwitch> = jdbcTemplate.queryForObject(
            sql, mapOf(
                "external_id" to newItem.externalId,
                "geometry_switch_id" to if (newItem.sourceId is IntId) newItem.sourceId.intValue else null,
                "name" to newItem.name,
                "switch_structure_id" to newItem.switchStructureId.intValue,
                "state_category" to newItem.stateCategory.name,
                "trap_point" to newItem.trapPoint,
                "owner_id" to newItem.ownerId?.intValue,
                "draft" to (newItem.draft != null),
                "draft_of_switch_id" to draftOfId(newItem.id, newItem.draft)?.intValue,
                "source" to newItem.source.name
            )
        ) { rs, _ -> rs.getDaoResponse("official_id", "row_id", "row_version") }
            ?: throw IllegalStateException("Failed to generate ID for new switch")
        if (newItem.joints.isNotEmpty()) upsertJoints(response.rowVersion, newItem.joints)
        logger.daoAccess(INSERT, TrackLayoutSwitch::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutSwitch): DaoResponse<TrackLayoutSwitch> {
        val rowId = toDbId(updatedItem.draft?.draftRowId ?: updatedItem.id)
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
              draft_of_switch_id = :draft_of_switch_id
            where id = :id
            returning 
              coalesce(draft_of_switch_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "external_id" to updatedItem.externalId,
            "geometry_switch_id" to if (updatedItem.sourceId is IntId<GeometrySwitch>) updatedItem.sourceId.intValue else null,
            "name" to updatedItem.name,
            "switch_structure_id" to updatedItem.switchStructureId.intValue,
            "state_category" to updatedItem.stateCategory.name,
            "trap_point" to updatedItem.trapPoint,
            "draft" to (updatedItem.draft != null),
            "draft_of_switch_id" to draftOfId(updatedItem.id, updatedItem.draft)?.intValue,
        )
        jdbcTemplate.setUser()
        val response: DaoResponse<TrackLayoutSwitch> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Track Layout Switch")

        upsertJoints(response.rowVersion, updatedItem.joints)

        logger.daoAccess(UPDATE, TrackLayoutSwitch::class, response)
        return response
    }

    private fun upsertJoints(
        switchVersion: RowVersion<TrackLayoutSwitch>,
        joints: List<TrackLayoutSwitchJoint>
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

    @Cacheable(CACHE_LAYOUT_SWITCH, sync = true)
    override fun fetch(version: RowVersion<TrackLayoutSwitch>): TrackLayoutSwitch {
        val sql = """
            select 
              id as row_id,
              version as row_version,
              coalesce(draft_of_switch_id, id) official_id, 
              case when draft then id end as draft_id,
              geometry_switch_id, 
              external_id, 
              name, 
              switch_structure_id,
              state_category,
              trap_point,
              owner_id,
              source
            from layout.switch_version
            where id = :id
              and version = :version
              and deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        val switch = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            val switchStructureId = rs.getIntId<SwitchStructure>("switch_structure_id")

            TrackLayoutSwitch(
                id = rs.getIntId("official_id"),
                dataType = DataType.STORED,
                externalId = rs.getOidOrNull("external_id"),
                sourceId = rs.getIntIdOrNull("geometry_switch_id"),
                name = SwitchName(rs.getString("name")),
                switchStructureId = switchStructureId,
                stateCategory = rs.getEnum("state_category"),
                joints = fetchSwitchJoints(version),
                trapPoint = rs.getBooleanOrNull("trap_point"),
                ownerId = rs.getIntIdOrNull("owner_id"),
                draft = rs.getIntIdOrNull<TrackLayoutSwitch>("draft_id")?.let { id -> Draft(id) },
                version = rs.getRowVersion("row_id", "row_version"),
                source = rs.getEnum("source"),
            )
        })
        logger.daoAccess(FETCH, TrackLayoutSwitch::class, switch.id)
        return switch
    }

    private fun fetchSwitchJoints(switchId: RowVersion<TrackLayoutSwitch>): List<TrackLayoutSwitchJoint> {
        val sql = """
            select 
              number, 
              postgis.st_x(location) as location_x, 
              postgis.st_y(location) as location_y,
              location_accuracy
            from layout.switch_joint_version joint 
            where switch_id = :switch_id
              and switch_version = :switch_version
              and deleted = false
            order by switch_id, number
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.id.intValue,
            "switch_version" to switchId.version,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutSwitchJoint(
                number = rs.getJointNumber("number"),
                location = rs.getPoint("location_x", "location_y"),
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy")
            )
        }
    }

    data class LinkingInfoAggregation (
        val switchId: IntId<TrackLayoutSwitch>,
        val switchStructureId: IntId<SwitchStructure>,
        val jointLocation: Pair<JointNumber, Point>,
    )
    fun getLinkingInfoOfExistingSwitches(): Map<Oid<TrackLayoutSwitch>, SwitchLinkingInfo> {
        val sql = """
          select external_id, id, switch_structure_id, switch_joint.number,
                 postgis.st_x(switch_joint.location) as location_x, postgis.st_y(location) as location_y
          from layout.switch
            join layout.switch_joint on switch.id = switch_joint.switch_id
          where 
            switch.external_id is not null
            and state_category = 'EXISTING' 
        """.trimIndent()
        val rows = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            rs.getOid<TrackLayoutSwitch>("external_id") to
                    LinkingInfoAggregation(
                        rs.getIntId("id"),
                        rs.getIntId("switch_structure_id"),
                        rs.getJointNumber("number") to rs.getPoint("location_x", "location_y")
                    )
        }
        return rows.groupBy { it.first }.mapValues { entry ->
            val switches = entry.value
            val prototype = switches[0].second
            SwitchLinkingInfo(prototype.switchId, prototype.switchStructureId,
                switches.map { s -> s.second.jointLocation }.associate { it })
        }
    }

    data class LocationTrackIdentifiers(
        val id: IntId<LocationTrack>,
        val rowVersion: RowVersion<LocationTrack>,
        val externalId: Oid<LocationTrack>?,
    )

    fun findLocationTracksLinkedToSwitch(
        publicationState: PublishType,
        switchId: IntId<TrackLayoutSwitch>,
        topologyJointNumber: JointNumber? = null
    ): List<LocationTrackIdentifiers> {
        val sql = """ 
            select 
              location_track.official_id, 
              location_track.row_id,
              location_track.row_version,
              location_track.external_id
            from layout.segment_version
            inner join layout.location_track_publication_view location_track 
              on location_track.alignment_id = segment_version.alignment_id
                and location_track.alignment_version = segment_version.alignment_version
            where :publication_state = any(publication_states)
             and (
               segment_version.switch_id = :switch_id
                 or (
                  location_track.topology_start_switch_id = :switch_id 
                  and (
                    :topology_joint_number::int is null 
                    or location_track.topology_start_switch_joint_number = :topology_joint_number::int
                  )
                 )
                 or (
                  location_track.topology_end_switch_id = :switch_id
                  and (
                    :topology_joint_number::int is null 
                    or location_track.topology_end_switch_joint_number = :topology_joint_number::int
                  )
                 )
               )
            group by 
              location_track.official_id, 
              location_track.row_id, 
              location_track.row_version, 
              location_track.external_id
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_state" to publicationState.name,
            "topology_joint_number" to topologyJointNumber?.intValue
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrackIdentifiers(
                id = rs.getIntId("official_id"),
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                externalId = rs.getOidOrNull("external_id"),
            )
        }
    }
}
