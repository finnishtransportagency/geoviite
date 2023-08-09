package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.WebElement

class TextField(fetch: () -> WebElement) : PageModel(fetch) {
    val text: String get() = webElement.text
}
