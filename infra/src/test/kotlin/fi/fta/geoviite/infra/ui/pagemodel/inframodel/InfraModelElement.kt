package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.DialogPopUp
import fi.fta.geoviite.infra.ui.pagemodel.common.FormGroup
import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import fi.fta.geoviite.infra.ui.pagemodel.common.TableRow
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateFromString
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.localDateTimeFromString
import org.apache.commons.lang3.builder.ToStringBuilder
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import java.time.Duration
import java.time.LocalDateTime


class InfraModelTable(tableRoot: By): PageModel(tableRoot) {
    private val headerElement: WebElement get() = childElement(By.xpath("//table/thead/tr"))

    private fun rowElements(): List<WebElement> = try {
        childElements(
            By.xpath("//tbody[@id='infra-model-list-search-result__table-body']/tr"),
            Duration.ofSeconds(2),
        )
    } catch (ex: TimeoutException) {
        emptyList()
    }

    // TODO: These list elements hold a reference to the WebElement, risking staleness
    //  See PublicationList for an example on how to handle lists
    //  In general: the list object should handle actions and row data can be given out as immutable data classes that don't know the WebElement
    fun infraModelRows(): List<InfraModelRow> {
        logger.info("Get Infra Model rows")
        return rowElements().map { rowElement -> InfraModelRow(headerElement.findElements(By.tagName("th")).map {it.text}, rowElement) }
    }

    fun sortBy(colName: String) {
        logger.info("Select column $colName")
        headerElement.findElement(By.xpath(".//*[text() = '$colName']")).click()
    }
}

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
    fun oid() = fieldValue("Projektin OID")
    fun suunnitteluYritys() = fieldValue("Suunnitteluyritys")

    fun addNimi(input: String) {
        changeToNewDropDownValue("Projektin nimi", listOf(input))
    }

    fun addOid(input: String) {
        clickEditIcon("Projektin OID")
        val element = fieldValueElement("Projektin OID")

        element.findElement(By.cssSelector("div.text-field__input input")).sendKeys(input)
        clickEditIcon("Projektin OID")
    }

    fun addNewSuunnitteluyritys(input: String) {
        changeToNewDropDownValue("Suunnitteluyritys", listOf(input))
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
