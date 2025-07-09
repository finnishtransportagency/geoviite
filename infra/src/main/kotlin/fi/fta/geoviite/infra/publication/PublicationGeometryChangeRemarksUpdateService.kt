package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.findCommonSubsequenceInCompactLists
import fi.fta.geoviite.infra.util.rangesOfConsecutiveIndicesOf
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val GEOMETRY_CHANGE_BATCH_SIZE = 10

@Component
class PublicationGeometryChangeRemarksUpdateService(
    private val lockDao: LockDao,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun processPublication(publicationId: IntId<Publication>) {
        publicationDao.fetchUnprocessedGeometryChangeRemarks(publicationId).forEach(::processOne)
    }

    fun updateUnprocessedGeometryChangeRemarks() {
        lockDao.runWithLock(DatabaseLock.PUBLICATION_GEOMETRY_CHANGE_CALCULATION, Duration.ofMinutes(60)) {
            var unprocessedRemarksWereLeft = true
            while (unprocessedRemarksWereLeft) {
                val unprocessed = publicationDao.fetchUnprocessedGeometryChangeRemarks(GEOMETRY_CHANGE_BATCH_SIZE)
                logger.info("Processing pending publication change remarks batch: count=${unprocessed.size}")
                unprocessedRemarksWereLeft = unprocessed.isNotEmpty()
                if (unprocessed.isNotEmpty()) processBatch(unprocessed)
            }
        }
    }

    private fun processBatch(unprocessedBatch: List<PublicationDao.UnprocessedGeometryChange>) {
        val range = " ${unprocessedBatch.first().publicationId}-${unprocessedBatch.last().publicationId}"
        logger.info("processing geometry change remarks for publicationIds $range")
        unprocessedBatch.forEach(::processOne)
        logger.info("finished processing geometry change remarks for publicationIds $range")
    }

    private fun processOne(unprocessedChange: PublicationDao.UnprocessedGeometryChange) {
        logger.info(
            "Processing publication change remarks for location track ${unprocessedChange.locationTrackId} in publication ${unprocessedChange.publicationId}"
        )
        val geocodingContext =
            geocodingService.getGeocodingContextAtMoment(
                unprocessedChange.branch,
                unprocessedChange.trackNumberId,
                unprocessedChange.publicationTime,
            )
        publicationDao.upsertGeometryChangeSummaries(
            unprocessedChange.publicationId,
            unprocessedChange.locationTrackId,
            if (geocodingContext == null || unprocessedChange.oldTrackVersion == null) {
                listOf()
            } else {
                summarizeAlignmentChanges(
                    geocodingContext = geocodingContext,
                    oldGeometry = alignmentDao.fetch(unprocessedChange.oldTrackVersion),
                    newGeometry = alignmentDao.fetch(unprocessedChange.newTrackVersion),
                )
            },
        )
    }
}

fun summarizeAlignmentChanges(
    geocodingContext: GeocodingContext<ReferenceLineM>,
    oldGeometry: LocationTrackGeometry,
    newGeometry: LocationTrackGeometry,
    changeThreshold: Double = 1.0,
): List<GeometryChangeSummary> =
    if (geometriesEqual(oldGeometry, newGeometry)) listOf()
    else
        getCommonAddressRange(geocodingContext, oldGeometry, newGeometry)?.let { (oldPoints, newPoints) ->
            summarizeCommonAddressRange(geocodingContext, oldPoints, newPoints, changeThreshold)
        } ?: listOf()

private fun geometriesEqual(old: LocationTrackGeometry, new: LocationTrackGeometry): Boolean =
    old.segments.map { it.geometry.id } == new.segments.map { it.geometry.id }

private fun getCommonAddressRange(
    geocodingContext: GeocodingContext<ReferenceLineM>,
    oldGeometry: LocationTrackGeometry,
    newGeometry: LocationTrackGeometry,
): Pair<List<AddressPoint<LocationTrackM>>, List<AddressPoint<LocationTrackM>>>? {
    val oldAddressPoints = geocodingContext.getAddressPoints(oldGeometry)
    val newAddressPoints = geocodingContext.getAddressPoints(newGeometry)
    if (oldAddressPoints == null || newAddressPoints == null) return null

    val (old, new) = getOldAndNewAddressPoints(oldAddressPoints, newAddressPoints)
    return findCommonSubsequenceInCompactLists(old.map { it.address }, new.map { it.address })?.let {
        (oldInNew, newInOld) ->
        old.slice(newInOld) to new.slice(oldInNew)
    }
}

private fun getOldAndNewAddressPoints(
    oldAddressPoints: AlignmentAddresses<LocationTrackM>,
    newAddressPoints: AlignmentAddresses<LocationTrackM>,
): Pair<List<AddressPoint<LocationTrackM>>, List<AddressPoint<LocationTrackM>>> {
    val takeStart = oldAddressPoints.startPoint.address == newAddressPoints.startPoint.address
    val takeEnd = oldAddressPoints.endPoint.address == newAddressPoints.endPoint.address

    return withEnds(oldAddressPoints, takeStart, takeEnd) to withEnds(newAddressPoints, takeStart, takeEnd)
}

private fun withEnds(addressPoints: AlignmentAddresses<LocationTrackM>, takeStart: Boolean, takeEnd: Boolean) =
    listOf(
            if (takeStart) listOf(addressPoints.startPoint) else listOf(),
            addressPoints.midPoints,
            if (takeEnd) listOf(addressPoints.endPoint) else listOf(),
        )
        .flatten()

private fun summarizeCommonAddressRange(
    geocodingContext: GeocodingContext<ReferenceLineM>,
    oldPoints: List<AddressPoint<LocationTrackM>>,
    newPoints: List<AddressPoint<LocationTrackM>>,
    changeThreshold: Double,
): List<GeometryChangeSummary> =
    rangesOfConsecutiveIndicesOf(
            true,
            oldPoints.zip(newPoints) { old, new -> lineLength(old.point, new.point) >= changeThreshold },
        )
        .map { range -> summarizeChangedRange(geocodingContext, oldPoints.slice(range), newPoints.slice(range)) }

private fun summarizeChangedRange(
    geocodingContext: GeocodingContext<ReferenceLineM>,
    oldPoints: List<AddressPoint<LocationTrackM>>,
    newPoints: List<AddressPoint<LocationTrackM>>,
): GeometryChangeSummary {
    val rangeStartAddress = oldPoints.first().address
    val rangeEndAddress = oldPoints.last().address
    val rangeStartMOnReferenceLine =
        requireNotNull(geocodingContext.getProjectionLine(rangeStartAddress)).distance.distance
    val rangeEndMOnReferenceLine = requireNotNull(geocodingContext.getProjectionLine(rangeEndAddress)).distance.distance
    val changeLengthM = rangeEndMOnReferenceLine - rangeStartMOnReferenceLine

    val maxDistance =
        oldPoints.zip(newPoints) { oldPoint, newPoint -> lineLength(oldPoint.point, newPoint.point) }.max()

    return GeometryChangeSummary(changeLengthM, maxDistance, rangeStartAddress, rangeEndAddress)
}
