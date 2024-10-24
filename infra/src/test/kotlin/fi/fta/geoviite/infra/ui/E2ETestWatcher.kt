package fi.fta.geoviite.infra.ui

import fi.fta.geoviite.infra.ui.util.DEV_DEBUG
import fi.fta.geoviite.infra.ui.util.closeBrowser
import fi.fta.geoviite.infra.ui.util.printBrowserLogs
import fi.fta.geoviite.infra.ui.util.takeScreenShot
import java.lang.reflect.Method
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class E2ETestWatcher : TestWatcher {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun testSuccessful(context: ExtensionContext) = finalize {
        logger.info("SUCCESS: ${context.getClassName()} > ${context.getMethodName()}")
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) = finalize {
        logger.error("ABORTED: ${context.getClassName()} > ${context.getMethodName()}", cause)
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) = finalize {
        logger.error("FAILED: ${context.getClassName()} > ${context.getMethodName()}", cause)
        takeScreenShot("${context.getClassName()}.${context.getMethodName().replace(" ", "_")}")
        printBrowserLogs()
        // There's a lot of stuff in network logs: enable when needed
        // printNetworkLogsAll()
    }

    private fun finalize(op: () -> Unit = {}) =
        try {
            op()
        } finally {
            if (!DEV_DEBUG) closeBrowser()
        }
}

fun ExtensionContext.getClassName(): String = requiredTestClass.let(Class<*>::getSimpleName)

fun ExtensionContext.getMethodName(): String = requiredTestMethod.let(Method::getName)
