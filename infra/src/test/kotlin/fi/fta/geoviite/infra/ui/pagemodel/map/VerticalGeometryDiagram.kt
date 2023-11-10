package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained

class E2EVerticalGeometryDiagram(parentView: E2EViewFragment) :
    E2EViewFragment(parentView, By.className("vertical-geometry-diagram-holder")) {

    fun waitForContent() { // will throw if no content
        waitUntilChildExists(ByChained(byQaId("vertical-geometry-diagram-proper"), By.tagName("line")))
    }
}
