package fi.fta.geoviite.infra.ifc

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.ifc.IfcDataTransformType.*


enum class IfcDataTransformType {
    RAW_STEP, JSON, JSON_LIST
}

data class IfcPropertyTransform(
    val key: String,
    val transform: IfcDataTransformType?,
    val propertyChainIndices: List<List<Int>>,
)

private fun getTransformType(transformString: String): Pair<String, IfcDataTransformType?> =
    if (transformString.contains(':')) {
        val parts = transformString.split(':')
        require(parts.size == 2)
        parts[1] to enumValueOf<IfcDataTransformType>(parts[0])
    } else {
        transformString to null
    }

data class IfcTransformTemplate(val jsonTemplate: String, val propertiesToIndices: (List<String>) -> List<Int>) {
    constructor(jsonTemplate: String, classification: IfcClassification?) : this(
        jsonTemplate = jsonTemplate,
        propertiesToIndices = classificationMapper(classification),
    )

    companion object {
        val propertyRegex = Regex("\\\$\\{[a-zA-Z0-9_,\\-:.]+}")

        fun propertyTemplate(content: String) = "\${$content}"

        fun classificationMapper(classification: IfcClassification?) = { props: List<String> ->
            classification?.getPropertyIndices(props) ?: props.map { p ->
                require(p.all(Char::isDigit)) { "Cannot interpret property names without classification: prop=$p" }
                p.toInt()
            }
        }
    }

    private val json by lazy { ObjectMapper().readValue(jsonTemplate, Map::class.java) }
    val transforms by lazy { findPropertyTransforms(json).map(::toIfcValueTransform) }

    fun toJson(mappedTransformValues: Map<String, Any?>): String =
        ObjectMapper().writeValueAsString(replaceValues(json, mappedTransformValues))

    private fun replaceValues(jsonValues: Map<*, *>, transformedValues: Map<String, Any?>): Map<*, *> {
        return jsonValues.mapValues { (_, v) ->
            if (v is Map<*, *>) replaceValues(v, transformedValues)
            else if (v is String && v.matches(propertyRegex)) transformedValues[v]
            else v
        }
    }

    private fun findPropertyTransforms(template: Map<*, *>): List<String> = template.values.flatMap { v ->
        if (v is Map<*, *>) findPropertyTransforms(v)
        else if (v is String && v.matches(propertyRegex)) setOf(v)
        else emptySet()
    }.distinct()

    private fun toIfcValueTransform(transformString: String): IfcPropertyTransform {
        val (valuesString: String, type: IfcDataTransformType?) = getTransformType(dropTemplating(transformString))
        val propertyChains: List<List<Int>> = valuesString.split(',').map { chain ->
            propertiesToIndices(chain.trim().split("."))
        }
        return IfcPropertyTransform(transformString, type, propertyChains)
    }

    private fun dropTemplating(property: String): String = property.substring(2, property.length - 1)
}

fun transform(
    ifc: Ifc,
    name: IfcName,
    template: String,
    classification: IfcClassification?,
): List<String> =
    transform(ifc, name, IfcTransformTemplate(template, IfcTransformTemplate.classificationMapper(classification)))

fun transform(
    ifc: Ifc,
    name: IfcName,
    transformTemplate: IfcTransformTemplate,
): List<String> = ifc.getDereferenced(name).map { entity ->
    val transformedValues = transformTemplate.transforms.associate { t ->
        t.key to transformValue(t.transform ?: JSON, t.propertyChainIndices.map(entity::getValue))
    }
    transformTemplate.toJson(transformedValues)
}

fun transformValue(type: IfcDataTransformType, attributes: List<IfcEntityAttribute>): Any? = when (type) {
    RAW_STEP -> {
        require(attributes.size == 1) { "Attribute combinations don't have a raw STEP representation" }
        // Default toString() -> RAW STEP format for value
        attributes[0].toString()
    }

    JSON -> {
        require(attributes.size == 1) { "Attribute combinations don't have a simple JSON representation" }
        getJsonMapValue(attributes[0])
    }

    JSON_LIST -> attributes.map(::getJsonMapValue)
}

private fun getJsonMapValue(attribute: IfcEntityAttribute): Any? = when (attribute) {
    is IfcEntityMissingValue -> null
    is IfcEntityNumber -> attribute.value
    is IfcEntityEnum -> attribute.value
    is IfcEntityString -> attribute.value
    is IfcEntityList -> attribute.items.map(::getJsonMapValue)
    else -> throw IllegalArgumentException("Cannot evaluate ${attribute::class.simpleName} as JSON value: value=\"$attribute\"")
}
