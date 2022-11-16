package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.RowVersion
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
    private val alignmentDao: LayoutAlignmentDao,
    private val switchDao: LayoutSwitchDao,
    private val switchService: LayoutSwitchService,
): ITTestBase() {

    @BeforeEach
    fun setup() {
        locationTrackService.deleteDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchService.deleteDrafts()
    }

    @Test
    fun creatingAndDeletingUnpublishedTrackWithAlignmentWorks() {
        val (track, alignment) = locationTrackAndAlignment(insertDraftTrackNumber(), someSegment())
        val version = locationTrackService.saveDraft(track, alignment)
        val (savedTrack, savedAlignment) = locationTrackService.getWithAlignment(version)
        assertTrue(alignmentExists(savedTrack.alignmentVersion!!.id))
        assertEquals(savedTrack.alignmentVersion?.id, savedAlignment.id as IntId)
        val deletedVersion = locationTrackService.deleteUnpublishedDraft(version.id)
        assertEquals(version, deletedVersion)
        assertFalse(alignmentExists(savedTrack.alignmentVersion!!.id))
    }

    @Test
    fun deletingOfficialLocationTrackThrowsException() {
        val (track, alignment) = locationTrackAndAlignment(insertOfficialTrackNumber(), someSegment())
        val version = locationTrackService.saveDraft(track, alignment)
        locationTrackService.publish(version.id)
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

        val (insertedTrackVersion, insertedTrack) = createAndVerifyTrack(trackNumberId, 1)
        val changeTimeAfterInsert = locationTrackService.getChangeTime()

        val updateRequest = saveRequest(trackNumberId, 2).copy(topologicalConnectivity = TopologicalConnectivityType.NONE)
        val updatedTrackVersion = locationTrackService.update(insertedTrackVersion.id, updateRequest)
        assertEquals(insertedTrackVersion.id, updatedTrackVersion.id)
        assertNotEquals(insertedTrackVersion.version, updatedTrackVersion.version)
        val updatedTrack = locationTrackService.getDraft(insertedTrackVersion.id)
        assertMatches(updateRequest, updatedTrack)
        assertEquals(insertedTrack.alignmentVersion, updatedTrack.alignmentVersion)
        val changeTimeAfterUpdate = locationTrackService.getChangeTime()

        val trackChangeTimes = locationTrackService.getChangeTimes(insertedTrackVersion.id)
        assertEquals(changeTimeAfterInsert, trackChangeTimes.created)
        assertEquals(changeTimeAfterUpdate, trackChangeTimes.draftChanged)
    }



    @Test
    fun savingCreatesDraft() {
        val (publishedVersion, published) = createPublishedLocationTrack(1)

        val editedVersion = locationTrackService.saveDraft(published.copy(name = AlignmentName("EDITED1")))
        assertNotEquals(publishedVersion.id, editedVersion.id)

        val editedDraft = getAndVerifyDraft(publishedVersion.id)
        assertEquals(AlignmentName("EDITED1"), editedDraft.name)
        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val editedVersion2 = locationTrackService.saveDraft(editedDraft.copy(name = AlignmentName("EDITED2")))
        assertNotEquals(publishedVersion.id, editedVersion2.id)

        val editedDraft2 = getAndVerifyDraft(publishedVersion.id)
        assertEquals(AlignmentName("EDITED2"), editedDraft2.name)
        assertNotEquals(published.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
        // Second edit to same draft should not duplicate alignment again
        assertEquals(editedDraft.alignmentVersion!!.id, editedDraft2.alignmentVersion!!.id)
    }

    @Test
    fun savingWithAlignmentCreatesDraft() {
        val (publishedVersion, published) = createPublishedLocationTrack(2)

        val alignmentTmp = alignment(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion = locationTrackService.saveDraft(published, alignmentTmp)
        assertNotEquals(publishedVersion.id, editedVersion.id)

        val (editedDraft, editedAlignment) = getAndVerifyDraftWithAlignment(publishedVersion.id)
        assertEquals(
            alignmentTmp.segments.flatMap(LayoutSegment::points),
            editedAlignment.segments.flatMap(LayoutSegment::points),
        )

        // Creating a draft should duplicate the alignment
        assertNotEquals(published.alignmentVersion!!.id, editedDraft.alignmentVersion!!.id)

        val alignmentTmp2 = alignment(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = locationTrackService.saveDraft(editedDraft, alignmentTmp2)
        assertNotEquals(publishedVersion.id, editedVersion2.id)

        val (editedDraft2, editedAlignment2) = getAndVerifyDraftWithAlignment(publishedVersion.id)
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
    fun switchNameIsReturnedForSegment() {
        val switch = switchDao.fetch(
            switchDao.insert(switch(132))
        )
        val start = Point(428123.459, 7208379.993)
        val verticalPoints = toTrackLayoutPoints((0..3).map { n ->
            Point3DM(start.x + 0.0, start.y + 3 * n.toDouble(), 3 * n.toDouble())
        })
        val segment = segment(verticalPoints).copy(switchId = switch.id)

        assertEquals(locationTrackService.getSwitchNameForSegment(OFFICIAL, segment), switch.name)
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
    fun findsSwitchesByKm() {
        // Reference line:
        //      |---------|------------------|--------------|----------|
        //  0002+800  0003+000           0004+000       0005+000   0005+800
        //
        // Location track:
        //           |-------------|-------------|
        //        switch 1     switch 2      switch 3

        val start = Point(385000.0, 6670000.00)
        val (_, geocodingContext) = referenceLineAndGeocodingContext(
            from = start,
            to = start + Point(3000.0, 0.0),
            startAddress = TrackMeter(KmNumber(2), 800),
            kmPosts = arrayOf(
                KmNumber(3) to start + Point(200.0, 0.0),
                KmNumber(4) to start + Point(1200.0, 0.0),
                KmNumber(5) to start + Point(2200.0, 0.0)
            )
        )

        val locationTrackSegments = segments(
            start + Point(100.0, 0.0),
            start + Point(1500.0, 0.0),
            segmentLength = 10.0
        )
        val (_, alignment) = attachSwitches(
            locationTrackAndAlignment(
                trackNumberId = insertDraftTrackNumber(),
                segments = locationTrackSegments
            ),
            IntId<TrackLayoutSwitch>(1) to TargetSegmentStart(),
            IntId<TrackLayoutSwitch>(2) to TargetSegmentMiddle(locationTrackSegments.count() / 2),
            IntId<TrackLayoutSwitch>(3) to TargetSegmentEnd()
        )

        val switchIdsForKm1 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(1)),
            geocodingContext
        )
        assertTrue(switchIdsForKm1.isEmpty())

        val switchIdsForKm2 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(2)),
            geocodingContext
        )
        assertContains(switchIdsForKm2, IntId(1))
        assertTrue(switchIdsForKm2.count() == 1)

        val switchIdsForKm3 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(3)),
            geocodingContext
        )
        assertContains(switchIdsForKm3, IntId(2))
        assertTrue(switchIdsForKm3.count() == 1)

        val switchIdsForKm2And4 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(2), KmNumber(4)),
            geocodingContext
        )
        assertContains(switchIdsForKm2And4, IntId(1))
        assertContains(switchIdsForKm2And4, IntId(3))
        assertTrue(switchIdsForKm2And4.count() == 2)

        val switchIdsForKm5 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(5)),
            geocodingContext
        )
        assertTrue(switchIdsForKm5.isEmpty())

        val switchIdsForKm6 = locationTrackService.getSwitchIdsByAddressKilometers(
            alignment,
            setOf(KmNumber(6)),
            geocodingContext
        )
        assertTrue(switchIdsForKm6.isEmpty())

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

        val referencePoint = Point(497879.651, 7272112.808)
        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(referencePoint+Point(0.0, 0.0), referencePoint+Point(10.0, 0.0)),
            segment(
                referencePoint+Point(10.0, 0.0),
                referencePoint+Point(20.0, 0.0),
            ).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(referencePoint+Point(20.0, 0.0), referencePoint+Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(
                referencePoint+Point(10.2, 0.0),
                referencePoint+Point(10.2, 20.2),
            )
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

        val referencePoint = Point(498879.651, 7282112.808)
        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(referencePoint+Point(0.0, 0.0), referencePoint+Point(10.0, 0.0)),
            segment(
                referencePoint+Point(10.0, 0.0),
                referencePoint+Point(20.0, 0.0),
            ).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(referencePoint+Point(20.0, 0.0), referencePoint+Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(
                referencePoint+Point(20.2, -20.0),
                referencePoint+Point(20.2, 0.2),
            )
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

        val referencePoint = Point(499879.651, 7292112.808)
        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(referencePoint+Point(0.0, 0.0), referencePoint+Point(10.0, 0.0)),
            segment(
                referencePoint+Point(10.0, 0.0),
                referencePoint+Point(20.0, 0.0),
            ).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(referencePoint+Point(20.0, 0.0), referencePoint+Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(
                referencePoint+Point(10.0, -10.0),
                referencePoint+Point(10.0, 0.0),
                referencePoint+Point(10.0, 10.0),
            )
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

        val referencePoint = Point(500879.651, 7302112.808)
        val (_, _) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(referencePoint+Point(0.0, 0.0), referencePoint+Point(10.0, 0.0)),
            segment(
                referencePoint+Point(10.0, 0.0),
                referencePoint+Point(20.0, 0.0),
            ).copy(
                switchId = switchId,
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(2),
            ),
            segment(referencePoint+Point(20.0, 0.0), referencePoint+Point(30.0, 0.0)),
        ))

        val (track, alignment) = insertAndFetch(locationTrack(trackNumberId), alignment(
            segment(
                referencePoint+Point(12.0, 0.0),
                referencePoint+Point(22.0, 0.0),
            )
        ))
        assertEquals(null, track.topologyStartSwitch)
        assertEquals(null, track.topologyEndSwitch)
        val updatedTrack = locationTrackService.updateTopology(track, alignment)
        assertEquals(null, updatedTrack.topologyStartSwitch)
        assertEquals(null, updatedTrack.topologyEndSwitch)
    }

    private fun insertAndFetch(switch: TrackLayoutSwitch) = switchService.get(switchService.saveDraft(switch))

    private fun insertAndFetch(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): Pair<LocationTrack, LayoutAlignment> =
        locationTrackService.getWithAlignment(locationTrackService.saveDraft(locationTrack, alignment))

    private fun createPublishedLocationTrack(seed: Int): Pair<RowVersion<LocationTrack>, LocationTrack> {
        val trackNumberId = insertOfficialTrackNumber()
        val (insertedTrackVersion, _) = createAndVerifyTrack(trackNumberId, seed)
        return publishAndVerify(insertedTrackVersion.id)
    }

    private fun createAndVerifyTrack(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        seed: Int,
    ): Pair<RowVersion<LocationTrack>, LocationTrack> {
        val insertRequest = saveRequest(trackNumberId, seed)
        val insertedTrackVersion = locationTrackService.insert(insertRequest)
        val insertedTrack = locationTrackService.getDraft(insertedTrackVersion.id)
        assertMatches(insertRequest, insertedTrack)
        return insertedTrackVersion to insertedTrack
    }

    private fun publishAndVerify(
        locationTrackId: IntId<LocationTrack>,
    ): Pair<RowVersion<LocationTrack>, LocationTrack> {
        val (draft, draftAlignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)
        assertNotNull(draft.draft)

        val publishedVersion = locationTrackService.publish(draft.id as IntId)
        val (published, publishedAlignment) = locationTrackService.getWithAlignment(OFFICIAL, publishedVersion.id)
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
        val (draft, alignment) = locationTrackService.getWithAlignment(DRAFT, id)
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
}
