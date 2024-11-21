package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.util.formatForException
import java.util.concurrent.ConcurrentHashMap

enum class PublicationState {
    OFFICIAL,
    DRAFT,
}

enum class LayoutBranchType {
    MAIN,
    DESIGN,
}

fun assertMainBranch(branch: LayoutBranch) =
    require(branch == LayoutBranch.main) {
        // TODO: Design branch support missing in Ratko integration and splits
        "Design branch use is not yet supported"
    }

sealed class LayoutBranch {
    companion object {
        @JvmStatic protected val separator = '_'

        val main = MainBranch.instance

        fun design(id: IntId<LayoutDesign>) = DesignBranch.of(id)

        fun tryParse(value: String): LayoutBranch? = MainBranch.tryParse(value) ?: DesignBranch.tryParse(value)

        @JvmStatic
        @JsonCreator
        fun parse(value: String): LayoutBranch =
            requireNotNull(tryParse(value)) {
                "Value is not a ${LayoutBranch::class.simpleName}: ${formatForException(value)}"
            }
    }

    val draft by lazy { LayoutContext.of(this, PublicationState.DRAFT) }

    val official by lazy { LayoutContext.of(this, PublicationState.OFFICIAL) }

    open val designId: IntId<LayoutDesign>?
        get() = null
}

class MainBranch private constructor() : LayoutBranch() {
    companion object {
        val type: LayoutBranchType = LayoutBranchType.MAIN
        val instance: MainBranch = MainBranch()

        fun tryParse(value: String): MainBranch? =
            if (value.length == type.name.length && value.uppercase() == type.name) instance else null

        @JvmStatic
        @JsonCreator
        fun parse(value: String): MainBranch =
            requireNotNull(tryParse(value)) {
                "Value is not a ${MainBranch::class.simpleName}: ${formatForException(value)}"
            }
    }

    @JsonValue override fun toString(): String = type.name
}

@Suppress("DataClassPrivateConstructor")
data class DesignBranch private constructor(override val designId: IntId<LayoutDesign>) : LayoutBranch() {
    private val stringFormat by lazy { "$prefix$designId" }

    companion object {
        val type: LayoutBranchType = LayoutBranchType.DESIGN
        val prefix: String = "$type$separator"

        private val branches: ConcurrentHashMap<IntId<LayoutDesign>, DesignBranch> = ConcurrentHashMap()
        private val stringLength: IntRange =
            (IntId.stringLength.first + prefix.length)..(IntId.stringLength.last + prefix.length)

        fun of(designId: IntId<LayoutDesign>): DesignBranch =
            branches.computeIfAbsent(designId) { id -> DesignBranch(id) }

        fun tryParse(value: String): DesignBranch? =
            value
                .takeIf { v -> v.length in stringLength }
                ?.uppercase()
                ?.takeIf { v -> v.startsWith(prefix) }
                ?.let { v -> of(IntId.parse(v.substring(prefix.length))) }

        @JvmStatic
        @JsonCreator
        fun parse(string: String): DesignBranch =
            requireNotNull(tryParse(string)) {
                "Value is not a ${DesignBranch::class.simpleName}: ${formatForException(string)}"
            }
    }

    @JsonValue override fun toString(): String = stringFormat
}

sealed class LayoutContext {
    abstract val branch: LayoutBranch
    abstract val state: PublicationState

    companion object {
        fun of(branch: LayoutBranch, state: PublicationState): LayoutContext =
            when (branch) {
                is MainBranch -> MainLayoutContext.of(state)
                is DesignBranch -> DesignLayoutContext.of(branch.designId, state)
            }
    }

    fun toSqlString() = "${branch.designId?.intValue ?: "main"}_${state.name.lowercase()}"
}

fun parseLayoutContextSqlString(text: String): LayoutContext {
    val split = text.split("_")
    require(split.size == 2) { "LayoutContext string must contain two parts separated by _" }
    val branch = if (split[0] == "main") LayoutBranch.main else DesignBranch.of(IntId(split[0].toInt()))
    val state = PublicationState.entries.find { state -> state.name.lowercase() == split[1] }
    requireNotNull(state) { "Did not recognize ${split[1]} as a publication state" }
    return LayoutContext.of(branch, state)
}

@Suppress("DataClassPrivateConstructor")
data class MainLayoutContext private constructor(override val state: PublicationState) : LayoutContext() {
    override val branch: LayoutBranch = MainBranch.instance

    companion object {
        val official = MainLayoutContext(PublicationState.OFFICIAL)
        val draft = MainLayoutContext(PublicationState.DRAFT)

        fun of(state: PublicationState): MainLayoutContext =
            when (state) {
                PublicationState.OFFICIAL -> official
                PublicationState.DRAFT -> draft
            }
    }
}

@Suppress("DataClassPrivateConstructor")
data class DesignLayoutContext
private constructor(override val branch: DesignBranch, override val state: PublicationState) : LayoutContext() {
    companion object {
        private val contexts: ConcurrentHashMap<Pair<Int, PublicationState>, DesignLayoutContext> = ConcurrentHashMap()

        fun of(id: IntId<LayoutDesign>, state: PublicationState): DesignLayoutContext =
            contexts.computeIfAbsent(id.intValue to state) { DesignLayoutContext(DesignBranch.of(id), state) }

        fun of(branch: DesignBranch, state: PublicationState): DesignLayoutContext =
            contexts.computeIfAbsent(branch.designId.intValue to state) { DesignLayoutContext(branch, state) }
    }
}
