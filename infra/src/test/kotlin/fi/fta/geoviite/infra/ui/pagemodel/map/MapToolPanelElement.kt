package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.byText
import fi.fta.geoviite.infra.ui.util.fetch
import getElementWhenClickable
import getElementWhenVisible
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import waitAndGetToasterElement
import waitUntilChildNotVisible
import waitUntilElementIsStale
import waitUntilVisible
import java.time.Duration

// TODO: GVT-1947 refactor these to match common style:
//   Code language,
//   Don't use localized strings as id
//   This file is also too large: split into separate components
//   The fields here (and other test data) should perhaps be properly typed instead of strings. That would make them easier to compare.
// TODO: GVT-1939 Replace init-sleeps with reliable waits
//   implement spinner-indicators for data that is still loading
//   InfoBox already has waitUntilLoaded() that is called on init - it will wait for all spinners to disappear
class GeometryPlanGeneralInfoBox(by: By) : InfoBox(by) {
    init {
        //Object initializes too quickly and webelement is not stable/ready
        Thread.sleep(500)
    }

    fun huomiot(): String = fieldValue("Huomiot")
    fun author(): String = fieldValue("Yritys")
    fun projekti(): String = fieldValue("Projekti")
    fun tiedosto(): String = fieldValue("Tiedosto")
    fun vaihe(): String = fieldValue("Vaihe")
    fun vaiheenTarkennus(): String = fieldValue("Vaiheen tarkennus")
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun rataKilometriAlku(): String = fieldValue("Ratakilometri alku")
    fun rataKilometriLoppu(): String = fieldValue("Ratakilometri loppu")
}

class GeometryPlanQualityInfobox(by: By) : InfoBox(by) {
    init {
        //Object initializes too quickly and webelement is not stable/ready
        Thread.sleep(500)
    }

    fun lahde(): String = fieldValue("Lähde")
    fun laadittu(): String = fieldValue("Laadittu")
    fun laatu(): String = fieldValue("Laatu")
    fun koordinaattijarjest(): String = fieldValue("Koordinaattijärjest.")
    fun pystygeometria(): String = fieldValue("Pystygeometria")
    fun kallistus(): String = fieldValue("Kallistus")
    fun korkeusjarjestelma(): String = fieldValue("Korkeusjärjestelmä")
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
    fun waitForAlkukoordinaatti(text: String) = waitUntilFieldValueChanges("Alkukoordinaatit TM35FIN", text)
    fun loppukoordinaatti(): String = fieldValue("Loppukoordinaatit TM35FIN")
    fun waitForLoppukoordinaatti(text: String) = waitUntilFieldValueChanges("Loppukoordinaatit TM35FIN", text)

    fun muokkaaAlkuLoppupistetta() {
        logger.info("Edit start/end point")
        clickButtonByText("Lyhennä alkua ja/tai loppua")
        waitUntilVisible(byText("Valmis")) // Ensures that the infobox has changed to edit mode
    }

    fun valmis(): Toaster {
        clickButtonByText("Valmis")
        return waitAndGetToasterElement()
    }

    fun peruuta() = clickButtonByText("Lopeta")
}

class LocationTrackGeneralInfoBox(by: By) : InfoBox(by) {
    fun tunniste(): String = fieldValue("Tunniste")
    fun sijainteraidetunnus(): String = fieldValue("Sijaintiraidetunnus")
    fun tila(): String = fieldValue("Tila")
    fun kuvaus(): String = fieldValue("Kuvaus")
    fun ratanumero(): String = fieldValue("Ratanumero")
    fun openRatanumero() = fieldElement("Ratanumero").findElement(By.cssSelector("a")).click()

    fun muokkaaTietoja(): CreateEditLocationTrackDialog {
        startEditingInfoBoxValues()
        // TODO: GVT-1947 Find and remove all places where the element is fetched and remembered, like this dialog creation
        //  To fix it:
        //    Ensure the component inherit PageModel (this dialog does)
        //    Change the constructor argument from (x: WebElement) to a function (fetchX: () -> WebElement) or By (elementBy: By)
        //    Change the use-place constructor call to match
        //      If the user is also inherited from pageModel, its own elementFetch or something derived from it can be used:
        //        This Component(webElement) becomes Component(elementFetch)
        //      If the user seeks the content with By, you can just pass that in
        return CreateEditLocationTrackDialog(webElement)
    }

    // TODO: GVT-1947 Sleep needed?
    fun kohdistaKartalla() = clickButtonByText("Kohdista kartalla").also { Thread.sleep(500) }
}

class CreateEditLayoutSwitchDialog(val editedElement: WebElement): DialogPopUp() {

    enum class Tilakategoria(val uiText: String) {
        POISTUNUT_KOHDE("Poistunut kohde"),
        OLEMASSA_OLEVA_KOHDE("Olemassa oleva kohde")
    }

    fun editVaihdetunnus(vaihdetunnus: String) = this {
        content.changeFieldValue("Vaihdetunnus", vaihdetunnus)
    }

    fun editTilakategoria(tilakategoria: Tilakategoria) = this {
        content.changeFieldDropDownValue("Tilakategoria", tilakategoria.uiText)
    }

    fun tallenna(): Toaster {
        val isPoistunut = content.fieldValue("Tilakategoria") == Tilakategoria.POISTUNUT_KOHDE.uiText
        clickButtonByText("Tallenna")

        if (isPoistunut) {
            logger.info("Confirm saving changes")
            DialogPopUp(By.cssSelector("div.dialog--dark div.dialog__popup")).clickPrimaryButton()
        }

        val toaster = waitAndGetToasterElement()
        Thread.sleep(1000) //switch list is unstable after deletion and there's no better fix for now
        return toaster
    }
}

// TODO: GVT-1939 This input editedElement is here to verify it going stale when the data updates. Use a generic pattern instead.
class KmPostEditDialog(val editedElement: WebElement): DialogPopUp() {

    enum class TilaTyyppi(val uiText: String) {
        SUUNNITELTU("Suunniteltu"),
        KAYTOSSA("Käytössä"),
        KAYTOSTA_POISTETTU("Käytöstä poistettu"),
    }

    fun editTasakmpistetunnus(tasakmpistetunnus: String) = this {
        content.changeFieldValue("Tasakmpistetunnus", tasakmpistetunnus)}

    fun editTila(tilaTyyppi: TilaTyyppi) = this {
        content.changeFieldDropDownValue("Tila", tilaTyyppi.uiText)}

    fun tallenna(waitUntilRootIsStale: Boolean = true): Toaster {
        clickButtonByText("Tallenna")
        if (waitUntilRootIsStale) waitUntilElementIsStale(editedElement)
        return waitAndGetToasterElement()
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
        clickButtonByText("Tallenna")
        try {
            if (waitUntilRootIsStale) waitUntilElementIsStale(editedElement, Duration.ofSeconds(1))
        } catch (ex: TimeoutException) {
            logger.warn("Root dialog never became stale or stabilized too fast")
        }

        if (isPoistettu) {
            DialogPopUp().clickPrimaryWarningButton()
        }

        val toaster = waitAndGetToasterElement()
        // TODO: GVT-1939 Get rid of unreliable sleep. This is the same in all these dialogs
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
        startEditingInfoBoxValues()
        return CreateEditTrackNumberDialog(webElement)
    }
}

class CreateEditTrackNumberDialog(val editedElement: WebElement): DialogPopUp() {
    enum class TilaTyyppi(val uiText: String) {
        KAYTOSSA("Käytössä"),
        KAYTOSTA_POISTETTU("Käytöstä poistettu"),
        POISTETTU("Poistettu")
    }

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
        clickButtonByText("Tallenna")
        try {
            if (waitUntilRootIsStale) waitUntilElementIsStale(editedElement, Duration.ofSeconds(1))
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
    fun kohdistaKartalla() = clickButtonByText("Kohdista kartalla").also { Thread.sleep(500) }
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
        startEditingInfoBoxValues()
        return CreateEditLayoutSwitchDialog(webElement)
    }

}

class LayoutSwitchAdditionalInfoInfoBox(by: By) : InfoBox(by) {
    fun omistaja(): String = fieldValue("Omistaja")
}

class SwitchStructureGeneralInfoBox(by: By) : InfoBox(by) {
    fun tyyppi(): String = childText(By.cssSelector("p"))
    fun katisyys(): String = fieldValue("Kätisyys")
    fun turvavaihde(): String = fieldValue("Turvavaihde")
}

class SwitchCoordinatesInfoBox(by: By) : InfoBox(by) {
    fun koordinaatit(): String = fieldValue("Koordinaatit (TM35FIN)")
    fun vaihteenLinjat(): List<SwitchLineAndTrack> {
        val switchLines = childTexts(By.cssSelector("dt.switch-joint-infobox__joint-alignments-title"))
        val switchTracks = childTexts(By.cssSelector("dd.switch-joint-infobox__location-tracks div span"))
        return switchLines.mapIndexed{ index, line -> SwitchLineAndTrack(line, switchTracks[index]) }
    }
    fun linjaJaRaide(linja: String) =
        vaihteenLinjat().first { it.switchLine == linja }

}

class GeometryAlignmentGeneralInfoBox(by: By) : InfoBox(by) {
    fun nimi() = fieldValue("Nimi")
    fun pituusmittauslinja() = fieldValue("Pituusmittauslinja")

    fun kohdistaKartalla() = clickButtonByText("Kohdista kartalla").also { Thread.sleep(500) }
}

open class LinkingInfoBox(by: By): InfoBox(by) {
    fun linkitetty() = fieldValue("Linkitetty")

    fun aloitaLinkitys() {
        logger.info("Start linking")
        clickButtonByText("Aloita linkitys")
        waitChildVisible(byText("Peruuta")) //ensures that the infobox has changed
    }

    fun lisaaLinkitettavia() {
        logger.info("Add more linked alignments")
        clickButtonByText("Lisää linkitettäviä")
        waitChildVisible(byText("Peruuta")) //ensures that the infobox has changed
    }

    fun linkita(): Toaster {
        logger.info("Link")
        childButton(byText("Linkitä")).clickAndWaitToDisappear()
        return waitAndGetToasterElement()
    }

    fun pituusmittauslinja() = fieldValue("Pituusmittauslinja")
}
class GeometryKmPostLinkingInfoBox(by: By): LinkingInfoBox(by) {
    fun linkTo(name: String) {
        logger.info("Link to $name")
        clickChild(By.xpath(
            ".//li[@class='geometry-km-post-linking-infobox__layout-km-post']/div/span[text() = '$name']"
        ))
    }

    fun createNewTrackLayoutKmPost(): KmPostEditDialog {
        getElementWhenClickable(By.cssSelector("div.geometry-km-post-linking-infobox__search button")).click()
        return KmPostEditDialog(webElement)
    }

    fun trackLayoutKmPosts(): List<String> = childTexts(By.xpath(
        ".//li[@class='geometry-km-post-linking-infobox__layout-km-post']/div/span"
    )).also { kmPosts -> logger.info("Track layout KM-posts [$kmPosts]") }
}

class GeometryAlignmentLinkingInfoBox(by: By): LinkingInfoBox(by) {

    fun linkTo(name: String) = this {
        logger.info("Link to $name")
        clickChild(By.xpath(
            ".//li[@class='geometry-alignment-infobox__alignment']/div/span[text() = '$name']"
        ))
    }

    fun createNewLocationTrack(): CreateEditLocationTrackDialog {
        logger.info("Create a new location track")
        clickButtonByQaId("create-location-track-button")
        return CreateEditLocationTrackDialog(webElement)
    }

    fun lukitseValinta() = this {
        logger.info("Lock selection")
        clickButtonByText("Lukitse valinta")
        //TODO: this might break things
        //getButtonElement("Poista valinta")
    }
}

class GeometrySwitchGeneralInfoBox(by: By): InfoBox(by) {
    fun nimi() = fieldValue("Nimi")
    fun katisyys() = fieldValue("Kätisyys")
    fun kohdistaKartalla() = clickButtonByText("Kohdista kartalla").also { Thread.sleep(500) }

}

class GeometrySwitchLinkingInfoBox(by: By): LinkingInfoBox(by) {
    fun lukitseValinta() = this {
        logger.info("Lock selection")
        clickButtonByText("Lukitse valinta")
        waitChildVisible(byText("Poista valinta"))
    }

    fun createNewTrackLayoutSwitch(): CreateEditLayoutSwitchDialog {
        logger.info("Create new track layout switch")
        getElementWhenClickable(By.cssSelector("div.geometry-switch-infobox__search-container button")).click()
        return CreateEditLayoutSwitchDialog(webElement)
    }

    fun linkTo(name: String) {
        logger.info("Link to $name")
        clickChild(By.xpath(
            ".//li[@class='geometry-switch-infobox__switch']/span/span[text() = '$name']"
        ))
    }
}

class MapLayerSettingsPanel(by: By) : PageModel(by) {
    enum class Setting(val uiText: String) {
        TAUSTAKARTTA("Taustakartta"),
        PITUUSMITTAUSLINJAT("Pituusmittauslinjat"),
        SIJAINTIRAITEET("Sijaintairaiteet"),
        KOROSTA_PUUTTUVA_PYSTYGEOMETRIAT("Korosta puuttuva pystygeometria"),
        KOROSTA_PUUTTUVA_LINKITYS("Korosta puuttuva linkitys"),
        KOROSTA_DUPLIKAATTIRAITEET("Korosta duplikaattiraiteet"),
        VAIHTEET("Vaihteet"),
        TASAKILOMETRIPISTEET("Tasakilometripisteet"),

        SUUNNITELMAN_RAITEET("Suunnitelman raiteet"),
        SUUNNITELMAN_VAIHEET("Suunnitelman vaiheet"),
        SUUNNITELMAN_TASAKILOMETRIPISTEET("Suunnitelman tasakilometripisteet"),
        SUUNNITELMAN_ALUEET("Suunnitelman alueet"),
    }

    fun close() = this {
        logger.info("Close map layer settings panel")
        clickChild(By.cssSelector("button"))
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
        return getElementWhenVisible(By.xpath("//label[@class='map-layer-menu__layer-visibility ' and span[text() = '${setting.uiText}']]/label[@class='switch']"))
            .findElement(
                By.cssSelector("input")
            ).isSelected
    }

    private fun clickSetting(setting: Setting) {
        logger.info("Click setting ${setting.uiText}")
        getElementWhenVisible(By.xpath("//label[@class='map-layer-menu__layer-visibility ' and span[text() = '${setting.uiText}']]/label[@class='switch']")).click()
    }
}

class AddEndPointDialog : DialogPopUp() {

    fun jatkuuToisenaRaiteena(): ContinueAsAnotherTrackDialog  {
        selectRadioButton("Jatkuu toisena raiteena")
        ok()
        return ContinueAsAnotherTrackDialog()
    }

    private fun ok() = this  {
        clickButtonByText("OK")
        waitUntilVisible(byText("Jatka"))
    }

    private fun selectRadioButton(buttonLabel: String) = this {
        logger.info("Select radio button $buttonLabel")
        clickChild(By.xpath(
            "//label[@class='radio' and span[text() = '$buttonLabel']]/span[@class='radio__visualization']"
        ))
    }
}

class SwitchLinkingDialog: DialogPopUp() {
    fun vaihdetyyppi(switchType: String) = this {
        logger.info("Select the switch type '$switchType'")
        val dropDown = childComponent(By.cssSelector("div.dialog .dropdown"), ::DropDown)
        dropDown.openDropdown()
        dropDown.selectItem(switchType)
    }

    fun selectLocationTrack(switchLine: String, locationTrackName: String) = this {
        logger.info("Select $locationTrackName to switch line $switchLine")
        val dropDown = childComponent(By.xpath("//label[text() = '$switchLine']/following-sibling::div[1]"), ::DropDown)
        dropDown.openDropdown()
        dropDown.selectItem(locationTrackName)
    }

    fun aloitaLinkitys() {
        clickButtonByText("Aloita linkitys")
    }
}

class ContinueAsAnotherTrackDialog: DialogPopUp() {
    fun valitseJatkuvaRaide(alignmentName: String) = this {
        logger.info("Select the alignment '$alignmentName' to continue with ")
        val dropDown = childComponent(By.cssSelector("div.dialog .dropdown"), ::DropDown)
        dropDown.openDropdown()
        dropDown.selectItem(alignmentName)
    }

    fun jatka() = this {
        clickButtonByText("Jatka")
    }
}

class SearchBox(val path: By): PageModel(path) {
    val qaId = "search-box"
    private val dropDown: DropDown get() = DropDown(elementFetch)

    fun search(input: String) {
        clearQuery()
        addToSearchInput(input)
    }

    fun addToSearchInput(input: String) {
        dropDown.inputText(input)
        logger.info("Perform query: ${dropDown.currentValue()}")
        waitUntilOpenAndNotLoading()
        logger.info("Search ready")
    }

    fun searchResults(): List<SearchResult> {
        // TODO: GVT-1935 These list elements hold a reference to the WebElement, risking staleness. Use ListModel to replace this.
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

    fun waitUntilOpenAndNotLoading() {
        waitUntilVisible(By.className("dropdown__list"))
        waitUntilChildNotVisible(fetch(By.className("dropdown__list")), byQaId("$qaId-loading"))
    }
}

data class SwitchLineAndTrack (
    val switchLine: String,
    val switchTrack: String
)
