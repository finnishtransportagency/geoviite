package fi.fta.geoviite.api.frameconverter.geojson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class GeoJsonFeatureCollection(
    val type: GeoJsonType = GeoJsonType.FeatureCollection,
    val features: List<GeoJsonFeature> = emptyList(),
) {
    constructor(features: GeoJsonFeature) : this(
        features = listOf(features)
    )
}

enum class GeoJsonType {
    FeatureCollection,
    Feature,
}

abstract class GeoJsonFeature {
    val type: GeoJsonType = GeoJsonType.Feature

    abstract val geometry: GeoJsonGeometry?
    abstract val properties: GeoJsonProperties?
}

interface GeoJsonGeometry

enum class GeoJsonGeometryType {
    Point,
}

data class GeoJsonGeometryPoint(
    val type: GeoJsonGeometryType = GeoJsonGeometryType.Point,
    val coordinates: List<Double>,
) : GeoJsonGeometry {

    companion object {
        fun empty() = GeoJsonGeometryPoint(coordinates = listOf())
    }
}

interface GeoJsonProperties
