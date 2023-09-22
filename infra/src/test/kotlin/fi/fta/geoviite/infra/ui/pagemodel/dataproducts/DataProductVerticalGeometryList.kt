package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

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
    val creationDate: String,
    val pviPointHeight: String,
    val pviPointLocationE: String,
) {
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        plan = getColumnContent("data-products.vertical-geometry.table.plan", columns, headers),
        creationDate = getColumnContent("data-products.vertical-geometry.table.creation-date", columns, headers),
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
