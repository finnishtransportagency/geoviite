package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType.ERROR
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.publication.validate
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import kotlin.math.max
import kotlin.math.min

const val VALIDATION_SPLIT = "$VALIDATION.split"

internal fun validateSourceGeometry(
    draftAddresses: AlignmentAddresses?,
    officialAddressPoint: AlignmentAddresses?,
): PublishValidationError? {
    return if (draftAddresses == null || officialAddressPoint == null) {
        PublishValidationError(ERROR, "$VALIDATION_SPLIT.source-no-geometry")
    } else {
        val officialPoints = officialAddressPoint.allPoints

        draftAddresses
            .allPoints
            .withIndex()
            .firstOrNull { (targetIndex, targetPoint) ->
                val idx = min(officialPoints.lastIndex, targetIndex)
                !targetPoint.isSame(officialPoints[idx])
            }?.let { (_, point) ->
                PublishValidationError(
                    ERROR,
                    "$VALIDATION_SPLIT.source-geometry-changed",
                    mapOf("point" to point),
                )
            }
    }
}

internal fun validateSplitContent(
    trackVersions: List<ValidationVersion<LocationTrack>>,
    switchVersions: List<ValidationVersion<TrackLayoutSwitch>>,
    splits: Collection<Split>,
): List<Pair<Split, PublishValidationError>> {
    return splits
        .filter { it.isPending }
        .flatMap { split ->
            val containsSource = trackVersions.any { it.officialId == split.locationTrackId }
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
}

internal fun validateTargetGeometry(
    targetAddresses: AlignmentAddresses?,
    sourceAddresses: AlignmentAddresses?,
): PublishValidationError? {
    return if (targetAddresses == null || sourceAddresses == null) {
        PublishValidationError(ERROR, "$VALIDATION_SPLIT.target-no-geometry")
    } else {
        val sourcePoints = sourceAddresses.allPoints
        val startIndex = max(0, sourcePoints.indexOfFirst { it.isSame(targetAddresses.startPoint) })

        targetAddresses.allPoints
            .withIndex()
            .firstOrNull { (targetIndex, targetPoint) ->
                val idx = min(sourcePoints.lastIndex, startIndex + targetIndex)
                !targetPoint.isSame(sourcePoints[idx])
            }?.let { (_, point) ->
                PublishValidationError(
                    ERROR,
                    "$VALIDATION_SPLIT.target-geometry-changed",
                    mapOf("point" to point),
                )
            }
    }
}

internal fun validateTargetTrackNumber(
    sourceLocationTrack: LocationTrack,
    targetLocationTrack: LocationTrack,
) = if (sourceLocationTrack.trackNumberId != targetLocationTrack.trackNumberId) {
    PublishValidationError(
        ERROR,
        "$VALIDATION_SPLIT.duplicate-on-different-track-number",
        mapOf("duplicateTrack" to targetLocationTrack.name)
    )
} else {
    null
}

internal fun validateSplitSourceLocationTrack(
    locationTrack: LocationTrack,
): PublishValidationError? {
    return if (locationTrack.exists) {
        PublishValidationError(ERROR, "$VALIDATION_SPLIT.source-not-deleted")
    } else {
        null
    }
}