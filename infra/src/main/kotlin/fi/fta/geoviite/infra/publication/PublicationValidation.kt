package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.ClientException
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingReferencePoint
import fi.fta.geoviite.infra.geocoding.KmPostRejectedReason
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.FATAL
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.WARNING
import fi.fta.geoviite.infra.switchLibrary.LinkableSwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.switchConnectivity
import fi.fta.geoviite.infra.tracklayout.GeocodingAlignmentM
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameBetweenOperatingPoints
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameChord
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.TOPOLOGY_CALC_DISTANCE
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLink
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
    trackNumberExists: Boolean,
    trackNumberIsCancelled: Boolean,
    referenceLine: ReferenceLine?,
    kmPosts: List<LayoutKmPost>,
    locationTracks: List<LocationTrack>,
): List<LayoutValidationIssue> =
    listOfNotNull(
        referenceLine?.let {
            validate(trackNumberExists || !trackNumberIsCancelled) {
                "$VALIDATION_TRACK_NUMBER.reference-line.cancelled-from-track-number"
            }
        },
        validate(!trackNumberExists || referenceLine != null) {
            "$VALIDATION_TRACK_NUMBER.reference-line.not-published"
        },
        locationTracks.filter(LocationTrack::exists).let { existingTracks ->
            validateWithParams(trackNumberExists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted" to
                    localizationParams("locationTracks" to existingNames)
            }
        },
        kmPosts.filter(LayoutKmPost::exists).let { existingKmPosts ->
            validateWithParams(trackNumberExists || existingKmPosts.isEmpty()) {
                val existingNames = existingKmPosts.joinToString(", ") { post -> post.kmNumber.toString() }
                "$VALIDATION_TRACK_NUMBER.km-post.reference-deleted" to localizationParams("kmPosts" to existingNames)
            }
        },
    )

fun validateTrackNumberNumberDuplication(
    trackNumber: LayoutTrackNumber,
    duplicates: List<LayoutTrackNumber>,
    validationTargetType: ValidationTargetType,
): List<LayoutValidationIssue> =
    validateNameDuplication(
        VALIDATION_TRACK_NUMBER,
        validationTargetType,
        duplicates.any { d -> d.id != trackNumber.id && d.isOfficial },
        duplicates.any { d -> d.id != trackNumber.id && d.isDraft },
        "trackNumber" to trackNumber.number,
    )

fun validateKmPostReferences(
    kmPost: LayoutKmPost,
    trackNumber: LayoutTrackNumber?,
    referenceLine: ReferenceLine?,
    trackNumberNumber: TrackNumber?,
    trackNumberIsCancelled: Boolean,
): List<LayoutValidationIssue> =
    listOfNotNull(
        validateWithParams(trackNumber != null) {
            cancelledOrNotPublishedKey("$VALIDATION_KM_POST.track-number", trackNumberIsCancelled) to
                localizationParams("trackNumber" to trackNumberNumber)
        },
        validateWithParams(referenceLine != null) {
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

fun validateKmPostNumberDuplication(
    kmPost: LayoutKmPost,
    trackNumber: TrackNumber,
    sameTrackNumberKmPosts: List<LayoutKmPost>,
    validationTargetType: ValidationTargetType,
): List<LayoutValidationIssue> {
    val duplicates = sameTrackNumberKmPosts.filter { it.id != kmPost.id && it.kmNumber == kmPost.kmNumber }
    return validateNameDuplication(
        VALIDATION_KM_POST,
        validationTargetType,
        duplicates.any { it.isOfficial },
        duplicates.any { it.isDraft },
        "kmNumber" to kmPost.kmNumber,
        "trackNumber" to trackNumber.toString(),
    )
}

fun validateSwitchLocationTrackLinkReferences(
    switchExists: Boolean,
    switchIsCancelled: Boolean,
    locationTracks: List<LocationTrack>,
): List<LayoutValidationIssue> {
    return listOfNotNull(
        locationTracks.filter(LocationTrack::exists).let { existingTracks ->
            validateWithParams(switchExists || existingTracks.isEmpty()) {
                val existingNames = existingTracks.joinToString(", ") { track -> track.name }
                "$VALIDATION_SWITCH.location-track.${if (switchIsCancelled) "cancelled"  else "reference-deleted"}" to
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

fun validateNameMandatedSwitchLinks(track: LocationTrack): List<LayoutValidationIssue> {
    val endSwitchesRequired =
        track.nameStructure.scheme == LocationTrackNamingScheme.CHORD ||
            track.nameStructure.scheme == LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS
    val bothEndSwitchesExist = track.startSwitchId != null && track.endSwitchId != null

    return listOfNotNull(
        validate(!endSwitchesRequired || bothEndSwitchesExist, ERROR) {
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-name"
        }
    )
}

fun validateSwitchNameShortenability(switch: LayoutSwitch) =
    listOfNotNull(
        validateWithParams(switch.nameParts != null) {
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name" to localizationParams("switchName" to switch.name)
        }
    )

fun validateDescriptionMandatedSwitchLinks(track: LocationTrack): List<LayoutValidationIssue> {
    val hasBothEndSwitches = track.startSwitchId != null && track.endSwitchId != null
    val hasOnlyOneEndSwitch = (track.startSwitchId != null || track.endSwitchId != null) && !hasBothEndSwitches

    return when (track.descriptionStructure.suffix) {
        LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH -> {
            listOfNotNull(
                validate(hasBothEndSwitches) { "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-description" }
            )
        }

        LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY,
        LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER -> {
            listOfNotNull(
                validate(hasOnlyOneEndSwitch) { "$VALIDATION_LOCATION_TRACK.switch.missing-one-switch" },
                validate(!hasBothEndSwitches) { "$VALIDATION_LOCATION_TRACK.switch.too-many-switches" },
            )
        }

        LocationTrackDescriptionSuffix.NONE -> {
            emptyList()
        }
    }
}

fun validateLocationTrackEndSwitchNames(track: LocationTrack, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?) =
    validateNameMandatedSwitchLinks(track) +
        validateDescriptionMandatedSwitchLinks(track) +
        validateLocationTrackEndSwitchNamingScheme(track, startSwitch, endSwitch)

fun validateLocationTrackEndSwitchNamingScheme(
    track: LocationTrack,
    startSwitch: LayoutSwitch?,
    endSwitch: LayoutSwitch?,
): List<LayoutValidationIssue> {
    require(startSwitch == null || track.startSwitchId == startSwitch.id) {
        "start switch id does not match track's start switch id: ${track.startSwitchId} != ${startSwitch?.id}"
    }
    require(endSwitch == null || track.endSwitchId == endSwitch.id) {
        "end switch id does not match track's end switch id: ${track.endSwitchId} != ${endSwitch?.id}"
    }

    val nameHasSwitchNames =
        track.nameStructure is LocationTrackNameChord || track.nameStructure is LocationTrackNameBetweenOperatingPoints
    val descriptionHasSwitchNames = track.descriptionStructure.suffix != LocationTrackDescriptionSuffix.NONE

    return if (nameHasSwitchNames || descriptionHasSwitchNames) {
        val startSwitchErrors = if (startSwitch != null) validateSwitchNameShortenability(startSwitch) else emptyList()
        val endSwitchErrors = if (endSwitch != null) validateSwitchNameShortenability(endSwitch) else emptyList()

        startSwitchErrors + endSwitchErrors
    } else emptyList()
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
    locationTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<LayoutValidationIssue> {
    if (!switch.exists) return emptyList()
    val indexedLinks =
        locationTracksAndGeometries.mapNotNull { (track, geometry) ->
            geometry
                .takeIf { track.exists }
                ?.let { geom ->
                    geom.trackSwitchLinks
                        .mapIndexed { index, link -> index to link }
                        .filter { (_, link) -> link.switchId == switch.id }
                }
                ?.takeIf { links -> links.isNotEmpty() }
                ?.let { links -> track to links }
        }
    val trackLinks = indexedLinks.map { (track, links) -> track to links.map { (_, link) -> link } }
    val tracksWithPartialSwitchEdges =
        locationTracksAndGeometries.mapNotNull { (track, geometry) ->
            track.name.takeIf {
                geometry.edges.any { edge ->
                    edge.containsSwitch(switch.id as IntId) && getEdgePartialSwitchIds(edge).contains(switch.id)
                }
            }
        }

    val structureJoints = collectJoints(structure)

    return listOfNotNull(
        indexedLinks
            .filterNot { (_, links) -> areLinksContinuous(links) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.not-continuous" to
                        localizationParams("locationTracks" to errorTrackNames)
                }
            },
        trackLinks
            .filterNot { (_, links) -> nodeAndJointLocationsAgree(switch, links) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty(), WARNING) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.joint-location-mismatch" to
                        localizationParams("locationTracks" to errorTrackNames)
                }
            },
        trackLinks
            .filterNot { (_, group) -> trackJointGroupFound(group.map(TrackSwitchLink::jointNumber), structureJoints) }
            .let { errorGroups ->
                validateWithParams(errorGroups.isEmpty()) {
                    val errorTrackNames = errorGroups.joinToString(", ") { (track, _) -> track.name }
                    "$VALIDATION_SWITCH.location-track.wrong-joint-sequence" to
                        localizationParams("locationTracks" to errorTrackNames)
                }
            },
        validateWithParams(tracksWithPartialSwitchEdges.isEmpty()) {
            "$VALIDATION_SWITCH.location-track.partial-linking-edge" to
                localizationParams("locationTracks" to tracksWithPartialSwitchEdges.joinToString(", "))
        },
    ) + validateSwitchTopologicalConnectivity(switch, structure, locationTracksAndGeometries, null)
}

fun validateLocationTrackSwitchConnectivity(
    layoutTrack: LocationTrack,
    geometry: LocationTrackGeometry,
): List<LayoutValidationIssue> {
    val hasStartSwitch = geometry.startSwitchLink != null
    val hasEndSwitch = geometry.endSwitchLink != null

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
    locationTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
    validatingTrack: LocationTrack?,
): List<LayoutValidationIssue> {
    val existingTracks = locationTracksAndGeometries.filter { it.first.exists }
    return listOf(
            listOfNotNull(validateFrontJointTopology(switch, structure, existingTracks, validatingTrack)),
            validateSwitchAlignmentTopology(
                switch.id as IntId,
                structure,
                existingTracks,
                switch.name,
                validatingTrack,
            ),
        )
        .flatten()
}

fun switchOrTrackLinkageKey(validatingTrack: LocationTrack?) =
    if (validatingTrack != null) "$VALIDATION_LOCATION_TRACK.switch-linkage" else "$VALIDATION_SWITCH.track-linkage"

private fun validateFrontJointTopology(
    switch: LayoutSwitch,
    switchStructure: SwitchStructure,
    locationTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
    validatingTrack: LocationTrack?,
): LayoutValidationIssue? {
    val connectivity = switchConnectivity(switchStructure)
    val frontJointConnections =
        connectivity.frontJoint?.let { frontJoint ->
            tracksWithOutsideConnection(switch.id as IntId, frontJoint, locationTracksAndGeometries)
        } ?: emptyList()

    val someFrontJointLink = frontJointConnections.isNotEmpty()
    val frontJointLinkInNonDuplicates = frontJointConnections.any { track -> track.duplicateOf == null }

    return validateWithParams(connectivity.frontJoint == null || frontJointLinkInNonDuplicates, WARNING) {
        val key =
            "${switchOrTrackLinkageKey(validatingTrack)}.${
            if (someFrontJointLink) "front-joint-only-duplicate-connected"
            else "front-joint-not-connected"
        }"

        key to localizationParams("switch" to switch.name.toString())
    }
}

private fun tracksWithOutsideConnection(
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<LocationTrack> =
    tracks.mapNotNull { (track, geometry) ->
        track.takeIf {
            geometry.nodes.isNotEmpty() &&
                (geometry.startNode?.switchOut?.matches(switchId, jointNumber) ?: false ||
                    geometry.endNode?.switchOut?.matches(switchId, jointNumber) ?: false ||
                    geometry.nodes.subList(1, geometry.nodes.lastIndex).any { node ->
                        node.containsJoint(switchId, jointNumber)
                    })
        }
    }

private fun findValidatingTrackSwitchAlignment(
    switchId: IntId<LayoutSwitch>,
    validatingTrack: LocationTrack?,
    locationTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
    structureAlignmentsToCheck: List<LinkableSwitchStructureAlignment>,
): SwitchStructureAlignment? =
    locationTracksAndGeometries
        .find { (track) -> track.id == validatingTrack?.id }
        ?.let { trackAndGeometry ->
            structureAlignmentsToCheck
                // with really bad linking, this could be ambiguous, and the choice of finding the
                // first one arbitrary; but that shouldn't really happen
                .find { switchAlignment ->
                    alignmentLinkingQuality(switchId, switchAlignment, listOf(trackAndGeometry)).hasSomethingLinked()
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
    switchId: IntId<LayoutSwitch>,
    switchStructure: SwitchStructure,
    locationTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
    switchName: SwitchName,
    validatingTrack: LocationTrack?,
): List<LayoutValidationIssue> {
    val structureAlignmentsToCheck = switchConnectivity(switchStructure).alignments.filter { !it.isSplittable }
    val linkingQuality =
        structureAlignmentsToCheck.map { alignment ->
            alignmentLinkingQuality(switchId, alignment, locationTracksAndGeometries)
        }
    val validatingTrackSwitchAlignment =
        findValidatingTrackSwitchAlignment(
            switchId,
            validatingTrack,
            locationTracksAndGeometries,
            structureAlignmentsToCheck,
        )
    val switchHasTopologicalConnections =
        locationTracksAndGeometries.any { (_, geom) ->
            geom.outerStartSwitch?.id == switchId || geom.outerEndSwitch?.id == switchId
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
    val switchAlignment: LinkableSwitchStructureAlignment,
    val nonDuplicateTracks: List<LocationTrack>,
    val duplicateTracks: List<LocationTrack>,
    val fullyLinked: List<LocationTrack>,
    val partiallyLinked: List<LocationTrack>,
) {

    val originalAlignment = switchAlignment.originalAlignment

    fun hasSomethingLinked() = nonDuplicateTracks.isNotEmpty() || duplicateTracks.isNotEmpty()
}

private fun alignmentLinkingQuality(
    switchId: IntId<LayoutSwitch>,
    switchAlignment: LinkableSwitchStructureAlignment,
    tracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
): SwitchAlignmentLinkingQuality {
    val nonDuplicateTracks = mutableListOf<LocationTrack>()
    val duplicateTracks = mutableListOf<LocationTrack>()
    val fullyLinked = mutableListOf<LocationTrack>()
    val partiallyLinked = mutableListOf<LocationTrack>()

    tracksAndGeometries.forEach { (track, geometry) ->
        val trackSwitchJoints = geometry.getSwitchJoints(switchId, includeOuterLinks = false).toSet()
        val hasStart = trackSwitchJoints.contains(switchAlignment.joints.first())
        val hasEnd = trackSwitchJoints.contains(switchAlignment.joints.last())
        val trackAlignmentHasOtherLinks = trackSwitchJoints.subtract(switchAlignment.joints.toSet()).isNotEmpty()

        val isPartiallyLinkedWithoutOtherLinks = (hasStart || hasEnd) && !trackAlignmentHasOtherLinks
        val isFullyLinkedToSplitAlignment = hasStart && hasEnd
        val isFullyLinkedToOriginalAlignment =
            trackSwitchJoints.contains(switchAlignment.originalAlignment.jointNumbers.first()) &&
                trackSwitchJoints.contains(switchAlignment.originalAlignment.jointNumbers.last())
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
                        "$VALIDATION_LOCATION_TRACK.duplicate-of",
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
            cancelledOrNotPublishedKey("$VALIDATION_REFERENCE_LINE.track-number", trackNumberIsCancelled) to
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
                cancelledOrNotPublishedKey("$VALIDATION_LOCATION_TRACK.track-number", trackNumberIsCancelled),
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

data class SwitchTrackLinking(
    val switchId: IntId<LayoutSwitch>,
    val switchName: SwitchName?,
    val switch: LayoutSwitch?,
    val switchStructure: SwitchStructure?,
    val indexedLinks: List<Pair<Int, TrackSwitchLink>>,
    val switchIsCancelled: Boolean,
)

fun validateTrackSwitchReferences(
    locationTrack: LocationTrack,
    switchTrackLinkings: List<SwitchTrackLinking>,
): List<LayoutValidationIssue> {
    return switchTrackLinkings.flatMap { linking ->
        val switch = linking.switch
        val switchStructure = linking.switchStructure
        val indexedLinks = linking.indexedLinks
        val nameLocalizationParams = localizationParams("switch" to linking.switchName)

        if (switch == null || switchStructure == null) {
            listOf(
                validationError(
                    cancelledOrNotPublishedKey("$VALIDATION_LOCATION_TRACK.switch", linking.switchIsCancelled),
                    nameLocalizationParams,
                )
            )
        } else {
            val stateErrors: List<LayoutValidationIssue> =
                listOfNotNull(
                    validateWithParams(!locationTrack.exists || switch.stateCategory.isLinkable()) {
                        "$VALIDATION_LOCATION_TRACK.switch.state-category.${switch.stateCategory}" to
                            nameLocalizationParams
                    }
                )

            val geometryErrors: List<LayoutValidationIssue> =
                if (locationTrack.exists && switch.exists) {
                    val links = indexedLinks.map { it.second }
                    val structureJoints = collectJoints(switchStructure)
                    val trackJoints = links.map(TrackSwitchLink::jointNumber)
                    listOfNotNull(
                        validateWithParams(areLinksContinuous(indexedLinks)) {
                            "$VALIDATION_LOCATION_TRACK.switch.alignment-not-continuous" to nameLocalizationParams
                        },
                        validateWithParams(nodeAndJointLocationsAgree(switch, links), WARNING) {
                            "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch" to nameLocalizationParams
                        },
                        validateWithParams(trackJointGroupFound(trackJoints, structureJoints)) {
                            "$VALIDATION_LOCATION_TRACK.switch.wrong-joint-sequence" to
                                nameLocalizationParams +
                                    localizationParams(
                                        "switchType" to switchStructure.baseType.name,
                                        "switchJoints" to jointSequence(trackJoints),
                                    )
                        },
                        validateWithParams(trackJoints.isNotEmpty()) {
                            "$VALIDATION_LOCATION_TRACK.switch.wrong-links" to nameLocalizationParams
                        },
                    )
                } else listOf()
            stateErrors + geometryErrors
        }
    }
}

private fun jointSequence(joints: List<JointNumber>) =
    joints.joinToString("-") { jointNumber -> "${jointNumber.intValue}" }

fun noGeocodingContext(validationTargetLocalizationPrefix: String) =
    LayoutValidationIssue(ERROR, "$validationTargetLocalizationPrefix.no-context")

fun validateGeocodingContext(
    contextCreateResult: GeocodingContextCreateResult<ReferenceLineM>,
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

private fun <M : GeocodingAlignmentM<M>> isOrderOk(
    previous: GeocodingReferencePoint<M>?,
    next: GeocodingReferencePoint<M>?,
) = if (previous == null || next == null) true else previous.distance < next.distance

fun validateAddressPoints(
    trackNumber: LayoutTrackNumber,
    locationTrack: LocationTrack,
    validationTargetLocalizationPrefix: String,
    geocode: () -> AlignmentAddresses<*>?,
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
    addresses: AlignmentAddresses<*>,
): List<LayoutValidationIssue> {
    val allPoints = listOf(addresses.startPoint) + addresses.midPoints + listOf(addresses.endPoint)
    val allCoordinates = allPoints.map { it.point }
    val allAddresses = allPoints.map { it.address }
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
        validateWithParams(addresses.alignmentWalkFinished) {
            "$VALIDATION_GEOCODING.bad-geometry-for-geocoding" to
                localizationParams(
                    "trackNumber" to trackNumber.number,
                    "locationTrack" to locationTrack.name,
                    "lastAddress" to (addresses.midPoints.lastOrNull()?.address ?: addresses.startPoint.address),
                )
        },
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

fun validateReferenceLineGeometry(alignment: LayoutAlignment): List<LayoutValidationIssue> =
    validateGeometry(VALIDATION_REFERENCE_LINE, alignment)

fun validateLocationTrackGeometry(geometry: LocationTrackGeometry): List<LayoutValidationIssue> =
    validateGeometry(VALIDATION_LOCATION_TRACK, geometry) +
        listOfNotNull(
            validateWithParams(geometry.length.distance > TOPOLOGY_CALC_DISTANCE, WARNING) {
                "$VALIDATION_LOCATION_TRACK.too-short" to
                    localizationParams(
                        "minLength" to TOPOLOGY_CALC_DISTANCE.toString(),
                        "length" to roundTo3Decimals(geometry.length.distance),
                    )
            }
        )

fun validateEdges(
    geometry: LocationTrackGeometry,
    getSwitchName: (IntId<LayoutSwitch>) -> SwitchName,
): List<LayoutValidationIssue> = geometry.edges.flatMap { edge -> validateEdge(edge, getSwitchName) }

fun validateEdge(edge: LayoutEdge, getSwitchName: (IntId<LayoutSwitch>) -> SwitchName): List<LayoutValidationIssue> =
    getEdgePartialSwitchIds(edge).map { partial ->
        validationWarning("$VALIDATION_LOCATION_TRACK.edge-switch-partial", "switch" to getSwitchName(partial))
    }

fun getEdgePartialSwitchIds(edge: LayoutEdge): List<IntId<LayoutSwitch>> =
    (edge.startNode.switchIn?.id to edge.endNode.switchIn?.id).let { (startId, endId) ->
        when {
            startId == null && endId == null -> emptyList()
            startId != endId -> listOfNotNull(startId, endId)
            else -> emptyList()
        }
    }

private fun validateGeometry(errorParent: String, alignment: IAlignment<*>) =
    listOfNotNull(
        validate(alignment.segments.isNotEmpty()) { "$errorParent.empty-segments" },
        validate(getMaxDirectionDeltaRads(alignment) <= MAX_LAYOUT_POINT_ANGLE_CHANGE) {
            "$errorParent.points.not-continuous"
        },
    )

fun getMaxDirectionDeltaRads(alignment: IAlignment<*>): Double =
    alignment.allSegmentPoints.zipWithNext(::directionBetweenPoints).zipWithNext(::angleDiffRads).maxOrNull() ?: 0.0

private fun nodeAndJointLocationsAgree(switch: LayoutSwitch, trackLinks: List<TrackSwitchLink>): Boolean =
    trackLinks
        .filter { link -> link.switchId == switch.id }
        .all { link ->
            val joint = switch.getJoint(link.jointNumber)
            joint != null && joint.location.isSame(link.location, JOINT_LOCATION_DELTA)
        }

private fun trackJointGroupFound(trackJoints: List<JointNumber>, structureJointGroups: List<List<JointNumber>>) =
    structureJointGroups.any { structureJoints -> jointGroupMatches(trackJoints, structureJoints) }

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

private fun areLinksContinuous(links: List<Pair<Int, TrackSwitchLink>>): Boolean =
    links.zipWithNext().all { (prev, next) -> prev.first + 1 == next.first }

private fun discontinuousDirectionRangeIndices(points: List<IPoint>) =
    rangesOfConsecutiveIndicesOf(false, points.zipWithNext(::directionBetweenPoints).zipWithNext(::isAngleDiffOk), 2)

private fun stretchedMeterRangeIndices(points: List<IPoint>) =
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
        issue().let { (key, params) -> LayoutValidationIssue(type, LocalizationKey.of(key), params) }
    } else {
        null
    }

fun validationFatal(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(FATAL, key, params.associate { it })

fun validationError(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(ERROR, key, params.associate { it })

fun validationError(key: String, params: LocalizationParams): LayoutValidationIssue =
    LayoutValidationIssue(ERROR, LocalizationKey.of(key), params)

fun validationWarning(key: String, vararg params: Pair<String, Any?>): LayoutValidationIssue =
    LayoutValidationIssue(WARNING, key, params.associate { it })

fun validationWarning(key: String, params: LocalizationParams): LayoutValidationIssue =
    LayoutValidationIssue(WARNING, LocalizationKey.of(key), params)

private fun cancelledOrNotPublishedKey(keyPrefix: String, cancelled: Boolean) =
    "$keyPrefix${if (cancelled) ".cancelled" else ".not-published"}"
