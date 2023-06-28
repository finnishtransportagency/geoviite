package fi.fta.geoviite.infra.ui.util

import getChildWhenVisible
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

fun byQaId(qaId: String): By = By.cssSelector("[qa-id='$qaId']")

fun byText(content: String): By = By.xpath(".//span[text() = '$content']")

fun fetch(by: By): () -> WebElement = { getElementWhenVisible(by) }
fun fetch(parentFetch: () -> WebElement, by: By): () -> WebElement = { getChildWhenVisible(parentFetch, by) }
