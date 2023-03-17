package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.PublishedTrackNumber
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoRouteNumberService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val trackNumberService: LayoutTrackNumberService,
    private val geocodingService: GeocodingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushTrackNumberChangesToRatko(publishedTrackNumbers: List<PublishedTrackNumber>): List<Oid<TrackLayoutTrackNumber>> {
        return publishedTrackNumbers
            .groupBy { it.version.id }
            .map { (trackNumberId, trackNumbers) ->
                trackNumberService.getOrThrow(
                    PublishType.OFFICIAL, trackNumberId
                ) to trackNumbers.flatMap { it.changedKmNumbers }.toSet()
            }
            .sortedBy { sortByDeletedStateFirst(it.first.state) }
            .mapNotNull { (trackNumber, changedKmNumbers) ->
                trackNumber.externalId?.also { externalId ->
                    try {
                        ratkoClient.getRouteNumber(RatkoOid(externalId))?.let { existingRouteNumber ->
                            if (trackNumber.state == LayoutState.DELETED) {
                                deleteRouteNumber(trackNumber, existingRouteNumber)
                            } else {
                                updateRouteNumber(
                                    existingRatkoRouteNumber = existingRouteNumber,
                                    trackNumber = trackNumber,
                                    changedKmNumbers = changedKmNumbers
                                )
                            }
                        } ?: createRouteNumber(trackNumber)
                    } catch (ex: RatkoPushException) {
                        throw RatkoTrackNumberPushException(ex, trackNumber)
                    }
                }
            }
    }

    fun forceRedraw(routeNumberOids: List<RatkoOid<RatkoRouteNumber>>) {
        if (routeNumberOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawRouteNumber(routeNumberOids)
        }
    }

    private fun deleteRouteNumber(trackNumber: TrackLayoutTrackNumber, existingRatkoRouteNumber: RatkoRouteNumber) {
        logger.serviceCall("deleteRouteNumber", "trackNumber" to trackNumber)
        requireNotNull(trackNumber.externalId) { "Cannot delete route number without oid $trackNumber" }

        val deletedEndsPoints = existingRatkoRouteNumber.nodecollection?.let { nodes -> toNodeCollectionMarkingEndpointsNotInUse(nodes) }

        updateRouteNumberProperties(trackNumber, deletedEndsPoints)

        val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumber.externalId)
        ratkoClient.deleteRouteNumberPoints(routeNumberOid, null)
    }

    private fun updateRouteNumber(
        trackNumber: TrackLayoutTrackNumber,
        existingRatkoRouteNumber: RatkoRouteNumber,
        changedKmNumbers: Set<KmNumber>,
    ) {
        logger.serviceCall(
            "updateRatkoRouteNumber",
            "trackNumber" to trackNumber,
            "existingRatkoRouteNumber" to existingRatkoRouteNumber,
            "changedKmNumbers" to changedKmNumbers
        )
        requireNotNull(trackNumber.externalId) { "Cannot update route number without oid $trackNumber" }

        val addresses =
            geocodingService.getGeocodingContext(PublishType.OFFICIAL, trackNumber.id)?.referenceLineAddresses
        checkNotNull(addresses) { "Cannot calculate addresses for track number ${trackNumber.id}" }

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
        newPoints: List<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(newPoints).forEach { points ->
        ratkoClient.updateRouteNumberPoints(routeNumberOid, points)
    }

    private fun createRouteNumber(trackNumber: TrackLayoutTrackNumber) {
        logger.serviceCall("createRouteNumber", "trackNumber" to trackNumber)

        val addresses =
            geocodingService.getGeocodingContext(PublishType.OFFICIAL, trackNumber.id)?.referenceLineAddresses
        checkNotNull(addresses) { "Cannot calculate addresses for track number ${trackNumber.id}" }

        val ratkoNodes = convertToRatkoNodeCollection(addresses)
        val ratkoRouteNumber = convertToRatkoRouteNumber(trackNumber, ratkoNodes)
        val routeNumberOid = ratkoClient.newRouteNumber(ratkoRouteNumber)
        checkNotNull(routeNumberOid) { "Did not receive oid from Ratko $ratkoRouteNumber" }
        createRouteNumberPoints(routeNumberOid, addresses.midPoints)
    }

    private fun createRouteNumberPoints(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        addressPoints: List<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
        ratkoClient.createRouteNumberPoints(routeNumberOid, points)
    }


    private fun updateRouteNumberProperties(
        trackNumber: TrackLayoutTrackNumber,
        nodeCollection: RatkoNodes? = null
    ) {
        requireNotNull(trackNumber.externalId) { "Cannot update route number properties without oid $trackNumber" }
        val ratkoRouteNumber = convertToRatkoRouteNumber(
            trackNumber = trackNumber,
            nodeCollection = nodeCollection,
        )
        ratkoClient.updateRouteNumber(ratkoRouteNumber)
    }
}
