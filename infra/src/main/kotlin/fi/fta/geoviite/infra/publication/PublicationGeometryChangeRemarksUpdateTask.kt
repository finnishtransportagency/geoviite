package fi.fta.geoviite.infra.publication

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.data-products.tasks.publication-geometry-remarks-update.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class PublicationGeometryChangeRemarksUpdateTask
@Autowired
constructor(private val publicationGeometryChangeRemarksUpdateService: PublicationGeometryChangeRemarksUpdateService) {

    @Scheduled(
        initialDelayString = "\${geoviite.data-products.tasks.publication-geometry-remarks-update.initial-delay}",
        fixedDelayString = "\${geoviite.data-products.tasks.publication-geometry-remarks-update.interval}",
    )
    private fun scheduledUpdateUnprocessedGeometryChangeRemarks() {
        publicationGeometryChangeRemarksUpdateService.updateUnprocessedGeometryChangeRemarks()
    }
}
