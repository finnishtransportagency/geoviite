package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.NOT_EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
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
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.offsetGeometry
import fi.fta.geoviite.infra.tracklayout.rawPoints
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchAndMatchingAlignments
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.to3DMPoints
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = LayoutState.DELETED),
            referenceLine,
            alignment.copy(state = LocationTrackState.DELETED),
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = LayoutState.IN_USE),
            referenceLine,
            alignment.copy(state = LocationTrackState.IN_USE),
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
    }

    @Test
    fun `Km-post validation catches un-published track number or reference line`() {
        val trackNumberId = IntId<LayoutTrackNumber>(1)
        val referenceLine = referenceLine(trackNumberId = IntId(1), id = IntId(1), draft = false)
        val kmPost = kmPost(trackNumberId, KmNumber(1), draft = true)
        val trackNumber = trackNumber(id = IntId(2), draft = false)
        assertKmPostReferenceError(
            true,
            kmPost,
            null,
            referenceLine,
            trackNumber.number,
            "$VALIDATION_KM_POST.track-number.not-published",
        )
        assertKmPostReferenceError(
            true,
            kmPost,
            trackNumber,
            null,
            trackNumber.number,
            "$VALIDATION_KM_POST.reference-line.not-published",
        )
        assertKmPostReferenceError(
            false,
            kmPost,
            trackNumber,
            referenceLine,
            trackNumber.number,
            "$VALIDATION_KM_POST.track-number.not-published",
        )
        assertKmPostReferenceError(
            false,
            kmPost,
            trackNumber,
            referenceLine,
            trackNumber.number,
            "$VALIDATION_KM_POST.reference-line.not-published",
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
        assertSegmentSwitchError(
            false,
            switchLinking(switchDraft = false, switchInPublication = false),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1), draft = false),
        )
        assertSegmentSwitchError(
            false,
            switchLinking(EXISTING, switchDraft = true, switchInPublication = true),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1), draft = false),
        )
        assertSegmentSwitchError(
            true,
            switchLinking(EXISTING, switchDraft = true, switchInPublication = false),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1), draft = false),
        )
    }

    @Test
    fun validationCatchesReferencingDeletedSwitch() {
        assertSegmentSwitchError(
            false,
            switchLinking(switchStateCategory = EXISTING),
            "$VALIDATION_LOCATION_TRACK.switch.state-category.EXISTING",
            locationTrack(IntId(1), draft = false),
        )
        assertSegmentSwitchError(
            true,
            switchLinking(switchStateCategory = NOT_EXISTING),
            "$VALIDATION_LOCATION_TRACK.switch.state-category.NOT_EXISTING",
            locationTrack(IntId(1), draft = false),
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
    fun validationCatchesInvalidGeocodingContext() {
        assertAddressPointError(true, { null }, "$VALIDATION_GEOCODING.no-context")
    }

    @Test
    fun validationOkForNormalAlignmentGeocoding() {
        // Reference line: straight up from origin, 1km
        val context = simpleGeocodingContext(rawPoints(100, 0.0, 0.0, 0.0, 1000.0))
        assertEquals(
            listOf(),
            validateAddressPoints(
                trackNumber(draft = true),
                locationTrack(trackNumberId = IntId(1), draft = true),
                "",
            ) {
                // Alignment at slight angle to reference line -> should be OK
                context.getAddressPoints(alignment(segment(Point(10.0, 10.0), Point(20.0, 100.0))).copy(id = IntId(2)))
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
                alignment(
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
                alignment(segment(Point(0.0, 0.0), Point(120.0, 50.0), Point(120.0, 60.0), Point(240.0, 110.0)))
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
            context.getAddressPoints(alignment(segment(Point(5.0, 5.0), Point(5.0, 25.0))).copy(id = IntId(2)))
        }
        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
    }

    @Test
    fun validationReportsSharpAngleSectionsCorrectly() {
        val referenceLinePoints = simpleSphereArc(10.0, PI, 20)
        val context = simpleGeocodingContext(toSegmentPoints(to3DMPoints(referenceLinePoints)))

        val sharpAngleTrack = to3DMPoints<SegmentM>(listOf(Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 0.0)))

        val geocode = {
            context.getAddressPoints(alignment(segment(toSegmentPoints(sharpAngleTrack))).copy(id = IntId(2)))
        }

        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
        // the correct error range is hard to calculate because it depends on how lines get
        // projected; this at least seems OK though
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0006..0000+0008, 0000+0008..0000+0013")
    }

    @Test
    fun validationCatchesKmPostsInWrongOrder() {
        val error = "$VALIDATION_GEOCODING.km-posts-invalid"
        assertContainsError(
            false,
            validateGeocodingContext(
                geocodingContext(
                    toSegmentPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(
                        kmPost(IntId(1), KmNumber(1), Point(12.0, 0.0), draft = true),
                        kmPost(IntId(1), KmNumber(2), Point(18.0, 0.0), draft = true),
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
                        kmPost(IntId(1), KmNumber(2), Point(18.0, 0.0), draft = true),
                        kmPost(IntId(1), KmNumber(1), Point(12.0, 0.0), draft = true),
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
                    listOf(kmPost(IntId(1), KmNumber(1), Point(15.0, 0.0), draft = true)),
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
                    listOf(kmPost(IntId(1), KmNumber(1), Point(5.0, 0.0), draft = true)),
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
                    listOf(kmPost(IntId(1), KmNumber(1), Point(25.0, 0.0), draft = true)),
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
            validateDuplicateOfState(lt, lt, AlignmentName("duplicateof"), false, listOf()),
            "$VALIDATION_LOCATION_TRACK.duplicate-of.publishing-duplicate-of-duplicated",
        )
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
        return SwitchTrackLinking(
            switchId = switch.id as IntId,
            switchName = switch.name,
            switch = if (!switchDraft || switchInPublication) switch else null,
            switchStructure = structure,
            indexedLinks = listOf(0 to toTrackSwitchLink(switch, joint1), 1 to toTrackSwitchLink(switch, joint2)),
            switchIsCancelled = false,
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
            validateTrackNumberReferences(
                trackNumberExists = trackNumber.exists,
                trackNumberIsCancelled = trackNumber.isCancelled,
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
        referenceLine: ReferenceLine?,
        trackNumberNumber: TrackNumber,
        error: String,
    ) =
        assertContainsError(
            hasError,
            validateKmPostReferences(
                kmPost,
                trackNumber,
                referenceLine,
                trackNumberNumber,
                trackNumberIsCancelled = false,
            ),
            error,
        )

    private fun assertSegmentSwitchError(
        hasError: Boolean,
        segmentAndSwitch: SwitchTrackLinking,
        error: String,
        locationTrack: LocationTrack = locationTrack(IntId(1), draft = true),
    ) = assertContainsError(hasError, validateTrackSwitchReferences(locationTrack, listOf(segmentAndSwitch)), error)

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

    private fun <M: AlignmentM<M>> assertAddressPointError(hasError: Boolean, geocode: () -> AlignmentAddresses<M>?, error: String) {
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
    }

    private fun assertSingleAddressPointErrorRangeDescription(
        geocode: () -> AlignmentAddresses<ReferenceLineM>?,
        errorRangeDescription: String,
    ) {
        val errors =
            validateAddressPoints(trackNumber(draft = true), locationTrack(IntId(1), draft = true), "", geocode)
        assertEquals(errorRangeDescription, errors[0].params.get("kmNumbers"))
    }

    private fun assertContainsError(contains: Boolean, errors: List<LayoutValidationIssue>, error: String) {
        val message = "Expected to ${if (contains) "have" else "not have"} error: expected=$error actual=$errors"
        assertEquals(contains, errors.any { e -> e.localizationKey == LocalizationKey(error) }, message)
    }

    private fun simpleGeocodingContext(referenceLinePoints: List<SegmentPoint>): GeocodingContext<ReferenceLineM> =
        geocodingContext(referenceLinePoints, listOf()).geocodingContext

    private fun geocodingContext(
        referenceLinePoints: List<SegmentPoint>,
        kmPosts: List<LayoutKmPost>,
    ): GeocodingContextCreateResult<ReferenceLineM> {
        val (referenceLine, alignment) =
            referenceLineAndAlignment(
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
