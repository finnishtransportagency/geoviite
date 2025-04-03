package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackServiceV1
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    fun locationTrackResponse(
        oid: Oid<LocationTrack>,
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtLocationTrackResponseV1 {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrack =
            locationTrackDao
                .lookupByExternalId(oid)
                ?.let { layoutRowId ->
                    locationTrackDao.fetchOfficialVersionAtMoment(
                        layoutContext.branch,
                        layoutRowId.id,
                        publication.publicationTime,
                    )
                }
                ?.let(locationTrackDao::fetch)
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

        return ExtLocationTrackResponseV1(
            trackNetworkVersion = publication.uuid,
            locationTrack =
                getExtLocationTrack(
                    oid,
                    locationTrack,
                    MainLayoutContext.official,
                    publication.publicationTime,
                    coordinateSystem,
                ),
        )
    }

    fun locationTrackModificationResponse(
        oid: Oid<LocationTrack>,
        modificationsFromVersion: Uuid<Publication>,
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtModifiedLocationTrackResponseV1? {
        val layoutContext = MainLayoutContext.official

        val previousPublication = publicationDao.fetchPublicationByUuid(modificationsFromVersion).let(::requireNotNull)
        val nextPublication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        return if (previousPublication == nextPublication) {
            null
        } else {
            // Katso onko versio vaihtunut
            val locationTrackId =
                locationTrackDao.lookupByExternalId(oid)?.id
                    ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

            val previousLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    previousPublication.publicationTime,
                )

            val nextLocationTrackVersion =
                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
                    layoutContext.branch,
                    locationTrackId,
                    nextPublication.publicationTime,
                )

            if (previousLocationTrackVersion == nextLocationTrackVersion) {
                return null
            } else {
                return ExtModifiedLocationTrackResponseV1(
                    modificationsFromVersion = modificationsFromVersion,
                    trackNetworkVersion = nextPublication.uuid,
                    locationTrack =
                        getExtLocationTrack(
                            oid,
                            locationTrackDao.fetch(nextLocationTrackVersion),
                            MainLayoutContext.official,
                            nextPublication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
        }
    }

    fun getExtLocationTrack(
        oid: Oid<LocationTrack>,
        locationTrack: LocationTrack,
        layoutContext: LayoutContext,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtLocationTrackV1 {
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

        // TODO Better error?
        val trackNumberName =
            layoutTrackNumberDao
                .fetchOfficialVersionAtMoment(layoutContext.branch, locationTrack.trackNumberId, moment)
                .let(::requireNotNull)
                .let(layoutTrackNumberDao::fetch)
                .let(::requireNotNull)
                .number

        val trackNumberOid =
            layoutTrackNumberDao
                .fetchExternalId(layoutContext.branch, locationTrack.trackNumberId)
                .let(::requireNotNull)
                .oid

        return ExtLocationTrackV1(
            locationTrackOid = oid,
            locationTrackName = locationTrack.name,
            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
            locationTrackDescription = locationTrackDescription,
            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
            coordinateSystem = coordinateSystem,
            startLocation = ExtCenterLineGeometryPointV1.of(startLocation),
            endLocation = ExtCenterLineGeometryPointV1.of(endLocation),
            trackNumberName = trackNumberName,
            trackNumberOid = trackNumberOid,
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
