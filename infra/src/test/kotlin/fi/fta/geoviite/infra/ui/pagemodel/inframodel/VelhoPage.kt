package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.pagemodel.map.LocationTrackListItem
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class VelhoPage(headers: List<String>) : TableModel<VelhoListItem>(
    listBy = By.xpath("//div[@qa-id='main-content-container']"),
    itemsBy = By.cssSelector("div.projektivelho-file-list table tbody tr"),
    getContent = { i: Int, e: WebElement -> VelhoListItem(i, e, headers) },
) {
    fun goToInfraModelList(): InfraModelPage {
        childElement(byQaId("infra-model-nav-tab-plan")).click()
        return InfraModelPage()
    }

    fun openWaitingForApprovalList(): VelhoPage = apply {
        childElement(byQaId("infra-model-nav-tab-waiting")).click()
    }

    fun openRejectedList(): VelhoPage = apply {
        childElement(byQaId("infra-model-nav-tab-rejected")).click()
    }

    fun rejectFirstMatching(by: (item: VelhoListItem) -> Boolean) = apply {
        clickOnItemBy(by, byQaId("pv-reject-button"))
    }

    fun restoreFirstMatching(by: (item: VelhoListItem) -> Boolean) = apply {
        clickOnItemBy(by, byQaId("pv-restore-button"))
    }

    fun acceptFirstMatching(by: (item: VelhoListItem) -> Boolean): InfraModelUploadAndEditForm {
        clickOnItemBy(by, byQaId("pv-import-button"))
        return InfraModelUploadAndEditForm()
    }
}

data class VelhoListItem(
    override val index: Int,
    val row: WebElement,
    override val headers: List<String>,
) : TableRowItem(index, row, headers) {
    fun getProjectName() = getColumnContent("projektivelho.project-name")
    fun getDocumentName() = getColumnContent("projektivelho.document-name")
    fun getDocumentDescription() = getColumnContent("projektivelho.document-description")
    fun getDocumentModified() = getColumnContent("projektivelho.document-modified")
}
