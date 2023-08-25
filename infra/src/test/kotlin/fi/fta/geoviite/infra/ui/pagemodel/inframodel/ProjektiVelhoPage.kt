package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContentByAttr
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EProjektiVelhoPage : E2ETable<E2EProjektiVelhoListItem>(
    tableFetch = fetch(byQaId("main-content-container")),
    rowsBy = By.cssSelector(".projektivelho-file-list table tbody tr"),
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
    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        projectName = getColumnContentByAttr("projektivelho.project-name", columns, headers),
        documentName = getColumnContentByAttr("projektivelho.document-name", columns, headers),
        documentDescription = getColumnContentByAttr("projektivelho.document-description", columns, headers),
        documentModified = getColumnContentByAttr("projektivelho.document-modified", columns, headers),
    )
}
