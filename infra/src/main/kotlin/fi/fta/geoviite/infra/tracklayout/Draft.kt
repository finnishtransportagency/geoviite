package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId

enum class DraftType {
    OFFICIAL, EDITED_DRAFT, NEW_DRAFT
}

data class Draft<T>(val draftRowId: DomainId<T> = StringId())

interface Draftable<T> {
    val id: DomainId<T>
    val dataType: DataType
    val draft: Draft<T>?

    fun getDraftType() =
        if (draft == null) DraftType.OFFICIAL
        else if (draft!!.draftRowId == id) DraftType.NEW_DRAFT
        else DraftType.EDITED_DRAFT
}

fun draft(trackNumber: LayoutTrackNumber): LayoutTrackNumber =
    draft(trackNumber) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(locationTrack: LocationTrack): LocationTrack =
    draft(locationTrack) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(referenceLine: ReferenceLine): ReferenceLine =
    draft(referenceLine) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(switch: TrackLayoutSwitch): TrackLayoutSwitch =
    draft(switch) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun draft(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
    draft(kmPost) { orig, draft, dataType -> orig.copy(draft = draft, dataType = dataType) }

fun published(trackNumber: LayoutTrackNumber): LayoutTrackNumber =
    published(trackNumber) { orig, draft -> orig.copy(draft = draft) }

fun published(referenceLine: ReferenceLine): ReferenceLine =
    published(referenceLine) { orig, draft -> orig.copy(draft = draft) }

fun published(locationTrack: LocationTrack): LocationTrack =
    published(locationTrack) { orig, draft -> orig.copy(draft = draft) }

fun published(switch: TrackLayoutSwitch): TrackLayoutSwitch =
    published(switch) { orig, draft -> orig.copy(draft = draft) }

fun published(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
    published(kmPost) { orig, draft -> orig.copy(draft = draft) }

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
