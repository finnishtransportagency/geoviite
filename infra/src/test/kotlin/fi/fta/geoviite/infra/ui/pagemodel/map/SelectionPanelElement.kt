package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.ElementFetch
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.fetch
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

const val ALIGNMENT_ACCORDION_QA_ID = "geometry-plan-panel-alignments"
const val KM_POST_ACCORDION_QA_ID = "geometry-plan-panel-km-posts"
const val SWITCHES_ACCORDION_QA_ID = "geometry-plan-panel-switches"

class E2EGeometryPlansAccordion(by: By) : E2EAccordion(by) {

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

    private fun subAccordion(qaId: String): E2EGeometryPlanAccordion {
        return E2EGeometryPlanAccordion(this.elementFetch, qaId)
    }
}

private class E2EGeometryPlanAccordion(
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

abstract class E2ESelectionListItem(open val name: String, override val index: Int) : E2EListItem

abstract class E2ESelectionList<T : E2ESelectionListItem>(
    listFetch: ElementFetch,
    itemsBy: By,
    getContent: (index: Int, child: WebElement) -> T,
) : E2EList<T>(
    listFetch = listFetch,
    itemsBy = itemsBy,
    getContent = getContent
) {
    fun selectByName(name: String) = selectItemWhenMatches { it.name == name }
}

data class E2EKmPostSelectionListItem(
    override val name: String,
    override val index: Int,
) : E2ESelectionListItem(name, index) {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        index = index,
    )
}

data class E2ELocationTrackSelectionListItem(
    override val name: String,
    override val index: Int,
    val type: String,
) : E2ESelectionListItem(name, index) {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        type = element.findElement(By.tagName("span")).text,
        index = index,
    )
}

data class E2ETrackNumberSelectionListItem(
    override val name: String,
    override val index: Int,
) : E2ESelectionListItem(name, index) {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./div/span")).text,
        index = index,
    )
}

data class E2EReferenceLineSelectionListItem(
    override val name: String,
    override val index: Int,
) : E2ESelectionListItem(name, index) {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.tagName("span")).text,
        index = index
    )
}

data class E2ESwitchSelectionListItem(
    override val name: String,
    override val index: Int,
) : E2ESelectionListItem(name, index) {
    constructor(index: Int, element: WebElement) : this(
        name = element.findElement(By.xpath("./span/span")).text,
        index = index
    )
}

class E2EKmPostSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2EKmPostSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("km-posts-panel__km-posts")),
    itemsBy = byLiTag,
    getContent = { i, e -> E2EKmPostSelectionListItem(i, e) },
)

class E2ELocationTrackSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ELocationTrackSelectionListItem>(
    listFetch = fetch(elementFetch, byQaId("location-tracks-list")),
    itemsBy = byLiTag,
    getContent = { i, e -> E2ELocationTrackSelectionListItem(i, e) }
) {
    // TODO: GVT-1935 implement a generic way to handle list selection status through qa-id
    override fun isSelected(element: WebElement): Boolean =
        element.findElement(By.xpath("./*[1]")).getAttribute("class").contains("selected")
}

class E2ETrackNumberSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ETrackNumberSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("track-number-panel__track-numbers")),
    itemsBy = byLiTag,
    getContent = { i, e -> E2ETrackNumberSelectionListItem(i, e) },
)

class E2EReferenceLineSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2EReferenceLineSelectionListItem>(
    listFetch = fetch(elementFetch, byQaId("reference-lines-list")),
    itemsBy = By.className("reference-lines-panel__reference-line"),
    getContent = { i, e -> E2EReferenceLineSelectionListItem(i, e) }
)

class E2ESwitchesSelectionList(elementFetch: ElementFetch) : E2ESelectionList<E2ESwitchSelectionListItem>(
    listFetch = fetch(elementFetch, By.className("switch-panel__switches")),
    itemsBy = byLiTag,
    getContent = { i, e -> E2ESwitchSelectionListItem(i, e) }
)
