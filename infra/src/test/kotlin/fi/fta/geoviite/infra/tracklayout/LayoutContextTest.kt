package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.StringId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayoutContextTest {

    @Test
    fun `Context ID is resolved correctly`() {
        val rowId = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()
        val designRowId = StringId<TrackLayoutTrackNumber>()

        assertEquals(rowId, MainOfficialContextData(rowId, TEMP).id)

        assertEquals(officialRowId, MainDraftContextData(rowId, officialRowId, designRowId, TEMP).id)
        assertEquals(designRowId, MainDraftContextData(rowId, null, designRowId, TEMP).id)
        assertEquals(rowId, MainDraftContextData(rowId, null, null, TEMP).id)

        assertEquals(officialRowId, DesignOfficialContextData(rowId, officialRowId, IntId(1), TEMP).id)
        assertEquals(rowId, DesignOfficialContextData(rowId, null, IntId(1), TEMP).id)

        assertEquals(officialRowId, DesignDraftContextData(rowId, designRowId, officialRowId, IntId(1), TEMP).id)
        assertEquals(designRowId, DesignDraftContextData(rowId, designRowId, null, IntId(1), TEMP).id)
        assertEquals(rowId, DesignDraftContextData(rowId, null, null, IntId(1), TEMP).id)
    }

    @Test
    fun `Official context calculated fields work as expected`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val context = MainOfficialContextData(id, TEMP)
        assertEquals(ContextType.OFFICIAL, context.draftType)
        assertFalse(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)
        assertFalse(context.isEditedDraft)
        assertFalse(context.isNewDraft)
    }

    @Test
    fun `Draft context calculated fields work as expected`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()
        val designRowId = StringId<TrackLayoutTrackNumber>()

        val editedContext = MainDraftContextData(id, officialRowId, designRowId, TEMP)
        assertEquals(ContextType.EDITED_DRAFT, editedContext.draftType)
        assertFalse(editedContext.isDesign)
        assertFalse(editedContext.isOfficial)
        assertTrue(editedContext.isDraft)
        assertTrue(editedContext.isEditedDraft)
        assertFalse(editedContext.isNewDraft)

        val newContext = MainDraftContextData(id, null, designRowId, TEMP)
        assertEquals(ContextType.NEW_DRAFT, newContext.draftType)
        assertFalse(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)
        assertFalse(newContext.isEditedDraft)
        assertTrue(newContext.isNewDraft)

        val newContext2 = MainDraftContextData(id, null, null, TEMP)
        assertEquals(ContextType.NEW_DRAFT, newContext2.draftType)
        assertFalse(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
        assertFalse(newContext2.isEditedDraft)
        assertTrue(newContext2.isNewDraft)
    }

    @Test
    fun `Official design context calculated fields work as expected`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()

        val context = DesignOfficialContextData(id, officialRowId, IntId(1), TEMP)
        assertEquals(ContextType.DESIGN, context.draftType)
        assertTrue(context.isDesign)
        assertFalse(context.isOfficial)
        assertFalse(context.isDraft)
        assertFalse(context.isEditedDraft)
        assertFalse(context.isNewDraft)

        val newContext = DesignOfficialContextData(id, null, IntId(1), TEMP)
        assertEquals(ContextType.DESIGN, newContext.draftType)
        assertTrue(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertFalse(newContext.isDraft)
        assertFalse(newContext.isEditedDraft)
        assertFalse(newContext.isNewDraft)
    }

    @Test
    fun `Draft design context calculated fields work as expected`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()
        val designRowId = StringId<TrackLayoutTrackNumber>()

        val context = DesignDraftContextData(id, designRowId, officialRowId, IntId(1), TEMP)
        assertEquals(ContextType.EDITED_DESIGN_DRAFT, context.draftType)
        assertTrue(context.isDesign)
        assertFalse(context.isOfficial)
        assertTrue(context.isDraft)
        assertTrue(context.isEditedDraft)
        assertFalse(context.isNewDraft)

        val newContext = DesignDraftContextData(id, null, officialRowId, IntId(1), TEMP)
        assertEquals(ContextType.NEW_DESIGN_DRAFT, newContext.draftType)
        assertTrue(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)
        assertFalse(newContext.isEditedDraft)
        assertTrue(newContext.isNewDraft)

        val newContext2 = DesignDraftContextData(id, null, null, IntId(1), TEMP)
        assertEquals(ContextType.NEW_DESIGN_DRAFT, newContext2.draftType)
        assertTrue(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
        assertFalse(newContext2.isEditedDraft)
        assertTrue(newContext2.isNewDraft)
    }

    @Test
    fun `Draft is correctly created from Official`() {
        val id = StringId<TrackLayoutTrackNumber>()

        val official = MainOfficialContextData(id, STORED)
        val draft = official.asMainDraft()

        assertEquals(id, draft.id)
        assertEquals(id, draft.officialRowId)
        assertNotEquals(id, draft.rowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Official is correctly created from Edited Draft`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()
        val designRowId = StringId<TrackLayoutTrackNumber>()

        val draft = MainDraftContextData(id, officialRowId, designRowId, STORED)
        val official = draft.asMainOfficial()

        assertEquals(officialRowId, official.id)
        assertEquals(officialRowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Official is correctly created from New Draft`() {
        val id = StringId<TrackLayoutTrackNumber>()

        val draft = MainDraftContextData(id, null, null, STORED)
        val official = draft.asMainOfficial()
        assertEquals(id, official.id)
        assertEquals(id, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from Official`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val official = MainOfficialContextData(id, STORED)
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
        val id = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(id, null, designId, STORED)
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
        val id = StringId<TrackLayoutTrackNumber>()
        val officialId = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(id, officialId, designId, STORED)
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
        val id = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(id, null, null, designId, STORED)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(id, designOfficial.id)
        assertEquals(id, designOfficial.rowId)
        assertNull(designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Design is correctly created from edited Design-Draft`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val designRowId = StringId<TrackLayoutTrackNumber>()
        val officialRowId = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(id, designRowId, officialRowId, designId, STORED)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(officialRowId, designOfficial.id)
        assertEquals(designRowId, designOfficial.rowId)
        assertEquals(officialRowId, designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from new Design-Official`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(id, null, designId, STORED)
        val draft = design.asMainDraft()

        assertEquals(id, draft.id)
        assertNotEquals(id, draft.rowId)
        assertEquals(id, draft.designRowId)
        assertNull(draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Main Draft is correctly created from edited Design-Official`() {
        val id = StringId<TrackLayoutTrackNumber>()
        val officialId = StringId<TrackLayoutTrackNumber>()
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(id, officialId, designId, STORED)
        val draft = design.asMainDraft()

        assertEquals(officialId, draft.id)
        assertNotEquals(id, draft.rowId)
        assertEquals(id, draft.designRowId)
        assertEquals(officialId, draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }
}
