package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAccordion
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EList
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETextList
import fi.fta.geoviite.infra.ui.pagemodel.common.byLiTag
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.pagefactory.ByChained

const val ALIGNMENT_ACCORDION_QA_ID = "geometry-plan-panel-alignments"
const val KM_POST_ACCORDION_QA_ID = "geometry-plan-panel-km-posts"
const val SWITCHES_ACCORDION_QA_ID = "geometry-plan-panel-switches"

class E2EGeometryPlanAccordion(accordionBy: By, val selectedItemBy: By?) : E2EAccordion(accordionBy) {

    private val alignmentsAccordion by lazy {
        subAccordion(ALIGNMENT_ACCORDION_QA_ID, By.className("geometry-plan-panel__alignment--selected"))
    }
    private val kmPostsAccordion by lazy {
        subAccordion(KM_POST_ACCORDION_QA_ID, By.className("geometry-plan-panel__kmpost--selected"))
    }
    private val switchesAccordion by lazy {
        subAccordion(SWITCHES_ACCORDION_QA_ID, By.className("geometry-plan-panel__switch--selected"))
    }

    val kmPostsList: E2ETextList = open().let { kmPostsAccordion.items }
    val alignmentsList: E2ETextList = open().let { alignmentsAccordion.items }
    val switchesList: E2ETextList = open().let { switchesAccordion.items }

    fun selectAlignment(alignment: String) {
        logger.info("Select geometry alignment $alignment")
        open().also { alignmentsAccordion.selectItem(alignment) }
    }

    fun selectKmPost(kmPost: String) {
        logger.info("Select geometry km post $kmPost")
        open().also { kmPostsAccordion.selectItem(kmPost) }
    }

    fun unselectKmPost(kmPost: String) {
        logger.info("Select geometry km post $kmPost")
        open().also { kmPostsAccordion.unselectItem(kmPost) }
    }

    fun selectSwitch(switch: String) {
        logger.info("Select geometry switch $switch")
        open().also { switchesAccordion.selectItem(switch) }
    }

    private fun subAccordion(qaId: String, selectedItemBy: By): E2EGeometryPlanItemAccordion {
        return E2EGeometryPlanItemAccordion(viewBy, qaId, selectedItemBy)
    }
}

private class E2EGeometryPlanItemAccordion(parentBy: By, qaId: String, selectedItemBy: By) :
    E2EAccordion(ByChained(parentBy, byQaId(qaId))) {

    private val _items: E2ETextList by lazy { E2ETextList(viewBy, selectedItemBy = selectedItemBy) }

    val items: E2ETextList
        get() = open().let { _items }

    fun selectItem(name: String) {
        items.selectByText(name)
    }

    fun unselectItem(name: String) {
        items.unselectByText(name)
    }
}

interface E2ESelectionListItem {
    val name: String
}

abstract class E2ESelectionList<T : E2ESelectionListItem>(
    listBy: By,
    private val getContent: (child: WebElement) -> T,
    itemsBy: By = byLiTag,
    selectedItemBy: By,
) : E2EList<T>(listBy, itemsBy, selectedItemBy) {
    override fun getItemContent(item: WebElement) = getContent(item)

    fun selectByName(name: String) = select { it.name == name }
}

data class E2EKmPostSelectionListItem(override val name: String) : E2ESelectionListItem {
    constructor(element: WebElement) : this(element.findElement(By.xpath("./div/span")).text)
}

data class E2ELocationTrackSelectionListItem(override val name: String, val type: String) : E2ESelectionListItem {
    constructor(
        element: WebElement
    ) : this(
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

class E2EKmPostSelectionList(parentBy: By) :
    E2ESelectionList<E2EKmPostSelectionListItem>(
        listBy = ByChained(parentBy, By.className("km-posts-panel__km-posts")),
        getContent = { e -> E2EKmPostSelectionListItem(e) },
        selectedItemBy = ByChained(parentBy, By.className("km-post-badge--selected")),
    )

class E2ELocationTrackSelectionList(parentBy: By) :
    E2ESelectionList<E2ELocationTrackSelectionListItem>(
        listBy = ByChained(parentBy, byQaId("location-tracks-list")),
        getContent = { e -> E2ELocationTrackSelectionListItem(e) },
        selectedItemBy = ByChained(parentBy, By.className("alignment-badge--selected")),
    )

class E2ETrackNumberSelectionList(parentBy: By) :
    E2ESelectionList<E2ETrackNumberSelectionListItem>(
        listBy = ByChained(parentBy, By.className("track-number-panel__track-numbers")),
        getContent = { e -> E2ETrackNumberSelectionListItem(e) },
        selectedItemBy = ByChained(parentBy, By.className("alignment-badge--selected")),
    )

class E2EReferenceLineSelectionList(parentBy: By) :
    E2ESelectionList<E2EReferenceLineSelectionListItem>(
        listBy = ByChained(parentBy, byQaId("reference-lines-list")),
        itemsBy = By.className("reference-lines-panel__reference-line"),
        getContent = { e -> E2EReferenceLineSelectionListItem(e) },
        selectedItemBy = ByChained(parentBy, By.className("alignment-badge--selected")),
    )

class E2ESwitchesSelectionList(parentBy: By) :
    E2ESelectionList<E2ESwitchSelectionListItem>(
        listBy = ByChained(parentBy, By.className("switch-panel__switches")),
        getContent = { e -> E2ESwitchSelectionListItem(e) },
        selectedItemBy = ByChained(parentBy, By.className("switch-badge--selected")),
    )
