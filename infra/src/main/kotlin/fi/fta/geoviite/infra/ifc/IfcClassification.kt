package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.assertSanitized
import java.math.BigDecimal

data class IfcCode(private val value: String) : CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-Za-z0-9\\-]+$")
    }

    init {
        assertSanitized<IfcTypeName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value
}

data class IfcClassificationName(private val value: String) : CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-ZÄÖÅa-zäöå0-9_\\- ]+$")
    }

    init {
        assertSanitized<IfcTypeName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value
}

data class IfcPropertySetName(private val value: String) : CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-Za-z0-9_]+$")
    }

    init {
        assertSanitized<IfcTypeName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value
}

data class IfcDomain(
    val name: IfcClassificationName,
    val version: BigDecimal,
    val classications: List<IfcClassification>,
    val properties: List<IfcProperty>,
)

data class IfcClassification(
    val name: IfcClassificationName,
    val code: IfcCode,
    val parentCode: IfcCode?,
    val propertySets: List<IfcPropertySet>,
)

data class IfcPropertySet(
    val name: IfcPropertySetName,
    val properties: List<IfcProperty>,
)

enum class IfcPropertyDataType {
    INTEGER, BOOLEAN, TIME,
}

data class IfcProperty(
    val name: IfcClassificationName,
    val code: IfcCode,
    val dataType: IfcPropertyDataType?,
    val allowedValues: List<String>?,
)
