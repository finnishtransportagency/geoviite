package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `Track number geometry api respects the resolution argument`() {
        val segment = segment(HelsinkiTestData.HKI_BASE_POINT, HelsinkiTestData.HKI_BASE_POINT + Point(1500.0, 0.0))
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        Resolution.entries
            .map { it.meters }
            .forEach { resolution ->
                val response = api.trackNumbers.getGeometry(oid, "osoitepistevali" to resolution.toString())

                val points = response.osoitevalit.flatMap { it.pisteet.mapNotNull { it.rataosoite?.let(::TrackMeter) } }
                points.forEachIndexed { i, address ->
                    if (i > 1) {
                        assertEquals((address.meters - points[i - 1].meters).toDouble(), resolution.toDouble(), 0.001)
                    }
                }
            }
    }
}
