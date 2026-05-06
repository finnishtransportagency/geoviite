package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
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
class ExtTrackNumberCollectionIT
@Autowired
constructor(mockMvc: MockMvc, private val layoutTrackNumberService: LayoutTrackNumberService) : DBTestBase() {
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
                val (trackNumberId, trackNumberOid) =
                    mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
                val referenceLineId =
                    mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id
                Triple(trackNumberId, referenceLineId, trackNumberOid)
            }

        testDBService.publish(
            trackNumbers = trackNumbers.map { (trackNumberId, _, _) -> trackNumberId },
            referenceLines = trackNumbers.map { (_, referenceLineId, _) -> referenceLineId },
        )

        val newestButEmptyPublication = testDBService.publish()
        val response = api.trackNumberCollection.get()

        assertEquals(newestButEmptyPublication.uuid.toString(), response.rataverkon_versio)
        assertEquals(trackNumbers.size, response.ratanumerot.size)
        assertEquals(
            trackNumbers.map { it.third.toString() }.toSet(),
            response.ratanumerot.map { it.ratanumero_oid }.toSet(),
        )
    }

    @Test
    fun `Newest track number listing does not contain draft tracks`() {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val officialTrackNumbers =
            listOf(1, 2, 3).map { _ ->
                val (trackNumberId, trackNumberOid) =
                    mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
                val referenceLineId =
                    mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id
                Triple(trackNumberId, referenceLineId, trackNumberOid)
            }

        val newestPublication =
            testDBService.publish(
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
        val (draftTrackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        mainDraftContext.save(referenceLine(draftTrackNumberId), referenceLineGeometry(segment))

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
                val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
                val referenceLineId =
                    mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

                val publication =
                    testDBService.publish(
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

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        testDBService.publish(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

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
                val (trackNumberId, oid) =
                    mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
                val referenceLineId =
                    mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

                val trackNumber =
                    layoutTrackNumberService.get(MainLayoutContext.draft, trackNumberId).let(::requireNotNull)

                Triple(oid, trackNumber, referenceLineId)
            }

        val initialPublication =
            testDBService.publish(
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

        val secondPublication = testDBService.publish(trackNumbers = modifiedTrackNumbers.map { (_, id) -> id })

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
                val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
                val referenceLineId =
                    mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

                val trackNumber =
                    layoutTrackNumberService.get(MainLayoutContext.draft, trackNumberId).let(::requireNotNull)

                trackNumber to referenceLineId
            }

        val initialPublication =
            testDBService.publish(
                trackNumbers = trackNumbers.map { (trackNumber, _) -> trackNumber.id as IntId },
                referenceLines = trackNumbers.map { (_, referenceLineId) -> referenceLineId },
            )

        trackNumbers
            .map { (trackNumber, _) -> trackNumber }
            .zip(LayoutState.entries)
            .forEach { (trackNumber, newState) -> mainDraftContext.saveTrackNumber(trackNumber.copy(state = newState)) }

        val secondPublication =
            testDBService.publish(trackNumbers = trackNumbers.map { (trackNumber, _) -> trackNumber.id as IntId })

        val response = api.trackNumberCollection.getModified("alkuversio" to initialPublication.uuid.toString())

        assertEquals(trackNumbers.size, response.ratanumerot.size)
    }

    @Test
    fun `Track number modification listing respects start & end track layout version arguments`() {
        testDBService.publish() // Purposeful empty publication which should not be in any of the responses

        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val publicationStart =
            testDBService.publish(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        val modifiedDescription = "modified description after publication ${publicationStart.uuid}"
        layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull).let { trackNumber
            ->
            mainDraftContext.save(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))
        }

        val publicationEnd = testDBService.publish(trackNumbers = listOf(trackNumberId))

        // Publish one more time, this should also not be in the response
        testDBService.publish()

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

        val (trackNumberId, _) = mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        val publicationStart =
            testDBService.publish(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        val modifiedDescription = "modified description after publication ${publicationStart.uuid}"
        layoutTrackNumberService.get(MainLayoutContext.official, trackNumberId).let(::requireNotNull).let { trackNumber
            ->
            mainDraftContext.save(trackNumber.copy(description = TrackNumberDescription(modifiedDescription)))
        }

        val publicationEnd = testDBService.publish(trackNumbers = listOf(trackNumberId))

        val response = api.trackNumberCollection.getModified("alkuversio" to publicationStart.uuid.toString())

        assertEquals(publicationStart.uuid.toString(), response.alkuversio)
        assertEquals(publicationEnd.uuid.toString(), response.loppuversio)

        assertEquals(1, response.ratanumerot.size)
        assertEquals(modifiedDescription, response.ratanumerot.first().kuvaus)
    }

    @Test
    fun `Track number modifications listing lists track numbers with calculated changes`() {
        val segment = segment(Point(0.0, 0.0), Point(10.0, 0.0))
        val (trackNumberId1, trackNumberOid1) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId1 = mainDraftContext.save(referenceLine(trackNumberId1), referenceLineGeometry(segment)).id
        val (trackNumberId2, trackNumberOid2) =
            mainDraftContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId2 = mainDraftContext.save(referenceLine(trackNumberId2), referenceLineGeometry(segment)).id

        val basePublication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId1, trackNumberId2),
                referenceLines = listOf(referenceLineId1, referenceLineId2),
            )
        assertAddressRange(getExtTrackNumberInCollection(trackNumberOid1)!!, "0000+0000.000", "0000+0010.000")
        api.trackNumberCollection.assertNoModificationSince(basePublication.uuid)

        initUser()
        mainDraftContext.save(
            mainOfficialContext.fetch(referenceLineId1)!!.copy(startAddress = TrackMeter("0001+0010.000")),
            referenceLineGeometry(segment),
        )
        val rlPublication = testDBService.publish(referenceLines = listOf(referenceLineId1))
        assertAddressRange(getExtTrackNumberInCollection(trackNumberOid1)!!, "0001+0010.000", "0001+0020.000")
        api.trackNumberCollection.getModifiedBetween(basePublication.uuid, rlPublication.uuid).let { mods ->
            assertEquals(listOf(trackNumberOid1.toString()), mods.ratanumerot.map { it.ratanumero_oid })
            assertAddressRange(mods.ratanumerot[0], "0001+0010.000", "0001+0020.000")
        }
        api.trackNumberCollection.assertNoModificationSince(rlPublication.uuid)

        initUser()
        val kmpId =
            mainDraftContext.save(kmPost(trackNumberId1, KmNumber(4), gkLocation = kmPostGkLocation(5.0, 0.0))).id
        val kmpPublication = testDBService.publish(kmPosts = listOf(kmpId))
        assertAddressRange(getExtTrackNumberInCollection(trackNumberOid1)!!, "0001+0010.000", "0004+0005.000")
        api.trackNumberCollection.getModifiedBetween(rlPublication.uuid, kmpPublication.uuid).let { mods ->
            assertEquals(listOf(trackNumberOid1.toString()), mods.ratanumerot.map { it.ratanumero_oid })
            assertAddressRange(mods.ratanumerot[0], "0001+0010.000", "0004+0005.000")
        }
        api.trackNumberCollection.getModifiedBetween(basePublication.uuid, kmpPublication.uuid).let { mods ->
            assertEquals(listOf(trackNumberOid1.toString()), mods.ratanumerot.map { it.ratanumero_oid })
            assertAddressRange(mods.ratanumerot[0], "0001+0010.000", "0004+0005.000")
        }
        api.trackNumberCollection.getModifiedBetween(basePublication.uuid, rlPublication.uuid).let { mods ->
            assertEquals(listOf(trackNumberOid1.toString()), mods.ratanumerot.map { it.ratanumero_oid })
            assertAddressRange(mods.ratanumerot[0], "0001+0010.000", "0001+0020.000")
        }
        api.trackNumberCollection.assertNoModificationSince(kmpPublication.uuid)

        assertAddressRange(getExtTrackNumberInCollection(trackNumberOid2)!!, "0000+0000.000", "0000+0010.000")
    }

    private fun getExtTrackNumberInCollection(oid: Oid<LayoutTrackNumber>): ExtTestTrackNumberV1? =
        api.trackNumberCollection.get().ratanumerot.find { it.ratanumero_oid == oid.toString() }

    @Test
    fun `Track number collection is filtered by track number name`() {
        val (matchingId, matchingOid) = mainDraftContext.saveWithOid(trackNumber(TrackNumber("001 MATCHING")))
        val matchingRefId = mainDraftContext.save(referenceLine(matchingId), referenceLineGeometry(someSegment())).id
        val (otherId, _) = mainDraftContext.saveWithOid(trackNumber(TrackNumber("999 OTHER")))
        val otherRefId = mainDraftContext.save(referenceLine(otherId), referenceLineGeometry(someSegment())).id

        testDBService.publish(
            trackNumbers = listOf(matchingId, otherId),
            referenceLines = listOf(matchingRefId, otherRefId),
        )

        api.trackNumberCollection.get(TRACK_NUMBER to "001 MATCHING").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.ratanumerot.map { it.ratanumero_oid })
        }
        api.trackNumberCollection.get(TRACK_NUMBER to "atchin").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.ratanumerot.map { it.ratanumero_oid })
        }
    }

    @Test
    fun `Track number change-list is filtered by track number name`() {
        val (matchingId, matchingOid) = mainDraftContext.saveWithOid(trackNumber(TrackNumber("001 MATCHING")))
        val matchingRefId = mainDraftContext.save(referenceLine(matchingId), referenceLineGeometry(someSegment())).id
        val (otherId, _) = mainDraftContext.saveWithOid(trackNumber(TrackNumber("999 OTHER")))
        val otherRefId = mainDraftContext.save(referenceLine(otherId), referenceLineGeometry(someSegment())).id

        val fromPublication =
            testDBService
                .publish(trackNumbers = listOf(matchingId, otherId), referenceLines = listOf(matchingRefId, otherRefId))
                .uuid

        layoutTrackNumberService.get(MainLayoutContext.official, matchingId).let(::requireNotNull).also { tn ->
            mainDraftContext.saveTrackNumber(tn.copy(description = TrackNumberDescription("changed")))
        }
        layoutTrackNumberService.get(MainLayoutContext.official, otherId).let(::requireNotNull).also { tn ->
            mainDraftContext.saveTrackNumber(tn.copy(description = TrackNumberDescription("changed")))
        }

        testDBService.publish(trackNumbers = listOf(matchingId, otherId))

        api.trackNumberCollection.getModifiedSince(fromPublication, TRACK_NUMBER to "001 MATCHING").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.ratanumerot.map { it.ratanumero_oid })
        }
        api.trackNumberCollection.getModifiedSince(fromPublication, TRACK_NUMBER to "atch").also { response ->
            assertEquals(listOf(matchingOid.toString()), response.ratanumerot.map { it.ratanumero_oid })
        }
    }
}
