package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import java.math.BigDecimal
import kotlin.math.abs

data class GeometryChangeRanges(val added: List<Range<Double>>, val removed: List<Range<Double>>)

data class GeometryChangeSummary(
    val changedLengthM: Double,
    val maxDistance: Double,
    val startAddress: TrackMeter,
    val endAddress: TrackMeter,
)

fun lengthDifference(len1: Double, len2: Double) = abs(abs(len1) - abs(len2))

fun lengthDifference(len1: BigDecimal, len2: BigDecimal) = abs(abs(len1.toDouble()) - abs(len2.toDouble()))

fun pointsAreSame(point1: IPoint?, point2: IPoint?) =
    point1 == point2 || point1 != null && point2 != null && point1.isSame(point2, DISTANCE_CHANGE_THRESHOLD)

fun getChangedGeometryRanges(newSegments: List<LayoutSegment>, oldSegments: List<LayoutSegment>): GeometryChangeRanges {
    // TODO If some kind of segment filtering remains after GVT-2967, segments should be grouped for
    // better performance
    val added =
        newSegments
            .filter { s -> oldSegments.none { s2 -> s.geometry.id == s2.geometry.id } }
            .map { s -> Range(s.startM, s.endM) }
    val removed =
        oldSegments
            .filter { s -> newSegments.none { s2 -> s.geometry.id == s2.geometry.id } }
            .map { s -> Range(s.startM, s.endM) }

    return GeometryChangeRanges(added = combineOverlappingRanges(added), removed = combineOverlappingRanges(removed))
}

fun <T : Comparable<T>> combineOverlappingRanges(ranges: List<Range<T>>) =
    ranges.fold(emptyList<Range<T>>()) { list, r ->
        val previous = list.lastOrNull()
        if (previous?.overlaps(r) == true) {
            list.take(list.size - 1) + previous.copy(max = r.max)
        } else {
            list + r
        }
    }

fun findJointPoint(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
): SegmentPoint? {
    val asTopoSwitch = TopologyLocationTrackSwitch(switchId, jointNumber)
    return if (locationTrack.topologyStartSwitch == asTopoSwitch) alignment.firstSegmentStart
    else if (locationTrack.topologyEndSwitch == asTopoSwitch) alignment.lastSegmentEnd
    else {
        val segment =
            alignment.segments.find { segment ->
                segment.switchId == switchId &&
                    (segment.startJointNumber == jointNumber || segment.endJointNumber == jointNumber)
            }
        if (segment == null) null
        else {
            if (segment.startJointNumber == jointNumber) segment.segmentStart else segment.segmentEnd
        }
    }
}

fun groupChangedKmNumbers(kmNumbers: List<KmNumber>) =
    kmNumbers
        .sorted()
        .fold(mutableListOf<List<KmNumber>>()) { acc, kmNumber ->
            if (acc.isEmpty()) acc.add(listOf(kmNumber))
            else {
                val previousKmNumbers = acc.last()
                val previousKmNumber = previousKmNumbers.last().number

                if (kmNumber.number == previousKmNumber || kmNumber.number == previousKmNumber + 1) {
                    acc[acc.lastIndex] = listOf(previousKmNumbers.first(), kmNumber)
                } else acc.add(listOf(kmNumber))
            }
            acc
        }
        .map { Range(it.first(), it.last()) }
