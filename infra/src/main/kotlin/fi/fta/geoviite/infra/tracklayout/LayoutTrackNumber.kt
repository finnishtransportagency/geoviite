package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import java.time.Instant

data class LayoutTrackNumber(
    val number: TrackNumber,
    val description: TrackNumberDescription,
    val state: LayoutState,
    @JsonIgnore override val contextData: LayoutContextData<LayoutTrackNumber>,
    @JsonIgnore val referenceLineId: IntId<ReferenceLine>? = null,
) : LayoutAsset<LayoutTrackNumber>(contextData) {
    @JsonIgnore val exists = !state.isRemoved()

    init {
        require(description.isNotBlank()) { "TrackNumber should have a non-blank description" }
        require(description.length < 100) { "TrackNumber description too long: ${description.length}>100" }
    }

    override fun toLog(): String =
        logFormat("id" to id, "version" to version, "context" to contextData::class.simpleName, "number" to number)

    override fun withContext(contextData: LayoutContextData<LayoutTrackNumber>): LayoutTrackNumber =
        copy(contextData = contextData)
}

data class TrackNumberAndChangeTime(val id: IntId<LayoutTrackNumber>, val number: TrackNumber, val changeTime: Instant)
