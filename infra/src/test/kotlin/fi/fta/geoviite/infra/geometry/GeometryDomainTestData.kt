package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.RotationDirection
import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.CantTransitionType.LINEAR
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.inframodel.getPlanNameByFileName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Grads
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.math.circleArcLength
import fi.fta.geoviite.infra.math.clothoidLength
import fi.fta.geoviite.infra.math.clothoidPointAtOffset
import fi.fta.geoviite.infra.math.clothoidRadiusAtLength
import fi.fta.geoviite.infra.math.clothoidTwistAtLength
import fi.fta.geoviite.infra.math.gradsToRads
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.math.radsToGrads
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.PI

fun lineFromOrigin(dirStartGrads: Double) = line(Point(0.0, 0.0), pointInDirection(50.0, gradsToRads(dirStartGrads)))

fun lineToOrigin(dirEndGrads: Double) = line(pointInDirection(50.0, gradsToRads(dirEndGrads - 200)), Point(0.0, 0.0))

fun curveFromOrigin(rotation: RotationDirection, dirStartGrads: Double): GeometryCurve {
    // For simple math, make it a 90-degree turn: chord = radius & end-point is in a 45-degree angle
    val radius = 50.0
    val centerDirection = if (rotation == CW) dirStartGrads - 100 else dirStartGrads + 100
    val center = pointInDirection(radius, gradsToRads(centerDirection))
    val endDirection = if (rotation == CW) dirStartGrads - 50 else dirStartGrads + 50
    val end = pointInDirection(radius, gradsToRads(endDirection))
    return curve(rotation, radius, start = Point(0.0, 0.0), end, center)
}

fun curveToOrigin(rotation: RotationDirection, dirEndGrads: Double): GeometryCurve {
    // For simple math, make it a 90-degree turn: chord = radius & start-point is in a 45-degree
    // angle
    val radius = 50.0
    val centerDirection = if (rotation == CW) dirEndGrads - 100 else dirEndGrads + 100
    val center = pointInDirection(radius, gradsToRads(centerDirection))
    val startDirection = if (rotation == CW) dirEndGrads - 150 else dirEndGrads + 150
    val start = pointInDirection(radius, gradsToRads(startDirection))
    return curve(rotation, radius, start, end = Point(0.0, 0.0), center)
}

fun clothoidFromOrigin(rotation: RotationDirection, dirStartGrads: Double) =
    clothoid(
        constant = 200.0,
        rotation = rotation,
        dirStartGrads = dirStartGrads,
        // Steepening: start straight, get a curvature in the end
        radiusStart = null,
        radiusEnd = 1000.0,
        start = Point(0.0, 0.0),
        // End point doesn't matter as the spiral is steepening (calculated from the start)
        dirEndGrads = 0.0,
        end = Point(1.0, 1.0),
        pi = pointInDirection(0.5, gradsToRads(dirStartGrads)),
    )

fun clothoidToOrigin(rotation: RotationDirection, dirEndGrads: Double) =
    clothoid(
        constant = 200.0,
        rotation = rotation,
        dirEndGrads = dirEndGrads,
        // Non-steepening: start curved, straighten out in the end
        radiusStart = 1000.0,
        radiusEnd = null,
        end = Point(0.0, 0.0),
        // Start point doesn't matter as the spiral is flattening (calculated from the end)
        dirStartGrads = 0.0,
        start = Point(1.0, 1.0),
        pi = Point(0.0, 0.0) - pointInDirection(0.5, gradsToRads(dirEndGrads)),
    )

fun biquadraticParabolaFromOrigin(rotation: RotationDirection, dirStartGrads: Double) =
    biquadraticParabola(
        length = 100.0,
        rotation = rotation,
        dirStartGrads = dirStartGrads,
        // Steepening: start straight, get a curvature in the end
        radiusStart = null,
        radiusEnd = 1000.0,
        start = Point(0.0, 0.0),
        // End point doesn't matter as the spiral is steepening (calculated from the start)
        dirEndGrads = 0.0,
        end = Point(1.0, 1.0),
        pi = pointInDirection(0.5, gradsToRads(dirStartGrads)),
    )

fun biquadraticParabolaToOrigin(rotation: RotationDirection, dirEndGrads: Double) =
    biquadraticParabola(
        length = 100.0,
        rotation = rotation,
        dirEndGrads = dirEndGrads,
        // Non-steepening: start curved, straighten out in the end
        radiusStart = 1000.0,
        radiusEnd = null,
        end = Point(0.0, 0.0),
        // Start point doesn't matter as the spiral is flattening (calculated from the end)
        dirStartGrads = 0.0,
        start = Point(1.0, 1.0),
        pi = Point(0.0, 0.0) - pointInDirection(0.5, gradsToRads(dirEndGrads)),
    )

fun line(
    start: Point,
    end: Point,
    length: Double = lineLength(start, end),
    staStart: Double = 0.0,
    name: String = "Test",
    switchData: SwitchData = emptySwitchData(),
) = line(start, end, length, staStart, PlanElementName(name), switchData)

fun minimalLine(
    start: Point = Point(0.0, 0.0),
    end: Point = Point(1.0, 1.0),
    id: DomainId<GeometryElement> = StringId(),
) =
    GeometryLine(
        elementData =
            ElementData(
                name = null,
                oidPart = null,
                start = start,
                end = end,
                staStart = BigDecimal.ZERO.setScale(6),
                length = lineLength(start, end).toBigDecimal().setScale(6, RoundingMode.HALF_UP),
            ),
        switchData = emptySwitchData(),
        id = id,
    )

fun minimalCurve(
    start: Point = Point(0.0, 0.0),
    end: Point = Point(1.0, 1.0),
    center: Point = Point(0.0, 1.0),
    rotation: RotationDirection = CCW,
    id: DomainId<GeometryElement> = StringId(),
) =
    GeometryCurve(
        elementData =
            ElementData(
                name = null,
                oidPart = null,
                start = start,
                end = end,
                staStart = BigDecimal.ZERO.setScale(6),
                length = circleArcLength(lineLength(center, start)).toBigDecimal().setScale(6, RoundingMode.HALF_UP),
            ),
        switchData = emptySwitchData(),
        curveData =
            CurveData(
                rotation = rotation,
                center = center,
                radius = lineLength(center, start).toBigDecimal().setScale(6, RoundingMode.HALF_UP),
                chord = lineLength(start, end).toBigDecimal().setScale(6, RoundingMode.HALF_UP),
            ),
        id = id,
    )

fun minimalClothoid(
    start: Point = Point(0.0, 0.0),
    end: Point = Point(1.0, 1.0),
    pi: Point = Point(0.0, 1.0),
    rotation: RotationDirection = CCW,
    constant: Double = 200.0,
    id: DomainId<GeometryElement> = StringId(),
): GeometryClothoid {
    val length = lineLength(start, end)
    return GeometryClothoid(
        ElementData(
            name = null,
            oidPart = null,
            start = start,
            end = end,
            staStart = BigDecimal.ZERO.setScale(6),
            length = length.toBigDecimal().setScale(6, RoundingMode.HALF_UP),
        ),
        switchData = emptySwitchData(),
        spiralData =
            SpiralData(
                rotation = rotation,
                pi = pi,
                directionEnd = null,
                directionStart = null,
                radiusStart = null,
                radiusEnd = BigDecimal(clothoidRadiusAtLength(constant, length)).setScale(6, RoundingMode.HALF_UP),
            ),
        constant = constant.toBigDecimal().setScale(6, RoundingMode.HALF_UP),
        id = id,
    )
}

fun line(
    start: Point,
    end: Point,
    length: Double = lineLength(start, end),
    staStart: Double = 0.0,
    name: PlanElementName,
    switchData: SwitchData = emptySwitchData(),
) =
    GeometryLine(
        ElementData(
            name = name,
            oidPart = PlanElementName("1"),
            start = start,
            end = end,
            staStart = staStart.toBigDecimal(),
            length = length.toBigDecimal(),
        ),
        switchData = switchData,
    )

fun emptySwitchData() = SwitchData(null, null, null)

fun curve(
    rotation: RotationDirection,
    radius: Double,
    start: Point,
    end: Point,
    center: Point,
    chord: Double = lineLength(start, end),
    length: Double = circleArcLength(radius, chord),
) =
    GeometryCurve(
        ElementData(
            name = PlanElementName("Test"),
            oidPart = PlanElementName("1"),
            start = start,
            end = end,
            staStart = BigDecimal("0.0"),
            length = length.toBigDecimal(),
        ),
        CurveData(rotation = rotation, radius = radius.toBigDecimal(), chord = chord.toBigDecimal(), center = center),
        switchData = emptySwitchData(),
    )

fun clothoidSteepening(
    constant: Double,
    length: Double,
    startAngle: Double,
    startPoint: Point,
    rotation: RotationDirection,
): GeometryClothoid {
    val endRadius = clothoidRadiusAtLength(constant, length)
    val endAngle = startAngle + clothoidTwistAtLength(endRadius, length)
    var idealOffset = clothoidPointAtOffset(constant, length)
    if (rotation == CW) idealOffset = Point(idealOffset.x, -1 * idealOffset.y)
    val offset = rotateAroundOrigin(startAngle, idealOffset)
    val endPoint = startPoint + offset
    val piPoint = piPoint(startPoint, endPoint, startAngle, endAngle)
    return clothoid(
        constant = constant,
        rotation = rotation,
        radiusStart = null,
        dirStartGrads = radsToGrads(startAngle),
        start = startPoint,
        radiusEnd = endRadius,
        dirEndGrads = radsToGrads(endAngle),
        end = endPoint,
        pi = piPoint,
    )
}

fun piPoint(start: Point, end: Point, startDir: Double, endDir: Double): Point =
    lineIntersection(
            start1 = start,
            end1 = start + pointInDirection(10.0, startDir),
            start2 = end - pointInDirection(10.0, endDir),
            end2 = end,
        )!!
        .point

fun clothoidFlattening(
    constant: Double,
    length: Double,
    endAngle: Double,
    endPoint: Point,
    rotation: RotationDirection,
): GeometryClothoid {
    val startRadius = clothoidRadiusAtLength(constant, length)
    val startAngle = endAngle - clothoidTwistAtLength(startRadius, length)
    var idealOffset = clothoidPointAtOffset(constant, length)
    if (rotation == CCW) idealOffset = Point(idealOffset.x, -1 * idealOffset.y)
    val offset = rotateAroundOrigin(endAngle - PI, idealOffset)
    val startPoint = endPoint + offset
    val piPoint = piPoint(startPoint, endPoint, startAngle, endAngle)
    return clothoid(
        constant = constant,
        rotation = rotation,
        radiusStart = startRadius,
        dirStartGrads = radsToGrads(startAngle),
        start = startPoint,
        radiusEnd = null,
        dirEndGrads = radsToGrads(endAngle),
        end = endPoint,
        pi = piPoint,
    )
}

fun clothoid(
    constant: Double,
    rotation: RotationDirection,
    radiusStart: Double?,
    radiusEnd: Double?,
    start: Point,
    end: Point,
    dirStartGrads: Double,
    dirEndGrads: Double,
    length: Double = clothoidLength(constant, radiusStart, radiusEnd),
) =
    clothoid(
        constant = constant,
        rotation = rotation,
        radiusStart = radiusStart,
        radiusEnd = radiusEnd,
        start = start,
        end = end,
        pi = piPoint(start, end, gradsToRads(dirStartGrads), gradsToRads(dirEndGrads)),
        dirStartGrads = dirStartGrads,
        dirEndGrads = dirEndGrads,
        length = length,
    )

fun clothoid(
    constant: Double,
    rotation: RotationDirection,
    radiusStart: Double?,
    radiusEnd: Double?,
    start: Point,
    end: Point,
    pi: Point,
    length: Double = clothoidLength(constant, radiusStart, radiusEnd),
    dirStartGrads: Double? = null,
    dirEndGrads: Double? = null,
) =
    GeometryClothoid(
        ElementData(
            name = PlanElementName("Test"),
            oidPart = PlanElementName("1"),
            start = start,
            end = end,
            staStart = BigDecimal("0.0"),
            length = length.toBigDecimal(),
        ),
        SpiralData(
            rotation = rotation,
            directionStart = dirStartGrads?.let { d -> Grads(d.toBigDecimal()) },
            directionEnd = dirEndGrads?.let { d -> Grads(d.toBigDecimal()) },
            radiusStart = radiusStart?.toBigDecimal(),
            radiusEnd = radiusEnd?.toBigDecimal(),
            pi = pi,
        ),
        constant = constant.toBigDecimal(),
        switchData = emptySwitchData(),
    )

fun biquadraticParabola(
    length: Double,
    rotation: RotationDirection,
    radiusStart: Double?,
    radiusEnd: Double?,
    start: Point,
    end: Point,
    dirStartGrads: Double,
    dirEndGrads: Double,
) =
    biquadraticParabola(
        length = length,
        rotation = rotation,
        radiusStart = radiusStart,
        radiusEnd = radiusEnd,
        start = start,
        end = end,
        pi = piPoint(start, end, gradsToRads(dirStartGrads), gradsToRads(dirEndGrads)),
        dirStartGrads = dirStartGrads,
        dirEndGrads = dirEndGrads,
    )

fun biquadraticParabola(
    length: Double,
    rotation: RotationDirection,
    radiusStart: Double?,
    radiusEnd: Double?,
    start: Point,
    end: Point,
    pi: Point,
    dirStartGrads: Double? = null,
    dirEndGrads: Double? = null,
) =
    BiquadraticParabola(
        ElementData(
            name = PlanElementName("Test"),
            oidPart = PlanElementName("1"),
            start = start,
            end = end,
            staStart = BigDecimal("0.0"),
            length = length.toBigDecimal(),
        ),
        SpiralData(
            rotation = rotation,
            directionStart = dirStartGrads?.let { d -> Grads(d.toBigDecimal()) },
            directionEnd = dirEndGrads?.let { d -> Grads(d.toBigDecimal()) },
            radiusStart = radiusStart?.toBigDecimal(),
            radiusEnd = radiusEnd?.toBigDecimal(),
            pi = pi,
        ),
        switchData = emptySwitchData(),
    )

fun infraModelFile(name: String = "test_file.xml") =
    InfraModelFile(name = FileName(name), content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><LandXml></LandXml>")

fun plan(
    trackNumber: TrackNumber = TrackNumber("001"),
    srid: Srid = Srid(3879),
    vararg alignments: GeometryAlignment = arrayOf(geometryAlignment()),
): GeometryPlan = plan(trackNumber, srid, alignments.toList())

fun plan(
    trackNumber: TrackNumber = TrackNumber("001"),
    srid: Srid? = Srid(3879),
    alignments: List<GeometryAlignment> = listOf(geometryAlignment()),
    switches: List<GeometrySwitch> = listOf(),
    measurementMethod: MeasurementMethod? = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
    elevationMeasurementMethod: ElevationMeasurementMethod? = ElevationMeasurementMethod.TOP_OF_SLEEPER,
    trackNumberDesc: PlanElementName = PlanElementName("TNDesc"),
    fileName: FileName = FileName("test_file.xml"),
    coordinateSystemName: CoordinateSystemName? = null,
    verticalCoordinateSystem: VerticalCoordinateSystem? = null,
    source: PlanSource = PlanSource.GEOMETRIAPALVELU,
    planTime: Instant = Instant.EPOCH,
    kmPosts: List<GeometryKmPost> = kmPosts(srid),
    project: Project = project(),
    units: GeometryUnits = geometryUnits(srid, coordinateSystemName, verticalCoordinateSystem),
    planApplicability: PlanApplicability? = null,
): GeometryPlan {
    return GeometryPlan(
        source = source,
        project = project,
        application = application(),
        author = author("TEST Company"),
        planTime = planTime,
        units = units,
        trackNumber = trackNumber,
        trackNumberDescription = trackNumberDesc,
        alignments = alignments,
        switches = switches,
        kmPosts = kmPosts,
        fileName = fileName,
        pvDocumentId = null,
        planPhase = PlanPhase.RAILWAY_PLAN,
        decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
        measurementMethod = measurementMethod,
        elevationMeasurementMethod = elevationMeasurementMethod,
        message = FreeTextWithNewLines.of("test text \n description"),
        uploadTime = Instant.now(),
        name = getPlanNameByFileName(fileName),
        planApplicability = planApplicability,
    )
}

fun planHeader(
    id: IntId<GeometryPlan> = IntId(1),
    fileName: FileName = FileName("test_file.xml"),
    measurementMethod: MeasurementMethod? = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
    elevationMeasurementMethod: ElevationMeasurementMethod? = ElevationMeasurementMethod.TOP_OF_SLEEPER,
    srid: Srid = Srid(3879),
    coordinateSystemName: CoordinateSystemName? = null,
    trackNumber: TrackNumber = TrackNumber("001"),
    verticalCoordinateSystem: VerticalCoordinateSystem? = null,
    source: PlanSource = PlanSource.GEOMETRIAPALVELU,
) =
    GeometryPlanHeader(
        id = id,
        version = RowVersion(id, 1),
        fileName = fileName,
        project = project(),
        units = geometryUnits(srid, coordinateSystemName, verticalCoordinateSystem),
        planPhase = PlanPhase.RAILWAY_PLAN,
        decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
        measurementMethod = measurementMethod,
        elevationMeasurementMethod = elevationMeasurementMethod,
        kmNumberRange = null,
        planTime = Instant.EPOCH,
        trackNumber = trackNumber,
        linkedAsPlanId = null,
        message = FreeTextWithNewLines.of("test text \n description"),
        uploadTime = Instant.now(),
        source = source,
        hasCant = false,
        hasProfile = false,
        author = "Test Company",
        isHidden = false,
        name = getPlanNameByFileName(fileName),
        planApplicability = null,
    )

fun minimalPlan(fileName: FileName = FileName("TEST_FILE.xml")) =
    GeometryPlan(
        source = PlanSource.GEOMETRIAPALVELU,
        fileName = fileName,
        units = GeometryUnits(null, null, null, AngularUnit.GRADS, LinearUnit.METER),
        trackNumberDescription = PlanElementName("TEST_TN_DESC"),
        project = Project(name = ProjectName("TEST Project"), description = null),
        application =
            Application(
                name = MetaDataName("TEST Application"),
                manufacturer = MetaDataName("TEST APP Company"),
                version = MetaDataName("v1.0.0"),
            ),
        author = null,
        message = null,
        planPhase = null,
        decisionPhase = null,
        planTime = null,
        switches = listOf(),
        alignments = listOf(),
        kmPosts = listOf(),
        pvDocumentId = null,
        measurementMethod = null,
        elevationMeasurementMethod = null,
        trackNumber = null,
        uploadTime = null,
        name = getPlanNameByFileName(fileName),
        planApplicability = null,
    )

fun geometryLine(
    name: String = "S004",
    oidPart: String = "3",
    start: Point = Point(x = 385372.582, y = 6673712.000614),
    end: Point = Point(x = 385379.2240573294, y = 6673744.810696),
    staStart: BigDecimal = BigDecimal("543.333470"),
    length: BigDecimal = BigDecimal("33.475639"),
    elementSwitchData: SwitchData? = null,
) =
    GeometryLine(
        elementData =
            ElementData(
                name = PlanElementName(name),
                oidPart = PlanElementName(oidPart),
                start = start,
                end = end,
                staStart = staStart,
                length = length,
            ),
        switchData = elementSwitchData ?: SwitchData(switchId = null, startJointNumber = null, endJointNumber = null),
    )

fun geometryElements(): List<GeometryElement> {
    val start = Point(x = 385372.582, y = 6673712.000614)
    val end = Point(x = 385379.2240573294, y = 6673744.810696)
    val staStart = BigDecimal("543.333470")
    val length = BigDecimal("33.475639")
    val element0 = geometryLine("S004", "3", start, end, staStart, length)

    val start1 = Point(x = 385379.2240573294, y = 6673744.810696)
    val end1 = Point(x = 385382.6413540228, y = 6673761.691271)
    val staStart1 = BigDecimal("576.809109")
    val length1 = BigDecimal("17.223000")
    val element1 = geometryLine("V022", "4", start1, end1, staStart1, length1)
    return listOf(element0, element1)
}

fun geometryAlignment(vararg elements: GeometryElement = geometryElements().toTypedArray()): GeometryAlignment =
    geometryAlignment(elements.toList())

fun geometryAlignment(
    elements: List<GeometryElement> = geometryElements(),
    profile: GeometryProfile? = null,
    cant: GeometryCant? = null,
    name: String = "001",
    id: DomainId<GeometryAlignment> = StringId(),
    featureTypeCode: FeatureTypeCode = FeatureTypeCode("281"),
): GeometryAlignment =
    GeometryAlignment(
        id = id,
        name = AlignmentName(name),
        description = FreeText("test-alignment 001"),
        oidPart = null,
        state = PlanState.PROPOSED,
        featureTypeCode = featureTypeCode,
        staStart = BigDecimal("0.000000"),
        elements = elements,
        profile = profile,
        cant = cant,
    )

fun linearCant(startDistance: Double, endDistance: Double, startValue: Double, endValue: Double): GeometryCant {
    val point1 =
        GeometryCantPoint(
            station = round(startDistance, 6),
            appliedCant = round(startValue, 6),
            curvature = CW,
            transitionType = LINEAR,
        )
    val point2 =
        GeometryCantPoint(
            station = round(endDistance, 6),
            appliedCant = round(endValue, 6),
            curvature = CW,
            transitionType = LINEAR,
        )
    return geometryCant(points = listOf(point1, point2))
}

fun geometryCant(points: List<GeometryCantPoint>) =
    GeometryCant(
        name = PlanElementName("TST Cant"),
        description = PlanElementName("Test alignment cant"),
        gauge = FINNISH_RAIL_GAUGE,
        rotationPoint = CantRotationPoint.INSIDE_RAIL,
        points = points,
    )

fun geometrySwitch(
    name: String = "TEST V001",
    switchStructureId: IntId<SwitchStructure>? = null,
    joints: List<GeometrySwitchJoint> =
        listOf(
            GeometrySwitchJoint(JointNumber(1), Point(1.0, 1.0)),
            GeometrySwitchJoint(JointNumber(2), Point(2.0, 2.0)),
        ),
) =
    GeometrySwitch(
        name = SwitchName(name),
        state = PlanState.EXISTING,
        switchStructureId = switchStructureId,
        joints = joints,
        typeName = GeometrySwitchTypeName("TestSwitchType"),
    )

fun kmPosts(srid: Srid?) =
    listOf(
        GeometryKmPost(
            staBack = null,
            staAhead = BigDecimal("-148.729000"),
            staInternal = BigDecimal("-148.729000"),
            kmNumber = KmNumber.ZERO,
            description = PlanElementName("0"),
            state = PlanState.PROPOSED,
            location = null,
        ),
        GeometryKmPost(
            staBack = BigDecimal("1003.440894"),
            staAhead = BigDecimal("854.711894"),
            staInternal = BigDecimal("854.711894"),
            kmNumber = KmNumber(1),
            description = PlanElementName("1"),
            state = PlanState.PROPOSED,
            location =
                if (srid == null) null
                else {
                    transformNonKKJCoordinate(Srid(3879), srid, Point(x = 25496284.448063374, y = 6674885.339042075))
                },
        ),
    )

fun geometryUnits(
    srid: Srid?,
    coordinateSystemName: CoordinateSystemName? = null,
    verticalCoordinateSystem: VerticalCoordinateSystem? = VerticalCoordinateSystem.N2000,
) =
    GeometryUnits(
        coordinateSystemSrid = srid,
        coordinateSystemName = coordinateSystemName,
        verticalCoordinateSystem = verticalCoordinateSystem,
        directionUnit = AngularUnit.GRADS,
        linearUnit = LinearUnit.METER,
    )

fun project(name: String = "TEST Project", description: String? = null) =
    Project(ProjectName(name), description?.let(::FreeText))

fun author(companyName: String = "TEST Company") = Author(CompanyName(companyName))

fun application(
    name: String = "TEST Application",
    manufacturer: String = "Solita Ab/Oy",
    version: String = "04.02.21.14.02.21.1",
) = Application(MetaDataName(name), MetaDataName(manufacturer), MetaDataName(version))

fun testFile() = InfraModelFile(FileName("testfile_empty_xml.xml"), "<a></a>")

fun someBoundingPolygon() =
    Polygon(
        Point(494585.0, 6710975.0),
        Point(494791.0, 6711265.0),
        Point(495074.0, 6711718.0),
        Point(494786.0, 6711281.0),
        Point(494585.0, 6710975.0),
    )
