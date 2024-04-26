package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSearchServiceIT @Autowired constructor(
    val searchService: LayoutSearchService,
    val trackNumberService: LayoutTrackNumberService,
): DBTestBase() {
    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "track_number", "reference_line")
    }

    @Test
    fun `free text search should find multiple matching track numbers, and not the ones that did not match`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
            TrackNumber("number 111"),
            TrackNumber("number111 111")
        )

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.IN_USE,
        )

        assertEquals(2, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("tRaCk number"), null).size)
        assertEquals(3, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("11"), null).size)
        assertEquals(4, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("1"), null).size)
    }

    @Test
    fun `free text search should limit the amount of returned track numbers as specified`() {
        val trackNumbers = (0..15).map { number ->
            TrackNumber("some track $number")
        }

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.IN_USE,
        )

        assertEquals(5, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("some track"), 5).size)
        assertEquals(10, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("some track"), 10).size)
        assertEquals(15, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("some track"), 15).size)
    }

    @Test
    fun `free text search should not return deleted track numbers when its domain id or external id does not match the search term`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
        )

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED)
        assertEquals(0, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("tRaCk number"), null).size)
    }

    @Test
    fun `free text search should return deleted track number when its domain id or external id matches`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
        )

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.DELETED,
        ).forEachIndexed { index, trackNumberId ->
            val oid = Oid<TrackLayoutTrackNumber>("1.2.3.4.5.6.$index")
            trackNumberService.updateExternalId(trackNumberId, oid)

            assertEquals(1, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText(oid.toString()), null).size)
            assertEquals(1, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText(trackNumberId.toString()), null).size)
        }

        // LayoutState was set to DELETED, meaning that these track numbers should not be found by free text.
        assertEquals(0, searchService.searchAllTrackNumbers(PublicationState.DRAFT, FreeText("tRaCk number"), null).size)
    }

    private fun saveTrackNumbersWithSaveRequests(
        trackNumbers: List<TrackNumber>,
        layoutState: LayoutState,
    ): List<IntId<TrackLayoutTrackNumber>> {
        return trackNumbers.map { trackNumber ->
            TrackNumberSaveRequest(
                trackNumber, FreeText("some description"), layoutState, TrackMeter(
                    KmNumber(5555), 5.5, 1
                )
            )
        }.map { saveRequest ->
            trackNumberService.insert(saveRequest)
        }
    }
}
