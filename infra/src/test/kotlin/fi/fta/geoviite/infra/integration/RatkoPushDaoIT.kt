package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.publishedVersions
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
internal class RatkoPushDaoIT
@Autowired
constructor(
    val ratkoPushDao: RatkoPushDao,
    val locationTrackService: LocationTrackService,
    val publicationDao: PublicationDao,
    val locationTrackDao: LocationTrackDao,
) : DBTestBase() {
    lateinit var trackNumberId: IntId<LayoutTrackNumber>
    lateinit var publicationId: IntId<Publication>
    lateinit var locationTrackId: IntId<LocationTrack>
    lateinit var publicationMoment: Instant

    @BeforeEach
    fun cleanUp() {
        // Mark off any old junk as done
        transactional {
            val lastSuccessTime = ratkoPushDao.getLatestPushedPublicationMoment()
            val hangingPublications =
                publicationDao.fetchPublicationsBetween(LayoutBranch.main, lastSuccessTime, null).filterNot {
                    it.publicationTime == lastSuccessTime
                }
            if (hangingPublications.isNotEmpty()) {
                ratkoPushDao.startPushing(hangingPublications.map { publication -> publication.id })
            }
            val markEverythingComplete = "update integrations.ratko_push set status='SUCCESSFUL' where true"
            jdbc.update(markEverythingComplete, mapOf<String, Unit>())
        }

        trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrackResponse = insertAndPublishLocationTrack()
        locationTrackId = locationTrackResponse.id
        val beforePublish = ratkoPushDao.getLatestPublicationMoment()
        publicationId = createPublication(locationTracks = listOf(locationTrackResponse))
        publicationMoment = publicationDao.getPublication(publicationId).publicationTime
        assertTrue(publicationMoment > beforePublish)
        assertEquals(publicationMoment, ratkoPushDao.getLatestPublicationMoment())
    }

    @Test
    fun shouldStartANewPublish() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        val (startTime, endTime) =
            jdbc
                .query(
                    "select start_time, end_time from integrations.ratko_push where id = :id",
                    mapOf("id" to ratkoPublicationId.intValue),
                ) { rs, _ ->
                    Pair(rs.getInstantOrNull("start_time"), rs.getInstantOrNull("end_time"))
                }
                .first()

        assertNotNull(startTime)
        assertNull(endTime)
    }

    @Test
    fun `ChangeTime fetch should fetch the start time of an ended push`() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        val startTime =
            jdbc
                .query(
                    "select start_time from integrations.ratko_push where id = :id",
                    mapOf("id" to ratkoPublicationId.intValue),
                ) { rs, _ ->
                    rs.getInstantOrNull("start_time")
                }
                .first()
        val changeTime = ratkoPushDao.getRatkoPushChangeTime()
        assertEquals(startTime, changeTime)
    }

    @Test
    fun `ChangeTime fetch should fetch the end time of an ended push`() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.SUCCESSFUL)
        val endTime =
            jdbc
                .query(
                    "select end_time from integrations.ratko_push where id = :id",
                    mapOf("id" to ratkoPublicationId.intValue),
                ) { rs, _ ->
                    rs.getInstantOrNull("end_time")
                }
                .first()
        val changeTime = ratkoPushDao.getRatkoPushChangeTime()
        assertEquals(endTime, changeTime)
    }

    @Test
    fun shouldSetEndTimeWhenFinishedPublishing() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.SUCCESSFUL)
        val (endTime, status) =
            jdbc
                .query(
                    "select end_time, status from integrations.ratko_push where id = :id",
                    mapOf("id" to ratkoPublicationId.intValue),
                ) { rs, _ ->
                    Pair(rs.getInstantOrNull("end_time"), rs.getEnum<RatkoPushStatus>("status"))
                }
                .first()

        assertNotNull(endTime)
        assertEquals(RatkoPushStatus.SUCCESSFUL, status)
    }

    @Test
    fun shouldReturnPublishableAlignments() {
        val lastPush = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(lastPush < publicationMoment)

        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, lastPush, null)
        val (publishedLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(publicationId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].id)
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.SUCCESSFUL)

        val latestPushMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertEquals(publicationMoment, latestPushMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushMoment, null)
        assertEquals(1, publications.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.FAILED)

        val latestPushedPublish = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(latestPushedPublish < publicationMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushedPublish, null)
        val (publishedLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(2, publications.size)
        assertEquals(publicationId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].id)
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Response = insertAndPublishLocationTrack()
        val publicationId2 = createPublication(locationTracks = listOf(locationTrack2Response), message = "Test")

        val latestPushedMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(latestPushedMoment < publicationMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushedMoment, null)

        val fetchedLayoutPublish = publications.find { it.id == publicationId }
        val fetchedLayoutPublish2 = publications.find { it.id == publicationId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        val (publishLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish.id)
        val (publish2LocationTracks, _) = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish2.id)

        assertEquals(1, publishLocationTracks.size)
        assertEquals(1, publish2LocationTracks.size)

        assertEquals(locationTrackId, publishLocationTracks[0].id)
        assertEquals(locationTrack2Response.id, publish2LocationTracks[0].id)
    }

    @Test
    fun `Should return latest publications`() {
        val locationTrack1Response = insertAndPublishLocationTrack()
        val publicationId1 = createPublication(locationTracks = listOf(locationTrack1Response), message = "Test")
        val locationTrack2Response = insertAndPublishLocationTrack()
        val publicationId2 = createPublication(locationTracks = listOf(locationTrack2Response), message = "Test")

        val publications = publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, 2)

        assertEquals(publications.size, 2)
        assertEquals(publications[0].id, publicationId2)
        assertEquals(publications[1].id, publicationId1)
    }

    @Test
    fun shouldFindLatestPushErrorByPublicationId() {
        val ratkoPushId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.LOCATION_TRACK,
            locationTrackId,
        )
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.CREATE,
            RatkoAssetType.TRACK_NUMBER,
            trackNumberId,
        )
        ratkoPushDao.updatePushStatus(ratkoPushId, status = RatkoPushStatus.FAILED)
        val ratkoPushError = ratkoPushDao.getLatestRatkoPushErrorFor(publicationId)

        assertNotNull(ratkoPushError)
        assertEquals(trackNumberId, ratkoPushError.assetId)
        assertEquals(RatkoOperation.CREATE, ratkoPushError.operation)
    }

    fun insertAndPublishLocationTrack(): LayoutRowVersion<LocationTrack> =
        locationTrackAndGeometry(trackNumberId, draft = true).let { (track, alignment) ->
            val draftVersion = locationTrackService.saveDraft(LayoutBranch.main, track, alignment)
            locationTrackService.publish(LayoutBranch.main, draftVersion)
        }

    fun createPublication(
        layoutBranch: LayoutBranch = LayoutBranch.main,
        trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>> = listOf(),
        referenceLines: List<LayoutRowVersion<ReferenceLine>> = listOf(),
        locationTracks: List<LayoutRowVersion<LocationTrack>> = listOf(),
        switches: List<LayoutRowVersion<LayoutSwitch>> = listOf(),
        kmPosts: List<LayoutRowVersion<LayoutKmPost>> = listOf(),
        message: String = "",
    ): IntId<Publication> =
        publicationDao
            .createPublication(
                layoutBranch = layoutBranch,
                message = FreeTextWithNewLines.of(message),
                cause = PublicationCause.MANUAL,
            )
            .also { publicationId ->
                val calculatedChanges =
                    CalculatedChanges(
                        directChanges =
                            DirectChanges(
                                kmPostChanges = kmPosts.map { it.id },
                                referenceLineChanges = referenceLines.map { it.id },
                                trackNumberChanges =
                                    trackNumbers.map {
                                        TrackNumberChange(
                                            trackNumberId = it.id,
                                            changedKmNumbers = emptySet(),
                                            isStartChanged = false,
                                            isEndChanged = false,
                                        )
                                    },
                                locationTrackChanges =
                                    locationTracks.map {
                                        LocationTrackChange(
                                            locationTrackId = it.id,
                                            changedKmNumbers = emptySet(),
                                            isStartChanged = false,
                                            isEndChanged = false,
                                        )
                                    },
                                switchChanges = switches.map { SwitchChange(it.id, emptyList()) },
                            ),
                        indirectChanges =
                            IndirectChanges(
                                trackNumberChanges = emptyList(),
                                locationTrackChanges = emptyList(),
                                switchChanges = emptyList(),
                            ),
                    )
                publicationDao.insertCalculatedChanges(
                    publicationId,
                    calculatedChanges,
                    publishedVersions(trackNumbers, referenceLines, locationTracks, switches, kmPosts),
                )
            }
}
