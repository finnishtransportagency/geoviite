package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementIfExists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class KilometerLengthsListPage : E2EViewFragment(By.className("data-product-view")) {
    fun openLocationTrackTab(): LocationTrackKilometerLengthsListPage {
        logger.info("Open per location track tab")

        clickChild(byQaId("select-location-track-km-lengths"))
        return LocationTrackKilometerLengthsListPage()
    }

    fun openEntireNetworkTab(): EntireNetworkKilometerLengthsListPage {
        logger.info("Open entire network tab")

        clickChild(byQaId("select-entire-rail-network"))
        return EntireNetworkKilometerLengthsListPage()
    }
}

class LocationTrackKilometerLengthsListPage : KilometerLengthsListPage() {
    val locationTrack: E2EDropdown = childDropdown(byQaId("km-lengths-search-location-track"))

    val startKm: E2ETextInput = childTextInput(byQaId("km-lengths-search-start-km"))

    val endKm: E2ETextInput = childTextInput(byQaId("km-lengths-search-end-km"))

    val resultList: LocationTrackKilometerLengthsList = LocationTrackKilometerLengthsList()

    fun selectLocationTrack(searchString: String) = apply {
        logger.info("Select location track $searchString")

        locationTrack.selectFromDynamicByName(searchString)
    }

    val downloadUrl: String
        get() = childElement(byQaId("km-lengths-csv-download")).getAttribute("href")

    val entireRailNetworkKmLengthsRadioButton = getElementIfExists(byQaId("select-entire-rail-network"))
}

class LocationTrackKilometerLengthsList :
    E2ETable<LocationTrackKilometerLengthsListItem>(
        tableBy = By.className("data-product-table__table-container"),
        rowsBy = By.cssSelector("tbody tr"),
    ) {
    override fun getRowContent(row: WebElement) =
        LocationTrackKilometerLengthsListItem(row.findElements(By.tagName("td")), headerElements)
}

class LocationTrackKilometerLengthsListItem(val stationStart: String) {
    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(stationStart = getColumnContent("km-length-header-station-start", columns, headers))
}

class EntireNetworkKilometerLengthsListPage : KilometerLengthsListPage() {
    val downloadUrl: String
        get() = childElement(byQaId("km-lengths-csv-download")).getAttribute("href")
}
