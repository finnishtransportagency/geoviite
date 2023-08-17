package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import browser
import fi.fta.geoviite.infra.ui.pagemodel.common.DialogPopUp
import fi.fta.geoviite.infra.ui.pagemodel.common.FormGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.TableModel
import fi.fta.geoviite.infra.ui.pagemodel.common.TableRowItem
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateFromString
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateTimeFromString
import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.time.LocalDateTime


fun infraModelTable(tableRoot: By): InfraModelTable {
    val headers =
        browser().findElement(tableRoot).findElements(By.cssSelector("thead th")).map { it.getAttribute("qa-id") }
    return InfraModelTable(tableRoot, headers)
}

class InfraModelTable(tableRoot: By, headers: List<String>) : TableModel<InfraModelRow>(
    listBy = tableRoot,
    itemsBy = By.cssSelector("tbody tr"),
    getContent = { i, e -> InfraModelRow(i, headers, e) }) {
    private val headerElement: WebElement get() = childElement(By.xpath("//table/thead/tr"))

    fun sortBy(colName: String) {
        logger.info("Select column $colName")
        headerElement.findElement(By.xpath(".//*[text() = '$colName']")).click()
    }
}

// TODO: GVT-1947 code language
class InfraModelRow(index: Int, headers: List<String>, row: WebElement) : TableRowItem(index, row, headers) {
    fun projektinNimi(): String = getColumnContent("im-form.name-header")
    fun tiedostonimi(): String = getColumnContent("im-form.file-name-header")
    fun ratanumero(): String = getColumnContent("im-form.track-number-header")
    fun alkukilometri(): String = getColumnContent("im-form.km-start-header")
    fun loppukilometri(): String = getColumnContent("im-form.km-end-header")
    fun suunnitelmavaihe(): String = getColumnContent("im-form.plan-phase-header")
    fun paatos(): String = getColumnContent("im-form.decision-phase-header")
    fun laadittu(): LocalDateTime = localDateFromString(getColumnContent("im-form.created-at-header"))
    fun ladattu(): LocalDateTime = localDateTimeFromString(getColumnContent("im-form.uploaded-at-header"))
    fun linkitetty(): LocalDateTime = localDateTimeFromString(getColumnContent("im-form.linked-at-header"))

    override fun toString(): String = ToStringBuilder.reflectionToString(this)
}

class ProjektinTiedotFromGroup(by: By) : FormGroup(by) {
    fun nimi() = fieldValue("Projektin nimi")
    fun suunnitteluYritys() = fieldValue("Suunnitteluyritys")

    fun addNimi(input: String) {
        changeToNewDropDownValue("Projektin nimi", listOf(input)).assertAndClose("Uusi projekti luotu")
    }

    fun addNewSuunnitteluyritys(input: String) {
        changeToNewDropDownValue("Suunnitteluyritys", listOf(input)).assertAndClose("Uusi suunnitteluyritys luotu")
    }
}

class SijaintitiedotFormGroup(by: By) : FormGroup(by) {
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun ratakilometrivali(): String = fieldValue("Ratakilometriväli")
    fun koordinaattijarjestelma(): String = fieldValue("Koordinaattijärjestelmä")
    fun korkeusjarjestelma(): String = fieldValue("Korkeusjärjestelmä")

    fun editRatanumero(ratanumero: String) {
        changeFieldDropDownValues("Ratanumero", listOf(ratanumero))
        clickEditIcon("Ratanumero")
    }

    fun addRatanumero(ratanumero: String, kuvaus: String) {
        changeToNewDropDownValue("Ratanumero", listOf(ratanumero, kuvaus)).assertAndClose("Ratanumero tallennettu")
        clickEditIcon("Ratanumero")
    }

    fun editKorkeusjarjestelma(korkeusjarjestelma: String) =
        changeFieldDropDownValues("Korkeusjärjestelmä", listOf(korkeusjarjestelma))


    fun editKoordinaattijarjestelma(koordinattijarjestelma: String) =
        changeFieldDropDownValues("Koordinaattijärjestelmä", listOf(koordinattijarjestelma))

}

class VaiheJaLaatutiedotFormGroup(by: By) : FormGroup(by) {
    fun suunnitteluvaihe(): String = fieldValue("Suunnitteluvaihe")
    fun vaiheenTarkennus(): String = fieldValue("Vaiheen tarkennus")
    fun laatu(): String = fieldValue("Laatu")
    fun editSuunnitteluvaihe(input: String) = changeFieldDropDownValues("Suunnitteluvaihe", listOf(input))
    fun editVaiheenTarkennus(input: String) = changeFieldDropDownValues("Vaiheen tarkennus", listOf(input))
    fun editLaatu(input: String) = changeFieldDropDownValues("Laatu", listOf(input))
}

class LokiJaLinkitystiedotFormGroup(by: By) : FormGroup(by) {
    fun laadittu(): String = fieldValue("Laadittu")
    fun editLaadittu(kuukausi: String, vuosi: String) = changeFieldDropDownValues("Laadittu", listOf(kuukausi, vuosi))
}

class ConfirmDialog : DialogPopUp() {
    fun tallenna() {
        clickButtonByText("Tallenna")
    }
}
