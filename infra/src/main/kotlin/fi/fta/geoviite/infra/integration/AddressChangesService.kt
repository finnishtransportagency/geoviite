package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.tracklayout.*
import org.springframework.stereotype.Service
import java.time.Instant


fun addressPointsAreEqual(point1: AddressPoint?, point2: AddressPoint?) =
    if (point1 == null && point2 == null) true
    else if (point1 == null || point2 == null) false
    else point1.isSame(point2)

fun resolveChangedGeometryKilometers(
    originalAddresses: AlignmentAddresses?,
    newAddresses: AlignmentAddresses?,
): Set<KmNumber> {
    val oldPoints = originalAddresses?.allPoints ?: listOf()
    val newPoints = newAddresses?.allPoints ?: listOf()
    val addedAddresses = findDiffAddresses(oldPoints, newPoints)
    val removedAddresses = findDiffAddresses(newPoints, oldPoints)

    return (addedAddresses + removedAddresses).toSet()
}

private fun findDiffAddresses(
    points1: List<AddressPoint>,
    points2: List<AddressPoint>,
): MutableList<KmNumber> {
    val differences = mutableListOf<KmNumber>()

    var points1Index = 0
    val firstKm = points1.firstOrNull()?.address?.kmNumber
    var points2Index = findFirstIndexForKm(points2, firstKm)

    while (points1Index in 0..points1.lastIndex) {
        val point1 = points1[points1Index]
        val point2 = points2Index?.let(points2::getOrNull)
        if (!addressPointsAreEqual(point2, point1)) {
            differences.add(point1.address.kmNumber)

            // Move indexes to the start of the next kilometer
            points1Index = findFistIndexAfterKm(points1, point1.address.kmNumber)
            val nextKm = points1.getOrNull(points1Index)?.address?.kmNumber
            points2Index = findFirstIndexForKm(points2, nextKm)
        } else {
            if (points2Index != null) points2Index++
            points1Index++
        }

    }
    return differences
}

private fun findFistIndexAfterKm(list: List<AddressPoint>, kmNumber: KmNumber) =
    list.indexOfFirst { point -> point.address.kmNumber > kmNumber }

private fun findFirstIndexForKm(list: List<AddressPoint>, kmNumber: KmNumber?) =
    kmNumber?.let { km -> list.indexOfFirst { point -> point.address.kmNumber == km } }

data class AddressChanges(
    val changedKmNumbers: Set<KmNumber>,
    val startPointChanged: Boolean,
    val endPointChanged: Boolean,
)

@Service
class AddressChangesService(
    val trackLayoutHistoryDao: TrackLayoutHistoryDao,
    val geocodingService: GeocodingService,
    val layoutAlignmentDao: LayoutAlignmentDao,
) {

    fun getAddressChangesSinceMoment(
        locationTrackId: IntId<LocationTrack>,
        moment: Instant
    ): AddressChanges? {
        val oldAddresses = getAlignmentAddressesAtMoment(locationTrackId, moment)
        val currentAddresses = getAlignmentAddressesAtMoment(locationTrackId)
        return getAddressChanges(oldAddresses, currentAddresses)
    }

    fun getAddressChangesInDraft(
        locationTrackId: IntId<LocationTrack>,
    ): AddressChanges? {
        val officialAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.OFFICIAL)
        val draftAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.DRAFT)
        return getAddressChanges(officialAddresses, draftAddresses)
    }

    /**
     * Returns addresses of a location track at a moment OR null if addresses
     * cannot be resolved for the given moment (e.g. the location track or
     * geometry does not exist at the moment).
     */
    fun getAlignmentAddressesAtMoment(
        locationTrackId: IntId<LocationTrack>,
        moment: Instant? = null,
    ): AlignmentAddresses? {
        val locationTrack = trackLayoutHistoryDao.fetchLocationTrackAtMoment(locationTrackId, moment)
        if (locationTrack?.alignmentVersion == null) return null

        val locationTrackGeometry = layoutAlignmentDao.fetch(locationTrack.alignmentVersion)
        if (locationTrackGeometry.segments.isEmpty()) return null

        val trackNumberId = locationTrack.trackNumberId
        val geocodingContext = getGeocodingContextAtMoment(trackNumberId, moment)
        return geocodingContext?.getAddressPoints(locationTrackGeometry)
    }

    fun getGeocodingContextAtMoment(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant? = null,
    ): GeocodingContext? {
        val trackNumber = trackLayoutHistoryDao.fetchTrackNumberAtMoment(trackNumberId, moment)

        val referenceLine = trackLayoutHistoryDao.fetchReferenceLineAtMoment(trackNumberId, moment)

        if (trackNumber == null || referenceLine?.alignmentVersion == null) return null

        val referenceLineGeometry = layoutAlignmentDao.fetch(referenceLine.alignmentVersion)

        val kmPosts = trackLayoutHistoryDao.fetchKmPostsAtMoment(trackNumberId, moment)
            .filter(TrackLayoutKmPost::exists)

        return GeocodingContext.create(
            trackNumber,
            referenceLine,
            referenceLineGeometry,
            kmPosts,
        )
    }

}

fun getAddressChanges(
    oldAddresses: AlignmentAddresses?,
    newAddresses: AlignmentAddresses?,
) = AddressChanges(
    changedKmNumbers = resolveChangedGeometryKilometers(oldAddresses, newAddresses),
    startPointChanged = !addressPointsAreEqual(oldAddresses?.startPoint, newAddresses?.startPoint),
    endPointChanged = !addressPointsAreEqual(oldAddresses?.endPoint, newAddresses?.endPoint),
)
