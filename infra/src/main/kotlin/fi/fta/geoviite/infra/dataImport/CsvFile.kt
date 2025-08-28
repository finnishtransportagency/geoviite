package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geography.parse2DLineString
import fi.fta.geoviite.infra.geography.parse2DPoint
import fi.fta.geoviite.infra.math.Point
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import kotlin.reflect.KClass

class CsvFile<T : Enum<T>>(val filePath: String, private val type: KClass<T>) {

    val file = File(filePath)

    private val csvFormat =
        CSVFormat.Builder.create()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setDelimiter(',')
            .setQuote('"')
            .get()
    private val expectedColumns: List<String> by lazy {
        type.java.enumConstants.map { value -> value.name.lowercase() }
    }

    private fun <T> read(op: (CSVParser) -> T): T {
        require(file.isFile) { "No such CSV file found: ${file.absolutePath}" }
        return CSVParser.builder()
            .setFormat(csvFormat)
            .setReader(file.bufferedReader(Charsets.UTF_8))
            .get()
            .also(::validateHeaders)
            .let(op)
    }

    fun <S> parseLines(handler: (CsvLine<T>) -> S?): List<S> = read { parser ->
        parser.mapNotNull { record -> handler(CsvLine(record)) }
    }

    fun <S> parseLinesStreaming(handler: (CsvLine<T>) -> S?): Sequence<S> = read { parser ->
        parser.asSequence().mapNotNull { record -> handler(CsvLine(record)) }
    }

    private fun validateHeaders(parser: CSVParser) =
        require(parser.headerNames.map { h -> h.lowercase() } == expectedColumns) {
            "CSV columns wrong: expected=$expectedColumns actual=${parser.headerNames}"
        }

    class CsvLine<T : Enum<T>>(private val record: CSVRecord) {
        fun get(field: T): String = record.get(field.name.lowercase())

        fun getNonEmpty(field: T): String? = get(field).let { s -> s.ifBlank { null } }

        fun getPoint(geometryField: T): Point = parse2DPoint(get(geometryField))

        fun getLinestringPoints(geometryField: T): List<Point> = parse2DLineString(get(geometryField))

        inline fun <reified S> getOid(field: T): Oid<S> = Oid(get(field))

        inline fun <reified S> getOidOrNull(field: T): Oid<S>? = get(field).takeIf(String::isNotEmpty)?.let(::Oid)

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
