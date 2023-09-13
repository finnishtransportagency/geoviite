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
    getContentLines(BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), charset)).lineSequence())
)

fun parseIfc(file: File, charset: Charset = Charsets.UTF_8): Ifc = parseIfc(file.useLines(charset, ::getContentLines))

fun parseIfc(rawLines: List<String>): Ifc {
    val lines = rawLines.filter(String::isNotBlank)
    require(lines.size >= 2) { "IFC must consist of at least the main section" }
    val mainSectionName = IfcTypeName.create(lines[0])
    val mainEndTag = IfcTypeName.create("END-$mainSectionName")
    val (endIndex, mainSectionContent) = parseIfcContent(lines, mainEndTag, 1)
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
    require(endIndex == lines.lastIndex) {
        "There should be no more data after main section end: endIndex=$endIndex lineCount=${lines.size}"
    }
    return Ifc(mainSectionName, header, data)
}

fun parseIfcContent(
    lines: List<String>,
    endTag: IfcTypeName? = null,
    fromIndex: Int = 0,
): Pair<Int, List<IfcContentPart>> {
    println("parsing until $endTag from index $fromIndex")
    val content = mutableListOf<IfcContentPart>()
    var index = fromIndex
    var maxContent: Pair<Int, Int> = 0 to 0
    while (index <= lines.lastIndex) {
        if (index % 100000 == 0) {
            println("100000 lines parsed: index=$index")
        }
//        val line = getLineContent(index, lines[index])
        val line = lines[index]
        if (line == "$endTag") {
            println("Max content: index=${maxContent.first} size=${maxContent.second}")
            return index to content
        } else if (line.startsWith(IfcDataLineId.PREFIX)) {
            content.add(IfcDataLine(line).also { l ->
                if (l.content.content.length > maxContent.second) maxContent = index to l.content.content.length
            })
        } else if (line.contains('(')) {
            content.add(IfcContentRaw(line))
        } else {
            val startTag = IfcTypeName.create(line)
            val (lastIndex, sectionContent) = parseIfcContent(lines, ifcSectionEnd, index + 1)
            content.add(IfcSection(startTag, ifcSectionEnd, sectionContent))
            index = lastIndex
        }
        index++
    }
    println("Max content: index=${maxContent.first} size=${maxContent.second}")
    throw IllegalArgumentException("No section end tag found: endTag=$endTag")
}

fun getContentLines(sequence: Sequence<String>) = sequence.mapNotNull(::toContentLine).toList()

fun toContentLine(line: String): String? {
    return if (line.isBlank()) {
        null
    } else if (line.endsWith(IFC_LINE_SUFFIX)) {
        line.dropLast(IFC_LINE_SUFFIX.length).trim()
    } else {
        throw IllegalArgumentException("All lines should end with ';': line=${formatForLog(line)}")
    }
}
