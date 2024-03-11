package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.logging.Loggable

// TODO: GVT-2426 This enum is an outdated concept that won't serve well with designs. It's only used by the front. Can we refactor it out?
enum class ContextType {
    OFFICIAL, NEW_DRAFT, EDITED_DRAFT, DESIGN, NEW_DESIGN_DRAFT, EDITED_DESIGN_DRAFT
}

interface LayoutContextAware<T> {
    val id: DomainId<T>
    val dataType: DataType
    // TODO: GVT-2426 if we keep this, the field should be renamed
//    val contextType: ContextType
    val draftType: ContextType
    @get:JsonIgnore val isDraft: Boolean get() = false
    @get:JsonIgnore val isOfficial: Boolean get() = false
    @get:JsonIgnore val isDesign: Boolean get() = false
}

sealed class LayoutConcept<T : LayoutConcept<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {

    abstract val version: RowVersion<T>?
    abstract val contextData: LayoutContextData<T>
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {

    @get:JsonIgnore abstract val rowId: DomainId<T>
    @get:JsonIgnore open val officialRowId: DomainId<T>? get() = null
    @get:JsonIgnore open val designRowId: DomainId<T>? get() = null

    companion object {
        fun <T> newDraft(id: DomainId<T> = StringId()): MainDraftContextData<T> =
            MainDraftContextData(id, null, null, TEMP)

        fun <T> newOfficial(id: DomainId<T> = StringId()): MainOfficialContextData<T> =
            MainOfficialContextData(id, TEMP)
    }
}

sealed class MainContextData<T> : LayoutContextData<T>()

sealed class DesignContextData<T> : LayoutContextData<T>() {
    abstract val designId: IntId<LayoutDesign>
}

data class MainOfficialContextData<T>(
    override val rowId: DomainId<T>,
    override val dataType: DataType,
) : MainContextData<T>() {
    override val id: DomainId<T> = rowId
    override val draftType: ContextType get() = ContextType.OFFICIAL
    override val isOfficial: Boolean get() = true

    fun asMainDraft(): MainDraftContextData<T> = MainDraftContextData(
        // TODO: GVT-2426 This is the old way of things, but do we actually need to support switching context in temp objects?
        // If datatype is TEMP, the official row doesn't actually exist in DB -> alter the context to desired form
        // Otherwise, we're creating the draft by copying the row -> new ID in a new TEMP object
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = if (dataType == TEMP) null else rowId,
        designRowId = null,
        dataType = TEMP,
    )

    fun asDesignDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> = DesignDraftContextData(
        // TODO: GVT-2426 This is the old way of things, but do we actually need to support switching context in temp objects?
        // If datatype is TEMP, the official row doesn't actually exist in DB -> alter the context to desired form
        // Otherwise, we're creating the draft by copying the row -> new ID in a new TEMP object
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = if (dataType == TEMP) null else rowId,
        designRowId = null,
        designId = designId,
        dataType = TEMP,
    )
}

data class MainDraftContextData<T>(
    override val rowId: DomainId<T>,
    override val officialRowId: DomainId<T>?,
    override val designRowId: DomainId<T>?,
    override val dataType: DataType,
) : MainContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId
    override val draftType: ContextType
        get() = if (officialRowId == null) ContextType.NEW_DRAFT else ContextType.EDITED_DRAFT
    override val isDraft: Boolean get() = true

    init {
        require(rowId != officialRowId) {
            "Draft row should not refer to itself as official: contextData=$this"
        }
        require(rowId != designRowId) {
            "Draft row should not refer to itself as design: contextData=$this"
        }
        require(designRowId == null || designRowId != officialRowId) {
            "Draft row should not refer to the same row as official and design: contextData=$this"
        }
    }

    fun asMainOfficial(): MainOfficialContextData<T> {
        require(dataType == STORED) { "The draft is not stored in DB and can't be published: contextData=$this" }
        return MainOfficialContextData(
            rowId = id, // The official ID points to the row that needs to be written over
            dataType = STORED, // There will always be an existing row to update: the draft-row or the original official
        )
    }
}

data class DesignOfficialContextData<T>(
    override val rowId: DomainId<T>,
    override val officialRowId: DomainId<T>?,
    override val designId: IntId<LayoutDesign>,
    override val dataType: DataType,
) : DesignContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: rowId
    override val draftType: ContextType get() = ContextType.DESIGN
    override val isDesign: Boolean get() = true

    init {
        require(rowId != officialRowId) {
            "Design row should not refer to itself as official: contextData=$this"
        }
    }

    fun asMainDraft(): MainDraftContextData<T> = MainDraftContextData(
        // TODO: GVT-2426 This is the old way of things, but do we actually need to support switching context in temp objects?
        // If datatype is TEMP, the official design doesn't actually exist in DB -> alter the context to desired form
        // Otherwise, we're creating the draft by copying the row -> new ID in a new TEMP object
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = officialRowId,
        designRowId = if (dataType == TEMP) null else rowId,
        dataType = TEMP,
    )

    fun asDesignDraft(): DesignDraftContextData<T> = DesignDraftContextData(
        // TODO: GVT-2426 This is the old way of things, but do we actually need to support switching context in temp objects?
        // If datatype is TEMP, the official design doesn't actually exist in DB -> alter the context to desired form
        // Otherwise, we're creating the draft-design by copying the row -> new ID in a new TEMP object
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = officialRowId,
        designRowId = if (dataType == TEMP) null else rowId,
        designId = designId,
        dataType = TEMP,
    )
}

data class DesignDraftContextData<T>(
    override val rowId: DomainId<T>,
    override val designRowId: DomainId<T>?,
    override val officialRowId: DomainId<T>?,
    override val designId: IntId<LayoutDesign>,
    override val dataType: DataType,
) : DesignContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId
    override val draftType: ContextType
        get() = if (designRowId == null) ContextType.NEW_DESIGN_DRAFT else ContextType.EDITED_DESIGN_DRAFT
    override val isDraft: Boolean get() = true
    override val isDesign: Boolean get() = true

    init {
        require(rowId != officialRowId) {
            "DesignDraft row should not refer to itself as official: contextData=$this"
        }
        require(rowId != designRowId) {
            "DesignDraft row should not refer to itself as design: contextData=$this"
        }
        require(designRowId == null || designRowId != officialRowId) {
            "DesignDraft row should not refer to the same row as official and design: contextData=$this"
        }
    }

    fun asDesignOfficial(): DesignOfficialContextData<T> {
        require(dataType == STORED) { "The design draft is not stored in DB and can't be published: context=$this" }
        return DesignOfficialContextData(
            rowId = designRowId ?: rowId, // The publishing should update either the official or the draft row
            officialRowId = officialRowId,
            designId = designId,
            dataType = STORED, // There will always be an existing row to update: the draft-row or the original official
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : LayoutConcept<T>> asMainDraft(item: LayoutConcept<T>): T = when (item) {
    is LocationTrack -> asMainDraft(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutSwitch -> asMainDraft(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutKmPost -> asMainDraft(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutTrackNumber -> asMainDraft(item) { contextData -> item.copy(contextData = contextData) }
    is ReferenceLine -> asMainDraft(item) { contextData -> item.copy(contextData = contextData) }
} as T

@Suppress("UNCHECKED_CAST")
fun <T : LayoutConcept<T>> asMainOfficial(item: LayoutConcept<T>): T = when (item) {
    is LocationTrack -> asMainOfficial(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutSwitch -> asMainOfficial(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutKmPost -> asMainOfficial(item) { contextData -> item.copy(contextData = contextData) }
    is TrackLayoutTrackNumber -> asMainOfficial(item) { contextData -> item.copy(contextData = contextData) }
    is ReferenceLine -> asMainOfficial(item) { contextData -> item.copy(contextData = contextData) }
} as T

private fun <T : LayoutConcept<T>> asMainDraft(item: T, withContext: (LayoutContextData<T>) -> T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is MainDraftContextData -> item
            is MainOfficialContextData -> withContext(ctx.asMainDraft())
            is DesignOfficialContextData -> withContext(ctx.asMainDraft())
            is DesignDraftContextData -> error {
                "Creating main branch draft from a design-draft is not supported (publish design first): id=${item.id}"
            }
        }
    }

private fun <T : LayoutConcept<T>> asMainOfficial(item: T, withContext: (LayoutContextData<T>) -> T): T =
    item.contextData.let { ctx ->
        if (ctx is MainDraftContextData) {
            withContext(ctx.asMainOfficial())
        } else error {
            "${item::class.simpleName} is not a main context draft and can't be published: id=${item.id} dataType=${item.dataType}"
        }
    }
