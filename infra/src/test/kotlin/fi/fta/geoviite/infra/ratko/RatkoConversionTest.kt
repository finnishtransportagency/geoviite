package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.ratko.model.mapToRatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.mapToRatkoLocationTrackType
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RatkoConversionTest {

    @Test
    fun shouldMapToRatkoAlignmentType() {

        val convertedAlignmentTrap = mapToRatkoLocationTrackType(LocationTrackType.TRAP)
        val convertedAlignmentMain = mapToRatkoLocationTrackType(LocationTrackType.MAIN)
        val convertedAlignmentChord = mapToRatkoLocationTrackType(LocationTrackType.CHORD)
        val convertedAlignmentSide = mapToRatkoLocationTrackType(LocationTrackType.SIDE)
        val convertedNull = mapToRatkoLocationTrackType(null)

        assertEquals("Ei määritelty", convertedNull.value)
        assertEquals("turvaraide", convertedAlignmentTrap.value)
        assertEquals("pääraide", convertedAlignmentMain.value)
        assertEquals("kujaraide", convertedAlignmentChord.value)
        assertEquals("sivuraide", convertedAlignmentSide.value)
    }

    @Test
    fun shouldMapToRatkoStateType() {

        val convertedLayoutNotInUse = mapToRatkoLocationTrackState(LayoutState.NOT_IN_USE)
        val convertedLayoutInUse = mapToRatkoLocationTrackState(LayoutState.IN_USE)

        assertEquals("NOT IN USE", convertedLayoutNotInUse.value)
        assertEquals("IN USE", convertedLayoutInUse.value)
    }
}
