package fi.fta.geoviite.infra.ui.testgroup1

import exists
import expectZeroSevereBrowserErrors
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERole
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.LocationTrackKilometerLengthsListPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.util.byQaId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import waitUntilExists
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class RoleTestUI @Autowired constructor(
//    private val switchDao: LayoutSwitchDao,
//    private val geometryDao: GeometryDao,
//    private val trackNumberDao: LayoutTrackNumberDao,
//    private val kmPostDao: LayoutKmPostDao,
//    private val referenceLineDao: ReferenceLineDao,
//    private val alignmentDao: LayoutAlignmentDao,
) : SeleniumTest() {

    @BeforeAll
    fun setup() {
        startGeoviite()
    }

    @Test
    fun `Consultant page test`() {
        val currentRole = E2ERole.Consultant
        selectRole(currentRole)
        goToMap()

        val kmLengthsPage = navigationBar.goToKilometerLengthsPage()
        assertNotNull(
            kmLengthsPage.locationTrackKmLengthsRadioButton,
            "Location track km lengths button was not found.",
        )

        assertNull(
            kmLengthsPage.locationTrackKmLengthsRadioButton,
            "Location track km lengths button was not supposed to exist but was found.",
        )

        expectZeroSevereBrowserErrors()
    }

    @Test
    fun `Page navigation test`() {
//        listOf(E2ERole.Team)
        E2ERole.entries
            .forEach { role ->
                if (role != E2ERole.Operator) {
                    selectRole(role)
                }

                assertNavigationBar(role)
//                assertLicensePage() // TODO At least locally, this causes 404 severe browser error for now.

                assertFrontPage(role)
                assertMapPage(role)
                assertInfraModelPage(role)

                assertElementListingPage(role)
                assertVerticalGeometryListPage(role)
                assertKmLengthsPage(role)

                expectZeroSevereBrowserErrors()
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

fun assertLicensePage() {
    E2EAppBar().goToLicensePage()
}

fun assertFrontPage(role: E2ERole) {
    E2EAppBar().goToFrontPage()
    assertPublicationLog()
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

        E2ERole.Consultant
        -> {} // Consultant should not have access to the infra model page.
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

fun assertPublicationLog() {
    E2EAppBar()
        .goToFrontPage()
        .openPublicationLog()
        .returnToFrontPage()
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

fun assertKmLengthsLink(navigationBar: E2EAppBar) {
    val kmLengthsLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.KILOMETER_LENGTHS)

    assertTrue(kmLengthsLinkExists)
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

fun assertElementListingPage(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            val elementListPage = E2EAppBar().goToElementListPage()

            val csvDownloaderRoles = setOf(
                E2ERole.Operator,
                E2ERole.Team,
            )

            if (role in csvDownloaderRoles) {
                val entireNetworkPage = elementListPage.entireNetworkPage()
                assertNotNull(entireNetworkPage.downloadUrl)
            } else {
                assertNull(elementListPage.entireRailNetworkGeometryRadioButton)
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

            val csvDownloaderRoles = setOf(
                E2ERole.Operator,
                E2ERole.Team,
            )

            if (role in csvDownloaderRoles) {
                assertNotNull(verticalGeometryListPage.entireNetworkPage().downloadUrl)
            } else {
                assertNull(verticalGeometryListPage.entireRailNetworkVerticalGeometryRadioButton)
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

    val csvDownloaderRoles = setOf(
        E2ERole.Operator,
        E2ERole.Team,
    )

    if (role in csvDownloaderRoles) {
        assertNotNull(kmLengthsPage.entireNetworkPage().downloadUrl)
    } else {
        assertNull(kmLengthsPage.entireRailNetworkKmLengthsRadioButton)
    }

    kmLengthsPage.locationTrackPage()
}


fun assertEntireRailNetworkKmLengthsRadioButton(
    role: E2ERole,
    kmLengthsPage: LocationTrackKilometerLengthsListPage,
){
    val element =  kmLengthsPage.entireRailNetworkKmLengthsRadioButton

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        -> {
            assertNotNull(element)
            assertNotNull(kmLengthsPage.entireNetworkPage().downloadUrl)
        }

        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant,
        -> assertNull(element)
    }
}
