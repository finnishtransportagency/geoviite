package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilExists

class E2EFrontPage : E2EViewFragment(By.className("frontpage")) {

    private val publicationElements: List<WebElement>
        get() = childElements(
            ByChained(byQaId("publication-list"), By.className("publication-list-item"))
        )
        

    fun openNthPublication(index: Int): E2EPublicationDetailsPage {
        logger.info("Open publication index=$index")
        publicationElements[index].findElement(By.tagName("a")).click()
        
        return E2EPublicationDetailsPage()
    }

    fun openLatestPublication(): E2EPublicationDetailsPage {
        return openNthPublication(0)
    }

    fun pushToRatko(): E2EFrontPage = apply {
        clickChild(byQaId("publish-to-ratko"))
        E2EDialog().clickPrimaryButton()
    }
}

class E2EPublicationDetailsPage(by: By = By.className("publication-details")) : E2EViewFragment(by) {
    fun returnToFrontPage(): E2EFrontPage {
        clickChild(By.cssSelector(".publication-details__title a"))
        waitUntilExists(By.className("frontpage"))

        return E2EFrontPage()
    }

    val rows: List<E2EPublicationDetailRow>
        get() {
            logger.info("Read publication detail rows")
            val headers = childElements(By.className("table__th-children"))

            return childElements(By.className("publication-table__row")).map { e ->
                E2EPublicationDetailRow(e.findElements(By.tagName("td")), headers)
            }
        }
}
