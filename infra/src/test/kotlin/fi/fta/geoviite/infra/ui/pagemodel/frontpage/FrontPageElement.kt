package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import fi.fta.geoviite.infra.ui.pagemodel.common.getColumnContent
import org.openqa.selenium.WebElement

data class E2EPublicationDetailRow(
    val name: String,
    val message: String,
    val trackNumbers: String,
    val pushedToRatko: String,
) {

    constructor(
        columns: List<WebElement>,
        headers: List<WebElement>,
    ) : this(
        name = getColumnContent("publication-table.name", columns, headers),
        message = getColumnContent("publication-table.message", columns, headers),
        trackNumbers = getColumnContent("publication-table.track-number", columns, headers),
        pushedToRatko = getColumnContent("publication-table.pushed-to-ratko", columns, headers),
    )
}
