package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationDaoIT @Autowired constructor(
    val publicationDao: PublicationDao,
    val switchDao: LayoutSwitchDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
): ITTestBase() {

    @BeforeEach
    fun setup() {
        locationTrackDao.deleteDrafts()
        referenceLineDao.deleteDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchDao.deleteDrafts()
        kmPostDao.deleteDrafts()
        trackNumberDao.deleteDrafts()
    }

    @Test
    fun noPublishCandidatesFoundWithoutDrafts() {
        assertTrue(publicationDao.fetchTrackNumberPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchReferenceLinePublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchLocationTrackPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchSwitchPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchKmPostPublishCandidates().isEmpty())
    }

    @Test
    fun referenceLinePublishCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(getUnusedTrackNumber())).id as IntId
        val line = insertAndCheck(referenceLine(trackNumberId))
        val draft = insertAndCheck(draft(line).copy(startAddress = TrackMeter("0123", 658.321, 3)))
        val candidates = publicationDao.fetchReferenceLinePublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
    }

    @Test
    fun locationTrackPublishCandidatesAreFound() {
        val track = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val draft = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
    }

    @Test
    fun switchPublishCandidatesAreFound() {
        val switch = insertAndCheck(switch(987))
        val draft = insertAndCheck(draft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publicationDao.fetchSwitchPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
    }

    @Test
    fun allCalculatedChangesAreRecorded() {
        val trackNumberId = insertOfficialTrackNumber()
        val locationTrackId = insertAndCheck(locationTrack(trackNumberId)).id as IntId<LocationTrack>
        val switchId = insertAndCheck(switch(234)).id as IntId<TrackLayoutSwitch>

        val switchJointChange = SwitchJointChange(
            JointNumber(1),
            false,
            TrackMeter(1234, "AA", 12.34, 3),
            Point(100.0, 200.0),
            locationTrackId,
            Oid("123.456.789"),
            trackNumberId,
            Oid("1.234.567")
        )

        val changes = CalculatedChanges(
            listOf(
                TrackNumberChange(
                    trackNumberId,
                    setOf(KmNumber(1234), KmNumber(45, "AB")),
                    true,
                    false
                )
            ), listOf(
                LocationTrackChange(
                    locationTrackId,
                    setOf(KmNumber(456)),
                    false,
                    true
                )
            ), listOf(
                SwitchChange(switchId, listOf(switchJointChange))
            )
        )
        val publishId = publicationDao.createPublish(listOf(), listOf(), listOf(), listOf(), listOf())
        publicationDao.savePublishCalculatedChanges(publishId, changes)
        val fetchedChanges = publicationDao.fetchCalculatedChangesInPublish(publishId)
        assertEquals(changes, fetchedChanges)
    }

    private fun insertAndCheck(trackNumber: TrackLayoutTrackNumber): TrackLayoutTrackNumber {
        val dbVersion = trackNumberDao.insert(trackNumber)
        val fromDb = trackNumberDao.fetch(dbVersion)
        assertMatches(trackNumber, fromDb)
        assertTrue { fromDb.id is IntId }
        return fromDb
    }

    private fun insertAndCheck(switch: TrackLayoutSwitch): TrackLayoutSwitch {
        val fromDb = switchDao.fetch(switchDao.insert(switch))
        assertMatches(switch, fromDb)
        assertTrue(fromDb.id is IntId)
        return fromDb
    }

    private fun insertAndCheck(referenceLine: ReferenceLine): ReferenceLine {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val lineWithAlignment = referenceLine.copy(alignmentVersion = dbAlignmentVersion)
        val fromDb = referenceLineDao.fetch(referenceLineDao.insert(lineWithAlignment))
        assertMatches(lineWithAlignment, fromDb)
        assertTrue(fromDb.id is IntId)
        return fromDb
    }

    private fun insertAndCheck(locationTrack: LocationTrack): LocationTrack {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val trackWithAlignment = locationTrack.copy(alignmentVersion = dbAlignmentVersion)
        val fromDb = locationTrackDao.fetch(locationTrackDao.insert(trackWithAlignment))
        assertMatches(trackWithAlignment, fromDb)
        assertTrue(fromDb.id is IntId)
        return fromDb
    }

}
