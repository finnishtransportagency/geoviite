package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geography.parse2DLineString
import fi.fta.geoviite.infra.geography.parse2DPoint
import fi.fta.geoviite.infra.math.Point
import java.io.File
import kotlin.reflect.KClass
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord

class CsvFile<T : Enum<T>>(val filePath: String, private val type: KClass<T>) : AutoCloseable {

    val file = File(filePath)

    private val csvFormat =
        CSVFormat.Builder.create()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setDelimiter(',')
            .setQuote('"')
            .build()
    private val expectedColumns: List<String> by lazy {
        type.java.enumConstants.map { value -> value.name.lowercase() }
    }
    private var parser: CSVParser? = null

    fun <S> parseLines(handler: (CsvLine<T>) -> S?): List<S> {
        if (!file.isFile) throw IllegalStateException("No such CSV file found: ${file.absolutePath}")
        return CSVParser(file.bufferedReader(Charsets.UTF_8), csvFormat).use { parser ->
            validateHeaders(parser)
            parser.mapNotNull { record -> handler(CsvLine(record)) }
        }
    }

    fun <S> parseLinesStreaming(handler: (CsvLine<T>) -> S?): Sequence<S> {
        if (!file.isFile) throw IllegalStateException("No such CSV file found: ${file.absolutePath}")
        parser = CSVParser(file.bufferedReader(Charsets.UTF_8), csvFormat)
        validateHeaders(parser ?: throw IllegalStateException("Parser lost"))
        return parser?.asSequence()?.mapNotNull { record -> handler(CsvLine(record)) }
            ?: throw IllegalStateException("Parser lost")
    }

    override fun close() {
        parser?.close()
        parser = null
    }

    private fun validateHeaders(parser: CSVParser) {
        if (parser.headerNames.map { h -> h.lowercase() } != expectedColumns) {
            throw IllegalStateException("CSV columns wrong: expected=$expectedColumns actual=${parser.headerNames}")
        }
    }

    class CsvLine<T : Enum<T>>(private val record: CSVRecord) {
        fun get(field: T): String = record.get(field.name.lowercase())

        fun getNonEmpty(field: T): String? = get(field).let { s -> s.ifBlank { null } }

        fun getPoint(geometryField: T): Point = parse2DPoint(get(geometryField))

        fun getLinestringPoints(geometryField: T): List<Point> = parse2DLineString(get(geometryField))

        inline fun <reified S> getOid(field: T): Oid<S> = Oid(get(field))

        inline fun <reified S> getOidOrNull(field: T): Oid<S>? =
            get(field).let {
                when (it) {
                    "" -> null
                    else -> Oid(it)
                }
            }

        inline fun <reified S : Enum<S>> getEnum(field: T): S = enumValueOf(get(field))

        inline fun <reified S : Enum<S>> getEnumOrNull(field: T): S? =
            getNonEmpty(field)?.let { s -> enumValueOf<S>(s) }

        fun getInt(field: T): Int = get(field).toInt()

        fun getIntOrNull(field: T): Int? = getNonEmpty(field)?.toInt()

        fun getDouble(field: T): Double = get(field).toDouble()

        fun getDoubleOrNull(field: T): Double? = getNonEmpty(field)?.toDouble()

        fun getBoolean(field: T): Boolean =
            getBooleanOrNull(field) ?: throw NullPointerException("Field $field contains null value")

        fun getBooleanOrNull(field: T): Boolean? =
            when (get(field)) {
                "f" -> false
                "t" -> true
                else -> null
            }
    }
}
