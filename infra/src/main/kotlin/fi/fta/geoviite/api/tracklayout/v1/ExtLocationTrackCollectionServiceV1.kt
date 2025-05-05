package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Schema(name = "Vastaus: Sijaintiraidejoukko")
data class ExtLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty("sijaintiraiteet") val locationTrackCollection: List<ExtLocationTrackV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidejoukko")
data class ExtModifiedLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty("sijaintiraiteet") val locationTrackCollection: List<ExtLocationTrackV1>,
)

@GeoviiteService
class ExtLocationTrackCollectionServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createLocationTrackCollectionResponse(
        trackNetworkVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtLocationTrackCollectionResponseV1 {
        val layoutContext = MainLayoutContext.official

        val (publication, locationTracks) =
            when (trackNetworkVersion) {
                null -> {
                    publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single().let {
                        newestPublication ->
                        newestPublication to locationTrackDao.list(layoutContext, includeDeleted = false)
                    }
                }
                else -> {
                    trackNetworkVersion
                        .let { uuid -> publicationDao.fetchPublicationByUuid(uuid) }
                        .let(::requireNotNull)
                        .let { specifiedPublication ->
                            specifiedPublication to
                                locationTrackDao.listPublishedLocationTracksAtMoment(
                                    specifiedPublication.publicationTime
                                )
                        }
                }
            }

        //        val locationTrackStartAndEnd = locationTrackService.getStartAndEnd()

        val moment = publication.publicationTime

        val locationTrackIds = locationTracks.map { locationTrack -> locationTrack.id as IntId }
        val distinctTrackNumberIds = locationTracks.map { locationTrack -> locationTrack.trackNumberId }.distinct()

        val trackNumbers =
            distinctTrackNumberIds
                .map { trackNumberId ->
                    layoutTrackNumberDao
                        // TODO Batch call instead of loop
                        .fetchOfficialVersionAtMoment(layoutContext.branch, trackNumberId, moment)
                        ?.let(layoutTrackNumberDao::fetch)
                        ?: throw ExtTrackNumberNotFoundV1(
                            "track number was not found for " +
                                "branch=${layoutContext.branch}, trackNumberId=${trackNumberId}, moment=$moment"
                        )
                }
                .associateBy { trackNumber -> trackNumber.id }

        val externalLocationTrackIds = locationTrackDao.fetchExternalIds(layoutContext.branch, locationTrackIds)
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(layoutContext.branch, distinctTrackNumberIds)

        val mockLocation =
            ExtAddressPointV1.of(AddressPoint(AlignmentPoint(0.0, 0.0, null, 1.0, null), TrackMeter("0000+0000")))

        // TODO Versionize/momentize
        val locationTrackDescriptions =
            locationTrackService.getFullDescriptions(layoutContext, locationTracks, LocalizationLanguage.FI)

        val locationTrackStartsAndEnds =
            locationTrackService.getStartAndEndAtMoment(layoutContext, locationTrackIds, publication.publicationTime)

        require(locationTracks.size == locationTrackDescriptions.size) {
            "locationTracks.size=${locationTracks.size} != locationTrackDescriptions.size=${locationTrackDescriptions.size}"
        }

        require(locationTracks.size == locationTrackStartsAndEnds.size) {
            "locationTracks.size=${locationTracks.size} != locationTrackStartsAndEnds.size=${locationTrackStartsAndEnds.size}"
        }

        return ExtLocationTrackCollectionResponseV1(
            trackNetworkVersion = publication.uuid,
            locationTrackCollection =
                locationTracks.mapIndexedNotNull { index, locationTrack ->
                    val locationTrackDescription = locationTrackDescriptions[index]
                    val (startLocation, endLocation) = // TODO Requires coordinate system conversion
                        locationTrackStartsAndEnds.getOrNull(index)?.let { startAndEnd ->
                            startAndEnd.start to startAndEnd.end
                        } ?: (null to null)

                    if (startLocation == null || endLocation == null) { // TODO These can actually be null
                        print("get start and end points failed for ${locationTrack.id}")
                        null
                    } else {

                        //                            val (startLocation, endLocation) =
                        //                                when (coordinateSystem) {
                        //                                    LAYOUT_SRID -> alignmentAddresses.startPoint to
                        // alignmentAddresses.endPoint
                        //                                    else -> {
                        //                                        val start =
                        //                                            layoutAddressPointToCoordinateSystem(
                        //                                                alignmentAddresses.startPoint,
                        //                                                coordinateSystem,
                        //                                            )
                        //                                        val end =
                        //                                            layoutAddressPointToCoordinateSystem(
                        //                                                alignmentAddresses.endPoint,
                        //                                                coordinateSystem,
                        //                                            )
                        //
                        //                                        start to end
                        //                                    }
                        //                                }

                        // TODO Versionize and batch
                        ExtLocationTrackV1(
                            locationTrackOid =
                                externalLocationTrackIds[locationTrack.id]?.oid
                                    ?: throw ExtOidNotFoundExceptionV1(
                                        "location track oid not found, locationTrackId=${locationTrack.id}"
                                    ),
                            locationTrackName = locationTrack.name,
                            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
                            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
                            locationTrackDescription = locationTrackDescription,
                            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
                            coordinateSystem = coordinateSystem,
                            startLocation = ExtAddressPointV1.of(startLocation),
                            endLocation = ExtAddressPointV1.of(endLocation),
                            //                            startLocation = mockLocation,
                            //                            endLocation = mockLocation,
                            trackNumberName =
                                trackNumbers[locationTrack.trackNumberId]?.number
                                    ?: throw ExtTrackNumberNotFoundV1(
                                        "track number was not found for " +
                                            "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}, moment=$moment"
                                    ),
                            trackNumberOid =
                                externalTrackNumberIds[locationTrack.trackNumberId]?.oid
                                    ?: throw ExtOidNotFoundExceptionV1(
                                        "track number oid not found, layoutTrackNumberId=${locationTrack.trackNumberId}"
                                    ),
                        )
                    }
                },
        )
    }

    //    fun createLocationTrackModificationResponse(
    //        oid: Oid<LocationTrack>,
    //        modificationsFromVersion: Uuid<Publication>,
    //        trackNetworkVersion: Uuid<Publication>?,
    //        coordinateSystem: Srid,
    //    ): ExtModifiedLocationTrackResponseV1? {
    //        val layoutContext = MainLayoutContext.official
    //
    //        val earlierPublication =
    //            publicationDao
    //                .fetchPublicationByUuid(modificationsFromVersion)
    //                .let(::requireNotNull) // TODO Improve error handling
    //        val laterPublication =
    //            trackNetworkVersion?.let { uuid ->
    //                publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull)
    //            } // TODO Improve error handling
    //            ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()
    //
    //        return if (earlierPublication == laterPublication) {
    //            logger.info(
    //                "there cannot be any differences if the requested publication ids are the same " +
    //                    "publication=${earlierPublication.id}"
    //            )
    //            null
    //        } else {
    //            val locationTrackId =
    //                locationTrackDao.lookupByExternalId(oid)?.id
    //                    ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")
    //
    //            val earlierLocationTrackVersion =
    //                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
    //                    layoutContext.branch,
    //                    locationTrackId,
    //                    earlierPublication.publicationTime,
    //                ) // TODO Improve error handling
    //
    //            val laterLocationTrackVersion =
    //                locationTrackDao.fetchOfficialVersionAtMomentOrThrow(
    //                    layoutContext.branch,
    //                    locationTrackId,
    //                    laterPublication.publicationTime,
    //                ) // TODO Improve error handling
    //
    //            if (earlierLocationTrackVersion == laterLocationTrackVersion) {
    //                logger.info(
    //                    "location track version was the same for locationTrackId=$locationTrackId, " +
    //                        "earlierPublication=${earlierPublication.id}, laterPublication=${laterPublication.id}"
    //                )
    //                return null
    //            } else {
    //                return ExtModifiedLocationTrackResponseV1(
    //                    modificationsFromVersion = modificationsFromVersion,
    //                    trackNetworkVersion = laterPublication.uuid,
    //                    locationTrack =
    //                        getExtLocationTrack(
    //                            oid,
    //                            locationTrackDao.fetch(laterLocationTrackVersion),
    //                            MainLayoutContext.official,
    //                            laterPublication.publicationTime,
    //                            coordinateSystem,
    //                        ),
    //                )
    //            }
    //        }
    //    }

    //    fun getLocationTrackCollection(
    //        layoutContext: LayoutContext,
    //        moment: Instant,
    //        coordinateSystem: Srid,
    //    ): ExtLocationTrackV1 {
    //
    //        val asd = locationTrackService.getStartAndEnd(layoutContext)
    //
    //        val alignmentAddresses =
    //            geocodingService.getAddressPoints(layoutContext, locationTrack.id as IntId)
    //                ?: throw ExtGeocodingFailedV1("address points not found, locationTrackId=${locationTrack.id}")
    //
    //        val trackNumberName =
    //            layoutTrackNumberDao
    //                .fetchOfficialVersionAtMoment(layoutContext.branch, locationTrack.trackNumberId, moment)
    //                ?.let(layoutTrackNumberDao::fetch)
    //                ?.number
    //                ?: throw ExtTrackNumberNotFoundV1(
    //                    "track number was not found for " +
    //                        "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId},
    // moment=$moment"
    //                )
    //
    //        val trackNumberOid =
    //            layoutTrackNumberDao.fetchExternalId(layoutContext.branch, locationTrack.trackNumberId)?.oid
    //                ?: throw ExtOidNotFoundExceptionV1(
    //                    "track number oid was not found for " +
    //                        "branch=${layoutContext.branch}, trackNumberId=${locationTrack.trackNumberId}"
    //                )
    //
    //        val (startLocation, endLocation) =
    //            when (coordinateSystem) {
    //                LAYOUT_SRID -> alignmentAddresses.startPoint to alignmentAddresses.endPoint
    //                else -> {
    //                    val start = layoutAddressPointToCoordinateSystem(alignmentAddresses.startPoint,
    // coordinateSystem)
    //                    val end = layoutAddressPointToCoordinateSystem(alignmentAddresses.endPoint, coordinateSystem)
    //
    //                    start to end
    //                }
    //            }
    //
    //        val locationTrackDescription =
    //            locationTrackService
    //                .getFullDescriptions(layoutContext, listOf(locationTrack), LocalizationLanguage.FI)
    //                .first()
    //
    //        return ExtLocationTrackV1(
    //            locationTrackOid = oid,
    //            locationTrackName = locationTrack.name,
    //            locationTrackType = ExtLocationTrackTypeV1(locationTrack.type),
    //            locationTrackState = ExtLocationTrackStateV1(locationTrack.state),
    //            locationTrackDescription = locationTrackDescription,
    //            locationTrackOwner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId).name,
    //            coordinateSystem = coordinateSystem,
    //            startLocation = ExtAddressPointV1.of(startLocation),
    //            endLocation = ExtAddressPointV1.of(endLocation),
    //            trackNumberName = trackNumberName,
    //            trackNumberOid = trackNumberOid,
    //        )
    //    }
}
