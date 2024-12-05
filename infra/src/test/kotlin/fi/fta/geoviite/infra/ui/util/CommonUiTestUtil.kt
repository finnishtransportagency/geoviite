package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.math.IPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH.mm")

fun currentLocalDateFormatted(): String = LocalDate.now().format(dateFormat)

fun currentLocalDateTimeFormatted(): String = LocalDateTime.now().format(dateTimeFormat)

fun localDateFromString(date: String): LocalDateTime = LocalDate.parse(date, dateFormat).atStartOfDay()

fun localDateTimeFromString(dateTime: String): LocalDateTime = LocalDateTime.parse(dateTime, dateTimeFormat)

fun pointToCoordinateString(point: IPoint) = pointToCoordinateString(point.x, point.y)

fun pointToCoordinateString(x: Double, y: Double) = "${asThreeDecimalPlaces(x)} E, ${asThreeDecimalPlaces(y)} N"

fun metersToDouble(meters: String) = meters.substringBefore(' ').toDouble()

// UI uses '.' instead of ','
private fun asThreeDecimalPlaces(v: Double) = String.format("%.3f", v).replace(",", ".")
