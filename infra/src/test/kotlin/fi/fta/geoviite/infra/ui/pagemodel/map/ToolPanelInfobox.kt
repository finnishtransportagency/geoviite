package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EInfoBox
import fi.fta.geoviite.infra.ui.pagemodel.common.expectToast
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.byText
import org.openqa.selenium.By

// TODO: GVT-1939 Replace init-sleeps with reliable waits
//   implement spinner-indicators for data that is still loading
//   InfoBox already has waitUntilLoaded() that is called on init - it will wait for all spinners to disappear
class E2EGeometryPlanGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val remarks: String get() = getValueForField("Huomiot")
    val author: String get() = getValueForField("Yritys")
    val projectName: String get() = getValueForField("Projekti")
    val fileName: String get() = getValueForField("Tiedosto")
    val planPhase: String get() = getValueForField("Vaihe")
    val decisionPhase: String get() = getValueForField("Vaiheen tarkennus")
    val trackNumber: String get() = getValueForField("Ratanumero")
    val startKmNumber: String get() = getValueForField("Ratakilometri alku")
    val endKmNumber: String get() = getValueForField("Ratakilometri loppu")
}

class E2EGeometryPlanQualityInfobox(infoboxBy: By) : E2EInfoBox(infoboxBy) {

    val source: String get() = getValueForField("Lähde")
    val planTime: String get() = getValueForField("Laadittu")
    val measurementMethod: String get() = getValueForField("Laatu")
    val coordinateSystem: String get() = getValueForField("Koordinaattijärjest.")
    val verticalGeometry: String get() = getValueForField("Pystygeometria")
    val cant: String get() = getValueForField("Kallistus")
    val verticalCoordinateSystem: String get() = getValueForField("Korkeusjärjestelmä")
}

class E2ELayoutKmPostGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String get() = getValueForFieldWhenNotEmpty("Tasakmpistetunnus")
    val trackNumber: String get() = getValueForFieldWhenNotEmpty("Ratanumero")
    fun zoomTo(): E2ELayoutKmPostGeneralInfoBox = apply {
        clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ELayoutKmPostLocationInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val location: String get() = getValueForField("Sijainti (km+m)")
    val coordinates: String get() = getValueForField("Koordinaatit (TM35FIN)")
}

class E2ELocationTrackLocationInfobox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val trackNumber: String get() = getValueForField("Ratanumero")
    val startLocation: String get() = getValueForField("Alkusijainti (km + m)")
    val endLocation: String get() = getValueForField("Loppusijainti (km + m)")
    val startPoint: String get() = getValueForField("Alkupiste")
    val endPoint: String get() = getValueForField("Loppupiste")

    val trueLength: String get() = getValueForField("Todellinen pituus (m)")
    val trueLengthDouble: Double? get() = Regex("[0-9.]*").find(trueLength)?.value?.toDouble()
    val startCoordinates: String get() = getValueForField("Alkukoordinaatit TM35FIN")
    val endCoordinates: String get() = getValueForField("Loppukoordinaatit TM35FIN")
    fun waitForStartCoordinatesChange(text: String) = waitUntilValueChangesForField("Alkukoordinaatit TM35FIN", text)
    fun waitForEndCoordinatesChange(text: String) = waitUntilValueChangesForField("Loppukoordinaatit TM35FIN", text)

    fun startLinking(): E2ELocationTrackLocationInfobox = apply {
        logger.info("Edit start/end point")
        clickButton(byText("Lyhennä alkua ja/tai loppua"))
        waitUntilChildVisible(byText("Valmis")) // Ensures that the infobox has changed to edit mode
    }

    fun save(): E2ELocationTrackLocationInfobox = apply {
        clickButton(byText("Valmis"))
        waitAndClearToast("location-track-endpoints-updated")
    }

    fun cancel(): E2ELocationTrackLocationInfobox = apply {
        clickButton(byText("Lopeta"))
    }
}

class E2ELocationTrackGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val oid: String get() = getValueForField("Tunniste")
    val name: String get() = getValueForField("Sijaintiraidetunnus")
    val state: String get() = getValueForField("Tila")
    val description: String get() = getValueForField("Kuvaus")
    val trackNumber: String get() = getValueForField("Ratanumero")

    fun edit(): E2ELocationTrackEditDialog {
        editFields()
        return E2ELocationTrackEditDialog()
    }

    fun zoomTo(): E2ELocationTrackGeneralInfoBox = apply {
        clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
    }
}


class E2ELocationTrackLogInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val created: String get() = getValueForField("Luotu")
    val changed: String get() = getValueForField("Muokattu")
}

class E2ETrackNumberGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val oid: String get() = getValueForField("OID")
    val name: String get() = getValueForField("Ratanumerotunnus")
    val state: String get() = getValueForField("Tila")
    val description: String get() = getValueForField("Ratanumeron kuvaus")

    fun edit(): E2ETrackNumberEditDialog {
        editFields()
        return E2ETrackNumberEditDialog()
    }
}

class E2EReferenceLineLocationInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val startLocation: String get() = getValueForField("Alkusijainti (km + m)")
    val endLocation: String get() = getValueForField("Loppusijainti (km + m)")
    val trueLength: String get() = getValueForField("Todellinen pituus (m)")
    val startCoordinates: String get() = getValueForField("Alkukoordinaatit TM35FIN")
    val endCoordinates: String get() = getValueForField("Loppukoordinaatit TM35FIN")
    fun zoomTo(): E2EReferenceLineLocationInfoBox = apply {
        clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ETrackNumberLogInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val created: String get() = getValueForField("Luotu")
    val changed: String get() = getValueForField("Muokattu")
}

class E2ELayoutSwitchGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String get() = getValueForField("Vaihdetunnus")
    val oid: String get() = getValueForField("OID")
    val category: String get() = getValueForField("Tila")

    fun edit(): E2ELayoutSwitchEditDialog {
        editFields()
        return E2ELayoutSwitchEditDialog()
    }

    fun zoomTo(): E2ELayoutSwitchGeneralInfoBox = apply {
        clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
    }
}

class E2ELayoutSwitchAdditionalInfoInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val owner: String get() = getValueForField("Omistaja")
}

class E2ESwitchStructureGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val type: String get() = childText(By.cssSelector("p"))
    val hand: String get() = getValueForField("Kätisyys")
    val trap: String get() = getValueForField("Turvavaihde")
}

class E2ESwitchCoordinatesInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    data class SwitchLineAndTrack(
        val switchLine: String,
        val switchTrack: String,
    )

    val coordinates: String get() = getValueForField("Koordinaatit (TM35FIN)")

    val jointAlignments: List<SwitchLineAndTrack>
        get() {
            val switchLines = childTexts(By.cssSelector("dt.switch-joint-infobox__joint-alignments-title"))
            val switchTracks = childTexts(By.cssSelector("dd.switch-joint-infobox__location-tracks div span"))
            return switchLines.mapIndexed { index, line -> SwitchLineAndTrack(line, switchTracks[index]) }
        }

    fun jointAlignment(line: String) = jointAlignments.first { it.switchLine == line }

}

class E2EGeometryAlignmentGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String get() = getValueForField("Nimi")
    val trackNumber: String get() = getValueForField("Pituusmittauslinja")
    fun zoomTo() = clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
}

open class E2ELinkingInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val linked: String get() = getValueForField("Linkitetty")

    val trackNumber: String get() = getValueForField("Pituusmittauslinja")

    fun startLinking() {
        logger.info("Start linking")
        clickButton(byText("Aloita linkitys"))
        waitUntilChildVisible(byText("Peruuta")) //ensures that the infobox has changed
    }

    fun addLinking() {
        logger.info("Add more linked alignments")
        clickButton(byText("Lisää linkitettäviä"))
        waitUntilChildVisible(byText("Peruuta")) //ensures that the infobox has changed
    }

    fun link() = expectToast {
        logger.info("Link")
        childButton(byText("Linkitä")).clickAndWaitToDisappear()
    }

}

class E2EGeometryKmPostLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {
    fun linkTo(name: String): E2EGeometryKmPostLinkingInfoBox = apply {
        logger.info("Link to $name")
        clickChild(
            By.xpath(
                ".//li[@class='geometry-km-post-linking-infobox__layout-km-post' and div/span[text() = '$name']]"
            )
        )
    }

    fun createNewTrackLayoutKmPost(): E2EKmPostEditDialog {
        clickChild(By.cssSelector("div.geometry-km-post-linking-infobox__search button"))
        return E2EKmPostEditDialog()
    }

    val trackLayoutKmPosts: List<String>
        get() = childTexts(
            By.xpath(".//li[@class='geometry-km-post-linking-infobox__layout-km-post']")
        )
}

class E2EGeometryAlignmentLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {

    fun linkTo(name: String): E2EGeometryAlignmentLinkingInfoBox = apply {
        logger.info("Link to $name")
        clickChild(
            By.xpath(".//li[@class='geometry-alignment-infobox__alignment' and div/span[text() = '$name']]")
        )
    }

    fun createNewLocationTrack(): E2ELocationTrackEditDialog {
        logger.info("Create a new location track")
        clickChild(byQaId("create-location-track-button"))

        return E2ELocationTrackEditDialog()
    }

    fun lock(): E2EGeometryAlignmentLinkingInfoBox = apply {
        logger.info("Lock selection")
        clickButton(byText("Lukitse valinta"))
        waitUntilChildVisible(byText("Poista valinta"))
    }
}

class E2EGeometrySwitchGeneralInfoBox(infoboxBy: By) : E2EInfoBox(infoboxBy) {
    val name: String get() = getValueForField("Nimi")
    val hand: String get() = getValueForField("Kätisyys")
    fun zoomTo(): E2EGeometrySwitchGeneralInfoBox = apply {
        clickButton(byText("Kohdista kartalla")).also { E2ETrackLayoutPage.finishLoading() }
    }

}

class E2EGeometrySwitchLinkingInfoBox(infoboxBy: By) : E2ELinkingInfoBox(infoboxBy) {
    fun lock(): E2EGeometrySwitchLinkingInfoBox = apply {
        logger.info("Lock selection")
        clickButton(byText("Lukitse valinta"))
        waitUntilChildVisible(byText("Poista valinta"))
    }

    fun createNewTrackLayoutSwitch(): E2ELayoutSwitchEditDialog {
        logger.info("Create new track layout switch")
        clickChild(By.cssSelector("div.geometry-switch-infobox__search-container button"))

        return E2ELayoutSwitchEditDialog()
    }

    fun linkTo(name: String): E2EGeometrySwitchLinkingInfoBox = apply {
        logger.info("Link to $name")
        clickChild(
            By.xpath(".//li[@class='geometry-switch-infobox__switch' and span/span[text() = '$name']]")
        )
    }
}
