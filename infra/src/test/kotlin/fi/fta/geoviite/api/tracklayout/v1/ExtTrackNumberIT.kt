package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
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
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

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
                val response = api.trackNumbers.getGeometry(oid, "osoitepistevali" to resolution.toString())
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
                val response = api.trackNumbers.getGeometry(oid, "osoitepistevali" to resolution.toString())
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
        val tnOid = testDBService.generateTrackNumberOid(tnId, LayoutBranch.main)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val basePublication =
            extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))
        getExtTrackNumber(tnOid).also { assertAddressRange(it, "0000+0000.000", "0000+0010.000") }
        api.trackNumbers.assertNoModificationSince(tnOid, basePublication.uuid)

        initUser()
        val newStart = TrackMeter(KmNumber("0001"), BigDecimal.TEN)
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
    fun `Deleted track numbers have no geometries exposed through the API`() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val tnOid = testDBService.generateTrackNumberOid(tnId, LayoutBranch.main)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(100.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        val initPublication =
            extTestDataService.publishInMain(trackNumbers = listOf(tnId), referenceLines = listOf(rlId))

        assertEquals(101, api.trackNumbers.getGeometry(tnOid).osoitevali?.pisteet?.size)

        initUser()
        mainDraftContext.save(mainDraftContext.fetch(tnId)!!.copy(state = LayoutState.DELETED))
        val deletePublication = extTestDataService.publishInMain(trackNumbers = listOf(tnId))

        api.trackNumbers.getGeometryWithEmptyBody(tnOid, httpStatus = HttpStatus.NO_CONTENT)
        assertEquals(101, api.trackNumbers.getGeometryAt(tnOid, initPublication.uuid).osoitevali?.pisteet?.size)
        api.trackNumbers.getGeometryWithEmptyBodyAt(tnOid, deletePublication.uuid, httpStatus = HttpStatus.NO_CONTENT)
    }

    private fun getExtTrackNumber(oid: Oid<LayoutTrackNumber>, publication: Publication? = null): ExtTestTrackNumberV1 =
        (publication?.uuid?.let { uuid -> api.trackNumbers.getAtVersion(oid, uuid) } ?: api.trackNumbers.get(oid))
            .ratanumero
}
