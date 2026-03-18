package fi.fta.geoviite.api.frameconverter.geojson

import io.swagger.v3.oas.annotations.media.Schema

@Schema(hidden = true, name = "GeoJSON FeatureCollection")
interface GeoJsonFeatureCollection {
    @get:Schema(type = "string", allowableValues = ["FeatureCollection"])
    val type: GeoJsonType
        get() = GeoJsonType.FeatureCollection

    val features: List<GeoJsonFeature>
}

enum class GeoJsonType {
    FeatureCollection,
    Feature,
}

@Schema(hidden = true, name = "GeoJSON Feature")
interface GeoJsonFeature {
    @get:Schema(type = "string", allowableValues = ["Feature"])
    val type: GeoJsonType
        get() = GeoJsonType.Feature

    val geometry: GeoJsonGeometry?
    val properties: GeoJsonProperties?
}

@Schema(hidden = true) interface GeoJsonGeometry

enum class GeoJsonGeometryType {
    Point
}

@Schema(hidden = true, name = "Pistesijainti (GeoJSON Point)")
data class GeoJsonGeometryPoint(
    @Schema(type = "string", allowableValues = ["Point"]) val type: GeoJsonGeometryType = GeoJsonGeometryType.Point,
    val coordinates: List<Double>,
) : GeoJsonGeometry {

    companion object {
        fun empty() = GeoJsonGeometryPoint(coordinates = listOf())
    }
}

@Schema(hidden = true) interface GeoJsonProperties
