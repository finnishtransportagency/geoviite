package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

const val COORDINATE_DELTA = 0.001

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtLocationTrackIT
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
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

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
    }

    @Test
    fun `Location track api respects the track layout version argument`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

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
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

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
            assertExtStartAndEnd(
                expectedStart,
                expectedEnd,
                requireNotNull(response.sijaintiraide.alkusijainti),
                requireNotNull(response.sijaintiraide.loppusijainti),
            )
        }
    }

    @Test
    fun `Official geometry is returned at correct track layout version state`() {
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
                startAddress = TrackMeter("0001+0100.000"),
            )

        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val trackId = mainDraftContext.save(locationTrack(trackNumberId), geometry).id
        val oid = mainDraftContext.generateOid(trackId)

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        api.locationTrackGeometry.get(oid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0100.000", "0001+0200.000", geometry, 101)
        }

        val newGeometry = trackGeometryOfSegments(segment(Point(10.0, 10.0), Point(90.0, 10.0)))
        initUser()
        mainDraftContext.fetch(trackId).also { track -> mainDraftContext.save(track!!, newGeometry) }

        val publication2 = extTestDataService.publishInMain(locationTracks = listOf(trackId))
        api.locationTrackGeometry.get(oid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0110.000", "0001+0190.000", newGeometry, 81)
        }

        api.locationTrackGeometry.getAtVersion(oid, publication2.uuid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0110.000", "0001+0190.000", newGeometry, 81)
        }

        api.locationTrackGeometry.getAtVersion(oid, publication1.uuid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0100.000", "0001+0200.000", geometry, 101)
        }
    }

    @Test
    fun `Location track geometry api returns points at addresses divisible by resolution`() {
        // Purposefully chosen to not be exactly divisible by any resolution
        val startM = 0.125
        val endM = 225.780

        val segment =
            segment(
                HelsinkiTestData.HKI_BASE_POINT,
                HelsinkiTestData.HKI_BASE_POINT + Point(0.0, endM - startM),
                startM,
            )
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
                startAddress = TrackMeter(KmNumber(0), startM.toBigDecimal()),
            )

        val track = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, track.id, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(track.id),
        )

        Resolution.entries
            .map { it.meters }
            .forEach { resolution ->
                val response = api.locationTrackGeometry.get(oid, "osoitepistevali" to resolution.toString())
                assertGeometryIntervalAddressResolution(requireNotNull(response.osoitevali), resolution, startM, endM)
            }
    }

    @Test
    fun `Location track geometry api only returns start and end points if track is shorter than resolution`() {
        val startKmNumber = KmNumber("0000")

        val startM = 0.1
        val endM = 0.2
        val intervalStartAddress = TrackMeter(startKmNumber, startM.toBigDecimal().setScale(3))
        val intervalEndAddress = TrackMeter(startKmNumber, endM.toBigDecimal().setScale(3))

        val segment =
            segment(
                HelsinkiTestData.HKI_BASE_POINT,
                HelsinkiTestData.HKI_BASE_POINT + Point(0.0, endM - startM),
                startM,
            )
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
                startAddress = TrackMeter(KmNumber("0000"), startM.toBigDecimal()),
            )

        val track = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment))
        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, track.id, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(track.id),
        )

        Resolution.entries
            .map { it.meters }
            .forEach { resolution ->
                val response = api.locationTrackGeometry.get(oid, "osoitepistevali" to resolution.toString())
                assertNotNull(response.osoitevali)
                assertEquals(intervalStartAddress, response.osoitevali.alkuosoite.let(::TrackMeter))
                assertEquals(intervalEndAddress, response.osoitevali.loppuosoite.let(::TrackMeter))

                listOf(intervalStartAddress, intervalEndAddress)
                    .map { address -> address.toString() }
                    .zip(response.osoitevali.pisteet)
                    .forEach { (expected, response) -> assertEquals(expected, response.rataosoite) }
            }
    }

    @Test
    fun `Location track api should return track information regardless of its state`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

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
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

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
            extTestDataService.publishInMain(locationTracks = tracks.map { (_, track, _) -> track.id as IntId })

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
    fun `Location track modification API should show modifications for calculated change`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val trackGeom = trackGeometryOfSegments(segment(Point(20.0, 0.0), Point(40.0, 0.0)))
        val trackId = mainDraftContext.save(locationTrack(tnId), trackGeom).id
        val trackOid = mainDraftContext.generateOid(trackId)

        val basePublication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                locationTracks = listOf(trackId),
            )
        assertEquals("0000+0020.000", getExtLocationTrack(trackOid).alkusijainti?.rataosoite)
        api.locationTracks.assertNoModificationSince(trackOid, basePublication.uuid)

        initUser()
        mainDraftContext.save(
            mainOfficialContext.fetch(rlId)!!.copy(startAddress = TrackMeter("0001+0010.000")),
            rlGeom,
        )
        val rlPublication = extTestDataService.publishInMain(referenceLines = listOf(rlId))
        assertEquals("0001+0030.000", getExtLocationTrack(trackOid).alkusijainti?.rataosoite)
        api.locationTracks.getModifiedBetween(trackOid, basePublication.uuid, rlPublication.uuid).also { mod ->
            assertEquals("0001+0030.000", mod.sijaintiraide.alkusijainti?.rataosoite)
        }
        api.locationTracks.assertNoModificationSince(trackOid, rlPublication.uuid)

        initUser()
        val kmpId = mainDraftContext.save(kmPost(tnId, KmNumber(4), gkLocation = kmPostGkLocation(10.0, 0.0))).id
        val kmpPublication = extTestDataService.publishInMain(kmPosts = listOf(kmpId))
        assertEquals("0004+0010.000", getExtLocationTrack(trackOid).alkusijainti?.rataosoite)

        api.locationTracks.getModifiedBetween(trackOid, basePublication.uuid, kmpPublication.uuid).also { mod ->
            assertEquals("0004+0010.000", mod.sijaintiraide.alkusijainti?.rataosoite)
        }
        api.locationTracks.assertNoModificationSince(trackOid, kmpPublication.uuid)

        assertEquals("0000+0020.000", getExtLocationTrack(trackOid, basePublication).alkusijainti?.rataosoite)
        assertEquals("0001+0030.000", getExtLocationTrack(trackOid, rlPublication).alkusijainti?.rataosoite)
        assertEquals("0004+0010.000", getExtLocationTrack(trackOid, kmpPublication).alkusijainti?.rataosoite)
    }

    @Test
    fun `Deleted tracks don't have geometry`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
                startAddress = TrackMeter("0001+0100.000"),
            )
        val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id
        val oid = mainDraftContext.generateOid(trackId)

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
                locationTracks = listOf(trackId),
            )

        api.locationTrackGeometry.get(oid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
        }

        initUser()
        mainDraftContext.mutate(trackId) { track -> track.copy(state = LocationTrackState.DELETED) }
        val publication2 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        api.locationTrackGeometry.assertDoesntExist(oid)
        api.locationTrackGeometry.assertDoesntExistAtVersion(oid, publication2.uuid)
        api.locationTrackGeometry.getAtVersion(oid, publication1.uuid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
        }
    }

    @Test
    fun `Deleted tracks have no addresses exposed through the API`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val trackGeom = trackGeometryOfSegments(segment(Point(10.0, 0.0), Point(90.0, 0.0)))
        val trackId = mainDraftContext.save(locationTrack(tnId), trackGeom).id
        val trackOid = mainDraftContext.generateOid(trackId)
        val initPublication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                locationTracks = listOf(trackId),
            )
        val startWithAddress = ExtTestAddressPointV1(10.0, 0.0, "0000+0010.000")
        val startWithoutAddress = ExtTestAddressPointV1(10.0, 0.0, null)
        val endWithAddress = ExtTestAddressPointV1(90.0, 0.0, "0000+0090.000")
        val endWithoutAddress = ExtTestAddressPointV1(90.0, 0.0, null)

        api.locationTracks.get(trackOid).also { track ->
            assertEquals(startWithAddress, track.sijaintiraide.alkusijainti)
            assertEquals(endWithAddress, track.sijaintiraide.loppusijainti)
        }

        initUser()
        val (origTrack, origGeom) = mainDraftContext.fetchWithGeometry(trackId)!!
        mainDraftContext.saveLocationTrack(origTrack.copy(state = LocationTrackState.DELETED) to origGeom)
        val deletePublication = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        api.locationTracks.get(trackOid).also { track ->
            assertEquals(startWithoutAddress, track.sijaintiraide.alkusijainti)
            assertEquals(endWithoutAddress, track.sijaintiraide.loppusijainti)
        }
        api.locationTracks.getAtVersion(trackOid, initPublication.uuid).also { track ->
            assertEquals(startWithAddress, track.sijaintiraide.alkusijainti)
            assertEquals(endWithAddress, track.sijaintiraide.loppusijainti)
        }
        api.locationTracks.getAtVersion(trackOid, deletePublication.uuid).also { track ->
            assertEquals(startWithoutAddress, track.sijaintiraide.alkusijainti)
            assertEquals(endWithoutAddress, track.sijaintiraide.loppusijainti)
        }
    }

    @Test
    fun `Geometry modifications show correct diffs`() {
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
                startAddress = TrackMeter("0001+0100.000"),
            )
        val publication0 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
            )

        // Publication 1 adds a new track
        val geometry1 =
            trackGeometryOfSegments(
                segment(Point(20.0, 0.0), Point(40.0, 0.0)),
                segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                segment(Point(60.0, 0.0), Point(80.0, 0.0)),
            )
        val trackId = mainDraftContext.save(locationTrack(trackNumberId), geometry1).id
        val oid = mainDraftContext.generateOid(trackId)

        val publication1 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        api.locationTrackGeometry.get(oid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0120.000", "0001+0180.000", geometry1, 61)
        }
        api.locationTrackGeometry.assertNoModificationSince(oid, publication1.uuid)
        // Modification since 0 shows the full geometry
        api.locationTrackGeometry.getModifiedSince(oid, publication0.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, publication0, publication1, LAYOUT_SRID, 1)
            assertIntervalMatches(response.osoitevalit[0], "0001+0120.000", "0001+0180.000", geometry1, 61)
        }

        // Publication 2 modifies the geometry
        val geometry2 =
            trackGeometryOfSegments(
                // Shorten the beginning
                segment(Point(30.0, 0.0), Point(40.0, 0.0)),
                // Bend in the middle
                segment(Point(40.0, 0.0), Point(42.0, 2.0)),
                segment(Point(42.0, 2.0), Point(58.0, 2.0)),
                segment(Point(58.0, 2.0), Point(60.0, 0.0)),
                // Extend the end
                segment(Point(60.0, 0.0), Point(90.0, 0.0)),
            )
        initUser()
        mainDraftContext.save(mainDraftContext.fetch(trackId)!!, geometry2)
        val publication2 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        api.locationTrackGeometry.get(oid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, oid, "0001+0130.000", "0001+0190.000", geometry2, 61)
        }
        api.locationTrackGeometry.assertNoModificationSince(oid, publication2.uuid)
        // Modification since 1 show the edits
        api.locationTrackGeometry.getModifiedSince(oid, publication1.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, publication1, publication2, LAYOUT_SRID, 3)
            assertEmptyInterval(response.osoitevalit[0], "0001+0120.000", "0001+0129.000")
            assertIntervalMatches(
                response.osoitevalit[1],
                "0001+0141.000",
                "0001+0159.000",
                geometry2,
                19,
                Point(41.0, 1.0),
                Point(59.0, 1.0),
            )
            assertIntervalMatches(
                response.osoitevalit[2],
                "0001+0181.000",
                "0001+0190.000",
                geometry2,
                10,
                Point(81.0, 0.0),
                Point(90.0, 0.0),
            )
        }
        // Modification since 0 shows the full geometry at latest version
        api.locationTrackGeometry.getModifiedSince(oid, publication0.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, publication0, publication2, LAYOUT_SRID, 1)
            assertIntervalMatches(response.osoitevalit[0], "0001+0130.000", "0001+0190.000", geometry2, 61)
        }

        // Publication 3 removes the geometry
        initUser()
        mainDraftContext.save(mainDraftContext.fetch(trackId)!!.copy(state = LocationTrackState.DELETED), geometry2)
        val publication3 = extTestDataService.publishInMain(locationTracks = listOf(trackId))

        api.locationTrackGeometry.assertNoModificationSince(oid, publication3.uuid)

        // Modifications since 2 show the state-2 address range emptied
        api.locationTrackGeometry.getModifiedSince(oid, publication2.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, publication2, publication3, LAYOUT_SRID, 1)
            assertEmptyInterval(response.osoitevalit[0], "0001+0130.000", "0001+0190.000")
        }

        // Modifications since 1 show the state-1 address range emptied
        api.locationTrackGeometry.getModifiedSince(oid, publication1.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, publication1, publication3, LAYOUT_SRID, 1)
            assertEmptyInterval(response.osoitevalit[0], "0001+0120.000", "0001+0180.000")
        }

        // Modifications since 0 show nothing as there was no geometry at either state
        api.locationTrackGeometry.assertNoModificationSince(oid, publication0.uuid)
    }

    @Test
    fun `Geometry modifications API shows calculated changes correctly`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val trackGeom = trackGeometryOfSegments(segment(Point(20.0, 0.0), Point(40.0, 0.0)))
        val trackId = mainDraftContext.save(locationTrack(tnId), trackGeom).id
        val oid = mainDraftContext.generateOid(trackId)

        val basePub =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                locationTracks = listOf(trackId),
            )
        api.locationTrackGeometry.get(oid).osoitevali!!.also { interval ->
            assertEquals("0000+0020.000", interval.alkuosoite)
            assertEquals("0000+0040.000", interval.loppuosoite)
        }
        api.locationTrackGeometry.assertNoModificationSince(oid, basePub.uuid)

        initUser()
        mainDraftContext.save(
            mainOfficialContext.fetch(rlId)!!.copy(startAddress = TrackMeter("0001+0010.000")),
            rlGeom,
        )
        val rlPub = extTestDataService.publishInMain(referenceLines = listOf(rlId))
        api.locationTrackGeometry.get(oid).osoitevali!!.also { interval ->
            assertEquals("0001+0030.000", interval.alkuosoite)
            assertEquals("0001+0050.000", interval.loppuosoite)
        }
        api.locationTrackGeometry.getModifiedBetween(oid, basePub.uuid, rlPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, basePub, rlPub, LAYOUT_SRID, 1)
            // Address range is [min(old,new), max(old,new)]
            assertIntervalMatches(
                response.osoitevalit[0],
                "0000+0020.000",
                "0001+0050.000",
                trackGeom,
                21,
                Point(20.0, 0.0),
                Point(40.0, 0.0),
            )
        }
        api.locationTrackGeometry.assertNoModificationSince(oid, rlPub.uuid)

        initUser()
        val kmpId = mainDraftContext.save(kmPost(tnId, KmNumber(4), gkLocation = kmPostGkLocation(30.0, 0.0))).id
        val kmpPub = extTestDataService.publishInMain(kmPosts = listOf(kmpId))

        api.locationTrackGeometry.get(oid).osoitevali!!.also { interval ->
            assertEquals("0001+0030.000", interval.alkuosoite)
            assertEquals("0004+0010.000", interval.loppuosoite)
        }
        // Mods since rl publication
        api.locationTrackGeometry.getModifiedBetween(oid, rlPub.uuid, kmpPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, rlPub, kmpPub, LAYOUT_SRID, 1)
            // Address range is [added-km-post, end] -> mod-range start is min(old,new) of the change point
            assertIntervalMatches(
                response.osoitevalit[0],
                "0001+0040.000",
                "0004+0010.000",
                trackGeom,
                11,
                Point(30.0, 0.0),
                Point(40.0, 0.0),
            )
        }
        api.locationTrackGeometry.assertNoModificationSince(oid, kmpPub.uuid)

        // Mods since base publication
        api.locationTrackGeometry.getModifiedBetween(oid, basePub.uuid, kmpPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, oid, basePub, kmpPub, LAYOUT_SRID, 1)
            // Address range is [min(old,new), max(old,new)]
            assertIntervalMatches(
                response.osoitevalit[0],
                "0000+0020.000",
                "0004+0010.000",
                trackGeom,
                21,
                Point(20.0, 0.0),
                Point(40.0, 0.0),
            )
        }
    }

    private fun getExtLocationTrack(oid: Oid<LocationTrack>, publication: Publication? = null): ExtTestLocationTrackV1 =
        (publication?.uuid?.let { uuid -> api.locationTracks.getAtVersion(oid, uuid) } ?: api.locationTracks.get(oid))
            .sijaintiraide
}

private fun assertGeometryModificationMetadata(
    response: ExtTestModifiedLocationTrackGeometryResponseV1,
    oid: Oid<LocationTrack>,
    fromVersion: Publication,
    toVersion: Publication,
    coordinateSystem: Srid,
    intervals: Int,
) {
    assertEquals(oid.toString(), response.sijaintiraide_oid)
    assertEquals(fromVersion.uuid.toString(), response.alkuversio)
    assertEquals(toVersion.uuid.toString(), response.loppuversio)
    assertEquals(coordinateSystem.toString(), response.koordinaatisto)
    assertEquals(intervals, response.osoitevalit.size)
}

private fun assertGeometryMatches(
    response: ExtTestLocationTrackGeometryResponseV1,
    oid: Oid<LocationTrack>,
    startAddress: String,
    endAddress: String,
    geometry: LocationTrackGeometry,
    pointCount: Int,
) {
    assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
    assertEquals(oid.toString(), response.sijaintiraide_oid)
    assertIntervalMatches(response.osoitevali, startAddress, endAddress, geometry, pointCount)
}
