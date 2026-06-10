package fi.fta.geoviite.infra.ratko

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["geoviite.ratko.enabled", "geoviite.ratko.tasks.health-check.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class RatkoHealthCheckTask @Autowired constructor(private val ratkoLocalService: RatkoLocalService) {

    @Scheduled(cron = "\${geoviite.ratko.tasks.health-check.cron}")
    fun scheduledRatkoHealthCheck() {
        ratkoLocalService.refreshOnlineStatus()
    }
}
