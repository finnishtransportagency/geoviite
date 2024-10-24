package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementIfExists
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class E2EElementListPage : E2EViewFragment(By.className("data-product-view")) {
    fun layoutListPage(): E2EDataProductLayoutElementListPage {
        logger.info("Open layout element tab")

        clickChild(byQaId("select-layout-geometry"))
        return E2EDataProductLayoutElementListPage()
    }

    fun planListPage(): E2EDataProductPlanElementListPage {
        logger.info("Open geometry element tab")

        clickChild(byQaId("select-plan-geometry"))
        return E2EDataProductPlanElementListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkElementListPage {
        logger.info("Open entire rail network tab")

        clickChild(byQaId("select-entire-rail-network"))

        return E2EDataProductEntireNetworkElementListPage()
    }
}

class E2EDataProductPlanElementListPage : E2EElementListPage() {
    val resultList: E2EDataProductPlanElementList = E2EDataProductPlanElementList()

    val plan: E2EDropdown = childDropdown(byQaId("data-products-search-plan"))

    val line: E2ECheckbox = childCheckbox(byQaId("data-products.search.line"))

    val curve: E2ECheckbox = childCheckbox(byQaId("data-products.search.curve"))

    val clothoid: E2ECheckbox = childCheckbox(byQaId("data-products.search.clothoid"))

    val downloadUrl: String
        get() {
            val text = childElement(byQaId("plan-element-list-csv-download")).getAttribute("href")
            return URLDecoder.decode(text, StandardCharsets.UTF_8)
        }

    fun selectPlan(searchString: String) = apply {
        logger.info("Select plan $searchString")

        plan.selectFromDynamicByName(searchString)
    }
}

class E2EDataProductLayoutElementListPage : E2EElementListPage() {
    val locationTrack: E2EDropdown = childDropdown(byQaId("data-products-search-location-track"))

    val startAddress: E2ETextInput = childTextInput(byQaId("data-products-search-start-km"))

    val endAddress: E2ETextInput = childTextInput(byQaId("data-products-search-end-km"))

    val line: E2ECheckbox = childCheckbox(byQaId("data-products.search.line"))

    val curve: E2ECheckbox = childCheckbox(byQaId("data-products.search.curve"))

    val clothoid: E2ECheckbox = childCheckbox(byQaId("data-products.search.clothoid"))

    val missingGeometry: E2ECheckbox = childCheckbox(byQaId("data-products.search.missing-section"))

    val resultList: E2EDataProductLayoutElementList = E2EDataProductLayoutElementList()

    val downloadUrl: String
        get() = childElement(byQaId("location-track-element-list-csv-download")).getAttribute("href")

    val locationTrackLayoutGeometryRadioButton by lazy { getElementIfExists(byQaId("select-layout-geometry")) }
    val layoutPlanGeometryRadioButton by lazy { getElementIfExists(byQaId("select-plan-geometry")) }
    val entireRailNetworkGeometryRadioButton by lazy { getElementIfExists(byQaId("select-entire-rail-network")) }

    val entireRailNetworkDownloadCsvButton = getElementIfExists(byQaId("element-list-csv-download"))

    fun selectLocationTrack(searchString: String) = apply {
        logger.info("Select location track $searchString")

        locationTrack.selectFromDynamicByName(searchString)
    }
}

class E2EDataProductEntireNetworkElementListPage : E2EElementListPage() {
    val downloadUrl: String
        get() = childElement(byQaId("element-list-csv-download")).getAttribute("href")
}

abstract class E2EDataProductElementList<Item : E2EDataProductElementListItem> :
    E2ETable<Item>(tableBy = By.className("data-product-table__table-container"), rowsBy = By.cssSelector("tbody tr"))

class E2EDataProductLayoutElementList : E2EDataProductElementList<E2EDataProductLayoutElementListItem>() {
    override fun getRowContent(row: WebElement) =
        E2EDataProductLayoutElementListItem(row.findElements(By.tagName("td")), headerElements)
}

class E2EDataProductPlanElementList : E2EDataProductElementList<E2EDataProductPlanElementListItem>() {
    override fun getRowContent(row: WebElement) =
        E2EDataProductPlanElementListItem(row.findElements(By.tagName("td")), headerElements)
}

abstract class E2EDataProductElementListItem(
    val trackNumber: String,
    val alignment: String,
    val elementType: String,
    val locationStartE: String,
    val length: String,
    val plan: String,
    val source: String,
) {
    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        trackNumber = getColumnContent("data-products.element-list.element-list-table.track-number", columns, headers),
        alignment = getColumnContent("data-products.element-list.element-list-table.alignment", columns, headers),
        elementType = getColumnContent("data-products.element-list.element-list-table.element-type", columns, headers),
        locationStartE =
            getColumnContent("data-products.element-list.element-list-table.location-start-e", columns, headers),
        length = getColumnContent("data-products.element-list.element-list-table.length", columns, headers),
        plan = getColumnContent("data-products.element-list.element-list-table.plan", columns, headers),
        source = getColumnContent("data-products.element-list.element-list-table.source", columns, headers),
    )
}

data class E2EDataProductPlanElementListItem(
    private val columns: List<WebElement>,
    private val headers: List<WebElement>,
) : E2EDataProductElementListItem(columns, headers)

data class E2EDataProductLayoutElementListItem(
    private val columns: List<WebElement>,
    private val headers: List<WebElement>,
) : E2EDataProductElementListItem(columns, headers) {
    val locationTrack =
        getColumnContent("data-products.element-list.element-list-table.location-track", columns, headers)
}
