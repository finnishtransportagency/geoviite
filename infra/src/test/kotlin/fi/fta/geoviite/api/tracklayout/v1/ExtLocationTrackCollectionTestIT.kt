package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.util.FreeText
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
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))

        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        val officialTracks =
            listOf(1, 2, 3)
                .map { index ->
                    mainDraftContext
                        .saveLocationTrack(
                            locationTrackAndGeometry(
                                trackNumberId,
                                segment,
                                description = "official description $index",
                            )
                        )
                        .id
                }
                .map { trackId ->
                    trackId to
                        someOid<LocationTrack>().also { oid ->
                            locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
                        }
                }

        val newestPublication =
            publicationTestSupportService
                .publish(
                    LayoutBranch.main,
                    publicationRequestIds(
                        trackNumbers = listOf(trackNumberId),
                        referenceLines = listOf(referenceLineId),
                        locationTracks = officialTracks.map { (id, _) -> id },
                    ),
                )
                .let { summary -> publicationDao.getPublication(requireNotNull(summary.publicationId)) }

        val tracksBeforeModifications =
            officialTracks.map { (id, oid) ->
                oid to locationTrackService.getWithGeometryOrThrow(MainLayoutContext.official, id)
            }

        tracksBeforeModifications
            .map { (_, trackAndGeometry) -> trackAndGeometry }
            .forEach { (track, geometry) ->
                locationTrackService.saveDraft(
                    LayoutBranch.main,
                    track.copy(
                        description = FreeText("only in draft"),
                    ),
                    geometry,
                )
            }

        mainDraftContext
            .saveLocationTrack(
                locationTrackAndGeometry(trackNumberId, segment, description = "some draft-only track description")
            )
            .id

        val response = api.getLocationTrackCollection()

        assertEquals(newestPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(officialTracks.size, response.sijaintiraiteet.size)

        tracksBeforeModifications
            .map { (oid, trackAndGeometry) -> oid to trackAndGeometry.first }
            .forEach { (oid, officialTrack) ->
                val responseTrack =
                    response.sijaintiraiteet
                        .find { responseTrack -> responseTrack.sijaintiraide_oid == oid.toString() }
                        .let(::requireNotNull)

                assertEquals(officialTrack.description.toString(), responseTrack.kuvaus)
            }
    }

    @Test fun `Location track listing respects the track layout version argument`() {}

    @Test fun `Location track listing respects the coordinate system argument`() {}

    @Test fun `Only modified location tracks are returned by the modified location track listing`() {}

    @Test fun `Location track listing contains expected fields for each location track`() {}

    @Test fun `Location track listing contains the correct state for a location track`() {}

    @Test fun `Location track listing returns 404 if the track layout version is not found`() {}
}
