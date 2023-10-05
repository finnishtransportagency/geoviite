package fi.fta.geoviite.infra.ui

import browser
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.ui.pagemodel.common.E2EAppBar
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.E2EFrontPage
import openBrowser
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import java.util.*


const val UI_TEST_USER = "UI_TEST_USER"

@ExtendWith(E2ETestWatcher::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class SeleniumTest : DBTestBase(UI_TEST_USER) {

    @Value("\${geoviite.e2e.url}")
    val startUrlProp: String? = null
    val startUrl: String by lazy { requireNotNull(startUrlProp) }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
    }


    val geoviite: E2EFrontPage
        get() {
            browser().navigate().to(startUrl)
            return E2EFrontPage()
        }

    val navigationBar: E2EAppBar get() = E2EAppBar()

    fun startGeoviite() {
        logger.info("Navigate to Geoviite $startUrl")
        openBrowser()
        browser().navigate().to(startUrl)
    }

    fun goToFrontPage() = navigationBar.goToFrontPage()

    fun goToMap() = navigationBar.goToMap()

    fun goToInfraModelPage() = navigationBar.goToInfraModel()

    protected fun clearAllTestData() {
       deleteFromTables(
            schema = "publication", tables = arrayOf(
                "km_post",
                "location_track",
                "reference_line",
                "switch",
                "track_number",
                "track_number_km",
                "publication",
            )
        )

        deleteFromTables(
            schema = "layout", tables = arrayOf(
                "alignment",
                "alignment_version",
                "km_post",
                "km_post_version",
                "location_track",
                "location_track_version",
                "reference_line",
                "reference_line_version",
                "switch",
                "switch_version",
                "switch_joint",
                "switch_joint_version",
                "track_number",
                "track_number_version",
                "segment_version",
                "segment_geometry",
            )
        )

        deleteFromTables(
            schema = "geometry", tables = arrayOf(
                "alignment",
                "cant_point",
                "element",
                "plan",
                "plan_application",
                "plan_application_version",
                "plan_file",
                "plan_project",
                "plan_project_version",
                "plan_author",
                "plan_author_version",
                "plan_version",
                "switch",
                "switch_joint",
                "vertical_intersection"
            )
        )
    }
}
