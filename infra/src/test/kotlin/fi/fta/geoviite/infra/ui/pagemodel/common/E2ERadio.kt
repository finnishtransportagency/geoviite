package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2ERadio(radioBy: By) : E2EViewFragment(radioBy) {

    fun choose(qaId: String) = apply {
        logger.info("Select option $qaId")

        clickChild(byQaId(qaId))
    }
}
