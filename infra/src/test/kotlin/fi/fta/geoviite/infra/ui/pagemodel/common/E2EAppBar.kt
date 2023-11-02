package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.E2EDataProductLayoutElementListPage
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.E2EDataProductLayoutVerticalGeometryListPage
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.LocationTrackKilometerLengthsListPage
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EAppBar(appbarBy: By = By.className("app-bar")) : E2EViewFragment(appbarBy) {

    enum class NavLink(val qaId: String) {
        FRONT_PAGE("frontpage-link"),
        MAP("track-layout-link"),
        INFRA_MODEL("infra-model-link"),
        DATA_PRODUCT("data-product-link"),
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

    enum class DataProductNavLink(val qaId: String) {
        ELEMENT_LIST("element-list-menu-link"),
        VERTICAL_GEOMETRY("vertical-geometry-menu-link"),
        KILOMETER_LENGTHS("kilometer-length-menu-link"),
    }

    private fun goToDataProductPage(link: DataProductNavLink) {
        goto(NavLink.DATA_PRODUCT)
        clickWhenClickable(byQaId(link.qaId))
    }

    fun goToElementListPage(): E2EDataProductLayoutElementListPage {
        goToDataProductPage(DataProductNavLink.ELEMENT_LIST)
        return E2EDataProductLayoutElementListPage()
    }

    fun goToVerticalGeometryListPage(): E2EDataProductLayoutVerticalGeometryListPage {
        goToDataProductPage(DataProductNavLink.VERTICAL_GEOMETRY)
        return E2EDataProductLayoutVerticalGeometryListPage()
    }

    fun goToKilometerLengthsPage(): LocationTrackKilometerLengthsListPage {
        goToDataProductPage(DataProductNavLink.KILOMETER_LENGTHS)
        return LocationTrackKilometerLengthsListPage()
    }
}
