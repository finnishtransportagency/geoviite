package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangle
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.getSomeNullableValue
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchElement
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.logger
import java.math.BigDecimal

fun createTrackLayoutTrackNumber(number: String, description: String = "description for $number" ) = TrackLayoutTrackNumber(
    number = TrackNumber(number),
    description = FreeText(description),
    state = LayoutState.IN_USE,
    externalId = null,
)

fun createGeometryKmPost(trackNumberId: IntId<TrackLayoutTrackNumber>, location: Point, kmNumber: String) =
    GeometryKmPost(
        staBack = null,
        staAhead = BigDecimal("-148.729000"),
        staInternal = BigDecimal("-148.729000"),
        kmNumber = KmNumber(kmNumber),
        description = PlanElementName("0"),
        state = PlanState.PROPOSED,
        location = location,
        trackNumberId = trackNumberId,
    )

fun trackLayoutKmPost(kmNumber: String, trackNumberId: IntId<TrackLayoutTrackNumber>, point: Point) =
    TrackLayoutKmPost(
        kmNumber = KmNumber(kmNumber),
        location = point,
        trackNumberId = trackNumberId,
        sourceId = null,
        state = LayoutState.IN_USE
    )

fun createGeometryAlignment(
    alignmentName: String,
    trackNumberId: DomainId<TrackLayoutTrackNumber>,
    basePoint: Point,
    incrementPoints: List<Point>,
    switchData: List<SwitchData?> = emptyList()
): GeometryAlignment {
    val points = pointsFromIncrementList(basePoint, incrementPoints)

    return createGeometryAlignment(
        alignmentName = alignmentName,
        trackNumberId = trackNumberId,
        locationPoints = points,
        switchData = switchData)
}

fun createGeometryAlignment(
    alignmentName: String,
    elementNamePrefix: String = "elm",
    trackNumberId: DomainId<TrackLayoutTrackNumber>,
    locationPoints: List<Point>,
    switchData: List<SwitchData?> = emptyList()
): GeometryAlignment {
    val staStart = BigDecimal("543.333470")
    val elements = locationPoints.dropLast(1).mapIndexed { index, pointA ->
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
        trackNumberId = trackNumberId,
    )
}

fun locationTrack(name: String,
                  trackNumber: IntId<TrackLayoutTrackNumber>,
                  layoutAlignmentType: LocationTrackType = LocationTrackType.MAIN,
                  basePoint: Point,
                  incrementPoints: List<Point>,
                  description: String = "$name location track description",
):Pair<LocationTrack, LayoutAlignment> {

    val points = pointsFromIncrementList(basePoint, incrementPoints)

    val segments = points.dropLast(1).mapIndexed{index, pointA ->
        val pointB = points[index +1]
        segment(toTrackLayoutPoints(pointA, pointB))
    }

    val alignment = alignment(segments)
    val track = locationTrack(
        trackNumberId = trackNumber,
        alignment = alignment,
        name = "lt-$name",
        description = description,
        type = layoutAlignmentType,
        state = LayoutState.IN_USE,
    )
    return track to alignment
}

/*
fun locationTrack(
    name: String,
    trackNumber: IntId<TrackLayoutTrackNumber>,
    layoutPoints: List<LayoutPoint>,
    layoutAlignmentType: LocationTrackType = LocationTrackType.MAIN,
    description: String = "$name location track description"
): Pair<LocationTrack, LayoutAlignment> {
    val segment = segment(layoutPoints)

    val segments = layoutPoints.dropLast(1).mapIndexed{index, pointA ->
        val pointB = layoutPoints[index +1]
        segment(listOf(pointA, pointB))
    }


    val alignment = alignment(listOf(segment(layoutPoints)))
    val track = locationTrack(
        trackNumberId = trackNumber,
        alignment = alignment,
        name = "$name-lt",
        description = description,
        type = layoutAlignmentType,
        state = LayoutState.IN_USE,
    )
    return track to alignment
}
*/

fun trackLayoutSwitch(name: String, jointPoints: List<Point>, switchStructure: SwitchStructure) = TrackLayoutSwitch(
    externalId = null,
    sourceId = null,
    name = SwitchName(name),
    stateCategory = LayoutStateCategory.EXISTING,
    switchStructureId = switchStructure.id as IntId,
    joints = jointPoints.map { point -> switchJoint(point) },
    trapPoint = false,
    ownerId = switchOwnerVayla().id,
    source = GeometrySource.GENERATED,
)

fun switchJoint(location: Point) = TrackLayoutSwitchJoint(
    number = JointNumber(1),
    location = location,
    locationAccuracy = getSomeNullableValue<LocationAccuracy>(1)
)

fun tmi35GeometryUnit() =
    GeometryUnits(
        coordinateSystemSrid = LAYOUT_SRID,
        coordinateSystemName = null,
        verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
        directionUnit = AngularUnit.GRADS,
        linearUnit = LinearUnit.METER,
)

fun createProject(name: String) =
    Project(
        name = ProjectName(name),
        description = FreeText("E2E test project"),
)

fun pointsFromIncrementList(basePoint: Point, incrementPoints: List<Point>) =
    incrementPoints.scan(basePoint) { prevPoint, pointIncr -> prevPoint + pointIncr }

fun locationTrackAndAlignmentForGeometryAlignment(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    geometryAlignment: GeometryAlignment,
    triangulationNetwork: List<KKJtoETRSTriangle>,
    planSrid: Srid = LAYOUT_SRID,
): Pair<LocationTrack, LayoutAlignment> {
    val transformation = Transformation.possiblyKKJToETRSTransform(planSrid, LAYOUT_SRID, triangulationNetwork)
    return locationTrackAndAlignment(
        trackNumberId,
        geometryAlignment.elements.map { element ->
            val start = transformation.transform(element.start)
            val end = transformation.transform(element.end)
            LayoutSegment(
                points = listOf(
                    LayoutPoint(start.x, start.y, 0.0, 0.0, 0.0),
                    LayoutPoint(end.x, end.y, 0.0, element.calculatedLength, 0.0)
                ),
                resolution = 100,
                startJointNumber = element.startJointNumber,
                endJointNumber = element.endJointNumber,
                start = 0.0,
                source = GeometrySource.PLAN,
                sourceId = element.id,
                sourceStart = null,
                switchId = null,
            )
        })
}

fun createSwitchAndAligments(switchName: String, switchStructure: SwitchStructure, switchAngle: Double, switchOrig: Point, trackNumberId: IntId<TrackLayoutTrackNumber>): Pair<GeometrySwitch, List<GeometryAlignment>> {
    val jointNumbers = switchStructure.joints.map { switchJoint -> switchJoint.number  }
    logger.info("Switch structure id ${switchStructure.id}")
    val geometrySwitch = GeometrySwitch(
        name = SwitchName(switchName),
        switchStructureId = switchStructure.id as IntId,
        typeName = GeometrySwitchTypeName(switchStructure.type.typeName),
        state = PlanState.EXISTING,
        joints = jointNumbers.map { jointNumber -> GeometrySwitchJoint(jointNumber, getTransformedPoint(switchStructure, switchOrig, switchAngle, jointNumber)) }
    )

    val geometryAlignments = switchStructureToGeometryAlignment(switchName, switchStructure, switchAngle, switchOrig, geometrySwitch,  trackNumberId)
    return Pair(geometrySwitch, geometryAlignments)
}

fun switchStructureToGeometryAlignment(switchName: String, switchStructure: SwitchStructure, switchAngle: Double, switchOrig: Point, geometrySwitch: GeometrySwitch, trackNumberId: IntId<TrackLayoutTrackNumber>): List<GeometryAlignment> {
    return switchStructure.alignments.mapIndexed { index, switchAlignment ->
        GeometryAlignment(
            name = AlignmentName("$index-$switchName"),
            description = FreeText("$index switch alignment"),
            oidPart = null,
            state = PlanState.PROPOSED,
            featureTypeCode = FeatureTypeCode("111"),
            staStart = BigDecimal("0.000000"),
            elements = alignmentElementsFromSwitchAlignment(switchAlignment, switchAngle, switchOrig, geometrySwitch.id, switchStructure.joints),
            profile = null,
            trackNumberId = trackNumberId,
        )
    }
}

private fun alignmentElementsFromSwitchAlignment(switchAlignment: SwitchAlignment, switchAngle: Double, switchOrig: Point, switchId: DomainId<GeometrySwitch>, switchJoints: List<SwitchJoint>): List<GeometryElement> {
    val switchJointsByNumber = switchJoints.associateBy { it.number }
    val switchJointData = switchAlignment.jointNumbers.dropLast(1).mapIndexed { index, startJointNumber ->
        val endJointNumber = switchAlignment.jointNumbers[index+1]
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
            elementSwitchData = matchSwitchDataToElement(switchElement, switchJointData, switchId)
        )
    }
}

private fun matchSwitchDataToElement(switchElement: SwitchElement, switchJointData: List<SwitchJointData>, switchId: DomainId<GeometrySwitch>): SwitchData {
    try {
        return switchJointData.first { data -> data.isInsideSwitchJoint(switchElement)  }
            .let { SwitchData(switchId, it.startSwitchJoint.number, it.endSwitchJoint.number )}
    } catch (ex: java.util.NoSuchElementException) {
        throw RuntimeException("Could not match switch element (${switchElement.start}, ${switchElement.end})")
    }
}

fun getTransformedPoint(switchStructure: SwitchStructure, orig: Point, switchAngle: Double, number: JointNumber): Point {
    val structureJointsByNumber = switchStructure.joints.associateBy { it.number }
    val jointPointOrig = structureJointsByNumber[number]!!.location
    return rotateAroundOrigin(switchAngle, jointPointOrig) + orig
}


data class SwitchJointData (
    val switchId: DomainId<GeometrySwitch>,
    val startSwitchJoint: SwitchJoint,
    val endSwitchJoint: SwitchJoint,
    ) {
    fun isInsideSwitchJoint(switchElement: SwitchElement): Boolean {
        logger.info("Matching switch element (${switchElement.start},${switchElement.end}) to ${startSwitchJoint.number}-${endSwitchJoint.number}")
        val startDistance = pointDistanceToLine(startSwitchJoint.location, endSwitchJoint.location, switchElement.start)
        val endDistance = pointDistanceToLine(startSwitchJoint.location, endSwitchJoint.location, switchElement.end)
        logger.info("start distance $startDistance, end distance $endDistance")
        return startDistance <= 0.5 && endDistance <= 2
    }
}
