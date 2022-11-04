package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

class InfraModelPage: PageModel(By.xpath("//div[@qa-id='main-content-container']")) {

    private val SAVE_BUTTON = By.xpath("//button[span[contains(text(),'Tallenna')]]")
    private val LIST_STATUS = By.xpath("//div[@class='infra-model-list__search-result']/div")

    fun lataaUusi(file: String): InfraModelUploadAndEditForm {
        logger.info("Upload IM file $file")
        getElementWhenExists(By.className("file-input__file-input"))
        .sendKeys(file)

        WebDriverWait(browser(), Duration.ofSeconds(15))
            .until(ExpectedConditions.visibilityOfElementLocated(SAVE_BUTTON))
        WebDriverWait(browser(), Duration.ofSeconds(20))
            .until(ExpectedConditions.elementToBeClickable(SAVE_BUTTON))

        return InfraModelUploadAndEditForm()
    }

    fun infraModelList(): InfraModelTable {
        return InfraModelTable(By.className("infra-model-list-search-result__table"))
    }

    fun openInfraModel(infraModelFileName: String): InfraModelUploadAndEditForm {
        WebDriverWait(browser(), Duration.ofSeconds(5))
            .until(ExpectedConditions.numberOfElementsToBeMoreThan(By.xpath("//tbody[@id='infra-model-list-search-result__table-body']/tr") , 0))

        val rows =  infraModelList().infraModelRows()
        try {
            rows.first { it.tiedostonimi() == infraModelFileName }.clickRow()
        } catch (ex: java.util.NoSuchElementException) {
            logger.warn("Cannot find IM file $infraModelFileName! Available files are ${rows.map { it.tiedostonimi() }}")
        }

        return  InfraModelUploadAndEditForm()
    }

    fun search(query: String) {
        logger.info("Search '$query'")
        val oldSearchResults = getChildElementStaleSafe(By.className("infra-model-list-search-result__table"))
        val searchField = getElementWhenVisible(By.xpath("//div[@class='infra-model-search-form__auto-complete']//input"))
        clearInput(searchField)
        searchField.sendKeys(query)
    }

}
