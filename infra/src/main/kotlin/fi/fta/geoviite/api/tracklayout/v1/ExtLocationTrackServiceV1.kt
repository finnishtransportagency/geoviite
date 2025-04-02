package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    fun locationTrackResponse(oid: Oid<LocationTrack>, coordinateSystem: Srid): ExtLocationTrackResponseV1 {
        return ExtLocationTrackResponseV1(
            trackNetworkVersion =
                publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single().uuid,
            locationTrack = getLocationTrack(MainLayoutContext.official, oid, coordinateSystem),
        )
    }

    fun getLocationTrack(
        layoutContext: LayoutContext,
        oid: Oid<LocationTrack>,
        coordinateSystem: Srid,
    ): ExtLocationTrackV1 {
        val locationTrack =
            locationTrackDao.lookupByExternalId(oid)?.let { layoutRow ->
                locationTrackService.get(layoutContext, layoutRow.id)
            } ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

        val trackNumberName =
            trackNumberService.get(layoutContext, locationTrack.trackNumberId).let(::requireNotNull).number

        val trackNumberOid =
            trackNumberDao.fetchExternalId(layoutContext.branch, locationTrack.trackNumberId).let(::requireNotNull).oid

        val locationTrackDescription =
            locationTrackService
                .getFullDescriptions(layoutContext, listOf(locationTrack), LocalizationLanguage.FI)
                .first()

        val alignmentAddresses =
            geocodingService.getAddressPoints(layoutContext, locationTrack.id as IntId)
                ?: throw ExtGeocodingFailedV1("address points not found, locationTrackId=${locationTrack.id}")

        val (startLocation, endLocation) =
            when (coordinateSystem) {
                LAYOUT_SRID -> alignmentAddresses.startPoint to alignmentAddresses.endPoint
                else -> {
                    val start = layoutAddressPointToCoordinateSystem(alignmentAddresses.startPoint, coordinateSystem)
                    val end = layoutAddressPointToCoordinateSystem(alignmentAddresses.endPoint, coordinateSystem)

                    start to end
                }
            }

        return ExtLocationTrackV1(
            trackNumberName = trackNumberName,
            trackNumberOid = trackNumberOid,
            locationTrackOid = oid,
            locationTrackName = locationTrack.name,
            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
            locationTrackDescription = locationTrackDescription,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
            coordinateSystem = coordinateSystem,
            startLocation = ExtCenterLineGeometryPointV1.of(startLocation),
            endLocation = ExtCenterLineGeometryPointV1.of(endLocation),
        )
    }
}

private fun layoutAddressPointToCoordinateSystem(
    addressPoint: AddressPoint,
    targetCoordinateSystem: Srid,
): AddressPoint {
    val convertedPoint = transformNonKKJCoordinate(LAYOUT_SRID, targetCoordinateSystem, addressPoint.point)
    return addressPoint.copy(point = addressPoint.point.copy(x = convertedPoint.x, y = convertedPoint.y))
}
