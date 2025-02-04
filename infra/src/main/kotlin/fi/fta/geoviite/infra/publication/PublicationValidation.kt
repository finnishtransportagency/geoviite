package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingReferencePoint
import fi.fta.geoviite.infra.geocoding.KmPostRejectedReason
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.FATAL
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.WARNING
import fi.fta.geoviite.infra.switchLibrary.LinkableSwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchConnectivity
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.switchConnectivity
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
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

fun validateTrackNumberReferences(
    trackNumber: LayoutTrackNumber,
    referenceLine: ReferenceLine?,
    kmPosts: List<LayoutKmPost>,
    locationTracks: List<LocationTrack>,
): List<LayoutValidationIssue> =
    listOfNotNull(
        validate(referenceLine != null) { "$VALIDATION_TRACK_NUMBER.reference-line.not-published" },
        locationTracks.filter(LocationTrack::exists).let { existingTracks ->
            validateWithParams(trackNumber.exists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted" to
                    localizationParams("locationTracks" to existingNames)
            }
        },
        kmPosts.filter(LayoutKmPost::exists).let { existingKmPosts ->
            validateWithParams(trackNumber.exists || existingKmPosts.isEmpty()) {
                val existingNames = existingKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
                "$VALIDATION_TRACK_NUMBER.km-post.reference-deleted" to localizationParams("kmPosts" to existingNames)
            }
        },
    )

fun validateTrackNumberNumberDuplication(
    trackNumber: LayoutTrackNumber,
    duplicates: List<LayoutTrackNumber>,
    validationTargetType: ValidationTargetType,
): List<LayoutValidationIssue> {
    return if (trackNumber.exists) {
        validateNameDuplication(
            VALIDATION_TRACK_NUMBER,
            validationTargetType,
            duplicates.any { d -> d.id != trackNumber.id && d.isOfficial },
            duplicates.any { d -> d.id != trackNumber.id && d.isDraft },
            "trackNumber" to trackNumber.number,
        )
    } else {
        emptyList()
    }
}

fun validateKmPostReferences(
    kmPost: LayoutKmPost,
    trackNumber: LayoutTrackNumber?,
    referenceLine: ReferenceLine?,
    trackNumberNumber: TrackNumber?,
): List<LayoutValidationIssue> =
    listOfNotNull(
        validateWithParams(trackNumber != null) {
            "$VALIDATION_KM_POST.track-number.not-published" to localizationParams("trackNumber" to trackNumberNumber)
        },
        // if the reference line doesn't exist, geocoding context validation doesn't happen, and
        // that would cause duplicate km post validation to also be skipped, which then would let us
        // get into a state where we hit the duplicate database constraint on km posts instead,
        // hence this check also has to be fatal
        validateWithParams(referenceLine != null, FATAL) {
            "$VALIDATION_KM_POST.reference-line.not-published" to localizationParams("trackNumber" to trackNumberNumber)
        },
        validateWithParams(!kmPost.exists || trackNumber == null || trackNumber.state.isLinkable()) {
            "$VALIDATION_KM_POST.track-number.state.${trackNumber?.state}" to
                localizationParams("trackNumber" to trackNumber?.number)
        },
        validateWithParams(trackNumber == null || kmPost.trackNumberId == trackNumber.id) {
            "$VALIDATION_KM_POST.track-number.not-official" to localizationParams("trackNumber" to trackNumber?.number)
        },
    )

fun validateSwitchLocationTrackLinkReferences(
    switch: LayoutSwitch,
    locationTracks: List<LocationTrack>,
): List<LayoutValidationIssue> {
    return listOfNotNull(
        locationTracks.filter(LocationTrack::exists).let { existingTracks ->
            validateWithParams(switch.exists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_SWITCH.location-track.reference-deleted" to
                    localizationParams("locationTracks" to existingNames)
            }
        }
    )
}

fun validateSwitchLocation(switch: LayoutSwitch): List<LayoutValidationIssue> =
    listOfNotNull(validate(switch.joints.isNotEmpty()) { "$VALIDATION_SWITCH.no-location" })

private fun validateNameDuplication(
    messagePrefix: String,
    validationTargetType: ValidationTargetType,
    officialDuplicateExists: Boolean,
    draftDuplicateExists: Boolean,
    vararg params: Pair<String, Any?>,
): List<LayoutValidationIssue> {
    val isMergingToMain = validationTargetType == ValidationTargetType.MERGING_TO_MAIN
    val key =
        if (isMergingToMain && draftDuplicateExists) "duplicate-name-draft-in-main"
        else if (draftDuplicateExists) "duplicate-name-draft"
        else if (officialDuplicateExists) "duplicate-name-official" else null
    return if (key == null) emptyList()
    else {
        listOf(validationFatal("$messagePrefix.$key", *params))
    }
}

fun validateSwitchNameDuplication(
    switch: LayoutSwitch,
    duplicates: List<LayoutSwitch>,
    validationTargetType: ValidationTargetType,
): List<LayoutValidationIssue> {
    return if (switch.exists) {
        validateNameDuplication(
            VALIDATION_SWITCH,
            validationTargetType,
            duplicates.any { d -> d.id != switch.id && d.isOfficial },
            duplicates.any { d -> d.id != switch.id && d.isDraft },
            "switch" to switch.name,
        )
    } else {
        emptyList()
    }
}

fun validateSwitchOidDuplication(switch: LayoutSwitch, oidDuplicate: LayoutSwitch?): List<LayoutValidationIssue> {
    return if (oidDuplicate != null && oidDuplicate.id != switch.id) {
        listOf(validationError("$VALIDATION_SWITCH.duplicate-oid"))
    } else listOf()
}

fun validateLocationTrackNameDuplication(
    track: LocationTrack,
    trackNumber: TrackNumber?,
    duplicates: List<LocationTrack>,
    validationTargetType: ValidationTargetType,
): List<LayoutValidationIssue> {
    return if (track.exists && duplicates.any { d -> d.id != track.id }) {
        validateNameDuplication(
            VALIDATION_LOCATION_TRACK,
            validationTargetType,
            // Location track names must be unique within the same track number, but there can be
            // location tracks with the same name on other track numbers
            duplicates.any { d -> d.id != track.id && d.isOfficial && d.trackNumberId == track.trackNumberId },
            duplicates.any { d -> d.id != track.id && d.isDraft && d.trackNumberId == track.trackNumberId },
            "locationTrack" to track.name,
            "trackNumber" to trackNumber,
        )
    } else emptyList()
}

fun validateSwitchLocationTrackLinkStructure(
    switch: LayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<LayoutValidationIssue> {
    // TODO: GVT-2933 Validation in topology model
    val existingTracks = locationTracks.filter { (track, _) -> track.exists }
    val segmentGroups =
        existingTracks // Only consider the non-deleted tracks for switch alignments
            .map { (track, alignment) ->
                track to alignment.segments.filter { segment -> segment.switchId == switch.id }
            }
            .filter { (_, segments) -> segments.isNotEmpty() }

    val structureJoints = collectJoints(structure)
    val segmentJoints = segmentGroups.map { (track, group) -> collectJoints(track, group) }

    val topologyLinks: List<Pair<LocationTrack, List<TopologyEndLink>>> =
        emptyList() // collectTopologyEndLinks(existingTracks, switch)

    return if (switch.exists)
        listOfNotNull(
            segmentGroups
                .filterNot { (_, group) -> areSegmentsContinuous(group) }
                .let { errorGroups ->
                    validateWithParams(errorGroups.isEmpty()) {
                        val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                        "$VALIDATION_SWITCH.location-track.not-continuous" to
                            localizationParams("locationTracks" to errorTrackNames)
                    }
                },
            segmentGroups
                .filterNot { (_, group) -> segmentAndJointLocationsAgree(switch, group) }
                .let { errorGroups ->
                    validateWithParams(errorGroups.isEmpty(), WARNING) {
                        val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                        "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to
                            localizationParams("locationTracks" to errorTrackNames)
                    }
                },
            topologyLinks
                .filterNot { (_, group) -> topologyLinkAndJointLocationsAgree(switch, group) }
                .let { errorGroups ->
                    validateWithParams(errorGroups.isEmpty(), WARNING) {
                        val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                        "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to
                            localizationParams("locationTracks" to errorTrackNames)
                    }
                },
            segmentJoints
                .filterNot { (_, group) -> alignmentJointGroupFound(group, structureJoints) }
                .let { errorGroups ->
                    validateWithParams(errorGroups.isEmpty()) {
                        val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                        "$VALIDATION_SWITCH.location-track.wrong-joint-sequence" to
                            localizationParams("locationTracks" to errorTrackNames)
                    }
                },
        ) + validateSwitchTopologicalConnectivity(switch, structure, locationTracks, null)
    else listOf()
}

fun validateLocationTrackSwitchConnectivity(
    layoutTrack: LocationTrack,
    alignment: LayoutAlignment,
): List<LayoutValidationIssue> {
    val startSegment = alignment.segments.firstOrNull()
    val endSegment = alignment.segments.lastOrNull()
    val topologyStartSwitch = layoutTrack.topologyStartSwitch?.switchId
    val topologyEndSwitch = layoutTrack.topologyEndSwitch?.switchId

    val hasStartSwitch =
        (startSegment?.switchId != null && startSegment.startJointNumber != null) || topologyStartSwitch != null
    val hasEndSwitch = (endSegment?.switchId != null && endSegment.endJointNumber != null) || topologyEndSwitch != null

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
                },
            )
        }
    }
}

fun validateSwitchTopologicalConnectivity(
    switch: LayoutSwitch,
    structure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    validatingTrack: LocationTrack?,
): List<LayoutValidationIssue> {
    val existingTracks = locationTracks.filter { it.first.exists }
    // TODO: GVT-2933 Validation in topology model
    return emptyList()
    //    return listOf(
    //            listOfNotNull(validateFrontJointTopology(switch, structure, existingTracks, validatingTrack)),
    //            validateSwitchAlignmentTopology(switch.id, structure, existingTracks, switch.name, validatingTrack),
    //        )
    //        .flatten()
}

fun switchOrTrackLinkageKey(validatingTrack: LocationTrack?) =
    if (validatingTrack != null) "$VALIDATION_LOCATION_TRACK.switch-linkage" else "$VALIDATION_SWITCH.track-linkage"

private fun getTracksThroughJoints(
    structure: SwitchStructure,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switch: LayoutSwitch,
): Map<JointNumber, List<LocationTrack>> =
    structure.joints
        .map { it.number }
        .associateWith { jointNumber -> getTracksThroughJoint(tracks, switch, jointNumber) }

private fun getTracksThroughJoint(
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switch: LayoutSwitch,
    jointNumber: JointNumber,
) =
    tracks
        .filter { (_, alignment) -> trackPassesThroughJoint(alignment, switch, jointNumber) }
        .map { (locationTrack, _) -> locationTrack }

private fun trackPassesThroughJoint(
    alignment: LayoutAlignment,
    switch: LayoutSwitch,
    jointNumber: JointNumber,
): Boolean {
    val startJointLinkIndex =
        alignment.segments.indexOfFirst { segment ->
            segment.switchId == switch.id && segment.startJointNumber == jointNumber
        }
    val endJointLinkIndex =
        alignment.segments.indexOfFirst { segment ->
            segment.switchId == switch.id && segment.endJointNumber == jointNumber
        }
    return startJointLinkIndex > 0 || (endJointLinkIndex != -1 && endJointLinkIndex < alignment.segments.lastIndex)
}

private fun validateFrontJointTopology(
    switch: LayoutSwitch,
    switchStructure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    validatingTrack: LocationTrack?,
): LayoutValidationIssue? {
    val connectivity = switchConnectivity(switchStructure)
    fun tracksHaveOkFrontJointLink(tracks: List<Pair<LocationTrack, LayoutAlignment>>) =
        tracks.any { (locationTrack, _) ->
            val topoStart =
                locationTrack.topologyStartSwitch?.switchId == switch.id &&
                    locationTrack.topologyStartSwitch.jointNumber == connectivity.frontJoint
            val topoEnd =
                locationTrack.topologyEndSwitch?.switchId == switch.id &&
                    locationTrack.topologyEndSwitch.jointNumber == connectivity.frontJoint
            val tracksThroughFrontJoint =
                if (connectivity.frontJoint == null) {
                    listOf()
                } else getTracksThroughJoints(switchStructure, tracks, switch)[connectivity.frontJoint]
            topoStart || topoEnd || !tracksThroughFrontJoint.isNullOrEmpty()
        }

    val okFrontJointLinkInDuplicates = tracksHaveOkFrontJointLink(locationTracks)
    val okFrontJointLinkInNonDuplicates =
        tracksHaveOkFrontJointLink(locationTracks.filter { it.first.duplicateOf == null })

    return validateWithParams(connectivity.frontJoint == null || okFrontJointLinkInNonDuplicates, WARNING) {
        val key =
            "${switchOrTrackLinkageKey(validatingTrack)}.${
            if (okFrontJointLinkInDuplicates) "front-joint-only-duplicate-connected"
            else "front-joint-not-connected"
        }"

        key to localizationParams("switch" to switch.name.toString())
    }
}

private fun findValidatingTrackSwitchAlignment(
    switchId: DomainId<LayoutSwitch>,
    validatingTrack: LocationTrack?,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    connectivity: SwitchConnectivity,
): SwitchStructureAlignment? =
    locationTracks
        .find { (track) -> track.id == validatingTrack?.id }
        ?.let { trackAndAlignment ->
            connectivity.alignments
                // with really bad linking, this could be ambiguous, and the choice of finding the
                // first one arbitrary; but that shouldn't really happen
                .find { switchAlignment ->
                    alignmentLinkingQuality(switchId, switchAlignment, listOf(trackAndAlignment)).hasSomethingLinked()
                }
                ?.originalAlignment
        }

private fun summarizeSwitchAlignmentLocationTrackLinks(
    links: List<Pair<LocationTrack, SwitchStructureAlignment>>
): String =
    links
        .groupBy { it.second }
        .entries
        .joinToString(", ") { (originalAlignment, linksOnAlignment) ->
            val alignmentString = originalAlignment.jointNumbers.joinToString("-") { j -> j.intValue.toString() }
            val tracksString = linksOnAlignment.map { it.first.name.toString() }.sorted().distinct().joinToString(", ")
            "$alignmentString ($tracksString)"
        }

fun validateSwitchAlignmentTopology(
    switchId: DomainId<LayoutSwitch>,
    switchStructure: SwitchStructure,
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switchName: SwitchName,
    validatingTrack: LocationTrack?,
): List<LayoutValidationIssue> {
    val connectivity = switchConnectivity(switchStructure)
    val linkingQuality =
        connectivity.alignments.map { alignment -> alignmentLinkingQuality(switchId, alignment, locationTracks) }
    val validatingTrackSwitchAlignment =
        findValidatingTrackSwitchAlignment(switchId, validatingTrack, locationTracks, connectivity)
    val switchHasTopologicalConnections =
        locationTracks.any { (track) ->
            track.topologyStartSwitch?.switchId == switchId || track.topologyEndSwitch?.switchId == switchId
        }

    val qualitiesToValidate =
        linkingQuality.filter { quality ->
            validatingTrackSwitchAlignment == null || quality.originalAlignment == validatingTrackSwitchAlignment
        }

    val notLinked = qualitiesToValidate.filter { !it.hasSomethingLinked() }
    val linkedOnlyToDuplicates =
        qualitiesToValidate
            .filter { it.nonDuplicateTracks.isEmpty() }
            .flatMap { alignment -> alignment.duplicateTracks.map { dup -> dup to alignment.originalAlignment } }
    val linkedPartially =
        qualitiesToValidate.flatMap { alignment ->
            alignment.partiallyLinked.map { part -> part to alignment.originalAlignment }
        }
    val linkedMultiply =
        qualitiesToValidate
            .filter { it.nonDuplicateTracks.size > 1 }
            .flatMap { alignment -> alignment.nonDuplicateTracks.map { track -> track to alignment.originalAlignment } }

    return listOfNotNull(
        validateWithParams(linkingQuality.any { it.hasSomethingLinked() } || switchHasTopologicalConnections, ERROR) {
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-no-alignments-connected" to
                localizationParams("switch" to switchName.toString())
        },
        validateWithParams(
            (linkingQuality.none { it.hasSomethingLinked() } && !switchHasTopologicalConnections) ||
                notLinked.isEmpty(),
            WARNING,
        ) {
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-alignment-not-connected" to
                localizationParams(
                    "switch" to switchName.toString(),
                    "alignments" to
                        notLinked.joinToString(", ") { q ->
                            q.switchAlignment.joints.joinToString("-") { it.intValue.toString() }
                        },
                )
        },
        validateWithParams(linkedOnlyToDuplicates.isEmpty(), WARNING) {
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-alignment-only-connected-to-duplicate" to
                localizationParams(
                    "switch" to switchName.toString(),
                    "locationTracks" to summarizeSwitchAlignmentLocationTrackLinks(linkedOnlyToDuplicates),
                )
        },
        validateWithParams(linkedPartially.isEmpty(), WARNING) {
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-alignment-partially-connected" to
                localizationParams(
                    "switch" to switchName.toString(),
                    "locationTracks" to summarizeSwitchAlignmentLocationTrackLinks(linkedPartially),
                )
        },
        validateWithParams(linkedMultiply.isEmpty(), WARNING) {
            "${switchOrTrackLinkageKey(validatingTrack)}.switch-alignment-multiply-connected" to
                localizationParams(
                    "switch" to switchName.toString(),
                    "locationTracks" to summarizeSwitchAlignmentLocationTrackLinks(linkedMultiply),
                )
        },
    )
}

private data class SwitchAlignmentLinkingQuality(
    val switchAlignment: LinkableSwitchAlignment,
    val nonDuplicateTracks: List<LocationTrack>,
    val duplicateTracks: List<LocationTrack>,
    val fullyLinked: List<LocationTrack>,
    val partiallyLinked: List<LocationTrack>,
) {

    val originalAlignment = switchAlignment.originalAlignment

    fun hasSomethingLinked() = nonDuplicateTracks.isNotEmpty() || duplicateTracks.isNotEmpty()
}

private fun alignmentLinkingQuality(
    switchId: DomainId<LayoutSwitch>,
    switchAlignment: LinkableSwitchAlignment,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
): SwitchAlignmentLinkingQuality {
    val nonDuplicateTracks = mutableListOf<LocationTrack>()
    val duplicateTracks = mutableListOf<LocationTrack>()
    val fullyLinked = mutableListOf<LocationTrack>()
    val partiallyLinked = mutableListOf<LocationTrack>()

    tracks.forEach { (track, trackAlignment) ->
        val trackAlignmentSwitchJointLinks =
            trackAlignment.segments
                .filter { it.switchId == switchId }
                .flatMap { listOf(it.startJointNumber, it.endJointNumber) }
                .filterNotNull()
                .toSet()
        val hasStart = trackAlignmentSwitchJointLinks.contains(switchAlignment.joints.first())
        val hasEnd = trackAlignmentSwitchJointLinks.contains(switchAlignment.joints.last())
        val trackAlignmentHasOtherLinks =
            trackAlignmentSwitchJointLinks.subtract(switchAlignment.joints.toSet()).isNotEmpty()

        val isPartiallyLinkedWithoutOtherLinks = (hasStart || hasEnd) && !trackAlignmentHasOtherLinks
        val isFullyLinkedToSplitAlignment = hasStart && hasEnd
        val isFullyLinkedToOriginalAlignment =
            trackAlignmentSwitchJointLinks.contains(switchAlignment.originalAlignment.jointNumbers.first()) &&
                trackAlignmentSwitchJointLinks.contains(switchAlignment.originalAlignment.jointNumbers.last())
        val isFullyLinked = isFullyLinkedToOriginalAlignment || isFullyLinkedToSplitAlignment

        if (isPartiallyLinkedWithoutOtherLinks || isFullyLinked) {
            if (isFullyLinked) {
                fullyLinked.add(track)
            } else {
                partiallyLinked.add(track)
            }

            if (track.duplicateOf == null) {
                nonDuplicateTracks.add(track)
            } else {
                duplicateTracks.add(track)
            }
        }
    }

    return SwitchAlignmentLinkingQuality(
        switchAlignment,
        nonDuplicateTracks,
        duplicateTracks,
        fullyLinked,
        partiallyLinked,
    )
}

fun validateDuplicateOfState(
    locationTrack: LocationTrack,
    duplicateOfLocationTrack: LocationTrack?,
    duplicateOfLocationTrackDraftName: AlignmentName?,
    duplicateOfLocationTrackIsCancelled: Boolean,
    duplicates: List<LocationTrack>,
): List<LayoutValidationIssue> {
    val duplicateNameParams =
        localizationParams("duplicateTrack" to (duplicateOfLocationTrack?.name ?: duplicateOfLocationTrackDraftName))
    val ownDuplicateOfErrors =
        if (duplicateOfLocationTrack == null) {
            listOfNotNull(
                // Non-null reference, but the duplicateOf track doesn't exist in validation context
                validateWithParams(locationTrack.duplicateOf == null) {
                    cancelledOrNotPublishedKey(
                        "$VALIDATION_LOCATION_TRACK.duplicate-of.",
                        duplicateOfLocationTrackIsCancelled,
                    ) to duplicateNameParams
                }
            )
        } else {
            listOfNotNull(
                validateWithParams(locationTrack.state.isRemoved() || duplicateOfLocationTrack.state.isLinkable()) {
                    "$VALIDATION_LOCATION_TRACK.duplicate-of.state.${duplicateOfLocationTrack.state}" to
                        duplicateNameParams
                },
                validateWithParams(duplicateOfLocationTrack.duplicateOf == null) {
                    "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-of-duplicated" to duplicateNameParams
                },
                validateWithParams(duplicates.isEmpty()) {
                    val suffix = if (duplicates.size > 1) "-multiple" else ""
                    "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-while-duplicated${suffix}" to
                        localizationParams(
                            mapOf(
                                "duplicateTrack" to duplicateOfLocationTrack.name,
                                "otherDuplicates" to
                                    duplicates.map { track -> track.name }.distinct().joinToString { it },
                            )
                        )
                },
            )
        }
    val otherDuplicateReferenceErrors =
        if (!locationTrack.exists) {
            val existingDuplicates = duplicates.filter { d -> d.exists }
            listOfNotNull(
                validateWithParams(existingDuplicates.isEmpty()) {
                    "$VALIDATION_LOCATION_TRACK.deleted-duplicated-by-existing" to
                        localizationParams("duplicates" to existingDuplicates.joinToString(",") { track -> track.name })
                }
            )
        } else {
            emptyList()
        }
    return ownDuplicateOfErrors + otherDuplicateReferenceErrors
}

fun validateReferenceLineReference(
    referenceLine: ReferenceLine,
    trackNumberNumber: TrackNumber?,
    trackNumber: LayoutTrackNumber?,
    trackNumberIsCancelled: Boolean,
): List<LayoutValidationIssue> {
    val numberParams = localizationParams("trackNumber" to trackNumberNumber)
    return listOfNotNull(
        validateWithParams(trackNumber != null) {
            cancelledOrNotPublishedKey("$VALIDATION_REFERENCE_LINE.track-number.", trackNumberIsCancelled) to
                numberParams
        },
        validateWithParams(trackNumber == null || referenceLine.trackNumberId == trackNumber.id) {
            "$VALIDATION_REFERENCE_LINE.track-number.not-official" to numberParams
        },
    )
}

fun validateLocationTrackReference(
    locationTrack: LocationTrack,
    trackNumber: LayoutTrackNumber?,
    trackNumberName: TrackNumber?,
    trackNumberIsCancelled: Boolean,
): List<LayoutValidationIssue> {
    return if (trackNumber == null) {
        listOf(
            validationError(
                cancelledOrNotPublishedKey("$VALIDATION_LOCATION_TRACK.track-number.", trackNumberIsCancelled),
                "trackNumber" to trackNumberName,
            )
        )
    } else {
        val numberParams = localizationParams("trackNumber" to trackNumber.number)
        listOfNotNull(
            validateWithParams(locationTrack.trackNumberId == trackNumber.id) {
                "$VALIDATION_LOCATION_TRACK.track-number.not-official" to numberParams
            },
            validateWithParams(locationTrack.state.isRemoved() || trackNumber.state.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.track-number.state.${trackNumber.state}" to numberParams
            },
        )
    }
}

data class SegmentSwitch(
    val switchId: IntId<LayoutSwitch>,
    val switchName: SwitchName?,
    val switch: LayoutSwitch?,
    val switchStructure: SwitchStructure?,
    val segments: List<LayoutSegment>,
)

fun validateSegmentSwitchReferences(
    locationTrack: LocationTrack,
    segmentSwitches: List<SegmentSwitch>,
): List<LayoutValidationIssue> {
    return segmentSwitches.flatMap { segmentSwitch ->
        val switch = segmentSwitch.switch
        val switchStructure = segmentSwitch.switchStructure
        val segments = segmentSwitch.segments

        val nameLocalizationParams = localizationParams("switch" to segmentSwitch.switchName)

        if (switch == null || switchStructure == null) {
            listOf(validationError("$VALIDATION_LOCATION_TRACK.switch.not-published", nameLocalizationParams))
        } else {
            val stateErrors: List<LayoutValidationIssue> =
                listOfNotNull(
                    validateWithParams(segments.all { segment -> switch.id == segment.switchId }) {
                        "$VALIDATION_LOCATION_TRACK.switch.not-official" to nameLocalizationParams
                    },
                    validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                        "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to
                            nameLocalizationParams
                    },
                )

            val geometryErrors: List<LayoutValidationIssue> =
                if (locationTrack.exists && switch.exists) {
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
                            "$VALIDATION_LOCATION_TRACK.switch.wrong-joint-sequence" to
                                localizationParams(
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
}

fun validateTopologicallyConnectedSwitchReferences(
    locationTrack: LocationTrack,
    topologicallyConnectedSwitches: List<Pair<SwitchName, LayoutSwitch?>>,
): List<LayoutValidationIssue> =
    topologicallyConnectedSwitches.mapNotNull { (name, switch) ->
        val nameParams = localizationParams("switch" to name)
        if (switch == null) {
            validationError("$VALIDATION_LOCATION_TRACK.switch.not-published", nameParams)
        } else {
            validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to nameParams
            }
        }
    }

private fun jointSequence(joints: List<JointNumber>) =
    joints.joinToString("-") { jointNumber -> "${jointNumber.intValue}" }

fun noGeocodingContext(validationTargetLocalizationPrefix: String) =
    LayoutValidationIssue(ERROR, "$validationTargetLocalizationPrefix.no-context")

fun validateGeocodingContext(
    contextCreateResult: GeocodingContextCreateResult,
    trackNumber: TrackNumber,
): List<LayoutValidationIssue> {
    val context = contextCreateResult.geocodingContext

    val badStartPoint =
        validateWithParams(contextCreateResult.startPointRejectedReason == null) {
            "$VALIDATION_GEOCODING.start-km-too-long" to localizationParams()
        }

    val kmPostsInWrongOrder =
        context.referencePoints
            .filter { point -> point.intersectType == WITHIN }
            .filterIndexed { index, point ->
                val previous = context.referencePoints.getOrNull(index - 1)
                val next = context.referencePoints.getOrNull(index + 1)
                !isOrderOk(previous, point) || !isOrderOk(point, next)
            }
            .let { invalidPoints ->
                validateWithParams(invalidPoints.isEmpty()) {
                    "$VALIDATION_GEOCODING.km-posts-invalid" to
                        localizationParams(
                            "trackNumber" to context.trackNumber,
                            "kmNumbers" to invalidPoints.joinToString(", ") { point -> point.kmNumber.toString() },
                        )
                }
            }

    val kmPostsFarFromLine =
        context.referencePoints
            .filter { point -> point.intersectType == WITHIN }
            .filter { point -> point.kmPostOffset > MAX_KM_POST_OFFSET }
            .let { farAwayPoints ->
                validateWithParams(farAwayPoints.isEmpty(), WARNING) {
                    "$VALIDATION_GEOCODING.km-posts-far-from-line" to
                        localizationParams(
                            "trackNumber" to context.trackNumber,
                            "kmNumbers" to farAwayPoints.joinToString(",") { point -> point.kmNumber.toString() },
                        )
                }
            }

    val kmPostsRejected =
        contextCreateResult.rejectedKmPosts.map { (kmPost, reason) ->
            val kmPostLocalizationParams = mapOf("trackNumber" to trackNumber, "kmNumber" to kmPost.kmNumber)

            when (reason) {
                KmPostRejectedReason.TOO_FAR_APART ->
                    LayoutValidationIssue(ERROR, "$VALIDATION_GEOCODING.km-post-too-long", kmPostLocalizationParams)

                KmPostRejectedReason.NO_LOCATION ->
                    LayoutValidationIssue(ERROR, "$VALIDATION_GEOCODING.km-post-no-location", kmPostLocalizationParams)

                KmPostRejectedReason.IS_BEFORE_START_ADDRESS ->
                    LayoutValidationIssue(
                        WARNING,
                        "$VALIDATION_GEOCODING.km-post-smaller-than-track-number-start",
                        kmPostLocalizationParams,
                    )

                KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE ->
                    LayoutValidationIssue(
                        WARNING,
                        "$VALIDATION_GEOCODING.km-post-outside-line-before",
                        kmPostLocalizationParams,
                    )

                KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE ->
                    LayoutValidationIssue(
                        WARNING,
                        "$VALIDATION_GEOCODING.km-post-outside-line-after",
                        kmPostLocalizationParams,
                    )

                KmPostRejectedReason.DUPLICATE ->
                    LayoutValidationIssue(FATAL, "$VALIDATION_GEOCODING.duplicate-km-posts", kmPostLocalizationParams)
            }
        }

    return kmPostsRejected + listOfNotNull(kmPostsFarFromLine, kmPostsInWrongOrder, badStartPoint)
}

private fun isOrderOk(previous: GeocodingReferencePoint?, next: GeocodingReferencePoint?) =
    if (previous == null || next == null) true else previous.distance < next.distance

fun validateAddressPoints(
    trackNumber: LayoutTrackNumber,
    locationTrack: LocationTrack,
    validationTargetLocalizationPrefix: String,
    geocode: () -> AlignmentAddresses?,
): List<LayoutValidationIssue> =
    try {
        geocode()?.let { addresses -> validateAddressPoints(trackNumber, locationTrack, addresses) }
            ?: listOf(LayoutValidationIssue(ERROR, "$validationTargetLocalizationPrefix.no-context", emptyMap()))
    } catch (e: ClientException) {
        listOf(LayoutValidationIssue(ERROR, e.localizationKey))
    }

fun validateAddressPoints(
    trackNumber: LayoutTrackNumber,
    locationTrack: LocationTrack,
    addresses: AlignmentAddresses,
): List<LayoutValidationIssue> {
    val allPoints = listOf(addresses.startPoint) + addresses.midPoints + listOf(addresses.endPoint)
    val allCoordinates = allPoints.map(AddressPoint::point)
    val allAddresses = allPoints.map(AddressPoint::address)
    val maxRanges = 5
    fun describeAsAddressRanges(indices: List<ClosedRange<Int>>): String =
        indices
            .take(maxRanges)
            .joinToString(", ") { range ->
                "${allAddresses[range.start].formatDropDecimals()}..${allAddresses[range.endInclusive].formatDropDecimals()}"
            }
            .let { if (indices.size > maxRanges) "$it..." else it }

    val discontinuousDirectionRanges = describeAsAddressRanges(discontinuousDirectionRangeIndices(allCoordinates))
    val stretchedMeterRanges = describeAsAddressRanges(stretchedMeterRangeIndices(allCoordinates))
    val discontinuousAddressRanges = describeAsAddressRanges(discontinuousAddressRangeIndices(allAddresses))

    return listOfNotNull(
        validateWithParams(addresses.startIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.start-outside-reference-line" to
                localizationParams("referenceLine" to trackNumber.number, "locationTrack" to locationTrack.name)
        },
        validateWithParams(addresses.endIntersect == WITHIN) {
            "$VALIDATION_GEOCODING.end-outside-reference-line" to
                localizationParams("referenceLine" to trackNumber.number, "locationTrack" to locationTrack.name)
        },
        validateWithParams(discontinuousDirectionRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.sharp-angle" to
                localizationParams(
                    "trackNumber" to trackNumber.number,
                    "locationTrack" to locationTrack.name,
                    "kmNumbers" to discontinuousDirectionRanges,
                )
        },
        validateWithParams(stretchedMeterRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.stretched-meters" to
                localizationParams(
                    "trackNumber" to trackNumber.number,
                    "locationTrack" to locationTrack.name,
                    "kmNumbers" to stretchedMeterRanges,
                )
        },
        validateWithParams(discontinuousAddressRanges.isEmpty()) {
            "$VALIDATION_GEOCODING.not-continuous" to
                localizationParams(
                    "trackNumber" to trackNumber.number,
                    "locationTrack" to locationTrack.name,
                    "kmNumbers" to discontinuousAddressRanges,
                )
        },
    )
}

fun validateReferenceLineGeometry(alignment: LayoutAlignment) = validateGeometry(VALIDATION_REFERENCE_LINE, alignment)

fun validateLocationTrackGeometry(geometry: LocationTrackGeometry) =
    validateGeometry(VALIDATION_LOCATION_TRACK, geometry)

private fun validateGeometry(errorParent: String, alignment: IAlignment) =
    listOfNotNull(
        validate(alignment.segments.isNotEmpty()) { "$errorParent.empty-segments" },
        validate(getMaxDirectionDeltaRads(alignment) <= MAX_LAYOUT_POINT_ANGLE_CHANGE) {
            "$errorParent.points.not-continuous"
        },
    )

fun getMaxDirectionDeltaRads(alignment: IAlignment): Double =
    alignment.allSegmentPoints.zipWithNext(::directionBetweenPoints).zipWithNext(::angleDiffRads).maxOrNull() ?: 0.0

private fun segmentAndJointLocationsAgree(switch: LayoutSwitch, segmentGroup: List<LayoutSegment>): Boolean =
    segmentGroup.all { segment -> segmentAndJointLocationsAgree(switch, segment) }

private fun segmentAndJointLocationsAgree(switch: LayoutSwitch, segment: LayoutSegment): Boolean {
    val jointLocations =
        listOfNotNull(
            segment.startJointNumber?.let { jn -> segment.segmentStart to jn },
            segment.endJointNumber?.let { jn -> segment.segmentEnd to jn },
        )
    return jointLocations.all { (location, jointNumber) ->
        val joint = switch.getJoint(jointNumber)
        joint != null && joint.location.isSame(location, JOINT_LOCATION_DELTA)
    }
}

private fun topologyLinkAndJointLocationsAgree(switch: LayoutSwitch, endLinks: List<TopologyEndLink>): Boolean {
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
        val partialAlignments =
            if (
                presentationJointIndex != -1 &&
                    firstJointNumber != structure.presentationJointNumber &&
                    lastJointNumber != structure.presentationJointNumber
            ) {
                listOf(
                    alignment.jointNumbers.subList(0, presentationJointIndex + 1),
                    alignment.jointNumbers.subList(presentationJointIndex, alignment.jointNumbers.lastIndex + 1),
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
    segments
        .mapIndexed { index, segment ->
            index == 0 || segments[index - 1].segmentEnd.isSame(segment.segmentStart, LAYOUT_COORDINATE_DELTA)
        }
        .all { it }

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

fun validate(valid: Boolean, type: LayoutValidationIssueType = ERROR, issue: () -> String) =
    validateWithParams(valid, type) { issue() to LocalizationParams.empty }

fun validateWithParams(
    valid: Boolean,
    type: LayoutValidationIssueType = ERROR,
    issue: () -> Pair<String, LocalizationParams>,
): LayoutValidationIssue? =
    if (!valid) {
        issue().let { (key, params) -> LayoutValidationIssue(type, LocalizationKey(key), params) }
    } else {
        null
    }

data class TopologyEndLink(val topologySwitch: TopologyLocationTrackSwitch, val point: AlignmentPoint)

private fun collectTopologyEndLinks(
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switch: LayoutSwitch,
): List<Pair<LocationTrack, List<TopologyEndLink>>> =
    locationTracks
        .map { (track, alignment) ->
            track to
                listOfNotNull(
                    track.topologyStartSwitch
                        ?.takeIf { topologySwitch -> topologySwitch.switchId == switch.id }
                        ?.let { topologySwitch -> alignment.start?.let { p -> TopologyEndLink(topologySwitch, p) } },
                    track.topologyEndSwitch
                        ?.takeIf { topologySwitch -> topologySwitch.switchId == switch.id }
                        ?.let { topologySwitch -> alignment.end?.let { p -> TopologyEndLink(topologySwitch, p) } },
                )
        }
        .filter { (_, ends) -> ends.isNotEmpty() }

fun validationFatal(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(FATAL, key, params.associate { it })

fun validationError(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(ERROR, key, params.associate { it })

fun validationError(key: String, params: LocalizationParams): LayoutValidationIssue =
    LayoutValidationIssue(ERROR, LocalizationKey(key), params)

fun validationWarning(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(WARNING, key, params.associate { it })

fun validationWarning(key: String, params: LocalizationParams): LayoutValidationIssue =
    LayoutValidationIssue(WARNING, LocalizationKey(key), params)

private fun cancelledOrNotPublishedKey(keyPrefix: String, cancelled: Boolean) =
    "$keyPrefix${if (cancelled) "cancelled" else "not-published"}"
