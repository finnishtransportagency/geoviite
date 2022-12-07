package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
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
class PublishDaoIT @Autowired constructor(
    val publishDao: PublishDao,
    val switchDao: LayoutSwitchDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackService: LocationTrackService,
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
        assertTrue(publishDao.fetchTrackNumberPublishCandidates().isEmpty())
        assertTrue(publishDao.fetchReferenceLinePublishCandidates().isEmpty())
        assertTrue(publishDao.fetchLocationTrackPublishCandidates().isEmpty())
        assertTrue(publishDao.fetchSwitchPublishCandidates().isEmpty())
        assertTrue(publishDao.fetchKmPostPublishCandidates().isEmpty())
    }

    @Test
    fun referenceLinePublishCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(getUnusedTrackNumber())).id as IntId
        val line = insertAndCheck(referenceLine(trackNumberId))
        val draft = insertAndCheck(draft(line).copy(startAddress = TrackMeter("0123", 658.321, 3)))
        val candidates = publishDao.fetchReferenceLinePublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun locationTrackPublishCandidatesAreFound() {
        val track = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val draft = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        val candidates = publishDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun switchPublishCandidatesAreFound() {
        val switch = insertAndCheck(switch(987))
        val draft = insertAndCheck(draft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publishDao.fetchSwitchPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun createOperationIsInferredCorrectly() {
        val track = insertAndCheck(draft(locationTrack(insertOfficialTrackNumber())))
        val candidates = publishDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.CREATE, candidates.first().operation)
    }

    @Test
    fun modifyOperationIsInferredCorrectly() {
        val track = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val draft = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        locationTrackService.publish(draft.id as IntId)
        locationTrackService.saveDraft(draft(locationTrackService.getOrThrow(PublishType.OFFICIAL, draft.id as IntId).let { lt -> lt.copy(name = AlignmentName("${lt.name} TEST")) }))
        val candidates = publishDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun deleteOperationIsInferredCorrectly() {
        val track = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val draft = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        locationTrackService.publish(draft.id as IntId)
        locationTrackService.saveDraft(draft(locationTrackService.getOrThrow(PublishType.OFFICIAL, draft.id as IntId).copy(state = LayoutState.DELETED)))
        val candidates = publishDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.DELETE, candidates.first().operation)
    }

    @Test
    fun restoreOperationIsInferredCorrectly() {
        val track = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val draft = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT"), state = LayoutState.DELETED))
        locationTrackService.publish(draft.id as IntId)
        locationTrackService.saveDraft(draft(locationTrackService.getOrThrow(PublishType.OFFICIAL, draft.id as IntId).copy(state = LayoutState.IN_USE)))
        val candidates = publishDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.RESTORE, candidates.first().operation)
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
        val publishId = publishDao.createPublish(listOf(), listOf(), listOf(), listOf(), listOf())
        publishDao.savePublishCalculatedChanges(publishId, changes)
        val fetchedChanges = publishDao.fetchCalculatedChangesInPublish(publishId)
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
