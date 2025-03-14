package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.localization.Translation
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun zip(files: List<Pair<FileName, String>>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zipStream ->
        files.forEach { (fileName, content) ->
            zipStream.putNextEntry(ZipEntry(fileName.toString()))
            zipStream.write(content.toByteArray())
            zipStream.closeEntry()
        }
    }
    return bos.toByteArray().also { bos.close() }
}

fun getNameForInfraModelZipFile(
    applicability: PlanApplicability?,
    alignmentName: String,
    startKmNumber: KmNumber?,
    endKmNumber: KmNumber?,
    translation: Translation,
): FileName {
    val kmNumberPart =
        if (startKmNumber == null && endKmNumber == null) {
            null
        } else {
            "${startKmNumber?.toString() ?: ""}-${endKmNumber?.toString() ?: ""}"
        }

    val parts =
        listOfNotNull(
            translation.t("plan-download.csv.geometry-plans"),
            translation.t("enum.PlanApplicability.${applicability?.name ?: "UNKNOWN"}"),
            alignmentName,
            kmNumberPart,
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        )
    return FileName(parts.joinToString(" ") + ".zip")
}
