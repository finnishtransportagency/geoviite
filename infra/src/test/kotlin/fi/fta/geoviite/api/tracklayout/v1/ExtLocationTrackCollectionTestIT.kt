package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
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
) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Newest location track listing is returned by default`() {
        testDBService.clearAllTables()

        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val segment2 = segment(Point(100.0, 0.0), Point(200.0, 0.0))

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val referenceLineId =
            mainOfficialContext
                .saveReferenceLine(
                    referenceLineAndAlignment(trackNumberId = trackNumberId, segments = listOf(segment, segment2))
                )
                .id

        //        val trackNumberId = mainDraftContext.createLayoutTrackNumberAndReferenceLine(alignment(segment,
        // segment2)).id
        val trackNumberOid =
            someOid<LayoutTrackNumber>().also { oid ->
                layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, oid)
            }

        //        val (track1, geom1) =
        //            mainDraftContext.saveAndFetchLocationTrack(locationTrackAndGeometry(trackNumberId, segment,
        // segment2))

        val (track1, geom1) =
            mainOfficialContext
                .saveLocationTrack(
                    locationTrackAndGeometry(
                        trackNumberId = trackNumberId,
                        segments = listOf(segment, segment2),
                    )
                )
                .let(locationTrackService::getWithGeometry)

        //        val (track2, geom2) =
        //            mainDraftContext.saveAndFetchLocationTrack(locationTrackAndGeometry(trackNumberId, segment,
        // segment2))

        val (track2, geom2) =
            mainOfficialContext
                .saveLocationTrack(
                    locationTrackAndGeometry(
                        trackNumberId = trackNumberId,
                        segments = listOf(segment, segment2),
                    )
                )
                .let(locationTrackService::getWithGeometry)

        val trackOids =
            listOf(track1, track2).map { track ->
                someOid<LocationTrack>().also { oid ->
                    locationTrackService.insertExternalId(LayoutBranch.main, track.id as IntId, oid)
                }
            }

        //                publicationTestSupportService.publish(
        //                    LayoutBranch.main,
        //                    publicationRequestIds(
        //                        trackNumbers = listOf(trackNumberId),
        //                        referenceLines = listOf(referenceLineId),
        //                        locationTracks = listOf(track1.id, track2.id).map { id -> id as IntId },
        //                    ),
        //                )

        //        mainDraftContext.saveLocationTrack(track1.copy(description = FreeText("official modified track 1")) to
        // geom1)

        val newestPublication =
            publicationTestSupportService
                //                .publish(LayoutBranch.main, publicationRequestIds(locationTracks = listOf(track1.id as
                // IntId)))
                .publish(LayoutBranch.main, publicationRequestIds())
                .let { result -> publicationDao.getPublication(requireNotNull(result.publicationId)) }

        val response = api.getLocationTrackCollection()

        assertEquals(newestPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(2, response.sijaintiraiteet.size)
        trackOids.forEach { oid ->
            assertTrue(oid.toString() in response.sijaintiraiteet.map { track -> track.sijaintiraide_oid })
        }
    }

    fun `Newest location track listing does not contain draft tracks`() {}

    @Test fun `Location track listing respects the given track layout version argument`() {}

    @Test fun `Location track listing respects the given coordinate system argument`() {}

    @Test fun `Only modified location tracks are returned by the modified location track listing`() {}

    @Test fun `Location track listing contains expected fields for each location track`() {}

    @Test fun `Location track listing contains the correct state for a location track`() {}

    //
    @Test fun `Location track listing returns 404 if the track layout version is not found`() {}
}
