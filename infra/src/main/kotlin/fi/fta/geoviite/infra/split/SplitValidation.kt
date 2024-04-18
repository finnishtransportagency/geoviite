package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.publication.PublicationValidationErrorType.ERROR
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.publication.validate
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.LocalizationKey
import kotlin.math.min

const val VALIDATION_SPLIT = "$VALIDATION.split"

internal fun validateSourceGeometry(
    draftAddresses: AlignmentAddresses?,
    officialAddressPoint: AlignmentAddresses?,
): PublicationValidationError? {
    return if (draftAddresses == null || officialAddressPoint == null) {
        PublicationValidationError(ERROR, "$VALIDATION_SPLIT.no-geometry")
    } else {
        val officialPoints = officialAddressPoint.allPoints

        draftAddresses
            .allPoints
            .withIndex()
            .firstOrNull { (targetIndex, targetPoint) ->
                val idx = min(officialPoints.lastIndex, targetIndex)
                !targetPoint.isSame(officialPoints[idx])
            }
            ?.let { (_, point) ->
                PublicationValidationError(
                    ERROR,
                    "$VALIDATION_SPLIT.geometry-changed",
                    mapOf("point" to point),
                )
            }
    }
}

internal fun validateSplitContent(
    trackVersions: List<ValidationVersion<LocationTrack>>,
    switchVersions: List<ValidationVersion<TrackLayoutSwitch>>,
    splits: Collection<Split>,
    allowMultipleSplits: Boolean,
): List<Pair<Split, PublicationValidationError>> {
    val multipleSplitsStagedErrors = if (!allowMultipleSplits && splits.size > 1) {
        splits.map { split ->
            split to PublicationValidationError(ERROR, "$VALIDATION_SPLIT.multiple-splits-not-allowed")
        }
    } else {
        emptyList()
    }

    val contentErrors = splits
        .filter { it.isPending }
        .flatMap { split ->
            val containsSource = trackVersions.any { it.officialId == split.sourceLocationTrackId }
            val containsTargets = split.targetLocationTracks.all { tlt ->
                trackVersions.any { it.officialId == tlt.locationTrackId }
            }
            val containsSwitches = split.relinkedSwitches.all { s -> switchVersions.any { sv -> sv.officialId == s } }
            listOfNotNull(
                validate(containsSource && containsTargets, ERROR) {
                    "$VALIDATION_SPLIT.split-missing-location-tracks"
                },
                validate(containsSwitches, ERROR) {
                    "$VALIDATION_SPLIT.split-missing-switches"
                },
            ).map { e -> split to e }
        }

    return listOf(multipleSplitsStagedErrors, contentErrors).flatten()
}

const val MAX_SPLIT_POINT_OFFSET = 1.0

internal fun validateTargetGeometry(
    operation: SplitTargetOperation,
    targetPoints: List<AddressPoint>?,
    sourcePoints: List<AddressPoint>?,
): PublicationValidationError? {
    return if (targetPoints == null || sourcePoints == null) {
        PublicationValidationError(ERROR, "$VALIDATION_SPLIT.no-geometry")
    } else {
        targetPoints
            .withIndex()
            .firstNotNullOfOrNull { (targetIndex, targetPoint) ->
                val sourcePoint = sourcePoints.getOrNull(targetIndex)
                if (sourcePoint?.address != targetPoint.address) {
                    println("sourcePoint=$sourcePoint targetPoint=$targetPoint")
                    PublicationValidationError(ERROR, "$VALIDATION_SPLIT.trackmeters-changed")
                } else if (operation != SplitTargetOperation.TRANSFER && !targetPoint.point.isSame(sourcePoint.point)) {
                    PublicationValidationError(ERROR, "$VALIDATION_SPLIT.geometry-changed")
                } else if (operation == SplitTargetOperation.TRANSFER && lineLength(targetPoint.point, sourcePoint.point) > MAX_SPLIT_POINT_OFFSET) {
                    PublicationValidationError(ERROR, "$VALIDATION_SPLIT.transfer-geometry-changed")
                } else {
                    null
                }
            }
    }
}

internal fun validateTargetTrackNumber(
    sourceLocationTrack: LocationTrack,
    targetLocationTrack: LocationTrack,
): PublicationValidationError? =
    if (sourceLocationTrack.trackNumberId != targetLocationTrack.trackNumberId) {
        PublicationValidationError(
            ERROR,
            LocalizationKey("$VALIDATION_SPLIT.source-and-target-track-numbers-are-different"),
            localizationParams("trackName" to targetLocationTrack.name),
        )
    } else {
        null
    }

internal fun validateSplitSourceLocationTrack(
    locationTrack: LocationTrack,
): PublicationValidationError? {
    return if (locationTrack.exists) {
        PublicationValidationError(ERROR, "$VALIDATION_SPLIT.source-not-deleted")
    } else {
        null
    }
}
