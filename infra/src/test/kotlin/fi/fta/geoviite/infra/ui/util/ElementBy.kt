package fi.fta.geoviite.infra.ui.util

import org.openqa.selenium.By

fun byQaId(qaId: String): By = By.cssSelector("[qa-id='$qaId']")
