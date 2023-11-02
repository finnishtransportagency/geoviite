package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2ERadio(radioBy: By) : E2EViewFragment(radioBy) {

    fun chooseByQaId(qaId: String) = apply {
        logger.info("Select option $qaId")

        clickChild(byQaId(qaId))
    }

    fun chooseByLabel(label: String) = apply {
        logger.info("Select option $label")

        clickChild(By.xpath("//*[label=\"$label\"]"))
    }
}
