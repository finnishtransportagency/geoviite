import org.openqa.selenium.By
import org.openqa.selenium.WebElement

fun WebElement.childExists(byCondition: By) = this.findElements(byCondition).isNotEmpty()

fun WebElement.childNotExists(byCondition: By) = this.findElements(byCondition).isEmpty()

fun WebElement.getInnerHtml(): String = getAttribute("innerHTML")
