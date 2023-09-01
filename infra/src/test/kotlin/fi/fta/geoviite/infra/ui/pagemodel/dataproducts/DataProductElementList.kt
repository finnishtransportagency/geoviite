package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EDataProductElementList : E2ETable<E2EDataProductElementListItem>(
    tableFetch = fetch(By.className("data-product-table__table-container")),
    rowsBy = By.cssSelector("tbody tr")
) {
    override fun getRowContent(row: WebElement) = E2EDataProductElementListItem(row.findElements(By.tagName("td")), headerElements)
}

data class E2EDataProductElementListItem(
    val trackNumber: String,
    val locationTrack: String,
    val alignment: String,
    val elementType: String,
    val trackAddressStart: String,
    val trackAddressEnd: String,
    val coordinateSystem: String,
    val locationStartE: String,
    val locationStartN: String,
    val locationEndE: String,
    val locationEndN: String,
    val length: String,
    val curveRadiusStart: String,
    val curveRadiusEnd: String,
    val cantStart: String,
    val cantEnd: String,
    val angleStart: String,
    val angleEnd: String,
    val plan: String,
    val source: String,
    val remarks: String,
) {
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
    trackNumber = getColumnContent("data-products.element-list.element-list-table.track-number", columns, headers),
    locationTrack = getColumnContent("data-products.element-list.element-list-table.location-track", columns, headers),
    alignment = getColumnContent("data-products.element-list.element-list-table.alignment", columns, headers),
    elementType = getColumnContent("data-products.element-list.element-list-table.element-type", columns, headers),
    trackAddressStart = getColumnContent("data-products.element-list.element-list-table.track-address-start", columns, headers),
    trackAddressEnd = getColumnContent("data-products.element-list.element-list-table.track-address-end", columns, headers),
    coordinateSystem = getColumnContent("data-products.element-list.element-list-table.coordinate-system", columns, headers),
    locationStartE = getColumnContent("data-products.element-list.element-list-table.location-start-e", columns, headers),
    locationStartN = getColumnContent("data-products.element-list.element-list-table.location-start-n", columns, headers),
    locationEndE = getColumnContent("data-products.element-list.element-list-table.location-end-e", columns, headers),
    locationEndN = getColumnContent("data-products.element-list.element-list-table.location-end-n", columns, headers),
    length = getColumnContent("data-products.element-list.element-list-table.length", columns, headers),
    curveRadiusStart = getColumnContent("data-products.element-list.element-list-table.curve-radius-start", columns, headers),
    curveRadiusEnd = getColumnContent("data-products.element-list.element-list-table.curve-radius-end", columns, headers),
    cantStart = getColumnContent("data-products.element-list.element-list-table.cant-start", columns, headers),
    cantEnd = getColumnContent("data-products.element-list.element-list-table.cant-end", columns, headers),
    angleStart = getColumnContent("data-products.element-list.element-list-table.angle-start", columns, headers),
    angleEnd = getColumnContent("data-products.element-list.element-list-table.angle-end", columns, headers),
    plan = getColumnContent("data-products.element-list.element-list-table.plan", columns, headers),
    source = getColumnContent("data-products.element-list.element-list-table.source", columns, headers),
    remarks = getColumnContent("data-products.element-list.element-list-table.remarks", columns, headers),
    )
}
