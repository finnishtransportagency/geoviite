package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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

fun getDateStringForFileName(instant1: Instant?, instant2: Instant?, timeZone: ZoneId): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(timeZone)

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
        ContentDisposition.attachment()
            .filename(fileName.toString())
            .build()
            .toString()
    )

    return ResponseEntity.ok()
        .headers(headers)
        .body(content.toByteArray())
}

fun asCsvFile(items: List<PublicationTableItem>, timeZone: ZoneId): String {
    val columns = mapOf<PublicationTableColumn, (item: PublicationTableItem) -> Any?>(
        PublicationTableColumn.NAME to { it.name },
        PublicationTableColumn.TRACK_NUMBERS to {
            it.trackNumbers.sorted().joinToString(", ")
        },
        PublicationTableColumn.CHANGED_KM_NUMBERS to {
            it.changedKmNumbers
                .map { range -> "${range.min}${if (range.min != range.max) "-${range.max}" else ""}" }
                .joinToString(", ")
        },
        PublicationTableColumn.OPERATION to { formatOperation(it.operation) },
        PublicationTableColumn.PUBLICATION_TIME to { formatInstant(it.publicationTime, timeZone) },
        PublicationTableColumn.PUBLICATION_USER to { "${it.publicationUser}" },
        PublicationTableColumn.MESSAGE to { it.message },
        PublicationTableColumn.RATKO_PUSH_TIME to {
            it.ratkoPushTime?.let { pushTime ->
                formatInstant(
                    pushTime,
                    timeZone
                )
            } ?: getTranslation("no")
        },
        PublicationTableColumn.CHANGES to {
            it.propChanges.map { change ->
                "${change.propKey.key}: ${change.value.oldValue ?: ""} -> ${change.value.newValue ?: ""}${if (change.remark != null) " (${change.remark.key})" else ""}"
            }
        }
    ).map { (column, fn) ->
        CsvEntry(getTranslation("$column-header"), fn)
    }

    return printCsv(columns, items)
}

private fun formatInstant(time: Instant, timeZone: ZoneId) =
    DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .withZone(timeZone)
        .format(time)

private fun formatOperation(operation: Operation) =
    when (operation) {
        Operation.CREATE -> getTranslation("create")
        Operation.MODIFY -> getTranslation("modify")
        Operation.DELETE -> getTranslation("delete")
        Operation.RESTORE -> getTranslation("restore")
        Operation.CALCULATED -> getTranslation("calculated-change")
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
    groupChangedKmNumbers(kmNumbers)
        .map { if (it.min == it.max) "${it.min}" else "${it.min}-${it.max}" }
        .joinToString(", ")

fun formatDistance(dist: Double) = if (dist >= 0.1) "${roundTo1Decimal(dist)}" else "<${roundTo1Decimal(0.1)}"

fun getComparator(sortBy: PublicationTableColumn, order: SortOrder? = null): Comparator<PublicationTableItem> =
    if (order == SortOrder.DESCENDING) getComparator(sortBy).reversed() else getComparator(sortBy)

//Nulls are "last", e.g., 0, 1, 2, null
private fun <T : Comparable<T>> compareNullsLast(a: T?, b: T?) =
    if (a == null && b == null) 0
    else if (a == null) 1
    else if (b == null) -1
    else a.compareTo(b)

private fun getComparator(sortBy: PublicationTableColumn): Comparator<PublicationTableItem> {
    return when (sortBy) {
        PublicationTableColumn.NAME -> Comparator.comparing { p -> p.name }
        PublicationTableColumn.TRACK_NUMBERS -> Comparator { a, b ->
            compareNullsLast(a.trackNumbers.minOrNull(), b.trackNumbers.minOrNull())
        }

        PublicationTableColumn.CHANGED_KM_NUMBERS -> Comparator { a, b ->
            compareNullsLast(a.changedKmNumbers.firstOrNull()?.min, b.changedKmNumbers.firstOrNull()?.min)
        }

        PublicationTableColumn.OPERATION -> Comparator.comparing { p -> p.operation.priority }
        PublicationTableColumn.PUBLICATION_TIME -> Comparator.comparing { p -> p.publicationTime }
        PublicationTableColumn.PUBLICATION_USER -> Comparator.comparing { p -> p.publicationUser }
        PublicationTableColumn.MESSAGE -> Comparator.comparing { p -> p.message }
        PublicationTableColumn.RATKO_PUSH_TIME -> Comparator { a, b ->
            compareNullsLast(a.ratkoPushTime, b.ratkoPushTime)
        }

        PublicationTableColumn.CHANGES -> Comparator { a, b ->
            compareNullsLast(a.propChanges.firstOrNull()?.propKey?.key, b.propChanges.firstOrNull()?.propKey?.key)
        }
    }
}

fun formatLocation(location: Point) =
    "${roundTo3Decimals(location.x)} E, ${
        roundTo3Decimals(
            location.y
        )
    } N"

val DISTANCE_CHANGE_THRESHOLD = 0.0005

fun lengthDifference(len1: Double, len2: Double) = abs(abs(len1) - abs(len2))
fun lengthDifference(len1: BigDecimal, len2: BigDecimal) = abs(abs(len1.toDouble()) - abs(len2.toDouble()))

fun pointsAreSame(point1: IPoint?, point2: IPoint?) =
    point1 == point2 || point1 != null && point2 != null && point1.isSame(point2, DISTANCE_CHANGE_THRESHOLD)

fun getLengthChangedRemarkOrNull(length1: Double?, length2: Double?) =
    if (length1 != null && length2 != null)
        lengthDifference(length1, length2).let { lengthDifference ->
            if (lengthDifference > DISTANCE_CHANGE_THRESHOLD)
                PublicationChangeRemark(
                    "changed-x-meters",
                    formatDistance(lengthDifference)
                )
            else null
        }
    else null

fun getPointMovedRemarkOrNull(oldPoint: Point?, newPoint: Point?) =
    oldPoint?.let { p1 ->
        newPoint?.let { p2 ->
            if (!pointsAreSame(p1, p2)) {
                val distance = calculateDistance(listOf(p1, p2), LAYOUT_SRID)
                if (distance > DISTANCE_CHANGE_THRESHOLD)
                    PublicationChangeRemark(
                        "moved-x-meters",
                        formatDistance(distance)
                    )
                else null
            } else null
        }
    }

fun getAddressMovedRemarkOrNull(oldAddress: TrackMeter?, newAddress: TrackMeter?) =
    if (newAddress == null || oldAddress == null) null
    else if (newAddress.kmNumber != oldAddress.kmNumber)
        PublicationChangeRemark(
            "km-number-changed",
            "${newAddress.kmNumber}"
        )
    else if (lengthDifference(newAddress.meters, oldAddress.meters) > DISTANCE_CHANGE_THRESHOLD)
        PublicationChangeRemark(
            "moved-x-meters",
            formatDistance(
                lengthDifference(
                    newAddress.meters,
                    oldAddress.meters
                )
            )
        )
    else null

fun getKmNumbersChangedRemarkOrNull(changedKmNumbers: Set<KmNumber>) =
    PublicationChangeRemark(
        if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
        formatChangedKmNumbers(changedKmNumbers.toList())
    )

fun <T, U> compareChangeValues(
    change: Change<T>,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: PublicationChangeRemark? = null,
    enumLocalizationKey: String? = null
) =
    compareChange(
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
    remark: PublicationChangeRemark? = null,
    enumLocalizationKey: String? = null
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
    remark: PublicationChangeRemark? = null,
    enumLocalizationKey: String? = null,
) =
    if (predicate()) {
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

val MATH_POINT_TRANSLATION = "matemaattinen piste"
val FORWARD_JOINT_TRANSLATION = "etujatkos"
fun switchBaseTypeToProp(switchBaseType: SwitchBaseType) =
    when (switchBaseType) {
        SwitchBaseType.KRV, SwitchBaseType.YRV, SwitchBaseType.SRR, SwitchBaseType.RR -> FORWARD_JOINT_TRANSLATION
        SwitchBaseType.KV, SwitchBaseType.SKV, SwitchBaseType.TYV, SwitchBaseType.UKV, SwitchBaseType.YV -> MATH_POINT_TRANSLATION
    }

val NOT_CALCULATED_TRANSLATION = "Ei laskettu"

fun getTranslation(key: String) = publicationTranslations[key] ?: ""

private val publicationTranslations = mapOf(
    "track-number" to "Ratanumero",
    "reference-line" to "Pituusmittauslinja",
    "km-post" to "Tasakilometripiste",
    "location-track" to "Sijaintiraide",
    "switch" to "Vaihde",
    "calculated-change" to "Laskettu muutos",
    "no" to "Ei",
    "create" to "Luonti",
    "modify" to "Muokkaus",
    "delete" to "Poisto",
    "restore" to "Palautus",
    "NAME-header" to "Muutoskohde",
    "TRACK_NUMBERS-header" to "Ratanro",
    "CHANGED_KM_NUMBERS-header" to "Ratakilometrit",
    "OPERATION-header" to "Muutos",
    "PUBLICATION_TIME-header" to "Aika",
    "PUBLICATION_USER-header" to "Käyttäjä",
    "MESSAGE-header" to "Selite",
    "RATKO_PUSH_TIME-header" to "Viety Ratkoon",
    "CHANGES-header" to "Muutokset",
)
