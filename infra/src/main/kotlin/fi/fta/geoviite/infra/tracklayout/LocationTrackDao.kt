package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_LOCATION_TRACK
import fi.fta.geoviite.infra.linking.LocationTrackPublishCandidate
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_LOCATION_TRACK
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocationTrackDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<LocationTrack>(jdbcTemplateParam, LAYOUT_LOCATION_TRACK) {


    @Transactional
    fun fetchDuplicates(id: IntId<LocationTrack>, publishType: PublishType): List<LocationTrackDuplicate> {
        val sql = """
            select 
              official_id,
              external_id, 
              name
            from layout.location_track_publication_view
            where duplicate_of_location_track_id = :id
        """.trimIndent()
        val params = mapOf("id" to id.intValue)
        val locationTracks = jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrackDuplicate(
                id = rs.getIntId("official_id"),
                externalId = rs.getOidOrNull("external_id"),
                name = AlignmentName(rs.getString("name")),
            )
        }
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, id)
        return locationTracks
    }


    @Cacheable(CACHE_LAYOUT_LOCATION_TRACK, sync = true)
    @Transactional
    override fun fetch(version: RowVersion<LocationTrack>): LocationTrack {
        val sql = """
            select 
              official_id, 
              official_version,
              draft_id,
              draft_version,
              alignment_id,
              alignment_version,
              track_number_id, 
              external_id, 
              name, 
              description, 
              type, 
              state, 
              postgis.st_astext(bounding_box) as bounding_box,
              length,
              segment_count,            
              duplicate_of_location_track_id,
              topological_connectivity,
              topology_start_switch_id,
              topology_start_switch_joint_number,
              topology_end_switch_id,
              topology_end_switch_joint_number
            from layout.location_track_publication_view
            where row_id = :location_track_id
        """.trimIndent()
        val params = mapOf("location_track_id" to version.id.intValue)
        val locationTrack = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrack(
                dataType = DataType.STORED,
                id = rs.getIntId("official_id"),
                alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
                sourceId = null,
                externalId = rs.getOidOrNull("external_id"),
                trackNumberId = rs.getIntId("track_number_id"),
                name = AlignmentName(rs.getString("name")),
                description = rs.getFreeText("description"),
                type = rs.getEnum("type"),
                state = rs.getEnum("state"),
                boundingBox = rs.getBboxOrNull("bounding_box"),
                length = rs.getDouble("length"),
                segmentCount = rs.getInt("segment_count"),
                draft = rs.getIntIdOrNull<LocationTrack>("draft_id")?.let { id -> Draft(id) },
                version = rs.getVersion("official_version", "draft_version"),
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
        })
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, locationTrack.id)
        return locationTrack
    }

    @Transactional
    override fun insert(newItem: LocationTrack): RowVersion<LocationTrack> {
        val sql = """
            insert into layout.location_track(
              track_number_id,
              external_id,
              alignment_id,
              alignment_version,
              name,
              description,
              type,
              state,
              draft, 
              draft_of_location_track_id,            
              duplicate_of_location_track_id,
              topological_connectivity,
              topology_start_switch_id,
              topology_start_switch_joint_number,
              topology_end_switch_id,
              topology_end_switch_joint_number
            ) 
            values (
              :track_number_id,
              :external_id,
              :alignment_id,
              :alignment_version,
              :name,
              :description,
              :type::layout.track_type,
              :state::layout.state,
              :draft, 
              :draft_of_location_track_id,            
              :duplicate_of_location_track_id,
              :topological_connectivity::layout.track_topological_connectivity_type,
              :topology_start_switch_id,
              :topology_start_switch_joint_number,
              :topology_end_switch_id,
              :topology_end_switch_joint_number
            ) 
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to newItem.trackNumberId.intValue,
            "external_id" to newItem.externalId?.stringValue,
            "alignment_id" to (newItem.alignmentVersion?.id?.intValue
                ?: throw IllegalStateException("LocationTrack in DB needs an alignment")),
            "alignment_version" to newItem.alignmentVersion.version,
            "name" to newItem.name,
            "description" to newItem.description,
            "type" to newItem.type.name,
            "state" to newItem.state.name,
            "draft" to (newItem.draft != null),
            "draft_of_location_track_id" to draftOfId(newItem.id, newItem.draft)?.intValue,
            "duplicate_of_location_track_id" to newItem.duplicateOf?.intValue,
            "topological_connectivity" to newItem.topologicalConnectivity.name,
            "topology_start_switch_id" to newItem.topologyStartSwitch?.switchId?.intValue,
            "topology_start_switch_joint_number" to newItem.topologyStartSwitch?.jointNumber?.intValue,
            "topology_end_switch_id" to newItem.topologyEndSwitch?.switchId?.intValue,
            "topology_end_switch_joint_number" to newItem.topologyEndSwitch?.jointNumber?.intValue
        )

        jdbcTemplate.setUser()
        val version: RowVersion<LocationTrack> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, LocationTrack::class, version)
        return version
    }

    @Transactional
    override fun update(updatedItem: LocationTrack): RowVersion<LocationTrack> {
        val rowId = toDbId(updatedItem.draft?.draftRowId ?: updatedItem.id)
        val sql = """
            update layout.location_track
            set
              track_number_id = :track_number_id,
              external_id = :external_id,
              alignment_id = :alignment_id,
              alignment_version = :alignment_version,
              name = :name,
              description = :description,
              type = :type::layout.track_type,
              state = :state::layout.state,
              draft = :draft,
              draft_of_location_track_id = :draft_of_location_track_id,
              duplicate_of_location_track_id = :duplicate_of_location_track_id,
              topological_connectivity = :topological_connectivity::layout.track_topological_connectivity_type,
              topology_start_switch_id = :topology_start_switch_id,
              topology_start_switch_joint_number = :topology_start_switch_joint_number,
              topology_end_switch_id = :topology_end_switch_id,
              topology_end_switch_joint_number = :topology_end_switch_joint_number
            where id = :id
            returning id, version 
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "external_id" to updatedItem.externalId?.stringValue,
            "alignment_id" to (updatedItem.alignmentVersion?.id?.intValue
                ?: throw IllegalStateException("LocationTrack in DB needs an alignment")),
            "alignment_version" to updatedItem.alignmentVersion.version,
            "name" to updatedItem.name,
            "description" to updatedItem.description,
            "type" to updatedItem.type.name,
            "state" to updatedItem.state.name,
            "draft" to (updatedItem.draft != null),
            "draft_of_location_track_id" to draftOfId(updatedItem.id, updatedItem.draft)?.intValue,
            "duplicate_of_location_track_id" to updatedItem.duplicateOf?.intValue,
            "topological_connectivity" to updatedItem.topologicalConnectivity.name,
            "topology_start_switch_id" to updatedItem.topologyStartSwitch?.switchId?.intValue,
            "topology_start_switch_joint_number" to updatedItem.topologyStartSwitch?.jointNumber?.intValue,
            "topology_end_switch_id" to updatedItem.topologyEndSwitch?.switchId?.intValue,
            "topology_end_switch_joint_number" to updatedItem.topologyEndSwitch?.jointNumber?.intValue,
        )
        jdbcTemplate.setUser()
        val result: RowVersion<LocationTrack> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get new version for Location Track")
        logger.daoAccess(AccessType.UPDATE, LocationTrack::class, rowId)
        return result
    }

    fun fetchVersionsNear(publishType: PublishType, bbox: BoundingBox): List<RowVersion<LocationTrack>> {
        val sql = """
            select
              distinct lt.row_id, lt.row_version
            from layout.location_track_publication_view lt
            inner join layout.segment s on lt.alignment_id = s.alignment_id
              and postgis.st_intersects(
                postgis.st_makeenvelope (
                  :x_min, :y_min,
                  :x_max, :y_max,
                  :layout_srid
                ),
                s.bounding_box
              )
            where :publication_state = any(lt.publication_states) 
              and lt.state != 'DELETED'
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "publication_state" to publishType.name,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchPublicationInformation(publicationId: IntId<Publication>): List<LocationTrackPublishCandidate> {
        val sql = """
          select 
            location_track_version.id, 
            location_track_version.change_time, 
            location_track_version.name, 
            location_track_version.track_number_id
          from publication.location_track published_location_track
            left join layout.location_track_version
              on published_location_track.location_track_id = location_track_version.id 
                and published_location_track.location_track_version = location_track_version.version
          where publication_id = :id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf(
                "id" to publicationId.intValue,
            )
        )
        { rs, _ ->
            LocationTrackPublishCandidate(
                id = rs.getIntId("id"),
                draftChangeTime = rs.getInstant("change_time"),
                name = AlignmentName(rs.getString("name")),
                trackNumberId = rs.getIntId("track_number_id"),
                duplicateOf = null,
            )
        }.also { logger.daoAccess(AccessType.FETCH, Publication::class, publicationId) }
    }

    fun fetchVersions(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
    ): List<RowVersion<LocationTrack>> {
        val sql = """
            select lt.row_id, lt.row_version 
            from layout.location_track_publication_view lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and :publication_state = any(lt.publication_states)
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "publication_state" to publishType.name,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }
}
