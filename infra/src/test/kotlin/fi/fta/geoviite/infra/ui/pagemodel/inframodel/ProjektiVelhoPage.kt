package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EProjektiVelhoPage :
    E2ETable<E2EProjektiVelhoListItem>(
        tableBy = byQaId("main-content-container"),
        rowsBy = By.cssSelector(".projektivelho-file-list table tbody tr"),
    ) {
    fun goToInfraModelList(): E2EInfraModelPage {
        clickChild(byQaId("infra-model-nav-tab-plan"))
        return E2EInfraModelPage()
    }

    fun openWaitingForApprovalList(): E2EProjektiVelhoPage = apply { clickChild(byQaId("infra-model-nav-tab-waiting")) }

    fun openRejectedList(): E2EProjektiVelhoPage = apply { clickChild(byQaId("infra-model-nav-tab-rejected")) }

    fun rejectFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean) = apply {
        clickChild(byQaId("pv-reject-button"))
        waitAndClearToast("reject-success")
    }

    fun restoreFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean) = apply {
        clickChild(byQaId("pv-restore-button"))
        waitAndClearToast("restore-success")
    }

    fun acceptFirstMatching(by: (item: E2EProjektiVelhoListItem) -> Boolean): E2EInfraModelForm {
        clickChild(byQaId("pv-import-button"))
        return E2EInfraModelForm()
    }

    override fun getRowContent(row: WebElement): E2EProjektiVelhoListItem {
        return E2EProjektiVelhoListItem(row.findElements(By.tagName("td")), headerElements)
    }
}

data class E2EProjektiVelhoListItem(
    val projectName: String,
    val documentName: String,
    val documentDescription: String,
    val documentModified: String,
) {
    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        projectName = getColumnContent("projektivelho.project-name", columns, headers),
        documentName = getColumnContent("projektivelho.document-name", columns, headers),
        documentDescription = getColumnContent("projektivelho.document-description", columns, headers),
        documentModified = getColumnContent("projektivelho.document-modified", columns, headers),
    )
}
