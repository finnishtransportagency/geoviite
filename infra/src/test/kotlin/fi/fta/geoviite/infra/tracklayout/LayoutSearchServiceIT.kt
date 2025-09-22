package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
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

        assertEquals(2, searchService.searchAllTrackNumbers(searchParameters("tRaCk number")).size)
        assertEquals(3, searchService.searchAllTrackNumbers(searchParameters("11")).size)
        assertEquals(4, searchService.searchAllTrackNumbers(searchParameters("1")).size)
    }

    @Test
    fun `free text search should limit the amount of returned track numbers as specified`() {
        val trackNumbers = (0..15).map { number -> TrackNumber("some track $number") }

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.IN_USE)
        val params = searchParameters("some track")

        assertEquals(5, searchService.searchAllTrackNumbers(params.copy(limitPerResultType = 5)).size)
        assertEquals(10, searchService.searchAllTrackNumbers(params.copy(limitPerResultType = 10)).size)
        assertEquals(15, searchService.searchAllTrackNumbers(params.copy(limitPerResultType = 15)).size)
    }

    @Test
    fun `free text search should not return deleted track numbers when its domain id or external id does not match the search term`() {
        val trackNumbers = listOf(TrackNumber("track number 1"), TrackNumber("track number 11"))

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED)
        assertEquals(0, searchService.searchAllTrackNumbers(searchParameters("tRaCk number", includeDeleted = false)).size)
    }

    @Test
    fun `free text search should return deleted track number when its domain id or external id matches`() {
        val trackNumbers = listOf(TrackNumber("track number 1"), TrackNumber("track number 11"))

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED).forEachIndexed { index, trackNumberId ->
            val oid = Oid<LayoutTrackNumber>("1.2.3.4.5.6.$index")
            trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, oid)

            assertEquals(1, searchService.searchAllTrackNumbers(searchParameters(oid.toString(), includeDeleted = false)).size)
            assertEquals(
                1,
                searchService.searchAllTrackNumbers(searchParameters(trackNumberId.toString(), includeDeleted = false)).size,
            )
        }

        // LayoutState was set to DELETED, meaning that these track numbers should not be found by
        // free text.
        assertEquals(0, searchService.searchAllTrackNumbers(searchParameters("tRaCk number", includeDeleted = false)).size)
    }

    @Test
    fun `free text search should return km posts when their km number matches the search term`() {
        val trackNumber1 = mainDraftContext.save(trackNumber(TrackNumber("001")))
        val trackNumber2 = mainDraftContext.save(trackNumber(TrackNumber("002")))

        val kp1 = mainDraftContext.save(kmPost(trackNumberId = trackNumber1.id, KmNumber(1234)))
        val kp2 = mainDraftContext.save(kmPost(trackNumberId = trackNumber1.id, KmNumber(1234, "AA")))
        val kp3 = mainDraftContext.save(kmPost(trackNumberId = trackNumber2.id, KmNumber(1234)))
        val kp4 = mainDraftContext.save(kmPost(trackNumberId = trackNumber1.id, KmNumber(4321)))

        val result = searchService.searchAllKmPosts(searchParameters("1234")).map { it.version }
        assertEquals(3, result.size)
        assertContains(result, kp1)
        assertContains(result, kp2)
        assertContains(result, kp3)
    }

    @Test
    fun `free text search should return no km posts if search term is too short`() {
        val trackNumber1 = mainDraftContext.save(trackNumber(TrackNumber("001")))
        mainDraftContext.save(kmPost(trackNumberId = trackNumber1.id, KmNumber(1234)))

        val result = searchService.searchAllKmPosts(searchParameters("123")).map { it.version }
        assertEquals(0, result.size)
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

        val searchResults =
            searchService.searchAssets(
                lt1.id,
                listOf(
                    TrackLayoutSearchedAssetType.LOCATION_TRACK,
                    TrackLayoutSearchedAssetType.SWITCH,
                    TrackLayoutSearchedAssetType.TRACK_NUMBER,
                ),
                searchParameters("bl"),
            )

        assertEquals(3, searchResults.locationTracks.size)
        assertContains(searchResults.locationTracks.map { it.id }, lt1.id)
        assertContains(searchResults.locationTracks.map { it.id }, lt2.id)
        assertContains(searchResults.locationTracks.map { it.id }, lt3.id)

        assertEquals(2, searchResults.switches.size)
        assertContains(searchResults.switches.map { it.id }, topologyStartSwitchId)
        assertContains(searchResults.switches.map { it.id }, topologyEndSwitchId)

        assertEquals(0, searchResults.trackNumbers.size)
    }

    @Test
    fun `search by oid supports finding design objects by main oid`() {
        val trackNumberVersion = mainOfficialContext.createLayoutTrackNumber()
        val trackNumberId = trackNumberVersion.id
        val designBranch = testDBService.createDesignBranch()
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, Oid("1.2.3.4.5"))
        trackNumberService.insertExternalId(designBranch, trackNumberId, Oid("2.3.4.5.6"))

        fun search(term: String, branch: LayoutBranch) =
            searchService.searchAssets(
                locationTrackSearchScope = null,
                listOf(
                    TrackLayoutSearchedAssetType.LOCATION_TRACK,
                    TrackLayoutSearchedAssetType.SWITCH,
                    TrackLayoutSearchedAssetType.TRACK_NUMBER,
                ),
                searchParameters(term, branch.draft),
            )

        val tn = testDBService.fetch(trackNumberVersion)

        assertEquals(listOf(tn), search("1.2.3.4.5", LayoutBranch.main).trackNumbers)
        assertEquals(listOf(), search("2.3.4.5.6", LayoutBranch.main).trackNumbers)
        assertEquals(listOf(), search("1.1.1.1.1", LayoutBranch.main).trackNumbers)

        assertEquals(listOf(tn), search("1.2.3.4.5", designBranch).trackNumbers)
        assertEquals(listOf(tn), search("2.3.4.5.6", designBranch).trackNumbers)
        assertEquals(listOf(), search("1.1.1.1.1", designBranch).trackNumbers)
    }

    @Test
    fun `free text search finds deleted assets if includeDeleted is set to true`() {
        val trackNumber =
            mainOfficialContext.save(trackNumber(number = TrackNumber("0001"), state = LayoutState.DELETED))
        val locationTrack =
            mainOfficialContext.save(
                locationTrack(
                    trackNumberId = trackNumber.id,
                    geometry = someTrackGeometry(),
                    name = "0001",
                    state = LocationTrackState.DELETED,
                )
            )
        val sw =
            mainOfficialContext.save(
                switch(name = "0001", draft = false, stateCategory = LayoutStateCategory.NOT_EXISTING)
            )
        val kmPost =
            mainOfficialContext.save(
                kmPost(trackNumberId = trackNumber.id, km = KmNumber(1), state = LayoutState.DELETED)
            )

        fun search(includeDeleted: Boolean) =
            searchService.searchAssets(
                locationTrackSearchScope = null,
                listOf(
                    TrackLayoutSearchedAssetType.LOCATION_TRACK,
                    TrackLayoutSearchedAssetType.SWITCH,
                    TrackLayoutSearchedAssetType.TRACK_NUMBER,
                    TrackLayoutSearchedAssetType.KM_POST,
                ),
                searchParameters("0001", includeDeleted = includeDeleted),
            )

        val undeletedSearchResults = search(false)
        val deletedSearchResults = search(true)

        assertEquals(0, undeletedSearchResults.trackNumbers.size)
        assertEquals(0, undeletedSearchResults.locationTracks.size)
        assertEquals(0, undeletedSearchResults.switches.size)
        assertEquals(0, undeletedSearchResults.kmPosts.size)

        assertEquals(1, deletedSearchResults.locationTracks.size)
        assertEquals(locationTrack.id, deletedSearchResults.locationTracks.first().id)

        assertEquals(1, deletedSearchResults.trackNumbers.size)
        assertEquals(trackNumber.id, deletedSearchResults.trackNumbers.first().id)

        assertEquals(1, deletedSearchResults.switches.size)
        assertEquals(sw.id, deletedSearchResults.switches.first().id)

        assertEquals(1, deletedSearchResults.kmPosts.size)
        assertEquals(kmPost.id, deletedSearchResults.kmPosts.first().id)
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

    private fun searchParameters(
        term: String,
        layoutContext: LayoutContext = MainLayoutContext.draft,
        limit: Int = 100,
        includeDeleted: Boolean = false,
    ) = AssetSearchParameters(layoutContext, FreeText(term), limit, includeDeleted)
}
