package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.ratko.model.RatkoPlanId
import fi.fta.geoviite.infra.util.StringSanitizer
import java.time.LocalDate

enum class DesignState {
    ACTIVE,
    DELETED,
    COMPLETED,
}

data class LayoutDesignName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LayoutDesignName>, CharSequence by value {
    companion object {
        const val ALLOWED_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-+_!?.,\"/()<>:&*#€$"
        val allowedLength = 1..100
        val sanitizer = StringSanitizer(LayoutDesignName::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        require(!value.startsWith(' ') && !value.endsWith(' '))
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LayoutDesignName): Int = value.compareTo(other.value)
}

data class LayoutDesign(
    val id: DomainId<LayoutDesign>,
    val ratkoId: RatkoPlanId?,
    val name: LayoutDesignName,
    val estimatedCompletion: LocalDate,
    val designState: DesignState,
)

data class LayoutDesignSaveRequest(
    val name: LayoutDesignName,
    val estimatedCompletion: LocalDate,
    val designState: DesignState,
)
