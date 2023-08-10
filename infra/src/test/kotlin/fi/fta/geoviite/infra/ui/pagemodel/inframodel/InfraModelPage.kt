package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import clearInput
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import getElementWhenVisible
import getElementsWhenVisible
import org.openqa.selenium.By
import waitUntilElementClickable
import kotlin.test.assertTrue

class InfraModelPage : PageModel(By.xpath("//div[@qa-id='main-content-container']")) {
    private val SAVE_BUTTON = By.xpath("//button[span[contains(text(),'Tallenna')]]")
    private val LIST_STATUS = By.xpath("//div[@class='infra-model-list__search-result']/div")

    fun lataaUusi(file: String): InfraModelUploadAndEditForm {
        logger.info("Upload IM file $file")
        getElementWhenExists(By.className("file-input__file-input")).sendKeys(file)

        waitUntilElementClickable(SAVE_BUTTON)

        return InfraModelUploadAndEditForm()
    }

    fun infraModelList(): InfraModelTable {
        childElement(byQaId("infra-model-nav-tab-plan")).click()
        return infraModelTable(By.className("infra-model-list-search-result__table"))
    }

    fun openInfraModel(infraModelFileName: String): InfraModelUploadAndEditForm {

        assertTrue(getElementsWhenVisible(By.xpath("//tbody[@id='infra-model-list-search-result__table-body']/tr")).isNotEmpty())

        infraModelList().selectItemWhenMatches { it.tiedostonimi() == infraModelFileName }

        return InfraModelUploadAndEditForm()
    }

    fun search(query: String) {
        logger.info("Search '$query'")
        waitChildVisible(By.className("infra-model-list-search-result__table"))
        val searchField =
            getElementWhenVisible(By.xpath("//div[@class='infra-model-search-form__auto-complete']//input"))
        clearInput(searchField)
        searchField.sendKeys(query)
    }

    fun openVelhoWaitingForApprovalList(): VelhoPage {
        childElement(byQaId("infra-model-nav-tab-waiting")).click()
        waitChildVisible(By.cssSelector("div.projektivelho-file-list"))
        val qaHeaders =
            childElements(By.cssSelector("div.projektivelho-file-list thead tr th")).map { it.getAttribute("qa-id") }
        return VelhoPage(qaHeaders)
    }

}
