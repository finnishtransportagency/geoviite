package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.publication.VALIDATION
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import kotlin.math.max
import kotlin.math.min

const val VALIDATION_SPLIT = "$VALIDATION.split"

internal fun validateSourceGeometry(
    draftAddresses: AlignmentAddresses?,
    officialAddressPoint: AlignmentAddresses?,
): PublishValidationError? {
    return if (draftAddresses == null || officialAddressPoint == null) {
        PublishValidationError(
            PublishValidationErrorType.ERROR,
            "$VALIDATION_SPLIT.source-no-geometry"
        )
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
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.source-geometry-changed",
                    mapOf("point" to point)
                )
            }
    }
}

internal fun validateSplitContent(
    versions: List<ValidationVersion<LocationTrack>>,
    splits: List<SplitSource>,
): List<Pair<SplitSource, PublishValidationError>> {
    val splitsInValidation = splits.filter { split ->
        versions.any { lt -> split.isPartOf(lt.officialId) }
    }

    return splitsInValidation
        .filterNot { split ->
            val containsSource = versions.any { it.officialId == split.locationTrackId }

            containsSource && split.targetLocationTracks.all { tlt ->
                versions.any { it.officialId == tlt.locationTrackId }
            }
        }.map { split ->
            split to PublishValidationError(
                PublishValidationErrorType.ERROR,
                "$VALIDATION_SPLIT.split-missing-location-tracks"
            )
        }
}

internal fun validateTargetGeometry(
    targetAddresses: AlignmentAddresses?,
    sourceAddresses: AlignmentAddresses?,
): PublishValidationError? {
    return if (targetAddresses == null || sourceAddresses == null) {
        PublishValidationError(
            PublishValidationErrorType.ERROR,
            "$VALIDATION_SPLIT.target-no-geometry"
        )
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
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.target-geometry-changed",
                    mapOf("point" to point)
                )
            }
    }
}

internal fun validateSplitSources(source: SplitSource, locationTrack: LocationTrack): PublishValidationError? {
    return if (source.locationTrackId == locationTrack.id && locationTrack.exists) PublishValidationError(
        PublishValidationErrorType.ERROR, "$VALIDATION_SPLIT.source-not-deleted"
    )
    else null
}
