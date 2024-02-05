package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
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

    @Test
    fun `location track split should work based on request`() {
        val switchStart = Point(0.0, 0.0)
        val structure = getYvStructure()
        val switchId = insertSwitch(switchFromDbStructure(getUnusedSwitchName().toString(), switchStart, structure)).id

        // Some segments in the beginning
        val preSwitchSegments = listOf(
            segment(switchStart + Point(-20.0, 0.0), switchStart + Point(-10.0, 0.0)),
            segment(switchStart + Point(-10.0, 0.0), switchStart + Point(0.0, 0.0)),
        )
        // Create segments that match the switch structure for re-linking to work
        val switchSegments = segmentsFromSwitchStructure(switchStart, switchId, structure, listOf(1, 5, 2))
        val switchEnd = switchSegments.last().segmentPoints.last()
        // Some segments in the end
        val postSwitchSegments = listOf(
            segment(switchEnd + Point(0.0, 0.0), switchEnd + Point(10.0, 0.0)),
            segment(switchEnd + Point(10.0, 0.0), switchEnd + Point(20.0, 0.0)),
        )

        val alignment = alignment(preSwitchSegments + switchSegments + postSwitchSegments)
        val trackId = insertLocationTrack(locationTrack(insertOfficialTrackNumber()), alignment).id

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

        val foundSplits = splitService.findUnfinishedSplits(listOf(split.locationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    @Test
    fun `should find splits by target location track`() {
        val splitId = insertSplitWithTwoTracks()
        val split = splitDao.getOrThrow(splitId)

        val foundSplits = splitService.findUnfinishedSplits(listOf(split.targetLocationTracks.first().locationTrackId))

        assertEquals(splitId, foundSplits.first().id)
    }

    private fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })

    private fun insertSplitWithTwoTracks(): IntId<Split> {
        val trackNumberId = insertOfficialTrackNumber()
        insertReferenceLine(
            referenceLine(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val sourceTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

        val endTrack = insertLocationTrack(
            locationTrack(trackNumberId = trackNumberId),
            alignment(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
        )

        val relinkedSwitchId = insertUniqueSwitch().id

        return splitDao.saveSplit(
            sourceTrack.id,
            listOf(SplitTarget(endTrack.id, 0..0)),
            listOf(relinkedSwitchId),
        )
    }
}
