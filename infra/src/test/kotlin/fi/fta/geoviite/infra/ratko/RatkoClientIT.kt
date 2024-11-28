package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoSwitch
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.switch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
@ConditionalOnBean(RatkoClient::class)
class RatkoClientIT
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val switchLibraryService: SwitchLibraryService,
    private val fakeRatkoService: FakeRatkoService,
    private val splitDao: SplitDao,
    private val splitTestDataService: SplitTestDataService,
) : DBTestBase() {

    lateinit var fakeRatko: FakeRatko

    @BeforeEach
    fun startServer() {
        fakeRatko = fakeRatkoService.start()
    }

    @AfterEach
    fun stopServer() {
        fakeRatko.stop()
    }

    private val switchOwners = switchLibraryService.getSwitchOwners()

    @Test
    fun checkOnlineStatus() {
        fakeRatko.isOnline()
        assertEquals(RatkoClient.RatkoStatus(RatkoConnectionStatus.ONLINE, 200), ratkoClient.getRatkoOnlineStatus())

        fakeRatko.isOffline()
        assertEquals(
            RatkoClient.RatkoStatus(RatkoConnectionStatus.ONLINE_ERROR, 500),
            ratkoClient.getRatkoOnlineStatus(),
        )
    }

    @Test
    fun shouldFetchRatkoSwitchByExternalId() {
        fakeRatko.hasSwitch(ratkoSwitch("1.2.3.4.5"))
        ratkoClient.getSwitchAsset(RatkoOid("1.2.3.4.5"))
    }

    @Test
    fun shouldUpdateRatkoSwitchProperties() {
        val oid = "1.2.3.4.5"
        fakeRatko.hasSwitch(ratkoSwitch(oid))
        val layoutSwitch = switch(draft = false)
        val basicUpdateSwitch = createRatkoBasicUpdateSwitch(layoutSwitch, oid)
        ratkoClient.updateAssetProperties(RatkoOid(oid), basicUpdateSwitch.properties)
    }

    private fun createRatkoBasicUpdateSwitch(layoutSwitch: LayoutSwitch, oid: String): RatkoSwitchAsset {
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        fakeRatko.hasSwitch(ratkoSwitch(oid))
        val ratkoSwitch = ratkoClient.getSwitchAsset(RatkoOid(oid))
        return convertToRatkoSwitch(
            layoutSwitch,
            MainBranchRatkoExternalId(Oid(oid)),
            switchStructure,
            switchOwners.firstOrNull(),
            ratkoSwitch,
        )
    }

    @Test
    fun shouldGetExternalIdForNewLayoutTrack() {
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.2.3.4.5")
        assertEquals("1.2.3.4.5", ratkoClient.getNewLocationTrackOid().id)
    }

    @Test
    fun shouldGetExternalIdForNewTrackNumber() {
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        assertEquals("1.2.3.4.5", ratkoClient.getNewRouteNumberOid().id)
    }

    @Test
    fun `New bulk transfer can be started`() {
        splitTestDataService.insertSplit().let(splitDao::getOrThrow)

        val expectedBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(expectedBulkTransferId)
        val (receivedBulkTransferId, receivedBulkTransferState) =
            ratkoClient.sendBulkTransferCreateRequest(bulkTransferStartRequest(), defaultBlockTimeout)

        assertEquals(expectedBulkTransferId, receivedBulkTransferId)
        assertEquals(BulkTransferState.CREATED, receivedBulkTransferState)
    }

    @Test
    fun `Bulk transfer state can be polled`() {
        splitTestDataService.insertSplit().let(splitDao::getOrThrow)
        val expectedBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(expectedBulkTransferId)
        val (receivedBulkTransferId, _) =
            ratkoClient.sendBulkTransferCreateRequest(bulkTransferStartRequest(), defaultBlockTimeout)

        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(
            bulkTransferId = receivedBulkTransferId,
            BulkTransferState.DONE,
        )

        val (polledState, _) = ratkoClient.pollBulkTransferState(receivedBulkTransferId, defaultBlockTimeout)
        assertEquals(BulkTransferState.DONE, polledState)
    }
}
