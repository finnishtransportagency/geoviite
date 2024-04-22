package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EToast
import fi.fta.geoviite.infra.ui.pagemodel.common.ToastType
import getElements
import org.openqa.selenium.By
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import synchronizeAndConsumeCurrentBrowserLog
import java.util.logging.Level
import kotlin.test.assertEquals

private val logger: Logger = LoggerFactory.getLogger("BROWSER")

fun assertStringContains(expectedList: List<String>, actual: String) =
    expectedList.forEach { expected -> kotlin.test.assertContains(actual, expected) }

fun assertZeroErrorToasts() {
    val toasts = getElements(By.xpath("//div[starts-with(@id, 'toast')]"))
        .map(::E2EToast)
        .filter { toast ->
            toast.type == ToastType.ERROR
        }

    assertEquals(0, toasts.size, "Expected zero error toasts, but found ${toasts.size}")
}

fun assertZeroBrowserConsoleErrors() {
    val logEntries = synchronizeAndConsumeCurrentBrowserLog()

    val severeLogEntries = logEntries
        .filter { logEntry -> logEntry.level == Level.SEVERE }

    severeLogEntries.forEach { logEntry ->
        logger.error(logEntry.toString())
    }

    val severeLogEntryAmount = severeLogEntries.toList().size
    assertEquals(0, severeLogEntryAmount, "Expected zero console errors, but found $severeLogEntryAmount")
}
