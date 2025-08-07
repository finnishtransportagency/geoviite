package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.LayoutGeocodingContextCacheKey
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM

fun <M : AlignmentM<M>> addressPointsAreEqual(point1: AddressPoint<M>?, point2: AddressPoint<M>?): Boolean =
    if (point1 == null && point2 == null) true
    else if (point1 == null || point2 == null) false else point1.isSame(point2)

fun <M : AlignmentM<M>> resolveChangedGeometryKilometers(
    originalAddresses: AlignmentAddresses<M>?,
    newAddresses: AlignmentAddresses<M>?,
): Set<KmNumber> {
    val oldPoints = originalAddresses?.allPoints ?: listOf()
    val newPoints = newAddresses?.allPoints ?: listOf()
    val addedAddresses = findDiffAddresses(oldPoints, newPoints)
    val removedAddresses = findDiffAddresses(newPoints, oldPoints)

    return (addedAddresses + removedAddresses).toSet()
}

private fun <M : AlignmentM<M>> findDiffAddresses(
    points1: List<AddressPoint<M>>,
    points2: List<AddressPoint<M>>,
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
            points1Index = findFirstIndexAfterKm(points1, point1.address.kmNumber)
            val nextKm = points1.getOrNull(points1Index)?.address?.kmNumber
            points2Index = findFirstIndexForKm(points2, nextKm)
        } else {
            if (points2Index != null) points2Index++
            points1Index++
        }
    }
    return differences
}

private fun findFirstIndexAfterKm(list: List<AddressPoint<*>>, kmNumber: KmNumber) =
    list.indexOfFirst { point -> point.address.kmNumber > kmNumber }

private fun findFirstIndexForKm(list: List<AddressPoint<*>>, kmNumber: KmNumber?) =
    kmNumber?.let { km -> list.indexOfFirst { point -> point.address.kmNumber == km } }

data class AddressChanges(
    val changedKmNumbers: Set<KmNumber>,
    val startPointChanged: Boolean,
    val endPointChanged: Boolean,
) {
    companion object {
        fun empty() = AddressChanges(setOf(), startPointChanged = false, endPointChanged = false)
    }

    fun isChanged() = changedKmNumbers.isNotEmpty() || startPointChanged || endPointChanged
}

@GeoviiteService
class AddressChangesService(val geocodingService: GeocodingService) {

    fun getAddressChanges(
        beforeTrack: LocationTrack?,
        afterTrack: LocationTrack,
        beforeContextKey: LayoutGeocodingContextCacheKey?,
        afterContextKey: LayoutGeocodingContextCacheKey?,
    ): AddressChanges =
        if (beforeTrack == afterTrack && beforeContextKey == afterContextKey) {
            AddressChanges(setOf(), startPointChanged = false, endPointChanged = false)
        } else {
            getAddressChanges(getAddresses(beforeTrack, beforeContextKey), getAddresses(afterTrack, afterContextKey))
        }

    private fun getAddresses(
        track: LocationTrack?,
        contextKey: LayoutGeocodingContextCacheKey?,
    ): AlignmentAddresses<LocationTrackM>? =
        if (track == null || contextKey == null || !track.exists) null
        else geocodingService.getAddressPoints(contextKey, track.getVersionOrThrow())
}

fun <M : AlignmentM<M>> getAddressChanges(oldAddresses: AlignmentAddresses<M>?, newAddresses: AlignmentAddresses<M>?) =
    AddressChanges(
        changedKmNumbers = resolveChangedGeometryKilometers(oldAddresses, newAddresses),
        startPointChanged = !addressPointsAreEqual(oldAddresses?.startPoint, newAddresses?.startPoint),
        endPointChanged = !addressPointsAreEqual(oldAddresses?.endPoint, newAddresses?.endPoint),
    )
