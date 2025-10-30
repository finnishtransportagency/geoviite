package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.ratko.ExternalIdDao
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutContextData
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getPointOrNull
import fi.fta.geoviite.infra.util.getPolygonPointListOrNull
import fi.fta.geoviite.infra.util.getUnsafeStringOrNull
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import java.sql.ResultSet
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val OPERATIONAL_POINT_CACHE_SIZE = 2000L

@Component
class OperationalPointDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val alignmentDao: LayoutAlignmentDao,
    @Value("\${geoviite.cache.enabled}") cacheEnabled: Boolean,
) :
    LayoutAssetDao<OperationalPoint, NoParams>(
        jdbcTemplateParam,
        LayoutAssetTable.LAYOUT_ASSET_OPERATIONAL_POINT,
        cacheEnabled,
        OPERATIONAL_POINT_CACHE_SIZE,
    ),
    IExternalIdDao<OperationalPoint> by ExternalIdDao(
        jdbcTemplateParam,
        "layout.operational_point_external_id",
        "layout.operational_point_external_id_version",
    ),
    IExternallyIdentifiedLayoutAssetDao<OperationalPoint> {

    override fun fetchManyInternal(
        versions: Collection<LayoutRowVersion<OperationalPoint>>
    ): Map<LayoutRowVersion<OperationalPoint>, OperationalPoint> {
        if (versions.isEmpty()) return emptyMap()
        val sql =
            """
                select
                  op.id,
                  op.draft,
                  op.design_id,
                  op.version,
                  op.layout_context_id,
                  op.design_asset_state,
                  op.origin_design_id,
                  op.name,
                  op.abbreviation,
                  op.uic_code,
                  op.type,
                  postgis.st_x(op.location) as location_x,
                  postgis.st_y(op.location) as location_y,
                  op.state,
                  op.rinf_type,
                  rt.code as rinf_type_code,
                  postgis.st_astext(op.polygon) as polygon,
                  op.origin
                from layout.operational_point_version op
                  left join common.rinf_operational_point_type rt on op.rinf_type = rt.enum_name
                where not op.deleted
            """
                .trimIndent()
        val params =
            mapOf(
                "ids" to versions.map { v -> v.id.intValue }.toTypedArray(),
                "versions" to versions.map { v -> v.version }.toTypedArray(),
                "layout_context_ids" to versions.map { v -> v.context.toSqlString() }.toTypedArray(),
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ -> getOperationalPoint(rs) }
            .associateBy { s -> s.getVersionOrThrow() }
            .also { logger.daoAccess(FETCH, OperationalPoint::class, versions) }
    }

    override fun preloadCache(): Int {
        TODO("Not yet implemented")
    }

    override fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
    ): List<LayoutRowVersion<OperationalPoint>> =
        fetchVersions(layoutContext, includeDeleted, ids = null, searchBox = null)

    fun fetchVersions(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        ids: List<IntId<OperationalPoint>>?,
        searchBox: OperationalPointSearchBbox?,
    ): List<LayoutRowVersion<OperationalPoint>> {
        val idsFragment = if (ids == null) "true" else if (ids.isEmpty()) "false" else "id = any(:ids)"
        val sql =
            """
                select id, design_id, draft, version
                from layout.operational_point_in_layout_context(:publication_state::layout.publication_state, :design_id)
                where (:include_deleted = true or state != 'DELETED')
                  and $idsFragment
                  and ((:search_target::text is null)
                       or (:search_target = 'location' and 
                             postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid), location))
                       or (:search_target = 'polygon' and
                             postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid), polygon)))
            """
                .trimIndent()
        val params =
            mapOf(
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "include_deleted" to includeDeleted,
                "search_target" to
                    when (searchBox) {
                        null -> null
                        is SearchOperationalPointsByLocation -> "location"
                        is SearchOperationalPointsByPolygon -> "polygon"
                    },
                "x_min" to searchBox?.bbox?.x?.min,
                "x_max" to searchBox?.bbox?.x?.max,
                "y_min" to searchBox?.bbox?.y?.min,
                "y_max" to searchBox?.bbox?.y?.max,
                "layout_srid" to LAYOUT_SRID.code,
            ) + if (ids != null) mapOf("ids" to ids.map { it.intValue }.toTypedArray()) else mapOf()

        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getLayoutRowVersion("id", "design_id", "draft", "version")
        }
    }

    @Transactional fun save(item: OperationalPoint): LayoutRowVersion<OperationalPoint> = save(item, NoParams.instance)

    @Transactional
    override fun save(item: OperationalPoint, params: NoParams): LayoutRowVersion<OperationalPoint> {
        val id = item.id as? IntId ?: createId()

        val sql =
            """
                insert into
                  layout.operational_point(
                    id,
                    draft,
                    design_id,
                    layout_context_id,
                    design_asset_state,
                    origin_design_id,
                    name,
                    abbreviation,
                    uic_code,
                    type,
                    location,
                    state,
                    rinf_type,
                    polygon,
                    origin
                )
                values (
                  :id,
                  :draft,
                  :design_id,
                  :layout_context_id,
                  :design_asset_state::layout.design_asset_state,
                  :origin_design_id,
                  :name,
                  :abbreviation,
                  :uic_code,
                  :type::layout.operational_point_type,
                  postgis.st_setsrid(postgis.st_point(:location_x, :location_y), :srid),
                  :state::layout.operational_point_state,
                  :rinf_type,
                  postgis.st_polygonfromtext(:polygon_wkt, :srid),
                  :origin::layout.operational_point_origin
                )
                on conflict (id, layout_context_id) do update set
                  design_asset_state = excluded.design_asset_state,
                  origin_design_id = excluded.origin_design_id,
                  name = excluded.name,
                  abbreviation = excluded.abbreviation,
                  uic_code = excluded.uic_code,
                  type = excluded.type,
                  location = excluded.location,
                  state = excluded.state,
                  rinf_type = excluded.rinf_type,
                  polygon = excluded.polygon
                returning id, design_id, draft, version
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val response: LayoutRowVersion<OperationalPoint> =
            jdbcTemplate.queryForObject(
                sql,
                mapOf(
                    "layout_context_id" to item.layoutContext.toSqlString(),
                    "draft" to item.isDraft,
                    "id" to id.intValue,
                    "design_asset_state" to item.designAssetState?.name,
                    "design_id" to item.contextData.designId?.intValue,
                    "origin_design_id" to item.contextData.originBranch?.designId?.intValue,
                    "name" to item.name.toString(),
                    "abbreviation" to item.abbreviation?.toString(),
                    "uic_code" to item.uicCode?.toString(),
                    "type" to item.raideType?.name,
                    "location_x" to item.location?.x,
                    "location_y" to item.location?.y,
                    "polygon_wkt" to item.polygon?.toWkt(),
                    "state" to item.state.name,
                    "origin" to item.origin.name,
                    "srid" to LAYOUT_SRID.code,
                    "rinf_type" to item.rinfType?.name,
                ),
            ) { rs, _ ->
                rs.getLayoutRowVersion("id", "design_id", "draft", "version")
            } ?: throw IllegalStateException("Failed to save operational point")
        logger.daoAccess(INSERT, OperationalPoint::class, response)
        return response
    }

    override fun getBaseSaveParams(rowVersion: LayoutRowVersion<OperationalPoint>): NoParams = NoParams.instance

    private fun getOperationalPoint(rs: ResultSet): OperationalPoint =
        OperationalPoint(
            name = rs.getString("name").let(::OperationalPointName),
            abbreviation = rs.getUnsafeStringOrNull("abbreviation")?.toString()?.let(::OperationalPointAbbreviation),
            uicCode = rs.getUnsafeStringOrNull("uic_code")?.toString()?.let(::UicCode),
            location = rs.getPointOrNull("location_x", "location_y"),
            raideType = rs.getEnumOrNull<OperationalPointRaideType>("type"),
            state = rs.getEnum("state"),
            rinfType = rs.getEnumOrNull<OperationalPointRinfType>("rinf_type"),
            polygon = rs.getPolygonPointListOrNull("polygon")?.let(::Polygon),
            origin = rs.getEnum("origin"),
            contextData =
                rs.getLayoutContextData("id", "design_id", "draft", "version", "design_asset_state", "origin_design_id"),
        )

    fun getChangeTime(): Instant {
        return fetchLatestChangeTime(DbTable.LAYOUT_OPERATIONAL_POINT)
    }

    fun findNameDuplicates(
        context: LayoutContext,
        items: List<OperationalPointName>,
    ): Map<OperationalPointName, List<LayoutRowVersion<OperationalPoint>>> =
        findFieldDuplicates(context, items, "name") { rs -> rs.getString("name").let(::OperationalPointName) }

    fun findAbbreviationDuplicates(
        context: LayoutContext,
        items: List<OperationalPointAbbreviation>,
    ): Map<OperationalPointAbbreviation, List<LayoutRowVersion<OperationalPoint>>> =
        findFieldDuplicates(context, items, "abbreviation") { rs ->
            rs.getString("abbreviation").let(::OperationalPointAbbreviation)
        }

    fun findUicCodeDuplicates(
        context: LayoutContext,
        items: List<UicCode>,
    ): Map<UicCode, List<LayoutRowVersion<OperationalPoint>>> =
        findFieldDuplicates(context, items, "uic_code") { rs -> rs.getString("uic_code").let(::UicCode) }

    /**
     * baseContext, candidateContext, and publicationCandidateIds define a publication set. If publicationCandidateIds
     * is null, all candidates are considered to be in the publication set. Find all overlaps between polygons in
     * checkIds (if checkIds is null, all publication candidates) and anywhere in the publication set.
     */
    fun findOverlappingPolygonsInPublicationCandidates(
        baseContext: LayoutContext,
        candidateContext: LayoutContext,
        publicationCandidateIds: List<IntId<OperationalPoint>>? = null,
        checkIds: List<IntId<OperationalPoint>>? = null,
    ): Map<IntId<OperationalPoint>, List<IntId<OperationalPoint>>> {
        require(
            publicationCandidateIds == null || checkIds == null || checkIds.all(publicationCandidateIds::contains)
        ) {
            "Making checkIds not a subset of publicationCandidateIds is not supported"
        }

        val (candidateIdSqlFragment, candidateIdSqlParams) =
            if (publicationCandidateIds == null) "true" to mapOf()
            else if (publicationCandidateIds.isEmpty()) "false" to mapOf()
            else
                "id = any(:candidate_ids)" to
                    mapOf("candidate_ids" to publicationCandidateIds.map { it.intValue }.toTypedArray())

        val (checkIdSqlFragment, checkIdSqlParams) =
            if (checkIds == null) "true" to mapOf()
            else if (checkIds.isEmpty()) "false" to mapOf()
            else "id = any(:check_ids)" to mapOf("check_ids" to checkIds.map { it.intValue }.toTypedArray())

        val sql =
            """
            with candidate_points as not materialized (
                select *
                  from layout.operational_point_in_layout_context(:candidate_publication_state::layout.publication_state,
                                                                  :candidate_design_id::int)
                  where ($candidateIdSqlFragment) and polygon is not null
              ),
            base_points as not materialized (
              select *
                from layout.operational_point_in_layout_context(:base_publication_state::layout.publication_state,
                                                                :base_design_id::int) base_point
                where not ($candidateIdSqlFragment) and polygon is not null
            ), check_points as not materialized (
              select * from candidate_points where $checkIdSqlFragment
            )
            (select point.id as point_id, other.id as other_id
             from check_points point
               join base_points other
                 on postgis.st_overlaps(point.polygon, other.polygon))
            union all
            (select point.id as point_id, other.id as other_id
             from check_points point
               join (select * from candidate_points where not ($checkIdSqlFragment)) other
                 on postgis.st_overlaps(point.polygon, other.polygon))
            union all
            (select point_id, other_id
             from check_points a
               join check_points b
                 on a.id < b.id and postgis.st_overlaps(a.polygon, b.polygon)
               cross join lateral (
                 select a.id as point_id, b.id as other_id
                 union all
                 select b.id as point_id, a.id as other_id
               ) symmetrize
            )
        """
                .trimIndent()

        val params =
            mapOf(
                "base_publication_state" to baseContext.state.name,
                "base_design_id" to baseContext.branch.designId?.intValue,
                "candidate_publication_state" to candidateContext.state.name,
                "candidate_design_id" to candidateContext.branch.designId?.intValue,
            ) + candidateIdSqlParams + checkIdSqlParams

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                rs.getIntId<OperationalPoint>("point_id") to rs.getIntId<OperationalPoint>("other_id")
            }
            .groupBy({ it.first }, { it.second })
    }

    fun polygonContainsLocation(rowVersion: LayoutRowVersion<OperationalPoint>): Boolean? {
        val sql =
            """
                select postgis.st_intersects(polygon, location) c
                from layout.operational_point_version opv
                where opv.id = :id and opv.version = :version and opv.layout_context_id = :context
            """
                .trimIndent()
        val params =
            mapOf(
                "id" to rowVersion.id.intValue,
                "version" to rowVersion.version,
                "context" to rowVersion.context.toSqlString(),
            )
        return jdbcTemplate.queryOptional(sql, params) { rs, _ -> rs.getBooleanOrNull("c") }
    }
}
