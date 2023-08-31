package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EAppBar(by: By = By.className("app-bar")) : E2EViewFragment(by) {

    enum class NavLink(val qaId: String) {
        FRONT_PAGE("frontpage-link"),
        MAP("track-layout-link"),
        INFRA_MODEL("infra-model-link"),
    }

    private fun goto(to: NavLink) = clickChild(byQaId(to.qaId))

    fun goToMap(): E2ETrackLayoutPage {
        goto(NavLink.MAP)
        return E2ETrackLayoutPage()
    }

    fun goToInfraModel(): E2EInfraModelPage {
        goto(NavLink.INFRA_MODEL)
        return E2EInfraModelPage()
    }

    fun goToFrontPage(): E2EFrontPage {
        goto(NavLink.FRONT_PAGE)
        return E2EFrontPage()
    }
}
