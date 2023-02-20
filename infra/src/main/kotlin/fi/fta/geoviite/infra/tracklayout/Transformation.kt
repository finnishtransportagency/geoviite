package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.geometry.PlanState.*
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import kotlin.math.max

val REFERENCE_LINE_TYPE_CODE = FeatureTypeCode("111")
const val MIN_POINT_DISTANCE = 0.01

fun toTrackLayout(
    geometryPlan: GeometryPlan,
    heightTriangles: List<HeightTriangle>,
    planToLayout: Transformation,
    pointListStepLength: Int,
    includeGeometryData: Boolean,
): GeometryPlanLayout {
    val switches = toTrackLayoutSwitches(geometryPlan.switches, planToLayout)

    val alignments: List<MapAlignment<GeometryAlignment>> = toTrackLayoutAlignments(
        geometryPlan.alignments,
//        switches.mapValues { s -> s.value.id },
        planToLayout,
        pointListStepLength,
        heightTriangles,
        geometryPlan.units.verticalCoordinateSystem,
        includeGeometryData
    )

    val kmPosts = toTrackLayoutKmPosts(geometryPlan.kmPosts, planToLayout)

    return GeometryPlanLayout(
        planId = geometryPlan.id,
        planDataType = geometryPlan.dataType,
        fileName = geometryPlan.fileName,
        alignments = alignments,
        switches = switches.values.toList(),
        kmPosts = kmPosts
    )
}


fun toTrackLayoutKmPosts(
    kmPosts: List<GeometryKmPost>,
    planToLayout: Transformation,
): List<TrackLayoutKmPost> {

    return kmPosts.mapNotNull { kmPost ->
        if (kmPost.location != null && kmPost.kmNumber != null) {
            TrackLayoutKmPost(
                kmNumber = kmPost.kmNumber,
                location = planToLayout.transform(kmPost.location),
                state = getLayoutStateOrDefault(kmPost.state),
                sourceId = kmPost.id,
                trackNumberId = kmPost.trackNumberId,
            )
        } else null
    }
}

fun toTrackLayoutSwitch(switch: GeometrySwitch, toMapCoordinate: Transformation): TrackLayoutSwitch? =
    if (switch.switchStructureId == null) null
    else TrackLayoutSwitch(
        name = switch.name,
        switchStructureId = switch.switchStructureId,
        stateCategory = getLayoutStateOrDefault(switch.state).category,
        joints = switch.joints.map { j ->
            TrackLayoutSwitchJoint(
                number = j.number,
                location = toMapCoordinate.transform(j.location),
                locationAccuracy = LocationAccuracy.DESIGNED_GEOLOCATION,
            )
        },
        sourceId = switch.id,
        externalId = null,
        trapPoint = null,
        ownerId = null,
        source = GeometrySource.PLAN,
    )

fun toTrackLayoutSwitches(
    geometrySwitches: List<GeometrySwitch>,
    planToLayout: Transformation,
): Map<DomainId<GeometrySwitch>, TrackLayoutSwitch> =
    geometrySwitches
        .mapNotNull { s -> toTrackLayoutSwitch(s, planToLayout)?.let { s.id to it } }
        .associate { it }

fun toTrackLayoutAlignments(
    geometryAlignments: List<GeometryAlignment>,
//    switchIds: Map<DomainId<GeometrySwitch>, DomainId<TrackLayoutSwitch>>,
    planToLayout: Transformation,
    pointListStepLength: Int,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    includeGeometryData: Boolean = true,
): List<MapAlignment<GeometryAlignment>> {
    return geometryAlignments
        .map { alignment ->
            val mapSegments = toMapSegments(
                alignment = alignment,
//                switchIds = switchIds,
                planToLayoutTransformation = planToLayout,
                pointListStepLength = pointListStepLength,
                heightTriangles = heightTriangles,
                verticalCoordinateSystem = verticalCoordinateSystem,
                includeGeometryData = includeGeometryData,
            )

            val state = getLayoutStateOrDefault(alignment.state)
            val boundingBoxInLayoutSpace = alignment.bounds?.let {
                val cornersInLayoutSpace = it.corners.map { corner -> planToLayout.transform(corner) }
                boundingBoxAroundPoints(cornersInLayoutSpace)
            }

            MapAlignment(
                name = alignment.name,
                description = null,
                alignmentSource = MapAlignmentSource.GEOMETRY,
                alignmentType = getAlignmentType(alignment.featureTypeCode),
                type = null,
                state = state,
                segments = mapSegments,
                trackNumberId = alignment.trackNumberId,
                sourceId = alignment.id,
                id = alignment.id,
                boundingBox = boundingBoxInLayoutSpace,
                length = alignment.elements.sumOf(GeometryElement::calculatedLength),
                dataType = DataType.TEMP,
                segmentCount = alignment.elements.size,
                version = null,
            )
        }
}

private fun toMapSegments(
    alignment: GeometryAlignment,
//    switchIds: Map<DomainId<GeometrySwitch>, DomainId<TrackLayoutSwitch>>,
    planToLayoutTransformation: Transformation,
    pointListStepLength: Int,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    includeGeometryData: Boolean = true,
): List<MapSegment> {
    val alignmentStationStart = alignment.staStart.toDouble()
    var segmentStartLength = 0.0
    val elements = alignment.elements
        .map { element ->
            val startLength = segmentStartLength
            segmentStartLength += element.calculatedLength
            element to startLength
        }
        .filter { (e, _) -> e.calculatedLength >= 0.001 }
    val segments =
        if (!includeGeometryData) listOf()
        else elements.map { (element, segmentStartLength) ->
            val segmentPoints = toPointList(element, pointListStepLength).map { p ->
                toTrackLayoutPoint(
                    planToLayoutTransformation.transform(p),
                    p.m,
                    alignment.profile,
                    alignment.cant,
                    alignmentStationStart,
                    segmentStartLength,
                    heightTriangles,
                    verticalCoordinateSystem
                )
            }

            MapSegment(
                id = deriveFromSourceId("AS", element.id),
                points = segmentPoints,
                sourceId = element.id,
                sourceStart = 0.0,
                resolution = pointListStepLength,
//                switchId = switchIds[element.switchId],
//                startJointNumber = element.startJointNumber,
//                endJointNumber = element.endJointNumber,
                start = segmentStartLength,
                source = GeometrySource.PLAN,
                length = segmentPoints.last().m,
                pointCount = segmentPoints.size,
                boundingBox = boundingBoxAroundPointsOrNull(segmentPoints),
            )
        }

    return segments
}

fun getAlignmentType(typeCode: FeatureTypeCode?): MapAlignmentType = when (typeCode) {
    REFERENCE_LINE_TYPE_CODE -> MapAlignmentType.REFERENCE_LINE
    else -> MapAlignmentType.LOCATION_TRACK
}

fun filter(boundingBox: BoundingBox, segments: List<LayoutSegment>): List<LayoutSegment> =
    segments.filter { s -> s.points.any { p -> boundingBox.x.contains(p.x) && boundingBox.y.contains(p.y) } }

fun getLayoutStateOrDefault(planState: PlanState?) = planState?.let { state -> getLayoutState(state) } ?: PLANNED
fun getLayoutState(planState: PlanState): LayoutState = when (planState) {
    ABANDONED -> DELETED
    DESTROYED -> DELETED
    EXISTING -> IN_USE
    PROPOSED -> NOT_IN_USE
}

fun toTrackLayoutPoint(
    point: Point,
    mValue: Double,
    profile: GeometryProfile?,
    cant: GeometryCant?,
    alignmentStartStation: Double,
    segmentStart: Double,
    triangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
): LayoutPoint {
    // Profile station values are alignment m-values calculated from given station-start
    val heightValue = verticalCoordinateSystem?.let { vcs ->
        if (vcs == VerticalCoordinateSystem.N43) null
        else profile?.getHeightAt(alignmentStartStation + segmentStart + mValue)
            ?.let { value -> transformHeightValue(value, point, triangles, vcs) }
    }
    // Cant station values are alignment m-values, calculated from 0 (ignoring alignment station-start)
    val cantValue = cant?.getCantValue(segmentStart + mValue)
    return LayoutPoint(
        x = point.x,
        y = point.y,
        z = heightValue,
        m = mValue,
        cant = cantValue,
    )
}

/**
 * Generates the list of points at stepLength interval along the element. Special rules:
 * - Always include the first and last point as-is, rather than calculated.
 *   - This ensures the result alignment is continuous (next segment starts where previous ends), despite less-than perfect parameter accuracy
 * - Avoid generating a point very close to the end-point, even if total length would have it due to step length
 *   - Inaccuracy in parameters could cause the point to be in any direction -> could create a short zig-zag with the actual end-point
 */
fun toPointList(element: GeometryElement, stepLength: Int): List<Point3DM> {
    return lengthPoints(element.calculatedLength, stepLength).map { length ->
        val point =
            if (length <= 0.0) element.start
            else if (length > element.calculatedLength - MIN_POINT_DISTANCE) element.end
            else element.getCoordinateAt(length)
        Point3DM(point.x, point.y, length)
    }
}

fun lengthPoints(length: Double, stepSize: Int): List<Double> {
    val last1mPoint =
        if (length % 1.0 > MIN_POINT_DISTANCE) length.toInt()
        else max(length.toInt() - 1, 0)
    val midPoints = (0..last1mPoint step stepSize).map(Int::toDouble)
    return midPoints + length
}

fun <T> deriveFromSourceId(prefix: String, source: DomainId<*>?): StringId<T> =
    if (source != null) StringId("${prefix}_$source") else StringId()
