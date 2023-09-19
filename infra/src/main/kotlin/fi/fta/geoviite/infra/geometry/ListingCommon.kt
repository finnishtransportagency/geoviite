package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.tracklayout.LayoutSegment

fun collectLinkedElements(
    segments: List<LayoutSegment>,
    context: GeocodingContext?,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
) = segments
    .filter { segment -> overlapsAddressInterval(segment, context, startAddress, endAddress) }
    .map { s -> if (s.sourceId is IndexedId) s to s.sourceId else s to null }

private fun overlapsAddressInterval(
    segment: LayoutSegment,
    context: GeocodingContext?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && getStartAddress(segment, context)?.let { it < end } == true) &&
            (start == null || context != null && getEndAddress(segment, context)?.let { it > start } == true)

private fun getStartAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.points.first())?.first

private fun getEndAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.points.last())?.first

fun getGeometryTranslation(key: String?) = geometryTranslations[key] ?: ""

private val geometryTranslations = mapOf(
    "APPROVED_PLAN" to "Suunniteltu",
    "UNDER_CONSTRUCTION" to "Toteutuksessa",
    "IN_USE" to "Valmis",
    "RAILWAY_PLAN" to "Ratasuunnitelma",
    "RAILWAY_CONSTRUCTION_PLAN" to "Rakentamissuunnitelma",
    "RENOVATION_PLAN" to "Peruskorjaus",
    "ENHANCED_RENOVATION_PLAN" to "Perusparannus / rakenteen parantaminen",
    "MAINTENANCE" to "Kunnossapitovaihe",
    "NEW_INVESTMENT" to "Uusinvestointi",
    "REMOVED_FROM_USE" to "Poistettu"
)
