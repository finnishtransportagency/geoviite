package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import org.openqa.selenium.By

class E2ECheckbox(val checkboxBy: By) {
    fun click(): E2ECheckbox = apply {
        clickWhenClickable(checkboxBy)
    }
}
