package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
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
        assertTrue(publicationDao.fetchTrackNumberPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchReferenceLinePublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchLocationTrackPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchSwitchPublishCandidates().isEmpty())
        assertTrue(publicationDao.fetchKmPostPublishCandidates().isEmpty())
    }

    @Test
    fun referenceLinePublishCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(getUnusedTrackNumber())).first.id
        val (_, line) = insertAndCheck(referenceLine(trackNumberId))
        val (_, draft) = insertAndCheck(draft(line).copy(
            startAddress = TrackMeter("0123", 658.321, 3),
        ))
        val candidates = publicationDao.fetchReferenceLinePublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun locationTrackPublishCandidatesAreFound() {
        val (_, track) = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val (_, draft) = insertAndCheck(draft(track).copy(
            name = AlignmentName("${track.name} DRAFT"),
        ))
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun switchPublishCandidatesAreFound() {
        val (_, switch) = insertAndCheck(switch(987))
        val (_, draft) = insertAndCheck(draft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publicationDao.fetchSwitchPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(UserName(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun createOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(draft(locationTrack(insertOfficialTrackNumber())))
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.CREATE, candidates.first().operation)
    }

    @Test
    fun modifyOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val (version, draft) = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            draft(locationTrackService.getOrThrow(OFFICIAL, draft.id as IntId)).let { lt -> lt.copy(
                name = AlignmentName("${lt.name} TEST"),
            ) }
        )
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun deleteOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val (version, draft) = insertAndCheck(draft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            draft(locationTrackService.getOrThrow(OFFICIAL, draft.id as IntId)).copy(
                state = LayoutState.DELETED,
            )
        )
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.DELETE, candidates.first().operation)
    }

    @Test
    fun restoreOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(insertOfficialTrackNumber()))
        val (version, draft) = insertAndCheck(draft(track).copy(
            name = AlignmentName("${track.name} DRAFT"),
            state = LayoutState.DELETED,
        ))
        publishAndCheck(version)
        locationTrackService.saveDraft(draft(locationTrackService.getOrThrow(OFFICIAL, draft.id as IntId).copy(
            state = LayoutState.IN_USE,
        )))
        val candidates = publicationDao.fetchLocationTrackPublishCandidates()
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.RESTORE, candidates.first().operation)
    }

    @Test
    fun allCalculatedChangesAreRecorded() {
        val trackNumberId = insertOfficialTrackNumber()
        val locationTrackId = insertAndCheck(locationTrack(trackNumberId)).first.id
        val switchId = insertAndCheck(switch(234)).first.id

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
        val publishId = publicationDao.createPublication(listOf(), listOf(), listOf(), listOf(), listOf(), "")
        publicationDao.savePublishCalculatedChanges(publishId, changes)
        val fetchedChanges = publicationDao.fetchCalculatedChangesInPublish(publishId)
        assertEquals(changes, fetchedChanges)
    }

    @Test
    fun `Publication message is stored and fetched correctly`() {
        val message = "Test"
        val publishId = publicationDao.createPublication(listOf(), listOf(), listOf(), listOf(), listOf(), message)
        assertEquals(message, publicationDao.getPublication(publishId).message)
    }

    @Test
    fun fetchOfficialSwitchTrackNumbers() {
        val trackNumberId = insertOfficialTrackNumber()
        val (_, switch) = insertAndCheck(switch(234, name = "Foo"))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1))
            )
        )
        insertAndCheck(draft(switch.copy(name = SwitchName("FooEdited"))))

        val publishCandidates = publicationDao.fetchSwitchPublishCandidates()
        val editedCandidate = publishCandidates.first { s -> s.name == SwitchName("FooEdited") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun fetchDraftOnlySwitchTrackNumbers() {
        val trackNumberId = insertOfficialTrackNumber()
        val (_, switch) = insertAndCheck(draft(switch(345, name = "Foo")))
        val switchId = switch.id as IntId
        insertAndCheck(
            draft(
                locationTrack(trackNumberId).copy(
                    topologyEndSwitch = TopologyLocationTrackSwitch(
                        switchId,
                        JointNumber(1)
                    )
                )
            )
        )
        val publishCandidates = publicationDao.fetchSwitchPublishCandidates()
        val editedCandidate = publishCandidates.first { s -> s.name == SwitchName("Foo") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    private fun insertAndCheck(trackNumber: TrackLayoutTrackNumber): Pair<RowVersion<TrackLayoutTrackNumber>, TrackLayoutTrackNumber> {
        val (officialId, rowVersion) = trackNumberDao.insert(trackNumber)
        val fromDb = trackNumberDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(trackNumber, fromDb)
        assertTrue { fromDb.id is IntId }
        return rowVersion to fromDb
    }

    private fun insertAndCheck(switch: TrackLayoutSwitch): Pair<RowVersion<TrackLayoutSwitch>, TrackLayoutSwitch> {
        val (officialId, rowVersion) = switchDao.insert(switch)
        val fromDb = switchDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(switch, fromDb)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun insertAndCheck(referenceLine: ReferenceLine): Pair<RowVersion<ReferenceLine>, ReferenceLine> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val lineWithAlignment = referenceLine.copy(alignmentVersion = dbAlignmentVersion)
        val (officialId, rowVersion) = referenceLineDao.insert(lineWithAlignment)
        val fromDb = referenceLineDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(lineWithAlignment, fromDb)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun insertAndCheck(locationTrack: LocationTrack): Pair<RowVersion<LocationTrack>, LocationTrack> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val trackWithAlignment = locationTrack.copy(alignmentVersion = dbAlignmentVersion)
        val (officialId, rowVersion) = locationTrackDao.insert(trackWithAlignment)
        val fromDb = locationTrackDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(trackWithAlignment, fromDb)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun publishAndCheck(rowVersion: RowVersion<LocationTrack>) {
        publishAndCheck(rowVersion, locationTrackDao, locationTrackService)
    }

}
