package fi.fta.geoviite.infra.ui.pagemodel

import fi.fta.geoviite.infra.ui.pagemodel.common.Accordion
import fi.fta.geoviite.infra.ui.pagemodel.map.MapLayerSettingsPanel
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

class TrackLayoutSwitch(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findElement(By.xpath("./span/span")).text
    override fun toString(): String = name()
}

class TrackLayoutKmPost(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findElement(By.xpath("./div/span")).text
    override fun toString(): String = name()
}

class TrackLayoutTrackNumber(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.text
    override fun toString(): String = name()
}

class TrackLayoutAlignment(element: WebElement): TrackLayoutElement(element) {
    override fun name(): String = element.findElement(By.xpath("./div/span")).text
    fun type(): String = element.findElement(By.xpath("./span")).text
    override fun toString(): String = name()
}

class GeometryPlanAccordio(by: By) : Accordion(by) {

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