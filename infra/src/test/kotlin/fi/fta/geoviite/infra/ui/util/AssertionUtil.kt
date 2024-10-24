package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EToast
import fi.fta.geoviite.infra.ui.pagemodel.common.ToastType
import getElements
import java.util.logging.Level
import kotlin.test.assertEquals
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("BROWSER")

fun assertStringContains(expectedList: List<String>, actual: String) =
    expectedList.forEach { expected -> kotlin.test.assertContains(actual, expected) }

fun assertZeroErrorToasts() {
    val toasts =
        getElements(By.xpath("//div[starts-with(@id, 'toast')]")).map(::E2EToast).filter { toast ->
            toast.type == ToastType.ERROR
        }

    assertEquals(0, toasts.size, "Expected zero error toasts, but found ${toasts.size}")
}

fun assertZeroBrowserConsoleErrors() {
    val skippedSevereErrors =
        listOf(
            // The map tiles are not configured to work in the AWS environment during E2E tests.
            // Consider that to be non-severe error for now.
            //
            // Example match:
            // [SEVERE]
            // http://127.0.0.1:9001/location-map/wmts/maasto?service=WMTS&request=GetCapabilities&version=1.0.0
            // - Failed to load resource: the server responded with a status of 404 (Not Found)
            "wmts/maasto?service=WMTS&request=GetCapabilities&version=1.0.0"
        )

    val logEntries = synchronizeAndConsumeCurrentBrowserLog()

    val severeLogEntries =
        logEntries
            .filter { logEntry -> logEntry.level == Level.SEVERE }
            .filter { logEntry -> !skippedSevereErrors.any { skippedError -> skippedError in logEntry.toString() } }

    severeLogEntries.forEach { logEntry -> logger.error(logEntry.toString()) }

    val severeLogEntryAmount = severeLogEntries.toList().size
    assertEquals(0, severeLogEntryAmount, "Expected zero console errors, but found $severeLogEntryAmount")
}
