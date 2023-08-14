package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETableRow
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EProjektiVelhoPage(headers: List<String>) : E2ETable<E2EProjektiVelhoListItem>(
    tableFetch = fetch(byQaId("main-content-container")),
    rowsBy = By.cssSelector(".projektivelho-file-list table tbody tr"),
    getContent = { i, e -> E2EProjektiVelhoListItem(i, e, headers) },
) {
    fun goToInfraModelList(): E2EInfraModelPage {
        clickChild(byQaId("infra-model-nav-tab-plan"))
        return E2EInfraModelPage()
    }
    
    fun openWaitingForApprovalList(): E2EProjektiVelhoPage = apply {
        clickChild(byQaId("infra-model-nav-tab-waiting"))
    }

    fun openRejectedList(): E2EProjektiVelhoPage = apply {
        clickChild(byQaId("infra-model-nav-tab-rejected"))
    }

    fun rejectFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean) = apply {
        clickChild(byQaId("pv-reject-button"))
    }

    fun restoreFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean) = apply {
        clickChild(byQaId("pv-restore-button"))
    }

    fun acceptFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean): E2EInfraModelForm {
        clickChild(byQaId("pv-import-button"))
        return E2EInfraModelForm()
    }
}

class E2EProjektiVelhoListItem(
    override val index: Int,
    row: WebElement,
    override val headers: List<String>,
) : E2ETableRow(index, row, headers) {
    fun getProjectName() = getColumnContent("projektivelho.project-name")
    fun getDocumentName() = getColumnContent("projektivelho.document-name")
    fun getDocumentDescription() = getColumnContent("projektivelho.document-description")
    fun getDocumentModified() = getColumnContent("projektivelho.document-modified")
}
