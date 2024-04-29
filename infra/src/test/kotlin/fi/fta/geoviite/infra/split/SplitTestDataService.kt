package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.segmentsFromSwitchStructure
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.switchFromDbStructure
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.test.assertEquals

data class SplitTestTrackStructure(
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val straightTrackId: IntId<LocationTrack>,
    val branchingTrackId: IntId<LocationTrack>,
    val switchId: IntId<TrackLayoutSwitch>,

    val straightTrackAlignment: LayoutAlignment,
)

data class SwitchAndSegments(
    val switch: DaoResponse<TrackLayoutSwitch>,
    val straightSwitchSegments: List<LayoutSegment>,
    val turningSwitchSegments: List<LayoutSegment>,
)

@Service
class SplitTestDataService @Autowired constructor(
    private val switchStructureDao: SwitchStructureDao,
    private val locationTrackService: LocationTrackService,
) : DBTestBase() {

    fun createSwitchAndGeometry(
        startPoint: IPoint,
        structure: SwitchStructure = getYvStructure(),
        externalId: Oid<TrackLayoutSwitch>? = null,
    ): SwitchAndSegments {
        val switchInsertResponse = insertSwitch(
            switchFromDbStructure(
                getUnusedSwitchName().toString(),
                startPoint,
                structure,
                draft = false,
                externalId = externalId?.toString(),
            )
        )
        return SwitchAndSegments(
            switchInsertResponse,
            segmentsFromSwitchStructure(startPoint, switchInsertResponse.id, structure, listOf(1, 5, 2)),
            segmentsFromSwitchStructure(startPoint, switchInsertResponse.id, structure, listOf(1, 3)),
        )
    }

    fun createSegments(startPoint: IPoint, count: Int = 3, pointOffset: Double = 10.0): List<LayoutSegment> {
        return (0..<count).map { idx ->
            val start = startPoint + Point(idx * pointOffset, 0.0)
            val end = start + Point(pointOffset, 0.0)
            segment(start, end)
        }
    }

    fun insertAsTrack(
        segments: List<LayoutSegment>,
        duplicateOf: IntId<LocationTrack>? = null,
        trackNumberId: IntId<TrackLayoutTrackNumber> = insertOfficialTrackNumber(),
    ): IntId<LocationTrack> {
        val alignment = alignment(segments)
        return insertLocationTrack(
            locationTrack(
                trackNumberId = trackNumberId,
                draft = false,
                duplicateOf = duplicateOf
            ),
            alignment,
        ).id
    }

    fun createAsMainTrack(
        segments: List<LayoutSegment>,
        trackNumberId: IntId<TrackLayoutTrackNumber> = insertOfficialTrackNumber(),
    ): DaoResponse<LocationTrack> {
        val alignment = alignment(segments)
        insertReferenceLine(referenceLine(trackNumberId, draft = false), alignment)

        return insertLocationTrack(locationTrack(trackNumberId, draft = false), alignment).also { r ->
            val (dbTrack, dbAlignment) = locationTrackService.getWithAlignment(r.rowVersion)
            assertEquals(trackNumberId, dbTrack.trackNumberId)
            assertEquals(segments.size, dbAlignment.segments.size)
            assertEquals(segments.sumOf { s -> s.length }, dbAlignment.length, 0.001)
        }
    }

//    fun createValidTrackStructureWithSingleSwitch(
//        trackNumber: TrackNumber = getUnusedTrackNumber(),
//        straightTrackName: String = "some straight track",
//        switchStart: Point = Point(0.0, 0.0),
//    ): SplitTestTrackStructure {
//        val trackNumberId = insertOfficialTrackNumber(trackNumber)
//
//        val structure = getYvStructure()
//        val switchId = insertSwitch(
//            switchFromDbStructure(
//                "split test switch",
//                switchStart,
//                structure,
//                draft = false,
//                externalId = someOid<TrackLayoutSwitch>().toString(),
//            )
//        ).id
//
//        // Some segments in the beginning
//        val preSwitchSegments = listOf(
//            segment(switchStart + Point(-20.0, 0.0), switchStart + Point(-10.0, 0.0)),
//            segment(switchStart + Point(-10.0, 0.0), switchStart + Point(0.0, 0.0)),
//        )
//        // Create segments & branching track that match the switch structure for re-linking to work
//        val switchSegments = segmentsFromSwitchStructure(switchStart, switchId, structure, listOf(1, 5, 2))
//        val branchingTrackId = insertBranchingTrackAlignment(
//            switchStart,
//            switchId,
//            structure,
//            listOf(1, 3),
//            trackNumberId,
//        )
//
//        val switchEnd = switchSegments.last().segmentPoints.last()
//        // Some segments in the end
//        val postSwitchSegments = listOf(
//            segment(switchEnd + Point(0.0, 0.0), switchEnd + Point(10.0, 0.0)),
//            segment(switchEnd + Point(10.0, 0.0), switchEnd + Point(20.0, 0.0)),
//        )
//
//        val straightTrackAlignment = alignment(preSwitchSegments + switchSegments + postSwitchSegments)
//
//        insertReferenceLine(referenceLine(trackNumberId, draft = false), straightTrackAlignment)
//
//        val straightTrackId = insertLocationTrack(
//            locationTrack(
//                trackNumberId,
//                name = straightTrackName,
//                draft = false
//            ),
//            straightTrackAlignment
//        ).id
//
//        return SplitTestTrackStructure(
//            trackNumberId,
//            straightTrackId,
//            branchingTrackId,
//            switchId,
//
//            straightTrackAlignment,
//        )
//    }
//
//    private fun insertBranchingTrackAlignment(
//        start: Point,
//        switchId: IntId<TrackLayoutSwitch>,
//        structure: SwitchStructure,
//        line: List<Int>,
//        trackNumberId: IntId<TrackLayoutTrackNumber> = insertOfficialTrackNumber(),
//    ): IntId<LocationTrack> {
//        val alignment = alignment(segmentsFromSwitchStructure(start, switchId, structure, line))
//        return insertLocationTrack(
//            locationTrack(
//                trackNumberId,
//                name = "split test branching track",
//                draft = false
//            ),
//            alignment,
//        ).id
//    }

    fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })
}
