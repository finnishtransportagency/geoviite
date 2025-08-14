package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.validateSplitContent
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType.END
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType.START
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType.START_AND_END
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publicationRequest
import publish
import kotlin.test.assertContains

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationValidationServiceIT
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationValidationService: PublicationValidationService,
    val publicationDao: PublicationDao,
    val alignmentDao: LayoutAlignmentDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val trackNumberService: LayoutTrackNumberService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val switchDao: LayoutSwitchDao,
    val switchService: LayoutSwitchService,
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
    val publicationTestSupportService: PublicationTestSupportService,
) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        publicationTestSupportService.cleanupPublicationTables()
    }

    @Test
    fun `Validating official location track should work`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val (locationTrack, geometry) =
            locationTrackAndGeometry(trackNumber, segment(Point(4.0, 4.0), Point(5.0, 5.0)), draft = false)
        val locationTrackId = locationTrackDao.save(locationTrack, geometry)

        val validation =
            publicationValidationService
                .validateLocationTracks(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(locationTrackId.id),
                )
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official track number should work`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id

        val validation =
            publicationValidationService
                .validateTrackNumbersAndReferenceLines(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(trackNumber),
                )
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Validating official switch should work`() {
        val switchId = switchDao.save(switch(draft = false, stateCategory = LayoutStateCategory.EXISTING)).id

        val validation =
            publicationValidationService.validateSwitches(
                draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                listOf(switchId),
            )
        assertEquals(1, validation.size)
        assertEquals(1, validation[0].errors.size)
    }

    @Test
    fun `Validating multiple switches should work`() {
        val switchId = switchDao.save(switch(draft = false)).id
        val switchId2 = switchDao.save(switch(draft = false)).id
        val switchId3 = switchDao.save(switch(draft = false)).id

        val validationIds =
            publicationValidationService
                .validateSwitches(
                    draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main),
                    listOf(switchId, switchId2, switchId3),
                )
                .map { it.id }
        assertEquals(3, validationIds.size)
        assertContains(validationIds, switchId)
        assertContains(validationIds, switchId2)
        assertContains(validationIds, switchId3)
    }

    @Test
    fun `Validating official km post should work`() {
        val kmPostId =
            kmPostDao
                .save(kmPost(mainOfficialContext.createLayoutTrackNumber().id, km = KmNumber.ZERO, draft = false))
                .id

        val validation =
            publicationValidationService
                .validateKmPosts(draftTransitionOrOfficialState(OFFICIAL, LayoutBranch.main), listOf(kmPostId))
                .first()
        assertEquals(validation.errors.size, 1)
    }

    @Test
    fun `Publication validation identifies duplicate names`() {
        trackNumberDao.save(trackNumber(number = TrackNumber("TN"), draft = false))
        val draftTrackNumberId = trackNumberDao.save(trackNumber(number = TrackNumber("TN"), draft = true)).id

        val someAlignment = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val referenceLineId =
            referenceLineDao.save(referenceLine(draftTrackNumberId, alignmentVersion = someAlignment, draft = true)).id
        val someGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 10.0)))
        locationTrackDao.save(locationTrack(draftTrackNumberId, name = "LT", draft = false), someGeometry)
        // one new draft location track trying to use an official one's name
        val draftLocationTrackId =
            locationTrackDao.save(locationTrack(draftTrackNumberId, name = "LT", draft = true), someGeometry).id

        // two new location tracks stepping over each other's names
        val newLt = locationTrack(draftTrackNumberId, name = "NLT", draft = true)
        val newLocationTrack1 = locationTrackDao.save(newLt, someGeometry).id
        val newLocationTrack2 = locationTrackDao.save(newLt, someGeometry).id

        switchDao.save(switch(name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = false))
        // one new switch trying to use an official one's name
        val draftSwitchId =
            switchDao.save(switch(name = "SW", stateCategory = LayoutStateCategory.EXISTING, draft = true)).id

        // two new switches both trying to use the same name
        val newSwitch = switch(name = "NSW", stateCategory = LayoutStateCategory.EXISTING, draft = true)
        val newSwitch1 = switchDao.save(newSwitch).id
        val newSwitch2 = switchDao.save(newSwitch).id

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                PublicationRequestIds(
                    trackNumbers = listOf(draftTrackNumberId),
                    locationTracks = listOf(draftLocationTrackId, newLocationTrack1, newLocationTrack2),
                    kmPosts = listOf(),
                    referenceLines = listOf(referenceLineId),
                    switches = listOf(draftSwitchId, newSwitch1, newSwitch2),
                ),
            )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.location-track.duplicate-name-official",
                    mapOf("locationTrack" to AlignmentName("LT"), "trackNumber" to TrackNumber("TN")),
                )
            ),
            validation.validatedAsPublicationUnit.locationTracks.find { lt -> lt.id == draftLocationTrackId }?.issues,
        )

        assertEquals(
            List(2) {
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.location-track.duplicate-name-draft",
                    mapOf("locationTrack" to AlignmentName("NLT"), "trackNumber" to TrackNumber("TN")),
                )
            },
            validation.validatedAsPublicationUnit.locationTracks
                .filter { lt -> lt.name == AlignmentName("NLT") }
                .flatMap { it.issues },
        )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.switch.duplicate-name-official",
                    mapOf("switch" to SwitchName("SW")),
                )
            ),
            validation.validatedAsPublicationUnit.switches
                .find { it.name == SwitchName("SW") }
                ?.issues
                ?.filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-official" },
        )

        assertEquals(
            List(2) {
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.switch.duplicate-name-draft",
                    mapOf("switch" to SwitchName("NSW")),
                )
            },
            validation.validatedAsPublicationUnit.switches
                .filter { it.name == SwitchName("NSW") }
                .flatMap { it.issues }
                .filter { it.localizationKey.toString() == "validation.layout.switch.duplicate-name-draft" },
        )

        assertEquals(
            listOf(
                LayoutValidationIssue(
                    LayoutValidationIssueType.FATAL,
                    "validation.layout.track-number.duplicate-name-official",
                    mapOf("trackNumber" to TrackNumber("TN")),
                )
            ),
            validation.validatedAsPublicationUnit.trackNumbers[0].issues,
        )
    }

    @Test
    fun `Publication validation rejects duplication by another referencing track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val someGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 1.0)))
        // Initial state, all official: Small duplicates middle, middle and big don't duplicate
        // anything
        val middleTrack =
            locationTrackDao.save(locationTrack(trackNumberId, name = "middle track", draft = false), someGeometry)
        val smallTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, name = "small track", duplicateOf = middleTrack.id, draft = false),
                someGeometry,
            )
        val bigTrack =
            locationTrackDao.save(locationTrack(trackNumberId, name = "big track", draft = false), someGeometry)

        // In new draft, middle wants to duplicate big (leading to: small->middle->big)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(middleTrack).copy(duplicateOf = bigTrack.id),
            someGeometry,
        )

        fun getPublishingDuplicateWhileDuplicatedValidationError(
            vararg publishableTracks: IntId<LocationTrack>
        ): LayoutValidationIssue? {
            val validation =
                publicationValidationService.validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    PublicationRequestIds(
                        trackNumbers = listOf(),
                        locationTracks = listOf(*publishableTracks),
                        kmPosts = listOf(),
                        referenceLines = listOf(),
                        switches = listOf(),
                    ),
                )
            val trackErrors = validation.validatedAsPublicationUnit.locationTracks[0].issues
            return trackErrors.find { error ->
                error.localizationKey ==
                    LocalizationKey(
                        "validation.layout.location-track.duplicate-of.publishing-duplicate-while-duplicated"
                    )
            }
        }

        // if we're only trying to publish the middle track, but the small is still duplicating it,
        // we pop
        val duplicateError = getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id)
        assertNotNull(duplicateError, "small track duplicates to-be-published middle track which duplicates big track")
        assertEquals("small track", duplicateError!!.params.get("otherDuplicates"))
        assertEquals("big track", duplicateError.params.get("duplicateTrack"))

        // if we have a draft of the small track that is not a duplicate of the middle track, but
        // we're not publishing
        // it in this unit, that doesn't fix the issue yet
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(smallTrack).copy(duplicateOf = null),
            someGeometry,
        )
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id),
            "only saving a draft of small track",
        )

        // but if we have the new non-duplicating small track in the same publication unit, it's
        // fine
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track",
        )

        // finally, if we have a track whose official version doesn't duplicate the middle track,
        // but the draft does,
        // it's only bad if the draft is in the publication unit
        val otherSmallTrack =
            locationTrackDao.save(locationTrack(trackNumberId, name = "other small track", draft = false), someGeometry)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(otherSmallTrack).copy(duplicateOf = middleTrack.id),
            someGeometry,
        )
        assertNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id),
            "publishing new small track with other small track added",
        )
        assertNotNull(
            getPublishingDuplicateWhileDuplicatedValidationError(middleTrack.id, smallTrack.id, otherSmallTrack.id),
            "publishing new small track with other small track added and in publication unit",
        )
    }

    @Test
    fun `Don't allow publishing a track that is a duplicate of an unpublished draft-only one`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (draftOnlyTrack, draftOnlyAlignment) =
            locationTrackAndGeometry(
                trackNumberId = trackNumberId,
                segments = listOf(someSegment()),
                duplicateOf = null,
                draft = true,
            )
        val draftOnlyId = locationTrackService.saveDraft(LayoutBranch.main, draftOnlyTrack, draftOnlyAlignment).id

        val (duplicateTrack, duplicateAlignment) =
            locationTrackAndGeometry(
                trackNumberId = trackNumberId,
                segments = listOf(someSegment()),
                duplicateOf = draftOnlyId,
                draft = true,
            )
        val duplicateId = locationTrackService.saveDraft(LayoutBranch.main, duplicateTrack, duplicateAlignment).id

        // Both tracks in validation set: this is fine
        assertFalse(
            containsDuplicateOfNotPublishedError(
                validateLocationTrack(toValidate = duplicateId, duplicateId, draftOnlyId)
            )
        )
        // Only the target (main) track in set: this is also fine
        assertFalse(containsDuplicateOfNotPublishedError(validateLocationTrack(toValidate = draftOnlyId, draftOnlyId)))
        // Only the duplicate track in set: this would result in official referring to draft through
        // duplicateOf
        assertTrue(containsDuplicateOfNotPublishedError(validateLocationTrack(toValidate = duplicateId, duplicateId)))
    }

    private fun containsDuplicateOfNotPublishedError(errors: List<LayoutValidationIssue>) =
        containsError(errors, "validation.layout.location-track.duplicate-of.not-published")

    private fun containsError(errors: List<LayoutValidationIssue>, key: String) =
        errors.any { e -> e.localizationKey.toString() == key }

    private fun validateLocationTrack(
        toValidate: IntId<LocationTrack>,
        vararg publicationSet: IntId<LocationTrack>,
    ): List<LayoutValidationIssue> {
        val candidates =
            publicationService
                .collectPublicationCandidates(PublicationInMain)
                .filter(publicationRequest(locationTracks = publicationSet.toList()))
        return publicationValidationService
            .validateAsPublicationUnit(candidates, false)
            .locationTracks
            .find { c -> c.id == toValidate }!!
            .issues
    }

    @Test
    fun `Location track validation catches only switch topology errors related to its own changes`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchDao
                .save(
                    switch(name = "TV123", structureId = switchStructureYV60_300_1_9().id, draft = false)
                        .copy(stateCategory = LayoutStateCategory.EXISTING)
                )
                .id
        val officialTrackOn152 =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switchId, 1),
                        segments = listOf(segment(Point(0.0, 5.0), Point(0.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 5),
                        segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 5),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
                    ),
                ),
            )
        val officialTrackOn13 =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 3),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 2.0))),
                    )
                ),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn152).copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(officialTrackOn152),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn13).copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(officialTrackOn13),
        )

        val errorsWhenDeletingStraightTrack = getLocationTrackValidationResult(officialTrackOn152.id).issues
        assertTrue(
            errorsWhenDeletingStraightTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("alignments") == "1-5-2" &&
                    error.params.get("switch") == "TV123"
            }
        )
        assertTrue(
            errorsWhenDeletingStraightTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected") &&
                    error.params.get("switch") == "TV123"
            }
        )

        val errorsWhenDeletingBranchingTrack =
            publicationValidationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn13.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertTrue(
            errorsWhenDeletingBranchingTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("alignments") == "1-3" &&
                    error.params.get("switch") == "TV123"
            }
        )
        assertFalse(
            errorsWhenDeletingBranchingTrack.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.front-joint-not-connected")
            }
        )
    }

    @Test
    fun `Location track validation catches track removal causing switches to go unlinked`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchDao
                .save(
                    switch(
                        name = "TV123",
                        joints =
                            listOf(
                                LayoutSwitchJoint(
                                    number = JointNumber(1),
                                    role = SwitchJointRole.MAIN,
                                    location = Point(0.0, 0.0),
                                    locationAccuracy = null,
                                )
                            ),
                        structureId = switchStructureYV60_300_1_9().id,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = false,
                    )
                )
                .id
        val officialTrackOn152 =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 5),
                        segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 5),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
                    ),
                ),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialTrackOn152).copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(officialTrackOn152),
        )
        locationTrackDao.save(
            locationTrack(trackNumberId, draft = false),
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(switchId, 1),
                    endInnerSwitch = switchLinkYV(switchId, 3),
                    segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 2.0))),
                )
            ),
        )

        val locationTrackDeletionErrors =
            publicationValidationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn152.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertTrue(
            locationTrackDeletionErrors.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected") &&
                    error.params.get("alignments") == "1-5-2" &&
                    error.params.get("switch") == "TV123"
            }
        )
        // but it's OK if we link a replacement track
        val replacementTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 5),
                        segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 5),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
                    ),
                ),
            )
        val errorsWithReplacementTrackLinked =
            publicationValidationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = listOf(officialTrackOn152.id, replacementTrack.id)),
                )
                .validatedAsPublicationUnit
                .locationTracks[0]
                .issues
        assertFalse(
            errorsWithReplacementTrackLinked.any { error ->
                error.localizationKey ==
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected")
            }
        )
    }

    @Test
    fun `duplicate km posts are fatal in validation`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        mainOfficialContext.save(kmPost(trackNumber, KmNumber(1)))
        mainOfficialContext.saveReferenceLine(
            referenceLineAndAlignment(trackNumber, segment(Point(0.0, 0.0), Point(2.0, 2.0)))
        )
        val design = testDBService.createDesignBranch()
        val designKmPost = testDBService.testContext(design, OFFICIAL).save(kmPost(trackNumber, KmNumber(1)))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation = publicationValidationService.validateKmPosts(transition, listOf(designKmPost.id)).first()

        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("$VALIDATION_GEOCODING.duplicate-km-posts")
            }
        )
    }

    @Test
    fun `duplicate switch names are found in merge to main`() {
        mainDraftContext.save(switch(name = "ABC V123"))
        val design = testDBService.createDesignBranch()
        val designSwitch = testDBService.testContext(design, OFFICIAL).save(switch(name = "ABC V123"))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation = publicationValidationService.validateSwitches(transition, listOf(designSwitch.id)).first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("validation.layout.switch.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `duplicate track numbers are found in merge to main`() {
        mainDraftContext.save(trackNumber(number = TrackNumber("123")))
        val design = testDBService.createDesignBranch()
        val designTrackNumber =
            testDBService.testContext(design, OFFICIAL).save(trackNumber(number = TrackNumber("123")))
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation =
            publicationValidationService
                .validateTrackNumbersAndReferenceLines(transition, listOf(designTrackNumber.id))
                .first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("validation.layout.track-number.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `duplicate location track from draft mode are found`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        mainDraftContext.saveLocationTrack(
            locationTrackAndGeometry(
                trackNumber,
                name = "ABC 123",
                segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
            )
        )
        val design = testDBService.createDesignBranch()
        val designLocationTrack =
            testDBService
                .testContext(design, OFFICIAL)
                .saveLocationTrack(
                    locationTrackAndGeometry(
                        trackNumber,
                        name = "ABC 123",
                        segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                    )
                )
        val transition = ValidateTransition(LayoutContextTransition.mergeToMainFrom(design))
        val validation =
            publicationValidationService.validateLocationTracks(transition, listOf(designLocationTrack.id)).first()
        assertTrue(
            validation.errors.any {
                it.type == LayoutValidationIssueType.FATAL &&
                    it.localizationKey == LocalizationKey("$VALIDATION_LOCATION_TRACK.duplicate-name-draft-in-main")
            }
        )
    }

    @Test
    fun `reference lines are validated on merge to main`() {
        val design = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)
        val trackNumberId = designOfficialContext.save(trackNumber()).id
        val referenceLineId =
            designOfficialContext
                .saveReferenceLine(
                    // segment with bendy alignment
                    referenceLineAndAlignment(trackNumberId, segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(0.0, 1.0)))
                )
                .id
        val validated =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(MergeFromDesign(design)),
                PublicationRequestIds(
                    trackNumbers = listOf(trackNumberId),
                    locationTracks = listOf(),
                    kmPosts = listOf(),
                    referenceLines = listOf(referenceLineId),
                    switches = listOf(),
                ),
            )
        val referenceLineIssues = validated.validatedAsPublicationUnit.referenceLines[0].issues
        assertEquals(1, referenceLineIssues.size)
        assertEquals(
            LocalizationKey("validation.layout.reference-line.points.not-continuous"),
            referenceLineIssues[0].localizationKey,
        )
    }

    private fun getLocationTrackValidationResult(
        locationTrackId: IntId<LocationTrack>,
        stagedSwitches: List<IntId<LayoutSwitch>> = listOf(),
        stagedTracks: List<IntId<LocationTrack>> = listOf(locationTrackId),
    ): LocationTrackPublicationCandidate {
        val publicationRequestIds =
            PublicationRequestIds(
                trackNumbers = listOf(),
                locationTracks = stagedTracks,
                referenceLines = listOf(),
                switches = stagedSwitches,
                kmPosts = listOf(),
            )

        val validationResult =
            publicationValidationService.validateAsPublicationUnit(
                publicationService.collectPublicationCandidates(PublicationInMain).filter(publicationRequestIds),
                allowMultipleSplits = false,
            )

        return validationResult.locationTracks.find { lt -> lt.id == locationTrackId }!!
    }

    private fun switchAlignmentNotConnectedTrackValidationError(alignments: String, switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.WARNING,
            "validation.layout.location-track.switch-linkage.switch-alignment-not-connected",
            mapOf("alignments" to alignments, "switch" to switchName),
        )

    private fun switchNotPublishedError(switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.ERROR,
            "validation.layout.location-track.switch.not-published",
            mapOf("switch" to switchName),
        )

    private fun switchFrontJointNotConnectedError(switchName: String) =
        LayoutValidationIssue(
            LayoutValidationIssueType.WARNING,
            "validation.layout.location-track.switch-linkage.front-joint-not-connected",
            mapOf("switch" to switchName),
        )

    private fun assertValidationErrorsForEach(
        expecteds: List<List<LayoutValidationIssue>>,
        actuals: List<List<LayoutValidationIssue>>,
    ) {
        assertEquals(expecteds.size, actuals.size, "size equals")
        expecteds.forEachIndexed { i, expected -> assertValidationErrorContentEquals(expected, actuals[i], i) }
    }

    private fun assertValidationErrorContentEquals(
        expected: List<LayoutValidationIssue>,
        actual: List<LayoutValidationIssue>,
        index: Int,
    ) {
        val allKeys = expected.map { it.localizationKey.toString() } + actual.map { it.localizationKey.toString() }
        val commonPrefix =
            allKeys.reduce { acc, next -> acc.take(acc.zip(next) { a, b -> a == b }.takeWhile { it }.count()) }

        fun cleanupKey(key: LocalizationKey) = key.toString().let { k -> if (commonPrefix.length > 3) "...$k" else k }

        assertEquals(
            expected.map { cleanupKey(it.localizationKey) }.sorted(),
            actual.map { cleanupKey(it.localizationKey) }.sorted(),
            "same errors by localization key, index $index, ",
        )

        val expectedByKey = expected.sortedBy { it.toString() }.groupBy { it.localizationKey }
        val actualByKey = actual.sortedBy { it.toString() }.groupBy { it.localizationKey }
        expectedByKey.keys.forEach { key ->
            assertEquals(
                expectedByKey[key]!!.map { it.params },
                actualByKey[key]!!.map { it.params },
                "params for key $key at index $index, ",
            )
            assertEquals(
                expectedByKey[key]!!.map { it.type },
                actualByKey[key]!!.map { it.type },
                "level for key $key at index $index, ",
            )
        }
    }

    private val topoTestDataContextOnLocationTrackValidationError =
        listOf(validationError("validation.layout.location-track.no-context"))
    private val topoTestDataStartSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test start switch")
    private val topoTestDataStartSwitchJointsNotConnectedError =
        switchAlignmentNotConnectedTrackValidationError(
            "1-5-2", // alignment 1-3 is generated in the data, 1-5-2 is not
            "Topological switch connection test start switch",
        )
    private val topoTestDataEndSwitchNotPublishedError =
        switchNotPublishedError("Topological switch connection test end switch")
    private val topoTestDataEndSwitchJointsNotConnectedError =
        switchAlignmentNotConnectedTrackValidationError(
            "1-5-2", // alignment 1-3 is generated in the data, 1-5-2 is not
            "Topological switch connection test end switch",
        )
    private val topoTestDataEndSwitchFrontJointNotConnectedError =
        switchFrontJointNotConnectedError("Topological switch connection test end switch")

    @Test
    fun `Location track validation should fail for unofficial and unstaged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart =
            listOf(
                topoTestDataStartSwitchNotPublishedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd = listOf(topoTestDataEndSwitchNotPublishedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(locationTrackId).issues
            }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for unofficial, but staged topologically linked switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()
        val noStart =
            listOf(
                topoTestDataStartSwitchJointsNotConnectedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd =
            listOf(topoTestDataEndSwitchJointsNotConnectedError, topoTestDataEndSwitchFrontJointNotConnectedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(
                        locationTrackId,
                        topologyTestData.switchIdsUnderTest,
                        topologyTestData.switchInnerTrackIds + locationTrackId,
                    )
                    .issues
            }

        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Location track validation should succeed for topologically linked official switches`() {
        val topologyTestData = getTopologicalSwitchConnectionTestData()

        val noStart =
            listOf(
                topoTestDataStartSwitchJointsNotConnectedError
                // no error about no track continuing from the front joint, because a track in fact
                // does continue from it
            )
        val noEnd =
            listOf(topoTestDataEndSwitchJointsNotConnectedError, topoTestDataEndSwitchFrontJointNotConnectedError)
        val expected =
            listOf(
                topoTestDataContextOnLocationTrackValidationError,
                topoTestDataContextOnLocationTrackValidationError + noStart,
                topoTestDataContextOnLocationTrackValidationError + noEnd,
                topoTestDataContextOnLocationTrackValidationError + noStart + noEnd,
            )

        publish(
            publicationService,
            switches = topologyTestData.switchIdsUnderTest,
            locationTracks = topologyTestData.switchInnerTrackIds,
        )
        val actual =
            topologyTestData.locationTracksUnderTest.map { (locationTrackId) ->
                getLocationTrackValidationResult(locationTrackId).issues
            }
        assertValidationErrorsForEach(expected, actual)
    }

    @Test
    fun `Switch validation checks duplicate tracks through non-math joints`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val structure = switchStructureDao.fetchSwitchStructures().find { ss -> ss.type.typeName == "KRV43-233-1:9" }!!
        val switchId =
            switchService
                .saveDraft(
                    LayoutBranch.main,
                    switch(
                        name = "TV123",
                        joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 0.0), null)),
                        structureId = structure.id,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = true,
                    ),
                )
                .id
        val locationTrack1 =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometry(
                    edge(
                        endOuterSwitch = SwitchLink(switchId, JointNumber(1), structure),
                        segments = listOf(segment(Point(0.0, 0.0), Point(2.0, 2.0))),
                    ),
                    edge(
                        startInnerSwitch = SwitchLink(switchId, JointNumber(1), structure),
                        endInnerSwitch = SwitchLink(switchId, JointNumber(5), structure),
                        segments = listOf(segment(Point(2.0, 2.0), Point(5.0, 5.0))),
                    ),
                    edge(
                        startInnerSwitch = SwitchLink(switchId, JointNumber(5), structure),
                        endInnerSwitch = SwitchLink(switchId, JointNumber(2), structure),
                        segments = listOf(segment(Point(5.0, 5.0), Point(8.0, 8.0))),
                    ),
                    edge(
                        startOuterSwitch = SwitchLink(switchId, JointNumber(2), structure),
                        segments = listOf(segment(Point(8.0, 8.0), Point(10.0, 10.0))),
                    ),
                ),
            )

        val otherGeometry =
            trackGeometry(
                edge(
                    endOuterSwitch = SwitchLink(switchId, JointNumber(4), structure),
                    segments = listOf(segment(Point(10.0, 0.0), Point(8.0, 2.0))),
                ),
                edge(
                    startInnerSwitch = SwitchLink(switchId, JointNumber(4), structure),
                    endInnerSwitch = SwitchLink(switchId, JointNumber(5), structure),
                    segments = listOf(segment(Point(8.0, 2.0), Point(5.0, 5.0))),
                ),
                edge(
                    startInnerSwitch = SwitchLink(switchId, JointNumber(5), structure),
                    endInnerSwitch = SwitchLink(switchId, JointNumber(3), structure),
                    segments = listOf(segment(Point(5.0, 5.0), Point(2.0, 8.0))),
                ),
                edge(
                    startInnerSwitch = SwitchLink(switchId, JointNumber(3), structure),
                    segments = listOf(segment(Point(2.0, 8.0), Point(0.0, 10.0))),
                ),
            )

        val locationTrack2 = locationTrack(trackNumberId, draft = true)
        val locationTrack3 = locationTrack(trackNumberId, draft = true)
        val locationTrack2Id = locationTrackService.saveDraft(LayoutBranch.main, locationTrack2, otherGeometry)
        val locationTrack3Id = locationTrackService.saveDraft(LayoutBranch.main, locationTrack3, otherGeometry)

        val validated =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(
                    locationTracks = listOf(locationTrack1.id, locationTrack2Id.id, locationTrack3Id.id),
                    switches = listOf(switchId),
                ),
            )
        val switchValidation = validated.validatedAsPublicationUnit.switches[0].issues
        assertContains(
            switchValidation,
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.switch.track-linkage.switch-alignment-multiply-connected",
                mapOf("locationTracks" to "4-5-3 (${locationTrack2.name}, ${locationTrack3.name})", "switch" to "TV123"),
            ),
        )
    }

    @Test
    fun `Switch validation requires a track to continue from the front joint`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId =
            switchService
                .saveDraft(
                    LayoutBranch.main,
                    switch(
                        name = "TV123",
                        joints =
                            listOf(
                                LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 0.0), null),
                                LayoutSwitchJoint(JointNumber(2), SwitchJointRole.MAIN, Point(10.0, 0.0), null),
                                LayoutSwitchJoint(JointNumber(3), SwitchJointRole.MAIN, Point(10.0, 2.0), null),
                                LayoutSwitchJoint(JointNumber(5), SwitchJointRole.MAIN, Point(5.0, 0.0), null),
                            ),
                        structureId = switchStructureYV60_300_1_9().id,
                        stateCategory = LayoutStateCategory.EXISTING,
                        draft = true,
                    ),
                )
                .id
        val trackOn152Alignment =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometry(
                        edge(
                            startInnerSwitch = switchLinkYV(switchId, 1),
                            endInnerSwitch = switchLinkYV(switchId, 5),
                            segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
                        ),
                        edge(
                            startInnerSwitch = switchLinkYV(switchId, 5),
                            endInnerSwitch = switchLinkYV(switchId, 2),
                            segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
                        ),
                    ),
                )
                .id
        val trackOn13Alignment =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    trackGeometry(
                        edge(
                            startInnerSwitch = switchLinkYV(switchId, 1),
                            endInnerSwitch = switchLinkYV(switchId, 3),
                            segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 2.0))),
                        )
                    ),
                )
                .id

        fun errorsWhenValidatingSwitchWithTracks(vararg locationTracks: IntId<LocationTrack>) =
            publicationValidationService
                .validatePublicationCandidates(
                    publicationService.collectPublicationCandidates(PublicationInMain),
                    publicationRequestIds(locationTracks = locationTracks.toList(), switches = listOf(switchId)),
                )
                .validatedAsPublicationUnit
                .switches[0]
                .issues

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment),
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                LocalizationKey("validation.layout.switch.track-linkage.front-joint-not-connected"),
                LocalizationParams(mapOf("switch" to "TV123")),
            ),
        )

        val topoGeometry =
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(switchId, 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(-5.0, 0.0))),
                )
            )
        val topoTrackMarkedAsDuplicate =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId = trackNumberId, duplicateOf = trackOn13Alignment, draft = true),
                    topoGeometry,
                )
                .id

        assertContains(
            errorsWhenValidatingSwitchWithTracks(trackOn152Alignment, trackOn13Alignment, topoTrackMarkedAsDuplicate),
            LayoutValidationIssue(
                LayoutValidationIssueType.WARNING,
                "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected",
                mapOf("switch" to "TV123"),
            ),
        )

        val goodTopoTrack =
            locationTrackService
                .saveDraft(LayoutBranch.main, locationTrack(trackNumberId, draft = true), topoGeometry)
                .id

        val errors =
            errorsWhenValidatingSwitchWithTracks(
                trackOn152Alignment,
                trackOn13Alignment,
                topoTrackMarkedAsDuplicate,
                goodTopoTrack,
            )
        assertFalse(
            errors.any { e ->
                e.localizationKey.contains("validation.layout.switch.track-linkage.front-joint-not-connected") ||
                    e.localizationKey.contains(
                        "validation.layout.switch.track-linkage.front-joint-only-duplicate-connected"
                    )
            }
        )
    }

    @Test
    fun `split target location track validation should fail if it's part of a published split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.targetTracks.first().first.id),
                TmpLocationTrackGeometry.empty,
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertContains(
            errors,
            validationError(
                "validation.layout.split.track-split-in-progress",
                "sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack).name,
            ),
        )
    }

    @Test
    fun `split target location track validation should not fail on finished split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        val splitId = publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        splitDao.updateSplit(splitId, bulkTransferState = BulkTransferState.DONE)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.targetTracks.first().first.id),
                alignmentDao.fetch(splitSetup.targetTracks.first().first),
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertTrue(errors.isEmpty(), "Expected no errors: found=$errors")
    }

    @Test
    fun `split source location track validation should fail if it's part of a published split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.sourceTrack.id),
                TmpLocationTrackGeometry.empty,
            )

        val errors = validateLocationTracks(listOf(draft.id))
        assertContains(
            errors,
            validationError(
                "validation.layout.split.track-split-in-progress",
                "sourceName" to locationTrackDao.fetch(draft).name,
            ),
        )
    }

    @Test
    fun `split source location track validation should not fail on finished split`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()

        val splitId = publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)
        publish(publicationService, locationTracks = splitSetup.trackIds)

        splitDao.updateSplit(splitId, bulkTransferState = BulkTransferState.DONE)

        val draft =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.getOrThrow(MainLayoutContext.draft, splitSetup.sourceTrack.id),
                TmpLocationTrackGeometry.empty,
            )

        val errors = validateLocationTracks(listOf(draft.id))
        // Unrelated to the split, the switch is left unconnected
        assertEquals(emptyList<LayoutValidationIssue>(), errors)
    }

    @Test
    fun `split source location track validation should fail if source location track isn't deleted`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup(LocationTrackState.IN_USE)

        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams).also(splitDao::get)

        val errors = validateLocationTracks(splitSetup.trackIds)
        assertContains(
            errors,
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                LocalizationKey("validation.layout.split.source-not-deleted"),
                localizationParams("sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack).name),
            ),
        )
    }

    @Test
    fun `split location track validation should fail if a target is on a different track number`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        val startTargetVersion = splitSetup.targetTracks.first().first
        val startTarget = locationTrackDao.fetch(startTargetVersion)
        locationTrackDao.save(
            startTarget.copy(trackNumberId = mainDraftContext.createLayoutTrackNumber().id),
            alignmentDao.fetch(startTargetVersion),
        )

        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val errors = validateLocationTracks(splitSetup.trackIds)
        assertContains(
            errors,
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                LocalizationKey("validation.layout.split.source-and-target-track-numbers-are-different"),
                localizationParams(
                    "targetName" to startTarget.name,
                    "sourceName" to locationTrackDao.fetch(splitSetup.sourceTrack).name,
                ),
            ),
        )
    }

    @Test
    fun `km post split validation should fail on unfinished split`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val kmPostId = mainDraftContext.save(kmPost(trackNumberId = trackNumberId, km = KmNumber.ZERO)).id
        val locationTrackResponse = mainDraftContext.save(locationTrack(trackNumberId), TmpLocationTrackGeometry.empty)

        publicationTestSupportService.saveSplit(locationTrackResponse)

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(locationTracks = listOf(locationTrackResponse.id), kmPosts = listOf(kmPostId)),
            )

        val errors = validation.validatedAsPublicationUnit.kmPosts.flatMap { it.issues }

        assertContains(
            errors,
            validationError(
                "validation.layout.split.affected-split-in-progress",
                "sourceName" to locationTrackDao.fetch(locationTrackResponse).name,
            ),
        )
    }

    @Test
    fun `reference line split validation should fail on unfinished split`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val referenceLineVersion =
            mainOfficialContext.save(referenceLine(trackNumberId), alignment).let { v ->
                referenceLineService.saveDraft(LayoutBranch.main, referenceLineDao.fetch(v))
            }

        val locationTrackResponse = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        publicationTestSupportService.saveSplit(locationTrackResponse)

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(
                    locationTracks = listOf(locationTrackResponse.id),
                    referenceLines = listOf(referenceLineVersion.id),
                ),
            )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.issues }

        assertContains(
            errors,
            validationError(
                "validation.layout.split.affected-split-in-progress",
                "sourceName" to locationTrackDao.fetch(locationTrackResponse).name,
            ),
        )
    }

    @Test
    fun `reference line split validation should not fail on finished splitting`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))

        val referenceLine = mainOfficialContext.save(referenceLine(trackNumberId), alignment)
        val locationTrackResponse = mainDraftContext.save(locationTrack(trackNumberId), geometry)

        referenceLineDao.fetch(referenceLine).also { d -> referenceLineService.saveDraft(LayoutBranch.main, d) }

        publicationTestSupportService.saveSplit(locationTrackResponse).also { splitId ->
            val split = splitDao.getOrThrow(splitId)
            splitDao.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)
        }

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInMain),
                publicationRequestIds(referenceLines = listOf(referenceLine.id)),
            )

        val errors = validation.validatedAsPublicationUnit.referenceLines.flatMap { it.issues }

        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `split geometry validation should fail on geometry changes in source track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        mainOfficialContext.save(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val splitSwitchId = mainOfficialContext.createSwitch().id

        val edge1 =
            edge(
                endInnerSwitch = switchLinkYV(splitSwitchId, 1),
                segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
            )
        val edge2 =
            edge(
                startOuterSwitch = switchLinkYV(splitSwitchId, 1),
                segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
            )
        val edge1Edited = edge1.copy(segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 5.0))))
        val edge2Edited = edge2.copy(segments = listOf(segment(Point(5.0, 5.0), Point(10.0, 0.0))))

        val sourceTrackResponse = mainOfficialContext.save(locationTrack(trackNumberId), trackGeometry(edge1, edge2))
        val updatedSourceTrackResponse =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrackDao.fetch(sourceTrackResponse).copy(state = LocationTrackState.DELETED),
                trackGeometry(edge1Edited, edge2Edited),
            )

        assertEquals(sourceTrackResponse.id, updatedSourceTrackResponse.id)
        assertNotEquals(sourceTrackResponse, updatedSourceTrackResponse)

        val startTargetTrackId = mainDraftContext.save(locationTrack(trackNumberId), trackGeometry(edge1)).id
        val endTargetTrackId = mainDraftContext.save(locationTrack(trackNumberId), trackGeometry(edge2)).id

        publicationTestSupportService.saveSplit(
            updatedSourceTrackResponse,
            listOf(startTargetTrackId to 0..0, endTargetTrackId to 1..1),
        )

        val errors = validateLocationTracks(sourceTrackResponse.id, startTargetTrackId, endTargetTrackId)

        assertTrue(
            errors.any { it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed") },
            errors.joinToString { ", " },
        )
    }

    @Test
    fun `split geometry validation should fail on geometry changes in target track`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        mainOfficialContext.save(referenceLine(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))

        val splitSwitch = mainOfficialContext.createSwitch().id
        val edge1 =
            edge(
                endOuterSwitch = switchLinkYV(splitSwitch, 1),
                segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
            )
        val edge2 =
            edge(
                startInnerSwitch = switchLinkYV(splitSwitch, 1),
                endInnerSwitch = switchLinkYV(splitSwitch, 2),
                segments = listOf(segment(Point(5.0, 0.0), Point(10.0, 0.0))),
            )
        val sourceTrackResponse =
            mainOfficialContext.save(locationTrack(trackNumberId), trackGeometry(edge1, edge2)).let { response ->
                val lt = locationTrackDao.fetch(response).copy(state = LocationTrackState.DELETED)
                locationTrackService.saveDraft(LayoutBranch.main, lt, alignmentDao.fetch(response))
            }

        val startTargetTrackId =
            mainDraftContext
                .save(locationTrack(trackNumberId), trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(5.0, 10.0))))
                .id

        val endTargetTrackId =
            mainDraftContext
                .save(locationTrack(trackNumberId), trackGeometryOfSegments(segment(Point(5.0, 0.0), Point(10.0, 0.0))))
                .id

        publicationTestSupportService.saveSplit(
            sourceTrackResponse,
            listOf(startTargetTrackId to 0..0, endTargetTrackId to 1..1),
        )

        val errors = validateLocationTracks(sourceTrackResponse.id, startTargetTrackId, endTargetTrackId)
        assertTrue(errors.any { it.localizationKey == LocalizationKey("validation.layout.split.geometry-changed") })
    }

    @Test
    fun `split validation should fail if the publication unit does not contain source track`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val errors = validateLocationTracks(splitSetup.targetTracks.map { it.first.id })
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `split validation should fail if the publication unit does not contain target track`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        publicationTestSupportService.saveSplit(splitSetup.sourceTrack, splitSetup.targetParams)

        val errors = validateLocationTracks(listOf(splitSetup.sourceTrack.id))
        assertContains(errors, validationError("validation.layout.split.split-missing-location-tracks"))
    }

    @Test
    fun `Split validation should respect allowMultipleSplits`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        val split =
            publicationTestSupportService
                .saveSplit(sourceTrackVersion = splitSetup.sourceTrack, targetTracks = splitSetup.targetParams)
                .let(splitDao::getOrThrow)

        val splitSetup2 = publicationTestSupportService.simpleSplitSetup()
        val split2 =
            publicationTestSupportService
                .saveSplit(sourceTrackVersion = splitSetup2.sourceTrack, targetTracks = splitSetup2.targetParams)
                .let(splitDao::getOrThrow)

        val trackValidationVersions = splitSetup.trackResponses + splitSetup2.trackResponses

        assertContains(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), false).map { it.second },
            validationError("validation.layout.split.multiple-splits-not-allowed"),
        )
        assertTrue(
            validateSplitContent(trackValidationVersions, emptyList(), listOf(split, split2), true)
                .map { it.second }
                .none { error -> error == validationError("validation.layout.split.multiple-splits-not-allowed") }
        )
    }

    @Test
    fun `Split validation should fail if switches are missing`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            publicationTestSupportService
                .saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertContains(
            validateSplitContent(splitSetup.trackResponses, emptyList(), listOf(split), false).map { it.second },
            validationError("validation.layout.split.split-missing-switches"),
        )
    }

    @Test
    fun `Split validation should fail if only switches are staged`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            publicationTestSupportService
                .saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertContains(
            validateSplitContent(emptyList(), listOf(switch), listOf(split), false).map { it.second },
            validationError("validation.layout.split.split-missing-location-tracks"),
        )
    }

    @Test
    fun `Split validation should not fail if switches and location tracks are staged`() {
        val splitSetup = publicationTestSupportService.simpleSplitSetup()
        val switch = mainOfficialContext.createSwitch()
        val split =
            publicationTestSupportService
                .saveSplit(
                    sourceTrackVersion = splitSetup.sourceTrack,
                    targetTracks = splitSetup.targetParams,
                    switches = listOf(switch.id),
                )
                .let(splitDao::getOrThrow)

        assertEquals(0, validateSplitContent(splitSetup.trackResponses, listOf(switch), listOf(split), false).size)
    }

    @Test
    fun `validation reports references to cancelled track number`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val trackNumber = designOfficialContext.save(trackNumber()).id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val referenceLine = designDraftContext.save(referenceLine(trackNumber), alignment).id
        val locationTrack = designDraftContext.save(locationTrack(trackNumber), geometry).id
        trackNumberService.cancel(designBranch, trackNumber)
        val validated =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(
                    trackNumbers = listOf(trackNumber),
                    referenceLines = listOf(referenceLine),
                    locationTracks = listOf(locationTrack),
                ),
            )
        assertEquals(
            listOf("validation.layout.reference-line.track-number.cancelled"),
            validated.validatedAsPublicationUnit.referenceLines[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(
            listOf("validation.layout.location-track.track-number.cancelled"),
            validated.validatedAsPublicationUnit.locationTracks[0].issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `validation reports references to cancelled duplicate location track`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val trackNumber = designOfficialContext.save(trackNumber()).id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        designOfficialContext.save(referenceLine(trackNumber), alignment).id
        val mainLocationTrack = designOfficialContext.save(locationTrack(trackNumber), geometry).id
        val duplicatingLocationTrack =
            designDraftContext.save(locationTrack(trackNumber, duplicateOf = mainLocationTrack), geometry).id
        locationTrackService.cancel(designBranch, mainLocationTrack)

        val validatedWithoutCancellationPublication =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(locationTracks = listOf(duplicatingLocationTrack)),
            )
        assertEquals(
            emptyList<LayoutValidationIssue>(),
            validatedWithoutCancellationPublication.validatedAsPublicationUnit.locationTracks[0].issues,
        )

        val validatedIncludingCancellation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(locationTracks = listOf(duplicatingLocationTrack, mainLocationTrack)),
            )
        assertEquals(
            listOf("validation.layout.location-track.duplicate-of.cancelled"),
            validatedIncludingCancellation.validatedAsPublicationUnit.locationTracks
                .find { it.id == duplicatingLocationTrack }
                ?.issues
                ?.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `validation notices a switch linking going bad due to the correctly linked location track being cancelled`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val trackNumber = designOfficialContext.save(trackNumber()).id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0)))
        designOfficialContext.save(referenceLine(trackNumber), alignment).id
        val switch =
            designOfficialContext
                .save(
                    switch(
                        name = "some switch",
                        joints =
                            listOf(
                                LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 0.0), null),
                                LayoutSwitchJoint(JointNumber(2), SwitchJointRole.CONNECTION, Point(34.4, 0.0), null),
                                LayoutSwitchJoint(JointNumber(3), SwitchJointRole.CONNECTION, Point(34.3, 2.0), null),
                            ),
                    )
                )
                .id
        val throughTrackGeometry =
            trackGeometry(
                edge(
                    endOuterSwitch = switchLinkYV(switch, 1),
                    segments = listOf(segment(Point(-1.0, 0.0), Point(0.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(switch, 1),
                    endInnerSwitch = switchLinkYV(switch, 2),
                    segments = listOf(segment(Point(0.0, 0.0), Point(34.4, 0.0))),
                ),
            )
        val mainOfficialBranchingTrackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(34.4, 2.0)))
        val linkedBranchingTrackGeometry =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(switch, 1),
                    endInnerSwitch = switchLinkYV(switch, 3),
                    segments = listOf(segment(Point(0.0, 0.0), Point(34.4, 2.0))),
                )
            )
        val throughTrack =
            mainOfficialContext.save(locationTrack(trackNumber, topologicalConnectivity = END), throughTrackGeometry).id
        val branchingTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber, topologicalConnectivity = START_AND_END),
                    mainOfficialBranchingTrackGeometry,
                )
                .id
        designDraftContext.save(mainOfficialContext.fetch(throughTrack)!!, throughTrackGeometry)
        designDraftContext.save(mainOfficialContext.fetch(branchingTrack)!!, linkedBranchingTrackGeometry)
        designDraftContext.save(designOfficialContext.fetch(switch)!!)

        // Tl;dr we created some tracks in the main-official context, then in design-draft we
        // created and linked a switch to them. The current situation is OK, we can publish
        // everything with no problem
        val okayValidation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(locationTracks = listOf(throughTrack, branchingTrack), switches = listOf(switch)),
            )
        assertEquals(
            listOf<LayoutValidationIssue>(),
            okayValidation.validatedAsPublicationUnit.locationTracks.flatMap { it.issues },
        )
        assertEquals(
            listOf<LayoutValidationIssue>(),
            okayValidation.validatedAsPublicationUnit.switches.flatMap { it.issues },
        )

        designOfficialContext.save(designDraftContext.fetch(throughTrack)!!, throughTrackGeometry)
        designOfficialContext.save(designDraftContext.fetch(branchingTrack)!!, linkedBranchingTrackGeometry)
        locationTrackService.cancel(designBranch, throughTrack)
        locationTrackService.cancel(designBranch, branchingTrack)

        // .. but if the location tracks are cancelled, then the switch will see the branching
        // alignment is no longer connected. Both the switch and the branching alignment should be blamed, but the
        // through track is innocent.
        val validateCancellation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(locationTracks = listOf(throughTrack, branchingTrack), switches = listOf(switch)),
            )
        assertEquals(
            listOf(
                LayoutValidationIssue(
                    localizationKey =
                        LocalizationKey("validation.layout.switch.track-linkage.switch-alignment-not-connected"),
                    type = LayoutValidationIssueType.WARNING,
                    params = LocalizationParams(mapOf("switch" to "some switch", "alignments" to "1-3")),
                )
            ),
            validateCancellation.validatedAsPublicationUnit.switches[0].issues,
        )
        assertContains(
            validateCancellation.validatedAsPublicationUnit.locationTracks.find { it.id == branchingTrack }!!.issues,
            LayoutValidationIssue(
                localizationKey =
                    LocalizationKey("validation.layout.location-track.switch-linkage.switch-alignment-not-connected"),
                type = LayoutValidationIssueType.WARNING,
                params = LocalizationParams(mapOf("switch" to "some switch", "alignments" to "1-3")),
            ),
        )
        // cancelling the edit to the through track is fine, as it was already correctly linked in main
        assertFalse(
            validateCancellation.validatedAsPublicationUnit.locationTracks
                .find { it.id == throughTrack }!!
                .issues
                .any { it.localizationKey.toString().contains("linkage") }
        )
    }

    @Test
    fun `switch draft OIDs are checked for uniqueness vs existing OIDs`() {
        testDBService.deleteFromTables("layout", "switch_external_id")
        switchDao.insertExternalId(mainOfficialContext.save(switch()).id, LayoutBranch.main, Oid("1.2.3.4.5"))
        val draftSwitch = mainDraftContext.save(switch(draftOid = Oid("1.2.3.4.5"))).id
        assertContains(
            publicationValidationService
                .validateSwitches(ValidateTransition(PublicationInMain), listOf(draftSwitch))[0]
                .errors,
            LayoutValidationIssue(
                localizationKey = LocalizationKey("validation.layout.switch.duplicate-oid"),
                type = LayoutValidationIssueType.ERROR,
                params = LocalizationParams(mapOf()),
            ),
        )
    }

    @Test
    fun `reference line validation notices cancellation of its track number's creation`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val trackNumber = designOfficialContext.save(trackNumber()).id
        val referenceLine =
            designOfficialContext
                .save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        trackNumberService.cancel(designBranch, trackNumber)
        designDraftContext.save(designOfficialContext.fetch(referenceLine)!!)
        val validateTrackNumber =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber)),
            )
        val validateReferenceLine =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(referenceLines = listOf(referenceLine)),
            )
        val validateBoth =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine)),
            )
        assertEquals(
            listOf("validation.layout.track-number.reference-line.cancelled-from-track-number"),
            validateBoth.validatedAsPublicationUnit.trackNumbers[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(
            listOf("validation.layout.reference-line.track-number.cancelled"),
            validateBoth.validatedAsPublicationUnit.referenceLines[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(
            listOf("validation.layout.track-number.reference-line.cancelled-from-track-number"),
            validateTrackNumber.validatedAsPublicationUnit.trackNumbers[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(0, validateReferenceLine.validatedAsPublicationUnit.referenceLines[0].issues.size)
    }

    @Test
    fun `track number validation notices cancellation of its reference line's creation`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val trackNumber = designOfficialContext.save(trackNumber()).id
        val referenceLine =
            designOfficialContext
                .save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        referenceLineService.cancel(designBranch, referenceLine)
        designDraftContext.save(designOfficialContext.fetch(trackNumber)!!)
        val validateBoth =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine)),
            )
        val validateReferenceLine =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(referenceLines = listOf(referenceLine)),
            )
        val validateTrackNumber =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber)),
            )
        // reference line is cancelled (implicitly as part of the track number's cancellation), but the track number's
        // cancellation has then been overwritten, meaning that publishing the reference line should fail
        assertEquals(
            listOf("validation.layout.track-number.reference-line.not-published"),
            validateBoth.validatedAsPublicationUnit.trackNumbers[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(
            listOf("validation.layout.reference-line.track-number.cancelled"),
            validateBoth.validatedAsPublicationUnit.referenceLines[0].issues.map { it.localizationKey.toString() },
        )
        assertEquals(
            listOf("validation.layout.reference-line.track-number.cancelled"),
            validateReferenceLine.validatedAsPublicationUnit.referenceLines[0].issues.map {
                it.localizationKey.toString()
            },
        )
        assertEquals(0, validateTrackNumber.validatedAsPublicationUnit.trackNumbers[0].issues.size)
    }

    @Test
    fun `track number created in main can have design edit cancelled independently of reference line`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val referenceLine =
            mainOfficialContext
                .save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        designDraftContext.save(mainOfficialContext.fetch(trackNumber)!!)
        designDraftContext.save(mainOfficialContext.fetch(referenceLine)!!)
        publicationTestSupportService.publish(
            designBranch,
            publicationRequestIds(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine)),
        )
        trackNumberService.cancel(designBranch, trackNumber)
        designDraftContext.save(designOfficialContext.fetch(referenceLine)!!)
        val validateTrackNumber =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber)),
            )
        val validateReferenceLine =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(referenceLines = listOf(referenceLine)),
            )
        val validateBoth =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine)),
            )
        // track number and reference line were created in main, hence cancelling the track number's edit without
        // the reference line's edit is fine
        assertEquals(0, validateTrackNumber.validatedAsPublicationUnit.trackNumbers[0].issues.size)
        assertEquals(0, validateReferenceLine.validatedAsPublicationUnit.referenceLines[0].issues.size)
        assertEquals(0, validateBoth.validatedAsPublicationUnit.trackNumbers[0].issues.size)
        assertEquals(0, validateBoth.validatedAsPublicationUnit.referenceLines[0].issues.size)
    }

    @Test
    fun `km posts notice their track number's cancellation`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, DRAFT)
        val trackNumberNumber = trackNumber()
        val trackNumber = designOfficialContext.save(trackNumberNumber).id
        val referenceLine =
            designOfficialContext
                .save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        val kmPost = designOfficialContext.save(kmPost(trackNumber, KmNumber(1), kmPostGkLocation(1.0, 0.0))).id
        designDraftContext.save(designOfficialContext.fetch(kmPost)!!)
        trackNumberService.cancel(designBranch, trackNumber)
        referenceLineService.cancel(designBranch, referenceLine)
        val validateTrackNumber =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(trackNumbers = listOf(trackNumber), referenceLines = listOf(referenceLine)),
            )
        val validateKmPost =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(kmPosts = listOf(kmPost)),
            )
        val validateBoth =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(PublicationInDesign(designBranch)),
                publicationRequestIds(
                    trackNumbers = listOf(trackNumber),
                    kmPosts = listOf(kmPost),
                    referenceLines = listOf(referenceLine),
                ),
            )
        assertEquals(
            listOf(
                LayoutValidationIssue(
                    localizationKey = LocalizationKey("validation.layout.track-number.km-post.reference-deleted"),
                    type = LayoutValidationIssueType.ERROR,
                    params = LocalizationParams(mapOf("kmPosts" to "0001")),
                )
            ),
            validateTrackNumber.validatedAsPublicationUnit.trackNumbers[0].issues,
        )
        assertEquals(0, validateKmPost.validatedAsPublicationUnit.kmPosts[0].issues.size)
        assertEquals(
            listOf(
                LayoutValidationIssue(
                    localizationKey = LocalizationKey("validation.layout.track-number.km-post.reference-deleted"),
                    type = LayoutValidationIssueType.ERROR,
                    params = LocalizationParams(mapOf("kmPosts" to "0001")),
                )
            ),
            validateBoth.validatedAsPublicationUnit.trackNumbers[0].issues,
        )
        assertContains(
            validateBoth.validatedAsPublicationUnit.kmPosts[0].issues,
            LayoutValidationIssue(
                localizationKey = LocalizationKey("validation.layout.km-post.track-number.cancelled"),
                type = LayoutValidationIssueType.ERROR,
                params = LocalizationParams(mapOf("trackNumber" to trackNumberNumber.number.toString())),
            ),
        )
    }

    @Test
    fun `track number numbers are checked for uniqueness upon merge, even if any are deleted`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val alignment = alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0)))
        mainOfficialContext.save(referenceLine(mainOfficialContext.save(trackNumber(TrackNumber("100"))).id), alignment)
        mainOfficialContext.save(referenceLine(mainOfficialContext.save(trackNumber(TrackNumber("200"))).id), alignment)
        mainOfficialContext.save(
            referenceLine(mainOfficialContext.save(trackNumber(TrackNumber("300"), state = LayoutState.DELETED)).id),
            alignment,
        )
        mainOfficialContext.save(
            referenceLine(mainOfficialContext.save(trackNumber(TrackNumber("400"), state = LayoutState.DELETED)).id),
            alignment,
        )
        val tn1 = designOfficialContext.save(trackNumber(TrackNumber("100"))).id
        val rl1 = designOfficialContext.save(referenceLine(tn1), alignment).id
        val tn2 = designOfficialContext.save(trackNumber(TrackNumber("200"), state = LayoutState.DELETED)).id
        val rl2 = designOfficialContext.save(referenceLine(tn2), alignment).id
        val tn3 = designOfficialContext.save(trackNumber(TrackNumber("300"))).id
        val rl3 = designOfficialContext.save(referenceLine(tn3), alignment).id
        val tn4 = designOfficialContext.save(trackNumber(TrackNumber("400"), state = LayoutState.DELETED)).id
        val rl4 = designOfficialContext.save(referenceLine(tn4), alignment).id

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(MergeFromDesign(designBranch)),
                publicationRequestIds(
                    trackNumbers = listOf(tn1, tn2, tn3, tn4),
                    referenceLines = listOf(rl1, rl2, rl3, rl4),
                ),
            )
        validation.validatedAsPublicationUnit.trackNumbers.forEach { tn ->
            assertContains(
                tn.issues,
                LayoutValidationIssue(
                    type = LayoutValidationIssueType.FATAL,
                    localizationKey = LocalizationKey("validation.layout.track-number.duplicate-name-official"),
                    params = LocalizationParams(mapOf("trackNumber" to tn.number.toString())),
                ),
            )
        }
    }

    @Test
    fun `km post km numbers are checked for uniqueness upon merge, even if any are deleted`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)
        val trackNumber = trackNumber()
        val trackNumberId = mainOfficialContext.save(trackNumber).id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0)))
        mainOfficialContext.save(referenceLine(trackNumberId), alignment).id

        mainOfficialContext.save(kmPost(trackNumberId, KmNumber(1), kmPostGkLocation(1.0, 0.0)))
        mainOfficialContext.save(kmPost(trackNumberId, KmNumber(2), kmPostGkLocation(2.0, 0.0)))
        mainOfficialContext.save(
            kmPost(trackNumberId, KmNumber(3), kmPostGkLocation(3.0, 0.0), state = LayoutState.DELETED)
        )
        mainOfficialContext.save(
            kmPost(trackNumberId, KmNumber(4), kmPostGkLocation(4.0, 0.0), state = LayoutState.DELETED)
        )
        val kp1 = designOfficialContext.save(kmPost(trackNumberId, KmNumber(1), kmPostGkLocation(1.0, 0.0))).id
        val kp2 =
            designOfficialContext
                .save(kmPost(trackNumberId, KmNumber(2), kmPostGkLocation(2.0, 0.0), state = LayoutState.DELETED))
                .id
        val kp3 = designOfficialContext.save(kmPost(trackNumberId, KmNumber(3), kmPostGkLocation(3.0, 0.0))).id
        val kp4 =
            designOfficialContext
                .save(kmPost(trackNumberId, KmNumber(4), kmPostGkLocation(4.0, 0.0), state = LayoutState.DELETED))
                .id

        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(MergeFromDesign(designBranch)),
                publicationRequestIds(kmPosts = listOf(kp1, kp2, kp3, kp4)),
            )

        validation.validatedAsPublicationUnit.kmPosts.forEach { kmPost ->
            assertContains(
                kmPost.issues,
                LayoutValidationIssue(
                    type = LayoutValidationIssueType.FATAL,
                    localizationKey = LocalizationKey("validation.layout.km-post.duplicate-name-official"),
                    params =
                        LocalizationParams(
                            mapOf(
                                "kmNumber" to kmPost.kmNumber.toString(),
                                "trackNumber" to trackNumber.number.toString(),
                            )
                        ),
                ),
            )
        }
    }

    private fun getTopologicalSwitchConnectionTestCases(
        trackNumberGenerator: () -> IntId<LayoutTrackNumber>,
        topologyStartSwitch: Pair<SwitchLink, Point>,
        topologyEndSwitch: Pair<SwitchLink, Point>,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        val (startSwitch, startPoint) = topologyStartSwitch
        val (endSwitch, endPoint) = topologyEndSwitch
        return listOf(
            locationTrack(trackNumberGenerator(), draft = true) to
                topologyTrackGeometry(startSwitch = null, endSwitch = null, startPoint, endPoint),
            locationTrack(trackNumberGenerator(), topologicalConnectivity = START, draft = true) to
                topologyTrackGeometry(startSwitch = startSwitch, endSwitch = null, startPoint, endPoint),
            locationTrack(trackNumberGenerator(), topologicalConnectivity = END, draft = true) to
                topologyTrackGeometry(startSwitch = null, endSwitch = endSwitch, startPoint, endPoint),
            locationTrack(trackNumberGenerator(), topologicalConnectivity = START_AND_END, draft = true) to
                topologyTrackGeometry(startSwitch = startSwitch, endSwitch = endSwitch, startPoint, endPoint),
        )
    }

    private fun topologyTrackGeometry(
        startSwitch: SwitchLink?,
        endSwitch: SwitchLink?,
        start: Point,
        end: Point,
    ): TmpLocationTrackGeometry =
        trackGeometry(
            edge(startOuterSwitch = startSwitch, endOuterSwitch = endSwitch, segments = listOf(segment(start, end)))
        )

    private data class TopologicalSwitchConnectionTestData(
        val locationTracksUnderTest: List<Pair<IntId<LocationTrack>, LocationTrack>>,
        val switchIdsUnderTest: List<IntId<LayoutSwitch>>,
        val switchInnerTrackIds: List<IntId<LocationTrack>>,
    )

    private fun getTopologicalSwitchConnectionTestData(): TopologicalSwitchConnectionTestData {
        val (topologyStartSwitchId, topologyStartSwitchInnerTrackIds) =
            mainDraftContext.createSwitchWithInnerTracks(
                name = "Topological switch connection test start switch",
                listOf(JointNumber(1) to Point(0.0, 0.0), JointNumber(3) to Point(1.0, 0.0)),
            )
        val (topologyEndSwitchId, topologyEndSwitchInnerTrackIds) =
            mainDraftContext.createSwitchWithInnerTracks(
                name = "Topological switch connection test end switch",
                listOf(JointNumber(1) to Point(2.0, 0.0), JointNumber(3) to Point(3.0, 0.0)),
            )

        val locationTracksUnderTest =
            getTopologicalSwitchConnectionTestCases(
                { mainOfficialContext.createLayoutTrackNumber().id },
                switchLinkYV(topologyStartSwitchId, 1) to Point(0.0, 0.0),
                switchLinkYV(topologyEndSwitchId, 3) to Point(3.0, 0.0),
            )
        println(locationTracksUnderTest.map { (t, g) -> t.id to g.nodesWithLocation })

        val locationTrackIdsUnderTest =
            locationTracksUnderTest
                .map { (track, geometry) -> locationTrackDao.save(asMainDraft(track), geometry) }
                .map { v -> v.id to locationTrackDao.fetch(v) }

        return TopologicalSwitchConnectionTestData(
            locationTracksUnderTest = locationTrackIdsUnderTest,
            switchIdsUnderTest = listOf(topologyStartSwitchId, topologyEndSwitchId),
            switchInnerTrackIds = topologyStartSwitchInnerTrackIds + topologyEndSwitchInnerTrackIds,
        )
    }

    private fun validateLocationTracks(vararg locationTracks: IntId<LocationTrack>): List<LayoutValidationIssue> =
        validateLocationTracks(locationTracks.toList())

    private fun validateLocationTracks(
        locationTracks: List<IntId<LocationTrack>>,
        transition: LayoutContextTransition = PublicationInMain,
    ): List<LayoutValidationIssue> {
        val publicationRequest = publicationRequestIds(locationTracks = locationTracks)
        val validation =
            publicationValidationService.validatePublicationCandidates(
                publicationService.collectPublicationCandidates(transition),
                publicationRequest,
            )

        return validation.validatedAsPublicationUnit.locationTracks.flatMap { it.issues }
    }
}
