package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.convertToRatkoAssetLocations
import fi.fta.geoviite.infra.ratko.model.mapToRatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.mapToRatkoLocationTrackType
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

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
        val convertedLayoutBuilt = mapToRatkoLocationTrackState(LocationTrackState.BUILT)
        val convertedLayoutNotInUse = mapToRatkoLocationTrackState(LocationTrackState.NOT_IN_USE)
        val convertedLayoutInUse = mapToRatkoLocationTrackState(LocationTrackState.IN_USE)

        assertEquals("BUILT", convertedLayoutBuilt.value)
        assertEquals("NOT IN USE", convertedLayoutNotInUse.value)
        assertEquals("IN USE", convertedLayoutInUse.value)
    }

    @Test
    fun `should not return locations with empty nodes`() {
        val jointChanges =
            listOf(
                SwitchJointChange(
                    number = JointNumber(1),
                    isRemoved = false,
                    address = TrackMeter("0000+0000"),
                    locationTrackExternalId = Oid("11.11111.1111"),
                    locationTrackId = IntId(1),
                    point = Point(0.0, 2.0),
                    trackNumberExternalId = Oid("00.00000.0000"),
                    trackNumberId = IntId(0),
                ),
                SwitchJointChange(
                    number = JointNumber(5),
                    isRemoved = false,
                    address = TrackMeter("0000+0000"),
                    locationTrackExternalId = Oid("11.11111.1111"),
                    locationTrackId = IntId(1),
                    point = Point(0.0, 1.0),
                    trackNumberExternalId = Oid("00.00000.0000"),
                    trackNumberId = IntId(0),
                ),
                SwitchJointChange(
                    number = JointNumber(2),
                    isRemoved = false,
                    address = TrackMeter("0000+0000"),
                    locationTrackExternalId = Oid("11.11111.1111"),
                    locationTrackId = IntId(1),
                    point = Point(0.0, 0.0),
                    trackNumberExternalId = Oid("00.00000.0000"),
                    trackNumberId = IntId(0),
                ),
                SwitchJointChange(
                    number = JointNumber(5),
                    isRemoved = false,
                    address = TrackMeter("0000+0000"),
                    locationTrackExternalId = Oid("22.22222.2222"),
                    locationTrackId = IntId(2),
                    point = Point(0.0, 1.0),
                    trackNumberExternalId = Oid("00.00000.0000"),
                    trackNumberId = IntId(0),
                ),
            )

        val locations = convertToRatkoAssetLocations(jointChanges, SwitchBaseType.KRV)

        assertTrue(locations.all { it.nodecollection.nodes.isNotEmpty() })
    }
}
