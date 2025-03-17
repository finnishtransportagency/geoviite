package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.GeometryChangeRanges
import fi.fta.geoviite.infra.publication.getChangedGeometryRanges
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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
            request.trackKilometerStart?.let { startKm ->
                validateTrackKilometer(startKm, CenterLineGeometryErrorV1.InvalidTrackKilometerStart)
            } ?: (null to emptyList())

        val (trackKmNumberEnd, trackKmNumberEndErrors) =
            request.trackKilometerInclusiveEnd?.let { endKm ->
                validateTrackKilometer(endKm, CenterLineGeometryErrorV1.InvalidTrackKilometerEnd)
            } ?: (null to emptyList())

        val (coordinateSystem, coordinateSystemErrors) =
            request.coordinateSystem?.let(::validateCoordinateSystem) ?: (null to emptyList())

        val (addressPointInterval, addressPointIntervalErrors) =
            request.addressPointInterval?.let(::validateAddressPointInterval) ?: (null to emptyList())

        val allErrors =
            listOf(
                    locationTrackOidErrors,
                    locationTrackErrors,
                    coordinateSystemErrors,
                    trackKmNumberStartErrors,
                    trackKmNumberEndErrors,
                    changesAfterTimestampErrors,
                    addressPointIntervalErrors,
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
                    addressPointInterval = addressPointInterval,
                    coordinateSystem = coordinateSystem,
                    includeGeometry = request.includeGeometry,
                )
        }
    }

    fun process(
        request: ValidCenterLineGeometryRequestV1
    ): Pair<CenterLineGeometryResponseV1?, List<CenterLineGeometryErrorV1>> {
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

        val alignmentAddresses =
            geocodingService.getAddressPoints(
                layoutContext,
                request.locationTrack.id as IntId,
                when (request.addressPointInterval) {
                    AddressPointInterval.ONE_METER -> Resolution.ONE_METER
                    AddressPointInterval.QUARTER_METER -> Resolution.QUARTER_METER
                },
            )

        checkNotNull(alignmentAddresses) // TODO Better error

        // TODO Supporting the custom interval in this will require a bunch of work due to address point interval being
        // in 1 meter in the cache.
        //
        // TODO This will require even more work due to having to get the differing address points based on the specific
        // alignment versions, specified by the optional change time submitted by the user. Also deleted kilometers
        // should be displayed as empty arrays in the result map.
        val (fullTrackIntervals, midPointErrors) =
            if (request.includeGeometry) {
                createTrackIntervals(alignmentAddresses, request.coordinateSystem, request.trackInterval)
            } else {
                emptyList<CenterLineTrackIntervalV1>() to emptyList()
            }

        val (convertedStartLocation, startLocationConversionErrors) =
            convertAddressPointToRequestCoordinateSystem(request.coordinateSystem, alignmentAddresses.startPoint)

        val (convertedEndLocation, endLocationConversionErrors) =
            convertAddressPointToRequestCoordinateSystem(request.coordinateSystem, alignmentAddresses.endPoint)

        val errors = listOf(startLocationConversionErrors, endLocationConversionErrors, midPointErrors).flatten()

        // TODO Move up so that intervals are not found twice, get change time from request instead
        // TODO Also check includegeometry also for this.
        //        val changeTime = Instant.parse("2024-06-01T01:00:00.000000000Z")

        val trackIntervals =
            if (request.changesAfterTimestamp != null) {
                findModifiedTrackIntervals(request, request.locationTrack.id, request.changesAfterTimestamp)
            } else {
                fullTrackIntervals
            }

        return if (errors.isNotEmpty()) {
            null to errors
        } else {
            CenterLineGeometryResponseOkV1(
                trackNumberName = trackNumberName,
                trackNumberOid = trackNumberOid,
                locationTrackOid = request.locationTrackOid,
                locationTrackName = request.locationTrack.name,
                locationTrackType = ApiLocationTrackType(request.locationTrack.type),
                locationTrackState = ApiLocationTrackState(request.locationTrack.state),
                locationTrackDescription = locationTrackDescription,
                locationTrackOwner = locationTrackService.getLocationTrackOwner(request.locationTrack.ownerId).name,
                addressPointInterval = request.addressPointInterval,
                coordinateSystem = request.coordinateSystem,
                startLocation = CenterLineGeometryPointV1.of(convertedStartLocation.let(::requireNotNull)),
                endLocation = CenterLineGeometryPointV1.of(convertedEndLocation.let(::requireNotNull)),
                trackIntervals = trackIntervals,
            ) to emptyList()
        }
    }

    fun findModifiedTrackIntervals(
        request: ValidCenterLineGeometryRequestV1, // TODO Not needed here
        locationTrackId: IntId<LocationTrack>,
        afterMoment: Instant,
    ): List<CenterLineTrackIntervalV1> {
        //        val locationTrackId = IntId<LocationTrack>(286)
        val moment = Instant.parse("2024-06-01T01:00:00.000000000Z") // TODO

        val versionAtMoment = locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, locationTrackId, moment)

        val mainOfficial = LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)

        val newestOfficialVersion = locationTrackDao.fetchVersion(mainOfficial, locationTrackId)

        val track1 = locationTrackService.getWithAlignment(versionAtMoment!!) // TODO Remove "!!"
        val track2 = locationTrackService.getWithAlignment(newestOfficialVersion!!)

        val changedGeometryRanges =
            getChangedGeometryRanges(track1?.second?.segments ?: emptyList(), track2?.second?.segments ?: emptyList())

        // sitten haetaan ne pisteet, jotka osuvat yllä määritellyille osoiteväleille
        // geocodingService.getAddressPoints(mainOfficial)

        // TODO Laske osoitevälit joissa on poistoja (eli lisätty väli ei kata poistettua osoiteväliä)
        // => Uusimmasta alignmentista voidaan hakea "added"-osoiteväleille addressPointit
        // => Tyhjät listat poistetuille osoiteväleille
        // => Diffaus done?

        //        val changedTrackMeterRanges =
        //            listOf(changedGeometryRanges.added, trimOverlappingRemovedGeometryRanges(changedGeometryRanges))
        //                .flatten()
        //                .sortedBy { it.min } // TODO Tämä ei enää tiedä mitkä ovat minkäkin tyyppisiä rangeja
        // TODO Trimmaa alle kysytyn välin mittaiset ranget, eg. 1m?

        //        val firstMeter = changedTrackMeterRanges.firstOrNull()?.min
        //        val lastMeter = changedTrackMeterRanges.lastOrNull()?.min

        val addressPoints = geocodingService.getAddressPoints(mainOfficial, locationTrackId)!! // TODO Remove "!!"

        //        val changedAddressPoints =
        //            addressPoints.allPoints.filter { addressPoint ->
        //                (firstMeter == null || addressPoint.point.m >= firstMeter) &&
        //                    (lastMeter == null || addressPoint.point.m <= lastMeter) &&
        //                    changedTrackMeterRanges.any { range -> range.contains(addressPoint.point.m) }
        //            }

        val addedOrModifiedIntervals =
            addressPoints.allPoints // TODO Kaikkia näitä ei tarvii käydä varmaan läpi
                .groupBy { addressPoint ->
                    // TODO Optimoi tämä siten että se etsii ensimmäisen ja viimeisen indeksin eikä käy koko
                    // interval-listaa aina läpi.
                    changedGeometryRanges.added.firstOrNull { range -> addressPoint.point.m in range.min..range.max }
                }
                .filterKeys { it != null }
                .map { (trackMeterRange, intervalAddressPoints) ->
                    val startAddress =
                        intervalAddressPoints.firstOrNull()?.address
                            ?: geocodingService.getAddress(
                                mainOfficial,
                                request.locationTrack.trackNumberId,
                                requireNotNull(trackMeterRange).min,
                            )

                    val endAddress =
                        intervalAddressPoints.lastOrNull()?.address
                            ?: geocodingService.getAddress(
                                mainOfficial,
                                request.locationTrack.trackNumberId,
                                requireNotNull(trackMeterRange).max,
                            )

                    CenterLineTrackIntervalV1(
                        // TODO This might error out if there are no points
                        startAddress = startAddress.toString(),
                        endAddress = endAddress.toString(),
                        addressPoints = intervalAddressPoints.map(CenterLineGeometryPointV1::of),
                    )

                    // TODO
                    //                    convertAddressPointsToRequestCoordinateSystem(coordinateSystem,
                    // filteredPoints)
                }

        val removedIntervals =
            trimOverlappingRemovedGeometryRanges(changedGeometryRanges).map { trackMeterRange ->
                val startAddress =
                    geocodingService.getAddress(mainOfficial, request.locationTrack.trackNumberId, trackMeterRange.min)

                val endAddress =
                    geocodingService.getAddress(mainOfficial, request.locationTrack.trackNumberId, trackMeterRange.max)

                CenterLineTrackIntervalV1(
                    // TODO This might error out if there are no points
                    startAddress = startAddress.toString(),
                    endAddress = endAddress.toString(),
                    addressPoints = listOf(),
                )
            }

        val asd =
            listOf((addedOrModifiedIntervals + removedIntervals)).flatten().sortedBy { interval ->
                interval.startAddress
            }

        return asd

        // TODO Filter
        //        val filteredPoints =
        //            alignmentAddresses.allPoints.filter {
        // trackIntervalFilter.containsKmEndInclusive(it.address.kmNumber) }

        //        val (convertedMidPoints, conversionErrors) =
        //            convertAddressPointsToRequestCoordinateSystem(coordinateSystem, filteredPoints)

        //        val intervals =
        //            listOf(
        //                CenterLineTrackIntervalV1(
        //                    startAddress = alignmentAddresses.startPoint.address.toString(),
        //                    endAddress = alignmentAddresses.endPoint.address.toString(),
        //                    addressPoints = convertedMidPoints?.map(CenterLineGeometryPointV1::of) ?: emptyList(),
        //                )
        //            )

        //        return asd2
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
    } catch (ex: IllegalArgumentException) {
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

fun validateAddressPointInterval(
    maybeAddressPointInterval: ApiRequestStringV1
): Pair<AddressPointInterval?, List<CenterLineGeometryErrorV1>> {
    val parsedAddressPointInterval = AddressPointInterval.of(maybeAddressPointInterval.toString())

    return if (parsedAddressPointInterval == null) {
        null to listOf(CenterLineGeometryErrorV1.InvalidAddressPointInterval)
    } else {
        parsedAddressPointInterval to emptyList()
    }
}

fun convertAddressPointToRequestCoordinateSystem(
    targetCoordinateSystem: Srid,
    addressPoint: AddressPoint,
): Pair<AddressPoint?, List<CenterLineGeometryErrorV1>> {
    return when (targetCoordinateSystem) {
        LAYOUT_SRID -> addressPoint to emptyList()
        else -> {
            try {
                val convertedPoint = transformNonKKJCoordinate(LAYOUT_SRID, targetCoordinateSystem, addressPoint.point)
                val convertedAddressPoint =
                    addressPoint.copy(point = addressPoint.point.copy(x = convertedPoint.x, y = convertedPoint.y))

                convertedAddressPoint to emptyList()
            } catch (ex: CoordinateTransformationException) {
                return null to listOf(CenterLineGeometryErrorV1.OutputCoordinateTransformationFailed)
            }
        }
    }
}

fun convertAddressPointsToRequestCoordinateSystem(
    targetCoordinateSystem: Srid,
    addressPoints: List<AddressPoint>,
): Pair<List<AddressPoint>?, List<CenterLineGeometryErrorV1>> {
    return when (targetCoordinateSystem) {
        LAYOUT_SRID -> addressPoints to emptyList()
        else -> {
            val convertedPointsAndErrors =
                addressPoints.map { addressPoint ->
                    convertAddressPointToRequestCoordinateSystem(targetCoordinateSystem, addressPoint)
                }

            if (convertedPointsAndErrors.any { (_, errors) -> errors.isNotEmpty() }) {
                return null to listOf(CenterLineGeometryErrorV1.OutputCoordinateTransformationFailed)
            } else {
                convertedPointsAndErrors.map { (addressPoint, _) -> requireNotNull(addressPoint) } to emptyList()
            }
        }
    }
}

fun createTrackIntervals(
    alignmentAddresses: AlignmentAddresses,
    coordinateSystem: Srid,
    trackIntervalFilter: TrackKilometerIntervalV1,
): Pair<List<CenterLineTrackIntervalV1>, List<CenterLineGeometryErrorV1>> {

    val filteredPoints =
        alignmentAddresses.allPoints.filter { trackIntervalFilter.containsKmEndInclusive(it.address.kmNumber) }

    val (convertedMidPoints, conversionErrors) =
        convertAddressPointsToRequestCoordinateSystem(coordinateSystem, filteredPoints)

    val intervals =
        listOf(
            CenterLineTrackIntervalV1(
                startAddress = alignmentAddresses.startPoint.address.toString(),
                endAddress = alignmentAddresses.endPoint.address.toString(),
                addressPoints = convertedMidPoints?.map(CenterLineGeometryPointV1::of) ?: emptyList(),
            )
        )

    return intervals to conversionErrors
}

fun trimOverlappingRemovedGeometryRanges(geometryChangeRanges: GeometryChangeRanges): List<Range<Double>> {
    return geometryChangeRanges.removed.mapNotNull { removedRange ->
        var trimmedRange: Range<Double> = removedRange
        var completelyReplaced = false

        for (addedRange in geometryChangeRanges.added) {
            if (addedRange.contains(removedRange)) {
                completelyReplaced = true
                break
            }

            if (addedRange.min <= trimmedRange.max && trimmedRange.max <= addedRange.max) {
                // Removal overlaps the added interval at the start => trim end of removal interval
                trimmedRange = Range(min = trimmedRange.min, max = addedRange.min)
            }

            if (addedRange.min <= trimmedRange.min && trimmedRange.min <= addedRange.max) {
                // Removal overlaps the added interval at the end => trim start of removal interval
                trimmedRange = Range(min = addedRange.max, max = trimmedRange.max)
            }
        }

        if (completelyReplaced) {
            null
        } else {
            trimmedRange
        }
    }
}
