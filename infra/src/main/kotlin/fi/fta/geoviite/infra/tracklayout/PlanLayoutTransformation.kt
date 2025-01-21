package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.ToGkFinTransformation
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryCant
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.PlanState
import fi.fta.geoviite.infra.geometry.PlanState.ABANDONED
import fi.fta.geoviite.infra.geometry.PlanState.DESTROYED
import fi.fta.geoviite.infra.geometry.PlanState.EXISTING
import fi.fta.geoviite.infra.geometry.PlanState.PROPOSED
import fi.fta.geoviite.infra.map.GeometryAlignmentHeader
import fi.fta.geoviite.infra.map.MapAlignmentType
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.LayoutState.DELETED
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.tracklayout.LayoutState.NOT_IN_USE
import java.math.BigDecimal
import kotlin.math.max

val REFERENCE_LINE_TYPE_CODE = FeatureTypeCode("111")
const val MIN_POINT_DISTANCE = 0.01

fun toTrackLayout(
    geometryPlan: GeometryPlan,
    trackNumberId: IntId<LayoutTrackNumber>?,
    heightTriangles: List<HeightTriangle>,
    planToLayout: Transformation,
    pointListStepLength: Int,
    includeGeometryData: Boolean,
    planToGkTransformation: ToGkFinTransformation,
): GeometryPlanLayout {
    val switches = toLayoutSwitches(geometryPlan.switches, planToLayout)

    val alignments: List<PlanLayoutAlignment> =
        toMapAlignments(
            trackNumberId,
            geometryPlan.alignments,
            planToLayout,
            pointListStepLength,
            heightTriangles,
            geometryPlan.units.verticalCoordinateSystem,
            includeGeometryData,
        )

    val kmPosts = toLayoutKmPosts(trackNumberId, geometryPlan.kmPosts, planToGkTransformation)
    val startAddress = getPlanStartAddress(geometryPlan.kmPosts)

    return GeometryPlanLayout(
        id = geometryPlan.id,
        planHidden = geometryPlan.isHidden,
        planDataType = geometryPlan.dataType,
        fileName = geometryPlan.fileName,
        alignments = alignments,
        switches = switches.values.toList(),
        kmPosts = kmPosts,
        startAddress = startAddress,
    )
}

fun toLayoutKmPosts(
    trackNumberId: IntId<LayoutTrackNumber>?,
    kmPosts: List<GeometryKmPost>,
    planToGkTransformation: ToGkFinTransformation,
): List<LayoutKmPost> {
    return kmPosts.mapIndexedNotNull { _, kmPost ->
        if (
            kmPost.location != null && kmPost.kmNumber != null && (kmPost.location.x != 0.0 || kmPost.location.y != 0.0)
        ) {
            LayoutKmPost(
                kmNumber = kmPost.kmNumber,
                state = getLayoutStateOrDefault(kmPost.state),
                sourceId = kmPost.id,
                trackNumberId = trackNumberId,
                gkLocation =
                    LayoutKmPostGkLocation(
                        location = planToGkTransformation.transform(kmPost.location),
                        source = KmPostGkLocationSource.FROM_GEOMETRY,
                        confirmed = true,
                    ),
                contextData = LayoutContextData.newDraft(LayoutBranch.main, id = null),
            )
        } else {
            null
        }
    }
}

fun toLayoutSwitch(switch: GeometrySwitch, toMapCoordinate: Transformation): LayoutSwitch? =
    if (switch.switchStructureId == null) null
    else
        LayoutSwitch(
            name = switch.name,
            switchStructureId = switch.switchStructureId,
            stateCategory = getLayoutStateOrDefault(switch.state).category,
            joints =
                switch.joints.map { j ->
                    LayoutSwitchJoint(
                        number = j.number,
                        location = toMapCoordinate.transform(j.location),
                        locationAccuracy = LocationAccuracy.DESIGNED_GEOLOCATION,
                    )
                },
            sourceId = switch.id,
            trapPoint = null,
            ownerId = null,
            source = GeometrySource.PLAN,
            contextData = LayoutContextData.newDraft(LayoutBranch.main, id = null),
            draftOid = null,
        )

fun toLayoutSwitches(
    geometrySwitches: List<GeometrySwitch>,
    planToLayout: Transformation,
): Map<DomainId<GeometrySwitch>, LayoutSwitch> =
    geometrySwitches.mapNotNull { s -> toLayoutSwitch(s, planToLayout)?.let { s.id to it } }.associate { it }

fun toMapAlignments(
    trackNumberId: IntId<LayoutTrackNumber>?,
    geometryAlignments: List<GeometryAlignment>,
    planToLayout: Transformation,
    pointListStepLength: Int,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    includeGeometryData: Boolean = true,
): List<PlanLayoutAlignment> {
    return geometryAlignments.map { alignment ->
        val mapSegments =
            toMapSegments(
                alignment = alignment,
                planToLayoutTransformation = planToLayout,
                pointListStepLength = pointListStepLength,
                heightTriangles = heightTriangles,
                verticalCoordinateSystem = verticalCoordinateSystem,
                includeGeometryData = includeGeometryData,
            )

        val boundingBoxInLayoutSpace =
            alignment.bounds?.let {
                val cornersInLayoutSpace = it.corners.map { corner -> planToLayout.transform(corner) }
                boundingBoxAroundPoints(cornersInLayoutSpace)
            }

        PlanLayoutAlignment(
            header = toAlignmentHeader(trackNumberId, alignment, boundingBoxInLayoutSpace),
            segments = mapSegments,
        )
    }
}

fun toAlignmentHeader(
    trackNumberId: IntId<LayoutTrackNumber>?,
    alignment: GeometryAlignment,
    boundingBoxInLayoutSpace: BoundingBox? = null,
) =
    GeometryAlignmentHeader(
        id = alignment.id,
        name = alignment.name,
        alignmentType = getAlignmentType(alignment.featureTypeCode),
        state = getLayoutStateOrDefault(alignment.state),
        trackNumberId = trackNumberId,
        boundingBox = boundingBoxInLayoutSpace,
        length = alignment.elements.sumOf(GeometryElement::calculatedLength),
        segmentCount = alignment.elements.size,
    )

private fun toMapSegments(
    alignment: GeometryAlignment,
    planToLayoutTransformation: Transformation,
    pointListStepLength: Int,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    includeGeometryData: Boolean = true,
): List<PlanLayoutSegment> {
    val alignmentStationStart = alignment.staStart.toDouble()
    var segmentStartLength = 0.0
    val elements =
        alignment.elements
            .map { element ->
                val startLength = segmentStartLength
                segmentStartLength += element.calculatedLength
                element to startLength
            }
            .filter { (e, _) -> e.calculatedLength >= 0.001 }
    val segments =
        if (!includeGeometryData) listOf()
        else
            elements.map { (element, segmentStartLength) ->
                val segmentPoints =
                    toPointList(element, pointListStepLength).map { p ->
                        toSegmentGeometryPoint(
                            point = planToLayoutTransformation.transform(p),
                            mValue = p.m,
                            profile = alignment.profile,
                            cant = alignment.cant,
                            alignmentStartStation = alignmentStationStart,
                            segmentStart = segmentStartLength,
                            heightTriangles = heightTriangles,
                            verticalCoordinateSystem = verticalCoordinateSystem,
                        )
                    }

                PlanLayoutSegment(
                    id = deriveFromSourceId("AS", element.id),
                    geometry = SegmentGeometry(resolution = pointListStepLength, segmentPoints = segmentPoints),
                    startM = segmentStartLength,
                    sourceId = element.id,
                    sourceStart = 0.0,
                    source = GeometrySource.PLAN,
                    pointCount = segmentPoints.size,
                )
            }

    return segments
}

fun getAlignmentType(typeCode: FeatureTypeCode?): MapAlignmentType =
    when (typeCode) {
        REFERENCE_LINE_TYPE_CODE -> MapAlignmentType.REFERENCE_LINE
        else -> MapAlignmentType.LOCATION_TRACK
    }

fun getLayoutStateOrDefault(planState: PlanState?) = planState?.let { state -> getLayoutState(state) } ?: IN_USE

fun getLayoutState(planState: PlanState): LayoutState =
    when (planState) {
        ABANDONED -> DELETED
        DESTROYED -> DELETED
        EXISTING -> IN_USE
        PROPOSED -> NOT_IN_USE
    }

fun toSegmentGeometryPoint(
    point: Point,
    mValue: Double,
    profile: GeometryProfile?,
    cant: GeometryCant?,
    alignmentStartStation: Double,
    segmentStart: Double,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
): SegmentPoint {
    // Profile station values are alignment m-values calculated from given station-start
    val heightValue =
        verticalCoordinateSystem?.let { vcs ->
            if (vcs == VerticalCoordinateSystem.N43) null
            else
                profile?.getHeightAt(alignmentStartStation + segmentStart + mValue)?.let { value ->
                    transformHeightValue(value, point, heightTriangles, vcs)
                }
        }
    // Cant station values are alignment m-values, calculated from 0 (ignoring alignment
    // station-start)
    val cantValue = cant?.getCantValue(segmentStart + mValue)
    return SegmentPoint(x = point.x, y = point.y, z = heightValue, m = mValue, cant = cantValue)
}

/**
 * Generates the list of points at stepLength interval along the element. Special rules:
 * - Always include the first and last point as-is, rather than calculated.
 *     - This ensures the result alignment is continuous (next segment starts where previous ends), despite less-than
 *       perfect parameter accuracy
 * - Avoid generating a point very close to the end-point, even if total length would have it due to step length
 *     - Inaccuracy in parameters could cause the point to be in any direction -> could create a short zig-zag with the
 *       actual end-point
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
    val last1mPoint = if (length % 1.0 > MIN_POINT_DISTANCE) length.toInt() else max(length.toInt() - 1, 0)
    val midPoints = (0..last1mPoint step stepSize).map(Int::toDouble)
    return midPoints + length
}

fun <T> deriveFromSourceId(prefix: String, source: DomainId<*>?): StringId<T> =
    if (source != null) StringId("${prefix}_$source") else StringId()

private fun getPlanStartAddress(planKmPosts: List<GeometryKmPost>): TrackMeter? {
    val minimumKmNumber = planKmPosts.mapNotNull(GeometryKmPost::kmNumber).minOrNull() ?: return null
    val minimumKmNumberPosts = planKmPosts.filter { kmPost -> kmPost.kmNumber == minimumKmNumber }
    return if (minimumKmNumberPosts.size == 1) {
        val precedingKmPost = minimumKmNumberPosts[0]
        // safety: minimumKmNumberPosts was filtered for equality with a known-not-null kmNumber
        // Negative or zero staInternals are assumed to be the distance of the preceding km post
        // from the plan's
        // reference line's start; for anything else, let's not assume we know what to do with it.
        // Round to 6 decimals to make sure TrackNumber creation doesn't fail due to too many
        // decimals
        if (precedingKmPost.staInternal <= BigDecimal.ZERO)
            TrackMeter(precedingKmPost.kmNumber!!, round(-precedingKmPost.staInternal, 6))
        else null
    } else null
}
