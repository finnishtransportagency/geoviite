package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import exists
import fi.fta.geoviite.infra.authorization.DESIRED_ROLE_COOKIE_NAME
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.E2EDataProductLayoutElementListPage
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.E2EDataProductLayoutVerticalGeometryListPage
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.LocationTrackKilometerLengthsListPage
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.waitForCookie
import getElement
import getNonNullAttribute
import org.openqa.selenium.By
import waitUntilExists
import waitUntilNotExist

class E2EAppBar : E2EViewFragment(By.className("app-bar")) {

    private val dataProductsMenuQaId = "data-products-menu"

    enum class NavLink(val qaId: String) {
        FRONT_PAGE("frontpage-link"),
        MAP("track-layout-link"),
        INFRA_MODEL("infra-model-link"),
        DATA_PRODUCT("data-product-link"),
    }

    private fun goto(to: NavLink) = clickChild(byQaId(to.qaId))

    fun goToMap(): E2ETrackLayoutPage {
        logger.info("Open map view")

        goto(NavLink.MAP)
        return E2ETrackLayoutPage()
    }

    fun goToInfraModel(): E2EInfraModelPage {
        logger.info("Open infra model view")

        goto(NavLink.INFRA_MODEL)
        return E2EInfraModelPage()
    }

    fun goToFrontPage(): E2EFrontPage {
        logger.info("Open frontpage")

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

    fun openDataProductsMenu() {
        if (!exists(byQaId(dataProductsMenuQaId))) {
            logger.info("Open data products menu")
            clickWhenClickable(byQaId(NavLink.DATA_PRODUCT.qaId))
        }

        waitUntilExists(byQaId(dataProductsMenuQaId))
    }

    fun closeDataProductsMenu() {
        if (exists(byQaId(dataProductsMenuQaId))) {
            logger.info("Close data products menu")
            clickWhenClickable(byQaId(NavLink.DATA_PRODUCT.qaId))
        }

        waitUntilNotExist(byQaId(dataProductsMenuQaId))
    }

    fun goToElementListPage(): E2EDataProductLayoutElementListPage {
        logger.info("Open geometry list view")

        goToDataProductPage(DataProductNavLink.ELEMENT_LIST)
        return E2EDataProductLayoutElementListPage()
    }

    fun goToVerticalGeometryListPage(): E2EDataProductLayoutVerticalGeometryListPage {
        logger.info("Open vertical geometry view")

        goToDataProductPage(DataProductNavLink.VERTICAL_GEOMETRY)
        return E2EDataProductLayoutVerticalGeometryListPage()
    }

    fun goToKilometerLengthsPage(): LocationTrackKilometerLengthsListPage {
        logger.info("Open km length view")

        goToDataProductPage(DataProductNavLink.KILOMETER_LENGTHS)
        return LocationTrackKilometerLengthsListPage()
    }

    fun selectRole(roleCode: String) {
        openAppBarMoreMenu()

        getElement(byQaId("select-role-$roleCode")).let { roleSelectionElement ->
            if (!roleSelectionElement.getNonNullAttribute("class").contains("disabled")) {
                logger.info("Select role with code=$roleCode")
                roleSelectionElement.click()
            } else {
                clickChild(byQaId("show-app-bar-more-menu"))
            }
        }

        waitUntilNotExist(byQaId("select-role-$roleCode"))
        waitUntilNotExist(byQaId("app-bar-more-menu"))

        waitForCookie(DESIRED_ROLE_COOKIE_NAME, roleCode)
    }

    fun dataProductNavLinkExists(dataProductNavLink: DataProductNavLink): Boolean {
        return exists(byQaId(dataProductNavLink.qaId))
    }

    fun openAppBarMoreMenu() {
        if (!childExists(byQaId("app-bar-more-menu"))) {
            logger.info("Open app bar more menu")
            clickChild(byQaId("show-app-bar-more-menu"))
        }
    }

    fun goToLicensePage() {
        openAppBarMoreMenu()
        clickWhenClickable(byQaId("licenses"))
    }
}
