package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementIfExists
import getNonNullAttribute
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class E2EDataProductVerticalGeometryListPage : E2EViewFragment(By.className("data-product-view")) {
    fun layoutListPage(): E2EDataProductLayoutVerticalGeometryListPage {
        logger.info("Open layout geometry tab")

        clickChild(byQaId("select-layout-geometry"))
        return E2EDataProductLayoutVerticalGeometryListPage()
    }

    fun planListPage(): E2EDataProductPlanVerticalGeometryListPage {
        logger.info("Open plan geometry tab")

        clickChild(byQaId("select-plan-geometry"))
        return E2EDataProductPlanVerticalGeometryListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkVerticalGeometryListPage {
        logger.info("Open entire network tab")

        clickChild(byQaId("select-entire-rail-network"))
        return E2EDataProductEntireNetworkVerticalGeometryListPage()
    }

    val entireRailNetworkVerticalGeometryRadioButton = getElementIfExists(byQaId("select-entire-rail-network"))
}

class E2EDataProductPlanVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {

    val resultList: E2EDataProductPlanVerticalGeometryList = E2EDataProductPlanVerticalGeometryList()

    val plan: E2EDropdown = childDropdown(byQaId("data-products-search-plan"))

    val downloadUrl: String
        get() {
            val text = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")
            return URLDecoder.decode(text, StandardCharsets.UTF_8)
        }

    fun selectPlan(searchString: String) = apply {
        logger.info("Select plan $searchString")

        plan.selectFromDynamicByName(searchString)
    }
}

class E2EDataProductLayoutVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    val locationTrack: E2EDropdown = childDropdown(byQaId("data-products-search-location-track"))

    val resultList: E2EDataProductLayoutVerticalGeometryList = E2EDataProductLayoutVerticalGeometryList()

    val downloadUrl: String
        get() = childElement(byQaId("vertical-geometry-csv-download")).getNonNullAttribute("href")

    fun selectLocationTrack(searchString: String) = apply {
        logger.info("Select location track $searchString")

        locationTrack.selectFromDynamicByName(searchString)
    }
}

class E2EDataProductEntireNetworkVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    val downloadUrl: String
        get() = childElement(byQaId("vertical-geometry-csv-download")).getNonNullAttribute("href")
}

abstract class E2EDataProductVerticalGeometryList<Item : E2EDataProductVerticalGeometryListItem> :
    E2ETable<Item>(tableBy = By.className("data-product-table__table-container"), rowsBy = By.cssSelector("tbody tr")) {
    override val headersBy: By = By.cssSelector("thead tr:nth-child(2) th")
}

class E2EDataProductLayoutVerticalGeometryList :
    E2EDataProductVerticalGeometryList<E2EDataProductLayoutVerticalGeometryListItem>() {
    override fun getRowContent(row: WebElement) =
        E2EDataProductLayoutVerticalGeometryListItem(row.findElements(By.tagName("td")), headerElements)
}

class E2EDataProductPlanVerticalGeometryList :
    E2EDataProductVerticalGeometryList<E2EDataProductPlanVerticalGeometryListItem>() {
    override fun getRowContent(row: WebElement) =
        E2EDataProductPlanVerticalGeometryListItem(row.findElements(By.tagName("td")), headerElements)
}

abstract class E2EDataProductVerticalGeometryListItem(
    val plan: String,
    val pviPointHeight: String,
    val pviPointLocationE: String,
) {
    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        plan = getColumnContent("data-products.vertical-geometry.table.plan", columns, headers),
        pviPointHeight = getColumnContent("data-products.vertical-geometry.table.pvi-point-height", columns, headers),
        pviPointLocationE =
            getColumnContent("data-products.vertical-geometry.table.pvi-point-location-e", columns, headers),
    )
}

class E2EDataProductLayoutVerticalGeometryListItem(columns: List<WebElement>, headers: List<WebElement>) :
    E2EDataProductVerticalGeometryListItem(columns, headers)

class E2EDataProductPlanVerticalGeometryListItem(columns: List<WebElement>, headers: List<WebElement>) :
    E2EDataProductVerticalGeometryListItem(columns, headers)
