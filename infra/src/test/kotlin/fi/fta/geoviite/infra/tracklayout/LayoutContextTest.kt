package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayoutContextTest {

    @Test
    fun `Context ID is resolved correctly`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        assertEquals(IntId(1), MainOfficialContextData(rowId, null).id)

        assertEquals(IntId(2), MainDraftContextData(rowId, null, officialRowId, designRowId).id)
        assertEquals(IntId(3), MainDraftContextData(rowId, null, null, designRowId).id)
        assertEquals(IntId(1), MainDraftContextData(rowId, null, null, null).id)

        assertEquals(IntId(2), DesignOfficialContextData(rowId, null, officialRowId, IntId(10)).id)
        assertEquals(IntId(1), DesignOfficialContextData(rowId, null, null, IntId(10)).id)

        assertEquals(IntId(2), DesignDraftContextData(rowId, null, designRowId, officialRowId, IntId(10)).id)
        assertEquals(IntId(3), DesignDraftContextData(rowId, null, designRowId, null, IntId(10)).id)
        assertEquals(IntId(1), DesignDraftContextData(rowId, null, null, null, IntId(10)).id)
    }

    @Test
    fun `Official context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)
        val context = MainOfficialContextData(rowId, null)
        assertEquals(EditState.UNEDITED, context.editState)
        assertFalse(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)
    }

    @Test
    fun `Draft context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val editedContext = MainDraftContextData(rowId, null, officialRowId, designRowId)
        assertEquals(EditState.EDITED, editedContext.editState)
        assertFalse(editedContext.isDesign)
        assertFalse(editedContext.isOfficial)
        assertTrue(editedContext.isDraft)

        val newContext = MainDraftContextData(rowId, null, null, designRowId)
        assertEquals(EditState.CREATED, newContext.editState)
        assertFalse(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = MainDraftContextData(rowId, null, null, null)
        assertEquals(EditState.CREATED, newContext2.editState)
        assertFalse(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Official design context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)

        val context = DesignOfficialContextData(rowId, null, officialRowId, IntId(10))
        assertEquals(EditState.UNEDITED, context.editState)
        assertTrue(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)

        val newContext = DesignOfficialContextData(rowId, null, null, IntId(10))
        assertEquals(EditState.UNEDITED, newContext.editState)
        assertTrue(newContext.isDesign)
        assertTrue(newContext.isOfficial)
        assertFalse(newContext.isDraft)
    }

    @Test
    fun `Draft design context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val context = DesignDraftContextData(rowId, null, designRowId, officialRowId, IntId(10))
        assertEquals(EditState.EDITED, context.editState)
        assertTrue(context.isDesign)
        assertFalse(context.isOfficial)
        assertTrue(context.isDraft)

        val newContext = DesignDraftContextData(rowId, null, null, officialRowId, IntId(10))
        assertEquals(EditState.CREATED, newContext.editState)
        assertTrue(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = DesignDraftContextData(rowId, null, null, null, IntId(10))
        assertEquals(EditState.CREATED, newContext2.editState)
        assertTrue(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Draft is correctly created from Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)

        val official = MainOfficialContextData(rowId, null)
        val draft = official.asMainDraft()

        assertEquals(IntId(123), draft.id)
        assertEquals(rowId, draft.officialRowId)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Official is correctly created from Edited Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val draft = MainDraftContextData(rowId, null, officialRowId, designRowId)
        val official = draft.asMainOfficial()

        assertEquals(IntId(2), official.id)
        assertEquals(officialRowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Official is correctly created from New Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)

        val draft = MainDraftContextData(rowId, null, null, null)
        val official = draft.asMainOfficial()
        assertEquals(IntId(123), official.id)
        assertEquals(rowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(321)
        val designId = IntId<LayoutDesign>(123)

        val official = MainOfficialContextData(rowId, null)
        val designDraft = official.asDesignDraft(designId)

        assertEquals(IntId(321), designDraft.id)
        assertEquals(rowId, designDraft.officialRowId)
        assertNull(designDraft.designRowId)
        assertEquals(designId, designDraft.designId)
        assertNotEquals(rowId, designDraft.rowId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from new Design`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(rowId, null, null, designId)
        val designDraft = official.asDesignDraft()

        assertEquals(IntId(1), designDraft.id)
        assertEquals(rowId, designDraft.designRowId)
        assertNull(designDraft.officialRowId)
        assertNotEquals(rowId, designDraft.rowId)
        assertEquals(designId, designDraft.designId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from edited Design`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(rowId, null, officialId, designId)
        val designDraft = official.asDesignDraft()

        assertEquals(IntId(2), designDraft.id)
        assertEquals(rowId, designDraft.designRowId)
        assertEquals(officialId, designDraft.officialRowId)
        assertNotEquals(rowId, designDraft.rowId)
        assertNotEquals(officialId, designDraft.rowId)
        assertEquals(designId, designDraft.designId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design is correctly created from new Design-Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(rowId, null, null, null, designId)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(IntId(1), designOfficial.id)
        assertEquals(rowId, designOfficial.rowId)
        assertNull(designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Design is correctly created from edited Design-Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(3)
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(rowId, null, designRowId, officialRowId, designId)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(IntId(3), designOfficial.id)
        assertEquals(designRowId, designOfficial.rowId)
        assertEquals(officialRowId, designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from new Design-Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(rowId, null, null, designId)
        val draft = design.asMainDraft()

        assertEquals(IntId(1), draft.id)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(rowId, draft.designRowId)
        assertNull(draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from edited Design-Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val officialId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(rowId, null, officialId, designId)
        val draft = design.asMainDraft()

        assertEquals(IntId(2), draft.id)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(rowId, draft.designRowId)
        assertEquals(officialId, draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }
}
