package fi.fta.geoviite.infra.ui

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class E2ETestWatcher : TestWatcher {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun testSuccessful(context: ExtensionContext?) {
        super.testSuccessful(context)
        logger.info("Success")
        PageModel.browser().close()
    }

    override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
        super.testFailed(context, cause)
        logger.info("Test failed, leaving browser open unless headless")
    }

    override fun testAborted(context: ExtensionContext?, cause: Throwable?) {
        super.testAborted(context, cause)
        logger.info("Test aborted, leaving browser open unless headless")
    }

}