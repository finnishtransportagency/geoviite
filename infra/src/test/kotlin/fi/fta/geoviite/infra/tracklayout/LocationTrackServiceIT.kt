package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.DeletingFailureException
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
import kotlin.test.assertContains


@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackServiceIT @Autowired constructor(
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchService: LayoutSwitchService,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        locationTrackDao.deleteDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchDao.deleteDrafts()
    }

    @Test
    fun creatingAndDeletingUnpublishedTrackWithAlignmentWorks() {
        val (track, alignment) = locationTrackAndAlignment(insertDraftTrackNumber(), someSegment())
        val (id, version) = locationTrackService.saveDraft(track, alignment)
        val (savedTrack, savedAlignment) = locationTrackService.getWithAlignment(version)
        assertTrue(alignmentExists(savedTrack.alignmentVersion!!.id))
        assertEquals(savedTrack.alignmentVersion?.id, savedAlignment.id as IntId)
        val deletedVersion = locationTrackService.deleteDraft(id).rowVersion
        assertEquals(version, deletedVersion)
        assertFalse(alignmentExists(savedTrack.alignmentVersion!!.id))
    }

    @Test
    fun deletingOfficialLocationTrackThrowsException() {
        val (track, alignment) = locationTrackAndAlignment(insertOfficialTrackNumber(), someSegment())
        val version = locationTrackService.saveDraft(track, alignment)
        publish(version.id)
        assertThrows<DeletingFailureException> { locationTrackService.deleteDraft(version.id) }
    }

    @Test
    fun nearbyLocationTracksAreFoundWithBbox() {
        val trackNumberId = insertDraftTrackNumber()
        val (trackInside, alignmentInside) = locationTrackAndAlignment(
            trackNumberId, segment(
                Point(x = 0.0, y = 0.0),
                Point(x = 5.0, y = 0.0),
                startM = 5.0,
            )
        )
        val (trackOutside, alignmentOutside) = locationTrackAndAlignment(
            trackNumberId, segment(
                Point(x = 20.0, y = 20.0),
                Point(x = 30.0, y = 20.0),
                startM = 10.0,
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

        val updateRequest =
            saveRequest(trackNumberId, 2).copy(topologicalConnectivity = TopologicalConnectivityType.NONE)
        val updatedTrackVersion = locationTrackService.update(id, updateRequest).rowVersion
        assertEquals(id, updatedTrackVersion.id)
        assertNotEquals(insertedTrackVersion.version, updatedTrackVersion.version)
        val updatedTrack = locationTrackService.get(DRAFT, id)!!
        assertMatches(updateRequest, updatedTrack)
        assertEquals(insertedTrack.alignmentVersion, updatedTrack.alignmentVersion)
        val changeTimeAfterUpdate = locationTrackService.getChangeTime()

        val changeInfo = locationTrackService.getDraftableChangeInfo(id)
        assertEquals(changeTimeAfterInsert, changeInfo.created)
        assertEquals(changeTimeAfterUpdate, changeInfo.draftChanged)
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
            alignmentTmp.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment.segments.flatMap(LayoutSegment::alignmentPoints),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = locationTrackService.saveDraft(editedDraft, alignmentTmp2)
        assertEquals(publishResponse.id, editedVersion2.id)
        assertNotEquals(publishResponse.rowVersion.id, editedVersion2.rowVersion.id)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishResponse.id)
        assertEquals(
            alignmentTmp2.segments.flatMap(LayoutSegment::alignmentPoints),
            editedAlignment2.segments.flatMap(LayoutSegment::alignmentPoints),
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
        val locationTrack = locationTrackService.get(DRAFT, id)!!

        assertNull(locationTrack.externalId)

        locationTrackService.updateExternalId(locationTrack.id as IntId, externalIdForLocationTrack())

        val updatedLocationTrack = locationTrackService.get(DRAFT, id)!!
        assertNotNull(updatedLocationTrack.externalId)
    }

    @Test
    fun returnsNullIfFetchingDraftOnlyLocationTrackUsingOfficialFetch() {
        val trackNumber = insertOfficialTrackNumber()
        val draft = createAndVerifyTrack(trackNumber, 35)

        assertNull(locationTrackService.get(OFFICIAL, draft.first.id))
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

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(10.0, 0.0)),
                segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                ),
                segment(Point(20.0, 0.0), Point(30.0, 0.0)),
            )
        )

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(10.2, 0.0), Point(10.2, 20.2))
            )
        )
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

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(10.0, 0.0)),
                segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                ),
                segment(Point(20.0, 0.0), Point(30.0, 0.0)),
            )
        )

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(20.2, -20.0), Point(20.2, 0.2))
            )
        )
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

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(1000.0, 1000.0), Point(1010.0, 1000.0)),
                segment(Point(1010.0, 1000.0), Point(1020.0, 1000.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                ),
                segment(Point(1020.0, 1000.0), Point(1030.0, 1000.0)),
            )
        )

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(1010.0, 990.0), Point(1010.0, 1000.0), Point(1010.0, 1010.0))
            )
        )
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

        val (_, _) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(10.0, 0.0)),
                segment(Point(10.0, 0.0), Point(20.0, 0.0)).copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                ),
                segment(Point(20.0, 0.0), Point(30.0, 0.0)),
            )
        )

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(12.0, 0.0), Point(22.0, 0.0))
            )
        )
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
            alignment(segment(Point(2000.0, 2000.0), Point(2010.0, 2010.0))),
        )

        val (track1, alignment1) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(2000.0, 2000.0), Point(2022.0, 2000.0))
            )
        )
        assertEquals(null, track1.topologyStartSwitch)
        assertEquals(null, track1.topologyEndSwitch)

        val updatedTrack1 = locationTrackService.updateTopology(track1, alignment1)
        assertEquals(TopologyLocationTrackSwitch(switchId1, JointNumber(3)), updatedTrack1.topologyStartSwitch)
        assertEquals(null, updatedTrack1.topologyEndSwitch)

        val (track2, alignment2) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(2009.9, 2010.0), Point(1990.0, 1990.0))
            )
        )
        assertEquals(null, track2.topologyStartSwitch)
        assertEquals(null, track2.topologyEndSwitch)

        val updatedTrack2 = locationTrackService.updateTopology(track2, alignment2)
        assertEquals(TopologyLocationTrackSwitch(switchId2, JointNumber(5)), updatedTrack2.topologyStartSwitch)
        assertEquals(null, updatedTrack2.topologyEndSwitch)

        val (track3, alignment3) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(1990.0, 1990.0), Point(1999.9, 2000.0))
            )
        )
        assertEquals(null, track3.topologyStartSwitch)
        assertEquals(null, track3.topologyEndSwitch)

        val updatedTrack3 = locationTrackService.updateTopology(track3, alignment3)
        assertEquals(null, updatedTrack3.topologyStartSwitch)
        assertEquals(TopologyLocationTrackSwitch(switchId1, JointNumber(3)), updatedTrack3.topologyEndSwitch)

        val (track4, alignment4) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(2020.0, 2020.0), Point(2010.1, 2009.9))
            )
        )
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

        val (track, alignment) = insertAndFetch(
            locationTrack(trackNumberId), alignment(
                segment(Point(0.0, 0.0), Point(22.0, 0.0))
            )
        )
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(TopologyLocationTrackSwitch(switchId, JointNumber(3)), updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    @Test
    fun `getLocationTrackSwitches finds both topology and segment switches`() {
        val trackNumberId = getUnusedTrackNumberId()
        val topologyStartSwitchId = insertAndFetch(switch()).id as IntId
        val topologyEndSwitchId = insertAndFetch(switch()).id as IntId
        val segmentSwitchId = insertAndFetch(switch()).id as IntId

        val (track, _) = insertAndFetch(
            locationTrack(trackNumberId).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(topologyStartSwitchId, JointNumber(3)),
                topologyEndSwitch = TopologyLocationTrackSwitch(topologyEndSwitchId, JointNumber(5)),
            ),
            alignment(
                segment(
                    Point(0.0, 0.0),
                    Point(10.0, 0.0),
                    switchId = segmentSwitchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2)
                )
            ),
        )

        val switches = locationTrackService.getSwitchesForLocationTrack(track.id as IntId, DRAFT)
        assertContains(switches, topologyEndSwitchId)
        assertContains(switches, topologyStartSwitchId)
        assertContains(switches, segmentSwitchId)
    }

    @Test
    fun fetchDuplicatesIsVersioned() {
        val trackNumberId = getUnusedTrackNumberId()
        val originalLocationTrackId = locationTrackService.insert(saveRequest(trackNumberId, 1)).id
        publish(originalLocationTrackId)
        val officialCopy = insertAndFetch(
            locationTrack(getUnusedTrackNumberId()).copy(duplicateOf = originalLocationTrackId), alignment()
        )
        publish(officialCopy.first.id as IntId<LocationTrack>)

        val draftCopyVersion = locationTrackService.update(
            officialCopy.first.id as IntId, saveRequest(trackNumberId, 1).copy(duplicateOf = originalLocationTrackId)
        )
        val draftCopy = locationTrackService.get(DRAFT, draftCopyVersion.id)!!
        assertEquals(
            listOf(asLocationTrackDuplicate(officialCopy.first)),
            locationTrackService.getInfoboxExtras(OFFICIAL, originalLocationTrackId)?.duplicates
        )
        assertEquals(
            listOf(asLocationTrackDuplicate(draftCopy)),
            locationTrackService.getInfoboxExtras(DRAFT, originalLocationTrackId)?.duplicates
        )
    }

    @Test
    fun fetchDuplicatesIsOrderedByTrackAddress() {
        val trackNumberId = getUnusedTrackNumberId()
        val fullAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0))))
        referenceLineDao.insert(referenceLine(trackNumberId, alignmentVersion = fullAlignment))

        val fullTrack = locationTrackDao.insert(locationTrack(trackNumberId, alignmentVersion = fullAlignment))

        fun makeDuplicateAt(xCoord: Double, name: String) {
            val duplicateAlignment =
                alignmentDao.insert(alignment(segment(Point(xCoord, 0.0), Point(xCoord + 10.0, 0.0))))
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId, name = name, alignmentVersion = duplicateAlignment, duplicateOf = fullTrack.id
                )
            )
        }

        makeDuplicateAt(30.0, "dupA")
        makeDuplicateAt(10.0, "dupB")
        makeDuplicateAt(80.0, "dupC")
        makeDuplicateAt(20.0, "dupD")

        val extras = locationTrackService.getInfoboxExtras(OFFICIAL, fullTrack.id)
        assertEquals(listOf("dupB", "dupD", "dupA", "dupC"), extras?.duplicates?.map { dup -> dup.name.toString() })
    }

    private fun asLocationTrackDuplicate(locationTrack: LocationTrack) =
        LocationTrackDuplicate(locationTrack.id as IntId, locationTrack.name, locationTrack.externalId)

    private fun insertAndFetch(switch: TrackLayoutSwitch) =
        switchDao.fetch(switchService.saveDraft(switch).rowVersion)

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
        val insertedTrack = locationTrackService.get(DRAFT, insertedTrackVersion.id)!!
        assertMatches(insertRequest, insertedTrack)
        return insertedTrackVersion to insertedTrack
    }

    private fun publishAndVerify(
        locationTrackId: IntId<LocationTrack>,
    ): Pair<DaoResponse<LocationTrack>, LocationTrack> {
        val (draft, draftAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
        assertNotNull(draft.draft)

        val publishedVersion = publish(draft.id as IntId)
        val (published, publishedAlignment) = locationTrackService.getWithAlignmentOrThrow(
            OFFICIAL, publishedVersion.id
        )
        assertNull(published.draft)
        assertEquals(draft.id, published.id)
        assertEquals(published.id, publishedVersion.id)
        assertEquals(draft.alignmentVersion, published.alignmentVersion)
        assertEquals(draftAlignment, publishedAlignment)

        return publishedVersion to published
    }

    private fun getAndVerifyDraft(id: IntId<LocationTrack>): LocationTrack {
        val draft = locationTrackService.get(DRAFT, id)!!
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
        assertEquals(saveRequest.descriptionBase, locationTrack.descriptionBase)
        assertEquals(saveRequest.state, locationTrack.state)
        assertEquals(saveRequest.type, locationTrack.type)
        assertEquals(saveRequest.topologicalConnectivity, locationTrack.topologicalConnectivity)
        assertEquals(saveRequest.ownerId, locationTrack.ownerId)
    }

    private fun saveRequest(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        seed: Int,
    ) = LocationTrackSaveRequest(
        name = AlignmentName("TST-TRACK$seed"),
        descriptionBase = FreeText("Description - $seed"),
        descriptionSuffix = DescriptionSuffixType.NONE,
        type = getSomeValue(seed),
        state = getSomeValue(seed),
        trackNumberId = trackNumberId,
        duplicateOf = null,
        topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
        ownerId = IntId(1)
    )

    private fun publish(id: IntId<LocationTrack>) = locationTrackDao
        .fetchPublicationVersions(listOf(id))
        .first()
        .let { version -> locationTrackService.publish(version) }
}
