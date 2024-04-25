package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.publication.PublicationValidationErrorType.ERROR
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.validate
import fi.fta.geoviite.infra.publication.validationError
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.produceIf
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
    trackIds: List<IntId<LocationTrack>>,
    switchIds: List<IntId<TrackLayoutSwitch>>,
    splits: List<Split>,
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
            val containsSource = trackIds.contains(split.sourceLocationTrackId)
            val containsTargets = split.targetLocationTracks.all { tlt -> trackIds.contains(tlt.locationTrackId) }
            val containsSwitches = split.relinkedSwitches.all(switchIds::contains)
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

internal fun validateTargetTrackNumberIsUnchanged(
    sourceLocationTrack: LocationTrack,
    targetLocationTrack: LocationTrack,
): PublicationValidationError? =
    produceIf(sourceLocationTrack.trackNumberId != targetLocationTrack.trackNumberId) {
        validationError(
            "$VALIDATION_SPLIT.source-and-target-track-numbers-are-different",
            // TODO: GVT-2524 add param to localized message
            "sourceName" to sourceLocationTrack.name,
            "trackName" to targetLocationTrack.name,
        )
    }

internal fun validateSplitStatus(
    track: LocationTrack,
    sourceTrack: LocationTrack,
    split: Split,
): PublicationValidationError? =
    produceIf(track.isDraft && split.isPublishedAndWaitingTransfer) {
        // TODO: GVT-2524 add localized message
        validationError("$VALIDATION_SPLIT.previous-split-in-progress", "sourceName" to sourceTrack.name)
    }

internal fun validateSplitSourceLocationTrack(
    locationTrack: LocationTrack,
    split: Split,
): List<PublicationValidationError> = listOfNotNull(
    produceIf(locationTrack.exists) {
        // TODO: GVT-2524 add param to localized message
        validationError("$VALIDATION_SPLIT.source-not-deleted", "sourceName" to locationTrack.name)
    },
    produceIf(locationTrack.version != split.sourceLocationTrackVersion) {
        // TODO: GVT-2524 add localized message
        validationError("$VALIDATION_SPLIT.source-edited-after-split", "sourceName" to locationTrack.name)
    }
)
