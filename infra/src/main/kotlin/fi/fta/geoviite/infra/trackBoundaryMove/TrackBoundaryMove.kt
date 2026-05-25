package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack

data class TrackBoundaryMove(
    val version: RowVersion<TrackBoundaryMove>,
    val shortenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val edgeRange: IntRange,
    val lengthenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val publicationId: IntId<Publication>?,
) {
    val id = version.id

    fun containsLocationTrack(track: IntId<LocationTrack>) =
        shortenedLocationTrack.id == track || lengthenedLocationTrack.id == track

    val locationTracks
        get() = listOf(shortenedLocationTrack, lengthenedLocationTrack)
}
