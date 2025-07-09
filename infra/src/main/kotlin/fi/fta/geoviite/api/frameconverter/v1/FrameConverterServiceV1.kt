package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonFeature
import fi.fta.geoviite.api.frameconverter.geojson.GeoJsonGeometryPoint
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressAndM
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationException
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackCacheHit
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackSpatialCache
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.Either
import fi.fta.geoviite.infra.util.Left
import fi.fta.geoviite.infra.util.Right
import fi.fta.geoviite.infra.util.all
import fi.fta.geoviite.infra.util.processRights
import fi.fta.geoviite.infra.util.produceIf
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class FrameConverterServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
    localizationService: LocalizationService,
) {

    val translation = localizationService.getLocalization(LocalizationLanguage.FI)

    fun coordinatesToTrackAddresses(
        layoutContext: LayoutContext,
        requests: List<ValidCoordinateToTrackAddressRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> =
        processRights(
            requests,
            { request -> getSearchPointInLayoutCoordinates(request).mapRight { point -> request to point } },
            { requestsWithPoints -> coordinatesWithPointsToTrackAddresses(layoutContext, requestsWithPoints, params) },
        )

    private fun coordinatesWithPointsToTrackAddresses(
        layoutContext: LayoutContext,
        requestsWithPoints: List<Pair<ValidCoordinateToTrackAddressRequestV1, IPoint>>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        val spatialCache = locationTrackSpatialCache.get(layoutContext)
        val nearbyTracks =
            requestsWithPoints.map { (request, point) -> spatialCache.getClosest(point, request.searchRadius) }

        val distinctTrackNumberIds = distinctTrackNumberIdsFromCacheHits(nearbyTracks)

        val trackNumbers = trackNumberService.getMany(layoutContext, distinctTrackNumberIds).associateBy { it.id }
        val trackNumberOids = getTrackNumberOids(distinctTrackNumberIds, layoutContext)
        val locationTrackOids = getLocationTrackOids(distinctLocationTrackIdsFromCacheHits(nearbyTracks), layoutContext)

        val closestTracks =
            requestsWithPoints.zip(nearbyTracks) { (request), nearby ->
                nearby.find { (track, _) ->
                    filterByRequest(request, track, locationTrackOids, trackNumbers, trackNumberOids)
                }
            }

        val trackNumberInfo = getTrackNumberInfoForLocationTrackHits(closestTracks, layoutContext)
        val trackOids =
            produceIf(params.featureDetails) { getLocationTrackOids(closestTracks.mapNotNull { it?.track }) }

        return closestTracks
            .mapIndexed { index, trackHit ->
                val (request, _) = requestsWithPoints[index]
                if (trackHit == null) createErrorResponse(request.identifier, FrameConverterErrorV1.FeaturesNotFound)
                else {
                    val trackOid = trackOids?.get(trackHit.track.id)
                    calculateCoordinateToTrackAddressResponse(request, trackHit, trackOid, params, trackNumberInfo)
                }
            }
            .toList()
    }

    private fun getTrackNumberInfoForLocationTrackHits(
        closestTracks: List<LocationTrackCacheHit?>,
        layoutContext: LayoutContext,
    ): Map<IntId<LayoutTrackNumber>, TrackNumberDetails> =
        closestTracks
            .mapNotNull { it?.track?.trackNumberId }
            .distinct()
            .let { trackNumberIds ->
                val trackNumbers = trackNumberService.getMany(layoutContext, trackNumberIds)
                val geocodingContexts =
                    trackNumberIds.associateWith { geocodingService.getGeocodingContext(layoutContext, it) }
                val oids = trackNumberDao.fetchExternalIds(layoutContext.branch, trackNumberIds)
                trackNumbers.associate { trackNumber ->
                    trackNumber.id as IntId to
                        TrackNumberDetails(trackNumber, geocodingContexts[trackNumber.id], oids[trackNumber.id]?.oid)
                }
            }

    private fun getTrackNumberOids(
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
        layoutContext: LayoutContext,
    ): Map<IntId<LayoutTrackNumber>, Oid<LayoutTrackNumber>> {
        return trackNumberDao.fetchExternalIds(layoutContext.branch, trackNumberIds).mapValues { (_, externalId) ->
            externalId.oid
        }
    }

    private fun getLocationTrackOids(
        locationTrackIds: List<IntId<LocationTrack>>,
        layoutContext: LayoutContext,
    ): Map<IntId<LocationTrack>, Oid<LocationTrack>> {
        return locationTrackDao.fetchExternalIds(layoutContext.branch, locationTrackIds).mapValues { (_, externalId) ->
            externalId.oid
        }
    }

    private fun filterByRequest(
        request: ValidCoordinateToTrackAddressRequestV1,
        track: LocationTrack,
        locationTrackOids: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
        trackNumbers: Map<DomainId<LayoutTrackNumber>, LayoutTrackNumber>,
        trackNumberOids: Map<IntId<LayoutTrackNumber>, Oid<LayoutTrackNumber>>,
    ): Boolean =
        all(
            // Spatial cache only returns non-deleted tracks -> no need to check the state here
            {
                request.locationTrackOid?.let { locationTrackOid -> locationTrackOid == locationTrackOids[track.id] }
                    ?: true
            },
            { request.locationTrackName?.let { locationTrackName -> locationTrackName == track.name } ?: true },
            { request.locationTrackType?.let { locationTrackType -> locationTrackType == track.type } ?: true },
            {
                request.trackNumberOid?.let { trackNumberOid -> trackNumberOid == trackNumberOids[track.trackNumberId] }
                    ?: true
            },
            {
                request.trackNumberName?.let { trackNumberName ->
                    val trackNumber = trackNumbers[track.trackNumberId]
                    trackNumber?.number == trackNumberName
                } ?: true
            },
        )

    private fun calculateCoordinateToTrackAddressResponse(
        request: ValidCoordinateToTrackAddressRequestV1,
        closestTrack: LocationTrackCacheHit,
        locationTrackOid: Oid<LocationTrack>?,
        params: FrameConverterQueryParamsV1,
        trackNumberInfo: Map<IntId<LayoutTrackNumber>, TrackNumberDetails>,
    ): List<GeoJsonFeature> {
        val (trackNumberDetails, geocodedAddress) =
            trackNumberInfo.getValue(closestTrack.track.trackNumberId).let { details ->
                details to details.geocodingContext?.getAddressAndM(closestTrack.closestPoint)
            }

        return if (geocodedAddress == null) {
            createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
        } else {
            createCoordinateToTrackAddressResponse(
                request,
                params,
                closestTrack,
                trackNumberDetails,
                geocodedAddress,
                locationTrackOid,
            )
        }
    }

    private data class TrackNumberRequests(
        val trackNumberDetails: TrackNumberDetails,
        val tracksAndGeometries: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
        val trackOids: Map<DomainId<LocationTrack>, Oid<LocationTrack>>?,
        val requests: List<ValidTrackAddressToCoordinateRequestV1>,
    )

    fun trackAddressesToCoordinates(
        layoutContext: LayoutContext,
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        return requests
            .groupBy { it.trackNumber }
            .values
            .let { allTrackNumberRequests ->
                val trackNumberIds = allTrackNumberRequests.map { it.first().trackNumber.id as IntId }
                val trackNumbers = trackNumberService.getMany(layoutContext, trackNumberIds).associateBy { it.id }
                val oids = trackNumberDao.fetchExternalIds(layoutContext.branch, trackNumberIds)
                val geocodingContexts =
                    trackNumberIds.associateWith {
                        geocodingService.getGeocodingContext(layoutContext = layoutContext, trackNumberId = it)
                    }
                allTrackNumberRequests.map { reqs ->
                    val trackNumberId = reqs.first().trackNumber.id
                    val trackNumber = requireNotNull(trackNumbers[trackNumberId])
                    reqs to TrackNumberDetails(trackNumber, geocodingContexts[trackNumberId], oids[trackNumberId]?.oid)
                }
            }
            .map { (trackNumberRequests, trackNumberDetails) ->
                val trackNumberId = trackNumberRequests.first().trackNumber.id as IntId
                val tracksAndGeometries =
                    locationTrackService.listWithGeometries(
                        layoutContext = layoutContext,
                        trackNumberId = trackNumberId,
                        includeDeleted = false,
                    )
                val trackOids =
                    produceIf(params.featureDetails) {
                        getLocationTrackOids(tracksAndGeometries.map { (track) -> track })
                    }
                TrackNumberRequests(trackNumberDetails, tracksAndGeometries, trackOids, trackNumberRequests)
            }
            .parallelStream()
            .map { r ->
                processForwardGeocodingRequestsForTrackNumber(
                    r.trackNumberDetails,
                    r.tracksAndGeometries,
                    r.trackOids,
                    r.requests,
                    params,
                )
            }
            .toList()
            .flatten()
    }

    private fun processForwardGeocodingRequestsForTrackNumber(
        trackNumberDetails: TrackNumberDetails,
        tracksAndGeometries: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
        locationTrackOids: Map<DomainId<LocationTrack>, Oid<LocationTrack>>?,
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<List<GeoJsonFeature>> {
        if (trackNumberDetails.geocodingContext == null) {
            return requests.map { request ->
                createErrorResponse(request.identifier, FrameConverterErrorV1.AddressGeocodingFailed)
            }
        }
        val resultsAndIndicesByTrack =
            tracksAndGeometries
                .parallelStream()
                .map { (locationTrack, geometry) ->
                    processForwardGeocodingRequestsForLocationTrack(
                        requests,
                        locationTrack,
                        geometry,
                        trackNumberDetails.geocodingContext,
                        params,
                        locationTrackOids,
                        trackNumberDetails,
                    )
                }
                .toList()

        val resultsByRequest = List<MutableList<GeoJsonFeature>>(requests.size) { mutableListOf() }
        resultsAndIndicesByTrack.forEach { trackResults ->
            trackResults.forEach { (index, result) -> resultsByRequest[index].add(result) }
        }
        return resultsByRequest.mapIndexed { index, result ->
            result.ifEmpty { createErrorResponse(requests[index].identifier, FrameConverterErrorV1.FeaturesNotFound) }
        }
    }

    private fun processForwardGeocodingRequestsForLocationTrack(
        requests: List<ValidTrackAddressToCoordinateRequestV1>,
        locationTrack: LocationTrack,
        geometry: DbLocationTrackGeometry,
        geocodingContext: GeocodingContext<ReferenceLineM>,
        params: FrameConverterQueryParamsV1,
        locationTrackOids: Map<DomainId<LocationTrack>, Oid<LocationTrack>>?,
        trackNumberDetails: TrackNumberDetails,
    ): List<Pair<Int, TrackAddressToCoordinateResponseV1>> {
        val locationTrackOidLookup =
            requests
                .mapNotNull { request -> request.locationTrackOid }
                .distinct()
                .let { distinctOids -> locationTrackDao.getByExternalIds(MainLayoutContext.official, distinctOids) }
                .entries
                .mapNotNull { (oid, locationTrack) -> locationTrack?.id?.let { id -> id as IntId to oid } }
                .toMap()

        val requestIndicesOnTrack =
            requests.mapIndexedNotNull { index, request ->
                index.takeIf {
                    filterByLocationTrackName(request.locationTrackName, locationTrack) &&
                        filterByLocationTrackType(request.locationTrackType, locationTrack) &&
                        filterByLocationTrackOid(request.locationTrackOid, locationTrack, locationTrackOidLookup)
                }
            }
        val trackAddresses =
            geocodingContext.getTrackLocations(geometry, requests.map { request -> request.trackAddress })
        return requestIndicesOnTrack
            .zip(trackAddresses) { requestIndex, addressPoint ->
                if (addressPoint != null) {
                    val request = requests[requestIndex]
                    val locationTrackInfo = locationTrackOids?.get(locationTrack.id)
                    requestIndex to
                        createTrackAddressToCoordinateResponse(
                            request,
                            params,
                            locationTrack,
                            addressPoint,
                            locationTrackInfo,
                            trackNumberDetails,
                        )
                } else null
            }
            .filterNotNull()
    }

    fun validateCoordinateToTrackAddressRequest(
        request: CoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
    ): Either<List<GeoJsonFeatureErrorResponseV1>, ValidCoordinateToTrackAddressRequestV1> {
        val allowedSearchRadiusRange = 1.0..1000.0

        val basicErrors =
            listOfNotNull(
                produceIf(request.x == null) { FrameConverterErrorV1.MissingXCoordinate },
                produceIf(request.y == null) { FrameConverterErrorV1.MissingYCoordinate },
                produceIf(request.searchRadius == null) { FrameConverterErrorV1.SearchRadiusUndefined },
                produceIf(request.searchRadius != null && request.searchRadius < allowedSearchRadiusRange.start) {
                    FrameConverterErrorV1.SearchRadiusUnderRange
                },
                produceIf(
                    request.searchRadius != null && request.searchRadius > allowedSearchRadiusRange.endInclusive
                ) {
                    FrameConverterErrorV1.SearchRadiusOverRange
                },
            )

        val (mappedLocationTrackTypeOrNull, trackTypeErrors) =
            mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType)

        val (trackNumberNameOrNull, trackNumberNameErrors) =
            request.trackNumberName?.let(::createValidTrackNumberNameOrNull) ?: (null to emptyList())

        val (trackNumberOidOrNull, trackNumberOidErrors) =
            request.trackNumberOid?.let(::createValidTrackNumberOidOrNull) ?: (null to emptyList())

        val (locationTrackNameOrNull, locationTrackNameErrors) =
            request.locationTrackName?.let(::createValidAlignmentNameOrNull) ?: (null to emptyList())

        val (locationTrackOidOrNull, locationTrackOidErrors) =
            request.locationTrackOid?.let(::createValidLocationTrackOidOrNull) ?: (null to emptyList())

        val errors =
            listOf(
                    basicErrors,
                    trackTypeErrors,
                    trackNumberNameErrors,
                    trackNumberOidErrors,
                    locationTrackNameErrors,
                    locationTrackOidErrors,
                )
                .flatten()

        return if (errors.isEmpty())
            Right(
                ValidCoordinateToTrackAddressRequestV1(
                    identifier = request.identifier,
                    searchCoordinate =
                        FrameConverterCoordinateV1(
                            srid = params.coordinateSystem,

                            // Already checked earlier but type-inference is not smart enough =(
                            x = requireNotNull(request.x),
                            y = requireNotNull(request.y),
                        ),
                    searchRadius = requireNotNull(request.searchRadius),
                    trackNumberName = trackNumberNameOrNull,
                    trackNumberOid = trackNumberOidOrNull,
                    locationTrackName = locationTrackNameOrNull,
                    locationTrackOid = locationTrackOidOrNull,
                    locationTrackType = mappedLocationTrackTypeOrNull,
                )
            )
        else Left(createErrorResponse(identifier = request.identifier, errors = errors))
    }

    fun validateTrackAddressToCoordinateRequests(
        requests: List<TrackAddressToCoordinateRequestV1>,
        params: FrameConverterQueryParamsV1,
    ): List<Either<List<GeoJsonFeatureErrorResponseV1>, ValidTrackAddressToCoordinateRequestV1>> {
        val trackNumberOidLookup =
            requests
                .mapNotNull { request -> request.trackNumberOid?.let(::createValidTrackNumberOidOrNull)?.first }
                .let { oids -> trackNumberDao.getByExternalIds(MainLayoutContext.official, oids) }
                .mapValues { (_, layoutTrackNumber) -> layoutTrackNumber?.number }

        val trackNumberNames =
            requests.flatMap { request ->
                listOfNotNull(request.trackNumberName?.let(::createValidTrackNumberNameOrNull)?.first)
            }

        val trackNumberLookup =
            (trackNumberOidLookup.values.filterNotNull() + trackNumberNames).distinct().associateWith { trackNumber ->
                trackNumberService.find(MainLayoutContext.official, trackNumber).firstOrNull {
                    it.state != LayoutState.DELETED
                }
            }

        return requests
            .parallelStream()
            .map { request ->
                validateTrackAddressToCoordinateRequest(request, trackNumberLookup, trackNumberOidLookup)
            }
            .toList()
    }

    fun validateTrackAddressToCoordinateRequest(
        request: TrackAddressToCoordinateRequestV1,
        trackNumberLookup: Map<TrackNumber, LayoutTrackNumber?>,
        trackNumberOidLookup: Map<Oid<LayoutTrackNumber>, TrackNumber?>,
    ): Either<List<GeoJsonFeatureErrorResponseV1>, ValidTrackAddressToCoordinateRequestV1> {
        val basicErrors =
            listOfNotNull(
                produceIf(request.trackNumberName != null && request.trackNumberOid != null) {
                    FrameConverterErrorV1.BothNameAndOidForTrackNumber
                },
                produceIf(request.trackKilometer == null) { FrameConverterErrorV1.MissingTrackKilometer },
                produceIf(request.trackMeter == null) { FrameConverterErrorV1.MissingTrackMeter },
            )

        val (validTrackMeterOrNull, trackMeterErrors) =
            createValidTrackMeterOrNull(request.trackKilometer, request.trackMeter)

        val (mappedLocationTrackTypeOrNull, locationTrackTypeErrors) =
            mapLocationTrackTypeToDomainTypeOrNull(request.locationTrackType)

        val (layoutTrackNumberOrNull, trackNumberErrors) =
            createValidTrackNumberOrNull(
                request.trackNumberName,
                request.trackNumberOid,
                trackNumberLookup,
                trackNumberOidLookup,
            )

        val (locationTrackOidOrNull, locationTrackOidErrors) =
            request.locationTrackOid?.let(::createValidLocationTrackOidOrNull) ?: (null to emptyList())

        val (locationTrackNameOrNull, locationTrackNameErrors) =
            request.locationTrackName?.let(::createValidAlignmentNameOrNull) ?: (null to emptyList())

        val errors =
            listOf(
                    basicErrors,
                    trackMeterErrors,
                    locationTrackTypeErrors,
                    trackNumberErrors,
                    locationTrackOidErrors,
                    locationTrackNameErrors,
                )
                .flatten()

        return if (errors.isEmpty()) {
            Right(
                ValidTrackAddressToCoordinateRequestV1(
                    identifier = request.identifier,
                    trackNumber = requireNotNull(layoutTrackNumberOrNull),
                    trackAddress = requireNotNull(validTrackMeterOrNull),
                    locationTrackOid = locationTrackOidOrNull,
                    locationTrackName = locationTrackNameOrNull,
                    locationTrackType = mappedLocationTrackTypeOrNull,
                )
            )
        } else {
            Left(createErrorResponse(identifier = request.identifier, errors = errors))
        }
    }

    fun createErrorResponse(
        identifier: FrameConverterIdentifierV1?,
        error: FrameConverterErrorV1,
    ): List<GeoJsonFeatureErrorResponseV1> = createErrorResponse(identifier, listOf(error))

    fun createErrorResponse(
        identifier: FrameConverterIdentifierV1?,
        errors: List<FrameConverterErrorV1>,
    ): List<GeoJsonFeatureErrorResponseV1> {
        return listOf(
            GeoJsonFeatureErrorResponseV1(
                identifier = identifier,
                errorMessages = errors.map { error -> translation.t(error.localizationKey) },
            )
        )
    }

    private fun createCoordinateToTrackAddressResponse(
        request: ValidCoordinateToTrackAddressRequestV1,
        params: FrameConverterQueryParamsV1,
        closestTrack: LocationTrackCacheHit,
        trackNumberDetails: TrackNumberDetails,
        geocodedAddress: AddressAndM,
        locationTrackOid: Oid<LocationTrack>?,
    ): List<CoordinateToTrackAddressResponseV1> {
        val featureGeometry = createFeatureGeometry(params, closestTrack.closestPoint)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(params, closestTrack.closestPoint, closestTrack.distance)

        val conversionDetails =
            produceIf(params.featureDetails) {
                createDetailedFeatureMatch(
                    closestTrack.track,
                    trackNumberDetails,
                    geocodedAddress.address,
                    locationTrackOid,
                )
            }

        return listOf(
            CoordinateToTrackAddressResponseV1(
                geometry = featureGeometry,
                properties =
                    CoordinateToTrackAddressResponsePropertiesV1(
                        identifier = request.identifier,
                        featureMatchSimple = featureMatchSimple,
                        featureMatchDetails = conversionDetails,
                    ),
            )
        )
    }

    private fun createTrackAddressToCoordinateResponse(
        request: ValidTrackAddressToCoordinateRequestV1,
        params: FrameConverterQueryParamsV1,
        locationTrack: LocationTrack,
        addressPoint: AddressPoint<*>,
        locationTrackOid: Oid<LocationTrack>?,
        trackNumberDetails: TrackNumberDetails,
    ): TrackAddressToCoordinateResponseV1 {
        val featureGeometry = createFeatureGeometry(params, addressPoint.point)

        val featureMatchSimple =
            createSimpleFeatureMatchOrNull(
                params,
                addressPoint.point,
                distanceToClosestPoint = 0.0, // The point should be directly on the track so there's no distance to it.
            )

        val conversionDetails =
            produceIf(params.featureDetails) {
                createDetailedFeatureMatch(locationTrack, trackNumberDetails, addressPoint.address, locationTrackOid)
            }

        return TrackAddressToCoordinateResponseV1(
            geometry = featureGeometry,
            properties =
                TrackAddressToCoordinateResponsePropertiesV1(
                    identifier = request.identifier,
                    featureMatchBasic = featureMatchSimple,
                    featureMatchDetails = conversionDetails,
                ),
        )
    }

    private fun createDetailedFeatureMatch(
        locationTrack: LocationTrack,
        trackNumberDetails: TrackNumberDetails,
        trackMeter: TrackMeter,
        locationTrackOid: Oid<LocationTrack>?,
    ): FeatureMatchDetailsV1? {
        val (trackMeterIntegers, trackMeterDecimals) = splitBigDecimal(trackMeter.meters)
        val translatedLocationTrackType = translation.enum(locationTrack.type, lowercase = true)
        return FeatureMatchDetailsV1(
            trackNumber = trackNumberDetails.trackNumber.number,
            trackNumberOid = trackNumberDetails.oid?.toString() ?: "",
            locationTrackName = locationTrack.name,
            locationTrackOid = locationTrackOid?.toString() ?: "",
            locationTrackDescription = locationTrack.description,
            translatedLocationTrackType = translatedLocationTrackType,
            kmNumber = trackMeter.kmNumber.number,
            trackMeter = trackMeterIntegers,
            trackMeterDecimals = trackMeterDecimals,
        )
    }

    private fun getSearchPointInLayoutCoordinates(
        request: ValidCoordinateToTrackAddressRequestV1
    ): Either<List<GeoJsonFeatureErrorResponseV1>, IPoint> =
        try {
            Right(
                when (request.searchCoordinate.srid) {
                    LAYOUT_SRID -> request.searchCoordinate
                    else ->
                        transformNonKKJCoordinate(request.searchCoordinate.srid, LAYOUT_SRID, request.searchCoordinate)
                }
            )
        } catch (ex: CoordinateTransformationException) {
            Left(createErrorResponse(request.identifier, FrameConverterErrorV1.InputCoordinateTransformationFailed))
        }

    private data class TrackNumberDetails(
        val trackNumber: LayoutTrackNumber,
        val geocodingContext: GeocodingContext<ReferenceLineM>?,
        val oid: Oid<LayoutTrackNumber>?,
    )

    private fun getLocationTrackOids(
        locationTracks: List<LocationTrack>
    ): Map<DomainId<LocationTrack>, Oid<LocationTrack>>? =
        locationTracks
            .distinctBy { it.id }
            .let { distinctTracks ->
                locationTrackDao.fetchExternalIds(LayoutBranch.main, distinctTracks.map { it.id as IntId }).mapValues {
                    (_, v) ->
                    v.oid
                }
            }
}

private fun createFeatureGeometry(params: FrameConverterQueryParamsV1, point: IPoint): GeoJsonGeometryPoint {
    return if (params.featureGeometry) {
        pointToFrameConverterCoordinate(params, point).let { coordinate ->
            GeoJsonGeometryPoint(coordinates = listOf(coordinate.x, coordinate.y))
        }
    } else {
        GeoJsonGeometryPoint.empty()
    }
}

private fun createSimpleFeatureMatchOrNull(
    params: FrameConverterQueryParamsV1,
    point: AlignmentPoint<*>,
    distanceToClosestPoint: Double,
): FeatureMatchBasicV1? {
    return if (params.featureBasic) {
        FeatureMatchBasicV1(
            coordinate = pointToFrameConverterCoordinate(params, point),
            distanceFromRequestPoint = distanceToClosestPoint,
        )
    } else {
        null
    }
}

private fun filterByLocationTrackName(locationTrackName: AlignmentName?, locationTrack: LocationTrack): Boolean =
    locationTrackName == null || locationTrackName == locationTrack.name

private fun filterByLocationTrackType(locationTrackType: LocationTrackType?, locationTrack: LocationTrack): Boolean {
    return if (locationTrackType == null) {
        true
    } else {
        locationTrackType == locationTrack.type
    }
}

private fun filterByLocationTrackOid(
    oid: Oid<LocationTrack>?,
    locationTrack: LocationTrack,
    locationTrackOids: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
): Boolean {
    return if (oid == null) {
        true
    } else {
        locationTrackOids[locationTrack.id] == oid
    }
}

private fun splitBigDecimal(number: BigDecimal, decimalPlaces: Int = 3): Pair<Int, Int> {
    val wholePart = number.toBigInteger().toInt()
    val fractionalPart = number.subtract(BigDecimal(wholePart))
    val scaledFractionalPart =
        fractionalPart.setScale(decimalPlaces, RoundingMode.DOWN).movePointRight(decimalPlaces).toInt()

    return Pair(wholePart, scaledFractionalPart)
}

private fun mapLocationTrackTypeToDomainTypeOrNull(
    locationTrackType: FrameConverterLocationTrackTypeV1?
): Pair<LocationTrackType?, List<FrameConverterErrorV1>> {
    return when (locationTrackType) {
        null -> null to emptyList()

        FrameConverterLocationTrackTypeV1.MAIN -> LocationTrackType.MAIN to emptyList()
        FrameConverterLocationTrackTypeV1.SIDE -> LocationTrackType.SIDE to emptyList()
        FrameConverterLocationTrackTypeV1.CHORD -> LocationTrackType.CHORD to emptyList()
        FrameConverterLocationTrackTypeV1.TRAP -> LocationTrackType.TRAP to emptyList()

        else -> {
            null to listOf(FrameConverterErrorV1.InvalidLocationTrackType)
        }
    }
}

private fun pointToFrameConverterCoordinate(
    params: FrameConverterQueryParamsV1,
    point: IPoint,
): FrameConverterCoordinateV1 {
    return when (params.coordinateSystem) {
        LAYOUT_SRID -> FrameConverterCoordinateV1(srid = LAYOUT_SRID, x = point.x, y = point.y)

        else ->
            transformNonKKJCoordinate(LAYOUT_SRID, params.coordinateSystem, point).let { transformedPoint ->
                FrameConverterCoordinateV1(
                    srid = params.coordinateSystem,
                    x = transformedPoint.x,
                    y = transformedPoint.y,
                )
            }
    }
}

private fun distinctTrackNumberIdsFromCacheHits(
    nearbyTracks: List<List<LocationTrackCacheHit>>
): List<IntId<LayoutTrackNumber>> {
    return nearbyTracks.flatten().map { cacheHit -> cacheHit.track.trackNumberId }.distinct()
}

private fun distinctLocationTrackIdsFromCacheHits(
    nearbyTracks: List<List<LocationTrackCacheHit>>
): List<IntId<LocationTrack>> {
    return nearbyTracks.flatten().map { cacheHit -> cacheHit.track.id as IntId }.distinct()
}
