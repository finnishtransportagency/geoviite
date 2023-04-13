package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_LAYOUT_LOCATION_TRACK
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_LOCATION_TRACK
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional(readOnly = true)
@Component
class LocationTrackDao(jdbcTemplateParam: NamedParameterJdbcTemplate?)
    : DraftableDaoBase<LocationTrack>(jdbcTemplateParam, LAYOUT_LOCATION_TRACK) {

    fun fetchDuplicates(id: IntId<LocationTrack>, publicationState: PublishType): List<LocationTrackDuplicate> {
        val sql = """
            select
              official_id,
              external_id, 
              name
            from layout.location_track_publication_view
            where duplicate_of_location_track_id = :id
              and :publication_state = any(publication_states)
              and state != 'DELETED'
        """.trimIndent()
        val params = mapOf("id" to id.intValue, "publication_state" to publicationState.name)
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
    override fun fetch(version: RowVersion<LocationTrack>): LocationTrack {
        val sql = """
            select 
              ltv.id as row_id,
              ltv.version as row_version,
              coalesce(ltv.draft_of_location_track_id, ltv.id) official_id, 
              case when ltv.draft then ltv.id end as draft_id,
              ltv.alignment_id,
              ltv.alignment_version,
              ltv.track_number_id, 
              ltv.external_id, 
              ltv.name, 
              ltv.description, 
              ltv.type, 
              ltv.state, 
              postgis.st_astext(av.bounding_box) as bounding_box,
              av.length,
              av.segment_count,            
              ltv.duplicate_of_location_track_id,
              ltv.topological_connectivity,
              ltv.topology_start_switch_id,
              ltv.topology_start_switch_joint_number,
              ltv.topology_end_switch_id,
              ltv.topology_end_switch_joint_number
            from layout.location_track_version ltv
              left join layout.alignment_version av on ltv.alignment_id = av.id and ltv.alignment_version = av.version
            where ltv.id = :id
              and ltv.version = :version
              and ltv.deleted = false
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue,
            "version" to version.version,
        )
        val locationTrack = getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ ->
            LocationTrack(
                dataType = DataType.STORED,
                id = rs.getIntId("official_id"),
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
                draft = rs.getIntIdOrNull<LocationTrack>("draft_id")?.let { id -> Draft(id) },
                version = rs.getRowVersion("row_id", "row_version"),
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
    override fun insert(newItem: LocationTrack): DaoResponse<LocationTrack> {
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
            returning 
              coalesce(draft_of_location_track_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to newItem.trackNumberId.intValue,
            "external_id" to newItem.externalId,
            "alignment_id" to newItem.getAlignmentVersionOrThrow().id.intValue,
            "alignment_version" to newItem.getAlignmentVersionOrThrow().version,
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
        val response: DaoResponse<LocationTrack> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, LocationTrack::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: LocationTrack): DaoResponse<LocationTrack> {
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
            returning 
              coalesce(draft_of_location_track_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val params = mapOf(
            "id" to rowId.intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "external_id" to updatedItem.externalId,
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
        val response: DaoResponse<LocationTrack> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Location Track")
        logger.daoAccess(AccessType.UPDATE, LocationTrack::class, rowId)
        return response
    }

    override fun fetchVersions(publicationState: PublishType, includeDeleted: Boolean) =
        fetchVersions(publicationState, includeDeleted, null)

    fun fetchVersions(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
    ): List<RowVersion<LocationTrack>> {
        val sql = """
            select lt.row_id, lt.row_version 
            from layout.location_track_publication_view lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and :publication_state = any(lt.publication_states)
              and (:include_deleted = true or state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchOfficialVersionsAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): List<RowVersion<LocationTrack>> {
        val sql = """
            select distinct on (id)
              case when deleted then null else id end as row_id, 
              case when deleted then null else version end as row_version
            from layout.location_track_version
            where track_number_id = :track_number_id 
              and draft = false
              and change_time <= :moment
            order by id, version desc
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId.intValue,
            "moment" to Timestamp.from(moment),
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun fetchVersionsNear(publicationState: PublishType, bbox: BoundingBox): List<RowVersion<LocationTrack>> {
        val sql = """
            select
              distinct lt.row_id, lt.row_version
            from layout.location_track_publication_view lt
            inner join layout.segment_version s on lt.alignment_id = s.alignment_id 
              and lt.alignment_version = s.alignment_version
            inner join layout.segment_geometry sg on s.geometry_id = sg.id
              and postgis.st_intersects(
                postgis.st_makeenvelope (
                  :x_min, :y_min,
                  :x_max, :y_max,
                  :layout_srid
                ),
                sg.bounding_box
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
            "publication_state" to publicationState.name,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun <T> fetchProfileInfoForSegmentsInBoundingBox(
        publishType: PublishType,
        bbox: BoundingBox
    ): List<MapSegmentProfileInfo<T>> {
        //language=SQL
        val sql = """
            select
              location_track.id,
              location_track.version,
              segment_version.alignment_id,
              segment_version.segment_index,
              plan.vertical_coordinate_system
              from layout.location_track
                inner join layout.segment_version on
                    location_track.alignment_id = segment_version.alignment_id and
                    location_track.alignment_version = segment_version.alignment_version
                inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                left join geometry.alignment on alignment.id = segment_version.geometry_alignment_id
                left join geometry.plan on alignment.plan_id = plan.id
              where postgis.st_intersects(
                  postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                  segment_geometry.bounding_box
                )
                and location_track.draft = :is_draft
              order by segment_version.segment_index
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "is_draft" to (publishType == PublishType.DRAFT)
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            MapSegmentProfileInfo(
                rowVersion = rs.getRowVersion("id", "version"),
                segmentId = rs.getIndexedId("alignment_id", "segment_index"),
                hasProfile = rs.getEnumOrNull<VerticalCoordinateSystem>("vertical_coordinate_system") != null
            )
        }
    }

    fun fetchPlanInfo(
        publishType: PublishType,
        ids: List<IntId<LocationTrack>>
    ): List<MapSegmentPlanInfo<LocationTrack>> {
        if (ids.isEmpty()) return emptyList()

        //language=SQL
        val sql = """
            select
              segment_version.alignment_id,
              segment_version.segment_index,
              location_track.id,
              location_track.version,
              alignment.plan_id
              from layout.location_track
                inner join layout.segment_version on
                    location_track.alignment_id = segment_version.alignment_id and
                    location_track.alignment_version = segment_version.alignment_version
                inner join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                inner join geometry.alignment on alignment.id = segment_version.geometry_alignment_id
              where location_track.id in (:ids)
                and location_track.draft = :is_draft
              order by alignment_id, segment_index
        """.trimIndent()

        val params = mapOf(
            "ids" to ids.map { it.intValue },
            "is_draft" to (publishType == PublishType.DRAFT)
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            MapSegmentPlanInfo(
                rowVersion = rs.getRowVersion("id", "version"),
                segmentId = rs.getIndexedId("alignment_id", "segment_index"),
                planId = rs.getIntId("plan_id")
            )
        }
    }
}
