package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.segmentsFromSwitchStructure
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.switchFromDbStructure
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class SwitchAndSegments(
    val switch: LayoutRowVersion<TrackLayoutSwitch>,
    val straightSwitchSegments: List<LayoutSegment>,
    val turningSwitchSegments: List<LayoutSegment>,
)

@Service
class SplitTestDataService
@Autowired
constructor(
    private val switchStructureDao: SwitchStructureDao,
    private val locationTrackService: LocationTrackService,
    private val splitDao: SplitDao,
    private val splitService: SplitService,
    private val switchDao: LayoutSwitchDao,
) : DBTestBase() {

    fun clearSplits() {
        val sql =
            """
        truncate publication.split cascade;
        truncate publication.split_version cascade;
        truncate publication.split_relinked_switch cascade;
        truncate publication.split_relinked_switch_version cascade;
        truncate publication.split_target_location_track cascade;
        truncate publication.split_target_location_track_version cascade;
        truncate publication.split_updated_duplicate cascade;
        truncate publication.split_updated_duplicate_version cascade;
    """
                .trimIndent()
        jdbc.execute(sql) { it.execute() }
    }

    fun insertSplit(
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id
    ): IntId<Split> {
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack =
            mainOfficialContext.insert(
                locationTrack(trackNumberId = trackNumberId, state = LocationTrackState.DELETED),
                alignment,
            )
        val targetTrack = mainOfficialContext.insert(locationTrack(trackNumberId), alignment)

        return splitDao.saveSplit(
            sourceLocationTrackVersion = sourceTrack,
            splitTargets = listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
            relinkedSwitches = listOf(mainOfficialContext.createSwitch().id),
            updatedDuplicates = emptyList(),
        )
    }

    fun insertGeocodableSplit(
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        alignment: LayoutAlignment = alignment(segment(Point(0.0, 0.0), Point(1000.0, 0.0))),
    ): IntId<Split> {

        val trackStartPoint = requireNotNull(alignment.start).toPoint()
        val preSegments = createSegments(trackStartPoint, 2)

        val switchStartPoint1 = lastPoint(preSegments)
        val (_, switchSegments1, _) = createSwitchAndGeometry(switchStartPoint1, externalId = someOid())

        val segments1To2 = createSegments(lastPoint(switchSegments1), 2)

        val switchStartPoint2 = lastPoint(segments1To2)
        val (_, switchSegments2, _) = createSwitchAndGeometry(switchStartPoint2, externalId = someOid())

        val postSegments = createSegments(lastPoint(switchSegments2), 4)

        val sourceTrackId =
            createAsMainTrack(
                trackNumberId = trackNumberId,
                segments = preSegments + switchSegments1 + segments1To2 + switchSegments2 + postSegments,
            )

        val destinationTrackId1 =
            insertAsTrack(trackNumberId = trackNumberId, segments = switchSegments1 + segments1To2)

        val destinationTrackId2 =
            insertAsTrack(trackNumberId = trackNumberId, segments = switchSegments2 + postSegments)

        return splitDao.saveSplit(
            sourceLocationTrackVersion = sourceTrackId,
            splitTargets =
                listOf(destinationTrackId1, destinationTrackId2).map { destinationTrack ->
                    SplitTarget(destinationTrack, 0..0, SplitTargetOperation.CREATE)
                },
            relinkedSwitches = listOf(mainOfficialContext.createSwitch().id),
            updatedDuplicates = emptyList(),
        )
    }

    fun createSwitchAndGeometry(
        startPoint: IPoint,
        structure: SwitchStructure = getYvStructure(),
        externalId: Oid<TrackLayoutSwitch>? = null,
    ): SwitchAndSegments {
        val switchInsertResponse =
            mainOfficialContext.insert(
                switchFromDbStructure(testDBService.getUnusedSwitchName().toString(), startPoint, structure)
            )
        if (externalId != null) {
            switchDao.insertExternalId(switchInsertResponse.id, LayoutBranch.main, externalId)
        }
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
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        oid: Oid<LocationTrack> = someOid(),
    ): IntId<LocationTrack> {
        val alignment = alignment(segments)
        return mainOfficialContext
            .insert(locationTrack(trackNumberId = trackNumberId, duplicateOf = duplicateOf), alignment)
            .id
            .also { locationTrackId -> locationTrackService.insertExternalId(LayoutBranch.main, locationTrackId, oid) }
    }

    fun createAsMainTrack(
        segments: List<LayoutSegment>,
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
    ): LayoutRowVersion<LocationTrack> {
        val alignment = alignment(segments)
        mainOfficialContext.insert(referenceLine(trackNumberId), alignment)

        return mainOfficialContext.insert(locationTrack(trackNumberId), alignment).also { r ->
            val (dbTrack, dbAlignment) = locationTrackService.getWithAlignment(r)
            assertEquals(trackNumberId, dbTrack.trackNumberId)
            assertEquals(segments.size, dbAlignment.segments.size)
            assertEquals(segments.sumOf { s -> s.length }, dbAlignment.length, 0.001)
            locationTrackService.insertExternalId(LayoutBranch.main, dbTrack.id as IntId, someOid())
        }
    }

    fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })

    fun forcefullyFinishAllCurrentlyUnfinishedSplits(branch: LayoutBranch) {
        assertMainBranch(branch)

        splitService.findUnfinishedSplits(branch).forEach { split ->
            splitDao.updateBulkTransfer(splitId = split.id, bulkTransferState = BulkTransferState.DONE)
        }
    }
}

private fun lastPoint(segments: List<LayoutSegment>): IPoint = segments.last().segmentPoints.last()
