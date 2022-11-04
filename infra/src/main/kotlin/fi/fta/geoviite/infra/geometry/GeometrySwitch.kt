package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.ISwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

data class GeometrySwitch(
    val name: SwitchName,
    val state: PlanState?,
    val switchStructureId: IntId<SwitchStructure>?,
    val typeName: GeometrySwitchTypeName,
    val joints: List<GeometrySwitchJoint>,
    val id: DomainId<GeometrySwitch> = StringId(),
) {
    fun getJoint(location: Point, delta: Double): GeometrySwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): GeometrySwitchJoint? =
        joints.find { j -> j.number == number }
}

data class GeometrySwitchJoint(override val number: JointNumber, override val location: Point) : ISwitchJoint

private val geometrySwitchTypeNameLength = 0..30
private val geometrySwitchTypeNameRegex = Regex("^[A-Za-z0-9:\\-/(),.]*\$")

data class GeometrySwitchTypeName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<GeometrySwitchTypeName>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<GeometrySwitchTypeName>(
            value,
            geometrySwitchTypeNameRegex,
            geometrySwitchTypeNameLength,
            allowBlank = true,
        )
    }

    override fun compareTo(other: GeometrySwitchTypeName): Int = value.compareTo(other.value)
}

class StringToGeometrySwitchTypeNameConverter : Converter<String, GeometrySwitchTypeName> {
    override fun convert(source: String): GeometrySwitchTypeName = GeometrySwitchTypeName(source)
}

class GeometrySwitchTypeNameToStringConverter : Converter<GeometrySwitchTypeName, String> {
    override fun convert(source: GeometrySwitchTypeName): String = source.toString()
}
