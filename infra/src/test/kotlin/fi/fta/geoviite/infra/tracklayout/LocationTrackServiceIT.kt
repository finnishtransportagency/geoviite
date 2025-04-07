package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.SplitSourceLocationTrackUpdateException
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTestDataService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackServiceIT
@Autowired
constructor(
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchService: LayoutSwitchService,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
    private val splitService: SplitService,
    private val splitTestDataService: SplitTestDataService,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        splitTestDataService.clearSplits()
        testDBService.clearLayoutTables()
    }

    @Test
    fun `Creating and deleting unpublished track with geometry works`() {
        val (track, geometry) =
            locationTrackAndGeometry(mainDraftContext.createLayoutTrackNumber().id, someSegment(), draft = true)
        val version = locationTrackService.saveDraft(LayoutBranch.main, track, geometry)
        val id = version.id
        val savedGeometry = alignmentDao.fetch(version)
        assertMatches(geometry, savedGeometry)
        assertFalse(savedGeometry.isEmpty)
        val deletedVersion = locationTrackService.deleteDraft(LayoutBranch.main, id)
        assertEquals(version, deletedVersion)
    }

    @Test
    fun deletingOfficialLocationTrackThrowsException() {
        val (track, geometry) =
            locationTrackAndGeometry(mainOfficialContext.createLayoutTrackNumber().id, someSegment(), draft = true)
        val version = locationTrackService.saveDraft(LayoutBranch.main, track, geometry)
        publish(version.id)
        assertThrows<DeletingFailureException> { locationTrackService.deleteDraft(LayoutBranch.main, version.id) }
    }

    @Test
    fun nearbyLocationTracksAreFoundWithBbox() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val (trackInside, alignmentInside) =
            locationTrackAndGeometry(
                trackNumberId,
                segment(Point(x = 0.0, y = 0.0), Point(x = 5.0, y = 0.0)),
                draft = true,
            )
        val (trackOutside, alignmentOutside) =
            locationTrackAndGeometry(
                trackNumberId,
                segment(Point(x = 20.0, y = 20.0), Point(x = 30.0, y = 20.0)),
                draft = true,
            )

        val alignmentIdInBbox = locationTrackService.saveDraft(LayoutBranch.main, trackInside, alignmentInside).id
        val alignmentIdOutsideBbox =
            locationTrackService.saveDraft(LayoutBranch.main, trackOutside, alignmentOutside).id

        val boundingBox = BoundingBox(Point(0.0, 0.0), Point(10.0, 10.0))

        val tracksAndAlignments = locationTrackService.listNearWithGeometries(MainLayoutContext.draft, boundingBox)

        assertTrue(tracksAndAlignments.any { (t, _) -> t.id == alignmentIdInBbox })
        assertTrue(tracksAndAlignments.none { (t, _) -> t.id == alignmentIdOutsideBbox })
    }

    @Test
    fun locationTrackInsertAndUpdateWorks() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val (insertedTrackVersion, _, insertedGeometry) = createAndVerifyTrack(trackNumberId, 1)
        val id = insertedTrackVersion.id
        val changeTimeAfterInsert = locationTrackService.getChangeTime()

        val updateRequest =
            saveRequest(trackNumberId, 2).copy(topologicalConnectivity = TopologicalConnectivityType.NONE)
        val updatedTrackVersion = locationTrackService.update(LayoutBranch.main, id, updateRequest)
        assertEquals(id, updatedTrackVersion.id)
        assertEquals(insertedTrackVersion.rowId, updatedTrackVersion.rowId)
        assertNotEquals(insertedTrackVersion.version, updatedTrackVersion.version)
        val (updatedTrack, updatedGeometry) = locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, id)
        assertMatches(updateRequest, updatedTrack)
        assertMatches(insertedGeometry, updatedGeometry)
        val changeTimeAfterUpdate = locationTrackService.getChangeTime()

        val changeInfo = locationTrackService.getLayoutAssetChangeInfo(MainLayoutContext.draft, id)
        assertEquals(changeTimeAfterInsert, changeInfo?.created)
        assertEquals(changeTimeAfterUpdate, changeInfo?.changed)
    }

    @Test
    fun `Saving creates draft`() {
        val (publicationResponse, published) = createPublishedLocationTrack(1)

        val editedVersion =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                published.copy(name = AlignmentName("EDITED1")),
                LocationTrackGeometry.empty,
            )
        assertEquals(publicationResponse.id, editedVersion.id)
        assertNotEquals(publicationResponse.rowId, editedVersion.rowId)

        val editedDraft = getAndVerifyDraft(publicationResponse.id)
        assertEquals(AlignmentName("EDITED1"), editedDraft.name)

        val editedVersion2 =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                editedDraft.copy(name = AlignmentName("EDITED2")),
                LocationTrackGeometry.empty,
            )
        assertEquals(publicationResponse.id, editedVersion2.id)
        assertNotEquals(publicationResponse.rowId, editedVersion2.rowId)

        val editedDraft2 = getAndVerifyDraft(publicationResponse.id)
        assertEquals(AlignmentName("EDITED2"), editedDraft2.name)
    }

    @Test
    fun `Saving with geometry creates draft`() {
        val (publicationResponse, published) = createPublishedLocationTrack(2)

        val geometryTmp = trackGeometryOfSegments(segment(2, 10.0, 20.0, 10.0, 20.0))
        val editedVersion = locationTrackService.saveDraft(LayoutBranch.main, published, geometryTmp)
        assertEquals(publicationResponse.id, editedVersion.id)
        assertNotEquals(publicationResponse.rowId, editedVersion.rowId)

        val (editedDraft, editedGeometry) = getAndVerifyDraftWithGeometry(publicationResponse.id)
        assertMatches(geometryTmp, editedGeometry)

        val geometryTmp2 = trackGeometryOfSegments(segment(4, 10.0, 20.0, 10.0, 20.0))
        val editedVersion2 = locationTrackService.saveDraft(LayoutBranch.main, editedDraft, geometryTmp2)
        assertEquals(publicationResponse.id, editedVersion2.id)
        assertNotEquals(publicationResponse.rowId, editedVersion2.rowId)

        val (editedDraft2, editedGeometry2) = getAndVerifyDraftWithGeometry(publicationResponse.id)
        assertMatches(geometryTmp2, editedGeometry2)
    }

    @Test
    fun updatingExternalIdWorks() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val insertRequest = saveRequest(trackNumberId, 2)
        val id = locationTrackService.insert(LayoutBranch.main, insertRequest).id
        val locationTrack = locationTrackService.get(MainLayoutContext.draft, id)!!

        assertNull(locationTrackDao.fetchExternalId(LayoutBranch.main, id))

        locationTrackService.insertExternalId(LayoutBranch.main, id, externalIdForLocationTrack())

        assertNotNull(locationTrackDao.fetchExternalId(LayoutBranch.main, id))
    }

    @Test
    fun returnsNullIfFetchingDraftOnlyLocationTrackUsingOfficialFetch() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val draft = createAndVerifyTrack(trackNumber, 35)

        assertNull(locationTrackService.get(MainLayoutContext.official, draft.version.id))
    }

    @Test
    fun throwsIfFetchingOfficialVersionOfDraftOnlyLocationTrackUsingGetOrThrow() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val draft = createAndVerifyTrack(trackNumber, 35)

        assertThrows<NoSuchEntityException> {
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.version.id)
        }
    }

    @Test
    fun `Topology recalculate works`() {
        val switch1 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(10.0, 0.0)),
                            switchJoint(5, Point(20.0, 0.0)),
                            switchJoint(2, Point(30.0, 0.0)),
                        )
                )
            )
        val switch2 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(30.0, 0.0)),
                            switchJoint(5, Point(40.0, 0.0)),
                            switchJoint(2, Point(50.0, 0.0)),
                        )
                )
            )

        // Track1 has the switch1 connected: |-----1-5-2|
        val track1 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switch1.id as IntId, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch1.id as IntId, 1),
                        endInnerSwitch = switchLinkYV(switch1.id as IntId, 5),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch1.id as IntId, 5),
                        endInnerSwitch = switchLinkYV(switch1.id as IntId, 2),
                        segments = listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                    ),
                ),
            )

        // Track 2 has no switch but ends at track1 location for joint 1 -> should get topologically connected at end
        val track2 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(edge(listOf(segment(Point(10.0, 10.0), Point(10.0, 0.0))))),
            )

        // Track 3 starts where track1 ends and has a switch of its own: |2-5-1------| -> the nodes should get combined
        val track3 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2.id as IntId, 2),
                        endInnerSwitch = switchLinkYV(switch2.id as IntId, 5),
                        segments = listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch2.id as IntId, 5),
                        endInnerSwitch = switchLinkYV(switch2.id as IntId, 1),
                        segments = listOf(segment(Point(40.0, 0.0), Point(50.0, 0.0))),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(switch2.id as IntId, 1),
                        segments = listOf(segment(Point(50.0, 0.0), Point(100.0, 0.0))),
                    ),
                ),
            )

        val changedTrackVersions = locationTrackService.recalculateTopology(switch1, MainLayoutContext.draft)

        val newTrack1 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track1.id })
        assertEquals(
            listOf(
                // Own inner links remain
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 5),
                    alignmentPoint(20.0, 0.0, m = 20.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 2),
                    alignmentPoint(30.0, 0.0, m = 30.0),
                    TrackSwitchLinkType.INNER,
                ),
                // Topology link added to the end
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 2),
                    alignmentPoint(30.0, 0.0, m = 30.0),
                    TrackSwitchLinkType.OUTER,
                ),
            ),
            newTrack1.second.trackSwitchLinks,
        )

        val newTrack2 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track2.id })
        assertEquals(
            listOf(
                // No own links, add topology link to the end
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.OUTER,
                )
            ),
            newTrack2.second.trackSwitchLinks,
        )

        val newTrack3 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track3.id })
        assertEquals(
            listOf(
                // Added topology link at start
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 2),
                    alignmentPoint(30.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.OUTER,
                ),
                // Own inner links remain
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 2),
                    alignmentPoint(30.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 5),
                    alignmentPoint(40.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 1),
                    alignmentPoint(50.0, 0.0, m = 20.0),
                    TrackSwitchLinkType.INNER,
                ),
            ),
            newTrack3.second.trackSwitchLinks,
        )
    }

    @Test
    fun `Topology recalculation does not connect an unrelated track to a combination node`() {
        val switch1 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints = listOf(switchJoint(1, Point(10.0, 0.0)), switchJoint(2, Point(20.0, 0.0)))
                )
            )
        val switch2 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints = listOf(switchJoint(2, Point(0.0, 0.0)), switchJoint(1, Point(10.0, 0.0)))
                )
            )

        val track1 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1.id as IntId, 1),
                        endInnerSwitch = switchLinkYV(switch1.id as IntId, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    )
                ),
            )
        // Track 2 is not connected to anything but starts at the same location
        val track2 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(edge(segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 10.0))))),
            )
        val track3 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2.id as IntId, 2),
                        endInnerSwitch = switchLinkYV(switch2.id as IntId, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    )
                ),
            )

        val changedTrackVersions = locationTrackService.recalculateTopology(switch1, MainLayoutContext.draft)

        val newTrack1 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track1.id })
        assertEquals(
            listOf(
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.OUTER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 2),
                    alignmentPoint(20.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
            ),
            newTrack1.second.trackSwitchLinks,
        )

        // Track 2 should not get changed
        assertNull(changedTrackVersions.find { it.id == track2.id })

        val newTrack3 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track3.id })
        assertEquals(
            listOf(
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 2),
                    alignmentPoint(0.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1.id as IntId, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.OUTER,
                ),
            ),
            newTrack3.second.trackSwitchLinks,
        )
    }

    @Test
    fun `Topology recalculation does not generate unnecessary changes`() {
        val switch =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints = listOf(switchJoint(1, Point(10.0, 0.0)), switchJoint(2, Point(20.0, 0.0)))
                )
            )
        val track1 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id as IntId, 1),
                        endInnerSwitch = switchLinkYV(switch.id as IntId, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    )
                ),
            )
        val track2 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(edge(listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))))),
            )

        val changedTrackVersions = locationTrackService.recalculateTopology(switch, MainLayoutContext.draft)
        // There's no need to change track1 -> only track2 should be updated
        assertEquals(listOf(track2.next()), changedTrackVersions)
    }

    @Test
    fun `Topology recalculation connects a three-way track combination to the same combination node`() {
        val switch1 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(10.0, 0.0)),
                            switchJoint(2, Point(20.0, 0.0)),
                            switchJoint(3, Point(20.0, 10.0)),
                        )
                )
            )
        val switch2 =
            testDBService.fetch(
                mainDraftContext.createSwitch(
                    joints = listOf(switchJoint(2, Point(0.0, 0.0)), switchJoint(1, Point(10.0, 0.0)))
                )
            )

        val track1 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1.id as IntId, 1),
                        endInnerSwitch = switchLinkYV(switch1.id as IntId, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    )
                ),
            )
        val track2 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1.id as IntId, 1),
                        endInnerSwitch = switchLinkYV(switch1.id as IntId, 3),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 10.0))),
                    )
                ),
            )
        val track3 =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2.id as IntId, 2),
                        endInnerSwitch = switchLinkYV(switch2.id as IntId, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    )
                ),
            )

        val changedTrackVersions = locationTrackService.recalculateTopology(switch1, MainLayoutContext.draft)

        val newTrack1 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track1.id })
        val newTrack2 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track2.id })
        val newTrack3 = testDBService.fetchWithGeometry(changedTrackVersions.first { it.id == track3.id })
        assertEquals(true, newTrack1.second.startNode?.node?.containsJoint(switch1.id as IntId, JointNumber(1)))
        assertEquals(true, newTrack1.second.startNode?.node?.containsJoint(switch2.id as IntId, JointNumber(1)))
        assertEquals(newTrack1.second.startNode?.node, newTrack2.second.startNode?.node)
        assertEquals(newTrack1.second.startNode?.node, newTrack3.second.endNode?.node)
    }

    @Test
    fun `getLocationTrackSwitches finds all connected switches`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val outerStartSwitchId = insertAndFetchDraft(switch(draft = true)).id as IntId
        val innerStartSwitchId = insertAndFetchDraft(switch(draft = true)).id as IntId
        val outerEndSwitchId = insertAndFetchDraft(switch(draft = true)).id as IntId
        val innerEndSwitchId = insertAndFetchDraft(switch(draft = true)).id as IntId
        val innerMidSwitchId = insertAndFetchDraft(switch(draft = true)).id as IntId

        val (track, _) =
            insertAndFetchDraft(
                locationTrack(trackNumberId = trackNumberId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode =
                            EdgeNode.switch(
                                inner = switchLinkYV(innerStartSwitchId, 2),
                                outer = switchLinkYV(outerStartSwitchId, 3),
                            ),
                        endNode = EdgeNode.switch(inner = switchLinkYV(innerMidSwitchId, 1), outer = null),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    ),
                    TmpLayoutEdge(
                        startNode = EdgeNode.switch(inner = null, outer = switchLinkYV(innerMidSwitchId, 1)),
                        endNode =
                            EdgeNode.switch(
                                inner = switchLinkYV(innerEndSwitchId, 1),
                                outer = switchLinkYV(outerEndSwitchId, 5),
                            ),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    ),
                ),
            )

        assertEquals(
            listOf(outerStartSwitchId, innerStartSwitchId, innerMidSwitchId, innerEndSwitchId, outerEndSwitchId),
            locationTrackService.getSwitchesForLocationTrack(MainLayoutContext.draft, track.id as IntId),
        )
    }

    @Test
    fun fetchDuplicatesIsVersioned() {
        val geometry = someTrackGeometry()
        val trackNumberId = mainOfficialContext.createLayoutTrackNumberAndReferenceLine(someAlignment()).id

        val (originalLocationTrack, _) = insertAndFetchDraft(locationTrack(trackNumberId, draft = true), geometry)
        publish(originalLocationTrack.id as IntId)
        val originalLocationTrackId = originalLocationTrack.id as IntId

        val (duplicateInOfficial, _) =
            insertAndFetchDraft(
                locationTrack(trackNumberId, duplicateOf = originalLocationTrackId, draft = true),
                geometry,
            )
        publish(duplicateInOfficial.id as IntId<LocationTrack>)

        val newTrackName = AlignmentName(duplicateInOfficial.name.toString() + " NEW NAME FOR DRAFT")
        val duplicateInDraftVersion =
            locationTrackService.update(
                LayoutBranch.main,
                duplicateInOfficial.id as IntId,
                saveRequest(trackNumberId, 1).copy(name = newTrackName, duplicateOf = originalLocationTrackId),
            )
        val duplicateInDraft = locationTrackService.get(MainLayoutContext.draft, duplicateInDraftVersion.id)!!

        assertEquals(
            duplicateInOfficial.name,
            locationTrackService
                .getInfoboxExtras(MainLayoutContext.official, originalLocationTrackId)
                ?.duplicates
                ?.firstOrNull()
                ?.name,
        )
        assertEquals(
            duplicateInDraft.name,
            locationTrackService
                .getInfoboxExtras(MainLayoutContext.draft, originalLocationTrackId)
                ?.duplicates
                ?.firstOrNull()
                ?.name,
        )
    }

    @Test
    fun `Splitting initialization parameters are fetched properly`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val mainLineSegment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val rlGeometry = alignmentDao.insert(alignment(mainLineSegment))
        referenceLineDao.save(referenceLine(trackNumberId, alignmentVersion = rlGeometry, draft = false))

        val switch =
            insertAndFetchDraft(
                    switch(
                        joints =
                            listOf(
                                LayoutSwitchJoint(
                                    JointNumber(1),
                                    SwitchJointRole.MAIN,
                                    Point(100.0, 0.0),
                                    LocationAccuracy.DIGITIZED_AERIAL_IMAGE,
                                )
                            ),
                        draft = true,
                    )
                )
                .id as IntId
        val locationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch, 1),
                        segments = listOf(segment(Point(50.0, 0.0), Point(100.0, 0.0))),
                    )
                ),
            )
        val duplicateLocationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, duplicateOf = locationTrack.id, draft = false),
                trackGeometryOfSegments(mainLineSegment),
            )

        val splittingParams =
            locationTrackService.getSplittingInitializationParameters(MainLayoutContext.draft, locationTrack.id)
        assertNotNull(splittingParams)
        assertEquals(locationTrack.id, splittingParams?.id)
        assertEquals(1, splittingParams?.switches?.size)
        assertEquals(1, splittingParams?.duplicates?.size)
        assertContains(splittingParams?.switches?.map { it.switchId } ?: emptyList(), switch)
        assertContains(splittingParams?.duplicates?.map { it.id } ?: emptyList(), duplicateLocationTrack.id)
        assertEquals(50.0, splittingParams?.switches?.first()?.distance ?: 0.0, 0.01)
    }

    @Test
    fun `Trying to update a split source location track should throw`() {
        val split = splitTestDataService.insertSplit().let(splitService::getOrThrow)

        val sourceLocationTrack = locationTrackService.getOrThrow(MainLayoutContext.draft, split.sourceLocationTrackId)

        assertThrows<SplitSourceLocationTrackUpdateException> {
            locationTrackService.update(
                LayoutBranch.main,
                split.sourceLocationTrackId,
                LocationTrackSaveRequest(
                    name = AlignmentName("Some other name"),
                    descriptionBase = sourceLocationTrack.descriptionBase,
                    descriptionSuffix = sourceLocationTrack.descriptionSuffix,
                    type = sourceLocationTrack.type,
                    state = sourceLocationTrack.state,
                    trackNumberId = sourceLocationTrack.trackNumberId,
                    duplicateOf = sourceLocationTrack.duplicateOf,
                    topologicalConnectivity = sourceLocationTrack.topologicalConnectivity,
                    ownerId = sourceLocationTrack.ownerId,
                ),
            )
        }
    }

    @Test
    fun `getFullDescriptions() works in happy case`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = mainDraftContext.save(switch(name = "ABC V123"))
        val switch2 = mainDraftContext.save(switch(name = "QUX V456"))
        val track1 =
            mainDraftContext
                .save(
                    locationTrack(
                        trackNumberId,
                        description = "track 1",
                        descriptionSuffix = LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH,
                    ),
                    trackGeometry(
                        TmpLayoutEdge(
                            startNode = EdgeNode.switch(inner = null, outer = switchLinkYV(switch1.id, 1)),
                            endNode = EdgeNode.switch(inner = switchLinkYV(switch2.id, 1), outer = null),
                            segments = listOf(segment(Point(1.0, 1.0), Point(2.0, 2.0))),
                        )
                    ),
                )
                .id
        val track2 =
            mainDraftContext
                .save(
                    locationTrack(
                        trackNumberId,
                        description = "track 2",
                        descriptionSuffix = LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER,
                    ),
                    trackGeometry(
                        TmpLayoutEdge(
                            startNode = EdgeNode.switch(inner = switchLinkYV(switch2.id, 1), outer = null),
                            endNode = PlaceHolderEdgeNode,
                            segments = listOf(segment(Point(2.0, 2.0), Point(3.0, 3.0))),
                        )
                    ),
                )
                .id
        val descriptions =
            locationTrackService
                .getFullDescriptions(
                    MainLayoutContext.draft,
                    listOf(track1, track2).map { mainDraftContext.fetch(it)!! },
                    LocalizationLanguage.FI,
                )
                .map { it.toString() }
        assertEquals(listOf("track 1 V123 - V456", "track 2 V456 - Puskin"), descriptions)
    }

    @Test
    fun `deleteDraft deletes duplicate references if track is only draft, but not if official exists`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val onlyDraftReal = mainDraftContext.save(locationTrack(trackNumber), geometry).id
        val onlyDraftDuplicate =
            mainDraftContext.save(locationTrack(trackNumber, duplicateOf = onlyDraftReal), geometry).id
        val officialReal = mainOfficialContext.save(locationTrack(trackNumber), geometry)
        mainDraftContext.copyFrom(officialReal)
        val officialDuplicate =
            mainDraftContext.save(locationTrack(trackNumber, duplicateOf = officialReal.id), geometry).id

        locationTrackService.deleteDraft(LayoutBranch.main, onlyDraftReal)
        locationTrackService.deleteDraft(LayoutBranch.main, officialReal.id)

        assertNull(mainDraftContext.fetch(onlyDraftDuplicate)!!.duplicateOf)
        assertEquals(officialReal.id, mainDraftContext.fetch(officialDuplicate)!!.duplicateOf)
    }

    private fun insertAndFetchDraft(switch: LayoutSwitch): LayoutSwitch =
        switchDao.fetch(switchService.saveDraft(LayoutBranch.main, switch))

    private fun insertAndFetchDraft(
        locationTrack: LocationTrack,
        geometry: LocationTrackGeometry,
    ): Pair<LocationTrack, LocationTrackGeometry> =
        locationTrackService.getWithGeometry(locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry))

    private fun createPublishedLocationTrack(seed: Int): VerifiedTrack {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (trackInsertResponse, _) = createAndVerifyTrack(trackNumberId, seed)
        return publishAndVerify(trackInsertResponse.id)
    }

    private data class VerifiedTrack(
        val version: LayoutRowVersion<LocationTrack>,
        val track: LocationTrack,
        val geometry: LocationTrackGeometry,
    )

    private fun createAndVerifyTrack(trackNumberId: IntId<LayoutTrackNumber>, seed: Int): VerifiedTrack {
        val insertRequest = saveRequest(trackNumberId, seed)
        val insertResponse = locationTrackService.insert(LayoutBranch.main, insertRequest)
        val (insertedTrack, insertedGeometry) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, insertResponse.id)
        assertMatches(insertRequest, insertedTrack)
        assertMatches(LocationTrackGeometry.empty, insertedGeometry)
        return VerifiedTrack(insertResponse, insertedTrack, insertedGeometry)
    }

    private fun publishAndVerify(locationTrackId: IntId<LocationTrack>): VerifiedTrack {
        val (draft, draftGeometry) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, locationTrackId)
        assertTrue(draft.isDraft)

        val publicationResponse = publish(draft.id as IntId)
        val (published, publishedGeometry) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.official, publicationResponse.id)
        assertFalse(published.isDraft)
        assertEquals(draft.id, published.id)
        assertEquals(published.id, publicationResponse.id)
        assertMatches(draftGeometry, publishedGeometry)
        assertEquals(draftGeometry.edges, publishedGeometry.edges)

        return VerifiedTrack(publicationResponse, published, publishedGeometry)
    }

    private fun getAndVerifyDraft(id: IntId<LocationTrack>): LocationTrack {
        val draft = locationTrackService.get(MainLayoutContext.draft, id)!!
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        return draft
    }

    private fun getAndVerifyDraftWithGeometry(id: IntId<LocationTrack>): Pair<LocationTrack, LocationTrackGeometry> {
        val (draft, geometry) = locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, id)
        assertEquals(id, draft.id)
        assertTrue(draft.isDraft)
        assertEquals(draft.versionOrThrow, geometry.trackRowVersion)
        return draft to geometry
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

    private fun saveRequest(trackNumberId: IntId<LayoutTrackNumber>, seed: Int) =
        LocationTrackSaveRequest(
            name = AlignmentName("TST-TRACK$seed"),
            descriptionBase = LocationTrackDescriptionBase("Description - $seed"),
            descriptionSuffix = LocationTrackDescriptionSuffix.NONE,
            type = getSomeValue(seed),
            state = getSomeValue(seed),
            trackNumberId = trackNumberId,
            duplicateOf = null,
            topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
            ownerId = IntId(1),
        )

    private fun publish(id: IntId<LocationTrack>): LayoutRowVersion<LocationTrack> =
        locationTrackDao.fetchCandidateVersions(MainLayoutContext.draft, listOf(id)).first().let { version ->
            locationTrackService.publish(LayoutBranch.main, version).published
        }
}
