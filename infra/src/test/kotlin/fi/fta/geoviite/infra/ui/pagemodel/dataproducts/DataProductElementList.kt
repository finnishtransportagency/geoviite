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

const val ELEMENT_LIST_TABLE_QA_ID = "data-products.element-list.element-list-table"

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
        trackNumber = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.track-number", columns, headers),
        locationTrack = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.location-track", columns, headers),
        alignment = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.alignment", columns, headers),
        elementType = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.element-type", columns, headers),
        trackAddressStart = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.track-address-start", columns, headers),
        trackAddressEnd = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.track-address-end", columns, headers),
        coordinateSystem = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.coordinate-system", columns, headers),
        locationStartE = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.location-start-e", columns, headers),
        locationStartN = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.location-start-n", columns, headers),
        locationEndE = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.location-end-e", columns, headers),
        locationEndN = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.location-end-n", columns, headers),
        length = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.length", columns, headers),
        curveRadiusStart = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.curve-radius-start", columns, headers),
        curveRadiusEnd = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.curve-radius-end", columns, headers),
        cantStart = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.cant-start", columns, headers),
        cantEnd = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.cant-end", columns, headers),
        angleStart = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.angle-start", columns, headers),
        angleEnd = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.angle-end", columns, headers),
        plan = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.plan", columns, headers),
        source = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.source", columns, headers),
        remarks = getColumnContent("$ELEMENT_LIST_TABLE_QA_ID.remarks", columns, headers),
    )
}
