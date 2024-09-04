package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.OVERWRITE
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.TRANSFER
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutDaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class SplitServiceIT
@Autowired
constructor(
    val splitService: SplitService,
    val splitDao: SplitDao,
    val switchDao: LayoutSwitchDao,
    val switchStructureDao: SwitchStructureDao,
    val locationTrackService: LocationTrackService,
    val splitTestDataService: SplitTestDataService,
) : DBTestBase() {
    // The run order of the tests in this test suite matters if the database is not cleaned before
    // each test.
    // This gave false positive results for tests.
    //
    // This cleanup code can be removed after GVT-2484 has been implemented.
    @BeforeEach
    fun clear() {
        testDBService.clearPublicationTables()
        testDBService.clearLayoutTables()
    }

    @Test
    fun `location track split should create new tracks based on request`() {
        // Test data:
        // Segements  ---1--2----
        // Main       |---------|
        // Splits     |--|--|---|

        val preSegments = splitTestDataService.createSegments(Point(0.0, 0.0), 3)

        // Segments that are a part of the first switch + the branching track for switch re-linking
        // to work
        val (switch1, switchSegments1, turningSegments1) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(preSegments))

        splitTestDataService.insertAsTrack(turningSegments1)

        val segments1To2 = splitTestDataService.createSegments(lastPoint(switchSegments1), 2)

        // Segments that are a part of the second switch + the branching track for switch re-linking
        // to work
        val (switch2, switchSegments2, turningSegments2) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(segments1To2))

        splitTestDataService.insertAsTrack(turningSegments2)

        val postSegments = splitTestDataService.createSegments(lastPoint(switchSegments2), 4)

        val track =
            splitTestDataService.createAsMainTrack(
                preSegments + switchSegments1 + segments1To2 + switchSegments2 + postSegments
            )

        val request =
            splitRequest(
                track.id,
                targetRequest(null, "part1"),
                targetRequest(switch1.id, "part2"),
                targetRequest(switch2.id, "part3"),
            )
        val result = splitDao.getOrThrow(splitService.split(LayoutBranch.main, request))

        assertSplitMatchesRequest(request, result)

        request.targetTracks.forEachIndexed { index, targetRequest ->
            assertTargetTrack(track, targetRequest, result.targetLocationTracks[index])
        }

        assertEquals(LocationTrackState.DELETED, locationTrackService.get(MainLayoutContext.draft, track.id)?.state)
    }

    @Test
    fun `location track split should reuse duplicates based on request`() {
        // Test data:
        // Segements  ---1--2----
        // Main       |---------|
        // Duplicates |--|--|
        // Splits     |--|--|---|

        val preSegments = splitTestDataService.createSegments(Point(0.0, 0.0), 3)

        // Segments that are a part of the first switch + the branching track for switch re-linking
        // to work
        val (switch1, switchSegments1, turningSegments1) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(preSegments))
        splitTestDataService.insertAsTrack(turningSegments1)

        val segments1To2 = splitTestDataService.createSegments(lastPoint(switchSegments1), 2)

        // Segments that are a part of the second switch + the branching track for switch re-linking
        // to work
        val (switch2, switchSegments2, turningSegments2) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(segments1To2))
        splitTestDataService.insertAsTrack(turningSegments2)

        val postSegments = splitTestDataService.createSegments(lastPoint(switchSegments2), 4)

        val track =
            splitTestDataService.createAsMainTrack(
                preSegments + switchSegments1 + segments1To2 + switchSegments2 + postSegments
            )

        // Create duplicates as completely separate of the original, as they're overwritten anyhow
        val duplicate1 =
            splitTestDataService.insertAsTrack(
                splitTestDataService.createSegments(Point(5.0, 5.0), 2),
                duplicateOf = track.id,
            )

        val duplicate2 =
            splitTestDataService.insertAsTrack(
                splitTestDataService.createSegments(Point(15.0, 15.0), 5),
                duplicateOf = track.id,
            )

        val request =
            splitRequest(
                track.id,
                targetRequest(null, "part1", duplicateTrackId = duplicate1, operation = OVERWRITE),
                targetRequest(switch1.id, "part2", duplicateTrackId = duplicate2, operation = OVERWRITE),
                targetRequest(switch2.id, "part3"),
            )
        val result = splitDao.getOrThrow(splitService.split(LayoutBranch.main, request))

        assertSplitMatchesRequest(request, result)

        request.targetTracks.forEachIndexed { index, targetRequest ->
            assertTargetTrack(track, targetRequest, result.targetLocationTracks[index])
        }

        assertEquals(LocationTrackState.DELETED, locationTrackService.get(MainLayoutContext.draft, track.id)?.state)
    }

    @Test
    fun `location track split should retain partial duplicates geometry`() {
        // Test data:
        // Segements  ---1--2---3----
        // Main          |------|
        // Duplicates |-----|-------|
        // Splits        |--|---|

        val preSegments = splitTestDataService.createSegments(Point(0.0, 0.0), 3)

        // Segments that are a part of the first switch + the branching track for switch re-linking
        // to work
        val (switch1, switchSegments1, switchTurningSegments1) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(preSegments))
        splitTestDataService.insertAsTrack(switchTurningSegments1)

        val segments1To2 = splitTestDataService.createSegments(lastPoint(switchSegments1), 2)

        // Segments that are a part of the second switch + the branching track for switch re-linking
        // to work
        val (switch2, switchSegments2, switchTurningSegments2) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(segments1To2))
        splitTestDataService.insertAsTrack(switchTurningSegments2)

        val segments2To3 = splitTestDataService.createSegments(lastPoint(switchSegments2), 3)

        // Segments that are a part of the second switch + the branching track for switch re-linking
        // to work
        val (_, switchSegments3, switchTurningSegments3) =
            splitTestDataService.createSwitchAndGeometry(lastPoint(segments2To3))
        splitTestDataService.insertAsTrack(switchTurningSegments3)

        val postSegments = splitTestDataService.createSegments(lastPoint(switchSegments3), 4)

        // The main track goes from switch 1 to switch 3
        val track =
            splitTestDataService.createAsMainTrack(switchSegments1 + segments1To2 + switchSegments2 + segments2To3)
        // Duplicate 1 starts before the main track and continues after the first switch
        val duplicate1 =
            splitTestDataService.insertAsTrack(
                segments = preSegments + switchSegments1 + segments1To2,
                duplicateOf = track.id,
            )
        // Duplicate 2 starts from the second switch and continues beyond the main track
        val duplicate2 =
            splitTestDataService.insertAsTrack(
                segments = switchSegments2 + segments2To3 + switchSegments3 + postSegments,
                duplicateOf = track.id,
            )

        val request =
            splitRequest(
                track.id,
                targetRequest(switch1.id, "part2", duplicateTrackId = duplicate1, operation = TRANSFER),
                targetRequest(switch2.id, "part3", duplicateTrackId = duplicate2, operation = TRANSFER),
            )
        val result = splitDao.getOrThrow(splitService.split(LayoutBranch.main, request))

        assertSplitMatchesRequest(request, result)

        request.targetTracks.forEachIndexed { index, targetRequest ->
            assertTransferTargetTrack(targetRequest, result.targetLocationTracks[index])
        }

        assertEquals(LocationTrackState.DELETED, locationTrackService.get(MainLayoutContext.draft, track.id)?.state)
    }

    private fun lastPoint(segments: List<LayoutSegment>): IPoint = segments.last().segmentPoints.last()

    private fun assertSplitMatchesRequest(request: SplitRequest, split: Split) {
        assertEquals(request.sourceTrackId, split.sourceLocationTrackId)
        request.targetTracks.forEach { targetRequest ->
            if (targetRequest.startAtSwitchId != null) {
                assertTrue(split.relinkedSwitches.contains(targetRequest.startAtSwitchId))
            }
        }
        assertEquals(request.targetTracks.size, split.targetLocationTracks.size)
        request.targetTracks.forEachIndexed { index, targetRequest ->
            val targetResult = split.targetLocationTracks[index]
            assertEquals(targetRequest.getOperation(), targetResult.operation)
            targetRequest.duplicateTrack?.let { d -> assertEquals(d.id, targetResult.locationTrackId) }
        }
    }

    private fun assertTransferTargetTrack(request: SplitRequestTarget, response: SplitTarget) {
        // This assert is for TRANSFER only: use assertTargetTrack for other operations
        assertEquals(SplitTargetOperation.TRANSFER, request.getOperation())

        val (track, alignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, response.locationTrackId)
        val (originalTrack, originalAlignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, response.locationTrackId)

        // TRANSFER operation should not change the duplicate track geometry or fields
        assertEquals(originalTrack.name, track.name)
        assertEquals(originalTrack.descriptionBase, track.descriptionBase)
        assertEquals(originalTrack.descriptionSuffix, track.descriptionSuffix)
        assertNull(track.duplicateOf)
        request.startAtSwitchId?.let { startSwitchId -> assertEquals(startSwitchId, track.switchIds.first()) }

        assertMatches(originalAlignment, alignment)
    }

    private fun assertTargetTrack(
        sourceResponse: LayoutDaoResponse<LocationTrack>,
        request: SplitRequestTarget,
        response: SplitTarget,
    ) {
        // This assert is not for TRANSFER operation: use assertTransferTargetTrack for that
        assertNotEquals(SplitTargetOperation.TRANSFER, request.getOperation())

        val (_, source) = locationTrackService.getWithAlignment(sourceResponse.rowVersion)
        val (track, alignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, response.locationTrackId)

        assertEquals(request.name, track.name)
        assertEquals(request.descriptionBase, track.descriptionBase)
        assertEquals(request.descriptionSuffix, track.descriptionSuffix)
        assertNull(track.duplicateOf)
        request.startAtSwitchId?.let { startSwitchId -> assertEquals(startSwitchId, track.switchIds.first()) }

        val sourceSegments = source.segments.subList(response.segmentIndices.first, response.segmentIndices.last + 1)

        assertEquals(sourceSegments.size, alignment.segments.size)
        assertEquals(sourceSegments.sumOf { s -> s.length }, alignment.length, 0.001)
    }

    @Test
    fun `should find splits by source location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits =
            splitService.findUnfinishedSplits(LayoutBranch.main, locationTrackIds = listOf(split.sourceLocationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `should find splits by target location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits =
            splitService.findUnfinishedSplits(
                LayoutBranch.main,
                locationTrackIds = listOf(split.targetLocationTracks.first().locationTrackId),
            )

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `Should find splits by relinked switches`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits = splitService.findUnfinishedSplits(LayoutBranch.main, switchIds = split.relinkedSwitches)

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `Duplicate tracks should be reassigned to most overlapping tracks if left unused`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(
            referenceLine(trackNumberId),
            alignment(segment(Point(-1000.0, 0.0), Point(1000.0, 0.0))),
        )

        val switchStartPoints =
            listOf(Point(100.0, 0.0), Point(200.0, 0.0), Point(300.0, 0.0), Point(400.0, 0.0), Point(500.0, 0.0))

        val (switchesAndSegments, alignment) = alignmentWithMultipleSwitches(switchStartPoints)

        val sourceTrack = mainOfficialContext.insert(locationTrack(trackNumberId), alignment)

        val duplicateIds =
            listOf(
                    mainOfficialContext.insert(
                        locationTrack(
                            name = "Used duplicate between first and second switch",
                            trackNumberId = trackNumberId,
                            duplicateOf = sourceTrack.id,
                        ),
                        alignment(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
                    ),
                    mainOfficialContext.insert(
                        locationTrack(
                            name = "Unused dupe with full overlap",
                            trackNumberId = trackNumberId,
                            duplicateOf = sourceTrack.id,
                        ),
                        alignment(segment(Point(250.0, 0.0), Point(275.0, 0.0))),
                    ),
                    mainOfficialContext.insert(
                        locationTrack(
                            name = "Unused dupe with partial overlap of two new tracks",
                            trackNumberId = trackNumberId,
                            duplicateOf = sourceTrack.id,
                        ),
                        alignment(segment(Point(275.0, 0.0), Point(350.0, 0.0))),
                    ),
                    mainOfficialContext.insert(
                        locationTrack(
                            name = "Unused dupe with flipped partial overlap",
                            trackNumberId = trackNumberId,
                            duplicateOf = sourceTrack.id,
                        ),
                        alignment(segment(Point(370.0, 0.0), Point(410.0, 0.0))),
                    ),
                )
                .map { daoResponse -> daoResponse.id }

        val splitRequest =
            SplitRequest(
                sourceTrack.id,
                listOf(
                    targetRequest(null, "track start"),
                    targetRequest(
                        switchesAndSegments.switchIds[0],
                        "used dupe track between switch 0 and 1",
                        duplicateTrackId = duplicateIds[0],
                    ),
                    targetRequest(switchesAndSegments.switchIds[1], "track with full dupe overlap after split"),
                    targetRequest(switchesAndSegments.switchIds[2], "track with partial dupe overlap after split"),
                    targetRequest(switchesAndSegments.switchIds[3], "track with flipped partial overlap"),
                    targetRequest(switchesAndSegments.switchIds[4], "track end"),
                ),
            )
        val splitResult = splitDao.getOrThrow(splitService.split(LayoutBranch.main, splitRequest))

        val usedDuplicateTrackAfterSplit =
            locationTrackService.getOrThrow(
                MainLayoutContext.draft,
                splitResult.targetLocationTracks[1].locationTrackId,
            )
        val unusedDuplicateFullyOverlappingNewTrack =
            locationTrackService.getOrThrow(MainLayoutContext.draft, duplicateIds[1])
        val unusedDuplicatePartiallyOverlappingNewTrack =
            locationTrackService.getOrThrow(MainLayoutContext.draft, duplicateIds[2])
        val unusedDuplicateWithFlippedPartialOverlap =
            locationTrackService.getOrThrow(MainLayoutContext.draft, duplicateIds[3])

        assertEquals(null, usedDuplicateTrackAfterSplit.duplicateOf)

        assertEquals(
            splitResult.targetLocationTracks[2].locationTrackId,
            unusedDuplicateFullyOverlappingNewTrack.duplicateOf,
        )

        assertEquals(
            splitResult.targetLocationTracks[3].locationTrackId,
            unusedDuplicatePartiallyOverlappingNewTrack.duplicateOf,
        )

        assertEquals(
            // Same target location track index as above on purpose.
            splitResult.targetLocationTracks[3].locationTrackId,
            unusedDuplicateWithFlippedPartialOverlap.duplicateOf,
        )
    }

    private fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })

    private fun insertSplitWithTwoTracks(): IntId<Split> {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val sourceTrack =
            mainOfficialContext.insert(
                locationTrack(trackNumberId),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )

        val endTrack =
            mainOfficialContext.insert(
                locationTrack(trackNumberId),
                alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
            )

        val relinkedSwitchId = mainOfficialContext.createSwitch().id

        return splitDao.saveSplit(
            sourceTrack.rowVersion,
            listOf(SplitTarget(endTrack.id, 0..0, SplitTargetOperation.CREATE)),
            listOf(relinkedSwitchId),
            updatedDuplicates = emptyList(),
        )
    }

    private data class SwitchesAndSegments(
        val switchIds: List<IntId<TrackLayoutSwitch>> = emptyList(),
        val switchStartPoints: List<Point> = emptyList(),
        val segments: List<LayoutSegment> = emptyList(),
    )

    private fun alignmentWithMultipleSwitches(
        startPoints: List<Point>,
        structure: SwitchStructure = getYvStructure(),
    ): Pair<SwitchesAndSegments, LayoutAlignment> {
        val segmentsBeforeFirstSwitch =
            startPoints.first().toPoint().let { firstSwitchStart ->
                listOf(
                    segment(
                        Point(firstSwitchStart.x - 20.0, firstSwitchStart.y),
                        Point(firstSwitchStart.x - 10.0, firstSwitchStart.y),
                    ),
                    segment(
                        Point(firstSwitchStart.x - 10.0, firstSwitchStart.y),
                        Point(firstSwitchStart.x, firstSwitchStart.y),
                    ),
                )
            }

        val initialSwitchesAndSegments = SwitchesAndSegments(segments = segmentsBeforeFirstSwitch)

        return startPoints
            .zip(startPoints.drop(1) + null)
            .fold(initialSwitchesAndSegments) { switchesAndSegments, (startPoint, nextStartPoint) ->
                val (switch, switchSegments, turningSegments) =
                    splitTestDataService.createSwitchAndGeometry(startPoint, structure)
                // For the switch relinking to have another track to find near the start of every
                // switch.
                splitTestDataService.insertAsTrack(turningSegments)

                val switchEnd = switchSegments.last().segmentPoints.last()
                val pointAfterSwitch = switchEnd + Point(10.0, startPoint.y)
                val segmentAfterSwitch = segment(switchEnd, pointAfterSwitch)

                val postSwitchSegments =
                    nextStartPoint?.let { actualNextStartPoint ->
                        listOf(segmentAfterSwitch, segment(pointAfterSwitch, actualNextStartPoint))
                    }
                        ?: listOf(
                            segmentAfterSwitch,
                            segment(pointAfterSwitch, Point(pointAfterSwitch.x + 10.0, pointAfterSwitch.y)),
                        )

                SwitchesAndSegments(
                    switchIds = switchesAndSegments.switchIds + switch.id,
                    switchStartPoints = switchesAndSegments.switchStartPoints + startPoint,
                    segments = switchesAndSegments.segments + switchSegments + postSwitchSegments,
                )
            }
            .let { switchesAndSegments -> switchesAndSegments to alignment(switchesAndSegments.segments) }
    }
}
