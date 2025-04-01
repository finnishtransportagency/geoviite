package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.util.FreeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSearchServiceIT
@Autowired
constructor(
    val searchService: LayoutSearchService,
    val trackNumberService: LayoutTrackNumberService,
    val locationTrackService: LocationTrackService,
    val switchService: LayoutSwitchService,
    val switchDao: LayoutSwitchDao,
) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `free text search should find multiple matching track numbers, and not the ones that did not match`() {
        val trackNumbers =
            listOf(
                TrackNumber("track number 1"),
                TrackNumber("track number 11"),
                TrackNumber("number 111"),
                TrackNumber("number111 111"),
            )

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.IN_USE)

        assertEquals(
            2,
            searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("tRaCk number"), 100).size,
        )
        assertEquals(3, searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("11"), 100).size)
        assertEquals(4, searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("1"), 100).size)
    }

    @Test
    fun `free text search should limit the amount of returned track numbers as specified`() {
        val trackNumbers = (0..15).map { number -> TrackNumber("some track $number") }

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.IN_USE)

        assertEquals(5, searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("some track"), 5).size)
        assertEquals(10, searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("some track"), 10).size)
        assertEquals(15, searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("some track"), 15).size)
    }

    @Test
    fun `free text search should not return deleted track numbers when its domain id or external id does not match the search term`() {
        val trackNumbers = listOf(TrackNumber("track number 1"), TrackNumber("track number 11"))

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED)
        assertEquals(
            0,
            searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("tRaCk number"), 100).size,
        )
    }

    @Test
    fun `free text search should return deleted track number when its domain id or external id matches`() {
        val trackNumbers = listOf(TrackNumber("track number 1"), TrackNumber("track number 11"))

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED).forEachIndexed { index, trackNumberId ->
            val oid = Oid<LayoutTrackNumber>("1.2.3.4.5.6.$index")
            trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, oid)

            assertEquals(
                1,
                searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText(oid.toString()), 100).size,
            )
            assertEquals(
                1,
                searchService
                    .searchAllTrackNumbers(MainLayoutContext.draft, FreeText(trackNumberId.toString()), 100)
                    .size,
            )
        }

        // LayoutState was set to DELETED, meaning that these track numbers should not be found by
        // free text.
        assertEquals(
            0,
            searchService.searchAllTrackNumbers(MainLayoutContext.draft, FreeText("tRaCk number"), 100).size,
        )
    }

    @Test
    fun `free text search using location track as search scope should return only assets relating to search scope`() {
        val trackNumberId =
            saveTrackNumbersWithSaveRequests(listOf(TrackNumber("track number 1")), LayoutState.IN_USE).first()

        // Search scope origin track's start switch, should be included
        val topologyStartSwitchId =
            switchDao
                .fetch(
                    switchService.saveDraft(
                        LayoutBranch.main,
                        switch(name = "blaa V0001", draft = true, stateCategory = EXISTING),
                    )
                )
                .id as IntId
        // Search scope origin track's end switch, should be included
        val topologyEndSwitchId =
            switchDao
                .fetch(
                    switchService.saveDraft(
                        LayoutBranch.main,
                        switch(name = "blee V0002", draft = true, stateCategory = EXISTING),
                    )
                )
                .id as IntId
        // Related to duplicate but not in search scope, should be left out of search results
        val duplicateStartSwitchId =
            switchDao
                .fetch(
                    switchService.saveDraft(
                        LayoutBranch.main,
                        switch(name = "bluu V0003", draft = true, stateCategory = EXISTING),
                    )
                )
                .id as IntId
        // Entirely unrelated, should be left out of search results
        switchDao
            .fetch(
                switchService.saveDraft(
                    LayoutBranch.main,
                    switch(name = "bluu V0003", draft = true, stateCategory = EXISTING),
                )
            )
            .id as IntId

        val lt1 =
            mainDraftContext.save( // Location track search scope origin, should be included
                locationTrack(trackNumberId = trackNumberId, name = "blaa"),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(topologyStartSwitchId, 3),
                        endOuterSwitch = switchLinkYV(topologyEndSwitchId, 5),
                        segments = listOf(someSegment()),
                    )
                ),
            )
        val lt2 =
            mainDraftContext.save( // Duplicate based on duplicateOf, should be included
                locationTrack(trackNumberId = trackNumberId, name = "blee", duplicateOf = lt1.id),
                trackGeometry(
                    edge(startOuterSwitch = switchLinkYV(duplicateStartSwitchId, 3), segments = listOf(someSegment()))
                ),
            )
        val lt3 =
            mainDraftContext.save( // Duplicate based on switches, should be included
                locationTrack(trackNumberId = trackNumberId, name = "bloo"),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(topologyStartSwitchId, 3),
                        endOuterSwitch = switchLinkYV(topologyEndSwitchId, 5),
                        segments = listOf(someSegment()),
                    )
                ),
            )
        mainDraftContext.save( // Non-duplicate, shouldn't be included in search results
            locationTrack(trackNumberId = trackNumberId, name = "bluu"),
            someTrackGeometry(),
        )

        val searchResults = searchService.searchAssets(MainLayoutContext.draft, FreeText("bl"), 100, lt1.id)

        assertEquals(3, searchResults.locationTracks.size)
        assertContains(searchResults.locationTracks.map { it.id }, lt1.id)
        assertContains(searchResults.locationTracks.map { it.id }, lt2.id)
        assertContains(searchResults.locationTracks.map { it.id }, lt3.id)

        assertEquals(2, searchResults.switches.size)
        assertContains(searchResults.switches.map { it.id }, topologyStartSwitchId)
        assertContains(searchResults.switches.map { it.id }, topologyEndSwitchId)

        assertEquals(0, searchResults.trackNumbers.size)
    }

    private fun saveTrackNumbersWithSaveRequests(
        trackNumbers: List<TrackNumber>,
        layoutState: LayoutState,
    ): List<IntId<LayoutTrackNumber>> {
        return trackNumbers
            .map { trackNumber ->
                TrackNumberSaveRequest(
                    trackNumber,
                    TrackNumberDescription("some description"),
                    layoutState,
                    TrackMeter(KmNumber(5555), 5.5, 1),
                )
            }
            .map { saveRequest -> trackNumberService.insert(LayoutBranch.main, saveRequest).id }
    }
}
