package fi.fta.geoviite.infra.ui.pagemodel

import browser
import org.openqa.selenium.By

class VaylaLoginPage {

    fun login(username: String, password: String) {
        val usernameField = browser().findElement(By.id("username"))
        usernameField.sendKeys(username)
        val passwordField = browser().findElement(By.id("password"))
        passwordField.sendKeys(password)
        browser().findElement(By.className("submit")).click()
    }

}
