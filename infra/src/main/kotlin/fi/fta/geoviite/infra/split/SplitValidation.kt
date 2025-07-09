package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.validate
import fi.fta.geoviite.infra.publication.validationError
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.util.produceIf
import kotlin.math.min

const val VALIDATION_SPLIT = "$VALIDATION.split"

internal fun validateSourceGeometry(
    draftAddresses: AlignmentAddresses<LocationTrackM>?,
    officialAddressPoint: AlignmentAddresses<LocationTrackM>?,
): LayoutValidationIssue? {
    return if (draftAddresses == null || officialAddressPoint == null) {
        LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.no-geometry")
    } else {
        val officialPoints = officialAddressPoint.allPoints

        draftAddresses.allPoints
            .withIndex()
            .firstOrNull { (targetIndex, targetPoint) ->
                val idx = min(officialPoints.lastIndex, targetIndex)
                !targetPoint.isSame(officialPoints[idx])
            }
            ?.let { (_, point) ->
                LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.geometry-changed", mapOf("point" to point))
            }
    }
}

internal fun validateSplitContent(
    trackVersions: List<LayoutRowVersion<LocationTrack>>,
    switchVersions: List<LayoutRowVersion<LayoutSwitch>>,
    publicationSplits: Collection<Split>,
    allowMultipleSplits: Boolean,
): List<Pair<Split, LayoutValidationIssue>> {
    val multipleSplitsStagedErrors =
        if (!allowMultipleSplits && publicationSplits.size > 1) {
            publicationSplits.map { split ->
                split to LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.multiple-splits-not-allowed")
            }
        } else {
            emptyList()
        }

    val contentErrors =
        publicationSplits.flatMap { split ->
            val containsSource = trackVersions.any { it.id == split.sourceLocationTrackId }
            val containsTargets =
                split.targetLocationTracks.all { tlt -> trackVersions.any { it.id == tlt.locationTrackId } }
            val containsSwitches = split.relinkedSwitches.all { s -> switchVersions.any { sv -> sv.id == s } }
            listOfNotNull(
                    validate(containsSource && containsTargets, ERROR) {
                        "$VALIDATION_SPLIT.split-missing-location-tracks"
                    },
                    validate(containsSwitches, ERROR) { "$VALIDATION_SPLIT.split-missing-switches" },
                )
                .map { e -> split to e }
        }

    return listOf(multipleSplitsStagedErrors, contentErrors).flatten()
}

const val MAX_SPLIT_POINT_OFFSET = 1.0

internal fun validateTargetGeometry(
    operation: SplitTargetOperation,
    targetPoints: List<AddressPoint<LocationTrackM>>?,
    sourcePoints: List<AddressPoint<LocationTrackM>>?,
): LayoutValidationIssue? {
    return if (targetPoints == null || sourcePoints == null) {
        LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.no-geometry")
    } else {
        targetPoints.withIndex().firstNotNullOfOrNull { (targetIndex, targetPoint) ->
            val sourcePoint = sourcePoints.getOrNull(targetIndex)
            if (sourcePoint?.address != targetPoint.address) {
                LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.trackmeters-changed")
            } else if (operation != SplitTargetOperation.TRANSFER && !targetPoint.point.isSame(sourcePoint.point)) {
                LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.geometry-changed")
            } else if (
                operation == SplitTargetOperation.TRANSFER &&
                    lineLength(targetPoint.point, sourcePoint.point) > MAX_SPLIT_POINT_OFFSET
            ) {
                LayoutValidationIssue(ERROR, "$VALIDATION_SPLIT.transfer-geometry-changed")
            } else {
                null
            }
        }
    }
}

internal fun validateTargetTrackNumberIsUnchanged(
    sourceLocationTrack: LocationTrack,
    targetLocationTrack: LocationTrack,
): LayoutValidationIssue? =
    produceIf(sourceLocationTrack.trackNumberId != targetLocationTrack.trackNumberId) {
        validationError(
            "$VALIDATION_SPLIT.source-and-target-track-numbers-are-different",
            "sourceName" to sourceLocationTrack.name,
            "targetName" to targetLocationTrack.name,
        )
    }

internal fun validateSplitStatus(
    track: LocationTrack,
    sourceTrack: LocationTrack,
    split: Split,
): LayoutValidationIssue? =
    produceIf(track.isDraft && split.isPublishedAndWaitingTransfer) {
        validationError("$VALIDATION_SPLIT.track-split-in-progress", "sourceName" to sourceTrack.name)
    }

internal fun validateSplitSourceLocationTrack(locationTrack: LocationTrack, split: Split): List<LayoutValidationIssue> =
    listOfNotNull(
        produceIf(locationTrack.exists) {
            validationError("$VALIDATION_SPLIT.source-not-deleted", "sourceName" to locationTrack.name)
        },
        produceIf(locationTrack.version != split.sourceLocationTrackVersion) {
            validationError("$VALIDATION_SPLIT.source-edited-after-split", "sourceName" to locationTrack.name)
        },
    )
