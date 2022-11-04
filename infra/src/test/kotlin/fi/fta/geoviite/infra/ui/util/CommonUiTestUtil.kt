package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CommonUiTestUtil {

    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH.mm")

        fun currentLocalDateFormatted(): String = LocalDate.now().format(dateFormat)
        fun currentLocalDateTimeFormatted(): String = LocalDateTime.now().format(dateTimeFormat)

        fun localDateFromString(date: String): LocalDateTime = LocalDate.parse(date, dateFormat).atStartOfDay()
        fun localDateTimeFromString(dateTime: String): LocalDateTime = LocalDateTime.parse(dateTime, dateTimeFormat)

        fun pointToCoordinateString(point: LayoutPoint) =
            pointToCoordinateString(point.x, point.y)

        fun pointToCoordinateString(point: Point) =
            pointToCoordinateString(point.x, point.y)

        fun pointToCoordinateString(x: Double, y: Double) =
            String.format("%.3f E %.3f N", x, y).replace(",", ".") //UI uses '.' instead of ','

        fun metersToDouble(meters: String) =
            meters.substringBefore(' ').toDouble()

    }
}