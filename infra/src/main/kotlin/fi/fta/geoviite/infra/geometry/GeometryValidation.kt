package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.codeDictionary.FeatureType
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.CantRotationPoint.CENTER
import fi.fta.geoviite.infra.geometry.ErrorType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.calculateSwitchLocationDelta
import fi.fta.geoviite.infra.switchLibrary.transformSwitchPoint
import fi.fta.geoviite.infra.tracklayout.REFERENCE_LINE_TYPE_CODE
import fi.fta.geoviite.infra.util.LocalizationKey
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

enum class ErrorType {
    PARSING_ERROR,
    TRANSFORMATION_ERROR,
    VALIDATION_ERROR,
    OBSERVATION_MAJOR,
    OBSERVATION_MINOR,
}

const val VALIDATION = "infra-model.validation"
const val VALIDATION_METADATA = "metadata"
const val VALIDATION_ALIGNMENT = "alignment"
const val VALIDATION_ELEMENT = "element"
const val VALIDATION_PROFILE = "profile"
const val VALIDATION_CANT = "cant"
const val VALIDATION_SWITCH = "switch"
const val VALIDATION_KM_POST = "km-post"

interface ValidationError {
    val localizationKey: LocalizationKey
    val errorType: ErrorType
}

data class ValidationErrorData(
    override val localizationKey: LocalizationKey,
    override val errorType: ErrorType,
): ValidationError {
    constructor(parentKey: String, errorKey: String, errorType: ErrorType):
            this(LocalizationKey("$VALIDATION.$parentKey.$errorKey"), errorType)
}

data class MetadataError(
    @JsonIgnore private val data: ValidationErrorData,
    val value: String?,
) : ValidationError by data {
    constructor(key: String, type: ErrorType, value: String? = null):
            this(ValidationErrorData(VALIDATION_METADATA, key, type), value)
}

data class SwitchDefinitionError(
    @JsonIgnore private val data: ValidationErrorData,
    val switchName: SwitchName,
    val switchType: GeometrySwitchTypeName?,
    val jointNumbers: List<JointNumber>?,
    val structureJointNumbers: List<JointNumber>?,
    val alignmentName: AlignmentName?,
) : ValidationError by data {
    constructor(
        key: String,
        type: ErrorType,
        switchName: SwitchName,
        switchType: GeometrySwitchTypeName? = null,
        jointNumbers: List<JointNumber>? = null,
        structureJointNumbers: List<JointNumber>? = null,
        alignmentName: AlignmentName? = null,
    ): this(
        data = ValidationErrorData(VALIDATION_SWITCH, key, type),
        switchName = switchName,
        switchType = switchType,
        jointNumbers = jointNumbers,
        structureJointNumbers = structureJointNumbers,
        alignmentName = alignmentName,
    )
}

data class AlignmentError(
    @JsonIgnore private val data: ValidationErrorData,
    val alignmentName: AlignmentName,
    val value: String?,
) : ValidationError by data {
    constructor(key: String, type: ErrorType, alignmentName: AlignmentName, value: String? = null):
            this(ValidationErrorData(VALIDATION_ALIGNMENT, key, type), alignmentName, value)
}

data class ElementError(
    @JsonIgnore private val data: ValidationErrorData,
    val alignmentName: AlignmentName,
    val elementName: PlanElementName?,
    val elementType: GeometryElementType,
    val value: String?,
) : ValidationError by data {
    constructor(
        key: String,
        type: ErrorType,
        alignmentName: AlignmentName,
        element: GeometryElement,
        value: String? = null
    ): this(
        data = ValidationErrorData(VALIDATION_ELEMENT, key, type),
        alignmentName = alignmentName,
        elementName = element.name ?: element.oidPart,
        elementType = element.type,
        value = value,
    )
}

data class ProfileError(
    @JsonIgnore private val data: ValidationErrorData,
    val profileName: PlanElementName,
    val viName: PlanElementName,
    val value: String?,
) : ValidationError by data {
    constructor(key: String, type: ErrorType, profileName: PlanElementName, viName: PlanElementName, value: String? = null):
            this(ValidationErrorData(VALIDATION_PROFILE, key, type), profileName, viName, value)
}

data class CantError(
    @JsonIgnore private val data: ValidationErrorData,
    val cantName: PlanElementName,
    val station: BigDecimal,
    val value: String?,
) : ValidationError by data {
    constructor(key: String, type: ErrorType, cantName: PlanElementName, station: BigDecimal, value: String? = null):
            this(ValidationErrorData(VALIDATION_CANT, key, type), cantName, station, value)
}

data class KmPostError(
    @JsonIgnore private val data: ValidationErrorData,
    val kmPostName: PlanElementName,
    val value: String?,
) : ValidationError by data {
    constructor(key: String, type: ErrorType, kmPostName: PlanElementName, value: String? = null):
            this(ValidationErrorData(VALIDATION_KM_POST, key, type), kmPostName, value)
}

private const val COORDINATE_DELTA = 0.1
private const val ACCURATE_COORDINATE_DELTA = 0.001

private const val LENGTH_DELTA = 0.1
private const val ACCURATE_LENGTH_DELTA = 0.001

private const val RADIUS_DELTA = 0.1
private const val ACCURATE_RADIUS_DELTA = 0.001

private const val ELEMENT_DIRECTION_DELTA = 0.1
private const val ACCURATE_ELEMENT_DIRECTION_DELTA = 0.001

private const val CONSTANT_A_DELTA = 0.001
private const val ACCURATE_CONSTANT_A_DELTA = 0.00001

private const val MAX_PROFILE_SLOPE_DEGREES = 45.0

private const val JOINT_LOCATION_DELTA = 0.01
private const val ACCURATE_JOINT_LOCATION_DELTA = 0.002

private const val MINIMUM_TURN_RADIUS = 180.0

val FINNISH_RAIL_GAUGE = BigDecimal("1.524")

fun validate(
    plan: GeometryPlan,
    featureTypes: List<FeatureType>,
    switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<ValidationError> {
    return validateMetadata(plan) +
            validateAlignments(plan.alignments, featureTypes) +
            validateSwitches(plan.switches, plan.alignments, switchStructures) +
            validateKmPosts(plan.kmPosts)
}

fun validateMetadata(plan: GeometryPlan): List<ValidationError> = listOfNotNull<ValidationError>(
    validate(plan.units.coordinateSystemSrid != null) {
        val key =
            if (plan.units.coordinateSystemName == null) "coordinate-system-missing"
            else "coordinate-system-unsupported"
        MetadataError(key, VALIDATION_ERROR, plan.units.coordinateSystemName?.toString())
    },
    validate(plan.units.verticalCoordinateSystem != null || plan.alignments.all { a -> a.profile == null }) {
        MetadataError("vertical-coordinate-system-missing", VALIDATION_ERROR)
    },
    validate(plan.trackNumberId != null) {
        MetadataError("track-number-missing", OBSERVATION_MAJOR)
    },
    validate(plan.planTime != null) {
        MetadataError("plan-time-missing", OBSERVATION_MINOR)
    },
    validate(plan.author != null) {
        MetadataError("author-missing", OBSERVATION_MINOR)
    },
    validate(plan.alignments.any { alignment -> alignment.featureTypeCode == REFERENCE_LINE_TYPE_CODE }) {
        MetadataError("reference-line-missing", OBSERVATION_MAJOR)
    },
    validate(plan.kmPosts.isNotEmpty()) {
        MetadataError("km-posts-missing", OBSERVATION_MAJOR)
    },
)

fun validateAlignments(
    alignments: List<GeometryAlignment>,
    featureTypes: List<FeatureType>,
): List<ValidationError> {
    val duplicateNames = alignments.mapNotNull { alignment ->
        if (alignments.any { other -> other.id != alignment.id && other.name == alignment.name }) {
            alignment.name
        } else null
    }.toSet()
    val duplicateErrors = duplicateNames.map { name -> AlignmentError("duplicate-name", OBSERVATION_MAJOR, name) }

    val alignmentErrors = alignments.flatMap { alignment -> validateAlignment(alignment, featureTypes) }

    return duplicateErrors + alignmentErrors
}

fun validateSwitches(
    switches: List<GeometrySwitch>,
    alignments: List<GeometryAlignment>,
    switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<ValidationError> {
    val duplicateNames = switches.mapNotNull { switch ->
        if (switches.any { other -> other.id != switch.id && other.name == switch.name }) {
            switch.name
        } else null
    }.toSet()
    val duplicateErrors = duplicateNames.map { name ->
        SwitchDefinitionError("duplicate-name", OBSERVATION_MAJOR, name)
    }
    val switchErrors = switches.flatMap { switch ->
        validateSwitch(
            switch,
            switch.switchStructureId?.let(switchStructures::get),
            alignments.mapNotNull { a -> collectAlignmentSwitchJoints(switch.id, a) },
        )
    }
    return duplicateErrors + switchErrors
}

fun validateKmPosts(kmPosts: List<GeometryKmPost>): List<ValidationError> = kmPosts.flatMapIndexed { i, p ->
    // Don't validate 1st km-post as it's just a 0-point with different data
    if (i > 0) validateKmPost(p) else listOf()
}

fun validateKmPost(post: GeometryKmPost) = listOfNotNull(
    validate(post.location != null) {
        KmPostError("location-missing", OBSERVATION_MAJOR, post.description)
    },
    validate(post.kmNumber != null) {
        KmPostError("km-number-incorrect", OBSERVATION_MINOR, post.description)
    },
)

fun validateAlignmentGeometry(alignment: GeometryAlignment): List<ValidationError> {
    return validatePieces(alignment.name, alignment.elements, ::validateElement, ::validateElementVsPrevious)
}

fun validateAlignmentProfile(alignment: GeometryAlignment): List<ValidationError> {
    return alignment.profile?.let { profile ->
        val intersectionErrors = validatePieces(
            profile.name,
            profile.elements,
            ::validateIntersection,
            ::validateIntersectionVsPrevious,
        )
        val segmentErrors = validatePieces(
            profile.name,
            profile.segments,
            ::validateProfileSegment,
            ::validateProfileSegmentVsPrevious,
        )
        intersectionErrors + segmentErrors
    } ?: listOf(AlignmentError("no-profile", OBSERVATION_MAJOR, alignment.name))
}

fun validateAlignmentCant(alignment: GeometryAlignment): List<ValidationError> {
    return alignment.cant?.let { cant ->
        val cantErrors = listOfNotNull(
            validate(cant.rotationPoint != CENTER) {
                AlignmentError("cant-rotation-point-center", VALIDATION_ERROR, alignment.name)
            },
            validate(cant.gauge == FINNISH_RAIL_GAUGE) {
                AlignmentError("cant-gauge-invalid", OBSERVATION_MAJOR, alignment.name, value = cant.gauge.toString())
            },
        )
        val pointErrors = validatePieces(
            parentName = cant.name,
            pieces = cant.points,
            itemValidator = { cantName, cp -> validateCantPoint(cantName, cp, cant.gauge) },
            itemVsPreviousValidator = ::validateCantPointVsPrevious,
        )
        cantErrors + pointErrors
    } ?: listOf(AlignmentError("no-cant", OBSERVATION_MAJOR, alignment.name))
}

fun validateAlignment(alignment: GeometryAlignment, featureTypes: List<FeatureType>): List<ValidationError> {
    val typeCode = alignment.featureTypeCode
    val alignmentErrors = listOfNotNull(
        validate(typeCode != null) {
            AlignmentError("no-feature-type", OBSERVATION_MAJOR, alignment.name)
        },
        validate(typeCode == null || featureTypes.any { ft -> ft.code == typeCode }) {
            AlignmentError("unknown-feature-type", OBSERVATION_MAJOR, alignment.name, typeCode?.toString())
        },
        // TypeCode 121 is for road center-line. Should use 281 instead for tracks.
        validate(typeCode == null || typeCode != FeatureTypeCode("121")) {
            AlignmentError("wrong-feature-type", OBSERVATION_MINOR, alignment.name, value = typeCode?.toString())
        },
        validate(alignment.state != null) {
            AlignmentError("no-state", OBSERVATION_MINOR, alignment.name)
        },
    )
    return alignmentErrors +
            validateAlignmentGeometry(alignment) +
            validateAlignmentProfile(alignment) +
            validateAlignmentCant(alignment)
}

private fun validateElement(alignmentName: AlignmentName, element: GeometryElement): List<ElementError> {

    val lengthDelta = abs(element.length.toDouble() - element.calculatedLength)
    val fieldErrors = listOfNotNull(
        validate(element.length > BigDecimal.ZERO) {
            ElementError(
                key = "field-invalid-length",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = element,
                value = element.length.toString(),
            )
        },
        validate(element.length <= BigDecimal.ZERO || lengthDelta < ACCURATE_LENGTH_DELTA) {
            val isIncorrect = lengthDelta > LENGTH_DELTA
            ElementError(
                key = if (isIncorrect) "field-incorrect-length" else "field-inaccurate-length",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = element,
                value = "${element.length} <> ${round(element.calculatedLength, element.length.scale())}"
            )
        },
    )

    val calculatedStart = element.getCoordinateAt(0.0)
    val calculatedEnd = element.getCoordinateAt(element.calculatedLength)
    val endPointErrors = listOfNotNull(
        validate(!element.start.isSame(element.end, ACCURATE_COORDINATE_DELTA)) {
            ElementError(
                key = "start-end-same",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = element,
            )
        },
        validate(calculatedStart.isSame(element.start, ACCURATE_COORDINATE_DELTA)) {
            val isIncorrect = !calculatedStart.isSame(element.start, COORDINATE_DELTA)
            ElementError(
                key = if (isIncorrect) "incorrect-start-point" else "inaccurate-start-point",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = element,
                value = roundTo3Decimals(lineLength(element.start, calculatedStart)).toString(),
            )
        },
        validate(calculatedEnd.isSame(element.end, ACCURATE_COORDINATE_DELTA)) {
            val isIncorrect = !calculatedEnd.isSame(element.end, COORDINATE_DELTA)
            ElementError(
                key = if (isIncorrect) "incorrect-end-point" else "inaccurate-end-point",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = element,
                value = roundTo3Decimals(lineLength(element.end, calculatedEnd)).toString(),
            )
        },
    )

    val typeErrors = when (element) {
        is GeometryLine -> listOf()
        is GeometryCurve -> validateCurve(alignmentName, element)
        is GeometryClothoid -> validateSpiral(alignmentName, element) + validateClothoid(alignmentName, element)
        is BiquadraticParabola -> validateSpiral(alignmentName, element)
    }

    return fieldErrors + endPointErrors + typeErrors
}

private fun validateElementVsPrevious(
    alignmentName: AlignmentName,
    element: GeometryElement,
    previous: GeometryElement,
): List<ElementError> {
    val directionDiff = angleDiffRads(element.startDirectionRads, previous.endDirectionRads)
    return listOfNotNull(
        validate(element.start.isSame(previous.end, ACCURATE_COORDINATE_DELTA)) {
            val isIncorrect = !element.start.isSame(previous.end, COORDINATE_DELTA)
            ElementError(
                key = if (isIncorrect) "coordinates-not-continuous" else "coordinates-inaccurate",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = element,
                value = roundTo3Decimals(lineLength(element.start, previous.end)).toString(),
            )
        },
        validate(directionDiff <= ACCURATE_ELEMENT_DIRECTION_DELTA) {
            val isIncorrect = directionDiff > ELEMENT_DIRECTION_DELTA
            ElementError(
                key = if (isIncorrect) "directions-not-continuous" else "directions-inaccurate",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = element,
                value = "${roundTo3Decimals(previous.endDirectionRads)} <> ${roundTo3Decimals(element.startDirectionRads)}",
            )
        },
        validate(element.staStart > previous.staStart) {
            ElementError(
                key = "station-not-increasing",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = element,
                value = "${previous.staStart} >= ${element.staStart}",
            )
        },
    )
}

private fun validateCurve(alignmentName: AlignmentName, curve: GeometryCurve): List<ElementError> {
    val startRadiusDiff = abs(lineLength(curve.center, curve.start) - curve.radius.toDouble())
    val endRadiusDiff = abs(lineLength(curve.center, curve.start) - curve.radius.toDouble())
    val chordDiff = abs(lineLength(curve.start, curve.end) - curve.chord.toDouble())

    return listOfNotNull(
        validate(startRadiusDiff <= ACCURATE_RADIUS_DELTA) {
            val isIncorrect = startRadiusDiff > RADIUS_DELTA
            ElementError(
                key = if (isIncorrect) "curve-radius-incorrect-start" else "curve-radius-inaccurate-start",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = curve,
                value = roundTo3Decimals(startRadiusDiff).toString(),
            )
        },
        validate(endRadiusDiff <= ACCURATE_RADIUS_DELTA) {
            val isIncorrect = endRadiusDiff > RADIUS_DELTA
            ElementError(
                key = if (isIncorrect) "incorrect-curve-radius-no-match-end" else "inaccurate-curve-radius-end",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = curve,
                value = roundTo3Decimals(endRadiusDiff).toString(),
            )
        },
        validate(chordDiff <= ACCURATE_LENGTH_DELTA) {
            val isIncorrect = chordDiff > LENGTH_DELTA
            ElementError(
                key = if (isIncorrect) "curve-chord-incorrect" else "curve-chord-inaccurate",
                type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                alignmentName = alignmentName,
                element = curve,
            )
        },
        validate(curve.radius.toDouble() >= MINIMUM_TURN_RADIUS) {
            ElementError(
                key = "curve-steep",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = curve,
                value = curve.radius.toString(),
            )
        },
    )
}

private fun validateSpiral(alignmentName: AlignmentName, spiral: GeometrySpiral): List<ElementError> {
    val startRadius = spiral.radiusStart
    val endRadius = spiral.radiusEnd
    return listOfNotNull(
        validate(startRadius == null || startRadius.toDouble() >= MINIMUM_TURN_RADIUS) {
            ElementError(
                key = "spiral-start-steep",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = spiral,
                value = spiral.radiusStart.toString(),
            )
        },
        validate(endRadius == null || endRadius.toDouble() >= MINIMUM_TURN_RADIUS) {
            ElementError(
                key = "spiral-end-steep",
                type = OBSERVATION_MAJOR,
                alignmentName = alignmentName,
                element = spiral,
                value = spiral.radiusEnd.toString(),
            )
        },
    )
}

private fun validateClothoid(alignmentName: AlignmentName, clothoid: GeometryClothoid): List<ElementError> {
    val calculatedConstant =
        if (clothoid.radiusStart != null) {
            sqrt(clothoid.radiusStart.toDouble() * clothoid.segmentToClothoidDistance(0.0))
        } else if (clothoid.radiusEnd != null) {
            sqrt(clothoid.radiusEnd.toDouble() * clothoid.segmentToClothoidDistance(clothoid.length.toDouble()))
        } else null

    return listOfNotNull(
        calculatedConstant?.let { calculated ->
            val constantDiff = abs(calculated - clothoid.constant.toDouble())
            validate(constantDiff <= ACCURATE_CONSTANT_A_DELTA) {
                val isIncorrect = constantDiff > CONSTANT_A_DELTA
                ElementError(
                    key = if (isIncorrect) "clothoid-incorrect-constant" else "clothoid-inaccurate-constant",
                    type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    alignmentName = alignmentName,
                    element = clothoid,
                    value = round(constantDiff, 6).toString(),
                )
            }
        },
    )
}

private fun validateIntersection(profileName: PlanElementName, intersection: VerticalIntersection): List<ProfileError> {
    return if (intersection is VICircularCurve) {
        listOfNotNull(
            validate(intersection.length != null) {
                ProfileError(
                    key = "curve-length-missing",
                    type = OBSERVATION_MAJOR,
                    profileName = profileName,
                    viName = intersection.description,
                )
            },
            validate(intersection.radius != null) {
                ProfileError(
                    key = "curve-radius-missing",
                    type = OBSERVATION_MAJOR,
                    profileName = profileName,
                    viName = intersection.description,
                )
            },
        )
    } else listOf()
}

private fun validateIntersectionVsPrevious(
    profileName: PlanElementName,
    intersection: VerticalIntersection,
    previous: VerticalIntersection,
) : List<ProfileError> {
    val deltaX = intersection.point.x - previous.point.x
    val deltaY = intersection.point.y - previous.point.y
    val profileAngle = if (deltaX > 0) radsToDegrees(sin(deltaY/deltaX)) else null
    return listOfNotNull(
        validate(deltaX > 0) {
            ProfileError("incorrect-station", OBSERVATION_MAJOR, profileName, intersection.description)
        },
        profileAngle?.let { angle ->
            validate(abs(angle) <= MAX_PROFILE_SLOPE_DEGREES) {
                ProfileError(
                    key = "incorrect-slope",
                    type = OBSERVATION_MAJOR,
                    profileName = profileName,
                    viName = intersection.description,
                    value = round(angle, 1).toString(),
                )
            }
        },
    )
}

private fun validateProfileSegment(profileName: PlanElementName, segment: ProfileSegment): List<ProfileError> {
    return listOfNotNull(
        validate(segment !is LinearProfileSegment || segment.valid) {
            ProfileError(
                key = "calculation-failed",
                type = OBSERVATION_MAJOR,
                profileName = profileName,
                viName = segment.viName,
                value = "${segment.start.x}-${segment.end.x}",
            )
        },
    )
}

private fun validateProfileSegmentVsPrevious(
    profileName: PlanElementName,
    segment: ProfileSegment,
    previous: ProfileSegment,
) : List<ProfileError> {
    val segmentValid = segment is LinearProfileSegment && !segment.valid
    val previousValid = previous is LinearProfileSegment && !previous.valid
    return listOfNotNull(
        validate(abs(segment.start.x - previous.end.x) <= 0.0001) {
            ProfileError(
                key = "segment-station-not-continuous",
                type = OBSERVATION_MAJOR,
                profileName = profileName,
                viName = segment.viName,
                value = "${roundTo3Decimals(previous.end.x)}<>${roundTo3Decimals(segment.start.x)}",
            )
        },
        validate(abs(segment.start.y - previous.end.y) <= 0.0001) {
            ProfileError(
                key = "segment-height-not-continuous",
                type = OBSERVATION_MAJOR,
                profileName = profileName,
                viName = segment.viName,
                value = "${roundTo3Decimals(previous.end.y)}<>${roundTo3Decimals(segment.start.y)}",
            )
        },
        validate(!segmentValid || !previousValid || abs(segment.startAngle - previous.endAngle) <= 0.0001) {
            ProfileError(
                key = "segment-angle-not-continuous",
                type = OBSERVATION_MAJOR,
                profileName = profileName,
                viName = segment.viName,
                value = "${roundTo3Decimals(radsToDegrees(previous.endAngle))}<>${roundTo3Decimals(radsToDegrees(segment.startAngle))}",
            )
        },
    )
}

private fun validateCantPoint(
    cantName: PlanElementName,
    cantPoint: GeometryCantPoint,
    gauge: BigDecimal
): List<CantError> {
    return listOfNotNull(
        validate (cantPoint.appliedCant.toDouble() in 0.0..gauge.toDouble()) {
            CantError(
                key = "value-incorrect",
                type = OBSERVATION_MAJOR,
                cantName = cantName,
                station = cantPoint.station,
                value = cantPoint.appliedCant.toString(),
            )
        },
    )
}


private fun validateCantPointVsPrevious(
    cantName: PlanElementName,
    cantPoint: GeometryCantPoint,
    previous: GeometryCantPoint,
): List<CantError> {
    return listOfNotNull(
        validate(cantPoint.station > previous.station) {
            CantError("station-not-continuous", OBSERVATION_MAJOR, cantName, cantPoint.station)
        },
    )
}


fun validateSwitch(
    switch: GeometrySwitch,
    structure: SwitchStructure?,
    alignmentSwitches: List<AlignmentSwitch>,
): List<ValidationError> {
    val jointNumbers = switch.joints.map(GeometrySwitchJoint::number)
    val structureJointNumbers = structure?.joints?.map(SwitchJoint::number) ?: listOf()

    val fieldErrors = listOfNotNull(
        validate(structure != null) {
            SwitchDefinitionError("type-unrecognized", OBSERVATION_MAJOR, switch.name, switch.typeName)
        },
        validate(structure == null || jointNumbers.all(structureJointNumbers::contains)) {
            SwitchDefinitionError(
                key = "incorrect-joints",
                switchName = switch.name,
                jointNumbers = jointNumbers,
                structureJointNumbers = structureJointNumbers,
                type = OBSERVATION_MAJOR,
            )
        },
    )

    val geometryErrors =
        if (structure != null) validateSwitchGeometry(switch, structure)
        else listOf()

    val alignmentErrors =
        if (structure != null) validateSwitchAlignments(switch, structure, alignmentSwitches)
        else listOf()

    return fieldErrors + geometryErrors + alignmentErrors
}

fun validateSwitchGeometry(
    switch: GeometrySwitch,
    switchStructure: SwitchStructure,
): List<SwitchDefinitionError> {
    val joints: List<GeometrySwitchJoint> = switch.joints
    val positionTransformation =
        if (joints.size > 1) getSwitchPositionTransformation(switchStructure, joints)
        else null
    return if (positionTransformation == null) {
        listOfNotNull(
            validate(joints.size <= 1) {
                SwitchDefinitionError("location-difference", OBSERVATION_MAJOR, switch.name)
            }
        )
    } else {
        val locationPairs = joints.mapNotNull { joint ->
            switchStructure.joints
                .find { structureJoint -> structureJoint.number == joint.number }
                ?.let { structureJoint -> transformSwitchPoint(positionTransformation, structureJoint.location) }
                ?.let { calculatedLocation -> joint.location to calculatedLocation }
        }
        listOfNotNull(
            validate(locationPairs.all { (loc, calc) -> loc.isSame(calc, ACCURATE_JOINT_LOCATION_DELTA) }) {
                val isIncorrect = locationPairs.any { (loc, calc) -> !loc.isSame(calc, JOINT_LOCATION_DELTA) }
                SwitchDefinitionError(
                    key = if (isIncorrect) "incorrect-joint-locations" else "inaccurate-joint-locations",
                    type = if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    switchName = switch.name,
                )
            }
        )
    }
}

fun validateSwitchAlignments(
    switch: GeometrySwitch,
    switchStructure: SwitchStructure,
    alignmentSwitches: List<AlignmentSwitch>,
): List<SwitchDefinitionError> {
    val joints: List<GeometrySwitchJoint> = switch.joints
    return alignmentSwitches.flatMap { alignmentSwitch ->
        val structureAlignment = switchStructure.alignments.find { sa ->
            sa.jointNumbers.containsAll(sa.jointNumbers)
        }
        val incorrectJoints = mutableListOf<JointNumber>()
        val inaccurateJoints = mutableListOf<JointNumber>()
        alignmentSwitch.joints.forEach { asj ->
            joints.find { j -> j.number == asj.number }?.also { joint ->
                if (!asj.location.isSame(joint.location, JOINT_LOCATION_DELTA)) {
                    incorrectJoints.add(asj.number)
                } else if (!asj.location.isSame(joint.location, ACCURATE_JOINT_LOCATION_DELTA)) {
                    inaccurateJoints.add(asj.number)
                }
            }
        }

        listOfNotNull(
            validate(structureAlignment != null) {
                SwitchDefinitionError(
                    key = "no-structure-alignment",
                    type = OBSERVATION_MAJOR,
                    switchName = switch.name,
                    switchType = switch.typeName,
                    jointNumbers = alignmentSwitch.jointNumbers,
                    alignmentName = alignmentSwitch.alignment.name,
                )
            },
            validate(incorrectJoints.isEmpty()) {
                SwitchDefinitionError(
                    key = "alignment-joint-mismatch",
                    type = OBSERVATION_MAJOR,
                    switchName = switch.name,
                    switchType = switch.typeName,
                    jointNumbers = incorrectJoints,
                    alignmentName = alignmentSwitch.alignment.name,
                )
            },
            validate(inaccurateJoints.isEmpty()) {
                SwitchDefinitionError(
                    key = "alignment-joint-inaccurate",
                    type = OBSERVATION_MINOR,
                    switchName = switch.name,
                    switchType = switch.typeName,
                    jointNumbers = inaccurateJoints,
                    alignmentName = alignmentSwitch.alignment.name,
                )
            },
        )
    }
}

fun getSwitchPositionTransformation(switchStructure: SwitchStructure, joints: List<GeometrySwitchJoint>) =
    try {
        calculateSwitchLocationDelta(joints, switchStructure)
    } catch (e: Exception) {
        null
    }

fun collectAlignmentSwitchJoints(
    switchId: DomainId<GeometrySwitch>,
    alignment: GeometryAlignment,
): AlignmentSwitch? {
    return alignment.elements
        .filter { e -> e.switchId == switchId }
        .flatMap { e ->
            listOfNotNull(
                e.startJointNumber?.let { n -> AlignmentSwitchJoint(n, e.start) },
                e.endJointNumber?.let { n -> AlignmentSwitchJoint(n, e.end) },
            )
        }
        .let { joints -> if (joints.isEmpty()) null else AlignmentSwitch(alignment, joints) }
}

data class AlignmentSwitch(val alignment: GeometryAlignment, val joints: List<AlignmentSwitchJoint>) {
    val jointNumbers = joints
        .filterIndexed { i, j -> j.number.intValue != 0 && (i == 0 || joints[i].number != j.number) }
        .map(AlignmentSwitchJoint::number)
}
data class AlignmentSwitchJoint(val number: JointNumber, val location: Point)

private fun <T: ValidationError> validate(check: Boolean, lazyError: () -> T): T? =
    if (check) null
    else lazyError()

/**
 * Validate a list of items, using one function to check the items themselves and another to check them versus the
 * previous one for consistency
 */
private fun <N : CharSequence, T : Any, E: ValidationError> validatePieces(
    parentName: N,
    pieces: List<T>,
    itemValidator: (N, T) -> List<E>,
    itemVsPreviousValidator: (N, T, T) -> List<E> = { _, _, _ -> listOf() },
): List<ValidationError> {
    return pieces.flatMapIndexed { index, item ->
        val pointErrors = itemValidator(parentName, item)
        val previous = pieces.getOrNull(index - 1)
        val consistencyErrors = previous?.let { p -> itemVsPreviousValidator(parentName, item, p) } ?: listOf()
        (pointErrors + consistencyErrors)
    }
}
