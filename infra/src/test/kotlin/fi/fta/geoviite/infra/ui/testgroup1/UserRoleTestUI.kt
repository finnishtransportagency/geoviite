package fi.fta.geoviite.infra.ui.testgroup1

import assertZeroBrowserConsoleErrors
import exists
import assertZeroErrorToasts
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERole
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.util.byQaId
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import waitUntilExists
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class UserRoleTestUI : SeleniumTest() {
    @Test
    fun `Page navigation test`() {
        startGeoviite()

        E2ERole.entries.forEach { role ->
            logger.info("Testing page navigation for roleCode=${role.roleCode}")

            if (role != E2ERole.Operator) {
                selectRole(role)
            }

            assertNavigationBar(role)
            assertLicensePage()

            assertFrontPage()
            assertMapPage(role)
            assertInfraModelPage(role)

            assertElementListingPage(role)
            assertVerticalGeometryListPage(role)
            assertKmLengthsPage(role)

            assertZeroErrorToasts()
            assertZeroBrowserConsoleErrors()
         }
    }
}

fun assertNavigationBar(role: E2ERole) {
    waitUntilExists(byQaId(E2EAppBar.NavLink.FRONT_PAGE.qaId))
    waitUntilExists(byQaId(E2EAppBar.NavLink.MAP.qaId))
    waitUntilExists(byQaId(E2EAppBar.NavLink.DATA_PRODUCT.qaId))

    assertInfraModelLink(role)
    assertDataProductNavigationLinks(role)
}

fun assertFrontPage() {
    val frontpage = E2EAppBar().goToFrontPage()
    assertPublicationLog(frontpage)
}

fun assertPublicationLog(frontpage: E2EFrontPage) {
    frontpage
        .openPublicationLog()
        .returnToFrontPage()
}

fun assertMapPage(role: E2ERole) {
    val mapPage = E2EAppBar().goToMap()

    when (role) {
        E2ERole.Operator,
        -> {
            mapPage
                .switchToDraftMode()
                .goToPreview()
                .waitForAllTableValidationsToComplete()
                .goToTrackLayout()
                .switchToOfficialMode()
        }

        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant,
        -> {
            assertNull(mapPage.switchToDraftModeButton)
        }
    }
}

fun assertInfraModelPage(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            val infraModelPage = E2EAppBar().goToInfraModel()
            assertInfraModelPageTabs(role, infraModelPage)
        }

        else -> {
            // No access.
        }
    }
}

fun assertInfraModelPageTabs(role: E2ERole, infraModelPage: E2EInfraModelPage) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        -> {
            infraModelPage
                .openVelhoWaitingForApprovalList()
                .openRejectedList()
                .goToInfraModelList()
        }

        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant
        -> {
            assertNotNull(infraModelPage.infraModelNavTabPlan)
            assertNull(infraModelPage.infraModelNavTabWaiting)
            assertNull(infraModelPage.infraModelNavTabRejected)
        }
    }
}

fun assertInfraModelLink(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> waitUntilExists(byQaId(E2EAppBar.NavLink.INFRA_MODEL.qaId))

        E2ERole.Consultant,
        -> assertFalse(exists(byQaId(E2EAppBar.NavLink.INFRA_MODEL.qaId)))
    }
}

fun assertDataProductNavigationLinks(role: E2ERole) {
    val navigationBar = E2EAppBar()

    navigationBar.openDataProductsMenu()

    assertElementListLink(role, navigationBar)
    assertVerticalGeometryListLink(role, navigationBar)
    assertKmLengthsLink(navigationBar)

    navigationBar.closeDataProductsMenu()
}

fun assertElementListLink(role: E2ERole, navigationBar: E2EAppBar) {
    val elementListLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.ELEMENT_LIST)

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> assertTrue(elementListLinkExists)

        E2ERole.Consultant,
        -> assertFalse(elementListLinkExists)
    }
}

fun assertVerticalGeometryListLink(role: E2ERole, navigationBar: E2EAppBar) {
    val verticalGeometryListLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.VERTICAL_GEOMETRY)

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> assertTrue(verticalGeometryListLinkExists)

        E2ERole.Consultant,
        -> assertFalse(verticalGeometryListLinkExists)
    }
}

fun assertKmLengthsLink(navigationBar: E2EAppBar) {
    val kmLengthsLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.KILOMETER_LENGTHS)

    assertTrue(kmLengthsLinkExists)
}

fun assertElementListingPage(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            val elementListPage = E2EAppBar().goToElementListPage()

            when (role) {
                E2ERole.Operator,
                E2ERole.Team,
                -> {
                    val entireNetworkPage = elementListPage.entireNetworkPage()
                    assertNotNull(entireNetworkPage.downloadUrl)
                }

                else
                -> assertNull(elementListPage.entireRailNetworkGeometryRadioButton)
            }

            elementListPage
                .planListPage()
                .layoutListPage()
        }

        E2ERole.Consultant,
        -> {} // Consultant should not have access to this page.
    }
}

fun assertVerticalGeometryListPage(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            val verticalGeometryListPage = E2EAppBar().goToVerticalGeometryListPage()

            when (role) {
                E2ERole.Operator,
                E2ERole.Team,
                -> assertNotNull(verticalGeometryListPage.entireNetworkPage().downloadUrl)

                else
                -> assertNull(verticalGeometryListPage.entireRailNetworkVerticalGeometryRadioButton)
            }

            verticalGeometryListPage
                .planListPage()
                .layoutListPage()
        }

        E2ERole.Consultant,
        -> {} // Consultant should not have access to this page.
    }
}

fun assertKmLengthsPage(role: E2ERole) {
    val kmLengthsPage = E2EAppBar().goToKilometerLengthsPage()

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        -> assertNotNull(kmLengthsPage.openEntireNetworkTab().downloadUrl)

        else -> assertNull(kmLengthsPage.entireRailNetworkKmLengthsRadioButton)
    }

    kmLengthsPage.openLocationTrackTab()
}

fun assertLicensePage() {
    E2EAppBar().goToLicensePage()
}
