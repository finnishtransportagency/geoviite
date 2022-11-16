package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GeometryPlanGeneralInfoBox(by: By) : InfoBox(by) {
    init {
        //Object initializes too quickly and webelement is not stable/ready
        Thread.sleep(500)
    }

    fun projekti(): String = fieldValue("Projekti")
    fun luotu(): String = fieldValue("Luotu")
    fun laatu(): String = fieldValue("Laatu")
    fun vaihe(): String = fieldValue("Vaihe")
    fun vaiheenTarkennus(): String = fieldValue("Vaiheen tarkennus")
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun alkupKoordinaattijarstelma(): String = fieldValue("Alkuperäinen koordinaattijärjest.")
}

class LayoutKmPostGeneralInfoBox(by: By) : InfoBox(by) {
    fun tasakmpistetunnus(): String = fieldValueWhenNotEmpty("Tasakmpistetunnus")
    fun ratanumero(): String = fieldValueWhenNotEmpty("Ratanumero")
}

class LayoutKmPostLocationInfoBox(by: By): InfoBox(by) {
    fun sijainti() = fieldValue("Sijainti (km+m)")
    fun koordinaatit() = fieldValue("Koordinaatit (TM35FIN)")
}

class LocationTrackLocationInfobox(by: By) : InfoBox(by) {
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun alkusijainti(): String = fieldValue("Alkusijainti (km + m)")
    fun loppusijainti(): String = fieldValue("Loppusijainti (km + m)")
    fun alkupiste(): String = fieldValue("Alkupiste")
    fun loppupiste(): String = fieldValue("Loppupiste")

    fun todellinenPituus(): String = fieldValue("Todellinen pituus (m)")
    fun todellinenPituusDoube(): Double = Regex("[0-9.]*").find(todellinenPituus())?.value?.toDouble() ?: -1.0
    fun alkukoordinaatti(): String = fieldValue("Alkukoordinaatit TM35FIN")
    fun loppukoordinaatti(): String = fieldValue("Loppukoordinaatit TM35FIN")

    fun muokkaaAlkuLoppupistetta() {
        logger.info("Edit start/end point")
        clickButton("Lyhennä alkua ja/tai loppua")
        getButtonElement("Valmis") //Only ensures that the infobox has changed to edit mode
    }

    fun valmis(): Toaster{
        clickButton("Valmis")
        waitUntilElementIsStale(rootElement)
        refresRootElement()
        return waitAndGetToasterElement()
    }

    fun peruuta() =
        clickButton("Lopeta")
}

class LocationTrackGeneralInfoBox(by: By) : InfoBox(by) {
    fun tunniste(): String = fieldValue("Tunniste")
    fun sijainteraidetunnus(): String = fieldValue("Sijaintiraidetunnus")
    fun tila(): String = fieldValue("Tila")
    fun kuvaus(): String = fieldValue("Kuvaus")
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun openRatanumero() = fieldElement("Ratanumero").findElement(By.cssSelector("a")).click()

    fun muokkaaTietoja(): CreateEditLocationTrackDialog {
        editInfoBoxValues()
        return CreateEditLocationTrackDialog(rootElement)
    }

    fun kohdistaKartalla() = clickButton("Kohdista kartalla").also { Thread.sleep(500) }
}

class CreateEditLayoutSwitchDialog(val editedElement: WebElement): DialogPopUp() {

    enum class Tilakategoria(val uiText: String) {
        POISTUNUT_KOHDE("Poistunut kohde"),
        OLEMASSA_OLEVA_KOHDE("Olemassa oleva kohde")
    }
    val content = FormLayout(By.className("dialog__content"))

    fun editVaihdetunnus(vaihdetunnus: String) = this {
        content.changeFieldValue("Vaihdetunnus", vaihdetunnus)
    }

    fun editTilakategoria(tilakategoria: Tilakategoria) = this {
        content.changeFieldDropDownValue("Tilakategoria", tilakategoria.uiText)
    }

    fun tallenna(waitUntilRootIsStale: Boolean = true): Toaster {
        val isPoistunut = content.fieldValue("Tilakategoria") == Tilakategoria.POISTUNUT_KOHDE.uiText
        clickButton("Tallenna")

        if (isPoistunut) {
            logger.info("Confirm saving changes")
            DialogPopUp(By.cssSelector("div.dialog--dark div.dialog__popup")).clickPrimaryButton()
        }

        val toaster = waitAndGetToasterElement()
        Thread.sleep(1000) //switch list is unstable after deletion and there's no better fix for now
        return toaster
    }
}

class KmPostEditDialog(val editedElement: WebElement): DialogPopUp() {

    enum class TilaTyyppi(val uiText: String) {
        SUUNNITELTU("Suunniteltu"),
        KAYTOSSA("Käytössä"),
        KAYTOSTA_POISTETTU("Käytöstä poistettu"),
    }

    val content = FormLayout(By.className("dialog__content"))

    fun editTasakmpistetunnus(tasakmpistetunnus: String) = this {
        content.changeFieldValue("Tasakmpistetunnus", tasakmpistetunnus)}

    fun editTila(tilaTyyppi: TilaTyyppi) = this {
        content.changeFieldDropDownValue("Tila", tilaTyyppi.uiText)}

    fun tallenna(waitUntilRootIsStale: Boolean = true): Toaster {
        clickButton("Tallenna")
        if (waitUntilRootIsStale) waitUntilElementIsStale(editedElement)
        return PageModel.waitAndGetToasterElement()
    }

    fun poistu() = clickSecondaryButton()
}

class CreateEditLocationTrackDialog(val editedElement: WebElement): DialogPopUp() {
    enum class TilaTyyppi(val uiText: String) {
        KAYTOSSA("Käytössä"),
        KAYTOSTA_POISTETTU("Käytöstä poistettu"),
        POISTETTU("Poistettu")
    }

    enum class RaideTyyppi(val uiText: String) {
        PAARAIDE("Pääraide"),
        SIVURAIDE("Sivuraide"),
        TURVARAIDE("Turvaraide"),
        KUJARAIDE("Kujaraide"),
        PITUUSMITTAUSLINJA("Pituusmittauslinja")
    }

    enum class TopologinenKytkeytyminen(val uiText: String) {
        EI_KYTKETTY("Ei kytketty"),
        RAITEEN_ALKU("Raiteen alku"),
        RAITEEN_LOPPU("Raiteen loppu"),
        RAITEEN_ALKU_JA_LOPPU("Raiteen alku ja loppu")
    }

    val content = FormLayout(By.className("dialog__content"))

    fun editSijaintiraidetunnus(sijaintiraidetunnus: String) = this {
        content.changeFieldValue("Sijaintiraidetunnus", sijaintiraidetunnus) }

    fun editExistingRatanumero(ratanumero: String) = this {
        content.changeFieldDropDownValue("Ratanumero", ratanumero)}

    fun editTila(tilaTyyppi: TilaTyyppi) = this {
        content.changeFieldDropDownValue("Tila", tilaTyyppi.uiText) }

    fun editRaidetyyppi(raideTyyppi: RaideTyyppi) = this {
        content.changeFieldDropDownValue("Raidetyyppi", raideTyyppi.uiText) }

    fun editKuvaus(kuvaus: String) = this {
        content.changeFieldValue("Kuvaus", kuvaus) }

    fun editTopologinenKytkeytyminen(topologinenKytkeytyminen: TopologinenKytkeytyminen) = this {
        content.changeFieldDropDownValue(fieldLabel = "Topologinen kytkeytyminen", topologinenKytkeytyminen.uiText) }

    fun tallenna(waitUntilRootIsStale: Boolean = true): Toaster {
        val isPoistettu = content.fieldValue("Tila") == TilaTyyppi.POISTETTU.uiText
        clickButton("Tallenna")
        try {
            if (waitUntilRootIsStale) waitUntilElementIsStale(element = editedElement, timeoutSeconds = 1)
        } catch (ex: TimeoutException) {
            logger.warn("Root dialog never became stale or stabilized too fast")
        }

        if (isPoistettu) {
            DialogPopUp().clickPrimaryWarningButton()
        }

        val toaster = waitAndGetToasterElement()
        Thread.sleep(200) //location track list is unstable after deletion and there's no better fix for now
        return toaster
    }

    fun poistu() = clickSecondaryButton()
}


class LocationTrackLogInfoBox(by: By) : InfoBox(by) {
    fun luotu(): String = fieldValue("Luotu")
    fun muokattu(): String = fieldValue("Muokattu")
}

class TrackNumberGeneralInfoBox(by: By) : InfoBox(by) {
    fun oid(): String = fieldValue("OID")
    fun tunnus(): String = fieldValue("Ratanumerotunnus")
    fun tila(): String = fieldValue("Tila")
    fun kuvaus(): String = fieldValue("Ratanumeron kuvaus")
    fun muokkaaTietoja(): CreateEditTrackNumberDialog {
        editInfoBoxValues()
        return CreateEditTrackNumberDialog(rootElement)
    }
}

class CreateEditTrackNumberDialog(val editedElement: WebElement): DialogPopUp() {
    enum class TilaTyyppi(val uiText: String) {
        KAYTOSSA("Käytössä"),
        KAYTOSTA_POISTETTU("Käytöstä poistettu"),
        POISTETTU("Poistettu")
    }

    val content = FormLayout(By.className("dialog__content"))

    fun editTunnus(tunnus: String) = this {
        content.changeFieldValue("Tunnus", tunnus)
    }

    fun editTila(tilaTyyppi: TilaTyyppi) = this{
        content.changeFieldDropDownValue("Tila", tilaTyyppi.uiText)
    }

    fun editKuvaus(kuvaus: String) = this {
        content.changeFieldValue("Kuvaus", kuvaus)
    }

    fun tallenna(waitUntilRootIsStale: Boolean = true): Toaster {
        val isPoistettu = content.fieldValue("Tila") == CreateEditLocationTrackDialog.TilaTyyppi.POISTETTU.uiText
        clickButton("Tallenna")
        try {
            if (waitUntilRootIsStale) waitUntilElementIsStale(element = editedElement, timeoutSeconds = 1)
        } catch (ex: TimeoutException) {
            logger.warn("Root dialog never became stale or stabilized too fast")
        }

        if (isPoistettu) {
            logger.info("Confirm saving changes")
            DialogPopUp(By.cssSelector("div.dialog--dark div.dialog__popup")).clickPrimaryButton()
        }

        val toaster = waitAndGetToasterElement()
        Thread.sleep(400) //reference line list is unstable after deletion and there's no better fix for now
        return toaster
    }
}

class ReferenceLineLocationInfoBox(by: By) : InfoBox(by) {
    fun alkusijainti(): String = fieldValue("Alkusijainti (km + m)")
    fun loppusijainti(): String = fieldValue("Loppusijainti (km + m)")
    fun todellinenPituus(): String = fieldValue("Todellinen pituus (m)")
    fun alkukoordinaatti(): String = fieldValue("Alkukoordinaatit TM35FIN")
    fun loppukoordinaatti(): String = fieldValue("Loppukoordinaatit TM35FIN")
    fun kohdistaKartalla() = clickButton("Kohdista kartalla").also { Thread.sleep(500) }
}

class TrackNumberLogInfoBox(by: By) : InfoBox(by) {
    fun luotu(): String = fieldValue("Luotu")
    fun muokattu(): String = fieldValue("Muokattu")
}

class LayoutSwitchGeneralInfoBox(by: By) : InfoBox(by) {
    fun vaihdetunnus(): String = fieldValue("Vaihdetunnus")
    fun oidTunnus(): String = fieldValue("OID")
    fun tila(): String = fieldValue("Tila")
    fun muokkaaTietoja(): CreateEditLayoutSwitchDialog {
        editInfoBoxValues()
        return CreateEditLayoutSwitchDialog(rootElement)
    }

}

class LayoutSwitchAdditionalInfoInfoBox(by: By) : InfoBox(by) {
    fun omistaja(): String = fieldValue("Omistaja")
}

class SwitchStructureGeneralInfoBox(by: By) : InfoBox(by) {
    fun tyyppi(): String = getChildElementStaleSafe(By.cssSelector("p")).text
    fun katisyys(): String = fieldValue("Kätisyys")
    fun turvavaihde(): String = fieldValue("Turvavaihde")
}

class SwitchCoordinatesInfoBox(by: By) : InfoBox(by) {
    fun koordinaatit(): String = fieldValue("Koordinaatit (TM35FIN)")
    fun vaihteenLinjat(): List<SwitchLineAndTrack> {
        val switchLines = getChildElementsStaleSafe(By.cssSelector("dt.switch-joint-infobox__joint-alignments-title")).map { it.text }
        val switchTracks = getChildElementsStaleSafe(By.cssSelector("dd.switch-joint-infobox__location-tracks div span")).map { it.text}
        return switchLines.mapIndexed{ index, line -> SwitchLineAndTrack(line, switchTracks[index]) }
    }
    fun linjaJaRaide(linja: String) =
        vaihteenLinjat().first { it.switchLine == linja }

}

class GeometryAlignmentGeneralInfoBox(by: By) : InfoBox(by) {
    fun nimi() = fieldValue("Nimi")
    fun pituusmittauslinja() = fieldValue("Pituusmittauslinja")
}

open class LinkingInfoBox(by: By): InfoBox(by) {
    fun linkitetty() = fieldValue("Linkitetty")

    fun aloitaLinkitys() {
        logger.info("Start linking")
        clickButton("Aloita linkitys")
        getButtonElement("Peruuta") //ensures that the infobox has changed
    }

    fun lisaaLinkitettavia() {
        logger.info("Add more linked alignments")
        clickButton("Lisää linkitettäviä")
        getButtonElement("Peruuta")
    }

    fun linkita(): Toaster {
        logger.info("Link")
        clickButton("Linkitä")
        val toaster = waitAndGetToasterElement()

        //This works as a basic Thread.Sleep() if root element becomes stable very fast
        try {
            waitUntilElementIsStale(rootElement, timeoutSeconds = 1)
        } catch (_: TimeoutException) {

        }

        return toaster
    }

    fun pituusmittauslinja() = fieldValue("Pituusmittauslinja")
}
class GeometryKmPostLinkingInfoBox(by: By): LinkingInfoBox(by) {
    fun linkTo(name: String) {
        logger.info("Link to $name")
        getChildElementStaleSafe(
            By.xpath(".//li[@class='geometry-km-post-linking-infobox__layout-km-post']/div/span[text() = '$name']")
        ).click()
    }

    fun createNewTrackLayoutKmPost(): KmPostEditDialog {
        getElementWhenClickable(By.cssSelector("div.geometry-km-post-linking-infobox__search button")).click()
        return KmPostEditDialog(rootElement)
    }

    fun trackLayoutKmPosts(): List<String> {
        val kmPosts = getChildElementsStaleSafe(
            By.xpath(".//li[@class='geometry-km-post-linking-infobox__layout-km-post']/div/span")
        ).map { element -> element.text }
        logger.info("Track layout KM-posts [$kmPosts]")
        return kmPosts
    }
}

class GeometryAlignmentLinkingInfoBox(by: By): LinkingInfoBox(by) {

    fun linkTo(name: String) = this {
        logger.info("Link to $name")
        getChildElementStaleSafe(
            By.xpath(".//li[@class='geometry-alignment-infobox__alignment']/div/span[text() = '$name']")
        ).click()
    }

    fun createNewLocationTrack(): CreateEditLocationTrackDialog {
        logger.info("Create a new location track")
        getElementWhenClickable(By.xpath("//button[@qa-id='create-location-track-button']")).click()
        return CreateEditLocationTrackDialog(rootElement)
    }

    fun lukitseValinta() = this {
        logger.info("Lock selection")
        clickButton("Lukitse valinta")
        //TODO: this might break things
        //getButtonElement("Poista valinta")
    }
}

class GeometrySwitchGeneralInfoBox(by: By): InfoBox(by) {
    fun nimi() = fieldValue("Nimi")
    fun katisyys() = fieldValue("Kätisyys")
    fun kohdistaKartalla() = clickButton("Kohdista kartalla").also { Thread.sleep(500) }

}

class GeometrySwitchLinkingInfoBox(by: By): LinkingInfoBox(by) {
    fun lukitseValinta() = this {
        logger.info("Lock selection")
        clickButton("Lukitse valinta")
        getButtonElement("Poista valinta")
    }

    fun createNewTrackLayoutSwitch(): CreateEditLayoutSwitchDialog {
        logger.info("Create new track layout switch")
        getElementWhenClickable(By.cssSelector("div.geometry-switch-infobox__search-container button")).click()
        return CreateEditLayoutSwitchDialog(rootElement)
    }

    fun linkTo(name: String) {
        logger.info("Link to $name")
        getChildElementStaleSafe(
            By.xpath(".//li[@class='geometry-switch-infobox__switch']/span/span[text() = '$name']")
        ).click()
    }
}

class MapLayerSettingsPanel(by: By) : PageModel(by) {
    enum class Setting(val uiText: String) {
        TAUSTAKARTTA("Taustakartta"),
        SIJAINTIRAITEET("Sijaintairaiteet"),
        RATANUMEROT("Ratanumerot"),
        KILOMETRIPYLVAAT("Kilometripylväät"),
        VAIHTEET("Vaihteet"),
        SUUNNITELMAT("Suunnitelmat"),
        SUUNNITELMAN_VAIHEET("Suunnitelman vaiheet"),
        SUUNNITELMAN_ALUEET("Suunnitelman alueet"),
        SUUNNITELMAN_KILOMETRIPYLVAAT("Suunnitelman kilometripylväät"),
        LINKITYS("Linkitys"),
        VAIHTEIDEN_LINKITYS("Vaihteiden linkitys"),
        MANUAALINEN_VAIHTEIDEN_LINKITYS("Manuaalinen vaihteiden linkitys"),
    }

    fun close() = this {
        logger.info("Close map layer settings panel")
        getChildElementStaleSafe(By.cssSelector("button")).click()
    }

    fun selectSetting(setting: Setting, enable: Boolean = true) = this {
        logger.info("Select $setting.uiText enable=$enable")
        val enabled = isSelected(setting)
        if (enabled != enable) {
            logger.info("Setting ${setting.uiText} is $enabled, setting it to $enable")
            clickSetting(setting)
        } else {
            logger.info("Setting ${setting.uiText} is already $enabled")
        }
    }

    private fun isSelected(setting: Setting): Boolean {
        return getElementWhenVisible(By.xpath("//label[@class='layer-visibility-setting ' and span[text() = '${setting.uiText}']]/label[@class='switch']"))
            .findElement(By.cssSelector("input")
        ).isSelected
    }

    private fun clickSetting(setting: Setting) {
        logger.info("Click setting ${setting.uiText}")
        getElementWhenVisible(By.xpath("//label[@class='layer-visibility-setting ' and span[text() = '${setting.uiText}']]/label[@class='switch']")).click()
    }
}

class AddEndPointDialog : DialogPopUp() {

    fun jatkuuToisenaRaiteena(): ContinueAsAnotherTrackDialog  {
        selectRadioButton("Jatkuu toisena raiteena")
        ok()
        return ContinueAsAnotherTrackDialog()
    }

    private fun ok() = this  {
        clickButton("OK")
        getButtonElement("Jatka")
        refresRootElement()
    }



    private fun selectRadioButton(buttonLabel: String) = this {
        logger.info("Select radio button $buttonLabel")
        contentElement.findElement(By.xpath("//label[@class='radio' and span[text() = '$buttonLabel']]/span[@class='radio__visualization']")).click()
    }
}

class SwitchLinkingDialog(): DialogPopUp() {
    fun vaihdetyyppi(switchType: String) = this {
        logger.info("Select the switch type '$switchType'")
        val dropDown = DropDown(getElementWhenVisible(By.cssSelector("div.dialog .dropdown")))
        dropDown.openDropdown()
        dropDown.selectItem(switchType)
    }

    fun selectLocationTrack(switchLine: String, locationTrackName: String) = this {
        logger.info("Select $locationTrackName to switch line $switchLine")
        val dropDown = DropDown(getElementWhenVisible(By.xpath("//label[text() = '$switchLine']/following-sibling::div[1]")))
        dropDown.openDropdown()
        dropDown.selectItem(locationTrackName)
    }

    fun aloitaLinkitys() {
        clickButton("Aloita linkitys")
    }
}

class ContinueAsAnotherTrackDialog(): DialogPopUp() {
    fun valitseJatkuvaRaide(alignmentName: String) = this {
        logger.info("Select the alignment '$alignmentName' to continue with ")
        val dropDown = DropDown(getElementWhenVisible(By.cssSelector("div.dialog .dropdown")))
        dropDown.openDropdown()
        dropDown.selectItem(alignmentName)
    }

    fun jatka() = this {
        clickButton("Jatka")
    }
}

class SearchBox(element: WebElement) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val dropDown = DropDown(element)

    fun search(input: String) {
        dropDown.inputText((input))
        logger.info("Perform query: ${dropDown.currentValue()}")
        PageModel.waitUntilChildDoesNotExist(dropDown.element,
            By.xpath(".//li[@class='dropdown__list-item dropdown__list-item--no-options' and contains(text(),'Ladataan...')]"))
        logger.info("Search ready")
    }

    fun searchResults(): List<SearchResult> {
        val searchResults = dropDown.listItems().map { SearchResult(it) }
        logger.info("Search results: ${searchResults.map { it.value() }}")
        return searchResults
    }

    fun clearQuery() {
        logger.info("Clear input")
        dropDown.clearInput()
    }

    fun selectResult(resultContains: String) {
        logger.info("Select result '$resultContains'")
        searchResults().first { it.value().contains(resultContains) }.select()
    }

    class SearchResult(val resultRow: WebElement) {
        fun value() = resultRow.text
        fun select() = resultRow.click()
    }

}

data class SwitchLineAndTrack (
    val switchLine: String,
    val switchTrack: String
)

