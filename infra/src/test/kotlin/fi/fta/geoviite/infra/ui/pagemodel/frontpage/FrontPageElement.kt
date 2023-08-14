package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETableRow
import org.openqa.selenium.WebElement

class E2EPublicationDetailRow(
    override val index: Int,
    row: WebElement,
    override val headers: List<String>,
) : E2ETableRow(index = index, row = row, headers = headers) {

    val name = getColumnContent("Muutoskohde")
    val trackNumbers = getColumnContent("Ratanro")
    val pushedToRatko = getColumnContent("Viety Ratkoon")
}
