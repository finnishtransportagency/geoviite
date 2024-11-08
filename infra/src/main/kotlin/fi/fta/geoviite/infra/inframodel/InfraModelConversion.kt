package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.Application
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.BiquadraticParabola
import fi.fta.geoviite.infra.geometry.CantRotationPoint
import fi.fta.geoviite.infra.geometry.CantTransitionType
import fi.fta.geoviite.infra.geometry.CantTransitionType.BIQUADRATIC_PARABOLA
import fi.fta.geoviite.infra.geometry.CantTransitionType.LINEAR
import fi.fta.geoviite.infra.geometry.CompanyName
import fi.fta.geoviite.infra.geometry.CurveData
import fi.fta.geoviite.infra.geometry.ElementData
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryCant
import fi.fta.geoviite.infra.geometry.GeometryCantPoint
import fi.fta.geoviite.infra.geometry.GeometryClothoid
import fi.fta.geoviite.infra.geometry.GeometryCurve
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryLine
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.GeometrySwitchJoint
import fi.fta.geoviite.infra.geometry.GeometrySwitchTypeName
import fi.fta.geoviite.infra.geometry.GeometryUnits
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.PlanState
import fi.fta.geoviite.infra.geometry.PlanState.ABANDONED
import fi.fta.geoviite.infra.geometry.PlanState.DESTROYED
import fi.fta.geoviite.infra.geometry.PlanState.EXISTING
import fi.fta.geoviite.infra.geometry.PlanState.PROPOSED
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.SpiralData
import fi.fta.geoviite.infra.geometry.SwitchData
import fi.fta.geoviite.infra.geometry.VICircularCurve
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.VerticalIntersection
import fi.fta.geoviite.infra.math.Angle
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.radsToAngle
import fi.fta.geoviite.infra.math.toAngle
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.switchLibrary.switchTypeRequiresHandedness
import fi.fta.geoviite.infra.switchLibrary.tryParseSwitchType
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.formatForException
import fi.fta.geoviite.infra.util.formatForLog
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val INFRAMODEL_SWITCH_CODE = "IM_switch"
const val INFRAMODEL_SWITCH_TYPE = "switchType"
const val INFRAMODEL_SWITCH_HAND = "switchHand"
const val INFRAMODEL_SWITCH_JOINT = "switchJoint"

const val KM_POST_N_LABEL = "kmPostN"
const val KM_POST_E_LABEL = "kmPostE"

const val COORDINATE_ACCURACY = 0.001

val defaultTimeZone: ZoneId = ZoneId.of("Europe/Helsinki")

val logger: Logger = LoggerFactory.getLogger(InfraModel::class.java)

fun toGvtPlan(
    source: PlanSource,
    fileName: FileName,
    infraModel: InfraModel,
    coordinateSystemNameToSrid: Map<CoordinateSystemName, Srid>,
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
): GeometryPlan {

    // Collect & verify expected mandatory sections
    val coordinateSystem = mandatorySection("coordinate-system", infraModel.coordinateSystem)
    val project = mandatorySection("project", infraModel.project)
    val application = mandatorySection("application", infraModel.application)
    val author = mandatorySection("author", application.author)
    val metricUnits = mandatorySection("units", infraModel.units?.metric)
    if (infraModel.alignmentGroups.size != 1) {
        throw InframodelParsingException(
            message = "Plan should have precisely one alignment group: groups=${infraModel.alignmentGroups.size}",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.missing-section.alignment-group",
        )
    }

    if (!isSupportedInframodelVersion(infraModel.featureDictionary?.version)) {
        throw InframodelParsingException(
            message = "Plan InfraModel version isn't supported. version=${infraModel.featureDictionary?.version}",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.unsupported-version",
        )
    }

    val units = parseUnits(coordinateSystem, metricUnits, coordinateSystemNameToSrid)
    val gvtSwitches = collectGeometrySwitches(switchStructuresByType, switchTypeNameAliases, infraModel.alignmentGroups)
    val trackNumberDescription = infraModel.alignmentGroups.first().name
    val trackNumber = tryParseTrackNumber(trackNumberDescription)
    val alignments = mutableListOf<GeometryAlignment>()
    val kmPosts = mutableListOf<GeometryKmPost>()

    infraModel.alignmentGroups.forEach { group ->
        val trackNumberState =
            group.state?.let { stateString -> tryParsePlanState("Track number ${group.name} state", stateString) }

        alignments.addAll(
            group.alignments.map { xmlAlignment -> toGvtAlignment(xmlAlignment, units, gvtSwitches, trackNumberState) }
        )
        kmPosts.addAll(
            group.alignments.flatMap { alignment ->
                val alignmentState =
                    alignment.state?.let { stateString ->
                        tryParsePlanState("Alignment ${alignment.name} state", stateString)
                    }
                alignment.staEquations.map { s -> toGvtKmPost(s, alignmentState ?: trackNumberState) }
            }
        )
    }

    return GeometryPlan(
        source = source,
        project =
            Project(
                name = tryParseText(project.name, ::ProjectName) ?: ProjectName(""),
                description = project.desc?.let(::tryParseFreeText),
            ),
        application =
            Application(
                name = MetaDataName.ofUnsafe(application.name),
                manufacturer = MetaDataName.ofUnsafe(application.manufacturer),
                version = MetaDataName.ofUnsafe(application.version),
            ),
        author = author.company?.let(::tryParseCompanyName)?.let(::Author),
        planTime = author.timeStamp?.let(::parseTime),
        units = units,
        trackNumber = trackNumber,
        trackNumberDescription = tryParsePlanElementName(trackNumberDescription) ?: emptyName(),
        alignments = alignments,
        switches = gvtSwitches.values.toList(),
        fileName = fileName,
        kmPosts = kmPosts,
        pvDocumentId = null,
        measurementMethod = null,
        elevationMeasurementMethod = null,
        planPhase = null,
        decisionPhase = null,
        message = null,
        uploadTime = null,
        isHidden = false,
    )
}

fun <T> mandatorySection(name: String, section: T?): T =
    section
        ?: throw InframodelParsingException(
            message = "Plan is missing mandatory section: $name",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.missing-section.$name",
        )

fun parseUnits(
    coordinateSystem: InfraModelCoordinateSystem,
    metricUnits: InfraModelMetric,
    coordinateSystemNameToSrid: Map<CoordinateSystemName, Srid>,
): GeometryUnits {
    val coordinateSystemName =
        if (coordinateSystem.name.isBlank()) null else CoordinateSystemName(coordinateSystem.name.trim())
    val rotationAngle = coordinateSystem.rotationAngle
    if (rotationAngle.isNotBlank() && rotationAngle.toBigDecimal().compareTo(ZERO) != 0) {
        throw InframodelParsingException("Plan rotation is not supported: value=${formatForException(rotationAngle)}")
    }
    return GeometryUnits(
        coordinateSystemSrid = toSrid(coordinateSystemName, coordinateSystem.epsgCode, coordinateSystemNameToSrid),
        coordinateSystemName = coordinateSystemName,
        verticalCoordinateSystem = coordinateSystem.verticalCoordinateSystemName.let(::toVerticalCoordinateSystem),
        directionUnit = parseEnum("Plan direction measurement unit", metricUnits.directionUnit),
        linearUnit = parseEnum("Plan linear measurement unit", metricUnits.linearUnit),
    )
}

private val SUPPORTED_INFRAMODEL_VERSIONS = listOf("4.0.3", "4.0.4")

private fun isSupportedInframodelVersion(versionString: String?) =
    versionString == null || SUPPORTED_INFRAMODEL_VERSIONS.contains(versionString)

fun parseTime(timeString: String): Instant =
    if (timeString.endsWith("Z")) Instant.parse(timeString)
    else ZonedDateTime.of(LocalDateTime.parse(timeString), defaultTimeZone).toInstant()

fun toSrid(
    csName: CoordinateSystemName?,
    epsgCode: String?,
    coordinateSystemNameToSrid: Map<CoordinateSystemName, Srid>,
): Srid? {
    val code = epsgCode?.let { code -> parseOptionalInt("Coordinate system EPSG code", code) }
    // Note: EPSG 4022 is just a code for "deprecated" and cannot be used for transformations as
    // such
    val parsedSrid = if (code != null && code > 0 && code != 4022) Srid(code) else null
    return parsedSrid ?: csName?.let { name -> coordinateSystemNameToSrid[name.uppercase()] }
}

fun toVerticalCoordinateSystem(name: String): VerticalCoordinateSystem? =
    if (name.isNotBlank()) {
        try {
            parseOptionalEnum<VerticalCoordinateSystem>("Plan vertical coordinate system", name)
        } catch (ex: InputValidationException) {
            logger.warn("Unknown vertical coordinate system: ${formatForException(name)}")
            null
        }
    } else null

fun toGvtAlignment(
    alignment: InfraModelAlignment,
    units: GeometryUnits,
    switches: Map<SwitchKey, GeometrySwitch>,
    parentState: PlanState?,
): GeometryAlignment {
    val profile = alignment.profile?.let { p -> toGvtProfile(p) }
    val cant = alignment.cant?.let { c -> toGvtCant(c) }
    return GeometryAlignment(
        name =
            tryParseAlignmentName(alignment.name)
                ?: throw InputValidationException(
                    message = "Invalid alignment name: ${formatForException(alignment.name)}",
                    type = AlignmentName::class,
                    value = alignment.name,
                ),
        description = alignment.desc?.let(::tryParseFreeText),
        oidPart = alignment.oid?.let(::tryParseFreeText),
        state = parseOptionalEnum<PlanState>("Alignment ${alignment.name} state", alignment.state) ?: parentState,
        staStart = parseBigDecimal("Alignment ${alignment.name} station start value", alignment.staStart),
        featureTypeCode = getAlignmentImCodingFeatureType(alignment.features),
        elements =
            alignment.elements.mapNotNull { e ->
                if (e.start != e.end) toGvtGeometryElement(e, units, switches) else null
            },
        profile = profile,
        cant = cant,
    )
}

private fun getAlignmentImCodingFeatureType(features: List<InfraModelFeature>): FeatureTypeCode? =
    features
        .find { feature -> feature.code == "IM_coding" }
        ?.getPropertyAnyMatch(listOf("terrainCoding", "infraCoding"))
        ?.let(::tryParseFeatureTypeCode)

fun toGvtKmPost(staEquation: InfraModelStaEquation, state: PlanState?): GeometryKmPost {
    val kmFeature: InfraModelFeature? = staEquation.feature
    val northing = kmFeature?.properties?.find { f -> f.label == KM_POST_N_LABEL }
    val easting = kmFeature?.properties?.find { f -> f.label == KM_POST_E_LABEL }
    val location =
        if (northing == null || easting == null) null
        else parsePoint("KM Post ${staEquation.desc} coordinates", y = northing.value, x = easting.value)
    val description = staEquation.desc.trim()
    return GeometryKmPost(
        staBack = parseOptionalBigDecimal("KM Post $description station back", staEquation.staBack),
        staAhead = parseBigDecimal("KM Post $description station ahead", staEquation.staAhead),
        staInternal = parseBigDecimal("KM Post $description station internal", staEquation.staInternal),
        kmNumber = tryParseKmNumber(description),
        description = PlanElementName(description),
        location = location,
        state = state,
    )
}

fun toGvtGeometryElement(
    element: InfraModelGeometryElement,
    units: GeometryUnits,
    switches: Map<SwitchKey, GeometrySwitch>,
): GeometryElement {
    val name = "Element ${element.name}"
    val start = xmlCoordinateToPoint("$name start point", element.start)
    val end = xmlCoordinateToPoint("$name end point", element.end)
    val switch: GeometrySwitch? = getSwitchKey(element)?.let(switches::get)
    val switchData =
        SwitchData(
            switchId = switch?.id,
            startJointNumber = switch?.getJoint(start, COORDINATE_ACCURACY)?.number,
            endJointNumber = switch?.getJoint(end, COORDINATE_ACCURACY)?.number,
        )
    val elementData =
        ElementData(
            name = element.name?.let(::tryParsePlanElementName),
            oidPart = element.oID?.let(::tryParsePlanElementName),
            start = start,
            end = end,
            staStart = parseBigDecimal("$name station start value", element.staStart),
            length = parseBigDecimal("$name length", element.length),
        )
    return when (element) {
        is InfraModelLine -> GeometryLine(elementData = elementData, switchData = switchData)
        is InfraModelCurve ->
            GeometryCurve(
                elementData = elementData,
                curveData =
                    CurveData(
                        rotation = parseEnum("$name rotation direction", element.rot),
                        radius = parseBigDecimal("$name circle radius", element.radius),
                        chord = parseBigDecimal("$name circle arc chord", element.chord),
                        center = xmlCoordinateToPoint("$name circular radius center point", element.center),
                    ),
                switchData = switchData,
            )

        is InfraModelSpiral -> {
            val pi = xmlCoordinateToPoint("$name spiral PI point", element.pi)
            val startAngle = angle("$name start direction", element.dirStart ?: "", units.directionUnit)
            val endAngle = angle("$name end direction", element.dirEnd ?: "", units.directionUnit)
            val spiralData =
                SpiralData(
                    rotation = parseEnum("$name spiral curvature direction", element.rot),
                    directionStart = startAngle,
                    directionEnd = endAngle,
                    radiusStart = spiralRadius(element.radiusStart),
                    radiusEnd = spiralRadius(element.radiusEnd),
                    pi = pi,
                )
            when (element.spiType.uppercase()) {
                "CLOTHOID" ->
                    GeometryClothoid(
                        elementData = elementData,
                        spiralData = spiralData,
                        switchData = switchData,
                        constant = parseBigDecimal("$name clothoid constant", element.constant ?: ""),
                    )

                "BIQUADRATICPARABOLA" ->
                    BiquadraticParabola(elementData = elementData, spiralData = spiralData, switchData = switchData)

                else ->
                    throw InframodelParsingException(
                        "${formatForException(name)} has unknown spiral type ${
                        formatForException(
                            element.spiType
                        )
                    }"
                    )
            }
        }

        else -> throw InframodelParsingException("Invalid element ${element::class.simpleName}")
    }
}

fun toGvtProfile(profile: InfraModelProfile): GeometryProfile? {
    val profAlign =
        profile.profAlign
            ?: throw InframodelParsingException(
                message = "XML Profile lacks ProfAlign element",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.missing-section.prof-align",
            )
    return try {
        GeometryProfile(
                name = tryParsePlanElementName(profAlign.name) ?: emptyName(),
                elements = profAlign.elements.map { pa -> toGvtVerticalIntersection(pa) },
            )
            .let { geometryProfile ->
                // Ensure that profile segment calculation works
                if (geometryProfile.segments.isEmpty()) {
                    logger.warn("GeometryProfile elements don't produce valid segments")
                    null
                } else geometryProfile
            }
    } catch (ex: Exception) {
        logger.warn(
            "Failed to parse profile: name=${formatForLog(profAlign.name)} elements=${profAlign.elements} cause=$ex"
        )
        null
    }
}

fun toGvtVerticalIntersection(profileElement: InfraModelProfileElement): VerticalIntersection {
    val name = "Profile element ${profileElement.desc}"
    return when (profileElement) {
        is InfraModelPvi ->
            VIPoint(
                description = profileElement.desc?.let(::tryParsePlanElementName) ?: emptyName(),
                point = xmlPointToPoint("$name x/z", profileElement.point),
            )

        is InfraModelCircCurve ->
            VICircularCurve(
                description = profileElement.desc?.let(::tryParsePlanElementName) ?: emptyName(),
                point = xmlPointToPoint("$name x/z", profileElement.point),
                radius =
                    parseOptionalBigDecimal("$name curve radius", profileElement.radius)?.let { r ->
                        if (r.compareTo(ZERO) == 0) null else r
                    },
                length = parseOptionalBigDecimal("$name curve length", profileElement.length),
            )

        else ->
            throw InputValidationException(
                message = "${formatForException(name)} has unknown type: ${profileElement::class.simpleName}",
                type = InfraModelProfileElement::class,
                value = profileElement::class.simpleName ?: "null",
            )
    }
}

fun toGvtCant(cant: InfraModelCant): GeometryCant {
    return GeometryCant(
        name = tryParsePlanElementName(cant.name) ?: emptyName(),
        description = cant.desc?.let(::tryParsePlanElementName) ?: emptyName(),
        gauge = parseBigDecimal("Cant gauge", cant.gauge),
        rotationPoint =
            when (cant.rotationPoint) {
                "insideRail" -> CantRotationPoint.INSIDE_RAIL
                "center" -> CantRotationPoint.CENTER
                "" -> null
                else ->
                    throw InputValidationException(
                        message = "XML Cant rotation point unrecognized: ${formatForException(cant.rotationPoint)}",
                        type = CantRotationPoint::class,
                        value = cant.rotationPoint,
                    )
            },
        points = cant.stations.map { s -> toGvtCantPoint(s) },
    )
}

fun toGvtCantPoint(station: InfraModelCantStation): GeometryCantPoint {

    return GeometryCantPoint(
        station = parseBigDecimal("Cant point station", station.station),
        appliedCant = parseBigDecimal("Cant point applied cant", station.appliedCant),
        curvature = parseEnum("Cant point curvature direction", station.curvature),
        transitionType =
            when (station.transitionType) {
                null -> LINEAR
                "biquadraticParabola" -> BIQUADRATIC_PARABOLA
                else ->
                    throw InputValidationException(
                        message =
                            "Unknown XML Cant transition type: ${station.transitionType?.let(::formatForException)}",
                        type = CantTransitionType::class,
                        value = station.transitionType ?: "null",
                    )
            },
    )
}

fun spiralRadius(value: String?): BigDecimal? {
    // Spiral starts from R=INF (straight) and reaches R=0 (infinite angle) at infinite length
    // (impossible in practice).
    // We use R=null to mark R=INF and also default to this value.
    return if (value == "INF") null else value?.let { v -> parseBigDecimal("Element spiral radius", v) }
}

fun angle(name: String, value: String, unit: AngularUnit): Angle? {
    // Plans include geo-angles (0=north, clockwise), so turn them into mathematical ones
    // (0=positive-X, counter-clockwise)
    return parseOptionalBigDecimal(name, value)?.let { d -> toAngle(d, unit).geoToMath() }
}

fun angleBetween(point1: Point, point2: Point, unit: AngularUnit): Angle {
    val rads = directionBetweenPoints(point2, point1)
    return radsToAngle(rads, unit)
}

fun xmlPointToPoint(name: String, xmlPoint: String): Point {
    val pieces = splitStringOnSpaces(xmlPoint)
    if (pieces.size != 2)
        throw InputValidationException(
            message = "Cannot parse ${formatForException(name)} from string: ${formatForException(xmlPoint)}",
            type = Point::class,
            value = xmlPoint,
        )
    return parsePoint(name, pieces[0], pieces[1])
}

fun xmlCoordinateToPoint(name: String, xmlCoordinate: String): Point {
    val pieces = splitStringOnSpaces(xmlCoordinate)
    // Some systems write in Z-coordinate as well. We use Profile to calculate it, so ignore it
    // here.
    if (pieces.size !in 2..3)
        throw InputValidationException(
            message = "Cannot parse ${formatForException(name)} from string: ${formatForException(xmlCoordinate)}",
            type = Point::class,
            value = xmlCoordinate,
        )
    // Geodetic coordinates: Northing = Latitude = Y, Easting = Longitude = X
    return parsePoint(name, pieces[1], pieces[0])
}

fun switchTypeHand(switchHand: String): SwitchHand {
    return SwitchHand.values().find { h -> h.name.startsWith(switchHand.uppercase()) }
        ?: throw InputValidationException(
            message = "Can't recognize switch hand type from value: ${formatForException(switchHand)}",
            type = SwitchHand::class,
            value = switchHand,
        )
}

fun normalizeSwitchTypeName(switchStructureNameAliases: Map<String, String>, switchTypeName: String): String {
    val withDecimalComma = switchTypeName.replace('.', ',')
    val withLowerCaseX =
        if (withDecimalComma.startsWith("RR") || withDecimalComma.startsWith("SRR")) {
            // RATO4 says this lowercase, for example: RR54-2x1:9.
            // https://www.doria.fi/handle/10024/121411
            // Sometimes the IM files have uppercase, so fix it for leniency
            switchTypeName.replace('X', 'x')
        } else withDecimalComma
    return switchStructureNameAliases[withLowerCaseX] ?: withLowerCaseX
}

fun collectGeometrySwitches(
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    alignmentGroups: List<InfraModelAlignmentGroup>,
): Map<SwitchKey, GeometrySwitch> {
    val tempSwitchAndJoints: List<TempSwitchAndJoints> =
        alignmentGroups.flatMap { group ->
            group.alignments.flatMap { alignment -> getInfraModelSwitches(alignment, group.state) }
        }

    return tempSwitchAndJoints
        // Switches have no proper id -> group individual pieces by a composite key of name + other
        // things
        .groupBy { imSwitch -> imSwitch.tempSwitch.key }
        .mapValues { (_, switchAndJoints) ->
            val xmlSwitches = switchAndJoints.map { it.tempSwitch }
            val switchJoints = switchAndJoints.map { it.joints }.flatten()
            val deduplicatedJoints =
                switchJoints.groupBy { j -> j.number }.values.map { list -> list.first() }.sortedBy { j -> j.number }

            val switchName = verifySameField("Switch name", xmlSwitches, TempSwitch::name, TempSwitch::name)
            val switchTypeNameXml =
                verifySameField("Switch typeName", xmlSwitches, TempSwitch::typeName, TempSwitch::name)
            val switchTypeName = normalizeSwitchTypeName(switchTypeNameAliases, switchTypeNameXml)
            val switchTypeRequiresHandedness =
                tryParseSwitchType(switchTypeName).let { switchType ->
                    if (switchType != null) switchTypeRequiresHandedness(switchType.parts.baseType) else false
                }
            val switchTypeHand = verifySameField("Switch hand", xmlSwitches, TempSwitch::hand, TempSwitch::name)
            val fullSwitchTypeName =
                switchTypeHand.let { hand ->
                    if (hand == SwitchHand.NONE || !switchTypeRequiresHandedness) switchTypeName
                    else "$switchTypeName-${hand.abbreviation}"
                }

            val switchType = tryParseSwitchType(fullSwitchTypeName)
            val switchStructure = switchType?.let { type -> switchStructuresByType[type] }

            if (switchType == null) {
                logger.warn(
                    "Invalid switch type name: " +
                        "full=${formatForLog(fullSwitchTypeName)} " +
                        "orig=${
                        formatForLog(
                            switchTypeName
                        )
                    }"
                )
            }

            GeometrySwitch(
                name =
                    tryParseSwitchName(switchName.trim())
                        ?: throw InputValidationException(
                            message = "Could not parse name for switch: name=${formatForException(switchName)}",
                            type = SwitchName::class,
                            value = switchName,
                        ),
                state = combineSwitchState(xmlSwitches.mapNotNull(TempSwitch::state)),
                joints = deduplicatedJoints,
                switchStructureId = switchStructure?.id as? IntId,
                typeName = tryParseGeometrySwitchTypeName(switchTypeName) ?: GeometrySwitchTypeName(""),
            )
        }
}

fun <T, S> verifySameField(description: String, objects: List<T>, getter: (T) -> S, nameGetter: (T) -> String): S {
    val first = getter(objects.first())
    if (objects.any { o: T -> getter(o) != first }) {
        val names = objects.map { o -> formatForException("${nameGetter(o)}:${getter(o)}") }
        throw InframodelParsingException("$description differs across elements: $names")
    }
    return first
}

fun combineSwitchState(alignmentStates: List<PlanState>): PlanState? =
    if (alignmentStates.contains(DESTROYED)) DESTROYED
    else if (alignmentStates.contains(ABANDONED)) ABANDONED
    else if (alignmentStates.contains(PROPOSED)) PROPOSED
    else if (alignmentStates.contains(EXISTING)) EXISTING else null

fun getInfraModelSwitches(alignment: InfraModelAlignment, trackNumberState: String?): List<TempSwitchAndJoints> {
    val state = parseOptionalEnum<PlanState>("Alignment ${alignment.name} state", alignment.state ?: trackNumberState)
    return alignment.elements
        .mapIndexedNotNull { index, element -> getSwitchElement(index, element, state) }
        // Group switch-elements by the switch "identity" (no real id -> name + other things)
        .groupBy { switchElement -> switchElement.switch.key }
        // Each group is a bundle of tempSwitches representing the same switch -> create joints per
        // group
        .map { group -> toGeometrySwitchJoints(group.value) }
}

fun toGeometrySwitchJoints(originalElements: List<TempSwitchElement>): TempSwitchAndJoints {
    // Unify representations: Repeating joint-numbers mean that the same element has been split ->
    // resolve as 0
    val sortedElements = originalElements.sortedBy { element -> element.elementIndex }
    val elements: List<TempSwitchElement> =
        sortedElements.mapIndexed { index, element ->
            val prevNumber = sortedElements.getOrNull(index - 1)?.jointNumber
            if (prevNumber == element.jointNumber) element.copy(jointNumber = 0) else element
        }

    val switch = elements.first().switch

    // Sanity check: this should not be possible, as the method is invoked per [alignment & switch]
    if (elements.any { ss -> ss.switch != switch }) {
        val elementNames = elements.map { e -> "${e.elementIndex}/${formatForException(e.switch.name)}" }
        throw InframodelParsingException("Switch element grouping failed: $elementNames")
    }

    return TempSwitchAndJoints(
        switch,
        elements.flatMapIndexed { index: Int, element: TempSwitchElement ->
            val startJoint =
                if (element.jointNumber == 0) null
                else GeometrySwitchJoint(JointNumber(element.jointNumber), element.startLocation)
            val endJointNumber = if (index < elements.lastIndex) elements.getOrNull(index + 1)?.jointNumber else null
            val endJoint =
                if (endJointNumber != null && endJointNumber > 0)
                    GeometrySwitchJoint(JointNumber(endJointNumber), element.endLocation)
                else null
            listOfNotNull(startJoint, endJoint)
        },
    )
}

fun getSwitchElement(elementIndex: Int, element: InfraModelGeometryElement, state: PlanState?): TempSwitchElement? {
    return element.features
        .find { feature -> feature.code == INFRAMODEL_SWITCH_CODE }
        ?.let { found -> parseSwitchElementFromFeature(elementIndex, element, found, state) }
}

fun parseSwitchElementFromFeature(
    elementIndex: Int,
    element: InfraModelGeometryElement,
    feature: InfraModelFeature,
    state: PlanState?,
): TempSwitchElement {
    val name = element.name ?: throw InframodelParsingException("Switch element [$elementIndex] must have a name")
    val typeName = feature.getProperty(INFRAMODEL_SWITCH_TYPE)
    val hand = switchTypeHand(feature.getProperty(INFRAMODEL_SWITCH_HAND))
    val joint = feature.getProperty(INFRAMODEL_SWITCH_JOINT)
    return TempSwitchElement(
        switch =
            TempSwitch(
                key = SwitchKey(name, typeName, hand),
                name = name,
                state = state,
                typeName = typeName,
                hand = hand,
            ),
        elementIndex = elementIndex,
        jointNumber = parseOptionalInt("Switch segment ${element.name}[$elementIndex] joint number", joint) ?: 0,
        startLocation =
            xmlCoordinateToPoint("Switch segment ${element.name}[$elementIndex] start point", element.start),
        endLocation = xmlCoordinateToPoint("Switch segment ${element.name}[$elementIndex] end point", element.end),
    )
}

fun getSwitchKey(element: InfraModelGeometryElement): SwitchKey? =
    element.features
        .find { feature -> feature.code == INFRAMODEL_SWITCH_CODE }
        ?.let { feature ->
            val name = element.name ?: throw InframodelParsingException("Switch element must have a name")
            val typeName = feature.getProperty(INFRAMODEL_SWITCH_TYPE)
            val hand = switchTypeHand(feature.getProperty(INFRAMODEL_SWITCH_HAND))
            SwitchKey(name, typeName, hand)
        }

fun tryParsePlanState(name: String, value: String): PlanState? =
    tryParseText(value) { v -> parseOptionalEnum<PlanState>(name, v) }

fun tryParseTrackNumber(text: String): TrackNumber? = if (text == "N/A") null else tryParseText(text, ::TrackNumber)

fun tryParseKmNumber(text: String): KmNumber? =
    if (text == "AKM" || text == "APU") {
        null
    } else if (text.length in 1..6 && text.all(Char::isLetterOrDigit) && text.first().isDigit()) {
        KmNumber(text)
    } else {
        logger.warn("StaEquation desc is not a KM-number: ${formatForLog(text)}")
        null
    }

fun tryParseCompanyName(text: String): CompanyName? = tryParseText(text, CompanyName::ofUnsafe)

fun tryParseAlignmentName(text: String): AlignmentName? = tryParseText(text, AlignmentName::ofUnsafe)

fun tryParseSwitchName(text: String): SwitchName? = tryParseText(text, SwitchName::ofUnsafe)

fun tryParsePlanElementName(text: String): PlanElementName? = tryParseText(text, PlanElementName::ofUnsafe)

fun emptyName() = PlanElementName("")

fun tryParseFreeText(text: String): FreeText? = tryParseText(text, ::FreeText)

fun tryParseFeatureTypeCode(text: String): FeatureTypeCode? = tryParseText(text, ::FeatureTypeCode)

fun tryParseGeometrySwitchTypeName(text: String): GeometrySwitchTypeName? = tryParseText(text, ::GeometrySwitchTypeName)

data class TempSwitchAndJoints(val tempSwitch: TempSwitch, val joints: List<GeometrySwitchJoint>)

data class TempSwitchElement(
    val switch: TempSwitch,
    val elementIndex: Int,
    val jointNumber: Int,
    val startLocation: Point,
    val endLocation: Point,
)

data class TempSwitch(
    val key: SwitchKey,
    val name: String,
    val state: PlanState?,
    val typeName: String,
    val hand: SwitchHand,
)

data class SwitchKey(val name: String, val typeName: String, val hand: SwitchHand)
