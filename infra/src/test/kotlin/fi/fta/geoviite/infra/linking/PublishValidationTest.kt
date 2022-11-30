package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import fi.fta.geoviite.infra.util.LocalizationKey
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.test.assertEquals

class PublishValidationTest {

    private val structure = switchStructureYV60_300_1_9()

    @Test
    fun trackNumberFieldValidationCatchesCatchesPublishingOfficial() {
        assertFieldError(true, trackNumber().copy(draft = null), "$VALIDATION_TRACK_NUMBER.not-draft")
        assertFieldError(false, draft(trackNumber()), "$VALIDATION_TRACK_NUMBER.not-draft")
    }

    @Test
    fun trackNumberFieldValidationCatchesCatchesPublishingPlanned() {
        assertFieldError(
            true,
            trackNumber().copy(state = PLANNED),
            "$VALIDATION_TRACK_NUMBER.state.PLANNED",
        )
        assertFieldError(
            false,
            trackNumber().copy(state = IN_USE),
            "$VALIDATION_TRACK_NUMBER.state.PLANNED",
        )
    }

    @Test
    fun trackNumberValidationCatchesAlignmentReferencingDeletedTrackNumber() {
        val trackNumber = trackNumber().copy(id = IntId(1))
        val alignment = locationTrack(IntId(0)).copy(trackNumberId = IntId(1))
        assertTrackNumberReferenceError(
            true,
            trackNumber.copy(state = DELETED),
            locationTrack(IntId(0)).copy(state = IN_USE),
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = DELETED),
            alignment.copy(state = DELETED),
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber.copy(state = IN_USE),
            alignment.copy(state = IN_USE),
            "$VALIDATION_TRACK_NUMBER.location-track.reference-deleted",
        )
    }

    @Test
    fun trackNumberValidationCatchesUnpublishedAlignment() {
        val trackNumber = trackNumber().copy(id = IntId(1))
        val unpublished = locationTrack(IntId(0)).copy(draft = Draft(IntId(2)), id = IntId(1))
        val published = locationTrack(IntId(0)).copy(draft = null, id = IntId(1))
        assertTrackNumberReferenceError(
            true,
            trackNumber,
            unpublished,
            "$VALIDATION_TRACK_NUMBER.location-track.not-published",
            includeAlignmentInPublish = false,
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber,
            published,
            "$VALIDATION_TRACK_NUMBER.location-track.not-published",
            includeAlignmentInPublish = false,
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber,
            unpublished,
            "$VALIDATION_TRACK_NUMBER.location-track.not-published",
            includeAlignmentInPublish = true,
        )
    }

    @Test
    fun trackNumberValidationCatchesUnpublishedKmPost() {
        val trackNumber = trackNumber().copy(id = IntId(1))
        val unpublished = kmPost(trackNumber.id as IntId, KmNumber(1))
            .copy(draft = Draft(IntId(2)), id = IntId(1))
        val published = kmPost(trackNumber.id as IntId, KmNumber(1))
            .copy(draft = null, id = IntId(1))
        assertTrackNumberReferenceError(
            true,
            trackNumber,
            unpublished,
            "$VALIDATION_TRACK_NUMBER.km-post.not-published",
            includeKmPostInPublish = false,
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber,
            published,
            "$VALIDATION_TRACK_NUMBER.km-post.not-published",
            includeKmPostInPublish = false,
        )
        assertTrackNumberReferenceError(
            false,
            trackNumber,
            unpublished,
            "$VALIDATION_TRACK_NUMBER.km-post.not-published",
            includeKmPostInPublish = true,
        )
    }

    @Test
    fun kmPostFieldValidationCatchesCatchesPublishingOfficial() {
        val someKmPost = kmPost(IntId(1), KmNumber(1))
        assertFieldError(true, someKmPost.copy(draft = null), "$VALIDATION_KM_POST.not-draft")
        assertFieldError(false, draft(someKmPost), "$VALIDATION_KM_POST.not-draft")
    }

    @Test
    fun kmPostFieldValidationCatchesCatchesPublishingPlanned() {
        val someKmPost = kmPost(IntId(1), KmNumber(1))
        assertFieldError(true, someKmPost.copy(state = PLANNED), "$VALIDATION_KM_POST.state.PLANNED")
        assertFieldError(false, someKmPost.copy(state = IN_USE), "$VALIDATION_KM_POST.state.PLANNED")
    }

    @Test
    fun kmPostValidationCatchesUnpublishedTrackNumber() {
        val trackNumberId = IntId<TrackLayoutTrackNumber>(1)
        val kmPost = kmPost(trackNumberId, KmNumber(1))
        val unpublished = trackNumber().copy(draft = Draft(IntId(2)), id = trackNumberId)
        val published = trackNumber().copy(draft = null, id = trackNumberId)
        assertKmPostReferenceError(
            true,
            kmPost,
            unpublished,
            "$VALIDATION_KM_POST.track-number.not-published",
            includeTrackNumberInPublish = false,
        )
        assertKmPostReferenceError(
            false,
            kmPost,
            published,
            "$VALIDATION_KM_POST.track-number.not-published",
            includeTrackNumberInPublish = false,
        )
        assertKmPostReferenceError(
            false,
            kmPost,
            unpublished,
            "$VALIDATION_KM_POST.track-number.not-published",
            includeTrackNumberInPublish = true,
        )
    }

    @Test
    fun switchFieldValidationCatchesCatchesPublishingOfficial() {
        assertFieldError(true, switch().copy(draft = null), "$VALIDATION_SWITCH.not-draft")
        assertFieldError(false, draft(switch()), "$VALIDATION_SWITCH.not-draft")
    }

    @Test
    fun switchFieldValidationCatchesCatchesPublishingPlanned() {
        assertFieldError(
            true,
            switch().copy(stateCategory = LayoutStateCategory.FUTURE_EXISTING),
            "$VALIDATION_SWITCH.state-category.FUTURE_EXISTING",
        )
        assertFieldError(
            false,
            switch().copy(stateCategory = LayoutStateCategory.EXISTING),
            "$VALIDATION_SWITCH.state-category.EXISTING",
        )
    }

    @Test
    fun switchValidationCatchesUnPublishedAlignment() {
        val switch = switch(structureId = structure.id as IntId)
        val unpublished = locationTrackAndAlignment(
            trackNumberId = IntId(0),
            segments = listOf(someSegment()),
            draft = Draft(IntId(2)),
            id = IntId(1),
        ).first
        val published = locationTrackAndAlignment(
            trackNumberId = IntId(0),
            segments = listOf(someSegment()),
            draft = null,
            id = IntId(1),
        ).first
        assertSwitchSegmentError(
            false,
            switch,
            published,
            "$VALIDATION_SWITCH.location-track.not-published"
        )
        assertSwitchSegmentError(
            false,
            switch,
            unpublished,
            "$VALIDATION_SWITCH.location-track.not-published",
            includeTracksInPublish = true,
        )
        assertSwitchSegmentError(
            true,
            switch,
            unpublished,
            "$VALIDATION_SWITCH.location-track.not-published",
        )
    }

    @Test
    fun switchValidationCatchesNonContinuousAlignment() {
        val switch = switch(structureId = structure.id as IntId).copy(id = IntId(1))
        val good = locationTrackAndAlignment(
            IntId(0),
            segment(Point(0.0, 0.0), Point(10.0, 10.0))
                .copy(switchId = switch.id, endJointNumber = switch.joints.last().number),
            segment(Point(10.0, 10.0), Point(20.0, 20.0))
                .copy(switchId = switch.id, startJointNumber = switch.joints.first().number),
            segment(Point(20.0, 20.0), Point(30.0, 30.0)),
        )
        val broken = locationTrackAndAlignment(
            IntId(0),
            segment(Point(0.0, 0.0), Point(10.0, 10.0))
                .copy(switchId = switch.id, endJointNumber = switch.joints.last().number),
            segment(Point(10.0, 10.0), Point(20.0, 20.0)),
            segment(Point(20.0, 20.0), Point(30.0, 30.0))
                .copy(switchId = switch.id, startJointNumber = switch.joints.first().number),
        )

        assertSwitchSegmentStructureError(
            false,
            switch,
            good,
            "$VALIDATION_SWITCH.location-track.not-continuous",
        )
        assertSwitchSegmentStructureError(
            true,
            switch,
            broken,
            "$VALIDATION_SWITCH.location-track.not-continuous",
        )
    }

    @Test
    fun switchValidationCatchesLocationMismatch() {
        val (switch, alignments) = switchAndMatchingAlignments(IntId(0), structure)
        val broken = alignments.mapIndexed { index, (track, alignment) ->
            if (index == 0) track to offsetAlignment(alignment, Point(5.0, 0.0))
            else track to alignment
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
        val (switch, alignments) = switchAndMatchingAlignments(IntId(0), structure)
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
            error = "$VALIDATION_SWITCH.location-track.unlinked",
        )
    }

    @Test
    fun alignmentFieldValidationCatchesPublishingOfficial() {
        assertFieldError(
            true,
            locationTrack(IntId(0)).copy(draft = null),
            "$VALIDATION_LOCATION_TRACK.not-draft",
        )
        assertFieldError(
            false,
            draft(locationTrack(IntId(0))),
            "$VALIDATION_LOCATION_TRACK.not-draft",
        )
    }

    @Test
    fun alignmentFieldValidationCatchesLackingGeometry() {
        assertLocationTrackFieldError(
            true,
            alignment(listOf()),
            "$VALIDATION_LOCATION_TRACK.empty-segments",
        )
        assertLocationTrackFieldError(
            false,
            alignment(someSegment()),
            "$VALIDATION_LOCATION_TRACK.empty-segments",
        )
    }

    @Test
    fun alignmentFieldValidationCatchesPublishingPlanned() {
        assertFieldError(
            true,
            locationTrack(IntId(0)).copy(state = PLANNED),
            "$VALIDATION_LOCATION_TRACK.state.PLANNED",
        )
        assertFieldError(
            false,
            locationTrack(IntId(0)).copy(state = IN_USE),
            "$VALIDATION_LOCATION_TRACK.state.PLANNED",
        )
    }

    @Test
    fun validationCatchesReferencingDraftRow() {
        val segmentSwitch = draftSegmentSwitchPair(switchDraft = true)
        assertSegmentSwitchError(
            false,
            segmentSwitch,
            "$VALIDATION_LOCATION_TRACK.switch.not-official",
        )
        assertSegmentSwitchError(
            true,
            editSegment(segmentSwitch) { segment ->
                segment.copy(switchId = segmentSwitch.switch.draft!!.draftRowId)
            },
            "$VALIDATION_LOCATION_TRACK.switch.not-official",
        )
    }

    @Test
    fun validationCatchesUnPublishedSwitch() {
        assertSegmentSwitchError(
            false,
            draftSegmentSwitchPair(switchDraft = false),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1)).copy(draft = null),
            false,
        )
        assertSegmentSwitchError(
            false,
            draftSegmentSwitchPair(switchDraft = true),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1)).copy(draft = null),
            true,
        )
        assertSegmentSwitchError(
            true,
            draftSegmentSwitchPair(switchDraft = true),
            "$VALIDATION_LOCATION_TRACK.switch.not-published",
            locationTrack(IntId(1)).copy(draft = null),
            false,
        )
    }


    @Test
    fun validationCatchesReferencingDeletedSwitch() {
        assertSegmentSwitchError(
            false,
            draftSegmentSwitchPair(switchStateCategory = LayoutStateCategory.EXISTING),
            "$VALIDATION_LOCATION_TRACK.switch.state-category.EXISTING",
            locationTrack(IntId(1)).copy(draft = null),
        )
        assertSegmentSwitchError(
            true,
            draftSegmentSwitchPair(switchStateCategory = LayoutStateCategory.NOT_EXISTING),
            "$VALIDATION_LOCATION_TRACK.switch.state-category.NOT_EXISTING",
            locationTrack(IntId(1)).copy(draft = null),
        )
    }

    @Test
    fun validationCatchesSegmentSwitchLocationMismatch() {
        val segmentSwitch = draftSegmentSwitchPair()
        assertSegmentSwitchError(
            false,
            segmentSwitch,
            "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch",
        )
        assertSegmentSwitchError(
            true,
            editSegment(segmentSwitch) { segment ->
                segment.copy(
                    points = toTrackLayoutPoints(segment.points.first(), segment.points.last() + Point(0.0, 1.0))
                )
            },
            "$VALIDATION_LOCATION_TRACK.switch.joint-location-mismatch",
        )
        assertSegmentSwitchError(
            true,
            editSegment(segmentSwitch) { segment ->
                segment.copy(
                    points = toTrackLayoutPoints(segment.points.first() + Point(0.0, 1.0), segment.points.last())
                )
            },
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
        val context = simpleGeocodingContext(points(100, 0.0, 0.0, 0.0, 1000.0))
        assertEquals(listOf(), validateAddressPoints(trackNumber(), locationTrack(trackNumberId = IntId(1)), "") {
            // Alignment at slight angle to reference line -> should be OK
            context.getAddressPoints(
                alignment(
                    segment(
                        Point(10.0, 10.0),
                        Point(20.0, 100.0),
                    )
                ).copy(id = IntId(2))
            )
        })
    }

    @Test
    fun validationCatchesStretchedOutAddressPoints() {
        val context = simpleGeocodingContext(
            // Reference line goes straight up
            toTrackLayoutPoints(
                Point(0.0, 0.0),
                Point(0.0, 1000.0),
            )
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
                ).copy(id = IntId(2))
            )
        }
        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.stretched-meters")
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0060..0000+0070")
    }

    @Test
    fun stretchedOutAddressPointsAtStartAndEndOfLineAreDescribedProperly() {
        val context = simpleGeocodingContext(
            // Reference line goes straight up
            toTrackLayoutPoints(
                Point(0.0, 0.0),
                Point(0.0, 1000.0),
            )
        )
        val geocode = {
            context.getAddressPoints(
                alignment(
                    segment(
                        Point(0.0, 0.0),
                        Point(120.0, 50.0),
                        Point(120.0, 60.0),
                        Point(240.0, 110.0),
                    )
                ).copy(id = IntId(2))
            )
        }
        assertSingleAddressPointErrorRangeDescription(geocode,
            "0000+0000..0000+0050, 0000+0060..0000+0110")
    }

    @Test
    fun validationCatchesZigZagAddressPoints() {
        val context = simpleGeocodingContext(
            toTrackLayoutPoints(
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
                alignment(
                    segment(
                        Point(5.0, 5.0),
                        Point(5.0, 25.0),
                    )
                ).copy(id = IntId(2))
            )
        }
        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
    }

    @Test
    fun validationReportsSharpAngleSectionsCorrectly() {
        val referenceLinePoints = simpleSphereArc(10.0, PI, 20)
        val context = simpleGeocodingContext(toTrackLayoutPoints(to3DMPoints(referenceLinePoints)))

        val sharpAngleTrack = to3DMPoints(
            listOf(
                Point(10.0, 0.0),
                Point(10.0, 10.0),
                Point(0.0, 0.0),
            )
        )

        val geocode = {
            context.getAddressPoints(
                alignment(segment(toTrackLayoutPoints(sharpAngleTrack))).copy(id = IntId(2))
            )
        }

        assertAddressPointError(true, geocode, "$VALIDATION_GEOCODING.sharp-angle")
        // the correct error range is hard to calculate because it depends on how lines get projected; this at least
        // seems OK though
        assertSingleAddressPointErrorRangeDescription(geocode, "0000+0006..0000+0008")
    }

    @Test
    fun validationCatchesKmPostsInWrongOrder() {
        val error = "$VALIDATION_GEOCODING.km-posts-invalid"
        assertContainsError(
            false,
            validateGeocodingContext(
                geocodingContext(
                    toTrackLayoutPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(
                        kmPost(IntId(1), KmNumber(1), Point(12.0, 0.0)),
                        kmPost(IntId(1), KmNumber(2), Point(18.0, 0.0)),
                    ),
                ), ""
            ),
            error,
        )

        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toTrackLayoutPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(
                        kmPost(IntId(1), KmNumber(2), Point(18.0, 0.0)),
                        kmPost(IntId(1), KmNumber(1), Point(12.0, 0.0)),
                    ),
                ), ""
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
                    toTrackLayoutPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), Point(15.0, 0.0))),
                ), ""
            ),
            error,
        )
        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toTrackLayoutPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), Point(5.0, 0.0))),
                ), ""
            ),
            kmPostsOutsideLineErrorBefore,
        )
        assertContainsError(
            true,
            validateGeocodingContext(
                geocodingContext(
                    toTrackLayoutPoints(Point(10.0, 0.0), Point(20.0, 0.0)),
                    listOf(kmPost(IntId(1), KmNumber(1), Point(25.0, 0.0))),
                ), ""
            ),
            kmPostsOutsideLineErrorAfter,
        )
    }

    @Test
    fun validationCatchesLoopyDuplicate() {
        val lt = locationTrack(IntId(0)).copy(duplicateOf = IntId(0))
        assertContainsError(
            true, validateDuplicateOfState(lt, lt, listOf(IntId(0))),
            "$VALIDATION_LOCATION_TRACK.duplicate-of.duplicate"
        )
    }

    private fun editSegment(segmentSwitch: SegmentSwitch, edit: (segment: LayoutSegment) -> LayoutSegment) =
        segmentSwitch.copy(
            segments = segmentSwitch.segments.map(edit),
        )

    private fun draftSegmentSwitchPair(
        switchStateCategory: LayoutStateCategory = LayoutStateCategory.EXISTING,
        switchDraft: Boolean = false,
    ): SegmentSwitch {
        val switch = switch(123).copy(
            id = IntId(1),
            stateCategory = switchStateCategory,
            draft = if (switchDraft) Draft(IntId(2)) else null,
        )
        val joint1 = switch.joints.first()
        val joint2 = switch.joints.last()
        val segment = segment(joint1.location, joint2.location).copy(
            switchId = switch.id,
            startJointNumber = joint1.number,
            endJointNumber = joint2.number,
        )
        return SegmentSwitch(switch, structure, listOf(segment))
    }

    private fun assertFieldError(hasError: Boolean, trackNumber: TrackLayoutTrackNumber, error: String) =
        assertContainsError(hasError, validateDraftTrackNumberFields(trackNumber), error)

    private fun assertFieldError(hasError: Boolean, kmPost: TrackLayoutKmPost, error: String) =
        assertContainsError(hasError, validateDraftKmPostFields(kmPost), error)

    private fun assertFieldError(hasError: Boolean, switch: TrackLayoutSwitch, error: String) =
        assertContainsError(hasError, validateDraftSwitchFields(switch), error)

    private fun assertFieldError(hasError: Boolean, track: LocationTrack, error: String) =
        assertContainsError(hasError, validateDraftLocationTrackFields(track), error)

    private fun assertLocationTrackFieldError(hasError: Boolean, alignment: LayoutAlignment, error: String) =
        assertContainsError(hasError, validateLocationTrackAlignment(alignment), error)

    private fun assertReferenceLineFieldError(hasError: Boolean, alignment: LayoutAlignment, error: String) =
        assertContainsError(hasError, validateLocationTrackAlignment(alignment), error)

    private fun assertTrackNumberReferenceError(
        hasError: Boolean,
        trackNumber: TrackLayoutTrackNumber,
        alignment: LocationTrack,
        error: String,
        includeAlignmentInPublish: Boolean = false,
    ) = assertTrackNumberReferenceError(
        hasError,
        trackNumber,
        error,
        alignments = listOf(alignment),
        includeAlignmentsInPublish = includeAlignmentInPublish,
    )

    private fun assertTrackNumberReferenceError(
        hasError: Boolean,
        trackNumber: TrackLayoutTrackNumber,
        kmPost: TrackLayoutKmPost,
        error: String,
        includeKmPostInPublish: Boolean = false,
    ) = assertTrackNumberReferenceError(
        hasError,
        trackNumber,
        error,
        kmPosts = listOf(kmPost),
        includeKmPostsInPublish = includeKmPostInPublish,
    )

    private fun assertTrackNumberReferenceError(
        hasError: Boolean,
        trackNumber: TrackLayoutTrackNumber,
        error: String,
        kmPosts: List<TrackLayoutKmPost> = listOf(),
        alignments: List<LocationTrack> = listOf(),
        includeKmPostsInPublish: Boolean = false,
        includeAlignmentsInPublish: Boolean = false,
    ) = assertContainsError(
        hasError,
        validateTrackNumberReferences(
            trackNumber,
            kmPosts,
            alignments,
            if (includeKmPostsInPublish) kmPosts.map { p -> p.id as IntId } else listOf(),
            if (includeAlignmentsInPublish) alignments.map { p -> p.id as IntId } else listOf(),
        ),
        error,
    )

    private fun assertKmPostReferenceError(
        hasError: Boolean,
        kmPost: TrackLayoutKmPost,
        trackNumber: TrackLayoutTrackNumber,
        error: String,
        includeTrackNumberInPublish: Boolean = false,
    ) = assertContainsError(
        hasError,
        validateKmPostReferences(
            kmPost,
            trackNumber,
            if (includeTrackNumberInPublish) listOf(trackNumber.id as IntId) else listOf(),
        ),
        error,
    )

    private fun assertGeocodingContextError(
        hasError: Boolean,
        context: GeocodingContext,
        error: String,
    ) = assertContainsError(hasError, validateGeocodingContext(context, ""), error)

    private fun assertSegmentSwitchError(
        hasError: Boolean,
        segmentAndSwitch: SegmentSwitch,
        error: String,
        locationTrack: LocationTrack = locationTrack(IntId(1)),
        includeSwitchInPublish: Boolean = false,
    ) = assertContainsError(
        hasError,
        validateSegmentSwitchReferences(
            locationTrack,
            listOf(segmentAndSwitch),
            if (includeSwitchInPublish) listOf(segmentAndSwitch.switch.id as IntId) else listOf(),
        ),
        error,
    )

    private fun assertSwitchSegmentError(
        hasError: Boolean,
        switch: TrackLayoutSwitch,
        alignment: LocationTrack,
        error: String,
        includeTracksInPublish: Boolean = false,
    ) = assertSwitchSegmentError(hasError, switch, listOf(alignment), error, includeTracksInPublish);

    private fun assertSwitchSegmentError(
        hasError: Boolean,
        switch: TrackLayoutSwitch,
        tracks: List<LocationTrack>,
        error: String,
        includeTracksInPublish: Boolean = false,
    ) = assertContainsError(hasError, getSwitchSegmentErrors(switch, tracks, includeTracksInPublish), error)

    private fun assertSwitchSegmentStructureError(
        hasError: Boolean,
        switch: TrackLayoutSwitch,
        track: Pair<LocationTrack, LayoutAlignment>,
        error: String,
    ) = assertSwitchSegmentStructureError(hasError, switch, listOf(track), error)

    private fun assertSwitchSegmentStructureError(
        hasError: Boolean,
        switch: TrackLayoutSwitch,
        tracks: List<Pair<LocationTrack, LayoutAlignment>>,
        error: String,
    ) = assertContainsError(hasError, getSwitchSegmentStructureErrors(switch, tracks), error)

    private fun getSwitchSegmentErrors(
        switch: TrackLayoutSwitch,
        tracks: List<LocationTrack>,
        includeTracksInPublish: Boolean = false,
    ): List<PublishValidationError> {
        val locationTrackIds = if (includeTracksInPublish) tracks.map { a -> a.id as IntId } else listOf()
        return validateSwitchSegmentReferences(switch, tracks, locationTrackIds)
    }

    private fun getSwitchSegmentStructureErrors(
        switch: TrackLayoutSwitch,
        tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    ): List<PublishValidationError> {
        return validateSwitchSegmentStructure(switch, structure, tracks)
    }

    private fun assertAddressPointError(hasError: Boolean, geocode: () -> AlignmentAddresses?, error: String) {
        assertContainsError(
            hasError,
            validateAddressPoints(trackNumber(), locationTrack(IntId(1)), VALIDATION_GEOCODING, geocode),
            error,
        )
    }

    private fun assertSingleAddressPointErrorRangeDescription(geocode: () -> AlignmentAddresses?, errorRangeDescription: String) {
        val errors = validateAddressPoints(trackNumber(), locationTrack(IntId(1)), "", geocode)
        assertEquals(errorRangeDescription, errors[0].params[2])
    }

    private fun assertContainsError(contains: Boolean, errors: List<PublishValidationError>, error: String) {
        val message = "Expected to ${if (contains) "have" else "not have"} error: expected=$error actual=$errors"
        assertEquals(contains, errors.any { e -> e.localizationKey == LocalizationKey(error) }, message)
    }

    private fun simpleGeocodingContext(referenceLinePoints: List<LayoutPoint>): GeocodingContext =
        geocodingContext(referenceLinePoints, listOf())

    private fun geocodingContext(
        referenceLinePoints: List<LayoutPoint>,
        kmPosts: List<TrackLayoutKmPost>,
    ): GeocodingContext {
        val (referenceLine, alignment) = referenceLineAndAlignment(
            IntId(1),
            listOf(segment(referenceLinePoints)),
            startAddress = TrackMeter.ZERO,
        )
        return GeocodingContext.create(
            // Start the geocoding from 0+0m
            trackNumber(TrackNumber("0000")),
            referenceLine,
            alignment,
            kmPosts,
        )
    }

    private fun simpleSphereArc(radius: Double, arcLength: Double, numPoints: Int): List<Point> =
        (0..numPoints).map { pointIndex ->
            pointInDirection(radius, pointIndex.toDouble() / numPoints.toDouble() * arcLength)
        }
}
