package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geography.create3DMLineString
import fi.fta.geoviite.infra.geography.get3DMLineStringContent
import fi.fta.geoviite.infra.geography.parseSegmentPoint
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.linking.NodeTrackConnections
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.MultiPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.roundTo6Decimals
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_ALIGNMENT
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.batchUpdateIndexed
import fi.fta.geoviite.infra.util.getBigDecimalOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumArray
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getFileNameOrNull
import fi.fta.geoviite.infra.util.getIndexedIdOrNull
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntArray
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getJointNumber
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getNullableBigDecimalArray
import fi.fta.geoviite.infra.util.getNullableIntArray
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.getPoint3DMOrNull
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.getSridOrNull
import fi.fta.geoviite.infra.util.measureAndCollect
import fi.fta.geoviite.infra.util.setNullableBigDecimal
import fi.fta.geoviite.infra.util.setNullableInt
import fi.fta.geoviite.infra.util.setUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.stream.Collectors
import kotlin.math.abs

const val NODE_CACHE_SIZE = 50000L
const val EDGE_CACHE_SIZE = 100000L
const val ALIGNMENT_CACHE_SIZE = 10000L
const val GEOMETRY_CACHE_SIZE = 500000L

data class MapSegmentProfileInfo<T, M : AlignmentM<M>>(
    val id: IntId<T>,
    val mRange: Range<LineM<M>>,
    val hasProfile: Boolean,
)

@Component
class LayoutAlignmentDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") val cacheEnabled: Boolean,
) : DaoBase(jdbcTemplateParam) {

    private val nodesCache: Cache<IntId<LayoutNode>, DbLayoutNode> =
        Caffeine.newBuilder().maximumSize(NODE_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val edgesCache: Cache<IntId<LayoutEdge>, DbLayoutEdge> =
        Caffeine.newBuilder().maximumSize(EDGE_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val locationTrackGeometryCache: Cache<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> =
        Caffeine.newBuilder().maximumSize(ALIGNMENT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val alignmentsCache: Cache<RowVersion<LayoutAlignment>, LayoutAlignment> =
        Caffeine.newBuilder().maximumSize(ALIGNMENT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val segmentGeometryCache: Cache<IntId<SegmentGeometry>, SegmentGeometry> =
        Caffeine.newBuilder().maximumSize(GEOMETRY_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    fun fetchVersions() = fetchRowVersions<LayoutAlignment>(LAYOUT_ALIGNMENT)

    fun getNode(id: IntId<LayoutNode>): DbLayoutNode = requireNotNull(getNodes(listOf(id))[id])

    fun getNodes(ids: Iterable<IntId<LayoutNode>>): Map<IntId<LayoutNode>, DbLayoutNode> =
        nodesCache.getAll(ids) { nonCached -> fetchNodes(nonCached) }

    fun preloadNodes(): Int {
        return fetchNodes(ids = null).also(nodesCache::putAll).size
    }

    private fun fetchNodes(ids: Set<IntId<LayoutNode>>?): Map<IntId<LayoutNode>, DbLayoutNode> {
        if (ids?.isEmpty() == true) return emptyMap()
        val sql =
            """
                select
                  port_a.node_id,
                  port_a.node_type,
                  port_a.switch_id as a_switch_id,
                  port_a.switch_joint_number as a_switch_joint_number,
                  port_a.switch_joint_role as a_switch_joint_role,
                  port_a.boundary_location_track_id as a_boundary_location_track_id,
                  port_a.boundary_type as a_boundary_type,
                  port_b.switch_id as b_switch_id,
                  port_b.switch_joint_number as b_switch_joint_number,
                  port_b.switch_joint_role as b_switch_joint_role,
                  port_b.boundary_location_track_id as b_boundary_location_track_id,
                  port_b.boundary_type as b_boundary_type
                  from layout.node_port port_a
                    left join layout.node_port port_b on port_a.node_id = port_b.node_id and port_b.port = 'B'
                where port_a.port = 'A'
                  and (:ids::int[] is null or port_a.node_id = any(:ids))
            """
                .trimIndent()
        val params = mapOf("ids" to ids?.map { id -> id.intValue }?.toTypedArray())

        fun getTrackBoundary(rs: ResultSet, prefix: String): TrackBoundary? =
            rs.getIntIdOrNull<LocationTrack>("${prefix}_boundary_location_track_id")?.let { id ->
                TrackBoundary(id = id, type = rs.getEnum("${prefix}_boundary_type"))
            }

        fun getSwitchLink(rs: ResultSet, prefix: String): SwitchLink? =
            rs.getIntIdOrNull<LayoutSwitch>("${prefix}_switch_id")?.let { id ->
                SwitchLink(
                    id = id,
                    jointNumber = rs.getJointNumber("${prefix}_switch_joint_number"),
                    jointRole = rs.getEnum("${prefix}_switch_joint_role"),
                )
            }

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val dbId = rs.getIntId<LayoutNode>("node_id")
                val type = rs.getEnum<LayoutNodeType>("node_type")
                dbId to
                    when (type) {
                        LayoutNodeType.TRACK_BOUNDARY ->
                            DbTrackBoundaryNode(
                                id = dbId,
                                portA =
                                    requireNotNull(getTrackBoundary(rs, "a")) { "Node must have at least one port" },
                                portB = getTrackBoundary(rs, "b"),
                            )
                        LayoutNodeType.SWITCH -> {
                            DbSwitchNode(
                                id = dbId,
                                portA = requireNotNull(getSwitchLink(rs, "a")) { "Node must have at least one port" },
                                portB = getSwitchLink(rs, "b"),
                            )
                        }
                    }
            }
            .associate { it }
    }

    @Transactional
    fun getOrCreateNode(content: LayoutNode): DbLayoutNode =
        if (content is DbLayoutNode) content else getNode(saveNode(content))

    private fun saveNode(content: LayoutNode): IntId<LayoutNode> {
        val sql =
            """
            select layout.get_or_insert_node(
                :switch_ids::int[],
                :switch_joint_numbers::int[],
                :switch_joint_roles::common.switch_joint_role[],
                :boundary_track_ids::int[],
                :boundary_types::layout.boundary_type[]
            ) as id
        """
        val switches = content.ports.filterIsInstance<SwitchLink>()
        val boundaries = content.ports.filterIsInstance<TrackBoundary>()
        val params =
            mapOf(
                "switch_ids" to switches.map { s -> s.id.intValue }.toTypedArray(),
                "switch_joint_numbers" to switches.map { s -> s.jointNumber.intValue }.toTypedArray(),
                "switch_joint_roles" to switches.map { s -> s.jointRole.name }.toTypedArray(),
                "boundary_track_ids" to boundaries.map { b -> b.id.intValue }.toTypedArray(),
                "boundary_types" to boundaries.map { b -> b.type.name }.toTypedArray(),
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutNode>("id") }.single()
    }

    fun getEdge(id: IntId<LayoutEdge>): DbLayoutEdge =
        edgesCache.get(id) {
            val edge = fetchEdges(ids = listOf(id), active = false).values.single()
            edge
        }

    fun getEdges(ids: List<IntId<LayoutEdge>>) = edgesCache.getAll(ids) { missing -> fetchEdges(missing, false) }

    fun preloadEdges(): Int {
        val edges = fetchEdges(ids = null, active = true)
        edgesCache.putAll(edges)
        return edges.size
    }

    private fun fetchEdges(ids: Iterable<IntId<LayoutEdge>>?, active: Boolean): Map<IntId<LayoutEdge>, DbLayoutEdge> {
        logger.info("Fetching edges: ids=$ids active=$active")
        val sql =
            """
                select
                  e.id,
                  e.start_node_id,
                  e.start_node_port,
                  e.end_node_id,
                  e.end_node_port,
                  array_agg(s.segment_index order by s.segment_index) as indices,
                  array_agg(s.geometry_alignment_id order by s.segment_index) as geometry_alignment_ids,
                  array_agg(s.geometry_element_index order by s.segment_index) as geometry_element_indices,
                  array_agg(s.source_start_m order by s.segment_index) as source_start_m_values,
                  array_agg(s.source order by s.segment_index) as sources,
                  array_agg(s.geometry_id order by s.segment_index) as geometry_ids
                  from layout.edge e
                    left join layout.edge_segment s on e.id = s.edge_id
                  where (:ids::int[] is null or e.id = any(:ids))
                    and (not :active or exists(
                      select 1
                      from layout.location_track_version_edge te
                        inner join layout.location_track t
                          on te.location_track_id = t.id
                            and te.location_track_layout_context_id = t.layout_context_id
                            and te.location_track_version = t.version
                      where e.id = te.edge_id
                    ))
                  group by e.id
            """
                .trimIndent()
        val params = mapOf("ids" to ids?.map { id -> id.intValue }?.toTypedArray(), "active" to active)

        val edges =
            measureAndCollect("fetch") {
                jdbcTemplate.query(sql, params) { rs, _ ->
                    val edgeId = rs.getIntId<LayoutEdge>("id")
                    val segmentIndices = rs.getIntArray("indices")
                    val geometryAlignmentIds =
                        rs.getNullableIntArray("geometry_alignment_ids").also {
                            require(it.size == segmentIndices.size)
                        }
                    val geometryElementIndices =
                        rs.getNullableIntArray("geometry_element_indices").also {
                            require(it.size == segmentIndices.size)
                        }
                    val sourceStartMValues =
                        rs.getNullableBigDecimalArray("source_start_m_values").also {
                            require(it.size == segmentIndices.size)
                        }
                    val sources =
                        rs.getEnumArray<GeometrySource>("sources").also { require(it.size == segmentIndices.size) }
                    val geometryIds =
                        rs.getIntIdArray<SegmentGeometry>("geometry_ids").also {
                            require(it.size == segmentIndices.size)
                        }

                    val startNodeId = rs.getIntId<LayoutNode>("start_node_id")
                    val startNodePort = rs.getEnum<NodePortType>("start_node_port")
                    val endNodeId = rs.getIntId<LayoutNode>("end_node_id")
                    val endNodePort = rs.getEnum<NodePortType>("end_node_port")

                    val segments =
                        segmentIndices.map { i ->
                            val geometryAlignmentId = geometryAlignmentIds[i]
                            val geometryElementIndex = geometryElementIndices[i]
                            val sourceId: IndexedId<GeometryElement>? =
                                if (geometryAlignmentId != null) {
                                    IndexedId(geometryAlignmentId, requireNotNull(geometryElementIndex))
                                } else {
                                    null
                                }
                            SegmentData(
                                sourceId = sourceId,
                                sourceStartM = sourceStartMValues[i],
                                source = sources[i],
                                geometryId = geometryIds[i],
                            )
                        }
                    EdgeData(edgeId, startNodeId to startNodePort, endNodeId to endNodePort, segments)
                }
            }
        val nodes = getNodes(edges.flatMap { d -> listOf(d.startNode.first, d.endNode.first) }.toSet())
        val geometries = fetchSegmentGeometries(edges.flatMap { d -> d.segments.map { s -> s.geometryId } }.distinct())
        return createEdges(edges, nodes, geometries).associateBy { e -> e.id }
    }

    @Transactional
    fun getOrCreateEdge(content: LayoutEdge): DbLayoutEdge =
        when (content) {
            is DbLayoutEdge -> content
            is TmpLayoutEdge -> getEdge(saveEdge(saveContentGeometry(content)))
        }

    private fun getOrSaveNodeConnection(node: NodeConnection): IntId<LayoutNode> =
        getOrCreateNode(requireNotNull(node.node) { "Cannot save edge with non-reified nodes: $node" }).id

    private fun saveEdge(content: TmpLayoutEdge): IntId<LayoutEdge> {
        val startNodeId = getOrSaveNodeConnection(content.startNode)
        val endNodeId = getOrSaveNodeConnection(content.endNode)
        val sql =
            """
            select layout.get_or_insert_edge(
              :start_node_id,
              :start_node_port::layout.node_port_type,
              :end_node_id,
              :end_node_port::layout.node_port_type,
              :geometry_alignment_ids,
              :geometry_element_indices,
              :start_m_values,
              :source_start_m_values,
              :sources,
              :geometry_ids,
               postgis.st_polygonfromtext(:polygon_string, 3067)
            ) as id
        """
        val params =
            mapOf(
                "start_node_id" to startNodeId.intValue,
                "start_node_port" to content.startNode.portConnection.name,
                "end_node_id" to endNodeId.intValue,
                "end_node_port" to content.endNode.portConnection.name,
                "geometry_alignment_ids" to content.segments.map { s -> s.sourceId?.parentId }.toTypedArray(),
                "geometry_element_indices" to content.segments.map { s -> s.sourceId?.index }.toTypedArray(),
                "start_m_values" to content.segmentMValues.map { m -> roundTo6Decimals(m.min.distance) }.toTypedArray(),
                "source_start_m_values" to content.segments.map { s -> s.sourceStartM }.toTypedArray(),
                "sources" to content.segments.map { s -> s.source.name }.toTypedArray(),
                "geometry_ids" to content.segments.map { s -> (s.geometry.id as IntId).intValue }.toTypedArray(),
                "polygon_string" to content.boundingBox.polygonFromCorners.toWkt(),
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutEdge>("id") }.single()
    }

    private fun saveContentGeometry(content: TmpLayoutEdge): TmpLayoutEdge {
        val newGeometryIds =
            insertSegmentGeometries(
                content.segments.mapNotNull { s -> if (s.geometry.id is StringId) s.geometry else null }
            )
        val newGeometries = fetchSegmentGeometries(newGeometryIds.values.toList())
        return if (newGeometryIds.isEmpty()) {
            content
        } else {
            val newSegments =
                content.segments.map { s ->
                    if (s.geometry.id is StringId) {
                        val geom = requireNotNull(newGeometries[requireNotNull(newGeometryIds[s.geometry.id])])
                        s.copy(geometry = geom)
                    } else {
                        s
                    }
                }
            content.copy(segments = newSegments)
        }
    }

    fun fetch(trackVersion: LayoutRowVersion<LocationTrack>): DbLocationTrackGeometry =
        locationTrackGeometryCache.get(trackVersion) { version ->
            fetchLocationTrackGeometry(version, false)[trackVersion]
                ?: throw NoSuchEntityException(LocationTrackGeometry::class, trackVersion.toString())
        }

    private fun fetchLocationTrackGeometry(
        trackVersion: LayoutRowVersion<LocationTrack>?,
        active: Boolean,
    ): Map<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> {
        val sql =
            """
                select
                  lt.id,
                  lt.layout_context_id,
                  lt.version,
                  array_agg(edge_id order by edge_index) filter (where edge_id is not null) as edge_ids
                  from layout.location_track_version lt
                    left join layout.location_track_version_edge lt_e
                              on lt_e.location_track_id = lt.id
                                and lt_e.location_track_layout_context_id = lt.layout_context_id
                                and lt_e.location_track_version = lt.version
                  where (:id::int is null or (lt.id = :id and lt.layout_context_id = :layout_context_id and lt.version = :version and deleted = false))
                    and (:active = false or exists(
                      select 1
                        from layout.location_track t
                        where t.id = lt.id and t.layout_context_id = lt.layout_context_id and t.version = lt.version
                    ))
                  group by lt.id, lt.layout_context_id, lt.version
            """
                .trimIndent()
        val params =
            mapOf(
                "id" to trackVersion?.id?.intValue,
                "layout_context_id" to trackVersion?.context?.toSqlString(),
                "version" to trackVersion?.version,
                "active" to active,
            )
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val edgeIds = rs.getIntIdArray<LayoutEdge>("edge_ids")
                val edges = getEdges(edgeIds)
                DbLocationTrackGeometry(
                    trackRowVersion = rs.getLayoutRowVersion("id", "layout_context_id", "version"),
                    edges = edgeIds.map { id -> requireNotNull(edges[id]) },
                )
            }
            .associateBy(DbLocationTrackGeometry::trackRowVersion)
            .also { logger.daoAccess(AccessType.FETCH, LocationTrackGeometry::class, trackVersion ?: "ALL") }
    }

    @Transactional
    fun saveLocationTrackGeometry(trackVersion: LayoutRowVersion<LocationTrack>, trackGeometry: LocationTrackGeometry) {
        val geometry = trackGeometry.withLocationTrackId(trackVersion.id)
        val edges = geometry.edges.associate { e -> e.contentHash to getOrCreateEdge(e).id }

        val sql =
            """
                insert into layout.location_track_version_edge(
                    location_track_id,
                    location_track_layout_context_id,
                    location_track_version,
                    edge_id,
                    edge_index,
                    start_m
                )
                values(?, ?, ?, ?, ?, ?)
            """
                .trimIndent()

        // This uses indexed parameters (rather than named ones),
        // since named parameter template's batch-method is considerably slower
        jdbcTemplate.batchUpdateIndexed(sql, geometry.edgesWithM) { ps, (index, edgeAndM) ->
            val (edge, m) = edgeAndM
            ps.setInt(1, trackVersion.id.intValue)
            ps.setString(2, trackVersion.context.toSqlString())
            ps.setInt(3, trackVersion.version)
            ps.setInt(4, requireNotNull(edges[edge.contentHash]).intValue)
            ps.setInt(5, index)
            ps.setBigDecimal(6, roundTo6Decimals(m.min.distance))
        }
        logger.daoAccess(AccessType.INSERT, LocationTrackGeometry::class, trackVersion)
    }

    fun preloadLocationTrackGeometries(): Int {
        val geoms = fetchLocationTrackGeometry(trackVersion = null, active = true)
        locationTrackGeometryCache.putAll(geoms)
        return geoms.size
    }

    fun fetch(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment =
        if (cacheEnabled) alignmentsCache.get(alignmentVersion, ::fetchInternal) else fetchInternal(alignmentVersion)

    @Transactional(readOnly = true)
    fun fetchMany(versions: List<RowVersion<LayoutAlignment>>): Map<RowVersion<LayoutAlignment>, LayoutAlignment> =
        versions.associateWith(this::fetch)

    @Transactional(readOnly = true)
    fun getMany(
        versions: List<LayoutRowVersion<LocationTrack>>
    ): Map<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> = versions.associateWith(this::fetch)

    private fun fetchInternal(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment {
        val sql =
            """
                select id, version
                from layout.alignment_version
                where id = :id
                  and version = :version
                  and deleted = false
            """
                .trimIndent()
        val params = mapOf("id" to alignmentVersion.id.intValue, "version" to alignmentVersion.version)
        return getOne(
                alignmentVersion.id,
                jdbcTemplate.query(sql, params) { rs, _ ->
                    LayoutAlignment(
                        dataType = DataType.STORED,
                        id = rs.getIntId("id"),
                        segments = fetchSegments(alignmentVersion),
                    )
                },
            )
            .also { alignment -> logger.daoAccess(AccessType.FETCH, LayoutAlignment::class, alignment.id) }
    }

    fun preloadAlignmentCache(): Int {
        val sql =
            """
              select
                a.id,
                a.version,
                sv.segment_index,
                sv.start,
                sv.geometry_alignment_id,
                sv.geometry_element_index,
                sv.source_start,
                sv.source,
                sv.geometry_id
              from layout.alignment a
                left join layout.segment_version sv on sv.alignment_id = a.id and sv.alignment_version = a.version
              order by a.id, sv.segment_index
            """
                .trimIndent()

        data class AlignmentData(val version: RowVersion<LayoutAlignment>)

        val alignmentAndSegment =
            jdbcTemplate.query(sql) { rs, _ ->
                val alignmentData = AlignmentData(version = rs.getRowVersion("id", "version"))
                val segmentId = rs.getIndexedIdOrNull<LayoutSegment>("id", "segment_index")
                val segment =
                    segmentId?.let { sId ->
                        SegmentData(
                            sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                            sourceStartM = rs.getBigDecimalOrNull("source_start"),
                            source = rs.getEnum("source"),
                            geometryId = rs.getIntId("geometry_id"),
                        )
                    }
                alignmentData to segment
            }
        val groupedByAlignment = alignmentAndSegment.groupBy({ (a, _) -> a }, { (_, s) -> s }).entries
        val geometries = fetchSegmentGeometries(alignmentAndSegment.mapNotNull { (_, s) -> s?.geometryId }.distinct())
        groupedByAlignment.parallelStream().forEach { (alignmentData, segmentDatas) ->
            alignmentsCache.get(alignmentData.version) { _ ->
                LayoutAlignment(
                    id = alignmentData.version.id,
                    segments = createSegments(segmentDatas.filterNotNull(), geometries),
                )
            }
        }
        return groupedByAlignment.size
    }

    @Transactional
    fun insert(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val sql =
            """
                insert into layout.alignment(
                    bounding_box,
                    segment_count,
                    length
                )
                values (
                    postgis.st_polygonfromtext(:polygon_string, 3067),
                    :segment_count,
                    :length
                )
                returning id, version
            """
                .trimIndent()
        val params =
            mapOf(
                "polygon_string" to alignment.boundingBox?.let { bbox -> bbox.polygonFromCorners.toWkt() },
                "segment_count" to alignment.segments.size,
                "length" to alignment.length.distance,
            )
        jdbcTemplate.setUser()
        val id: RowVersion<LayoutAlignment> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new Track Layout Alignment")
        upsertSegments(id, alignment.segmentsWithM)
        logger.daoAccess(AccessType.INSERT, LayoutAlignment::class, id)
        return id
    }

    @Transactional
    fun update(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        val alignmentId =
            if (alignment.id is IntId) alignment.id
            else throw IllegalArgumentException("Cannot update an alignment that isn't in DB already")
        val sql =
            """
                update layout.alignment
                set
                    bounding_box = postgis.st_polygonfromtext(:polygon_string, 3067),
                    segment_count = :segment_count,
                    length = :length
                where id = :id
                returning id, version
            """
                .trimIndent()
        val params =
            mapOf(
                "id" to alignmentId.intValue,
                "polygon_string" to alignment.boundingBox?.let(BoundingBox::polygonFromCorners)?.toWkt(),
                "segment_count" to alignment.segments.size,
                "length" to alignment.length.distance,
            )
        jdbcTemplate.setUser()
        val result: RowVersion<LayoutAlignment> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to get new version for Track Layout Alignment")
        logger.daoAccess(AccessType.UPDATE, LayoutAlignment::class, result.id)
        upsertSegments(result, alignment.segmentsWithM)
        return result
    }

    @Transactional
    fun delete(id: IntId<LayoutAlignment>): IntId<LayoutAlignment> {
        val sql = "delete from layout.alignment where id = :id returning id"
        val params = mapOf("id" to id.intValue)
        jdbcTemplate.setUser()
        val deletedRowId = getOne(id, jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutAlignment>("id") })
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedRowId)
        return deletedRowId
    }

    fun getNodeConnectionsNearPoints(
        context: LayoutContext,
        targets: List<MultiPoint>,
        distance: Double,
    ): List<List<NodeTrackConnections>> {
        if (targets.isEmpty()) {
            return listOf()
        }

        val sql =
            """
                with target as materialized (
                  select
                    postgis.st_setsrid(postgis.st_geomfromtext(target_wkt), :srid) as location,
                    ordinality - 1 as ix
                    from unnest(array [:target_wkts]) with ordinality as t(target_wkt, ordinality)
                ),
                  target_edge as materialized (
                    (
                      select e.start_node_id as node_id, e.id as edge_id, target.ix
                        from target
                          join layout.edge e on postgis.st_dwithin(start_location, target.location, :distance)
                    )
                    union all
                    (
                      select e.end_node_id as node_id, e.id as edge_id, target.ix
                        from target
                          join layout.edge e on postgis.st_dwithin(end_location, target.location, :distance)
                    )
                  )
                select
                  lt.id,
                  lt.layout_context_id,
                  lt.version,
                  target_edge.node_id,
                  target_edge.ix
                  from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
                    inner join layout.location_track_version_edge ltve
                               on ltve.location_track_id = lt.id
                                 and ltve.location_track_layout_context_id = lt.layout_context_id
                                 and ltve.location_track_version = lt.version
                    join target_edge using (edge_id)
                  where lt.state != 'DELETED'
            """
                .trimIndent()

        val params =
            mapOf(
                "target_wkts" to targets.map(MultiPoint::toWkt),
                "publication_state" to context.state.name,
                "design_id" to context.branch.designId?.intValue,
                "distance" to distance,
                "srid" to LAYOUT_SRID.code,
            )
        val connectionsByIndex =
            jdbcTemplate
                .query(sql, params) { rs, _ ->
                    val trackVersion = rs.getLayoutRowVersion<LocationTrack>("id", "layout_context_id", "version")
                    val index = rs.getInt("ix")
                    index to (rs.getIntId<LayoutNode>("node_id") to trackVersion)
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, nodeConnections) -> nodeConnections.groupBy({ it.first }, { it.second }) }
        val nodes = fetchNodes(connectionsByIndex.values.flatMap { it.keys }.toSet())
        return targets.indices.map { ix ->
            (connectionsByIndex[ix] ?: mapOf()).let { connections ->
                connections.map { (nodeId, entries) ->
                    NodeTrackConnections(requireNotNull(nodes[nodeId]), entries.toSet())
                }
            }
        }
    }

    fun getNodeConnectionsNear(
        context: LayoutContext,
        target: MultiPoint,
        distance: Double,
    ): List<NodeTrackConnections> = getNodeConnectionsNearPoints(context, listOf(target), distance).first()

    @Transactional
    fun deleteOrphanedAlignments(): List<IntId<LayoutAlignment>> {
        val sql =
            """
                delete
                from layout.alignment alignment
                where not exists(select 1 from layout.reference_line where reference_line.alignment_id = alignment.id)
                returning alignment.id
            """
                .trimIndent()
        jdbcTemplate.setUser()
        val deletedAlignments =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ -> rs.getIntId<LayoutAlignment>("id") }
        logger.daoAccess(AccessType.DELETE, LayoutAlignment::class, deletedAlignments)
        return deletedAlignments
    }

    private fun fetchSegments(alignmentVersion: RowVersion<LayoutAlignment>): List<LayoutSegment> {
        val sql =
            """
                select
                  geometry_alignment_id,
                  geometry_element_index,
                  source_start,
                  source,
                  geometry_id
                from layout.segment_version
                where alignment_id = :alignment_id
                  and alignment_version = :alignment_version
                order by alignment_id, segment_index
            """
                .trimIndent()
        val params =
            mapOf("alignment_id" to alignmentVersion.id.intValue, "alignment_version" to alignmentVersion.version)

        val segmentData =
            jdbcTemplate.query(sql, params) { rs, _ ->
                SegmentData(
                    sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                    sourceStartM = rs.getBigDecimalOrNull("source_start"),
                    source = rs.getEnum("source"),
                    geometryId = rs.getIntId("geometry_id"),
                )
            }
        val geometries = fetchSegmentGeometries(segmentData.map { s -> s.geometryId }.distinct())
        return createSegments(segmentData, geometries)
    }

    fun fetchSegmentGeometriesAndPlanMetadata(
        trackVersion: LayoutRowVersion<LocationTrack>,
        metadataExternalId: Oid<*>?,
        boundingBox: BoundingBox?,
    ): List<SegmentGeometryAndMetadata<LocationTrackM>> {
        val sql =
            """
              with
                segment_range as (
                  select
                    min(array [ltve.edge_index, s.segment_index]) as min_index,
                    max(array [ltve.edge_index, s.segment_index]) as max_index
                    from layout.location_track_version_edge ltve
                      inner join layout.edge_segment s on s.edge_id = ltve.edge_id
                      inner join layout.segment_geometry g on g.id = s.geometry_id
                              where ltve.location_track_id = :location_track_id
                      and ltve.location_track_layout_context_id = :location_track_layout_context_id
                      and ltve.location_track_version = :location_track_version
                      and (
                      :use_bounding_box = false or (
                        postgis.st_intersects(postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid), g.geometry)
                        )
                      )
                ),
                orig_metadata_plan as materialized (
                  select
                    plan.id as plan_id,
                    plan_file.name as file_name
                    from geometry.plan
                      inner join geometry.plan_file on plan.id = plan_file.plan_id
                      inner join geometry.plan_version init_version on init_version.id = plan.id
                    -- Ensure that the plan is from initial imports
                    where init_version.source = 'PAIKANNUSPALVELU'
                      and init_version.version = 1
                      and init_version.change_user = 'IM_IMPORT'
                ),
                orig_metadata as (
                  select
                    current_segment.edge_id as current_edge_id,
                    current_segment.segment_index as current_segment_index,
                    concat(metadata.plan_file_name, '.xml') as plan_file_name,
                    metadata.plan_alignment_name,
                    plan.plan_id
                    from layout.initial_import_metadata metadata
                      inner join layout.initial_edge_segment_metadata segment_metadata
                                 on metadata.id = segment_metadata.metadata_id
                      inner join layout.edge_segment orig_segment
                                 on orig_segment.edge_id = segment_metadata.edge_id
                                   and orig_segment.segment_index = segment_metadata.segment_index
                                   and orig_segment.source = 'IMPORTED'
                      inner join layout.edge_segment current_segment
                                 on current_segment.geometry_id = orig_segment.geometry_id
                                   and current_segment.geometry_alignment_id is null
                      inner join layout.location_track_version_edge ltve
                                 on ltve.edge_id = current_segment.edge_id
                                   and ltve.location_track_id = :location_track_id
                                   and ltve.location_track_layout_context_id = :location_track_layout_context_id
                                   and ltve.location_track_version = :location_track_version
                      inner join layout.segment_geometry on orig_segment.geometry_id = segment_geometry.id
                      left join orig_metadata_plan plan on plan.file_name = concat(metadata.plan_file_name, '.xml')
                    where metadata.alignment_external_id = :external_id
                ),
                segments as (
                  select
                    segment.edge_id,
                    ltve.edge_index,
                    segment.segment_index,
                    segment.geometry_id,
                    segment.source,
                    ltve.start_m + segment.start_m as start_m,
                    geom_alignment.id as geom_alignment_id,
                    geom_alignment.id is not null as is_linked,
                    coalesce(plan_file.plan_id, orig_metadata.plan_id) as plan_id,
                    coalesce(plan_file.name, orig_metadata.plan_file_name) as file_name,
                    coalesce(geom_alignment.name, orig_metadata.plan_alignment_name) as alignment_name,
                    (
                          row_number() over (order by ltve.edge_index, segment.segment_index) - row_number() over (
                            partition by
                              geom_alignment.id is not null,
                              coalesce(plan_file.plan_id, orig_metadata.plan_id),
                              coalesce(plan_file.name, orig_metadata.plan_file_name),
                              coalesce(geom_alignment.name, orig_metadata.plan_alignment_name)
                            order by ltve.edge_index, segment.segment_index
                            )
                      ) as grp
                    from layout.edge_segment segment
                      inner join layout.location_track_version_edge ltve
                                 on ltve.edge_id = segment.edge_id
                                   and ltve.location_track_id = :location_track_id
                                   and ltve.location_track_layout_context_id = :location_track_layout_context_id
                                   and ltve.location_track_version = :location_track_version
                      left join geometry.alignment geom_alignment on segment.geometry_alignment_id = geom_alignment.id
                      left join geometry.plan_file on plan_file.plan_id = geom_alignment.plan_id
                      left join orig_metadata
                                on orig_metadata.current_edge_id = segment.edge_id
                                  and orig_metadata.current_segment_index = segment.segment_index
                ),
                metadata_segments as (
                  select
                    min(array [edge_index, segment_index]) as min_index,
                    max(array [edge_index, segment_index]) as max_index,
                    common.first(geometry_id order by edge_index, segment_index) as from_geom_id,
                    common.last(geometry_id order by edge_index, segment_index) as to_geom_id,
                    common.first(start_m order by edge_index, segment_index) as from_start_m,
                    common.last(start_m order by edge_index, segment_index) as to_start_m,
                    is_linked,
                    plan_id,
                    file_name,
                    geom_alignment_id,
                    alignment_name
                    from segments
                    group by is_linked, grp, plan_id, file_name, geom_alignment_id, alignment_name
                )
              select
                segment.is_linked,
                segment.plan_id,
                segment.file_name,
                segment.geom_alignment_id,
                segment.alignment_name,
                concat(array_to_string(segment.min_index, '_'), '_', array_to_string(segment.max_index, '_')) range_id,
                postgis.st_x(postgis.st_startpoint(start_geom.geometry)) as start_x,
                postgis.st_y(postgis.st_startpoint(start_geom.geometry)) as start_y,
                postgis.st_m(postgis.st_startpoint(start_geom.geometry)) + from_start_m as start_m,
                postgis.st_x(postgis.st_endpoint(end_geom.geometry)) as end_x,
                postgis.st_y(postgis.st_endpoint(end_geom.geometry)) as end_y,
                postgis.st_m(postgis.st_endpoint(end_geom.geometry)) + to_start_m as end_m
                from segment_range range
                  inner join metadata_segments segment on segment.max_index >= range.min_index and segment.min_index <= range.max_index
                  left join layout.segment_geometry start_geom on start_geom.id = from_geom_id
                  left join layout.segment_geometry end_geom on end_geom.id = to_geom_id
                order by segment.min_index, segment.max_index;
            """
                .trimIndent()
        val params =
            mapOf(
                "location_track_id" to trackVersion.id.intValue,
                "location_track_layout_context_id" to trackVersion.context.toSqlString(),
                "location_track_version" to trackVersion.version,
                "external_id" to metadataExternalId,
                "use_bounding_box" to (boundingBox != null),
                "x_min" to boundingBox?.min?.x,
                "y_min" to boundingBox?.min?.y,
                "x_max" to boundingBox?.max?.x,
                "y_max" to boundingBox?.max?.y,
                "layout_srid" to LAYOUT_SRID.code,
            )
        val result =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val rangeId = rs.getString("range_id")
                SegmentGeometryAndMetadata<LocationTrackM>(
                    planId = rs.getIntIdOrNull("plan_id"),
                    fileName = rs.getFileNameOrNull("file_name"),
                    alignmentId = rs.getIntIdOrNull("geom_alignment_id"),
                    alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                    startPoint = rs.getPoint3DMOrNull("start_x", "start_y", "start_m"),
                    endPoint = rs.getPoint3DMOrNull("end_x", "end_y", "end_m"),
                    isLinked = rs.getBoolean("is_linked"),
                    id = StringId("${trackVersion.id.intValue}_$rangeId"),
                )
            }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, trackVersion)
        return result
    }

    fun fetchSegmentGeometriesAndPlanMetadata(
        alignmentVersion: RowVersion<LayoutAlignment>,
        metadataExternalId: Oid<*>?,
        boundingBox: BoundingBox?,
    ): List<SegmentGeometryAndMetadata<ReferenceLineM>> {
        // language=SQL
        val sql =
            """
                with
                  segment_range as (
                    select
                      alignment_id,
                      alignment_version,
                      min(segment_index) as min_index,
                      max(segment_index) as max_index
                    from layout.segment_version
                      inner join layout.segment_geometry on segment_geometry.id = segment_version.geometry_id
                    where alignment_id = :alignment_id
                      and alignment_version = :alignment_version
                      and (
                          :use_bounding_box = false or postgis.st_intersects(
                            postgis.st_makeenvelope (:x_min, :y_min, :x_max, :y_max, :layout_srid),
                            geometry
                          )
                      )
                    group by alignment_id, alignment_version
                  ),
                  orig_metadata_plan as materialized (
                    select
                      plan.id as plan_id,
                      plan_file.name as file_name
                    from geometry.plan
                      inner join geometry.plan_file on plan.id = plan_file.plan_id
                      -- Ensure that the plan is from initial imports
                      inner join geometry.plan_version init_version
                              on init_version.id = plan.id and init_version.version = 1 and init_version.change_user = 'IM_IMPORT'
                    where plan.source = 'PAIKANNUSPALVELU'
                  ),
                  orig_metadata as (
                    select
                      current_segment.alignment_id as current_alignment_id,
                      current_segment.alignment_version as current_alignment_version,
                      current_segment.segment_index as current_segment_index,
                      concat(metadata.plan_file_name, '.xml') as plan_file_name,
                      metadata.plan_alignment_name,
                      plan.plan_id

                    from layout.initial_import_metadata metadata
                      inner join layout.initial_segment_metadata segment_metadata on
                        metadata.id = segment_metadata.metadata_id
                      inner join layout.segment_version segment on
                        segment.alignment_id = segment_metadata.alignment_id
                        and segment.alignment_version = 1
                        and segment.segment_index = segment_metadata.segment_index
                        and segment.source = 'IMPORTED'
                      inner join layout.segment_version current_segment on
                        current_segment.geometry_id = segment.geometry_id
                        and current_segment.alignment_id = :alignment_id
                        and current_segment.alignment_version = :alignment_version
                        and current_segment.geometry_alignment_id is null
                      left join layout.segment_geometry on segment.geometry_id = segment_geometry.id
                      left join orig_metadata_plan plan on plan.file_name = concat(metadata.plan_file_name, '.xml')

                    where metadata.alignment_external_id = :external_id
                  ),
                  segments as (
                    select
                      segment.alignment_id,
                      segment.alignment_version,
                      segment.segment_index,
                      segment.geometry_id,
                      segment.source,
                      segment.start,
                      geom_alignment.id as geom_alignment_id,
                      geom_alignment.id is not null as is_linked,
                      coalesce(plan_file.plan_id, orig_metadata.plan_id) as plan_id,
                      coalesce(plan_file.name, orig_metadata.plan_file_name) as file_name,
                      coalesce(geom_alignment.name, orig_metadata.plan_alignment_name) as alignment_name,
                      row_number() over (order by segment.segment_index) - row_number() over (
                        partition by
                          geom_alignment.id is not null,
                          coalesce(plan_file.plan_id, orig_metadata.plan_id),
                          coalesce(plan_file.name, orig_metadata.plan_file_name),
                          coalesce(geom_alignment.name, orig_metadata.plan_alignment_name)
                        order by segment.segment_index
                      ) as grp
                    from layout.segment_version segment
                      left join geometry.alignment geom_alignment on segment.geometry_alignment_id = geom_alignment.id
                      left join geometry.plan_file on plan_file.plan_id = geom_alignment.plan_id
                      left join orig_metadata on
                        orig_metadata.current_alignment_id = segment.alignment_id
                        and orig_metadata.current_alignment_version = segment.alignment_version
                        and orig_metadata.current_segment_index = segment.segment_index
                    where segment.alignment_id = :alignment_id
                      and segment.alignment_version = :alignment_version
                  ),
                  metadata_segments as (
                    select
                      alignment_id,
                      alignment_version,
                      min(segment_index) as from_segment,
                      max(segment_index) as to_segment,
                      common.first(geometry_id order by segment_index) as from_geom_id,
                      common.last(geometry_id order by segment_index) as to_geom_id,
                      is_linked,
                      plan_id,
                      file_name,
                      geom_alignment_id,
                      alignment_name,
                      min(start) as start
                    from segments
                    group by alignment_id, alignment_version, is_linked, grp, plan_id, file_name, geom_alignment_id, alignment_name
                  )
                select
                  segment.*,
                  postgis.st_x(postgis.st_startpoint(start_geom.geometry)) as start_x,
                  postgis.st_y(postgis.st_startpoint(start_geom.geometry)) as start_y,
                  postgis.st_m(postgis.st_startpoint(start_geom.geometry)) + segment.start as start_m,
                  postgis.st_x(postgis.st_endpoint(end_geom.geometry)) as end_x,
                  postgis.st_y(postgis.st_endpoint(end_geom.geometry)) as end_y,
                  postgis.st_m(postgis.st_endpoint(end_geom.geometry)) + segment.start as end_m
                from segment_range range
                  inner join metadata_segments segment on
                      range.alignment_id = segment.alignment_id and range.alignment_version = segment.alignment_version
                  left join layout.segment_geometry start_geom on start_geom.id = from_geom_id
                  left join layout.segment_geometry end_geom on end_geom.id = to_geom_id
                where range.max_index >= segment.from_segment
                  and range.min_index <= segment.to_segment
                order by segment.from_segment, segment.to_segment
            """
                .trimIndent()
        val params =
            mapOf(
                "alignment_id" to alignmentVersion.id.intValue,
                "alignment_version" to alignmentVersion.version,
                "external_id" to metadataExternalId,
                "use_bounding_box" to (boundingBox != null),
                "x_min" to boundingBox?.min?.x,
                "y_min" to boundingBox?.min?.y,
                "x_max" to boundingBox?.max?.x,
                "y_max" to boundingBox?.max?.y,
                "layout_srid" to LAYOUT_SRID.code,
            )
        val result =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val fromSegment = rs.getInt("from_segment")
                val toSegment = rs.getInt("to_segment")
                SegmentGeometryAndMetadata<ReferenceLineM>(
                    planId = rs.getIntIdOrNull("plan_id"),
                    fileName = rs.getFileNameOrNull("file_name"),
                    alignmentId = rs.getIntIdOrNull("geom_alignment_id"),
                    alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                    startPoint = rs.getPoint3DMOrNull("start_x", "start_y", "start_m"),
                    endPoint = rs.getPoint3DMOrNull("end_x", "end_y", "end_m"),
                    isLinked = rs.getBoolean("is_linked"),
                    id = StringId("${alignmentVersion.id.intValue}_${fromSegment}_${toSegment}"),
                )
            }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, alignmentVersion)
        return result
    }

    fun fetchMetadata(trackVersion: LayoutRowVersion<LocationTrack>): List<LayoutSegmentMetadata> {
        // language=SQL
        val sql =
            """
                select
                  postgis.st_x(postgis.st_startpoint(segment_geometry.geometry)) as start_point_x,
                  postgis.st_y(postgis.st_startpoint(segment_geometry.geometry)) as start_point_y,
                  postgis.st_x(postgis.st_endpoint(segment_geometry.geometry)) as end_point_x,
                  postgis.st_y(postgis.st_endpoint(segment_geometry.geometry)) as end_point_y,
                  alignment.name as alignment_name,
                  plan.plan_time,
                  plan.measurement_method,
                  plan.srid,
                  plan_file.name as file_name
                from layout.edge_segment
                  inner join layout.segment_geometry on edge_segment.geometry_id = segment_geometry.id
                  left join layout.edge on edge_segment.edge_id = edge.id
                  left join layout.location_track_version_edge lt_edge on edge.id = lt_edge.edge_id
                  left join geometry.alignment on alignment.id = edge_segment.geometry_alignment_id
                  left join geometry.plan on alignment.plan_id = plan.id
                  left join geometry.plan_file on plan_file.plan_id = plan.id
                where lt_edge.location_track_id = :location_track_id
                  and lt_edge.location_track_layout_context_id = :location_track_layout_context_id
                  and lt_edge.location_track_version = :location_track_version
                order by lt_edge.edge_index, edge_segment.segment_index
            """
                .trimIndent()

        val params =
            mapOf(
                "location_track_id" to trackVersion.id.intValue,
                "location_track_layout_context_id" to trackVersion.context.toSqlString(),
                "location_track_version" to trackVersion.version,
            )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            LayoutSegmentMetadata(
                startPoint = rs.getPoint("start_point_x", "start_point_y"),
                endPoint = rs.getPoint("end_point_x", "end_point_y"),
                alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                planTime = rs.getInstantOrNull("plan_time"),
                measurementMethod = rs.getEnumOrNull<MeasurementMethod>("measurement_method"),
                fileName = rs.getString("file_name")?.let(::FileName),
                originalSrid = rs.getSridOrNull("srid"),
            )
        }
    }

    fun fetchLocationTrackProfileInfos(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        hasProfileInfo: Boolean? = null,
    ): List<MapSegmentProfileInfo<LocationTrack, LocationTrackM>> {
        // language=SQL
        val sql =
            """
                select *
                  from (
                    select
                      location_track.id,
                      ltve.start_m+edge_segment.start_m as start_m,
                      postgis.st_m(postgis.st_endpoint(segment_geometry.geometry)) as length,
                      (plan.vertical_coordinate_system is not null)
                        and exists(
                        select *
                          from geometry.vertical_intersection vi
                          where vi.alignment_id = alignment.id
                      ) as has_profile_info
                      from layout.location_track_in_layout_context(
                          :publication_state::layout.publication_state,
                          :design_id) location_track
                        join layout.location_track_version_edge ltve
                             on location_track.id = ltve.location_track_id
                               and location_track.layout_context_id = ltve.location_track_layout_context_id
                               and location_track.version = ltve.location_track_version
                        join layout.edge on edge.id = ltve.edge_id
                        join layout.edge_segment on edge.id = edge_segment.edge_id
                        join layout.segment_geometry on edge_segment.geometry_id = segment_geometry.id
                        left join geometry.alignment on alignment.id = edge_segment.geometry_alignment_id
                        left join geometry.plan on alignment.plan_id = plan.id
                      where postgis.st_intersects(
                          postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                          location_track.bounding_box
                        )
                        and postgis.st_intersects(
                          postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                          edge.bounding_box
                        )
                        and postgis.st_intersects(
                          postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                          segment_geometry.bounding_box
                        )
                        and location_track.state != 'DELETED'
                  ) s
                  where ((:has_profile_info::boolean is null) or :has_profile_info = has_profile_info)
                  order by id, start_m
            """
                .trimIndent()

        val params =
            mapOf(
                "x_min" to bbox.min.x,
                "y_min" to bbox.min.y,
                "x_max" to bbox.max.x,
                "y_max" to bbox.max.y,
                "layout_srid" to LAYOUT_SRID.code,
                "publication_state" to layoutContext.state.name,
                "design_id" to layoutContext.branch.designId?.intValue,
                "has_profile_info" to hasProfileInfo,
            )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val mRange = rs.getDouble("start_m").let { start -> Range(start, start + rs.getDouble("length")) }
            MapSegmentProfileInfo(
                id = rs.getIntId("id"),
                mRange = mRange.map(::LineM),
                hasProfile = rs.getBoolean("has_profile_info"),
            )
        }
    }

    private fun upsertSegments(
        alignmentId: RowVersion<LayoutAlignment>,
        segments: List<Pair<LayoutSegment, Range<out LineM<*>>>>,
    ) {
        if (segments.isNotEmpty()) {
            val newGeometryIds =
                insertSegmentGeometries(
                    segments.mapNotNull { (s, _) -> if (s.geometry.id is StringId) s.geometry else null }
                )
            // language=SQL
            val sqlIndexed =
                """
                  insert into layout.segment_version(
                    alignment_id,
                    alignment_version,
                    segment_index,
                    start,
                    geometry_alignment_id,
                    geometry_element_index,
                    source_start,
                    source,
                    geometry_id
                  )
                  values(?, ?, ?, ?, ?, ?, ?, ?::layout.geometry_source, ?)
                """
                    .trimIndent()
            // This uses indexed parameters (rather than named ones),
            // since named parameter template's batch-method is considerably slower
            jdbcTemplate.batchUpdateIndexed(sqlIndexed, segments) { ps, (index, segmentAndM) ->
                val (s, m) = segmentAndM
                ps.setInt(1, alignmentId.id.intValue)
                ps.setInt(2, alignmentId.version)
                ps.setInt(3, index)
                ps.setDouble(4, m.min.distance)
                ps.setNullableInt(5) { if (s.sourceId is IndexedId) s.sourceId.parentId else null }
                ps.setNullableInt(6) { if (s.sourceId is IndexedId) s.sourceId.index else null }
                ps.setNullableBigDecimal(7, s.sourceStartM)
                ps.setString(8, s.source.name)
                val geometryId =
                    if (s.geometry.id is IntId) s.geometry.id
                    else
                        requireNotNull(newGeometryIds[s.geometry.id]) {
                            "SegmentGeometry not stored: id=${s.geometry.id}"
                        }
                ps.setInt(9, geometryId.intValue)
            }
        }
    }

    // Batching this is a little tricky due to difficulty in mapping generated ids:
    //  There is no guarantee of result set order (though it's usually the insert order)
    private fun insertSegmentGeometries(
        geometries: List<SegmentGeometry>
    ): Map<StringId<SegmentGeometry>, IntId<SegmentGeometry>> {
        require(geometries.all { geom -> geom.segmentStart.m.distance == 0.0 }) {
            "Geometries in DB must be set to startM=0.0, so they remain valid if an earlier segment changes"
        }
        require(
            geometries.all { geom ->
                val calculatedLength = calculateDistance(geom.segmentPoints, LAYOUT_SRID)
                val maxDelta = calculatedLength * 0.01
                abs(calculatedLength - geom.segmentEnd.m.distance) <= maxDelta
            }
        ) {
            "Geometries in DB should have (approximately) endM=length"
        }
        // language=SQL
        val sql =
            """
              insert into layout.segment_geometry(
                resolution,
                geometry,
                height_values,
                cant_values
              )
              values(
                :resolution,
                postgis.st_setsrid(:line_string::postgis.geometry, :srid),
                string_to_array(:height_values, ',', 'null')::decimal[],
                string_to_array(:cant_values, ',', 'null')::decimal[]
              )
              on conflict (hash) do update
              set resolution = segment_geometry.resolution -- no-op update so that returns clause works on conflict as well
              returning id
            """
                .trimIndent()
        return geometries.associate { geometry ->
            val params =
                mapOf(
                    "resolution" to geometry.resolution,
                    "line_string" to create3DMLineString(geometry.segmentPoints),
                    "srid" to LAYOUT_SRID.code,
                    "height_values" to createListString(geometry.segmentPoints) { p -> p.z },
                    "cant_values" to createListString(geometry.segmentPoints) { p -> p.cant },
                )
            jdbcTemplate
                .query(sql, params) { rs, _ -> geometry.id as StringId to rs.getIntId<SegmentGeometry>("id") }
                .first()
        }
    }

    private fun fetchSegmentGeometries(
        ids: List<IntId<SegmentGeometry>>
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return segmentGeometryCache.getAll(ids) { fetchIds -> fetchSegmentGeometriesInternal(fetchIds) }
    }

    private fun fetchSegmentGeometriesInternal(
        ids: Set<IntId<SegmentGeometry>>
    ): Map<IntId<SegmentGeometry>, SegmentGeometry> {
        return if (ids.isNotEmpty()) {
            logger.info("Fetching segment geometries from DB: ${ids.size}")
            val sql =
                """
                  select
                    id,
                    postgis.st_astext(geometry) as geometry_wkt,
                    resolution,
                    case
                      when height_values is null then null
                      else array_to_string(height_values, ',', 'null')
                    end as height_values,
                    case
                      when cant_values is null then null
                      else array_to_string(cant_values, ',', 'null')
                    end as cant_values
                  from layout.segment_geometry
                  where id in (:ids)
                """
                    .trimIndent()
            ids.chunked(10_000)
                .parallelStream()
                .flatMap { idsChunk ->
                    val params = mapOf("ids" to idsChunk.map(IntId<SegmentGeometry>::intValue))
                    jdbcTemplate
                        .query(sql, params) { rs, _ ->
                            GeometryRowResult(
                                id = rs.getIntId("id"),
                                wktString = rs.getString("geometry_wkt"),
                                heightString = rs.getString("height_values"),
                                cantString = rs.getString("cant_values"),
                                resolution = rs.getInt("resolution"),
                            )
                        }
                        .stream()
                }
                .collect(Collectors.toMap(GeometryRowResult::id, ::parseGeometry))
        } else mapOf()
    }

    private fun fetchAllLiveSegmentGeometryIds(): List<IntId<SegmentGeometry>> {
        val sql =
            """
            select
              distinct geometry_id as id
              from
                (
                  select sv.geometry_id
                    from layout.segment_version sv
                      inner join layout.alignment a on a.id = sv.alignment_id and a.version = sv.alignment_version
                  union all
                  select s.geometry_id
                    from layout.edge_segment s
                      inner join layout.location_track_version_edge ltve on ltve.edge_id = s.edge_id
                      inner join layout.location_track lt
                                 on ltve.location_track_id = lt.id
                                   and ltve.location_track_layout_context_id = lt.layout_context_id
                                   and ltve.location_track_version = lt.version
                ) tmp;
        """

        return jdbcTemplate.query(sql) { rs, _ -> rs.getIntId("id") }
    }

    fun preloadSegmentGeometries(): Int {
        val ids = fetchAllLiveSegmentGeometryIds()
        segmentGeometryCache.getAll(ids, ::fetchSegmentGeometriesInternal)
        return ids.size
    }
}

data class GeometryRowResult(
    val id: IntId<SegmentGeometry>,
    val wktString: String,
    val cantString: String?,
    val heightString: String?,
    val resolution: Int,
)

private fun parseGeometry(row: GeometryRowResult): SegmentGeometry =
    SegmentGeometry(
        id = row.id,
        segmentPoints = parseSegmentPointsWkt(row.wktString, row.heightString, row.cantString),
        resolution = row.resolution,
    )

private fun parseSegmentPointsWkt(
    geometryWkt: String,
    heightString: String? = null,
    cantString: String? = null,
): List<SegmentPoint> {
    val heightValues = parseNullableDoubleList(heightString)
    val cantValues = parseNullableDoubleList(cantString)
    return parseSegmentPointLineString(geometryWkt, heightValues, cantValues)
}

fun parseSegmentPointLineString(
    lineString: String,
    heights: List<Double?>?,
    cants: List<Double?>?,
): List<SegmentPoint> =
    get3DMLineStringContent(lineString).let { pointStrings ->
        require(heights == null || heights.size == pointStrings.size) {
            "Height value count should match point count: points=${pointStrings.size} heights=${heights?.size}"
        }
        require(cants == null || cants.size == pointStrings.size) {
            "Cant value count should match point count: points=${pointStrings.size} cants=${cants?.size}"
        }
        pointStrings.mapIndexed { i, p -> parseSegmentPoint(p, heights?.get(i), cants?.get(i)) }
    }

private fun parseNullableDoubleList(listString: String?): List<Double?>? =
    listString?.split(",")?.map(String::toDoubleOrNull)

private fun createEdges(
    edgeResults: List<EdgeData>,
    nodes: Map<IntId<LayoutNode>, DbLayoutNode>,
    geometries: Map<IntId<SegmentGeometry>, SegmentGeometry>,
): List<DbLayoutEdge> {
    fun getConnection(id: IntId<LayoutNode>, port: NodePortType): DbNodeConnection {
        val node = requireNotNull(nodes[id]) { "Nodes should be pre-fetched before creating edges: missing=$id" }
        return DbNodeConnection(port, node)
    }
    return edgeResults
        .parallelStream()
        .map { edgeData ->
            DbLayoutEdge(
                    id = edgeData.id,
                    startNode = edgeData.startNode.let { (id, port) -> getConnection(id, port) },
                    endNode = edgeData.endNode.let { (id, port) -> getConnection(id, port) },
                    segments = createSegments(edgeData.segments, geometries),
                )
                .also { it.contentHash }
        }
        .collect(Collectors.toList())
}

private fun createSegments(
    segmentResults: List<SegmentData>,
    geometries: Map<IntId<SegmentGeometry>, SegmentGeometry>,
): List<LayoutSegment> {
    var start = 0.0
    return segmentResults.map { data ->
        val geometry =
            requireNotNull(geometries[data.geometryId]) { "Fetching geometry failed for segment: data=$data" }
        LayoutSegment(
                sourceId = data.sourceId,
                sourceStartM = data.sourceStartM,
                source = data.source,
                geometry = geometry,
            )
            .also { start += geometry.length }
    }
}

private data class EdgeData(
    val id: IntId<LayoutEdge>,
    val startNode: Pair<IntId<LayoutNode>, NodePortType>,
    val endNode: Pair<IntId<LayoutNode>, NodePortType>,
    val segments: List<SegmentData>,
)

private data class SegmentData(
    val sourceId: IndexedId<GeometryElement>?,
    val sourceStartM: BigDecimal?,
    val source: GeometrySource,
    val geometryId: IntId<SegmentGeometry>,
)
