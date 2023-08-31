package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContentByText
import org.openqa.selenium.WebElement

data class E2EPublicationDetailRow(
    val name: String,
    val trackNumbers: String,
    val pushedToRatko: String,
) {

    constructor(columns: List<WebElement>, headers: List<WebElement>) : this(
        name = getColumnContentByText("Muutoskohde", columns, headers),
        trackNumbers = getColumnContentByText("Ratanro", columns, headers),
        pushedToRatko = getColumnContentByText("Viety Ratkoon", columns, headers)
    )
}
