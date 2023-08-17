package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.Toaster
import getElementsWhenVisible
import org.openqa.selenium.By
import waitUntilVisible

class InfraModelUploadAndEditForm : PageModel(By.className("infra-model-upload__form-column")) {
    fun tallenna(expectConfirm: Boolean = true) {
        val projectName = projektinTiedot().nimi()

        logger.info("Saving infra model to database...")
        clickButtonByText("Tallenna")
        if (expectConfirm) confirmSaving()
        Toaster(By.className("infra-model-import-upload__success-toast")).close()
    }

    fun tallennaMuutokset() {
        clickButtonByText("Tallenna muutokset")
        confirmSaving()
    }

    fun projektinTiedot() = ProjektinTiedotFromGroup(By.xpath("//div[@qa-id='im-form-project']"))

    fun sijaintitiedot() = SijaintitiedotFormGroup(By.xpath("//div[@qa-id='im-form-location']"))

    fun tilanneJaLaatutiedot() = VaiheJaLaatutiedotFormGroup(By.xpath("//div[@qa-id='im-form-phase-quality']"))

    fun lokiJaLinkitystiedot() = LokiJaLinkitystiedotFormGroup(By.xpath("//div[@qa-id='im-form-log']"))

    fun puutteellisetTiedot(): List<String> =
        getElementsWhenVisible(By.className("infobox__row")).map { element -> element.text }

    private fun confirmSaving() {
        //Confirm saving if confirmation dialog appears
        ConfirmDialog().tallenna()
    }

}
