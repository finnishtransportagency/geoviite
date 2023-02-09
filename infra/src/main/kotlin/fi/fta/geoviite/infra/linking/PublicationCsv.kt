package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.util.FileName
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.io.StringWriter
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

fun asCsvFile(rows: List<PublicationCsvRow>, timeZone: ZoneId = ZoneId.of("UTC")): String {
    val writer = StringWriter()
    CSVPrinter(writer, CSVFormat.RFC4180).let { printer ->
        val headers = listOf(
            "name",
            "track-number",
            "km-numbers",
            "operation",
            "publication-time",
            "publication-user",
            "message",
            "ratko"
        ).map { getTranslation("$it-header") }

        printer.printRecord(headers)

        rows.forEach { item ->
            printer.printRecord(
                item.name,
                item.trackNumbers.sorted().joinToString(", "),
                item.changedKmNumbers?.let(::formatChangedKmNumbers),
                formatOperation(item.operation),
                formatInstant(item.publicationTime, timeZone),
                item.publicationUser,
                item.message,
                item.ratkoPushTime?.let { pushTime -> formatInstant(pushTime, timeZone) } ?: getTranslation("no")
            )
        }
    }

    return writer.toString()
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
    "name-header" to "Muutoskohde",
    "track-number-header" to "Ratanro",
    "km-numbers-header" to "Kilometrit",
    "operation-header" to "Muutos",
    "publication-time-header" to "Aika",
    "publication-user-header" to "Käyttäjä",
    "message-header" to "Selite",
    "ratko-header" to "Viety Ratkoon"
)
