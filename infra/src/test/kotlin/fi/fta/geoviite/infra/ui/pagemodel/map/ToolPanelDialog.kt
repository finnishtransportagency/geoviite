package fi.fta.geoviite.infra.ui.pagemodel.map

import fi.fta.geoviite.infra.ui.pagemodel.common.DIALOG_BY
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDialog
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2ETextInput
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.ui.util.dateFormat
import java.time.LocalDate
import org.openqa.selenium.By

class E2ELocationTrackEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    enum class State {
        IN_USE,
        NOT_IN_USE,
        DELETED,
    }

    enum class Type {
        MAIN,
        SIDE,
        TRAP,
        CHORD,
    }

    enum class TopologicalConnectivity {
        NONE,
        START,
        END,
        START_AND_END,
    }

    enum class DescriptionSuffix {
        NONE,
        SWITCH_TO_SWITCH,
        SWITCH_TO_BUFFER,
    }

    private val nameInput: E2ETextInput by lazy { childTextInput(byQaId("location-track-name")) }

    private val trackNumberDropdown: E2EDropdown by lazy { childDropdown(byQaId("location-track-track-number")) }

    val ownerDropdown: E2EDropdown by lazy { childDropdown(byQaId("location-track-dialog.owner")) }

    private val stateDropdown: E2EDropdown by lazy { childDropdown(byQaId("location-track-state")) }

    private val typeDropdown: E2EDropdown by lazy { childDropdown(byQaId("location-track-type")) }

    private val descriptionBaseInput: E2ETextInput by lazy { childTextInput(byQaId("location-track-description-base")) }

    private val descriptionSuffixDropdown: E2EDropdown by lazy {
        childDropdown(byQaId("location-track-description-suffix"))
    }

    private val topologicalConnectivityDropdown: E2EDropdown by lazy {
        childDropdown(byQaId("location-track-topological-connectivity"))
    }

    fun setName(name: String): E2ELocationTrackEditDialog = apply {
        logger.info("Set name $name")

        nameInput.replaceValue(name)
    }

    fun selectTrackNumber(trackNumber: String): E2ELocationTrackEditDialog = apply {
        logger.info("Select track number $trackNumber")

        trackNumberDropdown.selectByName(trackNumber)
    }

    fun selectState(state: State): E2ELocationTrackEditDialog = apply {
        logger.info("Select state $state")

        stateDropdown.selectByQaId(state.name)
    }

    fun selectType(type: Type): E2ELocationTrackEditDialog = apply {
        logger.info("Select type $type")

        typeDropdown.selectByQaId(type.name)
    }

    fun setDescription(description: String): E2ELocationTrackEditDialog = apply {
        logger.info("Set description $description")

        descriptionBaseInput.replaceValue(description)
    }

    fun setDescriptionSuffix(descriptionSuffix: DescriptionSuffix): E2ELocationTrackEditDialog = apply {
        logger.info("Set description suffix $descriptionSuffix")

        descriptionSuffixDropdown.selectByQaId(descriptionSuffix.name)
    }

    fun selectTopologicalConnectivity(topologicalConnectivity: TopologicalConnectivity): E2ELocationTrackEditDialog =
        apply {
            logger.info("Select topological connectivity $topologicalConnectivity")

            topologicalConnectivityDropdown.selectByQaId(topologicalConnectivity.name)
        }

    fun save() = waitUntilClosed {
        logger.info("Save location track changes")

        val isDeleted = stateDropdown.value == "Poistettu"
        clickPrimaryButton()

        if (isDeleted) {
            clickChild(
                By.xpath(
                    "following-sibling::div[contains(@class, 'dialog')]" +
                        "//button[contains(@class, 'button--primary')]"
                )
            )
        }
    }

    fun cancel() = waitUntilClosed {
        logger.info("Discard location track changes")

        clickSecondaryButton()
    }
}

class E2ETrackNumberEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {
    enum class State {
        IN_USE,
        NOT_IN_USE,
        DELETED,
    }

    private val nameInput: E2ETextInput by lazy { childTextInput(byQaId("track-number-name")) }
    private val stateDropdown: E2EDropdown by lazy { childDropdown(byQaId("track-number-state")) }
    private val descriptionInput: E2ETextInput by lazy { childTextInput(byQaId("track-number-description")) }

    fun setName(name: String): E2ETrackNumberEditDialog = apply {
        logger.info("Set name $name")

        nameInput.replaceValue(name)
    }

    fun selectState(state: State): E2ETrackNumberEditDialog = apply {
        logger.info("Select state $state")

        stateDropdown.selectByQaId(state.name)
    }

    fun setDescription(description: String): E2ETrackNumberEditDialog = apply {
        logger.info("Set description $description")

        descriptionInput.replaceValue(description)
    }

    fun save() = waitUntilClosed {
        logger.info("Save track number changes")

        val isDeleted = stateDropdown.value == "Poistettu"
        clickButton(byQaId("save-track-number-changes"))

        if (isDeleted) {
            clickChild(
                By.xpath(
                    "following-sibling::div[contains(@class, 'dialog')]/" +
                        "/button[contains(@class, 'button--primary')]"
                )
            )
        }
    }
}

class E2EKmPostEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {

    enum class State {
        IN_USE,
        NOT_IN_USE,
    }

    private val nameInput: E2ETextInput by lazy { childTextInput(byQaId("km-post-number")) }
    private val stateDropdown: E2EDropdown by lazy { childDropdown(byQaId("km-post-state")) }

    fun setName(name: String): E2EKmPostEditDialog = apply {
        logger.info("Set name $name")

        nameInput.replaceValue(name)
    }

    fun selectState(state: State): E2EKmPostEditDialog = apply {
        logger.info("Select state $state")

        stateDropdown.selectByQaId(state.name)
    }

    fun save() = waitUntilClosed {
        logger.info("Save km post changes")

        clickButton(byQaId("save-km-post-changes"))
    }

    fun cancel() = waitUntilClosed {
        logger.info("Discard km post changes")

        clickSecondaryButton()
    }
}

class E2ELayoutSwitchEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {

    enum class StateCategory {
        NOT_EXISTING,
        EXISTING,
    }

    private val nameInput: E2ETextInput by lazy { childTextInput(byQaId("switch-name")) }
    private val stateDropdown: E2EDropdown by lazy { childDropdown(byQaId("switch-state")) }

    fun setName(name: String): E2ELayoutSwitchEditDialog = apply {
        logger.info("Set name $name")

        nameInput.replaceValue(name)
    }

    fun selectStateCategory(stateCategory: StateCategory): E2ELayoutSwitchEditDialog = apply {
        logger.info("Select state $stateCategory")

        stateDropdown.selectByQaId(stateCategory.name)
    }

    fun save() = waitUntilClosed {
        logger.info("Save switch changes")

        val isNotExisting = stateDropdown.value == "Poistunut kohde"
        clickButton(byQaId("save-switch-changes"))

        if (isNotExisting) {
            clickChild(
                By.xpath(
                    "following-sibling::div[contains(@class, 'dialog')]//button[contains(@class, 'button--primary')]"
                )
            )
        }
    }
}

class E2EWorkspaceEditDialog(dialogBy: By = DIALOG_BY) : E2EDialog(dialogBy) {

    private val nameInput: E2ETextInput by lazy { childTextInput(byQaId("workspace-dialog-name")) }
    private val dateInput: E2ETextInput by lazy { childTextInput(byQaId("workspace-dialog-date")) }

    fun setName(name: String): E2EWorkspaceEditDialog = apply {
        logger.info("Set name $name")

        nameInput.replaceValue(name)
    }

    fun setDate(date: LocalDate): E2EWorkspaceEditDialog = apply {
        logger.info("Set date $date")

        dateInput.replaceValue(date.format(dateFormat))
    }

    fun save() = waitUntilClosed {
        logger.info("Save workspace changes")

        clickButton(byQaId("workspace-dialog-save"))
    }

    fun cancel() = waitUntilClosed {
        logger.info("Discard workspace changes")

        clickSecondaryButton()
    }
}
