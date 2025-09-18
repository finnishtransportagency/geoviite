package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
class ExtLocationTrackTestIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val locationTrackService: LocationTrackService,
    private val extTestDataService: ExtApiTestDataServiceV1,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @Test
    fun `Newest official location track is returned by default`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(
                mainDraftContext,
                segments = listOf(segment),
            )

        val (track, geometry) =
            mainDraftContext
                .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
                .let(locationTrackService::getWithGeometry)

        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, track.id as IntId, oid)
            }

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(track.id as IntId),
            )

        val modifiedDescription = "modified description after publication=${publication1.uuid}"
        mainDraftContext.saveLocationTrack(track.copy(description = FreeText(modifiedDescription)) to geometry)

        val responseAfterCreatingDraftTrack = api.locationTracks.get(oid)
        assertEquals(publication1.uuid.toString(), responseAfterCreatingDraftTrack.rataverkon_versio)
        assertNotEquals(modifiedDescription, responseAfterCreatingDraftTrack.sijaintiraide.kuvaus)

        val publication2 = extTestDataService.publishInMain(locationTracks = listOf(track.id))
        val responseAfterPublishingModification = api.locationTracks.get(oid)

        assertEquals(publication2.uuid.toString(), responseAfterPublishingModification.rataverkon_versio)
        assertEquals(modifiedDescription, responseAfterPublishingModification.sijaintiraide.kuvaus)
    }

    @Test
    fun `Location track api respects the track layout version argument`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(
                mainDraftContext,
                segments = listOf(segment),
            )

        val (track, geometry) =
            mainDraftContext
                .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
                .let(locationTrackService::getWithGeometry)

        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, track.id as IntId, oid)
            }

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(track.id as IntId),
            )

        val publication2 = extTestDataService.publishInMain()

        val modifiedDescription = "modified description after publication=${publication1.uuid}"
        mainDraftContext.saveLocationTrack(track.copy(description = FreeText(modifiedDescription)) to geometry)

        val publication3 = extTestDataService.publishInMain(locationTracks = listOf(track.id))

        val responses =
            listOf(publication1, publication2, publication3).map { publication ->
                val response = api.locationTracks.get(oid, "rataverkon_versio" to publication.uuid.toString())
                assertEquals(publication.uuid.toString(), response.rataverkon_versio)

                response
            }

        assertNotEquals(modifiedDescription, responses[0].sijaintiraide.kuvaus)
        assertNotEquals(modifiedDescription, responses[1].sijaintiraide.kuvaus)
        assertEquals(modifiedDescription, responses[2].sijaintiraide.kuvaus)
    }

    @Test
    fun `Location track api respects the coordinate system argument`() {
        val helsinkiRailwayStationTm35Fin = Point(385782.89, 6672277.83)
        val helsinkiRailwayStationTm35FinPlus10000 = Point(395782.89, 6682277.83)

        val tests =
            listOf(
                Triple("EPSG:3067", helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000),

                // EPSG:4326 == WGS84, converted using https://epsg.io/transform
                Triple("EPSG:4326", Point(24.9414003, 60.1713788), Point(25.1163757, 60.2637958)),
            )

        val segment = segment(helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000)

        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(mainDraftContext, segments = listOf(segment))

        val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id
        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        tests.forEach { (epsgCode, expectedStart, expectedEnd) ->
            val response = api.locationTracks.get(oid, "koordinaatisto" to epsgCode)

            assertEquals(epsgCode, response.koordinaatisto)
            assertExtStartAndEnd(expectedStart, expectedEnd, response.sijaintiraide)
        }
    }

    @Test
    fun `Location track api should return track information regardless of its state`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(mainDraftContext, segments = listOf(segment))

        val tracks =
            LocationTrackState.entries.map { state ->
                val trackId =
                    mainDraftContext
                        .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment, state = state))
                        .id

                val trackOid =
                    someOid<LocationTrack>().also { oid ->
                        locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
                    }

                Triple(trackOid, trackId, state)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks.map { (_, id, _) -> id },
        )

        tracks.forEach { (oid, _, state) ->
            val response = api.locationTracks.get(oid)

            assertEquals(oid.toString(), response.sijaintiraide.sijaintiraide_oid)
            assertExtLocationTrackState(state, response.sijaintiraide.tila)
        }
    }

    @Test
    fun `Location track modifications api should return track information regardless of its state`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceWithOid(mainDraftContext, segments = listOf(segment))

        val tracks =
            LocationTrackState.entries.map { state ->
                val (track, geometry) =
                    mainDraftContext
                        .saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment, state = state))
                        .let(locationTrackService::getWithGeometry)

                val trackOid =
                    someOid<LocationTrack>().also { oid ->
                        locationTrackService.insertExternalId(LayoutBranch.main, track.id as IntId, oid)
                    }

                Triple(trackOid, track, geometry)
            }

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = tracks.map { (_, track, _) -> track.id as IntId },
            )

        val modifiedDescription = "this is a modified location track after publication=${publication1.uuid}"
        tracks.forEach { (_, track, geometry) ->
            mainDraftContext.saveLocationTrack(track.copy(description = FreeText(modifiedDescription)) to geometry)
        }

        val publication2 =
            extTestDataService.publishInMain(
                locationTracks =
                    tracks.map {
                        (
                            _,
                            track,
                            _,
                        ) ->
                        track.id as IntId
                    }
            )

        tracks.forEach { (oid, track, _) ->
            val response =
                api.locationTracks.getModified(
                    oid,
                    "alkuversio" to publication1.uuid.toString(),
                    "loppuversio" to publication2.uuid.toString(),
                )

            assertEquals(oid.toString(), response.sijaintiraide.sijaintiraide_oid)
            assertEquals(modifiedDescription, response.sijaintiraide.kuvaus)
            assertExtLocationTrackState(track.state, response.sijaintiraide.tila)
        }
    }

    @Test
    fun `Location track api endpoints return HTTP 400 if the OID is invalid format`() { // todo abstract this to even
        // larger scale (not location
        // track specific)
        val invalidOid = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST

        val tests =
            listOf(
                api.locationTracks::getWithExpectedError,
                api.locationTracks::getGeometryWithExpectedError,
                api.locationTracks::getModifiedWithExpectedError,
            )

        tests.forEach { apiCall -> apiCall(invalidOid, emptyArray(), expectedStatus) }
    }

    @Test
    fun `Location track api returns HTTP 204 when the given track does not exist in the specified track layout version`() {
        // TODO
    }

    @Test
    fun `Location track modifications api returns HTTP 204 if there are no modifications`() {
        // TODO
    }

    @Test
    fun `Location track modifications api returns HTTP 200 when the track has been created between track layout versions`() {}

    @Test
    fun `Location track modifications api returns HTTP 204 when the given track does not exist between the track layout versions`() {}

    // TODO Tests for all of the weird cases of creation/etc

    @Test fun `Location track geometry api respects the track layout version argument`() {}

    @Test fun `Location track geometry api respects the coordinate system argument`() {}
}
