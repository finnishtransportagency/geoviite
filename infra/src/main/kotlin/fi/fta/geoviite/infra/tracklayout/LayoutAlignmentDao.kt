package fi.fta.geoviite.infra.tracklayout

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.layoutCacheDuration
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.roundTo6Decimals
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.LAYOUT_ALIGNMENT
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.math.abs
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

const val NODE_CACHE_SIZE = 50000L
const val EDGE_CACHE_SIZE = 100000L
const val ALIGNMENT_CACHE_SIZE = 10000L
const val GEOMETRY_CACHE_SIZE = 500000L

data class MapSegmentProfileInfo<T>(
    val id: IntId<T>,
    val alignmentId: IndexedId<LayoutSegment>,
    val segmentStartM: Double,
    val segmentEndM: Double,
    val hasProfile: Boolean,
)

@Transactional(readOnly = true)
@Component
class LayoutAlignmentDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") val cacheEnabled: Boolean,
) : DaoBase(jdbcTemplateParam) {

    private val nodeIdsByHash: ConcurrentHashMap<Int, IntId<LayoutNode>> = ConcurrentHashMap()
    private val nodesCache: Cache<IntId<LayoutNode>, LayoutNode> =
        Caffeine.newBuilder().maximumSize(NODE_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val edgeIdsByHash: ConcurrentHashMap<Int, IntId<LayoutEdge>> = ConcurrentHashMap()
    private val edgesCache: Cache<IntId<LayoutEdge>, LayoutEdge> =
        Caffeine.newBuilder().maximumSize(EDGE_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val locationTrackGeometryCache: Cache<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> =
        Caffeine.newBuilder().maximumSize(ALIGNMENT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val alignmentsCache: Cache<RowVersion<LayoutAlignment>, LayoutAlignment> =
        Caffeine.newBuilder().maximumSize(ALIGNMENT_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    private val segmentGeometryCache: Cache<IntId<SegmentGeometry>, SegmentGeometry> =
        Caffeine.newBuilder().maximumSize(GEOMETRY_CACHE_SIZE).expireAfterAccess(layoutCacheDuration).build()

    fun fetchVersions() = fetchRowVersions<LayoutAlignment>(LAYOUT_ALIGNMENT)

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun getNode(id: IntId<LayoutNode>): LayoutNode =
        nodesCache.get(id) {
            val node = fetchNodes(id).single()
            nodeIdsByHash[node.contentHash] = id
            node
        }

    fun preloadNodes(): Int {
        val nodes = fetchNodes(id = null)
        nodesCache.putAll(nodes.associateBy(LayoutNode::id))
        nodeIdsByHash.putAll(nodes.associate { n -> n.contentHash to n.id })
        return nodes.size
    }

    private fun fetchNodes(id: IntId<LayoutNode>?): List<LayoutNode> {
        val sql =
            """
            select 
              node.id,
              node.type,
              node.switch_in_id,
              node.switch_in_joint_number,
              node.switch_in_joint_role,
              node.switch_out_id,
              node.switch_out_joint_number,
              node.switch_out_joint_role,
              node.starting_location_track_id,
              node.ending_location_track_id
            from layout.node
            where (:id::int is null or node.id = :id)
            group by node.id 
        """
                .trimIndent()
        val params = mapOf("id" to id?.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            val type = rs.getEnum<LayoutNodeType>("type")
            val content =
                when (type) {
                    LayoutNodeType.TRACK_START -> LayoutNodeStartTrack(rs.getIntId("starting_location_track_id"))
                    LayoutNodeType.TRACK_END -> LayoutNodeEndTrack(rs.getIntId("ending_location_track_id"))
                    LayoutNodeType.SWITCH -> {
                        LayoutNodeSwitch(
                            switchIn =
                                rs.getIntIdOrNull<LayoutSwitch>("switch_in_id")?.let { id ->
                                    SwitchLink(
                                        id,
                                        rs.getEnum("switch_in_joint_role"),
                                        rs.getJointNumber("switch_in_joint_number"),
                                    )
                                },
                            switchOut =
                                rs.getIntIdOrNull<LayoutSwitch>("switch_out_id")?.let { id ->
                                    SwitchLink(
                                        id,
                                        rs.getEnum("switch_out_joint_role"),
                                        rs.getJointNumber("switch_out_joint_number"),
                                    )
                                },
                        )
                    }
                }
            LayoutNode(rs.getIntId("id"), content)
        }
    }

    @Transactional
    fun getOrCreateNode(content: ILayoutNodeContent): LayoutNode =
        if (content is LayoutNode) content else getNode(nodeIdsByHash[content.contentHash] ?: saveNode(content))

    private fun saveNode(content: ILayoutNodeContent): IntId<LayoutNode> {
        val sql =
            """
            select layout.get_or_insert_node(
                :switch_in_id,
                :switch_in_joint_number,
                :switch_in_joint_role,
                :switch_out_id,
                :switch_out_joint_number,
                :switch_out_joint_role,
                :start_track_id,
                :end_track_id
            )
        """
        val params =
            mapOf(
                "switch_in_id" to content.switchIn?.id?.intValue,
                "switch_in_joint_number" to content.switchIn?.jointNumber?.intValue,
                "switch_in_joint_role" to content.switchIn?.jointRole?.name,
                "switch_out_id" to content.switchOut?.id?.intValue,
                "switch_out_joint_number" to content.switchOut?.jointNumber?.intValue,
                "switch_out_joint_role" to content.switchOut?.jointRole?.name,
                "start_track_id" to content.startingTrackId?.intValue,
                "end_track_id" to content.endingTrack?.intValue,
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutNode>("id") }.single()
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun getEdge(id: IntId<LayoutEdge>): LayoutEdge =
        edgesCache.get(id) {
            val edge = fetchEdges(ids = listOf(id), active = false).values.single()
            edgeIdsByHash[edge.contentHash] = id
            edge
        }

    fun getEdges(ids: List<IntId<LayoutEdge>>) = edgesCache.getAll(ids) { missing -> fetchEdges(missing, false) }

    fun preloadEdges(): Int {
        val edges = fetchEdges(ids = null, active = true)
        //        logger.info("Preloaded: ${edges.keys.toList()}")
        edgesCache.putAll(edges)
        edgeIdsByHash.putAll(edges.values.associate { n -> n.contentHash to n.id })
        return edges.size
    }

    private fun fetchEdges(ids: Iterable<IntId<LayoutEdge>>?, active: Boolean): Map<IntId<LayoutEdge>, LayoutEdge> {
        logger.info("Fetching edges: ids=$ids active=$active")
        val sql =
            """
            select
              e.id,
              e.start_node_id,
              e.end_node_id,
              array_agg(s.segment_index order by s.segment_index) as indices,
              array_agg(s.geometry_alignment_id order by s.segment_index) as geometry_alignment_ids,
              array_agg(s.geometry_element_index order by s.segment_index) as geometry_element_indices,
              array_agg(s.source_start order by s.segment_index) as source_start_m_values,
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
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val edgeId = rs.getIntId<LayoutEdge>("id")
                val segmentIndices = rs.getIntArray("indices")
                val geometryAlignmentIds =
                    rs.getNullableIntArray("geometry_alignment_ids").also { require(it.size == segmentIndices.size) }
                val geometryElementIndices =
                    rs.getNullableIntArray("geometry_element_indices").also { require(it.size == segmentIndices.size) }
                val sourceStartMValues =
                    rs.getNullableBigDecimalArray("source_start_m_values").also {
                        require(it.size == segmentIndices.size)
                    }
                val sources =
                    rs.getEnumArray<GeometrySource>("sources").also { require(it.size == segmentIndices.size) }
                val geometryIds =
                    rs.getIntIdArray<SegmentGeometry>("geometry_ids").also { require(it.size == segmentIndices.size) }
                val segmentGeometries = fetchSegmentGeometries(geometryIds)
                val segments =
                    //                    segmentIndices.mapIndexed { i, segmentIndex ->
                    segmentIndices.map { i ->
                        val geometryAlignmentId = geometryAlignmentIds[i]
                        val geometryElementIndex = geometryElementIndices[i]
                        val sourceId: IndexedId<GeometryElement>? =
                            if (geometryAlignmentId != null) {
                                IndexedId(geometryAlignmentId, requireNotNull(geometryElementIndex))
                            } else {
                                null
                            }
                        LayoutSegment(
                            //                            id = IndexedId(edgeId.intValue, segmentIndex),
                            sourceId = sourceId,
                            sourceStart = sourceStartMValues[i]?.toDouble(),
                            source = sources[i],
                            geometry = requireNotNull(segmentGeometries[geometryIds[i]]),
                        )
                    }
                LayoutEdge(
                    id = edgeId,
                    content =
                        LayoutEdgeContent(
                            //                            startNodeId = rs.getIntId("start_node_id"),
                            //                            endNodeId = rs.getIntId("end_node_id"),
                            // TODO: GVT-1727 fetch with single query or just trust in the cache?
                            startNode = getNode(rs.getIntId("start_node_id")),
                            endNode = getNode(rs.getIntId("end_node_id")),
                            segments = segments,
                        ),
                )
            }
            .associateBy(LayoutEdge::id)
    }

    @Transactional
    fun getOrCreateEdge(content: ILayoutEdge): LayoutEdge =
        when (content) {
            is LayoutEdge -> content
            is LayoutEdgeContent ->
                saveContentGeometry(content).let { updatedContent ->
                    getEdge(edgeIdsByHash[updatedContent.contentHash] ?: saveEdge(updatedContent))
                }
            else -> error("Unknown edge content type ${content::class.simpleName}")
        }

    private fun saveEdge(content: LayoutEdgeContent): IntId<LayoutEdge> {
        val startNodeId = getOrCreateNode(content.startNode).id
        val endNodeId = getOrCreateNode(content.endNode).id
        val sql =
            """
            select layout.get_or_insert_edge(
              :start_node_id,
              :end_node_id,
              :geometry_alignment_ids,
              :geometry_element_indices,
              :start_m_values,
              :source_start_m_values,
              :sources,
              :geometry_ids
            ) as id
        """
        val params =
            mapOf(
                "start_node_id" to startNodeId.intValue,
                "end_node_id" to endNodeId.intValue,
                "geometry_alignment_ids" to content.segments.map { s -> s.sourceId?.parentId }.toTypedArray(),
                "geometry_element_indices" to content.segments.map { s -> s.sourceId?.index }.toTypedArray(),
                "start_m_values" to content.segmentMValues.map { m -> roundTo6Decimals(m.min) }.toTypedArray(),
                "source_start_m_values" to
                    content.segments.map { s -> s.sourceStart?.let(::roundTo6Decimals) }.toTypedArray(),
                "sources" to content.segments.map { s -> s.source.name }.toTypedArray(),
                "geometry_ids" to content.segments.map { s -> (s.geometry.id as IntId).intValue }.toTypedArray(),
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<LayoutEdge>("id") }.single()
    }

    private fun saveContentGeometry(content: LayoutEdgeContent): LayoutEdgeContent {
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

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun get(trackVersion: LayoutRowVersion<LocationTrack>): DbLocationTrackGeometry =
        locationTrackGeometryCache.get(trackVersion) { version ->
            fetchLocationTrackGeometry(version, false).values.single()
        }

    private fun fetchLocationTrackGeometry(
        trackVersion: LayoutRowVersion<LocationTrack>?,
        active: Boolean,
    ): Map<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> {
        val sql =
            """
            select
              location_track_id as id,
              location_track_layout_context_id as layout_context_id,
              location_track_version as version,
              array_agg(edge_id order by edge_index) as edge_ids
            from layout.location_track_version_edge
            where (:id::int is null or (
                location_track_id = :id and location_track_layout_context_id = :layout_context_id and location_track_version = :version
              ))
              and (:active = false or exists(
                select 1
                from layout.location_track t
                where t.id = location_track_id and t.layout_context_id = location_track_layout_context_id and t.version = location_track_version
              ))
            group by location_track_id, location_track_layout_context_id, location_track_version
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
    }

    @Transactional
    fun saveLocationTrackGeometry(trackVersion: LayoutRowVersion<LocationTrack>, content: LocationTrackGeometry) {
        val edges = content.edges.associate { e -> e.contentHash to getOrCreateEdge(e).id }
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
        jdbcTemplate.batchUpdateIndexed(sql, content.edgesWithM) { ps, (index, edgeAndM) ->
            val (edge, m) = edgeAndM
            ps.setInt(1, trackVersion.id.intValue)
            ps.setString(2, trackVersion.context.toSqlString())
            ps.setInt(3, trackVersion.version)
            ps.setInt(4, requireNotNull(edges[edge.contentHash]).intValue)
            ps.setInt(5, index)
            ps.setBigDecimal(6, roundTo6Decimals(m.min))
        }
    }

    //    fun copyLocationTrackGeometry(from: LayoutRowVersion<LocationTrack>, to: LayoutRowVersion<LocationTrack>) {
    //        val sql =
    //            """
    //            insert into layout.location_track_version_edge(
    //                location_track_id,
    //                location_track_layout_context_id,
    //                location_track_version,
    //                edge_id,
    //                edge_index,
    //                start_m
    //            ) select
    //                :to_id,
    //                :to_context_id,
    //                :to_version,
    //                edge_id,
    //                edge_index,
    //                start_m
    //                from layout.location_track_version_edge
    //                where location_track_id = :from_id
    //                  and location_track_layout_context_id = :from_context_id
    //                  and location_track_version = :from_version
    //        """
    //                .trimIndent()
    //        val params =
    //            mapOf(
    //                "from_id" to from.id.intValue,
    //                "from_context_id" to from.context.toSqlString(),
    //                "from_version" to from.version,
    //                "to_id" to to.id.intValue,
    //                "to_context_id" to to.context.toSqlString(),
    //                "to_version" to to.version,
    //            )
    //        jdbcTemplate.update(sql, params)
    //    }
    //
    fun preloadLocationTrackGeometries(): Int {
        val geoms = fetchLocationTrackGeometry(trackVersion = null, active = true)
        locationTrackGeometryCache.putAll(geoms)
        return geoms.size
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun fetch(alignmentVersion: RowVersion<LayoutAlignment>): LayoutAlignment =
        if (cacheEnabled) alignmentsCache.get(alignmentVersion, ::fetchInternal) else fetchInternal(alignmentVersion)

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun fetchMany(versions: List<RowVersion<LayoutAlignment>>): Map<RowVersion<LayoutAlignment>, LayoutAlignment> =
        versions.associateWith(::fetch)

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    fun getMany(
        versions: List<LayoutRowVersion<LocationTrack>>
    ): Map<LayoutRowVersion<LocationTrack>, DbLocationTrackGeometry> = versions.associateWith(::get)

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
            a.id alignment_id,
            a.version alignment_version,
            sv.segment_index,
            sv.start,
            sv.geometry_alignment_id,
            sv.geometry_element_index,
            sv.source_start,
            sv.switch_id,
            sv.switch_start_joint_number,
            sv.switch_end_joint_number,
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
                val alignmentData = AlignmentData(version = rs.getRowVersion("alignment_id", "alignment_version"))
                val segmentId = rs.getIndexedIdOrNull<LayoutSegment>("alignment_id", "segment_index")
                val segment =
                    segmentId?.let { sId ->
                        SegmentData(
                            id = sId,
                            start = rs.getDouble("start"),
                            sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                            sourceStart = rs.getDoubleOrNull("source_start"),
                            switchId = rs.getIntIdOrNull("switch_id"),
                            startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                            endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                            source = rs.getEnum("source"),
                            rs.getIntId("geometry_id"),
                        )
                    }
                alignmentData to segment
            }
        val groupedByAlignment = alignmentAndSegment.groupBy({ (a, _) -> a }, { (_, s) -> s })
        val alignments =
            groupedByAlignment.entries
                .parallelStream()
                .map { (alignmentData, segmentDatas) ->
                    alignmentData.version to
                        LayoutAlignment(
                            id = alignmentData.version.id,
                            segments = createSegments(segmentDatas.filterNotNull()),
                        )
                }
                .collect(Collectors.toList())
                .associate { it }
        alignmentsCache.putAll(alignments)
        return alignments.size
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
                "polygon_string" to
                    alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
                "segment_count" to alignment.segments.size,
                "length" to alignment.length,
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
                "polygon_string" to
                    alignment.boundingBox?.let { bbox -> create2DPolygonString(bbox.polygonFromCorners) },
                "segment_count" to alignment.segments.size,
                "length" to alignment.length,
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

    @Transactional
    fun deleteOrphanedAlignments(): List<IntId<LayoutAlignment>> {
        val sql =
            """
           delete
           from layout.alignment alignment
           where not exists(select 1 from layout.reference_line where reference_line.alignment_id = alignment.id)
             and not exists(select 1 from layout.location_track where location_track.alignment_id = alignment.id)
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
              alignment_id,
              segment_index,
              start,
              geometry_alignment_id,
              geometry_element_index,
              source_start,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number,
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

        return createSegments(
            jdbcTemplate.query(sql, params) { rs, _ ->
                SegmentData(
                    id = rs.getIndexedId("alignment_id", "segment_index"),
                    start = rs.getDouble("start"),
                    sourceId = rs.getIndexedIdOrNull("geometry_alignment_id", "geometry_element_index"),
                    sourceStart = rs.getDoubleOrNull("source_start"),
                    switchId = rs.getIntIdOrNull("switch_id"),
                    startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
                    endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
                    source = rs.getEnum("source"),
                    geometryId = rs.getIntId("geometry_id"),
                )
            }
        )
    }

    private fun createSegments(segmentResults: List<SegmentData>): List<LayoutSegment> {
        val geometries = fetchSegmentGeometries(segmentResults.map { s -> s.geometryId }.distinct())

        var start = 0.0
        return segmentResults.map { data ->
            require(abs(start - data.start) < LAYOUT_M_DELTA) {
                "Segment start value does not match the calculated one: stored=${data.start} calc=$start"
            }
            val geometry =
                requireNotNull(geometries[data.geometryId]) {
                    "Fetching geometry failed for segment: id=${data.id} geometryId=$data.geometryId"
                }
            LayoutSegment(
                    //                    id = data.id,
                    sourceId = data.sourceId,
                    sourceStart = data.sourceStart,
                    switchId = data.switchId,
                    startJointNumber = data.startJointNumber,
                    endJointNumber = data.endJointNumber,
                    source = data.source,
                    geometry = geometry,
                    //                    startM = start,
                )
                .also { start += geometry.length }
        }
    }

    // TODO: GVT-2948 fetch metadata from node-edge model
    fun fetchSegmentGeometriesAndPlanMetadata(
        trackVersion: LayoutRowVersion<LocationTrack>,
        metadataExternalId: Oid<*>?,
        boundingBox: BoundingBox?,
    ): List<SegmentGeometryAndMetadata> {
        return listOf()
    }

    @Deprecated("Should be implemented through nodes and edges")
    fun fetchSegmentGeometriesAndPlanMetadata(
        alignmentVersion: RowVersion<LayoutAlignment>,
        metadataExternalId: Oid<*>?,
        boundingBox: BoundingBox?,
    ): List<SegmentGeometryAndMetadata> {
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
                val startPoint =
                    rs.getDoubleOrNull("start_m")?.let { m ->
                        rs.getPointOrNull("start_x", "start_y")?.let { point ->
                            AlignmentPoint(point.x, point.y, 0.0, m, 0.0)
                        }
                    }
                val endPoint =
                    rs.getDoubleOrNull("end_m")?.let { m ->
                        rs.getPointOrNull("end_x", "end_y")?.let { point ->
                            AlignmentPoint(point.x, point.y, 0.0, m, 0.0)
                        }
                    }
                SegmentGeometryAndMetadata(
                    planId = rs.getIntIdOrNull("plan_id"),
                    fileName = rs.getFileNameOrNull("file_name"),
                    alignmentId = rs.getIntIdOrNull("geom_alignment_id"),
                    alignmentName = rs.getString("alignment_name")?.let(::AlignmentName),
                    startPoint = startPoint,
                    endPoint = endPoint,
                    isLinked = rs.getBoolean("is_linked"),
                    id = StringId("${alignmentVersion.id.intValue}_${fromSegment}_${toSegment}"),
                )
            }
        logger.daoAccess(AccessType.UPDATE, SegmentGeometryAndMetadata::class, alignmentVersion)
        return result
    }

    // TODO: GVT-2932 preliminary logic fixed (untested) to topology model, but might require optimization & indexes
    // TODO: GVT-2932 Especially since there is a group of new joins to get the metadata
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

    fun <T> fetchProfileInfoForSegmentsInBoundingBox(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        hasProfileInfo: Boolean? = null,
    ): List<MapSegmentProfileInfo<T>> {
        // language=SQL
        val sql =
            """
            select *
              from (
                select
                  location_track.id,
                  alignment_id,
                  segment_index,
                  start,
                  postgis.st_m(postgis.st_endpoint(segment_geometry.geometry)) as max_m,
                  (plan.vertical_coordinate_system is not null)
                    and exists(
                    select *
                      from geometry.vertical_intersection vi
                      where vi.alignment_id = alignment.id
                  ) as has_profile_info
                  from layout.location_track_in_layout_context(
                      :publication_state::layout.publication_state,
                      :design_id) location_track
                    join layout.segment_version using (alignment_id, alignment_version)
                    join layout.alignment layout_alignment on alignment_id = layout_alignment.id
                    join layout.segment_geometry on segment_version.geometry_id = segment_geometry.id
                    left join geometry.alignment on alignment.id = segment_version.geometry_alignment_id
                    left join geometry.plan on alignment.plan_id = plan.id
                  where postgis.st_intersects(
                      postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                      layout_alignment.bounding_box
                        )
                    and postgis.st_intersects(
                      postgis.st_makeenvelope(:x_min, :y_min, :x_max, :y_max, :layout_srid),
                      segment_geometry.bounding_box
                        )
                    and location_track.state != 'DELETED'
              ) s
              where ((:has_profile_info::boolean is null) or :has_profile_info = has_profile_info)
              order by id, segment_index
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
            val startM = rs.getDouble("start")
            MapSegmentProfileInfo(
                id = rs.getIntId("id"),
                alignmentId = rs.getIndexedId("alignment_id", "segment_index"),
                segmentStartM = startM,
                segmentEndM = startM + rs.getDouble("max_m"),
                hasProfile = rs.getBoolean("has_profile_info"),
            )
        }
    }

    private fun upsertSegments(
        alignmentId: RowVersion<LayoutAlignment>,
        segments: List<Pair<LayoutSegment, Range<Double>>>,
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
                  switch_id,
                  switch_start_joint_number,
                  switch_end_joint_number,
                  source_start,
                  source,
                  geometry_id
                )
                values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::layout.geometry_source, ?) 
              """
                    .trimIndent()
            // This uses indexed parameters (rather than named ones),
            // since named parameter template's batch-method is considerably slower
            jdbcTemplate.batchUpdateIndexed(sqlIndexed, segments) { ps, (index, segmentAndM) ->
                val (s, m) = segmentAndM
                ps.setInt(1, alignmentId.id.intValue)
                ps.setInt(2, alignmentId.version)
                ps.setInt(3, index)
                ps.setDouble(4, m.min)
                ps.setNullableInt(5) { if (s.sourceId is IndexedId) s.sourceId.parentId else null }
                ps.setNullableInt(6) { if (s.sourceId is IndexedId) s.sourceId.index else null }
                ps.setNullableInt(7) { if (s.switchId is IntId) s.switchId.intValue else null }
                ps.setNullableInt(8, s.startJointNumber?.intValue)
                ps.setNullableInt(9, s.endJointNumber?.intValue)
                ps.setNullableDouble(10, s.sourceStart)
                ps.setString(11, s.source.name)
                val geometryId =
                    if (s.geometry.id is IntId) s.geometry.id
                    else
                        requireNotNull(newGeometryIds[s.geometry.id]) {
                            "SegmentGeometry not stored: id=${s.geometry.id}"
                        }
                ps.setInt(12, geometryId.intValue)
            }
        }
    }

    // Batching this is a little tricky due to difficulty in mapping generated ids:
    //  There is no guarantee of result set order (though it's usually the insert order)
    private fun insertSegmentGeometries(
        geometries: List<SegmentGeometry>
    ): Map<StringId<SegmentGeometry>, IntId<SegmentGeometry>> {
        require(geometries.all { geom -> geom.segmentStart.m == 0.0 }) {
            "Geometries in DB must be set to startM=0.0, so they remain valid if an earlier segment changes"
        }
        require(
            geometries.all { geom ->
                val calculatedLength = calculateDistance(geom.segmentPoints, LAYOUT_SRID)
                val maxDelta = calculatedLength * 0.01
                abs(calculatedLength - geom.segmentEnd.m) <= maxDelta
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
            val params = mapOf("ids" to ids.map(IntId<SegmentGeometry>::intValue))
            val rowResults =
                jdbcTemplate.query(sql, params) { rs, _ ->
                    GeometryRowResult(
                        id = rs.getIntId("id"),
                        wktString = rs.getString("geometry_wkt"),
                        heightString = rs.getString("height_values"),
                        cantString = rs.getString("cant_values"),
                        resolution = rs.getInt("resolution"),
                    )
                }
            parseGeometries(rowResults)
        } else mapOf()
    }

    fun preloadSegmentGeometries(): Int {
        val sql =
            """
          select 
            geom.id,
            postgis.st_astext(geom.geometry) as geometry_wkt,
            geom.resolution,
            case 
              when geom.height_values is null then null 
              else array_to_string(geom.height_values, ',', 'null') 
            end as height_values,
            case 
              when geom.cant_values is null then null 
              else array_to_string(geom.cant_values, ',', 'null') 
            end as cant_values
          from layout.alignment a
            left join layout.segment_version sv on a.id = sv.alignment_id and a.version = sv.alignment_version
            left join layout.segment_geometry geom on geom.id = sv.geometry_id
          where geom.id is not null
          group by geom.id
        """
                .trimIndent()

        val rowResults =
            jdbcTemplate.query(sql) { rs, _ ->
                GeometryRowResult(
                    id = rs.getIntId("id"),
                    wktString = rs.getString("geometry_wkt"),
                    heightString = rs.getString("height_values"),
                    cantString = rs.getString("cant_values"),
                    resolution = rs.getInt("resolution"),
                )
            }
        segmentGeometryCache.putAll(parseGeometries(rowResults))
        return rowResults.size
    }
}

data class GeometryRowResult(
    val id: IntId<SegmentGeometry>,
    val wktString: String,
    val cantString: String?,
    val heightString: String?,
    val resolution: Int,
)

private fun parseGeometries(rowResults: List<GeometryRowResult>): Map<IntId<SegmentGeometry>, SegmentGeometry> =
    rowResults
        .parallelStream()
        .map { row ->
            SegmentGeometry(
                id = row.id,
                segmentPoints = parseSegmentPointsWkt(row.wktString, row.heightString, row.cantString),
                resolution = row.resolution,
            )
        }
        .collect(Collectors.toMap({ g -> g.id as IntId }, { it }))

private fun getSegmentPointsWkt(
    rs: ResultSet,
    geometryColumn: String,
    heightColumn: String? = null,
    cantColumn: String? = null,
): List<SegmentPoint> =
    rs.getString(geometryColumn)?.let { wktString ->
        parseSegmentPointsWkt(wktString, heightColumn?.let(rs::getString), cantColumn?.let(rs::getString))
    } ?: emptyList()

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

private data class SegmentData(
    val id: IndexedId<LayoutSegment>,
    val start: Double,
    val sourceId: IndexedId<GeometryElement>?,
    val sourceStart: Double?,
    val switchId: IntId<LayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    val source: GeometrySource,
    val geometryId: IntId<SegmentGeometry>,
)
