package fi.fta.geoviite.infra.geometry

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.data-products.tasks.create-vertical-geometry-listing-csv.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class CreateVerticalGeometryListingCsvTask @Autowired constructor(private val geometryService: GeometryService) {

    @Scheduled(
        initialDelayString = "\${geoviite.data-products.tasks.create-vertical-geometry-listing-csv.initial-delay}"
    )
    fun scheduledCreateVerticalGeometryListingCskTask() {
        geometryService.makeEntireVerticalGeometryListingCsv()
    }

    @Scheduled(cron = "\${geoviite.data-products.tasks.create-vertical-geometry-listing-csv.cron}")
    fun scheduledCreateVerticalGeometryListingCsvTaskCron() {
        geometryService.makeEntireVerticalGeometryListingCsv()
    }
}
