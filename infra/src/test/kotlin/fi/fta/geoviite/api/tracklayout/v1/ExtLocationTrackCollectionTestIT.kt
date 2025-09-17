package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
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
import org.springframework.http.HttpStatus
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

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks.map { (id, _) -> id },
        )

        val newestButEmptyPublication = extTestDataService.publishInMain()
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
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = officialTracks.map { (id, _) -> id },
            )

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

    @Test
    fun `Location track listing respects the track layout version argument`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))

        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
        )

        val tracksToPublications =
            listOf(1, 2, 3).map { totalAmountOfLocationTracks ->
                val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id
                val publication =
                    extTestDataService.publishInMain(
                        locationTracks = listOf(trackId),
                    )

                val trackOid =
                    someOid<LocationTrack>().also { oid ->
                        locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
                    }

                Triple(totalAmountOfLocationTracks, trackOid, publication)
            }

        tracksToPublications.forEach { (amountOfPublishedLocationTracks, trackOid, publication) ->
            val response =
                api.getLocationTrackCollection(
                    "rataverkon_versio" to publication.uuid.toString(),
                )

            assertEquals(amountOfPublishedLocationTracks, response.sijaintiraiteet.size)
            assertTrue(response.sijaintiraiteet.any { track -> track.sijaintiraide_oid == trackOid.toString() })
        }
    }

    @Test
    fun `Location track listing respects the coordinate system argument`() {
        val helsinkiRailwayStationTm35Fin = Point(385782.89, 6672277.83)
        val helsinkiRailwayStationTm35FinPlus10000 = Point(395782.89, 6682277.83)

        val tests =
            listOf(
                Triple("EPSG:3067", helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000),

                // EPSG:4326 == WGS84, converted using https://epsg.io/transform
                Triple("EPSG:4326", Point(24.9414003, 60.1713788), Point(25.1163757, 60.2637958)),
            )

        val segment = segment(helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000)

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))

        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id
        val trackOid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
            }

        val publication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        tests.forEach { (epsgCode, expectedStart, expectedEnd) ->
            val response =
                api.getLocationTrackCollection(
                    "rataverkon_versio" to publication.uuid.toString(),
                    "koordinaatisto" to epsgCode,
                )

            val responseTrack =
                response.sijaintiraiteet
                    .find { track -> track.sijaintiraide_oid == trackOid.toString() }
                    .let(::requireNotNull)

            assertEquals(epsgCode, response.koordinaatisto)
            assertTrackStartAndEnd(expectedStart, expectedEnd, responseTrack)
        }
    }

    @Test
    fun `Location track listing should not contain deleted tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))

        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        val deletedTrackId =
            mainDraftContext
                .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment, state = LocationTrackState.DELETED))
                .id

        locationTrackService.insertExternalId(LayoutBranch.main, deletedTrackId, someOid())

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(deletedTrackId),
        )

        val response = api.getLocationTrackCollection()
        assertEquals(0, response.sijaintiraiteet.size)
    }

    @Test
    fun `Location track listing should contain all but deleted tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId) =
            extTestDataService.insertTrackNumberAndReferenceLine(mainDraftContext, segments = listOf(segment))

        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())

        val tracks =
            LocationTrackState.entries
                .filter { it != LocationTrackState.DELETED }
                .map { state ->
                    val trackId =
                        mainDraftContext
                            .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment, state = state))
                            .id
                    val trackOid =
                        someOid<LocationTrack>().also { oid ->
                            locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
                        }

                    trackOid to trackId
                }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks.map { (_, id) -> id },
        )

        val response = api.getLocationTrackCollection()
        assertEquals(tracks.size, response.sijaintiraiteet.size)

        tracks.forEach { (oid, _) ->
            assertTrue(
                response.sijaintiraiteet.any { responseTrack -> responseTrack.sijaintiraide_oid == oid.toString() }
            )
        }
    }

    @Test fun `Only modified location tracks are returned by the modified location track listing`() {}

    @Test
    fun `Location track listing returns HTTP 400 if the track layout version is invalid format`() {
        api.getLocationTrackCollectionWithExpectedError(
            "rataverkon_versio" to "asd",
            httpStatus = HttpStatus.BAD_REQUEST,
        )
    }

    @Test
    fun `Location track listing returns HTTP 404 if the track layout version is not found`() {
        api.getLocationTrackCollectionWithExpectedError(
            "rataverkon_versio" to "00000000-0000-0000-0000-000000000000",
            httpStatus = HttpStatus.NOT_FOUND,
        )
    }

    @Test fun `Location track modification listing contains tracks in all states (including deleted)`() {}

    @Test fun `Location track modification listing is empty when there are no modifications`() {} // TODO Or 204?
}

private fun assertTrackStartAndEnd(
    expectedStart: Point,
    expectedEnd: Point,
    responseTrack: ExtTestLocationTrackV1,
) {

    assertEquals(expectedStart.x, requireNotNull(responseTrack.alkusijainti?.x), 0.0001)
    assertEquals(expectedStart.y, requireNotNull(responseTrack.alkusijainti.y), 0.0001)
    assertEquals(
        expectedEnd.x,
        requireNotNull(responseTrack.loppusijainti?.x),
        0.0001,
    )
    assertEquals(
        expectedEnd.y,
        requireNotNull(responseTrack.loppusijainti.y),
        0.0001,
    )
}
