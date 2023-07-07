package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class PublicationList(by: By = byQaId("publication-list")) : PageModel(by) {
    private val listElements: List<WebElement> get() = childElements(By.cssSelector("div.publication-list-item"))

    val publications: List<PublicationListItem> get() = listElements.mapIndexed { i, e -> PublicationListItem(e, i) }

    fun openPublicationDetails(item: PublicationListItem) = openPublicationDetails(item.index)

    fun openPublicationDetails(index: Int): PublicationDetails {
        logger.info("Open publication details")
        listElements[index].findElement(By.cssSelector("div.publication-list-item__text a")).click()
        return PublicationDetails()
    }
}

data class PublicationListItem(val trackNumbers: String, val timestamp: String, val index: Int) {
    constructor(rowElement: WebElement, index: Int) : this(
        trackNumbers = rowElement.findElement(By.cssSelector("div.publication-list-item__track-numbers")).text,
        timestamp = rowElement.findElement(By.cssSelector("div.publication-list-item__text a")).text,
        index = index,
    )
}

class PublicationDetails : PageModel(By.cssSelector("div.publication-details")) {
    fun returnToFrontPage() = clickChild(By.cssSelector("div.publication-details__title a"))

    fun detailRowContents(): List<PublicationDetailRowContent> {
        logger.info("Read publication detail rows")
        val header = childElements(By.cssSelector("th span.table__th-children")).map { it.text }
        return childElements(By.cssSelector("tr.publication-table__row")).map {
            PublicationDetailRowContent(it, header)
        }
    }
}

data class PublicationDetailRowContent(
    val muutoskohde: String,
    val ratanumero: String,
    val vietyRatkoon: String,
) {
    constructor(row: WebElement, headers: List<String>) : this(
        muutoskohde = getColumnContent(headers, row, "Muutoskohde"),
        ratanumero = getColumnContent(headers, row, "Ratanro"),
        vietyRatkoon = getColumnContent(headers, row, "Viety Ratkoon"),
    )
}
