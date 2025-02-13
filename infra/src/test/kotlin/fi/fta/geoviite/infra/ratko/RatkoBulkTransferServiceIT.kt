package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.matchers.Times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoBulkTransferServiceIT
@Autowired
constructor(
    private val fakeRatkoService: FakeRatkoService,
    private val splitDao: SplitDao,
    private val publicationDao: PublicationDao,
    private val splitTestDataService: SplitTestDataService,
    private val ratkoPushDao: RatkoPushDao,
    private val ratkoBulkTransferService: RatkoBulkTransferService,
) : DBTestBase() {

    lateinit var fakeRatko: FakeRatko

    @BeforeEach
    fun cleanup() {
        splitTestDataService.clearSplits()
        jdbc.execute("update integrations.ratko_push set status = 'SUCCESSFUL' where status != 'SUCCESSFUL'") {
            it.execute()
        }
    }

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
    fun `Bulk transfers should not be started for any unpublished splits`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitId = splitTestDataService.insertSplit()
        ratkoBulkTransferService.manageRatkoBulkTransfers(LayoutBranch.main)
        val splitAfterManagerRun = splitDao.getOrThrow(splitId)

        assertEquals(null, splitAfterManagerRun.publicationId)
        assertEquals(null, splitAfterManagerRun.bulkTransfer?.ratkoBulkTransferId)
        assertEquals(null, splitAfterManagerRun.bulkTransfer?.state)
    }

    @Test
    fun `Bulk transfer manager should start and poll bulk transfers for pending & published splits`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitId = splitTestDataService.insertGeocodableSplit()
        val publicationId =
            publicationDao.createPublication(
                LayoutBranch.main,
                FreeTextWithNewLines.of("test: bulk transfer to in progress"),
                PublicationCause.MANUAL,
            )

        splitDao.updateSplit(splitId = splitId, publicationId = publicationId)
        splitDao.insertBulkTransfer(splitId)

        val someBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(someBulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterBulkTransferStart = splitDao.getOrThrow(splitId)
        assertEquals(BulkTransferState.CREATED, splitAfterBulkTransferStart.bulkTransfer?.state)
        assertEquals(someBulkTransferId, splitAfterBulkTransferStart.bulkTransfer?.ratkoBulkTransferId)

        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(
            bulkTransferId = someBulkTransferId,
            BulkTransferState.IN_PROGRESS,
        )
        ratkoBulkTransferService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterPoll = splitDao.getOrThrow(splitId)
        assertEquals(BulkTransferState.IN_PROGRESS, splitAfterPoll.bulkTransfer?.state)
        assertEquals(someBulkTransferId, splitAfterPoll.bulkTransfer?.ratkoBulkTransferId)
    }

    @Test
    fun `Bulk transfer should not be started when another bulk transfer is in progress`() {
        val someBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        splitTestDataService.insertPublishedSplit().let { splitId ->
            splitDao.updateBulkTransfer(
                splitId = splitId,
                bulkTransferState = BulkTransferState.IN_PROGRESS,
                ratkoBulkTransferId = someBulkTransferId,
            )
        }

        val pendingSplitId = splitTestDataService.insertPublishedSplit()

        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(someBulkTransferId, BulkTransferState.IN_PROGRESS)
        ratkoBulkTransferService.manageRatkoBulkTransfers(LayoutBranch.main)

        val pendingSplitAfterBulkTransferProcessing = splitDao.getOrThrow(pendingSplitId)
        assertEquals(null, requireNotNull(pendingSplitAfterBulkTransferProcessing.bulkTransfer).ratkoBulkTransferId)
        assertEquals(BulkTransferState.PENDING, pendingSplitAfterBulkTransferProcessing.bulkTransfer?.state)
    }

    @Test
    fun `Assigns bulk transfer temporary failure status when receiving HTTP 502, 503, 504`() {
        val httpStatusTests = listOf(502, 503, 504)
        val branch = LayoutBranch.main

        httpStatusTests.forEach { httpStatus ->
            val splitId = splitTestDataService.insertPublishedGeocodableSplit()
            val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

            fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
            ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
            assertEquals(bulkTransferId, splitDao.getOrThrow(splitId).bulkTransfer?.ratkoBulkTransferId)

            fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(bulkTransferId, BulkTransferState.IN_PROGRESS)
            ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
            assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

            fakeRatko.respondsToBulkTransferPollWithHttpStatus(bulkTransferId, httpStatus)
            ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
            assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

            // Consider this one finished so that the next split bulk transfer can be started.
            splitDao.updateBulkTransfer(splitId = splitId, bulkTransferState = BulkTransferState.DONE)
        }
    }

    @Test
    fun `Retries temporarily failed bulk transfer`() {
        val branch = LayoutBranch.main

        val statesToRetry = listOf(BulkTransferState.PENDING, BulkTransferState.CREATED, BulkTransferState.IN_PROGRESS)

        statesToRetry.forEach { bulkTransferState ->
            val splitId = splitTestDataService.insertPublishedGeocodableSplit()
            val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

            if (bulkTransferState == BulkTransferState.PENDING) {
                splitDao.updateBulkTransfer(
                    splitId = splitId,
                    bulkTransferState = bulkTransferState,
                    temporaryFailure = true,
                )

                fakeRatko.respondsToBulkTransferCreateWithHttpStatus(502)
                ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

                fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
                ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                splitDao.getOrThrow(splitId).let { split ->
                    assertEquals(false, split.bulkTransfer?.temporaryFailure)
                    assertEquals(bulkTransferId, split.bulkTransfer?.ratkoBulkTransferId)
                }
            } else {
                splitDao.updateBulkTransfer(
                    splitId = splitId,
                    bulkTransferState = bulkTransferState,
                    ratkoBulkTransferId = bulkTransferId,
                    temporaryFailure = true,
                )

                fakeRatko.respondsToBulkTransferPollWithHttpStatus(bulkTransferId, 502)
                ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

                fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(
                    bulkTransferId,
                    BulkTransferState.IN_PROGRESS,
                )
                ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)
            }

            splitDao.updateBulkTransfer(splitId = splitId, bulkTransferState = BulkTransferState.DONE)
        }
    }

    @Test
    fun `Assign bulk transfer temporary failure when Ratko times out, but reset it when the connection works again`() {
        val branch = LayoutBranch.main

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()
        assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

        fakeRatko.delayedOkPostResponse(BULK_TRANSFER_CREATE_PATH, delay = Duration.ofSeconds(2))
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch, timeout = Duration.ofSeconds(1))
        assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
        splitDao.getOrThrow(splitId).let { split ->
            assertEquals(false, split.bulkTransfer?.temporaryFailure)
            assertEquals(bulkTransferId, split.bulkTransfer?.ratkoBulkTransferId)
        }

        fakeRatko.delayedOkGetResponse(bulkTransferPollPath(bulkTransferId), delay = Duration.ofSeconds(2))
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch, timeout = Duration.ofSeconds(1))
        splitDao.getOrThrow(splitId).let { split ->
            assertEquals(true, split.bulkTransfer?.temporaryFailure)
            assertEquals(bulkTransferId, split.bulkTransfer?.ratkoBulkTransferId)
        }

        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(bulkTransferId, BulkTransferState.IN_PROGRESS)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
        splitDao.getOrThrow(splitId).let { split ->
            assertEquals(false, split.bulkTransfer?.temporaryFailure)
            assertEquals(bulkTransferId, split.bulkTransfer?.ratkoBulkTransferId)
            assertEquals(BulkTransferState.IN_PROGRESS, split.bulkTransfer?.state)
        }
    }

    @Test
    fun `Assigns bulk transfer FAILED status when receiving HTTP 400, 500 and others`() {
        val branch = LayoutBranch.main

        val httpStatusCodesToTest = listOf(400, 418, 429, 500)
        val statesToTest = listOf(BulkTransferState.PENDING, BulkTransferState.CREATED, BulkTransferState.IN_PROGRESS)

        httpStatusCodesToTest.forEach { httpStatus ->
            statesToTest.forEach { bulkTransferState ->
                val splitId = splitTestDataService.insertPublishedGeocodableSplit()
                val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

                if (bulkTransferState == BulkTransferState.PENDING) {
                    fakeRatko.respondsToBulkTransferCreateWithHttpStatus(httpStatus)
                    ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                    assertEquals(BulkTransferState.FAILED, splitDao.getOrThrow(splitId).bulkTransfer?.state)
                } else {
                    splitDao.updateBulkTransfer(
                        splitId = splitId,
                        bulkTransferState = bulkTransferState,
                        ratkoBulkTransferId = bulkTransferId,
                    )

                    fakeRatko.respondsToBulkTransferPollWithHttpStatus(bulkTransferId, httpStatus)
                    ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
                    assertEquals(BulkTransferState.FAILED, splitDao.getOrThrow(splitId).bulkTransfer?.state)
                }

                // Finish the current test case so that a new split can be created and tested
                splitDao.updateBulkTransfer(splitId, bulkTransferState = BulkTransferState.DONE)
            }
        }
    }

    @Test
    fun `Bulk transfer with FAILED status should not poll or create new bulk transfers`() {
        val branch = LayoutBranch.main

        val failedSplitId = splitTestDataService.insertPublishedSplit()
        val failedBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        splitDao.updateBulkTransfer(
            failedSplitId,
            bulkTransferState = BulkTransferState.FAILED,
            ratkoBulkTransferId = failedBulkTransferId,
        )
        assertEquals(BulkTransferState.FAILED, splitDao.getOrThrow(failedSplitId).bulkTransfer?.state)

        val anotherSplitId = splitTestDataService.insertPublishedSplit()
        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(anotherSplitId).bulkTransfer?.state)

        val newBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(newBulkTransferId) // Should not be called though.
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

        assertEquals(BulkTransferState.FAILED, splitDao.getOrThrow(failedSplitId).bulkTransfer?.state)
        assertEquals(failedBulkTransferId, splitDao.getOrThrow(failedSplitId).bulkTransfer?.ratkoBulkTransferId)

        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(anotherSplitId).bulkTransfer?.state)
        assertEquals(null, splitDao.getOrThrow(anotherSplitId).bulkTransfer?.ratkoBulkTransferId)
    }

    @Test
    fun `Bulk transfer status values are updated based on poll response values`() {
        val branch = LayoutBranch.main

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()
        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(splitId).bulkTransfer?.state)

        splitDao.getOrThrow(splitId).let { split ->
            assertEquals(null, split.bulkTransfer?.assetsMoved)
            assertEquals(null, split.bulkTransfer?.assetsTotal)

            assertEquals(null, split.bulkTransfer?.trexAssetsRemaining)
            assertEquals(null, split.bulkTransfer?.trexAssetsTotal)
        }

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

        val values = listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8))

        values.forEach { valueList ->
            fakeRatko.respondsToBulkTransferPoll(
                bulkTransferId = bulkTransferId,
                response =
                    bulkTransferPollResponse(
                        bulkTransferId = bulkTransferId,
                        startTime = Instant.now(),
                        locationTrackChangeAssetsAmount = valueList[0],
                        assetsToMove = valueList[1],
                        remainingTrexAssets = valueList[2],
                        trexAssets = valueList[3],
                    ),
            )

            ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

            splitDao.getOrThrow(splitId).let { split ->
                assertEquals(valueList[0], split.bulkTransfer?.assetsMoved)
                assertEquals(valueList[1], split.bulkTransfer?.assetsTotal)

                assertEquals(valueList[2], split.bulkTransfer?.trexAssetsRemaining)
                assertEquals(valueList[3], split.bulkTransfer?.trexAssetsTotal)
            }
        }
    }

    @Test
    fun `Setting a failed bulk transfer back to pending will cause it to be retried and not start another one`() {
        val branch = LayoutBranch.main

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()
        val anotherSplitId = splitTestDataService.insertPublishedGeocodableSplit()
        splitDao.updateBulkTransfer(splitId, bulkTransferState = BulkTransferState.FAILED)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

        // Simulates a user action.
        splitDao.updateBulkTransfer(splitId, bulkTransferState = BulkTransferState.PENDING)

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

        assertEquals(BulkTransferState.CREATED, splitDao.getOrThrow(splitId).bulkTransfer?.state)
        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(anotherSplitId).bulkTransfer?.state)
    }

    @Test
    fun `Bulk transfer expedited starting works and marks the bulk transfer to be in progress`() {
        val branch = LayoutBranch.main

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()
        splitDao.updateBulkTransfer(splitId = splitId, expeditedStart = true)
        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(splitId).bulkTransfer?.state)

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
        assertEquals(BulkTransferState.CREATED, splitDao.getOrThrow(splitId).bulkTransfer?.state)
        assertEquals(bulkTransferId, splitDao.getOrThrow(splitId).bulkTransfer?.ratkoBulkTransferId)

        fakeRatko.acceptsBulkTransferExpeditedStart(bulkTransferId, times = Times.exactly(1))
        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(bulkTransferId, BulkTransferState.IN_PROGRESS)
        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)
        assertEquals(BulkTransferState.IN_PROGRESS, splitDao.getOrThrow(splitId).bulkTransfer?.state)
    }

    @Test
    fun `Bulk transfer expedited starting should not be tried more than once`() {
        val branch = LayoutBranch.main

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()
        splitDao.updateBulkTransfer(splitId = splitId, expeditedStart = true)

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()

        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        fakeRatko.acceptsBulkTransferExpeditedStart(bulkTransferId, times = Times.exactly(1))

        // Polling causes a temporary error which does not update the state of the bulk transfer
        // based on the received data during the poll request
        fakeRatko.respondsToBulkTransferPollWithHttpStatus(bulkTransferId, 502, times = Times.exactly(2))

        (0..2).forEach { _ -> ratkoBulkTransferService.manageRatkoBulkTransfers(branch) }

        assertEquals(BulkTransferState.IN_PROGRESS, splitDao.getOrThrow(splitId).bulkTransfer?.state)
    }

    @Test
    fun `New bulk transfer should not be created if the previous Ratko push failed`() {
        val branch = LayoutBranch.main

        val somePublication =
            publicationDao.createPublication(
                LayoutBranch.main,
                FreeTextWithNewLines.of("some publication"),
                PublicationCause.MANUAL,
            )

        ratkoPushDao.startPushing(listOf(somePublication)).let { ratkoPushId ->
            ratkoPushDao.updatePushStatus(ratkoPushId, RatkoPushStatus.FAILED)
        }

        val splitId = splitTestDataService.insertPublishedGeocodableSplit()

        val bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferId)
        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(bulkTransferId, BulkTransferState.IN_PROGRESS)

        ratkoBulkTransferService.manageRatkoBulkTransfers(branch)

        assertEquals(BulkTransferState.PENDING, splitDao.getOrThrow(splitId).bulkTransfer?.state)
    }

    // TODO
    @Test fun `In progress bulk transfer should be polled even if the previous Ratko push failed`() {}

    // TODO Move to SplitDao or elsewhere
    @Test
    fun `Bulk transfer expedited start can be toggled`() {
        val splitId = splitTestDataService.insertPublishedSplit()
        assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.expeditedStart)

        splitDao.updateBulkTransfer(splitId = splitId, expeditedStart = true)
        assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.expeditedStart)

        splitDao.updateBulkTransfer(splitId = splitId, expeditedStart = false)
        assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.expeditedStart)
    }

    @Test
    fun `Bulk transfer temporary failure can be toggled`() {
        val splitId = splitTestDataService.insertPublishedSplit()
        assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

        splitDao.updateBulkTransfer(splitId = splitId, temporaryFailure = true)
        assertEquals(true, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)

        splitDao.updateBulkTransfer(splitId = splitId, temporaryFailure = false)
        assertEquals(false, splitDao.getOrThrow(splitId).bulkTransfer?.temporaryFailure)
    }

    @Test
    fun `Bulk transfer should be started on the earliest unfinished split`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitIds =
            (0..2).map { index ->
                val splitId = splitTestDataService.insertGeocodableSplit()
                splitDao
                    .updateSplit(
                        splitId = splitId,
                        publicationId =
                            publicationDao.createPublication(
                                LayoutBranch.main,
                                FreeTextWithNewLines.of("pending bulk transfer $index"),
                                PublicationCause.MANUAL,
                            ),
                    )
                    .id
                    .also { splitDao.insertBulkTransfer(splitId) }
            }

        val someBulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(someBulkTransferId)
        ratkoBulkTransferService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterSecondExpectedBulkTransferStart = splitDao.getOrThrow(splitIds[0])
        assertEquals(someBulkTransferId, splitAfterSecondExpectedBulkTransferStart.bulkTransfer?.ratkoBulkTransferId)
        assertEquals(BulkTransferState.CREATED, splitAfterSecondExpectedBulkTransferStart.bulkTransfer?.state)
    }

    @Test
    fun `Polling bulk transfer state updates should not change the state of any splits that are not in progress`() {
        val splitsAndExpectedBulkTransferStates =
            BulkTransferState.entries
                .filter { state -> state != BulkTransferState.IN_PROGRESS }
                .map { bulkTransferState ->
                    val splitId = splitTestDataService.insertSplit()
                    splitDao.updateSplit(
                        splitId = splitId,
                        publicationId =
                            publicationDao.createPublication(
                                LayoutBranch.main,
                                FreeTextWithNewLines.of("pending bulk transfer, splitId=$splitId"),
                                PublicationCause.MANUAL,
                            ),
                    )
                    splitDao.insertBulkTransfer(splitId)

                    when (bulkTransferState) {
                        BulkTransferState.PENDING -> {
                            splitDao.updateBulkTransfer(splitId = splitId, bulkTransferState = bulkTransferState)
                        }

                        else -> {
                            splitDao.updateSplit(
                                splitId = splitId,
                                publicationId =
                                    publicationDao.createPublication(
                                        LayoutBranch.main,
                                        FreeTextWithNewLines.of("testing $bulkTransferState"),
                                        PublicationCause.MANUAL,
                                    ),
                            )

                            splitDao.updateBulkTransfer(
                                splitId = splitId,
                                bulkTransferState = bulkTransferState,
                                ratkoBulkTransferId = testDBService.getUnusedRatkoBulkTransferId(),
                            )
                        }
                    }

                    splitId to bulkTransferState
                }

        splitsAndExpectedBulkTransferStates.forEach { (splitId, _) ->
            splitDao.getOrThrow(splitId).let { split ->
                when (requireNotNull(split.bulkTransfer).state) {
                    BulkTransferState.PENDING ->
                        fakeRatko.acceptsNewBulkTransferGivingItId(
                            bulkTransferId = testDBService.getUnusedRatkoBulkTransferId()
                        )
                    BulkTransferState.CREATED ->
                        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(
                            requireNotNull(split.bulkTransfer?.ratkoBulkTransferId),
                            BulkTransferState.CREATED,
                        )
                    else ->
                        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(
                            requireNotNull(split.bulkTransfer?.ratkoBulkTransferId),
                            BulkTransferState.IN_PROGRESS,
                        )
                }

                ratkoBulkTransferService.pollBulkTransferStateUpdate(split, timeout = defaultBlockTimeout)
            }
        }

        splitsAndExpectedBulkTransferStates.forEach { (splitId, expectedBulkTransferState) ->
            assertEquals(expectedBulkTransferState, splitDao.getOrThrow(splitId).bulkTransfer?.state)
        }
    }
}
