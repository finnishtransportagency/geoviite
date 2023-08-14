package fi.fta.geoviite.infra.cloudfront

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class KeyUtils {
    companion object {
        fun byteArrayToPrivateKey(keyInDerFormat: ByteArray): PrivateKey {
            val keySpec = PKCS8EncodedKeySpec(keyInDerFormat)
            val kf: KeyFactory = KeyFactory.getInstance("RSA")
            return kf.generatePrivate(keySpec)
        }

        fun decodeBase64ToByteArray(encodedString: String): ByteArray {
            val sanitizedEncodedString = encodedString.replace(" ", "").replace("\n", "").trim()
            return Base64.getDecoder().decode(sanitizedEncodedString)
        }

        fun parseBase64DerToPrivateKey(base64EncodedDer: String): PrivateKey {
            val derKey = decodeBase64ToByteArray(base64EncodedDer)
            return byteArrayToPrivateKey(derKey)
        }
    }
}
