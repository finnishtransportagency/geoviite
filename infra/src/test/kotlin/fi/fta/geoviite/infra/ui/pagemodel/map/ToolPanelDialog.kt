package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.defaultDialogBy
import org.openqa.selenium.By

class E2ELocationTrackEditDialog(by: By = defaultDialogBy) : E2EDialog(by) {
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

    fun setName(name: String): E2ELocationTrackEditDialog = apply {
        content.inputFieldValue("Sijaintiraidetunnus", name)
    }

    fun selectTrackNumber(trackNumber: String): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValue("Ratanumero", trackNumber)
    }

    fun selectState(state: State): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValue("Tila", state.uiText)
    }

    fun selectType(type: Type): E2ELocationTrackEditDialog = apply {
        content.selectDropdownValue("Raidetyyppi", type.uiText)
    }

    fun setDescription(description: String): E2ELocationTrackEditDialog = apply {
        content.inputFieldValue("Kuvaus", description)
    }

    fun selectTopologicalConnectivity(topologicalConnectivity: TopologicalConnectivity): E2ELocationTrackEditDialog =
        apply {
            content.selectDropdownValue(label = "Topologinen kytkeytyminen", topologicalConnectivity.uiText)
        }

    fun save() = waitUntilClosed {
        val isDeleted = content.getValueForField("Tila") == State.DELETED.uiText
        clickPrimaryButton()

        if (isDeleted) {
            clickChild(
                By.xpath("div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
            )
        }
    }

    fun cancel() = waitUntilClosed {
        clickSecondaryButton()
    }
}

class E2ETrackNumberEditDialog(by: By = defaultDialogBy) : E2EDialog(by) {
    enum class State(val uiText: String) {
        IN_USE("Käytössä"),
        NOT_IN_USE("Käytöstä poistettu"),
        DELETED("Poistettu")
    }

    fun setName(name: String): E2ETrackNumberEditDialog = apply {
        content.inputFieldValue("Tunnus", name)
    }

    fun selectState(state: State): E2ETrackNumberEditDialog = apply {
        content.selectDropdownValue("Tila", state.uiText)
    }

    fun setDescription(description: String): E2ETrackNumberEditDialog = apply {
        content.inputFieldValue("Kuvaus", description)
    }

    fun save() = waitUntilClosed {
        val isDeleted = content.getValueForField("Tila") == E2ELocationTrackEditDialog.State.DELETED.uiText
        clickButtonByText("Tallenna")

        if (isDeleted) {
            clickChild(
                By.xpath("div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
            )
        }
    }
}

class E2EKmPostEditDialog(by: By = defaultDialogBy) : E2EDialog(by) {

    enum class State(val uiText: String) {
        PLANNED("Suunniteltu"),
        IN_USE("Käytössä"),
        NOT_IN_USE("Käytöstä poistettu"),
    }

    fun setName(name: String): E2EKmPostEditDialog = apply {
        content.inputFieldValue("Tasakmpistetunnus", name)
    }

    fun selectState(state: State): E2EKmPostEditDialog = apply {
        content.selectDropdownValue("Tila", state.uiText)
    }

    fun save() = waitUntilClosed { 
        clickButtonByText("Tallenna")
    }

    fun cancel() = waitUntilClosed {
        clickSecondaryButton()
    }
}

class E2ELayoutSwitchEditDialog(by: By = defaultDialogBy) : E2EDialog(by) {

    enum class StateCategory(val uiText: String) {
        NOT_EXISTING("Poistunut kohde"),
        EXISTING("Olemassa oleva kohde")
    }

    fun setName(name: String): E2ELayoutSwitchEditDialog = apply {
        content.inputFieldValue("Vaihdetunnus", name)
    }

    fun selectStateCategory(stateCategory: StateCategory): E2ELayoutSwitchEditDialog = apply {
        content.selectDropdownValue("Tilakategoria", stateCategory.uiText)
    }

    fun save() = waitUntilClosed { 
        val isNotExisting = content.getValueForField("Tilakategoria") == StateCategory.NOT_EXISTING.uiText
        clickButtonByText("Tallenna")

        if (isNotExisting) {
            clickChild(
                By.xpath("following-sibling::div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]")
            )
        }
    }
}
