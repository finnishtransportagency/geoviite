package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublicationDao
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getInstantOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
internal class RatkoPushDaoIT @Autowired constructor(
    val ratkoPushDao: RatkoPushDao,
    val locationTrackService: LocationTrackService,
    val publicationDao: PublicationDao,
): ITTestBase() {
    lateinit var trackNumberId: IntId<TrackLayoutTrackNumber>
    lateinit var layoutPublishId: IntId<Publication>
    lateinit var locationTrackId: RowVersion<LocationTrack>
    lateinit var layoutPublishMoment: Instant

    @BeforeEach
    fun cleanUp() {
        // Mark off any old junk as done
        transactional {
            val lastSuccessTime = ratkoPushDao.getLatestPushedPublicationMoment()
            val hangingPublications = ratkoPushDao.fetchPublicationsAfter(lastSuccessTime)
            if (hangingPublications.isNotEmpty()) ratkoPushDao.startPushing(
                getCurrentUserName(),
                hangingPublications.map { publication -> publication.id },
            )
            val markEverythingComplete = "update integrations.ratko_push set status='SUCCESSFUL' where 1=1"
            jdbc.update(markEverythingComplete, mapOf<String, Unit>())
        }

        trackNumberId = insertOfficialTrackNumber()
        locationTrackId = insertAndPublishLocationTrack()
        val beforePublish = ratkoPushDao.getLatestPublicationMoment()
        layoutPublishId = publicationDao.createPublication(listOf(), listOf(), listOf(locationTrackId), listOf(), listOf())
        layoutPublishMoment = ratkoPushDao.getLatestPublicationMoment()
        assertTrue(layoutPublishMoment > beforePublish)
        assertEquals(layoutPublishMoment, publicationDao.fetchPublishTime(layoutPublishId).publishTime)
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
        val latestMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(latestMoment < layoutPublishMoment)
        val publishes = ratkoPushDao.fetchPublicationsAfter(latestMoment)

        assertEquals(1, publishes.size)
        assertEquals(layoutPublishId, publishes[0].id)
        assertEquals(locationTrackId.id, publishes[0].locationTracks[0])
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)

        val latestMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertEquals(layoutPublishMoment, latestMoment)
        val publishes = ratkoPushDao.fetchPublicationsAfter(latestMoment)

        assertEquals(0, publishes.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.FAILED)

        val latestMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(latestMoment < layoutPublishMoment)
        val publications = ratkoPushDao.fetchPublicationsAfter(latestMoment)

        assertEquals(1, publications.size)
        assertEquals(layoutPublishId, publications[0].id)
        assertEquals(locationTrackId.id, publications[0].locationTracks[0])
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Id = insertAndPublishLocationTrack()
        val layoutPublishId2 = publicationDao.createPublication(listOf(), listOf(), listOf(locationTrack2Id), listOf(), listOf())

        val latestPushedMoment = ratkoPushDao.getLatestPushedPublicationMoment()
        assertTrue(latestPushedMoment < layoutPublishMoment)
        val publishes = ratkoPushDao.fetchPublicationsAfter(latestPushedMoment)

        val fetchedLayoutPublish = publishes.find { it.id == layoutPublishId }
        val fetchedLayoutPublish2 = publishes.find { it.id == layoutPublishId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        assertEquals(1, fetchedLayoutPublish.locationTracks.size)
        assertEquals(1, fetchedLayoutPublish2.locationTracks.size)

        assertEquals(locationTrackId.id, fetchedLayoutPublish.locationTracks[0])
        assertEquals(locationTrack2Id.id, fetchedLayoutPublish2.locationTracks[0])
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
