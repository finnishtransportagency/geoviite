package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.configuration.CACHE_COMMON_LOCATION_TRACK_OWNER
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getDaoResponse
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getFreeText
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getJointNumber
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

const val LOCATIONTRACK_CACHE_SIZE = 10000L

@Transactional(readOnly = true)
@Component
class LocationTrackDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) : LayoutAssetDao<LocationTrack>(
    jdbcTemplateParam,
    LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK,
    cacheEnabled,
    LOCATIONTRACK_CACHE_SIZE,
) {

    fun fetchDuplicateVersions(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
        includeDeleted: Boolean = false,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql = """
            select row_id, row_version
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where duplicate_of_location_track_id = :id
              and (:include_deleted or state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "id" to id.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
            "include_deleted" to includeDeleted,
        )
        val versions = jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion<LocationTrack>("row_id", "row_version")
        }
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, id)
        return versions
    }

    fun findOfficialNameDuplicates(
        layoutBranch: LayoutBranch,
        names: List<AlignmentName>,
    ): Map<AlignmentName, List<LayoutDaoResponse<LocationTrack>>> {
        return if (names.isEmpty()) {
            emptyMap()
        } else {
            val sql = """
                select official_id, row_id, row_version, name
                from layout.location_track_in_layout_context('OFFICIAL', :design_id)
                where name in (:names)
                  and state != 'DELETED'
            """.trimIndent()
            val params = mapOf("names" to names, "design_id" to layoutBranch.designId?.intValue)
            val found = jdbcTemplate.query<Pair<AlignmentName, LayoutDaoResponse<LocationTrack>>>(sql, params) { rs, _ ->
                val daoResponse = rs.getDaoResponse<LocationTrack>("official_id", "row_id", "row_version")
                val name = rs.getString("name").let(::AlignmentName)
                name to daoResponse
            }
            // Ensure that the result contains all asked-for names, even if there are no matches
            names.associateWith { n -> found.filter { (name, _) -> name == n }.map { (_, v) -> v } }
        }
    }

    override fun fetchInternal(version: LayoutRowVersion<LocationTrack>): LocationTrack {
        val sql = """
            select 
              ltv.id as row_id,
              ltv.version as row_version,
              ltv.official_row_id,
              ltv.design_row_id,
              ltv.design_id,
              ltv.draft,
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
              ltv.owner_id,
              (
                select
                  array_agg(distinct switch_id)
                  from layout.segment_version sv
                  where sv.alignment_id = ltv.alignment_id
                    and sv.alignment_version = ltv.alignment_version
                    and sv.switch_id is not null
              ) as segment_switch_ids
            from layout.location_track_version ltv
              left join layout.alignment_version av on ltv.alignment_id = av.id and ltv.alignment_version = av.version
            where ltv.id = :id
              and ltv.version = :version
              and ltv.deleted = false
        """.trimIndent()

        val params = mapOf(
            "id" to version.rowId.intValue,
            "version" to version.version,
        )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLocationTrack(rs) }).also {
            logger.daoAccess(AccessType.FETCH, LocationTrack::class, version)
        }
    }

    override fun preloadCache() {
        val sql = """
            select 
              lt.id as row_id,
              lt.version as row_version,
              lt.official_row_id, 
              lt.design_row_id,
              lt.design_id,
              lt.draft,
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
              lt.owner_id,
              (
                select
                  array_agg(distinct switch_id)
                  from layout.segment_version sv
                  where sv.alignment_id = lt.alignment_id
                    and sv.alignment_version = lt.alignment_version
                    and sv.switch_id is not null
              ) as segment_switch_ids
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
        segmentSwitchIds = rs.getIntIdArray("segment_switch_ids"),
        contextData = rs.getLayoutContextData(
            "official_row_id",
            "design_row_id",
            "design_id",
            "row_id",
            "row_version",
            "draft",
        ),
    )

    @Transactional
    override fun insert(newItem: LocationTrack): LayoutDaoResponse<LocationTrack> {
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
              official_row_id,
              design_row_id,
              design_id,
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
              :state::layout.location_track_state,
              :draft, 
              :official_row_id,
              :design_row_id,
              :design_id,
              :duplicate_of_location_track_id,
              :topological_connectivity::layout.track_topological_connectivity_type,
              :topology_start_switch_id,
              :topology_start_switch_joint_number,
              :topology_end_switch_id,
              :topology_end_switch_joint_number,
              :owner_id
            ) 
            returning 
              coalesce(official_row_id, id) as official_id,
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
            "draft" to newItem.isDraft,
            "official_row_id" to newItem.contextData.officialRowId?.intValue,
            "design_row_id" to newItem.contextData.designRowId?.intValue,
            "design_id" to newItem.contextData.designId?.intValue,
            "duplicate_of_location_track_id" to newItem.duplicateOf?.intValue,
            "topological_connectivity" to newItem.topologicalConnectivity.name,
            "topology_start_switch_id" to newItem.topologyStartSwitch?.switchId?.intValue,
            "topology_start_switch_joint_number" to newItem.topologyStartSwitch?.jointNumber?.intValue,
            "topology_end_switch_id" to newItem.topologyEndSwitch?.switchId?.intValue,
            "topology_end_switch_joint_number" to newItem.topologyEndSwitch?.jointNumber?.intValue,
            "owner_id" to newItem.ownerId.intValue,
        )

        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<LocationTrack> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        logger.daoAccess(AccessType.INSERT, LocationTrack::class, response)
        return response
    }

    @Transactional
    override fun update(updatedItem: LocationTrack): LayoutDaoResponse<LocationTrack> {
        val rowId = requireNotNull(updatedItem.contextData.rowId) {
            "Cannot update a row that doesn't have a DB ID: kmPost=$updatedItem"
        }
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
              state = :state::layout.location_track_state,
              draft = :draft,
              official_row_id = :official_row_id,
              design_row_id = :design_row_id,
              design_id = :design_id,
              duplicate_of_location_track_id = :duplicate_of_location_track_id,
              topological_connectivity = :topological_connectivity::layout.track_topological_connectivity_type,
              topology_start_switch_id = :topology_start_switch_id,
              topology_start_switch_joint_number = :topology_start_switch_joint_number,
              topology_end_switch_id = :topology_end_switch_id,
              topology_end_switch_joint_number = :topology_end_switch_joint_number,
              owner_id = :owner_id
            where id = :id
            returning 
              coalesce(official_row_id, design_row_id, id) as official_id,
              id as row_id,
              version as row_version
        """.trimIndent()
        val alignmentVersion = updatedItem.getAlignmentVersionOrThrow()
        val params = mapOf(
            "id" to rowId.intValue,
            "track_number_id" to updatedItem.trackNumberId.intValue,
            "external_id" to updatedItem.externalId,
            "alignment_id" to alignmentVersion.id.intValue,
            "alignment_version" to alignmentVersion.version,
            "name" to updatedItem.name,
            "description_base" to updatedItem.descriptionBase,
            "description_suffix" to updatedItem.descriptionSuffix.name,
            "type" to updatedItem.type.name,
            "state" to updatedItem.state.name,
            "draft" to updatedItem.isDraft,
            "official_row_id" to updatedItem.contextData.officialRowId?.intValue,
            "design_row_id" to updatedItem.contextData.designRowId?.intValue,
            "design_id" to updatedItem.contextData.designId?.intValue,
            "duplicate_of_location_track_id" to updatedItem.duplicateOf?.intValue,
            "topological_connectivity" to updatedItem.topologicalConnectivity.name,
            "topology_start_switch_id" to updatedItem.topologyStartSwitch?.switchId?.intValue,
            "topology_start_switch_joint_number" to updatedItem.topologyStartSwitch?.jointNumber?.intValue,
            "topology_end_switch_id" to updatedItem.topologyEndSwitch?.switchId?.intValue,
            "topology_end_switch_joint_number" to updatedItem.topologyEndSwitch?.jointNumber?.intValue,
            "owner_id" to updatedItem.ownerId.intValue
        )
        jdbcTemplate.setUser()
        val response: LayoutDaoResponse<LocationTrack> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        } ?: throw IllegalStateException("Failed to get new version for Location Track")
        logger.daoAccess(AccessType.UPDATE, LocationTrack::class, response)
        return response
    }

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null)

    fun list(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ): List<LocationTrack> = fetchVersions(layoutContext, includeDeleted, trackNumberId, names)
        .map { r -> fetch(r.rowVersion) }

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ): List<LayoutDaoResponse<LocationTrack>> {
        val sql = """
            select lt.official_id, lt.row_id, lt.row_version 
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and (:names = '' or lower(lt.name) = any(string_to_array(:names, ',')::varchar[]))
              and (:include_deleted = true or state != 'DELETED')
        """.trimIndent()
        val params = mapOf(
            "track_number_id" to trackNumberId?.intValue,
            "publication_state" to layoutContext.state.name,
            "design_id" to layoutContext.branch.designId?.intValue,
            "include_deleted" to includeDeleted,
            "names" to names.map { name -> name.toString().lowercase() }.joinToString(","),
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getDaoResponse("official_id", "row_id", "row_version")
        }
    }

    fun listNear(context: LayoutContext, bbox: BoundingBox): List<LocationTrack> =
        fetchVersionsNear(context, bbox).map(::fetch)

    fun fetchVersionsNear(context: LayoutContext, bbox: BoundingBox): List<LayoutRowVersion<LocationTrack>> {
        val sql = """
            select row_id, row_version
              from (
                select coalesce(official_row_id, design_row_id, id) as official_id, id as row_id, version as row_version
                  from layout.location_track
                    join (
                    select distinct alignment_id, alignment_version
                      from layout.segment_version
                        join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                      where postgis.st_intersects(
                          postgis.st_makeenvelope(
                              :x_min, :y_min,
                              :x_max, :y_max,
                              :layout_srid
                            ),
                          segment_geometry.bounding_box
                        )
                  ) in_box using (alignment_id, alignment_version)
              ) location_track
                join lateral (
                select *
                  from layout.location_track_in_layout_context(:publication_state::layout.publication_state,
                                                               :design_id, location_track.official_id)
                  where state != 'DELETED') in_layout_context using (row_id, row_version);
        """.trimIndent()

        val params = mapOf(
            "x_min" to bbox.min.x,
            "y_min" to bbox.min.y,
            "x_max" to bbox.max.x,
            "y_max" to bbox.max.y,
            "layout_srid" to LAYOUT_SRID.code,
            "publication_state" to context.state.name,
            "design_id" to context.branch.designId?.intValue,
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("row_id", "row_version")
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

    fun fetchOnlyDraftVersions(
        branch: LayoutBranch,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql = """
            select id, version
            from layout.location_track
            where draft
              and (:includeDeleted or state != 'DELETED')
              and (:trackNumberId::int is null or track_number_id = :trackNumberId)
              and design_id is not distinct from :design_id
        """.trimIndent()
        val params = mapOf(
            "includeDeleted" to includeDeleted,
            "trackNumberId" to trackNumberId?.intValue,
            "design_id" to branch.designId?.intValue,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion<LocationTrack>("id", "version")
        }.also { ids ->
            logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids)
        }
    }

    fun fetchVersionsForPublication(
        branch: LayoutBranch,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        trackIdsToPublish: List<IntId<LocationTrack>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<LayoutDaoResponse<LocationTrack>>> {
        if (trackNumberIds.isEmpty()) return emptyMap()

        val sql = """
            select track.track_number_id, track.official_id, track.row_id, track.row_version
            from (
            select state, track_number_id, official_id, row_id, row_version
              from layout.location_track_in_layout_context('OFFICIAL', :design_id) official
              where (official_id in (:track_ids_to_publish)) is distinct from true 
            union all
            select state, track_number_id, official_id, row_id, row_version
              from layout.location_track_in_layout_context('DRAFT', :design_id) draft
              where official_id in (:track_ids_to_publish)
            ) track
            where track_number_id in (:track_number_ids)
              and track.state != 'DELETED'
            order by track.track_number_id, track.row_id
        """.trimIndent()
        val params = mapOf(
            "track_number_ids" to trackNumberIds.map { id -> id.intValue },
            "design_id" to branch.designId?.intValue,
            // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is distinct from true" checks
            // explicitly for false or null, since "foo in (null)" in SQL is null
            "track_ids_to_publish" to (trackIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) }),
        )
        val versions = jdbcTemplate.query(sql, params) { rs, _ ->
            val trackNumberId = rs.getIntId<TrackLayoutTrackNumber>("track_number_id")
            val daoResponse = rs.getDaoResponse<LocationTrack>("official_id", "row_id", "row_version")
            trackNumberId to daoResponse
        }
        return trackNumberIds.associateWith { trackNumberId ->
            versions.filter { (tnId, _) -> tnId == trackNumberId }.map { (_, trackVersions) -> trackVersions }
        }
    }
}
