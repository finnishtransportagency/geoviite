package fi.fta.geoviite.infra.switchLibrary

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.inframodel.angleBetween
import fi.fta.geoviite.infra.math.Angle
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Rads
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.rotateAroundPoint
import fi.fta.geoviite.infra.util.formatForException
import kotlin.math.abs

class InvalidJointsException(msg: String) : IllegalArgumentException(msg)

enum class SwitchBaseType(val nationality: SwitchNationality = SwitchNationality.FINNISH) {
    // Finnish
    YV,
    TYV,
    KV,
    YRV,
    KRV,
    SKV,
    UKV,
    RR,
    SRR,

    // Swedish
    EV(nationality = SwitchNationality.SWEDISH),
}

enum class SwitchNationality {
    FINNISH,
    SWEDISH,
}

enum class SwitchHand(val abbreviation: String) {
    LEFT("V"),
    RIGHT("O"),
    NONE(""),
}

val SWITCH_BASE_TYPES_WITH_HANDEDNESS =
    setOf(SwitchBaseType.YV, SwitchBaseType.KV, SwitchBaseType.SKV, SwitchBaseType.UKV)

val FINNISH_SWITCH_TYPE_ABBREVIATION_REGEX_OPTIONS =
    enumValues<SwitchBaseType>()
        .filter { it.nationality == SwitchNationality.FINNISH }
        .joinToString(separator = "|", transform = { it.name })
val SWEDISH_SWITCH_TYPE_ABBREVIATION_REGEX_OPTIONS =
    enumValues<SwitchBaseType>()
        .filter { it.nationality == SwitchNationality.SWEDISH }
        .joinToString(separator = "|", transform = { it.name })

val SWITCH_TYPE_HAND_REGEX_OPTIONS =
    enumValues<SwitchHand>()
        .filter { it.abbreviation.isNotEmpty() }
        .joinToString(separator = "|", transform = { it.abbreviation })

private val FINNISH_SWITCH_TYPE_REGEX =
    Regex(
        "^" +
            "($FINNISH_SWITCH_TYPE_ABBREVIATION_REGEX_OPTIONS)" + // simple type
            "(\\d{2})" + // rail weight
            "(?:-([\\d/]+)([()A-Z]*))?" + // optional radius of the curve(s) + spread/tilted
            "-((?:\\dx)?1:[\\w\\d,.\\-/]+?)" + // ratio
            "(?:-($SWITCH_TYPE_HAND_REGEX_OPTIONS))?" + // optional hand
            "$"
    )
private val SWEDISH_SWITCH_TYPE_REGEX =
    Regex(
        "^" +
            "($SWEDISH_SWITCH_TYPE_ABBREVIATION_REGEX_OPTIONS)" + // simple type
            "-(SJ)" + // Additional switch type information not relevant to Geoviite
            "(\\d{2})" + // rail weight
            "-(5,9)" +
            "-(\\d:\\d)" + // ratio
            "(?:-(V|H))?" + // hand
            "$"
    )

fun switchTypeRequiresHandedness(baseType: SwitchBaseType): Boolean {
    return SWITCH_BASE_TYPES_WITH_HANDEDNESS.contains(baseType)
}

data class SwitchTypeParts(
    val baseType: SwitchBaseType,
    val railWeight: Int,
    val curveRadius: List<Int>,
    val spread: String?,
    val ratio: String, // has complex formats, like "2x1:9-4.8", use more structured type if needed
    val hand: SwitchHand,
)

private fun parseCurveRadius(curveRadiusList: String): List<Int> {
    return curveRadiusList.split("/").mapNotNull { radius -> radius.toIntOrNull() }
}

private fun findFinnishSwitchTypeHand(abbreviation: String): SwitchHand {
    return SwitchHand.values().find { hand -> hand.abbreviation == abbreviation } ?: SwitchHand.NONE
}

private fun findSwedishSwitchTypeHand(abbreviation: String): SwitchHand =
    when (abbreviation) {
        "V" -> SwitchHand.LEFT
        "H" -> SwitchHand.RIGHT
        else -> SwitchHand.NONE
    }

/** Returns parsed switch type parts or null if parsing fails */
fun parseFinnishSwitchType(typeName: String): SwitchTypeParts? {
    val matchResult = FINNISH_SWITCH_TYPE_REGEX.find(typeName) ?: return null
    val captured = matchResult.destructured.toList()
    val hand =
        if (captured.count() <= 5 || captured[5] == "") SwitchHand.NONE else findFinnishSwitchTypeHand(captured[5])

    return SwitchTypeParts(
        baseType = SwitchBaseType.valueOf(captured[0]),
        railWeight = captured[1].toInt(),
        curveRadius = parseCurveRadius(captured[2]),
        spread = captured[3].ifEmpty { null },
        ratio = captured[4],
        hand = hand,
    )
}

fun parseSwedishSwitchType(typeName: String): SwitchTypeParts? {
    val matchResult = SWEDISH_SWITCH_TYPE_REGEX.find(typeName) ?: return null
    val captured = matchResult.destructured.toList()

    return SwitchTypeParts(
        baseType = SwitchBaseType.valueOf(captured[0]),
        railWeight = captured[2].toInt(),
        curveRadius = emptyList(),
        spread = null,
        ratio = captured[4],
        hand = findSwedishSwitchTypeHand(captured[5]),
    )
}

data class SwitchType @JsonCreator(mode = DELEGATING) constructor(val typeName: String) {
    val parts =
        if (typeName.startsWith("EV")) {
            parseSwedishSwitchType(typeName)
                ?: throw IllegalArgumentException("Cannot parse switch type: \"${formatForException(typeName)}\"")
        } else {
            parseFinnishSwitchType(typeName)
                ?: throw IllegalArgumentException("Cannot parse switch type: \"${formatForException(typeName)}\"")
        }

    @JsonValue override fun toString(): String = typeName
}

fun tryParseSwitchType(typeName: String): SwitchType? {
    return try {
        SwitchType(typeName)
    } catch (e: Exception) {
        null
    }
}

interface ISwitchJoint {
    val number: JointNumber
    val location: Point
}

fun switchJointToString(switchJoint: ISwitchJoint): String {
    return "${switchJoint.number} (${switchJoint.location})"
}

data class SwitchJoint(override val number: JointNumber, override val location: Point) : ISwitchJoint {
    override fun toString(): String {
        return switchJointToString(this)
    }
}

enum class SwitchElementType {
    LINE,
    CURVE,
    //    CLOTHOID // this seems to be possible :(
}

sealed class SwitchElement {
    abstract val id: DomainId<SwitchElement>
    abstract val type: SwitchElementType
    abstract val start: Point
    abstract val end: Point
}

data class SwitchElementLine(
    override val id: DomainId<SwitchElement> = StringId(),
    override val start: Point,
    override val end: Point,
) : SwitchElement() {
    override val type: SwitchElementType = SwitchElementType.LINE
}

data class SwitchElementCurve(
    override val id: DomainId<SwitchElement> = StringId(),
    override val start: Point,
    override val end: Point,
    val radius: Double,
) : SwitchElement() {
    override val type: SwitchElementType = SwitchElementType.CURVE
}

data class SwitchAlignment(
    val jointNumbers: List<JointNumber>,
    val elements: List<SwitchElement>,
    val id: DomainId<SwitchAlignment> = StringId(jointNumbers.joinToString("-") { joint -> joint.intValue.toString() }),
) {
    init {
        if (jointNumbers.isEmpty()) {
            throw IllegalArgumentException("No joint numbers")
        }
        if (elements.isEmpty()) {
            throw IllegalArgumentException("No elements")
        }
    }
}

data class SwitchStructure(
    val id: DomainId<SwitchStructure> = StringId(),
    val type: SwitchType,
    val presentationJointNumber: JointNumber,
    val joints: List<SwitchJoint>,
    val alignments: List<SwitchAlignment>,
) {
    // These props are published into JSON from API
    @Suppress("unused") val hand = type.parts.hand

    @Suppress("unused") val baseType = type.parts.baseType

    val alignmentJoints by lazy {
        joints.filter { joint -> alignments.any { alignment -> alignment.jointNumbers.contains(joint.number) } }
    }

    val bbox: BoundingBox by lazy { boundingBoxAroundPoints(joints.map { joint -> joint.location }) }

    init {
        if (joints.isEmpty()) {
            throw IllegalArgumentException("No joint points")
        }
        if (joints.none { it.number == presentationJointNumber }) {
            throw IllegalArgumentException(
                "Presentation joint number $presentationJointNumber does not exists in joints!"
            )
        }
        if (alignments.isEmpty()) {
            throw IllegalArgumentException("No alignments")
        }
        if (
            alignments.any {
                it.jointNumbers.any { alignJointNumber -> joints.none { joint -> joint.number == alignJointNumber } }
            }
        ) {
            throw IllegalArgumentException("Alignment contains joint number that does not exists in joints!")
        }
        if (
            alignments.map { alignment -> alignment.id }.let { alignmentIds -> alignmentIds != alignmentIds.distinct() }
        ) {
            throw IllegalArgumentException("Two or more alignments has the same id!")
        }
    }

    fun getAlignment(id: StringId<SwitchAlignment>): SwitchAlignment {
        return alignments.find { alignment -> alignment.id == id }
            ?: throw IllegalArgumentException("Switch structure $type does not contain alignment $id!")
    }

    fun flipAlongYAxis(): SwitchStructure {
        val multiplier = Point(1.0, -1.0)
        return this.copy(
            joints = joints.map { joint -> joint.copy(location = joint.location * multiplier) },
            alignments =
                alignments.map { alignment ->
                    alignment.copy(
                        elements =
                            alignment.elements.map { element ->
                                when (element) {
                                    is SwitchElementLine -> {
                                        element.copy(start = element.start * multiplier, end = element.end * multiplier)
                                    }
                                    is SwitchElementCurve -> {
                                        element.copy(start = element.start * multiplier, end = element.end * multiplier)
                                    }
                                }
                            }
                    )
                },
        )
    }

    fun getJoint(jointNumber: JointNumber): SwitchJoint {
        return joints.find { joint -> joint.number == jointNumber }
            ?: throw IllegalArgumentException("Joint number $jointNumber does not exist in switch $type!")
    }

    fun getJointLocation(jointNumber: JointNumber): Point {
        return getJoint(jointNumber).location
    }

    fun stripUniqueIdentifiers(): SwitchStructure {
        var i = 0
        return copy(
            id = IntId(i++),
            alignments =
                alignments.map { a ->
                    a.copy(
                        id = IntId(i++),
                        elements =
                            a.elements.map { e ->
                                when (e) {
                                    is SwitchElementLine -> e.copy(id = IntId(i++))
                                    is SwitchElementCurve -> e.copy(id = IntId(i++))
                                }
                            },
                    )
                },
        )
    }

    fun isSame(other: SwitchStructure): Boolean {
        return stripUniqueIdentifiers() == other.stripUniqueIdentifiers()
    }
}

/** Contains information how the geometry of a switch is transformed from an ideal switch (from the switch library). */
data class SwitchPositionTransformation(val translation: Point, val rotation: Angle, val rotationReferencePoint: Point)

fun calculateSwitchLocationDelta(
    joints: List<ISwitchJoint>,
    switchStructure: SwitchStructure,
): SwitchPositionTransformation? {
    val jointPairs =
        joints.mapNotNull { joint ->
            val matchingStructureJoint =
                switchStructure.alignmentJoints.find { structureJoint -> joint.number == structureJoint.number }
            if (matchingStructureJoint != null) Pair(joint, matchingStructureJoint) else null
        }

    if (
        jointPairs.size < 2 ||
            abs(
                lineLength(jointPairs[0].first.location, jointPairs[1].first.location) -
                    lineLength(jointPairs[0].second.location, jointPairs[1].second.location)
            ) > 0.1
    ) {
        return null
    }

    val angleDelta =
        angleBetween(jointPairs[0].first.location, jointPairs[1].first.location, AngularUnit.RADIANS).original -
            angleBetween(jointPairs[0].second.location, jointPairs[1].second.location, AngularUnit.RADIANS).original
    val locationDelta = jointPairs[0].first.location - jointPairs[0].second.location
    return SwitchPositionTransformation(
        rotation = Rads(angleDelta),
        translation = locationDelta,
        rotationReferencePoint = jointPairs[0].second.location,
    )
}

fun transformSwitchPoint(transformation: SwitchPositionTransformation, point: Point): Point {
    return rotateAroundPoint(transformation.rotationReferencePoint, transformation.rotation.rads, point) +
        transformation.translation
}

data class LinkableSwitchAlignment(val joints: List<JointNumber>, val originalAlignment: SwitchAlignment)

data class SwitchConnectivity(val alignments: List<LinkableSwitchAlignment>, val frontJoint: JointNumber?)

fun switchConnectivity(structure: SwitchStructure): SwitchConnectivity =
    when (structure.baseType) {
        SwitchBaseType.YV,
        SwitchBaseType.TYV,
        SwitchBaseType.YRV,
        SwitchBaseType.SKV,
        SwitchBaseType.UKV,
        SwitchBaseType.EV,
        SwitchBaseType.KV ->
            SwitchConnectivity(
                alignments = structure.alignments.map { LinkableSwitchAlignment(it.jointNumbers, it) },
                frontJoint = JointNumber(1),
            )

        SwitchBaseType.KRV,
        SwitchBaseType.RR,
        SwitchBaseType.SRR ->
            SwitchConnectivity(
                alignments =
                    structure.alignments
                        .filter { it.jointNumbers.size == 3 }
                        .flatMap { alignment ->
                            val joints = alignment.jointNumbers
                            listOf(
                                LinkableSwitchAlignment(listOf(joints[0], joints[1]), alignment),
                                LinkableSwitchAlignment(listOf(joints[1], joints[2]), alignment),
                            )
                        },
                frontJoint = null,
            )
    }
