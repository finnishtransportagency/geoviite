package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

class InfraModelUploadAndEditForm(): PageModel(By.className("infra-model-upload__form-column")) {

    fun tallenna() {
        val projectName = projektinTiedot().nimi()

        clickButton("Tallenna")
        confirmSaving()
        logger.info("Saving infra model to database...")

        WebDriverWait(browser(), Duration.ofSeconds(20)).until(
            ExpectedConditions.visibilityOfElementLocated(By.xpath("//td[text() = '$projectName']")))
    }

    fun tallennaMuutokset() {
        clickButton("Tallenna muutokset")
        confirmSaving()
    }

    fun projektinTiedot() =
        ProjektinTiedotFromGroup(By.xpath("//div[@qa-id='im-form-project']"))

    fun sijaintitiedot() =
        SijaintitiedotFormGroup(By.xpath("//div[@qa-id='im-form-location']"))

    fun tilanneJaLaatutiedot() =
        VaiheJaLaatutiedotFormGroup(By.xpath("//div[@qa-id='im-form-phase-quality']"))

    fun lokiJaLinkitystiedot() =
        LokiJaLinkitystiedotFormGroup(By.xpath("//div[@qa-id='im-form-log']"))

    fun puutteellisetTiedot(): List<String> =
        getElementsWhenVisible(By.className("infobox__row")).map { element -> element.text }

    private fun confirmSaving() {
        //Confirm saving if confirmation dialog appears
        try {
            ConfirmDialog().tallenna()
        } catch (ex: TimeoutException) {
            logger.info("Confirm dialog did not appear")
        }
    }

}

