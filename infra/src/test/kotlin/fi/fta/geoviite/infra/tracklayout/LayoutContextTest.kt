package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.StringId
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LayoutContextTest {

    @Test
    fun `Context ID is resolved correctly`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val rowVersion = LayoutRowVersion(rowId, 123)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)
        val tmpId = StringId<TrackLayoutTrackNumber>()
        val anotherVersion = LayoutRowVersion<TrackLayoutTrackNumber>(LayoutRowId(123), 123)

        assertEquals(tmpId, MainOfficialContextData(UnstoredContextIdHolder(anotherVersion, tmpId)).id)
        assertEquals(IntId(1), MainOfficialContextData(OverwritingContextIdHolder(rowId, anotherVersion)).id)
        assertEquals(IntId(1), MainOfficialContextData(StoredContextIdHolder(rowVersion)).id)

        assertEquals(IntId(2), MainDraftContextData(StoredContextIdHolder(rowVersion), officialRowId, designRowId).id)
        assertEquals(IntId(3), MainDraftContextData(StoredContextIdHolder(rowVersion), null, designRowId).id)
        assertEquals(IntId(1), MainDraftContextData(StoredContextIdHolder(rowVersion), null, null).id)

        assertEquals(
            IntId(2),
            DesignOfficialContextData(StoredContextIdHolder(rowVersion), officialRowId, IntId(10), false).id,
        )
        assertEquals(IntId(1), DesignOfficialContextData(StoredContextIdHolder(rowVersion), null, IntId(10), false).id)

        assertEquals(
            IntId(2),
            DesignDraftContextData(StoredContextIdHolder(rowVersion), designRowId, officialRowId, IntId(10), false).id,
        )
        assertEquals(
            IntId(3),
            DesignDraftContextData(StoredContextIdHolder(rowVersion), designRowId, null, IntId(10), false).id,
        )
        assertEquals(
            IntId(1),
            DesignDraftContextData(StoredContextIdHolder(rowVersion), null, null, IntId(10), false).id,
        )
    }

    @Test
    fun `Official context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val context = MainOfficialContextData(contextId)
        assertTrue(context.hasOfficial)
        assertFalse(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)
    }

    @Test
    fun `Draft context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val editedContext = MainDraftContextData(contextId, officialRowId, designRowId)
        assertTrue(editedContext.hasOfficial)
        assertFalse(editedContext.isDesign)
        assertFalse(editedContext.isOfficial)
        assertTrue(editedContext.isDraft)

        val newContext = MainDraftContextData(contextId, null, designRowId)
        assertFalse(newContext.hasOfficial)
        assertFalse(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = MainDraftContextData(contextId, null, null)
        assertFalse(newContext2.hasOfficial)
        assertFalse(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Official design context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)

        val context = DesignOfficialContextData(contextId, officialRowId, IntId(10), false)
        assertTrue(context.hasOfficial)
        assertTrue(context.isDesign)
        assertTrue(context.isOfficial)
        assertFalse(context.isDraft)

        val newContext = DesignOfficialContextData(contextId, null, IntId(10), false)
        assertTrue(newContext.hasOfficial)
        assertTrue(newContext.isDesign)
        assertTrue(newContext.isOfficial)
        assertFalse(newContext.isDraft)
    }

    @Test
    fun `Draft design context calculated fields work as expected`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val context = DesignDraftContextData(contextId, designRowId, officialRowId, IntId(10), false)
        assertTrue(context.hasOfficial)
        assertTrue(context.isDesign)
        assertFalse(context.isOfficial)
        assertTrue(context.isDraft)

        val newContext = DesignDraftContextData(contextId, null, officialRowId, IntId(10), false)
        assertTrue(newContext.hasOfficial)
        assertTrue(newContext.isDesign)
        assertFalse(newContext.isOfficial)
        assertTrue(newContext.isDraft)

        val newContext2 = DesignDraftContextData(contextId, null, null, IntId(10), false)
        assertFalse(newContext2.hasOfficial)
        assertTrue(newContext2.isDesign)
        assertFalse(newContext2.isOfficial)
        assertTrue(newContext2.isDraft)
    }

    @Test
    fun `Draft is correctly created from Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))

        val official = MainOfficialContextData(contextId)
        val draft = official.asMainDraft()

        assertEquals(IntId(123), draft.id)
        assertEquals(rowId, draft.officialRowId)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Official is correctly created from Edited Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(3)

        val draft = MainDraftContextData(contextId, officialRowId, designRowId)
        val official = draft.asMainOfficial()

        assertEquals(IntId(2), official.id)
        assertEquals(officialRowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Official is correctly created from New Draft`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(123)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))

        val draft = MainDraftContextData(contextId, null, null)
        val official = draft.asMainOfficial()
        assertEquals(IntId(123), official.id)
        assertEquals(rowId, official.rowId)
        assertEquals(STORED, official.dataType)
    }

    @Test
    fun `Design-Draft is correctly created from Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(321)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val designId = IntId<LayoutDesign>(123)

        val official = MainOfficialContextData(contextId)
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
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(contextId, null, designId, false)
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
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val official = DesignOfficialContextData(contextId, officialId, designId, false)
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
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(contextId, null, null, designId, false)
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
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val designRowId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val officialRowId = LayoutRowId<TrackLayoutTrackNumber>(3)
        val designId = IntId<LayoutDesign>(123)

        val designDraft = DesignDraftContextData(contextId, designRowId, officialRowId, designId, false)
        val designOfficial = designDraft.asDesignOfficial()

        assertEquals(IntId(3), designOfficial.id)
        assertEquals(designRowId, designOfficial.rowId)
        assertEquals(officialRowId, designOfficial.officialRowId)
        assertNull(designOfficial.designRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Main-Draft is correctly created from new Design-Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(contextId, null, designId, false)
        val draft = design.asMainDraft(null)

        assertEquals(IntId(1), draft.id)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(rowId, draft.designRowId)
        assertNull(draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Main-Draft is correctly created from edited Design-Official`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val contextId = StoredContextIdHolder(LayoutRowVersion(rowId, 123))
        val officialId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val designId = IntId<LayoutDesign>(123)

        val design = DesignOfficialContextData(contextId, officialId, designId, false)
        val draft = design.asMainDraft(null)

        assertEquals(IntId(2), draft.id)
        assertNotEquals(rowId, draft.rowId)
        assertEquals(rowId, draft.designRowId)
        assertEquals(officialId, draft.officialRowId)
        assertEquals(TEMP, draft.dataType)
    }

    @Test
    fun `Merged Main-Draft is correctly created when merging Design-Official`() {
        val mainOfficialId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val designOfficialId = LayoutRowId<TrackLayoutTrackNumber>(2)
        val mainDraftId = LayoutRowId<TrackLayoutTrackNumber>(3)
        val designId = IntId<LayoutDesign>(123)

        val designOfficial =
            DesignOfficialContextData(
                contextIdHolder = StoredContextIdHolder(LayoutRowVersion(designOfficialId, 123)),
                officialRowId = mainOfficialId,
                designId = designId,
                false,
            )
        val designDraft = designOfficial.asMainDraft(mainDraftId)

        assertEquals(IntId(1), designDraft.id)
        assertEquals(mainDraftId, designDraft.rowId)
        assertEquals(designOfficialId, designDraft.designRowId)
        assertEquals(mainOfficialId, designDraft.officialRowId)
        assertEquals(STORED, designDraft.dataType)
    }

    @Test
    fun `Only stored rows can transition context`() {
        val rowId = LayoutRowId<TrackLayoutTrackNumber>(1)
        val rowVersion = LayoutRowVersion(rowId, 1)

        val storedContext = StoredContextIdHolder(rowVersion)
        val unstoredContext = UnstoredContextIdHolder(rowVersion, StringId())
        val overwritingContext = OverwritingContextIdHolder(rowId, rowVersion)

        assertNotNull(MainOfficialContextData(storedContext).asMainDraft())
        assertNotNull(MainOfficialContextData(storedContext).asDesignDraft(IntId(1)))
        assertNotNull(MainDraftContextData(storedContext, null, null).asMainOfficial())
        assertNotNull(DesignOfficialContextData(storedContext, null, IntId(1), false).asMainDraft(null))
        assertNotNull(DesignOfficialContextData(storedContext, null, IntId(1), false).asDesignDraft())
        assertNotNull(DesignDraftContextData(storedContext, null, null, IntId(1), false).asDesignOfficial())

        assertThrows<IllegalStateException> { MainOfficialContextData(unstoredContext).asMainDraft() }
        assertThrows<IllegalStateException> { MainOfficialContextData(unstoredContext).asDesignDraft(IntId(1)) }
        assertThrows<IllegalStateException> { MainDraftContextData(unstoredContext, null, null).asMainOfficial() }
        assertThrows<IllegalStateException> {
            DesignOfficialContextData(unstoredContext, null, IntId(1), false).asMainDraft(null)
        }
        assertThrows<IllegalStateException> {
            DesignOfficialContextData(unstoredContext, null, IntId(1), false).asDesignDraft()
        }
        assertThrows<IllegalStateException> {
            DesignDraftContextData(unstoredContext, null, null, IntId(1), false).asDesignOfficial()
        }

        assertThrows<IllegalStateException> { MainOfficialContextData(overwritingContext).asMainDraft() }
        assertThrows<IllegalStateException> { MainOfficialContextData(overwritingContext).asDesignDraft(IntId(1)) }
        assertThrows<IllegalStateException> { MainDraftContextData(overwritingContext, null, null).asMainOfficial() }
        assertThrows<IllegalStateException> {
            DesignOfficialContextData(overwritingContext, null, IntId(1), false).asMainDraft(null)
        }
        assertThrows<IllegalStateException> {
            DesignOfficialContextData(overwritingContext, null, IntId(1), false).asDesignDraft()
        }
        assertThrows<IllegalStateException> {
            DesignDraftContextData(overwritingContext, null, null, IntId(1), false).asDesignOfficial()
        }
    }
}
