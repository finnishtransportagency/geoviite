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
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.linking.LayoutKmPostSaveRequest
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.switches.LayoutSwitchSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
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
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameFreeTextPart
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.OperationalPointAbbreviation
import fi.fta.geoviite.infra.tracklayout.OperationalPointName
import fi.fta.geoviite.infra.tracklayout.OperationalPointRinfType
import fi.fta.geoviite.infra.tracklayout.OperationalPointState
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.UicCode
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.tracklayout.trackNumberSaveRequest
import fi.fta.geoviite.infra.util.SortOrder
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
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        val (track, geometry) = locationTrackAndGeometry(trackNumberId, draft = true)
        val draftId = locationTrackService.saveDraft(LayoutBranch.main, track, geometry).id
        assertThrows<NoSuchEntityException> {
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.official, draftId)
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
            publicationRequest(trackNumbers = listOf(trackNumber.id), referenceLines = listOf(rl.id as IntId)),
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
            publicationRequest(trackNumbers = listOf(trackNumber.id)),
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
                changes.getValue(trackNumber.id),
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
            publicationRequest(trackNumbers = listOf(trackNumber.id), referenceLines = listOf(rl.id as IntId)),
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
            publicationRequest(trackNumbers = listOf(trackNumber.id)),
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
                changes.getValue(trackNumber.id),
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
                        LocationTrackNamingScheme.FREE_TEXT,
                        LocationTrackNameFreeTextPart("TEST duplicate"),
                        null,
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
                        LocationTrackNamingScheme.FREE_TEXT,
                        LocationTrackNameFreeTextPart("TEST duplicate 2"),
                        null,
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
                        LocationTrackNamingScheme.FREE_TEXT,
                        LocationTrackNameFreeTextPart("TEST"),
                        null,
                        LocationTrackDescriptionBase("Test"),
                        LocationTrackDescriptionSuffix.NONE,
                        LocationTrackType.MAIN,
                        LocationTrackState.IN_USE,
                        mainDraftContext.createLayoutTrackNumber().id,
                        duplicate.id as IntId,
                        TopologicalConnectivityType.NONE,
                        IntId(1),
                    ),
                )
            )

        listOf(duplicate, duplicate2, locationTrack).forEach { track ->
            mainDraftContext.save(
                mainDraftContext.fetch(track.id as IntId)!!,
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        }

        publicationTestSupportService.publishAndVerify(
            LayoutBranch.main,
            publicationRequest(locationTracks = listOf(locationTrack.id as IntId, duplicate.id, duplicate2.id as IntId)),
        )

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService.update(
                    LayoutBranch.main,
                    locationTrack.id,
                    LocationTrackSaveRequest(
                        namingScheme = LocationTrackNamingScheme.FREE_TEXT,
                        nameFreeText = LocationTrackNameFreeTextPart("TEST2"),
                        nameSpecifier = null,
                        descriptionBase = LocationTrackDescriptionBase("Test2"),
                        descriptionSuffix = LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER,
                        type = LocationTrackType.SIDE,
                        state = LocationTrackState.NOT_IN_USE,
                        trackNumberId = locationTrack.trackNumberId,
                        duplicate2.id,
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
                changes.getValue(locationTrack.id),
                PublicationReferencedAssetSetChanges.empty(),
                { _ -> throw IllegalStateException("didn't expect to look up switches") },
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
                emptySet(),
                switchOids = mapOf(),
                operationalPointOids = mapOf(),
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
                LocationTrackNamingScheme.FREE_TEXT,
                LocationTrackNameFreeTextPart("TEST"),
                null,
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
        mainDraftContext.save(
            mainDraftContext.fetch(locationTrack.id as IntId)!!,
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )
        publish(publicationService, locationTracks = listOf(locationTrack.id as IntId))

        val updatedLocationTrack =
            locationTrackDao.fetch(
                locationTrackService.update(
                    LayoutBranch.main,
                    locationTrack.id,
                    saveReq.copy(descriptionBase = LocationTrackDescriptionBase("TEST2")),
                )
            )
        publish(publicationService, locationTracks = listOf(updatedLocationTrack.id as IntId))
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(locationTrack.id),
                PublicationReferencedAssetSetChanges.empty(),
                { _ -> throw IllegalStateException("didn't expect to look up switches") },
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                LayoutBranch.main,
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
                emptySet(),
                mapOf(),
                mapOf(),
            ) { _, _ ->
                null
            }
        assertEquals(1, diff.size)
        assertEquals("description-base", diff[0].propKey.key.toString())
        assertEquals(locationTrack.descriptionStructure.base, diff[0].value.oldValue)
        assertEquals(updatedLocationTrack.descriptionStructure.base, diff[0].value.newValue)
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

        val gkLocation = kmPostGkLocation(1.0, 1.0)
        val kmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.insertKmPost(
                    LayoutBranch.main,
                    LayoutKmPostSaveRequest(KmNumber(0), LayoutState.IN_USE, trackNumberId, gkLocation, sourceId = null),
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
                    kmPost.id,
                    LayoutKmPostSaveRequest(
                        KmNumber(1),
                        LayoutState.NOT_IN_USE,
                        trackNumber2Id,
                        gkLocation,
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
                changes.getValue(kmPost.id),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
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
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val kmPost = mainOfficialContext.save(kmPost(trackNumber, KmNumber(1), kmPostGkLocation(1.0, 1.0))).id
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
            trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
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
                kmPostGkLocation(1.0, 1.0),
                sourceId = null,
            )

        val kmPost =
            kmPostService.getOrThrow(MainLayoutContext.draft, kmPostService.insertKmPost(LayoutBranch.main, saveReq))
        publish(publicationService, kmPosts = listOf(kmPost.id as IntId))
        val updatedKmPost =
            kmPostService.getOrThrow(
                MainLayoutContext.draft,
                kmPostService.updateKmPost(LayoutBranch.main, kmPost.id, saveReq.copy(kmNumber = KmNumber(1))),
            )
        publish(publicationService, kmPosts = listOf(updatedKmPost.id as IntId))
        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val changes = publicationDao.fetchPublicationKmPostChanges(latestPub.id)

        val diff =
            publicationLogService.diffKmPost(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(kmPost.id),
                latestPub.publicationTime,
                latestPub.publicationTime.minusMillis(1),
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
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
                TrackMeter.ZERO.round(3),
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
                    LayoutSwitchSaveRequest(
                        SwitchName("TEST"),
                        IntId(1),
                        LayoutStateCategory.EXISTING,
                        IntId(1),
                        false,
                        draftOid = null,
                    ),
                ),
            )
        publish(publicationService, switches = listOf(switch.id as IntId), trackNumbers = listOf(tn1, tn2))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(
                    LayoutBranch.main,
                    switch.id,
                    LayoutSwitchSaveRequest(
                        SwitchName("TEST 2"),
                        IntId(2),
                        LayoutStateCategory.NOT_EXISTING,
                        IntId(2),
                        true,
                        draftOid = null,
                    ),
                    null,
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
                changes.getValue(switch.id),
                null,
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                mapOf(),
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
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
            LayoutSwitchSaveRequest(
                SwitchName("TEST"),
                IntId(1),
                LayoutStateCategory.EXISTING,
                IntId(1),
                false,
                draftOid = null,
            )

        val switch =
            switchService.getOrThrow(MainLayoutContext.draft, switchService.insertSwitch(LayoutBranch.main, saveReq))
        publish(publicationService, switches = listOf(switch.id as IntId))
        val updatedSwitch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.updateSwitch(LayoutBranch.main, switch.id, saveReq.copy(name = SwitchName("TEST 2")), null),
            )
        publish(publicationService, switches = listOf(updatedSwitch.id as IntId))

        val publication =
            publicationLogService
                .fetchPublicationDetails(
                    LayoutBranch.main,
                    translation = localizationService.getLocalization(LocalizationLanguage.FI),
                )
                .last()
        val diff = publication.propChanges
        assertEquals(1, diff.size)
        assertEquals("switch", diff[0].propKey.key.toString())
        assertEquals(switch.name, diff[0].value.oldValue)
        assertEquals(updatedSwitch.name, diff[0].value.newValue)
    }

    @Test
    fun `publication log switch changes in main do not include switch link removals in designs`() {
        val switch = switchDao.save(switch(name = "sw", draft = false)).id
        switchDao.insertExternalId(switch, LayoutBranch.main, Oid("1.1.1.1.2"))
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, draft = true),
                    geometryWithSwitchLinks(switch),
                )
                .id

        publish(publicationService, locationTracks = listOf(locationTrackId))

        val designBranch = testDBService.createDesignBranch()
        switchService.saveDraft(
            designBranch,
            switchDao.getOrThrow(MainLayoutContext.official, switch).copy(name = SwitchName("sw-edited-in-design")),
        )

        locationTrackService.saveDraft(
            designBranch,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(
            publicationService,
            branch = designBranch,
            locationTracks = listOf(locationTrackId),
            switches = listOf(switch),
        )

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        publish(publicationService, locationTracks = listOf(locationTrackId))

        val fullInterval =
            publicationLogService
                .fetchPublicationDetails(
                    LayoutBranch.main,
                    translation = localizationService.getLocalization(LocalizationLanguage.FI),
                )
                .map { publication ->
                    publication.propChanges.find { propChange ->
                        propChange.propKey.key.toString() == "linked-switches"
                    }
                }
        assertEquals(listOf("Vaihde sw linkitetty", "Vaihteen sw linkitys purettu"), fullInterval.map { it?.remark })
    }

    @Test
    fun `publication log switch changes in main do not include switch link additions in designs`() {
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
                    geometryWithSwitchLinks(switchAddedAndRemoved),
                )
                .id

        publish(publicationService, locationTracks = listOf(locationTrackId))

        val designBranch = testDBService.createDesignBranch()
        locationTrackService.saveDraft(
            designBranch,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            geometryWithSwitchLinks(switchFurtherAddedInDesign),
        )
        publish(publicationService, branch = designBranch, locationTracks = listOf(locationTrackId))

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.getOrThrow(MainLayoutContext.official, locationTrackId),
            geometryWithSwitchLinks(switchFurtherAddedInMain),
        )
        publish(publicationService, locationTracks = listOf(locationTrackId))

        val fullInterval =
            publicationLogService
                .fetchPublicationDetails(
                    LayoutBranch.main,
                    translation = localizationService.getLocalization(LocalizationLanguage.FI),
                )
                .map { publication ->
                    publication.propChanges.find { propChange ->
                        propChange.propKey.key.toString() == "linked-switches"
                    }
                }

        assertEquals(
            listOf(
                "Vaihde sw-added-and-later-removed linkitetty",
                "Vaihteen sw-added-and-later-removed linkitys purettu. Vaihde sw-later-added-in-main linkitetty.",
            ),
            fullInterval.map { it?.remark },
        )
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
                locationTrack(trackNumberId, draft = true),
                geometryWithSwitchLinks(
                    switchUnlinkedFromAlignment.id,
                    switchDeleted.id,
                    switchMerelyRenamed.id,
                    originalSwitchReplacedWithNewSameName.id,
                    outerLinks = (switchUnlinkedFromTopology.id to null),
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
            locationTrackDao.getOrThrow(MainLayoutContext.official, originalLocationTrack.id),
            geometryWithSwitchLinks(
                switchAddedToAlignment.id,
                switchMerelyRenamed.id,
                newSwitchReplacingOldWithSameName.id,
                null,
                outerLinks = (switchAddedToTopologyStart.id to switchAddedToTopologyEnd.id),
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

        val diff =
            publicationLogService
                .fetchPublicationDetails(
                    LayoutBranch.main,
                    translation = localizationService.getLocalization(LocalizationLanguage.FI),
                )
                .last { it.asset.type == PublishableObjectType.LOCATION_TRACK }
                .propChanges
                .find { it.propKey.key.toString() == "linked-switches" }!!
        assertEquals("linked-switches", diff.propKey.key.toString())
        assertEquals(
            """
                Vaihteiden sw-deleted, sw-replaced-with-new-same-name (1.1.1.1.8), sw-unlinked-from-alignment,
                sw-unlinked-from-topology linkitys purettu. Vaihteet sw-added-to-alignment, sw-added-to-topo-end,
                sw-added-to-topo-start, sw-replaced-with-new-same-name (1.1.1.1.9) linkitetty.
            """
                .trimIndent()
                .replace("\n", " "),
            diff.remark,
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

        val referenceLineAlignment = alignmentDao.insert(referenceLineGeometry(segmentWithCurveToMaxY(0.0)))
        referenceLineDao.save(referenceLine(trackNumberId, geometryVersion = referenceLineAlignment, draft = false))

        // track that had bump to y=-10 goes to having a bump to y=10, meaning the length and ends
        // stay the same,
        // but the geometry changes
        val originalLocationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometryOfSegments(segmentWithCurveToMaxY(-10.0)),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(originalLocationTrack)),
            trackGeometryOfSegments(segmentWithCurveToMaxY(10.0)),
        )
        publish(publicationService, locationTracks = listOf(originalLocationTrack.id))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        val changes = publicationDao.fetchPublicationLocationTrackChanges(latestPub.id)

        val diff =
            publicationLogService.diffLocationTrack(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(originalLocationTrack.id),
                PublicationReferencedAssetSetChanges.empty(),
                { _ -> throw IllegalStateException("did not expect to look up switches") },
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                LayoutBranch.main,
                latestPub.publicationTime,
                latestPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
                setOf(KmNumber(0)),
                mapOf(),
                mapOf(),
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
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndGeometry(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndGeometry(trackNumberId2, draft = true).let { (lt, a) ->
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
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack1 =
            locationTrackAndGeometry(trackNumberId1, draft = true).let { (lt, a) ->
                locationTrackService.saveDraft(LayoutBranch.main, lt, a).id
            }

        val publish1 =
            publish(publicationService, trackNumbers = listOf(trackNumberId1), locationTracks = listOf(locationTrack1))

        val trackNumberId2 = mainDraftContext.createLayoutTrackNumber().id
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        val locationTrack2 =
            locationTrackAndGeometry(trackNumberId2, draft = true).let { (lt, a) ->
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
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )

        publish(publicationService, trackNumbers = listOf(trackNumberId1))

        val trackNumberId2 = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("4321")).id as IntId
        referenceLineService.saveDraft(
            LayoutBranch.main,
            referenceLine(trackNumberId2, draft = true),
            referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
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
    fun `should also fetch reference line's publication log rows when fetching track number`() {
        val trackNumberId = mainDraftContext.getOrCreateLayoutTrackNumber(TrackNumber("1234")).id as IntId
        val referenceLineId =
            referenceLineService
                .saveDraft(
                    LayoutBranch.main,
                    referenceLine(trackNumberId, draft = true),
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                )
                .id

        publish(publicationService, trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        val rows =
            publicationLogService.fetchPublicationDetails(
                LayoutBranch.main,
                sortBy = PublicationTableColumn.NAME,
                translation = localizationService.getLocalization(LocalizationLanguage.FI),
                publicationLogAsset = PublicationLogAsset(trackNumberId, PublicationLogAssetType.TRACK_NUMBER),
            )

        assertEquals(2, rows.size)
        assertTrue { rows.any { it.name.contains("1234") && it.asset.type == PublishableObjectType.TRACK_NUMBER } }
        assertTrue { rows.any { it.name.contains("1234") && it.asset.type == PublishableObjectType.REFERENCE_LINE } }
    }

    @Test
    fun `switch diff consistently uses segment point for joint location`() {
        val trackNumber = TrackNumber("1234")
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(trackNumber).id as IntId
        val referenceLine =
            referenceLineDao.save(
                referenceLine(
                    trackNumberId,
                    geometryVersion =
                        alignmentDao.insert(referenceLineGeometry(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                    draft = false,
                )
            )
        val geocodingContext =
            GeocodingContext.create(
                    trackNumber = trackNumber,
                    startAddress = TrackMeter.ZERO,
                    referenceLineGeometry = referenceLineService.getWithGeometry(referenceLine).second,
                    kmPosts = emptyList(),
                )
                .geocodingContext
        val switch =
            switchDao.save(
                switch(
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(4.2, 0.1), null)),
                    draft = false,
                )
            )
        val locationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(4.0, 0.0), Point(10.0, 0.0))),
                    ),
                ),
            )
        switchService.saveDraft(
            LayoutBranch.main,
            switchDao
                .fetch(switch)
                .copy(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(4.1, 0.2), null))),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(locationTrack),
            trackGeometry(
                edge(
                    endInnerSwitch = switchLinkYV(switch.id, 1),
                    segments = listOf(segment(Point(0.1, 0.0), Point(4.1, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(switch.id, 1),
                    segments = listOf(segment(Point(4.1, 0.0), Point(10.1, 0.0))),
                ),
            ),
        )

        publish(publicationService, switches = listOf(switch.id), locationTracks = listOf(locationTrack.id))

        val latestPubs = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 2).items
        val latestPub = latestPubs.first()
        val previousPub = latestPubs.last()
        val changes = publicationDao.fetchPublicationSwitchChanges(latestPub.id)

        val diff =
            publicationLogService.diffSwitch(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(switch.id),
                null,
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                mapOf(),
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
                { _, _ -> geocodingContext },
            )
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
        val trackNumber = TrackNumber("1234")
        val trackNumberId = mainOfficialContext.getOrCreateLayoutTrackNumber(trackNumber).id as IntId
        val referenceLine =
            referenceLineDao.save(
                referenceLine(
                    trackNumberId,
                    geometryVersion =
                        alignmentDao.insert(referenceLineGeometry(segment(Point(0.0, 0.0), Point(11.0, 0.0)))),
                    draft = false,
                )
            )
        val geocodingContext =
            GeocodingContext.create(
                    trackNumber = trackNumber,
                    startAddress = TrackMeter.ZERO,
                    referenceLineGeometry = referenceLineService.getWithGeometry(referenceLine).second,
                    kmPosts = emptyList(),
                )
                .geocodingContext
        val switch =
            switchDao.save(
                switch(
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(4.2, 0.1), null)),
                    draft = false,
                )
            )
        val locationTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(4.0, 0.0), Point(10.0, 0.0))),
                    ),
                ),
            )

        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))

        switchService.saveDraft(
            testBranch,
            switchDao
                .fetch(switch)
                .copy(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(4.1, 0.2), null))),
        )
        locationTrackService.saveDraft(
            testBranch,
            locationTrackDao.fetch(locationTrack),
            trackGeometry(
                edge(
                    endInnerSwitch = switchLinkYV(switch.id, 1),
                    segments = listOf(segment(Point(0.1, 0.0), Point(4.1, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(switch.id, 1),
                    segments = listOf(segment(Point(4.1, 0.0), Point(10.1, 0.0))),
                ),
            ),
        )

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
                null,
                { _ -> throw IllegalStateException("didn't expect to look up operational points") },
                mapOf(),
                latestPub.publicationTime,
                previousPub.publicationTime,
                trackNumberDao.fetchTrackNumberNames(LayoutBranch.main),
                { _, _ -> geocodingContext },
            )
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
    fun `operational point diff diffs all fields`() {
        val op =
            operationalPoint(
                name = "name",
                abbreviation = "abbrev",
                rinfType = OperationalPointRinfType.STATION,
                state = OperationalPointState.IN_USE,
                uicCode = "123",
                location = Point(20.0, 20.0),
                polygon =
                    Polygon(Point(0.0, 0.0), Point(30.0, 0.0), Point(30.0, 30.0), Point(0.0, 30.0), Point(0.0, 0.0)),
            )
        val operationalPointId = mainDraftContext.save(op).id
        publish(publicationService, operationalPoints = listOf(operationalPointId))

        mainDraftContext.save(
            mainOfficialContext
                .fetch(operationalPointId)!!
                .copy(
                    name = OperationalPointName("ed name"),
                    abbreviation = OperationalPointAbbreviation("edabbrev"),
                    rinfType = OperationalPointRinfType.SMALL_STATION,
                    state = OperationalPointState.DELETED,
                    uicCode = UicCode("321"),
                    location = Point(25.0, 20.0),
                    polygon =
                        Polygon(Point(0.0, 0.0), Point(40.0, 0.0), Point(40.0, 40.0), Point(0.0, 40.0), Point(0.0, 0.0)),
                )
        )
        publish(publicationService, operationalPoints = listOf(operationalPointId))

        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        val changes = publicationDao.fetchPublicationOperationalPointChanges(latestPub.id)

        val diff =
            publicationLogService.diffOperationalPoint(
                localizationService.getLocalization(LocalizationLanguage.FI),
                changes.getValue(operationalPointId),
            )
        val expected =
            listOf(
                PublicationChange(
                    PropKey("operational-point"),
                    ChangeValue(OperationalPointName("name"), OperationalPointName("ed name")),
                    null,
                ),
                PublicationChange(
                    PropKey("abbreviation"),
                    ChangeValue(OperationalPointAbbreviation("abbrev"), OperationalPointAbbreviation("edabbrev")),
                    null,
                ),
                PublicationChange(PropKey("uic-code"), ChangeValue(UicCode("123"), UicCode("321")), null),
                PublicationChange(PropKey("rinf-type"), ChangeValue("Asema (10)", "Asema (pieni) (20)"), null),
                PublicationChange(PropKey("polygon"), ChangeValue(null, null), remark = "Alue muuttunut"),
                PublicationChange(
                    PropKey("location"),
                    ChangeValue("20.000 E, 20.000 N", "25.000 E, 20.000 N"),
                    "Siirtynyt 5.0 m",
                ),
                PublicationChange(
                    PropKey("state"),
                    ChangeValue(
                        OperationalPointState.IN_USE,
                        OperationalPointState.DELETED,
                        localizationKey = "OperationalPointState",
                    ),
                    null,
                ),
            )
        assertEquals(expected, diff)
    }

    @Test
    fun `changes published in design do not confuse publication change logs`() {
        val trackNumber = mainDraftContext.save(trackNumber(TrackNumber("original")))
        val referenceLine =
            mainDraftContext.save(
                referenceLine(trackNumber.id),
                referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 10.0))),
            )
        val switch =
            mainDraftContext.save(
                switch(
                    name = "original",
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(5.0, 5.0), null)),
                )
            )
        val locationTrack =
            mainDraftContext.save(
                locationTrack(trackNumber.id, name = "original"),
                trackGeometry(
                    edge(
                        endInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 5.0))),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(5.0, 5.0), Point(10.0, 10.0))),
                    ),
                ),
            )
        val kmPost =
            mainDraftContext.save(kmPost(trackNumber.id, km = KmNumber(124), gkLocation = kmPostGkLocation(4.0, 4.0)))
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

        val designBranch = testDBService.createDesignBranch()

        trackNumberService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(trackNumber.id)!!.copy(number = TrackNumber("edited in design")),
        )
        referenceLineService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(referenceLine.id)!!,
            referenceLineGeometry(segment(Point(1.0, 0.0), Point(1.0, 10.0))),
        )
        mainOfficialContext.fetchLocationTrackWithGeometry(locationTrack.id)!!.let { (t, g) ->
            locationTrackService.saveDraft(designBranch, t.copy(name = AlignmentName("edited in design")), g)
        }
        switchService.saveDraft(
            designBranch,
            mainOfficialContext.fetch(switch.id)!!.copy(name = SwitchName("edited in design")),
        )
        kmPostService.saveDraft(designBranch, mainOfficialContext.fetch(kmPost.id)!!.copy(kmNumber = KmNumber(101)))

        publicationTestSupportService.publishAndVerify(designBranch, requestPublishEverything)

        publicationDao.fetchPublishedTrackNumbers(setOf(originalPublication)).getValue(originalPublication).let {
            (directChanges, indirectChanges) ->
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
        assertEquals(
            listOf(referenceLineDao.fetchVersion(MainLayoutContext.official, referenceLine.id)),
            publicationDao.fetchPublishedReferenceLines(setOf(originalPublication)).getValue(originalPublication).map {
                it.version
            },
        )
        publicationDao.fetchPublicationReferenceLineChanges(originalPublication).let { changes ->
            assertEquals(1, changes.size)
            val change = changes[referenceLine.id]!!
            assertEquals(null, change.startPoint.old)
            assertEquals(Point(0.0, 0.0), change.startPoint.new)
        }
        publicationDao.fetchPublishedLocationTracks(setOf(originalPublication)).getValue(originalPublication).let {
            (directChanges, indirectChanges) ->
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
        publicationDao.fetchPublishedSwitches(setOf(originalPublication)).getValue(originalPublication).let {
            (directChanges, indirectChanges) ->
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
        publicationDao.fetchPublishedKmPosts(setOf(originalPublication)).getValue(originalPublication).let { published
            ->
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
        val trackNumber = testDBService.testContext(testBranch, DRAFT).save(trackNumber()).id
        publish(publicationService, testBranch, trackNumbers = listOf(trackNumber))
        trackNumberService.mergeToMainBranch(testBranch, trackNumber)
        publish(publicationService, trackNumbers = listOf(trackNumber))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.trackNumbers[0].operation)
    }

    @Test
    fun `location track created in design is reported as created`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val locationTrack =
            testDBService
                .testContext(testBranch, DRAFT)
                .save(locationTrack(trackNumber), TmpLocationTrackGeometry.empty)
                .id
        publish(publicationService, testBranch, locationTracks = listOf(locationTrack))
        locationTrackService.mergeToMainBranch(testBranch, locationTrack)
        publish(publicationService, locationTracks = listOf(locationTrack))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.locationTracks[0].operation)
    }

    @Test
    fun `switch created in design is reported as created`() {
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val switch = testDBService.testContext(testBranch, DRAFT).save(switch()).id
        publish(publicationService, testBranch, switches = listOf(switch))
        switchService.mergeToMainBranch(testBranch, switch)
        publish(publicationService, switches = listOf(switch))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.switches[0].operation)
    }

    @Test
    fun `km post created in design is reported as created`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val testBranch = DesignBranch.of(layoutDesignDao.insert(layoutDesign()))
        val kmPost = testDBService.testContext(testBranch, DRAFT).save(kmPost(trackNumber, KmNumber(2))).id
        publish(publicationService, testBranch, kmPosts = listOf(kmPost))
        kmPostService.mergeToMainBranch(testBranch, kmPost)
        publish(publicationService, kmPosts = listOf(kmPost))
        val latestPub = publicationLogService.fetchLatestPublicationDetails(LayoutBranchType.MAIN, 1).items[0]
        assertEquals(Operation.CREATE, latestPub.kmPosts[0].operation)
    }
}

private fun geometryWithSwitchLinks(
    vararg switchIds: IntId<LayoutSwitch>?,
    outerLinks: Pair<IntId<LayoutSwitch>?, IntId<LayoutSwitch>?> = (null to null),
): LocationTrackGeometry =
    trackGeometry(
        combineEdges(
            switchIds.mapIndexed { index, switchId ->
                edge(
                    startOuterSwitch = outerLinks.first.takeIf { index == 0 }?.let { id -> switchLinkYV(id, 1) },
                    endOuterSwitch =
                        outerLinks.second.takeIf { index == switchIds.lastIndex }?.let { id -> switchLinkYV(id, 1) },
                    startInnerSwitch = switchId?.let { s -> switchLinkYV(s, 1) },
                    segments = listOf(segment(Point(0.0, index * 1.0), Point(0.0, index * 1.0 + 1.0))),
                )
            }
        )
    )
