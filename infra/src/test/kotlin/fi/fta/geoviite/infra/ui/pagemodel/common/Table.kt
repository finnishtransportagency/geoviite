package fi.fta.geoviite.infra.ui.pagemodel.common

import org.openqa.selenium.By
import org.openqa.selenium.WebElement


open class TableRow (val headers: List<String>, val row: WebElement) {
    fun clickRow() = row.findElement(By.tagName("td")).click()
    protected fun getColumnByName(name: String): WebElement = row.findElements(By.ByTagName("td"))[colIndx(name)]
    private fun colIndx(name: String): Int = headers.indexOf(name).apply{ if (this == -1) throw RuntimeException("No such column $name")}
}

