package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2ERadio(radioBy: By) : E2EViewFragment(radioBy) {
    fun chooseByQaId(qaId: String) = apply {
        clickChild(byQaId(qaId))
    }

    fun chooseByLabel(label: String) = apply {
        clickChild(By.xpath("//*[label=\"$label\"]"))
    }
}
