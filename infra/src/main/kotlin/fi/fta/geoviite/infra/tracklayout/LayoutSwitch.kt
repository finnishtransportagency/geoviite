package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure

enum class SwitchJointRole {
    MAIN,
    CONNECTION,
    MATH;

    companion object {
        fun of(structure: SwitchStructure, number: JointNumber): SwitchJointRole =
            when {
                structure.presentationJointNumber == number -> MAIN
                structure.endJointNumbers.contains(number) -> CONNECTION
                else -> MATH
            }
    }
}

data class LayoutSwitchJoint(
    val number: JointNumber,
    val role: SwitchJointRole,
    val location: Point,
    val locationAccuracy: LocationAccuracy?,
)

data class LayoutSwitch(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val joints: List<LayoutSwitchJoint>,
    val sourceId: DomainId<GeometrySwitch>?,
    val trapPoint: Boolean?,
    val ownerId: IntId<SwitchOwner>?,
    val source: GeometrySource,
    val draftOid: Oid<LayoutSwitch>?,
    @JsonIgnore override val contextData: LayoutContextData<LayoutSwitch>,
) : LayoutAsset<LayoutSwitch>(contextData) {
    @JsonIgnore val exists = !stateCategory.isRemoved()
    val shortName =
        name.split(" ").lastOrNull()?.let { last ->
            if (last.startsWith("V")) {
                last.substring(1).toIntOrNull(10)?.toString()?.padStart(3, '0')?.let { switchNumber ->
                    "V$switchNumber"
                }
            } else {
                null
            }
        }

    fun getJoint(location: AlignmentPoint, delta: Double): LayoutSwitchJoint? =
        getJoint(Point(location.x, location.y), delta)

    fun getJoint(location: Point, delta: Double): LayoutSwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): LayoutSwitchJoint? = joints.find { j -> j.number == number }

    @get:JsonIgnore
    val presentationJoint
        get() = joints.find { j -> j.role == SwitchJointRole.MAIN }

    @get:JsonIgnore
    val presentationJointOrThrow
        get() = requireNotNull(presentationJoint) { "Presentation joint not found on switch: id=$id" }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "source" to source,
            "name" to name,
            "joints" to joints.map { j -> j.number.intValue },
        )

    override fun withContext(contextData: LayoutContextData<LayoutSwitch>): LayoutSwitch =
        copy(contextData = contextData)
}

data class LayoutSwitchJointMatch(val locationTrackId: IntId<LocationTrack>, val location: Point)

data class LayoutSwitchJointConnection(
    val number: JointNumber,
    val accurateMatches: List<LayoutSwitchJointMatch>,
    val locationAccuracy: LocationAccuracy?,
) {
    fun merge(other: LayoutSwitchJointConnection): LayoutSwitchJointConnection {
        check(number == other.number) {
            "expected $number == $other.number in ${LayoutSwitchJointConnection::class.simpleName}#merge"
        }
        // location accuracy comes from the joint and hence can't differ
        check(locationAccuracy == other.locationAccuracy) {
            "expected $locationAccuracy == ${other.locationAccuracy} in ${LayoutSwitchJointConnection::class.simpleName}#merge"
        }
        return LayoutSwitchJointConnection(number, accurateMatches + other.accurateMatches, locationAccuracy)
    }
}
