package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_SWITCH
import fi.fta.geoviite.infra.dataImport.SwitchLinkingIds
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.SwitchPublishCandidate
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_SWITCH
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val MAX_FALLBACK_SWITCH_JOINT_TRACK_LOOKUP_DISTANCE = 1.0

@Suppress("SameParameterValue")
@Service
class LayoutSwitchDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) :
    DraftableDaoBase<TrackLayoutSwitch>(jdbcTemplateParam, LAYOUT_SWITCH) {

    @Transactional
    fun fetchSwitchJointConnections(
        publishType: PublishType,
        switchId: IntId<TrackLayoutSwitch>
    ): List<TrackLayoutSwitchJointConnection> {
        val sql = """
            with alignment as (
              select
                location_track.official_id,
                location_track.alignment_id,
                segment.segment_index,
                segment.switch_id,
                segment.geometry,
                segment.switch_start_joint_number,
                segment.switch_end_joint_number,
                segment.bounding_box
              from layout.segment
                inner join layout.alignment on alignment.id = segment.alignment_id
                inner join layout.location_track_publication_view location_track
                  on location_track.alignment_id = alignment.id
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
              alignment.official_id as location_track_id,
              coalesce(alignment.switch_id = switch.official_id
                         and (alignment.switch_start_joint_number = switch_joint.number
                           or alignment.switch_end_joint_number = switch_joint.number), false) as match_based_on_segment_link
            from layout.switch_joint
              inner join layout.switch_publication_view switch 
                on switch.row_id = switch_joint.switch_id
                  and switch.state_category != 'NOT_EXISTING'
              left join alignment
                on (alignment.switch_id = switch.official_id
                      and (alignment.switch_start_joint_number = switch_joint.number
                        or alignment.switch_end_joint_number = switch_joint.number))
                  or (postgis.st_intersects(alignment.bounding_box,
                                            postgis.st_expand(switch_joint.location, :max_lookup_distance))
                      and postgis.st_distance(alignment.geometry, switch_joint.location) < :max_lookup_distance)
            where switch.official_id = :switch_id and :publication_state = any(switch.publication_states)
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_state" to publishType.name,
            "max_lookup_distance" to MAX_FALLBACK_SWITCH_JOINT_TRACK_LOOKUP_DISTANCE,
        )

        data class JointKey(
            val number: JointNumber,
            val locationAccuracy: LocationAccuracy?,
        )

        val unmatchedJoints: MutableSet<JointKey> = mutableSetOf()
        val accurateMatches: MutableMap<JointKey, MutableMap<IntId<LocationTrack>, Point>> = mutableMapOf()
        val fallbackMatches: MutableMap<JointKey, MutableSet<IntId<LocationTrack>>> = mutableMapOf()

        jdbcTemplate.query(sql, params) { rs, _ ->
            val jointKey = JointKey(
                number = JointNumber(rs.getInt("joint_number")),
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy")
            )
            val locationTrackId = rs.getIntIdOrNull<LocationTrack>("location_track_id")
            if (locationTrackId != null) {
                val matchBasedOnSegmentLink = rs.getBoolean("match_based_on_segment_link")
                if (matchBasedOnSegmentLink) {
                    val matchedAtStart = rs.getBoolean("matched_at_segment_start")
                    val location =
                        if (matchedAtStart) rs.getPoint("location_start_x", "location_start_y")
                        else rs.getPoint("location_end_x", "location_end_y")
                    accurateMatches.computeIfAbsent(jointKey) { mutableMapOf() }[locationTrackId] = location
                } else {
                    fallbackMatches.computeIfAbsent(jointKey) { mutableSetOf() }.add(locationTrackId)
                }
            } else {
                unmatchedJoints.add(jointKey)
            }
        }
        return (unmatchedJoints + accurateMatches.keys + fallbackMatches.keys).map { joint ->
            TrackLayoutSwitchJointConnection(joint.number,
                accurateMatches[joint]?.entries?.map { e -> TrackLayoutSwitchJointMatch(e.key, e.value) } ?: listOf(),
                fallbackMatches[joint]?.toList() ?: listOf(),
                joint.locationAccuracy)
        }
    }

    @Transactional
    override fun insert(newItem: TrackLayoutSwitch): RowVersion<TrackLayoutSwitch> {
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
            returning id, version
        """.trimIndent()
        jdbcTemplate.setUser()
        val id: RowVersion<TrackLayoutSwitch> = jdbcTemplate.queryForObject(
            sql, mapOf(
                "external_id" to newItem.externalId?.stringValue,
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
        ) { rs, _ -> rs.getRowVersion("id", "version") }
            ?: throw IllegalStateException("Failed to generate ID for new switch")
        if (newItem.joints.isNotEmpty()) upsertJoints(id, newItem.joints)
        logger.daoAccess(INSERT, TrackLayoutSwitch::class, id)
        return id
    }

    @Transactional
    override fun update(updatedItem: TrackLayoutSwitch): RowVersion<TrackLayoutSwitch> {
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
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "external_id" to updatedItem.externalId?.stringValue,
            "geometry_switch_id" to if (updatedItem.sourceId is IntId<GeometrySwitch>) updatedItem.sourceId.intValue else null,
            "name" to updatedItem.name,
            "switch_structure_id" to updatedItem.switchStructureId.intValue,
            "state_category" to updatedItem.stateCategory.name,
            "trap_point" to updatedItem.trapPoint,
            "draft" to (updatedItem.draft != null),
            "draft_of_switch_id" to draftOfId(updatedItem.id, updatedItem.draft)?.intValue,
        )
        jdbcTemplate.setUser()
        val result: RowVersion<TrackLayoutSwitch> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to get new version for Track Layout Switch")

        upsertJoints(result, updatedItem.joints)

        logger.daoAccess(UPDATE, TrackLayoutSwitch::class, rowId)
        return result
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
    @Transactional
    override fun fetch(version: RowVersion<TrackLayoutSwitch>): TrackLayoutSwitch {
        val sql = """
            select 
              official_id, 
              official_version,
              draft_id,
              draft_version,
              geometry_switch_id, 
              external_id, 
              name, 
              switch_structure_id,
              state_category,
              trap_point,
              owner_id,
              source
            from layout.switch_publication_view
            where row_id = :id
        """.trimIndent()
        val params = mapOf("id" to version.id.intValue)
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
                version = rs.getVersion("official_version", "draft_version"),
                source = rs.getEnum("source")
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
            from layout.switch_joint joint 
            where 
                switch_id = :switch_id and
                switch_version = :switch_version
            order by switch_id, number
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.id.intValue,
            "switch_version" to switchId.version
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutSwitchJoint(
                number = rs.getJointNumber("number"),
                location = rs.getPoint("location_x", "location_y"),
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy")
            )
        }
    }

    fun getExternalIdMappingOfExistingSwitches(): Map<Oid<TrackLayoutSwitch>, SwitchLinkingIds> {
        val sql = """
          select external_id, id, switch_structure_id
          from layout.switch 
          where 
            external_id is not null
            and state_category = 'EXISTING' 
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            rs.getOid<TrackLayoutSwitch>("external_id") to
                    SwitchLinkingIds(
                        rs.getIntId("id"),
                        rs.getIntId("switch_structure_id"),
                    )
        }.associate { it }
    }

    fun fetchSwitchPublicationInformation(publicationId: IntId<Publication>): List<SwitchPublishCandidate> {
        val sql = """
            select
              switch_version.id,
              switch_version.change_time,
              switch_version.name
            from publication.switch published_switch
              left join layout.switch_version
                on published_switch.switch_id = switch_version.id
                  and published_switch.switch_version = switch_version.version
            where publication_id = :id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "id" to publicationId.intValue
            )
        ) { rs, _ ->
            SwitchPublishCandidate(
                id = rs.getIntId("id"),
                draftChangeTime = rs.getInstant("change_time"),
                name = SwitchName(rs.getString("name"))
            )
        }.also { logger.daoAccess(FETCH, Publication::class, publicationId) }
    }
}
