package fi.fta.geoviite.infra.util

import org.apache.commons.io.ByteOrderMark
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class XMLUtilsTest {
    @Test
    fun importingBOMFileWithISOXmlEncodingWorks() {
        val isoTestFileWithBom =
            (ByteOrderMark.UTF_BOM + "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>").toByteArray()
        assertDoesNotThrow { xmlBytesToString(isoTestFileWithBom) }
    }

    @Test
    fun importingUTF16FileWithBOMWorks() {
        val utf16file =
            ByteOrderMark.UTF_16BE.bytes +
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?>".toByteArray(charset = Charsets.UTF_16BE)
        assertDoesNotThrow { xmlBytesToString(utf16file) }
    }

    @Test
    fun importingUSASCIIFileWorks() {
        val asciiTestFile = "<?xml version=\"1.0\" encoding=\"ASCII\"?>".toByteArray(charset = Charsets.US_ASCII)
        assertDoesNotThrow { xmlBytesToString(asciiTestFile) }
    }

    @Test
    fun importingUTF8FileWorks() {
        val testFileWithBom = (ByteOrderMark.UTF_BOM + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>").toByteArray()
        assertDoesNotThrow { xmlBytesToString(testFileWithBom) }
    }

    @Test
    fun importingISOFileWorks() {
        val isoFile = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>".toByteArray()
        assertDoesNotThrow { xmlBytesToString(isoFile) }
    }
}
