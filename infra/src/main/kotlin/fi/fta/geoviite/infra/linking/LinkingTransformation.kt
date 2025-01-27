package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ILayoutEdge
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutEdgeContent
import fi.fta.geoviite.infra.tracklayout.LayoutNodeEndTrack
import fi.fta.geoviite.infra.tracklayout.LayoutNodeStartTrack
import fi.fta.geoviite.infra.tracklayout.LayoutNodeSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

const val ALIGNMENT_LINKING_SNAP = 0.001

fun cutLayoutGeometry(alignment: LayoutAlignment, mRange: Range<Double>): LayoutAlignment {
    val cutSegments = slice(alignment, mRange, ALIGNMENT_LINKING_SNAP)
    val newSegments = removeSwitches(cutSegments, getSwitchIdsOutside(alignment, mRange))
    return tryCreateLinkedAlignment(alignment, newSegments)
}

fun replaceLayoutGeometry(
    trackId: IntId<LocationTrack>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LocationTrackGeometry =
    tryCreateLinkedTrackGeometry(trackId, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun replaceLayoutGeometry(
    layoutAlignment: LayoutAlignment,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment =
    tryCreateLinkedAlignment(layoutAlignment, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLayoutGeometrySection(
    trackId: IntId<LocationTrack>,
    layoutGeometry: LocationTrackGeometry,
    layoutMRange: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LocationTrackGeometry =
    splice(trackId, layoutGeometry, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

fun linkLayoutGeometrySection(
    layoutAlignment: LayoutAlignment,
    layoutMRange: Range<Double>,
    geometryAlignment: PlanLayoutAlignment,
    geometryMRange: Range<Double>,
): LayoutAlignment = splice(layoutAlignment, layoutMRange, createAlignmentGeometry(geometryAlignment, geometryMRange))

// {
//    val segments =
//        if (layoutMRange.min == layoutMRange.max) {
//            extendSegmentsWithGeometry(layoutAlignment, layoutMRange.min, geometryAlignment, geometryMRange)
//        } else {
//            replaceSegmentsWithGeometry(layoutAlignment, layoutMRange, geometryAlignment, geometryMRange)
//        }
//    return tryCreateLinkedAlignment(layoutAlignment, segments)
// }

// private fun createEdgeSegments(geometryAlignment: PlanLayoutAlignment, mRange: Range<Double>): List<LayoutSegment> =
//    slice(geometryAlignment, mRange, ALIGNMENT_LINKING_SNAP)

private fun createAlignmentGeometry(
    geometryAlignment: PlanLayoutAlignment,
    mRange: Range<Double>,
): List<LayoutSegment> = slice(geometryAlignment, mRange, ALIGNMENT_LINKING_SNAP)

// private fun extendSegmentsWithGeometry(
//    layoutAlignment: LayoutAlignment,
//    layoutM: Double,
//    geometryAlignment: PlanLayoutAlignment,
//    geometryMInterval: Range<Double>,
// ): List<LayoutSegment> {
//    val addedSegments = slice(geometryAlignment, geometryMInterval, ALIGNMENT_LINKING_SNAP)
//    return when (getExtensionDirection(layoutAlignment.length, layoutM)) {
//        ExtensionDirection.START -> {
//            val gap = createLinkingSegment(lastPoint(addedSegments), firstPoint(layoutAlignment.segments))
//            addedSegments + listOfNotNull(gap) + layoutAlignment.segments
//        }
//        ExtensionDirection.END -> {
//            val gap = createLinkingSegment(lastPoint(layoutAlignment.segments), firstPoint(addedSegments))
//            layoutAlignment.segments + listOfNotNull(gap) + addedSegments
//        }
//    }
// }

// private fun createExtensionSegments(
//    layoutLength: Double,
//    geometryAlignment: PlanLayoutAlignment,
//    geometryMInterval: Range<Double>,
// ) {
//    val addedSegments = slice(geometryAlignment, geometryMInterval, ALIGNMENT_LINKING_SNAP)
//    return if (isSame(0.0, layoutM, ALIGNMENT_LINKING_SNAP)) {
//        val gap = createLinkingSegment(lastPoint(addedSegments), firstPoint(layoutAlignment.segments))
//        addedSegments + listOfNotNull(gap) + layoutAlignment.segments
//    } else if (isSame(layoutAlignment.length, layoutM, ALIGNMENT_LINKING_SNAP)) {
//        val gap = createLinkingSegment(lastPoint(layoutAlignment.segments), firstPoint(addedSegments))
//        layoutAlignment.segments + listOfNotNull(gap) + addedSegments
//    } else {
//        throw LinkingFailureException("Alignment cannot be extended with selected m-values")
//    }
// }

// private fun replaceSegmentsWithGeometry(
//    layoutAlignment: LayoutAlignment,
//    layoutMInterval: Range<Double>,
//    geometryAlignment: PlanLayoutAlignment,
//    geometryMInterval: Range<Double>,
// ): List<LayoutSegment> {
//    val startSegments = slice(layoutAlignment, Range(0.0, layoutMInterval.min))
//    val geometrySegments = createAlignmentGeometry(geometryAlignment, geometryMInterval)
//    val endSegments = slice(layoutAlignment, Range(layoutMInterval.max, layoutAlignment.length))
//
//    val startGap = createLinkingSegment(lastPoint(startSegments), firstPoint(geometrySegments))
//    val endGap = createLinkingSegment(lastPoint(geometrySegments), firstPoint(endSegments))
//
//    val combinedSegments =
//        (startSegments + listOfNotNull(startGap) + geometrySegments + listOfNotNull(endGap) + endSegments)
//    val affectedSwitchIds = getSwitchIdsInside(layoutAlignment, layoutMInterval)
//    return removeSwitches(combinedSegments, affectedSwitchIds)
// }

private fun createLinkingSegment(start: IPoint?, end: IPoint?, tolerance: Double = LAYOUT_M_DELTA): LayoutSegment? {
    if (start == null || end == null) return null
    val length = calculateDistance(LAYOUT_SRID, start, end)
    return if (length > tolerance)
        LayoutSegment(
            geometry =
                SegmentGeometry(
                    resolution = max(length.toInt(), 1),
                    segmentPoints =
                        listOf(
                            SegmentPoint(x = start.x, y = start.y, z = null, m = 0.0, cant = null),
                            SegmentPoint(x = end.x, y = end.y, z = null, m = length, cant = null),
                        ),
                ),
            sourceId = null,
            sourceStart = null,
            switchId = null,
            startJointNumber = null,
            endJointNumber = null,
            source = GeometrySource.GENERATED,
        )
    else null
}

// private fun toLayoutEdgeSegment(segment: ISegment): LayoutEdgeSegment =
//    if (segment is LayoutEdgeSegment) segment
//    else
//        LayoutEdgeSegment(
//            geometry = segment.geometry,
//            sourceId = segment.sourceId as? IndexedId,
//            sourceStart = segment.sourceStart,
//            source = segment.source,
//        )
//
private fun toLayoutSegment(segment: ISegment): LayoutSegment =
    if (segment is LayoutSegment) segment
    else
        LayoutSegment(
            geometry = segment.geometry,
            sourceId = segment.sourceId as? IndexedId,
            sourceStart = segment.sourceStart,
            switchId = null,
            startJointNumber = null,
            endJointNumber = null,
            source = segment.source,
        )

private fun tryCreateLinkedTrackGeometry(
    trackId: IntId<LocationTrack>,
    newSegments: List<LayoutSegment>,
): LocationTrackGeometry =
    try {
        TmpLocationTrackGeometry(
            listOf(
                LayoutEdgeContent(
                    startNode = LayoutNodeStartTrack(trackId),
                    endNode = LayoutNodeEndTrack(trackId),
                    segments = newSegments,
                )
            )
        )
    } catch (e: IllegalArgumentException) {
        throw LinkingFailureException(
            message = "Linking selection produces invalid location track geometry",
            cause = e,
            localizedMessageKey = "alignment-geometry",
        )
    }

private fun tryCreateLinkedAlignment(original: LayoutAlignment, newSegments: List<LayoutSegment>): LayoutAlignment =
    try {
        original.withSegments(newSegments.also(::validateSegments))
    } catch (e: IllegalArgumentException) {
        throw LinkingFailureException(
            message = "Linking selection produces invalid alignment",
            cause = e,
            localizedMessageKey = "alignment-geometry",
        )
    }

private fun validateSegments(newSegments: List<LayoutSegment>) =
    newSegments.forEachIndexed { index, segment ->
        newSegments.getOrNull(index - 1)?.let { previous ->
            val diff = angleDiffRads(previous.endDirection, segment.startDirection)
            if (diff > PI / 2)
                throw LinkingFailureException(
                    message =
                        "Linked geometry has over 90 degree angles between segments: " +
                            "segments=${index-1}-$index prevEnd=${previous.endDirection} next=${segment.endDirection} angle=${radsToDegrees(diff)} point=${segment.segmentStart}",
                    localizedMessageKey = "segments-sharp-angle",
                )
        }
    }

fun splice(
    alignment: LayoutAlignment,
    mRange: Range<Double>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): LayoutAlignment {
    val start = slice(alignment, Range(0.0, mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(start, added))
    val end = slice(alignment, Range(mRange.max, alignment.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, end))
    return tryCreateLinkedAlignment(alignment, start + startGap + added + endGap + end)
}

fun splice(
    trackId: IntId<LocationTrack>,
    geometry: LocationTrackGeometry,
    mRange: Range<Double>,
    added: List<LayoutSegment>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): LocationTrackGeometry {
    val startEdges = slice(trackId, geometry, Range(0.0, mRange.min), snapDistance)
    val startGap = listOfNotNull(createGapIfNeeded(startEdges.lastOrNull()?.segments ?: listOf(), added))
    val endEdges = slice(trackId, geometry, Range(mRange.max, geometry.length), snapDistance)
    val endGap = listOfNotNull(createGapIfNeeded(added, endEdges.firstOrNull()?.segments ?: listOf()))
    val midEdge =
        LayoutEdgeContent(
            startNode = LayoutNodeStartTrack(trackId),
            endNode = LayoutNodeEndTrack(trackId),
            segments = startGap + added + endGap,
        )
    // TODO: GVT-2915 validations, like on LayoutAlignment?
    return TmpLocationTrackGeometry(combineEdges(startEdges + midEdge + endEdges))
}

/**
 * Combine edges from different sources into a single geometry:
 * - Between edges, both edges are linked to the same switch, if any
 * - Edges without a switch between them are combined into a single edge
 * - If edges point to having different switches between them, an exception is thrown
 */
private fun combineEdges(edges: List<ILayoutEdge>): List<ILayoutEdge> {
    if (edges.isEmpty()) return edges
    val combined = mutableListOf<ILayoutEdge>()
    var previous: ILayoutEdge? = null
    for (next in edges) {
        if (previous == null) previous = next
        // Both edges agree on the node between them -> move on
        else if (previous.endNode is LayoutNodeSwitch && next.startNode is LayoutNodeSwitch) {
            if (next.startNode.contentHash == previous.endNode.contentHash) {
                combined.add(previous)
                previous = next
            } else {
                throw LinkingFailureException(
                    "Cannot link edges with different switches: ${previous.endNode} -> ${next.startNode}"
                )
            }
        }
        // Previous has a switch node -> mark the next one to start from that node as well
        else if (previous.endNode is LayoutNodeSwitch) {
            combined.add(previous)
            previous = next.withStartNode(previous.endNode)
        }
        // Next has a switch node -> mark the previous one to end at that node as well
        else if (next.startNode is LayoutNodeSwitch) {
            combined.add(previous.withEndNode(next.startNode))
            previous = next
        }
        // Neither has a switch node -> combine them into a single edge
        else {
            previous =
                LayoutEdgeContent(
                    startNode = previous.startNode,
                    endNode = next.endNode,
                    segments = previous.segments + next.segments,
                )
        }
    }
    previous?.let(combined::add)
    return combined.toList()
}

fun slice(
    alignment: IAlignment,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> = slice(alignment.segmentsWithM, mRange, snapDistance)

fun slice(
    segmentsWithM: List<Pair<ISegment, Range<Double>>>,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<LayoutSegment> =
    segmentsWithM.mapNotNull { (segment, segmentM) ->
        if (segmentM.max - snapDistance <= mRange.min || segmentM.min + snapDistance >= mRange.max) {
            null
        } else if (segmentM.min >= mRange.min - snapDistance && segmentM.max <= mRange.max + snapDistance) {
            toLayoutSegment(segment)
        } else {
            val range = Range(max(mRange.min - segmentM.min, 0.0), min(mRange.max - segmentM.min, segment.length))
            toLayoutSegment(segment).slice(segmentMRange = range, snapDistance = snapDistance)
        }
    }

fun slice(
    trackId: IntId<LocationTrack>,
    geometry: LocationTrackGeometry,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): List<ILayoutEdge> {
    return geometry.edgesWithM.mapNotNull { (e, m) ->
        if (m.max - snapDistance <= mRange.min || m.min + snapDistance >= mRange.max) {
            null
        } else if (m.min >= mRange.min - snapDistance && m.max <= mRange.max + snapDistance) {
            e
        } else {
            slice(trackId, e, Range(mRange.min - m.min, mRange.max - m.min), snapDistance)
        }
    }
}

fun slice(
    trackId: IntId<LocationTrack>,
    edge: ILayoutEdge,
    mRange: Range<Double>,
    snapDistance: Double = ALIGNMENT_LINKING_SNAP,
): ILayoutEdge =
    LayoutEdgeContent(
        startNode = edge.startNode.takeIf { mRange.min - snapDistance <= 0.0 } ?: LayoutNodeStartTrack(trackId),
        endNode = edge.endNode.takeIf { mRange.max + snapDistance >= edge.length } ?: LayoutNodeEndTrack(trackId),
        segments = slice(edge.segmentsWithM, mRange, snapDistance),
    )

fun createGapIfNeeded(preceding: List<LayoutSegment>, following: List<LayoutSegment>): LayoutSegment? {
    val start = lastPoint(preceding)
    val end = firstPoint(following)
    return if (start != null && end != null) createLinkingSegment(start, end) else null
}

private fun firstPoint(segments: List<LayoutSegment>) = segments.firstOrNull()?.segmentStart

private fun lastPoint(segments: List<LayoutSegment>) = segments.lastOrNull()?.segmentEnd

fun removeSwitches(segments: List<LayoutSegment>, switchIds: Set<IntId<LayoutSwitch>>): List<LayoutSegment> =
    segments.map { s -> if (switchIds.contains(s.switchId)) s.withoutSwitch() else s }

@Deprecated("In layout graph model, this is no-longer needed")
fun getSwitchIdsInside(alignment: LayoutAlignment, mRange: Range<Double>) =
    getSwitchIds(alignment) { m -> mRange.min < m.max && mRange.max > m.min }

@Deprecated("In layout graph model, this is no-longer needed")
fun getSwitchIdsOutside(alignment: LayoutAlignment, mRange: Range<Double>) =
    getSwitchIds(alignment) { m -> mRange.min > m.min || mRange.max < m.max }

@Deprecated("In layout graph model, this is no-longer needed")
private fun getSwitchIds(alignment: LayoutAlignment, predicate: (Range<Double>) -> Boolean) =
    alignment.segmentsWithM.mapNotNull { (s, m) -> if (predicate(m)) s.switchId else null }.toSet()

// enum class ExtensionDirection {
//    START,
//    END,
// }
//
// private fun getExtensionDirection(layoutLength: Double, layoutM: Double): ExtensionDirection =
//    when {
//        isSame(0.0, layoutM, ALIGNMENT_LINKING_SNAP) -> ExtensionDirection.START
//        isSame(layoutLength, layoutM, ALIGNMENT_LINKING_SNAP) -> ExtensionDirection.END
//        else -> throw LinkingFailureException("Alignment cannot be extended with selected m-values")
//    }
