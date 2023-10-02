package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

class E2EElementListPage : E2EViewFragment(By.className("data-product-view")) {

    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val locationTrack: E2EDropdown get() = searchForm.dropdown("Sijaintiraide")
    val startAddress: E2ETextInput get() = searchForm.textInput("Rataosoitteen alku")
    val endAddress: E2ETextInput get() = searchForm.textInput("Rataosoitteen loppu")
    val line: E2ECheckbox get() = searchForm.checkBoxByLabel("Suora")
    val curve: E2ECheckbox get() = searchForm.checkBoxByLabel("Kaari")
    val clothoid: E2ECheckbox get() = searchForm.checkBoxByLabel("Siirtymäkaari")
    val missingGeometry: E2ECheckbox get() = searchForm.checkBoxByLabel("Puuttuva osuus")
    val resultList: E2EDataProductElementList get() = E2EDataProductElementList()

    fun selectLocationTrack(searchString: String) {
        locationTrack.inputValue(searchString)
        locationTrack.select(searchString)
    }

}
