package fi.fta.geoviite.infra.ui.pagemodel

import getElementWhenVisible
import org.openqa.selenium.By

class E2EVaylaLoginPage {

    fun login(username: String, password: String) {
        getElementWhenVisible(By.id("username")).sendKeys(username)
        getElementWhenVisible(By.id("password")).sendKeys(password)

        getElementWhenVisible(By.className("submit")).click()
    }

}
