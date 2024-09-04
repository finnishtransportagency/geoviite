package fi.fta.geoviite.infra.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

data class CsvEntry<T>(val header: String, val getValue: (data: T) -> Any?)

val csvDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun toCsvDate(date: Instant): String =
    csvDateFormatter
        // Default to Finnish time. Stored dates are at midnight Finnish time (21/22:00 UTC the
        // previous day),
        // so a timezone offset is added to make sure the correct date is shown
        .withZone(ZoneId.of("Europe/Helsinki"))
        .format(date)

fun <T> printCsv(columns: List<CsvEntry<T>>, data: List<T>): String {
    val csvBuilder = StringBuilder()
    CSVPrinter(csvBuilder, CSVFormat.RFC4180).use { csvPrinter ->
        csvPrinter.printRecord(columns.map { it.header })
        data.forEach { dataRow ->
            columns.map { column ->
                val csvValue = column.getValue(dataRow)
                if (csvValue is List<*>) csvValue.forEach { csvPrinter.print(it) } else csvPrinter.print(csvValue)
            }
            csvPrinter.println()
        }
    }
    return addBOM(csvBuilder.toString())
}

private fun addBOM(fileContent: String): String = "\uFEFF" + fileContent
