package fi.fta.geoviite.api.frameconverter.geojson

data class GeoJsonFeatureCollection(
    val type: GeoJsonType = GeoJsonType.FeatureCollection,
    val features: List<GeoJsonFeature> = emptyList(),
)

enum class GeoJsonType {
    FeatureCollection,
    Feature,
}

abstract class GeoJsonFeature {
    val type: GeoJsonType = GeoJsonType.Feature

    abstract val geometry: GeoJsonGeometry?
    abstract val properties: GeoJsonProperties?
}

abstract class GeoJsonGeometry

enum class GeoJsonGeometryType {
    Point
}

data class GeoJsonGeometryPoint(
    val type: GeoJsonGeometryType = GeoJsonGeometryType.Point,
    val coordinates: List<Double>,
) : GeoJsonGeometry() {

    companion object {
        fun empty() = GeoJsonGeometryPoint(coordinates = listOf())
    }
}

abstract class GeoJsonProperties
