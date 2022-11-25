package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.linking.PublishValidationErrorType.ERROR
import fi.fta.geoviite.infra.linking.PublishValidationErrorType.WARNING
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
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
        validate(trackNumber.draft != null) { "$VALIDATION_TRACK_NUMBER.not-draft" },
        validate(trackNumber.state.isPublishable()) { "$VALIDATION_TRACK_NUMBER.state.${trackNumber.state}" }
    )

fun validateTrackNumberReferences(
    trackNumber: TrackLayoutTrackNumber,
    kmPosts: List<TrackLayoutKmPost>,
    locationTracks: List<LocationTrack>,
    publishKmPostIds: List<IntId<TrackLayoutKmPost>>,
    publishedTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> = listOfNotNull(
    locationTracks.filter(LocationTrack::exists).let { existingTracks ->
        validateWithParams(trackNumber.exists || existingTracks.isEmpty()) {
            val existingNames = existingTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted" to listOf(existingNames)
        }
    },
    locationTracks.filterNot { track -> isPublished(track, publishedTrackIds) }.let { unpublishedTracks ->
        validateWithParams(unpublishedTracks.isEmpty()) {
            val unpublishedNames = unpublishedTracks.joinToString(", ") { track -> track.name }
            "$VALIDATION_TRACK_NUMBER.location-track.not-published" to listOf(unpublishedNames)
        }
    },
    kmPosts.filter(TrackLayoutKmPost::exists).let { existingKmPosts ->
        validateWithParams(trackNumber.exists || existingKmPosts.isEmpty()) {
            val existingNames = existingKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.reference-deleted" to listOf(existingNames)
        }
    },
    kmPosts.filterNot { kmPost -> isPublished(kmPost, publishKmPostIds) }.let { unpublishedKmPosts ->
        validateWithParams(unpublishedKmPosts.isEmpty()) {
            val unpublishedNames = unpublishedKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
            "$VALIDATION_TRACK_NUMBER.km-post.not-published" to listOf(unpublishedNames)
        }
    },
)

fun validateDraftKmPostFields(kmPost: TrackLayoutKmPost): List<PublishValidationError> =
    listOfNotNull(
        validate(kmPost.draft != null) { "$VALIDATION_KM_POST.not-draft" },
        validate(kmPost.state.isPublishable()) { "$VALIDATION_KM_POST.state.${kmPost.state}" },
        validate(kmPost.location != null) { "$VALIDATION_KM_POST.no-location" },
    )

fun validateKmPostReferences(
    kmPost: TrackLayoutKmPost,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
): List<PublishValidationError> =
    listOfNotNull(
        validate(trackNumber != null) { "$VALIDATION_KM_POST.track-number.null" },
        validateWithParams(!kmPost.exists || trackNumber == null || trackNumber.state.isLinkable()) {
            "$VALIDATION_KM_POST.track-number.state.${trackNumber?.state}" to listOfNotNull(trackNumber?.number?.value)
        },
        validateWithParams(trackNumber == null || kmPost.trackNumberId == trackNumber.id) {
            "$VALIDATION_KM_POST.track-number.not-official" to listOfNotNull(trackNumber?.number?.value)
        },
        validateWithParams(trackNumber == null || isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_KM_POST.track-number.not-published" to listOfNotNull(trackNumber?.number?.value)
        },
    )

fun validateDraftSwitchFields(switch: TrackLayoutSwitch): List<PublishValidationError> =
    listOfNotNull(
        validate(switch.draft != null) { "$VALIDATION_SWITCH.not-draft" },
        validate(switch.stateCategory.isPublishable()) { "$VALIDATION_SWITCH.state-category.${switch.stateCategory}" },
    )

fun validateSwitchSegmentReferences(
    switch: TrackLayoutSwitch,
    locationTracks: List<LocationTrack>,
    publishLocationTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> = listOfNotNull(
    locationTracks
        .filterNot { track -> isPublished(track, publishLocationTrackIds) }
        .let { unpublishedTracks ->
            validateWithParams(unpublishedTracks.isEmpty()) {
                val unpublishedNames = unpublishedTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_SWITCH.location-track.not-published" to listOf(unpublishedNames)
            }
        },
    locationTracks
        .filter(LocationTrack::exists)
        .let { existingTracks ->
            validateWithParams(switch.exists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_SWITCH.location-track.reference-deleted" to listOf(existingNames)
            }
        },
)

fun validateSwitchSegmentStructure(
    switch: TrackLayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): List<PublishValidationError> {
    val segmentGroups = locationTracks
        .filter { (track, _) -> track.exists } // Only consider the non-deleted tracks for switch alignments
        .map { (track, alignment) -> track to alignment.segments.filter { segment -> segment.switchId == switch.id } }
    val structureJoints = collectJoints(structure)
    val segmentJoints = segmentGroups.map { (track, group) -> collectJoints(track, group) }

    return if (switch.exists) listOfNotNull(
        segmentGroups.filterNot { (_, group) -> areSegmentsContinuous(group) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.not-continuous" to listOf(errorTrackNames)
                }
            },
        segmentGroups.filterNot { (_, group) -> segmentAndJointLocationsAgree(switch, group) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty(), WARNING) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to listOf(errorTrackNames)
                }
            },
        segmentJoints.filterNot { (_, group) -> alignmentJointGroupFound(group, structureJoints) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.wrong-joint-sequence" to listOf(errorTrackNames)
                }
            },
        structureJoints.filterNot { group ->
            structureJointGroupFound(
                group,
                segmentJoints.map { (_, group) -> group })
        }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty(), WARNING) {
                    val errorJointLists = errorGroups.joinToString(", ") { group -> jointSequence(group) }
                    "$VALIDATION_SWITCH.location-track.unlinked" to listOf(errorJointLists)
                }
            },
    ) else listOf()
}

fun validateDuplicateOfState(
    locationTrack: LocationTrack,
    duplicateOfLocationTrack: LocationTrack?,
    publishLocationTrackIds: List<IntId<LocationTrack>>,
): List<PublishValidationError> =
    if (duplicateOfLocationTrack == null) listOf()
    else listOfNotNull(
        validateWithParams(locationTrack.duplicateOf == duplicateOfLocationTrack.id) {
            "$VALIDATION_REFERENCE_LINE.duplicate-of.not-official" to listOf(duplicateOfLocationTrack.name.value)
        },
        validateWithParams(isPublished(duplicateOfLocationTrack, publishLocationTrackIds)) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.not-published" to listOf(duplicateOfLocationTrack.name.value)
        },
        validateWithParams(locationTrack.state.isRemoved() || duplicateOfLocationTrack.state.isLinkable()) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.state.${duplicateOfLocationTrack.state}" to listOf(
                duplicateOfLocationTrack.name.value
            )
        },
        validateWithParams(duplicateOfLocationTrack.duplicateOf == null) {
            "$VALIDATION_LOCATION_TRACK.duplicate-of.duplicate" to listOf(duplicateOfLocationTrack.name.value)
        }
   )

fun validateDraftReferenceLineFields(referenceLine: ReferenceLine): List<PublishValidationError> =
    listOfNotNull(
        validate(referenceLine.draft != null) { "$VALIDATION_REFERENCE_LINE.not-draft" },
    )

fun validateDraftLocationTrackFields(locationTrack: LocationTrack): List<PublishValidationError> =
    listOfNotNull(
        validate(locationTrack.draft != null) { "$VALIDATION_LOCATION_TRACK.not-draft" },
        validate(locationTrack.state.isPublishable()) { "$VALIDATION_LOCATION_TRACK.state.${locationTrack.state}" },
    )

fun validateReferenceLineReference(
    referenceLine: ReferenceLine,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
) =
    if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_REFERENCE_LINE.track-number.null")
    )
    else listOfNotNull(
        validateWithParams(referenceLine.trackNumberId == trackNumber.id) {
            "$VALIDATION_REFERENCE_LINE.track-number.not-official" to listOf(trackNumber.number.value)
        },
        validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_REFERENCE_LINE.track-number.not-published" to listOf(trackNumber.number.value)
        },
    )

fun validateLocationTrackReference(
    locationTrack: LocationTrack,
    trackNumber: TrackLayoutTrackNumber?,
    publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
) =
    if (trackNumber == null) listOf(
        PublishValidationError(ERROR, "$VALIDATION_LOCATION_TRACK.track-number.null")
    )
    else listOfNotNull(
        validateWithParams(locationTrack.trackNumberId == trackNumber.id) {
            "$VALIDATION_LOCATION_TRACK.track-number.not-official" to listOf(trackNumber.number.value)
        },
        validateWithParams(isPublished(trackNumber, publishTrackNumberIds)) {
            "$VALIDATION_LOCATION_TRACK.track-number.not-published" to listOf(trackNumber.number.value)
        },
        validateWithParams(locationTrack.state.isRemoved() || trackNumber.state.isLinkable()) {
            "$VALIDATION_LOCATION_TRACK.track-number.state.${trackNumber.state}" to listOf(trackNumber.number.value)
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
                "$VALIDATION_LOCATION_TRACK.switch.not-official" to listOf(switch.name.value)
            },
            validateWithParams(isPublished(switch, publishSwitchIds)) {
                "$VALIDATION_LOCATION_TRACK.switch.not-published" to listOf(switch.name.value)
            },
            validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to listOf(switch.name.value)
            },
        )
        val geometryErrors: List<PublishValidationError> = if (locationTrack.exists && switch.exists) {
            val structureJoints = collectJoints(segmentSwitch.switchStructure)
            val segmentJoints = collectJoints(segments)
            listOfNotNull(
                validateWithParams(areSegmentsContinuous(segments)) {
                    "$VALIDATION_LOCATION_TRACK.switch.alignment-not-continuous" to listOf(switch.name.value)
                },
                validateWithParams(segmentAndJointLocationsAgree(switch, segments), WARNING) {
                    "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch" to listOf(switch.name.value)
                },
                validateWithParams(alignmentJointGroupFound(segmentJoints, structureJoints)) {
                    "$VALIDATION_LOCATION_TRACK.switch.wrong-joint-sequence" to listOf(
                        switch.name.value,
                        segmentSwitch.switchStructure.baseType.name,
                        jointSequence(segmentJoints),
                    )
                },
            )
        } else listOf()

        stateErrors + geometryErrors
    }
}

fun jointSequence(joints: List<JointNumber>) =
    joints.joinToString("-") { jointNumber -> "${jointNumber.intValue}" }

fun validateGeocodingContext(
    context: GeocodingContext?,
    validationTargetLocalizationPrefix: String
): List<PublishValidationError> =
    if (context == null) listOf(
        PublishValidationError(
            ERROR,
            "$validationTargetLocalizationPrefix.no-context",
            listOf()
        )
    )
    else listOfNotNull(

        context.kmPostsSmallerThanTrackNumberStart
            //.filter { post -> post.location != null }
            .let { rejected ->
                println("km-post-smaller-than-track-number-start $rejected")
                validateWithParams(rejected.isEmpty()) {
                    "$VALIDATION_GEOCODING.km-post-smaller-than-track-number-start" to listOf(
                        context.trackNumber.number.value,
                        rejected.joinToString(",") { post -> post.kmNumber.toString() },
                    )
                }
            },
        context.referencePoints
            .filter { point-> point.intersectType != WITHIN }
            .let { rejected ->
                println("km-posts-outside-line $rejected")
                validateWithParams(rejected.isEmpty()) {
                    "$VALIDATION_GEOCODING.km-posts-outside-line" to listOf(
                        context.trackNumber.number.value,
                        rejected.joinToString(",") { post -> post.kmNumber.toString() },
                    )
                }
            },
        context.rejectedKmPosts
            .filter { post -> post.location != null }
            .let { rejected ->
                validateWithParams(rejected.isEmpty()) {
                    "$VALIDATION_GEOCODING.km-posts-rejected" to listOf(
                        context.trackNumber.number.value,
                        rejected.joinToString(",") { post -> post.kmNumber.toString() },
                    )
                }
            },
        context.referencePoints
            .filterIndexed { index, point ->
                val previous = context.referencePoints.getOrNull(index - 1)
                val next = context.referencePoints.getOrNull(index + 1)
                !isOrderOk(previous, point) || !isOrderOk(point, next)
            }.let { invalidPoints ->
                validateWithParams(invalidPoints.isEmpty()) {
                    "$VALIDATION_GEOCODING.km-posts-invalid" to listOf(
                        context.trackNumber.number.value,
                        invalidPoints.joinToString(",") { point -> point.kmNumber.toString() },
                    )
                }
            },
        context.referencePoints
            .filter { point -> point.kmPostOffset > MAX_KM_POST_OFFSET }
            .let { farAwayPoints ->
                validateWithParams(farAwayPoints.isEmpty(), WARNING) {
                    "$VALIDATION_GEOCODING.km-posts-far-from-line" to listOf(
                        context.trackNumber.number.value,
                        farAwayPoints.joinToString(",") { point -> point.kmNumber.toString() },
                    )
                }
            },
    )

fun isOrderOk(previous: GeocodingReferencePoint?, next: GeocodingReferencePoint?) =
    if (previous == null || next == null) true
    else previous.distance < next.distance

fun validateAddressPoints(
    trackNumber: TrackLayoutTrackNumber,
    locationTrack: LocationTrack,
    validationTargetLocalizationPrefix: String,
    geocode: () -> AlignmentAddresses?,
): List<PublishValidationError> =
    try {
        val addresses = geocode()
        if (addresses == null) {
            listOf(
                PublishValidationError(
                    ERROR,
                    "$validationTargetLocalizationPrefix.no-context",
                    listOf(trackNumber.number.value)
                )
            )
        } else {
            validateAddressPoints(trackNumber, locationTrack, addresses)
        }
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
            "$VALIDATION_GEOCODING.start-outside-reference-line" to listOf(
                trackNumber.number.value,
                locationTrack.name.value,
            )
        },
        validateWithParams(addresses.endIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.end-outside-reference-line" to listOf(
                trackNumber.number.value,
                locationTrack.name.value,
            )
        },
        validateWithParams(discontinuousDirectionRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.sharp-angle" to listOf(
                trackNumber.number.value,
                locationTrack.name.value,
                discontinuousDirectionRanges
            )
        },
        validateWithParams(stretchedMeterRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.stretched-meters" to listOf(
                trackNumber.number.value,
                locationTrack.name.value,
                stretchedMeterRanges
            )
        },
        validateWithParams(discontinuousAddressRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.not-continuous" to listOf(
                trackNumber.number.value,
                locationTrack.name.value,
                discontinuousAddressRanges
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

fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segmentGroup: List<LayoutSegment>): Boolean =
    segmentGroup.all { segment -> segmentAndJointLocationsAgree(switch, segment) }

fun segmentAndJointLocationsAgree(switch: TrackLayoutSwitch, segment: LayoutSegment): Boolean {
    val jointLocations = listOfNotNull(
        segment.startJointNumber?.let { jn -> segment.points.first() to jn },
        segment.endJointNumber?.let { jn -> segment.points.last() to jn },
    )
    return jointLocations.all { (location, jointNumber) ->
        val joint = switch.getJoint(jointNumber)
        joint != null && joint.location.isSame(location, JOINT_LOCATION_DELTA)
    }
}

fun alignmentJointGroupFound(alignmentJoints: List<JointNumber>, structureJointGroups: List<List<JointNumber>>) =
    structureJointGroups.any { structureJoints -> jointGroupMatches(alignmentJoints, structureJoints) }

fun structureJointGroupFound(structureJoints: List<JointNumber>, alignmentJointGroups: List<List<JointNumber>>) =
    alignmentJointGroups.any { alignmentJoints -> jointGroupMatches(alignmentJoints, structureJoints) }

fun jointGroupMatches(alignmentJoints: List<JointNumber>, structureJoints: List<JointNumber>): Boolean =
    if (!structureJoints.containsAll(alignmentJoints)) false
    else if (alignmentJoints.size == 1) true
    else {
        val alignmentStartAndEnd = listOf(alignmentJoints.first(), alignmentJoints.last())
        val structureStartAndEnd = listOf(structureJoints.first(), structureJoints.last())
        alignmentStartAndEnd == structureStartAndEnd || alignmentStartAndEnd == structureStartAndEnd.reversed()
    }

fun collectJoints(structure: SwitchStructure) =
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

fun collectJoints(track: LocationTrack, segments: List<LayoutSegment>): Pair<LocationTrack, List<JointNumber>> =
    track to collectJoints(segments)

fun collectJoints(segments: List<LayoutSegment>): List<JointNumber> {
    val allJoints = segments.flatMap { segment -> listOfNotNull(segment.startJointNumber, segment.endJointNumber) }
    return allJoints.filterIndexed { index, jointNumber -> index == 0 || allJoints[index - 1] != jointNumber }
}

fun areSegmentsContinuous(segments: List<LayoutSegment>): Boolean =
    segments.mapIndexed { index, segment ->
        index == 0 || segments[index - 1].points.last().isSame(segment.points.first(), LAYOUT_COORDINATE_DELTA)
    }.all { it }

fun areDirectionsContinuous(points: List<LayoutPoint>): Boolean {
    var prevDirection: Double? = null
    return points.mapIndexed { index, point ->
        val previous = if (index > 0) points[index - 1] else null
        val direction = previous?.let { prev -> directionBetweenPoints(prev, point) }
        val angleOk = isAngleDiffOk(prevDirection, direction)
        prevDirection = direction
        angleOk
    }.all { it }
}

fun rangesOfConsecutiveIndicesOf(
    value: Boolean,
    ts: List<Boolean>,
    offsetRangeEndsBy: Int = 0
): List<ClosedRange<Int>> =
    sequence { yield(!value); yieldAll(ts.asSequence()); yield(!value) }
        .zipWithNext()
        .mapIndexedNotNull { i, (a, b) -> if (a != b) i else null }
        .chunked(2)
        .map { c -> c[0]..c[1] + offsetRangeEndsBy }
        .toList()

fun discontinuousDirectionRangeIndices(points: List<LayoutPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::directionBetweenPoints).zipWithNext(::isAngleDiffOk), 2)

fun stretchedMeterRangeIndices(points: List<LayoutPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::lineLength).map { it <= MAX_LAYOUT_METER_LENGTH }, 1)

fun discontinuousAddressRangeIndices(addresses: List<TrackMeter>): List<ClosedRange<Int>> =
    rangesOfConsecutiveIndicesOf(false, addresses.zipWithNext(::isAddressDiffOk), 1)

fun isAngleDiffOk(direction1: Double?, direction2: Double?) =
    direction1 == null || direction2 == null || angleDiffRads(direction1, direction2) <= MAX_LAYOUT_POINT_ANGLE_CHANGE

fun isAddressDiffOk(address1: TrackMeter?, address2: TrackMeter?): Boolean =
    if (address1 == null || address2 == null) true
    else if (address1 > address2) false
    else if (address1.kmNumber != address2.kmNumber) true
    else (address2.meters - address1.meters).toDouble() in 0.0..MAX_LAYOUT_METER_LENGTH

fun validate(valid: Boolean, type: PublishValidationErrorType = ERROR, error: () -> String) =
    validateWithParams(valid, type) { error() to listOf() }

fun validateWithParams(
    valid: Boolean,
    type: PublishValidationErrorType = ERROR,
    error: () -> Pair<String, List<String>>,
): PublishValidationError? =
    if (!valid) error().let { (key, params) -> PublishValidationError(type, key, params) }
    else null

fun <T : Draftable<T>> isPublished(item: T, publishItemIds: List<IntId<T>>) =
    item.draft == null || publishItemIds.contains(item.id)
