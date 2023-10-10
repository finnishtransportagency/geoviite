package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.error.LocalizationParams
import fi.fta.geoviite.infra.geocoding.*
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
    listOfNotNull(
        validate(trackNumber.state.isPublishable()) { "$VALIDATION_TRACK_NUMBER.state.${trackNumber.state}" }
    )

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
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted" to mapOf("locationTracks" to existingNames)
        }
    },
    locationTracks.filterNot { track -> isPublished(track, publishedTrackIds) }.let { unpublishedTracks ->
        validateWithParams(unpublishedTracks.isEmpty()) {
            val unpublishedNames = unpublishedTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_TRACK_NUMBER.location-track.not-published" to mapOf("locationTracks" to unpublishedNames)
        }
    },
    kmPosts.filter(TrackLayoutKmPost::exists).let { existingKmPosts ->
        validateWithParams(trackNumber.exists || existingKmPosts.isEmpty()) {
            val existingNames = existingKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.reference-deleted" to mapOf("kmPosts" to existingNames)
        }
    },
    kmPosts.filterNot { kmPost -> isPublished(kmPost, publishKmPostIds) }.let { unpublishedKmPosts ->
        validateWithParams(unpublishedKmPosts.isEmpty()) {
            val unpublishedNames = unpublishedKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.not-published" to mapOf("kmPosts" to unpublishedNames)
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
): List<PublishValidationError> =
    listOfNotNull(
        validate(trackNumber != null) { "$VALIDATION_KM_POST.track-number.null" },
        validate(referenceLine != null) { "$VALIDATION_KM_POST.reference-line.null" },
        validateWithParams(!kmPost.exists || trackNumber == null || trackNumber.state.isLinkable()) {
            "$VALIDATION_KM_POST.track-number.state.${trackNumber?.state}" to mapOf("trackNumber" to trackNumber?.number?.toString())
        },
        validateWithParams(trackNumber == null || kmPost.trackNumberId == trackNumber.id) {
            "$VALIDATION_KM_POST.track-number.not-official" to mapOf("trackNumber" to trackNumber?.number?.toString())
        },
        validateWithParams(trackNumber == null || isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_KM_POST.track-number.not-published" to mapOf("trackNumber" to trackNumber?.number?.toString())
        },
    )

fun validateDraftSwitchFields(switch: TrackLayoutSwitch): List<PublishValidationError> =
    listOfNotNull(
        validate(switch.stateCategory.isPublishable()) { "$VALIDATION_SWITCH.state-category.${switch.stateCategory}" },
    )

fun validateSwitchLocationTrackLinkReferences(
    switch: TrackLayoutSwitch,
    locationTracks: List<LocationTrack>,
    publishLocationTrackIds: List<IntId<LocationTrack>>,
) = locationTracks
    .mapNotNull { locationTrack ->
        validateWithParams(isPublished(locationTrack, publishLocationTrackIds)) {
            "$VALIDATION_SWITCH.location-track.not-published" to mapOf("locationTrack" to locationTrack.name.toString())
        }
    } + listOfNotNull(
    locationTracks
        .filter(LocationTrack::exists)
        .let { existingTracks ->
            validateWithParams(switch.exists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_SWITCH.location-track.reference-deleted" to mapOf("locationTracks" to existingNames)
            }
        }
)

fun validateSwitchLocation(switch: TrackLayoutSwitch): List<PublishValidationError> = listOfNotNull(
    validate(switch.joints.isNotEmpty()) {
        "$VALIDATION_SWITCH.no-location"
    }
)

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
        segmentGroups.filterNot { (_, group) -> areSegmentsContinuous(group) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.not-continuous" to mapOf("locationTracks" to errorTrackNames)
                }
            },
        segmentGroups.filterNot { (_, group) -> segmentAndJointLocationsAgree(switch, group) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty(), WARNING) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to mapOf("locationTracks" to errorTrackNames)
                }
            },
        topologyLinks.filterNot { (_, group) -> topologyLinkAndJointLocationsAgree(switch, group) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty(), WARNING) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to mapOf("locationTracks" to errorTrackNames)
                }
            },
        segmentJoints.filterNot { (_, group) -> alignmentJointGroupFound(group, structureJoints) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.wrong-joint-sequence" to mapOf("locationTracks" to errorTrackNames)
                }
            },
    ) + validateSwitchTopologicalConnectivity(switch, structure, locationTracks) else listOf()
}

private fun validateSwitchTopologicalConnectivity(
    switch: TrackLayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): List<PublishValidationError> {
    val connectivityType = switchConnectivityType(structure)
    val nonDuplicateTracks = locationTracks.filter { it.first.duplicateOf == null }

    val tracksThroughJoint = structure.joints.map { it.number }
        .associateWith { jointNumber ->
            nonDuplicateTracks.filter { (_, alignment) ->
                val jointLinkedIndexRange = alignment.segments.mapIndexedNotNull { i, segment ->
                    if (segment.switchId == switch.id && (segment.startJointNumber == jointNumber || segment.endJointNumber == jointNumber)) i else null
                }
                jointLinkedIndexRange.isNotEmpty() && jointLinkedIndexRange.first() > 0 && jointLinkedIndexRange.last() < alignment.segments.lastIndex
            }.map { (locationTrack, _) -> locationTrack }
        }

    return listOfNotNull(
        validateFrontJointTopology(switch.id, tracksThroughJoint, connectivityType, locationTracks),
        validateExcessTracksThroughJoint(connectivityType, tracksThroughJoint),
        validateSwitchAlignmentTopology(switch.id, connectivityType, nonDuplicateTracks),
    )
}

private fun validateFrontJointTopology(
    switchId: DomainId<TrackLayoutSwitch>,
    tracksThroughJoint: Map<JointNumber, List<LocationTrack>>,
    connectivityType: SwitchConnectivityType,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): PublishValidationError? {
    val tracksThroughFrontJoint = if (connectivityType.frontJoint == null) {
        listOf()
    } else tracksThroughJoint.getOrDefault(connectivityType.frontJoint, listOf())

    fun tracksHaveOkFrontJointLink(tracks: List<Pair<LocationTrack, LayoutAlignment>>) =
        tracks.any { (locationTrack, _) ->
            val topoStart =
                locationTrack.topologyStartSwitch?.switchId == switchId && locationTrack.topologyStartSwitch.jointNumber == connectivityType.frontJoint
            val topoEnd =
                locationTrack.topologyEndSwitch?.switchId == switchId && locationTrack.topologyEndSwitch.jointNumber == connectivityType.frontJoint
            topoStart || topoEnd || tracksThroughFrontJoint.isNotEmpty()
        }

    val okFrontJointLinkInDuplicates = tracksHaveOkFrontJointLink(locationTracks)
    val okFrontJointLinkInNonDuplicates =
        tracksHaveOkFrontJointLink(locationTracks.filter { it.first.duplicateOf == null })

    return validateWithParams(
        connectivityType.frontJoint == null || okFrontJointLinkInNonDuplicates, WARNING
    ) {
        (if (okFrontJointLinkInDuplicates) "$VALIDATION_SWITCH.track-linkage.front-joint-only-duplicate-connected"
        else "$VALIDATION_SWITCH.track-linkage.front-joint-not-connected") to emptyMap()
    }
}

private fun validateExcessTracksThroughJoint(
    connectivityType: SwitchConnectivityType,
    tracksThroughJoint: Map<JointNumber, List<LocationTrack>>,
): PublishValidationError? {
    val excesses =
        tracksThroughJoint.filter { (joint, tracks) -> joint != connectivityType.sharedJoint && tracks.size > 1 }
    return validateWithParams(excesses.isEmpty(), WARNING) {
        val trackNames = excesses.entries
            .sortedBy { (jointNumber, _) -> jointNumber.intValue }
            .joinToString { (jointNumber, tracks) ->
                "${jointNumber.intValue} (${tracks.sortedBy { it.name }.joinToString { it.name }})"
            }

        "$VALIDATION_SWITCH.track-linkage.multiple-tracks-through-joint" to mapOf("locationTracks" to trackNames)
    }
}

private fun validateSwitchAlignmentTopology(
    switchId: DomainId<TrackLayoutSwitch>,
    connectivityType: SwitchConnectivityType,
    nonDuplicateTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): PublishValidationError? {
    val disconnectedAlignments = connectivityType.trackLinkedAlignmentsJoints.filter { switchAlignment ->
        nonDuplicateTracks.none { (_, alignment) ->
            val hasStart = alignmentHasSwitchJointLink(alignment, switchId, switchAlignment.first())
            val hasEnd = alignmentHasSwitchJointLink(alignment, switchId, switchAlignment.last())
            hasStart && hasEnd
        }
    }
    return validateWithParams(disconnectedAlignments.isEmpty(), WARNING) {
        val alignmentsString =
            disconnectedAlignments.joinToString { alignment -> alignment.joinToString("-") { joint -> joint.intValue.toString() } }
        "$VALIDATION_SWITCH.track-linkage.switch-alignment-not-connected" to mapOf("locationTracks" to alignmentsString)
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
    publishLocationTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> =
    if (duplicateOfLocationTrack == null) listOf()
    else listOfNotNull(
        validateWithParams(locationTrack.duplicateOf == duplicateOfLocationTrack.id) {
            "$VALIDATION_REFERENCE_LINE.duplicate-of.not-official" to mapOf("duplicateTrack" to duplicateOfLocationTrack.name.toString())
        },
        validateWithParams(isPublished(duplicateOfLocationTrack, publishLocationTrackIds)) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.not-published" to mapOf("duplicateTrack" to duplicateOfLocationTrack.name.toString())
        },
        validateWithParams(locationTrack.state.isRemoved() || duplicateOfLocationTrack.state.isLinkable()) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.state.${duplicateOfLocationTrack.state}" to mapOf(
                "duplicateTrack" to duplicateOfLocationTrack.name.toString()
            )
        },
        validateWithParams(duplicateOfLocationTrack.duplicateOf == null) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.duplicate" to mapOf("duplicateTrack" to duplicateOfLocationTrack.name.toString())
        }
    )

fun validateDraftLocationTrackFields(locationTrack: LocationTrack): List<PublishValidationError> =
    listOfNotNull(
        validate(locationTrack.state.isPublishable()) { "$VALIDATION_LOCATION_TRACK.state.${locationTrack.state}" },
    )

fun validateReferenceLineReference(
    referenceLine: ReferenceLine,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
) =
    if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_REFERENCE_LINE.track-number.null", emptyMap())
    )
    else listOfNotNull(
        validateWithParams(referenceLine.trackNumberId == trackNumber.id) {
            "$VALIDATION_REFERENCE_LINE.track-number.not-official" to mapOf("trackNumber" to trackNumber.number.toString())
        },
        validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_REFERENCE_LINE.track-number.not-published" to mapOf("trackNumber" to trackNumber.number.toString())
        },
    )

fun validateLocationTrackReference(
    locationTrack: LocationTrack,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
) =
    if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_LOCATION_TRACK.track-number.null", emptyMap())
    )
    else listOfNotNull(
        validateWithParams(locationTrack.trackNumberId == trackNumber.id) {
            "$VALIDATION_LOCATION_TRACK.track-number.not-official" to mapOf("trackNumber" to trackNumber.number.toString())
        },
        validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_LOCATION_TRACK.track-number.not-published" to mapOf("trackNumber" to trackNumber.number.toString())
        },
        validateWithParams(locationTrack.state.isRemoved() || trackNumber.state.isLinkable()) {
            "$VALIDATION_LOCATION_TRACK.track-number.state.${trackNumber.state}" to mapOf("trackNumber" to trackNumber.number.toString())
        },
    )

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

        val stateErrors: List<PublishValidationError> = listOfNotNull(
            validateWithParams(segments.all { segment -> switch.id == segment.switchId }) {
                "$VALIDATION_LOCATION_TRACK.switch.not-official" to mapOf("switch" to switch.name.toString())
            },
            validateWithParams(isPublished(switch, publishSwitchIds)) {
                "$VALIDATION_LOCATION_TRACK.switch.not-published" to mapOf("switch" to switch.name.toString())
            },
            validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to mapOf("switch" to switch.name.toString())
            },
        )
        val geometryErrors: List<PublishValidationError> = if (locationTrack.exists && switch.exists) {
            val structureJoints = collectJoints(segmentSwitch.switchStructure)
            val segmentJoints = collectJoints(segments)
            listOfNotNull(
                validateWithParams(areSegmentsContinuous(segments)) {
                    "$VALIDATION_LOCATION_TRACK.switch.alignment-not-continuous" to mapOf("switch" to switch.name.toString())
                },
                validateWithParams(segmentAndJointLocationsAgree(switch, segments), WARNING) {
                    "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch" to mapOf("switch" to switch.name.toString())
                },
                validateWithParams(alignmentJointGroupFound(segmentJoints, structureJoints)) {
                    "$VALIDATION_LOCATION_TRACK.switch.wrong-joint-sequence" to mapOf(
                        "switch" to switch.name.toString(),
                        "switchType" to segmentSwitch.switchStructure.baseType.name,
                        "switchJoints" to jointSequence(segmentJoints),
                    )
                },
                validateWithParams(segmentJoints.isNotEmpty()) {
                    "$VALIDATION_LOCATION_TRACK.switch.wrong-links" to mapOf(
                        "switch" to switch.name.toString(),
                    )
                },
            )
        } else listOf()

        stateErrors + geometryErrors
    }
}

fun validateTopologicallyConnectedSwitchReferences(
    topologicallyConnectedSwitches: List<TrackLayoutSwitch>,
    publishSwitchIds: List<IntId<TrackLayoutSwitch>>,
): List<PublishValidationError> {
    return topologicallyConnectedSwitches.mapNotNull { switch ->
        validateWithParams(isPublished(switch, publishSwitchIds)) {
            "$VALIDATION_LOCATION_TRACK.switch.not-published" to mapOf("switch" to switch.name.toString())
        }
    }
}

private fun jointSequence(joints: List<JointNumber>) =
    joints.joinToString("-") { jointNumber -> "${jointNumber.intValue}" }

fun noGeocodingContext(validationTargetLocalizationPrefix: String) =
    PublishValidationError(ERROR, "$validationTargetLocalizationPrefix.no-context", emptyMap())

fun validateGeocodingContext(stuff: GeocodingContextCreateResult): List<PublishValidationError> {
    val context = stuff.geocodingContext
    val kmPostsInWrongOrder = context.referencePoints
        .filter { point -> point.intersectType == WITHIN }
        .filterIndexed { index, point ->
            val previous = context.referencePoints.getOrNull(index - 1)
            val next = context.referencePoints.getOrNull(index + 1)
            !isOrderOk(previous, point) || !isOrderOk(point, next)
        }.let { invalidPoints ->
            validateWithParams(invalidPoints.isEmpty()) {
                "$VALIDATION_GEOCODING.km-posts-invalid" to mapOf(
                    "trackNumber" to context.trackNumber.number.toString(),
                    "kmNumbers" to invalidPoints.joinToString(",") { point -> point.kmNumber.toString() },
                )
            }
        }

    val kmPostsFarFromLine = context.referencePoints
        .filter { point -> point.intersectType == WITHIN }
        .filter { point -> point.kmPostOffset > MAX_KM_POST_OFFSET }
        .let { farAwayPoints ->
            validateWithParams(farAwayPoints.isEmpty(), WARNING) {
                "$VALIDATION_GEOCODING.km-posts-far-from-line" to mapOf(
                    "trackNumber" to context.trackNumber.number.toString(),
                    "kmNumbers" to farAwayPoints.joinToString(",") { point -> point.kmNumber.toString() },
                )
            }
        }

    val kmPostsRejected = stuff.rejectedKmPosts.map { (kmPost, reason) ->
        val params = mapOf("trackNumber" to context.trackNumber.number.value, "kmNumber" to kmPost.kmNumber.toString())

        when (reason) {
            KmPostRejectedReason.TOO_FAR_APART -> PublishValidationError(
                ERROR,
                "$VALIDATION_GEOCODING.km-post-too-long",
                params
            )

            KmPostRejectedReason.NO_LOCATION -> PublishValidationError(
                ERROR,
                "$VALIDATION_GEOCODING.km-post-no-location",
                params
            )

            KmPostRejectedReason.IS_BEFORE_START_ADDRESS -> PublishValidationError(
                WARNING,
                "$VALIDATION_GEOCODING.km-post-smaller-than-track-number-start",
                params
            )

            KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE -> PublishValidationError(
                WARNING,
                "$VALIDATION_GEOCODING.km-post-outside-line-before",
                params
            )

            KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE -> PublishValidationError(
                WARNING,
                "$VALIDATION_GEOCODING.km-post-outside-line-after",
                params
            )
        }
    }

    return kmPostsRejected + listOfNotNull(kmPostsFarFromLine, kmPostsInWrongOrder)
}

private fun isOrderOk(previous: GeocodingReferencePoint?, next: GeocodingReferencePoint?) =
    if (previous == null || next == null) true
    else previous.distance < next.distance

fun validateAddressPoints(
    trackNumber: TrackLayoutTrackNumber,
    locationTrack: LocationTrack,
    validationTargetLocalizationPrefix: String,
    geocode: () -> AlignmentAddresses?,
): List<PublishValidationError> =
    try {
        geocode()?.let { addresses ->
            validateAddressPoints(trackNumber, locationTrack, addresses)
        } ?: listOf(
            PublishValidationError(
                ERROR,
                "$validationTargetLocalizationPrefix.no-context",
                emptyMap()
            )
        )
    } catch (e: ClientException) {
        listOf(PublishValidationError(ERROR, e.localizedMessageKey))
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
            "$VALIDATION_GEOCODING.start-outside-reference-line" to mapOf(
                "referenceLine" to trackNumber.number.toString(),
                "locationTrack" to locationTrack.name.toString(),
            )
        },
        validateWithParams(addresses.endIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.end-outside-reference-line" to mapOf(
                "referenceLine" to trackNumber.number.toString(),
                "locationTrack" to locationTrack.name.toString(),
            )
        },
        validateWithParams(discontinuousDirectionRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.sharp-angle" to mapOf(
                "trackNumber" to trackNumber.number.toString(),
                "locationTrack" to locationTrack.name.toString(),
                "kmNumbers" to discontinuousDirectionRanges
            )
        },
        validateWithParams(stretchedMeterRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.stretched-meters" to mapOf(
                "trackNumber" to trackNumber.number.toString(),
                "locationTrack" to locationTrack.name.toString(),
                "kmNumbers" to stretchedMeterRanges
            )
        },
        validateWithParams(discontinuousAddressRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.not-continuous" to mapOf(
                "trackNumber" to trackNumber.number.toString(),
                "locationTrack" to locationTrack.name.toString(),
                "kmNumbers" to discontinuousAddressRanges
            )
        },
    )
}

fun validateReferenceLineAlignment(alignment: LayoutAlignment) =
    validateAlignment(VALIDATION_REFERENCE_LINE, alignment)

fun validateLocationTrackAlignment(alignment: LayoutAlignment) =
    validateAlignment(VALIDATION_LOCATION_TRACK, alignment)

private fun validateAlignment(errorParent: String, alignment: LayoutAlignment) = listOfNotNull(
    validate(alignment.segments.isNotEmpty()) { "$errorParent.empty-segments" },
    validate(areDirectionsContinuous(alignment.allPoints())) { "$errorParent.points.not-continuous" },
)

private fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segmentGroup: List<LayoutSegment>): Boolean =
    segmentGroup.all { segment -> segmentAndJointLocationsAgree(switch, segment) }

private fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segment: LayoutSegment): Boolean {
    val jointLocations = listOfNotNull(
        segment.startJointNumber?.let { jn -> segment.points.first() to jn },
        segment.endJointNumber?.let { jn -> segment.points.last() to jn },
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

private fun collectJoints(structure: SwitchStructure) =
    structure.alignments.flatMap { alignment ->
        // For RR/KRV/YRV etc. structures partial alignments are also OK (like 1-5 and 5-2)
        val firstJointNumber = alignment.jointNumbers.first()
        val lastJointNumber = alignment.jointNumbers.last()
        val presentationJointIndex =
            alignment.jointNumbers.indexOfFirst { jointNumber -> jointNumber == structure.presentationJointNumber }
        val partialAlignments = if (presentationJointIndex != -1 &&
            firstJointNumber != structure.presentationJointNumber &&
            lastJointNumber != structure.presentationJointNumber
        ) {
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

private fun areSegmentsContinuous(segments: List<LayoutSegment>): Boolean =
    segments.mapIndexed { index, segment ->
        index == 0 || segments[index - 1].points.last().isSame(segment.points.first(), LAYOUT_COORDINATE_DELTA)
    }.all { it }

private fun areDirectionsContinuous(points: List<LayoutPoint>): Boolean {
    var prevDirection: Double? = null
    return points.mapIndexed { index, point ->
        val previous = if (index > 0) points[index - 1] else null
        val direction = previous?.let { prev -> directionBetweenPoints(prev, point) }
        val angleOk = isAngleDiffOk(prevDirection, direction)
        prevDirection = direction
        angleOk
    }.all { it }
}

private fun discontinuousDirectionRangeIndices(points: List<LayoutPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::directionBetweenPoints).zipWithNext(::isAngleDiffOk), 2)

private fun stretchedMeterRangeIndices(points: List<LayoutPoint>) =
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

private fun validate(valid: Boolean, type: PublishValidationErrorType = ERROR, error: () -> String) =
    validateWithParams(valid, type) { error() to emptyMap() }

private fun validateWithParams(
    valid: Boolean,
    type: PublishValidationErrorType = ERROR,
    error: () -> Pair<String, LocalizationParams>,
): PublishValidationError? =
    if (!valid) error().let { (key, params) -> PublishValidationError(type, key, params) }
    else null

private fun <T : Draftable<T>> isPublished(item: T, publishItemIds: List<IntId<T>>) =
    item.draft == null || publishItemIds.contains(item.id)

data class TopologyEndLink(
    val topologySwitch: TopologyLocationTrackSwitch,
    val point: LayoutPoint
)

private fun collectTopologyEndLinks(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switch: TrackLayoutSwitch
) = locationTracks
    .map { (track, alignment) ->
        track to listOfNotNull(
            track.topologyStartSwitch?.let { topologySwitch ->
                if (topologySwitch.switchId == switch.id)
                    alignment.start?.let { p -> TopologyEndLink(topologySwitch, p) }
                else null
            }, track.topologyEndSwitch?.let { topologySwitch ->
                if (topologySwitch.switchId == switch.id)
                    alignment.end?.let { p -> TopologyEndLink(topologySwitch, p) }
                else null
            }
        )
    }.filter { (_, ends) -> ends.isNotEmpty() }
