package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.localization.LocalizationKey

enum class FrameConverterErrorV1(private val localizationSuffix: String) {
    FeaturesNotFound("features-not-found"),
    MissingXCoordinate("missing-x-coordinate"),
    MissingYCoordinate("missing-y-coordinate"),
    MissingTrackKilometer("missing-track-kilometer"),
    MissingTrackMeter("missing-track-meter"),
    MissingTrackNumber("missing-track-number"),
    BadRequest("bad-request"),
    UnsupportedRequestType("unsupported-request-type"),
    RequestCouldNotBeDeserialized("request-could-not-be-deserialized"),
    ListOfJsonRequestsCouldNotBeDeserialized("list-of-json-requests-could-not-be-deserialized"),
    SearchRadiusUndefined("search-radius-undefined"),
    SearchRadiusUnderRange("search-radius-under-range"),
    SearchRadiusOverRange("search-radius-over-range"),
    AddressGeocodingFailed("address-geocoding-failed"),
    InvalidTrackNumberName("invalid-track-number-name"),
    InvalidLocationTrackName("invalid-location-track-name"),
    InvalidLocationTrackType("invalid-location-track-type"),
    InvalidResponseSettings("invalid-response-settings"),
    InvalidTrackAddress("invalid-track-address"),
    TrackNumberNotFound("track-number-not-found");

    val localizationKey: LocalizationKey
        get() = LocalizationKey("$BASE.$localizationSuffix")

    companion object {
        private const val BASE: String = "ext-api.error"
    }
}
