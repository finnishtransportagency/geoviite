package fi.fta.geoviite.api.frameconverter.geojson

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "Vastaus: Muunnoksen tulosjoukko (GeoJSON FeatureCollection)",
    description = "GeoJSON FeatureCollection muunnostuloksineen.",
)
data class GeoJsonFeatureCollection(
    val type: GeoJsonType = GeoJsonType.FeatureCollection,
    val features: List<GeoJsonFeature> = emptyList(),
)

enum class GeoJsonType {
    FeatureCollection,
    Feature,
}

@Schema(name = "GeoJSON Feature", description = "GeoJSON Feature (yksittäinen muunnostulos)")
abstract class GeoJsonFeature {
    val type: GeoJsonType = GeoJsonType.Feature

    abstract val geometry: GeoJsonGeometry?
    abstract val properties: GeoJsonProperties?
}

abstract class GeoJsonGeometry

enum class GeoJsonGeometryType {
    Point
}

@Schema(name = "Pistesijainti")
data class GeoJsonGeometryPoint(
    val type: GeoJsonGeometryType = GeoJsonGeometryType.Point,
    val coordinates: List<Double>,
) : GeoJsonGeometry() {

    companion object {
        fun empty() = GeoJsonGeometryPoint(coordinates = listOf())
    }
}

abstract class GeoJsonProperties
