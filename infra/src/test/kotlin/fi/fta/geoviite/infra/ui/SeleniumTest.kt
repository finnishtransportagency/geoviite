package fi.fta.geoviite.infra.ui

import browser
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.FrontPage
import fi.fta.geoviite.infra.ui.util.TruncateDbDao
import openChrome
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.util.*


@ExtendWith(E2ETestWatcher::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class SeleniumTest (
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    val DEV_DEBUG = false

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)
    val uiTestUser = UserName("UI_TEST")

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        MDC.put(USER_HEADER, uiTestUser.toString())
    }

    protected fun clearAllTestData() {
        val truncateDbDao = TruncateDbDao(jdbcTemplate)
        truncateDbDao.truncateTables(
            schema = "publication",
            tables = arrayOf(
                "km_post",
                "location_track",
                "reference_line",
                "switch",
                "track_number",
                "publication",
            )
        )

        truncateDbDao.truncateTables(
            schema = "layout",
            tables = arrayOf(
                "alignment",
                "alignment_version",
                "km_post",
                "km_post_version",
                "location_track",
                "location_track_version",
                "reference_line",
                "reference_line_version",
                "switch",
                "switch_version",
                "switch_joint",
                "switch_joint_version",
                "track_number",
                "track_number_version",
            )
        )

        truncateDbDao.truncateTables(
            schema = "geometry",
            tables = arrayOf(
                "alignment",
                "cant_point",
                "element",
                "plan",
                "plan_application",
                "plan_application_version",
                "plan_file",
                "plan_project",
                "plan_project_version",
                "plan_author",
                "plan_author_version",
                "plan_version",
                "switch",
                "switch_joint",
                "vertical_intersection"
            )
        )
    }

    protected fun openBrowser() {
        val headless = !DEV_DEBUG
        logger.info("Initializing webdriver")
       //when (System.getProperty("browser.name")) {
       //    "chrome" -> PageModel.setBrowser(openChromeBrowser(headless))
       //    "firefox" -> PageModel.setBrowser(openFireFox(headless))
       //    else -> {
       //        //PageModel.setBrowser(openChromeBrowser(headless))
       //        PageModel.setBrowser(openFireFox(headless))
       //    }
       //}

//        openFirefox(headless)
        openChrome(headless)
        logger.info("Webdriver initialized")

        browser().manage().timeouts().implicitlyWait(Duration.ofSeconds(1))
        logger.info("Browser window size : ${browser().manage().window().size}")
        logger.info("Timezone: ${TimeZone.getDefault().id}")
    }

    fun openGeoviite(startUrl: String): FrontPage {
        logger.info("Navigate to Geoviite $startUrl")
        browser().navigate().to(startUrl);
        return FrontPage()
    }
}
