package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeatureCollection
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonProperties
import fi.fta.geoviite.api.tracklayout.v1.ExtSridV1
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

typealias FrameConverterIdentifierV1 = FreeText

const val COORDINATE_SYSTEM_PARAM = "koordinaatisto"
const val FEATURE_GEOMETRY_PARAM = "geometriatiedot"
const val FEATURE_BASIC_PARAM = "perustiedot"
const val FEATURE_DETAILS_PARAM = "lisatiedot"

const val IDENTIFIER_PARAM = "tunniste"
const val SEARCH_RADIUS_PARAM = "sade"
const val TRACK_NUMBER_OID_PARAM = "ratanumero_oid"
const val TRACK_NUMBER_NAME_PARAM = "ratanumero"
const val LOCATION_TRACK_OID_PARAM = "sijaintiraide_oid"
const val LOCATION_TRACK_NAME_PARAM = "sijaintiraide"
const val LOCATION_TRACK_TYPE_PARAM = "sijaintiraide_tyyppi"
const val TRACK_KILOMETER_PARAM = "ratakilometri"
const val TRACK_METER_PARAM = "ratametri"

data class FrameConverterStringV1 @JsonCreator(mode = DELEGATING) constructor(private val value: String) {

    init {
        require(value.length <= MAX_LENGTH) { "String field length must be at most $MAX_LENGTH characters" }
    }

    @JsonValue
    override fun toString(): String {
        return value
    }

    companion object {
        const val MAX_LENGTH = 200
    }
}

data class FrameConverterQueryParamsV1(
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid = DEFAULT_COORDINATE_SYSTEM,
    @JsonProperty(FEATURE_GEOMETRY_PARAM) val featureGeometry: Boolean = DEFAULT_FEATURE_GEOMETRY,
    @JsonProperty(FEATURE_BASIC_PARAM) val featureBasic: Boolean = DEFAULT_FEATURE_BASIC,
    @JsonProperty(FEATURE_DETAILS_PARAM) val featureDetails: Boolean = DEFAULT_FEATURE_DETAILS,
) {
    companion object {
        val DEFAULT_COORDINATE_SYSTEM = Srid(3067)
        const val DEFAULT_FEATURE_GEOMETRY = false
        const val DEFAULT_FEATURE_BASIC = true
        const val DEFAULT_FEATURE_DETAILS = true
    }

    constructor(
        coordinateSystem: ExtSridV1?,
        featureGeometry: Boolean?,
        featureBasic: Boolean?,
        featureDetails: Boolean?,
    ) : this(
        coordinateSystem = coordinateSystem?.value ?: DEFAULT_COORDINATE_SYSTEM,
        featureGeometry = featureGeometry ?: DEFAULT_FEATURE_GEOMETRY,
        featureBasic = featureBasic ?: DEFAULT_FEATURE_BASIC,
        featureDetails = featureDetails ?: DEFAULT_FEATURE_DETAILS,
    )
}

data class FrameConverterCoordinateV1(
    @JsonIgnore val srid: Srid,
    @Schema(description = FRAME_CONVERTER_OPENAPI_X) override val x: Double,
    @Schema(description = FRAME_CONVERTER_OPENAPI_Y) override val y: Double,
) : IPoint

/**
 * Maps Finnish track type names to internally used type. There is additional mapping to the even more specific domain
 * type [LocationTrackType] during request validation.
 */
@Schema(type = "String", allowableValues = ["pääraide", "sivuraide", "turvaraide", "kujaraide"])
@JsonDeserialize(using = FrameConverterLocationTrackTypeDeserializerV1::class)
enum class FrameConverterLocationTrackTypeV1(@JsonValue val value: String) {
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

/** General response type for a request that had an error during validation or processing. */
@Schema(name = "Virhetulos")
data class GeoJsonFeatureErrorResponseV1(
    @Schema(description = "Tyhjä geometria") override val geometry: GeoJsonGeometryPoint = GeoJsonGeometryPoint.empty(),
    override val properties: GeoJsonFeatureErrorResponsePropertiesV1,
) : TrackAddressToCoordinateSingleResponseV1, CoordinateToTrackAddressSingleResponseV1 {
    constructor(
        identifier: FrameConverterIdentifierV1?,
        errorMessages: List<String>,
    ) : this(properties = GeoJsonFeatureErrorResponsePropertiesV1(identifier = identifier, errors = errorMessages))
}

@Schema(name = "Virheen tiedot")
data class GeoJsonFeatureErrorResponsePropertiesV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_IDENTIFIER)
    @JsonProperty("tunniste")
    val identifier: FrameConverterIdentifierV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_ERRORS)
    @JsonProperty("virheet")
    val errors: List<String> = emptyList(),
) : GeoJsonProperties

/** Marker class for multiple request types. */
sealed class FrameConverterRequestV1

/**
 * @property identifier User provided request identifier which is also included in the response feature(s).
 * @property x User provided x coordinate in ETRS-TM35FIN (EPSG:3067)
 * @property y User provided y coordinate in ETRS-TM35FIN (EPSG:3067)
 * @property searchRadius User provided search radius filter in meters, defaults to 100m.
 * @property trackNumberOid User provided track number oid filter, optional.
 * @property trackNumberName User provided track number name filter, optional.
 * @property locationTrackOid User provided location track oid filter, optional.
 * @property locationTrackName User provided location track name filter, optional.
 * @property locationTrackType User provided location track type filter, optional.
 */
@Schema(name = "Pyyntö: Koordinaatista rataosoitteeseen (erämuunnos)")
data class CoordinateToTrackAddressRequestV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_REQUEST_IDENTIFIER)
    @JsonProperty(IDENTIFIER_PARAM)
    val identifier: FrameConverterIdentifierV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_X) val x: Double? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_Y) val y: Double? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_SEARCH_RADIUS, defaultValue = "100")
    @JsonProperty(SEARCH_RADIUS_PARAM)
    val searchRadius: Double? = DEFAULT_SEARCH_RADIUS,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER_OID, type = "string", format = "oid")
    @JsonProperty(TRACK_NUMBER_OID_PARAM)
    val trackNumberOid: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER)
    @JsonProperty(TRACK_NUMBER_NAME_PARAM)
    val trackNumberName: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_OID, type = "string", format = "oid")
    @JsonProperty(LOCATION_TRACK_OID_PARAM)
    val locationTrackOid: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK)
    @JsonProperty(LOCATION_TRACK_NAME_PARAM)
    val locationTrackName: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_TYPE)
    @JsonProperty(LOCATION_TRACK_TYPE_PARAM)
    val locationTrackType: FrameConverterLocationTrackTypeV1? = null,
) : FrameConverterRequestV1() {
    companion object {
        const val DEFAULT_SEARCH_RADIUS = 100.0
    }

    constructor(
        x: Double?,
        y: Double?,
        searchRadius: Double?,
        trackNumberOid: FrameConverterStringV1?,
        trackNumberName: FrameConverterStringV1?,
        locationTrackOid: FrameConverterStringV1?,
        locationTrackName: FrameConverterStringV1?,
        locationTrackType: FrameConverterLocationTrackTypeV1?,
    ) : this(
        identifier = null,
        x = x,
        y = y,
        searchRadius = searchRadius ?: DEFAULT_SEARCH_RADIUS,
        trackNumberOid = trackNumberOid,
        trackNumberName = trackNumberName,
        locationTrackOid = locationTrackOid,
        locationTrackName = locationTrackName,
        locationTrackType = locationTrackType,
    )
}

/**
 * Valid version of the coordinate to track meter request is created during processing, it is not created for an invalid
 * request.
 */
data class ValidCoordinateToTrackAddressRequestV1(
    val identifier: FrameConverterIdentifierV1?,
    val searchCoordinate: FrameConverterCoordinateV1,
    val searchRadius: Double,
    val trackNumberOid: Oid<LayoutTrackNumber>?,
    val trackNumberName: TrackNumber?,
    val locationTrackOid: Oid<LocationTrack>?,
    val locationTrackName: AlignmentName?,
    val locationTrackType: LocationTrackType?,
) : FrameConverterRequestV1()

/**
 * Coordinate to track meter response is only created for a valid, successfully processed request. For invalid requests
 * containing errors, see [GeoJsonFeatureErrorResponseV1].
 */
@Schema(name = "Vastaus: Koordinaatista rataosoitteeseen")
data class CoordinateToTrackAddressResponseV1(
    override val geometry: GeoJsonGeometryPoint,
    override val properties: CoordinateToTrackAddressResponsePropertiesV1,
) : CoordinateToTrackAddressSingleResponseV1

/**
 * @property identifier User provided optional request identifier.
 * @property featureMatchSimple Fields included in the output when [FrameConverterQueryParamsV1.featureBasic] is true
 * @property featureMatchDetails Fields included in the output when [FrameConverterQueryParamsV1.featureDetails] is true
 */
@Schema(name = "Vastauksen tiedot: Koordinaatista rataosoitteeseen")
data class CoordinateToTrackAddressResponsePropertiesV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_IDENTIFIER)
    @JsonProperty(IDENTIFIER_PARAM)
    val identifier: FrameConverterIdentifierV1? = null,
    @JsonUnwrapped val featureMatchSimple: FeatureMatchBasicV1? = null,
    @JsonUnwrapped val featureMatchDetails: FeatureMatchDetailsV1? = null,
) : GeoJsonProperties

/**
 * @property FrameConverterCoordinateV1.x The x coordinate on the alignment of the matched location track ETRS-TM35FIN
 *   (EPSG:3067)
 * @property FrameConverterCoordinateV1.y The y coordinate on the alignment of the matched location track in
 *   ETRS-TM35FIN (EPSG:3067)
 * @property distanceFromRequestPoint Calculated distance from user-specified coordinate in meters.
 */
@Schema(name = "Perustiedot")
data class FeatureMatchBasicV1(
    @JsonUnwrapped val coordinate: FrameConverterCoordinateV1,
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_DISTANCE)
    @JsonProperty("valimatka")
    val distanceFromRequestPoint: Double,
)

/** Returned within properties when [FrameConverterQueryParamsV1.featureDetails] is true */
@Schema(name = "Lisätiedot")
data class FeatureMatchDetailsV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER)
    @JsonProperty("ratanumero")
    val trackNumber: TrackNumber,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER_OID)
    @JsonProperty("ratanumero_oid")
    val trackNumberOid: String,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK)
    @JsonProperty("sijaintiraide")
    val locationTrackName: AlignmentName,
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_LOCATION_TRACK_DESCRIPTION)
    @JsonProperty("sijaintiraide_kuvaus")
    val locationTrackDescription: FreeText,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_TYPE)
    @JsonProperty("sijaintiraide_tyyppi")
    val translatedLocationTrackType: String,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_OID)
    @JsonProperty("sijaintiraide_oid")
    val locationTrackOid: String,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_KILOMETER) @JsonProperty("ratakilometri") val kmNumber: Int,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_METER) @JsonProperty("ratametri") val trackMeter: Int,
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_TRACK_METER_DECIMALS)
    @JsonProperty("ratametri_desimaalit")
    val trackMeterDecimals: Int,
)

/**
 * @property identifier User provided request identifier which is also included in the response feature(s), optional.
 * @property trackNumberName User provided track number, one of "trackNumberName, trackNumberOid" is required.
 * @property trackNumberOid User provided track number oid, one of "trackNumberName, trackNumberOid" is required.
 * @property trackKilometer User provided track kilometer, required for valid requests.
 * @property trackMeter User provided track meter on the specified track kilometer, required for valid requests.
 * @property locationTrackName User provided location track name filter, optional.
 * @property locationTrackOid User provided location track name filter, optional.
 * @property locationTrackType User provided location track type filter, optional.
 */
@Schema(name = "Pyyntö: Rataosoitteesta koordinaatteihin (erämuunnos)")
data class TrackAddressToCoordinateRequestV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_REQUEST_IDENTIFIER)
    @JsonProperty(IDENTIFIER_PARAM)
    val identifier: FrameConverterIdentifierV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER_OID_EXACTLY_ONE, type = "string", format = "oid")
    @JsonProperty(TRACK_NUMBER_OID_PARAM)
    val trackNumberOid: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_NUMBER_EXACTLY_ONE)
    @JsonProperty(TRACK_NUMBER_NAME_PARAM)
    val trackNumberName: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_KILOMETER, required = true)
    @JsonProperty(TRACK_KILOMETER_PARAM)
    val trackKilometer: Int? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_TRACK_METER, required = true)
    @JsonProperty(TRACK_METER_PARAM)
    val trackMeter: BigDecimal? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_OID, type = "string", format = "oid")
    @JsonProperty(LOCATION_TRACK_OID_PARAM)
    val locationTrackOid: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK)
    @JsonProperty(LOCATION_TRACK_NAME_PARAM)
    val locationTrackName: FrameConverterStringV1? = null,
    @Schema(description = FRAME_CONVERTER_OPENAPI_LOCATION_TRACK_TYPE)
    @JsonProperty(LOCATION_TRACK_TYPE_PARAM)
    val locationTrackType: FrameConverterLocationTrackTypeV1? = null,
) : FrameConverterRequestV1()

/**
 * Valid version of the track meter to coordinate request is created during processing, it is not created for an invalid
 * request.
 */
data class ValidTrackAddressToCoordinateRequestV1(
    val identifier: FrameConverterIdentifierV1?,
    val trackNumber: LayoutTrackNumber,
    val trackAddress: TrackMeter,
    val locationTrackOid: Oid<LocationTrack>?,
    val locationTrackName: AlignmentName?,
    val locationTrackType: LocationTrackType?,
) : FrameConverterRequestV1()

/**
 * Track meter to coordinate response is only created for a valid, successfully processed request. For invalid requests
 * containing errors, see [GeoJsonFeatureErrorResponseV1].
 */
@Schema(name = "Vastaus: Rataosoitteesta koordinaatteihin")
data class TrackAddressToCoordinateResponseV1(
    override val geometry: GeoJsonGeometryPoint,
    override val properties: TrackAddressToCoordinateResponsePropertiesV1,
) : TrackAddressToCoordinateSingleResponseV1

@Schema(name = "Vastaus: Erämuunnos rataosoitteesta koordinaatteihin")
data class TrackAddressToCoordinateCollectionResponseV1(
    override val features: List<TrackAddressToCoordinateSingleResponseV1>
) : GeoJsonFeatureCollection

@Schema(name = "Vastaus: Erämuunnos koordinaateista rataosoitteisiin")
data class CoordinateToTrackAddressCollectionResponseV1(
    override val features: List<CoordinateToTrackAddressSingleResponseV1>
) : GeoJsonFeatureCollection

@Schema(
    name = "Vastauksen osa: Erämuunnos rataosoitteesta koordinaatteihin",
    subTypes = [TrackAddressToCoordinateResponseV1::class, GeoJsonFeatureErrorResponseV1::class],
)
interface TrackAddressToCoordinateSingleResponseV1 : GeoJsonFeature

@Schema(
    name = "Vastauksen osa: Erämuunnos koordinaateista rataosoitteisiin",
    subTypes = [CoordinateToTrackAddressResponseV1::class, GeoJsonFeatureErrorResponseV1::class],
)
interface CoordinateToTrackAddressSingleResponseV1 : GeoJsonFeature

/**
 * @property identifier User provided optional request identifier.
 * @property featureMatchBasic Fields included in the output when [FrameConverterQueryParamsV1.featureBasic] is true
 * @property featureMatchDetails Fields included in the output when [FrameConverterQueryParamsV1.featureDetails] is true
 */
@Schema(name = "Vastauksen tiedot: Rataosoitteesta koordinaatteihin")
data class TrackAddressToCoordinateResponsePropertiesV1(
    @Schema(description = FRAME_CONVERTER_OPENAPI_RESPONSE_IDENTIFIER)
    @JsonProperty(IDENTIFIER_PARAM)
    val identifier: FrameConverterIdentifierV1? = null,
    @JsonUnwrapped val featureMatchBasic: FeatureMatchBasicV1? = null,
    @JsonUnwrapped val featureMatchDetails: FeatureMatchDetailsV1? = null,
) : GeoJsonProperties
