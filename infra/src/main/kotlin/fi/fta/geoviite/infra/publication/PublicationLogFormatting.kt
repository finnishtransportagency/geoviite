package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.math.roundTo3Decimals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatChangedKmNumbers(kmNumbers: List<KmNumber>) =
    groupChangedKmNumbers(kmNumbers).joinToString(", ") { if (it.min == it.max) "${it.min}" else "${it.min}-${it.max}" }

fun formatDistance(dist: Double) = if (dist >= 0.1) "${roundTo1Decimal(dist)}" else "<${roundTo1Decimal(0.1)}"

fun formatLocation(location: IPoint) =
    "${roundTo3Decimals(location.x)} E, ${
        roundTo3Decimals(
            location.y
        )
    } N"

fun formatGkLocation(location: GeometryPoint, crsNameGetter: (srid: Srid) -> String) =
    "${roundTo3Decimals(location.y)} N, ${
        roundTo3Decimals(
            location.x
        )
    } E (${crsNameGetter(location.srid)})"

fun getDateStringForFileName(instant1: Instant?, instant2: Instant?, timeZone: ZoneId): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(timeZone)

    val instant1Date = instant1?.let { dateFormatter.format(it) }
    val instant2Date = instant2?.let { dateFormatter.format(it) }

    return if (instant1Date == instant2Date) instant1Date
    else if (instant1Date == null) "-$instant2Date"
    else if (instant2Date == null) "$instant1Date" else "$instant1Date-$instant2Date"
}
