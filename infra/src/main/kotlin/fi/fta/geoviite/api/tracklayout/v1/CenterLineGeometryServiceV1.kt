package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import java.time.Instant
import java.time.format.DateTimeParseException
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class CenterLineGeometryServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    //    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    localizationService: LocalizationService,
) {
    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun validate(
        request: CenterLineGeometryRequestV1
    ): Pair<List<CenterLineGeometryErrorV1>, ValidCenterLineGeometryRequestV1?> {
        val (locationTrackOid, locationTrackOidErrors) = validateLocationTrackOid(request.locationTrackOid)

        val (locationTrack, locationTrackErrors) =
            locationTrackOid?.let(locationTrackDao::getByExternalId)?.let { locationTrack ->
                locationTrack to emptyList()
            } ?: (null to listOf(CenterLineGeometryErrorV1.LocationTrackOidNotFound))

        val (changesAfterTimestamp, changesAfterTimestampErrors) =
            request.changesAfterTimestamp?.let(::validateChangesAfterTimestamp) ?: (null to emptyList())

        val (trackKmNumberStart, trackKmNumberStartErrors) =
            request.trackKilometerStart?.let { km ->
                validateTrackKilometer(km, CenterLineGeometryErrorV1.InvalidTrackKilometerStart)
            } ?: (null to emptyList())

        val (trackKmNumberEnd, trackKmNumberEndErrors) =
            request.trackKilometerStart?.let { km ->
                validateTrackKilometer(km, CenterLineGeometryErrorV1.InvalidTrackKilometerEnd)
            } ?: (null to emptyList())

        val (coordinateSystem, coordinateSystemErrors) =
            request.coordinateSystem?.let(::validateCoordinateSystem) ?: (null to emptyList())

        val allErrors =
            listOf(
                    locationTrackOidErrors,
                    locationTrackErrors,
                    coordinateSystemErrors,
                    trackKmNumberStartErrors,
                    trackKmNumberEndErrors,
                    changesAfterTimestampErrors,
                )
                .flatten()

        return if (allErrors.isNotEmpty()) {
            return allErrors to null
        } else {
            emptyList<CenterLineGeometryErrorV1>() to
                ValidCenterLineGeometryRequestV1(
                    locationTrackOid = requireNotNull(locationTrackOid),
                    locationTrack = requireNotNull(locationTrack),
                    changesAfterTimestamp = changesAfterTimestamp,
                    trackInterval = TrackKilometerIntervalV1(trackKmNumberStart, trackKmNumberEnd),
                    addressPointIntervalMeters = request.addressPointIntervalMeters,
                    coordinateSystem = coordinateSystem,
                    includeGeometry = request.includeGeometry,
                )
        }
    }

    fun process(request: ValidCenterLineGeometryRequestV1): CenterLineGeometryResponseV1 {
        val layoutContext = MainLayoutContext.official

        val trackNumberName =
            trackNumberService.get(layoutContext, request.locationTrack.trackNumberId).let(::requireNotNull).number

        val trackNumberOid =
            trackNumberDao
                .fetchExternalId(layoutContext.branch, request.locationTrack.trackNumberId)
                .let(::requireNotNull)
                .oid

        val locationTrackDescription =
            locationTrackService
                .getFullDescriptions(layoutContext, listOf(request.locationTrack), LocalizationLanguage.FI)
                .first()

        val alignmentAddresses = geocodingService.getAddressPoints(layoutContext, request.locationTrack.id as IntId)
        checkNotNull(alignmentAddresses) // TODO Better error

        // TODO Supporting the custom interval in this will require a bunch of work due to address point interval being
        // in 1 meter in the cache.
        //
        // TODO This will require even more work due to having to get the differing address points based on the specific
        // alignment versions, specified by the optional change time submitted by the user. Also deleted kilometers
        // should be displayed as empty arrays in the result map.
        val midPoints =
            if (request.includeGeometry) {
                alignmentAddresses.midPoints.groupBy(
                    keySelector = { addressPoint -> addressPoint.address.kmNumber },

                    // TODO This call should probably include the coordinate system and/or convert coordinates during it
                    // or as parameters.
                    valueTransform = { addressPoint -> CenterLineGeometryPointV1.of(addressPoint) },
                )
            } else {
                emptyMap()
            }

        return CenterLineGeometryResponseOkV1(
            trackNumberName = trackNumberName,
            trackNumberOid = trackNumberOid,
            locationTrackOid = request.locationTrackOid,
            locationTrackName = request.locationTrack.name,
            locationTrackType = ApiLocationTrackType(request.locationTrack.type),
            locationTrackDescription = locationTrackDescription,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(request.locationTrack.ownerId).name,
            addressPointIntervalMeters = request.addressPointIntervalMeters,
            coordinateSystem = request.coordinateSystem,
            startLocation = CenterLineGeometryPointV1.of(alignmentAddresses.startPoint),
            endLocation = CenterLineGeometryPointV1.of(alignmentAddresses.endPoint),
            trackKilometerGeometry = midPoints,
        )
    }
}

fun validateLocationTrackOid(
    maybeLocationTrackOid: ApiRequestStringV1
): Pair<Oid<LocationTrack>?, List<CenterLineGeometryErrorV1>> {
    return try {
        Oid<LocationTrack>(maybeLocationTrackOid.value) to emptyList()
    } catch (ex: InputValidationException) {
        null to listOf(CenterLineGeometryErrorV1.InvalidLocationTrackOid)
    }
}

fun validateCoordinateSystem(maybeSrid: ApiRequestStringV1): Pair<Srid?, List<CenterLineGeometryErrorV1>> {
    return try {
        Srid(maybeSrid.value) to emptyList()
    } catch (ex: InputValidationException) {
        null to listOf(CenterLineGeometryErrorV1.InvalidSrid)
    }
}

fun validateTrackKilometer(
    maybeStartTrackKilometer: ApiRequestStringV1,
    error: CenterLineGeometryErrorV1,
): Pair<KmNumber?, List<CenterLineGeometryErrorV1>> {
    return try {
        KmNumber(maybeStartTrackKilometer.value) to emptyList()
    } catch (ex: InputValidationException) {
        null to listOf(error)
    }
}

fun validateChangesAfterTimestamp(
    maybeChangesAfterTimestamp: ApiRequestStringV1
): Pair<Instant?, List<CenterLineGeometryErrorV1>> {
    return try {
        Instant.parse(maybeChangesAfterTimestamp.value) to emptyList()
    } catch (ex: DateTimeParseException) {
        null to listOf(CenterLineGeometryErrorV1.InvalidChangeTime)
    }
}
