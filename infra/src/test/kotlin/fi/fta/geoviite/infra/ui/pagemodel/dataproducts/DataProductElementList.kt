package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

abstract class E2EDataProductElementList<Item : E2EDataProductElementListItem> : E2ETable<Item>(
    tableFetch = fetch(By.className("data-product-table__table-container")),
    rowsBy = By.cssSelector("tbody tr")
)

class E2EDataProductLayoutElementList : E2EDataProductElementList<E2EDataProductLayoutElementListItem>() {
    override fun getRowContent(row: WebElement) = E2EDataProductLayoutElementListItem(row.findElements(By.tagName("td")), headerElements)
}

class E2EDataProductPlanElementList : E2EDataProductElementList<E2EDataProductPlanElementListItem>() {
    override fun getRowContent(row: WebElement) = E2EDataProductPlanElementListItem(row.findElements(By.tagName("td")), headerElements)
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
        locationStartE = getColumnContent("data-products.element-list.element-list-table.location-start-e", columns, headers),
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
    val locationTrack = getColumnContent("data-products.element-list.element-list-table.location-track", columns, headers)
}
