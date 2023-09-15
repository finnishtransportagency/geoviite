package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EInfraModelPage : E2EViewFragment(By.className("infra-model-main")) {

    val infraModelsList: E2EInfraModelTable by lazy {
        childComponent(
            By.className("infra-model-list-search-result__table"), ::E2EInfraModelTable
        )
    }

    fun upload(file: String): E2EInfraModelForm {
        logger.info("Upload IM file $file")
        childElement(By.className("file-input__file-input")).sendKeys(file)

        return waitForInfraModelForm()
    }

    fun openInfraModel(fileName: String): E2EInfraModelForm {
        infraModelsList.selectItemWhenMatches { it.fileName == fileName }

        return waitForInfraModelForm()
    }

    fun search(query: String): E2EInfraModelPage = apply {
        logger.info("Search '$query'")
        waitChildVisible(By.className("infra-model-list-search-result__table"))
        childTextInput(By.cssSelector(".infra-model-search-form__auto-complete input")).clear().inputValue(query)
    }

    fun openVelhoWaitingForApprovalList(): E2EProjektiVelhoPage {
        clickButton(byQaId("infra-model-nav-tab-waiting"))

        waitChildVisible(By.className("projektivelho-file-list"))
        return E2EProjektiVelhoPage()
    }

    private fun waitForInfraModelForm(): E2EInfraModelForm {
        waitChildVisible(By.className("infra-model-upload__form-column"))
        return E2EInfraModelForm()
    }

}
