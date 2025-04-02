package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import java.time.Instant

const val LOCATION_TRACK_OID_PARAM = "location_track_oid"
const val TRACK_KILOMETER_START_PARAM = "ratakilometri_alku"
const val TRACK_KILOMETER_END_PARAM = "ratakilometri_loppu"
const val COORDINATE_SYSTEM_PARAM = "koordinaatisto"

const val CHANGE_TIME_PARAM = "muutosaika"
const val ADDRESS_POINT_INTERVAL_PARAM = "osoitepistevali"
const val INCLUDE_GEOMETRY_PARAM = "geometriatiedot"

/**
 * @property locationTrackOid Identifier for the location track geometry search. (Required)
 * @property changesAfterTimestamp Timestamp filter. The response data will only include geometry changes which occurred
 *   after the provided timestamp. If the timestamp filter is not provided, the response will include the entire current
 *   geometry regardless of the time when it was created or modified. (Optional)
 * @property trackKilometerStart Track kilometer filter describing the first track kilometer for which geometry should
 *   be returned. (Optional)
 * @property trackKilometerInclusiveEnd Track kilometer filter describing the last inclusive track kilometer for which
 *   geometry should be returned. (Optional)
 * @property coordinateSystem EPSG Code describing the coordinate system which is used for the response geometry data
 *   coordinates. Default: ETRS-TM35FIN (EPSG:3067).
 * @property addressPointInterval Decimal number describing the interval in meters of the track address points on
 *   included in the response geometry. Default: 1 meter.
 * @property includeGeometry Boolean describing if the response should include granular, possibly large amount of
 *   geometry data. Default: false.
 */
data class CenterLineGeometryRequestV1(
    val locationTrackOid: ApiRequestStringV1,
    val changesAfterTimestamp: ApiRequestStringV1?,
    val trackKilometerStart: ApiRequestStringV1?,
    val trackKilometerInclusiveEnd: ApiRequestStringV1?,
    val coordinateSystem: ApiRequestStringV1?,
    val addressPointInterval: ApiRequestStringV1?,
    val includeGeometry: Boolean?,
)

data class TrackKilometerIntervalV1(val start: KmNumber?, val inclusiveEnd: KmNumber?) {
    fun containsKmEndInclusive(kmNumber: KmNumber): Boolean {
        val startsAfterStartKmFilter = start == null || kmNumber >= start
        val endsBeforeEndKmFilter = inclusiveEnd == null || kmNumber <= inclusiveEnd

        return startsAfterStartKmFilter && endsBeforeEndKmFilter
    }
}

data class ValidCenterLineGeometryRequestV1(
    val locationTrackOid: Oid<LocationTrack>,
    val locationTrack: LocationTrack,
    val changesAfterTimestamp: Instant?,
    val trackInterval: TrackKilometerIntervalV1,
    val coordinateSystem: Srid,
    val addressPointInterval: AddressPointInterval,
    val includeGeometry: Boolean,
) {
    companion object {
        val DEFAULT_COORDINATE_SYSTEM = Srid(3067)
        val DEFAULT_ADDRESS_POINT_INTERVAL = AddressPointInterval.ONE_METER
        const val DEFAULT_INCLUDE_GEOMETRY = false
    }

    constructor(
        locationTrackOid: Oid<LocationTrack>,
        locationTrack: LocationTrack,
        changesAfterTimestamp: Instant?,
        trackInterval: TrackKilometerIntervalV1,
        coordinateSystem: Srid?,
        addressPointInterval: AddressPointInterval?,
        includeGeometry: Boolean?,
    ) : this(
        locationTrackOid = locationTrackOid,
        locationTrack = locationTrack,
        changesAfterTimestamp = changesAfterTimestamp,
        trackInterval = trackInterval,
        coordinateSystem = coordinateSystem ?: DEFAULT_COORDINATE_SYSTEM,
        addressPointInterval = addressPointInterval ?: DEFAULT_ADDRESS_POINT_INTERVAL,
        includeGeometry = includeGeometry ?: DEFAULT_INCLUDE_GEOMETRY,
    )
}

abstract class CenterLineGeometryResponseV1

data class CenterLineGeometryResponseOkV1(
    @JsonProperty("ratanumero") val trackNumberName: TrackNumber,
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("oid") val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    @JsonProperty("tyyppi") val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty("tila") val locationTrackState: ExtLocationTrackStateV1,
    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    @JsonProperty("alkusijainti") val startLocation: CenterLineGeometryPointV1,
    @JsonProperty("loppusijainti") val endLocation: CenterLineGeometryPointV1,
    @JsonProperty("koordinaatisto") val coordinateSystem: Srid,
    @JsonProperty("osoitepistevali") val addressPointInterval: AddressPointInterval,
    @JsonProperty("osoitevalit") val trackIntervals: List<CenterLineTrackIntervalV1>,
) : CenterLineGeometryResponseV1()

data class CenterLineGeometryResponseErrorV1(
    @JsonProperty("virheet") val errors: List<CenterLineGeometryTranslatedErrorV1>
) : CenterLineGeometryResponseV1()

data class CenterLineGeometryTranslatedErrorV1(
    @JsonProperty("koodi") val code: Int,
    @JsonProperty("viesti") val message: String,
)

enum class CenterLineGeometryErrorV1(val code: Int, private val localizationSuffix: String) {
    InvalidLocationTrackOid(1, "invalid-location-track-oid"),
    LocationTrackOidNotFound(2, "location-track-oid-not-found"),
    InvalidSrid(3, "invalid-coordinate-system-srid"),
    InvalidTrackKilometerStart(4, "invalid-track-kilometer-start"),
    InvalidTrackKilometerEnd(5, "invalid-track-kilometer-end"),
    InvalidChangeTime(6, "invalid-change-time"),
    InvalidAddressPointInterval(7, "invalid-address-point-interval"),
    OutputCoordinateTransformationFailed(8, "output-coordinate-transformation-failed");

    companion object {
        private const val BASE: String = "ext-api.track-layout.v1.center-line.error"
    }

    val localizationKey: LocalizationKey by lazy { LocalizationKey("$BASE.$localizationSuffix") }

    fun toResponseError(translation: Translation): CenterLineGeometryTranslatedErrorV1 {
        return CenterLineGeometryTranslatedErrorV1(code = this.code, message = translation.t(localizationKey))
    }
}

data class CenterLineTrackIntervalV1(
    @JsonProperty("alku") val startAddress: String,
    @JsonProperty("loppu") val endAddress: String,
    @JsonProperty("pisteet") val addressPoints: List<CenterLineGeometryPointV1>,
)

data class CenterLineGeometryPointV1(
    val x: Double,
    val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: KmNumber,
    @JsonProperty("ratametri") val trackMeter: BigDecimal,
) {
    companion object {
        fun of(addressPoint: AddressPoint): CenterLineGeometryPointV1 {
            return CenterLineGeometryPointV1(
                addressPoint.point.x,
                addressPoint.point.y,
                addressPoint.address.kmNumber,
                addressPoint.address.meters,
            )
        }
    }
}

enum class AddressPointInterval(@JsonValue val value: String) {
    QUARTER_METER("0.25"),
    ONE_METER("1.0");

    companion object {
        fun of(value: String): AddressPointInterval? {
            return entries.find { entry -> entry.value == value }
        }
    }

    override fun toString(): String {
        return this.value
    }
}
