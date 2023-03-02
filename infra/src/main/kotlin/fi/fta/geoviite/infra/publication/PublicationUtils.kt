package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun getDateStringForFileName(instant1: Instant?, instant2: Instant?, timeZone: ZoneId? = null): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        .withZone(timeZone ?: ZoneId.of("UTC"))

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

fun asCsvFile(items: List<PublicationTableItem>, timeZone: ZoneId = ZoneId.of("UTC")): String {
    val columns = mapOf<PublicationTableColumn, (item: PublicationTableItem) -> Any?>(
        PublicationTableColumn.NAME to { it.name },
        PublicationTableColumn.TRACK_NUMBERS to {
            it.trackNumbers.sorted().joinToString(", ")
        },
        PublicationTableColumn.CHANGED_KM_NUMBERS to {
            it.changedKmNumbers?.sorted()?.let(::formatChangedKmNumbers)
        },
        PublicationTableColumn.OPERATION to { formatOperation(it.operation) },
        PublicationTableColumn.PUBLICATION_TIME to { formatInstant(it.publicationTime, timeZone) },
        PublicationTableColumn.PUBLICATION_USER to { it.publicationUser },
        PublicationTableColumn.MESSAGE to { it.message },
        PublicationTableColumn.RATKO_PUSH_TIME to {
            it.ratkoPushTime?.let { pushTime ->
                formatInstant(
                    pushTime,
                    timeZone
                )
            } ?: getTranslation("no")
        },
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
    }

fun formatMessage(message: String?, isCalculatedChange: Boolean): String {
    val calculatedChangeTranslation = getTranslation("calculated-change")

    return if (message == null) {
        if (isCalculatedChange) calculatedChangeTranslation else ""
    } else if (isCalculatedChange) "$message ($calculatedChangeTranslation)"
    else message
}

private fun formatChangedKmNumbers(kmNumbers: List<KmNumber>) =
    kmNumbers
        .sorted()
        .fold(mutableListOf<List<KmNumber>>()) { acc, kmNumber ->
            if (acc.isEmpty()) acc.add(listOf(kmNumber))
            else {
                val previousKmNumbers = acc.last()
                val previousKmNumber = previousKmNumbers.last().number

                if (kmNumber.number == previousKmNumber || kmNumber.number == previousKmNumber + 1) {
                    acc[acc.lastIndex] = listOf(previousKmNumbers.first(), kmNumber)
                } else acc.add(listOf(kmNumber))
            }

            acc
        }.joinToString(", ") { it.joinToString("-") }

fun getComparator(sortBy: PublicationTableColumn, order: SortOrder? = null): Comparator<PublicationTableItem> =
    if (order == SortOrder.DESCENDING) getComparator(sortBy).reversed() else getComparator(sortBy)

//Nulls are "last", e.g., 0, 1, 2, null
private fun <T : Comparable<T>> compareNullableValues(a: T?, b: T?) =
    if (a == null && b == null) 0
    else if (a == null) 1
    else if (b == null) -1
    else a.compareTo(b)

private fun getComparator(sortBy: PublicationTableColumn): Comparator<PublicationTableItem> {
    return when (sortBy) {
        PublicationTableColumn.NAME -> Comparator.comparing { p -> p.name }
        PublicationTableColumn.TRACK_NUMBERS -> Comparator { a, b ->
            compareNullableValues(a.trackNumbers.minOrNull(), b.trackNumbers.minOrNull())
        }

        PublicationTableColumn.CHANGED_KM_NUMBERS -> Comparator { a, b ->
            compareNullableValues(a.changedKmNumbers?.minOrNull(), b.changedKmNumbers?.minOrNull())
        }

        PublicationTableColumn.OPERATION -> Comparator.comparing { p -> p.operation.priority }
        PublicationTableColumn.PUBLICATION_TIME -> Comparator.comparing { p -> p.publicationTime }
        PublicationTableColumn.PUBLICATION_USER -> Comparator.comparing { p -> p.publicationUser }
        PublicationTableColumn.MESSAGE -> Comparator.comparing { p -> p.message }
        PublicationTableColumn.RATKO_PUSH_TIME -> Comparator { a, b ->
            compareNullableValues(a.ratkoPushTime, b.ratkoPushTime)
        }
    }
}

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
    "TRACK-NUMBERS-header" to "Ratanro",
    "CHANGED_KM_NUMBERS-header" to "Kilometrit",
    "OPERATION-header" to "Muutos",
    "PUBLICATION_TIME-header" to "Aika",
    "PUBLICATION_USER-header" to "Käyttäjä",
    "MESSAGE-header" to "Selite",
    "RATKO_PUSH_TIME-header" to "Viety Ratkoon"
)
