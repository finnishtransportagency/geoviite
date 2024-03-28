package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.logging.Loggable

enum class EditState { UNEDITED, EDITED, CREATED }

interface LayoutContextAware<T> {
    val id: DomainId<T>
    val dataType: DataType
    val editState: EditState

    @get:JsonIgnore
    val isDraft: Boolean get() = false

    @get:JsonIgnore
    val isOfficial: Boolean get() = false

    @get:JsonIgnore
    val isDesign: Boolean get() = false
}

sealed class LayoutAsset<T : LayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {
    abstract val version: RowVersion<T>?

    @get:JsonIgnore
    abstract val contextData: LayoutContextData<T>

    abstract fun withContext(contextData: LayoutContextData<T>): T
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {

    @get:JsonIgnore abstract val rowId: DomainId<T>

    @get:JsonIgnore
    open val officialRowId: DomainId<T>? get() = null

    @get:JsonIgnore
    open val designRowId: DomainId<T>? get() = null

    protected fun requireStored() = require(dataType == STORED) {
        "Only $STORED rows can transition to a different context: context=${this::class.simpleName} dataType=$dataType"
    }

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
    override val editState: EditState get() = EditState.UNEDITED
    override val isOfficial: Boolean get() = true

    fun asMainDraft(): MainDraftContextData<T> {
        requireStored()
        return MainDraftContextData(
            rowId = StringId(),
            officialRowId = rowId,
            designRowId = null,
            dataType = TEMP,
        )
    }

    fun asDesignDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> {
        requireStored()
        return DesignDraftContextData(
            rowId = StringId(),
            officialRowId = rowId,
            designRowId = null,
            designId = designId,
            dataType = TEMP,
        )
    }
}

data class MainDraftContextData<T>(
    override val rowId: DomainId<T>,
    override val officialRowId: DomainId<T>?,
    override val designRowId: DomainId<T>?,
    override val dataType: DataType,
) : MainContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId
    override val editState: EditState get() = if (officialRowId != null) EditState.EDITED else EditState.CREATED
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
        requireStored()
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
    override val editState: EditState get() = EditState.UNEDITED
    override val isDesign: Boolean get() = true

    init {
        require(rowId != officialRowId) {
            "Design row should not refer to itself as official: contextData=$this"
        }
    }

    fun asMainDraft(): MainDraftContextData<T> {
        requireStored()
        return MainDraftContextData(
            rowId = StringId(),
            officialRowId = officialRowId,
            designRowId = rowId,
            dataType = TEMP,
        )
    }

    fun asDesignDraft(): DesignDraftContextData<T> {
        requireStored()
        return DesignDraftContextData(
            rowId = StringId(),
            officialRowId = officialRowId,
            designRowId = rowId,
            designId = designId,
            dataType = TEMP,
        )
    }
}

data class DesignDraftContextData<T>(
    override val rowId: DomainId<T>,
    override val designRowId: DomainId<T>?,
    override val officialRowId: DomainId<T>?,
    override val designId: IntId<LayoutDesign>,
    override val dataType: DataType,
) : DesignContextData<T>() {
    override val id: DomainId<T> get() = officialRowId ?: designRowId ?: rowId
    override val editState: EditState get() = if (designRowId != null) EditState.EDITED else EditState.CREATED
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
        requireStored()
        return DesignOfficialContextData(
            rowId = designRowId ?: rowId, // The publishing should update either the official or the draft row
            officialRowId = officialRowId,
            designId = designId,
            dataType = STORED, // There will always be an existing row to update: the draft-row or the original official
        )
    }
}

fun <T : LayoutAsset<T>> asMainDraft(item: T): T = item.contextData.let { ctx ->
    when (ctx) {
        is MainDraftContextData -> item
        is MainOfficialContextData -> item.withContext(ctx.asMainDraft())
        is DesignOfficialContextData -> item.withContext(ctx.asMainDraft())
        is DesignDraftContextData -> error {
            "Creating main branch draft from a design-draft is not supported (publish design first): item=$item"
        }
    }
}

fun <T : LayoutAsset<T>> asMainOfficial(item: T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is MainOfficialContextData -> item
            is MainDraftContextData -> item.withContext(ctx.asMainOfficial())
            else -> error {
                "Creating main branch official from a design is not supported (create main draft first): item=$item"
            }
        }
    }
