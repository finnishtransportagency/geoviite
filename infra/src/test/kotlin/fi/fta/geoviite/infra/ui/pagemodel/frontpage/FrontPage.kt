package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.MainNavigationBar
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By

class FrontPage : PageModel(By.cssSelector("div.frontpage")) {

        fun navigationBar() : MainNavigationBar {
            return MainNavigationBar()
        }

        fun openLatestPublication(): PublicationDetails {
            logger.info("Open latest publication")
            getChildElementStaleSafe(By.cssSelector("div.publication-list-item__text a")).click()
            return PublicationDetails()
        }

        fun publications(): List<PublicationListItem> =
            getChildElementsStaleSafe(By.cssSelector("div.publication-list-item")).map { PublicationListItem(it) }

    }

