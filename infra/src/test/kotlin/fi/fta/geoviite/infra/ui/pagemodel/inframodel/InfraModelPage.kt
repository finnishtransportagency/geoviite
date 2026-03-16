package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementIfExists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import waitUntilVisible

class E2EInfraModelPage : E2EViewFragment(By.className("infra-model-main")) {

    val infraModelsList: E2EInfraModelTable
        get() = childComponent(By.className("infra-model-list-search-result__table"), ::E2EInfraModelTable)

    fun upload(file: String): E2EInfraModelForm {
        logger.info("Upload infra model file $file")
        findElement(By.className("file-input__file-input")).sendKeys(file)

        return waitForInfraModelForm()
    }

    fun openInfraModel(fileName: String): E2EInfraModelForm {
        logger.info("Open infra model $fileName")

        infraModelsList.select { it.fileName == fileName }

        return waitForInfraModelForm()
    }

    fun search(query: String): E2EInfraModelPage = apply {
        logger.info("Search infra models $query")

        waitUntilChildVisible(By.className("infra-model-list-search-result__table"))
        childTextInput(By.cssSelector(".infra-model-search-form__auto-complete input")).replaceValue(query)

        infraModelsList.waitUntilReady()
    }

    fun openVelhoWaitingForApprovalList(): E2EProjektiVelhoPage {
        logger.info("Open Velho waiting tab")

        clickChild(byQaId("infra-model-nav-tab-waiting"))

        waitUntilChildVisible(By.className("projektivelho-file-list"))
        return E2EProjektiVelhoPage()
    }

    private fun waitForInfraModelForm(): E2EInfraModelForm {
        waitUntilChildVisible(By.className("infra-model-upload__form-column"))
        return E2EInfraModelForm()
    }

    val infraModelNavTabPlan: WebElement?
        get() =
            waitUntilVisible(byQaId("im-form.tabs-bar")).let { getElementIfExists(byQaId("infra-model-nav-tab-plan")) }

    val infraModelNavTabWaiting: WebElement?
        get() =
            waitUntilVisible(byQaId("im-form.tabs-bar")).let {
                getElementIfExists(byQaId("infra-model-nav-tab-waiting"))
            }

    val infraModelNavTabRejected: WebElement?
        get() =
            waitUntilVisible(byQaId("im-form.tabs-bar")).let {
                getElementIfExists(byQaId("infra-model-nav-tab-rejected"))
            }
}
