package fi.fta.geoviite.infra.ui

import browser
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ERole
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import java.util.*
import openBrowser
import openRemoteChrome
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ActiveProfiles

const val UI_TEST_USER = "UI_TEST_USER"

@ActiveProfiles("e2e")
@ExtendWith(E2ETestWatcher::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class SeleniumTest : DBTestBase(UI_TEST_USER) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${geoviite.e2e-client.geoviite-url}") val startUrlProp: String? = null
    val startUrl: String by lazy { requireNotNull(startUrlProp) }

    @Value("\${geoviite.e2e-client.remote-selenium.enabled:false}") val remoteSeleniumEnabled: Boolean = false
    @Value("\${geoviite.e2e-client.remote-selenium.hub-url:invalid}") val remoteSeleniumHubUrl: String = "invalid"

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
    }

    val geoviite: E2EFrontPage
        get() {
            browser().navigate().to(startUrl)
            return E2EFrontPage()
        }

    val navigationBar: E2EAppBar
        get() = E2EAppBar()

    fun startGeoviite() {
        logger.info("Navigate to Geoviite $startUrl")
        if (remoteSeleniumEnabled) {
            openRemoteChrome(remoteSeleniumHubUrl)
        } else {
            openBrowser()
        }

        browser().navigate().to(startUrl)
    }

    fun goToFrontPage() = navigationBar.goToFrontPage()

    fun goToMap() = navigationBar.goToMap()

    fun selectRole(roleCode: E2ERole) = navigationBar.selectRole(roleCode.toString())

    fun goToInfraModelPage() = navigationBar.goToInfraModel()
}
