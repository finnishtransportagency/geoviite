package fi.fta.geoviite.infra.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GvtZipEntry(val folder: FileName?, val fileName: FileName, val content: ByteArray)

fun zip(files: List<GvtZipEntry>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zipStream ->
        files.forEach { (folder, fileName, content) ->
            val fileNameWithinZip = folder?.let { "$it/$fileName" } ?: fileName.toString()
            zipStream.putNextEntry(ZipEntry(fileNameWithinZip))
            zipStream.write(content)
            zipStream.closeEntry()
        }
    }
    return bos.toByteArray().also { bos.close() }
}
