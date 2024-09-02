package fi.fta.geoviite.infra.geometry

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "geoviite.scheduling.tasks.create-element-listing-csv",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class CreateElementListingCsvTask @Autowired constructor(
    val geometryService: GeometryService,
) {
    @Scheduled(cron = "\${geoviite.rail-network-export.schedule}") // TODO move under "task configuration"
    @Scheduled(initialDelay = 1000 * 300, fixedDelay = Long.MAX_VALUE)
    private fun scheduledCreateElementListingCskTask() {
        geometryService.makeElementListingCsv()
    }
}
