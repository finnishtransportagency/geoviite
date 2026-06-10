package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class RatkoLocalServiceTest {

    private fun serviceWithClient(client: RatkoClient?) =
        RatkoLocalService(
            ratkoClient = client,
            ratkoPushDao = mock(RatkoPushDao::class.java),
            ratkoOperationalPointDao = mock(RatkoOperationalPointDao::class.java),
            operationalPointDao = mock(OperationalPointDao::class.java),
        )

    @Test
    fun `initial status is NOT_CONFIGURED when client is absent`() {
        val service = serviceWithClient(null)
        assertEquals(
            RatkoClient.RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null),
            service.getRatkoOnlineStatus(),
        )
    }

    @Test
    fun `status is NOT_CONFIGURED before first refresh even when client is present`() {
        val service = serviceWithClient(mock(RatkoClient::class.java))
        assertEquals(
            RatkoClient.RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null),
            service.getRatkoOnlineStatus(),
        )
    }

    @Test
    fun `refreshOnlineStatus updates cached value from client`() {
        val client = mock(RatkoClient::class.java)
        val expected = RatkoClient.RatkoStatus(RatkoConnectionStatus.ONLINE, 200)
        org.mockito.Mockito.`when`(client.getRatkoOnlineStatus()).thenReturn(expected)

        val service = serviceWithClient(client)
        service.refreshOnlineStatus()

        assertEquals(expected, service.getRatkoOnlineStatus())
    }

    @Test
    fun `refreshOnlineStatus with null client keeps NOT_CONFIGURED`() {
        val service = serviceWithClient(null)
        service.refreshOnlineStatus()
        assertEquals(
            RatkoClient.RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null),
            service.getRatkoOnlineStatus(),
        )
    }
}
