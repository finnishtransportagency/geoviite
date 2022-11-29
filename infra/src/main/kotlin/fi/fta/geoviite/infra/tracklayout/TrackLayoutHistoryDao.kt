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
                version = rs.getVersion("version", "version"),
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
                version = rs.getVersion("version", "version"),
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

    fun fetchLayoutAlignmentVersion(alignmentRowVersion: RowVersion<LayoutAlignment>): LayoutAlignment {
        val sql = """
            select 
              id, 
              geometry_alignment_id
              from layout.alignment_version
            where 
              id = :alignment_id 
              and version = :alignment_version
        """.trimIndent()
        val params = mapOf(
            "alignment_id" to alignmentRowVersion.id.intValue,
            "alignment_version" to alignmentRowVersion.version,
        )
        val alignment = getOne(alignmentRowVersion.id, jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutAlignment(
                dataType = DataType.STORED,
                id = rs.getIntId("id"),
                sourceId = rs.getIntIdOrNull("geometry_alignment_id"),
                segments = fetchSegments(alignmentRowVersion),
            )
        })
        logger.daoAccess(AccessType.FETCH, LayoutAlignment::class, alignment.id)
        return alignment
    }

    private fun fetchSegments(
        alignmentVersion: RowVersion<LayoutAlignment>,
    ): List<LayoutSegment> {
        val sql = """
            select 
              s.alignment_id,
              s.segment_index,
              geometry_alignment_id,
              geometry_element_index,
              source_start,
              postgis.st_astext(geometry) as geometry_wkt,
              resolution,
              case 
                when height_values is null then null 
                else array_to_string(height_values, ',') 
              end as height_values,
              case 
                when cant_values is null then null 
                else array_to_string(cant_values, ',') 
              end as cant_values,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number,
              start,
              length,
              source
            from layout.segment_version s
              right join (
                  select
                      alignment_id, 
                      alignment_version,
                      segment_index,
                      max(version) as latest_version
                  from layout.segment_version
                  where
                    alignment_id = :alignment_id
                    and alignment_version= :alignment_version
                  group by 
                    alignment_id, alignment_version, segment_index
              ) s2 on s2.alignment_id = s.alignment_id
                    and s2.alignment_version = s.alignment_version
                    and s2.segment_index = s.segment_index
                    and s2.latest_version = s.version
            order by segment_index
        """.trimIndent()
        val params = mapOf(
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutSegment(
                id = rs.getIndexedId("alignment_id", "segment_index"),
                sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                sourceStart = rs.getDoubleOrNull("source_start"),
                points = getSegmentPoints(rs, "geometry_wkt", "height_values", "cant_values"),
                resolution = rs.getInt("resolution"),
                switchId = rs.getIntIdOrNull("switch_id"),
                startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                start = rs.getDouble("start"),
                source = rs.getEnum("source"),
            )
        }
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
                version = rs.getVersion("version", "version"),
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
                version = rs.getVersion("version", "version"),
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
