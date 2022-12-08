package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Suppress("SameParameterValue")
@Transactional(readOnly = true)
@Component
class TrackLayoutHistoryDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchTrackNumberAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant? = null,
    ): TrackLayoutTrackNumber? {
        //language=SQL
        val sql = """
            select 
              id, 
              external_id, 
              number, 
              description,
              state
            from layout.track_number_version
            where 
                id = :id 
                and change_time <= coalesce(:moment, now())
                and not draft
                and not deleted
            order by change_time desc
            fetch first row only
        """.trimIndent()
        val params = mapOf(
            "id" to trackNumberId.intValue,
            "moment" to moment?.atOffset(ZoneOffset.UTC),
        )
        val trackNumber = jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            TrackLayoutTrackNumber(
                number = rs.getTrackNumber("number"),
                description = rs.getFreeText("description"),
                state = rs.getEnum("state"),
                externalId = rs.getOidOrNull("external_id"),
                id = rs.getIntId("id"),
                dataType = DataType.STORED,
            )
        }
        if (trackNumber != null) {
            logger.daoAccess(AccessType.FETCH, TrackLayoutTrackNumber::class, trackNumber.id)
        }
        return trackNumber
    }

    fun fetchReferenceLineAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant? = null,
    ): ReferenceLine? {
        //language=SQL
        val sql = """
            select 
              id,
              version,
              track_number_id, 
              alignment_id,
              alignment_version,
              start_address
            from layout.reference_line_version
            where
              track_number_id = :track_number_id
              and change_time <= coalesce(:moment, now())
              and not draft
              and not deleted
            order by change_time desc
            fetch first row only
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "moment" to moment?.atOffset(ZoneOffset.UTC),
        )
        val referenceLine = jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            ReferenceLine(
                dataType = DataType.STORED,
                id = rs.getIntId("id"),
                alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
                sourceId = null,
                trackNumberId = rs.getIntId("track_number_id"),
                startAddress = rs.getTrackMeter("start_address"),
                version = rs.getRowVersion("id", "version"),
            )
        }
        if (referenceLine != null) {
            logger.daoAccess(AccessType.FETCH, ReferenceLine::class, referenceLine.id)
        }
        return referenceLine
    }

    fun fetchLocationTrackAtMoment(locationTrackId: IntId<LocationTrack>, moment: Instant? = null): LocationTrack? {
        //language=SQL
        val sql = """
            select
              location_track_version.id,
              location_track_version.version,
              alignment_id,
              alignment_version,
              track_number_id, 
              external_id, 
              name, 
              description, 
              type, 
              state, 
              a.length,
              a.segment_count,
              duplicate_of_location_track_id,
              topological_connectivity,
              topology_start_switch_id,
              topology_start_switch_joint_number,
              topology_end_switch_id,
              topology_end_switch_joint_number,
              postgis.st_astext(a.bounding_box) as bounding_box
            from layout.location_track_version
            left join layout.alignment_version a on 
              location_track_version.alignment_id = a.id
              and location_track_version.alignment_version = a.version
            where
              location_track_version.id = :location_track_id
              and location_track_version.change_time <= coalesce(:moment, now())
              and not location_track_version.draft
              and not location_track_version.deleted
            order by location_track_version.change_time desc
            fetch first row only
        """.trimIndent()
        val params = mapOf(
            "location_track_id" to locationTrackId.intValue,
            "moment" to moment?.atOffset(ZoneOffset.UTC),
        )
        val locationTrack = jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            LocationTrack(
                dataType = DataType.STORED,
                id = rs.getIntId("id"),
                alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
                sourceId = null,
                externalId = rs.getOidOrNull("external_id"),
                trackNumberId = rs.getIntId("track_number_id"),
                name = rs.getString("name").let(::AlignmentName),
                description = rs.getFreeText("description"),
                type = rs.getEnum("type"),
                state = rs.getEnum("state"),
                boundingBox = rs.getBboxOrNull("bounding_box"),
                length = rs.getDouble("length"),
                segmentCount = rs.getInt("segment_count"),
                version = rs.getRowVersion("id", "version"),
                duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
                topologicalConnectivity = rs.getEnum("topological_connectivity"),
                topologyStartSwitch = rs.getIntIdOrNull<TrackLayoutSwitch>("topology_start_switch_id")
                    ?.let { id -> TopologyLocationTrackSwitch(
                        id,
                        rs.getJointNumber("topology_start_switch_joint_number"),
                    ) },
                topologyEndSwitch = rs.getIntIdOrNull<TrackLayoutSwitch>("topology_end_switch_id")
                    ?.let { id -> TopologyLocationTrackSwitch(
                        id,
                        rs.getJointNumber("topology_end_switch_joint_number"),
                    ) },
            )
        }
        if (locationTrack != null) {
            logger.daoAccess(AccessType.FETCH, LocationTrack::class, locationTrack.id)
        }
        return locationTrack
    }

    fun fetchKmPostsAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant? = null,
    ): List<TrackLayoutKmPost> {
        val sql = """
            select 
                id,
                km.track_number_id,
                geometry_km_post_id,
                km.km_number,
                postgis.st_x(location) as point_x, 
                postgis.st_y(location) as point_y,
                state,
                version,
                change_time,
                deleted
            from layout.km_post_version km
            right join (
                select
                  track_number_id,
                  km_number,
                  max(change_time) as max_change_time
                from layout.km_post_version
                where
                  track_number_id = :track_number_id
                  and change_time <= coalesce(:moment, now())
                  and not draft
                  and not deleted
                group by
                  track_number_id, km_number
            ) km2 on
                km2.track_number_id=km.track_number_id
                and km2.km_number=km.km_number
                and km2.max_change_time=km.change_time
                and not draft
                and not deleted
            order by km.km_number
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "moment" to moment?.atOffset(ZoneOffset.UTC),
        )
        val posts = jdbcTemplate.query(sql, params) { rs, _ ->
            TrackLayoutKmPost(
                id = rs.getIntId("id"),
                dataType = DataType.STORED,
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                location = rs.getPointOrNull("point_x", "point_y"),
                state = rs.getEnum("state"),
                sourceId = rs.getIntIdOrNull("geometry_km_post_id"),
                version = rs.getRowVersion("id", "version"),
            )
        }
        logger.daoAccess(AccessType.FETCH, TrackLayoutKmPost::class, posts.map { it.id })
        return posts
    }

    fun getSwitchAtMoment(
        switchId: IntId<TrackLayoutSwitch>,
        moment: Instant? = null,
    ): TrackLayoutSwitch? {
        //language=SQL
        val sql = """
            select
              id,
              version
            from layout.switch_version
            where
              id = :id
              and change_time <= coalesce(:moment, now())
              and not draft
              and not deleted
            order by change_time desc
            fetch first row only
        """.trimIndent()
        val params = mapOf(
            "id" to switchId.intValue,
            "moment" to moment?.atOffset(ZoneOffset.UTC),
        )
        val switchVersion = jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion<TrackLayoutSwitch>("id", "version")
        }

        return if (switchVersion != null) {
            logger.daoAccess(AccessType.FETCH, TrackLayoutSwitch::class, switchVersion)
            return getSwitchVersion(switchVersion)
        } else {
            null
        }
    }

    fun getSwitchVersion(
        switchId: RowVersion<TrackLayoutSwitch>
    ): TrackLayoutSwitch {
        val sql = """
            select 
              id, 
              version,
              geometry_switch_id, 
              external_id, 
              name, 
              switch_structure_id,
              state_category,
              trap_point,
              owner_id,
              source
            from layout.switch_version
            where 
              id = :id
              and version = :version
        """.trimIndent()
        val params = mapOf(
            "id" to switchId.id.intValue,
            "version" to switchId.version,
        )
        val switch = getOne(switchId.id, jdbcTemplate.query(sql, params) { rs, _ ->
            val switchStructureId = rs.getIntId<SwitchStructure>("switch_structure_id")

            TrackLayoutSwitch(
                id = rs.getIntId("id"),
                dataType = DataType.STORED,
                externalId = rs.getOidOrNull("external_id"),
                sourceId = rs.getIntIdOrNull("geometry_switch_id"),
                name = SwitchName(rs.getString("name")),
                switchStructureId = switchStructureId,
                stateCategory = rs.getEnum("state_category"),
                joints = fetchSwitchJoints(switchId),
                trapPoint = rs.getBooleanOrNull("trap_point"),
                ownerId = rs.getIntId("owner_id"),
                version = rs.getRowVersion("id", "version"),
                source = rs.getEnum("source"),
            )
        })
        logger.daoAccess(AccessType.FETCH, TrackLayoutSwitch::class, switch.id)
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
            where 
              switch_id = :switch_id
              and switch_version = :switch_version
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
                locationAccuracy = rs.getEnumOrNull<LocationAccuracy>("location_accuracy"),
            )
        }
    }
}
