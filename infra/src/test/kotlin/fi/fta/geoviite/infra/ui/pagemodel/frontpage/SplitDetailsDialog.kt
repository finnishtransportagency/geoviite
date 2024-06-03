package fi.fta.geoviite.infra.ui.pagemodel.frontpage

import clickWhenClickable
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import getElementWhenExists
import org.openqa.selenium.By.ByXPath

class SplitDetailsDialog : E2EViewFragment(byQaId("split-details-dialog")) {
    fun sourceTrackName(): String {
        val selector = "//div[@qa-id='split-source-track-name' and normalize-space()]"
        return getElementWhenExists(ByXPath(selector)).text
    }

    fun close(): E2EFrontPage {
        clickWhenClickable(byQaId("split-details-dialog-close"))
        return E2EFrontPage()
    }
}
