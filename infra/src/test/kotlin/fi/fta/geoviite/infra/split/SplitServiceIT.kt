package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import targetRequest
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class SplitServiceIT @Autowired constructor(
    val splitService: SplitService,
    val splitDao: SplitDao,
    val switchDao: LayoutSwitchDao,
    val switchStructureDao: SwitchStructureDao,
    val locationTrackService: LocationTrackService,
) : DBTestBase() {

    // The run order of the tests in this test suite matters if the database is not cleaned before each test.
    // This gave false positive results for tests.
    //
    // This cleanup code can be removed after GVT-2484 has been implemented.
    @BeforeEach
    fun clear() {
        deleteFromTables(
            schema = "layout",
            tables = arrayOf(
                "alignment",
                "alignment_version",
                "km_post",
                "km_post_version",
                "location_track",
                "location_track_version",
                "reference_line",
                "reference_line_version",
                "switch",
                "switch_version",
                "switch_joint",
                "switch_joint_version",
                "track_number",
                "track_number_version",
                "segment_version",
                "segment_geometry",
            ),
        )

        deleteFromTables(
            schema = "publication",
            tables = arrayOf(
                "split",
                "split_version",
                "split_relinked_switch",
                "split_relinked_switch_version",
                "split_target_location_track",
                "split_Target_location_track_version",
            ),
        )
    }

    @Test
    fun `location track split should work based on request`() {
        val switchStart = Point(0.0, 0.0)
        val structure = getYvStructure()
        val switchId = insertSwitch(
            switchFromDbStructure(getUnusedSwitchName().toString(), switchStart, structure, draft = false)
        ).id

        // Some segments in the beginning
        val preSwitchSegments = listOf(
            segment(switchStart + Point(-20.0, 0.0), switchStart + Point(-10.0, 0.0)),
            segment(switchStart + Point(-10.0, 0.0), switchStart + Point(0.0, 0.0)),
        )
        // Create segments & branching track that match the switch structure for re-linking to work
        val switchSegments = segmentsFromSwitchStructure(switchStart, switchId, structure, listOf(1, 5, 2))
        insertBranchingSwitchAlignment(switchStart, switchId, structure, listOf(1, 3))

        val switchEnd = switchSegments.last().segmentPoints.last()
        // Some segments in the end
        val postSwitchSegments = listOf(
            segment(switchEnd + Point(0.0, 0.0), switchEnd + Point(10.0, 0.0)),
            segment(switchEnd + Point(10.0, 0.0), switchEnd + Point(20.0, 0.0)),
        )

        val alignment = alignment(preSwitchSegments + switchSegments + postSwitchSegments)

        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(referenceLine(trackNumberId, draft = false), alignment)

        val trackId = insertLocationTrack(locationTrack(trackNumberId, draft = false), alignment).id

        val request = SplitRequest(
            trackId,
            listOf(targetRequest(null, "part1"), targetRequest(switchId, "part2")),
        )
        val result = splitDao.getOrThrow(splitService.split(request))

        // Verify split result data
        assertEquals(trackId, result.locationTrackId)
        assertEquals(listOf(switchId), result.relinkedSwitches)
        assertEquals(2, result.targetLocationTracks.size)
        assertEquals(0..1, result.targetLocationTracks[0].segmentIndices)
        assertEquals(2..5, result.targetLocationTracks[1].segmentIndices)

        // Verify created new tracks
        assertTargetTrack(alignment, request.targetTracks[0], result.targetLocationTracks[0])
        assertTargetTrack(alignment, request.targetTracks[1], result.targetLocationTracks[1])

        // Verify that the old track got deleted
        assertEquals(LayoutState.DELETED, locationTrackService.get(DRAFT, trackId)?.state)
    }

    private fun assertTargetTrack(
        source: LayoutAlignment,
        request: SplitRequestTarget,
        target: SplitTarget,
    ) {
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, target.locationTrackId)

        assertEquals(request.name, track.name)
        assertEquals(request.descriptionBase, track.descriptionBase)
        assertEquals(request.descriptionSuffix, track.descriptionSuffix)

        val sourceSegments = source.segments.subList(target.segmentIndices.first, target.segmentIndices.last + 1)
        assertEquals(sourceSegments.size, alignment.segments.size)
        assertEquals(sourceSegments.sumOf { s -> s.length }, alignment.length, 0.001)
    }

    @Test
    fun `should find splits by source location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits = splitService.findUnfinishedSplitsForLocationTracks(listOf(split.locationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `should find splits by target location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits = splitService.findUnfinishedSplitsForLocationTracks(
            listOf(split.targetLocationTracks.first().locationTrackId)
        )

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `Should find splits by relinked switches`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits = splitService.findUnfinishedSplitsForSwitches(split.relinkedSwitches)

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `Duplicate tracks should be reassigned to most overlapping tracks if left unused`() {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(-1000.0, 0.0), Point(1000.0, 0.0))),
        )

        val switchStartPoints = listOf(
            Point(100.0, 0.0),
            Point(200.0, 0.0),
            Point(300.0, 0.0),
            Point(400.0, 0.0),
            Point(500.0, 0.0),
        )

        val (
            switchesAndSegments,
            alignment
        ) = alignmentWithMultipleSwitches(switchStartPoints)

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment,
        )

        val duplicateIds = listOf(
            insertLocationTrack(
                locationTrack(
                    name = "Used duplicate between first and second switch",
                    trackNumberId = trackNumberId,
                    duplicateOf = sourceTrack.id,
                    draft = false,
                ),
                alignment(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
            ),

            insertLocationTrack(
                locationTrack(
                    name = "Unused dupe with full overlap",
                    trackNumberId = trackNumberId,
                    duplicateOf = sourceTrack.id,
                    draft = false,
                ),
                alignment(segment(Point(250.0, 0.0), Point(275.0, 0.0))),
            ),

            insertLocationTrack(
                locationTrack(
                    name = "Unused dupe with partial overlap of two new tracks",
                    trackNumberId = trackNumberId,
                    duplicateOf = sourceTrack.id,
                    draft = false,
                ),
                alignment(segment(Point(275.0, 0.0), Point(350.0, 0.0))),
            ),

            insertLocationTrack(
                locationTrack(
                    name = "Unused dupe with flipped partial overlap",
                    trackNumberId = trackNumberId,
                    duplicateOf = sourceTrack.id,
                    draft = false,
                ),
                alignment(segment(Point(370.0, 0.0), Point(410.0, 0.0))),
            ),
        ).map { daoResponse -> daoResponse.id }

        val splitRequest = SplitRequest(
            sourceTrack.id,
            listOf(
                targetRequest(
                    null,
                    "track start",
                ),
                targetRequest(
                    switchesAndSegments.switchIds[0],
                    "used dupe track between switch 0 and 1",
                    duplicateTrackId = duplicateIds[0],
                ),
                targetRequest(
                    switchesAndSegments.switchIds[1],
                    "track with full dupe overlap after split",
                ),
                targetRequest(
                    switchesAndSegments.switchIds[2],
                    "track with partial dupe overlap after split",
                ),
                targetRequest(
                    switchesAndSegments.switchIds[3],
                    "track with flipped partial overlap",
                ),
                targetRequest(
                    switchesAndSegments.switchIds[4],
                    "track end",
                ),
            ),
        )
        val splitResult = splitDao.getOrThrow(splitService.split(splitRequest))

        val usedDuplicateTrackAfterSplit = locationTrackService.getOrThrow(DRAFT, splitResult.targetLocationTracks[1].locationTrackId)
        val unusedDuplicateFullyOverlappingNewTrack = locationTrackService.getOrThrow(DRAFT, duplicateIds[1])
        val unusedDuplicatePartiallyOverlappingNewTrack = locationTrackService.getOrThrow(DRAFT, duplicateIds[2])
        val unusedDuplicateWithFlippedPartialOverlap = locationTrackService.getOrThrow(DRAFT, duplicateIds[3])

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

    private fun insertBranchingSwitchAlignment(
        start: Point,
        switchId: IntId<TrackLayoutSwitch>,
        structure: SwitchStructure,
        line: List<Int>,
    ): IntId<LocationTrack> {
        val alignment = alignment(segmentsFromSwitchStructure(start, switchId, structure, line))
        val trackNumberId = insertOfficialTrackNumber()
        return insertLocationTrack(locationTrack(trackNumberId, draft = false), alignment).id
    }

    private fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })

    private fun insertSplitWithTwoTracks(): IntId<Split> {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val endTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId, draft = false),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        return splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTarget(endTrack.id, 0..0)),
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
        val segmentsBeforeFirstSwitch = startPoints.first().toPoint().let { firstSwitchStart ->
            listOf(
                segment(
                    Point(firstSwitchStart.x - 20.0, firstSwitchStart.y),
                    Point(firstSwitchStart.x - 10.0, firstSwitchStart.y)
                ),
                segment(
                    Point(firstSwitchStart.x - 10.0, firstSwitchStart.y),
                    Point(firstSwitchStart.x, firstSwitchStart.y),
                ),
            )
        }

        val initialSwitchesAndSegments = SwitchesAndSegments(
            segments = segmentsBeforeFirstSwitch,
        )

        return startPoints.zip(
            startPoints.drop(1) + null
        ).fold(initialSwitchesAndSegments) { switchesAndSegments, (startPoint, nextStartPoint) ->
            val switchId = insertSwitch(
                switchFromDbStructure(getUnusedSwitchName().toString(), startPoint, structure, draft = false)
            ).id

            val switchSegments = segmentsFromSwitchStructure(startPoint, switchId, structure, listOf(1, 5, 2))

            // For the switch relinking to have another track to find near the start of every switch.
            insertBranchingSwitchAlignment(startPoint, switchId, structure, listOf(1, 3))

            val switchEnd = switchSegments.last().segmentPoints.last()
            val pointAfterSwitch = switchEnd + Point(10.0, startPoint.y)
            val segmentAfterSwitch = segment(switchEnd, pointAfterSwitch)

            val postSwitchSegments = nextStartPoint?.let { actualNextStartPoint ->
                listOf(
                    segmentAfterSwitch,
                    segment(pointAfterSwitch, actualNextStartPoint),
                )
            } ?: listOf(
                segmentAfterSwitch,
                segment(pointAfterSwitch, Point(pointAfterSwitch.x + 10.0, pointAfterSwitch.y)),
            )

            SwitchesAndSegments(
                switchIds = switchesAndSegments.switchIds + switchId,
                switchStartPoints = switchesAndSegments.switchStartPoints + startPoint,
                segments = switchesAndSegments.segments + switchSegments + postSwitchSegments
            )
        }.let { switchesAndSegments ->
            switchesAndSegments to alignment(switchesAndSegments.segments)
        }
    }
}
