package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import getChildElement
import org.openqa.selenium.By

class E2EVerticalGeometryDiagram(parentFetch: ElementFetch) :
    E2EViewFragment(fetch(parentFetch, By.className("vertical-geometry-diagram-holder"))) {

        fun waitForContent() { // will throw if no content
            childElement(byQaId("vertical-geometry-diagram-proper")).getChildElement(By.tagName("line"))
        }
}
