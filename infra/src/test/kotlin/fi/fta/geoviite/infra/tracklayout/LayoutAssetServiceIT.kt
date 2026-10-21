package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAssetServiceIT
@Autowired
constructor(
    private val layoutTrackNumberService: LayoutTrackNumberService,
    private val layoutLocationTrackService: LocationTrackService,
    private val layoutSwitchService: LayoutSwitchService,
    private val layoutKmPostService: LayoutKmPostService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val switchDao: LayoutSwitchDao,
    private val kmPostDao: LayoutKmPostDao,
    private val operationalPointDao: OperationalPointDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `objects with alignments can be merged to main-draft and then deleted from main-draft`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)

        val trackNumberId = designOfficialContext.save(trackNumber(), referenceLineGeometry()).id
        val locationTrackId = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)

        layoutLocationTrackService.deleteDraft(LayoutBranch.main, locationTrackId)

        assertNull(mainDraftContext.fetch(locationTrackId))
    }

    @Test
    fun `objects with a main-draft copy can still be edited in their design`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumberId = designOfficialContext.save(trackNumber(TrackNumber("original"))).id
        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        designDraftContext.save(
            asDesignDraft(
                designOfficialContext.fetch(trackNumberId)!!.copy(number = TrackNumber("edited")),
                someDesignBranch.designId,
            )
        )
        assertEquals("original", designOfficialContext.fetch(trackNumberId)!!.number.toString())
        assertEquals("edited", designDraftContext.fetch(trackNumberId)!!.number.toString())
    }

    @Test
    fun `object in design-official can be merged to main-draft`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)

        val trackNumberId = designOfficialContext.save(trackNumber(), referenceLineGeometry()).id
        val locationTrackId = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id
        val switchId = designOfficialContext.save(switch()).id
        val kmPostId = designOfficialContext.save(kmPost(trackNumberId, KmNumber(123))).id

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertEquals(trackNumberId, mainDraftContext.fetch(trackNumberId)!!.id)
        assertEquals(locationTrackId, mainDraftContext.fetch(locationTrackId)!!.id)
        assertEquals(switchId, mainDraftContext.fetch(switchId)!!.id)
        assertEquals(kmPostId, mainDraftContext.fetch(kmPostId)!!.id)

        assertEquals(trackNumberId, designOfficialContext.fetch(trackNumberId)!!.id)
        assertEquals(locationTrackId, designOfficialContext.fetch(locationTrackId)!!.id)
        assertEquals(switchId, designOfficialContext.fetch(switchId)!!.id)
        assertEquals(kmPostId, designOfficialContext.fetch(kmPostId)!!.id)
    }

    @Test
    fun `existing main-draft is overridden when merging object from design-official`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumberId = mainOfficialContext.save(trackNumber(), referenceLineGeometry()).id
        val locationTrackId = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id
        val switchId = mainOfficialContext.save(switch()).id
        val kmPostId = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(123))).id

        val mainDraftTrackNumber =
            mainDraftContext.save(
                asMainDraft(mainOfficialContext.fetch(trackNumberId)!!)
                    .copy(description = TrackNumberDescription("edited in main"))
            )
        val mainDraftLocationTrack =
            mainDraftContext.save(
                asMainDraft(mainOfficialContext.fetch(locationTrackId)!!).copy(name = AlignmentName("edited in main"))
            )
        val mainDraftSwitch =
            mainDraftContext.save(
                asMainDraft(mainOfficialContext.fetch(switchId)!!).copy(name = SwitchName("edited in main"))
            )
        val mainDraftKmPost =
            mainDraftContext.save(asMainDraft(mainOfficialContext.fetch(kmPostId)!!).copy(kmNumber = KmNumber(123)))

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext
                        .fetch(trackNumberId)!!
                        .copy(
                            description = TrackNumberDescription("edited in design"),
                            startAddress = TrackMeter("0123+0000.000"),
                        ),
                    someDesignBranch.designId,
                )
            )
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext.fetch(locationTrackId)!!.copy(name = AlignmentName("edited in design")),
                    someDesignBranch.designId,
                )
            )
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext.fetch(switchId)!!.copy(name = SwitchName("edited in design")),
                    someDesignBranch.designId,
                )
            )
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext.fetch(kmPostId)!!.copy(kmNumber = KmNumber(321)),
                    someDesignBranch.designId,
                )
            )
        )

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertEquals(mainDraftTrackNumber.rowId, mainDraftContext.fetch(trackNumberId)!!.version!!.rowId)
        assertEquals(mainDraftLocationTrack.rowId, mainDraftContext.fetch(locationTrackId)!!.version!!.rowId)
        assertEquals(mainDraftSwitch.rowId, mainDraftContext.fetch(switchId)!!.version!!.rowId)
        assertEquals(mainDraftKmPost.rowId, mainDraftContext.fetch(kmPostId)!!.version!!.rowId)

        assertEquals("edited in design", mainDraftContext.fetch(trackNumberId)!!.description.toString())
        assertEquals(123, mainDraftContext.fetch(trackNumberId)!!.startAddress.kmNumber.number)
        assertEquals("edited in design", mainDraftContext.fetch(locationTrackId)!!.name.toString())
        assertEquals("edited in design", mainDraftContext.fetch(switchId)!!.name.toString())
        assertEquals(321, mainDraftContext.fetch(kmPostId)!!.kmNumber.number)
    }

    @Test
    fun `draft cancellation of an item created in a plan hides it`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumber = designOfficialContext.save(trackNumber(), referenceLineGeometry())
        val locationTrack = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumber.id))
        val switch = designOfficialContext.save(switch())
        val kmPost = designOfficialContext.save(kmPost(trackNumber.id, KmNumber(123)))

        designDraftContext.save(cancelled(designOfficialContext.fetch(trackNumber.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(locationTrack.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(switch.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(kmPost.id)!!, someDesignBranch.designId))

        assertEquals(trackNumber, designOfficialContext.fetchVersion(trackNumber.id))
        assertEquals(locationTrack, designOfficialContext.fetchVersion(locationTrack.id))
        assertEquals(switch, designOfficialContext.fetchVersion(switch.id))
        assertEquals(kmPost, designOfficialContext.fetchVersion(kmPost.id))

        assertNull(designDraftContext.fetch(trackNumber.id))
        assertNull(designDraftContext.fetch(locationTrack.id))
        assertNull(designDraftContext.fetch(switch.id))
        assertNull(designDraftContext.fetch(kmPost.id))
    }

    @Test
    fun `draft cancellation of an item edited in a plan reveals the main-official version in design-draft context`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumber = mainOfficialContext.save(trackNumber(), referenceLineGeometry())
        val locationTrack = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumber.id))
        val switch = mainOfficialContext.save(switch())
        val kmPost = mainOfficialContext.save(kmPost(trackNumber.id, KmNumber(123)))

        val designOfficialTrackNumber =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(trackNumber.id)!!, someDesignBranch.designId)
                )
            )
        val designOfficialLocationTrack =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(locationTrack.id)!!, someDesignBranch.designId)
                )
            )
        val designOfficialSwitch =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(switch.id)!!, someDesignBranch.designId)
                )
            )
        val designOfficialKmPost =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(kmPost.id)!!, someDesignBranch.designId)
                )
            )

        designDraftContext.save(cancelled(designOfficialContext.fetch(trackNumber.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(locationTrack.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(switch.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(kmPost.id)!!, someDesignBranch.designId))

        assertEquals(designOfficialTrackNumber, designOfficialContext.fetchVersion(trackNumber.id))
        assertEquals(designOfficialLocationTrack, designOfficialContext.fetchVersion(locationTrack.id))
        assertEquals(designOfficialSwitch, designOfficialContext.fetchVersion(switch.id))
        assertEquals(designOfficialKmPost, designOfficialContext.fetchVersion(kmPost.id))

        assertEquals(trackNumber, designDraftContext.fetchVersion(trackNumber.id))
        assertEquals(locationTrack, designDraftContext.fetchVersion(locationTrack.id))
        assertEquals(switch, designDraftContext.fetchVersion(switch.id))
        assertEquals(kmPost, designDraftContext.fetchVersion(kmPost.id))
    }

    @Test
    fun `COMPLETED design rows reveal the main-official version in design contexts and at-moment fetches`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val segment = segment(Point(0.0, 0.0), Point(0.0, 2.0))
        val trackNumber = mainOfficialContext.save(trackNumber(), referenceLineGeometry(segment))
        val locationTrack = mainOfficialContext.save(locationTrack(trackNumber.id), trackGeometryOfSegments(segment))
        val switch = mainOfficialContext.save(switch())
        val kmPost = mainOfficialContext.save(kmPost(trackNumber.id, KmNumber(123)))
        val operationalPoint = mainOfficialContext.save(operationalPoint())

        val designTrackNumber = designOfficialContext.copyFrom(trackNumber)
        val designLocationTrack = designOfficialContext.copyFrom(locationTrack)
        val designSwitch = designOfficialContext.copyFrom(switch)
        val designKmPost = designOfficialContext.copyFrom(kmPost)
        val designOperationalPoint = designOfficialContext.copyFrom(operationalPoint)

        // Completion marker as a design draft: design-draft shows main, design-official still shows the design row
        val trackNumberCompletion = designDraftContext.save(completed(trackNumberDao.fetch(designTrackNumber)))
        val locationTrackCompletion =
            layoutLocationTrackService.getWithGeometry(designLocationTrack).let { (track, geometry) ->
                designDraftContext.save(completed(track), geometry)
            }
        val switchCompletion = designDraftContext.save(completed(switchDao.fetch(designSwitch)))
        val kmPostCompletion = designDraftContext.save(completed(kmPostDao.fetch(designKmPost)))
        val operationalPointCompletion =
            designDraftContext.save(completed(operationalPointDao.fetch(designOperationalPoint)))

        assertEquals(trackNumber, designDraftContext.fetchVersion(trackNumber.id))
        assertEquals(locationTrack, designDraftContext.fetchVersion(locationTrack.id))
        assertEquals(switch, designDraftContext.fetchVersion(switch.id))
        assertEquals(kmPost, designDraftContext.fetchVersion(kmPost.id))
        assertEquals(operationalPoint, designDraftContext.fetchVersion(operationalPoint.id))
        assertEquals(designTrackNumber, designOfficialContext.fetchVersion(trackNumber.id))
        assertEquals(designLocationTrack, designOfficialContext.fetchVersion(locationTrack.id))

        // Publishing the completion: the COMPLETED row stays live but both design contexts show main
        publishCompletion(trackNumberCompletion, designTrackNumber)
        publishCompletion(locationTrackCompletion, designLocationTrack)
        publishCompletion(switchCompletion, designSwitch)
        publishCompletion(kmPostCompletion, designKmPost)
        publishCompletion(operationalPointCompletion, designOperationalPoint)

        listOf(designOfficialContext, designDraftContext).forEach { context ->
            assertEquals(trackNumber, context.fetchVersion(trackNumber.id))
            assertEquals(locationTrack, context.fetchVersion(locationTrack.id))
            assertEquals(switch, context.fetchVersion(switch.id))
            assertEquals(kmPost, context.fetchVersion(kmPost.id))
            assertEquals(operationalPoint, context.fetchVersion(operationalPoint.id))
        }

        val afterCompletion = testDBService.layoutChangeTime()
        assertEquals(
            trackNumber,
            trackNumberDao.fetchOfficialVersionAtMoment(designBranch, trackNumber.id, afterCompletion),
        )
        assertEquals(
            locationTrack,
            locationTrackDao.fetchOfficialVersionAtMoment(designBranch, locationTrack.id, afterCompletion),
        )
        assertEquals(
            listOf(locationTrack),
            locationTrackDao.fetchAllOfficialVersionsAtMoment(designBranch, afterCompletion),
        )
        assertEquals(
            listOf(locationTrack),
            locationTrackDao.fetchOfficialVersionsNearAtMoment(
                designBranch,
                BoundingBox(Point(-1.0, -1.0), Point(1.0, 3.0)),
                afterCompletion,
            ),
        )
    }

    /**
     * Leaves the same rows behind as publishing a completion marker: the draft is gone, design-official is COMPLETED.
     */
    private inline fun <reified T : LayoutAsset<T>> publishCompletion(
        draft: LayoutRowVersion<T>,
        designOfficial: LayoutRowVersion<T>,
    ) {
        testDBService.delete(draft)
        testDBService.update(designOfficial) { asset ->
            asset.withContext(
                (asset.contextData as DesignOfficialContextData<T>).copy(designAssetState = DesignAssetState.COMPLETED)
            )
        }
    }
}
