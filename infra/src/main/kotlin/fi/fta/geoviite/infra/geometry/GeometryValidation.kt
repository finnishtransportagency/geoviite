package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.codeDictionary.FeatureType
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.CantRotationPoint.CENTER
import fi.fta.geoviite.infra.geometry.GeometryIssueType.OBSERVATION_MAJOR
import fi.fta.geoviite.infra.geometry.GeometryIssueType.OBSERVATION_MINOR
import fi.fta.geoviite.infra.geometry.GeometryIssueType.VALIDATION_ERROR
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.calculateSwitchLocationDelta
import fi.fta.geoviite.infra.switchLibrary.transformSwitchPoint
import fi.fta.geoviite.infra.tracklayout.REFERENCE_LINE_TYPE_CODE
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

enum class GeometryIssueType {
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

val trackTypeCodes = listOf(FeatureTypeCode("281"), FeatureTypeCode("111"))

data class GeometryValidationIssue(
    val localizationKey: LocalizationKey,
    val issueType: GeometryIssueType,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        fun of(
            parentKey: String,
            errorKey: String,
            issueType: GeometryIssueType,
            params: Map<String, String> = emptyMap(),
        ): GeometryValidationIssue =
            GeometryValidationIssue(
                localizationKey = LocalizationKey.of("$VALIDATION.$parentKey.$errorKey"),
                issueType = issueType,
                params = params,
            )
    }
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
    officialTrackNumbers: List<TrackNumber>,
): List<GeometryValidationIssue> {
    return validateMetadata(plan, officialTrackNumbers) +
        validateAlignments(plan.alignments, featureTypes) +
        validateSwitches(plan.switches, plan.alignments, switchStructures) +
        validateKmPosts(plan.kmPosts)
}

fun validateMetadata(plan: GeometryPlan, officialTrackNumbers: List<TrackNumber>): List<GeometryValidationIssue> =
    listOfNotNull(
        validate(plan.units.coordinateSystemSrid != null) {
            val key =
                if (plan.units.coordinateSystemName == null) "coordinate-system-missing"
                else "coordinate-system-unsupported"
            GeometryValidationIssue.of(
                "metadata",
                key,
                VALIDATION_ERROR,
                buildMap { plan.units.coordinateSystemName?.toString()?.let { put("value", it) } },
            )
        },
        validate(plan.units.verticalCoordinateSystem != null || plan.alignments.all { a -> a.profile == null }) {
            GeometryValidationIssue.of("metadata", "vertical-coordinate-system-missing", VALIDATION_ERROR)
        },
        validate(plan.trackNumber != null) {
            GeometryValidationIssue.of("metadata", "track-number-missing", OBSERVATION_MAJOR)
        },
        validate(plan.trackNumber == null || officialTrackNumbers.contains(plan.trackNumber)) {
            GeometryValidationIssue.of(
                "metadata",
                "track-number-not-found",
                OBSERVATION_MAJOR,
                buildMap { plan.trackNumber?.toString()?.let { put("value", it) } },
            )
        },
        validate(plan.planTime != null) {
            GeometryValidationIssue.of("metadata", "plan-time-missing", OBSERVATION_MINOR)
        },
        validate(plan.author != null) { GeometryValidationIssue.of("metadata", "author-missing", OBSERVATION_MINOR) },
        validate(plan.kmPosts.isNotEmpty()) {
            GeometryValidationIssue.of("metadata", "km-posts-missing", OBSERVATION_MAJOR)
        },
    )

fun validateAlignments(
    alignments: List<GeometryAlignment>,
    featureTypes: List<FeatureType>,
): List<GeometryValidationIssue> {
    val duplicateNames =
        alignments
            .mapNotNull { alignment ->
                if (alignments.any { other -> other.id != alignment.id && other.name == alignment.name }) {
                    alignment.name
                } else null
            }
            .toSet()
    val duplicateErrors = duplicateNames.map { name ->
        GeometryValidationIssue.of(
            "alignment",
            "duplicate-name",
            OBSERVATION_MAJOR,
            mapOf("alignmentName" to name.toString()),
        )
    }

    val alignmentErrors = alignments.flatMap { alignment -> validateAlignment(alignment, featureTypes) }

    val alignmentCollectionErrors = validateAlignmentCollection(alignments)

    return duplicateErrors + alignmentErrors + alignmentCollectionErrors
}

fun validateSwitches(
    switches: List<GeometrySwitch>,
    alignments: List<GeometryAlignment>,
    switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<GeometryValidationIssue> {
    val duplicateNames =
        switches
            .mapNotNull { switch ->
                if (switches.any { other -> other.id != switch.id && other.name == switch.name }) {
                    switch.name
                } else null
            }
            .toSet()
    val duplicateErrors = duplicateNames.map { name ->
        GeometryValidationIssue.of(
            "switch",
            "duplicate-name",
            OBSERVATION_MAJOR,
            mapOf("switchName" to name.toString()),
        )
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

fun validateKmPosts(kmPosts: List<GeometryKmPost>): List<GeometryValidationIssue> {
    val singularKmPostsValidations = kmPosts.flatMapIndexed { i, p ->
        // Don't validate 1st km-post as it's just a 0-point with different data
        if (i > 0) validateKmPost(p) else listOf()
    }

    return singularKmPostsValidations + validateKmPostCollection(kmPosts)
}

private fun validateKmPostCollection(kmPosts: List<GeometryKmPost>): List<GeometryValidationIssue> {
    val groupedKmPosts = kmPosts.filter { it.kmNumber != null }.sortedBy { it.kmNumber }
    val firstKmPost = groupedKmPosts.firstOrNull()
    val duplicateKmPosts = groupedKmPosts.groupBy { it.kmNumber }.filter { it.value.size > 1 }.keys

    val generalErrors =
        listOfNotNull(
            validate(duplicateKmPosts.isEmpty()) {
                GeometryValidationIssue.of(
                    "km-post",
                    "duplicate-km-posts",
                    VALIDATION_ERROR,
                    buildMap { put("value", duplicateKmPosts.joinToString(", ") { kmPost -> kmPost.toString() }) },
                )
            },
            validate(firstKmPost != null && firstKmPost.staAhead <= BigDecimal.ZERO) {
                GeometryValidationIssue.of(
                    "km-post",
                    "sta-ahead-not-negative",
                    VALIDATION_ERROR,
                    buildMap { firstKmPost?.staAhead?.toString()?.let { put("value", it) } },
                )
            },
        )
    return generalErrors
}

fun validateKmPost(post: GeometryKmPost) =
    listOfNotNull(
        validate(post.location != null) {
            GeometryValidationIssue.of(
                "km-post",
                "location-missing",
                OBSERVATION_MAJOR,
                mapOf("kmPostName" to post.description.toString()),
            )
        },
        validate(post.kmNumber != null) {
            GeometryValidationIssue.of(
                "km-post",
                "km-number-incorrect",
                OBSERVATION_MINOR,
                mapOf("kmPostName" to post.description.toString()),
            )
        },
    )

fun validateAlignmentCollection(alignments: List<GeometryAlignment>): List<GeometryValidationIssue> {
    val referenceLineAlignments = alignments.filter { alignment ->
        alignment.featureTypeCode == REFERENCE_LINE_TYPE_CODE
    }
    return listOfNotNull(
        validate(referenceLineAlignments.isNotEmpty()) {
            GeometryValidationIssue.of("alignment", "no-reference-lines", OBSERVATION_MAJOR)
        },
        validate(referenceLineAlignments.size <= 1) {
            GeometryValidationIssue.of("alignment", "multiple-reference-lines", VALIDATION_ERROR)
        },
    )
}

fun validateAlignmentGeometry(alignment: GeometryAlignment): List<GeometryValidationIssue> {
    return validatePieces(alignment.name, alignment.elements, ::validateElement, ::validateElementVsPrevious)
}

fun validateAlignmentProfile(alignment: GeometryAlignment): List<GeometryValidationIssue> {
    return alignment.profile?.let { profile ->
        val intersectionErrors =
            validatePieces(profile.name, profile.elements, ::validateIntersection, ::validateIntersectionVsPrevious)
        val segmentErrors =
            validatePieces(profile.name, profile.segments, ::validateProfileSegment, ::validateProfileSegmentVsPrevious)
        intersectionErrors + segmentErrors
    }
        ?: listOf(
            GeometryValidationIssue.of(
                "alignment",
                "no-profile",
                OBSERVATION_MAJOR,
                mapOf("alignmentName" to alignment.name.toString()),
            )
        )
}

fun validateAlignmentCant(alignment: GeometryAlignment): List<GeometryValidationIssue> {
    return alignment.cant?.let { cant ->
        val cantErrors =
            listOfNotNull(
                validate(cant.rotationPoint != null || alignment.featureTypeCode == REFERENCE_LINE_TYPE_CODE) {
                    GeometryValidationIssue.of(
                        "alignment",
                        "cant-rotation-point-undefined",
                        VALIDATION_ERROR,
                        mapOf("alignmentName" to alignment.name.toString()),
                    )
                },
                validate(cant.rotationPoint != CENTER) {
                    GeometryValidationIssue.of(
                        "alignment",
                        "cant-rotation-point-center",
                        VALIDATION_ERROR,
                        mapOf("alignmentName" to alignment.name.toString()),
                    )
                },
                validate(cant.gauge == FINNISH_RAIL_GAUGE) {
                    GeometryValidationIssue.of(
                        "alignment",
                        "cant-gauge-invalid",
                        OBSERVATION_MAJOR,
                        buildMap {
                            put("alignmentName", alignment.name.toString())
                            put("value", cant.gauge.toString())
                        },
                    )
                },
            )
        val pointErrors =
            validatePieces(
                parentName = cant.name,
                pieces = cant.points,
                itemValidator = { cantName, cp -> validateCantPoint(cantName, cp, cant.gauge) },
                itemVsPreviousValidator = ::validateCantPointVsPrevious,
            )
        cantErrors + pointErrors
    }
        ?: listOf(
            GeometryValidationIssue.of(
                "alignment",
                "no-cant",
                OBSERVATION_MAJOR,
                mapOf("alignmentName" to alignment.name.toString()),
            )
        )
}

fun validateAlignment(alignment: GeometryAlignment, featureTypes: List<FeatureType>): List<GeometryValidationIssue> {
    val typeCode = alignment.featureTypeCode
    val type = typeCode?.let { c -> featureTypes.find { ft -> ft.code == c } }
    val typeCodeError =
        if (typeCode == null) {
            GeometryValidationIssue.of(
                "alignment",
                "no-feature-type",
                OBSERVATION_MAJOR,
                mapOf("alignmentName" to alignment.name.toString()),
            )
        } else if (type == null) {
            GeometryValidationIssue.of(
                "alignment",
                "unknown-feature-type",
                OBSERVATION_MAJOR,
                buildMap {
                    put("alignmentName", alignment.name.toString())
                    put("value", typeCode.toString())
                },
            )
        } else if (type.code !in trackTypeCodes) {
            GeometryValidationIssue.of(
                "alignment",
                "wrong-feature-type",
                OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignment.name.toString())
                    put("value", "${type.code} (${type.description})")
                },
            )
        } else {
            null
        }
    val alignmentIssues =
        listOfNotNull(
            typeCodeError,
            validate(alignment.state != null) {
                GeometryValidationIssue.of(
                    "alignment",
                    "no-state",
                    OBSERVATION_MINOR,
                    mapOf("alignmentName" to alignment.name.toString()),
                )
            },
        )
    return alignmentIssues +
        validateAlignmentGeometry(alignment) +
        validateAlignmentProfile(alignment) +
        validateAlignmentCant(alignment)
}

private fun validateElement(alignmentName: AlignmentName, element: GeometryElement): List<GeometryValidationIssue> {
    val lengthDelta = abs(element.length.toDouble() - element.calculatedLength)
    val fieldErrors =
        listOfNotNull(
            validate(element.length > BigDecimal.ZERO) {
                GeometryValidationIssue.of(
                    "element",
                    "field-invalid-length",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", element.type.name)
                        put("value", element.length.toString())
                    },
                )
            },
            validate(element.length <= BigDecimal.ZERO || lengthDelta < ACCURATE_LENGTH_DELTA) {
                val isIncorrect = lengthDelta > LENGTH_DELTA
                GeometryValidationIssue.of(
                    "element",
                    if (isIncorrect) "field-incorrect-length" else "field-inaccurate-length",
                    if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", element.type.name)
                        put("value", "${element.length} <> ${round(element.calculatedLength, element.length.scale())}")
                    },
                )
            },
        )

    val calculatedStart = element.getCoordinateAt(0.0)
    val calculatedEnd = element.getCoordinateAt(element.calculatedLength)
    val endPointErrors =
        listOfNotNull(
            validate(!element.start.isSame(element.end, ACCURATE_COORDINATE_DELTA)) {
                GeometryValidationIssue.of(
                    "element",
                    "start-end-same",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", element.type.name)
                    },
                )
            },
            validate(calculatedStart.isSame(element.start, ACCURATE_COORDINATE_DELTA)) {
                val isIncorrect = !calculatedStart.isSame(element.start, COORDINATE_DELTA)
                GeometryValidationIssue.of(
                    "element",
                    if (isIncorrect) "incorrect-start-point" else "inaccurate-start-point",
                    if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", element.type.name)
                        put("value", roundTo3Decimals(lineLength(element.start, calculatedStart)).toString())
                    },
                )
            },
            validate(calculatedEnd.isSame(element.end, ACCURATE_COORDINATE_DELTA)) {
                val isIncorrect = !calculatedEnd.isSame(element.end, COORDINATE_DELTA)
                GeometryValidationIssue.of(
                    "element",
                    if (isIncorrect) "incorrect-end-point" else "inaccurate-end-point",
                    if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", element.type.name)
                        put("value", roundTo3Decimals(lineLength(element.end, calculatedEnd)).toString())
                    },
                )
            },
        )

    val typeErrors =
        when (element) {
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
): List<GeometryValidationIssue> {
    val directionDiff = angleDiffRads(element.startDirectionRads, previous.endDirectionRads)
    return listOfNotNull(
        validate(element.start.isSame(previous.end, ACCURATE_COORDINATE_DELTA)) {
            val isIncorrect = !element.start.isSame(previous.end, COORDINATE_DELTA)
            GeometryValidationIssue.of(
                "element",
                if (isIncorrect) "coordinates-not-continuous" else "coordinates-inaccurate",
                if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", element.type.name)
                    put("value", roundTo3Decimals(lineLength(element.start, previous.end)).toString())
                },
            )
        },
        validate(directionDiff <= ACCURATE_ELEMENT_DIRECTION_DELTA) {
            val isIncorrect = directionDiff > ELEMENT_DIRECTION_DELTA
            GeometryValidationIssue.of(
                "element",
                if (isIncorrect) "directions-not-continuous" else "directions-inaccurate",
                if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", element.type.name)
                    put(
                        "value",
                        "${roundTo3Decimals(previous.endDirectionRads)} <> ${roundTo3Decimals(element.startDirectionRads)}",
                    )
                },
            )
        },
        validate(element.staStart > previous.staStart) {
            GeometryValidationIssue.of(
                "element",
                "station-not-increasing",
                OBSERVATION_MAJOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (element.name ?: element.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", element.type.name)
                    put("value", "${previous.staStart} >= ${element.staStart}")
                },
            )
        },
    )
}

private fun validateCurve(alignmentName: AlignmentName, curve: GeometryCurve): List<GeometryValidationIssue> {
    val startRadiusDiff = abs(lineLength(curve.center, curve.start) - curve.radius.toDouble())
    val endRadiusDiff = abs(lineLength(curve.center, curve.start) - curve.radius.toDouble())
    val chordDiff = abs(lineLength(curve.start, curve.end) - curve.chord.toDouble())

    return listOfNotNull(
        validate(startRadiusDiff <= ACCURATE_RADIUS_DELTA) {
            val isIncorrect = startRadiusDiff > RADIUS_DELTA
            GeometryValidationIssue.of(
                "element",
                if (isIncorrect) "curve-radius-incorrect-start" else "curve-radius-inaccurate-start",
                if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (curve.name ?: curve.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", curve.type.name)
                    put("value", roundTo3Decimals(startRadiusDiff).toString())
                },
            )
        },
        validate(endRadiusDiff <= ACCURATE_RADIUS_DELTA) {
            val isIncorrect = endRadiusDiff > RADIUS_DELTA
            GeometryValidationIssue.of(
                "element",
                if (isIncorrect) "curve-radius-incorrect-end" else "curve-radius-inaccurate-end",
                if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (curve.name ?: curve.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", curve.type.name)
                    put("value", roundTo3Decimals(endRadiusDiff).toString())
                },
            )
        },
        validate(chordDiff <= ACCURATE_LENGTH_DELTA) {
            val isIncorrect = chordDiff > LENGTH_DELTA
            GeometryValidationIssue.of(
                "element",
                if (isIncorrect) "curve-chord-incorrect" else "curve-chord-inaccurate",
                if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (curve.name ?: curve.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", curve.type.name)
                },
            )
        },
        validate(curve.radius.toDouble() >= MINIMUM_TURN_RADIUS) {
            GeometryValidationIssue.of(
                "element",
                "curve-steep",
                OBSERVATION_MAJOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (curve.name ?: curve.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", curve.type.name)
                    put("value", curve.radius.toString())
                },
            )
        },
    )
}

private fun validateSpiral(alignmentName: AlignmentName, spiral: GeometrySpiral): List<GeometryValidationIssue> {
    val startRadius = spiral.radiusStart
    val endRadius = spiral.radiusEnd
    return listOfNotNull(
        validate(startRadius == null || startRadius.toDouble() >= MINIMUM_TURN_RADIUS) {
            GeometryValidationIssue.of(
                "element",
                "spiral-start-steep",
                OBSERVATION_MAJOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (spiral.name ?: spiral.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", spiral.type.name)
                    put("value", spiral.radiusStart.toString())
                },
            )
        },
        validate(endRadius == null || endRadius.toDouble() >= MINIMUM_TURN_RADIUS) {
            GeometryValidationIssue.of(
                "element",
                "spiral-end-steep",
                OBSERVATION_MAJOR,
                buildMap {
                    put("alignmentName", alignmentName.toString())
                    (spiral.name ?: spiral.oidPart)?.let { put("elementName", it.toString()) }
                    put("elementType", spiral.type.name)
                    put("value", spiral.radiusEnd.toString())
                },
            )
        },
    )
}

private fun validateClothoid(alignmentName: AlignmentName, clothoid: GeometryClothoid): List<GeometryValidationIssue> {
    val calculatedConstant =
        clothoid.radiusStart?.toDouble()?.let { radiusStart ->
            sqrt(radiusStart * clothoid.segmentToClothoidDistance(0.0))
        }
            ?: clothoid.radiusEnd?.toDouble()?.let { radiusEnd ->
                sqrt(radiusEnd * clothoid.segmentToClothoidDistance(clothoid.length.toDouble()))
            }

    return listOfNotNull(
        calculatedConstant?.let { calculated ->
            val constantDiff = abs(calculated - clothoid.constant.toDouble())
            validate(constantDiff <= ACCURATE_CONSTANT_A_DELTA) {
                val isIncorrect = constantDiff > CONSTANT_A_DELTA
                GeometryValidationIssue.of(
                    "element",
                    if (isIncorrect) "clothoid-incorrect-constant" else "clothoid-inaccurate-constant",
                    if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    buildMap {
                        put("alignmentName", alignmentName.toString())
                        (clothoid.name ?: clothoid.oidPart)?.let { put("elementName", it.toString()) }
                        put("elementType", clothoid.type.name)
                        put("value", round(constantDiff, 6).toString())
                    },
                )
            }
        }
    )
}

private fun validateIntersection(
    profileName: PlanElementName,
    intersection: VerticalIntersection,
): List<GeometryValidationIssue> {
    return if (intersection is VICircularCurve) {
        listOfNotNull(
            validate(intersection.length != null) {
                GeometryValidationIssue.of(
                    "profile",
                    "curve-length-missing",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("profileName", profileName.toString())
                        put("viName", intersection.description.toString())
                    },
                )
            },
            validate(intersection.radius != null) {
                GeometryValidationIssue.of(
                    "profile",
                    "curve-radius-missing",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("profileName", profileName.toString())
                        put("viName", intersection.description.toString())
                    },
                )
            },
        )
    } else listOf()
}

private fun validateIntersectionVsPrevious(
    profileName: PlanElementName,
    intersection: VerticalIntersection,
    previous: VerticalIntersection,
): List<GeometryValidationIssue> {
    val deltaX = intersection.point.x - previous.point.x
    val deltaY = intersection.point.y - previous.point.y
    val profileAngle = if (deltaX > 0) radsToDegrees(sin(deltaY / deltaX)) else null
    return listOfNotNull(
        validate(deltaX > 0) {
            GeometryValidationIssue.of(
                "profile",
                "incorrect-station",
                OBSERVATION_MAJOR,
                buildMap {
                    put("profileName", profileName.toString())
                    put("viName", intersection.description.toString())
                },
            )
        },
        profileAngle?.let { angle ->
            validate(abs(angle) <= MAX_PROFILE_SLOPE_DEGREES) {
                GeometryValidationIssue.of(
                    "profile",
                    "incorrect-slope",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("profileName", profileName.toString())
                        put("viName", intersection.description.toString())
                        put("value", round(angle, 1).toString())
                    },
                )
            }
        },
    )
}

private fun validateProfileSegment(
    profileName: PlanElementName,
    segment: ProfileSegment,
): List<GeometryValidationIssue> {
    return listOfNotNull(
        validate(segment !is LinearProfileSegment || segment.valid) {
            GeometryValidationIssue.of(
                "profile",
                "calculation-failed",
                OBSERVATION_MAJOR,
                buildMap {
                    put("profileName", profileName.toString())
                    put("viName", segment.viName.toString())
                    put("value", "${segment.start.x}-${segment.end.x}")
                },
            )
        }
    )
}

private fun validateProfileSegmentVsPrevious(
    profileName: PlanElementName,
    segment: ProfileSegment,
    previous: ProfileSegment,
): List<GeometryValidationIssue> {
    val segmentValid = segment is LinearProfileSegment && !segment.valid
    val previousValid = previous is LinearProfileSegment && !previous.valid
    return listOfNotNull(
        validate(abs(segment.start.x - previous.end.x) <= 0.0001) {
            GeometryValidationIssue.of(
                "profile",
                "segment-station-not-continuous",
                OBSERVATION_MAJOR,
                buildMap {
                    put("profileName", profileName.toString())
                    put("viName", segment.viName.toString())
                    put("value", "${roundTo3Decimals(previous.end.x)}<>${roundTo3Decimals(segment.start.x)}")
                },
            )
        },
        validate(abs(segment.start.y - previous.end.y) <= 0.0001) {
            GeometryValidationIssue.of(
                "profile",
                "segment-height-not-continuous",
                OBSERVATION_MAJOR,
                buildMap {
                    put("profileName", profileName.toString())
                    put("viName", segment.viName.toString())
                    put("value", "${roundTo3Decimals(previous.end.y)}<>${roundTo3Decimals(segment.start.y)}")
                },
            )
        },
        validate(!segmentValid || !previousValid || abs(segment.startAngle - previous.endAngle) <= 0.0001) {
            GeometryValidationIssue.of(
                "profile",
                "segment-angle-not-continuous",
                OBSERVATION_MAJOR,
                buildMap {
                    put("profileName", profileName.toString())
                    put("viName", segment.viName.toString())
                    put(
                        "value",
                        "${roundTo3Decimals(radsToDegrees(previous.endAngle))}<>${roundTo3Decimals(radsToDegrees(segment.startAngle))}",
                    )
                },
            )
        },
    )
}

private fun validateCantPoint(
    cantName: PlanElementName,
    cantPoint: GeometryCantPoint,
    gauge: BigDecimal,
): List<GeometryValidationIssue> {
    return listOfNotNull(
        validate(cantPoint.appliedCant.toDouble() in 0.0..gauge.toDouble()) {
            GeometryValidationIssue.of(
                "cant",
                "value-incorrect",
                OBSERVATION_MAJOR,
                buildMap {
                    put("cantName", cantName.toString())
                    put("station", cantPoint.station.toPlainString())
                    put("value", cantPoint.appliedCant.toString())
                },
            )
        }
    )
}

private fun validateCantPointVsPrevious(
    cantName: PlanElementName,
    cantPoint: GeometryCantPoint,
    previous: GeometryCantPoint,
): List<GeometryValidationIssue> {
    return listOfNotNull(
        validate(cantPoint.station > previous.station) {
            GeometryValidationIssue.of(
                "cant",
                "station-not-continuous",
                OBSERVATION_MAJOR,
                buildMap {
                    put("cantName", cantName.toString())
                    put("station", cantPoint.station.toPlainString())
                },
            )
        }
    )
}

fun validateSwitch(
    switch: GeometrySwitch,
    structure: SwitchStructure?,
    alignmentSwitches: List<AlignmentSwitch>,
): List<GeometryValidationIssue> {
    val jointNumbers = switch.joints.map(GeometrySwitchJoint::number)
    val structureJointNumbers = structure?.joints?.map(SwitchStructureJoint::number) ?: listOf()

    val fieldErrors =
        listOfNotNull(
            validate(structure != null) {
                GeometryValidationIssue.of(
                    "switch",
                    "type-unrecognized",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        switch.typeName?.let { put("switchType", it.toString()) }
                    },
                )
            },
            validate(structure == null || jointNumbers.all(structureJointNumbers::contains)) {
                GeometryValidationIssue.of(
                    "switch",
                    "incorrect-joints",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        put("jointNumbers", jointNumbers.map { it.intValue }.joinToString(", "))
                        put("structureJointNumbers", structureJointNumbers.map { it.intValue }.joinToString(", "))
                    },
                )
            },
            validate(jointNumbers.size >= 2) {
                GeometryValidationIssue.of(
                    "switch",
                    "insufficient-joints",
                    OBSERVATION_MINOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        put("jointNumbers", jointNumbers.map { it.intValue }.joinToString(", "))
                        put("structureJointNumbers", structureJointNumbers.map { it.intValue }.joinToString(", "))
                    },
                )
            },
        )

    val geometryErrors = structure?.let { s -> validateSwitchGeometry(switch, s) } ?: emptyList()

    val alignmentErrors = structure?.let { s -> validateSwitchAlignments(switch, s, alignmentSwitches) } ?: emptyList()

    return fieldErrors + geometryErrors + alignmentErrors
}

fun validateSwitchGeometry(switch: GeometrySwitch, switchStructure: SwitchStructure): List<GeometryValidationIssue> {
    val joints: List<GeometrySwitchJoint> = switch.joints
    val positionTransformation = if (joints.size > 1) calculateSwitchLocationDelta(joints, switchStructure) else null
    return if (positionTransformation == null) {
        listOfNotNull(
            validate(joints.size <= 1) {
                GeometryValidationIssue.of(
                    "switch",
                    "location-difference",
                    OBSERVATION_MAJOR,
                    mapOf("switchName" to switch.name.toString()),
                )
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
                GeometryValidationIssue.of(
                    "switch",
                    if (isIncorrect) "incorrect-joint-locations" else "inaccurate-joint-locations",
                    if (isIncorrect) OBSERVATION_MAJOR else OBSERVATION_MINOR,
                    mapOf("switchName" to switch.name.toString()),
                )
            }
        )
    }
}

fun validateSwitchAlignments(
    switch: GeometrySwitch,
    switchStructure: SwitchStructure,
    alignmentSwitches: List<AlignmentSwitch>,
): List<GeometryValidationIssue> {
    val joints: List<GeometrySwitchJoint> = switch.joints
    return alignmentSwitches.flatMap { alignmentSwitch ->
        val structureAlignment = switchStructure.alignments.find { sa -> sa.jointNumbers.containsAll(sa.jointNumbers) }
        val incorrectJoints = mutableListOf<JointNumber>()
        val inaccurateJoints = mutableListOf<JointNumber>()
        alignmentSwitch.joints.forEach { asj ->
            joints
                .find { j -> j.number == asj.number }
                ?.also { joint ->
                    if (!asj.location.isSame(joint.location, JOINT_LOCATION_DELTA)) {
                        incorrectJoints.add(asj.number)
                    } else if (!asj.location.isSame(joint.location, ACCURATE_JOINT_LOCATION_DELTA)) {
                        inaccurateJoints.add(asj.number)
                    }
                }
        }

        listOfNotNull(
            validate(structureAlignment != null) {
                GeometryValidationIssue.of(
                    "switch",
                    "no-structure-alignment",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        switch.typeName?.let { put("switchType", it.toString()) }
                        put("jointNumbers", alignmentSwitch.jointNumbers.map { it.intValue }.joinToString(", "))
                        put("alignmentName", alignmentSwitch.alignment.name.toString())
                    },
                )
            },
            validate(incorrectJoints.isEmpty()) {
                GeometryValidationIssue.of(
                    "switch",
                    "alignment-joint-mismatch",
                    OBSERVATION_MAJOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        switch.typeName?.let { put("switchType", it.toString()) }
                        put("jointNumbers", incorrectJoints.map { it.intValue }.joinToString(", "))
                        put("alignmentName", alignmentSwitch.alignment.name.toString())
                    },
                )
            },
            validate(inaccurateJoints.isEmpty()) {
                GeometryValidationIssue.of(
                    "switch",
                    "alignment-joint-inaccurate",
                    OBSERVATION_MINOR,
                    buildMap {
                        put("switchName", switch.name.toString())
                        switch.typeName?.let { put("switchType", it.toString()) }
                        put("jointNumbers", inaccurateJoints.map { it.intValue }.joinToString(", "))
                        put("alignmentName", alignmentSwitch.alignment.name.toString())
                    },
                )
            },
        )
    }
}

fun collectAlignmentSwitchJoints(switchId: DomainId<GeometrySwitch>, alignment: GeometryAlignment): AlignmentSwitch? {
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
    val jointNumbers =
        joints
            .filterIndexed { i, j -> j.number.intValue != 0 && (i == 0 || joints[i].number != j.number) }
            .map(AlignmentSwitchJoint::number)
}

data class AlignmentSwitchJoint(val number: JointNumber, val location: Point)

private fun validate(check: Boolean, lazyError: () -> GeometryValidationIssue): GeometryValidationIssue? =
    if (check) null else lazyError()

/**
 * Validate a list of items, using one function to check the items themselves and another to check them versus the
 * previous one for consistency
 */
private fun <N : CharSequence, T : Any> validatePieces(
    parentName: N,
    pieces: List<T>,
    itemValidator: (N, T) -> List<GeometryValidationIssue>,
    itemVsPreviousValidator: (N, T, T) -> List<GeometryValidationIssue> = { _, _, _ -> listOf() },
): List<GeometryValidationIssue> {
    return pieces.flatMapIndexed { index, item ->
        val pointErrors = itemValidator(parentName, item)
        val previous = pieces.getOrNull(index - 1)
        val consistencyErrors = previous?.let { p -> itemVsPreviousValidator(parentName, item, p) } ?: listOf()
        (pointErrors + consistencyErrors)
    }
}
