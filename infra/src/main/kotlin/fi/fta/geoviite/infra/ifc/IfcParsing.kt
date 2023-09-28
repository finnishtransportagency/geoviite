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
    iterator.next().also { line ->
        require(Ifc.startTag == line) { "IFC must start with the main tag (${Ifc.startTag}: found=$line" }
    }

    val mainSectionContent = parseIfcContent(iterator, Ifc.startTag, Ifc.endTag)
    val contentSections = mainSectionContent.map { c ->
        if (c is IfcSection) c
        else throw IllegalArgumentException("The main section (${Ifc.startTag}) should only contain other sections")
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
    return Ifc(header, data)
}

fun parseIfcContent(
    lines: Iterator<IfcContentPart>,
    startTag: IfcName,
    endTag: IfcName,
): List<IfcContentPart> {
    val content = mutableListOf<IfcContentPart>()
    while (lines.hasNext()) {
        when (val line = lines.next()) {
            endTag -> return content
            is IfcName -> content.add(IfcSection(line, parseIfcContent(lines, line, IfcSection.endTag)))
            else -> content.add(line)
        }
    }
    throw IllegalArgumentException("No section end tag found: startTag=$startTag endTag=$endTag")
}

fun toContentLine(rawLine: String): IfcContentPart? =
    rawLine.takeIf(String::isNotBlank)?.let(::dropSuffix)?.let { line ->
        try {
            if (line.startsWith(IfcEntityId.PREFIX)) IfcDataEntity(line)
            else if (line.contains(IfcEntityList.START_MARKER)) IfcEntity(line)
            else IfcName.valueOf(line)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse IFC line: $line", e)
        }
    }

fun dropSuffix(line: String): String = line.also {
    require(line.endsWith(IFC_LINE_SUFFIX)) {
        "IFC lines should end with '$IFC_LINE_SUFFIX': line=${formatForLog(line)}"
    }
}.dropLast(IFC_LINE_SUFFIX.length).trim()

fun parseAttributes(content: String): IfcEntityList {
    val iterator = content.iterator()
    val firstChar = iterator.next()
    require(firstChar == IfcEntityList.START_MARKER) {
        "List should start with '${IfcEntityList.START_MARKER}': found=$firstChar"
    }
    val (_, list) = takeList(iterator)
    return list
}

private fun takeList(iterator: Iterator<Char>): Pair<Char?, IfcEntityList> {
    val items = mutableListOf<IfcEntityAttribute>()
    var lastChar: Char? = null
    // Due to nested lists, we can't just split the list string by comma: Instead we pick item by item and handle nested lists recursively
    while (iterator.hasNext() && lastChar != IfcEntityList.END_MARKER) {
        val (last, item) = takeNextItem(iterator)
        item?.let(items::add)
        lastChar = last
    }
    val following = takeNonBlank(iterator)
    if (isItemEnd(following)) return following to IfcEntityList(items)
    else throw IllegalArgumentException(
        "List parsing failed: the attribute continues after closing parenthesis: found='$following'"
    )
}

private fun takeNextItem(iterator: Iterator<Char>): Pair<Char?, IfcEntityAttribute?> {
    return when (val next = takeNonBlank(iterator)) {
        null -> null to null
        IfcEntityMissingValue.UNSET.marker -> takeMissingValue(iterator, next)
        IfcEntityMissingValue.DERIVED.marker -> takeMissingValue(iterator, next)
        IfcEntityList.START_MARKER -> takeList(iterator)
        IfcEntityString.MARKER -> takeString(iterator)
        IfcEntityEnum.MARKER -> takeEnum(iterator)
        IfcEntityId.PREFIX -> takeEntityRef(iterator)
        else -> {
            if (next == '-' || next.isDigit()) takeNumber(iterator, next)
            else takeEntity(iterator, next)
        }
    }
}

private fun takeEntity(iterator: Iterator<Char>, firstChar: Char): Pair<Char?, IfcEntity> {
    val nameBuilder = StringBuilder()
    nameBuilder.append(firstChar)
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (next.isWhitespace()) continue
        else if (next == IfcEntityList.START_MARKER) {
            val (lastChar, attributes) = takeList(iterator)
            return lastChar to IfcEntity(IfcName.valueOf(nameBuilder.toString()), attributes)
        } else nameBuilder.append(next)
    }
    throw IllegalArgumentException("Entity ended before the parameter list")
}

private fun takeMissingValue(iterator: Iterator<Char>, marker: Char): Pair<Char?, IfcEntityMissingValue> {
    val value = IfcEntityMissingValue.values().find { v -> v.marker == marker }
        ?: throw IllegalArgumentException("Not a valid marker for missing value: '$marker'")
    val next = takeNonBlank(iterator)
    if (isItemEnd(next)) return next to value
    else throw IllegalArgumentException(
        "Unset value ($marker) parsing failed: the item continues after marker: found='$next'"
    )
}

private fun takeNonBlank(iterator: Iterator<Char>): Char? {
    var next = if (iterator.hasNext()) iterator.next() else null
    while (iterator.hasNext() && next != null && next.isWhitespace()) next = iterator.next()
    return next?.takeUnless { n -> n.isWhitespace() }
}

private fun takeString(iterator: Iterator<Char>): Pair<Char?, IfcEntityString> {
    val builder = StringBuilder()
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (next != IfcEntityString.MARKER) {
            builder.append(next)
        } else {
            val following = if (iterator.hasNext()) iterator.next() else null
            // Doubled ' is just an escaped part of the string
            if (following == IfcEntityString.MARKER) {
                builder.append(following)
            }
            // Anything else should mean that the string is over
            else if (isItemEnd(following)) {
                return following to IfcEntityString(builder.toString())
            }
            // If we get something unexpected after the string itself is over
            else throw IllegalArgumentException(
                "String parsing failed: endMarker (${IfcEntityString.MARKER}) was not the end of the item: found='$following'"
            )
        }
    }
    throw IllegalArgumentException("String value ended before the end marker: ${IfcEntityString.MARKER}")
}

private fun takeEnum(iterator: Iterator<Char>): Pair<Char?, IfcEntityEnum> {
    val builder = StringBuilder()
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (next != IfcEntityEnum.MARKER) {
            builder.append(next)
        } else {
            val following = if (iterator.hasNext()) iterator.next() else null
            if (isItemEnd(following)) return following to IfcEntityEnum.valueOf(builder.toString())
            // If we get something unexpected after the enum value is over
            else throw IllegalArgumentException(
                "Enum parsing failed: endMarker (${IfcEntityEnum.MARKER}) was not the end of the item: found='$following'"
            )
        }
    }
    throw IllegalArgumentException("Enum value ended before the end marker: ${IfcEntityEnum.MARKER}")
}

private fun takeNumber(iterator: Iterator<Char>, firstDigit: Char): Pair<Char?, IfcEntityNumber> {
    val builder = StringBuilder()
    builder.append(firstDigit)
    while (true) {
        val next = if (iterator.hasNext()) iterator.next() else null
        if (isItemEnd(next)) return next to IfcEntityNumber(builder.toString().toBigDecimal())
        else builder.append(next)
    }
}

private fun takeEntityRef(iterator: Iterator<Char>): Pair<Char?, IfcEntityId> {
    val builder = StringBuilder()
    while (true) {
        val next = if (iterator.hasNext()) iterator.next() else null
        if (isItemEnd(next)) return next to IfcEntityId.valueOf(builder.toString().toInt())
        else builder.append(next)
    }
}

private fun isItemEnd(char: Char?) = char == null || char == IFC_ATTRIBUTE_SEPARATOR || char == IfcEntityList.END_MARKER
