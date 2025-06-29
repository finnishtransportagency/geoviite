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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAssetServiceIT
@Autowired
constructor(
    private val layoutTrackNumberService: LayoutTrackNumberService,
    private val layoutReferenceLineService: ReferenceLineService,
    private val layoutLocationTrackService: LocationTrackService,
    private val layoutSwitchService: LayoutSwitchService,
    private val layoutKmPostService: LayoutKmPostService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `objects with alignments can be merged to main-draft and then deleted from main-draft`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)

        val trackNumberId = designOfficialContext.save(trackNumber()).id
        val referenceLineId = designOfficialContext.saveReferenceLine(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutReferenceLineService.mergeToMainBranch(someDesignBranch, referenceLineId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)

        layoutLocationTrackService.deleteDraft(LayoutBranch.main, locationTrackId)
        layoutReferenceLineService.deleteDraft(LayoutBranch.main, referenceLineId)

        assertNull(mainDraftContext.fetch(locationTrackId))
        assertNull(mainDraftContext.fetch(referenceLineId))
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

        val trackNumberId = designOfficialContext.save(trackNumber()).id
        val referenceLineId = designOfficialContext.saveReferenceLine(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id
        val switchId = designOfficialContext.save(switch()).id
        val kmPostId = designOfficialContext.save(kmPost(trackNumberId, KmNumber(123))).id

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutReferenceLineService.mergeToMainBranch(someDesignBranch, referenceLineId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertEquals(trackNumberId, mainDraftContext.fetch(trackNumberId)!!.id)
        assertEquals(referenceLineId, mainDraftContext.fetch(referenceLineId)!!.id)
        assertEquals(locationTrackId, mainDraftContext.fetch(locationTrackId)!!.id)
        assertEquals(switchId, mainDraftContext.fetch(switchId)!!.id)
        assertEquals(kmPostId, mainDraftContext.fetch(kmPostId)!!.id)

        assertEquals(trackNumberId, designOfficialContext.fetch(trackNumberId)!!.id)
        assertEquals(referenceLineId, designOfficialContext.fetch(referenceLineId)!!.id)
        assertEquals(locationTrackId, designOfficialContext.fetch(locationTrackId)!!.id)
        assertEquals(switchId, designOfficialContext.fetch(switchId)!!.id)
        assertEquals(kmPostId, designOfficialContext.fetch(kmPostId)!!.id)
    }

    @Test
    fun `existing main-draft is overridden when merging object from design-official`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumberId = mainOfficialContext.save(trackNumber()).id
        val referenceLineId = mainOfficialContext.saveReferenceLine(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId)).id
        val switchId = mainOfficialContext.save(switch()).id
        val kmPostId = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(123))).id

        val mainDraftTrackNumber =
            mainDraftContext.save(
                asMainDraft(mainOfficialContext.fetch(trackNumberId)!!)
                    .copy(description = TrackNumberDescription("edited in main"))
            )
        val mainDraftReferenceLine =
            mainDraftContext.save(
                asMainDraft(mainOfficialContext.fetch(referenceLineId)!!).copy(startAddress = TrackMeter("0000+0123"))
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
                        .copy(description = TrackNumberDescription("edited in design")),
                    someDesignBranch.designId,
                )
            )
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext.fetch(referenceLineId)!!.copy(startAddress = TrackMeter("0123+0000")),
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
        layoutReferenceLineService.mergeToMainBranch(someDesignBranch, referenceLineId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertEquals(mainDraftTrackNumber.rowId, mainDraftContext.fetch(trackNumberId)!!.version!!.rowId)
        assertEquals(mainDraftReferenceLine.rowId, mainDraftContext.fetch(referenceLineId)!!.version!!.rowId)
        assertEquals(mainDraftLocationTrack.rowId, mainDraftContext.fetch(locationTrackId)!!.version!!.rowId)
        assertEquals(mainDraftSwitch.rowId, mainDraftContext.fetch(switchId)!!.version!!.rowId)
        assertEquals(mainDraftKmPost.rowId, mainDraftContext.fetch(kmPostId)!!.version!!.rowId)

        assertEquals("edited in design", mainDraftContext.fetch(trackNumberId)!!.description.toString())
        assertEquals(123, mainDraftContext.fetch(referenceLineId)!!.startAddress.kmNumber.number)
        assertEquals("edited in design", mainDraftContext.fetch(locationTrackId)!!.name.toString())
        assertEquals("edited in design", mainDraftContext.fetch(switchId)!!.name.toString())
        assertEquals(321, mainDraftContext.fetch(kmPostId)!!.kmNumber.number)
    }

    @Test
    fun `draft cancellation of an item created in a plan hides it`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumber = designOfficialContext.save(trackNumber())
        val referenceLine = designOfficialContext.saveReferenceLine(referenceLineAndAlignment(trackNumber.id))
        val locationTrack = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumber.id))
        val switch = designOfficialContext.save(switch())
        val kmPost = designOfficialContext.save(kmPost(trackNumber.id, KmNumber(123)))

        designDraftContext.save(cancelled(designOfficialContext.fetch(trackNumber.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(referenceLine.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(locationTrack.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(switch.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(kmPost.id)!!, someDesignBranch.designId))

        assertEquals(trackNumber, designOfficialContext.fetchVersion(trackNumber.id))
        assertEquals(referenceLine, designOfficialContext.fetchVersion(referenceLine.id))
        assertEquals(locationTrack, designOfficialContext.fetchVersion(locationTrack.id))
        assertEquals(switch, designOfficialContext.fetchVersion(switch.id))
        assertEquals(kmPost, designOfficialContext.fetchVersion(kmPost.id))

        assertNull(designDraftContext.fetch(trackNumber.id))
        assertNull(designDraftContext.fetch(referenceLine.id))
        assertNull(designDraftContext.fetch(locationTrack.id))
        assertNull(designDraftContext.fetch(switch.id))
        assertNull(designDraftContext.fetch(kmPost.id))
    }

    @Test
    fun `draft cancellation of an item edited in a plan reveals the main-official version in design-draft context`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumber = mainOfficialContext.save(trackNumber())
        val referenceLine = mainOfficialContext.saveReferenceLine(referenceLineAndAlignment(trackNumber.id))
        val locationTrack = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumber.id))
        val switch = mainOfficialContext.save(switch())
        val kmPost = mainOfficialContext.save(kmPost(trackNumber.id, KmNumber(123)))

        val designOfficialTrackNumber =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(trackNumber.id)!!, someDesignBranch.designId)
                )
            )
        val designOfficialReferenceLine =
            designOfficialContext.moveFrom(
                designDraftContext.save(
                    asDesignDraft(mainOfficialContext.fetch(referenceLine.id)!!, someDesignBranch.designId)
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
        designDraftContext.save(cancelled(designOfficialContext.fetch(referenceLine.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(locationTrack.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(switch.id)!!, someDesignBranch.designId))
        designDraftContext.save(cancelled(designOfficialContext.fetch(kmPost.id)!!, someDesignBranch.designId))

        assertEquals(designOfficialTrackNumber, designOfficialContext.fetchVersion(trackNumber.id))
        assertEquals(designOfficialReferenceLine, designOfficialContext.fetchVersion(referenceLine.id))
        assertEquals(designOfficialLocationTrack, designOfficialContext.fetchVersion(locationTrack.id))
        assertEquals(designOfficialSwitch, designOfficialContext.fetchVersion(switch.id))
        assertEquals(designOfficialKmPost, designOfficialContext.fetchVersion(kmPost.id))

        assertEquals(trackNumber, designDraftContext.fetchVersion(trackNumber.id))
        assertEquals(referenceLine, designDraftContext.fetchVersion(referenceLine.id))
        assertEquals(locationTrack, designDraftContext.fetchVersion(locationTrack.id))
        assertEquals(switch, designDraftContext.fetchVersion(switch.id))
        assertEquals(kmPost, designDraftContext.fetchVersion(kmPost.id))
    }
}
