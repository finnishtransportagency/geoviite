package fi.fta.geoviite.infra.publication

import daoResponseToValidationVersion
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.DirectChanges
import fi.fta.geoviite.infra.integration.IndirectChanges
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
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
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun noPublicationCandidatesFoundWithoutDrafts() {
        assertTrue(publicationDao.fetchTrackNumberPublicationCandidates(LayoutBranch.main).isEmpty())
        assertTrue(publicationDao.fetchReferenceLinePublicationCandidates(LayoutBranch.main).isEmpty())
        assertTrue(publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main).isEmpty())
        assertTrue(publicationDao.fetchSwitchPublicationCandidates(LayoutBranch.main).isEmpty())
        assertTrue(publicationDao.fetchKmPostPublicationCandidates(LayoutBranch.main).isEmpty())
    }

    @Test
    fun referenceLinePublicationCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).first.id
        val (_, line) = insertAndCheck(referenceLine(trackNumberId, draft = false))
        val (_, draft) = insertAndCheck(
            asMainDraft(line).copy(startAddress = TrackMeter("0123", 658.321, 3))
        )
        val candidates = publicationDao.fetchReferenceLinePublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun locationTrackPublicationCandidatesAreFound() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (_, draft) = insertAndCheck(
            asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT"))
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun switchPublicationCandidatesAreFound() {
        val (_, switch) = insertAndCheck(switch(987, draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publicationDao.fetchSwitchPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun createOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = true))
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.CREATE, candidates.first().operation)
    }

    @Test
    fun modifyOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.id as IntId).let { lt ->
                lt.copy(name = AlignmentName("${lt.name} TEST"))
            },
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun deleteOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = AlignmentName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.DELETED),
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.DELETE, candidates.first().operation)
    }

    @Test
    fun restoreOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(
            asMainDraft(track).copy(
                name = AlignmentName("${track.name} DRAFT"),
                state = LocationTrackState.DELETED,
            )
        )
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.IN_USE),
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(LayoutBranch.main)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.RESTORE, candidates.first().operation)
    }

    @Test
    fun allCalculatedChangesAreRecorded() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackId = insertAndCheck(locationTrack(trackNumberId, draft = false)).first.id
        val switchId = insertAndCheck(switch(234, draft = false)).first.id

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
            directChanges = DirectChanges(
                trackNumberChanges = listOf(
                    TrackNumberChange(
                        trackNumberId,
                        setOf(KmNumber(1234), KmNumber(45, "AB")),
                        isStartChanged = true,
                        isEndChanged = false
                    ),
                ),
                kmPostChanges = emptyList(),
                referenceLineChanges = emptyList(),
                locationTrackChanges = listOf(
                    LocationTrackChange(
                        locationTrackId,
                        setOf(KmNumber(456)),
                        isStartChanged = false,
                        isEndChanged = true,
                    ),
                ),
                switchChanges = listOf(
                    SwitchChange(switchId, listOf(switchJointChange)),
                ),
            ),
            indirectChanges = IndirectChanges(emptyList(), emptyList(), emptyList()),
        )
        val publicationId = publicationDao.createPublication("")
        publicationDao.insertCalculatedChanges(publicationId, changes)

        val publishedTrackNumbers = publicationDao.fetchPublishedTrackNumbers(publicationId)
        val publishedLocationTracks = publicationDao.fetchPublishedLocationTracks(publicationId)
        val publishedSwitches = publicationDao.fetchPublishedSwitches(publicationId)
        assertTrue(publishedTrackNumbers.directChanges.all { it.version.id == trackNumberId })
        assertEquals(
            changes.directChanges.trackNumberChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedTrackNumbers.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedLocationTracks.directChanges.all { it.version.id == locationTrackId })
        assertEquals(
            changes.directChanges.locationTrackChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedLocationTracks.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedSwitches.directChanges.all { it.version.id == switchId })
        assertEquals(listOf(switchJointChange), publishedSwitches.directChanges.flatMap { it.changedJoints })
    }

    @Test
    fun `Publication message is stored and fetched correctly`() {
        val message = "Test"
        val publicationId = publicationDao.createPublication(message)
        assertEquals(message, publicationDao.getPublication(publicationId).message)
    }

    @Test
    fun fetchOfficialSwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(234, name = "Foo", draft = false))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = false).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1))
            )
        )
        insertAndCheck(asMainDraft(switch.copy(name = SwitchName("FooEdited"))))

        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(LayoutBranch.main)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("FooEdited") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun fetchDraftOnlySwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(345, name = "Foo", draft = true))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = true).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(
                    switchId,
                    JointNumber(1),
                )
            )
        )
        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(LayoutBranch.main)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("Foo") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun `fetchLinkedLocationTracks works on publication units`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchByAlignment = switchDao.insert(switch(1, draft = false)).id
        val switchByTopo = switchDao.insert(switch(2, draft = false)).id
        val dummyAlignment = alignmentDao.insert(alignment())
        val officialLinkedTopo = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchByTopo, JointNumber(1)),
                alignmentVersion = dummyAlignment,
                draft = false,
            )
        )
        val draftLinkedTopo = locationTrackDao.insert(
            locationTrack(
                trackNumberId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchByTopo, JointNumber(3)),
                alignmentVersion = dummyAlignment,
                draft = true,
            )
        )
        val officialLinkedAlignment = locationTrackDao.insert(
            locationTrack(
                trackNumberId = trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(
                            Point(0.0, 0.0),
                            Point(1.0, 1.0),
                            switchId = switchByAlignment,
                            startJointNumber = JointNumber(1)
                        )
                    )
                ),
                draft = false,
            )
        )
        val draftLinkedAlignment = locationTrackDao.insert(
            locationTrack(
                trackNumberId = trackNumberId,
                alignmentVersion = alignmentDao.insert(
                    alignment(
                        segment(
                            Point(2.0, 2.0),
                            Point(3.0, 3.0),
                            switchId = switchByAlignment,
                            startJointNumber = JointNumber(3)
                        )
                    )
                ),
                draft = true,
            )
        )
        assertEquals(
            setOf(
                daoResponseToValidationVersion(officialLinkedAlignment),
                daoResponseToValidationVersion(draftLinkedAlignment),
            ),
            publicationDao.fetchLinkedLocationTracks(LayoutBranch.main, listOf(switchByAlignment))[switchByAlignment],
        )
        assertEquals(
            setOf(daoResponseToValidationVersion(officialLinkedAlignment)),
            publicationDao.fetchLinkedLocationTracks(
                LayoutBranch.main,
                listOf(switchByAlignment),
                listOf()
            )[switchByAlignment]
        )
        assertEquals(
            setOf(
                daoResponseToValidationVersion(officialLinkedAlignment),
                daoResponseToValidationVersion(draftLinkedAlignment),
            ),
            publicationDao.fetchLinkedLocationTracks(
                LayoutBranch.main,
                listOf(switchByAlignment),
                listOf(draftLinkedAlignment.id),
            )[switchByAlignment],
        )
        assertEquals(
            setOf(
                daoResponseToValidationVersion(officialLinkedTopo),
                daoResponseToValidationVersion(draftLinkedTopo),
            ),
            publicationDao.fetchLinkedLocationTracks(LayoutBranch.main, listOf(switchByTopo))[switchByTopo],
        )
        assertEquals(
            setOf(daoResponseToValidationVersion(officialLinkedTopo)),
            publicationDao.fetchLinkedLocationTracks(LayoutBranch.main, listOf(switchByTopo), listOf())[switchByTopo]
        )
        assertEquals(
            setOf(daoResponseToValidationVersion(officialLinkedTopo), daoResponseToValidationVersion(draftLinkedTopo)),
            publicationDao.fetchLinkedLocationTracks(
                LayoutBranch.main,
                listOf(switchByTopo),
                listOf(draftLinkedTopo.id)
            )[switchByTopo]
        )
    }

    private fun insertAndCheck(
        trackNumber: TrackLayoutTrackNumber,
    ): Pair<RowVersion<TrackLayoutTrackNumber>, TrackLayoutTrackNumber> {
        val (officialId, rowVersion) = trackNumberDao.insert(trackNumber)
        val fromDb = trackNumberDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(trackNumber, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, trackNumber.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue { fromDb.id is IntId }
        return rowVersion to fromDb
    }

    private fun insertAndCheck(switch: TrackLayoutSwitch): Pair<RowVersion<TrackLayoutSwitch>, TrackLayoutSwitch> {
        val (officialId, rowVersion) = switchDao.insert(switch)
        val fromDb = switchDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(switch, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, switch.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun insertAndCheck(referenceLine: ReferenceLine): Pair<RowVersion<ReferenceLine>, ReferenceLine> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val lineWithAlignment = referenceLine.copy(alignmentVersion = dbAlignmentVersion)
        val (officialId, rowVersion) = referenceLineDao.insert(lineWithAlignment)
        val fromDb = referenceLineDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(lineWithAlignment, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, referenceLine.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun insertAndCheck(locationTrack: LocationTrack): Pair<RowVersion<LocationTrack>, LocationTrack> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val trackWithAlignment = locationTrack.copy(alignmentVersion = dbAlignmentVersion)
        val (officialId, rowVersion) = locationTrackDao.insert(trackWithAlignment)
        val fromDb = locationTrackDao.fetch(rowVersion)
        assertEquals(officialId, fromDb.id)
        assertMatches(trackWithAlignment, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, locationTrack.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return rowVersion to fromDb
    }

    private fun publishAndCheck(rowVersion: RowVersion<LocationTrack>) {
        publishAndCheck(rowVersion, locationTrackDao, locationTrackService)
    }
}
