package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.getLocationTrackEndpoints
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class LocationTrackEndpointTest {
    private val bbox = BoundingBox(-10.0..10.0, -10.0..10.0)
    private val pointInsideBbox = Point.zero()
    private val otherPointInsideBbox = Point(2.0, 2.0)
    private val thirdPointInsideBbox = Point(3.0, 3.0)
    private val pointOutsideBbox = Point(99.0, 99.0)
    private val otherPointOutsideBbox = Point(-99.0, -99.0)

    @Test
    fun shouldFindLocationTrackStartPoint() {
        val trackWithStartPointInsideBbox =
            locationTrackAndAlignment(
                IntId(0),
                listOf(segment(pointInsideBbox, pointOutsideBbox)),
                IntId(0)
            )
        val alignments = listOf(trackWithStartPointInsideBbox)

        val endpoints = getLocationTrackEndpoints(alignments, bbox)

        assertEquals(1, endpoints.count())
        assertEquals(
            LocationTrackEndpoint(
                locationTrackId = trackWithStartPointInsideBbox.first.id as IntId<LocationTrack>,
                location = pointInsideBbox,
                updateType = LocationTrackPointUpdateType.START_POINT
            ), endpoints.first()
        )
    }

    @Test
    fun shouldFindLocationTrackEndPoint() {
        val trackWithEndPointInsideBbox =
            locationTrackAndAlignment(
                IntId(0),
                listOf(segment(pointOutsideBbox, otherPointInsideBbox)),
                IntId(0)
            )
        val alignments = listOf(trackWithEndPointInsideBbox)

        val endpoints = getLocationTrackEndpoints(alignments, bbox)

        assertEquals(1, endpoints.count())
        assertEquals(
            LocationTrackEndpoint(
                locationTrackId = trackWithEndPointInsideBbox.first.id as IntId<LocationTrack>,
                location = otherPointInsideBbox,
                updateType = LocationTrackPointUpdateType.END_POINT
            ), endpoints.first()
        )
    }

    @Test
    fun shouldFindBothLocationTrackEndpoints() {
        val alignmentWithMissingBothEndpointsInsideBbox =
            locationTrackAndAlignment(
                IntId(0),
                listOf(
                    segment(pointInsideBbox, otherPointInsideBbox),
                    segment(otherPointInsideBbox, thirdPointInsideBbox),
                ), id = IntId(1)
            )
        val alignments = listOf(alignmentWithMissingBothEndpointsInsideBbox)

        val endpoints = getLocationTrackEndpoints(alignments, bbox)

        assert(
            endpoints.contains(
                LocationTrackEndpoint(
                    locationTrackId = alignmentWithMissingBothEndpointsInsideBbox.first.id as IntId<LocationTrack>,
                    location = pointInsideBbox,
                    updateType = LocationTrackPointUpdateType.START_POINT
                )
            )
        )
        assert(
            endpoints.contains(
                LocationTrackEndpoint(
                    locationTrackId = alignmentWithMissingBothEndpointsInsideBbox.first.id as IntId<LocationTrack>,
                    location = thirdPointInsideBbox,
                    updateType = LocationTrackPointUpdateType.END_POINT
                )
            )
        )
    }

    @Test
    fun shouldIgnoreLocationTrackEndpointsOutsideBbox() {
        val alignmentsWithEndpointsOutsideBbox = listOf(
            locationTrackAndAlignment(IntId(0), segment(pointOutsideBbox, otherPointOutsideBbox)),
        ).map { (track, alignment) -> track.copy(id = IntId(1)) to alignment }

        val endpoints = getLocationTrackEndpoints(alignmentsWithEndpointsOutsideBbox, bbox)

        assert(endpoints.isEmpty()) { "Missing endpoints outside bbox should not be found!" }
    }
}
