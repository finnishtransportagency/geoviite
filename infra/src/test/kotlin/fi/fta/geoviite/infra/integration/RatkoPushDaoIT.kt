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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @BeforeEach
    fun cleanUp() {
        transactional {
            val clearEverything = """
                delete from integrations.ratko_push_content where true;
                delete from integrations.ratko_push_error where true;
                delete from integrations.ratko_push where true;
                delete from publication.switch where true;
                delete from publication.km_post where true;
                delete from publication.reference_line where true;
                delete from publication.location_track where true;
                delete from publication.track_number where true;
                delete from publication.calculated_change_to_switch_joint where true;
                delete from publication.calculated_change_to_switch where true;
                delete from publication.calculated_change_to_location_track_km where true;
                delete from publication.calculated_change_to_location_track where true;
                delete from publication.calculated_change_to_track_number_km where true;
                delete from publication.calculated_change_to_track_number where true;
                delete from publication.publication where true;
            """.trimIndent()

            jdbc.update(clearEverything, mapOf<String, Unit>())
        }

        trackNumberId = insertOfficialTrackNumber()
        locationTrackId = insertAndPublishLocationTrack()
        layoutPublishId = publicationDao.createPublish(listOf(), listOf(), listOf(locationTrackId), listOf(), listOf())
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
        val publishes = ratkoPushDao.fetchNotPushedLayoutPublishes()

        assertEquals(1, publishes.size)
        assertEquals(layoutPublishId, publishes[0].id)
        assertEquals(locationTrackId.id, publishes[0].locationTracks[0])
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.SUCCESSFUL)

        val publishes = ratkoPushDao.fetchNotPushedLayoutPublishes()

        assertEquals(0, publishes.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublish() {
        val ratkoPublishId = ratkoPushDao.startPushing(getCurrentUserName(), listOf(layoutPublishId))
        ratkoPushDao.updatePushStatus(getCurrentUserName(), ratkoPublishId, status = RatkoPushStatus.FAILED)

        val publishes = ratkoPushDao.fetchNotPushedLayoutPublishes()

        assertEquals(1, publishes.size)
        assertEquals(layoutPublishId, publishes[0].id)
        assertEquals(locationTrackId.id, publishes[0].locationTracks[0])
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Id = insertAndPublishLocationTrack()
        val layoutPublishId2 = publicationDao.createPublish(listOf(), listOf(), listOf(locationTrack2Id), listOf(), listOf())

        val publishes = ratkoPushDao.fetchNotPushedLayoutPublishes()

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

    @Test
    fun shouldRunGetLatestSuccessfulPublish() {
        // For now test SQL execution only
        ratkoPushDao.getLatestSuccessfulPushMoment()
    }

    fun insertAndPublishLocationTrack() = locationTrackAndAlignment(trackNumberId).let { (track, alignment) ->
        val draftVersion = locationTrackService.saveDraft(track, alignment)
        locationTrackService.publish(PublicationVersion(track.id as IntId, draftVersion))
    }
}
