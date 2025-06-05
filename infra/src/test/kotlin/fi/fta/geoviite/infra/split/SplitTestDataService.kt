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
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.edgesFromSwitchStructure
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchFromDbStructure
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class SwitchAndEdges(
    val switch: LayoutRowVersion<LayoutSwitch>,
    val straightSwitchEdges: List<LayoutEdge>,
    val turningSwitchEdges: List<LayoutEdge>,
)

@Service
class SplitTestDataService
@Autowired
constructor(
    private val switchStructureDao: SwitchStructureDao,
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
        trackNumberId: IntId<LayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id
    ): IntId<Split> {
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val sourceTrack =
            mainDraftContext.save(
                locationTrack(trackNumberId = trackNumberId, state = LocationTrackState.DELETED),
                geometry,
            )
        val targetTrack = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        return splitDao.saveSplit(
            sourceLocationTrackVersion = sourceTrack,
            splitTargets = listOf(SplitTarget(targetTrack.id, 0..0, SplitTargetOperation.CREATE)),
            relinkedSwitches = listOf(mainOfficialContext.createSwitch().id),
            updatedDuplicates = emptyList(),
        )
    }

    fun createSwitchAndGeometry(
        startPoint: IPoint,
        structure: SwitchStructure = getYvStructure(),
        externalId: Oid<LayoutSwitch>? = null,
    ): SwitchAndEdges {
        val switchInsertResponse =
            mainOfficialContext.save(
                switchFromDbStructure(testDBService.getUnusedSwitchName().toString(), startPoint, structure)
            )
        if (externalId != null) {
            switchDao.insertExternalId(switchInsertResponse.id, LayoutBranch.main, externalId)
        }
        return SwitchAndEdges(
            switchInsertResponse,
            edgesFromSwitchStructure(startPoint, switchInsertResponse.id, structure, listOf(1, 5, 2)),
            edgesFromSwitchStructure(startPoint, switchInsertResponse.id, structure, listOf(1, 3)),
        )
    }

    fun getYvStructure(): SwitchStructure =
        requireNotNull(switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" })

    fun forcefullyFinishAllCurrentlyUnfinishedSplits(branch: LayoutBranch) {
        assertMainBranch(branch)

        splitService.findUnfinishedSplits(branch).forEach { split ->
            splitService.updateSplit(splitId = split.id, bulkTransferState = BulkTransferState.DONE)
        }
    }
}
