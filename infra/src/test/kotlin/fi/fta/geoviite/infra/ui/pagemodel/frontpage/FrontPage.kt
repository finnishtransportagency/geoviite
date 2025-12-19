package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import clickWhenClickable
import exists
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilExists
import waitUntilNotExist
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class E2EFrontPage : E2EViewFragment(By.className("frontpage")) {

    fun openNthPublication(nth: Int): E2EPublicationDetailsPage {
        logger.info("Open publication nth=$nth")

        clickChild(ByChained(By.xpath("(//div[@class='publication-list-item'])[$nth]"), By.tagName("a")))

        return E2EPublicationDetailsPage()
    }

    fun openNthSplitPublicationDetails(nth: Int): SplitDetailsDialog {
        logger.info("Open split publication nth=$nth")

        openNthSplitActionsMenu(nth)
        clickWhenClickable(byQaId("show-split-info-link"))

        return SplitDetailsDialog()
    }

    fun setNthSplitBulkTransferCompleted(nth: Int) = apply {
        logger.info("Set split publication bulk transfer completed nth=$nth")

        openNthSplitActionsMenu(nth)
        getElementWhenExists(byQaId("mark-bulk-transfer-as-finished-link")).click()
        waitAndClearToast("toast-bulk-transfer-marked-as-successful")
    }

    fun openNthSplitActionsMenu(nth: Int): E2EFrontPage = apply {
        clickChild(
            ByChained(
                By.xpath("(//div[@class='publication-list-item__split'])[$nth]"),
                byQaId("publication-actions-menu-toggle"),
            )
        )
    }

    fun openLatestPublication(): E2EPublicationDetailsPage {
        logger.info("Open latest publication")

        return openNthPublication(1)
    }

    fun pushToRatko(): E2EFrontPage = apply {
        logger.info("Push to Ratko")

        clickChild(byQaId("publish-to-ratko"))
        E2EDialog().clickPrimaryButton()
    }

    fun openPublicationLog(): E2EPublicationLog {
        logger.info("Open publication log")

        clickChild(byQaId("open-publication-log"))
        return E2EPublicationLog()
    }
}

class E2EPublicationDetailsPage(pageBy: By = By.className("publication-details")) : E2EViewFragment(pageBy) {
    fun returnToFrontPage(): E2EFrontPage {
        logger.info("Go to front page")

        clickChild(ByChained(By.className("publication-details__title"), By.tagName("a")))
        waitUntilExists(By.className("frontpage"))

        return E2EFrontPage()
    }

    val rows: List<E2EPublicationDetailRow>
        get() {
            val headers = childElements(By.tagName("th"))

            return childElements(By.className("publication-table__row")).map { e ->
                E2EPublicationDetailRow(e.findElements(By.tagName("td")), headers)
            }
        }
}

class E2EPublicationLog(pageBy: By = By.className("publication-log")) : E2EViewFragment(pageBy) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun formatInstant(instant: Instant): String {
        val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
        return dateFormatter.format(zonedDateTime)
    }

    fun returnToFrontPage(): E2EFrontPage {
        logger.info("Go to front page")

        clickChild(ByChained(By.className("publication-log__title"), By.tagName("a")))
        waitUntilExists(By.className("frontpage"))

        return E2EFrontPage()
    }

    fun setSearchStartDate(instant: Instant) = apply {
        childTextInput(byQaId("publication-log-start-date-input")).replaceValue(formatInstant(instant))
    }

    fun setSearchEndDate(instant: Instant) = apply {
        childTextInput(byQaId("publication-log-end-date-input")).replaceValue(formatInstant(instant))
    }

    fun waitUntilLoaded() = apply { waitUntilNotExist(By.className("table__container--loading")) }

    val rows: List<E2EPublicationDetailRow>
        get() {
            val headers = childElements(By.tagName("th"))

            return if (!exists(By.className("publication-table__row"))) {
                emptyList()
            } else {
                childElements(By.className("publication-table__row")).map { e ->
                    E2EPublicationDetailRow(e.findElements(By.tagName("td")), headers)
                }
            }
        }
}
