package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType.YV
import fi.fta.geoviite.infra.switchLibrary.SwitchHand.RIGHT
import fi.fta.geoviite.infra.switchLibrary.SwitchOwnerDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import org.junit.Ignore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class CsvImportIT @Autowired constructor(
    val switchStructureDao: SwitchStructureDao,
    private val switchOwnerDao: SwitchOwnerDao,
) : ITTestBase() {

    @Test
    fun shouldCreateTrackNumbersFromCsv() {
        val trackLayoutTrackNumbers = createTrackNumbersFromCsv(
            CsvFile("src/test/resources/csvImport/01-track-number.csv", TrackNumberColumns::class)
        )
        assertEquals(6, trackLayoutTrackNumbers.size)
        assertEquals(TrackNumber("001"), trackLayoutTrackNumbers[0].number)
        assertEquals("Helsinki - Kirkkonummi (PR) - Karjaa - Turku", trackLayoutTrackNumbers[0].description.value)
        assertEquals(Oid<TrackLayoutTrackNumber>("1.2.246.578.3.10001.188907"), trackLayoutTrackNumbers[0].externalId)
        assertEquals(IN_USE, trackLayoutTrackNumbers[0].state)
    }

    @Test
    fun shouldCreateKmPostsFromCsv() {
        val trackIdMap = generateTestDataTrackIdMap()
        val trackLayoutKmPosts = createKmPostsFromCsv(
            CsvFile("src/test/resources/csvImport/05-km-post.csv", KmPostColumns::class),
            trackIdMap,
        )
        assertEquals(39, trackLayoutKmPosts.size)
        assertEquals(KmNumber(10), trackLayoutKmPosts[0].kmNumber)
        assertEquals(IN_USE, trackLayoutKmPosts[0].state)
        assertEquals(trackIdMap[Oid("1.2.246.578.3.10001.188907")], trackLayoutKmPosts[0].trackNumberId)
        assertApproximatelyEquals(
            transformNonKKJCoordinate(RATKO_SRID, LAYOUT_SRID, Point(24.835685172734934, 60.218980868897404)),
            trackLayoutKmPosts[0].location!!,
        )
    }


    @Test
    fun shouldCreateLocationTracksFromCsv() {
        val trackIdMap = generateTestDataTrackIdMap()
        val alignmentsFile =
            CsvFile("src/test/resources/csvImport/03-location-track.csv", LocationTrackColumns::class)
        alignmentsFile.use { file ->
            val locationTracks = createLocationTracksFromCsv(
                file,
                mapOf(),
                mapOf(),
                trackIdMap,
                emptyList()
            ).toList()

            assertEquals(15, locationTracks.size)
            assertEquals(Oid<LocationTrack>("1.2.246.578.3.10002.189390"), locationTracks[0].locationTrack.externalId)
            assertEquals(trackIdMap[Oid("1.2.246.578.3.10001.188976")], locationTracks[0].locationTrack.trackNumberId)
            assertEquals(LocationTrackType.SIDE, locationTracks[0].locationTrack.type)
            assertEquals(IN_USE, locationTracks[0].locationTrack.state)
            assertEquals("PTS 102", locationTracks[0].locationTrack.name.value)
            assertEquals("Pietarsaari raide: 102 V111 - V114", locationTracks[0].locationTrack.description.value)
        }
    }

    @Test
    @Ignore
    fun shouldCreateSwitchesFromCsv() {
        val switches = createSwitchesFromCsv(
            CsvFile("src/test/resources/csvImport/06-switch.csv", SwitchColumns::class),
            CsvFile("src/test/resources/csvImport/07-switch-joint.csv", SwitchJointColumns::class),
            switchStructureDao.fetchSwitchStructures().associateBy { it.type },
            switchOwnerDao.fetchSwitchOwners()
        )
        val switchStructures = switchStructureDao.fetchSwitchStructures().associateBy { it.id }

        assertEquals(15, switches.size)
        val switch = switches.find { s ->
            s.externalId == Oid<TrackLayoutSwitch>("1.2.246.578.3.117.194129")
        }
        assertNotNull(switch)
        switch!!
        assertEquals("YV  V0116", switch.name.value)
        assertEquals(LayoutStateCategory.EXISTING, switch.stateCategory)
        assertEquals(YV, switchStructures[switch.switchStructureId]?.baseType)
        assertEquals(RIGHT, switchStructures[switch.switchStructureId]?.hand)
        assertEquals(false, switch.trapPoint)

        val otherSwitch = switches.find { s ->
            s.externalId == Oid<TrackLayoutSwitch>("1.2.246.578.3.117.194131")
        }
        assertNotNull(otherSwitch)
        otherSwitch!!
        assertNull(otherSwitch.trapPoint)

        assertEquals(4, switch.joints.size)

        assertEquals(
            listOf(JointNumber(1), JointNumber(3), JointNumber(5)),
            switches[0].joints.map { j -> j.number }
        )
    }

    var intIdGenerator: Int = 0
    private fun generateTestDataTrackIdMap(): Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>> =
        listOf(
            "1.2.246.578.3.10001.188907",
            "1.2.246.578.3.10001.188925",
            "1.2.246.578.3.10001.188976",
            "1.2.246.578.3.10001.189022",
            "1.2.246.578.3.10001.188957",
            "1.2.246.578.3.10001.188935",
            "1.2.246.578.3.10001.188923",
            "1.2.246.578.3.10001.188963",
            "1.2.246.578.3.10001.188912",
            "1.2.246.578.3.10001.188917",
            "1.2.246.578.3.10001.189320",
            "1.2.246.578.3.10001.188917",
            "1.2.246.578.3.10001.188923",
            "1.2.246.578.3.10001.188913",
            "1.2.246.578.3.10001.188913",
            "1.2.246.578.3.10001.188919",
            "1.2.246.578.3.10001.189320",

            ).associate { tnStr -> Oid<TrackLayoutTrackNumber>(tnStr) to IntId(intIdGenerator++) }
}
