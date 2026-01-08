package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
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

fun getOperationalPointAreaRemarkOrNull(translation: Translation, oldArea: Polygon?, newArea: Polygon?): String? {
    return if (oldArea != null && newArea != null && oldArea != newArea) {
        publicationChangeRemark(translation = translation, key = "operational-point-area-changed")
    } else if (oldArea == null && newArea != null) {
        publicationChangeRemark(translation = translation, key = "operational-point-area-added")
    } else if (oldArea != null && newArea == null) {
        publicationChangeRemark(translation = translation, key = "operational-point-area-cleared")
    } else {
        null
    }
}

fun getSwitchLinksChangedRemark(
    translation: Translation,
    switchLinkChanges: Map<ChangeSide, Set<LayoutRowVersion<LayoutSwitch>>>,
    lookupVersion: (version: LayoutRowVersion<LayoutSwitch>) -> LayoutSwitch,
    lookupOid: (id: IntId<LayoutSwitch>) -> Oid<LayoutSwitch>?,
): String =
    getReferendsChangedRemark(
        translation,
        "switch-link",
        { s -> s.name.toString() },
        switchLinkChanges,
        lookupVersion,
        lookupOid,
    )

fun getOperationalPointLinksChangedRemark(
    translation: Translation,
    linkChanges: Map<ChangeSide, Set<LayoutRowVersion<OperationalPoint>>>,
    lookupVersion: (version: LayoutRowVersion<OperationalPoint>) -> OperationalPoint,
    lookupOid: (id: IntId<OperationalPoint>) -> Oid<OperationalPoint>?,
): String =
    getReferendsChangedRemark(
        translation,
        "operational-point-link",
        { op -> op.name.toString() },
        linkChanges,
        lookupVersion,
        lookupOid,
    )

fun <T : LayoutAsset<T>> getReferendsChangedRemark(
    translation: Translation,
    referendKeyFragment: String,
    assetName: (asset: T) -> String,
    linkChanges: Map<ChangeSide, Set<LayoutRowVersion<T>>>,
    lookupVersion: (version: LayoutRowVersion<T>) -> T,
    lookupOid: (id: IntId<T>) -> Oid<T>?,
): String {
    val versionById = linkChanges.values.flatten().associateBy { it.id }
    fun lookupById(id: IntId<T>): T = lookupVersion(versionById.getValue(id))
    val (added, removed) = getAddedAndRemoved(linkChanges)

    val commonNames =
        removed.map { assetName(lookupById(it)) }.intersect(added.map { assetName(lookupById(it)) }.toSet())

    fun remark(id: IntId<T>): String {
        val version = versionById.getValue(id)
        val name = assetName(lookupVersion(version))
        val oid = lookupOid(id)
        return if (commonNames.contains(name) && oid != null) "$name ($oid)" else "$name"
    }

    val remarkRemoved =
        publicationChangeRemark(
            translation,
            if (removed.size > 1) "$referendKeyFragment-removed-plural" else "$referendKeyFragment-removed-singular",
            removed.map(::remark).sorted().joinToString(),
        )
    val remarkAdded =
        publicationChangeRemark(
            translation,
            if (added.size > 1) "$referendKeyFragment-added-plural" else "$referendKeyFragment-added-singular",
            added.map(::remark).sorted().joinToString(),
        )
    return if (removed.isNotEmpty() && added.isNotEmpty()) "${remarkRemoved}. ${remarkAdded}."
    else if (removed.isNotEmpty()) remarkRemoved else remarkAdded
}

private fun <T : LayoutAsset<T>> getAddedAndRemoved(
    linkChanges: Map<ChangeSide, Set<LayoutRowVersion<T>>>
): Pair<List<IntId<T>>, List<IntId<T>>> {
    val old = linkChanges[ChangeSide.OLD] ?: setOf()
    val new = linkChanges[ChangeSide.NEW] ?: setOf()
    val removed = old.map { it.id }.minus(new.map { it.id })
    val added = new.map { it.id }.minus(old.map { it.id })
    return Pair(added, removed)
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
