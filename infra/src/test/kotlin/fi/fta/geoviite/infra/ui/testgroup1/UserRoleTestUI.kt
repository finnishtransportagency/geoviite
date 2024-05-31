package fi.fta.geoviite.infra.ui.testgroup1

import exists
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERole
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.HKI_TRACK_NUMBER_1
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westReferenceLine
import fi.fta.geoviite.infra.ui.util.assertZeroBrowserConsoleErrors
import fi.fta.geoviite.infra.ui.util.assertZeroErrorToasts
import fi.fta.geoviite.infra.ui.util.byQaId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import waitUntilExists
import waitUntilNotExist
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class UserRoleTestUI @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val referenceLineDao: ReferenceLineDao,
) : SeleniumTest() {
    @BeforeEach
    fun beforeEach() {
        clearAllTestData()
    }

    @Test
    fun `Page navigation test`() {
        val trackNumber = trackNumber(HKI_TRACK_NUMBER_1, draft = false)
        val trackNumberId = trackNumberDao.insert(trackNumber)
        val referenceLineAndAlignment = westReferenceLine(trackNumberId.id)
        val alignmentVersion = alignmentDao.insert(referenceLineAndAlignment.second)
        referenceLineDao.insert(referenceLineAndAlignment.first.copy(alignmentVersion = alignmentVersion))

        startGeoviite()

        E2ERole.entries.forEach { role ->
            logger.info("Testing page navigation for roleCode=${role.roleCode}")

            if (role != E2ERole.Operator) {
                selectRole(role)
            }

            assertNavigationBar(role)
            assertLicensePage()

            assertFrontPage()
            assertMapPage(role, trackNumber.number)
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

fun assertMapPage(role: E2ERole, trackNumber: TrackNumber) {
    val mapPage = E2EAppBar().goToMap()

    when (role) {
        E2ERole.Operator,
        -> {
            mapPage
                .also { assertDraftAndDesignModeTabsVisible() }
                .switchToDraftMode()
                .also { assertTrackLayoutPageEditButtonsVisible(it, trackNumber) }
                .goToPreview()
                .waitForAllTableValidationsToComplete()
                .goToTrackLayout()
                .switchToDesignMode()
                .also { it.toolBar.createNewWorkspace("operator") }
                // TODO: Uncomment when design mode works
                //.also { assertTrackLayoutPageEditButtonsVisible(it, trackNumber) }
                .switchToOfficialMode()
        }

        E2ERole.Team -> {
            mapPage
                .also { assertDraftAndDesignModeTabsVisible() }
                .switchToDraftMode()
                .also { assertTrackLayoutPageEditButtonsInvisible(it, trackNumber) }
                .switchToDesignMode()
                .also { it.toolBar.workspaceDropdown().selectByName("operator") }
                .also { assertTrackLayoutPageEditButtonsInvisible(it, trackNumber) }
        }

        E2ERole.Authority,
        E2ERole.Browser,
        E2ERole.Consultant,
        -> {
            mapPage
                .also { assertDraftAndDesignModeTabsInvisible() }
                .also { assertTrackLayoutPageEditButtonsInvisible(it, trackNumber) }
        }
    }
}

fun assertDraftAndDesignModeTabsVisible() {
    waitUntilExists(byQaId("draft-mode-tab"))
    waitUntilExists(byQaId("design-mode-tab"))
}

fun assertDraftAndDesignModeTabsInvisible() {
    waitUntilNotExist(byQaId("draft-mode-tab"))
    waitUntilNotExist(byQaId("design-mode-tab"))
}

fun assertTrackLayoutPageEditButtonsInvisible(trackLayoutPage: E2ETrackLayoutPage, trackNumber: TrackNumber): Unit {
    trackLayoutPage.selectionPanel.selectReferenceLine(trackNumber.toString())

    waitUntilNotExist(byQaId("open-preview-view"))
    waitUntilNotExist(byQaId("tool-bar.new"))
    waitUntilNotExist(By.cssSelector(".infobox__edit-icon"))

    trackLayoutPage.selectionPanel.selectReferenceLine(trackNumber.toString())
}

fun assertTrackLayoutPageEditButtonsVisible(trackLayoutPage: E2ETrackLayoutPage, trackNumber: TrackNumber): Unit {
    trackLayoutPage.selectionPanel.selectReferenceLine(trackNumber.toString())

    waitUntilExists(byQaId("draft-mode-tab"))
    waitUntilExists(byQaId("design-mode-tab"))
    waitUntilExists(byQaId("open-preview-view"))
    waitUntilExists(byQaId("tool-bar.new"))
    waitUntilExists(By.cssSelector(".infobox__edit-icon"))

    trackLayoutPage.selectionPanel.selectReferenceLine(trackNumber.toString())
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

    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser,
        -> {
            waitUntilExists(byQaId(E2EAppBar.NavLink.DATA_PRODUCT.qaId))
            assertTrue(exists(byQaId(E2EAppBar.NavLink.DATA_PRODUCT.qaId)))

            navigationBar.openDataProductsMenu()

            assertElementListLink(role, navigationBar)
            assertVerticalGeometryListLink(role, navigationBar)
            assertKmLengthsLink(navigationBar)

            navigationBar.closeDataProductsMenu()
        }
        E2ERole.Consultant ->
            assertFalse(exists(byQaId(E2EAppBar.NavLink.DATA_PRODUCT.qaId)))
    }
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
    when (role) {
        E2ERole.Operator,
        E2ERole.Team,
        E2ERole.Authority,
        E2ERole.Browser
        -> {
            val kmLengthsPage = E2EAppBar().goToKilometerLengthsPage()

            when (role) {
                E2ERole.Operator,
                E2ERole.Team,
                -> assertNotNull(kmLengthsPage.openEntireNetworkTab().downloadUrl)

                else -> assertNull(kmLengthsPage.entireRailNetworkKmLengthsRadioButton)
            }

            kmLengthsPage.openLocationTrackTab()
        }
        E2ERole.Consultant -> {} // Consultant should not have access to this page.
    }
}

fun assertLicensePage() {
    E2EAppBar().goToLicensePage()
}
