package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.DirectChanges
import fi.fta.geoviite.infra.integration.IndirectChanges
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.RatkoAssetType
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.publication.Change
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.publishedVersions
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
            val lastSuccessTime = ratkoPushDao.getLatestPushedPublicationMoment(LayoutBranch.main)
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
        publicationId = createPublication(locationTracks = listOf(Change(null, locationTrackResponse)))
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
        val lastPush = ratkoPushDao.getLatestPushedPublicationMoment(LayoutBranch.main)
        assertTrue(lastPush < publicationMoment)

        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, lastPush, null)
        val (publishedLocationTracks, _) =
            publicationDao.fetchPublishedLocationTracks(setOf(publications[1].id)).getValue(publications[1].id)

        assertEquals(publicationId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].id)
    }

    @Test
    fun shouldNotReturnSuccessfullyPublishedAlignments() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.SUCCESSFUL)

        val latestPushMoment = ratkoPushDao.getLatestPushedPublicationMoment(LayoutBranch.main)
        assertEquals(publicationMoment, latestPushMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushMoment, null)
        assertEquals(1, publications.size)
    }

    @Test
    fun shouldReturnAlignmentsWithFailedPublication() {
        val ratkoPublicationId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.updatePushStatus(ratkoPublicationId, status = RatkoPushStatus.FAILED)

        val latestPushedPublish = ratkoPushDao.getLatestPushedPublicationMoment(LayoutBranch.main)
        assertTrue(latestPushedPublish < publicationMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushedPublish, null)
        val (publishedLocationTracks, _) =
            publicationDao.fetchPublishedLocationTracks(setOf(publications[1].id)).getValue(publications[1].id)

        assertEquals(2, publications.size)
        assertEquals(publicationId, publications[1].id)
        assertEquals(locationTrackId, publishedLocationTracks[0].id)
    }

    @Test
    fun shouldReturnMultipleUnpublishedLayoutPublishes() {
        val locationTrack2Response = insertAndPublishLocationTrack()
        val publicationId2 =
            createPublication(locationTracks = listOf(Change(null, locationTrack2Response)), message = "Test")

        val latestPushedMoment = ratkoPushDao.getLatestPushedPublicationMoment(LayoutBranch.main)
        assertTrue(latestPushedMoment < publicationMoment)
        val publications = publicationDao.fetchPublicationsBetween(LayoutBranch.main, latestPushedMoment, null)

        val fetchedLayoutPublish = publications.find { it.id == publicationId }
        val fetchedLayoutPublish2 = publications.find { it.id == publicationId2 }

        assertNotNull(fetchedLayoutPublish)
        assertNotNull(fetchedLayoutPublish2)

        val (publishLocationTracks, _) =
            publicationDao
                .fetchPublishedLocationTracks(setOf(fetchedLayoutPublish.id))
                .getValue(fetchedLayoutPublish.id)
        val (publish2LocationTracks, _) =
            publicationDao
                .fetchPublishedLocationTracks(setOf(fetchedLayoutPublish2.id))
                .getValue(fetchedLayoutPublish2.id)

        assertEquals(1, publishLocationTracks.size)
        assertEquals(1, publish2LocationTracks.size)

        assertEquals(locationTrackId, publishLocationTracks[0].id)
        assertEquals(locationTrack2Response.id, publish2LocationTracks[0].id)
    }

    @Test
    fun `Should return latest publications`() {
        val locationTrack1Response = insertAndPublishLocationTrack()
        val publicationId1 =
            createPublication(locationTracks = listOf(Change(null, locationTrack1Response)), message = "Test")
        val locationTrack2Response = insertAndPublishLocationTrack()
        val publicationId2 =
            createPublication(locationTracks = listOf(Change(null, locationTrack2Response)), message = "Test")

        val publications = publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, 2)

        assertEquals(publications.size, 2)
        assertEquals(publications[0].id, publicationId2)
        assertEquals(publications[1].id, publicationId1)
    }

    @Test
    fun `should find push error if latest push failed`() {
        val ratkoPushId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.LOCATION_TRACK,
            locationTrackId,
        )
        ratkoPushDao.updatePushStatus(ratkoPushId, status = RatkoPushStatus.FAILED)
        val ratkoPushError = ratkoPushDao.getCurrentRatkoPushError()

        assertNotNull(ratkoPushError)
        assertEquals(locationTrackId, ratkoPushError.first.assetId)
        assertEquals(RatkoOperation.UPDATE, ratkoPushError.first.operation)
        assertEquals(publicationId, ratkoPushError.second)
    }

    @Test
    fun `should find push error if there are waiting pushes after it`() {
        val ratkoPushId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.LOCATION_TRACK,
            locationTrackId,
        )
        ratkoPushDao.updatePushStatus(ratkoPushId, status = RatkoPushStatus.FAILED)
        val locationTrackResponse = insertAndPublishLocationTrack()
        val publicationId2 = createPublication(locationTracks = listOf(Change(null, locationTrackResponse)))

        ratkoPushDao.startPushing(listOf(publicationId2))
        val ratkoPushError = ratkoPushDao.getCurrentRatkoPushError()

        assertNotNull(ratkoPushError)
        assertEquals(locationTrackId, ratkoPushError.first.assetId)
        assertEquals(RatkoOperation.UPDATE, ratkoPushError.first.operation)
        assertEquals(publicationId, ratkoPushError.second)
    }

    @Test
    fun `should not find any push errors if latest push is successful`() {
        val ratkoPushId = ratkoPushDao.startPushing(listOf(publicationId))
        ratkoPushDao.insertRatkoPushError(
            ratkoPushId,
            RatkoPushErrorType.PROPERTIES,
            RatkoOperation.UPDATE,
            RatkoAssetType.LOCATION_TRACK,
            locationTrackId,
        )
        ratkoPushDao.updatePushStatus(ratkoPushId, status = RatkoPushStatus.FAILED)
        val locationTrackResponse = insertAndPublishLocationTrack()
        val publicationId2 = createPublication(locationTracks = listOf(Change(null, locationTrackResponse)))

        val ratkoPushId2 = ratkoPushDao.startPushing(listOf(publicationId2))
        ratkoPushDao.updatePushStatus(ratkoPushId2, status = RatkoPushStatus.SUCCESSFUL)
        val ratkoPushError = ratkoPushDao.getCurrentRatkoPushError()

        assertNull(ratkoPushError)
    }

    fun insertAndPublishLocationTrack(): LayoutRowVersion<LocationTrack> =
        locationTrackAndGeometry(trackNumberId, draft = true).let { (track, geometry) ->
            val draftVersion = locationTrackService.saveDraft(LayoutBranch.main, track, geometry)
            locationTrackService.publish(LayoutBranch.main, draftVersion).published
        }

    fun createPublication(
        layoutBranch: LayoutBranch = LayoutBranch.main,
        trackNumbers: List<Change<LayoutRowVersion<LayoutTrackNumber>>> = listOf(),
        referenceLines: List<Change<LayoutRowVersion<ReferenceLine>>> = listOf(),
        locationTracks: List<Change<LayoutRowVersion<LocationTrack>>> = listOf(),
        switches: List<Change<LayoutRowVersion<LayoutSwitch>>> = listOf(),
        kmPosts: List<Change<LayoutRowVersion<LayoutKmPost>>> = listOf(),
        operationalPoints: List<Change<LayoutRowVersion<OperationalPoint>>> = listOf(),
        message: String = "",
    ): IntId<Publication> =
        publicationDao
            .createPublication(
                layoutBranch = layoutBranch,
                message = PublicationMessage.of(message),
                cause = PublicationCause.MANUAL,
                parentId = null,
            )
            .also { publicationId ->
                val calculatedChanges =
                    CalculatedChanges(
                        directChanges =
                            DirectChanges(
                                kmPostChanges = kmPosts.map { it.new.id },
                                referenceLineChanges = referenceLines.map { it.new.id },
                                trackNumberChanges =
                                    trackNumbers.map {
                                        TrackNumberChange(
                                            trackNumberId = it.new.id,
                                            changedKmNumbers = emptySet(),
                                            isStartChanged = false,
                                            isEndChanged = false,
                                        )
                                    },
                                locationTrackChanges =
                                    locationTracks.map {
                                        LocationTrackChange(
                                            locationTrackId = it.new.id,
                                            changedKmNumbers = emptySet(),
                                            isStartChanged = false,
                                            isEndChanged = false,
                                        )
                                    },
                                switchChanges = switches.map { SwitchChange(it.new.id, emptyList()) },
                                operationalPointChanges = operationalPoints.map { it.new.id },
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
