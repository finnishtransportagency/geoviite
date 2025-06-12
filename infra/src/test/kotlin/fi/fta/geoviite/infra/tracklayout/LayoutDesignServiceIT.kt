package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationLogService
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.util.LayoutAssetTable
import fi.fta.geoviite.infra.util.queryOne
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
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
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber())
        designDraftContext.save(referenceLine(trackNumber.id), alignment)
        designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        designDraftContext.save(switch())
        designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        val latestPublication = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        layoutDesignService.update(designId, designForm(designId))
        val afterUpdate = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        assertEquals(latestPublication.map { it.id }, afterUpdate.map { it.id })
    }

    @Test
    fun `updating a design with past design publications creates a new design publication`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = designDraftContext.save(trackNumber()).id
        publicationTestSupportService.publish(designBranch, publicationRequestIds(trackNumbers = listOf(trackNumber)))
        val latestPublication = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        layoutDesignService.update(designId, designForm(designId))
        val afterUpdate = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        assertNotEquals(latestPublication.map { it.id }, afterUpdate.map { it.id })
        val details = publicationLogService.getPublicationDetails(afterUpdate[0].id)
        assertEquals(listOf(), details.trackNumbers)
        assertEquals(listOf(), details.referenceLines)
        assertEquals(listOf(), details.locationTracks)
        assertEquals(listOf(), details.switches)
        assertEquals(listOf(), details.kmPosts)
        assertEquals(designId, details.layoutBranch.branch.designId)
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

        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER))
        val latestPublicationsBeforeEdit = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        layoutDesignService.update(designId, designForm(designId))
        val latestPublicationsAfterEdit = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        assertNotEquals(latestPublicationsBeforeEdit.map { it.id }, latestPublicationsAfterEdit.map { it.id })
        val latestDetails = publicationLogService.getPublicationDetails(latestPublicationsAfterEdit[0].id)
        assertEquals(listOf(), latestDetails.trackNumbers)
    }

    @Test
    fun `cancelling a design creates a cancelling publication and leaves no trace behind in the layout asset tables`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber())
        val referenceLine = designDraftContext.save(referenceLine(trackNumber.id), alignment)
        val locationTrack = designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        val switch = designDraftContext.save(switch())
        val kmPost = designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        publicationTestSupportService.publish(
            designBranch,
            publicationRequestIds(
                trackNumbers = listOf(trackNumber.id),
                referenceLines = listOf(referenceLine.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
                kmPosts = listOf(kmPost.id),
            ),
        )

        deleteDesign(designId)

        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_REFERENCE_LINE))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_SWITCH))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_KM_POST))

        val latestPublication = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        val details = publicationLogService.getPublicationDetails(latestPublication[0].id)
        assertContainsSingleCancelledOfficialObject(details.trackNumbers.map { it.version }, trackNumberDao)
        assertContainsSingleCancelledOfficialObject(details.referenceLines.map { it.version }, referenceLineDao)
        assertContainsSingleCancelledOfficialObject(
            details.locationTracks.map { it.cacheKey.trackVersion },
            locationTrackDao,
        )
        assertContainsSingleCancelledOfficialObject(details.switches.map { it.version }, switchDao)
        assertContainsSingleCancelledOfficialObject(details.kmPosts.map { it.version }, kmPostDao)
    }

    @Test
    fun `deleting a design with no publications deletes all of its drafts`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0)))
        val trackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val trackNumber = designDraftContext.save(trackNumber())
        designDraftContext.save(referenceLine(trackNumber.id), alignment)
        designDraftContext.save(locationTrack(trackNumber.id), trackGeometry)
        designDraftContext.save(switch())
        designDraftContext.save(kmPost(trackNumber.id, KmNumber(123)))

        deleteDesign(designId)

        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_TRACK_NUMBER))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_REFERENCE_LINE))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_SWITCH))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_KM_POST))
    }

    @Test
    fun `deleting a design containing calculated changes to objects not directly changed in the design cancels those as well`() {
        val designBranch = testDBService.createDesignBranch()
        val designId = designBranch.designId
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val alignment = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))

        val trackNumber = mainOfficialContext.save(trackNumber()).id
        mainOfficialContext.save(referenceLine(trackNumber), alignment)
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
        locationTrackDao.insertExternalId(locationTrack, designBranch, Oid("1.2.3.4.5"))
        switchDao.insertExternalId(switch, designBranch, Oid("2.3.4.5.6"))

        val latestPublicationBeforeDelete = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        deleteDesign(designId)
        val latestPublicationAfterDelete = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        assertNotEquals(latestPublicationBeforeDelete.map { it.id }, latestPublicationAfterDelete.map { it.id })
        val details = publicationLogService.getPublicationDetails(latestPublicationAfterDelete[0].id)

        assertContainsSingleCancelledOfficialObject(details.switches.map { it.version }, switchDao)
        assertContainsSingleCancelledOfficialObject(
            details.locationTracks.map { it.cacheKey.trackVersion },
            locationTrackDao,
        )
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_LOCATION_TRACK))
        assertEquals(0, countDesignObjectsInLayoutTable(designId, LayoutAssetTable.LAYOUT_ASSET_SWITCH))
    }

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
