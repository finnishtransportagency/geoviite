package fi.fta.geoviite.infra.cloudfront

import kotlin.test.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KeyUtilsTest {

    /** These are dummy keys, not in use anywhere except in this class. */
    val base64EncodedDerKey =
        "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDdfHVQyiLKz8Zb5pVxbKaLJPs6jN287bMrc2NBDmFfbHspsPg2kFYWFlQns6NpwzZF7HaF+WZNeZfeVBjYDzJA1os62JroNF6+cYrpIsIpjr0jdA0am19Kai3+MYt3d1qi4ILp2I/4LX/axiFnK4Gfe430jN7o4Ud0hFl6vRbbWfr9D3OOxHjLRprf2N3gPZE/D3m2HAwAUy1tX1My88ItFlDynmGQm/bpywTOgxEeeXRmsZ6FoigNgNpMh5wp/Sdf6rMC0XtxbDng/PgzWhA5i7Z6R0VNGds06zuadsF/izqtV92iveCb/BDGAq3mYXjvWUw/MD6A3Ddb1qfgabq9AgMBAAECggEAZxEAgqzdjeUsGB3wEw0NXxjBc9iTYtR2GNUkLeTkOQSBN8BrcPCvrq2LUcJNW+0Ed3t3GBcbnRflLQeTXA+OQg/UUHj1dPLR1+t8Scrr8WzD5Rie9G+y4y0P5AboMJqw6bRyFyG4tTNvGL40Uw8yzaUwRMm8/T/AAZ+JCA2v8jhJ3i4yHGiiHUnjY1CRLaUDpkycxRaGgA9q7JnDzfp8CCh88pFFr5nKEGWhoJq6noNm/rh9OpYgXx9byylTE9lGq8GQtlfRo1kQP9h1lsZztQ4rP+MKQhCOBsyQrCJn1GIjmv1utqxTipcgukQxtIzykbSXBsykufblozFjfikeIQKBgQD/wa2G9I1PuBmmY7NVj8cuPbMqYGGOshzeRuz/aYuOo7ajw6i6YgutP3Qvq0Ix99cvLru/tcDbR9+xfmKwoA/uSK1wOazusikskrnA8phJUkpeEnjumGhtqWRpXvM0P5MU3oFj+poSVjoZYScLgbHnF1pS/Fm0fzI17rr2CAJeKQKBgQDdsm3zZY1OcmRK8beUiNqAsXY0pMQ0gWjTdeJYbCtif0Vkpem0tMRm9bbA8Gora1DKlfBHuzopdq8HBbi/4PSY4so4zkJB3EZnmGDEthEF8KZEXi8KGU7v6/0ViCcjujCsvqdAZgsGKYfRDdXRPm17Chd2Rg5KFmJhcGgrTCFidQKBgADZyhP62EV9nUg6aKxOMCFtPx1S+MAaw5HRtpQa68XrsX3V9se378YBwgcukKfN5T9Y7nLyzdNs58eVXgqsXaEzSLBo4LRij1SAoHGN3QfRaEHr2c8hXqeOurDHChQQahLVsqR8fuq0srjG4/Rb2BWmtDw2bq31Blu7kY+j8y4RAoGBAJkWaDRl0LD17umNho5L/k5VvOFXUaFMJ122DomukDrg1cNNilddaC4MyJjsqvO2lECATz7JO718FhrMSao+JckY+jlFvJ0MBZXttAzCCHlIlxeozeS0WzzzgX0H2rciEBCJSqb+j+g+b2ndmuN1r1YCPvdOIvnoASF15IjZdkgtAoGASUm2gEr3mtoRFh1Q0xJwRNM3hV0dvderUv2k3Hyv+qM/3LValLimy7NSwSyrIkIB2NoXW/zsWTXrcQ8jkpmvACnXAdkVcYyidxeRzzshQjioQaIO8IY3Wtc8Jhnq7Qi8g0FPn2Hc/Q8eee0M5+gf68f/N9z88R3I1bsSIwrvU6U="

    val base64EncodedDerKeyWithExtraChars =
        "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDdfHVQyiLKz8Zb5pVxbKaLJPs6\n" +
            "jN287bMrc2NBDmFfbHspsPg2kFYWFlQns6NpwzZF7HaF+WZNeZfeVBjYDzJA1os62JroNF6+cYrp\n" +
            "IsIpjr0jdA0am19Kai3+MYt3d1qi4ILp2I/4LX/axiFnK4Gfe430jN7o4Ud0hFl6vRbbWfr9D3OO\n" +
            "xHjLRprf2N3gPZE/D3m2HAwAUy1tX1My88ItFlDynmGQm/bpywTOgxEeeXRmsZ6FoigNgNpMh5wp\n" +
            "/Sdf6rMC0XtxbDng/PgzWhA5i7Z6R0VNGds06zuadsF/izqtV92iveCb/BDGAq3mYXjvWUw/MD6A\n" +
            "3Ddb1qfgabq9AgMBAAECggEAZxEAgqzdjeUsGB3wEw0NXxjBc9iTYtR2GNUkLeTkOQSBN8BrcPCv\n" +
            "rq2LUcJNW+0Ed3t3GBcbnRflLQeTXA+OQg/UUHj1dPLR1+t8Scrr8WzD5Rie9G+y4y0P5AboMJqw\n" +
            "6bRyFyG4tTNvGL40Uw8yzaUwRMm8/T/AAZ+JCA2v8jhJ3i4yHGiiHUnjY1CRLaUDpkycxRaGgA9q\n" +
            "7JnDzfp8CCh88pFFr5nKEGWhoJq6noNm/rh9OpYgXx9byylTE9lGq8GQtlfRo1kQP9h1lsZztQ4r\n" +
            "P+MKQhCOBsyQrCJn1GIjmv1utqxTipcgukQxtIzykbSXBsykufblozFjfikeIQKBgQD/wa2G9I1P\n" +
            "uBmmY7NVj8cuPbMqYGGOshzeRuz/aYuOo7ajw6i6YgutP3Qvq0Ix99cvLru/tcDbR9+xfmKwoA/u\n" +
            "SK1wOazusikskrnA8phJUkpeEnjumGhtqWRpXvM0P5MU3oFj+poSVjoZYScLgbHnF1pS/Fm0fzI1\n" +
            "7rr2CAJeKQKBgQDdsm3zZY1OcmRK8beUiNqAsXY0pMQ0gWjTdeJYbCtif0Vkpem0tMRm9bbA8Gor\n" +
            "a1DKlfBHuzopdq8HBbi/4PSY4so4zkJB3EZnmGDEthEF8KZEXi8KGU7v6/0ViCcjujCsvqdAZgsG\n" +
            "KYfRDdXRPm17Chd2Rg5KFmJhcGgrTCFidQKBgADZyhP62EV9nUg6aKxOMCFtPx1S+MAaw5HRtpQa\n" +
            "68XrsX3V9se378YBwgcukKfN5T9Y7nLyzdNs58eVXgqsXaEzSLBo4LRij1SAoHGN3QfRaEHr2c8h\n" +
            "XqeOurDHChQQahLVsqR8fuq0srjG4/Rb2BWmtDw2bq31Blu7kY+j8y4RAoGBAJkWaDRl0LD17umN\n" +
            "ho5L/k5VvOFXUaFMJ122DomukDrg1cNNilddaC4MyJjsqvO2lECATz7JO718FhrMSao+JckY+jlF\n" +
            "vJ0MBZXttAzCCHlIlxeozeS0WzzzgX0H2rciEBCJSqb+j+g+b2ndmuN1r1YCPvdOIvnoASF15IjZ\n" +
            "dkgtAoGASUm2gEr3mtoRFh1Q0xJwRNM3hV0dvderUv2k3Hyv+qM/3LValLimy7NSwSyrIkIB2NoX\n" +
            "W/zsWTXrcQ8jkpmvACnXAdkVcYyidxeRzzshQjioQaIO8IY3Wtc8Jhnq7Qi8g0FPn2Hc/Q8eee0M\n" +
            "5+gf68f/N9z88R3I1bsSIwrvU6U="

    @Test
    fun decodedResultIsNotNull() {
        val decoded: ByteArray = KeyUtils.decodeBase64ToByteArray(base64EncodedDerKeyWithExtraChars)
        Assertions.assertNotNull(decoded)
    }

    @Test
    fun decodedResultIsNotEmpty() {
        val decoded = KeyUtils.decodeBase64ToByteArray(base64EncodedDerKeyWithExtraChars)
        Assertions.assertFalse(decoded.isEmpty())
    }

    /** AWS Requires the resulting key to be in PKCS#8 format */
    @Test
    fun privateKeyFormatEqualsPKCS8() {
        val decoded = KeyUtils.decodeBase64ToByteArray(base64EncodedDerKeyWithExtraChars)
        val privateKey = KeyUtils.byteArrayToPrivateKey(decoded)
        assertEquals(privateKey.format, "PKCS#8")
    }
}
