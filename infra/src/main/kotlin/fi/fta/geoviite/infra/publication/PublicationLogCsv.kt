package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.printCsv
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun asCsvFile(items: List<PublicationTableItem>, timeZone: ZoneId, translation: Translation): String {
    val columns =
        mapOf<String, (item: PublicationTableItem) -> Any?>(
                "publication-table.name" to { it.name },
                "publication-table.track-number" to { it.trackNumbers.sorted().joinToString(", ") },
                "publication-table.km-number" to
                    {
                        it.changedKmNumbers.joinToString(", ") { range ->
                            "${range.min}${if (range.min != range.max) "-${range.max}" else ""}"
                        }
                    },
                "publication-table.operation" to { translation.enum(it.operation) },
                "publication-table.publication-time" to { formatInstant(it.publicationTime, timeZone) },
                "publication-table.publication-user" to { "${it.publicationUser}" },
                "publication-table.message" to { it.message.escapeNewLines() },
                "publication-table.pushed-to-ratko" to
                    {
                        it.ratkoPushTime?.let { pushTime -> formatInstant(pushTime, timeZone) } ?: translation.t("no")
                    },
                "publication-table.changes" to
                    {
                        it.propChanges.map { change ->
                            "${
                            translation.t("publication-details-table.prop.${change.propKey.key}", change.propKey.params)
                        }: ${formatChangeValue(translation, change.value)}${
                            if (change.remark != null) " (${change.remark})" else ""
                        }"
                        }
                    },
            )
            .map { (column, fn) -> CsvEntry(translation.t(column), fn) }

    return printCsv(columns, items)
}

private fun enumTranslationKey(enumName: LocalizationKey, value: String) = "enum.${enumName}.${value}"

private fun <T> formatChangeValue(translation: Translation, value: ChangeValue<T>): String {
    return "${
        (if (value.localizationKey != null && value.oldValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.oldValue.toString(),
            )
        ) else if (value.oldValue == null) null else value.oldValue.toString()) ?: ""
    } -> ${
        (if (value.localizationKey != null && value.newValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.newValue.toString(),
            )
        ) else if (value.newValue == null) null else value.newValue.toString()) ?: ""
    }"
}

private fun formatInstant(time: Instant, timeZone: ZoneId) =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(timeZone).format(time)
