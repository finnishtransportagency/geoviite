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

interface LayoutContextAware<T> {
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
    val layoutContext: LayoutContext
        get() = LayoutContext.of(branch, if (isDraft) DRAFT else OFFICIAL)
}

sealed class LayoutAssetId<T> {
    abstract val id: DomainId<T>
    abstract val version: LayoutRowVersion<T>?
}

class TemporaryAssetId<T> : LayoutAssetId<T>() {
    override val id: DomainId<T> by lazy { StringId() }
    override val version = null
}

data class IdentifiedAssetId<T>(override val id: IntId<T>) : LayoutAssetId<T>() {
    override val version = null
}

data class EditedAssetId<T>(val sourceRowVersion: LayoutRowVersion<T>) : LayoutAssetId<T>() {
    override val id: IntId<T>
        get() = sourceRowVersion.id

    override val version = null
}

data class StoredAssetId<T>(override val version: LayoutRowVersion<T>) : LayoutAssetId<T>() {
    override val id: IntId<T>
        get() = version.id
}

sealed class LayoutAsset<T : LayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {
    @get:JsonIgnore abstract val contextData: LayoutContextData<T>

    val hasOfficial: Boolean
        get() = contextData.hasOfficial

    abstract fun withContext(contextData: LayoutContextData<T>): T
}

sealed class LayoutContextData<T> : LayoutContextAware<T> {
    @get:JsonIgnore abstract val hasOfficial: Boolean
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
                        layoutAssetId = TemporaryAssetId(),
                        hasOfficial = false,
                        originBranch = LayoutBranch.main,
                    )
                is DesignBranch ->
                    DesignDraftContextData(
                        layoutAssetId = id?.let(::IdentifiedAssetId) ?: TemporaryAssetId(),
                        designId = branch.designId,
                        cancelled = false,
                        hasOfficial = false,
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
                        cancelled = false,
                    )
            }
    }
}

sealed class MainContextData<T> : LayoutContextData<T>()

sealed class DesignContextData<T> : LayoutContextData<T>() {
    abstract override val designId: IntId<LayoutDesign>
    abstract val cancelled: Boolean
}

data class MainOfficialContextData<T>(override val layoutAssetId: LayoutAssetId<T>) : MainContextData<T>() {
    override val hasOfficial: Boolean
        get() = true

    override val isOfficial: Boolean
        get() = true

    fun asMainDraft(): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(
            layoutAssetId = EditedAssetId(ownRowVersion),
            hasOfficial = true,
            originBranch = LayoutBranch.main,
        )
    }

    fun asDesignDraft(designId: IntId<LayoutDesign>): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            layoutAssetId = EditedAssetId(ownRowVersion),
            designId = designId,
            cancelled = false,
            hasOfficial = true,
        )
    }
}

data class MainDraftContextData<T>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val hasOfficial: Boolean,
    val originBranch: LayoutBranch,
) : MainContextData<T>() {
    override val isDraft: Boolean
        get() = true

    fun asMainOfficial(): MainOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainOfficialContextData(EditedAssetId(sourceRowVersion = ownRowVersion))
    }
}

data class DesignOfficialContextData<T>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val designId: IntId<LayoutDesign>,
    override val cancelled: Boolean,
) : DesignContextData<T>() {
    override val hasOfficial: Boolean
        get() = true

    override val isOfficial: Boolean
        get() = true

    override val isDesign: Boolean
        get() = true

    override val isCancelled: Boolean
        get() = cancelled

    fun asMainDraft(): MainDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return MainDraftContextData(
            layoutAssetId = EditedAssetId(sourceRowVersion = ownRowVersion),
            hasOfficial = true,
            originBranch = DesignBranch.of(designId),
        )
    }

    fun asDesignDraft(): DesignDraftContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignDraftContextData(
            layoutAssetId = EditedAssetId(ownRowVersion),
            designId = designId,
            cancelled = cancelled,
            hasOfficial = true,
        )
    }

    fun cancelled(): DesignDraftContextData<T> = asDesignDraft().copy(cancelled = true)
}

data class DesignDraftContextData<T>(
    override val layoutAssetId: LayoutAssetId<T>,
    override val designId: IntId<LayoutDesign>,
    override val cancelled: Boolean,
    override val hasOfficial: Boolean,
) : DesignContextData<T>() {

    override val isDraft: Boolean
        get() = true

    override val isDesign: Boolean
        get() = true

    override val isCancelled: Boolean
        get() = cancelled

    fun asDesignOfficial(): DesignOfficialContextData<T> {
        val ownRowVersion = requireStoredRowVersion()
        return DesignOfficialContextData(
            layoutAssetId = EditedAssetId(sourceRowVersion = ownRowVersion),
            designId = designId,
            cancelled = cancelled,
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

fun <T : LayoutAsset<T>> cancelled(item: T): T =
    item.withContext(
        (item.contextData as? DesignOfficialContextData)?.cancelled()
            ?: error("The cancellation operation is only allowed for design-official items")
    )
