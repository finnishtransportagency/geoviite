package fi.fta.geoviite.infra.ifc

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

const val IFC_ATTRIBUTE_SEPARATOR = ','

data class IfcEntity(
    override val name: IfcName, // In STEP spec, referred to as entity name
    val content: IfcEntityList,
) : IfcContentPart, IfcEntityAttribute, IfcEntityAttributeContainer by content {

    private constructor(nameAndContent: Pair<IfcName, IfcEntityList>) : this(
        nameAndContent.first,
        nameAndContent.second,
    )

    constructor(stringFormat: String) : this(stringFormat.let { string ->
        val contentStart = string.indexOf(IfcEntityList.START_MARKER)
        val name = string.take(contentStart).trim()
        val content = string.substring(name.length).trim()
        IfcName.valueOf(name) to parseAttributes(content)
    })

    override fun toString() = "$name$content"

    override fun dereference(ifc: Ifc): IfcEntity = copy(content = content.dereference(ifc))

}

interface IfcEntityAttribute {
    fun dereference(ifc: Ifc) = this
}

interface IfcEntityAttributeContainer : IfcEntityAttribute {
    operator fun get(index: Int): IfcEntityAttribute

    fun getValue(indices: List<Int>): IfcEntityAttribute {
        var current: IfcEntityAttribute = this
        for (index in indices) {
            current = when (current) {
                is IfcEntityAttributeContainer -> current[index]
                else -> throw IllegalArgumentException(
                    "Cannot fetch sub-item with index from type ${current::class.simpleName}"
                )
            }
        }
        return current
    }

    fun getStringField(vararg indices: Int): IfcEntityString = getStringField(indices.toList())
    fun getStringField(indices: List<Int>): IfcEntityString
    fun getNullableStringField(vararg indices: Int): IfcEntityString? = getNullableStringField(indices.toList())
    fun getNullableStringField(indices: List<Int>): IfcEntityString?

    fun getNumberField(vararg indices: Int): IfcEntityNumber = getNumberField(indices.toList())
    fun getNumberField(indices: List<Int>): IfcEntityNumber
    fun getNullableNumberField(vararg indices: Int): IfcEntityNumber? = getNullableNumberField(indices.toList())
    fun getNullableNumberField(indices: List<Int>): IfcEntityNumber?

    fun getEnumField(vararg indices: Int): IfcEntityEnum = getEnumField(indices.toList())
    fun getEnumField(indices: List<Int>): IfcEntityEnum
    fun getNullableEnumField(vararg indices: Int): IfcEntityEnum? = getEnumField(indices.toList())
    fun getNullableEnumField(indices: List<Int>): IfcEntityEnum?

    fun getIdField(vararg indices: Int): IfcEntityId = getIdField(indices.toList())
    fun getIdField(indices: List<Int>): IfcEntityId
    fun getNullableIdField(vararg indices: Int): IfcEntityId? = getNullableIdField(indices.toList())
    fun getNullableIdField(indices: List<Int>): IfcEntityId?

    fun getListField(vararg indices: Int): IfcEntityList = getListField(indices.toList())
    fun getListField(indices: List<Int>): IfcEntityList
    fun getNullableListField(vararg indices: Int): IfcEntityList? = getNullableListField(indices.toList())
    fun getNullableListField(indices: List<Int>): IfcEntityList?

    fun getEntityField(vararg indices: Int): IfcEntity = getEntityField(indices.toList())
    fun getEntityField(indices: List<Int>): IfcEntity
    fun getNullableEntityField(vararg indices: Int): IfcEntity? = getNullableEntityField(indices.toList())
    fun getNullableEntityField(indices: List<Int>): IfcEntity?
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

    override fun dereference(ifc: Ifc): IfcEntity =
        ifc.get(this).dereference(ifc).also { println("Dereferenced ${it.name}") }
}

data class IfcEntityList(val items: List<IfcEntityAttribute>) : IfcEntityAttribute, IfcEntityAttributeContainer {
    companion object {
        const val START_MARKER = '('
        const val END_MARKER = ')'
    }

    constructor(vararg items: IfcEntityAttribute) : this(items.toList())

    override fun toString(): String = "$START_MARKER${items.joinToString("$IFC_ATTRIBUTE_SEPARATOR")}$END_MARKER"
    override fun dereference(ifc: Ifc): IfcEntityList = copy(items = items.map { a -> a.dereference(ifc) })
    override operator fun get(index: Int) = items[index]

    inline fun <reified T : IfcEntityAttribute> getTyped(index: Int): T = coerce<T>(get(index))

    inline fun <reified T : IfcEntityAttribute> getNullableTyped(index: Int): T? = coerceNullable<T>(get(index))

    inline fun <reified T : IfcEntityAttribute> getTypedValue(indices: List<Int>): T = coerce<T>(getValue(indices))

    inline fun <reified T : IfcEntityAttribute> getNullableTypedValue(indices: List<Int>): T? =
        coerceNullable<T>(getValue(indices))

    override fun getStringField(indices: List<Int>): IfcEntityString = getTypedValue(indices)
    override fun getNullableStringField(indices: List<Int>): IfcEntityString? = getNullableTypedValue(indices)

    override fun getNumberField(indices: List<Int>): IfcEntityNumber = getTypedValue(indices)
    override fun getNullableNumberField(indices: List<Int>): IfcEntityNumber? = getNullableTypedValue(indices)

    override fun getEnumField(indices: List<Int>): IfcEntityEnum = getTypedValue(indices)
    override fun getNullableEnumField(indices: List<Int>): IfcEntityEnum? = getNullableTypedValue(indices)

    override fun getIdField(indices: List<Int>): IfcEntityId = getTypedValue(indices)
    override fun getNullableIdField(indices: List<Int>): IfcEntityId? = getNullableTypedValue(indices)

    override fun getListField(indices: List<Int>): IfcEntityList = getTypedValue(indices)
    override fun getNullableListField(indices: List<Int>): IfcEntityList? = getNullableTypedValue(indices)

    override fun getEntityField(indices: List<Int>): IfcEntity = getTypedValue(indices)
    override fun getNullableEntityField(indices: List<Int>): IfcEntity? = getNullableTypedValue(indices)
}

data class IfcEntityString(val value: String) : IfcEntityAttribute {
    companion object {
        const val MARKER = '\''
    }

    override fun toString() = "$MARKER${escapeQuotes(value)}$MARKER"

    private fun escapeQuotes(value: String): String = value.replace("$MARKER", "$MARKER$MARKER")
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

enum class IfcEntityMissingValue(val marker: Char) : IfcEntityAttribute {
    UNSET('$'), DERIVED('*');

    override fun toString() = "$marker"
}

inline fun <reified T : IfcEntityAttribute> coerceNullable(value: IfcEntityAttribute): T? =
    if (value is IfcEntityMissingValue) null
    else coerce<T>(value)
