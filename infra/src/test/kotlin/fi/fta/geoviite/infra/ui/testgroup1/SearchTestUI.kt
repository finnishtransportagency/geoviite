package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.map.MapPage
import fi.fta.geoviite.infra.ui.pagemodel.map.MapToolPanel
import fi.fta.geoviite.infra.ui.pagemodel.map.SearchBox
import fi.fta.geoviite.infra.ui.testdata.SearchTestData
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import fi.fta.geoviite.infra.ui.util.assertStringContains
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class SearchTestUI @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    jdbcTemplate: NamedParameterJdbcTemplate,
    @Value("\${geoviite.e2e.url}") private val url: String,
) : SeleniumTest(jdbcTemplate) {
    lateinit var mapPage: MapPage
    lateinit var searchBox: SearchBox

    val toolPanel: MapToolPanel = MapToolPanel()

    lateinit var SEARCH_TRACK_NUMBER_0084: LayoutTrackNumber

    lateinit var LT_HKI_A: Pair<LocationTrack, LayoutAlignment>
    lateinit var LT_HKI_B: Pair<LocationTrack, LayoutAlignment>
    lateinit var LT_ESP_A: Pair<LocationTrack, LayoutAlignment>
    lateinit var LT_ESP_B: Pair<LocationTrack, LayoutAlignment>

    @BeforeAll
    fun createTestData() {
        clearAllTestData()

        SEARCH_TRACK_NUMBER_0084 = createTrackLayoutTrackNumber( "0084", "search test")
        val trackNumberId = trackNumberDao.insert(SEARCH_TRACK_NUMBER_0084).id

        LT_HKI_A = SearchTestData.createLocationTrackHkiA(trackNumberId)
        LT_HKI_B = SearchTestData.createLocationTrackHkiB(trackNumberId)
        LT_ESP_A = SearchTestData.createLocationTrackEspA(trackNumberId)
        LT_ESP_B = SearchTestData.createLocationTrackEspB(trackNumberId)
        insertLocationTracks(LT_HKI_A, LT_HKI_B, LT_ESP_A, LT_ESP_B)
    }

    @BeforeEach
    fun goToMapPage() {
        openBrowser()
        mapPage = PageModel.openGeoviite(url).navigationBar().kartta()
        searchBox = mapPage.searchBox()
    }

    @AfterEach
    fun closeBrowser() {
        PageModel.browser().quit()
    }

    @Test
    @Disabled
    fun launchBrowserForDebug() {

    }

    @Test
    fun `Narrow search results`() {
        searchBox.search("lt")
        val searchResults4 = searchBox.searchResults()
        assertEquals(4, searchResults4.size)

        assertStringContains(
            arrayOf(LT_HKI_A, LT_HKI_B, LT_ESP_A, LT_ESP_B).map { trackAndAlignment -> trackAndAlignment.first.name.toString() },
            searchResults4.joinToString { it.value() },
        )

        searchBox.search("-b")
        val searchResults2 = searchBox.searchResults()
        assertEquals(2, searchResults2.size)
        assertStringContains(
            arrayOf(LT_HKI_B, LT_ESP_B).map { trackAndAlignment -> trackAndAlignment.first.name.toString() },
            searchResults2.joinToString { it.value() },
        )


        searchBox.search(" esp")
        val searchResults1 = searchBox.searchResults()
        assertEquals(1, searchResults1.size)
        assertStringContains(
            arrayOf(LT_ESP_B).map { trackAndAlignment -> trackAndAlignment.first.name.toString() },
            searchResults1.joinToString { it.value() },
        )
    }

    @Test
    fun `Search opens specific location track`() {
        val locationTrack = LT_HKI_A.first

        searchBox.search(locationTrack.name.toString())
        searchBox.selectResult(locationTrack.name.toString())

        val locationTrackGeneralInfoBox = toolPanel.locationTrackGeneralInfo()
        assertEquals(locationTrack.name.toString(), locationTrackGeneralInfoBox.sijainteraidetunnus())
        assertEquals(locationTrack.description.toString(), locationTrackGeneralInfoBox.kuvaus())
        assertEquals(SEARCH_TRACK_NUMBER_0084.number.toString(), locationTrackGeneralInfoBox.ratanumero())

    }

    fun insertLocationTracks(vararg locationTracks:  Pair<LocationTrack, LayoutAlignment>) {
        locationTracks.forEach{ trackAndAlignment ->
            val alignmentVersion = alignmentDao.insert(trackAndAlignment.second)
            locationTrackDao.insert(trackAndAlignment.first.copy(alignmentVersion = alignmentVersion))
        }
    }
}
