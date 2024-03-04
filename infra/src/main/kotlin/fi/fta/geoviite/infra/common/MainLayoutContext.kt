package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.util.formatForException

enum class PublicationState { OFFICIAL, DRAFT }

sealed class LayoutContext {
    companion object {
        @JvmStatic
        protected val SEPARATOR = "_"

        fun tryParse(value: String): LayoutContext? =
            if (value.startsWith(MainLayoutContext.PREFIX)) MainLayoutContext.parse(value)
            else if (value.startsWith(DesignLayoutContext.PREFIX)) DesignLayoutContext.parse(value)
            else null

        @JvmStatic
        @JsonCreator
        fun parse(value: String): LayoutContext = requireNotNull(tryParse(value)) {
            "Value is not a ${LayoutContext::class.simpleName}: ${formatForException(value)}"
        }
    }
}

@Suppress("DataClassPrivateConstructor")
data class MainLayoutContext private constructor(val type: PublicationState) : LayoutContext() {
    val name: String get() = type.name

    private val stringFormat: String = "$PREFIX$name"

    companion object {
        val PREFIX = "L$SEPARATOR"

        val OFFICIAL: MainLayoutContext = MainLayoutContext(PublicationState.OFFICIAL)
        val DRAFT: MainLayoutContext = MainLayoutContext(PublicationState.DRAFT)

        fun tryParse(string: String): MainLayoutContext? =
            if (string == OFFICIAL.stringFormat) OFFICIAL
            else if (string == DRAFT.stringFormat) DRAFT
            else null

        @JvmStatic
        @JsonCreator
        fun parse(string: String): MainLayoutContext = requireNotNull(tryParse(string)) {
            "Value is not a ${MainLayoutContext::class.simpleName}: ${formatForException(string)}"
        }

        fun of(type: PublicationState): MainLayoutContext = when (type) {
            PublicationState.OFFICIAL -> OFFICIAL
            PublicationState.DRAFT -> DRAFT
        }
    }

    @JsonValue
    override fun toString(): String = stringFormat
}

data class DesignLayoutContext(
    val type: PublicationState,
    val designId: IntId<LayoutDesign>,
) : LayoutContext() {
    companion object {
        val PREFIX = "D${SEPARATOR}"

        fun tryParse(string: String): DesignLayoutContext? {
            val rest = if (string.startsWith(PREFIX)) string.substring(PREFIX.length) else null
            return rest?.let { r ->
                val type = PublicationState.entries.firstOrNull { v -> r.startsWith("${v.name}_") }
                val id = type?.let { t -> stringToIntId<LayoutDesign>(r.substring(t.name.length + 1)) }
                if (type != null && id != null) DesignLayoutContext(type, id) else null
            }
        }

        @JvmStatic
        @JsonCreator
        fun parse(string: String): DesignLayoutContext = requireNotNull(tryParse(string)) {
            "Value is not a ${DesignLayoutContext::class.simpleName}: ${formatForException(string)}"
        }
    }

    @JsonValue
    override fun toString(): String = "$PREFIX$type$SEPARATOR$designId"
}
