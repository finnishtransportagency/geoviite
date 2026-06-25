package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.validate
import fi.fta.geoviite.infra.publication.validationError
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMove
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.util.produceIf
import kotlin.math.min

const val VALIDATION_ADMINISTRATIVE_CHANGE = "$VALIDATION.administrative-change"
const val VALIDATION_BOUNDARY_MOVE = "$VALIDATION.track-boundary-move"
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

internal fun validateAdministrativeChangeContent(
    trackVersions: List<LayoutRowVersion<LocationTrack>>,
    switchVersions: List<LayoutRowVersion<LayoutSwitch>>,
    publicationSplits: Collection<Split>,
    publicationTrackBoundaryMoves: Collection<TrackBoundaryMove>,
    allowMultipleAdministrativeChanges: Boolean,
): List<Pair<AdministrativeChange, LayoutValidationIssue>> {
    val multipleChangesStagedErrors =
        if (allowMultipleAdministrativeChanges) listOf()
        else if ((publicationSplits.size + publicationTrackBoundaryMoves.size) > 1) {
            (publicationSplits + publicationTrackBoundaryMoves).map { change ->
                change to LayoutValidationIssue(ERROR, "$VALIDATION_ADMINISTRATIVE_CHANGE.multiple-changes-not-allowed")
            }
        } else {
            emptyList()
        }

    val splitContentErrors = publicationSplits.flatMap { split ->
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

    val boundaryMoveContentErrors = publicationTrackBoundaryMoves.flatMap { move ->
        val containsShortened = trackVersions.any { it.id == move.shortenedLocationTrack.id }
        val containsLengthened = trackVersions.any { it.id == move.lengthenedLocationTrack.id }
        val containsSwitches = move.relinkedSwitches.all { s -> switchVersions.any { sv -> sv.id == s } }
        listOfNotNull(
                validate(containsShortened && containsLengthened, ERROR) {
                    "$VALIDATION_BOUNDARY_MOVE.missing-location-tracks"
                },
                validate(containsSwitches, ERROR) { "$VALIDATION_BOUNDARY_MOVE.missing-switches" },
            )
            .map { e -> move to e }
    }

    return listOf(multipleChangesStagedErrors, splitContentErrors, boundaryMoveContentErrors).flatten()
}

const val MAX_SPLIT_POINT_OFFSET = 1.0

fun validateTargetGeometry(
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
            } else if (operation != SplitTargetOperation.TRANSFER && !targetPoint.point.isSameXY(sourcePoint.point)) {
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
        produceIf(locationTrack.exists) { validationError("$VALIDATION_SPLIT.source-not-deleted") },
        produceIf(locationTrack.version != split.sourceLocationTrackVersion) {
            validationError("$VALIDATION_SPLIT.source-edited-after-split", "sourceName" to locationTrack.name)
        },
    )
