package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackServiceIT @Autowired constructor(
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchService: LayoutSwitchService,
): ITTestBase() {

    @BeforeEach
    fun setup() {
        locationTrackService.deleteAllDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchService.deleteAllDrafts()
    }

    @Test
    fun creatingAndDeletingUnpublishedTrackWithAlignmentWorks() {
        val (track, alignment) = locationTrackAndAlignment(insertDraftTrackNumber(), someSegment())
        val (id, version) = locationTrackService.saveDraft(track, alignment)
        val (savedTrack, savedAlignment) = locationTrackService.getWithAlignment(version)
        assertTrue(alignmentExists(savedTrack.alignmentVersion!!.id))
        assertEquals(savedTrack.alignmentVersion?.id, savedAlignment.id as IntId)
        val deletedVersion = locationTrackService.deleteUnpublishedDraft(id).rowVersion
        assertEquals(version, deletedVersion)
        assertFalse(alignmentExists(savedTrack.alignmentVersion!!.id))
    }

    @Test
    fun deletingOfficialLocationTrackThrowsException() {
        val (track, alignment) = locationTrackAndAlignment(insertOfficialTrackNumber(), someSegment())
        val version = locationTrackService.saveDraft(track, alignment)
        publish(version.id)
        assertThrows<NoSuchEntityException> { locationTrackService.deleteUnpublishedDraft(version.id) }
    }

    @Test
    fun nearbyLocationTracksAreFoundWithBbox() {
        val trackNumberId = insertDraftTrackNumber()
        val (trackInside, alignmentInside) = locationTrackAndAlignment(
            trackNumberId,
            segment(
                Point(x = 0.0, y = 0.0),
                Point(x = 5.0, y = 0.0),
                startLength = 5.0,
            )
        )
        val (trackOutside, alignmentOutside) = locationTrackAndAlignment(
            trackNumberId,
            segment(
                Point(x = 20.0, y = 20.0),
                Point(x = 30.0, y = 20.0),
                startLength = 10.0,
            )
        )

        val alignmentIdInBbox = locationTrackService.saveDraft(trackInside, alignmentInside).id
        val alignmentIdOutsideBbox = locationTrackService.saveDraft(trackOutside, alignmentOutside).id

        val boundingBox = BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0))

        val tracksAndAlignments = locationTrackService.listNearWithAlignments(DRAFT, boundingBox)

        assertTrue(tracksAndAlignments.any { (t, _) -> t.id == alignmentIdInBbox })
        assertTrue(tracksAndAlignments.none { (t, _) -> t.id == alignmentIdOutsideBbox })
    }


    @Test
    fun locationTrackInsertAndUpdateWorks() {
        val trackNumberId = insertDraftTrackNumber()

        val (response, insertedTrack) = createAndVerifyTrack(trackNumberId, 1)
        val (id, insertedTrackVersion) = response
        val changeTimeAfterInsert = locationTrackService.getChangeTime()

        val updateRequest = saveRequest(trackNumberId, 2).copy(topologicalConnectivity = TopologicalConnectivityType.NONE)
        val updatedTrackVersion = locationTrackService.update(id, updateRequest).rowVersion
        assertEquals(id, updatedTrackVersion.id)
        assertNotEquals(insertedTrackVersion.version, updatedTrackVersion.version)
        val updatedTrack = locationTrackService.getDraft(id)
        assertMatches(updateRequest, updatedTrack)
        assertEquals(insertedTrack.alignmentVersion, updatedTrack.alignmentVersion)
        val changeTimeAfterUpdate = locationTrackService.getChangeTime()

        val trackChangeTimes = locationTrackService.getChangeTimes(id)
        assertEquals(changeTimeAfterInsert, trackChangeTimes.created)
        assertEquals(changeTimeAfterUpdate, trackChangeTimes.draftChanged)
    }



    @Test
    fun savingCreatesDraft() {
        val (publishResponse, published) = createPublishedLocationTrack(1)

        val editedVersion = locationTrackService.saveDraft(published.copy(name = AlignmentName("EDITED1")))
        assertEquals(publishResponse.id, editedVersion.id)
        assertNotEquals(publishResponse.rowVersion.id, editedVersion.rowVersion.id)

        val editedDraft = getAndVerifyDraft(publishResponse.id)
        assertEquals(AlignmentName("EDITED1"), editedDraft.name)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 = locationTrackService.saveDraft(editedDraft.copy(name = AlignmentName("EDITED2")))
        assertEquals(publishResponse.id, editedVersion2.id)
        assertNotEquals(publishResponse.rowVersion.id, editedVersion2.rowVersion.id)

        val editedDraft2 = getAndVerifyDraft(publishResponse.id)
        assertEquals(AlignmentName("EDITED2"), editedDraft2.name)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingWithAlignmentCreatesDraft() {
        val (publishResponse, published) = createPublishedLocationTrack(2)

        val alignmentTmp = alignment(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion = locationTrackService.saveDraft(published, alignmentTmp)
        assertEquals(publishResponse.id, editedVersion.id)
        assertNotEquals(publishResponse.rowVersion.id, editedVersion.rowVersion.id)

        val (editedDraft, editedAlignment) = getAndVerifyDraftWithAlignment(publishResponse.id)
        assertEquals(
            alignmentTmp.segments.flatMap(LayoutSegment::points),
            editedAlignment.segments.flatMap(LayoutSegment::points),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = locationTrackService.saveDraft(editedDraft, alignmentTmp2)
        assertEquals(publishResponse.id, editedVersion2.id)
        assertNotEquals(publishResponse.rowVersion.id, editedVersion2.rowVersion.id)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishResponse.id)
        assertEquals(
            alignmentTmp2.segments.flatMap(LayoutSegment::points),
            editedAlignment2.segments.flatMap(LayoutSegment::points),
        )
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        assertNotEquals(editedDraft.alignmentVersion!!.version, editedDraft2.alignmentVersion!!.version)
    }

    @Test
    fun updatingExternalIdWorks() {
        val trackNumberId = insertDraftTrackNumber()

        val insertRequest = saveRequest(trackNumberId, 2)
        val id = locationTrackService.insert(insertRequest).id
        val locationTrack = locationTrackService.getDraft(id)

        assertNull(locationTrack.externalId)

        locationTrackService.updateExternalId(locationTrack.id as IntId, externalIdForLocationTrack())

        val updatedLocationTrack = locationTrackService.getDraft(id)
        assertNotNull(updatedLocationTrack.externalId)
    }

    @Test
    fun returnsNullIfFetchingDraftOnlyLocationTrackUsingOfficialFetch() {
        val trackNumber = insertOfficialTrackNumber()
        val draft = createAndVerifyTrack(trackNumber, 35)

        assertNull(locationTrackService.getOfficial(draft.first.id))
    }

    @Test
    fun throwsIfFetchingOfficialVersionOfDraftOnlyLocationTrackUsingGetOrThrow() {
        val trackNumber = insertOfficialTrackNumber()
        val draft = createAndVerifyTrack(trackNumber, 35)

        assertThrows<NoSuchEntityException> { (locationTrackService.getOrThrow(OFFICIAL, draft.first.id)) }
    }

    @Test
    fun updateTopologyFindsSwitchStartConnectionInTheMiddleOfAlignment() {

        val trackNumberId = getUnusedTrackNumberId()
        val switchId = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(10.0, 0.0)),
            segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(Point(20.0, 0.0), Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(10.2, 0.0), Point(10.2, 20.2))
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(TopologyLocationTrackSwitch(switchId, JointNumber(1)), updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    @Test
    fun updateTopologyFindsSwitchEndConnectionInTheMiddleOfAlignment() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(10.0, 0.0)),
            segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(Point(20.0, 0.0), Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(20.2, -20.0), Point(20.2, 0.2))
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(null, updatedTrack.topologyStartSwitch)
        assertEquals(TopologyLocationTrackSwitch(switchId, JointNumber(2)), updatedTrack.topologyEndSwitch)
    }

    @Test
    fun updateTopologyDoesntFindSwitchConnectionForTrackCrossingOverSwitch() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(10.0, 0.0)),
            segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(Point(20.0, 0.0), Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(10.0, -10.0), Point(10.0, 0.0), Point(10.0, 10.0))
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(null, updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    @Test
    fun updateTopologyDoesntFindSwitchConnectionForTrackFartherAway() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(10.0, 0.0)),
            segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(Point(20.0, 0.0), Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(12.0, 0.0), Point(22.0, 0.0))
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(null, updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    @Test
    fun updateTopologyFindsSwitchConnectionFromOtherTopology() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId1 = insertAndFetch(switch()).id as IntId
        val switchId2 = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId1, JointNumber(3)),
                topologyEndSwitch = TopologyLocationTrackSwitch(switchId2, JointNumber(5)),
            ),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))),
        )

        val (track1, alignment1) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(22.0, 0.0))
        ))
        assertEquals(null, track1.topologyStartSwitch)
        assertEquals(null, track1.topologyEndSwitch)

        val updatedTrack1 = locationTrackService.updateTopology(track1, alignment1)
        assertEquals(TopologyLocationTrackSwitch(switchId1, JointNumber(3)), updatedTrack1.topologyStartSwitch)
        assertEquals(null, updatedTrack1.topologyEndSwitch)

        val (track2, alignment2) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(9.9, 10.0), Point(-10.0, -10.0))
        ))
        assertEquals(null, track2.topologyStartSwitch)
        assertEquals(null, track2.topologyEndSwitch)

        val updatedTrack2 = locationTrackService.updateTopology(track2, alignment2)
        assertEquals(TopologyLocationTrackSwitch(switchId2, JointNumber(5)), updatedTrack2.topologyStartSwitch)
        assertEquals(null, updatedTrack2.topologyEndSwitch)

        val (track3, alignment3) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(-10.0, -10.0), Point(-0.1, 0.0))
        ))
        assertEquals(null, track3.topologyStartSwitch)
        assertEquals(null, track3.topologyEndSwitch)

        val updatedTrack3 = locationTrackService.updateTopology(track3, alignment3)
        assertEquals(null, updatedTrack3.topologyStartSwitch)
        assertEquals(TopologyLocationTrackSwitch(switchId1, JointNumber(3)), updatedTrack3.topologyEndSwitch)

        val (track4, alignment4) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(20.0, 20.0), Point(10.1, 9.9))
        ))
        assertEquals(null, track4.topologyStartSwitch)
        assertEquals(null, track4.topologyEndSwitch)

        val updatedTrack4 = locationTrackService.updateTopology(track4, alignment4)
        assertEquals(null, updatedTrack4.topologyStartSwitch)
        assertEquals(TopologyLocationTrackSwitch(switchId2, JointNumber(5)), updatedTrack4.topologyEndSwitch)
    }

    @Test
    fun updateTopologyDoesntLoseCurrentConnectionIfNothingIsFound() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId1 = insertAndFetch(switch()).id as IntId
        val switchId2 = insertAndFetch(switch()).id as IntId

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId1, JointNumber(3)),
                topologyEndSwitch = TopologyLocationTrackSwitch(switchId2, JointNumber(5)),
            ),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )
        val updated = locationTrackService.updateTopology(track, alignment)
        assertEquals(track.topologyStartSwitch, updated.topologyStartSwitch)
        assertEquals(track.topologyEndSwitch, updated.topologyEndSwitch)
    }

    @Test
    fun updateTopologyFindsSwitchConnectionFromOtherTopologyEnd() {
        val trackNumberId = getUnusedTrackNumberId()
        val switchId = insertAndFetch(switch()).id as IntId

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(3))
            ),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(Point(0.0, 0.0), Point(22.0, 0.0))
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(TopologyLocationTrackSwitch(switchId, JointNumber(3)), updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    @Test
    fun fetchDuplicatesIsVersioned() {
        val trackNumberId = getUnusedTrackNumberId()
        val originalLocationTrackId = locationTrackService.insert(saveRequest(trackNumberId, 1)).id
        publish(originalLocationTrackId)
        val officialCopy = insertAndFetch(
            locationTrack(getUnusedTrackNumberId()).copy(duplicateOf = originalLocationTrackId),
            alignment()
        )
        publish(officialCopy.first.id as IntId<LocationTrack>)

        val draftCopyVersion = locationTrackService.update(
            officialCopy.first.id as IntId,
            saveRequest(trackNumberId, 1).copy(duplicateOf = originalLocationTrackId)
        )
        val draftCopy = locationTrackService.getDraft(draftCopyVersion.id)
        assertEquals(
            listOf(asLocationtrackDuplicate(officialCopy.first)),
            locationTrackService.getDuplicates(originalLocationTrackId, OFFICIAL)
        )
        assertEquals(
            listOf(asLocationtrackDuplicate(draftCopy)),
            locationTrackService.getDuplicates(originalLocationTrackId, DRAFT)
        )
    }

    private fun asLocationtrackDuplicate(locationTrack: LocationTrack) =
        LocationTrackDuplicate(locationTrack.id as IntId, locationTrack.name, locationTrack.externalId)

    private fun insertAndFetch(switch: TrackLayoutSwitch) =
        switchService.get(switchService.saveDraft(switch).rowVersion)

    private fun insertAndFetch(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): Pair<LocationTrack, LayoutAlignment> =
        locationTrackService.getWithAlignment(locationTrackService.saveDraft(locationTrack, alignment).rowVersion)

    private fun createPublishedLocationTrack(seed: Int): Pair<DaoResponse<LocationTrack>, LocationTrack> {
        val trackNumberId = insertOfficialTrackNumber()
        val (insertedTrackVersion, _) = createAndVerifyTrack(trackNumberId, seed)
        return publishAndVerify(insertedTrackVersion.id)
    }

    private fun createAndVerifyTrack(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        seed: Int,
    ): Pair<DaoResponse<LocationTrack>, LocationTrack> {
        val insertRequest = saveRequest(trackNumberId, seed)
        val insertedTrackVersion = locationTrackService.insert(insertRequest)
        val insertedTrack = locationTrackService.getDraft(insertedTrackVersion.id)
        assertMatches(insertRequest, insertedTrack)
        return insertedTrackVersion to insertedTrack
    }

    private fun publishAndVerify(
        locationTrackId: IntId<LocationTrack>,
    ): Pair<DaoResponse<LocationTrack>, LocationTrack> {
        val (draft, draftAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        assertNotNull(draft.draft)

        val publishedVersion = publish(draft.id as IntId)
        val (published, publishedAlignment) = locationTrackService.getWithAlignmentOrThrow(OFFICIAL, publishedVersion.id)
        assertNull(published.draft)
        assertEquals(draft.id, published.id)
        assertEquals(published.id, publishedVersion.id)
        assertEquals(draft.alignmentVersion, published.alignmentVersion)
        assertEquals(draftAlignment, publishedAlignment)

        return publishedVersion to published
    }

    private fun getAndVerifyDraft(id: IntId<LocationTrack>): LocationTrack {
        val draft = locationTrackService.getDraft(id)
        assertEquals(id, draft.id)
        assertNotNull(draft.draft)
        return draft
    }

    private fun getAndVerifyDraftWithAlignment(id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        val (draft, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, id)
        assertEquals(id, draft.id)
        assertNotNull(draft.draft)
        assertEquals(draft.alignmentVersion!!.id, alignment.id)
        return draft to alignment
    }

    private fun alignmentExists(id: IntId<LayoutAlignment>): Boolean {
        val sql = "select exists(select 1 from layout.alignment where id = :id) as exists"
        val params = mapOf("id" to id.intValue)
        return jdbc.queryForObject(sql, params) { rs, _ -> rs.getBoolean("exists") }
            ?: throw IllegalStateException("Exists-check failed")
    }

    private fun assertMatches(saveRequest: LocationTrackSaveRequest, locationTrack: LocationTrack) {
        assertEquals(saveRequest.trackNumberId, locationTrack.trackNumberId)
        assertEquals(saveRequest.name, locationTrack.name)
        assertEquals(saveRequest.description, locationTrack.description)
        assertEquals(saveRequest.state, locationTrack.state)
        assertEquals(saveRequest.type, locationTrack.type)
        assertEquals(saveRequest.topologicalConnectivity, locationTrack.topologicalConnectivity)
    }

    private fun saveRequest(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        seed: Int,
    ) = LocationTrackSaveRequest(
        name = AlignmentName("TST-TRACK$seed"),
        description = FreeText("Description - $seed"),
        type = getSomeValue(seed),
        state = getSomeValue(seed),
        trackNumberId = trackNumberId,
        duplicateOf = null,
        topologicalConnectivity = TopologicalConnectivityType.START_AND_END
    )

    private fun publish(id: IntId<LocationTrack>) =
        locationTrackDao.fetchPublicationVersions(listOf(id))
            .first()
            .let { version -> locationTrackService.publish(version) }
}
