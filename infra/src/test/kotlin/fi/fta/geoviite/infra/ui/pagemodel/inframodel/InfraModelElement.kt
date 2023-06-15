package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.DialogPopUp
import fi.fta.geoviite.infra.ui.pagemodel.common.FormGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.TableRow
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateFromString
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateTimeFromString
import getChildElements
import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import java.time.LocalDateTime


// TODO: GVT-1936 Use generic table model to handle this (modeling after ListModel)
class InfraModelTable(tableRoot: By): PageModel(tableRoot) {
    private val headerElement: WebElement get() = childElement(By.xpath("//table/thead/tr"))

    // TODO: GVT-1936 This should not wait at all but only get the current listing
    //  If the caller wants to wait for some specific data to appear, it has to have a different condition anyhow
    private fun rowElements(): List<WebElement> = try {
        webElement.getChildElements(
            By.xpath("//tbody[@id='infra-model-list-search-result__table-body']/tr"),
        )
    } catch (ex: TimeoutException) {
        emptyList()
    }

    fun infraModelRows(): List<InfraModelRow> {
        logger.info("Get Infra Model rows")
        return rowElements().map { rowElement -> InfraModelRow(headerElement.findElements(By.tagName("th")).map {it.text}, rowElement) }
    }

    fun sortBy(colName: String) {
        logger.info("Select column $colName")
        headerElement.findElement(By.xpath(".//*[text() = '$colName']")).click()
    }
}

// TODO: GVT-1947 code language
class InfraModelRow(headers: List<String>, row: WebElement) : TableRow(headers, row) {
    fun projektinNimi(): String = getColumnByName("Projektin nimi").text
    fun tiedostonimi(): String = getColumnByName("Nimi").text
    fun ratanumero(): String = getColumnByName("Ratanumero").text
    fun alkukilometri() = getColumnByName("Alkukm").text
    fun loppukilometri() = getColumnByName("Loppukm").text
    fun suunnitelmavaihe(): String = getColumnByName("Suunnitelmavaihe").text
    fun paatos(): String = getColumnByName("Päätös").text
    fun laadittu(): LocalDateTime = localDateFromString(getColumnByName("Laadittu").text)
    fun ladattu(): LocalDateTime = localDateTimeFromString(getColumnByName("Ladattu").text)
    fun linkitetty(): LocalDateTime = localDateTimeFromString(getColumnByName("Linkitetty").text)
    fun editInfraModel() = clickRow()

    override fun toString(): String = ToStringBuilder.reflectionToString(this)
}

class ProjektinTiedotFromGroup(by: By) : FormGroup(by) {
    fun nimi() = fieldValue("Projektin nimi")
    fun suunnitteluYritys() = fieldValue("Suunnitteluyritys")

    fun addNimi(input: String) {
        changeToNewDropDownValue("Projektin nimi", listOf(input))
            .assertAndClose("Uusi projekti luotu")
    }

    fun addNewSuunnitteluyritys(input: String) {
        changeToNewDropDownValue("Suunnitteluyritys", listOf(input))
            .assertAndClose("Uusi suunnitteluyritys luotu")
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
        changeToNewDropDownValue("Ratanumero", listOf(ratanumero, kuvaus))
            .assertAndClose("Ratanumero tallennettu")
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

class ConfirmDialog(): DialogPopUp() {
    fun tallenna() {
        clickButtonByText("Tallenna")
    }
}
