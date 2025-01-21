package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationGroup
import fi.fta.geoviite.infra.ratko.FakeRatko
import fi.fta.geoviite.infra.ratko.FakeRatkoService
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class SplitTestUI
@Autowired
constructor(
    private val locationTrackService: LocationTrackService,
    private val splitService: SplitService,
    private val splitTestDataService: SplitTestDataService,
    private val fakeRatkoService: FakeRatkoService,
) : SeleniumTest() {

    lateinit var fakeRatko: FakeRatko

    @BeforeEach
    fun startServer() {
        fakeRatko = fakeRatkoService.start()
        fakeRatko.isOnline()
    }

    @AfterEach
    fun stopServer() {
        fakeRatko.stop()
    }

    @Test
    fun `Split can be created and published`() {
        testDBService.clearAllTables()
        val trackNumber = TrackNumber("876")
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(trackNumber).id as IntId

        val trackStartPoint = HelsinkiTestData.HKI_BASE_POINT + Point(x = 675.0, y = 410.0)
        val preSegments = splitTestDataService.createSegments(trackStartPoint, 3)

        val switchStartPoint1 = lastPoint(preSegments)
        val (_, switchSegments1, turningSegments1) =
            splitTestDataService.createSwitchAndGeometry(switchStartPoint1, externalId = someOid())

        val segments1To2 = splitTestDataService.createSegments(lastPoint(switchSegments1), 2)

        val switchStartPoint2 = lastPoint(segments1To2)
        val (_, switchSegments2, turningSegments2) =
            splitTestDataService.createSwitchAndGeometry(switchStartPoint2, externalId = someOid())

        val postSegments = splitTestDataService.createSegments(lastPoint(switchSegments2), 4)

        val sourceTrackId =
            splitTestDataService
                .createAsMainTrack(
                    trackNumberId = trackNumberId,
                    segments = preSegments + switchSegments1 + segments1To2 + switchSegments2 + postSegments,
                )
                .id

        val sourceTrackName = locationTrackService.get(MainLayoutContext.official, sourceTrackId)!!.name.toString()
        splitTestDataService.insertAsTrack(trackNumberId = trackNumberId, segments = turningSegments1)
        splitTestDataService.insertAsTrack(trackNumberId = trackNumberId, segments = turningSegments2)

        startGeoviite()

        val trackLayoutPage = E2EAppBar().goToMap().switchToDraftMode()

        trackLayoutPage.selectionPanel.selectReferenceLine(trackNumber.toString())
        trackLayoutPage.toolPanel.referenceLineLocation.zoomTo()

        trackLayoutPage.selectionPanel.selectLocationTrack(sourceTrackName)
        val splittingInfobox = trackLayoutPage.toolPanel.locationTrackLocation.startSplitting()
        splittingInfobox.waitUntilTargetTrackInputExists(0)

        trackLayoutPage.clickAtCoordinates(switchStartPoint1.toPoint())
        splittingInfobox.waitUntilTargetTrackInputExists(1)

        trackLayoutPage.clickAtCoordinates(switchStartPoint2.toPoint())
        splittingInfobox.waitUntilTargetTrackInputExists(2)

        val targetTrackNames =
            (0..2).map { index ->
                val targetTrackName = "target track $index"
                val targetTrackDescription = "target track description $index"

                splittingInfobox
                    .setTargetTrackName(index, targetTrackName)
                    .setTargetTrackDescription(index, targetTrackDescription)

                targetTrackName
            }

        splittingInfobox.confirmSplit()

        val unpublishedSplit =
            splitService
                .findUnpublishedSplits(branch = LayoutBranch.main, locationTrackIds = listOf(sourceTrackId))
                .first()
        assertEquals(3, unpublishedSplit.targetLocationTracks.size)

        // External IDs are fetched from (fake) Ratko service during publication,
        // this creates enough OIDs for the new tracks to the fake Ratko service.
        //
        // Publication fails if the backend cannot fetch new OIDs for new tracks during publication.
        repeat(unpublishedSplit.targetLocationTracks.size) {
            fakeRatko.acceptsNewLocationTrackGivingItOid(someOid<LocationTrack>().toString())
        }

        val previewView = trackLayoutPage.goToPreview().waitForAllTableValidationsToComplete()

        val splitPublicationGroup = PublicationGroup(id = unpublishedSplit.id)
        previewView.changesTable.movePublicationGroup(splitPublicationGroup)

        val trackLayoutPageAfterPublishing = previewView.waitForAllTableValidationsToComplete().publish()

        trackLayoutPageAfterPublishing.selectionPanel.selectLocationTrack(targetTrackNames[0])
        assertFalse(
            getElementWhenExists(byQaId("start-splitting")).isEnabled,
            "splitting a target track again should not be possible before bulk transfer has completed",
        )

        val frontpage = goToFrontPage()
        val splitDetailsDialog = frontpage.openNthSplitPublicationDetails(1)

        assertEquals(sourceTrackName, splitDetailsDialog.sourceTrackName())
        splitDetailsDialog.close().setNthSplitBulkTransferCompleted(1)

        goToMap().selectionPanel.selectLocationTrack(targetTrackNames[1])
        assertTrue(
            getElementWhenExists(byQaId("start-splitting")).isEnabled,
            "splitting a target track again should be possible after setting the bulk transfer to be completed",
        )
    }
}

private fun lastPoint(segments: List<LayoutSegment>): IPoint = segments.last().segmentPoints.last()
