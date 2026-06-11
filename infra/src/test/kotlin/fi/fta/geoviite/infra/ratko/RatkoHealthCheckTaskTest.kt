package fi.fta.geoviite.infra.ratko

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RatkoHealthCheckTaskTest {

    @Test
    fun `initialRatkoHealthCheck calls refreshOnlineStatus on startup`() {
        val service = mock(RatkoLocalService::class.java)
        val task = RatkoHealthCheckTask(service)

        task.initialRatkoHealthCheck()

        verify(service).refreshOnlineStatus()
    }
}
