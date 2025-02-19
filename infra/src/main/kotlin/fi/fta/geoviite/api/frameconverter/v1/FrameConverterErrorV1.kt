package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.localization.LocalizationKey

enum class FrameConverterErrorV1(private val localizationSuffix: String) {
    TooManyRequests("too-many-requests"),
    FeaturesNotFound("features-not-found"),
    MissingXCoordinate("missing-x-coordinate"),
    MissingYCoordinate("missing-y-coordinate"),
    MissingTrackKilometer("missing-track-kilometer"),
    MissingTrackMeter("missing-track-meter"),
    MissingTrackNumber("missing-track-number"),
    UnsupportedRequestType("unsupported-request-type"),
    SearchRadiusUndefined("search-radius-undefined"),
    SearchRadiusUnderRange("search-radius-under-range"),
    SearchRadiusOverRange("search-radius-over-range"),
    AddressGeocodingFailed("address-geocoding-failed"),
    InvalidTrackNumberName("invalid-track-number-name"),
    InvalidTrackNumberOid("invalid-track-number-oid"),
    InvalidLocationTrackName("invalid-location-track-name"),
    InvalidLocationTrackOid("invalid-location-track-oid"),
    InvalidLocationTrackType("invalid-location-track-type"),
    InvalidTrackAddress("invalid-track-address"),
    TrackNumberNotFound("track-number-not-found"),
    InputCoordinateTransformationFailed("input-coordinate-transformation-failed");

    val localizationKey: LocalizationKey by lazy { LocalizationKey("$BASE.$localizationSuffix") }

    companion object {
        private const val BASE: String = "ext-api.error"
    }
}
