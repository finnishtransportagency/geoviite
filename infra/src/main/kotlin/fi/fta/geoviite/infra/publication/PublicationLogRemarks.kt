package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.roundTo1Decimal
import kotlin.math.abs

fun publicationChangeRemark(translation: Translation, key: String, value: String? = null) =
    translation.t("publication-details-table.remark.$key", localizationParams("value" to value))

fun getLengthChangedRemarkOrNull(translation: Translation, length1: Double?, length2: Double?) =
    if (length1 == null || length2 == null) {
        null
    } else {
        (length2 - length1).let { directionalLengthDifference ->
            when {
                abs(directionalLengthDifference) <= DISTANCE_CHANGE_THRESHOLD -> null

                (directionalLengthDifference < 0) ->
                    publicationChangeRemark(
                        translation,
                        "shortened-x-meters",
                        formatDistance(abs(directionalLengthDifference)),
                    )

                (directionalLengthDifference > 0) ->
                    publicationChangeRemark(
                        translation,
                        "lengthened-x-meters",
                        formatDistance(abs(directionalLengthDifference)),
                    )

                else -> null
            }
        }
    }

fun getPointMovedRemarkOrNull(
    translation: Translation,
    oldPoint: Point?,
    newPoint: Point?,
    translationKey: String = "moved-x-meters",
): String? =
    if (oldPoint != null && newPoint != null && !pointsAreSame(oldPoint, newPoint)) {
        // For the remarks, pythagorean distance is accurate enough
        val distance = lineLength(oldPoint, newPoint)
        if (distance > DISTANCE_CHANGE_THRESHOLD) {
            publicationChangeRemark(translation, translationKey, formatDistance(distance))
        } else {
            null
        }
    } else {
        null
    }

fun getAddressMovedRemarkOrNull(translation: Translation, change: Change<TrackMeter?>): String? =
    getAddressMovedRemarkOrNull(translation, change.old, change.new)

fun getAddressMovedRemarkOrNull(translation: Translation, oldAddress: TrackMeter?, newAddress: TrackMeter?): String? {
    return if (newAddress == null || oldAddress == null) {
        null
    } else if (newAddress.kmNumber != oldAddress.kmNumber) {
        publicationChangeRemark(translation = translation, key = "km-number-changed", value = "${newAddress.kmNumber}")
    } else if (lengthDifference(newAddress.meters, oldAddress.meters) > DISTANCE_CHANGE_THRESHOLD) {
        publicationChangeRemark(
            translation = translation,
            key = "moved-x-meters",
            value = formatDistance(lengthDifference(newAddress.meters, oldAddress.meters)),
        )
    } else {
        null
    }
}

fun getSwitchLinksChangedRemark(
    translation: Translation,
    switchLinkChanges: LocationTrackPublicationSwitchLinkChanges,
): String {
    val removed = switchLinkChanges.old.minus(switchLinkChanges.new.keys)
    val added = switchLinkChanges.new.minus(switchLinkChanges.old.keys)
    val commonNames = removed.values.map { it.name }.intersect(added.values.map { it.name }.toSet())

    fun remarkOnIds(ids: SwitchChangeIds) =
        if (commonNames.contains(ids.name) && ids.externalId != null) "${ids.name} (${ids.externalId})" else ids.name

    val remarkRemoved =
        publicationChangeRemark(
            translation,
            if (removed.size > 1) "switch-link-removed-plural" else "switch-link-removed-singular",
            removed.values.map(::remarkOnIds).sorted().joinToString(),
        )
    val remarkAdded =
        publicationChangeRemark(
            translation,
            if (added.size > 1) "switch-link-added-plural" else "switch-link-added-singular",
            added.values.map(::remarkOnIds).sorted().joinToString(),
        )
    return if (removed.isNotEmpty() && added.isNotEmpty()) "${remarkRemoved}. ${remarkAdded}."
    else if (removed.isNotEmpty()) remarkRemoved else remarkAdded
}

fun addChangeClarification(
    publicationChange: PublicationChange<*>,
    clarification: String,
    clarificationInSentenceBody: String? = null,
): PublicationChange<*> {
    return when (publicationChange.remark) {
        null -> publicationChange.copy(remark = clarification)

        else -> {
            val displayedClarification = clarificationInSentenceBody ?: clarification

            publicationChange.copy(remark = "${publicationChange.remark}, $displayedClarification")
        }
    }
}

fun addOperationClarificationsToPublicationTableItem(
    translation: Translation,
    publicationTableItem: PublicationTableItem,
): PublicationTableItem {
    return when (publicationTableItem.operation) {
        Operation.CALCULATED ->
            publicationTableItem.copy(
                propChanges =
                    publicationTableItem.propChanges.map { publicationChange ->
                        addChangeClarification(
                            publicationChange,
                            translation.t("publication-table.calculated-change"),
                            translation.t("publication-table.calculated-change-lowercase"),
                        )
                    }
            )

        else -> publicationTableItem
    }
}

fun getKmNumbersChangedRemarkOrNull(
    translation: Translation,
    changedKmNumbers: Set<KmNumber>,
    summaries: List<GeometryChangeSummary>?,
): String =
    if (summaries.isNullOrEmpty()) {
        publicationChangeRemark(
            translation,
            if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
            formatChangedKmNumbers(changedKmNumbers.toList()),
        )
    } else
        summaries.joinToString(". ") { summary ->
            translation.t(
                "publication-details-table.remark.geometry-changed",
                localizationParams(
                    "changedLengthM" to roundTo1Decimal(summary.changedLengthM).toString(),
                    "maxDistance" to roundTo1Decimal(summary.maxDistance).toString(),
                    "addressRange" to "${summary.startAddress.round(0)}-${summary.endAddress.round(0)}",
                ),
            )
        }
