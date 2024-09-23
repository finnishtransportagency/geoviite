package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.publication.PublishedTrackNumber
import fi.fta.geoviite.infra.ratko.model.RatkoNodes
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.ratko.model.convertToRatkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoRouteNumberService
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    fun pushTrackNumberChangesToRatko(
        branch: LayoutBranch,
        publishedTrackNumbers: Collection<PublishedTrackNumber>,
        publicationTime: Instant,
    ): List<Oid<TrackLayoutTrackNumber>> {
        // TODO: Design branches not yet supported. We need to ensure that design tracknumbers have
        // own OIDs -> could we rely on tracknumber.branch instead of arg?
        assertMainBranch(branch)
        return publishedTrackNumbers
            .groupBy { it.id }
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
                                branch = branch,
                                existingRatkoRouteNumber = existingRouteNumber,
                                trackNumber = trackNumber,
                                moment = publicationTime,
                                changedKmNumbers = changedKmNumbers,
                            )
                        }
                    }
                        ?: if (trackNumber.state != LayoutState.DELETED) {
                            createRouteNumber(branch, trackNumber, publicationTime)
                        } else {
                            null
                        }
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
        try {
            requireNotNull(trackNumber.externalId) { "Cannot delete route number without oid, id=${trackNumber.id}" }

            val deletedEndsPoints =
                existingRatkoRouteNumber.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

            updateRouteNumberProperties(trackNumber, deletedEndsPoints)

            val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumber.externalId)
            ratkoClient.deleteRouteNumberPoints(routeNumberOid, null)
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.DELETE, ex)
        }
    }

    private fun updateRouteNumber(
        branch: LayoutBranch,
        trackNumber: TrackLayoutTrackNumber,
        existingRatkoRouteNumber: RatkoRouteNumber,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>,
    ) {
        try {
            require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }
            requireNotNull(trackNumber.externalId) { "Cannot update route number without oid, id=${trackNumber.id}" }

            val addresses =
                geocodingService.getGeocodingContextAtMoment(branch, trackNumber.id, moment)?.referenceLineAddresses
            checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

            val existingStartNode = existingRatkoRouteNumber.nodecollection?.getStartNode()
            val existingEndNode = existingRatkoRouteNumber.nodecollection?.getEndNode()

            val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumber.externalId)
            val endPointNodeCollection =
                getEndPointNodeCollection(
                    alignmentAddresses = addresses,
                    changedKmNumbers = changedKmNumbers,
                    existingStartNode = existingStartNode,
                    existingEndNode = existingEndNode,
                )

            // Update route number end points before deleting anything, otherwise old end points
            // will
            // stay in use
            updateRouteNumberProperties(trackNumber, endPointNodeCollection)

            deleteRouteNumberPoints(routeNumberOid, changedKmNumbers)

            updateRouteNumberGeometry(
                routeNumberOid = routeNumberOid,
                newPoints = addresses.midPoints.filter { p -> changedKmNumbers.contains(p.address.kmNumber) },
            )
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.UPDATE, ex)
        }
    }

    private fun deleteRouteNumberPoints(routeNumberOid: RatkoOid<RatkoRouteNumber>, changedKmNumbers: Set<KmNumber>) =
        changedKmNumbers.forEach { kmNumber -> ratkoClient.deleteRouteNumberPoints(routeNumberOid, kmNumber) }

    private fun updateRouteNumberGeometry(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        newPoints: Collection<AddressPoint>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.updateRouteNumberPoints(routeNumberOid, points)
        }

    private fun createRouteNumber(branch: LayoutBranch, trackNumber: TrackLayoutTrackNumber, moment: Instant) {
        try {
            require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }

            val addresses =
                geocodingService.getGeocodingContextAtMoment(branch, trackNumber.id, moment)?.referenceLineAddresses
            checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

            val ratkoNodes = convertToRatkoNodeCollection(addresses)
            val ratkoRouteNumber = convertToRatkoRouteNumber(trackNumber, ratkoNodes)
            val routeNumberOid = ratkoClient.newRouteNumber(ratkoRouteNumber)
            checkNotNull(routeNumberOid) { "Did not receive oid from Ratko for track number $ratkoRouteNumber" }
            if (trackNumber.state != LayoutState.DELETED) {
                createRouteNumberPoints(routeNumberOid, addresses.midPoints)
            }
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.CREATE, ex)
        }
    }

    private fun createRouteNumberPoints(
        routeNumberOid: RatkoOid<RatkoRouteNumber>,
        newPoints: Collection<AddressPoint>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.createRouteNumberPoints(routeNumberOid, points)
        }

    private fun updateRouteNumberProperties(trackNumber: TrackLayoutTrackNumber, nodeCollection: RatkoNodes? = null) {
        val ratkoRouteNumber = convertToRatkoRouteNumber(trackNumber = trackNumber, nodeCollection = nodeCollection)
        ratkoClient.updateRouteNumber(ratkoRouteNumber)
    }
}
