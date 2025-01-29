import org.junit.jupiter.api.Assertions
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

fun WebElement.childExists(byCondition: By) = this.findElements(byCondition).isNotEmpty()

fun WebElement.childNotExists(byCondition: By) = this.findElements(byCondition).isEmpty()

fun WebElement.getInnerHtml(): String = getNonNullAttribute("innerHTML")

fun WebElement.getNonNullAttribute(attribute: String): String =
    getAttribute(attribute) ?: Assertions.fail("Attribute $attribute is null in element $this")
