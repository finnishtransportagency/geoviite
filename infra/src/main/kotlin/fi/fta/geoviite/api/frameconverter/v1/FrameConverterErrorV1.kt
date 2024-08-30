package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.util.LocalizationKey

private const val BASE: String = "integration-api.error"

enum class FrameConverterErrorV1(val localizationKey: LocalizationKey) {
    FeaturesNotFound(LocalizationKey("$BASE.features-not-found")),
    MissingXCoordinate(LocalizationKey("$BASE.missing-x-coordinate")),
    MissingYCoordinate(LocalizationKey("$BASE.missing-y-coordinate")),
    MissingTrackKilometer(LocalizationKey("$BASE.missing-track-kilometer")),
    MissingTrackMeter(LocalizationKey("$BASE.missing-track-meter")),
    MissingTrackNumber(LocalizationKey("$BASE.missing-track-number")),
    BadRequest(LocalizationKey("$BASE.bad-request")),
    UnsupportedRequestType(LocalizationKey("$BASE.unsupported-request-type")),
    RequestCouldNotBeDeserialized(LocalizationKey("$BASE.request-could-not-be-deserialized")),
    ListOfJsonRequestsCouldNotBeDeserialized(LocalizationKey("$BASE.list-of-json-requests-could-not-be-deserialized")),
    SearchRadiusUndefined(LocalizationKey("$BASE.search-radius-undefined")),
    SearchRadiusUnderRange(LocalizationKey("$BASE.search-radius-under-range")),
    SearchRadiusOverRange(LocalizationKey("$BASE.search-radius-over-range")),
    AddressGeocodingFailed(LocalizationKey("$BASE.address-geocoding-failed")),
    InvalidTrackNumberName(LocalizationKey("$BASE.invalid-track-number-name")),
    InvalidLocationTrackName(LocalizationKey("$BASE.invalid-location-track-name")),
    InvalidLocationTrackType(LocalizationKey("$BASE.invalid-location-track-type")),
    InvalidResponseSettings(LocalizationKey("$BASE.invalid-response-settings")),
    TrackKilometerUnderRange(LocalizationKey("$BASE.track-kilometer-under-range")),
    TrackKilometerOverRange(LocalizationKey("$BASE.track-kilometer-over-range")),
    TrackMeterUnderRange(LocalizationKey("$BASE.track-meter-under-range")),
    TrackMeterOverRange(LocalizationKey("$BASE.track-meter-over-range")),
    TrackNumberNotFound(LocalizationKey("$BASE.track-number-not-found")),
}
