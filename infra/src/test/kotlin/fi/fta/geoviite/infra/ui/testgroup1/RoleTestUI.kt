package fi.fta.geoviite.infra.ui.testgroup1

import exists
import expectZeroSevereBrowserErrors
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERole
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.E2EDataProductLayoutElementListPage
import fi.fta.geoviite.infra.ui.pagemodel.dataproducts.LocationTrackKilometerLengthsListPage
import fi.fta.geoviite.infra.ui.util.byQaId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import waitUntilExists
import waitUntilVisible
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
                assertElementListingPage(role, E2EAppBar().goToElementListPage())
//                assertVerticalGeometryListPage(role, E2EAppBar().goToVerticalGeometryListPage())
                assertKmLengthsPage(role, E2EAppBar().goToKilometerLengthsPage())

                goToFrontPage()
                expectZeroSevereBrowserErrors()
            }
    }
}

fun assertNavigationBar(
    role: E2ERole,
) {
    assertDoesNotThrow("Front page navigation link did not exist for role=$role") {
        waitUntilExists(byQaId(E2EAppBar.NavLink.FRONT_PAGE.qaId))
    }

    assertDoesNotThrow("Map page navigation link did not exist for role=$role") {
        waitUntilExists(byQaId(E2EAppBar.NavLink.MAP.qaId))
    }

    assertDoesNotThrow("Data product page navigation link did not exist for role=$role") {
        waitUntilExists(byQaId(E2EAppBar.NavLink.DATA_PRODUCT.qaId))
    }

    assertInfraModelLink(role)
    assertDataProductNavigationLinks(role)
}

fun assertInfraModelLink(role: E2ERole) {
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> assertDoesNotThrow("Infra model page navigation link did not exist for role=$role") {
            waitUntilExists(byQaId(E2EAppBar.NavLink.INFRA_MODEL.qaId))
        }

        E2ERole.Consultant,
        -> assertFalse("Infra model page navigation link was unexpectedly found for role=$role") {
            exists(byQaId(E2EAppBar.NavLink.INFRA_MODEL.qaId))
        }
    }
}

fun assertDataProductNavigationLinks(role: E2ERole) {
    val navigationBar = E2EAppBar()

    navigationBar.openDataProductsMenu()

    assertElementListLink(role, navigationBar)
    assertVerticalGeometryListLink(role, navigationBar)
    assertKmLengthsLink(role, navigationBar)

    navigationBar.closeDataProductsMenu()
}

fun assertKmLengthsLink(role: E2ERole, navigationBar: E2EAppBar) {
    val kmLengthsLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.KILOMETER_LENGTHS)

    assertTrue(
        kmLengthsLinkExists,
        "Data product kilometer lengths list page navigation button was not found for role=$role."
    )
}

fun assertElementListLink(role: E2ERole, navigationBar: E2EAppBar) {
    val elementListLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.ELEMENT_LIST)

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> assertTrue(
            elementListLinkExists,
            "Data product element list page navigation button was not found for role=$role."
        )

        E2ERole.Consultant,
        -> assertFalse(
            elementListLinkExists,
            "Data product element list page navigation button was unexpectedly found for role=$role."
        )
    }
}


fun assertVerticalGeometryListLink(role: E2ERole, navigationBar: E2EAppBar) {
    val verticalGeometryListLinkExists =
        navigationBar.dataProductNavLinkExists(E2EAppBar.DataProductNavLink.VERTICAL_GEOMETRY)

    val element = "Data product vertical geometry list page navigation button"

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> assertTrue(
            verticalGeometryListLinkExists,
            expectToBeFoundMessage(element, role)
        )

        E2ERole.Consultant,
        -> assertFalse(
            verticalGeometryListLinkExists,
            expectToBeNotFoundMessage(element, role)
        )
    }
}

fun assertElementListingPage(role: E2ERole, elementListPage: E2EDataProductLayoutElementListPage) {
    assertLayoutGeometryRadioButton(role, elementListPage)
    assertPlanGeometryRadioButton(role, elementListPage)
    assertEntireRailNetworkGeometryRadioButton(role, elementListPage)
}

fun assertLayoutGeometryRadioButton(
    role: E2ERole,
    elementListPage: E2EDataProductLayoutElementListPage,
) {
    val element = "Location track layout geometry button"

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            assertNotNull(
                elementListPage.locationTrackLayoutGeometryRadioButton,
                expectToBeFoundMessage(element, role)
            )
        }

        E2ERole.Consultant,
        -> {
            assertNull(
                elementListPage.locationTrackLayoutGeometryRadioButton,
                expectToBeNotFoundMessage(element, role)
            )
        }
    }
}

fun assertPlanGeometryRadioButton(
    role: E2ERole,
    elementListPage: E2EDataProductLayoutElementListPage,
) {
    assertNotNull(
        elementListPage.layoutPlanGeometryRadioButton,
        expectToBeFoundMessage("Layout plan geometry button", role)
    )
}

fun assertEntireRailNetworkGeometryRadioButton(
    role: E2ERole,
    elementListPage: E2EDataProductLayoutElementListPage,
){
    val element = "Entire rail network element geometry"

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        -> {
            assertNotNull(
                elementListPage.entireRailNetworkGeometryRadioButton,
                expectToBeFoundMessage("$element button", role)
            )

            assertDoesNotThrow(expectToBeFoundMessage("$element download button", role)) {
                elementListPage.entireNetworkPage().downloadUrl
            }
        }

        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant,
        -> assertNull(
            elementListPage.entireRailNetworkGeometryRadioButton,
            expectToBeNotFoundMessage("$element button", role),
        )
    }
}

fun assertKmLengthsPage(role: E2ERole, kmLengthsPage: LocationTrackKilometerLengthsListPage) {
    assertLocationTrackKmLengthsRadioButton(role, kmLengthsPage)
    assertEntireRailNetworkKmLengthsRadioButton(role, kmLengthsPage)
}

fun assertLocationTrackKmLengthsRadioButton(
    role: E2ERole,
    kmLengthsPage: LocationTrackKilometerLengthsListPage,
) {
    assertNotNull(
        kmLengthsPage.entireRailNetworkKmLengthsRadioButton,
        expectToBeFoundMessage("Entire rail network track km lengths button", role)
    )
}

fun assertEntireRailNetworkKmLengthsRadioButton(
    role: E2ERole,
    kmLengthsPage: LocationTrackKilometerLengthsListPage,
){
    val element = "Entire rail network track km lengths"

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        -> {
            assertNotNull(
                kmLengthsPage.entireRailNetworkKmLengthsRadioButton,
                expectToBeFoundMessage("$element button", role),
            )

            assertDoesNotThrow("Front page navigation link did not exist for role=$role") {
                kmLengthsPage.entireNetworkPage().downloadUrl
            }
        }

        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant,
        -> assertNull(
            kmLengthsPage.entireRailNetworkKmLengthsRadioButton,
            expectToBeNotFoundMessage("$element button", role),
        )
    }
}

fun expectToBeFoundMessage(elementDescription: String, role: E2ERole): String {
    return "$elementDescription was not found for role=$role."
}

fun expectToBeNotFoundMessage(elementDescription: String, role: E2ERole): String {
    return "error: $elementDescription was unexpectedly found for role=$role."
}
