package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAccordion
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EList
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETextList
import fi.fta.geoviite.infra.ui.pagemodel.common.byLiTag
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

const val ALIGNMENT_ACCORDION_QA_ID = "geometry-plan-panel-alignments"
const val KM_POST_ACCORDION_QA_ID = "geometry-plan-panel-km-posts"
const val SWITCHES_ACCORDION_QA_ID = "geometry-plan-panel-switches"

class E2EGeometryPlanAccordion(by: By) : E2EAccordion(by) {

    private val alignmentsAccordion by lazy { subAccordion(ALIGNMENT_ACCORDION_QA_ID) }
    private val kmPostsAccordion by lazy { subAccordion(KM_POST_ACCORDION_QA_ID) }
    private val switchesAccordion by lazy { subAccordion(SWITCHES_ACCORDION_QA_ID) }

    val kmPostsList: E2ETextList get() = open().let { kmPostsAccordion.items }
    val alignmentsList: E2ETextList get() = open().let { alignmentsAccordion.items }
    val switchesList: E2ETextList get() = open().let { switchesAccordion.items }
    
    fun selectAlignment(alignment: String) {
        logger.info("Select geometry alignment $alignment")
        open().also { alignmentsAccordion.selectItem(alignment) }
    }

    fun selectKmPost(kmPost: String) {
        logger.info("Select geometry km post $kmPost")
        open().also { kmPostsAccordion.selectItem(kmPost) }
    }

    fun selectSwitch(switch: String) {
        logger.info("Select geometry switch $switch")
        open().also { switchesAccordion.selectItem(switch) }
    }

    private fun subAccordion(qaId: String): E2EGeometryPlanItemAccordion {
        return E2EGeometryPlanItemAccordion(this.elementFetch, qaId)
    }
}

private class E2EGeometryPlanItemAccordion(
    parentFetch: ElementFetch,
    qaId: String,
) : E2EAccordion(fetch(parentFetch, byQaId(qaId))) {

    private val _items: E2ETextList by lazy {
        E2ETextList(this.elementFetch)
    }

    val items: E2ETextList get() = open().let { _items }

    fun selectItem(name: String) {
        items.selectByTextWhenMatches(name)
    }
}

interface E2ESelectionListItem {
    val name: String
}

abstract class E2ESelectionList<T : E2ESelectionListItem>(
    listFetch: ElementFetch,
    private val getContent: (child: WebElement) -> T,
    itemsBy: By = byLiTag,
) : E2EList<T>(listFetch, itemsBy) {
    override fun getItemContent(item: WebElement) = this.getContent(item)
    
    fun selectByName(name: String) = selectItemWhenMatches { it.name == name }
}

data class E2EKmPostSelectionListItem(override val name: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(element.findElement(By.xpath("./div/span")).text)
}

data class E2ELocationTrackSelectionListItem(override val name: String, val type: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        type = element.findElement(By.tagName("span")).text,
    )
}

data class E2ETrackNumberSelectionListItem(override val name: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(element.findElement(By.xpath("./div/span")).text)
}

data class E2EReferenceLineSelectionListItem(override val name: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(element.findElement(By.tagName("span")).text)
}

data class E2ESwitchSelectionListItem(override val name: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(element.findElement(By.xpath("./span/span")).text)
}

class E2EKmPostSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2EKmPostSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("km-posts-panel__km-posts")),
    getContent = { e -> E2EKmPostSelectionListItem(e) },
)

class E2ELocationTrackSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ELocationTrackSelectionListItem>(
    listFetch = fetch(elementFetch, byQaId("location-tracks-list")),
    getContent = { e -> E2ELocationTrackSelectionListItem(e) }
) {
    override fun isSelected(element: WebElement): Boolean =
        element.findElement(By.xpath("./*[1]")).getAttribute("class").contains("selected")
}

class E2ETrackNumberSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ETrackNumberSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("track-number-panel__track-numbers")),
    getContent = { e -> E2ETrackNumberSelectionListItem(e) },
)

class E2EReferenceLineSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2EReferenceLineSelectionListItem>(
    listFetch = fetch(elementFetch, byQaId("reference-lines-list")),
    itemsBy = By.className("reference-lines-panel__reference-line"),
    getContent = { e -> E2EReferenceLineSelectionListItem(e) }
)

class E2ESwitchesSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ESwitchSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("switch-panel__switches")),
    getContent = { e -> E2ESwitchSelectionListItem(e) }
)
