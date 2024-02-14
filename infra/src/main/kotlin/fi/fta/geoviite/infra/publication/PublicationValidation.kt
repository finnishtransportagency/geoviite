package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.geocoding.*
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublishValidationErrorType.ERROR
import fi.fta.geoviite.infra.publication.PublishValidationErrorType.WARNING
import fi.fta.geoviite.infra.switchLibrary.SwitchConnectivityType
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.switchConnectivityType
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.rangesOfConsecutiveIndicesOf
import kotlin.math.PI

const val VALIDATION = "validation.layout"
const val VALIDATION_TRACK_NUMBER = "$VALIDATION.track-number"
const val VALIDATION_KM_POST = "$VALIDATION.km-post"
const val VALIDATION_REFERENCE_LINE = "$VALIDATION.reference-line"
const val VALIDATION_LOCATION_TRACK = "$VALIDATION.location-track"
const val VALIDATION_GEOCODING = "$VALIDATION.geocoding"
const val VALIDATION_SWITCH = "$VALIDATION.switch"

private const val JOINT_LOCATION_DELTA = 0.5
const val MAX_LAYOUT_POINT_ANGLE_CHANGE = PI / 2
const val MAX_LAYOUT_METER_LENGTH = 2.0
const val MAX_KM_POST_OFFSET = 10.0

fun validateDraftTrackNumberFields(trackNumber: TrackLayoutTrackNumber): List<PublishValidationError> =
    listOfNotNull(validate(trackNumber.state.isPublishable()) { "$VALIDATION_TRACK_NUMBER.state.${trackNumber.state}" })

fun validateTrackNumberReferences(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine?,
    kmPosts: List<TrackLayoutKmPost>,
    locationTracks: List<LocationTrack>,
    publishKmPostIds: List<IntId<TrackLayoutKmPost>>,
    publishedTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> = listOfNotNull(
    validate(referenceLine != null) { "$VALIDATION_TRACK_NUMBER.reference-line.null" },
    locationTracks.filter(LocationTrack::exists).let { existingTracks ->
        validateWithParams(trackNumber.exists || existingTracks.isEmpty()) {
            val existingNames = existingTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted" to localizationParams("locationTracks" to existingNames)
        }
    },
    locationTracks.filterNot { track -> isPublished(track, publishedTrackIds) }.let { unpublishedTracks ->
        validateWithParams(unpublishedTracks.isEmpty()) {
            val unpublishedNames = unpublishedTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_TRACK_NUMBER.location-track.not-published" to localizationParams("locationTracks" to unpublishedNames)
        }
    },
    kmPosts.filter(TrackLayoutKmPost::exists).let { existingKmPosts ->
        validateWithParams(trackNumber.exists || existingKmPosts.isEmpty()) {
            val existingNames = existingKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.reference-deleted" to localizationParams("kmPosts" to existingNames)
        }
    },
    kmPosts.filterNot { kmPost -> isPublished(kmPost, publishKmPostIds) }.let { unpublishedKmPosts ->
        validateWithParams(unpublishedKmPosts.isEmpty()) {
            val unpublishedNames = unpublishedKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.not-published" to localizationParams("kmPosts" to unpublishedNames)
        }
    },
)

//Location is validated by GeocodingContext
fun validateDraftKmPostFields(kmPost: TrackLayoutKmPost): List<PublishValidationError> =
    listOfNotNull(validate(kmPost.state.isPublishable()) { "$VALIDATION_KM_POST.state.${kmPost.state}" })

fun validateKmPostReferences(
    kmPost: TrackLayoutKmPost,
    trackNumber: TrackLayoutTrackNumber?,
    referenceLine: ReferenceLine?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
): List<PublishValidationError> = listOfNotNull(
    validate(trackNumber != null) { "$VALIDATION_KM_POST.track-number.null" },
    validate(referenceLine != null) { "$VALIDATION_KM_POST.reference-line.null" },
    validateWithParams(!kmPost.exists || trackNumber == null || trackNumber.state.isLinkable()) {
        "$VALIDATION_KM_POST.track-number.state.${trackNumber?.state}" to localizationParams("trackNumber" to trackNumber?.number)
    },
    validateWithParams(trackNumber == null || kmPost.trackNumberId == trackNumber.id) {
        "$VALIDATION_KM_POST.track-number.not-official" to localizationParams("trackNumber" to trackNumber?.number)
    },
    validateWithParams(trackNumber == null || isPublished(trackNumber, publishTrackNumberIds)) {
        "$VALIDATION_KM_POST.track-number.not-published" to localizationParams("trackNumber" to trackNumber?.number)
    },
)

fun validateDraftSwitchFields(switch: TrackLayoutSwitch): List<PublishValidationError> = listOfNotNull(
    validate(switch.stateCategory.isPublishable()) { "$VALIDATION_SWITCH.state-category.${switch.stateCategory}" },
)

fun validateSwitchLocationTrackLinkReferences(
    switch: TrackLayoutSwitch,
    locationTracks: List<LocationTrack>,
    publishLocationTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> {
    val notPublishedTracks = locationTracks.mapNotNull { locationTrack ->
        validateWithParams(isPublished(locationTrack, publishLocationTrackIds)) {
            "$VALIDATION_SWITCH.location-track.not-published" to localizationParams("locationTrack" to locationTrack.name)
        }
    }

    val noReferenceTracks = listOfNotNull(locationTracks.filter(LocationTrack::exists).let { existingTracks ->
        validateWithParams(switch.exists || existingTracks.isEmpty()) {
            val existingNames = existingTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_SWITCH.location-track.reference-deleted" to localizationParams("locationTracks" to existingNames)
        }
    })

    return notPublishedTracks + noReferenceTracks
}

fun validateSwitchLocation(switch: TrackLayoutSwitch): List<PublishValidationError> =
    listOfNotNull(validate(switch.joints.isNotEmpty()) {
        "$VALIDATION_SWITCH.no-location"
    })

fun validateSwitchNameDuplication(
    switch: TrackLayoutSwitch,
    draftsBySameName: List<TrackLayoutSwitch>,
    officialsBySameName: List<TrackLayoutSwitch>,
): List<PublishValidationError> {
    return if (switch.exists) {
        val officialDuplicateExists = officialsBySameName.any { official -> official.id != switch.id }
        val stagedDuplicateExists = draftsBySameName.any { draft -> draft.id != switch.id }

        listOfNotNull(
            validateWithParams(stagedDuplicateExists || !officialDuplicateExists) {
                "$VALIDATION_SWITCH.duplicate-name-official" to localizationParams("switch" to switch.name)
            },
            validateWithParams(!stagedDuplicateExists) {
                "$VALIDATION_SWITCH.duplicate-name-draft" to localizationParams("switch" to switch.name)
            },
        )
    } else {
        emptyList()
    }
}

fun validateSwitchLocationTrackLinkStructure(
    switch: TrackLayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): List<PublishValidationError> {
    val existingTracks = locationTracks.filter { (track, _) -> track.exists }
    val segmentGroups = locationTracks
        .filter { (track, _) -> track.exists } // Only consider the non-deleted tracks for switch alignments
        .map { (track, alignment) -> track to alignment.segments.filter { segment -> segment.switchId == switch.id } }
        .filter { (_, segments) -> segments.isNotEmpty() }

    val structureJoints = collectJoints(structure)
    val segmentJoints = segmentGroups.map { (track, group) -> collectJoints(track, group) }

    val topologyLinks = collectTopologyEndLinks(existingTracks, switch)

    return if (switch.exists) listOfNotNull(
        segmentGroups.filterNot { (_, group) -> areSegmentsContinuous(group) }.let { errorGroups ->
            validateWithParams(errorGroups.isEmpty()) {
                val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                "$VALIDATION_SWITCH.location-track.not-continuous" to localizationParams("locationTracks" to errorTrackNames)
            }
        },
        segmentGroups.filterNot { (_, group) -> segmentAndJointLocationsAgree(switch, group) }.let { errorGroups ->
            validateWithParams(errorGroups.isEmpty(), WARNING) {
                val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to localizationParams("locationTracks" to errorTrackNames)
            }
        },
        topologyLinks.filterNot { (_, group) -> topologyLinkAndJointLocationsAgree(switch, group) }.let { errorGroups ->
            validateWithParams(errorGroups.isEmpty(), WARNING) {
                val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to localizationParams("locationTracks" to errorTrackNames)
            }
        },
        segmentJoints.filterNot { (_, group) -> alignmentJointGroupFound(group, structureJoints) }.let { errorGroups ->
            validateWithParams(errorGroups.isEmpty()) {
                val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                "$VALIDATION_SWITCH.location-track.wrong-joint-sequence" to localizationParams("locationTracks" to errorTrackNames)
            }
        },
    ) + validateSwitchTopologicalConnectivity(switch, structure, locationTracks, null) else listOf()
}

fun validateLocationTrackSwitchConnectivity(
    layoutTrack: LocationTrack,
    alignment: LayoutAlignment,
): List<PublishValidationError> {
    val segmentStartSwitch = alignment.segments.first().switchId
    val segmentEndSwitch = alignment.segments.last().switchId
    val topologyStartSwitch = layoutTrack.topologyStartSwitch?.switchId
    val topologyEndSwitch = layoutTrack.topologyEndSwitch?.switchId

    val hasStartSwitch = segmentStartSwitch != null || topologyStartSwitch != null
    val hasEndSwitch = segmentEndSwitch != null || topologyEndSwitch != null

    return when (layoutTrack.topologicalConnectivity) {
        TopologicalConnectivityType.NONE -> {
            listOfNotNull(
                validate(!hasStartSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.start-switch-is-topologically-connected"
                },
                validate(!hasEndSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.end-switch-is-topologically-connected"
                },
            )
        }

        TopologicalConnectivityType.START -> {
            listOfNotNull(
                validate(hasStartSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.start-switch-missing"
                },
                validate(!hasEndSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.end-switch-is-topologically-connected"
                },
            )
        }

        TopologicalConnectivityType.END -> {
            listOfNotNull(
                validate(!hasStartSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.start-switch-is-topologically-connected"
                },
                validate(hasEndSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.end-switch-missing"
                },
            )
        }

        TopologicalConnectivityType.START_AND_END -> {
            listOfNotNull(
                validate(hasStartSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.start-switch-missing"
                },
                validate(hasEndSwitch, WARNING) {
                    "$VALIDATION_LOCATION_TRACK.topological-connectivity.end-switch-missing"
                }
            )
        }
    }
}

fun validateSwitchTopologicalConnectivity(
    switch: TrackLayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    validatingTrack: LocationTrack?,
): List<PublishValidationError> {
    val connectivityType = switchConnectivityType(structure)
    val nonDuplicateTracks = locationTracks.filter { (locationTrack, _) -> locationTrack.duplicateOf == null }
    val nonDuplicateTracksThroughJoints = getTracksThroughJoints(structure, nonDuplicateTracks, switch)
    return listOfNotNull(
        validateFrontJointTopology(switch, structure, locationTracks, validatingTrack),
        validateExcessTracksThroughJoint(connectivityType, nonDuplicateTracksThroughJoints, switch.name, validatingTrack),
        validateSwitchAlignmentTopology(switch.id, structure, connectivityType, locationTracks, switch.name, validatingTrack),
    )
}

fun switchOrTrackLinkageKey(validatingTrack: LocationTrack?) =
    if (validatingTrack != null) "$VALIDATION_LOCATION_TRACK.switch-linkage" else "$VALIDATION_SWITCH.track-linkage"

private fun <T> getTracksThroughJoints(
    structure: SwitchStructure,
    tracks: List<Pair<T, LayoutAlignment>>,
    switch: TrackLayoutSwitch,
): Map<JointNumber, List<T>> {
    val tracksThroughJoint = structure.joints.map { it.number }.associateWith { jointNumber ->
        tracks.filter { (_, alignment) ->
            val jointLinkedIndexRange = alignment.segments.mapIndexedNotNull { i, segment ->
                if (segment.switchId == switch.id && (segment.startJointNumber == jointNumber || segment.endJointNumber == jointNumber)) i else null
            }
            jointLinkedIndexRange.isNotEmpty() && jointLinkedIndexRange.first() > 0 && jointLinkedIndexRange.last() < alignment.segments.lastIndex
        }.map { (locationTrack, _) -> locationTrack }
    }
    return tracksThroughJoint
}

private fun validateFrontJointTopology(
    switch: TrackLayoutSwitch,
    switchStructure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    validatingTrack: LocationTrack?,
): PublishValidationError? {
    val connectivityType = switchConnectivityType(switchStructure)
    fun tracksHaveOkFrontJointLink(tracks: List<Pair<LocationTrack, LayoutAlignment>>) =
        tracks.any { (locationTrack, _) ->
            val topoStart =
                locationTrack.topologyStartSwitch?.switchId == switch.id && locationTrack.topologyStartSwitch.jointNumber == connectivityType.frontJoint
            val topoEnd =
                locationTrack.topologyEndSwitch?.switchId == switch.id && locationTrack.topologyEndSwitch.jointNumber == connectivityType.frontJoint
            val tracksThroughFrontJoint = if (connectivityType.frontJoint == null) {
                listOf()
            } else getTracksThroughJoints(switchStructure, tracks, switch)[connectivityType.frontJoint]
            topoStart || topoEnd || !tracksThroughFrontJoint.isNullOrEmpty()
        }

    val okFrontJointLinkInDuplicates = tracksHaveOkFrontJointLink(locationTracks)
    val okFrontJointLinkInNonDuplicates =
        tracksHaveOkFrontJointLink(locationTracks.filter { it.first.duplicateOf == null })

    return validateWithParams(
        connectivityType.frontJoint == null || okFrontJointLinkInNonDuplicates, WARNING
    ) {
        val key = "${switchOrTrackLinkageKey(validatingTrack)}.${
            if (okFrontJointLinkInDuplicates) "front-joint-only-duplicate-connected"
            else "front-joint-not-connected"
        }"

        key to localizationParams("switch" to switch.name.toString())
    }
}

private fun validateExcessTracksThroughJoint(
    connectivityType: SwitchConnectivityType,
    tracksThroughJoint: Map<JointNumber, List<LocationTrack>>,
    switchName: SwitchName,
    validatingTrack: LocationTrack?,
): PublishValidationError? {
    val excesses = tracksThroughJoint.filter { (joint, tracks) ->
        joint != connectivityType.sharedJoint && tracks.count(LocationTrack::isDraft) > 1
    }
    val someoneElseIsResponsible = validatingTrack?.let {
        excesses.values.flatten().none { excess -> excess.id == validatingTrack.id }
    } ?: false

    return validateWithParams(excesses.isEmpty() || someoneElseIsResponsible, WARNING) {
        val trackNames = excesses.entries
            .sortedBy { (jointNumber, _) -> jointNumber.intValue }
            .joinToString { (jointNumber, tracks) ->
                "${jointNumber.intValue} (${tracks.sortedBy { it.name }.joinToString { it.name }})"
            }

        "${switchOrTrackLinkageKey(validatingTrack)}.multiple-tracks-through-joint" to
                localizationParams("locationTracks" to trackNames, "switch" to switchName.toString())
    }
}

private fun alignmentsAreLinked(
    switchAlignment: List<JointNumber>,
    trackAlignment: LayoutAlignment,
    switchId: DomainId<TrackLayoutSwitch>,
): Boolean {
    val hasStart = alignmentHasSwitchJointLink(trackAlignment, switchId, switchAlignment.first())
    val hasEnd = alignmentHasSwitchJointLink(trackAlignment, switchId, switchAlignment.last())
    return hasStart && hasEnd
}

fun validateSwitchAlignmentTopology(
    switchId: DomainId<TrackLayoutSwitch>,
    switchStructure: SwitchStructure,
    connectivityType: SwitchConnectivityType,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switchName: SwitchName,
    validatingTrack: LocationTrack?,
): PublishValidationError? {
    val nonDuplicateTracks = locationTracks.filter { (lt) -> lt.duplicateOf == null }
    val switchAlignmentsUnlinkedToNonduplicates = connectivityType.trackLinkedAlignmentsJoints.filter { switchAlignment ->
        nonDuplicateTracks.none { (_, alignment) ->
            alignmentsAreLinked(switchAlignment, alignment, switchId)
        }
    }
    val switchAlignmentsUnlinkedToAny = connectivityType.trackLinkedAlignmentsJoints.filter { switchAlignment ->
        locationTracks.none { (_, alignment) ->
            alignmentsAreLinked(switchAlignment, alignment, switchId)
        }
    }
    val switchAlignmentsLinkedToOnlyDuplicates =
        switchAlignmentsUnlinkedToNonduplicates.subtract(switchAlignmentsUnlinkedToAny.toSet())

    // trackLinkedAlignmentsJoints splits up switch alignments going through the center on rail crossings; but it's
    // possible that the alignment was supposed to actually go through the entire crossing. So, if the switch has any
    // unlinked alignments, a track alignment that's only linked to a split alignment could in fact be the cause, so
    // we need to check the unsplit switch alignments instead.
    val trackBeingValidatedIsConnectedToFullAlignment =
        nonDuplicateTracks.find { (lt) -> lt == validatingTrack }?.let { (_, validatingAlignment) ->
            switchStructure.alignments.any { switchAlignment ->
                alignmentsAreLinked(switchAlignment.jointNumbers, validatingAlignment, switchId)
            }
        } ?: false

    return validateWithParams(
        switchAlignmentsUnlinkedToNonduplicates.isEmpty() || trackBeingValidatedIsConnectedToFullAlignment,
        WARNING
    ) {
        val alignmentsString = switchAlignmentsUnlinkedToNonduplicates.joinToString { alignment ->
            alignment.joinToString("-") { joint -> joint.intValue.toString() }
        }
        val key =
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-alignment-${if (switchAlignmentsLinkedToOnlyDuplicates.isEmpty()) "not-connected" else "only-connected-to-duplicate"}"

        key to localizationParams("locationTracks" to alignmentsString, "switch" to switchName.toString())
    }
}

private fun alignmentHasSwitchJointLink(
    alignment: LayoutAlignment,
    switchId: DomainId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
) = alignment.segments.any { segment ->
    segment.switchId == switchId && jointNumber in listOfNotNull(
        segment.startJointNumber, segment.endJointNumber
    )
}

fun validateDuplicateOfState(
    locationTrack: LocationTrack,
    duplicateOfLocationTrack: LocationTrack?,
    duplicateOfLocationTrackDraftName: AlignmentName?,
    duplicates: List<LocationTrack>,
): List<PublishValidationError> {
    val duplicateNameParams = localizationParams(
        "duplicateTrack" to (duplicateOfLocationTrack?.name ?: duplicateOfLocationTrackDraftName)
    )
    return if (duplicateOfLocationTrack == null) {
        listOfNotNull(
            // Non-null reference, but the duplicateOf track doesn't exist in validation context
            validateWithParams(locationTrack.duplicateOf == null) {
                "$VALIDATION_LOCATION_TRACK.duplicate-of.not-published" to duplicateNameParams
            },
        )
    } else {
        listOfNotNull(
            validateWithParams(locationTrack.state.isRemoved() || duplicateOfLocationTrack.state.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.duplicate-of.state.${duplicateOfLocationTrack.state}" to duplicateNameParams
            },
            validateWithParams(duplicateOfLocationTrack.duplicateOf == null) {
                "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-of-duplicated" to duplicateNameParams
            },
            validateWithParams(duplicates.isEmpty()) {
                val suffix = if (duplicates.size > 1) "-multiple" else ""
                "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-while-duplicated${suffix}" to localizationParams(
                    mapOf("duplicateTrack" to duplicateOfLocationTrack.name,
                        "otherDuplicates" to duplicates.map { track -> track.name }.distinct().joinToString { it })
                )
            },
        )
    }
}

fun validateDraftLocationTrackFields(locationTrack: LocationTrack): List<PublishValidationError> = listOfNotNull(
    validate(locationTrack.state.isPublishable()) { "$VALIDATION_LOCATION_TRACK.state.${locationTrack.state}" },
)

fun validateReferenceLineReference(
    referenceLine: ReferenceLine,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
): List<PublishValidationError> {
    return if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_REFERENCE_LINE.track-number.null")
    )
    else {
        val numberParams = localizationParams("trackNumber" to trackNumber.number)

        listOfNotNull(
            validateWithParams(referenceLine.trackNumberId == trackNumber.id) {
                "$VALIDATION_REFERENCE_LINE.track-number.not-official" to numberParams
            },
            validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
                "$VALIDATION_REFERENCE_LINE.track-number.not-published" to numberParams
            },
        )
    }
}

fun validateLocationTrackReference(
    locationTrack: LocationTrack,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
): List<PublishValidationError> {
    return if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_LOCATION_TRACK.track-number.null")
    )
    else {
        val numberParams = localizationParams("trackNumber" to trackNumber.number)

        listOfNotNull(
            validateWithParams(locationTrack.trackNumberId == trackNumber.id) {
                "$VALIDATION_LOCATION_TRACK.track-number.not-official" to numberParams
            },
            validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
                "$VALIDATION_LOCATION_TRACK.track-number.not-published" to numberParams
            },
            validateWithParams(locationTrack.state.isRemoved() || trackNumber.state.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.track-number.state.${trackNumber.state}" to numberParams
            },
        )
    }
}

data class SegmentSwitch(
    val switch: TrackLayoutSwitch,
    val switchStructure: SwitchStructure,
    val segments: List<LayoutSegment>,
)

fun validateSegmentSwitchReferences(
    locationTrack: LocationTrack,
    segmentSwitches: List<SegmentSwitch>,
    publishSwitchIds: List<IntId<TrackLayoutSwitch>>,
): List<PublishValidationError> {
    return segmentSwitches.flatMap { segmentSwitch ->
        val switch = segmentSwitch.switch
        val segments = segmentSwitch.segments

        val nameLocalizationParams = localizationParams("switch" to switch.name)

        val stateErrors: List<PublishValidationError> = listOfNotNull(
            validateWithParams(segments.all { segment -> switch.id == segment.switchId }) {
                "$VALIDATION_LOCATION_TRACK.switch.not-official" to nameLocalizationParams
            },
            validateWithParams(isPublished(switch, publishSwitchIds)) {
                "$VALIDATION_LOCATION_TRACK.switch.not-published" to nameLocalizationParams
            },
            validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to nameLocalizationParams
            },
        )

        val geometryErrors: List<PublishValidationError> = if (locationTrack.exists && switch.exists) {
            val structureJoints = collectJoints(segmentSwitch.switchStructure)
            val segmentJoints = collectJoints(segments)
            listOfNotNull(
                validateWithParams(areSegmentsContinuous(segments)) {
                    "$VALIDATION_LOCATION_TRACK.switch.alignment-not-continuous" to nameLocalizationParams
                },
                validateWithParams(segmentAndJointLocationsAgree(switch, segments), WARNING) {
                    "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch" to nameLocalizationParams
                },
                validateWithParams(alignmentJointGroupFound(segmentJoints, structureJoints)) {
                    "$VALIDATION_LOCATION_TRACK.switch.wrong-joint-sequence" to localizationParams(
                        "switch" to switch.name,
                        "switchType" to segmentSwitch.switchStructure.baseType.name,
                        "switchJoints" to jointSequence(segmentJoints),
                    )
                },
                validateWithParams(segmentJoints.isNotEmpty()) {
                    "$VALIDATION_LOCATION_TRACK.switch.wrong-links" to nameLocalizationParams
                },
            )
        } else listOf()

        stateErrors + geometryErrors
    }
}

fun validateTopologicallyConnectedSwitchReferences(
    topologicallyConnectedSwitches: List<TrackLayoutSwitch>,
    publishSwitchIds: List<IntId<TrackLayoutSwitch>>,
) = topologicallyConnectedSwitches.mapNotNull { switch ->
    validateWithParams(isPublished(switch, publishSwitchIds)) {
        "$VALIDATION_LOCATION_TRACK.switch.not-published" to localizationParams("switch" to switch.name)
    }
}

private fun jointSequence(joints: List<JointNumber>) =
    joints.joinToString("-") { jointNumber -> "${jointNumber.intValue}" }

fun noGeocodingContext(validationTargetLocalizationPrefix: String) =
    PublishValidationError(ERROR, "$validationTargetLocalizationPrefix.no-context")

fun validateGeocodingContext(
    contextCreateResult: GeocodingContextCreateResult,
    trackNumber: TrackNumber,
): List<PublishValidationError> {
    val context = contextCreateResult.geocodingContext

    val badStartPoint = validateWithParams(contextCreateResult.startPointRejectedReason == null) {
        "$VALIDATION_GEOCODING.start-km-too-long" to localizationParams()
    }

    val kmPostsInWrongOrder =
        context.referencePoints.filter { point -> point.intersectType == WITHIN }.filterIndexed { index, point ->
            val previous = context.referencePoints.getOrNull(index - 1)
            val next = context.referencePoints.getOrNull(index + 1)
            !isOrderOk(previous, point) || !isOrderOk(point, next)
        }.let { invalidPoints ->
            validateWithParams(invalidPoints.isEmpty()) {
                "$VALIDATION_GEOCODING.km-posts-invalid" to localizationParams(
                    "trackNumber" to context.trackNumber.number,
                    "kmNumbers" to invalidPoints.joinToString(", ") { point -> point.kmNumber.toString() },
                )
            }
        }

    val kmPostsFarFromLine = context.referencePoints
        .filter { point -> point.intersectType == WITHIN }
        .filter { point -> point.kmPostOffset > MAX_KM_POST_OFFSET }
        .let { farAwayPoints ->
            validateWithParams(farAwayPoints.isEmpty(), WARNING) {
                "$VALIDATION_GEOCODING.km-posts-far-from-line" to localizationParams(
                    "trackNumber" to context.trackNumber.number,
                    "kmNumbers" to farAwayPoints.joinToString(",") { point -> point.kmNumber.toString() },
                )
            }
        }

    val kmPostsRejected = contextCreateResult.rejectedKmPosts.map { (kmPost, reason) ->
        val kmPostLocalizationParams = mapOf("trackNumber" to trackNumber, "kmNumber" to kmPost.kmNumber)

        when (reason) {
            KmPostRejectedReason.TOO_FAR_APART -> PublishValidationError(
                ERROR, "$VALIDATION_GEOCODING.km-post-too-long", kmPostLocalizationParams
            )

            KmPostRejectedReason.NO_LOCATION -> PublishValidationError(
                ERROR, "$VALIDATION_GEOCODING.km-post-no-location", kmPostLocalizationParams
            )

            KmPostRejectedReason.IS_BEFORE_START_ADDRESS -> PublishValidationError(
                WARNING, "$VALIDATION_GEOCODING.km-post-smaller-than-track-number-start", kmPostLocalizationParams
            )

            KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE -> PublishValidationError(
                WARNING, "$VALIDATION_GEOCODING.km-post-outside-line-before", kmPostLocalizationParams
            )

            KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE -> PublishValidationError(
                WARNING, "$VALIDATION_GEOCODING.km-post-outside-line-after", kmPostLocalizationParams
            )
        }
    }

    return kmPostsRejected + listOfNotNull(kmPostsFarFromLine, kmPostsInWrongOrder, badStartPoint)
}

private fun isOrderOk(previous: GeocodingReferencePoint?, next: GeocodingReferencePoint?) =
    if (previous == null || next == null) true
    else previous.distance < next.distance

fun validateAddressPoints(
    trackNumber: TrackLayoutTrackNumber,
    locationTrack: LocationTrack,
    validationTargetLocalizationPrefix: String,
    geocode: () -> AlignmentAddresses?,
): List<PublishValidationError> = try {
    geocode()?.let { addresses ->
        validateAddressPoints(trackNumber, locationTrack, addresses)
    } ?: listOf(
        PublishValidationError(ERROR, "$validationTargetLocalizationPrefix.no-context", emptyMap()),
    )
} catch (e: ClientException) {
    listOf(PublishValidationError(ERROR, e.localizationKey))
}

fun validateAddressPoints(
    trackNumber: TrackLayoutTrackNumber,
    locationTrack: LocationTrack,
    addresses: AlignmentAddresses,
): List<PublishValidationError> {
    val allPoints = listOf(addresses.startPoint) + addresses.midPoints + listOf(addresses.endPoint)
    val allCoordinates = allPoints.map(AddressPoint::point)
    val allAddresses = allPoints.map(AddressPoint::address)
    val maxRanges = 5
    fun describeAsAddressRanges(indices: List<ClosedRange<Int>>): String =
        indices.take(maxRanges).joinToString(", ") { range ->
            "${allAddresses[range.start].formatDropDecimals()}..${allAddresses[range.endInclusive - 1].formatDropDecimals()}"
        }.let { if (indices.size > maxRanges) "$it..." else it }

    val discontinuousDirectionRanges = describeAsAddressRanges(discontinuousDirectionRangeIndices(allCoordinates))
    val stretchedMeterRanges = describeAsAddressRanges(stretchedMeterRangeIndices(allCoordinates))
    val discontinuousAddressRanges = describeAsAddressRanges(discontinuousAddressRangeIndices(allAddresses))

    return listOfNotNull(
        validateWithParams(addresses.startIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.start-outside-reference-line" to localizationParams(
                "referenceLine" to trackNumber.number,
                "locationTrack" to locationTrack.name,
            )
        },
        validateWithParams(addresses.endIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.end-outside-reference-line" to localizationParams(
                "referenceLine" to trackNumber.number,
                "locationTrack" to locationTrack.name,
            )
        },
        validateWithParams(discontinuousDirectionRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.sharp-angle" to localizationParams(
                "trackNumber" to trackNumber.number,
                "locationTrack" to locationTrack.name,
                "kmNumbers" to discontinuousDirectionRanges
            )
        },
        validateWithParams(stretchedMeterRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.stretched-meters" to localizationParams(
                "trackNumber" to trackNumber.number,
                "locationTrack" to locationTrack.name,
                "kmNumbers" to stretchedMeterRanges
            )
        },
        validateWithParams(discontinuousAddressRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.not-continuous" to localizationParams(
                "trackNumber" to trackNumber.number,
                "locationTrack" to locationTrack.name,
                "kmNumbers" to discontinuousAddressRanges
            )
        },
    )
}

fun validateReferenceLineAlignment(alignment: LayoutAlignment) = validateAlignment(VALIDATION_REFERENCE_LINE, alignment)

fun validateLocationTrackAlignment(alignment: LayoutAlignment) = validateAlignment(VALIDATION_LOCATION_TRACK, alignment)

private fun validateAlignment(errorParent: String, alignment: LayoutAlignment) = listOfNotNull(
    validate(alignment.segments.isNotEmpty()) { "$errorParent.empty-segments" },
    validate(alignment.getMaxDirectionDeltaRads() <= MAX_LAYOUT_POINT_ANGLE_CHANGE) { "$errorParent.points.not-continuous" },
)

private fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segmentGroup: List<LayoutSegment>): Boolean =
    segmentGroup.all { segment -> segmentAndJointLocationsAgree(switch, segment) }

private fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segment: LayoutSegment): Boolean {
    val jointLocations = listOfNotNull(
        segment.startJointNumber?.let { jn -> segment.segmentStart to jn },
        segment.endJointNumber?.let { jn -> segment.segmentEnd to jn },
    )
    return jointLocations.all { (location, jointNumber) ->
        val joint = switch.getJoint(jointNumber)
        joint != null && joint.location.isSame(location, JOINT_LOCATION_DELTA)
    }
}

private fun topologyLinkAndJointLocationsAgree(switch: TrackLayoutSwitch, endLinks: List<TopologyEndLink>): Boolean {
    return endLinks.all { topologyEndLink ->
        val switchJoint = switch.getJoint(topologyEndLink.topologySwitch.jointNumber)
        switchJoint != null && switchJoint.location.isSame(topologyEndLink.point, JOINT_LOCATION_DELTA)
    }
}

private fun alignmentJointGroupFound(
    alignmentJoints: List<JointNumber>,
    structureJointGroups: List<List<JointNumber>>,
) = structureJointGroups.any { structureJoints -> jointGroupMatches(alignmentJoints, structureJoints) }

private fun jointGroupMatches(alignmentJoints: List<JointNumber>, structureJoints: List<JointNumber>): Boolean =
    if (!structureJoints.containsAll(alignmentJoints)) false
    else if (alignmentJoints.size <= 1) true
    else {
        val alignmentStartAndEnd = listOf(alignmentJoints.first(), alignmentJoints.last())
        val structureStartAndEnd = listOf(structureJoints.first(), structureJoints.last())
        alignmentStartAndEnd == structureStartAndEnd || alignmentStartAndEnd == structureStartAndEnd.reversed()
    }

private fun collectJoints(structure: SwitchStructure) = structure.alignments.flatMap { alignment ->
    // For RR/KRV/YRV etc. structures partial alignments are also OK (like 1-5 and 5-2)
    val firstJointNumber = alignment.jointNumbers.first()
    val lastJointNumber = alignment.jointNumbers.last()
    val presentationJointIndex =
        alignment.jointNumbers.indexOfFirst { jointNumber -> jointNumber == structure.presentationJointNumber }
    val partialAlignments =
        if (presentationJointIndex != -1 && firstJointNumber != structure.presentationJointNumber && lastJointNumber != structure.presentationJointNumber) {
            listOf(
                alignment.jointNumbers.subList(0, presentationJointIndex + 1),
                alignment.jointNumbers.subList(presentationJointIndex, alignment.jointNumbers.lastIndex + 1)
            )
        } else listOf()

    listOf(alignment.jointNumbers) + partialAlignments
}

private fun collectJoints(track: LocationTrack, segments: List<LayoutSegment>): Pair<LocationTrack, List<JointNumber>> =
    track to collectJoints(segments)

private fun collectJoints(segments: List<LayoutSegment>): List<JointNumber> {
    val allJoints = segments.flatMap { segment -> listOfNotNull(segment.startJointNumber, segment.endJointNumber) }
    return allJoints.filterIndexed { index, jointNumber -> index == 0 || allJoints[index - 1] != jointNumber }
}

private fun areSegmentsContinuous(segments: List<LayoutSegment>): Boolean = segments.mapIndexed { index, segment ->
    index == 0 || segments[index - 1].segmentEnd.isSame(segment.segmentStart, LAYOUT_COORDINATE_DELTA)
}.all { it }

private fun discontinuousDirectionRangeIndices(points: List<AlignmentPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::directionBetweenPoints).zipWithNext(::isAngleDiffOk), 2)

private fun stretchedMeterRangeIndices(points: List<AlignmentPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::lineLength).map { it <= MAX_LAYOUT_METER_LENGTH }, 1)

private fun discontinuousAddressRangeIndices(addresses: List<TrackMeter>): List<ClosedRange<Int>> =
    rangesOfConsecutiveIndicesOf(false, addresses.zipWithNext(::isAddressDiffOk), 1)

private fun isAngleDiffOk(direction1: Double?, direction2: Double?) =
    direction1 == null || direction2 == null || angleDiffRads(direction1, direction2) <= MAX_LAYOUT_POINT_ANGLE_CHANGE

private fun isAddressDiffOk(address1: TrackMeter?, address2: TrackMeter?): Boolean =
    if (address1 == null || address2 == null) true
    else if (address1 > address2) false
    else if (address1.kmNumber != address2.kmNumber) true
    else (address2.meters - address1.meters).toDouble() in 0.0..MAX_LAYOUT_METER_LENGTH

fun validate(valid: Boolean, type: PublishValidationErrorType = ERROR, error: () -> String) =
    validateWithParams(valid, type) { error() to LocalizationParams.empty }

private fun validateWithParams(
    valid: Boolean,
    type: PublishValidationErrorType = ERROR,
    error: () -> Pair<String, LocalizationParams>,
) = if (!valid) error().let { (key, params) -> PublishValidationError(type, LocalizationKey(key), params) }
else null

private fun <T : Draftable<T>> isPublished(item: T, publishItemIds: List<IntId<T>>) =
    item.draft == null || publishItemIds.contains(item.id)

data class TopologyEndLink(
    val topologySwitch: TopologyLocationTrackSwitch,
    val point: AlignmentPoint,
)

private fun collectTopologyEndLinks(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switch: TrackLayoutSwitch,
): List<Pair<LocationTrack, List<TopologyEndLink>>> = locationTracks.map { (track, alignment) ->
    track to listOfNotNull(track.topologyStartSwitch?.let { topologySwitch ->
        if (topologySwitch.switchId == switch.id) alignment.start?.let { p ->
            TopologyEndLink(topologySwitch, p)
        }
        else null
    }, track.topologyEndSwitch?.let { topologySwitch ->
        if (topologySwitch.switchId == switch.id) alignment.end?.let { p -> TopologyEndLink(topologySwitch, p) }
        else null
    })
}.filter { (_, ends) -> ends.isNotEmpty() }

fun <T> combineVersions(
    officials: List<RowVersion<T>>,
    validations: List<ValidationVersion<T>>,
): Collection<RowVersion<T>> {
    val officialVersions = officials.filterNot { official -> validations.any { v -> v.officialId == official.id } }
    val validationVersions = validations.map { it.validatedAssetVersion }
    return (officialVersions + validationVersions).distinct()
}
