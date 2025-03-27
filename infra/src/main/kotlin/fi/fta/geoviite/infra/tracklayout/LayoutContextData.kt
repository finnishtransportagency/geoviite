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

interface LayoutContextAware<T : LayoutAsset<T>> {
    val id: DomainId<T>
    val version: LayoutRowVersion<T>?
    val dataType: DataType
    val branch: LayoutBranch

    val isDraft: Boolean
        get() = false

    @get:JsonIgnore
    val isOfficial: Boolean
        get() = false

    @get:JsonIgnore
    val isDesign: Boolean
        get() = false

    @get:JsonIgnore
    val isCancelled: Boolean
        get() = false

    @get:JsonIgnore
    val designAssetState: DesignAssetState?
        get() = null

    @get:JsonIgnore
    val layoutContext: LayoutContext
        get() = LayoutContext.of(branch, if (isDraft) DRAFT else OFFICIAL)
}

sealed class LayoutAssetId<T : LayoutAsset<T>> {
    abstract val id: DomainId<T>
    abstract val version: LayoutRowVersion<T>?
}

class TemporaryAssetId<T : LayoutAsset<T>> : LayoutAssetId<T>() {
    // Note: this is not a data class because the id is lazily initialized
    // As the ID is unique for each instance, we can also rely on basic Java `equals` and `hashCode` implementations
    override val id: DomainId<T> by lazy { StringId() }
    override val version = null
}

data class IdentifiedAssetId<T : LayoutAsset<T>>(override val id: IntId<T>) : LayoutAssetId<T>() {
    override val version = null
}

data class EditedAssetId<T : LayoutAsset<T>>(val sourceRowVersion: LayoutRowVersion<T>) : LayoutAssetId<T>() {
    override val id: IntId<T>
        get() = sourceRowVersion.id

    override val version = null
}

data class StoredAssetId<T : LayoutAsset<T>>(override val version: LayoutRowVersion<T>) : LayoutAssetId<T>() {
    override val id: IntId<T>
        get() = version.id
}

sealed class LayoutContextData<T : LayoutAsset<T>> : LayoutContextAware<T> {
    @get:JsonIgnore abstract val layoutAssetId: LayoutAssetId<T>

    override val version: LayoutRowVersion<T>?
        get() = layoutAssetId.version

    final override val id: DomainId<T>
        get() = layoutAssetId.id

    final override val dataType
        get() = if (layoutAssetId is StoredAssetId) STORED else TEMP

    override val branch
        get() = designId?.let(LayoutBranch::design) ?: LayoutBranch.main

    @get:JsonIgnore
    open val designId: IntId<LayoutDesign>?
        get() = null

    @get:JsonIgnore
    open val originBranch: LayoutBranch?
        get() = null

    protected fun requireStoredRowVersion() =
        layoutAssetId.let { current ->
            if (current is StoredAssetId) current.version
            else
                error(
                    "Only $STORED rows can transition to a different context: context=${this::class.simpleName} dataType=$dataType"
                )
        }

    companion object {
        fun <T : LayoutAsset<T>> new(context: LayoutContext, id: IntId<T>?): LayoutContextData<T> =
            when (context.state) {
                DRAFT -> newDraft(context.branch, id = id)
                OFFICIAL -> newOfficial(context.branch, id = id)
            }

        fun <T : LayoutAsset<T>> newDraft(branch: LayoutBranch, id: IntId<T>?): LayoutContextData<T> =
            when (branch) {
                is MainBranch ->
                    MainDraftContextData(
                        layoutAssetId = id?.let(::IdentifiedAssetId) ?: TemporaryAssetId(),
                        originBranch = LayoutBranch.main,
                    )
                is DesignBranch ->
                    DesignDraftContextData(
                        layoutAssetId = id?.let(::IdentifiedAssetId) ?: TemporaryAssetId(),
                        designId = branch.designId,
                        designAssetState = DesignAssetState.OPEN,
                    )
            }

        fun <T : LayoutAsset<T>> newOfficial(branch: LayoutBranch, id: IntId<T>? = null): LayoutContextData<T> =
            when (branch) {
                is MainBranch ->
                    MainOfficialContextData(layoutAssetId = id?.let { IdentifiedAssetId(id) } ?: TemporaryAssetId())
                is DesignBranch ->
                    DesignOfficialContextData(
                        layoutAssetId = id?.let { IdentifiedAssetId(id) } ?: TemporaryAssetId(),
                        designId = branch.designId,
                        designAssetState = DesignAssetState.OPEN,
                    )
            }
    }
}

sealed class MainContextData<T : LayoutAsset<T>> : LayoutContextData<T>()

sealed class DesignContextData<T : LayoutAsset<T>> : LayoutContextData<T>() {
    abstract override val designId: IntId<LayoutDesign>
    abstract override val designAssetState: DesignAssetState
}

data class MainOfficialContextData<T : LayoutAsset<T>>(override val layoutAssetId: LayoutAssetId<T>) :
    MainContextData<T>() {
    override val isOfficial: Boolean
        get() = true

    fun asMainDraft(): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(layoutAssetId = EditedAssetId(ownRowVersion), originBranch = LayoutBranch.main)
    }

    fun asDesignDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            layoutAssetId = EditedAssetId(ownRowVersion),
            designId = designId,
            designAssetState = DesignAssetState.OPEN,
        )
    }

    fun asCancelledDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> =
        asDesignDraft(designId).copy(designAssetState = DesignAssetState.CANCELLED)
}

data class MainDraftContextData<T : LayoutAsset<T>>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val originBranch: LayoutBranch,
) : MainContextData<T>() {
    override val isDraft: Boolean
        get() = true

    fun asMainOfficial(): MainOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainOfficialContextData(EditedAssetId(sourceRowVersion = ownRowVersion))
    }
}

data class DesignOfficialContextData<T : LayoutAsset<T>>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val designId: IntId<LayoutDesign>,
    override val designAssetState: DesignAssetState,
) : DesignContextData<T>() {
    override val isOfficial: Boolean
        get() = true

    override val isDesign: Boolean
        get() = true

    override val isCancelled: Boolean
        get() = designAssetState == DesignAssetState.CANCELLED

    fun asMainDraft(): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(
            layoutAssetId = EditedAssetId(sourceRowVersion = ownRowVersion),
            originBranch = DesignBranch.of(designId),
        )
    }

    fun asDesignDraft(): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            layoutAssetId = EditedAssetId(ownRowVersion),
            designId = designId,
            designAssetState = designAssetState,
        )
    }

    fun cancelled(): DesignDraftContextData<T> = asDesignDraft().copy(designAssetState = DesignAssetState.CANCELLED)

    fun completed(): DesignDraftContextData<T> = asDesignDraft().copy(designAssetState = DesignAssetState.COMPLETED)
}

data class DesignDraftContextData<T : LayoutAsset<T>>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val designId: IntId<LayoutDesign>,
    override val designAssetState: DesignAssetState,
) : DesignContextData<T>() {

    override val isDraft: Boolean
        get() = true

    override val isDesign: Boolean
        get() = true

    override val isCancelled: Boolean
        get() = designAssetState == DesignAssetState.CANCELLED

    fun asDesignOfficial(): DesignOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignOfficialContextData(
            layoutAssetId = EditedAssetId(sourceRowVersion = ownRowVersion),
            designId = designId,
            designAssetState = designAssetState,
        )
    }
}

fun <T : LayoutAsset<T>> asDraft(branch: LayoutBranch, item: T): T =
    when (branch) {
        is MainBranch -> asMainDraft(item)
        is DesignBranch -> asDesignDraft(item, branch.designId)
    }

fun <T : LayoutAsset<T>> asMainDraft(item: T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is MainDraftContextData -> item
            is MainOfficialContextData -> item.withContext(ctx.asMainDraft())
            is DesignOfficialContextData -> item.withContext(ctx.asMainDraft())
            is DesignDraftContextData ->
                error(
                    "Creating a main-draft from a design-draft is not supported (publish the design first): item=$item"
                )
        }
    }

fun <T : LayoutAsset<T>> asOfficial(branch: LayoutBranch, item: T): T =
    when (branch) {
        is MainBranch -> asMainOfficial(item)
        is DesignBranch -> asDesignOfficial(item, branch.designId)
    }

fun <T : LayoutAsset<T>> asMainOfficial(item: T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is MainOfficialContextData -> item
            is MainDraftContextData -> item.withContext(ctx.asMainOfficial())
            else ->
                error("Creating a main-official from a design is not supported (create a main-draft first): item=$item")
        }
    }

fun <T : LayoutAsset<T>> asDesignOfficial(item: T, designId: IntId<LayoutDesign>): T =
    item.contextData.let { ctx ->
        require(ctx.designId == null || ctx.designId == designId) {
            "Creating a design-official from another design is not supported: item=$item designId=$designId"
        }
        when (ctx) {
            is DesignDraftContextData -> item.withContext(ctx.asDesignOfficial())
            else ->
                error(
                    "Creating a design-official from the main branch is not supported (create a design draft first): item=$item designId=$designId"
                )
        }
    }

fun <T : LayoutAsset<T>> asDesignDraft(item: T, designId: IntId<LayoutDesign>): T =
    item.contextData.let { ctx ->
        require(ctx.designId == null || ctx.designId == designId) {
            "Creating a design-draft from another design is not supported: item=$item designId=$designId"
        }
        when (ctx) {
            is DesignDraftContextData -> item
            is MainOfficialContextData -> item.withContext(ctx.asDesignDraft(designId))
            is DesignOfficialContextData -> item.withContext(ctx.asDesignDraft())
            is MainDraftContextData ->
                error(
                    "Creating a design-draft from a main-draft is not supported (publish the draft first): item=$item designId=$designId"
                )
        }
    }

fun <T : LayoutAsset<T>> cancelled(item: T, designId: IntId<LayoutDesign>): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is DesignOfficialContextData -> item.withContext(ctx.cancelled())
            is MainOfficialContextData -> item.withContext(ctx.asCancelledDraft(designId))
            else -> error("The cancellation operation is only allowed for official items")
        }
    }

fun <T : LayoutAsset<T>> completed(item: T): T =
    item.contextData.let { ctx ->
        when (ctx) {
            is DesignOfficialContextData -> item.withContext(ctx.completed())
            else -> error("Only design-official items can be merged to main")
        }
    }
