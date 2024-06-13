package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.PublishedTrackNumber
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import java.time.Instant

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoRouteNumberService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    fun pushTrackNumberChangesToRatko(
        publishedTrackNumbers: Collection<PublishedTrackNumber>,
        publicationTime: Instant,
    ): List<Oid<TrackLayoutTrackNumber>> {
        return publishedTrackNumbers
            .groupBy { it.version.id }
            .map { (_, trackNumbers) ->
                val newestVersion = trackNumbers.maxBy { it.version.version }.version
                trackNumberDao.fetch(newestVersion) to trackNumbers.flatMap { it.changedKmNumbers }.toSet()
            }
            .sortedBy { sortByDeletedStateFirst(it.first.state) }
            .map { (trackNumber, changedKmNumbers) ->
                val externalId =
                    requireNotNull(trackNumber.externalId) { "OID required for track number, tn=${trackNumber.id}" }
                try {
                    ratkoClient.getRouteNumber(RatkoOid(externalId))?.let { existingRouteNumber ->
                        if (trackNumber.state == LayoutState.DELETED) {
                            deleteRouteNumber(trackNumber, existingRouteNumber)
                        } else {
                            updateRouteNumber(
                                existingRatkoRouteNumber = existingRouteNumber,
                                trackNumber = trackNumber,
                                moment = publicationTime,
                                changedKmNumbers = changedKmNumbers
                            )
                        }
                    } ?: createRouteNumber(trackNumber, publicationTime)
                } catch (ex: RatkoPushException) {
                    throw RatkoTrackNumberPushException(ex, trackNumber)
                }
                externalId
            }
    }

    fun forceRedraw(routeNumberOids: Set<RatkoOid<RatkoRouteNumber>>) {
        if (routeNumberOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawRouteNumber(routeNumberOids)
        }
    }

    private fun deleteRouteNumber(trackNumber: TrackLayoutTrackNumber, existingRatkoRouteNumber: RatkoRouteNumber) {
        requireNotNull(trackNumber.externalId) { "Cannot delete route number without oid, id=${trackNumber.id}" }

        val deletedEndsPoints = existingRatkoRouteNumber.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

        updateRouteNumberProperties(trackNumber, deletedEndsPoints)

        val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumber.externalId)
        ratkoClient.deleteRouteNumberPoints(routeNumberOid, null)
    }

    private fun updateRouteNumber(
        trackNumber: TrackLayoutTrackNumber,
        existingRatkoRouteNumber: RatkoRouteNumber,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>,
    ) {
        require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }
        requireNotNull(trackNumber.externalId) { "Cannot update route number without oid, id=${trackNumber.id}" }

        val addresses = geocodingService.getGeocodingContextAtMoment(trackNumber.id, moment)?.referenceLineAddresses
        checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

        val existingStartNode = existingRatkoRouteNumber.nodecollection?.getStartNode()
        val existingEndNode = existingRatkoRouteNumber.nodecollection?.getEndNode()

        val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumber.externalId)
        val endPointNodeCollection = getEndPointNodeCollection(
            alignmentAddresses = addresses,
            changedKmNumbers = changedKmNumbers,
            existingStartNode = existingStartNode,
            existingEndNode = existingEndNode,
        )

        //Update route number end points before deleting anything, otherwise old end points will stay in use
        updateRouteNumberProperties(trackNumber, endPointNodeCollection)

        deleteRouteNumberPoints(routeNumberOid, changedKmNumbers)

        updateRouteNumberGeometry(
            routeNumberOid = routeNumberOid,
            newPoints = addresses.midPoints.filter { p ->
                changedKmNumbers.contains(p.address.kmNumber)
            }
        )
    }

    private fun deleteRouteNumberPoints(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        changedKmNumbers: Set<KmNumber>,
    ) = changedKmNumbers.forEach { kmNumber ->
        ratkoClient.deleteRouteNumberPoints(routeNumberOid, kmNumber)
    }

    private fun updateRouteNumberGeometry(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        newPoints: Collection<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(newPoints).forEach { points ->
        ratkoClient.updateRouteNumberPoints(routeNumberOid, points)
    }

    private fun createRouteNumber(trackNumber: TrackLayoutTrackNumber, moment: Instant) {
        require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }

        val addresses = geocodingService.getGeocodingContextAtMoment(trackNumber.id, moment)?.referenceLineAddresses
        checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

        val ratkoNodes = convertToRatkoNodeCollection(addresses)
        val ratkoRouteNumber = convertToRatkoRouteNumber(trackNumber, ratkoNodes)
        val routeNumberOid = ratkoClient.newRouteNumber(ratkoRouteNumber)
        checkNotNull(routeNumberOid) { "Did not receive oid from Ratko for track number $ratkoRouteNumber" }
        createRouteNumberPoints(routeNumberOid, addresses.midPoints)
    }

    private fun createRouteNumberPoints(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        newPoints: Collection<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(newPoints).forEach { points ->
        ratkoClient.createRouteNumberPoints(routeNumberOid, points)
    }

    private fun updateRouteNumberProperties(
        trackNumber: TrackLayoutTrackNumber,
        nodeCollection: RatkoNodes? = null
    ) {
        val ratkoRouteNumber = convertToRatkoRouteNumber(
            trackNumber = trackNumber,
            nodeCollection = nodeCollection,
        )
        ratkoClient.updateRouteNumber(ratkoRouteNumber)
    }
}
