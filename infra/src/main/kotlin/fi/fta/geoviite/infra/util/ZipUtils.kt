package fi.fta.geoviite.infra.util

import java.io.ByteArrayOutputStream
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
