package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement

open class E2EMenu(menuBy: By = By.className("menu"), itemsBy: By = By.tagName("li")) :
    E2EList<E2EMenuItem>(menuBy, itemsBy) {
    override fun getItemContent(item: WebElement) = E2EMenuItem(item)
}

data class E2EMenuItem(val name: String, val element: WebElement?) {
    constructor(item: WebElement) : this(name = item.text, element = item)
}
