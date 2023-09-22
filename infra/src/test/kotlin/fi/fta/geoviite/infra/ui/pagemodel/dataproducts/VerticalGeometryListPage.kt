package fi.fta.geoviite.infra.ui.pagemodel.dataproducts

import fi.fta.geoviite.infra.ui.pagemodel.common.E2EDropdown
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EFormLayout
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EViewFragment
import fi.fta.geoviite.infra.ui.util.byQaId
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


abstract class E2EDataProductVerticalGeometryListPage : E2EViewFragment(By.className("data-product-view"))  {
    fun layoutListPage(): E2EDataProductLayoutVerticalGeometryListPage {
        childElement(byQaId("select-layout-geometry")).click()
        return E2EDataProductLayoutVerticalGeometryListPage()
    }

    fun planListPage(): E2EDataProductPlanVerticalGeometryListPage {
        childElement(byQaId("select-plan-geometry")).click()
        return E2EDataProductPlanVerticalGeometryListPage()
    }

    fun entireNetworkPage(): E2EDataProductEntireNetworkVerticalGeometryListPage {
        childElement(byQaId("select-entire-rail-network")).click()
        return E2EDataProductEntireNetworkVerticalGeometryListPage()
    }
}

class E2EDataProductPlanVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }
    val resultList: E2EDataProductPlanVerticalGeometryList get() = E2EDataProductPlanVerticalGeometryList()

    val plan: E2EDropdown get() = searchForm.dropdownByQaId("data-products-search-plan")

    val downloadUrl: String get() {
        val text = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")
        return URLDecoder.decode(text, StandardCharsets.UTF_8)
    }

    fun selectPlan(searchString: String) {
        plan.inputValue(searchString)
        plan.select(searchString)
    }
}

class E2EDataProductLayoutVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    private val searchFormElement: WebElement get() = childElement(By.className("data-products__search"))

    val searchForm: E2EFormLayout get() = E2EFormLayout { searchFormElement }

    val locationTrack: E2EDropdown get() = searchForm.dropdownByQaId("data-products-search-location-track")
    val resultList: E2EDataProductLayoutVerticalGeometryList get() = E2EDataProductLayoutVerticalGeometryList()

    val downloadUrl: String get() = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")

    fun selectLocationTrack(searchString: String) {
        locationTrack.inputValue(searchString)
        locationTrack.select(searchString)
    }
}

class E2EDataProductEntireNetworkVerticalGeometryListPage : E2EDataProductVerticalGeometryListPage() {
    val downloadUrl: String get() = childElement(byQaId("vertical-geometry-csv-download")).getAttribute("href")
}
