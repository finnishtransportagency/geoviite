package fi.fta.geoviite.infra.util

import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.ResponseEntity

fun <T> toResponse(value: T?) = value?.let { v -> ResponseEntity(v, OK) } ?: ResponseEntity(NO_CONTENT)
