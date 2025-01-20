package fi.fta.geoviite.infra.switchLibrary

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.RowVersion
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

// Enum values are Finnish abbreviations, as they are most common types Geoviite will encounter
enum class SwitchHand(val abbreviation: String) {
    LEFT("V"),
    RIGHT("O"),
    NONE(""),
}

val SWITCH_BASE_TYPES_WITH_HANDEDNESS =
    setOf(SwitchBaseType.YV, SwitchBaseType.KV, SwitchBaseType.SKV, SwitchBaseType.UKV)

private fun switchTypeAbbreviationRegexOptions(nationality: SwitchNationality): String =
    enumValues<SwitchBaseType>()
        .filter { it.nationality == nationality }
        .joinToString(separator = "|", transform = { it.name })

val SWITCH_TYPE_HAND_REGEX_OPTIONS =
    enumValues<SwitchHand>()
        .filter { it.abbreviation.isNotEmpty() }
        .joinToString(separator = "|", transform = { it.abbreviation })

private fun finnishSwitchTypeRegex(): Regex =
    Regex(
        "^" +
            "(${switchTypeAbbreviationRegexOptions(SwitchNationality.FINNISH)})" + // simple type
            "(\\d{2})" + // rail weight
            "(?:-([\\d/]+)([()A-Z]*))?" + // optional radius of the curve(s) + spread/tilted
            "-((?:\\dx)?1:[\\w\\d,.\\-/]+?)" + // ratio
            "(?:-($SWITCH_TYPE_HAND_REGEX_OPTIONS))?" + // optional hand
            "$"
    )

private fun swedishSwitchTypeRegex(): Regex =
    Regex(
        "^" +
            "(${switchTypeAbbreviationRegexOptions(SwitchNationality.SWEDISH)})" + // simple type
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
    val matchResult = finnishSwitchTypeRegex().find(typeName) ?: return null
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
    val matchResult = swedishSwitchTypeRegex().find(typeName) ?: return null
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

data class SwitchStructureJoint(override val number: JointNumber, override val location: Point) : ISwitchJoint {
    override fun toString(): String {
        return switchJointToString(this)
    }
}

enum class SwitchStructureElementType {
    LINE,
    CURVE,
    //    CLOTHOID // this seems to be possible :(
}

sealed class SwitchStructureElement {
    abstract val type: SwitchStructureElementType
    abstract val start: Point
    abstract val end: Point
}

data class SwitchStructureLine(override val start: Point, override val end: Point) : SwitchStructureElement() {
    override val type: SwitchStructureElementType = SwitchStructureElementType.LINE
}

data class SwitchStructureCurve(override val start: Point, override val end: Point, val radius: Double) :
    SwitchStructureElement() {
    override val type: SwitchStructureElementType = SwitchStructureElementType.CURVE
}

data class SwitchStructureAlignment(val jointNumbers: List<JointNumber>, val elements: List<SwitchStructureElement>) {
    init {
        require(jointNumbers.isNotEmpty()) { "Switch structure alignment must have some joints" }
        require(elements.isNotEmpty()) { "Switch structure alignment must have some elements" }
    }
}

interface ISwitchStructure {
    val type: SwitchType
    val presentationJointNumber: JointNumber
    val joints: Set<SwitchStructureJoint>
    val alignments: List<SwitchStructureAlignment>
    val data: SwitchStructureData

    // These props are published into JSON from API
    @Suppress("unused")
    val hand
        get() = type.parts.hand

    @Suppress("unused")
    val baseType
        get() = type.parts.baseType

    val alignmentJoints: List<SwitchStructureJoint>
    val bbox: BoundingBox

    fun isSame(other: ISwitchStructure): Boolean = data == other.data

    fun getJoint(jointNumber: JointNumber): SwitchStructureJoint {
        return joints.find { joint -> joint.number == jointNumber }
            ?: throw IllegalArgumentException("Joint number $jointNumber does not exist in switch $type!")
    }

    fun getJointLocation(jointNumber: JointNumber): Point {
        return getJoint(jointNumber).location
    }
}

data class SwitchStructure(
    val version: RowVersion<SwitchStructure>,
    @JsonIgnore override val data: SwitchStructureData,
) : ISwitchStructure by data {
    val id: IntId<SwitchStructure> = version.id
}

data class SwitchStructureData(
    override val type: SwitchType,
    override val presentationJointNumber: JointNumber,
    override val joints: Set<SwitchStructureJoint>,
    override val alignments: List<SwitchStructureAlignment>,
) : ISwitchStructure {
    override val data = this

    override val alignmentJoints: List<SwitchStructureJoint> by lazy {
        joints.filter { joint -> alignments.any { alignment -> alignment.jointNumbers.contains(joint.number) } }
    }

    override val bbox: BoundingBox by lazy { boundingBoxAroundPoints(joints.map { joint -> joint.location }) }

    init {
        require(joints.isNotEmpty()) { "Switch structure must have joint points: type=$type" }
        val allJointNumbers = joints.map { j -> j.number }.toSet()
        require(allJointNumbers.contains(presentationJointNumber)) {
            "Switch structure must contain the joint point for presentation joint: type=$type presentationJointNumber=$presentationJointNumber allJoints=$allJointNumbers"
        }

        require(alignments.isNotEmpty()) { "Switch structure must have at least one alignment: type=$type" }
        val allAlignmentJointNumbers = alignments.flatMap { a -> a.jointNumbers }.toSet()
        require(allAlignmentJointNumbers.all(allJointNumbers::contains)) {
            "Switch structure alignments can only have joints that exist in the structure: type=$type alignmentJoints=$allAlignmentJointNumbers allJoints=$allJointNumbers"
        }
        require(allAlignmentJointNumbers.contains(presentationJointNumber)) {
            "Some switch structure alignment must contain the joint point for presentation joint: type=$type presentationJointNumber=$presentationJointNumber allAlignmentJoints=$allAlignmentJointNumbers"
        }
    }

    fun flipAlongYAxis(): SwitchStructureData {
        val multiplier = Point(1.0, -1.0)
        return this.copy(
            joints = joints.map { joint -> joint.copy(location = joint.location * multiplier) }.toSet(),
            alignments =
                alignments.map { alignment ->
                    alignment.copy(
                        elements =
                            alignment.elements.map { element ->
                                when (element) {
                                    is SwitchStructureLine -> {
                                        element.copy(start = element.start * multiplier, end = element.end * multiplier)
                                    }
                                    is SwitchStructureCurve -> {
                                        element.copy(start = element.start * multiplier, end = element.end * multiplier)
                                    }
                                }
                            }
                    )
                },
        )
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

data class LinkableSwitchAlignment(val joints: List<JointNumber>, val originalAlignment: SwitchStructureAlignment)

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
