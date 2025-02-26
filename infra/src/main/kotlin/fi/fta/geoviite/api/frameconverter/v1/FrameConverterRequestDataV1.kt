package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack

fun createValidTrackMeterOrNull(
    trackKilometer: Int?,
    trackMeter: Int?,
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
            val (trackNumberOidOrNull, oidErrors) = createValidTrackNumberOidOrNull(requestTrackNumberOid)

            if (trackNumberOidOrNull == null) {
                null to oidErrors
            } else {
                trackNumberOidLookup[trackNumberOidOrNull]
                    ?.let { tn -> trackNumberLookup[tn] }
                    ?.let { layoutTrackNumber -> layoutTrackNumber to emptyList() }
                    ?: (null to listOf(FrameConverterErrorV1.TrackNumberNotFound))
            }
        }

        requestTrackNumberName != null && requestTrackNumberOid == null -> {
            val (trackNumberNameOrNull, nameErrors) = createValidTrackNumberNameOrNull(requestTrackNumberName)

            if (trackNumberNameOrNull == null) {
                null to nameErrors
            } else {
                trackNumberLookup[trackNumberNameOrNull]?.let { layoutTrackNumber -> layoutTrackNumber to emptyList() }
                    ?: (null to listOf(FrameConverterErrorV1.TrackNumberNotFound))
            }
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
    unvalidatedTrackNumberOid: FrameConverterStringV1?
): Pair<Oid<LayoutTrackNumber>?, List<FrameConverterErrorV1>> {
    return when (unvalidatedTrackNumberOid) {
        null -> null to emptyList()
        else ->
            try {
                Oid<LayoutTrackNumber>(unvalidatedTrackNumberOid.toString()) to emptyList()
            } catch (e: InputValidationException) {
                null to listOf(FrameConverterErrorV1.InvalidTrackNumberOid)
            }
    }
}

fun createValidTrackNumberNameOrNull(
    unvalidatedTrackNumberName: FrameConverterStringV1?
): Pair<TrackNumber?, List<FrameConverterErrorV1>> {
    return when (unvalidatedTrackNumberName) {
        null -> null to emptyList()
        else ->
            try {
                TrackNumber(unvalidatedTrackNumberName.toString()) to emptyList()
            } catch (e: InputValidationException) {
                null to listOf(FrameConverterErrorV1.InvalidTrackNumberName)
            }
    }
}

fun createValidAlignmentNameOrNull(
    unvalidatedLocationTrackName: FrameConverterStringV1?
): Pair<AlignmentName?, List<FrameConverterErrorV1>> {
    return when (unvalidatedLocationTrackName) {
        null -> null to emptyList()
        else ->
            try {
                AlignmentName(unvalidatedLocationTrackName.toString()) to emptyList()
            } catch (e: InputValidationException) {
                null to listOf(FrameConverterErrorV1.InvalidLocationTrackName)
            }
    }
}

fun createValidLocationTrackOidOrNull(
    unvalidatedLocationTrackOid: FrameConverterStringV1?
): Pair<Oid<LocationTrack>?, List<FrameConverterErrorV1>> {
    return when (unvalidatedLocationTrackOid) {
        null -> null to emptyList()
        else ->
            try {
                Oid<LocationTrack>(unvalidatedLocationTrackOid.toString()) to emptyList()
            } catch (e: InputValidationException) {
                null to listOf(FrameConverterErrorV1.InvalidLocationTrackOid)
            }
    }
}
