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
    @get:JsonIgnore val rowId: DomainId<T>
    @get:JsonIgnore val isDraft: Boolean
    @get:JsonIgnore val isOfficial: Boolean
    @get:JsonIgnore val isDesign: Boolean
    @get:JsonIgnore val isEditedDraft: Boolean
    @get:JsonIgnore val isNewDraft: Boolean
}

sealed class LayoutConcept<T : LayoutConcept<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {

    abstract val version: RowVersion<T>?
    abstract val contextData: LayoutContextData<T>
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {
    @get:JsonIgnore
    override val isDraft: Boolean get() = this is MainDraftContextData || this is DesignDraftContextData

    @get:JsonIgnore
    override val isOfficial: Boolean get() = this is MainOfficialContextData

    @get:JsonIgnore
    override val isDesign: Boolean get() = this is DesignContextData

    @get:JsonIgnore
    override val isEditedDraft: Boolean
        get() = when (this) {
            is MainDraftContextData -> officialRowId != null
            is DesignDraftContextData -> designRowId != null
            else -> false
        }

    @get:JsonIgnore
    override val isNewDraft: Boolean get() = this.isDraft && !isEditedDraft

    @get:JsonIgnore
    open val officialRowId: DomainId<T>? get() = null

    @get:JsonIgnore
    open val designRowId: DomainId<T>? get() = null

    companion object {
        fun <T> newDraft(id: DomainId<T> = StringId()) = MainDraftContextData(id, null, null, TEMP)
        fun <T> newOfficial(id: DomainId<T> = StringId()) = MainOfficialContextData(id, TEMP)
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

    fun asMainDraft(): MainDraftContextData<T> = MainDraftContextData(
        // TODO: GVT-2426 This is the old way of things, but do we actually need to support switching context in temp objects?
        // If datatype is TEMP, the official row doesn't actually exist in DB -> alter the context to desired form
        // Otherwise, we're creating the draft by copying the row -> new ID in a new TEMP object
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = if (dataType == TEMP) null else rowId,
        designRowId = null,
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

    fun asMainOfficial(): MainOfficialContextData<T> {
        require(dataType == TEMP) { "The draft is not stored in DB and can't be published: contextData=$this" }
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

    fun asDesignOfficial(): DesignOfficialContextData<T> {
        require(dataType == TEMP) { "The design draft is not stored in DB and can't be published: context=$this" }
        return DesignOfficialContextData(
            rowId = designRowId ?: rowId, // The publishing should update either the official or the draft row
            officialRowId = officialRowId,
            designId = designId,
            dataType = STORED, // There will always be an existing row to update: the draft-row or the original official
        )
    }
}

//data class LayoutContextData2<T>(
//    // Database ID of this concept instance
//    val rowId: DomainId<T>,
//    // Reference to the design row that this instance would edit (if a design-draft) or remove on publish (if a draft)
//    val designRowId: DomainId<T>?,
//    // Reference to the official row that this instance would edit (as a published draft or implemented design)
//    val officialRowId: DomainId<T>?,
//    // Reference to the LayoutDesign that this row is a part of (if a design)
//    val designId: IntId<LayoutDesign>?,
//    // Flag indicating if this version is a (new of edited) draft
//    override val isDraft: Boolean,
//) : LayoutContextAware<T> {
//    init {
//        if (isOfficial) require(designRowId == null && officialRowId == null)
//        if (isDesign) require(designId != null)
//    }
//
//    // Official ID of the concept, same across LayoutContexts
//    override val id: DomainId<T> = officialRowId ?: designRowId ?: rowId
//
//    override val isOfficial: Boolean get() = !isDraft && !isDesign
//    override val isDesign: Boolean get() = designId != null
//    override val isEditedDraft: Boolean get() = isDraft && draftOfRowId != null
//    override val isNewDraft: Boolean get() = isDraft && draftOfRowId == null
//    val draftOfRowId: DomainId<T>? get() = if (isDesign) designRowId else officialRowId
//
//    override val contextType: ContextType
//        get() = if (isDesign) when {
//            isNewDraft -> ContextType.NEW_DESIGN_DRAFT
//            isEditedDraft -> ContextType.EDITED_DESIGN_DRAFT
//            else -> ContextType.DESIGN
//        } else when {
//            isNewDraft -> ContextType.NEW_DRAFT
//            isEditedDraft -> ContextType.EDITED_DRAFT
//            else -> ContextType.OFFICIAL
//        }
//}

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
