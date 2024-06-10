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
        val rowId = IntId<TrackLayoutTrackNumber>(1)
        val officialRowId = IntId<TrackLayoutTrackNumber>(2)
        val designRowId = IntId<TrackLayoutTrackNumber>(3)

        assertEquals(rowId, MainOfficialContextData(rowId).id)

        assertEquals(officialRowId, MainDraftContextData(rowId, officialRowId, designRowId).id)
        assertEquals(designRowId, MainDraftContextData(rowId, null, designRowId).id)
        assertEquals(rowId, MainDraftContextData(rowId, null, null).id)

        assertEquals(officialRowId, DesignOfficialContextData(rowId, officialRowId, IntId(10)).id)
        assertEquals(rowId, DesignOfficialContextData(rowId, null, IntId(10)).id)

        assertEquals(officialRowId, DesignDraftContextData(rowId, designRowId, officialRowId, IntId(10)).id)
        assertEquals(designRowId, DesignDraftContextData(rowId, designRowId, null, IntId(10)).id)
        assertEquals(rowId, DesignDraftContextData(rowId, null, null, IntId(10)).id)
    }

    @Test
    fun `Official context calculated fields work as expected`() {
        val id = IntId<TrackLayoutTrackNumber>(123)
        val context = MainOfficialContextData(id)
        assertEquals(EditState.UNEDITED, context.editState)
        assertFalse(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)
    }

    @Test
    fun `Draft context calculated fields work as expected`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialRowId = IntId<TrackLayoutTrackNumber>(2)
        val designRowId = IntId<TrackLayoutTrackNumber>(3)

        val editedContext = MainDraftContextData(id, officialRowId, designRowId)
        assertEquals(EditState.EDITED, editedContext.editState)
        assertFalse(editedContext.isDesign)
        assertFalse(editedContext.isOfficial)
        assertTrue(editedContext.isDraft)

        val newContext = MainDraftContextData(id, null, designRowId)
        assertEquals(EditState.CREATED, newContext.editState)
        assertFalse(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = MainDraftContextData(id, null, null)
        assertEquals(EditState.CREATED, newContext2.editState)
        assertFalse(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Official design context calculated fields work as expected`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialRowId = IntId<TrackLayoutTrackNumber>(2)

        val context = DesignOfficialContextData(id, officialRowId, IntId(10))
        assertEquals(EditState.UNEDITED, context.editState)
        assertTrue(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)

        val newContext = DesignOfficialContextData(id, null, IntId(10))
        assertEquals(EditState.UNEDITED, newContext.editState)
        assertTrue(newContext.isDesign)
        assertTrue(newContext.isOfficial)
        assertFalse(newContext.isDraft)
    }

    @Test
    fun `Draft design context calculated fields work as expected`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialRowId = IntId<TrackLayoutTrackNumber>(2)
        val designRowId = IntId<TrackLayoutTrackNumber>(3)

        val context = DesignDraftContextData(id, designRowId, officialRowId, IntId(10))
        assertEquals(EditState.EDITED, context.editState)
        assertTrue(context.isDesign)
        assertFalse(context.isOfficial)
        assertTrue(context.isDraft)

        val newContext = DesignDraftContextData(id, null, officialRowId, IntId(10))
        assertEquals(EditState.CREATED, newContext.editState)
        assertTrue(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = DesignDraftContextData(id, null, null, IntId(10))
        assertEquals(EditState.CREATED, newContext2.editState)
        assertTrue(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Draft is correctly created from Official`() {
        val id = IntId<TrackLayoutTrackNumber>(123)

        val official = MainOfficialContextData(id)
        val draft = official.asMainDraft()

        assertEquals(id, draft.id)
        assertEquals(id, draft.officialRowId)
        assertNotEquals(id, draft.rowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Official is correctly created from Edited Draft`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialRowId = IntId<TrackLayoutTrackNumber>(2)
        val designRowId = IntId<TrackLayoutTrackNumber>(3)

        val draft = MainDraftContextData(id, officialRowId, designRowId)
        val official = draft.asMainOfficial()

        assertEquals(officialRowId, official.id)
        assertEquals(officialRowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Official is correctly created from New Draft`() {
        val id = IntId<TrackLayoutTrackNumber>(123)

        val draft = MainDraftContextData(id, null, null)
        val official = draft.asMainOfficial()
        assertEquals(id, official.id)
        assertEquals(id, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from Official`() {
        val id = IntId<TrackLayoutTrackNumber>(321)
        val designId = IntId<LayoutDesign>(123)

        val official = MainOfficialContextData(id)
        val designDraft = official.asDesignDraft(designId)

        assertEquals(id, designDraft.id)
        assertEquals(id, designDraft.officialRowId)
        assertNull(designDraft.designRowId)
        assertEquals(designId, designDraft.designId)
        assertNotEquals(id, designDraft.rowId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from new Design`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(id, null, designId)
        val designDraft = official.asDesignDraft()

        assertEquals(id, designDraft.id)
        assertEquals(id, designDraft.designRowId)
        assertNull(designDraft.officialRowId)
        assertNotEquals(id, designDraft.rowId)
        assertEquals(designId, designDraft.designId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from edited Design`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialId = IntId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(id, officialId, designId)
        val designDraft = official.asDesignDraft()

        assertEquals(officialId, designDraft.id)
        assertEquals(id, designDraft.designRowId)
        assertEquals(officialId, designDraft.officialRowId)
        assertNotEquals(id, designDraft.rowId)
        assertNotEquals(officialId, designDraft.rowId)
        assertEquals(designId, designDraft.designId)
        assertEquals(TEMP, designDraft.dataType)
    }

    @Test
    fun `Design is correctly created from new Design-Draft`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(id, null, null, designId)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(id, designOfficial.id)
        assertEquals(id, designOfficial.rowId)
        assertNull(designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Design is correctly created from edited Design-Draft`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val designRowId = IntId<TrackLayoutTrackNumber>(2)
        val officialRowId = IntId<TrackLayoutTrackNumber>(3)
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(id, designRowId, officialRowId, designId)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(officialRowId, designOfficial.id)
        assertEquals(designRowId, designOfficial.rowId)
        assertEquals(officialRowId, designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from new Design-Official`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(id, null, designId)
        val draft = design.asMainDraft()

        assertEquals(id, draft.id)
        assertNotEquals(id, draft.rowId)
        assertEquals(id, draft.designRowId)
        assertNull(draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from edited Design-Official`() {
        val id = IntId<TrackLayoutTrackNumber>(1)
        val officialId = IntId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(id, officialId, designId)
        val draft = design.asMainDraft()

        assertEquals(officialId, draft.id)
        assertNotEquals(id, draft.rowId)
        assertEquals(id, draft.designRowId)
        assertEquals(officialId, draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }
}
