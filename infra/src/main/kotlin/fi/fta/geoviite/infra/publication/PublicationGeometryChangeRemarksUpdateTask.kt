package fi.fta.geoviite.infra.publication

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "geoviite.scheduling.tasks.publication-geometry-change-remarks-update",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PublicationGeometryChangeRemarksUpdateTask @Autowired constructor(
    private val publicationGeometryChangeRemarksUpdateService: PublicationGeometryChangeRemarksUpdateService,
) {

    @Scheduled(initialDelay = 1000 * 30, fixedDelay = 24 * 60 * 60 * 1000)
    fun scheduledUpdateUnprocessedGeometryChangeRemarks() {
        publicationGeometryChangeRemarksUpdateService.updateUnprocessedGeometryChangeRemarks()
    }
}
