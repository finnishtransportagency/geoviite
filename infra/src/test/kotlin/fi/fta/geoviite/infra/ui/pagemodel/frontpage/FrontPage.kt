package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilExists

class E2EFrontPage : E2EViewFragment(By.className("frontpage")) {

    fun openNthPublication(nth: Int): E2EPublicationDetailsPage {
        logger.info("Open publication nth=$nth")

        clickChild(
            ByChained(
                By.xpath("(//div[@class='publication-list-item'])[$nth]"),
                By.tagName("a")
            )
        )

        return E2EPublicationDetailsPage()
    }

    fun openLatestPublication(): E2EPublicationDetailsPage {
        logger.info("Open latest publication")

        return openNthPublication(1)
    }

    fun pushToRatko(): E2EFrontPage = apply {
        logger.info("Push to Ratko")

        clickChild(byQaId("publish-to-ratko"))
        E2EDialog().clickPrimaryButton()
    }
}

class E2EPublicationDetailsPage(pageBy: By = By.className("publication-details")) : E2EViewFragment(pageBy) {
    fun returnToFrontPage(): E2EFrontPage {
        logger.info("Go to front page")

        clickChild(ByChained(By.className("publication-details__title"), By.tagName("a")))
        waitUntilExists(By.className("frontpage"))

        return E2EFrontPage()
    }

    val rows: List<E2EPublicationDetailRow>
        get() {
            val headers = childElements(By.tagName("th"))

            return childElements(By.className("publication-table__row")).map { e ->
                E2EPublicationDetailRow(e.findElements(By.tagName("td")), headers)
            }
        }
}
