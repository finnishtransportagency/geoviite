package fi.fta.geoviite.infra.ui.pagemodel.inframodel

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By

class E2EInfraModelForm : E2EViewFragment(By.className("infra-model-upload__form-column")) {
    fun saveAsNew() {
        logger.info("Saving infra model to database...")
        save(true)
        waitAndClearToast("infra-model.upload.success")
    }

    fun save(expectConfirm: Boolean = false) {
        clickChild(byQaId("infra-model-save-button"))
        if (expectConfirm) confirmSaving()
    }

    val metaFormGroup: E2EMetaFormGroup by lazy {
        childComponent(byQaId("im-form-project"), ::E2EMetaFormGroup)
    }

    val locationFormGroup: E2ELocationFormGroup by lazy {
        childComponent(byQaId("im-form-location"), ::E2ELocationFormGroup)
    }

    val qualityFormGroup: E2EQualityFormGroup by lazy {
        childComponent(byQaId("im-form-phase-quality"), ::E2EQualityFormGroup)
    }

    val logFormGroup: E2ELogFormGroup by lazy {
        childComponent(byQaId("im-form-log"), ::E2ELogFormGroup)
    }

    private fun confirmSaving() {
        //Confirm saving if confirmation dialog appears
        E2EConfirmDialog().confirm()
    }

}
