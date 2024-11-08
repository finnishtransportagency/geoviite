package fi.fta.geoviite.infra.cloudfront

import kotlin.test.assertContains
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@Disabled
class CookieSignerTest
@Autowired
constructor(
    private val cookieSigner: CookieSigner,
    @Value("\${geoviite.cloudfront.key-pair-id}") private val keyPairId: String,
) {

    @Test
    fun signedCookieContainsConfiguredKeyPairId() {
        val result = cookieSigner.createSignedCustomCookies()
        assertContains(result.keyPairId, keyPairId)
    }

    @Test
    fun thisTestFailsOnPurpose() {
        assertContains("ei", "toimi")
    }
}
