package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class LocationTrackCollectionTestIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val layoutTrackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
    private val publicationTestSupportService: PublicationTestSupportService,
    private val extTestDataService: ExtApiTestDataServiceV1,
) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Newest location track listing is returned by default`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))
        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        val tracks =
            listOf(1, 2, 3)
                .map { _ -> mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id }
                .map { trackId ->
                    trackId to
                        someOid<LocationTrack>().also { oid ->
                            locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
                        }
                }

        publicationTestSupportService.publish(
            LayoutBranch.main,
            publicationRequestIds(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = tracks.map { (id, _) -> id },
            ),
        )

        val newestButEmptyPublication =
            publicationTestSupportService.publish(LayoutBranch.main, publicationRequestIds()).let { result ->
                publicationDao.getPublication(requireNotNull(result.publicationId))
            }

        val response = api.getLocationTrackCollection()

        assertEquals(newestButEmptyPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(tracks.size, response.sijaintiraiteet.size)

        val responseOids = response.sijaintiraiteet.map { track -> track.sijaintiraide_oid }
        tracks.forEach { (id, oid) -> assertTrue(oid.toString() in responseOids) }
    }

    @Test
    fun `Newest location track listing does not contain draft tracks`() {

        //        val (track1, geom1) =
        //            mainDraftContext.saveAndFetchLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
        //
        //        val (track2, geom2) =
        //            mainDraftContext.saveAndFetchLocationTrack(locationTrackAndGeometry(trackNumberId, segment))

        //        val modifiedDescription = "official modified track 1"
        //        mainDraftContext.saveLocationTrack(track1.copy(description = FreeText(modifiedDescription)) to geom1)
    }

    @Test fun `Location track listing respects the given track layout version argument`() {}

    @Test fun `Location track listing respects the given coordinate system argument`() {}

    @Test fun `Only modified location tracks are returned by the modified location track listing`() {}

    @Test fun `Location track listing contains expected fields for each location track`() {}

    @Test fun `Location track listing contains the correct state for a location track`() {}

    //
    @Test fun `Location track listing returns 404 if the track layout version is not found`() {}
}
