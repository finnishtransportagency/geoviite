import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometry
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonProperties
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.util.FreeText

typealias FrameConverterResponseSettings = Set<Int>

data class GeoJsonFeatureErrorResponseV1(
    override val geometry: GeoJsonGeometry = GeoJsonGeometryPoint.empty(),
    override val properties: GeoJsonFeatureErrorResponsePropertiesV1,
) : GeoJsonFeature() {
    constructor(identifier: String?, errorMessages: String) : this(
        properties = GeoJsonFeatureErrorResponsePropertiesV1(
            identifier = identifier,
            errors = errorMessages,
        )
    )
}

data class GeoJsonFeatureErrorResponsePropertiesV1(
    // TODO Multiple errors in a single string? Are multiple errors even returned?
    @JsonProperty("tunniste") val identifier: String? = null,
    @JsonProperty("virheet") val errors: String = "",
) : GeoJsonProperties

sealed class FrameConverterRequestV1

data class CoordinateToTrackMeterRequestV1(
    @JsonProperty("tunniste") val identifier: String? = null,

    val x: Double? = null, // x coordinate in ETRS-TM35FIN (EPSG:3067)
    val y: Double? = null, // y coordinate in ETRS-TM35FIN (EPSG:3067)

    // TODO Clamp to 1-1000 meters
    // TODO Use a radius instead of bbox
    @JsonProperty("sade") val searchRadius: Double = 100.0, // Search radius
    @JsonProperty("ratanumero") val trackNumberName: String? = null, // Filter results by track number
    @JsonProperty("sijaintiraide") val locationTrackName: String? = null, // Filter results by location track name (0 or 1)
    @JsonProperty("sijaintiraide_tyyppi") val locationTrackType: String? = null,

    // Response settings:
    // 1: Response contains location track's track address in properties
    // 5: Response contains location track's track address in geometry
    // 10: Result contains location track address and location track's information
    @JsonProperty("palautusarvot") val responseSettings: FrameConverterResponseSettings = setOf(1, 10), // TODO Enum?
) : FrameConverterRequestV1()

data class CoordinateToTrackMeterResponsePropertiesV1(
    // Same as request if supplied by the user.
    @JsonProperty("tunniste") val id: String? = null,

    // Returned when responseSettings in the request contain the number 1.
    @JsonUnwrapped val closestLocationTrackMatch: ClosestLocationTrackMatchV1? = null,

    // Returned when responseSettings in the request contain the number 10.
    @JsonUnwrapped val conversionDetails: CoordinateToTrackMeterConversionDetailsV1? = null,
) : GeoJsonProperties

data class CoordinateToTrackMeterResponseV1(
    override val geometry: GeoJsonGeometryPoint,
    override val properties: CoordinateToTrackMeterResponsePropertiesV1,
) : GeoJsonFeature()

data class ClosestLocationTrackMatchV1(
    val x: Double, // x coordinate on the geometry of the matched location track ETRS-TM35FIN (EPSG:3067)
    val y: Double, // y coordinate on the geometry of the matched location track in ETRS-TM35FIN (EPSG:3067)
    @JsonProperty("valimatka") val distanceFromRequestPoint: Double,
)

data class CoordinateToTrackMeterConversionDetailsV1(
    @JsonProperty("ratanumero") val trackNumber: TrackNumber,
    @JsonProperty("sijaintiraide") val locationTrackName: AlignmentName,
    @JsonProperty("sijaintiraide_kuvaus") val locationTrackDescription: FreeText, // See GetFullDescription
    @JsonProperty("sijaintiraide_tyyppi") val translatedLocationTrackType: String,
    @JsonProperty("ratakilometri") val kmNumber: Int,
    @JsonProperty("ratametri") val trackMeter: Int, // TODO Verify this and the one below
    @JsonProperty("ratametri_desimaalit") val trackMeterDecimals: Int,
)


