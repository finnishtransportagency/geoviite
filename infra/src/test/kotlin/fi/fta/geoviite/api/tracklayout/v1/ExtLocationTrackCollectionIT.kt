package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
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
class ExtLocationTrackCollectionIT
@Autowired
constructor(mockMvc: MockMvc, private val locationTrackService: LocationTrackService) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Newest location track listing is returned by default`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val tracks =
            listOf(1, 2, 3).map { _ ->
                mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment))
            }

        testDBService.publish(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks.map { (id, _) -> id },
        )

        val newestButEmptyPublication = testDBService.publish()
        val response = api.locationTrackCollection.get()

        assertEquals(newestButEmptyPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(tracks.size, response.sijaintiraiteet.size)

        val responseOids = response.sijaintiraiteet.map { track -> track.sijaintiraide_oid }
        tracks.forEach { (_, oid) -> assertTrue(oid.toString() in responseOids) }
    }

    @Test
    fun `Newest location track listing does not contain draft tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val officialTracks =
            listOf(1, 2, 3).map { index ->
                mainDraftContext.saveWithOid(
                    locationTrack(trackNumberId, description = "official description $index"),
                    trackGeometryOfSegments(segment),
                )
            }

        val newestPublication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = officialTracks.map { (id, _) -> id },
            )

        val tracksBeforeModifications = officialTracks.map { (id, oid) ->
            oid to locationTrackService.getWithGeometryOrThrow(MainLayoutContext.official, id)
        }

        tracksBeforeModifications
            .map { (_, trackAndGeometry) -> trackAndGeometry }
            .forEach { (track, geometry) ->
                locationTrackService.saveDraft(
                    LayoutBranch.main,
                    track.copy(description = FreeText("only in draft")),
                    geometry,
                )
            }

        mainDraftContext.save(
            locationTrack(trackNumberId, description = "some draft-only track description"),
            trackGeometryOfSegments(segment),
        )

        val response = api.locationTrackCollection.get()

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

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        testDBService.publish(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        val tracksToPublications =
            listOf(1, 2, 3).map { totalAmountOfLocationTracks ->
                val (trackId, trackOid) =
                    mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment))
                val publication = testDBService.publish(locationTracks = listOf(trackId))

                Triple(totalAmountOfLocationTracks, trackOid, publication)
            }

        tracksToPublications.forEach { (amountOfPublishedLocationTracks, trackOid, publication) ->
            val response = api.locationTrackCollection.get("rataverkon_versio" to publication.uuid.toString())

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

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val (trackId, trackOid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment))

        val publication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        tests.forEach { (epsgCode, expectedStart, expectedEnd) ->
            val response =
                api.locationTrackCollection.get(
                    "rataverkon_versio" to publication.uuid.toString(),
                    "koordinaatisto" to epsgCode,
                )

            val responseTrack =
                response.sijaintiraiteet
                    .find { track -> track.sijaintiraide_oid == trackOid.toString() }
                    .let(::requireNotNull)

            assertEquals(epsgCode, response.koordinaatisto)
            assertExtStartAndEnd(
                expectedStart,
                expectedEnd,
                requireNotNull(responseTrack.alkusijainti),
                requireNotNull(responseTrack.loppusijainti),
            )
        }
    }

    @Test
    fun `Location track listing should not contain deleted tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val (deletedTrackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId, state = LocationTrackState.DELETED),
                trackGeometryOfSegments(segment),
            )

        testDBService.publish(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(deletedTrackId),
        )

        val response = api.locationTrackCollection.get()
        assertEquals(0, response.sijaintiraiteet.size)
    }

    @Test
    fun `Location track listing should contain all but deleted tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val tracks =
            LocationTrackState.entries
                .filter { it != LocationTrackState.DELETED }
                .map { state ->
                    val (trackId, trackOid) =
                        mainDraftContext.saveWithOid(
                            locationTrack(trackNumberId, state = state),
                            trackGeometryOfSegments(segment),
                        )

                    trackOid to trackId
                }

        testDBService.publish(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks.map { (_, id) -> id },
        )

        val response = api.locationTrackCollection.get()
        assertEquals(tracks.size, response.sijaintiraiteet.size)

        tracks.forEach { (oid, _) ->
            assertTrue(
                response.sijaintiraiteet.any { responseTrack -> responseTrack.sijaintiraide_oid == oid.toString() }
            )
        }
    }

    @Test
    fun `Location track modifications listing only lists modified location tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val allTracks =
            listOf(1, 2, 3, 4).map { _ ->
                val trackVersion = mainDraftContext.save(locationTrack(trackNumberId), trackGeometryOfSegments(segment))

                val oid = mainDraftContext.generateOid(trackVersion.id)

                val (track, geometry) = locationTrackService.getWithGeometry(trackVersion)

                Triple(oid, track, geometry)
            }

        val initialPublication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = allTracks.map { (_, track, _) -> track.id as IntId },
            )

        val modifiedDescription = "this is a modified description, previous publication uuid=${initialPublication.uuid}"
        val modifiedTracks =
            listOf(allTracks[1], allTracks[2]).map { (oid, track, geometry) ->
                mainDraftContext.save(track.copy(description = FreeText(modifiedDescription)), geometry)

                oid to track.id as IntId
            }

        val secondPublication = testDBService.publish(locationTracks = modifiedTracks.map { (_, id) -> id })
        val response = api.locationTrackCollection.getModifiedSince(initialPublication.uuid)
        assertEquals(modifiedTracks.size, response.sijaintiraiteet.size)
        assertEquals(secondPublication.uuid.toString(), response.loppuversio)
        response.sijaintiraiteet.forEach { track -> assertEquals(modifiedDescription, track.kuvaus) }
        assertEquals(
            response,
            api.locationTrackCollection.getModifiedBetween(initialPublication.uuid, secondPublication.uuid),
        )
    }

    @Test
    fun `Location track modifications listing lists tracks with calculated changes`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId1, trackNumberOid1) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId1 = mainDraftContext.save(referenceLine(trackNumberId1), referenceLineGeometry(segment)).id
        val (trackNumberId2, trackNumberOid2) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId2 = mainDraftContext.save(referenceLine(trackNumberId2), referenceLineGeometry(segment)).id

        val trackGeom = trackGeometryOfSegments(segment(Point(20.0, 0.0), Point(40.0, 0.0)))
        val (trackId1, trackOid1) = mainDraftContext.saveWithOid(locationTrack(trackNumberId1), trackGeom)
        val (trackId2, trackOid2) = mainDraftContext.saveWithOid(locationTrack(trackNumberId1), trackGeom)
        val (trackId3, _) = mainDraftContext.saveWithOid(locationTrack(trackNumberId2), trackGeom)

        val basePublication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId1, trackNumberId2),
                referenceLines = listOf(referenceLineId1, referenceLineId2),
                locationTracks = listOf(trackId1, trackId2, trackId3),
            )
        getTracksByTrackNumberOids(trackNumberOid1, trackNumberOid2).forEach { track ->
            assertEquals("0000+0020.000", track.alkusijainti?.rataosoite)
        }
        api.locationTrackCollection.assertNoModificationSince(basePublication.uuid)

        initUser()
        mainDraftContext.save(
            mainOfficialContext.fetch(referenceLineId1)!!.copy(startAddress = TrackMeter("0001+0010.000")),
            referenceLineGeometry(segment),
        )
        val rlPublication = testDBService.publish(referenceLines = listOf(referenceLineId1))
        getTracksByTrackNumberOids(trackNumberOid1).forEach { track ->
            assertEquals("0001+0030.000", track.alkusijainti?.rataosoite)
        }
        assertEquals(
            setOf(trackOid1.toString() to "0001+0030.000", trackOid2.toString() to "0001+0030.000"),
            api.locationTrackCollection
                .getModifiedBetween(basePublication.uuid, rlPublication.uuid)
                .sijaintiraiteet
                .map { it.sijaintiraide_oid to it.alkusijainti?.rataosoite }
                .toSet(),
        )
        api.locationTrackCollection.assertNoModificationSince(rlPublication.uuid)

        initUser()
        val kmpId =
            mainDraftContext.save(kmPost(trackNumberId1, KmNumber(4), gkLocation = kmPostGkLocation(10.0, 0.0))).id
        val kmpPublication = testDBService.publish(kmPosts = listOf(kmpId))
        getTracksByTrackNumberOids(trackNumberOid1).forEach { track ->
            assertEquals("0004+0010.000", track.alkusijainti?.rataosoite)
        }
        assertEquals(
            setOf(trackOid1.toString() to "0004+0010.000", trackOid2.toString() to "0004+0010.000"),
            api.locationTrackCollection
                .getModifiedBetween(rlPublication.uuid, kmpPublication.uuid)
                .sijaintiraiteet
                .map { it.sijaintiraide_oid to it.alkusijainti?.rataosoite }
                .toSet(),
        )
        api.locationTrackCollection.assertNoModificationSince(kmpPublication.uuid)

        getTracksByTrackNumberOids(trackNumberOid2).forEach { track ->
            assertEquals("0000+0020.000", track.alkusijainti?.rataosoite)
        }
    }

    private fun getTracksByTrackNumberOids(vararg tnOids: Oid<LayoutTrackNumber>): List<ExtTestLocationTrackV1> =
        api.locationTrackCollection.get().sijaintiraiteet.filter { tn ->
            tn.ratanumero_oid in tnOids.map { oid -> oid.toString() }
        }

    @Test
    fun `Location track modifications listing contains tracks in all states (including deleted)`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val tracks =
            LocationTrackState.entries.map { state ->
                mainDraftContext.saveWithOid(
                    locationTrack(trackNumberId, state = state),
                    trackGeometryOfSegments(segment),
                )
            }

        val initialPublication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = tracks.map { (id, _) -> id },
            )

        val modifiedTrackDescription = "this is a modified track from publicationUuid=${initialPublication.uuid}"
        tracks.forEach { (id, _) ->
            mainDraftContext.mutate(id) { track -> track.copy(description = FreeText(modifiedTrackDescription)) }
        }

        val secondPublication = testDBService.publish(locationTracks = tracks.map { (id, _) -> id })

        val response =
            api.locationTrackCollection.getModified(
                "alkuversio" to initialPublication.uuid.toString(),
                "loppuversio" to secondPublication.uuid.toString(),
            )

        assertEquals(tracks.size, response.sijaintiraiteet.size)
        response.sijaintiraiteet.forEach { track -> assertEquals(modifiedTrackDescription, track.kuvaus) }
    }

    @Test
    fun `Location track modifications listing respects start & end track layout version arguments`() {
        // Create an initial publication which should not be in any of the following responses.
        // This verifies that the api does not always use the first publication as the comparison base.
        testDBService.publish()

        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val trackVersion = mainDraftContext.save(locationTrack(trackNumberId), trackGeometryOfSegments(segment))
        val trackId = trackVersion.id
        val trackOid = mainDraftContext.generateOid(trackId)

        val publicationStart =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        val modifiedDescription = "previous track layout version uuid=${publicationStart.uuid}"
        locationTrackService.getWithGeometry(trackVersion).let { (track, geometry) ->
            mainDraftContext.save(track.copy(description = FreeText(modifiedDescription)), geometry)
        }

        val publicationEnd = testDBService.publish(locationTracks = listOf(trackId))

        // Create a publication after the last one which should not be used either.
        // This verifies that the api does not always use the last publication as the comparison target.
        testDBService.publish()

        val response =
            api.locationTrackCollection.getModified(
                "alkuversio" to publicationStart.uuid.toString(),
                "loppuversio" to publicationEnd.uuid.toString(),
            )

        assertEquals(publicationStart.uuid.toString(), response.alkuversio)
        assertEquals(publicationEnd.uuid.toString(), response.loppuversio)

        assertEquals(
            modifiedDescription,
            response.sijaintiraiteet.find { track -> track.sijaintiraide_oid == trackOid.toString() }?.kuvaus,
        )
    }

    @Test
    fun `Location track modifications listing uses the newest track layout version if end version is not supplied`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val trackVersion = mainDraftContext.save(locationTrack(trackNumberId), trackGeometryOfSegments(segment))
        val trackId = trackVersion.id
        val trackOid = mainDraftContext.generateOid(trackId)

        val publicationStart =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        val modifiedDescription = "previous track layout version uuid=${publicationStart.uuid}"
        locationTrackService.getWithGeometry(trackVersion).let { (track, geometry) ->
            mainDraftContext.save(track.copy(description = FreeText(modifiedDescription)), geometry)
        }

        val publicationEnd = testDBService.publish(locationTracks = listOf(trackId))

        val response = api.locationTrackCollection.getModified("alkuversio" to publicationStart.uuid.toString())

        assertEquals(publicationStart.uuid.toString(), response.alkuversio)
        assertEquals(publicationEnd.uuid.toString(), response.loppuversio)

        assertEquals(
            modifiedDescription,
            response.sijaintiraiteet.find { track -> track.sijaintiraide_oid == trackOid.toString() }?.kuvaus,
        )
    }

    @Test
    fun `Location track collection is filtered by track number OID & track name`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (matchingTnId, matchingTnOid) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val matchingRlId = mainDraftContext.save(referenceLine(matchingTnId), referenceLineGeometry(segment)).id
        val (otherTnId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val otherRlId = mainDraftContext.save(referenceLine(otherTnId), referenceLineGeometry(segment)).id

        val (matchingTrackId, matchingOid) =
            mainDraftContext.saveWithOid(
                locationTrack(matchingTnId, name = "MATCHING TRACK"),
                trackGeometryOfSegments(segment),
            )
        val (otherTrackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(otherTnId, name = "OTHER TRACK"),
                trackGeometryOfSegments(segment),
            )

        testDBService.publish(
            trackNumbers = listOf(matchingTnId, otherTnId),
            referenceLines = listOf(matchingRlId, otherRlId),
            locationTracks = listOf(matchingTrackId, otherTrackId),
        )

        // TrackNumber OID match (exact)
        api.locationTrackCollection.get(TRACK_NUMBER_OID to matchingTnOid.toString()).also { response ->
            assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
        }
        // Exact name match
        api.locationTrackCollection.get(LOCATION_TRACK_NAME to "MATCHING TRACK").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
        }
        // Case-insensitive partial match on name
        api.locationTrackCollection.get(LOCATION_TRACK_NAME to "atching").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
        }
    }

    @Test
    fun `Location track change-list is filtered by track number OID & track name`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (matchingTnId, matchingTnOid) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val matchingRlId = mainDraftContext.save(referenceLine(matchingTnId), referenceLineGeometry(segment)).id
        val (otherTnId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val otherRlId = mainDraftContext.save(referenceLine(otherTnId), referenceLineGeometry(segment)).id

        val matchingTrackVersion =
            mainDraftContext.save(
                locationTrack(matchingTnId, name = "MATCHING TRACK"),
                trackGeometryOfSegments(segment),
            )
        val matchingTrackId = matchingTrackVersion.id
        val matchingOid = mainDraftContext.generateOid(matchingTrackId)
        val otherTrackVersion =
            mainDraftContext.save(locationTrack(otherTnId, name = "OTHER TRACK"), trackGeometryOfSegments(segment))
        val otherTrackId = otherTrackVersion.id
        mainDraftContext.generateOid(otherTrackId)

        val fromPublication =
            testDBService
                .publish(
                    trackNumbers = listOf(matchingTnId, otherTnId),
                    referenceLines = listOf(matchingRlId, otherRlId),
                    locationTracks = listOf(matchingTrackId, otherTrackId),
                )
                .uuid

        locationTrackService.getWithGeometry(matchingTrackVersion).let { (track, geometry) ->
            mainDraftContext.save(track.copy(description = FreeText("changed matching")), geometry)
        }
        locationTrackService.getWithGeometry(otherTrackVersion).let { (track, geometry) ->
            mainDraftContext.save(track.copy(description = FreeText("changed other")), geometry)
        }
        testDBService.publish(locationTracks = listOf(matchingTrackId, otherTrackId))

        // TrackNumber OID match (exact)
        api.locationTrackCollection
            .getModifiedSince(fromPublication, TRACK_NUMBER_OID to matchingTnOid.toString())
            .also { response ->
                assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
            }
        // Exact name match
        api.locationTrackCollection.getModifiedSince(fromPublication, LOCATION_TRACK_NAME to "MATCHING TRACK").also {
            response ->
            assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
        }
        // Case-insensitive partial match on name
        api.locationTrackCollection.getModifiedSince(fromPublication, LOCATION_TRACK_NAME to "atching").also { response
            ->
            assertEquals(listOf(matchingOid.toString()), response.sijaintiraiteet.map { it.sijaintiraide_oid })
        }
    }
}
