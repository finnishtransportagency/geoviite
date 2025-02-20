package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.configuration.CachePreloadService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import withUser

val CHANGE_REMARKS = UserName.of("CHANGE_REMARKS")

@Component
@ConditionalOnProperty(
    name = ["geoviite.data-products.tasks.publication-geometry-remarks-update.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class PublicationGeometryChangeRemarksUpdateTask
@Autowired
constructor(
    private val cachePreloadService: CachePreloadService,
    private val publicationGeometryChangeRemarksUpdateService: PublicationGeometryChangeRemarksUpdateService,
) {

    @Scheduled(
        initialDelayString = "\${geoviite.data-products.tasks.publication-geometry-remarks-update.initial-delay}",
        fixedDelayString = "\${geoviite.data-products.tasks.publication-geometry-remarks-update.interval}",
    )
    fun scheduledUpdateUnprocessedGeometryChangeRemarks() {
        if (!cachePreloadService.preloadInProgress) {
            withUser(CHANGE_REMARKS) {
                publicationGeometryChangeRemarksUpdateService.updateUnprocessedGeometryChangeRemarks()
            }
        }
    }
}
