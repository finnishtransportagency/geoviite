package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement


abstract class KilometerLengthsListPage : E2EViewFragment(By.className("data-product-view")) {
    fun locationTrackPage(): LocationTrackKilometerLengthsListPage {
        childElement(byQaId("select-location-track-km-lengths")).click()
        return LocationTrackKilometerLengthsListPage()
    }

    fun entireNetworkPage(): EntireNetworkKilometerLengthsListPage {
        childElement(byQaId("select-entire-rail-network")).click()
        return EntireNetworkKilometerLengthsListPage()
    }
}

class LocationTrackKilometerLengthsListPage : KilometerLengthsListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))
    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val locationTrack: E2EDropdown get() = searchForm.dropdown("km-lengths-search-location-track")
    val startKm: E2ETextInput get() = searchForm.textInputByQaId("km-lengths-search-start-km")
    val endKm: E2ETextInput get() = searchForm.textInputByQaId("km-lengths-search-end-km")

    val resultList: LocationTrackKilometerLengthsList get() = LocationTrackKilometerLengthsList()
    fun selectLocationTrack(searchString: String) {
        locationTrack.selectFromDynamicByName(searchString)
    }

    val downloadUrl: String get() = childElement(byQaId("km-lengths-csv-download")).getAttribute("href")
}

class LocationTrackKilometerLengthsList: E2ETable<LocationTrackKilometerLengthsListItem>(
    tableFetch = fetch(By.className("data-product-table__table-container")),
    rowsBy = By.cssSelector("tbody tr")
) {
    override fun getRowContent(row: WebElement) = LocationTrackKilometerLengthsListItem(row.findElements(By.tagName("td")), headerElements)
}

class LocationTrackKilometerLengthsListItem(val stationStart: String) {
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        stationStart = getColumnContent("km-length-header-station-start", columns, headers),
    )
}


class EntireNetworkKilometerLengthsListPage : KilometerLengthsListPage() {
    val downloadUrl: String get() = childElement(byQaId("km-lengths-csv-download")).getAttribute("href")

}


