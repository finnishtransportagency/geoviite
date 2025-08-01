package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.FullRatkoExternalId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.publication.PublishedTrackNumber
import fi.fta.geoviite.infra.ratko.model.PushableLayoutBranch
import fi.fta.geoviite.infra.ratko.model.RatkoNodes
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.ratko.model.convertToRatkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
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
        branch: PushableLayoutBranch,
        publishedTrackNumbers: Collection<PublishedTrackNumber>,
        publicationTime: Instant,
    ): List<Oid<LayoutTrackNumber>> {
        return publishedTrackNumbers
            .groupBy { it.id }
            .map { (_, trackNumbers) ->
                val newestVersion = trackNumbers.maxBy { it.version.version }.version
                trackNumberDao.fetch(newestVersion) to trackNumbers.flatMap { it.changedKmNumbers }.toSet()
            }
            .sortedBy { sortByDeletedStateFirst(it.first.state) }
            .map { (trackNumber, changedKmNumbers) ->
                val externalId =
                    getFullExtIdAndManagePlanItem(
                        branch,
                        trackNumber.id as IntId,
                        trackNumber.designAssetState,
                        ratkoClient,
                        trackNumberDao::fetchExternalId,
                        trackNumberDao::savePlanItemId,
                    )
                requireNotNull(externalId) { "OID required for track number, tn=${trackNumber.id}" }
                try {
                    ratkoClient.getRouteNumber(RatkoOid(externalId.oid))?.let { existingRouteNumber ->
                        if (
                            trackNumber.state == LayoutState.DELETED ||
                                trackNumber.designAssetState == DesignAssetState.CANCELLED
                        ) {
                            deleteRouteNumber(trackNumber, externalId, existingRouteNumber)
                        } else {
                            updateRouteNumber(
                                branch = branch.branch,
                                existingRatkoRouteNumber = existingRouteNumber,
                                trackNumber = trackNumber,
                                trackNumberExternalId = externalId,
                                moment = publicationTime,
                                changedKmNumbers = changedKmNumbers,
                            )
                        }
                    }
                        ?: if (
                            trackNumber.state != LayoutState.DELETED &&
                                trackNumber.designAssetState != DesignAssetState.CANCELLED
                        ) {
                            createRouteNumber(branch.branch, trackNumber, externalId, publicationTime)
                        } else {
                            null
                        }
                } catch (ex: RatkoPushException) {
                    throw RatkoTrackNumberPushException(ex, trackNumber)
                }
                externalId.oid
            }
    }

    fun forceRedraw(routeNumberOids: Set<RatkoOid<RatkoRouteNumber>>) {
        if (routeNumberOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawRouteNumber(routeNumberOids)
        }
    }

    private fun deleteRouteNumber(
        trackNumber: LayoutTrackNumber,
        trackNumberExternalId: FullRatkoExternalId<LayoutTrackNumber>,
        existingRatkoRouteNumber: RatkoRouteNumber,
    ) {
        try {
            val deletedEndsPoints =
                existingRatkoRouteNumber.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

            updateRouteNumberProperties(trackNumber, trackNumberExternalId, deletedEndsPoints)

            val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumberExternalId.oid)
            ratkoClient.deleteRouteNumberPoints(routeNumberOid, null)
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.DELETE, ex)
        }
    }

    private fun updateRouteNumber(
        branch: LayoutBranch,
        trackNumber: LayoutTrackNumber,
        trackNumberExternalId: FullRatkoExternalId<LayoutTrackNumber>,
        existingRatkoRouteNumber: RatkoRouteNumber,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>,
    ) {
        try {
            require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }

            val addresses =
                geocodingService.getGeocodingContextAtMoment(branch, trackNumber.id, moment)?.referenceLineAddresses
            checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

            val existingStartNode = existingRatkoRouteNumber.nodecollection?.getStartNode()
            val existingEndNode = existingRatkoRouteNumber.nodecollection?.getEndNode()

            val routeNumberOid = RatkoOid<RatkoRouteNumber>(trackNumberExternalId.oid)
            val endPointNodeCollection =
                getEndPointNodeCollection(
                    alignmentAddresses = addresses,
                    changedKmNumbers = changedKmNumbers,
                    existingStartNode = existingStartNode,
                    existingEndNode = existingEndNode,
                )

            // Update route number end points before deleting anything, otherwise old end points
            // will stay in use
            updateRouteNumberProperties(trackNumber, trackNumberExternalId, endPointNodeCollection)

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
        newPoints: Collection<AddressPoint<ReferenceLineM>>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.updateRouteNumberPoints(routeNumberOid, points)
        }

    private fun createRouteNumber(
        branch: LayoutBranch,
        trackNumber: LayoutTrackNumber,
        trackNumberOid: FullRatkoExternalId<LayoutTrackNumber>,
        moment: Instant,
    ) {
        try {
            require(trackNumber.id is IntId) { "Only layout route numbers can be updated, id=${trackNumber.id}" }

            val addresses =
                geocodingService.getGeocodingContextAtMoment(branch, trackNumber.id, moment)?.referenceLineAddresses
            checkNotNull(addresses) { "Cannot calculate addresses for track number, id=${trackNumber.id}" }

            val ratkoNodes = convertToRatkoNodeCollection(addresses)
            val ratkoRouteNumber = convertToRatkoRouteNumber(trackNumber, trackNumberOid, ratkoNodes)
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
        newPoints: Collection<AddressPoint<ReferenceLineM>>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.createRouteNumberPoints(routeNumberOid, points)
        }

    private fun updateRouteNumberProperties(
        trackNumber: LayoutTrackNumber,
        trackNumberExternalId: FullRatkoExternalId<LayoutTrackNumber>,
        nodeCollection: RatkoNodes? = null,
    ) {
        val ratkoRouteNumber =
            convertToRatkoRouteNumber(
                trackNumber = trackNumber,
                trackNumberExternalId = trackNumberExternalId,
                nodeCollection = nodeCollection,
            )
        ratkoClient.updateRouteNumber(ratkoRouteNumber)
    }
}
