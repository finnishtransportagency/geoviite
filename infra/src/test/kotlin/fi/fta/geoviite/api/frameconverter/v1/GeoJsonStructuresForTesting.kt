import com.fasterxml.jackson.annotation.JsonProperty

data class TestGeoJsonFeatureCollection(
    @JsonProperty("type") val type: String,
    @JsonProperty("features") val features: List<TestGeoJsonFeature>,
)

data class TestGeoJsonFeature(
    @JsonProperty("type") val type: String,
    @JsonProperty("geometry") val geometry: TestGeoJsonGeometry?,
    @JsonProperty("properties") val properties: Map<String, Any>?,
)

data class TestGeoJsonGeometry(
    @JsonProperty("type") val type: String,
    @JsonProperty("coordinates") val coordinates: Any,
)
