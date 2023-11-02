package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.DIALOG_BY
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.expectToast
import fi.fta.geoviite.infra.ui.util.byText
import org.openqa.selenium.By

class E2ELocationTrackEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    enum class State(val uiText: String) {
        IN_USE("Käytössä"),
        NOT_IN_USE("Käytöstä poistettu"),
        DELETED("Poistettu")
    }

    enum class Type(val uiText: String) {
        MAIN("Pääraide"),
        SIDE("Sivuraide"),
        TRAP("Turvaraide"),
        CHORD("Kujaraide"),
    }

    enum class TopologicalConnectivity(val uiText: String) {
        NONE("Ei kytketty"),
        START("Raiteen alku"),
        END("Raiteen loppu"),
        START_AND_END("Raiteen alku ja loppu")
    }

    enum class DescriptionSuffix(val uiText: String) {
        NONE("Ei lisäosaa"),
        SWITCH_TO_SWITCH("Vaihde alussa - vaihde lopussa"),
        SWITCH_TO_BUFFER("Vaihde - Puskin")
    }

    fun setName(name: String): E2ELocationTrackEditDialog = apply {
        content.inputFieldValueByLabel("Sijaintiraidetunnus", name)
    }

    fun selectTrackNumber(trackNumber: String): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValueByLabel("Ratanumero", trackNumber)
    }

    fun selectState(state: State): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValueByLabel("Tila", state.uiText)
    }

    fun selectType(type: Type): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValueByLabel("Raidetyyppi", type.uiText)
    }

    fun setDescription(description: String): E2ELocationTrackEditDialog = apply {
        content.inputFieldValueByLabel("Kuvauksen perusosa", description)
    }

    fun setDescriptionSuffix(descriptionSuffix: DescriptionSuffix): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValueByLabel("Kuvauksen lisäosa", descriptionSuffix.uiText)
    }

    fun selectTopologicalConnectivity(topologicalConnectivity: TopologicalConnectivity): E2ELocationTrackEditDialog =
        apply {
            content.selectDropdownValueByLabel(label = "Topologinen kytkeytyminen", topologicalConnectivity.uiText)
        }

    fun save() = expectToast {
        waitUntilClosed {
            val isDeleted = content.getValueForFieldByLabel("Tila") == State.DELETED.uiText
            clickPrimaryButton()

            if (isDeleted) {
                clickChild(
                    By.xpath("following-sibling::div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
                )
            }
        }
    }

    fun cancel() = waitUntilClosed {
        clickSecondaryButton()
    }
}

class E2ETrackNumberEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    enum class State(val uiText: String) {
        IN_USE("Käytössä"),
        NOT_IN_USE("Käytöstä poistettu"),
        DELETED("Poistettu")
    }

    fun setName(name: String): E2ETrackNumberEditDialog = apply {
        content.inputFieldValueByLabel("Tunnus", name)
    }

    fun selectState(state: State): E2ETrackNumberEditDialog = apply {
        content.selectDropdownValueByLabel("Tila", state.uiText)
    }

    fun setDescription(description: String): E2ETrackNumberEditDialog = apply {
        content.inputFieldValueByLabel("Kuvaus", description)
    }

    fun save() = waitUntilClosed {
        val isDeleted = content.getValueForFieldByLabel("Tila") == E2ELocationTrackEditDialog.State.DELETED.uiText
        clickButton(byText("Tallenna"))

        if (isDeleted) {
            clickChild(
                By.xpath("following-sibling::div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
            )
        }
    }
}

class E2EKmPostEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {

    enum class State(val uiText: String) {
        PLANNED("Suunniteltu"),
        IN_USE("Käytössä"),
        NOT_IN_USE("Käytöstä poistettu"),
    }

    fun setName(name: String): E2EKmPostEditDialog = apply {
        content.inputFieldValueByLabel("Tasakmpistetunnus", name)
    }

    fun selectState(state: State): E2EKmPostEditDialog = apply {
        content.selectDropdownValueByLabel("Tila", state.uiText)
    }

    fun save() = expectToast {
        waitUntilClosed {
            clickButton(byText("Tallenna"))
        }
    }

    fun cancel() = waitUntilClosed {
        clickSecondaryButton()
    }
}

class E2ELayoutSwitchEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {

    enum class StateCategory(val uiText: String) {
        NOT_EXISTING("Poistunut kohde"),
        EXISTING("Olemassa oleva kohde")
    }

    fun setName(name: String): E2ELayoutSwitchEditDialog = apply {
        content.inputFieldValueByLabel("Vaihdetunnus", name)
    }

    fun selectStateCategory(stateCategory: StateCategory): E2ELayoutSwitchEditDialog = apply {
        content.selectDropdownValueByLabel("Tilakategoria", stateCategory.uiText)
    }

    fun save() = expectToast {
        waitUntilClosed {
            val isNotExisting = content.getValueForFieldByLabel("Tilakategoria") == StateCategory.NOT_EXISTING.uiText
            clickButton(byText("Tallenna"))

            if (isNotExisting) {
                clickChild(
                    By.xpath("following-sibling::div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
                )
            }
        }
    }
}
