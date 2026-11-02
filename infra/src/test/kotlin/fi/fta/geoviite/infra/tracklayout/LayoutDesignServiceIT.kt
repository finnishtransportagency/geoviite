package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.DesignHasCompletedItemsException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationLogService
import fi.fta.geoviite.infra.publication.PublicationRequestIds
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.PublishedInDesign
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.queryOne
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutDesignServiceIT
@Autowired
constructor(
    private val layoutDesignService: LayoutDesignService,
    private val layoutDesignDao: LayoutDesignDao,
    private val publicationDao: PublicationDao,
    private val publicationLogService: PublicationLogService,
    private val publicationTestSupportService: PublicationTestSupportService,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val kmPostService: LayoutKmPostService,
    private val operationalPointService: OperationalPointService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val switchDao: LayoutSwitchDao,
    private val kmPostDao: LayoutKmPostDao,
) : DBTestBase() {
    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `updating a design with no publications does not cause a design publication`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val referenceLineGeometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber(), referenceLineGeometry)
        designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        designDraftContext.save(switch())
        designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        val latestPublication =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        layoutDesignService.update(designId, designForm(designId))
        val afterUpdate = publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        assertEquals(latestPublication.map { it.id }, afterUpdate.map { it.id })
    }

    @Test
    fun `updating a design with past design publications creates a new design publication`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = designDraftContext.save(trackNumber()).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber)))
        val latestPublication =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        layoutDesignService.update(designId, designForm(designId))
        val afterUpdate = publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        assertNotEquals(latestPublication.map { it.id }, afterUpdate.map { it.id })
        val details = publicationLogService.getPublicationDetails(afterUpdate[0].id)
        assertEquals(listOf(), details.trackNumbers)
        assertEquals(listOf(), details.locationTracks)
        assertEquals(listOf(), details.switches)
        assertEquals(listOf(), details.kmPosts)
        assertEquals(designId, details.layoutBranch.branch.designId)
    }

    @Test
    fun `design publication caused by an update refers to the updated design version`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = designDraftContext.save(trackNumber()).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber)))

        val newName = LayoutDesignName("design name after update")
        layoutDesignService.update(designId, designForm(designId).copy(name = newName))

        val publication = publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)[0]
        val publishedIn = publication.layoutBranch as PublishedInDesign
        assertEquals(newName, layoutDesignDao.fetchVersion(RowVersion(designId, publishedIn.designVersion)).name)
        assertEquals(PublicationCause.LAYOUT_DESIGN_CHANGE, publication.cause)
    }

    @Test
    fun `updating a previously published design that no longer contains any assets still causes a design publication`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val trackNumber = designDraftContext.save(trackNumber()).id

        val objectIds = publicationRequestIds(trackNumbers = listOf(trackNumber))
        publicationTestSupportService.publish(designBranch, objectIds)
        trackNumberService.cancel(designBranch, trackNumber)
        publicationTestSupportService.publish(designBranch, objectIds)

        val latestPublicationsBeforeEdit =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        layoutDesignService.update(designId, designForm(designId))
        val latestPublicationsAfterEdit =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        assertNotEquals(latestPublicationsBeforeEdit.map { it.id }, latestPublicationsAfterEdit.map { it.id })
        val latestDetails = publicationLogService.getPublicationDetails(latestPublicationsAfterEdit[0].id)
        assertEquals(listOf(), latestDetails.trackNumbers)
    }

    @Test
    fun `cancelling a design creates two publications (design delete and design cancellation)`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val referenceLineGeometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber(), referenceLineGeometry)
        val locationTrack = designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        val switch = designDraftContext.save(switch())
        val kmPost = designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        publicationTestSupportService.publish(
            designBranch,
            publicationRequestIds(
                trackNumbers = listOf(trackNumber.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
                kmPosts = listOf(kmPost.id),
            ),
        )

        deleteDesign(designId)

        val latestPublication =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 2)
        val designCancellationDetails = publicationLogService.getPublicationDetails(latestPublication[0].id)
        val designDeleteDetails = publicationLogService.getPublicationDetails(latestPublication[1].id)

        // Design delete should only contain design metadata change, no assets
        assertEquals(PublicationCause.LAYOUT_DESIGN_DELETE, designDeleteDetails.cause)
        assertEquals(0, designDeleteDetails.trackNumbers.size)
        assertEquals(0, designDeleteDetails.locationTracks.size)
        assertEquals(0, designDeleteDetails.switches.size)
        assertEquals(0, designDeleteDetails.kmPosts.size)
        assertEquals(null, designDeleteDetails.split)

        // Design cancellation should contain assets
        assertEquals(PublicationCause.LAYOUT_DESIGN_CANCELLATION, designCancellationDetails.cause)
        assertContainsSingleCancelledOfficialObject(
            designCancellationDetails.trackNumbers.map { it.version },
            trackNumberDao,
        )
        assertContainsSingleCancelledOfficialObject(
            designCancellationDetails.locationTracks.map { it.version },
            locationTrackDao,
        )
        assertContainsSingleCancelledOfficialObject(designCancellationDetails.switches.map { it.version }, switchDao)
        assertContainsSingleCancelledOfficialObject(designCancellationDetails.kmPosts.map { it.version }, kmPostDao)
    }

    @Test
    fun `deleting a design with no publications deletes all of its drafts`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val referenceLineGeometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber(), referenceLineGeometry)
        designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        designDraftContext.save(switch())
        designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        deleteDesign(designId)

        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_SWITCH))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_KM_POST))
    }

    @Test
    fun `deleting a design containing calculated changes to objects not directly changed in the design cancels those as well`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val referenceLineGeometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0)))

        val trackNumber = mainOfficialContext.save(trackNumber(), referenceLineGeometry).id
        val switch =
            mainOfficialContext
                .save(
                    switch(
                        joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 0.0), null))
                    )
                )
                .id
        val locationTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                            endInnerSwitch = switchLinkYV(switch, 1),
                        )
                    ),
                )
                .id
        designDraftContext.save(mainOfficialContext.fetch(trackNumber)!!)
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber)))

        // at the time of writing this test, we don't actually have support for updating designs
        // on inheriting calculated changes from main; but once we do, they will be identified by
        // those objects having an extId in the design
        testDBService.generateOid(locationTrack, designBranch)
        testDBService.generateOid(switch, designBranch)

        val latestPublicationBeforeDelete =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        deleteDesign(designId)
        val latestPublicationAfterDelete =
            publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)
        assertNotEquals(latestPublicationBeforeDelete.map { it.id }, latestPublicationAfterDelete.map { it.id })
        val details = publicationLogService.getPublicationDetails(latestPublicationAfterDelete[0].id)

        assertContainsSingleCancelledOfficialObject(details.switches.map { it.version }, switchDao)
        assertContainsSingleCancelledOfficialObject(details.locationTracks.map { it.version }, locationTrackDao)
    }

    @Test
    fun `deleting a design with a completed track number is rejected`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = testDBService.testContext(designBranch, PublicationState.DRAFT).save(trackNumber()).id
        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber))) {
            trackNumberService.mergeToMainBranch(designBranch, trackNumber)
        }

        assertDeletionRejected(designBranch, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER)
    }

    @Test
    fun `deleting a design with a completed location track is rejected`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val locationTrack =
            testDBService
                .testContext(designBranch, PublicationState.DRAFT)
                .save(locationTrack(trackNumber), trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0))))
                .id
        completeInDesign(designBranch, publicationRequestIds(locationTracks = listOf(locationTrack))) {
            locationTrackService.mergeToMainBranch(designBranch, locationTrack)
        }

        assertDeletionRejected(designBranch, LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK)
    }

    @Test
    fun `deleting a design with a completed switch is rejected`() {
        val designBranch = testDBService.createDesignBranch()
        val switch = testDBService.testContext(designBranch, PublicationState.DRAFT).save(switch()).id
        completeInDesign(designBranch, publicationRequestIds(switches = listOf(switch))) {
            switchService.mergeToMainBranch(designBranch, switch)
        }

        assertDeletionRejected(designBranch, LayoutAssetTable.LAYOUT_ASSET_SWITCH)
    }

    @Test
    fun `deleting a design with a completed km post is rejected`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val kmPost =
            testDBService.testContext(designBranch, PublicationState.DRAFT).save(kmPost(trackNumber, KmNumber(1))).id
        completeInDesign(designBranch, publicationRequestIds(kmPosts = listOf(kmPost))) {
            kmPostService.mergeToMainBranch(designBranch, kmPost)
        }

        assertDeletionRejected(designBranch, LayoutAssetTable.LAYOUT_ASSET_KM_POST)
    }

    @Test
    fun `deleting a design with a completed operational point is rejected`() {
        val designBranch = testDBService.createDesignBranch()
        val operationalPoint =
            testDBService.testContext(designBranch, PublicationState.DRAFT).save(operationalPoint()).id
        completeInDesign(designBranch, publicationRequestIds(operationalPoints = listOf(operationalPoint))) {
            operationalPointService.mergeToMainBranch(designBranch, operationalPoint)
        }

        assertDeletionRejected(designBranch, LayoutAssetTable.LAYOUT_ASSET_OPERATIONAL_POINT)
    }

    @Test
    fun `deleting a design with only open and cancelled items still works`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val open = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        val cancelled = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        publicationTestSupportService.publish(
            designBranch,
            publicationRequestIds(trackNumbers = listOf(open, cancelled)),
        )
        trackNumberService.cancel(designBranch, cancelled)
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(cancelled)))

        deleteDesign(designBranch.designId)

        assertEquals(DesignState.DELETED, layoutDesignDao.fetch(designBranch.designId).designState)
        assertEquals(
            listOf("CANCELLED", "CANCELLED"),
            designAssetStates(designBranch.designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER),
        )
    }

    @Test
    fun `cancelling single items is still allowed after a partial merge`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val completed = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        val toCancel = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(toCancel)))
        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(completed))) {
            trackNumberService.mergeToMainBranch(designBranch, completed)
        }

        trackNumberService.cancel(designBranch, toCancel)
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(toCancel)))

        assertEquals(
            listOf("CANCELLED", "COMPLETED"),
            designAssetStates(designBranch.designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER),
        )
    }

    @Test
    fun `hasCompletedItems is true only after an item is merged to main`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val open = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        val completed = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(open)))

        assertFalse(layoutDesignService.hasCompletedItems(designBranch.designId))

        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(completed))) {
            trackNumberService.mergeToMainBranch(designBranch, completed)
        }

        assertTrue(layoutDesignService.hasCompletedItems(designBranch.designId))
    }

    @Test
    fun `design becomes COMPLETED when the last open item is merged to main`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val first = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        val second = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(second)))

        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(first))) {
            trackNumberService.mergeToMainBranch(designBranch, first)
        }
        assertEquals(DesignState.ACTIVE, layoutDesignDao.fetch(designBranch.designId).designState)

        trackNumberService.mergeToMainBranch(designBranch, second)
        publicationTestSupportService.publish(LayoutBranch.main, publicationRequestIds(trackNumbers = listOf(second)))
        assertEquals(DesignState.COMPLETED, layoutDesignDao.fetch(designBranch.designId).designState)
    }

    @Test
    fun `design becomes COMPLETED when the last open item is cancelled and something was completed`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val completed = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        val toCancel = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(toCancel)))
        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(completed))) {
            trackNumberService.mergeToMainBranch(designBranch, completed)
        }
        assertEquals(DesignState.ACTIVE, layoutDesignDao.fetch(designBranch.designId).designState)

        trackNumberService.cancel(designBranch, toCancel)
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(toCancel)))

        assertEquals(DesignState.COMPLETED, layoutDesignDao.fetch(designBranch.designId).designState)
    }

    @Test
    fun `design with only cancelled items stays ACTIVE`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = testDBService.testContext(designBranch, PublicationState.DRAFT).save(trackNumber()).id
        val ids = publicationRequestIds(trackNumbers = listOf(trackNumber))
        publicationTestSupportService.publish(designBranch, ids)
        trackNumberService.cancel(designBranch, trackNumber)
        publicationTestSupportService.publish(designBranch, ids)

        assertEquals(DesignState.ACTIVE, layoutDesignDao.fetch(designBranch.designId).designState)
    }

    @Test
    fun `design with an unpublished draft stays ACTIVE after its published items are merged`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val completed = designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
        designDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber()))
        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(completed))) {
            trackNumberService.mergeToMainBranch(designBranch, completed)
        }

        assertEquals(DesignState.ACTIVE, layoutDesignDao.fetch(designBranch.designId).designState)
    }

    @Test
    fun `completing a design creates a design change publication referring to the completed design version`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val trackNumber = testDBService.testContext(designBranch, PublicationState.DRAFT).save(trackNumber()).id
        completeInDesign(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber))) {
            trackNumberService.mergeToMainBranch(designBranch, trackNumber)
        }

        val publication = publicationDao.fetchLatestPublications(onlyBranchType = LayoutBranchType.DESIGN, count = 1)[0]
        val publishedIn = publication.layoutBranch as PublishedInDesign
        assertEquals(PublicationCause.LAYOUT_DESIGN_CHANGE, publication.cause)
        assertEquals(
            DesignState.COMPLETED,
            layoutDesignDao.fetchVersion(RowVersion(designId, publishedIn.designVersion)).designState,
        )
    }

    private fun completeInDesign(designBranch: DesignBranch, ids: PublicationRequestIds, mergeToMain: () -> Unit) {
        publicationTestSupportService.publish(designBranch, ids)
        mergeToMain()
        publicationTestSupportService.publish(LayoutBranch.main, ids)
    }

    private fun assertDeletionRejected(designBranch: DesignBranch, table: LayoutAssetTable) {
        val designId = designBranch.designId
        val stateBefore = layoutDesignDao.fetch(designId).designState
        assertThrows<DesignHasCompletedItemsException> { deleteDesign(designId) }
        assertEquals(stateBefore, layoutDesignDao.fetch(designId).designState)
        assertEquals(listOf("COMPLETED"), designAssetStates(designId, table))
    }

    private fun designAssetStates(designId: IntId<LayoutDesign>, table: LayoutAssetTable): List<String> =
        jdbc
            .query(
                "select design_asset_state from ${table.fullName} where design_id = :design_id and not draft",
                mapOf("design_id" to designId.intValue),
            ) { rs, _ ->
                rs.getString("design_asset_state")
            }
            .sorted()

    private fun <T : LayoutAsset<T>> assertContainsSingleCancelledOfficialObject(
        versions: List<LayoutRowVersion<T>>,
        reader: LayoutAssetReader<T>,
    ) {
        assertEquals(1, versions.size)
        val asset = reader.fetch(versions[0])
        assertTrue(asset.isCancelled)
        assertTrue(asset.isOfficial)
    }

    private fun countDesignObjectsInLayoutTable(designId: IntId<LayoutDesign>, table: LayoutAssetTable): Int {
        return jdbc.queryOne(
            "select count(*) c from ${table.fullName} where design_id = :design_id",
            mapOf("design_id" to designId.intValue),
        ) { rs, _ ->
            rs.getInt("c")
        }
    }

    private fun deleteDesign(designId: IntId<LayoutDesign>) {
        layoutDesignService.update(designId, designForm(designId).copy(designState = DesignState.DELETED))
    }

    private fun designForm(designId: IntId<LayoutDesign>): LayoutDesignSaveRequest {
        val design = layoutDesignDao.fetch(designId)
        return LayoutDesignSaveRequest(
            name = design.name,
            designState = design.designState,
            estimatedCompletion = design.estimatedCompletion,
        )
    }
}
