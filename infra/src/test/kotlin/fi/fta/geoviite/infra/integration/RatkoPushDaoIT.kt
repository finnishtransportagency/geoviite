package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
internal class RatkoPushDaoIT @Autowired constructor(
    val ratkoPushDao: RatkoPushDao,
    val locationTrackService: LocationTrackService,
    val publicationDao: PublicationDao,
    val locationTrackDao: LocationTrackDao,
): DBTestBase() {
    lateinit var trackNumberId: IntId<TrackLayoutTrackNumber>
    lateinit var layoutPublishId: IntId<Publication>
    lateinit var locationTrackId: IntId<LocationTrack>
    lateinit var layoutPublishMoment: Instant

    @BeforeEach
    fun cleanUp() {
        // Mark off any old junk as done
        transactional {
            val lastSuccessTime = ratkoPushDao.getLatestPushedPublicationMoment()
            val hangingPublications = publicationDao.fetchPublicationsBetween(lastSuccessTime, null)
                .filterNot { it.publicationTime == lastSuccessTime }
            if (hangingPublications.isNotEmpty()) ratkoPushDao.startPushing(
                hangingPublications.map { publication -> publication.id },
            )
            val markEverythingComplete = "update integrations.ratko_push set status='SUCCESSFUL' where true"
            jdbc.update(markEverythingComplete, mapOf<String, Unit>())
        }

        trackNumberId = insertOfficialTrackNumber()
        val locationTrackResponse = insertAndPublishLocationTrack()
        locationTrackId = locationTrackResponse.id
        val beforePublish = ratkoPushDao.getLatestPublicationMoment()
        layoutPublishId = createPublication(locationTracks = listOf(locationTrackResponse.rowVersion.id))
        layoutPublishMoment = publicationDao.getPublication(layoutPublishId).publicationTime
        Assertions.assertTrue(layoutPublishMoment > beforePublish)
        assertEquals(layoutPublishMoment, ratkoPushDao.getLatestPublicationMoment())
    }

    @Test
    fun shouldStartANewPublish() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        val (startTime, endTime) = jdbc.query(
            "select start_time, end_time from integrations.ratko_push where id = :id",
            mapOf("id" to ratkoPublishId.intValue)
        ) { rs, _ ->
            Pair(
                rs.getInstantOrNull("start_time"),
                rs.getInstantOrNull("end_time")
            )
        }.first()

        assertNotNull(startTime)
        assertNull(endTime)
    }

    @Test
    fun `ChangeTime fetch should fetch the start time of an ended push`() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        val startTime = jdbc.query(
            "select start_time from integrations.ratko_push where id = :id",
            mapOf("id" to ratkoPublishId.intValue)
        ) { rs, _ ->
            rs.getInstantOrNull("start_time")
        }.first()
        val changeTime = ratkoPushDao.getRatkoPushChangeTime()
        assertEquals(startTime, changeTime)
    }

    @Test
    fun `ChangeTime fetch should fetch the end time of an ended push`() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)
        val endTime = jdbc.query(
            "select end_time from integrations.ratko_push where id = :id",
            mapOf("id" to ratkoPublishId.intValue)
        ) { rs, _ ->
                rs.getInstantOrNull("end_time")
        }.first()
        val changeTime = ratkoPushDao.getRatkoPushChangeTime()
        assertEquals(endTime, changeTime)
    }

    @Test
    fun shouldSetEndTimeWhenFinishedPublishing() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)
        val (endTime, status) = jdbc.query(
            "select end_time, status from integrations.ratko_push where id = :id",
            mapOf("id" to ratkoPublishId.intValue)
        ) { rs, _ ->
            Pair(
                rs.getInstantOrNull("end_time"),
                rs.getEnum<RatkoPushStatus>("status")
            )
        }.first()

        assertNotNull(endTime)
        assertEquals(RatkoPushStatus.SUCCESSFUL, status)
    }

    @Test
    fun shouldReturnPublishableAlignments() {
        val lastPush = ratkoPushDao.getLatestPushedPublicationMoment()
        Assertions.assertTrue(lastPush < layoutPublishMoment)

        val publications = publicationDao.fetchPublicationsBetween(lastPush, null)
        val (publishedLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)

        val latestPushMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertEquals(layoutPublishMoment, latestPushMoment)
        val publications = publicationDao.fetchPublicationsBetween(latestPushMoment, null)
        assertEquals(1, publications.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublishId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(ratkoPublishId, status = RatkoPushStatus.FAILED)

        val latestPushedPublish = ratkoPushDao.getLatestPushedPublicationMoment()
        Assertions.assertTrue(latestPushedPublish < layoutPublishMoment)
        val publications = publicationDao.fetchPublicationsBetween(latestPushedPublish, null)
        val (publishedLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(2, publications.size)
        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Response = insertAndPublishLocationTrack()
        val layoutPublishId2 =
            createPublication(locationTracks = listOf(locationTrack2Response.rowVersion.id), message = "Test")

        val latestPushedMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        Assertions.assertTrue(latestPushedMoment < layoutPublishMoment)
        val publications = publicationDao.fetchPublicationsBetween(latestPushedMoment, null)

        val fetchedLayoutPublish = publications.find { it.id == layoutPublishId }
        val fetchedLayoutPublish2 = publications.find { it.id == layoutPublishId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        val (publishLocationTracks, _) = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish.id)
        val (publish2LocationTracks, _) = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish2.id)


        assertEquals(1, publishLocationTracks.size)
        assertEquals(1, publish2LocationTracks.size)

        assertEquals(locationTrackId, publishLocationTracks[0].version.id)
        assertEquals(locationTrack2Response.id, publish2LocationTracks[0].version.id)
    }

    @Test
    fun `Should return latest publications`() {
        val locationTrack1Response = insertAndPublishLocationTrack()
        val layoutPublishId1 = createPublication(locationTracks = listOf(locationTrack1Response.id), message = "Test")
        val locationTrack2Response = insertAndPublishLocationTrack()
        val layoutPublishId2 = createPublication(locationTracks = listOf(locationTrack2Response.id), message = "Test")

        val publications = publicationDao.fetchLatestPublications(2)

        assertEquals(publications.size, 2)
        assertEquals(publications[0].id, layoutPublishId2)
        assertEquals(publications[1].id, layoutPublishId1)
    }

    @Test
    fun shouldFindLatestPushErrorByPublicationId() {
        val ratkoPushId = ratkoPushDao.startPushing(listOf(layoutPublishId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.LOCATION_TRACK,
            locationTrackId,
            "Response body"
        )
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.CREATE,
            RatkoAssetType.TRACK_NUMBER,
            trackNumberId,
            "Response body"
        )
        ratkoPushDao.updatePushStatus(ratkoPushId, status = RatkoPushStatus.FAILED)
        val ratkoPushError = ratkoPushDao.getLatestRatkoPushErrorFor(layoutPublishId)

        assertNotNull(ratkoPushError)
        assertEquals(trackNumberId, ratkoPushError.assetId)
        assertEquals(RatkoOperation.CREATE, ratkoPushError.operation)
    }

    fun insertAndPublishLocationTrack() = locationTrackAndAlignment(trackNumberId).let { (track, alignment) ->
        val draftVersion = locationTrackService.saveDraft(track, alignment)
        locationTrackService.publish(ValidationVersion(draftVersion.id, draftVersion.rowVersion))
    }

    fun createPublication(
        trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
        referenceLines: List<IntId<ReferenceLine>> = listOf(),
        locationTracks: List<IntId<LocationTrack>> = listOf(),
        switches: List<IntId<TrackLayoutSwitch>> = listOf(),
        kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
        message: String = "",
    ) = publicationDao.createPublication(message = message)
        .also { publicationId ->
            val calculatedChanges = CalculatedChanges(
                directChanges = DirectChanges(
                    kmPostChanges = kmPosts,
                    referenceLineChanges = referenceLines,
                    trackNumberChanges = trackNumbers.map {
                        TrackNumberChange(
                            trackNumberId = it,
                            changedKmNumbers = emptySet(),
                            isStartChanged = false,
                            isEndChanged = false
                        )
                    },
                    locationTrackChanges = locationTracks.map {
                        LocationTrackChange(
                            locationTrackId = it,
                            changedKmNumbers = emptySet(),
                            isStartChanged = false,
                            isEndChanged = false
                        )
                    },
                    switchChanges = switches.map { SwitchChange(it, emptyList()) }
                ),
                indirectChanges = IndirectChanges(
                    trackNumberChanges = emptyList(),
                    locationTrackChanges = emptyList(),
                    switchChanges = emptyList(),
                ),
            )
            publicationDao.insertCalculatedChanges(publicationId, calculatedChanges)
        }
}
