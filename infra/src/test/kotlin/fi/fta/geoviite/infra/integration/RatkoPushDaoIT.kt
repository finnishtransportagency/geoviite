package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublicationDao
import fi.fta.geoviite.infra.linking.PublicationVersion
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
    lateinit var locationTrackId: RowVersion<LocationTrack>
    lateinit var layoutPublishMoment: Instant

    @BeforeEach
    fun cleanUp() {
        // Mark off any old junk as done
        transactional {
            val lastSuccessPush = ratkoPushDao.getLatestPushedPublication()
            val lastSuccessTime = lastSuccessPush?.publicationTime ?: Instant.EPOCH
            val hangingPublications = publicationDao.fetchPublications(lastSuccessTime, Instant.now())
                .filterNot { it.id == lastSuccessPush?.id }
            if (hangingPublications.isNotEmpty()) ratkoPushDao.startPushing(
                getCurrentUserName(),
                hangingPublications.map { publication -> publication.id },
            )
            val markEverythingComplete = "update integrations.ratko_push set status='SUCCESSFUL' where 1=1"
            jdbc.update(markEverythingComplete, mapOf<String, Unit>())
        }

        trackNumberId = insertOfficialTrackNumber()
        locationTrackId = insertAndPublishLocationTrack()
        layoutPublishId =
            publicationDao.createPublication(listOf(), listOf(), listOf(locationTrackId), listOf(), listOf())
        layoutPublishMoment = publicationDao.getPublication(layoutPublishId).publicationTime
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
        val latestPublish = ratkoPushDao.getLatestPushedPublication()
        assertNotNull(latestPublish)
        Assertions.assertTrue(latestPublish.publicationTime < layoutPublishMoment)

        val publications = publicationDao.fetchPublications(latestPublish.publicationTime, Instant.now())
        val publishedLocationTracks = locationTrackDao.fetchPublicationInformation(publications[1].id)

        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId.id, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)

        val latestMoment = ratkoPushDao.getLatestPushedPublication()?.publicationTime ?: Instant.EPOCH
        assertEquals(layoutPublishMoment, latestMoment)
        val publications = publicationDao.fetchPublications(latestMoment, Instant.now())
        assertEquals(1, publications.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.FAILED)

        val latestPublish = ratkoPushDao.getLatestPushedPublication()
        assertNotNull(latestPublish)
        Assertions.assertTrue(latestPublish.publicationTime < layoutPublishMoment)
        val publications = publicationDao.fetchPublications(latestPublish.publicationTime, Instant.now())
        val publishedLocationTracks = locationTrackDao.fetchPublicationInformation(publications[1].id)

        assertEquals(2, publications.size)
        assertEquals(layoutPublishId, publications[1].id)
        assertEquals(locationTrackId.id, publishedLocationTracks[0].version.id)
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Id = insertAndPublishLocationTrack()
        val layoutPublishId2 =
            publicationDao.createPublication(listOf(), listOf(), listOf(locationTrack2Id), listOf(), listOf())

        val latestMoment = ratkoPushDao.getLatestPushedPublication()?.publicationTime ?: Instant.EPOCH
        Assertions.assertTrue(latestMoment < layoutPublishMoment)
        val publications = publicationDao.fetchPublications(latestMoment, Instant.now())

        val fetchedLayoutPublish = publications.find { it.id == layoutPublishId }
        val fetchedLayoutPublish2 = publications.find { it.id == layoutPublishId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        val publishLocationTracks = locationTrackDao.fetchPublicationInformation(fetchedLayoutPublish.id)
        val publish2LocationTracks = locationTrackDao.fetchPublicationInformation(fetchedLayoutPublish2.id)


        assertEquals(1, publishLocationTracks.size)
        assertEquals(1, publish2LocationTracks.size)

        assertEquals(locationTrackId.id, publishLocationTracks[0].version.id)
        assertEquals(locationTrack2Id.id, publish2LocationTracks[0].version.id)
    }

    @Test
    fun shouldFindPushErrorByPublicationId() {
        val ratkoPushId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.TRACK_NUMBER,
            trackNumberId,
            "Response body"
        )
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPushId, status = RatkoPushStatus.FAILED)
        val ratkoPushError = ratkoPushDao.getLatestRatkoPushErrorFor(layoutPublishId)

        assertNotNull(ratkoPushError)
        assertEquals(trackNumberId, ratkoPushError.assetId)
    }

    fun insertAndPublishLocationTrack() = locationTrackAndAlignment(trackNumberId).let { (track, alignment) ->
        val draftVersion = locationTrackService.saveDraft(track, alignment)
        locationTrackService.publish(PublicationVersion(draftVersion.id, draftVersion))
    }
}
