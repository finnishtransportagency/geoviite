package fi.fta.geoviite.infra.tracklayout

import com.amazonaws.services.cloudfront.model.EntityNotFoundException
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.configuration.CACHE_COMMON_LOCATION_TRACK_OWNER
import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.ValidationTarget
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getDbLocationTrackNaming
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getLayoutRowVersionOrNull
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

@Component
class LocationTrackDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val switchDao: LayoutSwitchDao,
    val trackNumberDao: LayoutTrackNumberDao,
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

    fun fetchAugLocationTrack(
        translation: Translation,
        id: IntId<LocationTrack>,
        layoutContext: LayoutContext,
    ): AugLocationTrack? = fetchAugLocationTrackKey(id, layoutContext)?.let { key -> fetch(key, translation) }

    fun fetchAugLocationTrackOrThrow(
        translation: Translation,
        id: IntId<LocationTrack>,
        layoutContext: LayoutContext,
    ): AugLocationTrack =
        fetchAugLocationTrack(translation, id, layoutContext)
            ?: throw EntityNotFoundException("Location track with id $id not found in layout context $layoutContext")

    fun fetchManyAugLocationTracks(
        defaultTranslation: Translation,
        layoutContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<AugLocationTrack> =
        fetchManyAugLocationTrackKeys(ids, layoutContext).map { key -> fetch(key, defaultTranslation) }

    fun listAugLocationTracks(
        translation: Translation,
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        boundingBox: BoundingBox? = null,
    ): List<AugLocationTrack> =
        listAugLocationTrackKeys(layoutContext, includeDeleted, trackNumberId, boundingBox).map { key ->
            fetch(key, translation)
        }

    fun fetchAugLocationTrackKey(id: IntId<LocationTrack>, layoutContext: LayoutContext): AugLocationTrackCacheKey? =
        fetchAugLocationTrackKeys(listOf(id), layoutContext).firstOrNull()

    fun fetchManyAugLocationTrackKeys(
        ids: List<IntId<LocationTrack>>,
        layoutContext: LayoutContext,
    ): List<AugLocationTrackCacheKey> = fetchManyAugLocationTrackKeys(ids, layoutContext)

    fun listAugLocationTrackKeys(
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        boundingBox: BoundingBox? = null,
    ): List<AugLocationTrackCacheKey> =
        fetchAugLocationTrackKeys(null, layoutContext, includeDeleted, trackNumberId).also {
            if (boundingBox != null) TODO("BBOX fetch not implemented yet")
        }

    private fun fetchAugLocationTrackKeys(
        ids: List<IntId<LocationTrack>>?,
        layoutContext: LayoutContext,
        includeDeleted: Boolean = false,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
    ): List<AugLocationTrackCacheKey> {
        val sql =
            """
            select
              lt.id as lt_id,
              lt.draft as lt_draft,
              lt.design_id as lt_design_id,
              lt.version as lt_version,
              tn.id as tn_id,
              tn.draft as tn_draft,
              tn.design_id as tn_design_id,
              tn.version as tn_version,
              start_switch.id as sw_start_id,
              start_switch.draft as sw_start_draft,
              start_switch.design_id as sw_start_design_id,
              start_switch.version as sw_start_version,
              end_switch.id as sw_end_id,
              end_switch.draft as sw_end_draft,
              end_switch.design_id as sw_end_design_id,
              end_switch.version as sw_end_version
              from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
                inner join layout.track_number_in_layout_context(:publication_state::layout.publication_state, :design_id) tn on lt.track_number_id = tn.id
                left join layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id) start_switch on lt.start_switch_id = start_switch.id
                left join layout.switch_in_layout_context(:publication_state::layout.publication_state, :design_id) end_switch on lt.end_switch_id = end_switch.id
              where (:ids::int[] is null or lt.id = any(:ids))
                and (:include_deleted or lt.state != 'DELETED')
                and (:track_number_id::int is null or lt.track_number_id = :track_number_id)
        """
                .trimIndent()
        val params =
            mapOf(
                "ids" to ids?.map { id -> id.intValue }?.toTypedArray(),
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "track_number_id" to trackNumberId?.intValue,
            )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val trackVersion = rs.getLayoutRowVersion<LocationTrack>("lt_id", "lt_design_id", "lt_draft", "lt_version")
            val trackNumberVersion =
                rs.getLayoutRowVersion<LayoutTrackNumber>("tn_id", "tn_design_id", "tn_draft", "tn_version")
            val startSwitchVersion =
                rs.getLayoutRowVersionOrNull<LayoutSwitch>(
                    "sw_start_id",
                    "sw_start_design_id",
                    "sw_start_draft",
                    "sw_start_version",
                )
            val endSwitchVersion =
                rs.getLayoutRowVersionOrNull<LayoutSwitch>(
                    "sw_end_id",
                    "sw_end_design_id",
                    "sw_end_draft",
                    "sw_end_version",
                )
            AugLocationTrackCacheKey(trackVersion, trackNumberVersion, startSwitchVersion, endSwitchVersion)
        }
    }

    fun fetch(key: AugLocationTrackCacheKey, translation: Translation): AugLocationTrack {
        val dbTrack = fetch(key.trackVersion)
        val startSwitch = key.startSwitchVersion?.let(switchDao::fetch)
        val endSwitch = key.startSwitchVersion?.let(switchDao::fetch)
        val trackNumber = trackNumberDao.fetch(key.trackNumberVersion)
        return AugLocationTrack(
            translation,
            key,
            dbTrack,
            ReifiedTrackNaming.of(dbTrack, trackNumber, startSwitch, endSwitch),
            ReifiedTrackDescription(dbTrack.dbDescription, startSwitch?.name, endSwitch?.name),
        )
    }

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

    override fun fetchInternal(version: LayoutRowVersion<LocationTrack>): LocationTrack {
        val sql =
            """
            select 
              ltv.id,
              ltv.version,
              ltv.design_id,
              ltv.draft,
              ltv.design_asset_state,
              ltv.track_number_id, 
              ltv.naming_scheme, 
              ltv.name_free_text,
              ltv.name_specifier,
              ltv.description_base,
              ltv.description_suffix,
              ltv.type, 
              ltv.state, 
              postgis.st_astext(ltv.bounding_box) as bounding_box,
              ltv.length,
              ltv.segment_count,            
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
            where ltv.id = :id
              and ltv.layout_context_id = :layout_context_id
              and ltv.version = :version
              and ltv.deleted = false
        """
                .trimIndent()

        val params =
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            )
        return getOne(version, jdbcTemplate.query(sql, params) { rs, _ -> getLocationTrack(rs) }).also {
            logger.daoAccess(AccessType.FETCH, LocationTrack::class, version)
        }
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
              lt.naming_scheme, 
              lt.name_free_text,
              lt.name_specifier,
              lt.description_base,
              lt.description_suffix,
              lt.type, 
              lt.state, 
              postgis.st_astext(lt.bounding_box) as bounding_box,
              lt.length,
              lt.segment_count,            
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
            dbName = rs.getDbLocationTrackNaming("naming_scheme", "name_specifier", "name_free_text"),
            dbDescription =
                DbLocationTrackDescription(
                    descriptionBase = rs.getString("description_base").let(::LocationTrackDescriptionBase),
                    descriptionSuffix = rs.getEnum<LocationTrackDescriptionSuffix>("description_suffix"),
                ),
            type = rs.getEnum("type"),
            state = rs.getEnum("state"),
            boundingBox = rs.getBboxOrNull("bounding_box"),
            length = rs.getDouble("length"),
            segmentCount = rs.getInt("segment_count"),
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
              naming_scheme,
              name_free_text,
              name_specifier,
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
              length,
              edge_count,
              segment_count,
              bounding_box,
              start_switch_id,
              end_switch_id
            ) 
            values (
              :layout_context_id,
              :id,
              :track_number_id,
              :naming_scheme::layout.location_track_naming_scheme,
              :name_free_text,
              :name_specifier::layout.location_track_specifier,
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
              :length,
              :edge_count,
              :segment_count,
              postgis.st_polygonfromtext(:polygon_string, 3067),
              :start_switch_id,
              :end_switch_id
            ) on conflict (id, layout_context_id) do update set
              track_number_id = excluded.track_number_id,
              naming_scheme = excluded.naming_scheme,
              name_free_text = excluded.name_free_text,
              name_specifier = excluded.name_specifier,
              description_base = excluded.description_base,
              description_suffix = excluded.description_suffix,
              type = excluded.type,
              state = excluded.state,
              design_asset_state = excluded.design_asset_state,
              duplicate_of_location_track_id = excluded.duplicate_of_location_track_id,
              topological_connectivity = excluded.topological_connectivity,
              owner_id = excluded.owner_id,
              origin_design_id = excluded.origin_design_id,
              length = excluded.length,
              edge_count = excluded.edge_count,
              segment_count = excluded.segment_count,
              bounding_box = excluded.bounding_box,
              start_switch_id = excluded.start_switch_id,
              end_switch_id = excluded.end_switch_id
            returning id, design_id, draft, version
        """
                .trimIndent()
        val params =
            mapOf(
                "layout_context_id" to item.layoutContext.toSqlString(),
                "id" to id.intValue,
                "track_number_id" to item.trackNumberId.intValue,
                "naming_scheme" to item.dbName.namingScheme.name,
                "name_free_text" to item.dbName.nameFreeText,
                "name_specifier" to item.dbName.nameSpecifier?.name,
                "description_base" to item.dbDescription.descriptionBase,
                "description_suffix" to item.dbDescription.descriptionSuffix.name,
                "type" to item.type.name,
                "state" to item.state.name,
                "draft" to item.isDraft,
                "design_asset_state" to item.designAssetState?.name,
                "design_id" to item.contextData.designId?.intValue,
                "duplicate_of_location_track_id" to item.duplicateOf?.intValue,
                "topological_connectivity" to item.topologicalConnectivity.name,
                "owner_id" to item.ownerId.intValue,
                "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
                "length" to geometry.length,
                "edge_count" to geometry.edges.size,
                "segment_count" to geometry.segments.size,
                "polygon_string" to
                    geometry.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
                "start_switch_id" to geometry.startSwitchLink?.id?.intValue,
                "end_switch_id" to geometry.endSwitchLink?.id?.intValue,
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

    fun list(layoutContext: LayoutContext, includeDeleted: Boolean, trackNumberId: IntId<LayoutTrackNumber>? = null) =
        fetchVersions(layoutContext, includeDeleted, trackNumberId).map(::fetch)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
    ): List<LayoutRowVersion<LocationTrack>> {
        val sql =
            """
            select id, design_id, draft, version 
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
            where 
              (cast(:track_number_id as int) is null or lt.track_number_id = :track_number_id) 
              and (:include_deleted = true or state != 'DELETED')
        """
                .trimIndent()
        val params =
            mapOf(
                "track_number_id" to trackNumberId?.intValue,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
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
}
