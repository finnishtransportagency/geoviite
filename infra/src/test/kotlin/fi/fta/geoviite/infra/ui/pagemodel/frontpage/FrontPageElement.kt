package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.ListContentItem
import fi.fta.geoviite.infra.ui.pagemodel.common.ListModel
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class PublicationList(by: By = byQaId("publication-list")) : PageModel(by) {
    private val listElements: List<WebElement> get() = childElements(By.cssSelector("div.publication-list-item"))

    val publications: List<PublicationListItem> get() = listElements.mapIndexed { i, e -> PublicationListItem(e, i) }

    fun openPublicationDetails(item: PublicationListItem) = openPublicationDetails(item.index)

    fun openPublicationDetails(index: Int): PublicationDetails {
        logger.info("Open publication details")
        listElements[index].findElement(By.cssSelector("div.publication-list-item__text a")).click()
        return publicationDetails()
    }
}

data class PublicationListItem(val trackNumbers: String, val timestamp: String, val index: Int) {
    constructor(rowElement: WebElement, index: Int) : this(
        trackNumbers = rowElement.findElement(By.cssSelector("div.publication-list-item__track-numbers")).text,
        timestamp = rowElement.findElement(By.cssSelector("div.publication-list-item__text a")).text,
        index = index,
    )
}

fun publicationDetails(): PublicationDetails {
    val headers = getElementWhenVisible(By.cssSelector("div.publication-details")).findElements(By.cssSelector("th span.table__th-children")).map { it.text }
    return PublicationDetails(headers)
}

class PublicationDetails(
    headers: List<String>,
) : ListModel<PublicationDetailRowContent>(
    listBy = By.cssSelector("div.publication-details"),
    itemsBy = By.cssSelector("tr.publication-table__row"),
    getContent = { index, element ->
        PublicationDetailRowContent(
            element,
            headers,
            index
        )
    }
) {
    fun returnToFrontPage(): FrontPage {
        clickChild(By.cssSelector("div.publication-details__title a"))
        return FrontPage()
    }
}

data class PublicationDetailRowContent(
    val muutoskohde: String,
    val ratanumero: String,
    val vietyRatkoon: String,
    override val index: Int,
): ListContentItem {
    constructor(row: WebElement, headers: List<String>, index: Int) : this(
        muutoskohde = getColumnContent(headers, row, "Muutoskohde"),
        ratanumero = getColumnContent(headers, row, "Ratanro"),
        vietyRatkoon = getColumnContent(headers, row, "Viety Ratkoon"),
        index,
    )
}
