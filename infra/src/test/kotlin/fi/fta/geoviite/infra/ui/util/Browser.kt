package fi.fta.geoviite.infra.ui.util

import defaultWait
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v137.emulation.Emulation
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.logging.LogEntries
import org.openqa.selenium.logging.LogEntry
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.LocalFileDetector
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level

const val E2E_TASKBAR_BUFFER_PIXELS = 80
const val E2E_WINDOW_WIDTH = 1920
const val E2E_WINDOW_HEIGHT = 1080 - E2E_TASKBAR_BUFFER_PIXELS

private fun createChromeDriver(headless: Boolean): WebDriver {

    val options = ChromeOptions()

    if (headless) options.addArguments("--headless")
    // if (!headless) chromeOptions.addArguments("--app=http://localhost:9001")
    options.addArguments("--disable-dev-shm-usage")
    options.addArguments("--no-sandbox")
    options.addArguments("--whitelisted-ips=")
    options.addArguments("--window-size=$E2E_WINDOW_WIDTH,$E2E_WINDOW_HEIGHT")
    options.addArguments("--incognito")
    options.setExperimentalOption("excludeSwitches", listOf("enable-automation"))
    options.addArguments("--remote-allow-origins=*")

    // if (!headless) chromeOptions.addArguments("--auto-open-devtools-for-tabs")
    if (DEV_DEBUG) options.setExperimentalOption("detach", true)

    val logPrefs = LoggingPreferences()
    logPrefs.enable(LogType.BROWSER, Level.ALL)
    logPrefs.enable(LogType.PERFORMANCE, Level.ALL)
    options.setCapability("goog:loggingPrefs", logPrefs)

    val driver = ChromeDriver(options)
    val devTools = driver.devTools
    devTools.createSession()
    devTools.send(Emulation.setTimezoneOverride("Europe/Helsinki"))

    return driver
}

private fun createRemoteChromeDriver(seleniumHubUrl: String): RemoteWebDriver {
    val remoteDriverOptions = ChromeOptions()
    remoteDriverOptions.addArguments("--window-size=$E2E_WINDOW_WIDTH,$E2E_WINDOW_HEIGHT")
    remoteDriverOptions.addArguments("--incognito")
    remoteDriverOptions.setExperimentalOption("excludeSwitches", listOf("enable-automation"))

    val logPrefs = LoggingPreferences()
    logPrefs.enable(LogType.BROWSER, Level.ALL)
    logPrefs.enable(LogType.PERFORMANCE, Level.ALL)

    remoteDriverOptions.setCapability("goog:loggingPrefs", logPrefs)

    return RemoteWebDriver(URL(seleniumHubUrl), remoteDriverOptions).also { driver ->
        driver.fileDetector = LocalFileDetector()
    }
}

private fun createFirefoxDriver(headless: Boolean): WebDriver {
    val firefoxOptions = FirefoxOptions()
    if (headless) firefoxOptions.addArguments("-headless")
    firefoxOptions.addArguments("-private")
    firefoxOptions.addArguments("-width=2560")
    firefoxOptions.addArguments("-height=1640")
    firefoxOptions.addArguments("-purgecaches")
    firefoxOptions.addArguments("-safe-mode")
    return FirefoxDriver(firefoxOptions)
}

private val logger: Logger = LoggerFactory.getLogger("BROWSER")
private val webDriver: AtomicReference<WebDriver> = AtomicReference(null)

private fun setBrowser(createWebDriver: () -> WebDriver?) {
    webDriver.updateAndGet { currentDriver ->
        logger.info("Removing browser: title=${currentDriver?.title}")
        currentDriver?.quit()
        createWebDriver().also { newDriver -> logger.info("Setting browser: title=${newDriver?.title}") }
    }
}

const val DEV_DEBUG = false

fun openBrowser() {
    val headless = !DEV_DEBUG
    logger.info("Initializing webdriver")
    //    openFirefox(headless)
    openChrome(headless)
    logger.info("Webdriver initialized")

    browser().manage().timeouts()
    logger.info("Browser window size : ${browser().manage().window().size}")
    logger.info("Timezone: ${TimeZone.getDefault().id}")
}

fun openRemoteBrowser(seleniumHubUrl: String) {
    logger.info("Initializing remote webdriver")
    openRemoteChrome(seleniumHubUrl)
    browser().manage().timeouts()
}

fun openChrome(headless: Boolean) = setBrowser { createChromeDriver(headless) }

fun openRemoteChrome(seleniumHubUrl: String) = setBrowser { createRemoteChromeDriver(seleniumHubUrl) }

fun openFirefox(headless: Boolean) = setBrowser { createFirefoxDriver(headless) }

fun closeBrowser() = setBrowser { null }

fun browser() = requireNotNull(webDriver.get()) { "Browser null: not initialized, or already destroyed." }

fun javaScriptExecutor(): JavascriptExecutor = browser() as JavascriptExecutor

fun scrollIntoView(by: By, alignToTop: Boolean) =
    javaScriptExecutor().executeScript("arguments[0].scrollIntoView(arguments[1]);", browser().findElement(by))

const val SCREENSHOTS_PATH = "build/reports/screenshots"

fun takeScreenShot(targetFilePrefix: String) =
    try {
        val screenShotDir = File(SCREENSHOTS_PATH)
        screenShotDir.mkdirs()
        val targetFile = File("$SCREENSHOTS_PATH/$targetFilePrefix-screenshot-${Instant.now()}.png")
        logger.info("Taking screenshot for $targetFilePrefix")
        val screenShot = (browser() as TakesScreenshot).getScreenshotAs(OutputType.FILE)
        logger.info("Saving screenshot to ${targetFile.absolutePath}")
        FileUtils.copyFile(screenShot, targetFile)
        logger.info("Screenshot saved: exists=${targetFile.exists()} isFile=${targetFile.isFile}")
    } catch (e: Exception) {
        logger.error("Failed to take screenshot for $targetFilePrefix: error=${e.message}")
    }

enum class LogSource {
    CONSOLE,
    NETWORK,
    NETWORK_RESPONSES,
}

fun printBrowserLogs() =
    try {
        val logEntries: LogEntries = browser().manage().logs().get(LogType.BROWSER)
        printLogEntries(LogSource.CONSOLE, logEntries)
    } catch (e: Exception) {
        logger.error("Failed to print browser logs ${e.message}")
    }

fun printNetworkLogsAll() =
    try {
        val logEntries = browser().manage().logs().get(LogType.PERFORMANCE)
        printLogEntries(LogSource.NETWORK, logEntries.toList())
    } catch (e: Exception) {
        logger.error("Failed to print network logs ${e.message}")
    }

fun printNetworkLogsResponses() =
    try {
        val logEntries: LogEntries = browser().manage().logs().get(LogType.PERFORMANCE)
        val filtered = filter(logEntries.toList(), ".*\"Network.responseReceived\".*".toRegex())
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
        printLogEntries(LogSource.NETWORK_RESPONSES, filtered)
    } catch (e: Exception) {
        logger.error("Failed to print network responses ${e.message}")
    }

fun synchronizeAndConsumeCurrentBrowserLog(timeoutInSeconds: Duration = defaultWait): List<LogEntry> {
    val timestamp = Instant.now().toEpochMilli()
    val syncMessage = "browser_log_sync_and_consume$timestamp"
    val syncScript = "console.log('$syncMessage')"

    javaScriptExecutor().executeScript(syncScript)

    val consumedLogEntries: MutableList<LogEntry> = mutableListOf()

    WebDriverWait(browser(), timeoutInSeconds).until { driver ->
        val newLogEntries = driver.manage().logs().get(LogType.BROWSER)
        consumedLogEntries.addAll(newLogEntries)

        newLogEntries.any { log -> log.message.contains(syncMessage) }
    }

    return consumedLogEntries
}

fun waitForCookie(cookieName: String, desiredValue: String? = null, timeout: Duration = defaultWait) {
    WebDriverWait(browser(), timeout).until { driver ->
        driver.manage().cookies.any { cookie ->
            if (desiredValue == null) {
                cookie.name == cookieName
            } else {
                cookie.name == cookieName && cookie.value == desiredValue
            }
        }
    }
}

private fun printLogEntries(source: LogSource, logEntries: LogEntries) {
    printLogEntries(source, logEntries.toList())
}

private fun printLogEntries(source: LogSource, logEntries: List<LogEntry>) {
    logger.info("============ START OF LOGS FROM $source ============")
    for (entry in logEntries) logger.info("$source (${entry.level}): ${entry.message}")
    logger.info("============ END OF LOGS FROM $source ============")
}

private fun filter(logEntries: LogEntries, start: Long, end: Long): List<LogEntry> {
    return logEntries.filter { logEntry -> logEntry.timestamp in (start + 1) until end }
}

private fun filter(logEntries: LogEntries, start: Long): List<LogEntry> {
    return logEntries.filter { logEntry -> logEntry.timestamp > start }
}

private fun filter(logEntries: List<LogEntry>, regex: Regex): List<LogEntry> {
    return logEntries.filter { logEntry -> logEntry.message.matches(regex) }
}

private fun hasEntryLevel(logEntries: List<LogEntry>, level: Level): Boolean {
    val filtered = logEntries.filter { logEntry -> logEntry.level == level }
    return filtered.isNotEmpty()
}

private fun messageMatches(logEntries: List<LogEntry>, regex: Regex): Boolean {
    val filtered = logEntries.filter { logEntry -> logEntry.message.matches(regex) }
    return filtered.isNotEmpty()
}
