package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import getChildElement
import org.openqa.selenium.By

class E2ERadio(elementFetch: ElementFetch) : E2EViewFragment(elementFetch) {
    fun chooseByQaId(qaId: String) = apply {
        webElement.getChildElement(byQaId(qaId)).click()
    }

    fun chooseByLabel(label: String) = apply {
        webElement.getChildElement(By.xpath("//*[label=\"$label\"]")).click()
    }
}
