package fi.fta.geoviite.infra.ui

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.util.BrowserLogUtil
import fi.fta.geoviite.infra.ui.util.TruncateDbDao
import io.github.bonigarcia.wdm.WebDriverManager
import org.json.JSONObject
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.SessionNotCreatedException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v106.emulation.Emulation
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.logging.LogEntries
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.util.*
import java.util.logging.Level


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class SeleniumTest (
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    val DEV_DEBUG = false;

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)
    val uiTestUser = UserName("UI_TEST")

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        MDC.put(USER_HEADER, uiTestUser.value)
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
        val browserName = System.getProperty("browser.name")
        val headless = !DEV_DEBUG
       //when (browserName) {
       //    "chrome" -> PageModel.setBrowser(openChromeBrowser(headless))
       //    "firefox" -> PageModel.setBrowser(openFireFox(headless))
       //    else -> {
       //        //PageModel.setBrowser(openChromeBrowser(headless))
       //        PageModel.setBrowser(openFireFox(headless))
       //    }
       //}

        logger.info("Create a webdriver")
        try {
            PageModel.setBrowser(openFireFox(headless))
        } catch (ex: SessionNotCreatedException) {
            logger.warn("Failed to create a webdriver. RETRY")
            PageModel.setBrowser(openFireFox(headless))
        }
        logger.info("Webdriver complete")

        PageModel.browser().manage().timeouts().implicitlyWait(Duration.ofSeconds(1))
        logger.info("Browser window size : ${PageModel.browser().manage().window().size}")
        logger.info("Timezone: ${TimeZone.getDefault().id}")
    }

    private fun openChromeBrowser(headless: Boolean = false): WebDriver {
        WebDriverManager.chromedriver().setup()
        val chromeOptions = ChromeOptions()

        if (headless) chromeOptions.addArguments("--headless")
        //if (!headless) chromeOptions.addArguments("--app=http://localhost:9000")
        chromeOptions.addArguments("--disable-dev-shm-usage")
        chromeOptions.addArguments("--no-sandbox")
        chromeOptions.addArguments("--whitelisted-ips=")
        chromeOptions.addArguments("--window-size=2560,1640")
        chromeOptions.addArguments("--incognito")
        chromeOptions.setExperimentalOption("excludeSwitches", listOf("enable-automation"))

        //if (!headless) chromeOptions.addArguments("--auto-open-devtools-for-tabs")

        val logPrefs = LoggingPreferences()
        logPrefs.enable(LogType.BROWSER, Level.ALL)
        logPrefs.enable(LogType.PERFORMANCE, Level.ALL)
        chromeOptions.setCapability("goog:loggingPrefs", logPrefs)

        val driver = ChromeDriver(chromeOptions)
        val devTools = driver.devTools
        devTools.createSession()
        devTools.send(Emulation.setTimezoneOverride("Europe/Helsinki"))

        return driver
    }

    private fun openFireFox(headless: Boolean = false): WebDriver {
        WebDriverManager.firefoxdriver().setup()
        val firefoxOptions = FirefoxOptions()
        if (headless) firefoxOptions.addArguments("-headless")
        firefoxOptions.addArguments("-private")
        firefoxOptions.addArguments("-width=2560")
        firefoxOptions.addArguments("-height=1640")
        firefoxOptions.addArguments("-purgecaches")
        return FirefoxDriver(firefoxOptions)
    }

    protected fun printBrowserLogs() {
        val logEntries: LogEntries = PageModel.browser().manage().logs().get(LogType.BROWSER)
        BrowserLogUtil.printLogEntries(logEntries)
    }

    protected fun printNetworkLogsAll() {
        val logEntries = PageModel.browser().manage().logs().get(LogType.PERFORMANCE)
        BrowserLogUtil.printLogEntries(logEntries.toList(), "ALL NETWORK LOGS")
    }

    protected fun printNetworkLogsResponses() {
        val logEntries: LogEntries = PageModel.browser().manage().logs().get(LogType.PERFORMANCE)
        val filtered = BrowserLogUtil.filter(logEntries.toList(), ".*\"Network.responseReceived\".*".toRegex())
        for (entry in filtered) {
            val jsonObject = JSONObject(entry.message)

            val response = jsonObject.getJSONObject("message").getJSONObject("params").getJSONObject("response")
            val url = response.get("url")
            val statusCode = response.get("status")
            val statusText = response.get("statusText")

            if (!url.toString().contentEquals("data:,")) {
                println("$url $statusCode/$statusText")
            }

        }
        //BrowserLogUtil.printLogEntries(filtered, "RESPONSE NETWORK LOGS")
    }
}
