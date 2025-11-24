package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTrackNumberIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val layoutTrackNumberService: LayoutTrackNumberService,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Newest official track number is returned by default`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
            )

        val modifiedDescription = "modified description after publication ${publication1.uuid}"
        val trackNumber = layoutTrackNumberDao.getOrThrow(MainLayoutContext.official, trackNumberId)
        mainDraftContext.saveTrackNumber(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))

        val responseAfterCreatingDraft = api.trackNumbers.get(oid)
        assertEquals(publication1.uuid.toString(), responseAfterCreatingDraft.rataverkon_versio)
        assertNotEquals(modifiedDescription, responseAfterCreatingDraft.ratanumero.kuvaus)
    }

    @Test
    fun `Track number api respects the track layout version argument`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
            )

        val publication2 = extTestDataService.publishInMain()

        val modifiedDescription = "modified description after publication ${publication1.uuid}"
        val trackNumber = layoutTrackNumberDao.getOrThrow(MainLayoutContext.official, trackNumberId)
        mainDraftContext.saveTrackNumber(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))

        val publication3 = extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId))

        val responses =
            listOf(publication1, publication2, publication3).map { publication ->
                val response = api.trackNumbers.get(oid, "rataverkon_versio" to publication.uuid.toString())
                assertEquals(publication.uuid.toString(), response.rataverkon_versio)

                response
            }

        assertNotEquals(modifiedDescription, responses[0].ratanumero.kuvaus)
        assertNotEquals(modifiedDescription, responses[1].ratanumero.kuvaus)
        assertEquals(modifiedDescription, responses[2].ratanumero.kuvaus)
    }

    @Test
    fun `Track number api respects the coordinate system argument`() {
        val helsinkiRailwayStationTm35Fin = Point(385782.89, 6672277.83)
        val helsinkiRailwayStationTm35FinPlus10000 = Point(395782.89, 6682277.83)

        val tests =
            listOf(
                Triple("EPSG:3067", helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000),

                // EPSG:4326 == WGS84, converted using https://epsg.io/transform
                Triple("EPSG:4326", Point(24.9414003, 60.1713788), Point(25.1163757, 60.2637958)),
            )

        val segment = segment(helsinkiRailwayStationTm35Fin, helsinkiRailwayStationTm35FinPlus10000)

        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        tests.forEach { (epsgCode, expectedStart, expectedEnd) ->
            val response = api.trackNumbers.get(oid, "koordinaatisto" to epsgCode)

            assertEquals(epsgCode, response.koordinaatisto)
            assertExtStartAndEnd(
                expectedStart,
                expectedEnd,
                requireNotNull(response.ratanumero.alkusijainti),
                requireNotNull(response.ratanumero.loppusijainti),
            )
        }
    }

    @Test
    fun `Official geometry is returned at correct track layout version state`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0100.000")), alignment).id
        val tnOid = mainDraftContext.generateOid(tnId)

        val publication1 = extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        api.trackNumberGeometry.get(tnOid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0100.000", "0001+0200.000", alignment, 101)
        }

        val newAlignment = alignment(segment(Point(10.0, 10.0), Point(90.0, 10.0)))
        initUser()
        mainDraftContext.fetch(rlId).also { rl ->
            mainDraftContext.save(rl!!.copy(startAddress = TrackMeter("0001+0200.000")), newAlignment)
        }
        val publication2 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

        api.trackNumberGeometry.get(tnOid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0200.000", "0001+0280.000", newAlignment, 81)
        }

        api.trackNumberGeometry.getAtVersion(tnOid, publication2.uuid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0200.000", "0001+0280.000", newAlignment, 81)
        }

        api.trackNumberGeometry.getAtVersion(tnOid, publication1.uuid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0100.000", "0001+0200.000", alignment, 101)
        }
    }

    @Test
    fun `Track number api should return track number information regardless of its state`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val trackNumbers =
            LayoutState.entries.mapIndexed { index, state ->
                val trackNumber =
                    mainDraftContext
                        .saveTrackNumber(trackNumber(TrackNumber("30$index"), state = state))
                        .let(layoutTrackNumberDao::fetch)

                val referenceLineId =
                    mainDraftContext.saveReferenceLine(referenceLineAndAlignment(trackNumber.id as IntId, segment)).id

                extTestDataService.publishInMain(
                    trackNumbers = listOf(trackNumber.id as IntId),
                    referenceLines = listOf(referenceLineId),
                )

                val oid =
                    someOid<LayoutTrackNumber>().also { oid ->
                        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumber.id, oid)
                    }

                oid to state
            }

        trackNumbers.forEach { (oid, state) ->
            val response = api.trackNumbers.get(oid)

            assertEquals(oid.toString(), response.ratanumero.ratanumero_oid)
            assertExtLayoutState(state, response.ratanumero.tila)
        }
    }

    @Test
    fun `Track number modifications api should return track number regardless of its state`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val trackNumbers =
            LayoutState.entries.mapIndexed { index, state ->
                val trackNumber =
                    mainDraftContext
                        .saveTrackNumber(trackNumber(TrackNumber("30$index"), state = state))
                        .let(layoutTrackNumberDao::fetch)

                val referenceLineId =
                    mainDraftContext.saveReferenceLine(referenceLineAndAlignment(trackNumber.id as IntId, segment)).id

                extTestDataService.publishInMain(referenceLines = listOf(referenceLineId))

                val oid =
                    someOid<LayoutTrackNumber>().also { oid ->
                        layoutTrackNumberService.insertExternalId(LayoutBranch.main, trackNumber.id, oid)
                    }

                Triple(oid, trackNumber, state)
            }

        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = trackNumbers.map { (_, trackNumber, _) -> trackNumber.id as IntId }
            )

        val modifiedDescription = "modified description after publication ${publication1.uuid}"
        trackNumbers.forEach { (_, trackNumber, _) ->
            mainDraftContext.saveTrackNumber(
                trackNumber.copy(description = TrackNumberDescription(modifiedDescription))
            )
        }

        val publication2 =
            extTestDataService.publishInMain(
                trackNumbers = trackNumbers.map { (_, trackNumber, _) -> trackNumber.id as IntId }
            )

        trackNumbers.forEach { (oid, trackNumber, _) ->
            val response =
                api.trackNumbers.getModified(
                    oid,
                    "alkuversio" to publication1.uuid.toString(),
                    "loppuversio" to publication2.uuid.toString(),
                )

            assertEquals(oid.toString(), response.ratanumero.ratanumero_oid)
            assertEquals(modifiedDescription, response.ratanumero.kuvaus)
            assertExtLayoutState(trackNumber.state, response.ratanumero.tila)
        }
    }

    @Test
    fun `Track number geometry api returns points at addresses divisible by resolution`() {
        // Purposefully chosen to not be exactly divisible by any resolution
        val startM = 0.125
        val endM = 225.780

        val segment =
            segment(
                HelsinkiTestData.HKI_BASE_POINT,
                HelsinkiTestData.HKI_BASE_POINT + Point(endM - startM, 0.0),
                startM,
            )
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
                startAddress = TrackMeter(KmNumber("0000"), startM.toBigDecimal()),
            )

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        Resolution.entries
            .map { it.meters }
            .forEach { resolution ->
                val response = api.trackNumberGeometry.get(oid, "osoitepistevali" to resolution.toString())
                assertGeometryIntervalAddressResolution(requireNotNull(response.osoitevali), resolution, startM, endM)
            }
    }

    @Test
    fun `Track number geometry api only returns start and end points if track number is shorter than resolution`() {
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
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                mainDraftContext,
                segments = listOf(segment),
                startAddress = intervalStartAddress,
            )

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        Resolution.entries
            .map { it.meters }
            .forEach { resolution ->
                val response = api.trackNumberGeometry.get(oid, "osoitepistevali" to resolution.toString())
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
    fun `Track number modification API should show modifications for calculated change`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val basePublication =
            extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))
        getExtTrackNumber(tnOid).also { assertAddressRange(it, "0000+0000.000", "0000+0010.000") }
        api.trackNumbers.assertNoModificationSince(tnOid, basePublication.uuid)

        initUser()
        val newStart = TrackMeter("0001+0010.000")
        mainDraftContext.save(mainOfficialContext.fetch(rlId)!!.copy(startAddress = newStart), rlGeom)
        val rlPublication = extTestDataService.publishInMain(referenceLines = listOf(rlId))
        assertAddressRange(getExtTrackNumber(tnOid), "0001+0010.000", "0001+0020.000")
        api.trackNumbers.getModifiedBetween(tnOid, basePublication.uuid, rlPublication.uuid).also { mod ->
            assertAddressRange(mod.ratanumero, "0001+0010.000", "0001+0020.000")
        }
        api.trackNumbers.assertNoModificationSince(tnOid, rlPublication.uuid)

        initUser()
        val kmpId = mainDraftContext.save(kmPost(tnId, KmNumber(4), gkLocation = kmPostGkLocation(5.0, 0.0))).id
        val kmpPublication = extTestDataService.publishInMain(kmPosts = listOf(kmpId))
        assertAddressRange(getExtTrackNumber(tnOid), "0001+0010.000", "0004+0005.000")
        api.trackNumbers.getModifiedBetween(tnOid, rlPublication.uuid, kmpPublication.uuid).also { mod ->
            assertAddressRange(mod.ratanumero, "0001+0010.000", "0004+0005.000")
        }
        api.trackNumbers.getModifiedBetween(tnOid, basePublication.uuid, kmpPublication.uuid).also { mod ->
            assertAddressRange(mod.ratanumero, "0001+0010.000", "0004+0005.000")
        }
        api.trackNumbers.getModifiedBetween(tnOid, basePublication.uuid, rlPublication.uuid).also { mod ->
            assertAddressRange(mod.ratanumero, "0001+0010.000", "0001+0020.000")
        }
        api.trackNumbers.assertNoModificationSince(tnOid, kmpPublication.uuid)

        assertAddressRange(getExtTrackNumber(tnOid, basePublication), "0000+0000.000", "0000+0010.000")
        assertAddressRange(getExtTrackNumber(tnOid, rlPublication), "0001+0010.000", "0001+0020.000")
        assertAddressRange(getExtTrackNumber(tnOid, kmpPublication), "0001+0010.000", "0004+0005.000")
    }

    @Test
    fun `Deleted track numbers don't have geometry`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0100.000")), alignment).id
        val tnOid = mainDraftContext.generateOid(tnId)

        val publication1 = extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        api.trackNumberGeometry.get(tnOid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
        }

        initUser()
        mainDraftContext.mutate(tnId) { tn -> tn.copy(state = LayoutState.DELETED) }
        val publication2 = extTestDataService.publishInMain(trackNumbers = listOf(tnId))

        api.trackNumberGeometry.assertDoesntExist(tnOid)
        api.trackNumberGeometry.assertDoesntExistAtVersion(tnOid, publication2.uuid)
        api.trackNumberGeometry.getAtVersion(tnOid, publication1.uuid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
        }
    }

    @Test
    fun `Deleted track numbers have no addresses exposed through the API`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id
        val startWithAddress = ExtTestAddressPointV1(0.0, 0.0, "0000+0000.000")
        val startWithoutAddress = ExtTestAddressPointV1(0.0, 0.0, null)
        val endWithAddress = ExtTestAddressPointV1(100.0, 0.0, "0000+0100.000")
        val endWithoutAddress = ExtTestAddressPointV1(100.0, 0.0, null)

        val initPublication =
            extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        api.trackNumbers.get(tnOid).also { tn ->
            assertEquals(startWithAddress, tn.ratanumero.alkusijainti)
            assertEquals(endWithAddress, tn.ratanumero.loppusijainti)
        }

        initUser()
        mainDraftContext.save(mainDraftContext.fetch(tnId)!!.copy(state = LayoutState.DELETED))
        val deletePublication = extTestDataService.publishInMain(trackNumbers = listOf(tnId))

        api.trackNumbers.get(tnOid).also { tn ->
            assertEquals(startWithoutAddress, tn.ratanumero.alkusijainti)
            assertEquals(endWithoutAddress, tn.ratanumero.loppusijainti)
        }

        api.trackNumbers.getAtVersion(tnOid, initPublication.uuid).also { tn ->
            assertEquals(startWithAddress, tn.ratanumero.alkusijainti)
            assertEquals(endWithAddress, tn.ratanumero.loppusijainti)
        }
        api.trackNumbers.getAtVersion(tnOid, deletePublication.uuid).also { tn ->
            assertEquals(startWithoutAddress, tn.ratanumero.alkusijainti)
            assertEquals(endWithoutAddress, tn.ratanumero.loppusijainti)
        }
    }

    @Test
    fun `Geometry modifications show correct diffs`() {
        // Add "some" publication to have a baseline version for queries prior to the track number creation
        val publication0 =
            mainDraftContext
                .createLayoutTrackNumber()
                .id
                .let { tnId -> tnId to mainDraftContext.save(referenceLine(tnId)).id }
                .let { (tn, rl) ->
                    extTestDataService.publishInMain(trackNumbers = listOf(tn), referenceLines = listOf(rl))
                }

        // Publication 1 adds a new track number
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val alignment1 = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        // Straight reference line initially
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0100.000")), alignment1).id
        // Use km-posts to reset address calculation, as otherwise any change would change the geometry until the end
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(2), gkLocation = kmPostGkLocation(15.0, 0.0))).id
        val kmp2Id = mainDraftContext.save(kmPost(tnId, KmNumber(3), gkLocation = kmPostGkLocation(65.0, 0.0))).id
        val tnOid = mainDraftContext.generateOid(tnId)
        val publication1 =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id, kmp2Id),
            )

        api.trackNumberGeometry.get(tnOid).also { response ->
            assertEquals(publication1.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0100.000", "0003+0035.000", alignment1, 101)
        }
        api.trackNumberGeometry.assertNoModificationSince(tnOid, publication1.uuid)
        // Modification since 0 shows the full geometry
        api.trackNumberGeometry.getModifiedSince(tnOid, publication0.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, publication0, publication1, LAYOUT_SRID, 1)
            assertIntervalMatches(response.osoitevalit[0], "0001+0100.000", "0003+0035.000", alignment1, 101)
        }

        // Publication 2 modifies the geometry
        val alignment2 =
            alignment(
                // Shorten the beginning
                segment(Point(10.0, 0.0), Point(40.0, 0.0)),
                // Bend in the middle
                segment(Point(40.0, 0.0), Point(42.0, 2.0)),
                segment(Point(42.0, 2.0), Point(58.0, 2.0)),
                segment(Point(58.0, 2.0), Point(60.0, 0.0)),
                // Extend the end
                segment(Point(60.0, 0.0), Point(120.0, 0.0)),
            )
        initUser()
        mainDraftContext.save(mainDraftContext.fetch(rlId)!!, alignment2)
        val publication2 = extTestDataService.publishInMain(referenceLines = listOf(rlId))

        api.trackNumberGeometry.get(tnOid).also { response ->
            assertEquals(publication2.uuid.toString(), response.rataverkon_versio)
            assertGeometryMatches(response, tnOid, "0001+0100.000", "0003+0055.000", alignment2, 113)
        }
        api.trackNumberGeometry.assertNoModificationSince(tnOid, publication2.uuid)
        // Modification since 1 show the edits
        api.trackNumberGeometry.getModifiedSince(tnOid, publication1.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, publication1, publication2, LAYOUT_SRID, 3)
            // Start address moves with the line beginning
            // Addressing change affects all points until next km-post
            assertIntervalMatches(
                response.osoitevalit[0],
                "0001+0100.000",
                "0001+0114.000",
                alignment2,
                5,
                Point(10.0, 0.0),
                Point(14.0, 0.0),
            )
            // The bend adds to the distance and moves the points
            // Also affects addressing until the next km-post
            // The change interval is from x=41 (first changed point) to x=65 (next km-post) -> 24m -> 25 points
            // The bend adds just shy of 2 meters in length -> rounds down to +1 point -> 26 points
            assertIntervalMatches(
                response.osoitevalit[1],
                "0002+0026.000",
                "0002+0051.000",
                alignment2,
                26,
                Point(40.7072, 0.7072), // Where 1m points land by pythagorean distance
                Point(64.3433, 0.0),
            )
            // The final changed segment is the extension: simply added points after the end of the original geom
            // The previous km-post is at x=65, with the geom extending to x=100 -> 35 points (and m) originally
            // The added section is then from point x=100 (0036) to x=120 (0055) -> 19 meters (20 points)
            assertIntervalMatches(
                response.osoitevalit[2],
                "0003+0036.000",
                "0003+0055.000",
                alignment2,
                20,
                Point(101.0, 0.0),
                Point(120.0, 0.0),
            )
        }
        // Modification since 0 shows the full geometry at latest version
        api.trackNumberGeometry.getModifiedSince(tnOid, publication0.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, publication0, publication2, LAYOUT_SRID, 1)
            assertIntervalMatches(response.osoitevalit[0], "0001+0100.000", "0003+0055.000", alignment2, 113)
        }

        // Publication 3 removes the geometry
        initUser()
        mainDraftContext.save(mainDraftContext.fetch(tnId)!!.copy(state = LayoutState.DELETED))
        val publication3 = extTestDataService.publishInMain(trackNumbers = listOf(tnId))

        api.trackNumberGeometry.assertNoModificationSince(tnOid, publication3.uuid)
        // Modifications since 2 show the state-2 address range emptied
        api.trackNumberGeometry.getModifiedSince(tnOid, publication2.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, publication2, publication3, LAYOUT_SRID, 1)
            assertEmptyInterval(response.osoitevalit[0], "0001+0100.000", "0003+0055.000")
        }
        // Modifications since 1 show the state-1 address range emptied
        api.trackNumberGeometry.getModifiedSince(tnOid, publication1.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, publication1, publication3, LAYOUT_SRID, 1)
            assertEmptyInterval(response.osoitevalit[0], "0001+0100.000", "0003+0035.000")
        }
        // Modifications since 0 show nothing as there was no geometry at either state
        api.trackNumberGeometry.assertNoModificationSince(tnOid, publication0.uuid)
    }

    @Test
    fun `Geometry modifications API shows calculated changes correctly`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val alignment = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId, startAddress = TrackMeter("0001+0100.000")), alignment).id
        val kmp1Id = mainDraftContext.save(kmPost(tnId, KmNumber(2), gkLocation = kmPostGkLocation(15.0, 0.0))).id
        val kmp2Id = mainDraftContext.save(kmPost(tnId, KmNumber(4), gkLocation = kmPostGkLocation(65.0, 0.0))).id
        val tnOid = mainDraftContext.generateOid(tnId)

        val basePub =
            extTestDataService.publishInMain(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                kmPosts = listOf(kmp1Id, kmp2Id),
            )
        api.trackNumberGeometry.get(tnOid).osoitevali!!.also { interval ->
            assertEquals("0001+0100.000", interval.alkuosoite)
            assertEquals("0004+0035.000", interval.loppuosoite)
        }
        api.trackNumberGeometry.assertNoModificationSince(tnOid, basePub.uuid)

        initUser()
        mainDraftContext.save(
            mainOfficialContext.fetch(rlId)!!.copy(startAddress = TrackMeter("0001+0010.000")),
            alignment,
        )
        val rlPub = extTestDataService.publishInMain(referenceLines = listOf(rlId))
        api.trackNumberGeometry.get(tnOid).osoitevali!!.also { interval ->
            assertEquals("0001+0010.000", interval.alkuosoite)
            assertEquals("0004+0035.000", interval.loppuosoite)
        }
        api.trackNumberGeometry.getModifiedBetween(tnOid, basePub.uuid, rlPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, basePub, rlPub, LAYOUT_SRID, 1)
            // Address range is [min(old,new), max(old,new)]
            assertIntervalMatches(
                response.osoitevalit[0],
                "0001+0010.000",
                "0001+0114.000",
                alignment,
                15,
                Point(0.0, 0.0),
                Point(14.0, 0.0),
            )
        }
        api.trackNumberGeometry.assertNoModificationSince(tnOid, rlPub.uuid)

        initUser()
        val kmpId = mainDraftContext.save(kmPost(tnId, KmNumber(3), gkLocation = kmPostGkLocation(30.0, 0.0))).id
        val kmpPub = extTestDataService.publishInMain(kmPosts = listOf(kmpId))

        api.trackNumberGeometry.get(tnOid).osoitevali!!.also { interval ->
            assertEquals("0001+0010.000", interval.alkuosoite)
            assertEquals("0004+0035.000", interval.loppuosoite)
            assertNotNull(interval.pisteet.find { it.rataosoite == "0003+0000.000" })
            assertNotNull(interval.pisteet.find { it.rataosoite == "0003+0034.000" })
        }
        api.trackNumberGeometry.assertNoModificationSince(tnOid, kmpPub.uuid)
        // Mods since rl publication
        api.trackNumberGeometry.getModifiedBetween(tnOid, rlPub.uuid, kmpPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, rlPub, kmpPub, LAYOUT_SRID, 1)
            // Address range is [added-km-post, end] -> mod-range start is min(old,new) of the change point
            assertIntervalMatches(
                response.osoitevalit[0],
                "0002+0015.000",
                "0003+0034.000",
                alignment,
                35,
                Point(30.0, 0.0),
                Point(64.0, 0.0),
            )
        }
        // Mods since base publication
        api.trackNumberGeometry.getModifiedBetween(tnOid, basePub.uuid, kmpPub.uuid).also { response ->
            assertGeometryModificationMetadata(response, tnOid, basePub, kmpPub, LAYOUT_SRID, 2)
            // Address range is [min(old,new), max(old,new)]
            assertIntervalMatches(
                response.osoitevalit[0],
                "0001+0010.000",
                "0001+0114.000",
                alignment,
                15,
                Point(0.0, 0.0),
                Point(14.0, 0.0),
            )
            assertIntervalMatches(
                response.osoitevalit[1],
                "0002+0015.000",
                "0003+0034.000",
                alignment,
                35,
                Point(30.0, 0.0),
                Point(64.0, 0.0),
            )
        }
    }

    private fun getExtTrackNumber(oid: Oid<LayoutTrackNumber>, publication: Publication? = null): ExtTestTrackNumberV1 =
        (publication?.uuid?.let { uuid -> api.trackNumbers.getAtVersion(oid, uuid) } ?: api.trackNumbers.get(oid))
            .ratanumero
}

private fun assertGeometryModificationMetadata(
    response: ExtTestModifiedTrackNumberGeometryResponseV1,
    oid: Oid<LayoutTrackNumber>,
    fromVersion: Publication,
    toVersion: Publication,
    coordinateSystem: Srid,
    intervals: Int,
) {
    assertEquals(oid.toString(), response.ratanumero_oid)
    assertEquals(fromVersion.uuid.toString(), response.alkuversio)
    assertEquals(toVersion.uuid.toString(), response.loppuversio)
    assertEquals(coordinateSystem.toString(), response.koordinaatisto)
    assertEquals(intervals, response.osoitevalit.size)
}

private fun assertGeometryMatches(
    response: ExtTestTrackNumberGeometryResponseV1,
    oid: Oid<LayoutTrackNumber>,
    startAddress: String,
    endAddress: String,
    geometry: LayoutAlignment,
    pointCount: Int,
) {
    assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
    assertEquals(oid.toString(), response.ratanumero_oid)
    assertIntervalMatches(response.osoitevali, startAddress, endAddress, geometry, pointCount)
}
