package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoSwitch
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
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
        assertEquals(RatkoClient.RatkoStatus(true), ratkoClient.getRatkoOnlineStatus())

        fakeRatko.isOffline()
        assertEquals(RatkoClient.RatkoStatus(false), ratkoClient.getRatkoOnlineStatus())
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
        val layoutSwitch = switch(externalId = oid, draft = false)
        val basicUpdateSwitch = createRatkoBasicUpdateSwitch(layoutSwitch)
        ratkoClient.updateAssetProperties(RatkoOid(oid), basicUpdateSwitch.properties)
    }

    private fun createRatkoBasicUpdateSwitch(layoutSwitch: TrackLayoutSwitch): RatkoSwitchAsset {
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val oid = (layoutSwitch.externalId ?: throw IllegalArgumentException("No switch external ID")).toString()
        fakeRatko.hasSwitch(ratkoSwitch(oid))
        val ratkoSwitch = ratkoClient.getSwitchAsset(RatkoOid(oid))
        return convertToRatkoSwitch(layoutSwitch, switchStructure, switchOwners.firstOrNull(), ratkoSwitch)
    }

    @Test
    fun shouldGetExternalIdForNewLayoutTrack() {
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.2.3.4.5")
        assertEquals("1.2.3.4.5", ratkoClient.getNewLocationTrackOid()!!.id)
    }

    @Test
    fun shouldGetExternalIdForNewTrackNumber() {
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        assertEquals("1.2.3.4.5", ratkoClient.getNewRouteNumberOid()!!.id)
    }

    @Test
    fun `New bulk transfer can be started`() {
        val split = splitTestDataService.insertSplit().let(splitDao::getOrThrow)

        val expectedBulkTransferId = testDBService.getUnusedBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(expectedBulkTransferId)
        val (receivedBulkTransferId, receivedBulkTransferState) = ratkoClient.startNewBulkTransfer(split)

        assertEquals(expectedBulkTransferId, receivedBulkTransferId)
        assertEquals(BulkTransferState.IN_PROGRESS, receivedBulkTransferState)
    }

    @Test
    fun `Bulk transfer state can be polled`() {
        val split = splitTestDataService.insertSplit().let(splitDao::getOrThrow)

        val expectedBulkTransferId = testDBService.getUnusedBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(expectedBulkTransferId)
        val (receivedBulkTransferId, _) = ratkoClient.startNewBulkTransfer(split)

        assertEquals(BulkTransferState.DONE, ratkoClient.pollBulkTransferState(receivedBulkTransferId))
    }
}
