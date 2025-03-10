package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.tracklayout.LocationTrack

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
 * @property addressPointIntervalMeters Decimal number describing the interval in meters of the track address points on
 *   included in the response geometry. Default: 1 meter.
 * @property includeGeometry Boolean describing if the response should include granular, possibly large amount of
 *   geometry data. Default: false.
 */
data class CenterLineGeometryRequestV1(
    val locationTrackOid: Oid<LocationTrack>,
    val changesAfterTimestamp: ApiRequestStringV1?,
    val trackKilometerStart: ApiRequestStringV1?,
    val trackKilometerInclusiveEnd: ApiRequestStringV1?,
    val coordinateSystem: ApiRequestStringV1?,
    val addressPointIntervalMeters: ApiRequestStringV1?,
    val includeGeometry: Boolean?,
)

data class TrackKilometerIntervalV1(val start: KmNumber?, val inclusiveEnd: KmNumber?)

data class ValidCenterLineGeometryRequestV1(
    val locationTrackOid: Oid<LocationTrack>
    //    val changesAfterTimestamp: Instant?,
    //    val trackInterval: TrackKilometerIntervalV1,
    //    val coordinateSystem: Srid,
    //    val addressPointIntervalMeters: Double, // TODO Double or something else?
    //    val includeGeometry: Boolean,
) {
    companion object {
        val DEFAULT_COORDINATE_SYSTEM = Srid(3067)
        const val DEFAULT_ADDRESS_POINT_INTERVAL_METERS = 1.0
        const val DEFAULT_INCLUDE_GEOMETRY = false
    }

    //    constructor(
    //        locationTrackOid: Oid<LocationTrack>
    //        //        changesAfterTimestamp: Instant?,
    //        //        trackInterval: TrackKilometerIntervalV1,
    //        //        coordinateSystem: Srid?,
    //        //        addressPointIntervalMeters: Double?,
    //        //        includeGeometry: Boolean?,
    //    ) : this(
    //        locationTrackOid = locationTrackOid
    //        changesAfterTimestamp = changesAfterTimestamp,
    //        trackInterval = trackInterval,
    //        coordinateSystem = coordinateSystem ?: DEFAULT_COORDINATE_SYSTEM,
    //        addressPointIntervalMeters ?: DEFAULT_ADDRESS_POINT_INTERVAL_METERS,
    //        includeGeometry = includeGeometry ?: DEFAULT_INCLUDE_GEOMETRY,
    //    )
}

abstract class CenterLineGeometryResponseV1

// TODO
data class CenterLineGeometryResponseOkV1(
    //    @JsonProperty("ratanumero") val trackNumber: LayoutTrackNumber,
    //    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("oid") val locationTrackOid: Oid<LocationTrack>
    //    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    //    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    //    @JsonProperty("tyyppi") val locationTrackType: ApiLocationTrackType,
    //    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    //    @JsonProperty("alkusijainti") val startLocation: CenterLineGeometryPointV1,
    //    @JsonProperty("loppusijainti") val endLocation: CenterLineGeometryPointV1,
    //    @JsonProperty("osoitepistevali") val addressPointIntervalMeters: Double, // TODO Should this be a string
    // instead?
    //    @JsonProperty("muuttuneet_kilometrit") val trackKilometerGeometry: Map<KmNumber,
    // List<CenterLineGeometryPointV1>>,
) : CenterLineGeometryResponseV1()

data class CenterLineGeometryPointV1(
    val x: Double,
    val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: KmNumber,
    @JsonProperty("ratametri") val trackMeter: TrackMeter, // TODO should not include decimals, might require a new type
    @JsonProperty("ratametri_desimaalit") val trackMeterDecimals: Int,
)

enum class CenterLineGeometryErrorV1(private val errorCode: Int, private val localizationSuffix: String) {
    Something(1, "todo"),
    Something2(2, "todo-2");

    val localizationKey: LocalizationKey by lazy { LocalizationKey("$BASE.$localizationSuffix") }

    companion object {
        private const val BASE: String = "ext-api.track-layout.v1.center-line.error"
    }
}

data class CenterLineGeometryResponseErrorV1(@JsonProperty("virheet") val errors: List<CenterLineGeometryErrorV1>) :
    CenterLineGeometryResponseV1()
