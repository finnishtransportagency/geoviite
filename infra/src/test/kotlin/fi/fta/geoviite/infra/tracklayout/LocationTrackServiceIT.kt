package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.SplitSourceLocationTrackUpdateException
import fi.fta.geoviite.infra.geography.contains
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.MultiPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.util.FreeText
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
    private val layoutTrackNumberService: LayoutTrackNumberService,
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

        val tracksAndGeometries = locationTrackService.listNearWithGeometries(MainLayoutContext.draft, boundingBox)

        assertTrue(tracksAndGeometries.any { (t, _) -> t.id == alignmentIdInBbox })
        assertTrue(tracksAndGeometries.none { (t, _) -> t.id == alignmentIdOutsideBbox })
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
                published.copy(nameStructure = trackNameStructure("EDITED1")),
                TmpLocationTrackGeometry.empty,
            )
        assertEquals(publicationResponse.id, editedVersion.id)
        assertNotEquals(publicationResponse.rowId, editedVersion.rowId)

        val editedDraft = getAndVerifyDraft(publicationResponse.id)
        assertEquals(AlignmentName("EDITED1"), editedDraft.name)

        val editedVersion2 =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                editedDraft.copy(nameStructure = trackNameStructure("EDITED2")),
                TmpLocationTrackGeometry.empty,
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
    fun `getWithGeometries returns results in request order, even with duplicates`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val one = createAndVerifyTrack(trackNumber, 1)
        val two = createAndVerifyTrack(trackNumber, 2)
        val three = createAndVerifyTrack(trackNumber, 3)

        assertEquals(
            listOf(one.id, two.id, two.id, three.id),
            locationTrackService
                .getManyWithGeometries(mainDraftContext.context, listOf(one.id, two.id, two.id, three.id))
                .map { it.first.id },
        )
        assertEquals(
            listOf(three.id, one.id, two.id, three.id, two.id),
            locationTrackService
                .getManyWithGeometries(mainDraftContext.context, listOf(three.id, one.id, two.id, three.id, two.id))
                .map { it.first.id },
        )
    }

    @Test
    fun `Topology recalculate works`() {
        val switch1Id =
            mainDraftContext
                .createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(10.0, 0.0)),
                            switchJoint(5, Point(20.0, 0.0)),
                            switchJoint(2, Point(30.0, 0.0)),
                        )
                )
                .id
        val switch2Id =
            mainDraftContext
                .createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(30.0, 0.0)),
                            switchJoint(5, Point(40.0, 0.0)),
                            switchJoint(2, Point(50.0, 0.0)),
                        )
                )
                .id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        // Track1 has the switch1 connected: |-----1-5-2|
        val track1 =
            locationTrack(trackNumberId, id = IntId(1)) to
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switch1Id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 1),
                        endInnerSwitch = switchLinkYV(switch1Id, 5),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 5),
                        endInnerSwitch = switchLinkYV(switch1Id, 2),
                        segments = listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                    ),
                )

        // Track 2 has no switch but ends at track1 location for joint 1 -> should get topologically connected at end
        val track2 =
            locationTrack(trackNumberId, id = IntId(2)) to
                trackGeometry(edge(listOf(segment(Point(10.0, 10.0), Point(10.0, 0.0)))))

        // Track 3 starts where track1 ends and has a switch of its own: |2-5-1------| -> the nodes should get combined
        val track3 =
            locationTrack(trackNumberId, id = IntId(3)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 2),
                        endInnerSwitch = switchLinkYV(switch2Id, 5),
                        segments = listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 5),
                        endInnerSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(40.0, 0.0), Point(50.0, 0.0))),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(50.0, 0.0), Point(100.0, 0.0))),
                    ),
                )

        val changedTracks =
            locationTrackService.recalculateTopology(MainLayoutContext.draft, listOf(track1, track2, track3), switch1Id)

        val newTrack1 = changedTracks.first { it.first.id == track1.first.id }
        assertEquals(
            listOf(
                // Own inner links remain
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 5),
                    alignmentPoint(20.0, 0.0, m = 20.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 2),
                    alignmentPoint(30.0, 0.0, m = 30.0),
                    TrackSwitchLinkType.INNER,
                ),
                // Topology link added to the end
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 2),
                    alignmentPoint(30.0, 0.0, m = 30.0),
                    TrackSwitchLinkType.OUTER,
                ),
            ),
            newTrack1.second.trackSwitchLinks,
        )

        val newTrack2 = changedTracks.first { it.first.id == track2.first.id }
        assertEquals(
            listOf(
                // No own links, add topology link to the end
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.OUTER,
                )
            ),
            newTrack2.second.trackSwitchLinks,
        )

        val newTrack3 = changedTracks.first { it.first.id == track3.first.id }
        assertEquals(
            listOf(
                // Added topology link at start
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 2),
                    alignmentPoint(30.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.OUTER,
                ),
                // Own inner links remain
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 2),
                    alignmentPoint(30.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 5),
                    alignmentPoint(40.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 1),
                    alignmentPoint(50.0, 0.0, m = 20.0),
                    TrackSwitchLinkType.INNER,
                ),
            ),
            newTrack3.second.trackSwitchLinks,
        )
    }

    @Test
    fun `Topology recalculation does not connect an unrelated track to a combination node`() {
        val switch1Id =
            mainDraftContext
                .createSwitch(joints = listOf(switchJoint(1, Point(10.0, 0.0)), switchJoint(2, Point(20.0, 0.0))))
                .id
        val switch2Id =
            mainDraftContext
                .createSwitch(joints = listOf(switchJoint(2, Point(0.0, 0.0)), switchJoint(1, Point(10.0, 0.0))))
                .id

        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val track1 =
            locationTrack(trackNumberId, id = IntId(1)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 1),
                        endInnerSwitch = switchLinkYV(switch1Id, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    )
                )
        // Track 2 is not connected to anything but starts at the same location
        val track2 =
            locationTrack(trackNumberId, id = IntId(2)) to
                trackGeometry(edge(segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 10.0)))))
        val track3 =
            locationTrack(trackNumberId, id = IntId(3)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 2),
                        endInnerSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    )
                )

        val changedTracks =
            locationTrackService.recalculateTopology(MainLayoutContext.draft, listOf(track1, track2, track3), switch1Id)

        val newTrack1 = changedTracks.first { it.first.id == track1.first.id }
        assertEquals(
            listOf(
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 1),
                    alignmentPoint(10.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.OUTER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 1),
                    alignmentPoint(10.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 2),
                    alignmentPoint(20.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
            ),
            newTrack1.second.trackSwitchLinks,
        )

        // Track 2 should not get changed, since we can't know which side of a combination to connect to
        val newTrack2 = changedTracks.first { it.first.id == track2.first.id }
        assertEquals(track2.first, newTrack2.first)
        assertEquals(track2.second.withLocationTrackId(IntId(2)), newTrack2.second)

        val newTrack3 = changedTracks.first { it.first.id == track3.first.id }
        assertEquals(
            listOf(
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 2),
                    alignmentPoint(0.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch2Id, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(switch1Id, 1),
                    alignmentPoint(10.0, 0.0, m = 10.0),
                    TrackSwitchLinkType.OUTER,
                ),
            ),
            newTrack3.second.trackSwitchLinks,
        )
    }

    @Test
    fun `Topology recalculation does not generate unnecessary changes`() {
        val switchId =
            mainDraftContext
                .createSwitch(joints = listOf(switchJoint(1, Point(10.0, 0.0)), switchJoint(2, Point(20.0, 0.0))))
                .id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val track1 =
            locationTrack(trackNumberId, id = IntId(1)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    ),
                    trackId = IntId(1),
                )
        val track2 =
            locationTrack(trackNumberId, id = IntId(2)) to
                trackGeometry(edge(listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0)))), trackId = IntId(2))
        val changedTracks =
            locationTrackService.recalculateTopology(MainLayoutContext.draft, listOf(track1, track2), switchId)

        // Both tracks are in the result set
        assertEquals(2, changedTracks.size)

        // Track1 should not have changed
        val (updatedTrack1, updatedGeometry1) = changedTracks.first { it.first.id == track1.first.id }
        assertEquals(track1.first, updatedTrack1)
        assertEquals(track1.second, updatedGeometry1)

        // Track 2 got connected -> not equal
        val (updatedTrack2, updatedGeometry2) = changedTracks.first { it.first.id == track2.first.id }
        assertEquals(track2.first, updatedTrack2)
        assertNotEquals(track2.second, updatedGeometry2)
    }

    @Test
    fun `Topology recalculation connects a three-way track combination to the same combination node`() {
        val switch1Id =
            mainDraftContext
                .createSwitch(
                    joints =
                        listOf(
                            switchJoint(1, Point(10.0, 0.0)),
                            switchJoint(2, Point(20.0, 0.0)),
                            switchJoint(3, Point(20.0, 10.0)),
                        )
                )
                .id
        val switch2Id =
            mainDraftContext
                .createSwitch(joints = listOf(switchJoint(2, Point(0.0, 0.0)), switchJoint(1, Point(10.0, 0.0))))
                .id

        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val track1 =
            locationTrack(trackNumberId, id = IntId(1)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 1),
                        endInnerSwitch = switchLinkYV(switch1Id, 2),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    )
                )
        val track2 =
            locationTrack(trackNumberId, id = IntId(2)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 1),
                        endInnerSwitch = switchLinkYV(switch1Id, 3),
                        segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 10.0))),
                    )
                )
        val track3 =
            locationTrack(trackNumberId, id = IntId(3)) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 2),
                        endInnerSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    )
                )

        val changedTracks =
            locationTrackService.recalculateTopology(MainLayoutContext.draft, listOf(track1, track2, track3), switch1Id)

        val newTrack1 = changedTracks.first { it.first.id == track1.first.id }
        val newTrack2 = changedTracks.first { it.first.id == track2.first.id }
        val newTrack3 = changedTracks.first { it.first.id == track3.first.id }
        assertEquals(true, newTrack1.second.startNode?.node?.containsJoint(switch1Id, JointNumber(1)))
        assertEquals(true, newTrack1.second.startNode?.node?.containsJoint(switch2Id, JointNumber(1)))
        assertEquals(newTrack1.second.startNode?.node, newTrack2.second.startNode?.node)
        assertEquals(newTrack1.second.startNode?.node, newTrack3.second.endNode?.node)
    }

    @Test
    fun `Topology recalculation combines DB-only tracks by location fetch`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(10.0, 0.0)
        val point3 = Point(20.0, 0.0)

        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val switch1Id = mainDraftContext.createSwitch(joints = listOf(switchJoint(1, point2))).id
        val track1SavedOfficial =
            mainOfficialContext
                .save(
                    locationTrack(trackNumberId),
                    trackGeometry(edge(listOf(segment(point1, point2)), endInnerSwitch = switchLinkYV(switch1Id, 1))),
                )
                .id

        val track2SavedDraft =
            mainDraftContext.save(locationTrack(trackNumberId), trackGeometry(edge(listOf(segment(point2, point3))))).id

        val changedTracks =
            locationTrackService.recalculateTopology(
                MainLayoutContext.draft,
                listOf(),
                listOf(MultiPoint(Point(10.0, 0.0))),
            )

        // Nothing to do on the first track & not pre-changed -> no unneeded change is generated
        assertNull(changedTracks.find { it.first.id == track1SavedOfficial })

        // The second track should get connected to the first one
        val newTrack2 = changedTracks.first { it.first.id == track2SavedDraft }
        assertEquals(switchLinkYV(switch1Id, 1), newTrack2.second.outerStartSwitch)
    }

    @Test
    fun `Topology recalculation overrides DB tracks with changed ones`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(10.0, 0.0)
        val point3 = Point(20.0, 0.0)
        val point4 = Point(30.0, 0.0)

        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val switch1Id =
            mainDraftContext.createSwitch(joints = listOf(switchJoint(1, point2), switchJoint(2, point3))).id

        val track1SavedOfficial =
            mainOfficialContext
                .save(
                    locationTrack(trackNumberId),
                    trackGeometry(edge(listOf(segment(point1, point2)), endInnerSwitch = switchLinkYV(switch1Id, 1))),
                )
                .id

        val track2SavedDraft =
            mainDraftContext.save(locationTrack(trackNumberId), trackGeometry(edge(listOf(segment(point2, point3))))).id

        val track1UnsavedChange =
            locationTrack(trackNumberId, id = track1SavedOfficial) to
                trackGeometry(edge(listOf(segment(point3, point4)), startInnerSwitch = switchLinkYV(switch1Id, 2)))

        val changedTracks =
            locationTrackService.recalculateTopology(MainLayoutContext.draft, listOf(track1UnsavedChange), switch1Id)

        // Track1 should come out as given on the changed-list as there's no new topology changes
        val newTrack1 = changedTracks.first { it.first.id == track1SavedOfficial }
        assertEquals(track1UnsavedChange.second.trackSwitchLinks, newTrack1.second.trackSwitchLinks)

        // The second track should get connected to the unsaved version of the first one from the end
        val newTrack2 = changedTracks.first { it.first.id == track2SavedDraft }
        assertNull(newTrack2.second.outerStartSwitch)
        assertEquals(switchLinkYV(switch1Id, 2), newTrack2.second.outerEndSwitch)
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
                            NodeConnection.switch(
                                inner = switchLinkYV(innerStartSwitchId, 2),
                                outer = switchLinkYV(outerStartSwitchId, 3),
                            ),
                        endNode = NodeConnection.switch(inner = switchLinkYV(innerMidSwitchId, 1), outer = null),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    ),
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = null, outer = switchLinkYV(innerMidSwitchId, 1)),
                        endNode =
                            NodeConnection.switch(
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
        val originalLocationTrackId = originalLocationTrack.id

        val (duplicateInOfficial, _) =
            insertAndFetchDraft(
                locationTrack(trackNumberId, duplicateOf = originalLocationTrackId, draft = true),
                geometry,
            )
        publish(duplicateInOfficial.id as IntId<LocationTrack>)

        val newTrackName = trackNameStructure(duplicateInOfficial.name.toString() + " NEW NAME FOR DRAFT")
        val duplicateInDraftVersion =
            locationTrackService.update(
                LayoutBranch.main,
                duplicateInOfficial.id,
                saveRequest(trackNumberId, 1)
                    .copy(
                        namingScheme = newTrackName.scheme,
                        nameFreeText = newTrackName.freeText,
                        nameSpecifier = newTrackName.specifier,
                        duplicateOf = originalLocationTrackId,
                    ),
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
        val referenceLineSegment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val referenceLineGeometry = alignmentDao.insert(alignment(referenceLineSegment))
        referenceLineDao.save(referenceLine(trackNumberId, alignmentVersion = referenceLineGeometry, draft = false))

        val middleSwitch =
            insertAndFetchDraft(
                    switch(
                        joints =
                            listOf(
                                LayoutSwitchJoint(
                                    JointNumber(1),
                                    SwitchJointRole.MAIN,
                                    Point(50.0, 0.0),
                                    LocationAccuracy.DIGITIZED_AERIAL_IMAGE,
                                )
                            ),
                        draft = true,
                    )
                )
                .id as IntId
        
        val mainLineLocationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        segments = listOf(segment(Point(0.0, 0.0), Point(50.0, 0.0))),
                        endOuterSwitch = switchLinkYV(middleSwitch, 1),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(middleSwitch, 1),
                        segments = listOf(segment(Point(50.0, 0.0), Point(100.0, 0.0))),
                    ),
                ),
            )

        val duplicateLocationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(middleSwitch, 1),
                        segments = listOf(segment(Point(50.0, 0.0), Point(100.0, 0.0))),
                    )
                ),
            )

        val splittingParams =
            locationTrackService.getSplittingInitializationParameters(MainLayoutContext.draft, mainLineLocationTrack.id)
        assertNotNull(splittingParams)
        assertEquals(mainLineLocationTrack.id, splittingParams?.id)
        assertEquals(1, splittingParams?.switches?.size)
        assertEquals(1, splittingParams?.duplicates?.size)
        assertContains(splittingParams?.switches?.map { it.switchId } ?: emptyList(), middleSwitch)
        assertContains(splittingParams?.duplicates?.map { it.id } ?: emptyList(), duplicateLocationTrack.id)
        assertEquals(locationTrackM(50.0), splittingParams?.switches?.first()?.distance ?: LineM(0.0), 0.01)
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
                    nameFreeText = LocationTrackNameFreeTextPart("Some other name"),
                    namingScheme = LocationTrackNamingScheme.FREE_TEXT,
                    nameSpecifier = null,
                    descriptionBase = sourceLocationTrack.descriptionStructure.base,
                    descriptionSuffix = sourceLocationTrack.descriptionStructure.suffix,
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

    @Test
    fun `LocationTrack polygon is simplified to reduce point count`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(2000.0, 0.0))))
                .id
        val trackSegment = segment(Point(0.0, 0.0), Point(1000.0, 0.0))
        assertTrue(trackSegment.segmentPoints.size > 900)
        val (track, _) =
            mainOfficialContext.save(locationTrack(trackNumberId, draft = false), trackGeometryOfSegments(trackSegment))
        val polygon = locationTrackService.getTrackPolygon(MainLayoutContext.official, track.id, null, null, 10.0)!!
        assertTrue(polygon.points.size < 50)
    }

    @Test
    fun `LocationTrack polygon is resolved correctly without cropping`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0))))
                .id
        val (track, _) =
            mainOfficialContext.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometryOfSegments(segment(Point(32.0, 0.0), Point(50.0, 0.0))),
            )

        val polygon = locationTrackService.getTrackPolygon(MainLayoutContext.official, track.id, null, null, 10.0)!!

        // Should be inside the buffered (+10m) polygon
        assertContains(
            polygon,
            // Start and end points
            Point(32.0, 0.0),
            Point(50.0, 0.0),
            // Diagonally beyond end points but inside the buffer
            Point(27.0, -5.0),
            Point(27.0, 5.0),
            Point(55.0, -5.0),
            Point(55.0, 5.0),
            // To the sides but inside the buffer
            Point(40.0, -5.0),
            Point(40.0, 5.0),
        )

        // Should not be inside the buffered polygon
        assertDoesntContain(
            polygon,
            Point(20.0, 0.0),
            Point(61.0, 0.0),
            Point(23.0, -8.0),
            Point(23.0, 8.0),
            Point(58.0, -8.0),
            Point(58.0, 8.0),
            Point(40.0, -11.0),
            Point(40.0, 11.0),
        )
    }

    @Test
    fun `LocationTrack polygon is resolved correctly with cropping`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(4000.0, 0.0))))
                .id
        val (track, _) =
            mainOfficialContext.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(4000.0, 0.0))),
            )

        mainOfficialContext.saveAndFetch(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(1), gkLocation = kmPostGkLocation(0.0, 0.0))
        )
        val kmPost2 =
            mainOfficialContext.saveAndFetch(
                kmPost(trackNumberId = trackNumberId, km = KmNumber(2), gkLocation = kmPostGkLocation(1000.0, 0.0))
            )
        val kmPost3 =
            mainOfficialContext.saveAndFetch(
                kmPost(trackNumberId = trackNumberId, km = KmNumber(3), gkLocation = kmPostGkLocation(2000.0, 0.0))
            )
        mainOfficialContext.saveAndFetch(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(4), gkLocation = kmPostGkLocation(3000.0, 0.0))
        )

        val polygon =
            locationTrackService.getTrackPolygon(
                MainLayoutContext.official,
                track.id,
                kmPost2.kmNumber,
                kmPost3.kmNumber,
                10.0,
            )!!

        // Should be inside the buffered (+10m) polygon: the cropped area includes km 1 & 2 -> x=[1000..3000]
        assertContains(
            polygon,
            // The cropping km locations
            Point(1000.0, 0.0),
            Point(3000.0, 0.0),
            // Something in the middle and to the sides within buffer
            Point(1100.0, 8.0),
            Point(2900.0, -8.0),
            // Still inside buffer
            Point(995.0, -5.0),
            Point(995.0, 5.0),
            Point(3005.0, -5.0),
            Point(3005.0, 5.0),
        )

        // Should not be inside the buffered polygon
        assertDoesntContain(polygon, Point(980.0, 0.0), Point(3020.0, 0.0), Point(2000.0, 20.0), Point(2000.0, -20.0))
    }

    @Test
    fun `overlapping plan search cropping works correctly in different edge cases`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(2000.0, 0.0), Point(5000.0, 0.0))))
                .id
        val (track, _) =
            mainOfficialContext.save(
                locationTrack(trackNumberId),
                trackGeometryOfSegments(segment(Point(3200.0, 0.0), Point(3800.0, 0.0))),
            )

        mainOfficialContext.save(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(2), gkLocation = kmPostGkLocation(2000.0, 0.0))
        )
        mainOfficialContext.save(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(3), gkLocation = kmPostGkLocation(3000.0, 0.0))
        )
        mainOfficialContext.save(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(4), gkLocation = kmPostGkLocation(4000.0, 0.0))
        )

        fun getPolygon(startKm: KmNumber?, endKm: KmNumber?) =
            locationTrackService.getTrackPolygon(MainLayoutContext.official, track.id, startKm, endKm, 1.0)

        // Full polygon without cropping
        val fullTrackPolygon =
            getPolygon(null, null)!!.also { polygon ->
                // Include whole track
                assertContains(polygon, Point(3200.0, 0.0), Point(3800.0, 0.0))
                // Don't include outside track
                assertDoesntContain(polygon, Point(3190.0, 0.0), Point(3810.0, 0.0))
            }

        // Various crops that cover the whole LocationTrack and hence should result in the same polygon
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(0), KmNumber(4)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(0), KmNumber(6)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(0), null))
        assertEquals(fullTrackPolygon, getPolygon(null, KmNumber(6)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(2), null)!!)
        assertEquals(fullTrackPolygon, getPolygon(null, KmNumber(3)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(3), null))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(3), KmNumber(3)))

        // Crops entirely outside TrackNumber should result in null polygon
        assertNull(getPolygon(KmNumber(0), KmNumber(0)))
        assertNull(getPolygon(KmNumber(6), KmNumber(6)))
        assertNull(getPolygon(null, KmNumber(0)))
        assertNull(getPolygon(KmNumber(6), null))

        // Crops within TrackNumber, but not within LocationTrack should result in null polygon
        assertNull(getPolygon(KmNumber(2), KmNumber(2)))
        assertNull(getPolygon(KmNumber(4), KmNumber(4)))
        assertNull(getPolygon(null, KmNumber(2)))
        assertNull(getPolygon(KmNumber(4), null))
    }

    @Test
    fun `name and description autogeneration works`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = mainOfficialContext.save(trackNumber(trackNumber)).id
        mainOfficialContext.save(referenceLine(trackNumberId), someAlignment()).id
        val switch1Id = mainOfficialContext.save(switch(name = "ABC V0001", draft = false)).id
        val switch2Id = mainOfficialContext.save(switch(name = "ABC V0002", draft = false)).id
        val switch3Id = mainOfficialContext.save(switch(name = "ABC V0003", draft = false)).id

        val unlinkedGeometry = trackGeometryOfSegments(someSegment())
        val linkedGeometry =
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(switch1Id, 1),
                    endOuterSwitch = switchLinkYV(switch2Id, 1),
                    segments = listOf(someSegment()),
                )
            )

        val trackOfficialVersion =
            mainOfficialContext.save(
                locationTrack(
                    trackNumberId = trackNumberId,
                    name = "TST-TRACK-INIT",
                    description = "Initial description",
                ),
                unlinkedGeometry,
            )
        val trackId = trackOfficialVersion.id
        assertEquals(AlignmentName("TST-TRACK-INIT"), getDraftNameAndStructure(trackId).first)
        assertEquals(FreeText("Initial description"), getDraftDescriptionAndStructure(trackId).first)

        // Linking the track to switches should not change the name or description since we're still set as free-text
        updateDraft(trackId) { t, g -> t to linkedGeometry }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.FREE_TEXT, structure.scheme)
            assertEquals(AlignmentName("TST-TRACK-INIT"), name)
        }
        assertEquals(FreeText("Initial description"), getDraftDescriptionAndStructure(trackId).first)

        // Operating point name is basically just a different free text
        updateDraft(trackId) { t, g ->
            t.copy(
                nameStructure =
                    LocationTrackNameWithinOperatingPoint(LocationTrackNameFreeTextPart("within operating point"))
            ) to g
        }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.WITHIN_OPERATING_POINT, structure.scheme)
            assertEquals(AlignmentName("within operating point"), name)
        }

        // Track number name gets the name from track number + specifier
        updateDraft(trackId) { t, g ->
            t.copy(
                nameStructure =
                    LocationTrackNameByTrackNumber(
                        LocationTrackNameFreeTextPart("tn-track"),
                        LocationTrackNameSpecifier.LANHR,
                    )
            ) to g
        }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.TRACK_NUMBER_TRACK, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.LANHR, structure.specifier)
            assertEquals(AlignmentName("$trackNumber ${LocationTrackNameSpecifier.LANHR.properForm} tn-track"), name)
        }

        // Changing the track number should update the name
        val newTrackNumber = testDBService.getUnusedTrackNumber()
        layoutTrackNumberService.saveDraft(
            LayoutBranch.main,
            mainDraftContext.fetch(trackNumberId)!!.copy(number = newTrackNumber),
        )
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.TRACK_NUMBER_TRACK, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.LANHR, structure.specifier)
            assertEquals(AlignmentName("$newTrackNumber ${LocationTrackNameSpecifier.LANHR.properForm} tn-track"), name)
        }
        // Make sure that track number draft revert also reverts the track name
        layoutTrackNumberService.deleteDraft(LayoutBranch.main, trackNumberId)
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.TRACK_NUMBER_TRACK, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.LANHR, structure.specifier)
            assertEquals(AlignmentName("$trackNumber ${LocationTrackNameSpecifier.LANHR.properForm} tn-track"), name)
        }

        // Between operating points the name combines specifier and switches
        // Since switches can also affect the description, we also check that
        updateDraft(trackId) { t, g ->
            t.copy(
                nameStructure = LocationTrackNameBetweenOperatingPoints(LocationTrackNameSpecifier.EKR),
                descriptionStructure =
                    LocationTrackDescriptionStructure(
                        LocationTrackDescriptionBase("New description"),
                        LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH,
                    ),
            ) to g
        }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.EKR, structure.specifier)
            assertEquals(AlignmentName("${LocationTrackNameSpecifier.EKR.properForm} ABC V001-ABC V002"), name)
        }
        getDraftDescriptionAndStructure(trackId).let { (description, structure) ->
            assertEquals(LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH, structure.suffix)
            assertEquals(FreeText("New description V001 - V002"), description)
        }

        // Changing the switch linkings should update the name and description
        updateDraft(trackId) { t, g ->
            t to
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(switch1Id, 1),
                        endOuterSwitch = switchLinkYV(switch3Id, 1),
                        segments = listOf(someSegment()),
                    )
                )
        }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.EKR, structure.specifier)
            assertEquals(AlignmentName("${LocationTrackNameSpecifier.EKR.properForm} ABC V001-ABC V003"), name)
        }
        getDraftDescriptionAndStructure(trackId).let { (description, structure) ->
            assertEquals(LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH, structure.suffix)
            assertEquals(FreeText("New description V001 - V003"), description)
        }

        // Changing the switch names should update the name and description
        switchService.saveDraft(
            LayoutBranch.main,
            mainDraftContext.fetch(switch1Id)!!.copy(name = SwitchName("ABC V0999")),
        )
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.EKR, structure.specifier)
            assertEquals(AlignmentName("${LocationTrackNameSpecifier.EKR.properForm} ABC V999-ABC V003"), name)
        }
        getDraftDescriptionAndStructure(trackId).let { (description, structure) ->
            assertEquals(LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH, structure.suffix)
            assertEquals(FreeText("New description V999 - V003"), description)
        }
        // Make sure that switch draft revert also reverts the track name
        switchService.deleteDraft(LayoutBranch.main, switch1Id)
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS, structure.scheme)
            assertEquals(LocationTrackNameSpecifier.EKR, structure.specifier)
            assertEquals(AlignmentName("${LocationTrackNameSpecifier.EKR.properForm} ABC V001-ABC V003"), name)
        }
        getDraftDescriptionAndStructure(trackId).let { (description, structure) ->
            assertEquals(LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH, structure.suffix)
            assertEquals(FreeText("New description V001 - V003"), description)
        }

        // Finally, verify that chord-style naming also works
        updateDraft(trackId) { t, g -> t.copy(nameStructure = LocationTrackNameChord) to g }
        getDraftNameAndStructure(trackId).let { (name, structure) ->
            assertEquals(LocationTrackNamingScheme.CHORD, structure.scheme)
            assertEquals(AlignmentName("ABC V001-V003"), name)
        }
    }

    @Test
    fun `idMatches finds tracks even if ids or oids need trimming`() {
        val track1 = createPublishedLocationTrack(1)
        val track2 = createPublishedLocationTrack(2)
        val track2oid = externalIdForLocationTrack()
        locationTrackService.insertExternalId(LayoutBranch.main, track2.track.id as IntId, track2oid)

        val intIdTerm = FreeText(" ${track1.track.id} ")
        val intIdMatchFunction = locationTrackService.idMatches(MainLayoutContext.official, intIdTerm, null)

        val oidTerm = FreeText(" $track2oid ")
        val oidMatchFunction = locationTrackService.idMatches(MainLayoutContext.official, oidTerm, null)

        assertTrue(intIdMatchFunction(intIdTerm.toString(), track1.track))
        assertTrue(oidMatchFunction(oidTerm.toString(), track2.track))
    }

    private fun updateDraft(
        id: IntId<LocationTrack>,
        op: (LocationTrack, LocationTrackGeometry) -> Pair<LocationTrack, LocationTrackGeometry>,
    ) =
        locationTrackService.getWithGeometry(MainLayoutContext.draft, id)!!.let { (t, g) ->
            op(t, g).let { (newT, newG) -> locationTrackService.saveDraft(LayoutBranch.main, newT, newG) }
        }

    private fun getDraftNameAndStructure(trackId: IntId<LocationTrack>) =
        locationTrackDao.get(MainLayoutContext.draft, trackId).let { track ->
            assertNotNull(track, "Draft track with ID $trackId should exist")
            track!!.name to track.nameStructure
        }

    private fun getDraftDescriptionAndStructure(trackId: IntId<LocationTrack>) =
        locationTrackDao.get(MainLayoutContext.draft, trackId).let { track ->
            assertNotNull(track, "Draft track with ID $trackId should exist")
            track!!.description to track.descriptionStructure
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
    ) {
        val id: IntId<LocationTrack>
            get() = version.id
    }

    private fun createAndVerifyTrack(trackNumberId: IntId<LayoutTrackNumber>, seed: Int): VerifiedTrack {
        val insertRequest = saveRequest(trackNumberId, seed)
        val insertResponse = locationTrackService.insert(LayoutBranch.main, insertRequest)
        val (insertedTrack, insertedGeometry) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, insertResponse.id)
        assertMatches(insertRequest, insertedTrack)
        assertMatches(TmpLocationTrackGeometry.empty, insertedGeometry)
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
        assertEquals(draft.getVersionOrThrow(), geometry.trackRowVersion)
        return draft to geometry
    }

    private fun assertMatches(saveRequest: LocationTrackSaveRequest, locationTrack: LocationTrack) {
        assertEquals(saveRequest.trackNumberId, locationTrack.trackNumberId)
        assertEquals(saveRequest.namingScheme, locationTrack.nameStructure.scheme)
        assertEquals(saveRequest.nameFreeText, locationTrack.nameStructure.freeText)
        assertEquals(saveRequest.nameSpecifier, locationTrack.nameStructure.specifier)
        assertEquals(saveRequest.descriptionBase, locationTrack.descriptionStructure.base)
        assertEquals(saveRequest.descriptionSuffix, locationTrack.descriptionStructure.suffix)
        assertEquals(saveRequest.state, locationTrack.state)
        assertEquals(saveRequest.type, locationTrack.type)
        assertEquals(saveRequest.topologicalConnectivity, locationTrack.topologicalConnectivity)
        assertEquals(saveRequest.ownerId, locationTrack.ownerId)
    }

    private fun saveRequest(trackNumberId: IntId<LayoutTrackNumber>, seed: Int) =
        LocationTrackSaveRequest(
            namingScheme = LocationTrackNamingScheme.FREE_TEXT,
            nameFreeText = LocationTrackNameFreeTextPart("TST-TRACK$seed"),
            nameSpecifier = null,
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

fun assertContains(polygon: Polygon, vararg points: Point) =
    points.forEach { p ->
        assertTrue(contains(polygon, p, LAYOUT_SRID), "Expected polygon to overlap location: point=$p polygon=$polygon")
    }

fun assertDoesntContain(polygon: Polygon, vararg points: Point) =
    points.forEach { p ->
        assertFalse(
            contains(polygon, p, LAYOUT_SRID),
            "Expected polygon to overlap location: point=$p polygon=$polygon",
        )
    }
