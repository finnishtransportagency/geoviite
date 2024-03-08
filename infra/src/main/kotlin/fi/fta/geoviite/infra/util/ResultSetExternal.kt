package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.parse2DPolygon
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.projektivelho.PVDictionaryCode
import fi.fta.geoviite.infra.projektivelho.PVDictionaryName
import fi.fta.geoviite.infra.projektivelho.PVId
import fi.fta.geoviite.infra.projektivelho.PVProjectName
import fi.fta.geoviite.infra.publication.Change
import fi.fta.geoviite.infra.tracklayout.*
import java.sql.ResultSet
import java.time.Instant


fun ResultSet.getIntOrNull(name: String): Int? = getInt(name).takeUnless { wasNull() }

fun ResultSet.getLongOrNull(name: String): Long? = getLong(name).takeUnless { wasNull() }

fun ResultSet.getFloatOrNull(name: String): Float? = getFloat(name).takeUnless { wasNull() }

fun ResultSet.getDoubleOrNull(name: String): Double? = getDouble(name).takeUnless { wasNull() }

fun ResultSet.getBooleanOrNull(name: String): Boolean? = getBoolean(name).takeUnless { wasNull() }

fun ResultSet.getInstant(name: String): Instant = verifyNotNull(name, ::getInstantOrNull)

fun ResultSet.getInstantOrNull(name: String): Instant? = getTimestamp(name)?.toInstant()

fun <T> ResultSet.getIndexedId(parent: String, index: String): IndexedId<T> =
    requireNotNull(getIndexedIdOrNull(parent, index)) {
        "ResultSet value was null: type=${IndexedId::class.simpleName} parent=$parent index=$index"
    }

fun <T> ResultSet.getIndexedIdOrNull(parent: String, index: String): IndexedId<T>? {
    val parentId = getIntOrNull(parent)
    val childIndex = getIntOrNull(index)
    return if (parentId != null && childIndex != null) {
        IndexedId(parentId, childIndex)
    } else {
        null
    }
}

fun <T> ResultSet.getIntId(name: String): IntId<T> = verifyNotNull(name, ::getIntIdOrNull)

fun <T> ResultSet.getIntIdOrNull(name: String): IntId<T>? = getIntOrNull(name)?.let(::IntId)

fun <T> ResultSet.getStringId(name: String): StringId<T> = verifyNotNull(name, ::getStringIdOrNull)

fun <T> ResultSet.getStringIdOrNull(name: String): StringId<T>? = getString(name)?.let(::StringId)

fun <T> ResultSet.getOid(name: String): Oid<T> = verifyNotNull(name, ::getOidOrNull)

fun <T> ResultSet.getOidOrNull(name: String): Oid<T>? = getString(name)?.let(::Oid)

fun ResultSet.getSrid(name: String): Srid = verifyNotNull(name, ::getSridOrNull)

fun ResultSet.getSridOrNull(name: String): Srid? = getIntOrNull(name)?.let(::Srid)

fun ResultSet.getTrackNumber(name: String): TrackNumber = requireNotNull(getTrackNumberOrNull(name)) {
    "Track number was null"
}

fun ResultSet.getTrackNumberOrNull(name: String): TrackNumber? = getString(name)?.let(::TrackNumber)

fun ResultSet.getKmNumber(name: String): KmNumber = verifyNotNull(name, ::getKmNumberOrNull)

fun ResultSet.getKmNumberOrNull(name: String): KmNumber? = getString(name)?.let(::KmNumber)

fun ResultSet.getTrackMeter(name: String): TrackMeter = verifyNotNull(name, ::getTrackMeterOrNull)

fun ResultSet.getTrackMeterOrNull(name: String): TrackMeter? = getString(name)?.let(::TrackMeter)

fun ResultSet.getJointNumber(name: String): JointNumber = verifyNotNull(name, ::getJointNumberOrNull)

fun ResultSet.getJointNumberOrNull(name: String): JointNumber? = getIntOrNull(name)?.let(::JointNumber)

fun ResultSet.getFeatureTypeCode(name: String): FeatureTypeCode = verifyNotNull(name, ::getFeatureTypeCodeOrNull)

fun ResultSet.getFeatureTypeCodeOrNull(name: String): FeatureTypeCode? = getString(name)?.let(::FeatureTypeCode)

fun ResultSet.getPoint(nameX: String, nameY: String): Point = requireNotNull(getPointOrNull(nameX, nameY)) {
    "Point does not exist in result set: nameX=$nameX nameY=$nameY"
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
    verifyNotNull(name, ::getStringsListOrNullFromString)

fun ResultSet.getStringsListOrNullFromString(name: String): List<String>? = getString(name)?.split(",")

fun ResultSet.getDoubleListFromString(name: String): List<Double> = verifyNotNull(name, ::getDoubleListOrNullFromString)

fun ResultSet.getDoubleListOrNullFromString(name: String): List<Double>? =
    getStringsListOrNullFromString(name)?.map { s -> s.toDouble() }

fun ResultSet.getNullableDoubleListOrNullFromString(name: String): List<Double?>? =
    getStringsListOrNullFromString(name)?.map { s -> s.toDoubleOrNull() }

fun ResultSet.getIntListFromString(name: String): List<Int> = verifyNotNull(name, ::getIntListOrNullFromString)

fun ResultSet.getIntListOrNullFromString(name: String): List<Int>? = getString(name)?.split(",")?.map { s -> s.toInt() }

fun <T> ResultSet.getIntIdArray(name: String): List<IntId<T>> = getListOrNull<Int>(name)?.map(::IntId) ?: emptyList()

fun ResultSet.getIntArray(name: String): List<Int> = verifyNotNull(name, ::getIntArrayOrNull)

fun ResultSet.getIntArrayOrNull(name: String): List<Int>? = getListOrNull(name)

fun ResultSet.getDoubleArray(name: String): List<Double> = verifyNotNull(name, ::getDoubleArrayOrNull)

fun ResultSet.getDoubleArrayOrNull(name: String): List<Double>? = getListOrNull(name)

fun ResultSet.getStringArray(name: String): List<String> = verifyNotNull(name, ::getListOrNull)

fun ResultSet.getStringArrayOrNull(name: String): List<String>? = getListOrNull(name)

fun ResultSet.getNullableIntArray(name: String): List<Int?> = verifyNotNull(name, ::getNullableIntArrayOrNull)

fun ResultSet.getNullableIntArrayOrNull(name: String): List<Int?>? = getNullableListOrNull(name)

fun ResultSet.getNullableDoubleArray(name: String): List<Double?> = verifyNotNull(name, ::getNullableDoubleArrayOrNull)

fun ResultSet.getNullableDoubleArrayOrNull(name: String): List<Double?>? = getNullableListOrNull(name)

fun ResultSet.getNullableStringArray(name: String): List<String?> = verifyNotNull(name, ::getNullableStringArrayOrNull)

fun ResultSet.getNullableStringArrayOrNull(name: String): List<String?>? = getNullableListOrNull(name)

inline fun <reified T : Enum<T>> ResultSet.getNullableEnumArray(name: String): List<T?> =
    verifyNotNull(name) { getNullableEnumArrayOrNull<T>(name) }

inline fun <reified T : Enum<T>> ResultSet.getNullableEnumArrayOrNull(name: String): List<T?>? =
    getNullableStringArrayOrNull(name)?.map { string -> string?.let { s -> enumValueOf<T>(s) } }

fun ResultSet.getIntArrayOfArrayOrNull(name: String): List<List<Int>>? =
    getListOrNull<Array<Int>>(name)?.map { it.toList() }

inline fun <reified T> ResultSet.getList(name: String): List<T> = verifyNotNull(name, ::getListOrNull)

inline fun <reified T> ResultSet.getListOrNull(name: String): List<T>? = getArray(name)?.array?.let { arr ->
    if (arr is Array<*>) (arr as Array<out Any?>).mapNotNull(::verifyType)
    else null
}

inline fun <reified T> ResultSet.getNullableListOrNull(name: String): List<T?>? = getArray(name)?.array?.let { arr ->
    if (arr is Array<*>) (arr as Array<out Any?>).map { it?.let(::verifyType) }
    else null
}

fun <T> ResultSet.getDaoResponse(officialIdName: String, versionIdName: String, versionName: String) = DaoResponse<T>(
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

fun ResultSet.getCode(name: String): Code = getCodeOrNull(name) ?: throw IllegalStateException("StringCode was null")

fun ResultSet.getCodeOrNull(name: String): Code? = getString(name)?.let(::Code)

fun ResultSet.getFreeText(name: String): FreeText = verifyNotNull(name, ::getFreeTextOrNull)

fun ResultSet.getFreeTextOrNull(name: String): FreeText? = getString(name)?.let(::FreeText)

fun ResultSet.getFreeTextWithNewLinesOrNull(name: String): FreeTextWithNewLines? = getString(name)?.let(::FreeTextWithNewLines)

fun ResultSet.getFileName(name: String): FileName = verifyNotNull(name, ::getFileNameOrNull)

fun ResultSet.getFileNameOrNull(name: String): FileName? = getString(name)?.let(::FileName)

fun ResultSet.getBbox(name: String): BoundingBox = verifyNotNull(name, ::getBboxOrNull)

fun ResultSet.getBboxOrNull(name: String): BoundingBox? =
    getPolygonPointListOrNull(name)?.let(::boundingBoxAroundPoints)

fun ResultSet.getPolygonPointList(name: String): List<Point> = verifyNotNull(name, ::getPolygonPointListOrNull)

fun ResultSet.getPolygonPointListOrNull(name: String): List<Point>? = getString(name)?.let(::parse2DPolygon)

fun ResultSet.getPVProjectName(name: String): PVProjectName = verifyNotNull(name, ::getPVProjectNameOrNull)

fun ResultSet.getPVProjectNameOrNull(name: String): PVProjectName? = getString(name)?.let(::PVProjectName)

fun ResultSet.getPVDictionaryName(name: String): PVDictionaryName = verifyNotNull(name, ::getPVDictionaryNameOrNull)

fun ResultSet.getPVDictionaryNameOrNull(name: String): PVDictionaryName? = getString(name)?.let(::PVDictionaryName)

fun ResultSet.getPVDictionaryCode(name: String): PVDictionaryCode = verifyNotNull(name, ::getPVDictionaryCodeOrNull)

fun ResultSet.getPVDictionaryCodeOrNull(name: String): PVDictionaryCode? = getString(name)?.let(::PVDictionaryCode)

fun ResultSet.getPVId(name: String): PVId = verifyNotNull(name, ::getPVIdOrNull)

fun ResultSet.getPVIdOrNull(name: String): PVId? = getString(name)?.let(::PVId)

fun <T> ResultSet.getChange(name: String, getter: (name: String) -> T?): Change<T> =
    Change(getter("old_$name"), getter(name))

fun ResultSet.getChangePoint(nameX: String, nameY: String) =
    Change(getPointOrNull("old_$nameX", "old_$nameY"), getPointOrNull(nameX, nameY))

fun <T> ResultSet.getChangeRowVersion(idName: String, versionName: String): Change<RowVersion<T>> =
    Change(getRowVersionOrNull("old_$idName", "old_$versionName"), getRowVersionOrNull(idName, versionName))

fun ResultSet.getLayoutContext(designIdName: String, draftFlagName: String): LayoutContext =
    toLayoutContext(getPublicationState(draftFlagName), getIntIdOrNull(designIdName))

fun ResultSet.getLayoutContextOrNull(designIdName: String, draftFlagName: String): LayoutContext? =
    getPublicationStateOrNull(draftFlagName)?.let { state -> toLayoutContext(state, getIntIdOrNull(designIdName)) }

fun ResultSet.getDesignLayoutContext(designIdName: String, draftFlagName: String): DesignLayoutContext =
    DesignLayoutContext.of(getIntId(designIdName), getPublicationState(draftFlagName))

fun ResultSet.getDesignLayoutContextOrNull(designIdName: String, draftFlagName: String): DesignLayoutContext? =
    getIntIdOrNull<LayoutDesign>(designIdName)
        ?.let { id -> id to getPublicationState(draftFlagName) }
        ?.let { (id, state) -> DesignLayoutContext.of(id, state) }

fun ResultSet.getMainLayoutContext(draftFlagName: String): MainLayoutContext =
    MainLayoutContext.of(getPublicationState(draftFlagName))

fun ResultSet.getMainLayoutContextOrNull(draftFlagName: String): MainLayoutContext? =
    getPublicationStateOrNull(draftFlagName)?.let(MainLayoutContext::of)

private fun toLayoutContext(state: PublicationState, designId: IntId<LayoutDesign>?): LayoutContext =
    designId?.let { id -> DesignLayoutContext.of(id, state) } ?: MainLayoutContext.of(state)

fun ResultSet.getPublicationState(draftFlagName: String): PublicationState =
    requireNotNull(getPublicationStateOrNull(draftFlagName)) { "Value was null: type=Boolean column=$draftFlagName" }

fun ResultSet.getPublicationStateOrNull(draftFlagName: String): PublicationState? =
    getBooleanOrNull(draftFlagName)?.let { draft -> if (draft) PublicationState.DRAFT else PublicationState.OFFICIAL }

inline fun <reified T> verifyNotNull(column: String, nullableGet: (column: String) -> T?): T =
    requireNotNull(nullableGet(column)) { "Value was null: type=${T::class.simpleName} column=$column" }

inline fun <reified T> verifyType(value: Any?): T = value.let { v ->
    require(v is T) { "Value is of unexpected type: expected=${T::class.simpleName} value=${v}" }
    v
}

fun <T> ResultSet.getLayoutContextData(
    officialRowIdName: String,
    rowIdName: String,
    draftFlagName: String,
): LayoutContextData<T> {
    // TODO: GVT-2395 Read design context data
    val officialRowId = getIntIdOrNull<T>(officialRowIdName)
    val rowId = getIntId<T>(rowIdName)
    val isDraft = getBoolean(draftFlagName)
    return if (isDraft) {
        MainDraftContextData(
            officialRowId = officialRowId,
            rowId = rowId,
            designRowId = null,
            dataType = DataType.STORED,
        )
    } else {
        require(officialRowId == null) {
            "For official rows, official row ref should be null: officialRow=$officialRowId rowId=$rowId draft=$isDraft"
        }
        MainOfficialContextData(rowId, DataType.STORED)
    }
}

inline fun <reified T, S> mapIfTypeMatces(value: Any, getter: (T) -> S): S? = if (value is T) getter(value) else null
