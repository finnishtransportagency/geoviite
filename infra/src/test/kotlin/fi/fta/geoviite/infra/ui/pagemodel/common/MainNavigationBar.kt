package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.pagemodel.frontpage.FrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.InfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.MapPage
import org.openqa.selenium.By

enum class NavBarLink(val text: String) {
    FRONT_PAGE("Etusivu"), MAP("Kartta"), INFRA_MODEL("InfraModel"),
}

class MainNavigationBar : PageModel(By.className("app-bar")) {

    // TODO: GVT-1947 Change these to qa-id instead of localized link text
    fun clickLink(to: NavBarLink) = childElement(By.ByLinkText(to.text)).click()

    fun goToMap(): MapPage {
        clickLink(NavBarLink.MAP)
        return MapPage()
    }

    fun goToInfraModel(): InfraModelPage {
        clickLink(NavBarLink.INFRA_MODEL)
        return InfraModelPage()
    }

    fun goToFrontPage(): FrontPage {
        clickLink(NavBarLink.FRONT_PAGE)
        return FrontPage()
    }
}
