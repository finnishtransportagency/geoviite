package fi.fta.geoviite.infra.cloudfront

import com.amazonaws.services.cloudfront.CloudFrontCookieSigner
import fi.fta.geoviite.infra.SpringContextUtility
import fi.fta.geoviite.infra.cloudfront.KeyUtils.Companion.parseBase64DerToPrivateKey
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CookieSigner {
    private val keyPairId: String by lazy { SpringContextUtility.getProperty("geoviite.cloudfront.key-pair-id") }
    private val encodedKey: String by lazy { SpringContextUtility.getProperty("geoviite.cloudfront.private-key") }
    private val distributionDnsName: String by lazy {
        SpringContextUtility.getProperty("geoviite.cloudfront.distribution-name")
    }
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun createSignedCustomCookies(): CloudFrontCookies {
        logger.info(
            "KeyPairId=[{}] - Encoded Key Length=[{}] - distribution dns name=[{}]",
            keyPairId,
            encodedKey.length,
            distributionDnsName,
        )

        val resourcePath = "https://$distributionDnsName/*"
        val privateKey = parseBase64DerToPrivateKey(encodedKey)
        val activeFrom = Instant.now().minusSeconds(5)
        val expiresOn = activeFrom.plus(Duration.ofHours(8))
        val df =
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).also { date ->
                date.timeZone = TimeZone.getTimeZone("GMT")
            }

        val customPolicyCookies =
            CloudFrontCookieSigner.getCookiesForCustomPolicy(
                resourcePath,
                privateKey,
                keyPairId,
                Date.from(expiresOn),
                Date.from(activeFrom),
                "0.0.0.0/0",
            )

        val cookieAttributes = "SameSite=Lax; Path=/; Secure; HttpOnly; Expires=${df.format(Date.from(expiresOn))}"
        val keyPairIdCookie = "CloudFront-Key-Pair-Id=${customPolicyCookies.keyPairId.value};$cookieAttributes"
        val policyCookie = "CloudFront-Policy=${customPolicyCookies.policy.value};$cookieAttributes"
        val signatureCookie = "CloudFront-Signature=${customPolicyCookies.signature.value};$cookieAttributes"

        return CloudFrontCookies(
            policy = policyCookie,
            signature = signatureCookie,
            keyPairId = keyPairIdCookie,
            domain = distributionDnsName,
        )
    }
}
