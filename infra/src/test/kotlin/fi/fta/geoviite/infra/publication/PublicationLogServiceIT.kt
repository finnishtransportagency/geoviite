package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.linking.LayoutKmPostSaveRequest
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.switches.LayoutSwitchSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.tracklayout.trackNumberSaveRequest
import fi.fta.geoviite.infra.util.SortOrder
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import publicationRequest
import publish

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationLogServiceIT
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationLogService: PublicationLogService,
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
    val localizationService: LocalizationService,
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
    val layoutDesignDao: LayoutDesignDao,
    val geographyService: GeographyService,
    val publicationTestSupportService: PublicationTestSupportService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        publicationTestSupportService.cleanupPublicationTables()
    }

    @Test
    fun fetchingPublicationListingWorks() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        val (track, alignment) = locationTrackAndAlignment(trackNumberId, draft = true)
        val draftId = locationTrackService.saveDraft(LayoutBranch.main, track, alignment).id
        assertThrows<NoSuchEntityException> {
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.official, draftId)
        }
        assertEquals(draftId, locationTrackService.getOrThrow(MainLayoutContext.draft, draftId).id)

        val publicationCountBeforePublishing = publicationLogService.fetchPublications(LayoutBranch.main).size

        val publicationResult =
            publish(publicationService, trackNumbers = listOf(trackNumberId), locationTracks = listOf(draftId))

        val publicationCountAfterPublishing = publicationLogService.fetchPublications(LayoutBranch.main)

        assertEquals(publicationCountBeforePublishing + 1, publicationCountAfterPublishing.size)
        assertEquals(publicationResult.publicationId, publicationCountAfterPublishing.last().id)
    }

    @Test
    fun `should sort publications by publication time in descending order`() {
        val trackNumber1Id = mainDraftContext.createLayoutTrackNumber().id
        val trackNumber2Id = mainDraftContext.createLayoutTrackNumber().id
        val publish1Result =
            publicationRequest(trackNumbers = listOf(trackNumber1Id, trackNumber2Id)).let { r ->
                val versions = publicationService.getValidationVersions(LayoutBranch.main, r)
                publicationTestSupportService.testPublish(
                    LayoutBranch.main,
                    versions,
                    publicationTestSupportService.getCalculatedChangesInRequest(versions),
                )
            }

        assertEquals(2, publish1Result.trackNumbers)

        val trackNumber1 = trackNumberService.get(MainLayoutContext.official, trackNumber1Id)
        val trackNumber2 = trackNumberService.get(MainLayoutContext.official, trackNumber2Id)
        assertNotNull(trackNumber1)
        assertNotNull(trackNumber2)

        val newTrackNumber1TrackNumber = "${trackNumber1.number} ZZZ"

        trackNumberService.saveDraft(
            LayoutBranch.main,
            trackNumber1.copy(number = TrackNumber(newTrackNumber1TrackNumber)),
        )
        val publish2Result =
            publicationRequest(trackNumbers = listOf(trackNumber1Id)).let { r ->
                val versions = publicationService.getValidationVersions(LayoutBranch.main, r)
                publicationTestSupportService.testPublish(
                    LayoutBranch.main,
                    versions,
                    publicationTestSupportService.getCalculatedChangesInRequest(versions),
                )
            }

        assertEquals(1, publish2Result.trackNumbers)
    }

    @Test
    fun `Track number diff finds all changed fields`() {
        val (trackNumberId, trackNumber) =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(
                        number = testDBService.getUnusedTrackNumber(),
                        description = "TEST",
                        state = LayoutState.IN_USE,
                    ),
                )
                .let { r -> r.id to trackNumberDao.fetch(r) }
        val rl = referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumber.id as IntId)!!
        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId), referenceLines = listOf(rl.id as IntId)),
        )
        trackNumberService.update(
            LayoutBranch.main,
            trackNumberId,
            trackNumberSaveRequest(
                number = TrackNumber(trackNumber.number.value + " T"),
                description = "${trackNumber.description}_TEST",
                state = LayoutState.NOT_IN_USE,
            ),
        )
        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)),
        )
        val thisAndPreviousPublication =
            publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val changes =
            publicationDao.fetchPublicationTrackNumberChanges(
                LayoutBranch.main,
                thisAndPreviousPublication.first().id,
                thisAndPreviousPublication.last().publicationTime,
            )

        val diff =
            publicationLogService.diffTrackNumber(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(trackNumber.id as IntId),
                thisAndPreviousPublication.first().publicationTime,
                thisAndPreviousPublication.last().publicationTime,
            ) { _, _ ->
                null
            }
        assertEquals(3, diff.size)
        assertEquals("track-number", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
        assertEquals("description", diff[2].propKey.key.toString())
    }

    @Test
    fun `Changing specific Track Number field returns only that field`() {
        val (trackNumberId, trackNumber) =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(
                        number = testDBService.getUnusedTrackNumber(),
                        description = "TEST",
                        state = LayoutState.IN_USE,
                    ),
                )
                .let { r -> r.id to trackNumberDao.fetch(r) }
        val rl = referenceLineService.getByTrackNumber(MainLayoutContext.draft, trackNumber.id as IntId)!!
        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId), referenceLines = listOf(rl.id as IntId)),
        )

        val idOfUpdated =
            trackNumberService
                .update(
                    LayoutBranch.main,
                    trackNumberId,
                    trackNumberSaveRequest(
                        number = trackNumber.number,
                        description = "TEST2",
                        state = trackNumber.state,
                    ),
                )
                .id
        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(trackNumbers = listOf(trackNumber.id as IntId)),
        )
        val thisAndPreviousPublication =
            publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val changes =
            publicationDao.fetchPublicationTrackNumberChanges(
                LayoutBranch.main,
                thisAndPreviousPublication.first().id,
                thisAndPreviousPublication.last().publicationTime,
            )
        val updatedTrackNumber = trackNumberService.getOrThrow(MainLayoutContext.official, idOfUpdated)

        val diff =
            publicationLogService.diffTrackNumber(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(trackNumber.id as IntId),
                thisAndPreviousPublication.first().publicationTime,
                thisAndPreviousPublication.last().publicationTime,
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("description", diff[0].propKey.key.toString())
        assertEquals(trackNumber.description, diff[0].value.oldValue)
        assertEquals(updatedTrackNumber.description, diff[0].value.newValue)
    }

    @Test
    fun `Location track diff finds all changed fields`() {
        val duplicate =
            locationTrackDao.fetch(
                locationTrackService.insert(
                    LayoutBranch.main,
                    LocationTrackSaveRequest(
                        AlignmentName("TEST duplicate"),
                        LocationTrackDescriptionBase("Test"),
                        LocationTrackDescriptionSuffix.NONE,
                        LocationTrackType.MAIN,
                        LocationTrackState.IN_USE,
                        mainDraftContext.createLayoutTrackNumber().id,
                        null,
                        TopologicalConnectivityType.NONE,
                        IntId(1),
                    ),
                )
            )

        val duplicate2 =
            locationTrackDao.fetch(
                locationTrackService.insert(
                    LayoutBranch.main,
                    LocationTrackSaveRequest(
                        AlignmentName("TEST duplicate 2"),
                        LocationTrackDescriptionBase("Test"),
                        LocationTrackDescriptionSuffix.NONE,
                        LocationTrackType.MAIN,
                        LocationTrackState.IN_USE,
                        mainDraftContext.createLayoutTrackNumber().id,
                        null,
                        TopologicalConnectivityType.NONE,
                        IntId(1),
                    ),
                )
            )

        val locationTrack =
            locationTrackDao.fetch(
                locationTrackService.insert(
                    LayoutBranch.main,
                    LocationTrackSaveRequest(
                        AlignmentName("TEST"),
                        LocationTrackDescriptionBase("Test"),
                        LocationTrackDescriptionSuffix.NONE,
                        LocationTrackType.MAIN,
                        LocationTrackState.IN_USE,
                        mainDraftContext.createLayoutTrackNumber().id,
                        duplicate.id as IntId<LocationTrack>,
                        TopologicalConnectivityType.NONE,
                        IntId(1),
                    ),
                )
            )
        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(
                locationTracks =
                    listOf(
                        locationTrack.id as IntId<LocationTrack>,
                        duplicate.id as IntId<LocationTrack>,
                        duplicate2.id as IntId<LocationTrack>,
                    )
            ),
        )

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService.update(
                    LayoutBranch.main,
                    locationTrack.id as IntId,
                    LocationTrackSaveRequest(
                        name = AlignmentName("TEST2"),
                        descriptionBase = LocationTrackDescriptionBase("Test2"),
                        descriptionSuffix = LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER,
                        type = LocationTrackType.SIDE,
                        state = LocationTrackState.NOT_IN_USE,
                        trackNumberId = locationTrack.trackNumberId,
                        duplicate2.id as IntId<LocationTrack>,
                        topologicalConnectivity = TopologicalConnectivityType.START_AND_END,
                        IntId(1),
                    ),
                )
            )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(locationTrack.id as IntId<LocationTrack>),
                null,
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                emptySet(),
            ) { _, _ ->
                null
            }
        assertEquals(6, diff.size)
        assertEquals("location-track", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
        assertEquals("location-track-type", diff[2].propKey.key.toString())
        assertEquals("description-base", diff[3].propKey.key.toString())
        assertEquals("description-suffix", diff[4].propKey.key.toString())
        assertEquals("duplicate-of", diff[5].propKey.key.toString())
    }

    @Test
    fun `Changing specific Location Track field returns only that field`() {
        val saveReq =
            LocationTrackSaveRequest(
                AlignmentName("TEST"),
                LocationTrackDescriptionBase("Test"),
                LocationTrackDescriptionSuffix.NONE,
                LocationTrackType.MAIN,
                LocationTrackState.IN_USE,
                mainOfficialContext.createLayoutTrackNumber().id,
                null,
                TopologicalConnectivityType.NONE,
                IntId(1),
            )

        val locationTrack = locationTrackDao.fetch(locationTrackService.insert(LayoutBranch.main, saveReq))
        publish(publicationService, locationTracks = listOf(locationTrack.id as IntId<LocationTrack>))

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService.update(
                    LayoutBranch.main,
                    locationTrack.id as IntId,
                    saveReq.copy(descriptionBase = LocationTrackDescriptionBase("TEST2")),
                )
            )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId<LocationTrack>))
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(locationTrack.id as IntId<LocationTrack>),
                null,
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                emptySet(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("description-base", diff[0].propKey.key.toString())
        assertEquals(locationTrack.descriptionBase, diff[0].value.oldValue)
        assertEquals(updatedLocationTrack.descriptionBase, diff[0].value.newValue)
    }

    @Test
    fun `KM Post diff finds all changed fields`() {
        val trackNumberId =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(number = testDBService.getUnusedTrackNumber(), description = "TEST"),
                )
                .id
        val trackNumber2Id =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveRequest(number = testDBService.getUnusedTrackNumber(), description = "TEST 2"),
                )
                .id

        val kmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.insertKmPost(
                    LayoutBranch.main,
                    LayoutKmPostSaveRequest(
                        KmNumber(0),
                        LayoutState.IN_USE,
                        trackNumberId,
                        gkLocation = null,
                        sourceId = null,
                    ),
                ),
            )
        publish(
            publicationService,
            kmPosts = listOf(kmPost.id as IntId),
            trackNumbers = listOf(trackNumberId, trackNumber2Id),
        )
        val updatedKmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.updateKmPost(
                    LayoutBranch.main,
                    kmPost.id as IntId,
                    LayoutKmPostSaveRequest(
                        KmNumber(1),
                        LayoutState.NOT_IN_USE,
                        trackNumber2Id,
                        gkLocation = null,
                        sourceId = null,
                    ),
                ),
            )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff =
            publicationLogService.diffKmPost(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(kmPost.id as IntId),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(),
                { _, _ -> null },
                { geographyService.getCoordinateSystem(it).name.toString() },
            )
        assertEquals(2, diff.size)
        // assertEquals("track-number", diff[0].propKey) TODO Enable when track number switching
        // works
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals("state", diff[1].propKey.key.toString())
    }

    @Test
    fun `simple km post change in design is reported`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val kmPost = mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1))).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        kmPostService.saveDraft(testBranch, mainOfficialContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publish(publicationService, testBranch, kmPosts = listOf(kmPost))
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        publish(publicationService, kmPosts = listOf(kmPost))
        val diff = getLatestPublicationDiffForKmPost(kmPost)
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals(KmNumber(1), diff[0].value.oldValue)
        assertEquals(KmNumber(2), diff[0].value.newValue)
    }

    private fun getLatestPublicationDiffForKmPost(
        id: IntId<LayoutKmPost>
    ): List<PublicationChange<out Comparable<Nothing>?>> {
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)
        return publicationLogService.diffKmPost(
            localizationService.getLocalization(LocalizationLanguage.FI),
            changes.getValue(id),
            latestPub.publicationTime,
            latestPub.publicationTime.minusMillis(1),
            trackNumberDao.fetchTrackNumberNames(),
            { _, _ -> null },
            { geographyService.getCoordinateSystem(it).name.toString() },
        )
    }

    @Test
    fun `Changing specific KM Post field returns only that field`() {
        val saveReq =
            LayoutKmPostSaveRequest(
                KmNumber(0),
                LayoutState.IN_USE,
                mainOfficialContext.createLayoutTrackNumber().id,
                gkLocation = null,
                sourceId = null,
            )

        val kmPost =
            kmPostService.getOrThrow(MainLayoutContext.draft, kmPostService.insertKmPost(LayoutBranch.main, saveReq))
        publish(publicationService, kmPosts = listOf(kmPost.id as IntId))
        val updatedKmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.updateKmPost(LayoutBranch.main, kmPost.id as IntId, saveReq.copy(kmNumber = KmNumber(1))),
            )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff =
            publicationLogService.diffKmPost(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(kmPost.id as IntId),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(),
                { _, _ -> null },
                { geographyService.getCoordinateSystem(it).name.toString() },
            )
        assertEquals(1, diff.size)
        assertEquals("km-post", diff[0].propKey.key.toString())
        assertEquals(kmPost.kmNumber, diff[0].value.oldValue)
        assertEquals(updatedKmPost.kmNumber, diff[0].value.newValue)
    }

    @Test
    fun `Switch diff finds all changed fields`() {
        val trackNumberSaveReq =
            TrackNumberSaveRequest(
                testDBService.getUnusedTrackNumber(),
                TrackNumberDescription("TEST"),
                LayoutState.IN_USE,
                TrackMeter(0, 0),
            )
        val tn1 = trackNumberService.insert(LayoutBranch.main, trackNumberSaveReq).id
        val tn2 =
            trackNumberService
                .insert(
                    LayoutBranch.main,
                    trackNumberSaveReq.copy(testDBService.getUnusedTrackNumber(), TrackNumberDescription("TEST 2")),
                )
                .id

        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.insertSwitch(
                    LayoutBranch.main,
                    LayoutSwitchSaveRequest(SwitchName("TEST"), IntId(1), LayoutStateCategory.EXISTING, IntId(1), false),
                ),
            )
        publish(publicationService, switches = listOf(switch.id as IntId), trackNumbers = listOf(tn1, tn2))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(
                    LayoutBranch.main,
                    switch.id as IntId,
                    LayoutSwitchSaveRequest(
                        SwitchName("TEST 2"),
                        IntId(2),
                        LayoutStateCategory.NOT_EXISTING,
                        IntId(2),
                        true,
                    ),
                ),
            )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationLogService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id as IntId),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.DELETE,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(5, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals("state-category", diff[1].propKey.key.toString())
        assertEquals("switch-type", diff[2].propKey.key.toString())
        assertEquals("trap-point", diff[3].propKey.key.toString())
        assertEquals("owner", diff[4].propKey.key.toString())
    }

    @Test
    fun `Changing specific switch field returns only that field`() {
        val saveReq =
            LayoutSwitchSaveRequest(SwitchName("TEST"), IntId(1), LayoutStateCategory.EXISTING, IntId(1), false)

        val switch =
            switchService.getOrThrow(MainLayoutContext.draft, switchService.insertSwitch(LayoutBranch.main, saveReq))
        publish(publicationService, switches = listOf(switch.id as IntId))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(
                    LayoutBranch.main,
                    switch.id as IntId,
                    saveReq.copy(name = SwitchName("TEST 2")),
                ),
            )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationLogService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id as IntId),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals(switch.name, diff[0].value.oldValue)
        assertEquals(updatedSwitch.name, diff[0].value.newValue)
    }

    @Test
    fun `fetchPublicationLocationTrackSwitchLinkChanges() in main does not include switch changes in designs`() {
        val switch = switchDao.save(switch(name = "sw", draft = false)).id
        switchDao.insertExternalId(switch, LayoutBranch.main, Oid("1.1.1.1.2"))
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    alignmentWithSwitchLinks(switch),
                )
                .id

        val firstMainPublicationId =
            publish(publicationService, locationTracks = listOf(locationTrackId))
                .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-02T00:00:00Z")) }
                .publicationId!!

        val designBranch = testDBService.createDesignBranch()
        switchService.saveDraft(
            designBranch,
            switchDao.getOrThrow(MainLayoutContext.official, switch).copy(name = SwitchName("sw-edited-in-design")),
        )

        locationTrackService.saveDraft(
            designBranch,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            alignmentWithSwitchLinks(),
        )

        publish(
                publicationService,
                branch = designBranch,
                locationTracks = listOf(locationTrackId),
                switches = listOf(switch),
            )
            .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-03T00:00:00Z")) }
            .publicationId!!

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            alignmentWithSwitchLinks(),
        )
        val secondMainPublicationId =
            publish(publicationService, locationTracks = listOf(locationTrackId))
                .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-04T00:00:00Z")) }
                .publicationId!!

        val fullInterval =
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(
                null,
                LayoutBranch.main,
                Instant.parse("2024-01-02T00:00:00Z"),
                Instant.parse("2024-01-04T00:00:00Z"),
            )
        val expect = expectLocationTrackSwitchLinkChanges(locationTrackId)
        val switchChangeIds = switch to SwitchChangeIds("sw", Oid("1.1.1.1.2"))
        val expectedFirstPublicationInMain = expect(firstMainPublicationId, mapOf(), mapOf(switchChangeIds))
        val expectedSecondPublicationInMain = expect(secondMainPublicationId, mapOf(switchChangeIds), mapOf())
        assertEquals(mapOf(expectedFirstPublicationInMain, expectedSecondPublicationInMain), fullInterval)
    }

    @Test
    fun `fetchPublicationLocationTrackSwitchLinkChanges() can filter by design branch and is not confused by design publications`() {
        val switchAddedAndRemoved = switchDao.save(switch(name = "sw-added-and-later-removed", draft = false)).id
        val switchFurtherAddedInMain = switchDao.save(switch(name = "sw-later-added-in-main", draft = false)).id
        val switchFurtherAddedInDesign = switchDao.save(switch(name = "sw-later-added-in-design", draft = false)).id
        switchDao.insertExternalId(switchAddedAndRemoved, LayoutBranch.main, Oid("1.1.1.1.2"))
        switchDao.insertExternalId(switchFurtherAddedInMain, LayoutBranch.main, Oid("1.1.1.1.5"))
        switchDao.insertExternalId(switchFurtherAddedInDesign, LayoutBranch.main, Oid("1.1.1.1.6"))

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    alignmentWithSwitchLinks(switchAddedAndRemoved),
                )
                .id

        val firstMainPublicationId =
            publish(publicationService, locationTracks = listOf(locationTrackId))
                .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-02T00:00:00Z")) }
                .publicationId!!

        // confuser design publication in the middle, to test filtering
        val designBranch = testDBService.createDesignBranch()
        locationTrackService.saveDraft(
            designBranch,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            alignmentWithSwitchLinks(switchFurtherAddedInDesign),
        )
        publish(publicationService, branch = designBranch, locationTracks = listOf(locationTrackId))
            .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-03T00:00:00Z")) }
            .publicationId!!

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            alignmentWithSwitchLinks(switchFurtherAddedInMain),
        )
        val secondMainPublicationId =
            publish(publicationService, locationTracks = listOf(locationTrackId))
                .also { result -> setPublicationTime(result.publicationId!!, Instant.parse("2024-01-04T00:00:00Z")) }
                .publicationId!!

        val firstInterval =
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(
                null,
                LayoutBranch.main,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
            )
        val fullInterval =
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(
                null,
                LayoutBranch.main,
                Instant.parse("2024-01-02T00:00:00Z"),
                Instant.parse("2024-01-04T00:00:00Z"),
            )
        val expect = expectLocationTrackSwitchLinkChanges(locationTrackId)
        val expectedFirstPublicationInMain =
            expect(
                firstMainPublicationId,
                mapOf(),
                mapOf(switchAddedAndRemoved to SwitchChangeIds("sw-added-and-later-removed", Oid("1.1.1.1.2"))),
            )
        val expectedSecondPublicationInMain =
            expect(
                secondMainPublicationId,
                mapOf(switchAddedAndRemoved to SwitchChangeIds("sw-added-and-later-removed", Oid("1.1.1.1.2"))),
                mapOf(switchFurtherAddedInMain to SwitchChangeIds("sw-later-added-in-main", Oid("1.1.1.1.5"))),
            )
        assertEquals(mapOf(expectedFirstPublicationInMain), firstInterval)
        assertEquals(mapOf(expectedFirstPublicationInMain, expectedSecondPublicationInMain), fullInterval)
    }

    private fun expectLocationTrackSwitchLinkChanges(
        locationTrackId: IntId<LocationTrack>
    ): (
        publicationId: IntId<Publication>,
        old: Map<IntId<LayoutSwitch>, SwitchChangeIds>,
        new: Map<IntId<LayoutSwitch>, SwitchChangeIds>,
    ) -> Pair<IntId<Publication>, Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>> =
        { publicationId, old, new ->
            publicationId to mapOf(locationTrackId to LocationTrackPublicationSwitchLinkChanges(old, new))
        }

    @Test
    fun `Location track switch link changes are reported`() {
        val switchUnlinkedFromTopology = switchDao.save(switch(name = "sw-unlinked-from-topology", draft = false))
        val switchUnlinkedFromAlignment = switchDao.save(switch(name = "sw-unlinked-from-alignment", draft = false))
        val switchAddedToTopologyStart = switchDao.save(switch(name = "sw-added-to-topo-start", draft = false))
        val switchAddedToTopologyEnd = switchDao.save(switch(name = "sw-added-to-topo-end", draft = false))
        val switchAddedToAlignment = switchDao.save(switch(name = "sw-added-to-alignment", draft = false))
        val switchDeleted = switchDao.save(switch(name = "sw-deleted", draft = false))
        val switchMerelyRenamed = switchDao.save(switch(name = "sw-merely-renamed", draft = false))
        val originalSwitchReplacedWithNewSameName =
            switchDao.save(switch(name = "sw-replaced-with-new-same-name", draft = false))

        switchDao.insertExternalId(switchUnlinkedFromTopology.id, LayoutBranch.main, Oid("1.1.1.1.1"))
        switchDao.insertExternalId(switchUnlinkedFromAlignment.id, LayoutBranch.main, Oid("1.1.1.1.2"))
        switchDao.insertExternalId(switchAddedToTopologyStart.id, LayoutBranch.main, Oid("1.1.1.1.3"))
        switchDao.insertExternalId(switchAddedToTopologyEnd.id, LayoutBranch.main, Oid("1.1.1.1.4"))
        switchDao.insertExternalId(switchAddedToAlignment.id, LayoutBranch.main, Oid("1.1.1.1.5"))
        switchDao.insertExternalId(switchDeleted.id, LayoutBranch.main, Oid("1.1.1.1.6"))
        switchDao.insertExternalId(switchMerelyRenamed.id, LayoutBranch.main, Oid("1.1.1.1.7"))
        switchDao.insertExternalId(originalSwitchReplacedWithNewSameName.id, LayoutBranch.main, Oid("1.1.1.1.8"))

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id

        val originalLocationTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchUnlinkedFromTopology.id, JointNumber(1)),
                    draft = true,
                ),
                alignmentWithSwitchLinks(
                    switchUnlinkedFromAlignment.id,
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                ),
            )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao.fetch(switchDeleted).copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao
                .fetch(originalSwitchReplacedWithNewSameName)
                .copy(stateCategory = LayoutStateCategory.NOT_EXISTING),
        )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao.fetch(switchMerelyRenamed).copy(name = SwitchName("sw-with-new-name")),
        )
        val newSwitchReplacingOldWithSameName =
            switchService.saveDraft(LayoutBranch.main, switch(name = "sw-replaced-with-new-same-name", draft = true))
        switchDao.insertExternalId(newSwitchReplacingOldWithSameName.id, LayoutBranch.main, Oid("1.1.1.1.9"))

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao
                .getOrThrow(MainLayoutContext.official, originalLocationTrack.id)
                .copy(
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyStart.id, JointNumber(1)),
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchAddedToTopologyEnd.id, JointNumber(1)),
                ),
            alignmentWithSwitchLinks(
                switchAddedToAlignment.id,
                switchMerelyRenamed.id,
                newSwitchReplacingOldWithSameName.id,
                null,
            ),
        )
        publish(
            publicationService,
            locationTracks = listOf(originalLocationTrack.id),
            switches =
                listOf(
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                    newSwitchReplacingOldWithSameName.id,
                ),
        )
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs[0]
        val previousPub = latestPubs[1]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                setOf(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("linked-switches", diff[0].propKey.key.toString())
        assertEquals(
            """
                Vaihteiden sw-deleted, sw-replaced-with-new-same-name (1.1.1.1.8), sw-unlinked-from-alignment,
                sw-unlinked-from-topology linkitys purettu. Vaihteet sw-added-to-alignment, sw-added-to-topo-end,
                sw-added-to-topo-start, sw-replaced-with-new-same-name (1.1.1.1.9) linkitetty.
            """
                .trimIndent()
                .replace("\n", " "),
            diff[0].remark,
        )
    }

    @Test
    fun `Location track geometry changes are reported`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        fun segmentWithCurveToMaxY(maxY: Double) =
            segment(
                *(0..10)
                    .map { x -> Point(x.toDouble(), (5.0 - (x.toDouble() - 5.0).absoluteValue) / 10.0 * maxY) }
                    .toTypedArray()
            )

        val referenceLineAlignment = alignmentDao.insert(alignment(segmentWithCurveToMaxY(0.0)))
        referenceLineDao.save(referenceLine(trackNumberId, alignmentVersion = referenceLineAlignment, draft = false))

        // track that had bump to y=-10 goes to having a bump to y=10, meaning the length and ends
        // stay the same,
        // but the geometry changes
        val originalAlignment = alignment(segmentWithCurveToMaxY(-10.0))
        val newAlignment = alignment(segmentWithCurveToMaxY(10.0))
        val originalLocationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(originalLocationTrack)),
            newAlignment,
        )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(latestPub.id)[originalLocationTrack.id],
                LayoutBranch.main,
                latestPub.publicationTime,
                latestPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(),
                setOf(KmNumber(0)),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("Muutos välillä 0000+0001-0000+0009, sivusuuntainen muutos 10.0 m", diff[0].remark)
    }

    @Test
    fun `should filter publication details by dates`() {
        val trackNumberId1 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish2 =
            publish(publicationService, trackNumbers = listOf(trackNumberId2), locationTracks = listOf(locationTrack2))

        val publication1 = publicationDao.getPublication(publish1.publicationId!!)
        val publication2 = publicationDao.getPublication(publish2.publicationId!!)

        assertTrue {
            publicationLogService
                .fetchPublicationDetailsBetweenInstants(LayoutBranch.main, to = publication1.publicationTime)
                .isEmpty()
        }

        assertTrue {
            publicationLogService
                .fetchPublicationDetailsBetweenInstants(
                    LayoutBranch.main,
                    from = publication2.publicationTime.plusMillis(1),
                )
                .isEmpty()
        }

        assertEquals(
            2,
            publicationLogService
                .fetchPublicationDetailsBetweenInstants(
                    LayoutBranch.main,
                    from = publication1.publicationTime,
                    to = publication2.publicationTime.plusMillis(1),
                )
                .size,
        )
    }

    @Test
    fun `should fetch latest publications`() {
        val trackNumberId1 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndAlignment(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndAlignment(trackNumberId2, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish2 =
            publish(publicationService, trackNumbers = listOf(trackNumberId2), locationTracks = listOf(locationTrack2))

        assertEquals(2, publicationLogService.fetchPublications(LayoutBranch.main).size)

        assertEquals(1, publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items.size)
        assertEquals(
            publish2.publicationId,
            publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0].id,
        )

        assertEquals(2, publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items.size)
        assertEquals(
            publish1.publicationId,
            publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 10).items[1].id,
        )

        assertTrue { publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 0).items.isEmpty() }
    }

    @Test
    fun `should sort publications by header column`() {
        val trackNumberId1 = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId1, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId1))

        val trackNumberId2 = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("4321")).id as IntId
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId2))

        val rows1 =
            publicationLogService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.NAME,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows1.size)
        assertTrue { rows1[0].name.contains("1234") }

        val rows2 =
            publicationLogService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.NAME,
                order = SortOrder.DESCENDING,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows2.size)
        assertTrue { rows2[0].name.contains("4321") }

        val rows3 =
            publicationLogService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.PUBLICATION_TIME,
                order = SortOrder.ASCENDING,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
            )

        assertEquals(2, rows3.size)
        assertTrue { rows3[0].name.contains("1234") }
    }

    @Test
    fun `switch diff consistently uses segment point for joint location`() {
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineDao.save(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                draft = false,
            )
        )
        val switch =
            switchDao.save(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)), draft = false)
            )
        val originalAlignment =
            alignment(
                segment(Point(0.0, 0.0), Point(4.0, 0.0)),
                segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        val locationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao.fetch(switch).copy(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null))),
        )
        val updatedAlignment =
            alignment(
                segment(Point(0.1, 0.0), Point(4.1, 0.0)),
                segment(Point(4.1, 0.0), Point(10.1, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        locationTrackService.saveDraft(LayoutBranch.main, locationTrackDao.fetch(locationTrack), updatedAlignment)

        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationLogService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(2, diff.size)
        assertEquals(
            listOf("switch-joint-location", "switch-track-address").sorted(),
            diff.map { it.propKey.key.toString() }.sorted(),
        )
        val jointLocationDiff = diff.find { it.propKey.key.toString() == "switch-joint-location" }!!
        assertEquals("4.000 E, 0.000 N", jointLocationDiff.value.oldValue)
        assertEquals("4.100 E, 0.000 N", jointLocationDiff.value.newValue)
        val trackAddressDiff = diff.find { it.propKey.key.toString() == "switch-track-address" }!!
        assertEquals("0000+0004.100", trackAddressDiff.value.newValue)
    }

    @Test
    fun `switch diff consistently uses segment point for joint location with edit made in design`() {
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        referenceLineDao.save(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                draft = false,
            )
        )
        val switch =
            switchDao.save(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(4.2, 0.1), null)), draft = false)
            )
        val originalAlignment =
            alignment(
                segment(Point(0.0, 0.0), Point(4.0, 0.0)),
                segment(Point(4.0, 0.0), Point(10.0, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        val locationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, alignmentVersion = alignmentDao.insert(originalAlignment), draft = false)
            )

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))

        switchService.saveDraft(
            testBranch,
            switchDao.fetch(switch).copy(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(4.1, 0.2), null))),
        )
        val updatedAlignment =
            alignment(
                segment(Point(0.1, 0.0), Point(4.1, 0.0)),
                segment(Point(4.1, 0.0), Point(10.1, 0.0)).copy(switchId = switch.id, startJointNumber = JointNumber(1)),
            )
        locationTrackService.saveDraft(testBranch, locationTrackDao.fetch(locationTrack), updatedAlignment)

        publish(publicationService, testBranch, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack.id)
        switchService.mergeToMainBranch(testBranch, switch.id)
        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationLogService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id),
                latestPub.publicationTime,
                previousPub.publicationTime,
                Operation.MODIFY,
                trackNumberDao.fetchTrackNumberNames(),
            ) { _, _ ->
                null
            }
        assertEquals(2, diff.size)
        assertEquals(
            listOf("switch-joint-location", "switch-track-address").sorted(),
            diff.map { it.propKey.key.toString() }.sorted(),
        )
        val jointLocationDiff = diff.find { it.propKey.key.toString() == "switch-joint-location" }!!
        assertEquals("4.000 E, 0.000 N", jointLocationDiff.value.oldValue)
        assertEquals("4.100 E, 0.000 N", jointLocationDiff.value.newValue)
        val trackAddressDiff = diff.find { it.propKey.key.toString() == "switch-track-address" }!!
        assertEquals("0000+0004.100", trackAddressDiff.value.newValue)
    }

    @Test
    fun `changes published in design do not confuse publication change logs`() {
        val trackNumber = mainDraftContext.insert(trackNumber(TrackNumber("original")))
        val referenceLine =
            mainDraftContext.insert(
                referenceLine(trackNumber.id),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))),
            )
        val switch =
            mainDraftContext.insert(
                switch(name = "original", joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(5.0, 5.0), null)))
            )
        val locationTrack =
            mainDraftContext.insert(
                locationTrack(trackNumber.id, name = "original"),
                alignment(
                    segment(Point(0.0, 0.0), Point(5.0, 5.0))
                        .copy(switchId = switch.id, endJointNumber = JointNumber(1)),
                    segment(Point(5.0, 5.0), Point(10.0, 10.0)),
                ),
            )
        val kmPost =
            mainDraftContext.insert(kmPost(trackNumber.id, km = KmNumber(124), roughLayoutLocation = Point(4.0, 4.0)))
        val requestPublishEverything =
            publicationRequest(
                trackNumbers = listOf(trackNumber.id),
                referenceLines = listOf(referenceLine.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
                kmPosts = listOf(kmPost.id),
            )
        val originalPublication =
            publicationTestSupportService.publishAndVerify(LayoutBranch.main, requestPublishEverything).publicationId!!
        setPublicationTime(originalPublication, Instant.parse("2024-01-01T00:00:00Z"))

        val designBranch = testDBService.createDesignBranch()

        trackNumberService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(trackNumber.id)!!.copy(number = TrackNumber("edited in design")),
        )
        referenceLineService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(referenceLine.id)!!,
            alignment(segment(Point(1.0, 0.0), Point(1.0, 10.0))),
        )
        locationTrackService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(locationTrack.id)!!.copy(name = AlignmentName("edited in design")),
        )
        switchService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(switch.id)!!.copy(name = SwitchName("edited in design")),
        )
        kmPostService.saveDraft(designBranch, mainOfficialContext.fetch(kmPost.id)!!.copy(kmNumber = KmNumber(101)))

        publicationTestSupportService.publishAndVerify(designBranch, requestPublishEverything).also {
            setPublicationTime(it.publicationId!!, Instant.parse("2024-01-02T00:00:00Z"))
        }

        publicationDao.fetchPublishedTrackNumbers(originalPublication).let { (directChanges, indirectChanges) ->
            assertEquals(
                listOf(trackNumberDao.fetchVersion(MainLayoutContext.official, trackNumber.id)),
                directChanges.map { it.version },
            )
            assertEquals(listOf(), indirectChanges)
        }
        publicationDao
            .fetchPublicationTrackNumberChanges(
                LayoutBranch.main,
                originalPublication,
                Instant.parse("2023-01-01T00:00:00Z"),
            )
            .let { changes ->
                assertEquals(1, changes.size)
                val change = changes[trackNumber.id]!!
                assertEquals(null, change.trackNumber.old)
                assertEquals("original", change.trackNumber.new.toString())
            }
        publicationDao.fetchPublishedReferenceLines(originalPublication).let { published ->
            assertEquals(
                listOf(referenceLineDao.fetchVersion(MainLayoutContext.official, referenceLine.id)),
                published.map { it.version },
            )
        }
        publicationDao.fetchPublicationReferenceLineChanges(originalPublication).let { changes ->
            assertEquals(1, changes.size)
            val change = changes[referenceLine.id]!!
            assertEquals(null, change.startPoint.old)
            assertEquals(Point(0.0, 0.0), change.startPoint.new)
        }
        publicationDao.fetchPublishedLocationTracks(originalPublication).let { (directChanges, indirectChanges) ->
            assertEquals(
                listOf(locationTrackDao.fetchVersion(MainLayoutContext.official, locationTrack.id)),
                directChanges.map { it.version },
            )
            assertEquals(listOf(), indirectChanges)
        }
        publicationDao.fetchPublicationLocationTrackChanges(originalPublication).let { changes ->
            assertEquals(1, changes.size)
            val change = changes[locationTrack.id]!!
            assertEquals("original", change.name.new.toString())
        }
        publicationDao.fetchPublishedSwitches(originalPublication).let { (directChanges, indirectChanges) ->
            assertEquals(
                listOf(switchDao.fetchVersion(MainLayoutContext.official, switch.id)),
                directChanges.map { it.version },
            )
            assertEquals(listOf(), indirectChanges)
        }
        publicationDao.fetchPublicationSwitchChanges(originalPublication).let { changes ->
            assertEquals(1, changes.size)
            val change = changes[switch.id]!!
            assertEquals("original", change.name.new.toString())
        }
        publicationDao.fetchPublishedKmPosts(originalPublication).let { published ->
            assertEquals(1, published.size)
            assertEquals(KmNumber(124), published[0].kmNumber)
        }
        publicationDao.fetchPublicationKmPostChanges(originalPublication).let { changes ->
            assertEquals(1, changes.size)
            val change = changes[kmPost.id]!!
            assertEquals(KmNumber(124), change.kmNumber.new)
        }
    }

    @Test
    fun `track number created in design is reported as created`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val trackNumber = testDBService.testContext(testBranch, DRAFT).insert(trackNumber()).id
        publish(publicationService, testBranch, trackNumbers = listOf(trackNumber))
        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        publish(publicationService, trackNumbers = listOf(trackNumber))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.trackNumbers[0].operation)
    }

    @Test
    fun `location track created in design is reported as created`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val locationTrack =
            testDBService.testContext(testBranch, DRAFT).insert(locationTrack(trackNumber), alignment()).id
        publish(publicationService, testBranch, locationTracks = listOf(locationTrack))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        publish(publicationService, locationTracks = listOf(locationTrack))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.locationTracks[0].operation)
    }

    @Test
    fun `switch created in design is reported as created`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val switch = testDBService.testContext(testBranch, DRAFT).insert(switch()).id
        publish(publicationService, testBranch, switches = listOf(switch))
        switchService.mergeToMainBranch(testBranch, switch)
        publish(publicationService, switches = listOf(switch))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.switches[0].operation)
    }

    @Test
    fun `km post created in design is reported as created`() {
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val kmPost = testDBService.testContext(testBranch, DRAFT).insert(kmPost(trackNumber, KmNumber(2))).id
        publish(publicationService, testBranch, kmPosts = listOf(kmPost))
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        publish(publicationService, kmPosts = listOf(kmPost))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.kmPosts[0].operation)
    }

    private fun setPublicationTime(publicationId: IntId<Publication>, time: Instant) =
        jdbc.update(
            "update publication.publication set publication_time = :time where id = :id",
            mapOf("id" to publicationId.intValue, "time" to Timestamp.from(time)),
        )
}

private fun alignmentWithSwitchLinks(vararg switchIds: IntId<LayoutSwitch>?): LayoutAlignment =
    alignment(
        switchIds.mapIndexed { index, switchId ->
            segment(Point(0.0, index * 1.0), Point(0.0, index * 1.0 + 1.0)).let { segment ->
                if (switchId == null) {
                    segment
                } else {
                    segment.copy(switchId = switchId, startJointNumber = JointNumber(1))
                }
            }
        }
    )
