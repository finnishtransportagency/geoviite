package fi.fta.geoviite.infra.ui.pagemodel.common

import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.support.pagefactory.ByChained
import waitUntilTextExists
import waitUntilTextIs

abstract class E2EInfoBox(infoboxBy: By) : E2EViewFragment(infoboxBy) {

    protected val title: String
        get() = childText(By.className("infobox__title"))

    private fun getValueBy(fieldQaId: String) = ByChained(byQaId(fieldQaId), By.className("infobox__field-value"))

    protected fun getEnumValueForField(fieldQaId: String): String {
        logger.info("Get enum value for field $fieldQaId")

        return childElement(ByChained(getValueBy(fieldQaId), By.tagName("span"))).getAttribute("qa-id").let { value ->
            checkNotNull(value) { "could not find value for fieldQaId=$fieldQaId" }
        }
    }

    protected fun getValueForField(fieldQaId: String): String {
        logger.info("Get value for field $fieldQaId")

        return childText(getValueBy(fieldQaId))
    }

    protected fun getValueWhenFieldHasValue(fieldQaId: String): String {
        logger.info("Get value for field $fieldQaId when it has value")

        val by = getValueBy(fieldQaId)
        waitUntilTextExists(childBy(by))

        return childText(by)
    }

    protected fun editFields(): E2EInfoBox = apply {
        logger.info("Enable editing")

        clickChild(byQaId("infobox-edit-button"))
    }

    protected fun waitUntilValueChangesForField(fieldQaId: String, targetValue: String): E2EInfoBox = apply {
        logger.info("Wait for field $fieldQaId to change value to $targetValue")

        waitUntilTextIs(childBy(getValueBy(fieldQaId)), targetValue)
    }

    fun waitUntilLoaded(): E2EInfoBox = apply { waitUntilChildInvisible(By.className("spinner")) }
}
