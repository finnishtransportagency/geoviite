package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.assertSanitized
import java.util.concurrent.ConcurrentHashMap

const val IFC_LINE_SUFFIX = ";"
val ifcSectionEnd = IfcTypeName.create("ENDSEC")

data class Ifc(
    val mainSectionName: IfcTypeName,
    val header: IfcHeader,
    val data: IfcData,
)

data class IfcHeader(val contentLines: List<IfcContentRaw>) {
    companion object {
        val sectionName = IfcTypeName.create("HEADER")
    }

    constructor(section: IfcSection) : this(section.typedContent<IfcContentRaw>())

    fun getContent(header: IfcTypeName) = contentLines.find { c -> c.name == header }?.content
}

interface IfcContentPart {
    val name: IfcTypeName
}

data class IfcSection(val startTag: IfcTypeName, val endTag: IfcTypeName, val content: List<IfcContentPart>) :
    IfcContentPart {
    override val name: IfcTypeName get() = startTag

    inline fun <reified T> typedContent(): List<T> = content.map { c ->
        require(c is T) { "Expected content to be of type ${T::class.simpleName}" }
        c
    }
}

data class IfcData(val linesById: Map<IfcDataLineId, IfcDataLine>) {
    companion object {
        val sectionName = IfcTypeName.create("DATA")
    }

    constructor(section: IfcSection) : this(section.typedContent<IfcDataLine>().associateBy { dl -> dl.id })
}

data class IfcDataLine(val id: IfcDataLineId, val content: IfcContentRaw) : IfcContentPart {
    companion object {
        const val SEPARATOR = "="
    }

    override val name: IfcTypeName get() = content.name

    private constructor(idAndContent: Pair<String, String>) : this(
        id = IfcDataLineId(idAndContent.first),
        content = IfcContentRaw(idAndContent.second),
    )

    constructor(line: String) : this(line.let { string ->
        val parts = string.split(SEPARATOR)
        require(parts.size == 2) { "Data lines must consist of ID and content: parts=${parts.size}" }
        parts[0].trim() to parts[1].trim()
    })

    override fun toString() = "$id = $content"
}

data class IfcDataLineId(val number: Int) {
    companion object {
        const val PREFIX = "#"
    }

    constructor(stringFormat: String) : this(stringFormat.let { string ->
        require(string.startsWith(PREFIX)) { "Data line ID should start with $PREFIX" }
        val numberPart = string.drop(PREFIX.length)
        require(numberPart.all(Char::isDigit)) { "Data line ID should be numeric" }
        numberPart.toInt()
    })

    override fun toString() = "$PREFIX$number"
}

@Suppress("DataClassPrivateConstructor")
data class IfcTypeName private constructor(private val value: String) : IfcContentPart, CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-Z0-9_\\-]+$")

        // TypeNames come from a limited dictionary: Cache them so that the object is only created once per string
        private val typeNameCache: MutableMap<String, IfcTypeName> = ConcurrentHashMap()
        fun create(value: String) = typeNameCache.computeIfAbsent(value, ::IfcTypeName)
    }

    init {
        assertSanitized<IfcTypeName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value

    override val name = this
}

data class IfcContentRaw(override val name: IfcTypeName, val content: String) : IfcContentPart {

    private constructor(nameAndContent: Pair<String, String>) : this(
        IfcTypeName.create(nameAndContent.first),
        nameAndContent.second,
    )

    constructor(stringFormat: String) : this(stringFormat.let { string ->
        val contentStart = string.indexOf('(')
        require(contentStart > 1) { "IFC content must have opening paranthesis '('" }
        require(string.endsWith(')')) { "IFC content must have closing parenthesis ')'" }
        val name = string.take(contentStart)
        val content = string.slice(name.length + 1..string.length - 2)
        name.trim() to content.trim()
    })

    override fun toString() = "$name($content)"
}
