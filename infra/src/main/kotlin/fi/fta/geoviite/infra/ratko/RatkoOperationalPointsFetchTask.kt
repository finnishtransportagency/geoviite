package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.UserName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import withUser

@Component
@ConditionalOnProperty(
    name = ["geoviite.ratko.enabled", "geoviite.ratko.tasks.operational-points-fetch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class RatkoOperationalPointsFetchTask @Autowired constructor(private val ratkoService: RatkoService) {

    companion object {
        private val ratkoOperationalPointTaskUserName = UserName.of("RATKO_FETCH")
    }

    @Scheduled(cron = "\${geoviite.ratko.tasks.operational-points-fetch.cron}")
    fun scheduledRatkoOperationalPointsFetch() {
        withUser(ratkoOperationalPointTaskUserName, ratkoService::updateOperationalPointsFromRatko)
    }
}
