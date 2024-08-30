package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometry
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonProperties
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FreeText

typealias FrameConverterIdentifierV1 = FreeText
typealias FrameConverterResponseSettingsV1 = Set<FrameConverterResponseSettingV1>

/**
 * @property FeatureMatchSimple Response will include all fields from [FeatureMatchSimpleV1]
 * @property FeatureGeometry Response will include geometry data, such as [GeoJsonGeometryPoint]
 * @property FeatureMatchDetails Response will include detailed data depending on request,
 *   such as [FeatureMatchDetailsV1]
 *
 * @property INVALID Response will include an error when this value was mapped to the set of response settings.
 */
enum class FrameConverterResponseSettingV1(val code: Int) {
    FeatureMatchSimple(1),
    FeatureGeometry(5),
    FeatureMatchDetails(10),

    INVALID(-1);

    companion object {
        private val map = entries.associateBy(FrameConverterResponseSettingV1::code)
        fun fromValue(type: Int) = map[type] ?: INVALID
    }
}

data class FrameConverterStringV1(val value: String) {
    init {
        require(value.length <= MAX_LENGTH) { "String field length must be at most $MAX_LENGTH characters" }
    }

    override fun toString(): String {
        return value
    }

    companion object {
        const val MAX_LENGTH = 200
    }
}

/**
 * Maps Finnish track type names to internally used type.
 * There is additional mapping to the even more specific domain type [LocationTrackType] during request validation.
 */
enum class FrameConverterLocationTrackTypeV1(val value: String) {
    MAIN("pääraide"),
    SIDE("sivuraide"),
    TRAP("turvaraide"),
    CHORD("kujaraide"),

    INVALID("invalid");

    companion object {
        private val map = entries.associateBy(FrameConverterLocationTrackTypeV1::value)
        fun fromValue(value: String): FrameConverterLocationTrackTypeV1 {
            return map[value] ?: INVALID
        }
    }
}

/**
 * General response type for a request that had an error during validation or processing.
 */
data class GeoJsonFeatureErrorResponseV1(
    override val geometry: GeoJsonGeometry = GeoJsonGeometryPoint.empty(),
    override val properties: GeoJsonFeatureErrorResponsePropertiesV1,
) : GeoJsonFeature() {
    constructor(identifier: FrameConverterIdentifierV1?, errorMessages: String) : this(
        properties = GeoJsonFeatureErrorResponsePropertiesV1(
            identifier = identifier,
            errors = errorMessages,
        )
    )
}

data class GeoJsonFeatureErrorResponsePropertiesV1(
    @JsonProperty("tunniste") val identifier: FrameConverterIdentifierV1? = null,
    @JsonProperty("virheet") val errors: String = "",
) : GeoJsonProperties()

/**
 * Marker class for multiple request types.
 */
sealed class FrameConverterRequestV1

/**
 * @property identifier User provided request identifier which is also included in the response feature(s).
 * @property x User provided x coordinate in ETRS-TM35FIN (EPSG:3067)
 * @property y User provided y coordinate in ETRS-TM35FIN (EPSG:3067)
 *
 * @property searchRadius User provided search radius filter in meters, defaults to 100m.
 * @property trackNumberName User provided track number name filter, optional.
 * @property locationTrackName User provided location track name filter, optional.
 * @property locationTrackType User provided location track type filter, optional.
 * @property responseSettings User provided array of integers, which map to enum values, optional, has defaults.
 */
data class CoordinateToTrackAddressRequestV1(
    @JsonProperty("tunniste") val identifier: FrameConverterIdentifierV1? = null,

    val x: Double? = null,
    val y: Double? = null,

    @JsonProperty("sade")
    val searchRadius: Double? = 100.0,

    @JsonProperty("ratanumero")
    @JsonDeserialize(using = FrameConverterStringDeserializerV1::class)
    val trackNumberName: FrameConverterStringV1? = null,

    @JsonProperty("sijaintiraide")
    @JsonDeserialize(using = FrameConverterStringDeserializerV1::class)
    val locationTrackName: FrameConverterStringV1? = null,

    @JsonProperty("sijaintiraide_tyyppi")
    @JsonDeserialize(using = FrameConverterLocationTrackTypeDeserializerV1::class)
    val locationTrackType: FrameConverterLocationTrackTypeV1? = null,

    @JsonProperty("palautusarvot")
    @JsonDeserialize(using = FrameConverterResponseSettingsDeserializerV1::class)
    val responseSettings: FrameConverterResponseSettingsV1 = setOf(
        FrameConverterResponseSettingV1.FeatureMatchSimple,
        FrameConverterResponseSettingV1.FeatureMatchDetails,
    ),

) : FrameConverterRequestV1()

/**
 * Valid version of the coordinate to track meter request is created during processing,
 * it is not created for an invalid request.
 */
data class ValidCoordinateToTrackAddressRequestV1(
    val identifier: FrameConverterIdentifierV1?,

    val x: Double,
    val y: Double,
    val searchRadius: Double,

    val trackNumberName: TrackNumber?,
    val locationTrackName: AlignmentName?,
    val locationTrackType: LocationTrackType?,

    val responseSettings: FrameConverterResponseSettingsV1,
) : FrameConverterRequestV1()

/**
 * Coordinate to track meter response is only created for a valid, successfully processed request.
 * For invalid requests containing errors, see [GeoJsonFeatureErrorResponseV1].
 */
data class CoordinateToTrackAddressResponseV1(
    override val geometry: GeoJsonGeometryPoint,
    override val properties: CoordinateToTrackAddressResponsePropertiesV1,
) : GeoJsonFeature()

/**
 * @property identifier User provided optional request identifier.
 * @property featureMatchSimple Fields included in the output when responseSettings of the request contain
 *   [FrameConverterResponseSettingV1.FeatureMatchSimple].
 * @property featureMatchDetails Fields included in the output when responseSettings of the request contain
 *   [FrameConverterResponseSettingV1.FeatureMatchDetails].
 */
data class CoordinateToTrackAddressResponsePropertiesV1(
    @JsonProperty("tunniste") val identifier: FrameConverterIdentifierV1? = null,
    @JsonUnwrapped val featureMatchSimple: FeatureMatchSimpleV1? = null,
    @JsonUnwrapped val featureMatchDetails: FeatureMatchDetailsV1? = null,
) : GeoJsonProperties()

/**
 * @property x The x coordinate on the alignment of the matched location track ETRS-TM35FIN (EPSG:3067)
 * @property y The y coordinate on the alignment of the matched location track in ETRS-TM35FIN (EPSG:3067)
 * @property distanceFromRequestPoint Calculated distance from user-specified coordinate in meters.
 */
data class FeatureMatchSimpleV1(
    val x: Double,
    val y: Double,
    @JsonProperty("valimatka") val distanceFromRequestPoint: Double,
)

/**
 * Returned within properties when responseSettings of a request
 * contain [FrameConverterResponseSettingV1.FeatureMatchDetails].
 */
data class FeatureMatchDetailsV1(
    @JsonProperty("ratanumero") val trackNumber: TrackNumber,
    @JsonProperty("sijaintiraide") val locationTrackName: AlignmentName,
    @JsonProperty("sijaintiraide_kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("sijaintiraide_tyyppi") val translatedLocationTrackType: String,
    @JsonProperty("ratakilometri") val kmNumber: Int,
    @JsonProperty("ratametri") val trackMeter: Int,
    @JsonProperty("ratametri_desimaalit") val trackMeterDecimals: Int,
)

/**
 * @property identifier User provided request identifier which is also included in the response feature(s), optional.
 * @property trackNumberName User provided track number, required for valid requests.
 * @property trackKilometer User provided track kilometer, required for valid requests.
 * @property trackMeter User provided track meter on the specified track kilometer, required for valid requests.
 *
 * @property locationTrackName User provided location track name filter, optional.
 * @property locationTrackType User provided location track type filter, optional.
 * @property responseSettings User provided array of integers, which map to enum values, optional, has defaults.
 */
data class TrackAddressToCoordinateRequestV1(
    @JsonProperty("tunniste")
    val identifier: FrameConverterIdentifierV1? = null,

    @JsonProperty("ratanumero")
    @JsonDeserialize(using = FrameConverterStringDeserializerV1::class)
    val trackNumberName: FrameConverterStringV1? = null,

    @JsonProperty("ratakilometri")
    val trackKilometer: Int? = null,

    @JsonProperty("ratametri")
    val trackMeter: Int? = null,

    @JsonProperty("sijaintiraide")
    @JsonDeserialize(using = FrameConverterStringDeserializerV1::class)
    val locationTrackName: FrameConverterStringV1? = null,

    @JsonProperty("sijaintiraide_tyyppi")
    @JsonDeserialize(using = FrameConverterLocationTrackTypeDeserializerV1::class)
    val locationTrackType: FrameConverterLocationTrackTypeV1? = null,

    @JsonProperty("palautusarvot")
    @JsonDeserialize(using = FrameConverterResponseSettingsDeserializerV1::class)
    val responseSettings: FrameConverterResponseSettingsV1 = setOf(
        FrameConverterResponseSettingV1.FeatureMatchSimple,
        FrameConverterResponseSettingV1.FeatureMatchDetails,
    ),

) : FrameConverterRequestV1()

/**
 * Valid version of the track meter to coordinate request is created during processing,
 * it is not created for an invalid request.
 */
data class ValidTrackAddressToCoordinateRequestV1(
    val identifier: FrameConverterIdentifierV1?,

    val trackNumber: TrackLayoutTrackNumber,
    val trackAddress: TrackMeter,

    val locationTrackName: AlignmentName?,
    val locationTrackType: LocationTrackType?,

    val responseSettings: FrameConverterResponseSettingsV1,
) : FrameConverterRequestV1()


/**
 * Track meter to coordinate response is only created for a valid, successfully processed request.
 * For invalid requests containing errors, see [GeoJsonFeatureErrorResponseV1].
 */
data class TrackAddressToCoordinateResponseV1(
    override val geometry: GeoJsonGeometryPoint,
    override val properties: TrackAddressToCoordinateResponsePropertiesV1,
) : GeoJsonFeature()

/**
 * @property identifier User provided optional request identifier.
 * @property featureMatchSimple Fields included in the output when responseSettings of the request contain
 *   [FrameConverterResponseSettingV1.FeatureMatchSimple]
 * @property featureMatchDetails Fields included in the output when responseSettings of the request contain
 *   [FrameConverterResponseSettingV1.FeatureMatchDetails]
 */
data class TrackAddressToCoordinateResponsePropertiesV1(
    @JsonProperty("tunniste") val identifier: FrameConverterIdentifierV1? = null,
    @JsonUnwrapped val featureMatchSimple: FeatureMatchSimpleV1? = null,
    @JsonUnwrapped val featureMatchDetails: FeatureMatchDetailsV1? = null,
) : GeoJsonProperties()
