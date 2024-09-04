package fi.fta.geoviite.infra.ui.pagemodel.map

import childExists
import fi.fta.geoviite.infra.publication.PublicationGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EMenu
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETable
import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EChangePreviewTable(tableBy: By) : E2ETable<E2EChangePreviewRow>(tableBy, By.cssSelector("tbody tr")) {
    val errorRows: List<E2EChangePreviewRow>
        get() = rows.filter { it.state == E2EChangePreviewRow.State.ERROR }

    val warningRows: List<E2EChangePreviewRow>
        get() = rows.filter { it.state == E2EChangePreviewRow.State.WARNING }

    fun hasErrors(): Boolean = errorRows.isNotEmpty()

    fun hasWarnings(): Boolean = warningRows.isNotEmpty()

    fun stageChange(change: E2EChangePreviewRow): E2EChangePreviewTable = apply {
        logger.info("Stage change $change")

        selectBy(change, byQaId("stage-change-button"))
    }

    fun revertChange(change: E2EChangePreviewRow): E2EChangePreviewTable = apply {
        logger.info("Revert change $change")

        openMenu(change)
        getElementWhenExists(byQaId("preview-revert-change")).click()

        E2EPreviewChangesSaveOrDiscardDialog().reject()
    }

    fun openMenu(change: E2EChangePreviewRow): E2EMenu {
        logger.info("Open more menu for change $change")

        selectBy(change, byQaId("menu-button"))
        return E2EMenu()
    }

    fun closeMenu(change: E2EChangePreviewRow) {
        logger.info("Close more menu for change $change")

        selectBy(change, byQaId("menu-button"))
    }

    fun movePublicationGroup(publicationGroup: PublicationGroup): E2EChangePreviewTable = apply {
        rows.find { row ->
            val rowMenu = openMenu(row)

            val movePublicationGroupMenuItem =
                rowMenu.items.firstOrNull { menuItem ->
                    val menuItemQaId = menuItem.element?.getAttribute("qa-id")
                    menuItemQaId == "preview-move-publication-group-${publicationGroup.id}"
                }

            movePublicationGroupMenuItem?.let { item ->
                item.element?.click()
                true
            }
                ?: run {
                    closeMenu(row)
                    false
                }
        }
    }

    override fun getRowContent(row: WebElement): E2EChangePreviewRow {
        return E2EChangePreviewRow(row, row.findElements(By.tagName("td")), headerElements)
    }
}

data class E2EChangePreviewRow(val name: String, val trackNumber: String, val state: State) {
    enum class State {
        OK,
        WARNING,
        ERROR,
    }

    constructor(
        row: WebElement,
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        name = getColumnContent("preview-table.change-target", columns, headers),
        trackNumber = getColumnContent("preview-table.track-number-short", columns, headers),
        state =
            if (row.childExists(By.className("preview-table-item__error-status"))) State.ERROR
            else if (row.childExists(By.className("preview-table-item__warning-status"))) State.WARNING else State.OK,
    )
}
