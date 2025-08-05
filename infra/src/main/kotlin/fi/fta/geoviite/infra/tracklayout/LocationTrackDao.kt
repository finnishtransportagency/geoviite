package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.configuration.CACHE_COMMON_LOCATION_TRACK_OWNER
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationTarget
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getFreeText
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getLayoutRowVersionOrNull
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val LOCATIONTRACK_CACHE_SIZE = 10000L

data class LocationTrackDependencyVersions(
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val startSwitchVersion: LayoutRowVersion<LayoutSwitch>?,
    val endSwitchVersion: LayoutRowVersion<LayoutSwitch>?,
)

@Component
class LocationTrackDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val alignmentDao: LayoutAlignmentDao,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<LocationTrack, LocationTrackGeometry>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK,
        cacheEnabled,
        LOCATIONTRACK_CACHE_SIZE,
    ),
    IExternalIdDao<LocationTrack> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.location_track_external_id",
        "layout.location_track_external_id_version",
    ),
    IExternallyIdentifiedLayoutAssetDao<LocationTrack> {

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<LocationTrack>): LocationTrackGeometry =
        alignmentDao.fetch(rowVersion)

    fun fetchDuplicates(layoutContext: LayoutContext, id: IntId<LocationTrack>, includeDeleted: Boolean = false) =
        fetchMany(fetchDuplicateVersions(layoutContext, id, includeDeleted))

    fun fetchDuplicateVersions(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
        includeDeleted: Boolean = false,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql =
            """
            select id, design_id, draft, version
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where duplicate_of_location_track_id = :id
              and (:include_deleted or state != 'DELETED')
        """
                .trimIndent()
        val params =
            mapOf(
                "id" to id.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
            )
        val versions =
            jdbcTemplate.query(sql, params) { rs, _ ->
                rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
            }
        logger.daoAccess(AccessType.FETCH, LocationTrack::class, id)
        return versions
    }

    fun findNameDuplicates(
        context: LayoutContext,
        names: List<AlignmentName>,
    ): Map<AlignmentName, List<LayoutRowVersion<LocationTrack>>> {
        return if (names.isEmpty()) {
            emptyMap()
        } else {
            val sql =
                """
                select id, design_id, draft, version, name
                from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where name in (:names)
                  and state != 'DELETED'
            """
                    .trimIndent()
            val params =
                mapOf(
                    "names" to names,
                    "publication_state" to context.state.name,
                    "design_id" to context.branch.designId?.intValue,
                )
            val found =
                jdbcTemplate.query<Pair<AlignmentName, LayoutRowVersion<LocationTrack>>>(sql, params) { rs, _ ->
                    val daoResponse = rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
                    val name = rs.getString("name").let(::AlignmentName)
                    name to daoResponse
                }
            // Ensure that the result contains all asked-for names, even if there are no matches
            names.associateWith { n -> found.filter { (name, _) -> name == n }.map { (_, v) -> v } }
        }
    }

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<LocationTrack>>
    ): Map<LayoutRowVersion<LocationTrack>, LocationTrack> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
            select 
              ltv.id,
              ltv.version,
              ltv.design_id,
              ltv.draft,
              ltv.design_asset_state,
              ltv.track_number_id, 
              ltv.name, 
              ltv.naming_scheme,
              ltv.name_free_text,
              ltv.name_specifier,
              ltv.description,
              ltv.description_base,
              ltv.description_suffix,
              ltv.type, 
              ltv.state, 
              postgis.st_astext(ltv.bounding_box) as bounding_box,
              ltv.length,
              ltv.segment_count,
              ltv.start_switch_id,
              ltv.end_switch_id,
              ltv.duplicate_of_location_track_id,
              ltv.topological_connectivity,
              ltv.owner_id,
              (
              select array_agg(switch_id order by switch_sort) from (
                select distinct on (lt_s_view.switch_id) lt_s_view.switch_id, lt_s_view.switch_sort
                  from layout.location_track_version_switch_view lt_s_view
                  where lt_s_view.location_track_id = ltv.id
                    and lt_s_view.location_track_layout_context_id = ltv.layout_context_id
                    and lt_s_view.location_track_version = ltv.version
                ) as switch_ids_unnest
              ) as switch_ids,
              ltv.origin_design_id
            from layout.location_track_version ltv
              inner join lateral
                (
                  select
                    unnest(:ids) id,
                    unnest(:layout_context_ids) layout_context_id,
                    unnest(:versions) version
                ) args on args.id = ltv.id and args.layout_context_id = ltv.layout_context_id and args.version = ltv.version
              where ltv.deleted = false
        """
                .trimIndent()

        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getLocationTrack(rs) }
            .associateBy { lt -> lt.getVersionOrThrow() }
            .also { logger.daoAccess(AccessType.FETCH, LocationTrack::class, versions) }
    }

    override fun preloadCache(): Int {
        val sql =
            """
            select 
              lt.id,
              lt.version,
              lt.design_id,
              lt.draft,
              lt.design_asset_state,
              lt.track_number_id, 
              lt.name, 
              lt.naming_scheme,
              lt.name_free_text,
              lt.name_specifier,
              lt.description,
              lt.description_base,
              lt.description_suffix,
              lt.type, 
              lt.state, 
              postgis.st_astext(lt.bounding_box) as bounding_box,
              lt.length,
              lt.segment_count,
              lt.start_switch_id,
              lt.end_switch_id,
              lt.duplicate_of_location_track_id,
              lt.topological_connectivity,
              lt.owner_id,
              (
              select array_agg(switch_id order by switch_sort) from (
                select distinct on (lt_s_view.switch_id) lt_s_view.switch_id, lt_s_view.switch_sort
                  from layout.location_track_version_switch_view lt_s_view
                  where lt_s_view.location_track_id = lt.id
                    and lt_s_view.location_track_layout_context_id = lt.layout_context_id
                    and lt_s_view.location_track_version = lt.version
                ) as switch_ids_unnest
              ) as switch_ids,
              lt.origin_design_id
            from layout.location_track lt
        """
                .trimIndent()

        val tracks =
            jdbcTemplate
                .query(sql) { rs, _ -> getLocationTrack(rs) }
                .associateBy { locationTrack -> requireNotNull(locationTrack.version) }

        logger.daoAccess(AccessType.FETCH, LocationTrack::class, tracks.keys)
        cache.putAll(tracks)

        return tracks.size
    }

    private fun getLocationTrack(rs: ResultSet): LocationTrack =
        LocationTrack(
            sourceId = null,
            trackNumberId = rs.getIntId("track_number_id"),
            name = rs.getString("name").let(::AlignmentName),
            nameStructure =
                LocationTrackNameStructure.of(
                    scheme = rs.getEnum("naming_scheme"),
                    freeText = rs.getString("name_free_text")?.let(::AlignmentName),
                    specifier = rs.getEnumOrNull<LocationTrackNameSpecifier>("name_specifier"),
                ),
            descriptionStructure =
                LocationTrackDescriptionStructure(
                    base = rs.getString("description_base").let(::LocationTrackDescriptionBase),
                    suffix = rs.getEnum("description_suffix"),
                ),
            description = rs.getFreeText("description"),
            type = rs.getEnum("type"),
            state = rs.getEnum("state"),
            boundingBox = rs.getBboxOrNull("bounding_box"),
            length = LineM(rs.getDouble("length")),
            segmentCount = rs.getInt("segment_count"),
            startSwitchId = rs.getIntIdOrNull("start_switch_id"),
            endSwitchId = rs.getIntIdOrNull("end_switch_id"),
            duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
            topologicalConnectivity = rs.getEnum("topological_connectivity"),
            ownerId = rs.getIntId("owner_id"),
            switchIds = rs.getIntIdArray("switch_ids"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )

    @Transactional
    override fun save(item: LocationTrack, params: LocationTrackGeometry): LayoutRowVersion<LocationTrack> {
        val id = item.id as? IntId ?: createId()
        val geometry = params

        val sql =
            """
            insert into layout.location_track(
              layout_context_id,
              id,
              track_number_id,
              name,
              naming_scheme,
              name_free_text,
              name_specifier,
              description,
              description_base,
              description_suffix,
              type,
              state,
              draft, 
              design_asset_state,
              design_id,
              duplicate_of_location_track_id,
              topological_connectivity,
              owner_id,
              origin_design_id,
              start_switch_id,
              end_switch_id,
              length,
              edge_count,
              segment_count,
              bounding_box
            ) 
            values (
              :layout_context_id,
              :id,
              :track_number_id,
              :name,
              :naming_scheme::layout.location_track_naming_scheme,
              :name_free_text,
              :name_specifier::layout.location_track_specifier,
              :description,
              :description_base,
              :description_suffix::layout.location_track_description_suffix,
              :type::layout.track_type,
              :state::layout.location_track_state,
              :draft, 
              :design_asset_state::layout.design_asset_state,
              :design_id,
              :duplicate_of_location_track_id,
              :topological_connectivity::layout.track_topological_connectivity_type,
              :owner_id,
              :origin_design_id,
              :start_switch_id,
              :end_switch_id,
              :length,
              :edge_count,
              :segment_count,
              postgis.st_polygonfromtext(:polygon_string, 3067)
            ) on conflict (id, layout_context_id) do update set
              track_number_id = excluded.track_number_id,
              name = excluded.name,
              naming_scheme = excluded.naming_scheme,
              name_free_text = excluded.name_free_text,
              name_specifier = excluded.name_specifier,
              description = excluded.description,
              description_base = excluded.description_base,
              description_suffix = excluded.description_suffix,
              type = excluded.type,
              state = excluded.state,
              design_asset_state = excluded.design_asset_state,
              duplicate_of_location_track_id = excluded.duplicate_of_location_track_id,
              topological_connectivity = excluded.topological_connectivity,
              owner_id = excluded.owner_id,
              origin_design_id = excluded.origin_design_id,
              start_switch_id = excluded.start_switch_id,
              end_switch_id = excluded.end_switch_id,
              length = excluded.length,
              edge_count = excluded.edge_count,
              segment_count = excluded.segment_count,
              bounding_box = excluded.bounding_box
            returning id, design_id, draft, version
        """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "track_number_id" to item.trackNumberId.intValue,
                "name" to item.name,
                "naming_scheme" to item.nameStructure.scheme.name,
                "name_free_text" to item.nameStructure.freeText,
                "name_specifier" to item.nameStructure.specifier?.name,
                "description" to item.description,
                "description_base" to item.descriptionStructure.base,
                "description_suffix" to item.descriptionStructure.suffix.name,
                "type" to item.type.name,
                "state" to item.state.name,
                "draft" to item.isDraft,
                "design_asset_state" to item.designAssetState?.name,
                "design_id" to item.contextData.designId?.intValue,
                "duplicate_of_location_track_id" to item.duplicateOf?.intValue,
                "topological_connectivity" to item.topologicalConnectivity.name,
                "owner_id" to item.ownerId.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
                "start_switch_id" to geometry.startSwitchLink?.id?.intValue,
                "end_switch_id" to geometry.endSwitchLink?.id?.intValue,
                "length" to geometry.length.distance,
                "edge_count" to geometry.edges.size,
                "segment_count" to geometry.segments.size,
                "polygon_string" to geometry.boundingBox?.let(BoundingBox::polygonFromCorners)?.toWkt(),
            )

        jdbcTemplate.setUser()
        val response: LayoutRowVersion<LocationTrack> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to save Location Track")
        logger.daoAccess(AccessType.INSERT, LocationTrack::class, response)
        alignmentDao.saveLocationTrackGeometry(response, geometry)
        return response
    }

    override fun fetchVersions(layoutContext: LayoutContext, includeDeleted: Boolean) =
        fetchVersions(layoutContext, includeDeleted, null)

    fun list(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ) = fetchVersions(layoutContext, includeDeleted, trackNumberId, names).let(::fetchMany)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        names: List<AlignmentName> = emptyList(),
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql =
            """
            select id, design_id, draft, version 
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and (:names = '' or lower(lt.name) = any(string_to_array(:names, ',')::varchar[]))
              and (:include_deleted = true or state != 'DELETED')
        """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId?.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "names" to names.joinToString(",") { name -> name.toString().lowercase() },
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    @Transactional(readOnly = true)
    fun listNear(context: LayoutContext, bbox: BoundingBox): List<LocationTrack> =
        fetchVersionsNear(context, bbox).map(::fetch)

    fun fetchVersionsNear(
        context: LayoutContext,
        bbox: BoundingBox,
        includeDeleted: Boolean = false,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        minLength: Double? = null,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql =
            """
            select id, design_id, draft, version
              from layout.location_track_in_layout_context(
                      :publication_state::layout.publication_state, :design_id) location_track
              where (:track_number_id::int is null or track_number_id = :track_number_id)
                and (:include_deleted or state != 'DELETED')
                and (:min_length::numeric is null or location_track.length >= :min_length)
                and exists(
                  select *
                    from layout.location_track_version_edge lt_edge
                      inner join layout.edge on edge.id = lt_edge.edge_id
                    where location_track.id = lt_edge.location_track_id
                      and location_track.layout_context_id = lt_edge.location_track_layout_context_id
                      and location_track.version = lt_edge.location_track_version
                      and postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                                                edge.bounding_box)
                      and exists(
                        select *
                          from layout.edge_segment
                            inner join layout.segment_geometry on edge_segment.geometry_id = segment_geometry.id
                          where edge_segment.edge_id = edge.id
                            and postgis.st_intersects(
                              postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                              segment_geometry.bounding_box
                            )
                      )
                );
        """
                .trimIndent()

        val params =
            mapOf(
                "x_min" to bbox.min.x,
                "y_min" to bbox.min.y,
                "x_max" to bbox.max.x,
                "y_max" to bbox.max.y,
                "layout_srid" to LAYOUT_SRID.code,
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "track_number_id" to trackNumberId?.intValue,
                "min_length" to minLength,
            )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    @Cacheable(CACHE_COMMON_LOCATION_TRACK_OWNER, sync = true)
    fun fetchLocationTrackOwners(): List<LocationTrackOwner> {
        val sql =
            """
        select
            id,
            name
        from common.location_track_owner
        order by sort_priority, name
    """
                .trimIndent()

        val locationTrackOwners =
            jdbcTemplate.query(sql) { rs, _ ->
                LocationTrackOwner(id = rs.getIntId("id"), name = MetaDataName(rs.getString("name")))
            }
        logger.daoAccess(AccessType.FETCH, LocationTrackOwner::class, locationTrackOwners.map { it.id })
        return locationTrackOwners
    }

    fun fetchOnlyDraftVersions(
        branch: LayoutBranch,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql =
            """
            select id, design_id, draft, version
            from layout.location_track
            where draft
              and (:includeDeleted or state != 'DELETED')
              and (:trackNumberId::int is null or track_number_id = :trackNumberId)
              and design_id is not distinct from :design_id
        """
                .trimIndent()
        val params =
            mapOf(
                "includeDeleted" to includeDeleted,
                "trackNumberId" to trackNumberId?.intValue,
                "design_id" to branch.designId?.intValue,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
            }
            .also { ids -> logger.daoAccess(AccessType.VERSION_FETCH, "fetchOnlyDraftVersions", ids) }
    }

    fun fetchVersionsForPublication(
        target: ValidationTarget,
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
        trackIdsToPublish: List<IntId<LocationTrack>>,
    ): Map<IntId<LayoutTrackNumber>, List<LayoutRowVersion<LocationTrack>>> {
        if (trackNumberIds.isEmpty()) return emptyMap()

        val sql =
            """
            select track_number_id, id, design_id, draft, version
            from (
            select state, track_number_id, id, design_id, draft, version
              from layout.location_track_in_layout_context(:base_state::layout.publication_state, :base_design_id) official
              where (id in (:track_ids_to_publish)) is distinct from true
            union all
            select state, track_number_id, id, design_id, draft, version
              from layout.location_track_in_layout_context(:candidate_state::layout.publication_state, :candidate_design_id) draft
              where id in (:track_ids_to_publish)
            ) track
            where track_number_id in (:track_number_ids)
              and track.state != 'DELETED'
            order by track.track_number_id, track.id
        """
                .trimIndent()
        val params =
            mapOf(
                "track_number_ids" to trackNumberIds.map { id -> id.intValue },
                // listOf(null) to indicate an empty list due to SQL syntax limitations; the "is
                // distinct from true" checks
                // explicitly for false or null, since "foo in (null)" in SQL is null
                "track_ids_to_publish" to (trackIdsToPublish.map { id -> id.intValue }.ifEmpty { listOf(null) }),
            ) + target.sqlParameters()
        val versions =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val trackNumberId = rs.getIntId<LayoutTrackNumber>("track_number_id")
                val daoResponse = rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
                trackNumberId to daoResponse
            }
        return trackNumberIds.associateWith { trackNumberId ->
            versions.filter { (tnId, _) -> tnId == trackNumberId }.map { (_, trackVersions) -> trackVersions }
        }
    }

    fun listPublishedLocationTracksAtMoment(moment: Instant): List<LocationTrack> {
        val sql =
            """
              select distinct on (id) id, design_id, draft, version
              from layout.location_track_version
              where change_time <= :change_time
                and not deleted
                and not draft
                and design_id is null
              order by id, change_time desc
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("change_time" to Timestamp.from(moment))) { rs, _ ->
                rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
            }
            .map(::fetch)
    }

    @Transactional
    fun savePlanItemId(id: IntId<LocationTrack>, branch: DesignBranch, planItemId: RatkoPlanItemId) {
        jdbcTemplate.setUser()
        savePlanItemIdInExistingTransaction(branch, id, planItemId)
    }

    @Transactional
    fun insertExternalId(id: IntId<LocationTrack>, branch: LayoutBranch, oid: Oid<LocationTrack>) {
        jdbcTemplate.setUser()
        insertExternalIdInExistingTransaction(branch, id, oid)
    }

    fun fetchDependencyVersions(
        context: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        startSwitchId: IntId<LayoutSwitch>?,
        endSwitchId: IntId<LayoutSwitch>?,
    ): LocationTrackDependencyVersions {
        // language=PostgreSQL
        val sql =
            """
            select
              tn.id as track_number_id,
              tn.layout_context_id as track_number_layout_context_id,
              tn.version as track_number_version,
              start_sw.id as start_switch_id,
              start_sw.layout_context_id as start_switch_layout_context_id,
              start_sw.version as start_switch_version,
              end_sw.id as end_switch_id,
              end_sw.layout_context_id as end_switch_layout_context_id,
              end_sw.version as end_switch_version
            from layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id::int) tn
            left join lateral (
              select id, layout_context_id, version
              from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id::int)
              where id = :start_switch_id::int
            ) start_sw on true
            left join lateral (
              select id, layout_context_id, version
              from layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id::int)
              where id = :end_switch_id::int
            ) end_sw on true
            where tn.id = :track_number_id::int
        """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
                "track_number_id" to trackNumberId.intValue,
                "start_switch_id" to startSwitchId?.intValue,
                "end_switch_id" to endSwitchId?.intValue,
            )
        return jdbcTemplate.queryOne(sql, params) { rs, _ ->
            LocationTrackDependencyVersions(
                trackNumberVersion =
                    rs.getLayoutRowVersion("track_number_id", "track_number_layout_context_id", "track_number_version"),
                startSwitchVersion =
                    rs.getLayoutRowVersionOrNull(
                        "start_switch_id",
                        "start_switch_layout_context_id",
                        "start_switch_version",
                    ),
                endSwitchVersion =
                    rs.getLayoutRowVersionOrNull("end_switch_id", "end_switch_layout_context_id", "end_switch_version"),
            )
        }
    }

    fun fetchDependencyVersions(
        context: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        switchId: IntId<LayoutSwitch>? = null,
    ): Map<LayoutRowVersion<LocationTrack>, LocationTrackDependencyVersions> {
        if (trackNumberId == null && switchId == null) return emptyMap()
        val sql =
            """
            select
              t.id as track_id,
              t.layout_context_id as track_layout_context_id,
              t.version as track_version,
              tn.id as track_number_id,
              tn.layout_context_id as track_number_layout_context_id,
              tn.version as track_number_version,
              start_sw.id as start_switch_id,
              start_sw.layout_context_id as start_switch_layout_context_id,
              start_sw.version as start_switch_version,
              end_sw.id as end_switch_id,
              end_sw.layout_context_id as end_switch_layout_context_id,
              end_sw.version as end_switch_version
              from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id::int) t
                inner join layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id::int) tn
                          on t.track_number_id = tn.id
                left join layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id::int) start_sw
                          on t.start_switch_id = start_sw.id
                left join layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id::int) end_sw
                          on t.end_switch_id = end_sw.id
              where (:track_number_id::int is not null and t.track_number_id = :track_number_id::int)
                 or (:switch_id::int is not null and (t.start_switch_id = :switch_id::int or t.end_switch_id = :switch_id::int))
        """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
                "track_number_id" to trackNumberId?.intValue,
                "switch_id" to switchId?.intValue,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val trackVersion =
                    rs.getLayoutRowVersion<LocationTrack>("track_id", "track_layout_context_id", "track_version")
                trackVersion to
                    LocationTrackDependencyVersions(
                        trackNumberVersion =
                            rs.getLayoutRowVersion(
                                "track_number_id",
                                "track_number_layout_context_id",
                                "track_number_version",
                            ),
                        startSwitchVersion =
                            rs.getLayoutRowVersionOrNull(
                                "start_switch_id",
                                "start_switch_layout_context_id",
                                "start_switch_version",
                            ),
                        endSwitchVersion =
                            rs.getLayoutRowVersionOrNull(
                                "end_switch_id",
                                "end_switch_layout_context_id",
                                "end_switch_version",
                            ),
                    )
            }
            .associate { it }
    }
}
