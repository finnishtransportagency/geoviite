package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.findMandatoryByXpath
import fi.fta.geoviite.infra.ui.pagemodel.common.Accordion
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO: These list elements hold a reference to the WebElement, risking staleness
//  See PublicationList for an example on how to handle lists
//  In general: the list object should handle actions and row data can be given out as immutable data classes that don't know the WebElement
//  If these elements need activity of their own, you'll need them to be findable so that the element reference can be a dynamic fetch
@Deprecated("Element risks staleness")
abstract class TrackLayoutElement(val element: WebElement) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun select(select: Boolean = true) {
            logger.info("Select '${name()}' to $select")
            val selected = isSelected()
            if (selected != select) {
                logger.info("Track layout element '${name()}' is $selected, setting it to $select")
                element.click()
            } else {
                logger.info("Track layout element '${name()}' is already $selected")
            }
    }

    abstract fun name(): String

    private fun isSelected(): Boolean =
        element.findElement(By.xpath("./*[1]")).getAttribute("class").contains("selected")

}

// TODO: These elements don't hold a WebElement reference, so they work. However they should be refactored as data classes
class TrackLayoutSwitch(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findMandatoryByXpath("./span/span", "name").text
    override fun toString(): String = name()
}

class TrackLayoutKmPost(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findMandatoryByXpath("./div/span", "name").text
    override fun toString(): String = name()
}

class TrackLayoutTrackNumber(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.text
    override fun toString(): String = name()
}

class TrackLayoutAlignment(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findMandatoryByXpath("./div/span", "name").text
    fun type(): String = element.findMandatoryByXpath("./span", "type").text
    override fun toString(): String = name()
}

class GeometryPlanAccordion(by: By) : Accordion(by) {

    private val RAITEET = "Raiteet"
    private val KMPISTEET = "Tasakilometripisteet"
    private val VAIHTEET = "Vaihteet"

    fun open() = this {
        toggleAccordion() }

    fun alignments(): Accordion {
        logger.info("Get geometry alignments")
        return subAccordioByTitle(RAITEET)
    }

    fun openAlignments() = this {
        logger.info("Open alignments accordion")
        subAccordioByTitle(RAITEET).toggleAccordion()
    }

    fun selecAlignment(alignment: String) =
        open()
            .openAlignments()
            .selectListItem(alignment)

    fun kmPosts(): Accordion {
        logger.info("Get geometry km-posts")
        return subAccordioByTitle(KMPISTEET)
    }

    fun openKmPosts() = this {
        logger.info("Open KM-posts")
        subAccordioByTitle(KMPISTEET).toggleAccordion()
    }

    fun selectKmPost(kmPost: String) =
        open()
            .openKmPosts()
            .selectListItem(kmPost)

    fun switches(): Accordion {
        logger.info("Get geometry switches")
        return subAccordioByTitle(VAIHTEET)
    }
    fun openSwitches() = this {
        logger.info("Open switches")
        subAccordioByTitle(VAIHTEET).toggleAccordion()
    }

    fun selectSwitch(switch: String) =
        open()
            .openSwitches()
            .selectListItem(switch)

}
