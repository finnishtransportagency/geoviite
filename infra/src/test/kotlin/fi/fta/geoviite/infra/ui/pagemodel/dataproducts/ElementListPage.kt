package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.*
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

abstract class E2EElementListPage : E2EViewFragment(By.className("data-product-view")) {
    fun layoutListPage(): E2EDataProductLayoutElementListPage {
        childElement(byQaId("select-layout-geometry")).click()
        return E2EDataProductLayoutElementListPage()
    }
    fun planListPage(): E2EDataProductPlanElementListPage {
        childElement(byQaId("select-plan-geometry")).click()
        return E2EDataProductPlanElementListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkElementListPage {
        childElement(byQaId("select-entire-rail-network")).click()
        return E2EDataProductEntireNetworkElementListPage()
    }
}

class E2EDataProductPlanElementListPage : E2EElementListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val resultList: E2EDataProductPlanElementList get() = E2EDataProductPlanElementList()

    val plan: E2EDropdown get() = searchForm.dropdownByQaId("data-products-search-plan")

    val line: E2ECheckbox get() = searchForm.checkBoxByLabel("Suora")
    val curve: E2ECheckbox get() = searchForm.checkBoxByLabel("Kaari")
    val clothoid: E2ECheckbox get() = searchForm.checkBoxByLabel("Siirtymäkaari")
    val downloadUrl: String get() {
        val text = childElement(byQaId("plan-element-list-csv-download")).getAttribute("href")
        return URLDecoder.decode(text, StandardCharsets.UTF_8)
    }

    fun selectPlan(searchString: String) {
        plan.inputValue(searchString)
        plan.select(searchString)
    }
}

class E2EDataProductLayoutElementListPage : E2EElementListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val locationTrack: E2EDropdown get() = searchForm.dropdown("Sijaintiraide")
    val startAddress: E2ETextInput get() = searchForm.textInput("Rataosoitteen alku")
    val endAddress: E2ETextInput get() = searchForm.textInput("Rataosoitteen loppu")
    val line: E2ECheckbox get() = searchForm.checkBoxByLabel("Suora")
    val curve: E2ECheckbox get() = searchForm.checkBoxByLabel("Kaari")
    val clothoid: E2ECheckbox get() = searchForm.checkBoxByLabel("Siirtymäkaari")
    val missingGeometry: E2ECheckbox get() = searchForm.checkBoxByLabel("Puuttuva osuus")
    val downloadUrl: String get() = childElement(byQaId("location-track-element-list-csv-download")).getAttribute("href")
    val resultList: E2EDataProductLayoutElementList get() = E2EDataProductLayoutElementList()

    fun selectLocationTrack(searchString: String) {
        locationTrack.inputValue(searchString)
        locationTrack.select(searchString)
    }
}


class E2EDataProductEntireNetworkElementListPage : E2EElementListPage() {
    val downloadUrl: String get() = childElement(byQaId("element-list-csv-download")).getAttribute("href")
}
