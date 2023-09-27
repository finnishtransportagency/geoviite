package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


abstract class E2EDataProductVerticalGeometryListPage : E2EViewFragment(By.className("data-product-view"))  {
    fun layoutListPage(): E2EDataProductLayoutVerticalGeometryListPage {
        childElement(byQaId("select-layout-geometry")).click()
        return E2EDataProductLayoutVerticalGeometryListPage()
    }

    fun planListPage(): E2EDataProductPlanVerticalGeometryListPage {
        childElement(byQaId("select-plan-geometry")).click()
        return E2EDataProductPlanVerticalGeometryListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkVerticalGeometryListPage {
        childElement(byQaId("select-entire-rail-network")).click()
        return E2EDataProductEntireNetworkVerticalGeometryListPage()
    }
}

class E2EDataProductPlanVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val resultList: E2EDataProductPlanVerticalGeometryList get() = E2EDataProductPlanVerticalGeometryList()

    val plan: E2EDropdown get() = searchForm.dropdown("data-products-search-plan")

    val downloadUrl: String get() {
        val text = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")
        return URLDecoder.decode(text, StandardCharsets.UTF_8)
    }

    fun selectPlan(searchString: String) {
        plan.inputValue(searchString).select(searchString)
    }
}

class E2EDataProductLayoutVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }

    val locationTrack: E2EDropdown get() = searchForm.dropdown("data-products-search-location-track")
    val resultList: E2EDataProductLayoutVerticalGeometryList get() = E2EDataProductLayoutVerticalGeometryList()

    val downloadUrl: String get() = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")

    fun selectLocationTrack(searchString: String) {
        locationTrack.inputValue(searchString)
        locationTrack.select(searchString)
    }
}

class E2EDataProductEntireNetworkVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    val downloadUrl: String get() = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")
}

abstract class E2EDataProductVerticalGeometryList<Item : E2EDataProductVerticalGeometryListItem> : E2ETable<Item>(
    tableFetch = fetch(By.className("data-product-table__table-container")),
    rowsBy = By.cssSelector("tbody tr"),
) {
    override val headersBy: By = By.cssSelector("thead tr:nth-child(2) th")
}

class E2EDataProductLayoutVerticalGeometryList : E2EDataProductVerticalGeometryList<E2EDataProductLayoutVerticalGeometryListItem>() {
    override fun getRowContent(row: WebElement) = E2EDataProductLayoutVerticalGeometryListItem(row.findElements(By.tagName("td")), headerElements)
}

class E2EDataProductPlanVerticalGeometryList : E2EDataProductVerticalGeometryList<E2EDataProductPlanVerticalGeometryListItem>() {
    override fun getRowContent(row: WebElement) = E2EDataProductPlanVerticalGeometryListItem(row.findElements(By.tagName("td")), headerElements)
}

abstract class E2EDataProductVerticalGeometryListItem(
    val plan: String,
    val pviPointHeight: String,
    val pviPointLocationE: String,
) {
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        plan = getColumnContent("data-products.vertical-geometry.table.plan", columns, headers),
        pviPointHeight = getColumnContent("data-products.vertical-geometry.table.pvi-point-height", columns, headers),
        pviPointLocationE = getColumnContent("data-products.vertical-geometry.table.pvi-point-location-e", columns, headers),
    )
}

class E2EDataProductLayoutVerticalGeometryListItem(
    private val columns: List<WebElement>,
    private val headers: List<WebElement>,
) : E2EDataProductVerticalGeometryListItem(columns, headers)

class E2EDataProductPlanVerticalGeometryListItem(
    private val columns: List<WebElement>,
    private val headers: List<WebElement>,
) : E2EDataProductVerticalGeometryListItem(columns, headers)
