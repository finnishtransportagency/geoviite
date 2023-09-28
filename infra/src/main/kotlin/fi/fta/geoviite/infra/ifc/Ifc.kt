package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.assertSanitized
import java.util.concurrent.ConcurrentHashMap

// IFC Data spec: https://standards.buildingsmart.org/documents/Implementation
// IFC follows the STEP file specification building blocks: https://en.wikipedia.org/wiki/ISO_10303-21
const val IFC_LINE_SUFFIX = ";"
const val IFC_ISO_STRING = "ISO-10303-21"

data class Ifc(
    val header: IfcHeader,
    val data: IfcData,
) {
    companion object {
        val startTag = IfcName.valueOf(IFC_ISO_STRING)
        val endTag = IfcName.valueOf("END-$IFC_ISO_STRING")
    }

    fun get(id: IfcEntityId): IfcEntity =
        data.linesById[id]?.content ?: throw NoSuchElementException("No data row found: id=$id")

    fun getDereferenced(name: IfcName) =
        data.linesById.values.filter { v -> v.name == name }.map { e -> e.content.dereference(this) }

}

// https://standards.buildingsmart.org/documents/Implementation/ImplementationGuide_IFCHeaderData_Version_1.0.2.pdf
data class IfcHeader(val contentLines: List<IfcEntity>) {
    companion object {
        val fileNameHeader = IfcName.valueOf("FILE_NAME")
        val fileDescription = IfcName.valueOf("FILE_DESCRIPTION")
        val fileSchema = IfcName.valueOf("FILE_SCHEMA")
        val sectionName = IfcName.valueOf("HEADER")
    }

    constructor(vararg contentLines: IfcEntity) : this(contentLines.toList())

    constructor(section: IfcSection) : this(section.typedContent<IfcEntity>())

    fun getElement(header: IfcName) = contentLines.find { c -> c.name == header }

    val fileName by lazy { getElement(fileNameHeader)?.getStringField(listOf(0))?.value?.let(::FileName) }
}

data class IfcData(val linesById: Map<IfcEntityId, IfcDataEntity>) {
    companion object {
        val sectionName = IfcName.valueOf("DATA")
    }

    constructor(vararg dataLines: IfcDataEntity) : this(dataLines.associateBy { dl -> dl.id })

    constructor(section: IfcSection) : this(section.typedContent<IfcDataEntity>().associateBy { dl -> dl.id })
}

data class IfcDataEntity(val id: IfcEntityId, val content: IfcEntity) : IfcContentPart {
    companion object {
        const val SEPARATOR = "="
    }

    override val name: IfcName get() = content.name

    private constructor(idAndContent: Pair<String, String>) : this(
        id = IfcEntityId(idAndContent.first),
        content = IfcEntity(idAndContent.second),
    )

    constructor(line: String) : this(line.let { string ->
        val parts = string.split(SEPARATOR)
        require(parts.size == 2) { "Entity lines must consist of ID and content: parts=${parts.size}" }
        parts[0].trim() to parts[1].trim()
    })

    override fun toString() = "$id = $content"
}

interface IfcContentPart {
    val name: IfcName
}

data class IfcSection(
    val startTag: IfcName,
    val content: List<IfcContentPart>,
) : IfcContentPart {
    companion object {
        val endTag = IfcName.valueOf("ENDSEC")
    }

    override val name: IfcName get() = startTag

    inline fun <reified T> typedContent(): List<T> = content.map { c -> coerce<T>(c) }
}


@Suppress("DataClassPrivateConstructor")
data class IfcName private constructor(private val value: String) : IfcContentPart, CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-Z0-9_\\-]+$")

        // TypeNames come from a limited dictionary: Cache them so that the object is only created once per string
        private val typeNameCache: MutableMap<String, IfcName> = ConcurrentHashMap()
        fun valueOf(value: String) = typeNameCache.computeIfAbsent(value, ::IfcName)
    }

    init {
        assertSanitized<IfcName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value

    override val name = this
}

inline fun <reified T> coerce(value: Any): T = if (value is T) {
    value
} else {
    throw IllegalArgumentException("Unexpected value type: expected=${T::class.simpleName} actual=${value::class.simpleName}")
}
