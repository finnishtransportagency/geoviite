package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.StationLink
import fi.fta.geoviite.infra.tracklayout.StationLinkService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtStationLinkServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val stationLinkService: StationLinkService,
    private val operationalPointDao: OperationalPointDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtStationLinkCollection(layoutVersion: ExtLayoutVersionV1?): ExtStationLinkCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        return createStationLinkCollectionResponse(publication)
    }

    private fun createStationLinkCollectionResponse(publication: Publication): ExtStationLinkCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val stationLinks = stationLinkService.getStationLinks(branch, moment)
        return ExtStationLinkCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            connectionCollection = createExtStationLinks(branch, stationLinks),
        )
    }

    private fun createExtStationLinks(branch: LayoutBranch, stationLinks: List<StationLink>): List<ExtStationLinkV1> {
        val trackNumbers =
            layoutTrackNumberDao.fetchMany(stationLinks.map { it.trackNumberVersion }).associateBy { it.id as IntId }
        val trackNumberExtIds = layoutTrackNumberDao.fetchExternalIds(branch, trackNumbers.keys)

        val operationalPointVersions =
            stationLinks.flatMap { listOf(it.startOperationalPointVersion, it.endOperationalPointVersion) }.distinct()
        val operationalPoints = operationalPointDao.fetchMany(operationalPointVersions).associateBy { it.id as IntId }
        val operationalPointExtIds = operationalPointDao.fetchExternalIds(branch, operationalPoints.keys)

        val locationTrackIds = stationLinks.flatMap { it.locationTrackIds }.distinct()
        val locationTrackExtIds = locationTrackDao.fetchExternalIds(branch, locationTrackIds)

        return stationLinks.map { link ->
            createExtStationLink(
                link,
                trackNumbers,
                trackNumberExtIds,
                operationalPointExtIds,
                operationalPoints,
                locationTrackExtIds,
                branch,
            )
        }
    }

    private fun createExtStationLink(
        stationLink: StationLink,
        trackNumbers: Map<IntId<LayoutTrackNumber>, LayoutTrackNumber>,
        trackNumberExtIds: Map<IntId<LayoutTrackNumber>, RatkoExternalId<LayoutTrackNumber>>,
        operationalPointExtIds: Map<IntId<OperationalPoint>, RatkoExternalId<OperationalPoint>>,
        operationalPoints: Map<IntId<OperationalPoint>, OperationalPoint>,
        locationTrackExtIds: Map<IntId<LocationTrack>, RatkoExternalId<LocationTrack>>,
        branch: LayoutBranch,
    ): ExtStationLinkV1 {
        val trackNumberExtId =
            trackNumberExtIds[stationLink.trackNumberId]?.oid ?: throwOidNotFound(branch, stationLink.trackNumberId)
        val trackNumber =
            requireNotNull(trackNumbers[stationLink.trackNumberId]) {
                "Track number ${stationLink.trackNumberId} not found"
            }

        val startOpExtId =
            operationalPointExtIds[stationLink.startOperationalPointId]?.oid
                ?: throwOidNotFound(branch, stationLink.startOperationalPointId)
        val startOp =
            requireNotNull(operationalPoints[stationLink.startOperationalPointId]) {
                "Operational point ${stationLink.startOperationalPointId} not found"
            }

        val endOpExtId =
            operationalPointExtIds[stationLink.endOperationalPointId]?.oid
                ?: throwOidNotFound(branch, stationLink.endOperationalPointId)
        val endOp =
            requireNotNull(operationalPoints[stationLink.endOperationalPointId]) {
                "Operational point ${stationLink.endOperationalPointId} not found"
            }

        val locationTrackOids =
            stationLink.locationTrackIds.map { trackId ->
                val oid = locationTrackExtIds[trackId]?.oid ?: throwOidNotFound(branch, trackId)
                ExtStationLinkTrackV1(locationTrackOid = ExtOidV1(oid))
            }

        return ExtStationLinkV1(
            trackNumber = trackNumber.number,
            trackNumberOid = ExtOidV1(trackNumberExtId),
            start = ExtStationLinkEndpointV1(operationalPointOid = ExtOidV1(startOpExtId), name = startOp.name),
            end = ExtStationLinkEndpointV1(operationalPointOid = ExtOidV1(endOpExtId), name = endOp.name),
            length = stationLink.length,
            tracks = locationTrackOids,
        )
    }
}
