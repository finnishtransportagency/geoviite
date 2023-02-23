package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublicationDao
import fi.fta.geoviite.infra.linking.ValidationVersion
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
): ITTestBase() {
    lateinit var trackNumberId: IntId<TrackLayoutTrackNumber>
    lateinit var layoutPublishId: IntId<Publication>
    lateinit var locationTrackId: IntId<LocationTrack>
    lateinit var layoutPublishMoment: Instant

    @BeforeEach
    fun cleanUp() {
        // Mark off any old junk as done
        transactional {
            val lastSuccessTime = ratkoPushDao.getLatestPushedPublicationMoment()
            val hangingPublications = publicationDao.fetchPublications(lastSuccessTime, null)
                .filterNot { it.publicationTime == lastSuccessTime }
            if (hangingPublications.isNotEmpty()) ratkoPushDao.startPushing(
                getCurrentUserName(),
                hangingPublications.map { publication -> publication.id },
            )
            val markEverythingComplete = "update integrations.ratko_push set status='SUCCESSFUL' where true"
            jdbc.update(markEverythingComplete, mapOf<String, Unit>())
        }

        trackNumberId = insertOfficialTrackNumber()
        val locationTrackResponse = insertAndPublishLocationTrack()
        locationTrackId = locationTrackResponse.id
        val beforePublish = ratkoPushDao.getLatestPublicationMoment()
        layoutPublishId = createPublication(locationTracks = listOf(locationTrackResponse.rowVersion))
        layoutPublishMoment = publicationDao.getPublication(layoutPublishId).publicationTime
        Assertions.assertTrue(layoutPublishMoment > beforePublish)
        assertEquals(layoutPublishMoment, ratkoPushDao.getLatestPublicationMoment())
    }

    @Test
    fun shouldStartANewPublish() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
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
    fun shouldSetEndTimeWhenFinishedPublishing() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)
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

        val publications = publicationDao.fetchPublications(lastPush, null)
        val publishedLocationTracks = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)

        val latestPushMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertEquals(layoutPublishMoment, latestPushMoment)
        val publications = publicationDao.fetchPublications(latestPushMoment, null)
        assertEquals(1, publications.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.FAILED)

        val latestPushedPublish = ratkoPushDao.getLatestPushedPublicationMoment()
        Assertions.assertTrue(latestPushedPublish < layoutPublishMoment)
        val publications = publicationDao.fetchPublications(latestPushedPublish, null)
        val publishedLocationTracks = publicationDao.fetchPublishedLocationTracks(publications[1].id)

        assertEquals(2, publications.size)
        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Response = insertAndPublishLocationTrack()
        val layoutPublishId2 = createPublication(locationTracks = listOf(locationTrack2Response.rowVersion), message = "Test")

        val latestPushedMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        Assertions.assertTrue(latestPushedMoment < layoutPublishMoment)
        val publications = publicationDao.fetchPublications(latestPushedMoment, null)

        val fetchedLayoutPublish = publications.find { it.id == layoutPublishId }
        val fetchedLayoutPublish2 = publications.find { it.id == layoutPublishId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        val publishLocationTracks = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish.id)
        val publish2LocationTracks = publicationDao.fetchPublishedLocationTracks(fetchedLayoutPublish2.id)


        assertEquals(1, publishLocationTracks.size)
        assertEquals(1, publish2LocationTracks.size)

        assertEquals(locationTrackId, publishLocationTracks[0].version.id)
        assertEquals(locationTrack2Response.id, publish2LocationTracks[0].version.id)
    }

    @Test
    fun shouldFindLatestPushErrorByPublicationId() {
        val ratkoPushId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
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
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPushId, status = RatkoPushStatus.FAILED)
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
        trackNumbers: List<RowVersion<TrackLayoutTrackNumber>> = listOf(),
        referenceLines: List<RowVersion<ReferenceLine>> = listOf(),
        locationTracks: List<RowVersion<LocationTrack>> = listOf(),
        switches: List<RowVersion<TrackLayoutSwitch>> = listOf(),
        kmPosts: List<RowVersion<TrackLayoutKmPost>> = listOf(),
        message: String = ""
    ) = publicationDao.createPublication(
        trackNumbers = trackNumbers,
        referenceLines = referenceLines,
        locationTracks = locationTracks,
        switches = switches,
        kmPosts = kmPosts,
        message = message
    )
}
