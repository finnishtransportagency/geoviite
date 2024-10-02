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
import kotlin.test.assertNotEquals
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

        val trackNumberId = designOfficialContext.insert(trackNumber()).id
        val referenceLineId = designOfficialContext.insert(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = designOfficialContext.insert(locationTrackAndAlignment(trackNumberId)).id

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

        val trackNumberId = designOfficialContext.insert(trackNumber(TrackNumber("original"))).id
        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        designDraftContext.insert(
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

        val trackNumberId = designOfficialContext.insert(trackNumber()).id
        val referenceLineId = designOfficialContext.insert(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = designOfficialContext.insert(locationTrackAndAlignment(trackNumberId)).id
        val switchId = designOfficialContext.insert(switch()).id
        val kmPostId = designOfficialContext.insert(kmPost(trackNumberId, KmNumber(123))).id

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutReferenceLineService.mergeToMainBranch(someDesignBranch, referenceLineId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertNotEquals(trackNumberId.intValue, mainDraftContext.fetch(trackNumberId)!!.contextData.rowId!!.intValue)
        assertNotEquals(
            referenceLineId.intValue,
            mainDraftContext.fetch(referenceLineId)!!.contextData.rowId!!.intValue,
        )
        assertNotEquals(
            locationTrackId.intValue,
            mainDraftContext.fetch(locationTrackId)!!.contextData.rowId!!.intValue,
        )
        assertNotEquals(switchId.intValue, mainDraftContext.fetch(switchId)!!.contextData.rowId!!.intValue)
        assertNotEquals(kmPostId.intValue, mainDraftContext.fetch(kmPostId)!!.contextData.rowId!!.intValue)

        assertEquals(trackNumberId.intValue, mainDraftContext.fetch(trackNumberId)!!.contextData.designRowId!!.intValue)
        assertEquals(
            referenceLineId.intValue,
            mainDraftContext.fetch(referenceLineId)!!.contextData.designRowId!!.intValue,
        )
        assertEquals(
            locationTrackId.intValue,
            mainDraftContext.fetch(locationTrackId)!!.contextData.designRowId!!.intValue,
        )
        assertEquals(switchId.intValue, mainDraftContext.fetch(switchId)!!.contextData.designRowId!!.intValue)
        assertEquals(kmPostId.intValue, mainDraftContext.fetch(kmPostId)!!.contextData.designRowId!!.intValue)

        assertEquals(trackNumberId.intValue, designOfficialContext.fetch(trackNumberId)!!.contextData.rowId!!.intValue)
        assertEquals(
            referenceLineId.intValue,
            designOfficialContext.fetch(referenceLineId)!!.contextData.rowId!!.intValue,
        )
        assertEquals(
            locationTrackId.intValue,
            designOfficialContext.fetch(locationTrackId)!!.contextData.rowId!!.intValue,
        )
        assertEquals(switchId.intValue, designOfficialContext.fetch(switchId)!!.contextData.rowId!!.intValue)
        assertEquals(kmPostId.intValue, designOfficialContext.fetch(kmPostId)!!.contextData.rowId!!.intValue)
    }

    @Test
    fun `existing main-draft is overridden when merging object from design-official`() {
        val someDesignBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(someDesignBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(someDesignBranch, PublicationState.DRAFT)

        val trackNumberId = mainOfficialContext.insert(trackNumber()).id
        val referenceLineId = mainOfficialContext.insert(referenceLineAndAlignment(trackNumberId)).id
        val locationTrackId = mainOfficialContext.insert(locationTrackAndAlignment(trackNumberId)).id
        val switchId = mainOfficialContext.insert(switch()).id
        val kmPostId = mainOfficialContext.insert(kmPost(trackNumberId, KmNumber(123))).id

        mainDraftContext.insert(
            asMainDraft(mainOfficialContext.fetch(trackNumberId)!!)
                .copy(description = TrackNumberDescription("edited in main"))
        )
        mainDraftContext.insert(
            asMainDraft(mainOfficialContext.fetch(referenceLineId)!!).copy(startAddress = TrackMeter("0000+0123"))
        )
        mainDraftContext.insert(
            asMainDraft(mainOfficialContext.fetch(locationTrackId)!!).copy(name = AlignmentName("edited in main"))
        )
        mainDraftContext.insert(
            asMainDraft(mainOfficialContext.fetch(switchId)!!).copy(name = SwitchName("edited in main"))
        )
        mainDraftContext.insert(asMainDraft(mainOfficialContext.fetch(kmPostId)!!).copy(kmNumber = KmNumber(123)))

        val designOfficialTrackNumberRowId =
            designOfficialContext
                .moveFrom(
                    designDraftContext
                        .insert(
                            asDesignDraft(
                                mainOfficialContext
                                    .fetch(trackNumberId)!!
                                    .copy(description = TrackNumberDescription("edited in design")),
                                someDesignBranch.designId,
                            )
                        )
                        .rowVersion
                )
                .rowVersion
                .rowId
        val designOfficialReferenceLineRowId =
            designOfficialContext
                .moveFrom(
                    designDraftContext
                        .insert(
                            asDesignDraft(
                                mainOfficialContext
                                    .fetch(referenceLineId)!!
                                    .copy(startAddress = TrackMeter("0123+0000")),
                                someDesignBranch.designId,
                            )
                        )
                        .rowVersion
                )
                .rowVersion
                .rowId
        val designOfficialLocationTrackRowId =
            designOfficialContext
                .moveFrom(
                    designDraftContext
                        .insert(
                            asDesignDraft(
                                mainOfficialContext
                                    .fetch(locationTrackId)!!
                                    .copy(name = AlignmentName("edited in design")),
                                someDesignBranch.designId,
                            )
                        )
                        .rowVersion
                )
                .rowVersion
                .rowId
        val designOfficialSwitchRowId =
            designOfficialContext
                .moveFrom(
                    designDraftContext
                        .insert(
                            asDesignDraft(
                                mainOfficialContext.fetch(switchId)!!.copy(name = SwitchName("edited in design")),
                                someDesignBranch.designId,
                            )
                        )
                        .rowVersion
                )
                .rowVersion
                .rowId
        val designOfficialKmPostRowId =
            designOfficialContext
                .moveFrom(
                    designDraftContext
                        .insert(
                            asDesignDraft(
                                mainOfficialContext.fetch(kmPostId)!!.copy(kmNumber = KmNumber(321)),
                                someDesignBranch.designId,
                            )
                        )
                        .rowVersion
                )
                .rowVersion
                .rowId

        layoutTrackNumberService.mergeToMainBranch(someDesignBranch, trackNumberId)
        layoutReferenceLineService.mergeToMainBranch(someDesignBranch, referenceLineId)
        layoutLocationTrackService.mergeToMainBranch(someDesignBranch, locationTrackId)
        layoutSwitchService.mergeToMainBranch(someDesignBranch, switchId)
        layoutKmPostService.mergeToMainBranch(someDesignBranch, kmPostId)

        assertNotEquals(trackNumberId.intValue, mainDraftContext.fetch(trackNumberId)!!.contextData.rowId!!.intValue)
        assertNotEquals(
            referenceLineId.intValue,
            mainDraftContext.fetch(referenceLineId)!!.contextData.rowId!!.intValue,
        )
        assertNotEquals(
            locationTrackId.intValue,
            mainDraftContext.fetch(locationTrackId)!!.contextData.rowId!!.intValue,
        )
        assertNotEquals(switchId.intValue, mainDraftContext.fetch(switchId)!!.contextData.rowId!!.intValue)
        assertNotEquals(kmPostId.intValue, mainDraftContext.fetch(kmPostId)!!.contextData.rowId!!.intValue)

        assertEquals(designOfficialTrackNumberRowId, mainDraftContext.fetch(trackNumberId)!!.contextData.designRowId)
        assertEquals(
            designOfficialReferenceLineRowId,
            mainDraftContext.fetch(referenceLineId)!!.contextData.designRowId,
        )
        assertEquals(
            designOfficialLocationTrackRowId,
            mainDraftContext.fetch(locationTrackId)!!.contextData.designRowId,
        )
        assertEquals(designOfficialSwitchRowId, mainDraftContext.fetch(switchId)!!.contextData.designRowId)
        assertEquals(designOfficialKmPostRowId, mainDraftContext.fetch(kmPostId)!!.contextData.designRowId)

        assertEquals("edited in design", mainDraftContext.fetch(trackNumberId)!!.description.toString())
        assertEquals(123, mainDraftContext.fetch(referenceLineId)!!.startAddress.kmNumber.number)
        assertEquals("edited in design", mainDraftContext.fetch(locationTrackId)!!.name.toString())
        assertEquals("edited in design", mainDraftContext.fetch(switchId)!!.name.toString())
        assertEquals(321, mainDraftContext.fetch(kmPostId)!!.kmNumber.number)
    }
}
