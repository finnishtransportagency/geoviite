package fi.fta.geoviite.infra.ui.pagemodel.common

import clickWhenClickable
import exists
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.pagefactory.ByChained
import org.openqa.selenium.support.ui.ExpectedConditions
import tryWait
import waitUntilInvisible
import waitUntilVisible

private val CONTAINER_BY: By = By.className("dropdown__list-container")

data class E2EDropdownListItem(val name: String, val qaId: String?)

class E2EDropdownList :
    E2EList<E2EDropdownListItem>(
        CONTAINER_BY,
        By.className("dropdown__list-item"),
        selectedItemBy = null, // Add selectedItemBy if ever needed, as dropdowns can have selected items too
    ) {
    override fun getItemContent(item: WebElement): E2EDropdownListItem {
        return E2EDropdownListItem(item.text, item.getAttribute("qa-id"))
    }

    fun selectByQaId(qaId: String) = apply { select { i -> i.qaId == qaId } }

    fun selectByName(name: String) = apply { select { i -> i.name.contains(name) } }
}

class E2EDropdown(dropdownBy: By) : E2EViewFragment(dropdownBy) {

    private val inputBy: By = By.tagName("input")

    private val input: E2ETextInput = childTextInput(inputBy)

    private val optionsList: E2EDropdownList by lazy { E2EDropdownList() }

    val options: List<E2EDropdownListItem>
        get() = optionsList.items

    val value: String
        get() = input.value

    fun open(): E2EDropdown = apply {
        logger.info("Open dropdown")

        if (!exists(CONTAINER_BY)) {
            clickChild(By.className("dropdown__header"))
            waitUntilVisible(CONTAINER_BY)
        }
    }

    fun selectByName(name: String): E2EDropdown = apply {
        logger.info("Select item $name")

        open()
        optionsList.selectByName(name)
        waitUntilInvisible(CONTAINER_BY)
    }

    fun selectByQaId(qaId: String): E2EDropdown = apply {
        logger.info("Select item $qaId")

        open()
        optionsList.selectByQaId(qaId)
        waitUntilInvisible(CONTAINER_BY)
    }

    fun selectFromDynamicByName(name: String): E2EDropdown = apply {
        logger.info("Select item $name from dynamic dropdown")

        input.inputValue(name)
        // Can't use optionsList directly, as it comes and goes for a while before it's stable
        // This can be removed once the dropdown component itself has been fixed
        tryWait(
            ExpectedConditions.textToBePresentInElementLocated(
                ByChained(CONTAINER_BY, By.className("dropdown__list-item"), By.className("dropdown__list-item-text")),
                name,
            )
        ) {
            "Option list does not contain item $name"
        }

        optionsList.selectByName(name)
    }

    fun new() = apply {
        logger.info("Add new option")

        open()
        clickWhenClickable(By.cssSelector(".dropdown__add-new-container > button"))
    }

    fun search(text: String): E2EDropdown = apply {
        logger.info("Search options with text $text")

        input.inputValue(text)
    }

    fun clearSearch(): E2EDropdown = apply {
        logger.info("Clear dropdown input")

        input.clear()
    }
}
