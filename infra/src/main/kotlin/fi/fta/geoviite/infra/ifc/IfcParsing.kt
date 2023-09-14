package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.formatForLog
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

fun parseIfcFromClasspath(path: String): Ifc =
    parseIfc(requireNotNull(Ifc::class.java.getResource(path)) { "Resource does not exist: path=$path" }.readBytes())

fun parseIfcFromPath(path: String): Ifc = parseIfc(File(path))

fun parseIfc(bytes: ByteArray, charset: Charset = Charsets.UTF_8) = parseIfc(
    BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), charset)).lineSequence().mapNotNull(::toContentLine)
)

fun parseIfc(file: File, charset: Charset = Charsets.UTF_8): Ifc =
    file.useLines(charset) { lines -> parseIfc(lines.mapNotNull(::toContentLine)) }

fun parseIfc(lines: Sequence<IfcContentPart>): Ifc {
    val iterator = lines.iterator()
    require(iterator.hasNext()) { "IFC must have some lines" }
    val mainSectionName = iterator.next().let { l ->
        if (l is IfcTypeName) l
        else throw IllegalArgumentException("IFC must start with the main section: found=$l")
    }

    val mainEndTag = IfcTypeName.create("END-$mainSectionName")
    val mainSectionContent = parseIfcContent(iterator, mainEndTag)
    val contentSections = mainSectionContent.map { c ->
        if (c is IfcSection) c
        else throw IllegalArgumentException("The main section ($mainSectionName) should only contain other sections")
    }
    val header = requireNotNull(contentSections.find { s -> s.name == IfcHeader.sectionName }?.let(::IfcHeader)) {
        "IFC file must contain the ${IfcHeader.sectionName} section"
    }
    val data = requireNotNull(contentSections.find { s -> s.name == IfcData.sectionName }?.let(::IfcData)) {
        "IFC file must contain the ${IfcData.sectionName} section"
    }
    require(!iterator.hasNext()) {
        "All data should have been consumed: there should be no more data after the main section end"
    }
    return Ifc(mainSectionName, header, data)
}

fun parseIfcContent(lines: Iterator<IfcContentPart>, endTag: IfcTypeName): List<IfcContentPart> {
    val content = mutableListOf<IfcContentPart>()
    while (lines.hasNext()) {
        when (val line = lines.next()) {
            endTag -> return content
            is IfcTypeName -> content.add(IfcSection(line, ifcSectionEnd, parseIfcContent(lines, ifcSectionEnd)))
            else -> content.add(line)
        }
    }
    throw IllegalArgumentException("No section end tag found: endTag=$endTag")
}

fun toContentLine(rawLine: String): IfcContentPart? =
    rawLine.takeIf(String::isNotBlank)?.let(::dropSuffix)?.let { line ->
        if (line.startsWith(IfcDataLineId.PREFIX)) {
            IfcDataLine(line)
        } else if (line.contains('(')) {
            IfcContentRaw(line)
        } else {
            IfcTypeName.create(line)
        }
    }

private fun dropSuffix(line: String): String =
    line.also { require(line.endsWith(IFC_LINE_SUFFIX)) { "Lines should end with ';': line=${formatForLog(line)}" } }
        .dropLast(IFC_LINE_SUFFIX.length)
        .trim()
