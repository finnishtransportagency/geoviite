package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.ISwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.assertSanitized

data class GeometrySwitch(
    val name: SwitchName,
    val state: PlanState?,
    val switchStructureId: IntId<SwitchStructure>?,
    val typeName: GeometrySwitchTypeName,
    val joints: List<GeometrySwitchJoint>,
    val id: DomainId<GeometrySwitch> = StringId(),
) : Loggable {
    fun getJoint(location: Point, delta: Double): GeometrySwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): GeometrySwitchJoint? =
        joints.find { j -> j.number == number }

    override fun toLog(): String = logFormat("id" to id, "name" to name, "joints" to joints.map { j -> j.number.intValue })
}

data class GeometrySwitchJoint(override val number: JointNumber, override val location: Point) : ISwitchJoint

private val geometrySwitchTypeNameLength = 0..30
private val geometrySwitchTypeNameRegex = Regex("^[A-Za-z0-9:\\-/(),.]*\$")

data class GeometrySwitchTypeName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<GeometrySwitchTypeName>, CharSequence by value {
    init {
        assertSanitized<GeometrySwitchTypeName>(
            value,
            geometrySwitchTypeNameRegex,
            geometrySwitchTypeNameLength,
            allowBlank = true,
        )
    }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: GeometrySwitchTypeName): Int = value.compareTo(other.value)
}
