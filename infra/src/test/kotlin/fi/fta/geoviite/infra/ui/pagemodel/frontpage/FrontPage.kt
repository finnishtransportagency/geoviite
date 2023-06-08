package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By

class FrontPage : PageModel(By.cssSelector("div.frontpage")) {

    fun openLatestPublication(): PublicationDetails {
        logger.info("Open latest publication")
        clickChild(By.cssSelector("div.publication-list-item__text a"))
        return PublicationDetails()
    }

    fun publications() = PublicationList()
}
