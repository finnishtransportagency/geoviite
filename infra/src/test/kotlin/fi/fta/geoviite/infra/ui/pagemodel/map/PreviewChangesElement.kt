package fi.fta.geoviite.infra.ui.pagemodel.map

import childExists
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EMenu
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EMenuItem
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContentByText
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import getChildElements
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EChangePreviewTable(
    tableFetch: ElementFetch,
) : E2ETable<E2EChangePreviewRow>(tableFetch, By.cssSelector("tbody tr")) {
    val errorRows: List<E2EChangePreviewRow> get() = rows.filter { it.state == E2EChangePreviewRow.State.ERROR }

    val warningRows: List<E2EChangePreviewRow> get() = rows.filter { it.state == E2EChangePreviewRow.State.WARNING }

    fun hasErrors(): Boolean = errorRows.isNotEmpty()

    fun hasWarnings(): Boolean = warningRows.isNotEmpty()

    fun stageChange(change: E2EChangePreviewRow): E2EChangePreviewTable = apply {
        selectBy(change, byQaId("stage-change-button"))
    }

    fun revertChange(change: E2EChangePreviewRow): E2EChangePreviewTable = apply {
        openMenu(change)
            .select(E2EMenuItem("Hylkää muutos"))
        E2EPreviewChangesSaveOrDiscardDialog().reject()
    }

    fun openMenu(change: E2EChangePreviewRow): E2EMenu {
        selectBy(change, byQaId("menu-button"))
        return E2EMenu()
    }

    override fun getRowContent(row: WebElement): E2EChangePreviewRow {
        return E2EChangePreviewRow(row, row.getChildElements(By.tagName("td")), headerElements)
    }
}

data class E2EChangePreviewRow(
    val name: String,
    val trackNumber: String,
    val state: State,
) {
    enum class State { OK, WARNING, ERROR }

    constructor(row: WebElement, columns: List<WebElement>, headers: List<WebElement>) : this(
        name = getColumnContentByText("Muutoskohde", columns, headers),
        trackNumber = getColumnContentByText("Ratanro", columns, headers),
        state = if (row.childExists(By.className("preview-table-item__error-status"))) State.ERROR
        else if (row.childExists(By.className("preview-table-item__warning-status"))) State.WARNING
        else State.OK
    )

}
