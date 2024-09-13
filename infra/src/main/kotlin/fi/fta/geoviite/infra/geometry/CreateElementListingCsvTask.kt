package fi.fta.geoviite.infra.geometry

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.data-products.tasks.create-element-listing-csv.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class CreateElementListingCsvTask @Autowired constructor(private val geometryService: GeometryService) {

    @Scheduled(initialDelayString = "\${geoviite.data-products.tasks.create-element-listing-csv.initial-delay}")
    @Scheduled(cron = "\${geoviite.data-products.tasks.create-element-listing-csv.cron}")
    private fun scheduledCreateElementListingCskTask() {
        geometryService.makeElementListingCsv()
    }
}
