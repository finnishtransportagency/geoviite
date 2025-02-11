package fi.fta.geoviite.infra.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.xml.stream.XMLInputFactory
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream

enum class XmlCharset(val charset: Charset) {
    UTF_8(StandardCharsets.UTF_8),
    UTF_16(StandardCharsets.UTF_16),
    UTF_16BE(StandardCharsets.UTF_16BE),
    UTF_16LE(StandardCharsets.UTF_16LE),
    US_ASCII(StandardCharsets.US_ASCII),
    ISO_8859_1(StandardCharsets.ISO_8859_1),
}

fun mapBomToCharset(bom: ByteOrderMark): Charset? =
    when (bom) {
        ByteOrderMark.UTF_8 -> StandardCharsets.UTF_8
        ByteOrderMark.UTF_16BE -> StandardCharsets.UTF_16BE
        ByteOrderMark.UTF_16LE -> StandardCharsets.UTF_16LE
        else -> null
    }

fun encodingsFromXmlStream(stream: InputStream): Pair<String?, String?> =
    XMLInputFactory.newInstance().createXMLStreamReader(stream).let { xmlStreamReader ->
        xmlStreamReader.encoding to xmlStreamReader.characterEncodingScheme
    }

fun findXmlCharset(name: String): Charset? = XmlCharset.entries.find { cs -> cs.charset.name() == name }?.charset

fun getEncodingAndBom(bytes: ByteArray): Pair<Charset, Boolean> {
    BOMInputStream.builder().setInputStream(ByteArrayInputStream(bytes)).get().use { stream ->
        val encodingFromBOM = stream.bom?.let { bom -> mapBomToCharset(bom) }
        val (fileEncoding, encodingFromXMLDeclaration) = encodingsFromXmlStream(stream)
        val encoding =
            encodingFromBOM
                ?: (encodingFromXMLDeclaration ?: fileEncoding)?.let(::findXmlCharset)
                ?: StandardCharsets.UTF_8
        return encoding to (stream.bom != null)
    }
}

fun xmlBytesToString(bytes: ByteArray, encodingOverride: Charset? = null): String {
    val (encoding, hasBom) = getEncodingAndBom(bytes)
    val stringifiedXml = String(bytes, encodingOverride ?: encoding)
    return if (hasBom) stringifiedXml.substring(1) else stringifiedXml
}
