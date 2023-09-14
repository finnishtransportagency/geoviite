package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.assertSanitized
import fi.fta.geoviite.infra.util.formatForLog
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

// IFC Data spec: https://standards.buildingsmart.org/documents/Implementation
// IFC follows the STEP file specification building blocks: https://en.wikipedia.org/wiki/ISO_10303-21
const val IFC_LINE_SUFFIX = ";"
const val IFC_UNSET_MARK = '$'
const val IFC_DERIVED = '*'
const val IFC_ATTRIBUTE_SEPARATOR = ','

const val IFC_ISO_STRING = "ISO-10303-21"

data class Ifc(
    val header: IfcHeader,
    val data: IfcData,
) {
    companion object {
        val startTag = IfcEntityName.valueOf(IFC_ISO_STRING)
        val endTag = IfcEntityName.valueOf("END-$IFC_ISO_STRING")
    }
}

enum class IfcEntityMissingValue(val marker: Char) : IfcEntityAttribute {
    UNSET(IFC_UNSET_MARK), DERIVED(IFC_DERIVED);

    override fun toString() = "$marker"
}

// https://standards.buildingsmart.org/documents/Implementation/ImplementationGuide_IFCHeaderData_Version_1.0.2.pdf
data class IfcHeader(val contentLines: List<IfcEntity>) {
    companion object {
        val sectionName = IfcEntityName.valueOf("HEADER")
    }

    constructor(section: IfcSection) : this(section.typedContent<IfcEntity>())

    fun getContent(header: IfcEntityName) = contentLines.find { c -> c.name == header }?.content
}

interface IfcContentPart {
    val name: IfcEntityName
}

data class IfcSection(
    val startTag: IfcEntityName,
    val content: List<IfcContentPart>,
) : IfcContentPart {
    companion object {
        val endTag = IfcEntityName.valueOf("ENDSEC")
    }

    override val name: IfcEntityName get() = startTag

    inline fun <reified T> typedContent(): List<T> = content.map { c ->
        require(c is T) { "Expected content to be of type ${T::class.simpleName}" }
        c
    }
}

data class IfcData(val linesById: Map<IfcEntityId, IfcDataEntity>) {
    companion object {
        val sectionName = IfcEntityName.valueOf("DATA")
    }

    constructor(section: IfcSection) : this(section.typedContent<IfcDataEntity>().associateBy { dl -> dl.id })
}

data class IfcDataEntity(val id: IfcEntityId, val content: IfcEntity) : IfcContentPart {
    companion object {
        const val SEPARATOR = "="
    }

    override val name: IfcEntityName get() = content.name

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

// In step spec, this is referred to as "instance name"
@Suppress("DataClassPrivateConstructor")
data class IfcEntityId private constructor(val number: Int) : IfcEntityAttribute {
    companion object {
        const val PREFIX = '#'
        private val entityIdCache: MutableMap<Int, IfcEntityId> = ConcurrentHashMap()
        fun valueOf(number: Int) = entityIdCache.computeIfAbsent(number, ::IfcEntityId)
    }

    constructor(stringFormat: String) : this(stringFormat.let { string ->
        require(string.startsWith(PREFIX)) { "Data line ID should start with $PREFIX" }
        val numberPart = string.drop(1)
        require(numberPart.all(Char::isDigit)) { "Data line ID should be numeric" }
        numberPart.toInt()
    })

    override fun toString() = "$PREFIX$number"
}

@Suppress("DataClassPrivateConstructor")
data class IfcEntityName private constructor(private val value: String) : IfcContentPart, CharSequence by value {
    companion object {
        private val allowedLength = 1..1000
        private val regex = Regex("^[A-Z0-9_\\-]+$")

        // TypeNames come from a limited dictionary: Cache them so that the object is only created once per string
        private val typeNameCache: MutableMap<String, IfcEntityName> = ConcurrentHashMap()
        fun valueOf(value: String) = typeNameCache.computeIfAbsent(value, ::IfcEntityName)
    }

    init {
        assertSanitized<IfcEntityName>(value, regex, allowedLength, allowBlank = false)
    }

    override fun toString(): String = value

    override val name = this
}

interface IfcEntityAttribute
data class IfcEntityString(val value: String) : IfcEntityAttribute {
    companion object {
        const val MARKER = '\''
    }

    override fun toString() = "$MARKER$value$MARKER"
}

@Suppress("DataClassPrivateConstructor")
data class IfcEntityEnum private constructor(val value: String) : IfcEntityAttribute {
    companion object {
        const val MARKER = '.'

        // Enum values come from a limited dictionary: Cache them so that the object is only created once per string
        private val entityEnumCache: MutableMap<String, IfcEntityEnum> = ConcurrentHashMap()
        fun valueOf(value: String) = entityEnumCache.computeIfAbsent(value, ::IfcEntityEnum)
    }

    init {
        require(value.all { c -> c.isUpperCase() || c.isDigit() || c == '_' }) {
            "Enum values should be uppercase: found=$value"
        }
    }

    override fun toString() = "$MARKER$value$MARKER"
}

data class IfcEntityNumber(val value: BigDecimal) : IfcEntityAttribute {
    override fun toString(): String = value.toPlainString()
}

data class IfcEntityList(val items: List<IfcEntityAttribute>) : IfcEntityAttribute {
    companion object {
        const val START_MARKER = '('
        const val END_MARKER = ')'
    }

    override fun toString(): String = "$START_MARKER${items.joinToString("$IFC_ATTRIBUTE_SEPARATOR")}$END_MARKER"
}

data class IfcEntity(
    override val name: IfcEntityName, // In STEP spec, referred to as entity name
    val content: IfcEntityList,
) : IfcContentPart, IfcEntityAttribute {

    private constructor(nameAndContent: Pair<IfcEntityName, IfcEntityList>) : this(
        nameAndContent.first,
        nameAndContent.second,
    )

    constructor(stringFormat: String) : this(stringFormat.let { string ->
        val contentStart = string.indexOf(IfcEntityList.START_MARKER)
        val name = string.take(contentStart).trim()
        val content = string.substring(name.length).trim()
        IfcEntityName.valueOf(name) to parseAttributes(content)
    })

    override fun toString() = "$name$content"
}

fun dropSuffix(line: String): String = line.also {
    require(line.endsWith(IFC_LINE_SUFFIX)) {
        "IFC lines should end with '$IFC_LINE_SUFFIX': line=${formatForLog(line)}"
    }
}.dropLast(IFC_LINE_SUFFIX.length).trim()
