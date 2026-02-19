package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressPointsResult
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.ValidatedGeocodingContext
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.BuildTrackTopology
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionStructure
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameSpecifier
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameStructure
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.SegmentM
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLink
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.offsetGeometry
import fi.fta.geoviite.infra.tracklayout.rawPoints
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchAndMatchingAlignments
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackDescriptionStructure
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNameStructure
import fi.fta.geoviite.infra.tracklayout.trackNumber
import kotlin.math.PI
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PublicationValidationTest {

    private val structure = switchStructureYV60_300_1_9()

    @Test
    fun trackNumberValidationCatchesLocationTrackReferencingDeletedTrackNumber() {
        val trackNumber = trackNumber(id = IntId(1), draft = true)
        val referenceLine = referenceLine(trackNumberId = trackNumber.id as IntId, id = IntId(1), draft = true)
        val alignment = locationTrack(trackNumberId = IntId(1), draft = true)
        assertTrackNumberReferenceError(
            true,
            trackNumber.copy(state = LayoutState.DELETED),
            referenceLine,
            locationTrack(IntId(0), draft = true).copy(state = LocationTrackState.IN_USE),
            "$VALIDATION_TRACK_NUMBER.reference-from-location-track.deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = LayoutState.DELETED),
            referenceLine,
            alignment.copy(state = LocationTrackState.DELETED),
            "$VALIDATION_TRACK_NUMBER.reference-from-location-track.deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = LayoutState.IN_USE),
            referenceLine,
            alignment.copy(state = LocationTrackState.IN_USE),
            "$VALIDATION_TRACK_NUMBER.reference-from-location-track.deleted",
        )
    }

    @Test
    fun `Km-post validation catches un-published track number`() {
        val trackNumberId = IntId<LayoutTrackNumber>(1)
        val kmPost = kmPost(trackNumberId, KmNumber(1), draft = true)
        val trackNumber = trackNumber(id = IntId(2), draft = false)
        assertKmPostReferenceError(
            true,
            kmPost,
            null,
            trackNumber.number,
            "$VALIDATION_KM_POST.reference-to-track-number.not-published",
        )
        assertKmPostReferenceError(
            false,
            kmPost,
            trackNumber,
            trackNumber.number,
            "$VALIDATION_KM_POST.reference-to-track-number.not-published",
        )
    }

    @Test
    fun switchValidationCatchesNonContinuousAlignment() {
        val switch =
            switch(
                structureId = structure.id,
                id = IntId(1),
                joints =
                    listOf(
                        LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 0.0), null),
                        LayoutSwitchJoint(JointNumber(2), SwitchJointRole.CONNECTION, Point(0.0, 20.0), null),
                    ),
            )
        val switch2 =
            switch(
                structureId = structure.id,
                id = IntId(2),
                joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 10.0), null)),
            )
        val track = locationTrack(trackNumberId = IntId(0))
        val goodGeom =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(switch.id as IntId, 1),
                    endInnerSwitch = switchLinkYV(switch.id as IntId, 2),
                    segments =
                        listOf(segment(Point(0.0, 0.0), Point(0.0, 10.0)), segment(Point(0.0, 10.0), Point(0.0, 20.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(switch.id as IntId, 2),
                    segments = listOf(segment(Point(0.0, 20.0), Point(0.0, 30.0))),
                ),
            )
        val brokenGeom =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(switch.id as IntId, 1),
                    endInnerSwitch = switchLinkYV(switch2.id as IntId, 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(0.0, 10.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(switch2.id as IntId, 1),
                    endInnerSwitch = switchLinkYV(switch.id as IntId, 2),
                    segments = listOf(segment(Point(0.0, 10.0), Point(0.0, 20.0))),
                ),
            )

        assertSwitchSegmentStructureError(
            false,
            switch,
            track to goodGeom,
            "$VALIDATION_SWITCH.location-track.not-continuous",
        )
        assertSwitchSegmentStructureError(
            true,
            switch,
            track to brokenGeom,
            "$VALIDATION_SWITCH.location-track.not-continuous",
        )
    }

    @Test
    fun switchValidationCatchesLocationMismatch() {
        val (switch, alignments) = switchAndMatchingAlignments(IntId(0), structure, draft = true)
        val broken =
            alignments.mapIndexed { index, (track, geometry) ->
                if (index == 0) track to offsetGeometry(geometry, Point(5.0, 0.0)) else track to geometry
            }
        assertSwitchSegmentStructureError(
            hasError = false,
            switch = switch,
            tracks = alignments,
            error = "$VALIDATION_SWITCH.location-track.joint-location-mismatch",
        )
        assertSwitchSegmentStructureError(
            hasError = true,
            switch = switch,
            tracks = broken,
            error = "$VALIDATION_SWITCH.location-track.joint-location-mismatch",
        )
    }

    @Test
    fun switchValidationCatchesMissingLinking() {
        val (switch, alignments) = switchAndMatchingAlignments(IntId(0), structure, draft = true)
        val broken = alignments.take(alignments.size - 1)
        assertSwitchSegmentStructureError(
            hasError = false,
            switch = switch,
            tracks = alignments,
            error = "$VALIDATION_SWITCH.location-track.unlinked",
        )
        assertSwitchSegmentStructureError(
            hasError = true,
            switch = switch,
            tracks = broken,
            error = "$VALIDATION_SWITCH.track-linkage.switch-alignment-not-connected",
        )
    }

    @Test
    fun alignmentFieldValidationCatchesLackingGeometry() {
        assertLocationTrackFieldError(true, TmpLocationTrackGeometry.empty, "$VALIDATION_LOCATION_TRACK.empty-segments")
        assertLocationTrackFieldError(
            false,
            trackGeometryOfSegments(someSegment()),
            "$VALIDATION_LOCATION_TRACK.empty-segments",
        )
    }

    @Test
    fun validationCatchesUnPublishedSwitch() {
        assertLocationTrackToSwitchReferenceError(
            false,
            listOf(AssetLiveness("switch", AssetLivenessType.EXISTS)),
            "$VALIDATION_LOCATION_TRACK.reference-to-switch.not-published",
            locationTrack(IntId(1), draft = false),
        )
        assertLocationTrackToSwitchReferenceError(
            true,
            listOf(AssetLiveness("switch", AssetLivenessType.DRAFT_NOT_PUBLISHED)),
            "$VALIDATION_LOCATION_TRACK.reference-to-switch.not-published",
            locationTrack(IntId(1), draft = false),
        )
    }

    @Test
    fun validationCatchesReferencingDeletedSwitch() {
        assertLocationTrackToSwitchReferenceError(
            false,
            listOf(AssetLiveness("switch", AssetLivenessType.EXISTS)),
            "$VALIDATION_LOCATION_TRACK.reference-to-switch.deleted",
            locationTrack(IntId(1), draft = false),
        )
        assertLocationTrackToSwitchReferenceError(
            true,
            listOf(AssetLiveness("switch", AssetLivenessType.DELETED)),
            "$VALIDATION_LOCATION_TRACK.reference-to-switch.deleted",
            locationTrack(IntId(1), draft = false),
        )
        assertLocationTrackToSwitchReferenceError(
            false,
            listOf(AssetLiveness("switch", AssetLivenessType.DELETED)),
            "$VALIDATION_LOCATION_TRACK.reference-to-switch.deleted",
            locationTrack(IntId(1), draft = false, state = LocationTrackState.DELETED),
        )
    }

    @Test
    fun validationCatchesSegmentSwitchLocationMismatch() {
        val switchLinking = switchLinking()
        assertSegmentSwitchError(false, switchLinking, "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch")
        fun moveLink(link: Pair<Int, TrackSwitchLink>, point: Point) =
            link.first to link.second.copy(location = toAlignmentPoint(link.second.location + point))
        assertSegmentSwitchError(
            true,
            switchLinking.copy(
                indexedLinks =
                    listOf(switchLinking.indexedLinks[0], moveLink(switchLinking.indexedLinks[1], Point(0.0, 1.0)))
            ),
            "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch",
        )
        assertSegmentSwitchError(
            true,
            switchLinking.copy(
                indexedLinks =
                    listOf(moveLink(switchLinking.indexedLinks[0], Point(0.0, 1.0)), switchLinking.indexedLinks[1])
            ),
            "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch",
        )
    }

    @Test
    fun `Validation gives error when no addresses can be calculated`() {
        assertAddressPointError(true, { null }, "$VALIDATION_GEOCODING.no-addresses")
    }

    @Test
    fun `Validation gives error when end address is before start`() {
        val context = simpleGeocodingContext(rawPoints(100, 0.0, 0.0, 0.0, 1000.0))
        val testTrackNumber = trackNumber(draft = true)
        val testLocationTrack = locationTrack(IntId(1), draft = true)
        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.ERROR,
                    "$VALIDATION_GEOCODING.end-before-start",
                    mapOf(
                        "trackNumber" to testTrackNumber.number.value,
                        "locationTrack" to testLocationTrack.name.toString(),
                    ),
                )
            ),
            validateAddressPoints(testTrackNumber, testLocationTrack, VALIDATION_GEOCODING) {
                context.getAddressPoints(
                    // geometry being compared against (type is ignored) goes downward
                    referenceLineGeometry(segment(Point(10.0, 100.0), Point(10.0, 10.0))).copy(id = IntId(2))
                )
            },
        )
    }

    @Test
    fun validationOkForNormalAlignmentGeocoding() {
        // Reference line: straight up from origin, 1km
        val context = simpleGeocodingContext(rawPoints(100, 0.0, 0.0, 0.0, 1000.0))
        assertEquals(
            listOf(),
            validateAddressPoints(trackNumber(draft = true), locationTrack(trackNumberId = IntId(1), draft = true), "")
            // Alignment at slight angle to reference line -> should be OK
            {
                context.getAddressPoints(
                    referenceLineGeometry(segment(Point(10.0, 10.0), Point(20.0, 100.0))).copy(id = IntId(2))
                )
            },
        )
    }

    @Test
    fun validationCatchesStretchedOutAddressPoints() {
        val context =
            simpleGeocodingContext(
                // Reference line goes straight up
                toSegmentPoints(Point(0.0, 0.0), Point(0.0, 1000.0))
            )
        val geocode = {
            context.getAddressPoints(
                referenceLineGeometry(
                        segment(
                            Point(0.0, 10.0),
                            Point(0.0, 20.0),
                            Point(10.0, 40.0),
                            Point(30.0, 60.0),
                            Point(50.0, 70.0), // Over 45 degree diff to reference line -> should fail
                            Point(70.0, 90.0),
                        )
                    )
                    .copy(id = IntId(2))
            )
        }
        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.stretched-meters")
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0060..0000+0070")
    }

    @Test
    fun stretchedOutAddressPointsAtStartAndEndOfLineAreDescribedProperly() {
        val context =
            simpleGeocodingContext(
                // Reference line goes straight up
                toSegmentPoints(Point(0.0, 0.0), Point(0.0, 1000.0))
            )
        val geocode = {
            context.getAddressPoints(
                referenceLineGeometry(
                        segment(Point(0.0, 0.0), Point(120.0, 50.0), Point(120.0, 60.0), Point(240.0, 110.0))
                    )
                    .copy(id = IntId(2))
            )
        }
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0000..0000+0050, 0000+0060..0000+0110")
    }

    @Test
    fun validationCatchesZigZagAddressPoints() {
        val context =
            simpleGeocodingContext(
                toSegmentPoints(
                    Point(0.0, 0.0),
                    Point(0.0, 10.0),
                    // Reference line makes a bend to right
                    Point(2.5, 15.0),
                    Point(2.5, 25.0),
                )
            )
        val geocode = {
            //  alignment goes straight up at offset -> should get non-continuous points
            context.getAddressPoints(
                referenceLineGeometry(segment(Point(5.0, 5.0), Point(5.0, 25.0))).copy(id = IntId(2))
            )
        }
        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
    }

    @Test
    fun validationReportsSharpAngleSectionsCorrectly() {
        val referenceLinePoints = simpleSphereArc(10.0, PI, 20)
        val context = simpleGeocodingContext(toSegmentPoints(to3DMPoints(referenceLinePoints)))

        val sharpAngleTrack = to3DMPoints<SegmentM>(listOf(Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 0.0)))

        val geocode = {
            context.getAddressPoints(
                referenceLineGeometry(segment(toSegmentPoints(sharpAngleTrack))).copy(id = IntId(2))
            )
        }

        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
        // the correct error range is hard to calculate because it depends on how lines get
        // projected; this at least seems OK though
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0006..0000+0008, 0000+0008..0000+0013")
    }

    @Test
    fun validationCatchesKmPostsInWrongOrder() {
        val error = "$VALIDATION_GEOCODING.km-posts-wrong-order"
        assertContainsError(
            false,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(
                        kmPost(IntId(1), KmNumber(1), kmPostGkLocation(12.0, 0.0), draft = true),
                        kmPost(IntId(1), KmNumber(2), kmPostGkLocation(18.0, 0.0), draft = true),
                    ),
                ),
                TrackNumber("001"),
            ),
            error,
        )

        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(
                        kmPost(IntId(1), KmNumber(2), kmPostGkLocation(18.0, 0.0), draft = true),
                        kmPost(IntId(1), KmNumber(1), kmPostGkLocation(12.0, 0.0), draft = true),
                    ),
                ),
                TrackNumber("001"),
            ),
            error,
        )
    }

    @Test
    fun validationCatchesKmPostOutsideReferenceLine() {
        val error = "$VALIDATION_GEOCODING.km-posts-rejected"
        val kmPostsOutsideLineErrorBefore = "$VALIDATION_GEOCODING.km-post-outside-line-before"
        val kmPostsOutsideLineErrorAfter = "$VALIDATION_GEOCODING.km-post-outside-line-after"
        assertContainsError(
            false,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), kmPostGkLocation(15.0, 0.0), draft = true)),
                ),
                TrackNumber("001"),
            ),
            error,
        )
        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), kmPostGkLocation(5.0, 0.0), draft = true)),
                ),
                TrackNumber("001"),
            ),
            kmPostsOutsideLineErrorBefore,
        )
        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), kmPostGkLocation(25.0, 0.0), draft = true)),
                ),
                TrackNumber("001"),
            ),
            kmPostsOutsideLineErrorAfter,
        )
    }

    @Test
    fun validationCatchesLoopyDuplicate() {
        val lt = locationTrack(IntId(0), duplicateOf = IntId(0), draft = true)
        assertContainsError(
            true,
            validateDuplicateStructure(lt, AlignmentName("duplicateof"), listOf()),
            "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-of-duplicated",
        )
    }

    @Test
    fun `Validation catches chord tracks without both end switches`() {
        val switch = switch(id = IntId(0))
        val trackWithoutSwitches =
            locationTrack(
                IntId(0),
                nameStructure = LocationTrackNameStructure.of(scheme = LocationTrackNamingScheme.CHORD),
                draft = true,
            )
        val trackWithoutEndSwitch = trackWithoutSwitches.copy(startSwitchId = switch.id as IntId)
        val trackWithoutStartSwitch = trackWithoutSwitches.copy(endSwitchId = switch.id)
        val trackWithBothSwitches = trackWithoutSwitches.copy(startSwitchId = switch.id, endSwitchId = switch.id)

        assertContainsError(
            true,
            validateNameMandatedSwitchLinks(trackWithoutSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-name",
        )
        assertContainsError(
            true,
            validateNameMandatedSwitchLinks(trackWithoutStartSwitch),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-name",
        )
        assertContainsError(
            true,
            validateNameMandatedSwitchLinks(trackWithoutEndSwitch),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-name",
        )
        assertEquals(0, validateNameMandatedSwitchLinks(trackWithBothSwitches).size)
    }

    @Test
    fun `Validation catches unshortenable start or end switches`() {
        val switch = switch(name = "Unshortenable", id = IntId(0))
        val switch2 = switch(name = "HKI V0001", id = IntId(1))

        assertContainsError(
            true,
            validateSwitchNameShortenability(switch),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
        assertContainsError(
            false,
            validateSwitchNameShortenability(switch2),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
    }

    @Test
    fun `Switch name shortenability validation is run for tracks with dynamic names`() {
        val switch = switch(name = "Unshortenable", id = IntId(0))
        val baseLocationTrack =
            locationTrack(IntId(0), nameStructure = trackNameStructure("name"), startSwitch = switch.id as IntId)

        assertEquals(0, validateLocationTrackEndSwitchNamingScheme(baseLocationTrack, switch, null).size)
        assertEquals(
            0,
            validateLocationTrackEndSwitchNamingScheme(
                    baseLocationTrack.copy(
                        nameStructure =
                            trackNameStructure(
                                scheme = LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
                                specifier = LocationTrackNameSpecifier.EKR,
                            )
                    ),
                    switch,
                    null,
                )
                .size,
        )
        assertEquals(
            0,
            validateLocationTrackEndSwitchNamingScheme(
                    baseLocationTrack.copy(
                        nameStructure =
                            trackNameStructure(
                                scheme = LocationTrackNamingScheme.WITHIN_OPERATIONAL_POINT,
                                freeText = "name",
                            )
                    ),
                    switch,
                    null,
                )
                .size,
        )
        assertContainsError(
            true,
            validateLocationTrackEndSwitchNamingScheme(
                baseLocationTrack.copy(nameStructure = trackNameStructure(scheme = LocationTrackNamingScheme.CHORD)),
                switch,
                null,
            ),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
        assertContainsError(
            true,
            validateLocationTrackEndSwitchNamingScheme(
                baseLocationTrack.copy(
                    nameStructure = trackNameStructure(scheme = LocationTrackNamingScheme.BETWEEN_OPERATIONAL_POINTS)
                ),
                switch,
                null,
            ),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
    }

    @Test
    fun `Switch name shortenability validation is run for tracks with dynamic descriptions`() {
        val switch = switch(name = "Unshortenable", id = IntId(0))
        val baseLocationTrack =
            locationTrack(
                IntId(0),
                descriptionStructure =
                    trackDescriptionStructure(
                        descriptionBase = "desc",
                        descriptionSuffix = LocationTrackDescriptionSuffix.NONE,
                    ),
                startSwitch = switch.id as IntId,
            )

        assertEquals(0, validateLocationTrackEndSwitchNamingScheme(baseLocationTrack, switch, null).size)
        assertContainsError(
            true,
            validateLocationTrackEndSwitchNamingScheme(
                baseLocationTrack.copy(
                    descriptionStructure =
                        baseLocationTrack.descriptionStructure.copy(
                            suffix = LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH
                        )
                ),
                switch,
                null,
            ),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
        assertContainsError(
            true,
            validateLocationTrackEndSwitchNamingScheme(
                baseLocationTrack.copy(
                    descriptionStructure =
                        baseLocationTrack.descriptionStructure.copy(
                            suffix = LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER
                        )
                ),
                switch,
                null,
            ),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
        assertContainsError(
            true,
            validateLocationTrackEndSwitchNamingScheme(
                baseLocationTrack.copy(
                    descriptionStructure =
                        baseLocationTrack.descriptionStructure.copy(
                            suffix = LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY
                        )
                ),
                switch,
                null,
            ),
            "$VALIDATION_LOCATION_TRACK.switch.unshortenable-name",
        )
    }

    @Test
    fun `Switch name form validation throws if not given proper switches`() {
        val switch = switch(name = "HKI V0001", id = IntId(0))
        val switch2 = switch(name = "HKI V0002", id = IntId(1))
        val locationTrack =
            locationTrack(
                IntId(0),
                descriptionStructure =
                    trackDescriptionStructure(
                        descriptionBase = "desc",
                        descriptionSuffix = LocationTrackDescriptionSuffix.NONE,
                    ),
                startSwitch = switch.id as IntId,
            )

        assertThrows<IllegalArgumentException> {
            validateLocationTrackEndSwitchNamingScheme(locationTrack, switch2, null)
        }
        assertThrows<IllegalArgumentException> {
            validateLocationTrackEndSwitchNamingScheme(locationTrack, switch, switch2)
        }
        assertDoesNotThrow { validateLocationTrackEndSwitchNamingScheme(locationTrack, switch, null) }
    }

    @Test
    fun `Track that ends in ownership boundary accepts exactly one terminus switch`() {
        val switchId = switch(id = IntId(0)).id as IntId
        val trackWithoutSwitches =
            locationTrack(
                IntId(0),
                descriptionStructure =
                    LocationTrackDescriptionStructure(
                        base = LocationTrackDescriptionBase("desc"),
                        suffix = LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY,
                    ),
            )
        val trackWithStartSwitch = trackWithoutSwitches.copy(startSwitchId = switchId)
        val trackWithEndSwitch = trackWithoutSwitches.copy(endSwitchId = switchId)
        val trackWithBothSwitches = trackWithoutSwitches.copy(startSwitchId = switchId, endSwitchId = switchId)

        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithoutSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.missing-one-switch",
        )
        assertEquals(0, validateDescriptionMandatedSwitchLinks(trackWithStartSwitch).size)
        assertEquals(0, validateDescriptionMandatedSwitchLinks(trackWithEndSwitch).size)
        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithBothSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.too-many-switches",
        )
    }

    @Test
    fun `Track that ends in buffer accepts exactly one terminus switch`() {
        val switchId = switch(id = IntId(0)).id as IntId
        val trackWithoutSwitches =
            locationTrack(
                IntId(0),
                descriptionStructure =
                    LocationTrackDescriptionStructure(
                        base = LocationTrackDescriptionBase("desc"),
                        suffix = LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER,
                    ),
            )
        val trackWithStartSwitch = trackWithoutSwitches.copy(startSwitchId = switchId)
        val trackWithEndSwitch = trackWithoutSwitches.copy(endSwitchId = switchId)
        val trackWithBothSwitches = trackWithoutSwitches.copy(startSwitchId = switchId, endSwitchId = switchId)

        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithoutSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.missing-one-switch",
        )
        assertEquals(0, validateDescriptionMandatedSwitchLinks(trackWithStartSwitch).size)
        assertEquals(0, validateDescriptionMandatedSwitchLinks(trackWithEndSwitch).size)
        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithBothSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.too-many-switches",
        )
    }

    @Test
    fun `Track that ends in switches at both ends requires switches at both ends`() {
        val switchId = switch(id = IntId(0)).id as IntId
        val trackWithoutSwitches =
            locationTrack(
                IntId(0),
                descriptionStructure =
                    LocationTrackDescriptionStructure(
                        base = LocationTrackDescriptionBase("desc"),
                        suffix = LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH,
                    ),
            )
        val trackWithStartSwitch = trackWithoutSwitches.copy(startSwitchId = switchId)
        val trackWithEndSwitch = trackWithoutSwitches.copy(endSwitchId = switchId)
        val trackWithBothSwitches = trackWithoutSwitches.copy(startSwitchId = switchId, endSwitchId = switchId)

        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithoutSwitches),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-description",
        )
        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithStartSwitch),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-description",
        )
        assertContainsError(
            true,
            validateDescriptionMandatedSwitchLinks(trackWithEndSwitch),
            "$VALIDATION_LOCATION_TRACK.switch.missing-both-switches-description",
        )
        assertEquals(0, validateDescriptionMandatedSwitchLinks(trackWithBothSwitches).size)
    }

    @Test
    fun validationCatchesMisplacedTopologyLink() {
        val wrongPlaceSwitch =
            switch(
                stateCategory = EXISTING,
                joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(100.0, 100.0), null)),
                id = IntId(1),
                draft = true,
            )
        val rightPlaceSwitch =
            switch(
                stateCategory = EXISTING,
                joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(200.0, 200.0), null)),
                id = IntId(2),
                draft = true,
            )
        val lt =
            locationTrack(IntId(0), draft = true, state = LocationTrackState.IN_USE) to
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(wrongPlaceSwitch.id as IntId, 1),
                        endOuterSwitch = switchLinkYV(rightPlaceSwitch.id as IntId, 1),
                        segments = listOf(segment(Point(150.0, 150.0), Point(200.0, 200.0))),
                    )
                )

        assertContainsError(
            true,
            validateSwitchLocationTrackLinkStructure(wrongPlaceSwitch, switchStructureYV60_300_1_9(), listOf(lt)),
            "$VALIDATION_SWITCH.location-track.joint-location-mismatch",
        )

        assertContainsError(
            false,
            validateSwitchLocationTrackLinkStructure(rightPlaceSwitch, switchStructureYV60_300_1_9(), listOf(lt)),
            "$VALIDATION_SWITCH.location-track.joint-location-mismatch",
        )
    }

    @Test
    fun `should return validation warning if location track end is linked to switch a but connectivity type is set to NONE`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.NONE,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-is-topologically-connected")
    }

    @Test
    fun `should return validation warnings if location track start is not linked to a switch but connectivity type is set to START`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(2, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-missing")
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-is-topologically-connected")
    }

    @Test
    fun `should not return any validation warnings if location track end is linked to a switch and connectivity type is set to END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(0, connectivityWarnings.size)
    }

    @Test
    fun `should return validation warning if only location track end is linked to a switch but connectivity type is set to START AND END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-missing")
    }

    @Test
    fun `should return validation warning if location track start is linked to a switch but connectivity type is set to NONE`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.NONE,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-is-topologically-connected")
    }

    @Test
    fun `should not return validation warning if location track start is linked to a switch and connectivity type is set to START`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(0, connectivityWarnings.size)
    }

    @Test
    fun `should return validation warnings if location start is linked to a switch but connectivity type is set to END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(2, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-is-topologically-connected")
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-missing")
    }

    @Test
    fun `should return validation warning if location start is linked to a switch but connectivity type is set to START AND END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-missing")
    }

    @Test
    fun `should return validation warning if location track end is topologically connected but connectivity type is set to NONE`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.NONE,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-is-topologically-connected")
    }

    @Test
    fun `should return validation warnings if location track end is topologically connected but connectivity type is set to START`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    trackNumberId = IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(2, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-missing")
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-is-topologically-connected")
    }

    @Test
    fun `should not return any validation warnings if location track end is topologically connected and connectivity type is set to END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(IntId(100), topologicalConnectivity = TopologicalConnectivityType.END, draft = true),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(0, connectivityWarnings.size)
    }

    @Test
    fun `should return validation warning if location track end is topologically connected but connectivity type is set to START AND END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-missing")
    }

    @Test
    fun `should return validation warning if location track start is topologically connected but connectivity type is set to NONE`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(IntId(100), topologicalConnectivity = TopologicalConnectivityType.NONE, draft = true),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-is-topologically-connected")
    }

    @Test
    fun `should not return any validation warnings if location track start is topologically connected and connectivity type is set to START`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(IntId(100), topologicalConnectivity = TopologicalConnectivityType.START, draft = true),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(0, connectivityWarnings.size)
    }

    @Test
    fun `should return validation warnings if location track start is topologically connected but connectivity type is set to END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(IntId(100), topologicalConnectivity = TopologicalConnectivityType.END, draft = true),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(2, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "start-switch-is-topologically-connected")
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-missing")
    }

    @Test
    fun `should return validation warning if location start is topologically connceted but connectivity type is set to START AND END`() {
        val connectivityWarnings =
            validateLocationTrackSwitchConnectivity(
                locationTrack(
                    IntId(100),
                    topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                    draft = true,
                ),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(IntId(100), 1),
                        segments =
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 1.0)), segment(Point(0.0, 1.0), Point(0.0, 2.0))),
                    )
                ),
            )

        assertEquals(1, connectivityWarnings.size)
        assertContainsConnectivityWarning(connectivityWarnings, "end-switch-missing")
    }

    @Test
    fun `should give validation error if edge inner joints aren't consistent`() {
        assertEquals(
            listOf(
                validationWarning(
                    "validation.layout.location-track.edge-switch-partial",
                    localizationParams("switch" to IntId<LayoutSwitch>(1)),
                    inRelationTo =
                        setOf(PublicationLogAsset(id = IntId<LayoutSwitch>(1), type = PublicationLogAssetType.SWITCH)),
                )
            ),
            validateEdges(
                geometry =
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 2.0))),
                            startInnerSwitch = switchLinkYV(IntId(1), 1),
                        )
                    ),
                getSwitchName = { id -> SwitchName("$id") },
            ),
        )
        assertEquals(
            listOf(
                validationWarning(
                    "validation.layout.location-track.edge-switch-partial",
                    localizationParams("switch" to IntId<LayoutSwitch>(1)),
                    inRelationTo =
                        setOf(PublicationLogAsset(id = IntId<LayoutSwitch>(1), type = PublicationLogAssetType.SWITCH)),
                ),
                validationWarning(
                    "validation.layout.location-track.edge-switch-partial",
                    localizationParams("switch" to IntId<LayoutSwitch>(2)),
                    inRelationTo =
                        setOf(PublicationLogAsset(id = IntId<LayoutSwitch>(2), type = PublicationLogAssetType.SWITCH)),
                ),
            ),
            validateEdges(
                geometry =
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 2.0))),
                            startInnerSwitch = switchLinkYV(IntId(1), 1),
                            endInnerSwitch = switchLinkYV(IntId(2), 1),
                        )
                    ),
                getSwitchName = { id -> SwitchName("$id") },
            ),
        )
    }

    @Test
    fun `should not give validation error if edge inner joints are consistent`() {
        assertEquals(
            emptyList(),
            validateEdges(
                geometry = trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(0.0, 2.0))))),
                getSwitchName = { id -> SwitchName("$id") },
            ),
        )
        assertEquals(
            emptyList(),
            validateEdges(
                geometry =
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.0), Point(0.0, 2.0))),
                            startInnerSwitch = switchLinkYV(IntId(1), 1),
                            endInnerSwitch = switchLinkYV(IntId(1), 2),
                        )
                    ),
                getSwitchName = { id -> SwitchName("$id") },
            ),
        )
    }

    @Test
    fun `multiple outer links without inner links are reported`() {
        val switchId: IntId<LayoutSwitch> = IntId(1)
        val switchName = SwitchName.ofUnsafe("ABC V123")

        val tracks =
            listOf(
                // tracks to connect the switch as usual
                locationTrack(IntId(1)) to
                    BuildTrackTopology()
                        .edge(endOuterSwitch = switchLinkYV(switchId, 1))
                        .edge(startInnerSwitch = switchLinkYV(switchId, 1), endInnerSwitch = switchLinkYV(switchId, 2))
                        .build(),
                locationTrack(IntId(1)) to
                    BuildTrackTopology()
                        .edge(startInnerSwitch = switchLinkYV(switchId, 1), endInnerSwitch = switchLinkYV(switchId, 3))
                        .build(),
                // branching tracks
                locationTrack(IntId(1), name = "brancher 1") to
                    BuildTrackTopology().edge(endOuterSwitch = switchLinkYV(switchId, 3)).build(),
                locationTrack(IntId(1), name = "brancher 2") to
                    BuildTrackTopology().edge(endOuterSwitch = switchLinkYV(switchId, 3)).build(),
            )
        val validation =
            validateSwitchAlignmentTopology(switchId, switchStructureYV60_300_1_9(), tracks, switchName, null)
        assertContains(
            validation,
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.switch.track-linkage.multiple-outer-without-inner-links",
                mapOf("switch" to "ABC V123", "joint" to 3, "locationTracks" to "brancher 1, brancher 2"),
            ),
        )
    }

    @Test
    fun `multiple outer links without inner links are ok if marked duplicate`() {
        val switchId: IntId<LayoutSwitch> = IntId(1)
        val switchName = SwitchName.ofUnsafe("ABC V123")

        val tracks =
            listOf(
                // tracks to connect the switch as usual
                locationTrack(IntId(1)) to
                    BuildTrackTopology()
                        .edge(endOuterSwitch = switchLinkYV(switchId, 1))
                        .edge(startInnerSwitch = switchLinkYV(switchId, 1), endInnerSwitch = switchLinkYV(switchId, 2))
                        .build(),
                locationTrack(IntId(1)) to
                    BuildTrackTopology()
                        .edge(startInnerSwitch = switchLinkYV(switchId, 1), endInnerSwitch = switchLinkYV(switchId, 3))
                        .build(),
                // branching tracks
                locationTrack(IntId(1)) to
                    BuildTrackTopology().edge(endOuterSwitch = switchLinkYV(switchId, 3)).build(),
                locationTrack(IntId(1), duplicateOf = IntId(123)) to
                    BuildTrackTopology().edge(endOuterSwitch = switchLinkYV(switchId, 3)).build(),
            )
        assertEquals(
            listOf(),
            validateSwitchAlignmentTopology(switchId, switchStructureYV60_300_1_9(), tracks, switchName, null),
        )
    }

    private fun assertContainsConnectivityWarning(warnings: Collection<LayoutValidationIssue>, translationKey: String) {
        assertContains(
            warnings,
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.location-track.topological-connectivity.$translationKey",
            ),
        )
    }

    private fun switchLinking(
        switchStateCategory: LayoutStateCategory = EXISTING,
        switchDraft: Boolean = false,
        switchInPublication: Boolean = true,
    ): SwitchTrackLinking {
        val switch =
            switch(
                id = if (switchDraft) IntId(2) else IntId(1),
                stateCategory = switchStateCategory,
                draft = switchDraft,
                joints =
                    listOf(
                        LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 10.0), null),
                        LayoutSwitchJoint(JointNumber(2), SwitchJointRole.CONNECTION, Point(20.0, 20.0), null),
                    ),
            )
        val joint1 = switch.joints.first()
        val joint2 = switch.joints.last()
        val switchInContext = if (!switchDraft || switchInPublication) switch else null
        return SwitchTrackLinking(
            switchId = switch.id as IntId,
            switchName = switch.name,
            switch = switchInContext,
            switchStructure = structure,
            indexedLinks = listOf(0 to toTrackSwitchLink(switch, joint1), 1 to toTrackSwitchLink(switch, joint2)),
        )
    }

    private fun toTrackSwitchLink(switch: LayoutSwitch, joint: LayoutSwitchJoint) =
        TrackSwitchLink(
            SwitchLink(switch.id as IntId, joint.number, switchStructureYV60_300_1_9()),
            toAlignmentPoint(joint.location),
            TrackSwitchLinkType.INNER,
        )

    private fun toAlignmentPoint(point: Point, m: Double = 0.0) =
        AlignmentPoint<LocationTrackM>(point.x, point.y, null, LineM(m), null)

    private fun assertLocationTrackFieldError(hasError: Boolean, geometry: LocationTrackGeometry, error: String) =
        assertContainsError(hasError, validateLocationTrackGeometry(geometry), error)

    private fun assertTrackNumberReferenceError(
        hasError: Boolean,
        trackNumber: LayoutTrackNumber,
        referenceLine: ReferenceLine?,
        locationTrack: LocationTrack,
        error: String,
    ) =
        assertTrackNumberReferenceError(
            hasError,
            trackNumber,
            referenceLine,
            error,
            locationTracks = listOf(locationTrack),
        )

    private fun assertTrackNumberReferenceError(
        hasError: Boolean,
        trackNumber: LayoutTrackNumber,
        referenceLine: ReferenceLine?,
        error: String,
        kmPosts: List<LayoutKmPost> = listOf(),
        locationTracks: List<LocationTrack> = listOf(),
    ) =
        assertContainsError(
            hasError,
            validateReferencesToTrackNumber(
                if (trackNumber.exists) AssetLivenessType.EXISTS else AssetLivenessType.DELETED,
                referenceLine,
                kmPosts,
                locationTracks,
            ),
            error,
        )

    private fun assertKmPostReferenceError(
        hasError: Boolean,
        kmPost: LayoutKmPost,
        trackNumber: LayoutTrackNumber?,
        trackNumberNumber: TrackNumber,
        error: String,
    ) =
        assertContainsError(
            hasError,
            validateKmPostReferences(
                kmPost,
                AssetLiveness(
                    trackNumberNumber.toString(),
                    if (trackNumber == null) AssetLivenessType.DRAFT_NOT_PUBLISHED
                    else if (trackNumber.exists) AssetLivenessType.EXISTS else AssetLivenessType.DELETED,
                ),
            ),
            error,
        )

    private fun assertLocationTrackToSwitchReferenceError(
        hasError: Boolean,
        switchLivenesses: List<AssetLiveness<LayoutSwitch>>,
        error: String,
        locationTrack: LocationTrack = locationTrack(IntId(1), draft = true),
    ) =
        assertContainsError(
            hasError,
            validateReferencesFromLocationTrack(
                trackNumber = AssetLiveness("tracknum", AssetLivenessType.EXISTS),
                switches = switchLivenesses,
                operationalPoints = listOf(),
                duplicateOf = null,
                locationTrack = locationTrack,
            ),
            error,
        )

    private fun assertSegmentSwitchError(
        hasError: Boolean,
        segmentAndSwitch: SwitchTrackLinking,
        error: String,
        locationTrack: LocationTrack = locationTrack(IntId(1), draft = true),
    ) =
        assertContainsError(
            hasError,
            validateTrackSwitchLinkingGeometry(locationTrack, listOf(segmentAndSwitch)),
            error,
        )

    private fun assertSwitchSegmentStructureError(
        hasError: Boolean,
        switch: LayoutSwitch,
        track: Pair<LocationTrack, LocationTrackGeometry>,
        error: String,
    ) = assertSwitchSegmentStructureError(hasError, switch, listOf(track), error)

    private fun assertSwitchSegmentStructureError(
        hasError: Boolean,
        switch: LayoutSwitch,
        tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
        error: String,
    ) = assertContainsError(hasError, getSwitchSegmentStructureErrors(switch, tracks), error)

    private fun getSwitchSegmentStructureErrors(
        switch: LayoutSwitch,
        tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    ): List<LayoutValidationIssue> = validateSwitchLocationTrackLinkStructure(switch, structure, tracks)

    private fun <M : AlignmentM<M>> assertAddressPointError(
        hasError: Boolean,
        geocode: () -> AddressPointsResult<M>?,
        error: String,
    ) =
        assertContainsError(
            hasError,
            validateAddressPoints(
                trackNumber(draft = true),
                locationTrack(IntId(1), draft = true),
                VALIDATION_GEOCODING,
                geocode,
            ),
            error,
        )

    private fun assertSingleAddressPointErrorRangeDescription(
        geocode: () -> AddressPointsResult<ReferenceLineM>?,
        errorRangeDescription: String,
    ) {
        val errors =
            validateAddressPoints(trackNumber(draft = true), locationTrack(IntId(1), draft = true), "", geocode)
        assertEquals(errorRangeDescription, errors[0].params.get("kmNumbers"))
    }

    private fun assertContainsError(contains: Boolean, errors: List<LayoutValidationIssue>, error: String) {
        val message = "Expected to ${if (contains) "have" else "not have"} error: expected=$error actual=$errors"
        assertEquals(contains, errors.any { e -> e.localizationKey == LocalizationKey.of(error) }, message)
    }

    private fun simpleGeocodingContext(referenceLinePoints: List<SegmentPoint>): GeocodingContext<ReferenceLineM> =
        geocodingContext(referenceLinePoints, listOf()).geocodingContext

    private fun geocodingContext(
        referenceLinePoints: List<SegmentPoint>,
        kmPosts: List<LayoutKmPost>,
    ): ValidatedGeocodingContext<ReferenceLineM> {
        val (referenceLine, alignment) =
            referenceLineAndGeometry(
                trackNumberId = IntId(1),
                segments = listOf(segment(referenceLinePoints)),
                startAddress = TrackMeter.ZERO,
                draft = true,
            )
        return GeocodingContext.create(
            // Start the geocoding from 0+0m
            TrackNumber("0000"),
            referenceLine.startAddress,
            alignment,
            kmPosts,
        )
    }

    private fun simpleSphereArc(radius: Double, arcLength: Double, numPoints: Int): List<Point> =
        (0..numPoints).map { pointIndex ->
            pointInDirection(radius, pointIndex.toDouble() / numPoints.toDouble() * arcLength)
        }
}
