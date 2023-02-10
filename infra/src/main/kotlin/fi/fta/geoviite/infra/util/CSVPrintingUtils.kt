package fi.fta.geoviite.infra.util

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

data class CsvEntry<T>(
    val header: String,
    val getValue: (data: T) -> Any?,
)

fun <T>printCsv(columns: List<CsvEntry<T>>, data: List<T>): ByteArray {
    val csvBuilder = StringBuilder()
    CSVPrinter(csvBuilder, CSVFormat.RFC4180).let { csvPrinter ->
        try {
            csvPrinter.printRecord(columns.map { it.header })
            data.forEach { dataRow ->
                columns.map { column -> csvPrinter.print(column.getValue(dataRow)) }
                csvPrinter.println()
            }
        } finally {
            csvPrinter.close()
        }
    }
    return csvBuilder.toString().toByteArray()
}
