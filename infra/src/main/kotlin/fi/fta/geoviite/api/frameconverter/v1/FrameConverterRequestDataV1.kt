package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.common.LocationTrackName
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.math.BigDecimal

fun createValidTrackMeterOrNull(
    trackKilometer: Int?,
    trackMeter: BigDecimal?,
): Pair<TrackMeter?, List<FrameConverterErrorV1>> {
    return if (trackKilometer == null || trackMeter == null) {
        null to emptyList()
    } else {
        try {
            TrackMeter(trackKilometer, trackMeter) to emptyList()
        } catch (e: IllegalArgumentException) {
            null to listOf(FrameConverterErrorV1.InvalidTrackAddress)
        }
    }
}

fun createValidTrackNumberOrNull(
    requestTrackNumberName: FrameConverterStringV1?,
    requestTrackNumberOid: FrameConverterStringV1?,
    trackNumberLookup: Map<TrackNumber, LayoutTrackNumber?>,
    trackNumberOidLookup: Map<Oid<LayoutTrackNumber>, TrackNumber?>,
): Pair<LayoutTrackNumber?, List<FrameConverterErrorV1>> {
    return when {
        requestTrackNumberName == null && requestTrackNumberOid != null -> {
            findTrackNumberByOid(requestTrackNumberOid, trackNumberLookup, trackNumberOidLookup)
        }

        requestTrackNumberName != null && requestTrackNumberOid == null -> {
            findTrackNumberByName(requestTrackNumberName, trackNumberLookup)
        }

        requestTrackNumberName != null && requestTrackNumberOid != null -> {
            null to listOf(FrameConverterErrorV1.BothNameAndOidForTrackNumber)
        }

        else -> {
            null to listOf(FrameConverterErrorV1.NoTrackNumberSearchCondition)
        }
    }
}

fun createValidTrackNumberOidOrNull(
    unvalidatedTrackNumberOid: FrameConverterStringV1
): Pair<Oid<LayoutTrackNumber>?, List<FrameConverterErrorV1>> {
    return try {
        Oid<LayoutTrackNumber>(unvalidatedTrackNumberOid.toString()) to emptyList()
    } catch (e: InputValidationException) {
        null to listOf(FrameConverterErrorV1.InvalidTrackNumberOid)
    }
}

fun createValidTrackNumberNameOrNull(
    unvalidatedTrackNumberName: FrameConverterStringV1
): Pair<TrackNumber?, List<FrameConverterErrorV1>> {
    return try {
        TrackNumber(unvalidatedTrackNumberName.toString()) to emptyList()
    } catch (e: InputValidationException) {
        null to listOf(FrameConverterErrorV1.InvalidTrackNumberName)
    }
}

fun createValidLocationTrackNameOrNull(
    unvalidatedLocationTrackName: FrameConverterStringV1
): Pair<LocationTrackName?, List<FrameConverterErrorV1>> {
    return try {
        LocationTrackName(unvalidatedLocationTrackName.toString()) to emptyList()
    } catch (e: InputValidationException) {
        null to listOf(FrameConverterErrorV1.InvalidLocationTrackName)
    }
}

fun createValidLocationTrackOidOrNull(
    unvalidatedLocationTrackOid: FrameConverterStringV1
): Pair<Oid<LocationTrack>?, List<FrameConverterErrorV1>> {
    return try {
        Oid<LocationTrack>(unvalidatedLocationTrackOid.toString()) to emptyList()
    } catch (e: InputValidationException) {
        null to listOf(FrameConverterErrorV1.InvalidLocationTrackOid)
    }
}

fun findTrackNumberByName(
    requestTrackNumberName: FrameConverterStringV1,
    trackNumberLookup: Map<TrackNumber, LayoutTrackNumber?>,
): Pair<LayoutTrackNumber?, List<FrameConverterErrorV1>> {
    val (trackNumberNameOrNull, nameErrors) = createValidTrackNumberNameOrNull(requestTrackNumberName)

    return if (trackNumberNameOrNull == null) {
        null to nameErrors
    } else {
        trackNumberLookup[trackNumberNameOrNull]?.let { layoutTrackNumber -> layoutTrackNumber to emptyList() }
            ?: (null to listOf(FrameConverterErrorV1.TrackNumberNotFound))
    }
}

fun findTrackNumberByOid(
    requestTrackNumberOid: FrameConverterStringV1,
    trackNumberLookup: Map<TrackNumber, LayoutTrackNumber?>,
    trackNumberOidLookup: Map<Oid<LayoutTrackNumber>, TrackNumber?>,
): Pair<LayoutTrackNumber?, List<FrameConverterErrorV1>> {
    val (trackNumberOidOrNull, oidErrors) = createValidTrackNumberOidOrNull(requestTrackNumberOid)

    return if (trackNumberOidOrNull == null) {
        null to oidErrors
    } else {
        trackNumberOidLookup[trackNumberOidOrNull]
            ?.let { tn -> trackNumberLookup[tn] }
            ?.let { layoutTrackNumber -> layoutTrackNumber to emptyList() }
            ?: (null to listOf(FrameConverterErrorV1.TrackNumberNotFound))
    }
}
