package fi.fta.geoviite.infra.ui.pagemodel

import browser
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.FrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.InfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.MapPage
import org.openqa.selenium.By

class MainNavigationBar {

    fun kartta(): MapPage {
        browser().findElement(By.ByLinkText("Kartta")).click()
        return MapPage()
    }

    fun inframodel(): InfraModelPage {
        browser().findElement(By.ByLinkText("InfraModel")).click()
        return InfraModelPage()
    }

    fun etusivu(): FrontPage {
        browser().findElement(By.linkText("Etusivu")).click()
        return FrontPage()
    }

}
