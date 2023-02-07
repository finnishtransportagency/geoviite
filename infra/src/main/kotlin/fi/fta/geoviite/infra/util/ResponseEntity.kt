package fi.fta.geoviite.infra.util

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

fun <T> toResponse(value: T?) = value?.let { v -> ResponseEntity(v, OK) } ?: ResponseEntity(NO_CONTENT)

fun toFileDownloadResponse(fileName: String, content: ByteArray): ResponseEntity<ByteArray> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_OCTET_STREAM
    headers.set(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName).build().toString())
    return ResponseEntity.ok().headers(headers).body(content)
}
