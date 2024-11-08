package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.LayoutBranch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import withUser

@Component
@ConditionalOnProperty(
    name = ["geoviite.ratko.enabled", "geoviite.ratko.tasks.push.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class RatkoPushTask @Autowired constructor(private val ratkoService: RatkoService) {
    companion object {
        private val ratkoPushTaskUserName = UserName.of("RATKO_PUSH")
    }

    @Scheduled(cron = "\${geoviite.ratko.tasks.push.cron}")
    private fun scheduledRatkoPush() {
        withUser(ratkoPushTaskUserName) {
            // Don't retry failed on auto-push
            ratkoService.pushChangesToRatko(LayoutBranch.main, retryFailed = false)
        }
    }
}
