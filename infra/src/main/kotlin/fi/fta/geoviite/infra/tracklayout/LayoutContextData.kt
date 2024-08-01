package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.STORED
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.logging.Loggable

enum class EditState { UNEDITED, EDITED, CREATED }

interface LayoutContextAware<T> {
    val id: DomainId<T>
    val version: LayoutRowVersion<T>?
    val dataType: DataType
    val editState: EditState
    val branch: LayoutBranch

    @get:JsonIgnore
    val isDraft: Boolean get() = false

    @get:JsonIgnore
    val isOfficial: Boolean get() = false

    @get:JsonIgnore
    val isDesign: Boolean get() = false
}

sealed class ContextIdentity<T> {
    abstract val rowId: LayoutRowId<T>?
}

data class UnsavedIdentity<T>(
    val sourceRowVersion: LayoutRowVersion<T>?,
    val tempId: StringId<T> = StringId(),
) : ContextIdentity<T>() {
    override val rowId: LayoutRowId<T>? get() = null
}

data class OverwritingIdentity<T>(
    val targetRowId: LayoutRowId<T>,
    val sourceRowVersion: LayoutRowVersion<T>
) : ContextIdentity<T>() {
    override val rowId: LayoutRowId<T> get() = targetRowId
}

data class StoredIdentity<T>(
    val version: LayoutRowVersion<T>
) : ContextIdentity<T>() {
    override val rowId: LayoutRowId<T> get() = version.rowId
}

sealed class LayoutAsset<T : LayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {
    @get:JsonIgnore
    abstract val contextData: LayoutContextData<T>

    abstract fun withContext(contextData: LayoutContextData<T>): T
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {

    @get:JsonIgnore
    abstract val contextRowIdentity: ContextIdentity<T>

    @get:JsonIgnore
    open val officialRowId: LayoutRowId<T>? get() = null

    @get:JsonIgnore
    open val designRowId: LayoutRowId<T>? get() = null

    @get:JsonIgnore
    val rowId: LayoutRowId<T>? get() = contextRowIdentity.rowId

    override val version: LayoutRowVersion<T>? get() = (contextRowIdentity as? StoredIdentity)?.version

    final override val id: DomainId<T> by lazy {
        (officialRowId ?: designRowId ?: rowId)
            ?.let { rowId -> IntId(rowId.intValue) }
            ?: (contextRowIdentity as UnsavedIdentity).tempId
    }

    final override val dataType get() = if (rowId == null) TEMP else STORED

    override val branch get() = designId?.let(LayoutBranch::design) ?: LayoutBranch.main

    @get:JsonIgnore
    open val designId: IntId<LayoutDesign>? get() = null

    protected fun requireStoredRowVersion() = contextRowIdentity.let { current ->
        if (current is StoredIdentity) current.version
        else error {
            "Only $STORED rows can transition to a different context: context=${this::class.simpleName} dataType=$dataType"
        }
    }

    companion object {
        fun <T : LayoutAsset<T>> new(context: LayoutContext): LayoutContextData<T> =
            when (context.state) {
                DRAFT -> newDraft(context.branch)
                OFFICIAL -> newOfficial(context.branch)
            }

        fun <T : LayoutAsset<T>> newDraft(branch: LayoutBranch): LayoutContextData<T> = when (branch) {
            is MainBranch -> MainDraftContextData(
                contextRowIdentity = UnsavedIdentity(null),
                officialRowId = null,
                designRowId = null,
            )
            is DesignBranch -> DesignDraftContextData(
                contextRowIdentity = UnsavedIdentity(null),
                designRowId = null,
                officialRowId = null,
                designId = branch.designId,
            )
        }

        fun <T : LayoutAsset<T>> newOfficial(branch: LayoutBranch): LayoutContextData<T> = when (branch) {
            is MainBranch -> MainOfficialContextData(
                contextRowIdentity = UnsavedIdentity(null),
            )
            is DesignBranch -> DesignOfficialContextData(
                contextRowIdentity = UnsavedIdentity(null),
                officialRowId = null,
                designId = branch.designId,
            )
        }
    }
}

sealed class MainContextData<T> : LayoutContextData<T>()

sealed class DesignContextData<T> : LayoutContextData<T>() {
    abstract override val designId: IntId<LayoutDesign>
}

data class MainOfficialContextData<T>(
    override val contextRowIdentity: ContextIdentity<T>,
) : MainContextData<T>() {

    override val editState: EditState get() = EditState.UNEDITED
    override val isOfficial: Boolean get() = true

    fun asMainDraft(): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(
            contextRowIdentity = UnsavedIdentity(ownRowVersion),
            officialRowId = ownRowVersion.rowId,
            designRowId = null,
        )
    }

    fun asDesignDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            contextRowIdentity = UnsavedIdentity(ownRowVersion),
            officialRowId = ownRowVersion.rowId,
            designRowId = null,
            designId = designId,
        )
    }
}

data class MainDraftContextData<T>(
    override val contextRowIdentity: ContextIdentity<T>,
    override val officialRowId: LayoutRowId<T>?,
    override val designRowId: LayoutRowId<T>?,
) : MainContextData<T>() {
    override val editState: EditState get() = if (officialRowId != null) EditState.EDITED else EditState.CREATED
    override val isDraft: Boolean get() = true

    init {
        requireUniqueRowIds(this)
    }

    fun asMainOfficial(): MainOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainOfficialContextData(
            OverwritingIdentity(
                // At publish, we update the first known row for the asset, making it main-official if it wasn't
                targetRowId = officialRowId ?: designRowId ?: ownRowVersion.rowId,
                sourceRowVersion = ownRowVersion,
            )
        )
    }
}

data class DesignOfficialContextData<T>(
    override val contextRowIdentity: ContextIdentity<T>,
    override val officialRowId: LayoutRowId<T>?,
    override val designId: IntId<LayoutDesign>,
) : DesignContextData<T>() {
    override val editState: EditState get() = EditState.UNEDITED
    override val isOfficial: Boolean get() = true
    override val isDesign: Boolean get() = true

    init {
        requireUniqueRowIds(this)
    }

    fun asMainDraft(overwriteRowId: LayoutRowId<T>?): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(
            contextRowIdentity = if (overwriteRowId == null) {
                UnsavedIdentity(ownRowVersion)
            } else {
                OverwritingIdentity(targetRowId = overwriteRowId, sourceRowVersion = ownRowVersion)
            },
            officialRowId = officialRowId,
            designRowId = ownRowVersion.rowId,
        )
    }

    fun asDesignDraft(): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            contextRowIdentity = UnsavedIdentity(ownRowVersion),
            officialRowId = officialRowId,
            designRowId = ownRowVersion.rowId,
            designId = designId,
        )
    }
}

data class DesignDraftContextData<T>(
    override val contextRowIdentity: ContextIdentity<T>,
    override val designRowId: LayoutRowId<T>?,
    override val officialRowId: LayoutRowId<T>?,
    override val designId: IntId<LayoutDesign>,
) : DesignContextData<T>() {
    override val editState: EditState get() = if (designRowId != null) EditState.EDITED else EditState.CREATED
    override val isDraft: Boolean get() = true
    override val isDesign: Boolean get() = true

    init {
        requireUniqueRowIds(this)
    }

    fun asDesignOfficial(): DesignOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignOfficialContextData(
            contextRowIdentity = OverwritingIdentity(
                // The publishing should update either the official or the draft row in the design (not main official)
                targetRowId = designRowId ?: ownRowVersion.rowId,
                sourceRowVersion = ownRowVersion,
            ),
            officialRowId = officialRowId,
            designId = designId,
        )
    }
}

fun <T : LayoutAsset<T>> asDraft(branch: LayoutBranch, item: T): T = when (branch) {
    is MainBranch -> asMainDraft(item)
    is DesignBranch -> asDesignDraft(item, branch.designId)
}

fun <T : LayoutAsset<T>> asMainDraft(item: T): T = item.contextData.let { ctx ->
    when (ctx) {
        is MainDraftContextData -> item
        is MainOfficialContextData -> item.withContext(ctx.asMainDraft())
        is DesignOfficialContextData -> item.withContext(ctx.asMainDraft(null))
        is DesignDraftContextData -> error(
            "Creating a main-draft from a design-draft is not supported (publish the design first): item=$item"
        )
    }
}

fun <T : LayoutAsset<T>> asOverwritingMainDraft(item: T, overwriteRowId: LayoutRowId<T>): T = item.contextData.let { ctx ->
    when (ctx) {
        is DesignOfficialContextData -> item.withContext(ctx.asMainDraft(overwriteRowId))
        else -> error(
            "Creating an overwriting main-draft (a merge of drafts) is only possible from an official design: item=$item"
        )
    }
}

fun <T : LayoutAsset<T>> asOfficial(branch: LayoutBranch, item: T): T = when (branch) {
    is MainBranch -> asMainOfficial(item)
    is DesignBranch -> asDesignOfficial(item, branch.designId)
}

fun <T : LayoutAsset<T>> asMainOfficial(item: T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is MainOfficialContextData -> item
            is MainDraftContextData -> item.withContext(ctx.asMainOfficial())
            else -> error(
                "Creating a main-official from a design is not supported (create a main-draft first): item=$item"
            )
        }
    }

fun <T : LayoutAsset<T>> asDesignOfficial(item: T, designId: IntId<LayoutDesign>): T = item.contextData.let { ctx ->
    require(ctx.designId == null || ctx.designId == designId) {
        "Creating a design-official from another design is not supported: item=$item designId=$designId"
    }
    when (ctx) {
        is DesignDraftContextData -> item.withContext(ctx.asDesignOfficial())
        else -> error(
            "Creating a design-official from the main branch is not supported (create a design draft first): item=$item designId=$designId"
        )
    }
}

fun <T : LayoutAsset<T>> asDesignDraft(item: T, designId: IntId<LayoutDesign>): T = item.contextData.let { ctx ->
    require(ctx.designId == null || ctx.designId == designId) {
        "Creating a design-draft from another design is not supported: item=$item designId=$designId"
    }
    when (ctx) {
        is DesignDraftContextData -> item
        is MainOfficialContextData -> item.withContext(ctx.asDesignDraft(designId))
        is DesignOfficialContextData -> item.withContext(ctx.asDesignDraft())
        is MainDraftContextData -> error(
            "Creating a design-draft from a main-draft is not supported (publish the draft first): item=$item designId=$designId"
        )
    }
}

private fun requireUniqueRowIds(contextData: LayoutContextData<*>) {
    contextData.rowId?.let { r ->
        require(r != contextData.officialRowId) {
            "Draft row should not refer to itself as official: contextData=$contextData"
        }
        require(r != contextData.designRowId) {
            "Draft row should not refer to itself as design: contextData=$contextData"
        }
    }
    contextData.designRowId?.let { dr ->
        require(dr != contextData.officialRowId) {
            "Draft row should not refer to the same row as official and design: contextData=$contextData"
        }
    }
}
