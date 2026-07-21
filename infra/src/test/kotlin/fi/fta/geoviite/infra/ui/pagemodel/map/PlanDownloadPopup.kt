package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import org.openqa.selenium.By

class E2EPlanDownloadPopup : E2EViewFragment(byQaId("plan-download-popup")) {

    fun openAreaSection(): E2EPlanDownloadPopup = apply {
        logger.info("Open plan download area section")
        clickChild(byQaId("plan-download-section-area"))
        waitUntilChildVisible(By.className("plan-download-popup__area-grid"))
    }

    fun openPlanSection(): E2EPlanDownloadPopup = apply {
        logger.info("Open plan download plan section")
        clickChild(byQaId("plan-download-section-plan"))
        waitUntilChildVisible(By.className("plan-download-popup__plans-container"))
    }

    val assetSearch: E2EDropdown
        get() = childDropdown(By.cssSelector(".plan-download-popup__area-grid .dropdown"))

    val startKm: E2EDropdown
        get() = childDropdown(byQaId("plan-download-start-km"))

    val endKm: E2EDropdown
        get() = childDropdown(byQaId("plan-download-end-km"))

    fun selectAsset(name: String): E2EPlanDownloadPopup = apply {
        logger.info("Select plan download asset $name")
        assetSearch.selectFromDynamicByName(name)
    }

    val planRows: List<String>
        get() =
            childElements(By.cssSelector("[qa-id^='plan-download-row-']")).map {
                it.getAttribute("qa-id")!!.removePrefix("plan-download-row-")
            }

    fun togglePlan(name: String): E2EPlanDownloadPopup = apply {
        logger.info("Toggle plan $name selection")
        clickChild(By.cssSelector("[qa-id='plan-download-row-$name'] input[type='checkbox']"))
    }

    fun selectAll(): E2EPlanDownloadPopup = apply {
        logger.info("Select all plans")
        clickChild(byQaId("plan-download-select-all"))
    }

    fun unselectAll(): E2EPlanDownloadPopup = apply {
        logger.info("Unselect all plans")
        clickChild(byQaId("plan-download-unselect-all"))
    }

    val isDownloadEnabled: Boolean
        get() = getElementWhenExists(childBy(byQaId("plan-download-download"))).isEnabled

    fun close(): Unit {
        logger.info("Close plan download popup")
        clickChild(By.cssSelector(".plan-download-popup__close button"))
    }
}
