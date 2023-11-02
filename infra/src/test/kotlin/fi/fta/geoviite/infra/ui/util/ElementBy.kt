package fi.fta.geoviite.infra.ui.util

import org.openqa.selenium.By


fun byQaId(qaId: String): By = By.cssSelector("[qa-id='$qaId']")

@Deprecated("Use qaIds instead", ReplaceWith("byQaId(qaId)"))
fun byText(content: String): By = By.xpath(".//span[text() = '$content']")
