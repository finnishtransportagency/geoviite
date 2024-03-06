package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.logging.Loggable

enum class DraftType {
    OFFICIAL, EDITED_DRAFT, NEW_DRAFT
}

//enum class ContextType {
//    OFFICIAL, NEW_DRAFT, EDITED_DRAFT, DESIGN, NEW_DESIGN_DRAFT, EDITED_DESIGN_DRAFT
//}

//    @get:JsonIgnore val contextType: ContextType
//        get() = when (this) {
//            is OfficialMainContextData -> ContextType.OFFICIAL
//        }
interface LayoutContextAware<T> {
    val id: DomainId<T>
    val dataType: DataType
    @get:JsonIgnore val isDraft: Boolean
    @get:JsonIgnore val isOfficial: Boolean
    @get:JsonIgnore val isDesign: Boolean
    @get:JsonIgnore val isEditedDraft: Boolean
    @get:JsonIgnore val isNewDraft: Boolean
}

sealed class LayoutConcept<T>(contextData: LayoutContextData<T>) : LayoutContextAware<T> by contextData, Loggable {
    abstract val version: RowVersion<LocationTrack>?
    abstract val contextData: LayoutContextData<T>
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {
    @get:JsonIgnore
    override val isDraft: Boolean get() = this is DraftMainContextData || this is DraftDesignContextData

    @get:JsonIgnore
    override val isOfficial: Boolean get() = this is OfficialMainContextData

    @get:JsonIgnore
    override val isDesign: Boolean get() = this is DesignContextData

    @get:JsonIgnore
    override val isEditedDraft: Boolean
        get() = when (this) {
            is DraftMainContextData -> officialRowId != null
            is DraftDesignContextData -> designRowId != null
            else -> false
        }

    @get:JsonIgnore
    override val isNewDraft: Boolean get() = this.isDraft && !isEditedDraft

    companion object {
        fun <T> newDraft(id: DomainId<T>) = DraftMainContextData(id, null, null)
    }
}

sealed class MainContextData<T> : LayoutContextData<T>()

sealed class DesignContextData<T> : LayoutContextData<T>() {
    abstract val designId: IntId<LayoutDesign>
}

data class OfficialMainContextData<T>(
    val rowId: DomainId<T>,
    override val dataType: DataType = TEMP,
) : MainContextData<T>() {
    override val id: DomainId<T> = rowId

    fun asMainDraft(): DraftMainContextData<T> = DraftMainContextData(
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = if (dataType == TEMP) null else rowId,
        designRowId = null,
    )
}

data class DraftMainContextData<T>(
    val rowId: DomainId<T>,
    val officialRowId: DomainId<T>?,
    val designRowId: DomainId<T>?,
    override val dataType: DataType = TEMP,
) : MainContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId

    fun asMainOfficial(): OfficialMainContextData<T> = OfficialMainContextData(
        rowId = id,
        dataType = dataType,
    )
}

data class OfficialDesignContextData<T>(
    val rowId: DomainId<T>,
    val officialRowId: DomainId<T>?,
    override val designId: IntId<LayoutDesign>,
    override val dataType: DataType = TEMP,
) : DesignContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: rowId

    fun asMainDraft(): DraftMainContextData<T> = DraftMainContextData(
        rowId = if (dataType == TEMP) rowId else StringId(),
        officialRowId = officialRowId,
        designRowId = if (dataType == TEMP) null else rowId,
    )
}

data class DraftDesignContextData<T>(
    val rowId: DomainId<T>,
    val designRowId: DomainId<T>?,
    val officialRowId: DomainId<T>?,
    override val designId: IntId<LayoutDesign>,
    override val dataType: DataType = TEMP,
) : DesignContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId
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


data class Draft<T>(val draftRowId: DomainId<T> = StringId())

interface Draftable<T> : Loggable {
    val id: DomainId<T>
    val version: RowVersion<T>?
    val dataType: DataType
    val draft: Draft<T>?

    fun getDraftType() = draft.let { d ->
        if (d == null) DraftType.OFFICIAL
        else if (d.draftRowId == id) DraftType.NEW_DRAFT
        else DraftType.EDITED_DRAFT
    }

    fun isOfficial(): Boolean = draft == null

    fun isDraft(): Boolean = draft != null

    fun isEditedDraft(): Boolean = draft != null && draft?.draftRowId != id

    fun isNewDraft(): Boolean = draft != null && draft?.draftRowId == id
}

fun draft(trackNumber: TrackLayoutTrackNumber): TrackLayoutTrackNumber =
    draft(trackNumber) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(locationTrack: LocationTrack): LocationTrack =
    mainDraft(locationTrack) { orig, contextData -> orig.copy(contextData = contextData) }

fun draft(referenceLine: ReferenceLine): ReferenceLine =
    draft(referenceLine) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(switch: TrackLayoutSwitch): TrackLayoutSwitch =
    draft(switch) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
    draft(kmPost) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun published(trackNumber: TrackLayoutTrackNumber): TrackLayoutTrackNumber =
    published(trackNumber) { orig, draft -> orig.copy(draft = draft) }

fun published(referenceLine: ReferenceLine): ReferenceLine =
    published(referenceLine) { orig, draft -> orig.copy(draft = draft) }

fun published(locationTrack: LocationTrack): LocationTrack =
    officialMain(locationTrack) { orig, contextData -> orig.copy(contextData = contextData) }

fun published(switch: TrackLayoutSwitch): TrackLayoutSwitch =
    published(switch) { orig, draft -> orig.copy(draft = draft) }

fun published(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
    published(kmPost) { orig, draft -> orig.copy(draft = draft) }

private fun <T : LayoutConcept<T>> mainDraft(item: T, copy: (T, LayoutContextData<T>) -> T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is DraftMainContextData -> item
            is OfficialMainContextData -> copy(item, ctx.asMainDraft())
            is OfficialDesignContextData -> copy(item, ctx.asMainDraft())
            is DraftDesignContextData -> error {
                "Creating main branch draft from a design-draft is not supported (publish design first): id=${item.id}"
            }
        }
    }

private fun <T : LayoutConcept<T>> officialMain(item: T, copy: (T, LayoutContextData<T>) -> T): T =
    item.contextData.let { ctx ->
        if (ctx.dataType == TEMP) error {
            "${item::class} is not stored as draft and can't be published: id=${item.id} dataType=${item.dataType}"
        } else if (ctx is DraftMainContextData) {
            copy(item, ctx.asMainOfficial())
        } else error {
            "${item::class.simpleName} is not a draft and can't be published: id=${item.id} dataType=${item.dataType}"
        }
    }

private fun <T : Draftable<T>> draft(draftable: T, copy: (T, Draft<T>, DataType) -> T): T =
    if (draftable.draft != null) draftable
    else if (draftable.dataType == TEMP) copy(draftable, Draft(draftable.id), TEMP)
    else copy(draftable, Draft(), TEMP)

private fun <T : Draftable<T>> published(draft: T, copy: (T, Draft<T>?) -> T) =
    if (draft.draft == null) throw IllegalArgumentException(
        "${draft::class} is not a draft and can't be published: id=${draft.id} dataType=${draft.dataType}"
    )
    else if (draft.dataType == TEMP) throw IllegalArgumentException(
        "${draft::class} is not stored as draft and can't be published: id=${draft.id} dataType=${draft.dataType}"
    )
    else copy(draft, null)
