package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Unlike most of the sanitized string classes, this one does not actually enforce any safe content. Instead, it takes
 * in anything given, providing methods to get both the sanitized and unsafe values.
 *
 * This is useful when we cannot enforce the validity of the content but want to store it as-is, for example,
 * integrations.
 */
data class UnsafeString @JsonCreator constructor(val unsafeValue: String) :
    Comparable<UnsafeString>, CharSequence by unsafeValue {

    override fun toString(): String = unsafeValue

    override fun compareTo(other: UnsafeString): Int = unsafeValue.compareTo(other.unsafeValue)

    @JsonValue fun toJson(): String = error("${this::class.simpleName} should not be serialized")
}
