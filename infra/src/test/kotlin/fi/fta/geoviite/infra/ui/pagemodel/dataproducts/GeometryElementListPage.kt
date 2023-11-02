package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

abstract class E2EElementListPage : E2EViewFragment(By.className("data-product-view")) {
    fun layoutListPage(): E2EDataProductLayoutElementListPage {
        clickChild(byQaId("select-layout-geometry"))
        return E2EDataProductLayoutElementListPage()
    }

    fun planListPage(): E2EDataProductPlanElementListPage {
        clickChild(byQaId("select-plan-geometry"))
        return E2EDataProductPlanElementListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkElementListPage {
        clickChild(byQaId("select-entire-rail-network"))
        return E2EDataProductEntireNetworkElementListPage()
    }
}

class E2EDataProductPlanElementListPage : E2EElementListPage() {
    val searchForm: E2EFormLayout get() = childComponent(By.className("data-products__search"), ::E2EFormLayout)
    val resultList: E2EDataProductPlanElementList get() = E2EDataProductPlanElementList()

    val plan: E2EDropdown get() = searchForm.dropdownByQaId("data-products-search-plan")

    val line: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.line")
    val curve: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.curve")
    val clothoid: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.clothoid")
    val downloadUrl: String get() {
        val text = childElement(byQaId("plan-element-list-csv-download")).getAttribute("href")
        return URLDecoder.decode(text, StandardCharsets.UTF_8)
    }

    fun selectPlan(searchString: String) {
        plan.selectFromDynamicByName(searchString)
    }
}

class E2EDataProductLayoutElementListPage : E2EElementListPage() {
    val searchForm: E2EFormLayout get() = childComponent(By.className("data-products__search"), ::E2EFormLayout)
    val locationTrack: E2EDropdown get() = searchForm.dropdownByQaId("data-products-search-location-track")
    val startAddress: E2ETextInput get() = searchForm.textInputByQaId("data-products-search-start-km")
    val endAddress: E2ETextInput get() = searchForm.textInputByQaId("data-products-search-end-km")
    val line: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.line")
    val curve: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.curve")
    val clothoid: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.clothoid")
    val missingGeometry: E2ECheckbox get() = searchForm.checkBoxByQaId("data-products.search.missing-section")
    val downloadUrl: String
        get() = childElement(byQaId("location-track-element-list-csv-download")).getAttribute("href")
    val resultList: E2EDataProductLayoutElementList get() = E2EDataProductLayoutElementList()

    fun selectLocationTrack(searchString: String) {
        locationTrack.selectFromDynamicByName(searchString)
    }
}


class E2EDataProductEntireNetworkElementListPage : E2EElementListPage() {
    val downloadUrl: String get() = childElement(byQaId("element-list-csv-download")).getAttribute("href")
}


abstract class E2EDataProductElementList<Item : E2EDataProductElementListItem> : E2ETable<Item>(
    tableBy = By.className("data-product-table__table-container"),
    rowsBy = By.cssSelector("tbody tr")
)

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
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        trackNumber = getColumnContent("data-products.element-list.element-list-table.track-number", columns, headers),
        alignment = getColumnContent("data-products.element-list.element-list-table.alignment", columns, headers),
        elementType = getColumnContent("data-products.element-list.element-list-table.element-type", columns, headers),
        locationStartE = getColumnContent(
            "data-products.element-list.element-list-table.location-start-e",
            columns,
            headers,
        ),
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
    val locationTrack = getColumnContent(
        "data-products.element-list.element-list-table.location-track",
        columns,
        headers,
    )
}
