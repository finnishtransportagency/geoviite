package fi.fta.geoviite.infra.util

import PVCode
import PVId
import PVName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.parse2DPolygon
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import java.sql.ResultSet
import java.time.Instant


fun ResultSet.getIntOrNull(name: String): Int? {
    val value = getInt(name)
    return if (wasNull()) null else value
}

fun ResultSet.getLongOrNull(name: String): Long? {
    val value = getLong(name)
    return if (wasNull()) null else value
}

fun ResultSet.getFloatOrNull(name: String): Float? {
    val value = getFloat(name)
    return if (wasNull()) null else value
}

fun ResultSet.getDoubleOrNull(name: String): Double? {
    val value = getDouble(name)
    return if (wasNull()) null else value
}

fun ResultSet.getBooleanOrNull(name: String): Boolean? {
    val value = getBoolean(name)
    return if (wasNull()) null else value
}

fun ResultSet.getInstant(name: String): Instant {
    return getInstantOrNull(name) ?: throw IllegalStateException("Instant was null")
}

fun ResultSet.getInstantOrNull(name: String): Instant? {
    return getTimestamp(name)?.toInstant()
}

fun <T> ResultSet.getIndexedId(parent: String, index: String): IndexedId<T> {
    return getIndexedIdOrNull(parent, index) ?: throw IllegalStateException("Indexed id was null")
}

fun <T> ResultSet.getIndexedIdOrNull(parent: String, index: String): IndexedId<T>? {
    val geometryAlignmentId = getIntOrNull(parent)
    val geometryElementIndex = getIntOrNull(index)
    return if (geometryAlignmentId != null && geometryElementIndex != null) {
        IndexedId(geometryAlignmentId, geometryElementIndex)
    } else {
        null
    }
}

fun <T> ResultSet.getIntId(name: String): IntId<T> {
    return getIntIdOrNull(name) ?: throw IllegalStateException("Int id was null")
}

fun <T> ResultSet.getIntIdOrNull(name: String): IntId<T>? {
    return getIntOrNull(name)?.let { i -> IntId(i) }
}

fun <T> ResultSet.getStringId(name: String): StringId<T> {
    return getStringIdOrNull(name) ?: throw IllegalStateException("String id was null")
}

fun <T> ResultSet.getStringIdOrNull(name: String): StringId<T>? {
    return getString(name)?.let { i -> StringId(i) }
}

fun <T> ResultSet.getOid(name: String): Oid<T> {
    return getOidOrNull(name) ?: throw IllegalStateException("OID was null")
}

fun <T> ResultSet.getOidOrNull(name: String): Oid<T>? {
    return getString(name)?.let { i -> Oid(i) }
}

fun ResultSet.getSrid(name: String): Srid {
    return getSridOrNull(name) ?: throw IllegalStateException("SRID was null")
}

fun ResultSet.getSridOrNull(name: String): Srid? {
    return getIntOrNull(name)?.let { i -> Srid(i) }
}

fun ResultSet.getTrackNumber(name: String): TrackNumber {
    return getTrackNumberOrNull(name) ?: throw IllegalStateException("Track number was null")
}

fun ResultSet.getTrackNumberOrNull(name: String): TrackNumber? {
    return getString(name)?.let { n -> TrackNumber(n) }
}

fun ResultSet.getKmNumber(name: String): KmNumber {
    return getKmNumberOrNull(name) ?: throw IllegalStateException("KM number was null")
}

fun ResultSet.getKmNumberOrNull(name: String): KmNumber? {
    return getString(name)?.let(::KmNumber)
}

fun ResultSet.getTrackMeter(name: String): TrackMeter {
    return getTrackMeterOrNull(name) ?: throw IllegalStateException("Track meter was null")
}

fun ResultSet.getTrackMeterOrNull(name: String): TrackMeter? {
    return getString(name)?.let(::TrackMeter)
}

fun ResultSet.getJointNumber(name: String): JointNumber {
    return getJointNumberOrNull(name) ?: throw IllegalStateException("Joint number was null")
}

fun ResultSet.getJointNumberOrNull(name: String): JointNumber? {
    return getIntOrNull(name)?.let { n -> JointNumber(n) }
}

fun ResultSet.getFeatureTypeCode(name: String): FeatureTypeCode {
    return getFeatureTypeCodeOrNull(name) ?: throw IllegalStateException("Feature type code was null")
}

fun ResultSet.getFeatureTypeCodeOrNull(name: String): FeatureTypeCode? {
    return getString(name)?.let { n -> FeatureTypeCode(n) }
}

fun ResultSet.getPoint(nameX: String, nameY: String): Point {
    return getPointOrNull(nameX, nameY) ?: throw IllegalStateException("Point does not exist in result set")
}

fun ResultSet.getPointOrNull(nameX: String, nameY: String): Point? {
    val x = getDoubleOrNull(nameX)
    val y = getDoubleOrNull(nameY)
    return if (x == null || y == null) null else Point(x, y)
}

inline fun <reified T : Enum<T>> ResultSet.getEnum(name: String): T {
    return getEnumOrNull<T>(name) ?: throw IllegalStateException("Enum value does not exist in result set")
}

inline fun <reified T : Enum<T>> ResultSet.getEnumOrNull(name: String): T? {
    return getString(name)?.let { stringValue -> enumValueOf<T>(stringValue) }
}

fun ResultSet.getStringListFromString(name: String): List<String> =
    getStringsListOrNullFromString(name)
        ?: throw IllegalStateException("List<String> value does not exist in result set")

fun ResultSet.getStringsListOrNullFromString(name: String): List<String>? = getString(name)?.split(",")

fun ResultSet.getDoubleListFromString(name: String): List<Double> =
    getDoubleListOrNullFromString(name)
        ?: throw IllegalStateException("List<Double> value does not exist in result set")

fun ResultSet.getDoubleListOrNullFromString(name: String): List<Double>? =
    getStringsListOrNullFromString(name)?.map { s -> s.toDouble() }

fun ResultSet.getNullableDoubleListOrNullFromString(name: String): List<Double?>? =
    getStringsListOrNullFromString(name)?.map { s -> s.toDoubleOrNull() }

fun ResultSet.getIntListFromString(name: String): List<Int> =
    getIntListOrNullFromString(name) ?: throw IllegalStateException("List<Int> value does not exist in result set")

fun ResultSet.getIntListOrNullFromString(name: String): List<Int>? {
    return getString(name)
        ?.split(",")
        ?.map { s -> s.toInt() }
}

fun ResultSet.getIntArray(name: String): List<Int> =
    getIntArrayOrNull(name) ?: throw IllegalStateException("Array does not exist in result set")

fun <T> ResultSet.getIntIdArray(name: String): List<IntId<T>> = getListOrNull<Int>(name)?.map(::IntId) ?: emptyList()

fun ResultSet.getIntArrayOrNull(name: String): List<Int>? = getListOrNull(name)

fun ResultSet.getNullableIntArray(name: String): List<Int?> =
    getNullableIntArrayOrNull(name) ?: throw IllegalStateException("Array does not exist in result set")

fun ResultSet.getNullableIntArrayOrNull(name: String): List<Int?>? = getListOrNull(name)

fun ResultSet.getStringArray(name: String): List<String> =
    getListOrNull(name) ?: throw IllegalStateException("Array does not exist in result set")

fun ResultSet.getStringArrayOrNull(name: String): List<String>? = getListOrNull(name)

fun ResultSet.getIntArrayOfArrayOrNull(name: String): List<List<Int>>? =
    getListOrNull<Array<Int>>(name)?.map { it.toList() }

inline fun <reified T> ResultSet.getListOrNull(name: String): List<T>? {
    val arrayObj = getArray(name)?.array
    return if (arrayObj is Array<*>) {
        (arrayObj as Array<out Any?>).toList().mapNotNull { o ->
            if (o is T) o
            else throw IllegalStateException("Array contains value of unexpected type: expectedType=${T::class.simpleName} found=$o")
        }
    } else {
        null
    }
}

inline fun <reified T> ResultSet.getList(name: String): List<T> {
    val arrayObj = getArray(name)?.array
    val list = (arrayObj as Array<out Any?>).toList()

    return list.mapNotNull { o ->
        if (o is T) o
        else throw IllegalStateException("Array contains value of unexpected type")
    }
}

fun <T> ResultSet.getDaoResponse(officialIdName: String, versionIdName: String, versionName: String) =
    DaoResponse<T>(
        id = getIntId(officialIdName),
        rowVersion = getRowVersion(versionIdName, versionName),
    )

fun <T> ResultSet.getRowVersion(idName: String, versionName: String): RowVersion<T> =
    RowVersion(getIntId(idName), getIntNonNull(versionName))

fun <T> ResultSet.getRowVersionOrNull(idName: String, versionName: String): RowVersion<T>? {
    val intId = getIntIdOrNull<T>(idName)
    val version = getIntOrNull(versionName)
    return if (intId != null && version != null) RowVersion(intId, version) else null
}

fun ResultSet.getIntNonNull(name: String) = getIntOrNull(name) ?: throw IllegalStateException("$name can't be null")

fun ResultSet.getCode(name: String): Code = getCodeOrNull(name)
    ?: throw IllegalStateException("StringCode was null")

fun ResultSet.getCodeOrNull(name: String): Code? = getString(name)?.let(::Code)

fun ResultSet.getFreeText(name: String): FreeText = getFreeTextOrNull(name)
    ?: throw IllegalStateException("FreeText was null")

fun ResultSet.getFreeTextOrNull(name: String): FreeText? = getString(name)?.let(::FreeText)

fun ResultSet.getFileName(name: String): FileName = getFileNameOrNull(name)
    ?: throw IllegalStateException("FileName was null")

fun ResultSet.getFileNameOrNull(name: String): FileName? = getString(name)?.let(::FileName)

fun ResultSet.getBbox(name: String): BoundingBox = getBboxOrNull(name)
    ?: throw IllegalStateException("Bounding box was null")

fun ResultSet.getBboxOrNull(name: String): BoundingBox? =
    getPolygonPointListOrNull(name)?.let(::boundingBoxAroundPoints)

fun ResultSet.getPolygonPointList(name: String): List<Point> = getPolygonPointListOrNull(name)
    ?: throw IllegalStateException("WKT Point list was null")

fun ResultSet.getPolygonPointListOrNull(name: String): List<Point>? =
    getString(name)?.let(::parse2DPolygon)

fun ResultSet.getVelhoName(name: String): PVName = getVelhoNameOrNull(name)
    ?: throw IllegalStateException("Velho name was null: column=$name")

fun ResultSet.getVelhoNameOrNull(name: String): PVName? =
    getString(name)?.let(::PVName)

fun ResultSet.getVelhoCode(name: String): PVCode = getVelhoCodeOrNull(name)
    ?: throw IllegalStateException("Velho code was null: column=$name")

fun ResultSet.getVelhoCodeOrNull(name: String): PVCode? =
    getString(name)?.let(::PVCode)

fun ResultSet.getVelhoId(name: String): PVId = getVelhoIdOrNull(name)
    ?: throw IllegalStateException("Velho code was null: column=$name")

fun ResultSet.getVelhoIdOrNull(name: String): PVId? =
    getString(name)?.let(::PVId)
