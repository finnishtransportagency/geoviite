package fi.fta.geoviite.infra.projektivelho

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(PVIntegrationService::class)
@ConditionalOnProperty(name = ["geoviite.projektivelho.tasks.enabled"], havingValue = "true", matchIfMissing = false)
class PVIntegrationServiceScheduler @Autowired constructor(private val pvIntegrationService: PVIntegrationService) {

    @Scheduled(
        initialDelayString = "\${geoviite.projektivelho.tasks.search-poll.initial-delay}",
        fixedRateString = "\${geoviite.projektivelho.tasks.search-poll.interval}",
    )
    private fun scheduledSearch() {
        pvIntegrationService.search()
    }

    @Scheduled(
        initialDelayString = "\${geoviite.projektivelho.tasks.result-poll.initial-delay}",
        fixedRateString = "\${geoviite.projektivelho.tasks.result-poll.interval}",
    )
    private fun scheduledPollAndFetchIfWaiting() {
        pvIntegrationService.pollAndFetchIfWaiting()
    }
}
