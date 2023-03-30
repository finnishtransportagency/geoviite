package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.TableRow
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PublicationListItem(val root: WebElement) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun trackNumbers(): String = root.findElement(By.cssSelector("div.publication-list-item__track-numbers")).text
    fun timestamp(): String = root.findElement(By.cssSelector("div.publication-list-item__text a")).text
    fun openPublicationDetails(): PublicationDetails  {
        logger.info("Open publication details")
        root.findElement(By.cssSelector("div.publication-list-item__text a")).click()
        return PublicationDetails()
    }
}

class PublicationDetails(): PageModel(By.cssSelector("div.publication-details")) {
    fun returnFrontPage() = rootElement.findElement(By.cssSelector("div.publication-details__title a")).click()
    fun publicationDetails(): List<PublicationDetailRow> {
        logger.info("Load publication detail rows")
        val header = getChildElementsStaleSafe(By.cssSelector("th span.table__th-children")).map { it.text }
        return getChildElementsStaleSafe(By.cssSelector("tr.publication-table__row")).map { PublicationDetailRow(it, header) }
    }
}

class PublicationDetailRow(row: WebElement, header: List<String> ): TableRow(header, row) {
    fun muutoskohde(): String = getColumnByName("Muutoskohde").text
    fun ratanumero(): String = getColumnByName("Ratanro").text
    fun muokattu(): String = getColumnByName("Muokattu").text
    fun vietyRatkoon(): String = getColumnByName("Viety ratkoon").text

}
