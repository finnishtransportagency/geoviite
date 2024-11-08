package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.util.KnownFileSuffix.XML
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

fun <T> toResponse(value: T?) = value?.let { v -> ResponseEntity(v, OK) } ?: ResponseEntity(NO_CONTENT)

fun toFileDownloadResponse(file: InfraModelFile): ResponseEntity<ByteArray> =
    toFileDownloadResponse(file.name.withSuffix(XML), file.content.toByteArray())

fun toFileDownloadResponse(fileName: FileName, content: ByteArray): ResponseEntity<ByteArray> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_OCTET_STREAM
    headers.set(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName.toString(), Charsets.UTF_8).build().toString(),
    )
    return ResponseEntity.ok().headers(headers).body(content)
}
