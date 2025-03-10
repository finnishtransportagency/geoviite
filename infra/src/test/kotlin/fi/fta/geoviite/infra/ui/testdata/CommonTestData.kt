package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.GeometrySwitchJoint
import fi.fta.geoviite.infra.geometry.GeometrySwitchTypeName
import fi.fta.geoviite.infra.geometry.GeometryUnits
import fi.fta.geoviite.infra.geometry.PlanState
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.SwitchData
import fi.fta.geoviite.infra.geometry.geometryLine
import fi.fta.geoviite.infra.getSomeNullableValue
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureElement
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchOwnerVayla
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.logger
import java.math.BigDecimal

fun createGeometryKmPost(location: Point?, kmNumber: String, staInternal: BigDecimal = BigDecimal("-148.729000")) =
    GeometryKmPost(
        staBack = null,
        staAhead = BigDecimal("-148.729000"),
        staInternal = staInternal,
        kmNumber = KmNumber(kmNumber),
        description = PlanElementName("0"),
        state = PlanState.PROPOSED,
        location = location,
    )

fun createGeometryAlignment(
    alignmentName: String,
    basePoint: Point,
    incrementPoints: List<Point>,
    switchData: List<SwitchData?> = emptyList(),
): GeometryAlignment {
    val points = pointsFromIncrementList(basePoint, incrementPoints)

    return createGeometryAlignment(alignmentName = alignmentName, locationPoints = points, switchData = switchData)
}

fun createGeometryAlignment(
    alignmentName: String,
    elementNamePrefix: String = "elm",
    locationPoints: List<Point>,
    switchData: List<SwitchData?> = emptyList(),
): GeometryAlignment {
    val staStart = BigDecimal("543.333470")
    val elements =
        locationPoints.dropLast(1).mapIndexed { index, pointA ->
            val elementSwitchData: SwitchData? = switchData.getOrNull(index)
            val pointB = locationPoints[index + 1]
            val length = calculateDistance(listOf(pointA, pointB), LAYOUT_SRID).toBigDecimal()
            geometryLine("$elementNamePrefix-$index", "$index", pointA, pointB, staStart, length, elementSwitchData)
        }

    return GeometryAlignment(
        name = AlignmentName(alignmentName),
        description = FreeText("$alignmentName description"),
        oidPart = null,
        state = PlanState.PROPOSED,
        featureTypeCode = FeatureTypeCode("111"),
        staStart = BigDecimal("0.000000"),
        elements = elements,
        profile = null,
    )
}

fun locationTrack(
    name: String,
    trackNumber: IntId<LayoutTrackNumber>,
    layoutAlignmentType: LocationTrackType = LocationTrackType.MAIN,
    basePoint: Point,
    incrementPoints: List<Point>,
    description: String = "$name location track description",
    draft: Boolean = false,
): Pair<LocationTrack, LayoutAlignment> {
    val alignment = alignmentFromPointIncrementList(basePoint, incrementPoints)
    val track =
        locationTrack(
            trackNumberId = trackNumber,
            alignment = alignment,
            name = "lt-$name",
            description = description,
            type = layoutAlignmentType,
            state = LocationTrackState.IN_USE,
            draft = draft,
        )
    return track to alignment
}

fun referenceLine(
    trackNumber: IntId<LayoutTrackNumber>,
    basePoint: Point,
    incrementPoints: List<Point>,
    draft: Boolean,
): Pair<ReferenceLine, LayoutAlignment> {
    val alignment = alignmentFromPointIncrementList(basePoint, incrementPoints)
    val line = referenceLine(trackNumberId = trackNumber, alignment = alignment, draft = draft)
    return line to alignment
}

private fun alignmentFromPointIncrementList(basePoint: Point, incrementPoints: List<Point>): LayoutAlignment {
    val points = pointsFromIncrementList(basePoint, incrementPoints)

    var startM = 0.0
    val segments =
        points.dropLast(1).mapIndexed { index, pointA ->
            val pointB = points[index + 1]
            segment(points = toSegmentPoints(pointA, pointB), startM = startM).also { s -> startM += s.length }
        }
    return alignment(segments)
}

fun layoutSwitch(name: String, jointPoints: List<Point>, switchStructure: SwitchStructure) =
    LayoutSwitch(
        sourceId = null,
        name = SwitchName(name),
        stateCategory = LayoutStateCategory.EXISTING,
        switchStructureId = switchStructure.id,
        joints = jointPoints.map { point -> switchJoint(point) },
        trapPoint = false,
        ownerId = switchOwnerVayla().id,
        source = GeometrySource.GENERATED,
        contextData = LayoutContextData.newOfficial(LayoutBranch.main),
        draftOid = null,
    )

fun switchJoint(location: Point) =
    LayoutSwitchJoint(
        number = JointNumber(1),
        role = SwitchJointRole.MAIN,
        location = location,
        locationAccuracy = getSomeNullableValue<LocationAccuracy>(1),
    )

fun tmi35GeometryUnit() =
    GeometryUnits(
        coordinateSystemSrid = LAYOUT_SRID,
        coordinateSystemName = null,
        verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
        directionUnit = AngularUnit.GRADS,
        linearUnit = LinearUnit.METER,
    )

fun createProject(name: String) = Project(name = ProjectName(name), description = FreeText("E2E test project"))

fun pointsFromIncrementList(basePoint: Point, incrementPoints: List<Point>) =
    incrementPoints.scan(basePoint) { prevPoint, pointIncr -> prevPoint + pointIncr }

fun locationTrackAndAlignmentForGeometryAlignment(
    trackNumberId: IntId<LayoutTrackNumber>,
    geometryAlignment: GeometryAlignment,
    transformation: Transformation,
    draft: Boolean,
): Pair<LocationTrack, LayoutAlignment> {
    var startM = 0.0
    val segments =
        geometryAlignment.elements.map { element ->
            val start = transformation.transform(element.start)
            val end = transformation.transform(element.end)
            LayoutSegment(
                    geometry =
                        SegmentGeometry(
                            segmentPoints =
                                listOf(
                                    SegmentPoint(start.x, start.y, 0.0, 0.0, 0.0),
                                    SegmentPoint(end.x, end.y, 0.0, element.calculatedLength, 0.0),
                                ),
                            resolution = 100,
                        ),
                    startM = startM,
                    startJointNumber = null,
                    endJointNumber = null,
                    source = GeometrySource.PLAN,
                    sourceId = element.id as IndexedId,
                    sourceStart = null,
                    switchId = null,
                )
                .also { startM += it.length }
        }
    return locationTrackAndAlignment(trackNumberId, segments, draft = draft)
}

fun createSwitchAndAlignments(
    switchName: String,
    switchStructure: SwitchStructure,
    switchAngle: Double,
    switchOrig: Point,
): Pair<GeometrySwitch, List<GeometryAlignment>> {
    val jointNumbers = switchStructure.joints.map { switchJoint -> switchJoint.number }
    logger.info("Switch structure id ${switchStructure.id}")
    val geometrySwitch =
        GeometrySwitch(
            name = SwitchName(switchName),
            switchStructureId = switchStructure.id,
            typeName = GeometrySwitchTypeName(switchStructure.type.typeName),
            state = PlanState.EXISTING,
            joints =
                jointNumbers.map { jointNumber ->
                    GeometrySwitchJoint(
                        jointNumber,
                        getTransformedPoint(switchStructure, switchOrig, switchAngle, jointNumber),
                    )
                },
        )

    val geometryAlignments =
        switchStructureToGeometryAlignment(switchName, switchStructure, switchAngle, switchOrig, geometrySwitch)
    return Pair(geometrySwitch, geometryAlignments)
}

fun switchStructureToGeometryAlignment(
    switchName: String,
    switchStructure: SwitchStructure,
    switchAngle: Double,
    switchOrig: Point,
    geometrySwitch: GeometrySwitch,
): List<GeometryAlignment> {
    return switchStructure.alignments.mapIndexed { index, switchAlignment ->
        GeometryAlignment(
            name = AlignmentName("$index-$switchName"),
            description = FreeText("$index switch alignment"),
            oidPart = null,
            state = PlanState.PROPOSED,
            featureTypeCode = FeatureTypeCode("111"),
            staStart = BigDecimal("0.000000"),
            elements =
                alignmentElementsFromSwitchAlignment(
                    switchAlignment,
                    switchAngle,
                    switchOrig,
                    geometrySwitch.id,
                    switchStructure.joints,
                ),
            profile = null,
        )
    }
}

private fun alignmentElementsFromSwitchAlignment(
    switchAlignment: SwitchStructureAlignment,
    switchAngle: Double,
    switchOrig: Point,
    switchId: DomainId<GeometrySwitch>,
    switchJoints: Set<SwitchStructureJoint>,
): List<GeometryElement> {
    val switchJointsByNumber = switchJoints.associateBy { it.number }
    val switchJointData =
        switchAlignment.jointNumbers.dropLast(1).mapIndexed { index, startJointNumber ->
            val endJointNumber = switchAlignment.jointNumbers[index + 1]
            val startSwitchJoint = switchJointsByNumber[startJointNumber]!!
            val endSwitchJoint = switchJointsByNumber[endJointNumber]!!
            SwitchJointData(switchId, startSwitchJoint, endSwitchJoint)
        }

    return switchAlignment.elements.mapIndexed { index, switchElement ->
        geometryLine(
            name = "element-$index",
            oidPart = "$index",
            start = rotateAroundOrigin(switchAngle, switchElement.start) + switchOrig,
            end = rotateAroundOrigin(switchAngle, switchElement.end) + switchOrig,
            length = calculateDistance(listOf(switchElement.start, switchElement.end), LAYOUT_SRID).toBigDecimal(),
            staStart = BigDecimal("543.333470"),
            elementSwitchData = matchSwitchDataToElement(switchElement, switchJointData, switchId),
        )
    }
}

private fun matchSwitchDataToElement(
    switchElement: SwitchStructureElement,
    switchJointData: List<SwitchJointData>,
    switchId: DomainId<GeometrySwitch>,
): SwitchData {
    try {
        return switchJointData
            .first { data -> data.isInsideSwitchJoint(switchElement) }
            .let { SwitchData(switchId, it.startSwitchJoint.number, it.endSwitchJoint.number) }
    } catch (ex: java.util.NoSuchElementException) {
        throw RuntimeException("Could not match switch element (${switchElement.start}, ${switchElement.end})")
    }
}

fun getTransformedPoint(
    switchStructure: SwitchStructure,
    orig: Point,
    switchAngle: Double,
    number: JointNumber,
): Point {
    val structureJointsByNumber = switchStructure.joints.associateBy { it.number }
    val jointPointOrig = structureJointsByNumber[number]!!.location
    return rotateAroundOrigin(switchAngle, jointPointOrig) + orig
}

data class SwitchJointData(
    val switchId: DomainId<GeometrySwitch>,
    val startSwitchJoint: SwitchStructureJoint,
    val endSwitchJoint: SwitchStructureJoint,
) {
    fun isInsideSwitchJoint(switchElement: SwitchStructureElement): Boolean {
        logger.info(
            "Matching switch element (${switchElement.start},${switchElement.end}) to ${startSwitchJoint.number}-${endSwitchJoint.number}"
        )
        val startDistance = pointDistanceToLine(startSwitchJoint.location, endSwitchJoint.location, switchElement.start)
        val endDistance = pointDistanceToLine(startSwitchJoint.location, endSwitchJoint.location, switchElement.end)
        logger.info("start distance $startDistance, end distance $endDistance")
        return startDistance <= 0.5 && endDistance <= 2
    }
}
