package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.segment
import java.math.BigDecimal
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
class ExtTrackNumberCollectionIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val layoutTrackNumberService: LayoutTrackNumberService,
    private val extTestDataService: ExtApiTestDataServiceV1,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Newest track number listing is returned by default`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val trackNumbers =
            listOf(1, 2, 3).map { _ ->
                extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                    mainDraftContext,
                    segments = listOf(segment),
                )
            }

        extTestDataService.publishInMain(
            trackNumbers = trackNumbers.map { (trackNumberId, _, _) -> trackNumberId },
            referenceLines = trackNumbers.map { (_, referenceLineId, _) -> referenceLineId },
        )

        val newestButEmptyPublication = extTestDataService.publishInMain()
        val response = api.trackNumberCollection.get()

        assertEquals(newestButEmptyPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(trackNumbers.size, response.ratanumerot.size)

        val responseOids = response.ratanumerot.map { trackNumber -> trackNumber.ratanumero_oid }
        trackNumbers.forEach { (_, _, oid) -> assertTrue(oid.toString() in responseOids) }
    }

    @Test
    fun `Newest track number listing does not contain draft tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val officialTrackNumbers =
            listOf(1, 2, 3).map { _ ->
                extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                    mainDraftContext,
                    segments = listOf(segment),
                )
            }

        val newestPublication =
            extTestDataService.publishInMain(
                trackNumbers = officialTrackNumbers.map { it.first },
                referenceLines = officialTrackNumbers.map { it.second },
            )

        val modifiedDescription = "this is a draft track number after publication ${newestPublication.uuid}"
        officialTrackNumbers.forEach { (trackNumberId, _, _) ->
            val trackNumber =
                layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull)

            mainDraftContext.saveTrackNumber(
                trackNumber.copy(description = TrackNumberDescription(modifiedDescription))
            )
        }

        // Also save an additional draft track
        extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val response = api.trackNumberCollection.get()
        assertEquals(newestPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(officialTrackNumbers.size, response.ratanumerot.size)

        officialTrackNumbers.forEach { (trackNumberId, _, oid) ->
            val officialTrack =
                layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull)

            val responseTrackNumber =
                response.ratanumerot
                    .find { responseTrackNumber -> responseTrackNumber.ratanumero_oid == oid.toString() }
                    .let(::requireNotNull)

            assertEquals(officialTrack.description.toString(), responseTrackNumber.kuvaus)
        }
    }

    @Test
    fun `Track number listing respects the track layout version argument`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val expectedAmountToPublications =
            listOf(1, 2, 3).map { totalAmountOfTrackNumbers ->
                val (trackNumberId, referenceLineId, _) =
                    extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                        mainDraftContext,
                        segments = listOf(segment),
                    )

                val publication =
                    extTestDataService.publishInMain(
                        trackNumbers = listOf(trackNumberId),
                        referenceLines = listOf(referenceLineId),
                    )

                totalAmountOfTrackNumbers to publication
            }

        expectedAmountToPublications.forEach { (expectedAmountOfTrackNumbers, publication) ->
            val response = api.trackNumberCollection.get("rataverkon_versio" to publication.uuid.toString())
            assertEquals(expectedAmountOfTrackNumbers, response.ratanumerot.size)
        }
    }

    @Test
    fun `Track number listing respects the coordinate system argument`() {
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

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        tests.forEach { (epsgCode, expectedStart, expectedEnd) ->
            val response = api.trackNumberCollection.get("koordinaatisto" to epsgCode)

            val responseTrackNumber = response.ratanumerot.first().let(::requireNotNull)

            assertEquals(epsgCode, response.koordinaatisto)
            assertExtStartAndEnd(
                expectedStart,
                expectedEnd,
                requireNotNull(responseTrackNumber.alkusijainti),
                requireNotNull(responseTrackNumber.loppusijainti),
            )
        }
    }

    @Test
    fun `Track number modifications listing only lists modified track numbers`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val allTrackNumbers =
            listOf(1, 2, 3, 4).map { _ ->
                val (trackNumberId, referenceLineId, oid) =
                    extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                        mainDraftContext,
                        segments = listOf(segment),
                    )

                val trackNumber =
                    layoutTrackNumberService.get(MainLayoutContext.draft, trackNumberId).let(::requireNotNull)

                Triple(oid, trackNumber, referenceLineId)
            }

        val initialPublication =
            extTestDataService.publishInMain(
                trackNumbers = allTrackNumbers.map { (_, trackNumber, _) -> trackNumber.id as IntId },
                referenceLines = allTrackNumbers.map { (_, _, referenceLineId) -> referenceLineId },
            )

        val modifiedDescription = "modified description after publication ${initialPublication.uuid}"
        val modifiedTrackNumbers =
            listOf(allTrackNumbers[1], allTrackNumbers[2]).map { (oid, trackNumber, _) ->
                mainDraftContext.saveTrackNumber(
                    trackNumber.copy(description = TrackNumberDescription(modifiedDescription))
                )

                oid to trackNumber.id as IntId
            }

        val secondPublication =
            extTestDataService.publishInMain(trackNumbers = modifiedTrackNumbers.map { (_, id) -> id })

        val response = api.trackNumberCollection.getModified("alkuversio" to initialPublication.uuid.toString())

        assertEquals(modifiedTrackNumbers.size, response.ratanumerot.size)
        response.ratanumerot.forEach { responseTrackNumber ->
            assertEquals(modifiedDescription, responseTrackNumber.kuvaus)
        }
    }

    @Test
    fun `Track number modifications listing contains track numbers in all states (including deleted)`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val trackNumbers =
            LayoutState.entries.map { _ ->
                val (trackNumberId, referenceLineId, _) =
                    extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                        mainDraftContext,
                        segments = listOf(segment),
                    )

                val trackNumber =
                    layoutTrackNumberService.get(MainLayoutContext.draft, trackNumberId).let(::requireNotNull)

                trackNumber to referenceLineId
            }

        val initialPublication =
            extTestDataService.publishInMain(
                trackNumbers = trackNumbers.map { (trackNumber, _) -> trackNumber.id as IntId },
                referenceLines = trackNumbers.map { (_, referenceLineId) -> referenceLineId },
            )

        trackNumbers
            .map { (trackNumber, _) -> trackNumber }
            .zip(LayoutState.entries)
            .forEach { (trackNumber, newState) -> mainDraftContext.saveTrackNumber(trackNumber.copy(state = newState)) }

        val secondPublication =
            extTestDataService.publishInMain(
                trackNumbers = trackNumbers.map { (trackNumber, _) -> trackNumber.id as IntId }
            )

        val response = api.trackNumberCollection.getModified("alkuversio" to initialPublication.uuid.toString())

        assertEquals(trackNumbers.size, response.ratanumerot.size)
    }

    @Test
    fun `Track number modification listing respects start & end track layout version arguments`() {
        extTestDataService.publishInMain() // Purposeful empty publication which should not be in any of the responses

        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val publicationStart =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
            )

        val modifiedDescription = "modified description after publication ${publicationStart.uuid}"
        layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull).let { trackNumber
            ->
            mainDraftContext.save(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))
        }

        val publicationEnd = extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId))

        // Publish one more time, this should also not be in the response
        extTestDataService.publishInMain()

        val response =
            api.trackNumberCollection.getModified(
                "alkuversio" to publicationStart.uuid.toString(),
                "loppuversio" to publicationEnd.uuid.toString(),
            )

        assertEquals(publicationStart.uuid.toString(), response.alkuversio)
        assertEquals(publicationEnd.uuid.toString(), response.loppuversio)

        assertEquals(1, response.ratanumerot.size)
        assertEquals(modifiedDescription, response.ratanumerot.first().kuvaus)
    }

    @Test
    fun `Track number modification listing should use the newest track layout version if end version is not supplied`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val publicationStart =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(referenceLineId),
            )

        val modifiedDescription = "modified description after publication ${publicationStart.uuid}"
        layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull).let { trackNumber
            ->
            mainDraftContext.save(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))
        }

        val publicationEnd = extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId))

        val response = api.trackNumberCollection.getModified("alkuversio" to publicationStart.uuid.toString())

        assertEquals(publicationStart.uuid.toString(), response.alkuversio)
        assertEquals(publicationEnd.uuid.toString(), response.loppuversio)

        assertEquals(1, response.ratanumerot.size)
        assertEquals(modifiedDescription, response.ratanumerot.first().kuvaus)
    }

    @Test
    fun `Track number modifications listing lists track numbers with calculated changes`() {
        val segment = segment(Point(0.0, 0.0), Point(10.0, 0.0))
        val (trackNumberId1, referenceLineId1, trackNumberOid1) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))
        val (trackNumberId2, referenceLineId2, trackNumberOid2) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val basePublication =
            extTestDataService.publishInMain(
                trackNumbers = listOf(trackNumberId1, trackNumberId2),
                referenceLines = listOf(referenceLineId1, referenceLineId2),
            )
        assertEquals("0000+0000.000", getExtTrackNumberInCollection(trackNumberOid1)?.alkusijainti?.rataosoite)
        assertEquals("0000+0010.000", getExtTrackNumberInCollection(trackNumberOid1)?.loppusijainti?.rataosoite)
        assertEquals("0000+0000.000", getExtTrackNumberInCollection(trackNumberOid2)?.alkusijainti?.rataosoite)
        assertEquals("0000+0010.000", getExtTrackNumberInCollection(trackNumberOid2)?.loppusijainti?.rataosoite)
        verifyNoModificationSince(basePublication)

        mainDraftContext.save(
            mainOfficialContext
                .fetch(referenceLineId1)!!
                .copy(startAddress = TrackMeter(KmNumber("0001"), BigDecimal.TEN)),
            alignment(segment),
        )
        val updateRlPublication = extTestDataService.publishInMain(referenceLines = listOf(referenceLineId1))
        assertEquals("0001+0010.000", getExtTrackNumberInCollection(trackNumberOid1)?.alkusijainti?.rataosoite)
        assertEquals("0001+0020.000", getExtTrackNumberInCollection(trackNumberOid1)?.loppusijainti?.rataosoite)
        assertEquals("0000+0000.000", getExtTrackNumberInCollection(trackNumberOid2)?.alkusijainti?.rataosoite)
        assertEquals("0000+0010.000", getExtTrackNumberInCollection(trackNumberOid2)?.loppusijainti?.rataosoite)
        getModificationsSince(basePublication).let { mods ->
            assertEquals(listOf(trackNumberOid1.toString()), mods.map { it.ratanumero_oid })
            assertEquals("0001+0010.000", mods[0].alkusijainti?.rataosoite)
            assertEquals("0001+0020.000", mods[0].loppusijainti?.rataosoite)
        }
        verifyNoModificationSince(updateRlPublication)

        val kmpId =
            mainDraftContext.save(kmPost(trackNumberId1, KmNumber(4), gkLocation = kmPostGkLocation(5.0, 0.0))).id
        val updateKmpPublication = extTestDataService.publishInMain(kmPosts = listOf(kmpId))
        assertEquals("0001+0010.000", getExtTrackNumberInCollection(trackNumberOid1)?.alkusijainti?.rataosoite)
        assertEquals("0004+0005.000", getExtTrackNumberInCollection(trackNumberOid1)?.loppusijainti?.rataosoite)
        assertEquals("0000+0000.000", getExtTrackNumberInCollection(trackNumberOid2)?.alkusijainti?.rataosoite)
        assertEquals("0000+0010.000", getExtTrackNumberInCollection(trackNumberOid2)?.loppusijainti?.rataosoite)
        verifyNoModificationSince(updateKmpPublication)
    }

    private fun getExtTrackNumberInCollection(oid: Oid<LayoutTrackNumber>): ExtTestTrackNumberV1? =
        api.trackNumberCollection.get().ratanumerot.find { it.ratanumero_oid == oid.toString() }.also { initUser() }

    private fun getModificationsSince(publication: Publication): List<ExtTestTrackNumberV1> =
        api.trackNumberCollection
            .getModified(TRACK_LAYOUT_VERSION_FROM to publication.uuid.toString())
            .ratanumerot
            .also { initUser() }

    private fun verifyNoModificationSince(publication: Publication) =
        api.trackNumberCollection
            .getModifiedWithEmptyBody(
                TRACK_LAYOUT_VERSION_FROM to publication.uuid.toString(),
                httpStatus = HttpStatus.NO_CONTENT,
            )
            .also { initUser() }
}
