package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geometry.GeometryAlignmentLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryElementLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryKmPostLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.GeometrySwitchLinkStatus
import fi.fta.geoviite.infra.geometry.PlanLayoutService
import fi.fta.geoviite.infra.geometry.SwitchData
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.geometryLine
import fi.fta.geoviite.infra.geometry.geometrySwitch
import fi.fta.geoviite.infra.geometry.kmPosts
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.KmPostGkLocationSource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.NodeConnection
import fi.fta.geoviite.infra.tracklayout.PlaceHolderNodeConnection
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.segmentToAlignmentM
import fi.fta.geoviite.infra.tracklayout.someKmNumber
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
class LinkingServiceIT
@Autowired
constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
    private val geometryDao: GeometryDao,
    private val linkingService: LinkingService,
    private val locationTrackService: LocationTrackService,
    private val kmPostService: LayoutKmPostService,
    private val splitDao: SplitDao,
) : DBTestBase() {

    @Test
    fun alignmentGeometryLinkingWorks() {
        val geometryStart = Point(377680.0, 6676160.0)
        // 6m of geometry to replace
        val geometrySegmentChange = geometryStart + Point(3.0, 3.5)
        val geometryEnd = geometrySegmentChange + Point(3.0, 2.5)
        val plan =
            plan(
                trackNumber = testDBService.getUnusedTrackNumber(),
                srid = Srid(3067),
                alignments =
                    listOf(
                        geometryAlignment(
                            line(geometryStart, geometrySegmentChange),
                            line(geometrySegmentChange, geometryEnd),
                        )
                    ),
            )

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)
        val (geometryLayoutPlan, transformationError) = planLayoutService.getLayoutPlan(geometryPlanId.id)
        assertNull(transformationError)
        assertNotNull(geometryLayoutPlan)

        val geometryLayoutAlignment = geometryLayoutPlan?.alignments?.get(0)!!
        val geometryStartSegment = geometryLayoutAlignment.segmentsWithM.first()
        val geometryEndSegment = geometryLayoutAlignment.segmentsWithM.last()
        assertNotEquals(geometryStartSegment, geometryEndSegment)

        // Geometry is about 6m and we need 1m for transition on both sides
        // Build layout so that we keep first point, replacing the next 8: 4 from first, 4 from
        // second segment
        val start = geometryStart - Point(1.0, 1.5)
        val segment1 = segment(start, start + 1.0, start + 2.0, start + 3.0, start + 4.0)
        val segment2 = segment(start + 4.0, start + 5.0, start + 6.0, start + 7.0, start + 8.0, start + 9.0)
        val segment3 = segment(start + 9.0, start + 10.0, start + 11.0)

        val (locationTrack, geometry) =
            locationTrackAndGeometry(
                mainOfficialContext.createLayoutTrackNumber().id,
                segment1,
                segment2,
                segment3,
                draft = true,
            )
        val locationTrackVersion = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry)
        locationTrackService.publish(LayoutBranch.main, locationTrackVersion)
        val locationTrackId = locationTrackVersion.id

        val (officialTrack, officialGeometry) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.official, locationTrackId)
        assertMatches(
            officialTrack to officialGeometry,
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, locationTrackId),
        )

        // Pick the whole geometry as interval
        val geometryInterval =
            GeometryInterval(
                alignmentId = geometryLayoutAlignment.id as IntId,
                mRange = Range(geometryStartSegment.second.min, geometryEndSegment.second.max).map { it.distance },
            )

        // Pick layout interval to cut after first 2 point, skipping to 5th point of second interval
        val layoutInterval =
            LayoutInterval(
                alignmentId = locationTrackId,
                mRange =
                    Range(
                        officialGeometry.segmentMValues[0].min,
                        officialGeometry.segmentsWithM[1].let { (s, m) -> s.segmentPoints[4].m.segmentToAlignmentM(m.min) },
                    ).map { it.distance },
            )

        linkingService.saveLocationTrackLinking(
            LayoutBranch.main,
            LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval),
        )
        assertEquals(
            officialTrack to officialGeometry,
            locationTrackService.getWithGeometry(MainLayoutContext.official, locationTrackId),
        )

        val (_, draftAlignment) = locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, locationTrackId)
        assertNotEquals(officialGeometry, draftAlignment)
        // Should be split so that we have;
        // all geometry segments
        // 1 generated segment from geometry end to 2:nd segment middle
        // second segment partially (4 last points)
        // third segment fully
        assertEquals(geometryLayoutAlignment.segments.size + 3, draftAlignment.segments.size)
        assertEquals(6, draftAlignment.segments[0].segmentPoints.size)
        for (i in 0..geometryLayoutAlignment.segments.lastIndex) {
            assertEquals(
                geometryLayoutAlignment.segments[i].segmentPoints.size,
                draftAlignment.segments[i].segmentPoints.size,
            )
        }
        assertEquals(2, draftAlignment.segments[1 + geometryLayoutAlignment.segments.size].segmentPoints.size)
        assertEquals(3, draftAlignment.segments[2 + geometryLayoutAlignment.segments.size].segmentPoints.size)
    }

    @Test
    fun kmPostGeometryLinkingWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPostId = mainOfficialContext.save(kmPost(trackNumberId, someKmNumber())).id
        val officialKmPost = kmPostService.get(MainLayoutContext.official, kmPostId)

        assertMatches(
            officialKmPost!!,
            kmPostService.getOrThrow(MainLayoutContext.draft, kmPostId),
            contextMatch = false,
        )

        val trackNumber = testDBService.getUnusedTrackNumber()

        val plan = plan(trackNumber)
        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null).id

        val fetchedPlan = geometryService.getGeometryPlan(geometryPlanId)

        val geometryKmPostId = fetchedPlan.kmPosts[1].id as IntId
        val geometryKmPost = geometryDao.fetchKmPosts(kmPostId = geometryKmPostId)[0]

        val kmPostLinkingParameters = KmPostLinkingParameters(geometryPlanId, geometryKmPostId, kmPostId)

        linkingService.saveKmPostLinking(LayoutBranch.main, kmPostLinkingParameters)

        assertEquals(officialKmPost, kmPostService.get(MainLayoutContext.official, kmPostId))

        val draftKmPost = kmPostService.getOrThrow(MainLayoutContext.draft, kmPostId)

        assertNotEquals(officialKmPost, draftKmPost)

        assertEquals(geometryKmPostId, draftKmPost.sourceId)
        assertEquals(true, draftKmPost.gkLocation?.confirmed)
        assertEquals(KmPostGkLocationSource.FROM_GEOMETRY, draftKmPost.gkLocation?.source)
        assertEquals(geometryKmPost.location, draftKmPost.gkLocation?.location?.toPoint())
        assertEquals(fetchedPlan.units.coordinateSystemSrid, draftKmPost.gkLocation?.location?.srid)
    }

    @Test
    fun `Linking alignments throws if alignment is deleted`() {
        val plan = plan(testDBService.getUnusedTrackNumber(), LAYOUT_SRID)

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)

        val (locationTrack, geometry) =
            locationTrackAndGeometry(
                trackNumberId = mainOfficialContext.createLayoutTrackNumber().id,
                state = LocationTrackState.DELETED,
                draft = true,
            )
        val locationTrackVersion = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry)
        val locationTrackId = locationTrackVersion.id
        locationTrackService.publish(LayoutBranch.main, locationTrackVersion)

        val geometryInterval = GeometryInterval(alignmentId = IntId(0), mRange = Range(0.0, 0.0))

        val layoutInterval = LayoutInterval(alignmentId = locationTrackId, mRange = Range(0.0, 0.0))

        val ex =
            assertThrows<LinkingFailureException> {
                linkingService.saveLocationTrackLinking(
                    LayoutBranch.main,
                    LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval),
                )
            }
        assertEquals("Linking failed: Cannot link a location track that is deleted", ex.message)
    }

    @Test
    fun `Linking alignments throws if alignment has splits`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val plan = plan(trackNumber, LAYOUT_SRID)

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)

        val (locationTrack, geometry) =
            locationTrackAndGeometry(mainOfficialContext.createLayoutTrackNumber().id, draft = true)
        val locationTrackResponse =
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry).let { rowVersion ->
                locationTrackService.publish(LayoutBranch.main, rowVersion).published
            }

        val geometryInterval = GeometryInterval(alignmentId = IntId(0), mRange = Range(0.0, 0.0))

        val layoutInterval = LayoutInterval(alignmentId = locationTrackResponse.id, mRange = Range(0.0, 0.0))

        splitDao.saveSplit(
            locationTrackResponse,
            listOf(SplitTarget(locationTrackResponse.id, 0..1, SplitTargetOperation.CREATE)),
            relinkedSwitches = emptyList(),
            updatedDuplicates = emptyList(),
        )

        val ex =
            assertThrows<LinkingFailureException> {
                linkingService.saveLocationTrackLinking(
                    LayoutBranch.main,
                    LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval),
                )
            }
        assertEquals("Linking failed: Cannot link a location track that has unfinished splits", ex.message)
    }

    @Test
    fun `Linking alignments works if all alignment splits are finished`() {
        val trackNumber = testDBService.getUnusedTrackNumber()

        val geometryStart = Point(377680.0, 6676160.0)
        // 6m of geometry to replace
        val geometrySegmentChange = geometryStart + Point(3.0, 3.5)
        val geometryEnd = geometrySegmentChange + Point(3.0, 2.5)
        val plan =
            plan(
                trackNumber,
                LAYOUT_SRID,
                geometryAlignment(line(geometryStart, geometrySegmentChange), line(geometrySegmentChange, geometryEnd)),
            )

        val geometryPlanId = geometryDao.insertPlan(plan, testFile(), null)
        val (geometryLayoutPlan, _) = planLayoutService.getLayoutPlan(geometryPlanId.id)

        val geometryLayoutAlignment = geometryLayoutPlan?.alignments?.get(0)!!
        val geometryStartSegment = geometryLayoutAlignment.segmentsWithM.first()
        val geometryEndSegment = geometryLayoutAlignment.segmentsWithM.last()

        val start = geometryStart - Point(1.0, 1.5)
        val segment1 = segment(start, start + 1.0, start + 2.0, start + 3.0, start + 4.0)

        val (locationTrack, geometry) =
            locationTrackAndGeometry(mainOfficialContext.createLayoutTrackNumber().id, segment1, draft = true)
        val locationTrackResponse =
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry).let { rowVersion ->
                locationTrackService.publish(LayoutBranch.main, rowVersion).published
            }

        val (_, officialAlignment) = locationTrackService.getWithGeometry(locationTrackResponse)

        val geometryInterval =
            GeometryInterval(
                alignmentId = geometryLayoutAlignment.id as IntId,
                mRange = Range(geometryStartSegment.second.min, geometryEndSegment.second.max).map { it.distance },
            )

        val layoutInterval =
            LayoutInterval(
                alignmentId = locationTrackResponse.id,
                mRange =
                    Range(
                        officialAlignment.segments[0].segmentPoints.first().m,
                        officialAlignment.segments[0].segmentPoints[4].m,
                    ).map { it.distance },
            )

        val split =
            splitDao.getOrThrow(
                splitDao.saveSplit(
                    locationTrackResponse,
                    listOf(SplitTarget(locationTrackResponse.id, 0..1, SplitTargetOperation.CREATE)),
                    relinkedSwitches = emptyList(),
                    updatedDuplicates = emptyList(),
                )
            )
        splitDao.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)

        assertDoesNotThrow {
            linkingService.saveLocationTrackLinking(
                LayoutBranch.main,
                LinkingParameters(geometryPlanId.id, geometryInterval, layoutInterval),
            )
        }
    }

    @Test
    fun `Location track linking updates topology links correctly`() {
        val (trackNumber, trackNumberId) = mainOfficialContext.createTrackNumberAndId()

        val connectionPoint = Point(377921.0, 6676129.0)

        // Create a switch and a linked track: these are not edited but the topology should connect here
        val connectionJoint = switchJoint(1, connectionPoint)
        val switchId = mainOfficialContext.createSwitch(joints = listOf(connectionJoint)).id
        val switchLink = SwitchLink(switchId, connectionJoint.role, connectionJoint.number)
        mainOfficialContext.save(
            locationTrack(trackNumberId),
            trackGeometry(
                edge(
                    listOf(segment(connectionPoint + Point(-10.0, 0.0), connectionJoint.location)),
                    endInnerSwitch = switchLink,
                )
            ),
        )

        // Create some geometry to link into the track
        val plan =
            plan(
                trackNumber = trackNumber,
                srid = Srid(3067),
                alignments = listOf(geometryAlignment(line(connectionPoint, connectionPoint + Point(100.0, 0.0)))),
            )
        val planVersion = geometryDao.insertPlan(plan, testFile(), null)
        val alignmentId = geometryDao.fetchPlan(planVersion).alignments[0].id as IntId

        // Create an empty base track to start editing
        val initVersion = mainDraftContext.save(locationTrack(trackNumberId), TmpLocationTrackGeometry.empty)
        val id = initVersion.id
        val trackStartPort = TrackBoundary(id, TrackBoundaryType.START)
        locationTrackService.getWithGeometry(initVersion).second.let { geometry ->
            assertNull(geometry.startNode?.innerPort)
            assertNull(geometry.startNode?.outerPort)
        }

        // Link it so that it touches the switch
        val endM = 50.0
        val connectedVersion =
            linkingService.saveLocationTrackLinking(
                LayoutBranch.main,
                EmptyAlignmentLinkingParameters(
                    geometryPlanId = planVersion.id,
                    layoutAlignmentId = id,
                    geometryInterval = GeometryInterval(alignmentId, Range(0.0, endM)),
                ),
            )
        locationTrackService.getWithGeometry(connectedVersion).second.let { geometry ->
            assertNull(geometry.startNode?.innerPort)
            assertEquals(switchLink, geometry.startNode?.outerPort)
        }

        // Shorten from the start, so that it separates again
        val separationM = 20.0
        val separatedVersion =
            linkingService.shortenLocationTrackGeometry(LayoutBranch.main, id, Range(separationM, endM))
        locationTrackService.getWithGeometry(separatedVersion).second.let { geometry ->
            assertEquals(trackStartPort, geometry.startNode?.innerPort)
            assertNull(geometry.startNode?.outerPort)
        }

        // Relink it so that it touches the switch again
        val reconnectedVersion =
            linkingService.saveLocationTrackLinking(
                LayoutBranch.main,
                LinkingParameters(
                    geometryPlanId = planVersion.id,
                    // Link more stuff at the beginning
                    layoutInterval = LayoutInterval(id, Range(0.0, 0.0)),
                    // About what was cut off, with a small connection segment
                    geometryInterval = GeometryInterval(alignmentId, Range(0.0, separationM - 1.0)),
                ),
            )
        locationTrackService.getWithGeometry(reconnectedVersion).second.let { geometry ->
            assertNull(geometry.startNode?.innerPort)
            assertEquals(switchLink, geometry.startNode?.outerPort)
        }
    }

    @Test
    fun `getPlanLinkStatuses() returns plan link statuses across multiple plans`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        fun makeSomePlan(): GeometryPlan {
            val switch = geometrySwitch()
            val line = geometryLine(elementSwitchData = SwitchData(switchId = switch.id, null, null))
            return plan(
                trackNumber,
                alignments = listOf(geometryAlignment(line)),
                switches = listOf(switch),
                kmPosts = kmPosts(Srid(3879)).take(1),
            )
        }
        val linkingLocationTrack = geometryDao.fetchPlan(geometryDao.insertPlan(makeSomePlan(), testFile(), null))
        val linkingReferenceLine = geometryDao.fetchPlan(geometryDao.insertPlan(makeSomePlan(), testFile(), null))
        val linkingSwitchAndLocationTrack =
            geometryDao.fetchPlan(geometryDao.insertPlan(makeSomePlan(), testFile(), null))
        val linkingKmPost = geometryDao.fetchPlan(geometryDao.insertPlan(makeSomePlan(), testFile(), null))
        val trackNumberId = mainOfficialContext.save(trackNumber(trackNumber)).id
        val linkedLocationTrack =
            mainDraftContext
                .saveLocationTrack(
                    locationTrackAndGeometry(
                        trackNumberId,
                        segment(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            sourceId = linkingLocationTrack.alignments[0].elements[0].id,
                        ),
                    )
                )
                .id
        val linkedReferenceLine =
            mainDraftContext
                .saveReferenceLine(
                    referenceLineAndAlignment(
                        trackNumberId,
                        segment(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            sourceId = linkingReferenceLine.alignments[0].elements[0].id,
                        ),
                    )
                )
                .id
        val linkedSwitch = mainDraftContext.save(switch(stateCategory = LayoutStateCategory.EXISTING)).id
        val linkedLocationTrackAlongSwitch =
            mainDraftContext
                .save(
                    locationTrack(trackNumberId),
                    trackGeometry(
                        TmpLayoutEdge(
                            startNode = NodeConnection.switch(inner = switchLinkYV(linkedSwitch, 1), outer = null),
                            endNode = PlaceHolderNodeConnection,
                            segments =
                                listOf(
                                    segment(
                                        points = toSegmentPoints(Point(0.0, 0.0), Point(10.0, 0.0)),
                                        source = GeometrySource.PLAN,
                                        sourceId = linkingSwitchAndLocationTrack.alignments[0].elements[0].id,
                                        resolution = 1,
                                    )
                                ),
                        )
                    ),
                )
                .id
        val linkedKmPost =
            mainDraftContext
                .save(kmPost(trackNumberId, KmNumber("123"), sourceId = linkingKmPost.kmPosts[0].id as IntId))
                .id
        val allPlanIds =
            listOf(linkingLocationTrack, linkingReferenceLine, linkingSwitchAndLocationTrack, linkingKmPost).map {
                it.id as IntId
            }

        assertEquals(
            listOf(
                expectPlanLinkedObjects(linkingLocationTrack),
                expectPlanLinkedObjects(linkingReferenceLine),
                expectPlanLinkedObjects(linkingSwitchAndLocationTrack),
                expectPlanLinkedObjects(linkingKmPost),
            ),
            linkingService.getGeometryPlanLinkStatuses(mainOfficialContext.context, allPlanIds),
        )
        assertEquals(
            listOf(
                expectPlanLinkedObjects(linkingLocationTrack, linkedLocationTrack to null),
                expectPlanLinkedObjects(linkingReferenceLine, null to linkedReferenceLine),
                expectPlanLinkedObjects(
                    linkingSwitchAndLocationTrack,
                    linkedLocationTrackAlongSwitch to null,
                    firstSwitchIsLinked = true,
                ),
                expectPlanLinkedObjects(linkingKmPost, kmPostLinkedToFirst = linkedKmPost),
            ),
            linkingService.getGeometryPlanLinkStatuses(mainDraftContext.context, allPlanIds),
        )
    }

    fun expectPlanLinkedObjects(
        plan: GeometryPlan,
        firstAlignmentFirstElementLink: Pair<IntId<LocationTrack>?, IntId<ReferenceLine>?> = null to null,
        firstSwitchIsLinked: Boolean = false,
        kmPostLinkedToFirst: IntId<LayoutKmPost>? = null,
    ) =
        GeometryPlanLinkStatus(
            plan.id as IntId,
            listOf(
                GeometryAlignmentLinkStatus(
                    plan.alignments[0].id as IntId,
                    listOf(
                        GeometryElementLinkStatus(
                            plan.alignments[0].elements[0].id as IndexedId<GeometryElement>,
                            firstAlignmentFirstElementLink.first != null ||
                                firstAlignmentFirstElementLink.second != null,
                            listOfNotNull(firstAlignmentFirstElementLink.first),
                            listOfNotNull(firstAlignmentFirstElementLink.second),
                        )
                    ),
                )
            ),
            listOf(GeometrySwitchLinkStatus(plan.switches[0].id as IntId, firstSwitchIsLinked)),
            listOf(GeometryKmPostLinkStatus(plan.kmPosts[0].id as IntId, listOfNotNull(kmPostLinkedToFirst))),
        )

    fun assertMatches(
        trackAndAlignment1: Pair<LocationTrack, LocationTrackGeometry>,
        trackAndAlignment2: Pair<LocationTrack, LocationTrackGeometry>,
    ) {
        assertMatches(trackAndAlignment1.first, trackAndAlignment2.first, contextMatch = false)
        assertMatches(trackAndAlignment1.second, trackAndAlignment2.second)
    }
}
