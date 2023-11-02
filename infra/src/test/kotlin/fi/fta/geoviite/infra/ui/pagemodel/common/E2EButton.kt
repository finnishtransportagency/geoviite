package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import org.openqa.selenium.By
import waitUntilNotExist

class E2EButton(val buttonBy: By) {
    fun click(): E2EButton = apply {
        clickWhenClickable(buttonBy)
    }

    fun clickAndWaitToDisappear() {
        click()
        waitUntilNotExist(buttonBy)
    }
}
