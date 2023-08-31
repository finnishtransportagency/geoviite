package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import org.openqa.selenium.WebElement

data class E2EPublicationDetailRow(
    val name: String,
    val trackNumbers: String,
    val pushedToRatko: String,
) {

    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        name = getColumnContent("Muutoskohde", columns, headers),
        trackNumbers = getColumnContent("Ratanro", columns, headers),
        pushedToRatko = getColumnContent("Viety Ratkoon", columns, headers)
    )
}
