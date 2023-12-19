package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_COMMON_LOCATION_TRACK_OWNER
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_LOCATION_TRACK
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

const val LOCATIONTRACK_CACHE_SIZE = 10000L

@Transactional(readOnly = true)
@Component
class LocationTrackDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) : DraftableDaoBase<LocationTrack>(jdbcTemplateParam, LAYOUT_LOCATION_TRACK, cacheEnabled, LOCATIONTRACK_CACHE_SIZE) {

    fun fetchDuplicates(id: IntId<LocationTrack>, publicationState: PublishType? = null): List<RowVersion<LocationTrack>> {
        val sql = """
            select row_id, row_version
            from layout.location_track_publication_view
            where duplicate_of_location_track_id = :id
              and (:publication_state::varchar is null or :publication_state = any(publication_states))
              and state != 'DELETED'
        """.trimIndent()
        val params = mapOf("id" to id.intValue, "publication_state" to publicationState?.name)
        val locationTracks = jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion<LocationTrack>("row_id", "row_version")
        }
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, id)
        return locationTracks
    }

    override fun fetchInternal(version: RowVersion<LocationTrack>): LocationTrack {
        val sql = """
            select 
              ltv.id as row_id,
              ltv.version as row_version,
              coalesce(ltv.draft_of_location_track_id, ltv.id) as official_id, 
              case when ltv.draft then ltv.id end as draft_id,
              ltv.alignment_id,
              ltv.alignment_version,
              ltv.track_number_id, 
              ltv.external_id, 
              ltv.name, 
              ltv.description_base,
              ltv.description_suffix,
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
              ltv.topology_end_switch_joint_number,
              ltv.owner_id
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
        return getOne(version.id, jdbcTemplate.query(sql, params) { rs, _ -> getLocationTrack(rs) }).also {
            logger.daoAccess(AccessType.FETCH, LocationTrack::class, version)
        }
    }

    override fun preloadCache() {
        val sql = """
            select 
              lt.id as row_id,
              lt.version as row_version,
              coalesce(lt.draft_of_location_track_id, lt.id) as official_id, 
              case when lt.draft then lt.id end as draft_id,
              lt.alignment_id,
              lt.alignment_version,
              lt.track_number_id, 
              lt.external_id, 
              lt.name, 
              lt.description_base,
              lt.description_suffix,
              lt.type, 
              lt.state, 
              postgis.st_astext(av.bounding_box) as bounding_box,
              av.length,
              av.segment_count,            
              lt.duplicate_of_location_track_id,
              lt.topological_connectivity,
              lt.topology_start_switch_id,
              lt.topology_start_switch_joint_number,
              lt.topology_end_switch_id,
              lt.topology_end_switch_joint_number,
              lt.owner_id
            from layout.location_track lt
              left join layout.alignment_version av on lt.alignment_id = av.id and lt.alignment_version = av.version
        """.trimIndent()

        val tracks = jdbcTemplate
            .query(sql, mapOf<String, Any>()) { rs, _ -> getLocationTrack(rs) }
            .associateBy(LocationTrack::version)
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, tracks.keys)
        cache.putAll(tracks)
    }

    private fun getLocationTrack(rs: ResultSet): LocationTrack = LocationTrack(
        dataType = DataType.STORED,
        id = rs.getIntId("official_id"),
        alignmentVersion = rs.getRowVersion("alignment_id", "alignment_version"),
        sourceId = null,
        externalId = rs.getOidOrNull("external_id"),
        trackNumberId = rs.getIntId("track_number_id"),
        name = rs.getString("name").let(::AlignmentName),
        descriptionBase = rs.getFreeText("description_base"),
        descriptionSuffix = rs.getEnum<DescriptionSuffixType>("description_suffix"),
        type = rs.getEnum("type"),
        state = rs.getEnum("state"),
        boundingBox = rs.getBboxOrNull("bounding_box"),
        length = rs.getDouble("length"),
        segmentCount = rs.getInt("segment_count"),
        draft = rs.getIntIdOrNull<LocationTrack>("draft_id")?.let { id -> Draft(id) },
        version = rs.getRowVersion("row_id", "row_version"),
        duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
        topologicalConnectivity = rs.getEnum("topological_connectivity"),
        ownerId = rs.getIntId("owner_id"),
        topologyStartSwitch = rs.getIntIdOrNull<TrackLayoutSwitch>("topology_start_switch_id")?.let { id ->
            TopologyLocationTrackSwitch(
                id,
                rs.getJointNumber("topology_start_switch_joint_number"),
            )
        },
        topologyEndSwitch = rs.getIntIdOrNull<TrackLayoutSwitch>("topology_end_switch_id")?.let { id ->
            TopologyLocationTrackSwitch(
                id,
                rs.getJointNumber("topology_end_switch_joint_number"),
            )
        },
    )

    @Transactional
    override fun insert(newItem: LocationTrack): DaoResponse<LocationTrack> {
        val sql = """
            insert into layout.location_track(
              track_number_id,
              external_id,
              alignment_id,
              alignment_version,
              name,
              description_base,
              description_suffix,
              type,
              state,
              draft, 
              draft_of_location_track_id,            
              duplicate_of_location_track_id,
              topological_connectivity,
              topology_start_switch_id,
              topology_start_switch_joint_number,
              topology_end_switch_id,
              topology_end_switch_joint_number,
              owner_id
            ) 
            values (
              :track_number_id,
              :external_id,
              :alignment_id,
              :alignment_version,
              :name,
              :description_base,
              :description_suffix::layout.location_track_description_suffix,
              :type::layout.track_type,
              :state::layout.state,
              :draft, 
              :draft_of_location_track_id,            
              :duplicate_of_location_track_id,
              :topological_connectivity::layout.track_topological_connectivity_type,
              :topology_start_switch_id,
              :topology_start_switch_joint_number,
              :topology_end_switch_id,
              :topology_end_switch_joint_number,
              :owner_id
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
            "description_base" to newItem.descriptionBase,
            "description_suffix" to newItem.descriptionSuffix.name,
            "type" to newItem.type.name,
            "state" to newItem.state.name,
            "draft" to (newItem.draft != null),
            "draft_of_location_track_id" to draftOfId(newItem.id, newItem.draft)?.intValue,
            "duplicate_of_location_track_id" to newItem.duplicateOf?.intValue,
            "topological_connectivity" to newItem.topologicalConnectivity.name,
            "topology_start_switch_id" to newItem.topologyStartSwitch?.switchId?.intValue,
            "topology_start_switch_joint_number" to newItem.topologyStartSwitch?.jointNumber?.intValue,
            "topology_end_switch_id" to newItem.topologyEndSwitch?.switchId?.intValue,
            "topology_end_switch_joint_number" to newItem.topologyEndSwitch?.jointNumber?.intValue,
            "owner_id" to newItem.ownerId.intValue,
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
              description_base = :description_base,
              description_suffix = :description_suffix::layout.location_track_description_suffix,
              type = :type::layout.track_type,
              state = :state::layout.state,
              draft = :draft,
              draft_of_location_track_id = :draft_of_location_track_id,
              duplicate_of_location_track_id = :duplicate_of_location_track_id,
              topological_connectivity = :topological_connectivity::layout.track_topological_connectivity_type,
              topology_start_switch_id = :topology_start_switch_id,
              topology_start_switch_joint_number = :topology_start_switch_joint_number,
              topology_end_switch_id = :topology_end_switch_id,
              topology_end_switch_joint_number = :topology_end_switch_joint_number,
              owner_id = :owner_id
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
            "description_base" to updatedItem.descriptionBase,
            "description_suffix" to updatedItem.descriptionSuffix.name,
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
            "owner_id" to updatedItem.ownerId.intValue
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

    fun list(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ): List<LocationTrack> = fetchVersions(publicationState, includeDeleted, trackNumberId, names).map(::fetch)

    fun fetchVersions(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ): List<RowVersion<LocationTrack>> {
        val sql = """
            select lt.row_id, lt.row_version 
            from layout.location_track_publication_view lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and (:names = '' or lower(lt.name) = any(string_to_array(:names, ',')::varchar[]))
              and :publication_state = any(lt.publication_states)
              and (:include_deleted = true or state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "publication_state" to publicationState.name,
            "include_deleted" to includeDeleted,
            "names" to names.map { name -> name.toString().lowercase() }.joinToString(","),
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
    fun listNear(publicationState: PublishType, bbox: BoundingBox): List<LocationTrack> =
        fetchVersionsNear(publicationState, bbox).map(::fetch)

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

    @Cacheable(CACHE_COMMON_LOCATION_TRACK_OWNER, sync = true)
    fun fetchLocationTrackOwners(): List<LocationTrackOwner> {
        val sql = """
        select
            id,
            name
        from common.location_track_owner
        order by sort_priority, name
    """.trimIndent()

        val locationTrackOwners = jdbcTemplate.query(sql) { rs, _ ->
            LocationTrackOwner(
                id = rs.getIntId("id"),
                name = MetaDataName(rs.getString("name")),
            )
        }
        logger.daoAccess(AccessType.FETCH, LocationTrackOwner::class, locationTrackOwners.map { it.id })
        return locationTrackOwners
    }

    fun fetchOnlyDraftVersions(includeDeleted: Boolean, trackNumberId: IntId<TrackLayoutTrackNumber>? = null): List<RowVersion<LocationTrack>> {
        val sql = """
            select id, version
            from layout.location_track
            where draft
              and (:includeDeleted or state != 'DELETED')
              and (:trackNumberId::int is null or track_number_id = :trackNumberId)
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("includeDeleted" to includeDeleted, "trackNumberId" to trackNumberId?.intValue)
        ) { rs, _ ->
            rs.getRowVersion<LocationTrack>("id", "version")
        }.also { ids ->
            logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids)
        }
    }
}
