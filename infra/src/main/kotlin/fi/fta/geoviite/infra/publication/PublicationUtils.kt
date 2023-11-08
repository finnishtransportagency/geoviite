package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutPoint
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.util.*
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.hypot

fun getDateStringForFileName(instant1: Instant?, instant2: Instant?, timeZone: ZoneId): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(timeZone)

    val instant1Date = instant1?.let { dateFormatter.format(it) }
    val instant2Date = instant2?.let { dateFormatter.format(it) }

    return if (instant1Date == instant2Date) instant1Date
    else if (instant1Date == null) "-$instant2Date"
    else if (instant2Date == null) "$instant1Date"
    else "$instant1Date-$instant2Date"
}

fun getCsvResponseEntity(content: String, fileName: FileName): ResponseEntity<ByteArray> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_OCTET_STREAM
    headers.set(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName.toString()).build().toString()
    )

    return ResponseEntity.ok().headers(headers).body(content.toByteArray())
}

fun asCsvFile(items: List<PublicationTableItem>, timeZone: ZoneId, translation: Translation): String {
    val columns = mapOf<String, (item: PublicationTableItem) -> Any?>("publication-table.name" to { it.name },
        "publication-table.track-number" to {
            it.trackNumbers.sorted().joinToString(", ")
        },
        "publication-table.km-number" to {
            it.changedKmNumbers.joinToString(", ") { range -> "${range.min}${if (range.min != range.max) "-${range.max}" else ""}" }
        },
        "publication-table.operation" to { formatOperation(translation, it.operation) },
        "publication-table.publication-time" to { formatInstant(it.publicationTime, timeZone) },
        "publication-table.publication-user" to { "${it.publicationUser}" },
        "publication-table.message" to { it.message },
        "publication-table.pushed-to-ratko" to {
            it.ratkoPushTime?.let { pushTime ->
                formatInstant(
                    pushTime, timeZone
                )
            } ?: translation.t("no")
        },
        "publication-table.changes" to {
            it.propChanges.map { change ->
                "${
                    translation.t("publication-details-table.prop.${change.propKey.key}", change.propKey.params)
                }: ${formatChangeValue(translation, change.value)}${
                    if (change.remark != null) " (${change.remark})" else ""
                }"
            }
        }).map { (column, fn) ->
        CsvEntry(translation.t(column), fn)
    }

    return printCsv(columns, items)
}

private fun enumTranslationKey(enumName: LocalizationKey, value: String) = "enum.${enumName}.${value}"

private fun <T> formatChangeValue(translation: Translation, value: ChangeValue<T>): String {

    return "${
        (if (value.localizationKey != null && value.oldValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.oldValue.toString()
            )
        ) else if (value.oldValue == null) null else value.oldValue.toString()) ?: ""
    } -> ${
        (if (value.localizationKey != null && value.newValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.newValue.toString()
            )
        ) else if (value.newValue == null) null else value.newValue.toString()) ?: ""
    }"
}


private fun formatInstant(time: Instant, timeZone: ZoneId) =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(timeZone).format(time)

private fun formatOperation(translation: Translation, operation: Operation) = when (operation) {
    Operation.CREATE -> translation.t(
        enumTranslationKey(LocalizationKey("publish-operation"), "CREATE"),
        LocalizationParams.empty()
    )

    Operation.MODIFY -> translation.t(
        enumTranslationKey(LocalizationKey("publish-operation"), "MODIFY"),
        LocalizationParams.empty()
    )

    Operation.DELETE -> translation.t(
        enumTranslationKey(LocalizationKey("publish-operation"), "DELETE"),
        LocalizationParams.empty()
    )

    Operation.RESTORE -> translation.t(
        enumTranslationKey(LocalizationKey("publish-operation"), "RESTORE"),
        LocalizationParams.empty()
    )

    Operation.CALCULATED -> translation.t(
        enumTranslationKey(LocalizationKey("publish-operation"), "CALCULATED"),
        LocalizationParams.empty()
    )
}

fun groupChangedKmNumbers(kmNumbers: List<KmNumber>) =
    kmNumbers.sorted().fold(mutableListOf<List<KmNumber>>()) { acc, kmNumber ->
        if (acc.isEmpty()) acc.add(listOf(kmNumber))
        else {
            val previousKmNumbers = acc.last()
            val previousKmNumber = previousKmNumbers.last().number

            if (kmNumber.number == previousKmNumber || kmNumber.number == previousKmNumber + 1) {
                acc[acc.lastIndex] = listOf(previousKmNumbers.first(), kmNumber)
            } else acc.add(listOf(kmNumber))
        }
        acc
    }.map { Range(it.first(), it.last()) }

fun formatChangedKmNumbers(kmNumbers: List<KmNumber>) =
    groupChangedKmNumbers(kmNumbers).joinToString(", ") { if (it.min == it.max) "${it.min}" else "${it.min}-${it.max}" }

fun formatDistance(dist: Double) = if (dist >= 0.1) "${roundTo1Decimal(dist)}" else "<${roundTo1Decimal(0.1)}"

fun getComparator(sortBy: PublicationTableColumn, order: SortOrder? = null): Comparator<PublicationTableItem> =
    if (order == SortOrder.DESCENDING) getComparator(sortBy).reversed() else getComparator(sortBy)

private fun getComparator(sortBy: PublicationTableColumn): Comparator<PublicationTableItem> {
    return when (sortBy) {
        PublicationTableColumn.NAME -> Comparator.comparing { p -> p.name }
        PublicationTableColumn.TRACK_NUMBERS -> Comparator { a, b ->
            nullsLastComparator(a.trackNumbers.minOrNull(), b.trackNumbers.minOrNull())
        }

        PublicationTableColumn.CHANGED_KM_NUMBERS -> Comparator { a, b ->
            nullsLastComparator(a.changedKmNumbers.firstOrNull()?.min, b.changedKmNumbers.firstOrNull()?.min)
        }

        PublicationTableColumn.OPERATION -> Comparator.comparing { p -> p.operation.priority }
        PublicationTableColumn.PUBLICATION_TIME -> Comparator.comparing { p -> p.publicationTime }
        PublicationTableColumn.PUBLICATION_USER -> Comparator.comparing { p -> p.publicationUser }
        PublicationTableColumn.MESSAGE -> Comparator.comparing { p -> p.message }
        PublicationTableColumn.RATKO_PUSH_TIME -> Comparator { a, b ->
            nullsLastComparator(a.ratkoPushTime, b.ratkoPushTime)
        }

        PublicationTableColumn.CHANGES -> Comparator { a, b ->
            nullsLastComparator(a.propChanges.firstOrNull()?.propKey?.key, b.propChanges.firstOrNull()?.propKey?.key)
        }
    }
}

fun formatLocation(location: Point) = "${roundTo3Decimals(location.x)} E, ${
    roundTo3Decimals(
        location.y
    )
} N"

const val DISTANCE_CHANGE_THRESHOLD = 0.0005

fun lengthDifference(len1: Double, len2: Double) = abs(abs(len1) - abs(len2))
fun lengthDifference(len1: BigDecimal, len2: BigDecimal) = abs(abs(len1.toDouble()) - abs(len2.toDouble()))

fun pointsAreSame(point1: IPoint?, point2: IPoint?) =
    point1 == point2 || point1 != null && point2 != null && point1.isSame(point2, DISTANCE_CHANGE_THRESHOLD)

fun getLengthChangedRemarkOrNull(translation: Translation, length1: Double?, length2: Double?) =
    if (length1 != null && length2 != null) lengthDifference(length1, length2).let { lengthDifference ->
        if (lengthDifference > DISTANCE_CHANGE_THRESHOLD) publicationChangeRemark(
            translation, "changed-x-meters", formatDistance(lengthDifference)
        )
        else null
    }
    else null

fun getPointMovedRemarkOrNull(translation: Translation, oldPoint: Point?, newPoint: Point?) = oldPoint?.let { p1 ->
    newPoint?.let { p2 ->
        if (!pointsAreSame(p1, p2)) {
            val distance = calculateDistance(listOf(p1, p2), LAYOUT_SRID)
            if (distance > DISTANCE_CHANGE_THRESHOLD) publicationChangeRemark(
                translation, "moved-x-meters", formatDistance(distance)
            )
            else null
        } else null
    }
}

fun getAddressMovedRemarkOrNull(translation: Translation, oldAddress: TrackMeter?, newAddress: TrackMeter?) =
    if (newAddress == null || oldAddress == null) null
    else if (newAddress.kmNumber != oldAddress.kmNumber) publicationChangeRemark(
        translation, "km-number-changed", "${newAddress.kmNumber}"
    )
    else if (lengthDifference(
            newAddress.meters, oldAddress.meters
        ) > DISTANCE_CHANGE_THRESHOLD) publicationChangeRemark(
        translation, "moved-x-meters", formatDistance(
            lengthDifference(
                newAddress.meters, oldAddress.meters
            )
        )
    )
    else null


fun getSwitchLinksChangedRemark(
    translation: Translation,
    switchLinkChanges: LocationTrackPublicationSwitchLinkChanges,
): String {
    val removed = switchLinkChanges.old.minus(switchLinkChanges.new.keys)
    val added = switchLinkChanges.new.minus(switchLinkChanges.old.keys)
    val commonNames = removed.values.map { it.name }.intersect(added.values.map { it.name }.toSet())

    fun remarkOnIds(ids: SwitchChangeIds) =
        if (commonNames.contains(ids.name) && ids.externalId != null) "${ids.name} (${ids.externalId})" else ids.name

    val remarkRemoved = publicationChangeRemark(
        translation,
        if (removed.size > 1) "switch-link-removed-plural" else "switch-link-removed-singular",
        removed.values.map(::remarkOnIds).sorted().joinToString()
    )
    val remarkAdded = publicationChangeRemark(
        translation,
        if (added.size > 1) "switch-link-added-plural" else "switch-link-added-singular",
        added.values.map(::remarkOnIds).sorted().joinToString()
    )
    return if (removed.isNotEmpty() && added.isNotEmpty()) "${remarkRemoved}. ${remarkAdded}."
    else if (removed.isNotEmpty()) remarkRemoved
    else remarkAdded
}

fun <T, U> compareChangeValues(
    change: Change<T>,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) = compareChange(
    predicate = { change.new != change.old },
    oldValue = change.old,
    newValue = change.new,
    valueTransform = valueTransform,
    propKey = propKey,
    remark = remark,
    enumLocalizationKey = enumLocalizationKey,
)

fun <U> compareLength(
    oldValue: Double?,
    newValue: Double?,
    threshold: Double,
    valueTransform: (Double) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) = compareChange(
    predicate = {
        if (oldValue != null && newValue != null) lengthDifference(oldValue, newValue) > threshold
        else if (oldValue != null || newValue != null) true
        else false
    },
    oldValue = oldValue,
    newValue = newValue,
    valueTransform = valueTransform,
    propKey = propKey,
    remark = remark,
    enumLocalizationKey = enumLocalizationKey,
)

fun <T, U> compareChange(
    predicate: () -> Boolean,
    oldValue: T?,
    newValue: T?,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) = if (predicate()) {
    PublicationChange(
        propKey,
        value = ChangeValue(
            oldValue = oldValue?.let(valueTransform),
            newValue = newValue?.let(valueTransform),
            localizationKey = enumLocalizationKey,
        ),
        remark,
    )
} else null

fun switchBaseTypeToProp(translation: Translation, switchBaseType: SwitchBaseType) = when (switchBaseType) {
    SwitchBaseType.KRV, SwitchBaseType.YRV, SwitchBaseType.SRR, SwitchBaseType.RR -> translation.t(
        "publication-details-table.joint.forward-joint"
    )

    SwitchBaseType.KV, SwitchBaseType.SKV, SwitchBaseType.TYV, SwitchBaseType.UKV, SwitchBaseType.YV -> translation.t(
        "publication-details-table.joint.math-point"
    )
}

data class GeometryChangeSummary(
    val changedLengthM: Double,
    val maxDistance: Double,
    val startKm: KmNumber,
    val endKm: KmNumber,
)

fun getKmNumbersChangedRemarkOrNull(
    translation: Translation,
    changedKmNumbers: Set<KmNumber>,
    summaries: List<GeometryChangeSummary>?,
): String = if (summaries.isNullOrEmpty()) {
    publicationChangeRemark(
        translation,
        if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
        formatChangedKmNumbers(changedKmNumbers.toList())
    )
} else summaries.joinToString { summary ->
    val key =
        "publication-details-table.remark.geometry-changed${if (summary.startKm == summary.endKm) "" else "-many-km"}"
    translation.t(
        key, LocalizationParams(
            mapOf(
                "changedLengthM" to roundTo1Decimal(summary.changedLengthM).toString(),
                "maxDistance" to roundTo1Decimal(summary.maxDistance).toString(),
                "addressRange" to if (summary.startKm == summary.endKm) summary.startKm.toString()
                else "${summary.startKm}-${summary.endKm}"
            )
        )
    )
}


private data class ComparisonPoints(
    val mOnReferenceLine: Double,
    val oldPointIndex: Int,
    val oldPoint: LayoutPoint,
    val newPoint: LayoutPoint,
) {
    val roughDistance = hypot(oldPoint.x - newPoint.x, oldPoint.y - newPoint.y)
    fun distance() = calculateDistance(LAYOUT_SRID, oldPoint, newPoint)
}

fun summarizeAlignmentChanges(
    geocodingContext: GeocodingContext,
    oldAlignment: LayoutAlignment,
    newAlignment: LayoutAlignment,
    changeThreshold: Double = 1.0,
): List<GeometryChangeSummary> {
    val changedRanges = getChangedAlignmentRanges(oldAlignment, newAlignment)
    return changedRanges.mapNotNull { oldSegments ->
        // TODO: GVT-2217 This unnecessarily duplicates all points as LayoutPoint. Refactor.
        val oldPoints = oldSegments.flatMap { segment -> segment.alignmentPoints }
        val changedPoints = oldPoints.mapIndexedNotNull { index, oldPoint ->
            geocodingContext.getAddressAndM(oldPoint)?.let { (address, mOnReferenceLine) ->
                geocodingContext.getTrackLocation(newAlignment, address)?.let { newAddressPoint ->
                    ComparisonPoints(mOnReferenceLine, index, oldPoint, newAddressPoint.point)
                }?.let { comparison -> if (comparison.roughDistance < changeThreshold) null else comparison }
            }
        }

        if (changedPoints.isEmpty()) null else {
            val startKm = geocodingContext.getAddress(oldPoints[changedPoints.first().oldPointIndex])?.first?.kmNumber
            val endKm = geocodingContext.getAddress(oldPoints[changedPoints.last().oldPointIndex])?.first?.kmNumber

            if (startKm == null || endKm == null) null
            else GeometryChangeSummary(
                changedPoints.last().mOnReferenceLine - changedPoints.first().mOnReferenceLine,
                changedPoints.maxByOrNull { it.roughDistance }?.distance() ?: 0.0,
                startKm,
                endKm,
            )
        }
    }
}

private fun getChangedAlignmentRanges(old: LayoutAlignment, new: LayoutAlignment): List<List<LayoutSegment>> {
    val newIndexByGeometryId = new.segments.mapIndexed { i, s -> i to s }.associate { (index, segment) -> segment.geometry.id to index }
    val changedOldSegmentIndexRanges = rangesOfConsecutiveIndicesOf(
        false,
        old.segments.map { segment -> newIndexByGeometryId.containsKey(segment.geometry.id) }
    )
    return changedOldSegmentIndexRanges.map { oldSegmentIndexRange ->
        old.segments.subList(oldSegmentIndexRange.start, oldSegmentIndexRange.endInclusive)
    }
}

fun publicationChangeRemark(translation: Translation, key: String, value: String?) =
    translation.t("publication-details-table.remark.$key", LocalizationParams("value" to value))
