package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By

class FrontPage : PageModel(By.cssSelector("div.frontpage")) {

    fun openNthPublication(index: Int): PublicationDetails {
        logger.info("Open publication index=$index")
        waitChildVisible(By.cssSelector("div.publication-list-item"))
        childElements(By.cssSelector("div.publication-list-item"))[index].findElement(By.tagName("a")).click()
        return publicationDetails()
    }

    fun openLatestPublication(): PublicationDetails {
        return openNthPublication(0)
    }

    fun publications() = PublicationList()
}
