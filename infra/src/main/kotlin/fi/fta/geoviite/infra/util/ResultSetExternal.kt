package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.authorization.AuthCode
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DesignLayoutContext
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.common.parseLayoutContextSqlString
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.parse2DPolygon
import fi.fta.geoviite.infra.geometry.PlanName
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.projektivelho.PVDictionaryCode
import fi.fta.geoviite.infra.projektivelho.PVDictionaryName
import fi.fta.geoviite.infra.projektivelho.PVId
import fi.fta.geoviite.infra.projektivelho.PVProjectName
import fi.fta.geoviite.infra.publication.Change
import fi.fta.geoviite.infra.publication.PublishedInBranch
import fi.fta.geoviite.infra.publication.PublishedInDesign
import fi.fta.geoviite.infra.publication.PublishedInMain
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.tracklayout.AnyM
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.DesignDraftContextData
import fi.fta.geoviite.infra.tracklayout.DesignOfficialContextData
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.MainDraftContextData
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.StoredAssetId
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate

fun ResultSet.getIntOrNull(name: String): Int? = getInt(name).takeUnless { wasNull() }

fun ResultSet.getLongOrNull(name: String): Long? = getLong(name).takeUnless { wasNull() }

fun ResultSet.getFloatOrNull(name: String): Float? = getFloat(name).takeUnless { wasNull() }

fun ResultSet.getDoubleOrNull(name: String): Double? = getDouble(name).takeUnless { wasNull() }

fun ResultSet.getBigDecimalOrNull(name: String): BigDecimal? = getBigDecimal(name).takeUnless { wasNull() }

fun ResultSet.getBooleanOrNull(name: String): Boolean? = getBoolean(name).takeUnless { wasNull() }

fun ResultSet.getInstant(name: String): Instant = verifyNotNull(name, ::getInstantOrNull)

fun ResultSet.getInstantOrNull(name: String): Instant? = getTimestamp(name)?.toInstant()

fun ResultSet.getLocalDate(name: String): LocalDate = verifyNotNull(name, ::getLocalDateOrNull)

fun ResultSet.getLocalDateOrNull(name: String): LocalDate? = getString(name)?.let(LocalDate::parse)

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

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowId(idName: String, contextIdName: String) =
    LayoutRowId(getIntId<T>(idName), getLayoutContext(contextIdName))

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowIdOrNull(idName: String, contextIdName: String) =
    getIntIdOrNull<T>(idName)?.let { id ->
        getLayoutContextOrNull(contextIdName)?.let { layoutContext -> LayoutRowId(id, layoutContext) }
    }

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowId(idName: String, designIdName: String, draftFlagName: String) =
    LayoutRowId(getIntId<T>(idName), getLayoutContext(designIdName, draftFlagName))

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowIdOrNull(idName: String, designIdName: String, draftFlagName: String) =
    getIntIdOrNull<T>(idName)?.let { id ->
        getLayoutContextOrNull(designIdName, draftFlagName)?.let { layoutContext -> LayoutRowId(id, layoutContext) }
    }

fun <T> ResultSet.getIntId(name: String): IntId<T> = verifyNotNull(name, ::getIntIdOrNull)

fun <T> ResultSet.getIntIdOrNull(name: String): IntId<T>? = getIntOrNull(name)?.let(::IntId)

fun <T> ResultSet.getStringId(name: String): StringId<T> = verifyNotNull(name, ::getStringIdOrNull)

fun <T> ResultSet.getStringIdOrNull(name: String): StringId<T>? = getString(name)?.let(::StringId)

fun <T> ResultSet.getUuid(name: String): Uuid<T> = verifyNotNull(name, ::getUuidOrNull)

fun <T> ResultSet.getUuidOrNull(name: String): Uuid<T>? = getString(name)?.let(::Uuid)

fun <T> ResultSet.getOid(name: String): Oid<T> = verifyNotNull(name, ::getOidOrNull)

fun <T> ResultSet.getOidOrNull(name: String): Oid<T>? = getString(name)?.let(::Oid)

fun <T> ResultSet.getRatkoExternalId(oidName: String, planItemIdName: String): RatkoExternalId<T> =
    verifyNotNull(oidName, planItemIdName, ::getRatkoExternalIdOrNull)

fun <T> ResultSet.getRatkoExternalIdOrNull(oidName: String, planItemIdName: String): RatkoExternalId<T>? {
    val oid = getOidOrNull<T>(oidName)
    val planItemId = getIntOrNull(planItemIdName)?.let(::RatkoPlanItemId)
    return if (oid == null) null else RatkoExternalId(oid, planItemId)
}

fun ResultSet.getSrid(name: String): Srid = verifyNotNull(name, ::getSridOrNull)

fun ResultSet.getSridOrNull(name: String): Srid? = getIntOrNull(name)?.let(::Srid)

fun ResultSet.getTrackNumber(name: String): TrackNumber =
    requireNotNull(getTrackNumberOrNull(name)) { "Track number was null" }

fun ResultSet.getTrackNumberOrNull(name: String): TrackNumber? = getString(name)?.let(::TrackNumber)

fun ResultSet.getKmNumber(name: String): KmNumber = verifyNotNull(name, ::getKmNumberOrNull)

fun ResultSet.getKmNumberOrNull(name: String): KmNumber? = getString(name)?.let(::KmNumber)

fun ResultSet.getTrackMeter(name: String): TrackMeter = verifyNotNull(name, ::getTrackMeterOrNull)

fun ResultSet.getTrackMeterOrNull(name: String): TrackMeter? = getString(name)?.let(::TrackMeter)

fun ResultSet.getJointNumber(name: String): JointNumber = verifyNotNull(name, ::getJointNumberOrNull)

fun ResultSet.getJointNumberOrNull(name: String): JointNumber? = getIntOrNull(name)?.let(::JointNumber)

fun ResultSet.getFeatureTypeCode(name: String): FeatureTypeCode = verifyNotNull(name, ::getFeatureTypeCodeOrNull)

fun ResultSet.getFeatureTypeCodeOrNull(name: String): FeatureTypeCode? = getString(name)?.let(::FeatureTypeCode)

fun ResultSet.getPoint(nameX: String, nameY: String): Point =
    requireNotNull(getPointOrNull(nameX, nameY)) { "Point does not exist in result set: nameX=$nameX nameY=$nameY" }

fun ResultSet.getGeometryPoint(nameX: String, nameY: String, nameSrid: String): GeometryPoint =
    getPoint(nameX, nameY).let { point -> GeometryPoint(point.x, point.y, getSrid(nameSrid)) }

fun ResultSet.getGeometryPointOrNull(nameX: String, nameY: String, nameSrid: String): GeometryPoint? =
    getPointOrNull(nameX, nameY)?.let { point ->
        getSridOrNull(nameSrid)?.let { srid -> GeometryPoint(point.x, point.y, srid) }
    }

fun <M : AnyM<M>> ResultSet.getPoint3DMOrNull(nameX: String, nameY: String, nameM: String): Point3DM<M>? {
    val x = getDoubleOrNull(nameX)
    val y = getDoubleOrNull(nameY)
    val m = getDoubleOrNull(nameM)
    return if (x == null || y == null || m == null) null else Point3DM(x, y, LineM(m))
}

fun ResultSet.getPointOrNull(nameX: String, nameY: String): Point? {
    val x = getDoubleOrNull(nameX)
    val y = getDoubleOrNull(nameY)
    return if (x == null || y == null) null else Point(x, y)
}

inline fun <reified T : Enum<T>> ResultSet.getEnum(name: String): T {
    return getEnumOrNull<T>(name) ?: error("Enum value does not exist in result set: name=$name")
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

fun <T> ResultSet.getNullableIntIdArray(name: String): List<IntId<T>?> =
    getNullableListOrNull<Int>(name)?.map { it?.let(::IntId) } ?: emptyList()

fun ResultSet.getIntArray(name: String): List<Int> = verifyNotNull(name, ::getIntArrayOrNull)

fun ResultSet.getIntArrayOrNull(name: String): List<Int>? = getListOrNull(name)

fun ResultSet.getBigDecimalArray(name: String): List<BigDecimal> = verifyNotNull(name, ::getBigDecimalArrayOrNull)

fun ResultSet.getBigDecimalArrayOrNull(name: String): List<BigDecimal>? = getListOrNull(name)

fun ResultSet.getNullableBigDecimalArray(name: String): List<BigDecimal?> =
    verifyNotNull(name, ::getNullableBigDecimalArrayOrNull)

fun ResultSet.getNullableBigDecimalArrayOrNull(name: String): List<BigDecimal?>? = getNullableListOrNull(name)

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

inline fun <reified T : Enum<T>> ResultSet.getEnumArray(name: String): List<T> =
    verifyNotNull(name) { getEnumArrayOrNull<T>(name) }

inline fun <reified T : Enum<T>> ResultSet.getEnumArrayOrNull(name: String): List<T>? =
    getStringArrayOrNull(name)?.map { string -> enumValueOf<T>(string) }

inline fun <reified T : Enum<T>> ResultSet.getNullableEnumArray(name: String): List<T?> =
    verifyNotNull(name) { getNullableEnumArrayOrNull<T>(name) }

inline fun <reified T : Enum<T>> ResultSet.getNullableEnumArrayOrNull(name: String): List<T?>? =
    getNullableStringArrayOrNull(name)?.map { string -> string?.let { s -> enumValueOf<T>(s) } }

fun ResultSet.getIntArrayOfArrayOrNull(name: String): List<List<Int>>? =
    getListOrNull<Array<Int>>(name)?.map { it.toList() }

inline fun <reified T> ResultSet.getList(name: String): List<T> = verifyNotNull(name, ::getListOrNull)

inline fun <reified T> ResultSet.getListOrNull(name: String): List<T>? =
    getArray(name)?.array?.let { arr ->
        if (arr is Array<*>) (arr as Array<out Any?>).mapNotNull(::verifyType) else null
    }

inline fun <reified T> ResultSet.getNullableListOrNull(name: String): List<T?>? =
    getArray(name)?.array?.let { arr ->
        if (arr is Array<*>) (arr as Array<out Any?>).map { it?.let(::verifyType) } else null
    }

fun <T> ResultSet.getRowVersion(idName: String, versionName: String): RowVersion<T> =
    RowVersion(getIntId(idName), getIntNonNull(versionName))

fun <T> ResultSet.getRowVersionOrNull(idName: String, versionName: String): RowVersion<T>? {
    val rowId = getIntIdOrNull<T>(idName)
    val version = getIntOrNull(versionName)
    return if (rowId != null && version != null) RowVersion(rowId, version) else null
}

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersion(
    idName: String,
    contextIdName: String,
    versionName: String,
): LayoutRowVersion<T> = LayoutRowVersion(getLayoutRowId(idName, contextIdName), getIntNonNull(versionName))

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersionOrNull(
    idName: String,
    contextIdName: String,
    versionName: String,
): LayoutRowVersion<T>? {
    val rowId = getLayoutRowIdOrNull<T>(idName, contextIdName)
    val version = getIntOrNull(versionName)
    return if (rowId != null && version != null) LayoutRowVersion(rowId, version) else null
}

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersion(
    idName: String,
    layoutBranchName: String,
    publicationStateName: String,
    versionName: String,
): LayoutRowVersion<T> =
    LayoutRowVersion(getLayoutRowId(idName, layoutBranchName, publicationStateName), getIntNonNull(versionName))

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersionOrNull(
    idName: String,
    layoutBranchName: String,
    publicationStateName: String,
    versionName: String,
): LayoutRowVersion<T>? {
    val rowId = getLayoutRowIdOrNull<T>(idName, layoutBranchName, publicationStateName)
    val version = getIntOrNull(versionName)
    return if (rowId != null && version != null) LayoutRowVersion(rowId, version) else null
}

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersionArray(
    idsName: String,
    designIdsName: String,
    publicationStatesName: String,
    versionsName: String,
): List<LayoutRowVersion<T>> {
    val ids = getIntIdArray<T>(idsName)
    val branches = getLayoutBranchArray(designIdsName)
    val drafts = getBooleanArray(publicationStatesName)
    val versions = getIntArray(versionsName)
    return ids.mapIndexed { index, id ->
        LayoutRowVersion(id, branches[index].let { if (drafts[index]) it.draft else it.official }, versions[index])
    }
}

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersionArray(prefix: String): List<LayoutRowVersion<T>> =
    getLayoutRowVersionArray("${prefix}_ids", "${prefix}_layout_context_ids", "${prefix}_versions")

fun <T : LayoutAsset<T>> ResultSet.getLayoutRowVersionArray(
    idsName: String,
    layoutContextIdsName: String,
    versionsName: String,
): List<LayoutRowVersion<T>> {
    val ids = getIntIdArray<T>(idsName)
    val contextIds = getLayoutContextArray(layoutContextIdsName)
    val versions = getIntArray(versionsName)
    return ids.mapIndexed { index, id -> LayoutRowVersion(id, contextIds[index], versions[index]) }
}

fun <T : LayoutAsset<T>> ResultSet.getNullableLayoutRowVersionArray(prefix: String): List<LayoutRowVersion<T>?> =
    getNullableLayoutRowVersionArray("${prefix}_ids", "${prefix}_layout_context_ids", "${prefix}_versions")

fun <T : LayoutAsset<T>> ResultSet.getNullableLayoutRowVersionArray(
    idsName: String,
    layoutContextIdsName: String,
    versionsName: String,
): List<LayoutRowVersion<T>?> {
    val ids = getNullableIntIdArray<T>(idsName)
    val contextIds = getNullableLayoutContextArray(layoutContextIdsName)
    val versions = getNullableIntArray(versionsName)
    return ids.mapIndexed { index, id ->
        id?.let {
            LayoutRowVersion(
                id,
                requireNotNull(contextIds[index]) {
                    "Layout context id was null for index $index in $layoutContextIdsName"
                },
                requireNotNull(versions[index]) { "Version was null for index $index in $versionsName" },
            )
        }
    }
}

fun ResultSet.getBooleanArray(name: String) = getList<Boolean>(name)

fun ResultSet.getIntNonNull(name: String) = getIntOrNull(name) ?: error("$name can't be null")

fun ResultSet.getCode(name: String): AuthCode = getCodeOrNull(name) ?: error("StringCode was null")

fun ResultSet.getCodeOrNull(name: String): AuthCode? = getString(name)?.let(::AuthCode)

fun ResultSet.getFreeText(name: String): FreeText = verifyNotNull(name, ::getFreeTextOrNull)

fun ResultSet.getFreeTextOrNull(name: String): FreeText? = getString(name)?.let(::FreeText)

fun ResultSet.getFreeTextWithNewLines(name: String): FreeTextWithNewLines =
    verifyNotNull(name, ::getFreeTextWithNewLinesOrNull)

fun ResultSet.getFreeTextWithNewLinesOrNull(name: String): FreeTextWithNewLines? =
    getString(name)?.let(FreeTextWithNewLines::of)

fun ResultSet.getFileName(name: String): FileName = verifyNotNull(name, ::getFileNameOrNull)

fun ResultSet.getFileNameOrNull(name: String): FileName? = getString(name)?.let(::FileName)

fun ResultSet.getPlanName(name: String): PlanName = verifyNotNull<PlanName>(name) { getString(name)?.let(::PlanName) }

fun ResultSet.getBbox(name: String): BoundingBox = verifyNotNull(name, ::getBboxOrNull)

fun ResultSet.getBboxOrNull(name: String): BoundingBox? =
    getPolygonPointListOrNull(name)?.let(::boundingBoxAroundPoints)

fun ResultSet.getPolygonPointList(name: String): List<Point> = verifyNotNull(name, ::getPolygonPointListOrNull)

fun ResultSet.getPolygonPointListOrNull(name: String): List<Point>? = getString(name)?.let(::parse2DPolygon)

fun ResultSet.getUnsafeString(name: String): UnsafeString = verifyNotNull(name, ::getUnsafeStringOrNull)

fun ResultSet.getUnsafeStringOrNull(name: String): UnsafeString? = getString(name)?.let(::UnsafeString)

fun ResultSet.getPVDictionaryName(name: String): PVDictionaryName = verifyNotNull(name, ::getPVDictionaryNameOrNull)

fun ResultSet.getPVDictionaryNameOrNull(name: String): PVDictionaryName? =
    getUnsafeStringOrNull(name)?.let(::PVDictionaryName)

fun ResultSet.getPVProjectName(name: String): PVProjectName = verifyNotNull(name, ::getPVProjectNameOrNull)

fun ResultSet.getPVProjectNameOrNull(name: String): PVProjectName? = getUnsafeStringOrNull(name)?.let(::PVProjectName)

fun ResultSet.getPVDictionaryCode(name: String): PVDictionaryCode = verifyNotNull(name, ::getPVDictionaryCodeOrNull)

fun ResultSet.getPVDictionaryCodeOrNull(name: String): PVDictionaryCode? = getString(name)?.let(::PVDictionaryCode)

fun ResultSet.getPVId(name: String): PVId = verifyNotNull(name, ::getPVIdOrNull)

fun ResultSet.getPVIdOrNull(name: String): PVId? = getString(name)?.let(::PVId)

fun <T> ResultSet.getChange(name: String, getter: (name: String) -> T?): Change<T> =
    Change(getter("old_$name"), getter(name))

fun ResultSet.getChangePoint(nameX: String, nameY: String) =
    Change(getPointOrNull("old_$nameX", "old_$nameY"), getPointOrNull(nameX, nameY))

fun ResultSet.getChangeGeometryPoint(nameX: String, nameY: String, sridName: String) =
    Change(
        getGeometryPointOrNull("old_$nameX", "old_$nameY", "old_$sridName"),
        getGeometryPointOrNull(nameX, nameY, sridName),
    )

fun <T> ResultSet.getChangeRowVersion(idName: String, versionName: String): Change<RowVersion<T>> =
    Change(getRowVersionOrNull("old_$idName", "old_$versionName"), getRowVersionOrNull(idName, versionName))

fun ResultSet.getLayoutContext(contextIdName: String): LayoutContext =
    verifyNotNull(contextIdName, ::getLayoutContextOrNull)

fun ResultSet.getNullableLayoutContextArray(contextIdsName: String): List<LayoutContext?> =
    getNullableStringArray(contextIdsName).map { it?.let(::parseLayoutContextSqlString) }

fun ResultSet.getLayoutContextArray(contextIdsName: String): List<LayoutContext> =
    getStringArray(contextIdsName).map(::parseLayoutContextSqlString)

fun ResultSet.getLayoutContextOrNull(contextIdName: String): LayoutContext? =
    getString(contextIdName)?.let(::parseLayoutContextSqlString)

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

fun ResultSet.getLayoutBranch(designIdName: String): LayoutBranch =
    getIntIdOrNull<LayoutDesign>(designIdName).let { id -> if (id == null) LayoutBranch.main else DesignBranch.of(id) }

fun ResultSet.getPublicationPublishedIn(
    designIdName: String,
    designVersionName: String,
    parentIdName: String,
): PublishedInBranch =
    getIntIdOrNull<LayoutDesign>(designIdName).let { id ->
        if (id == null) PublishedInMain
        else PublishedInDesign(DesignBranch.of(id), getInt(designVersionName), getIntIdOrNull(parentIdName))
    }

// no getLayoutBranchOrNull, as we couldn't distinguish between when to return the main branch and
// when null

fun ResultSet.getLayoutBranchArray(designIdsName: String): List<LayoutBranch> =
    getNullableIntArray(designIdsName).map { id -> if (id == null) LayoutBranch.main else DesignBranch.of(IntId(id)) }

fun ResultSet.getLayoutBranchArrayOrNull(designIdsName: String): List<LayoutBranch>? =
    getNullableIntArrayOrNull(designIdsName)?.map { id ->
        if (id == null) LayoutBranch.main else DesignBranch.of(IntId(id))
    }

inline fun <reified T> verifyNotNull(column: String, nullableGet: (column: String) -> T?): T =
    requireNotNull(nullableGet(column)) { "Value was null: type=${T::class.simpleName} column=$column" }

inline fun <reified T> verifyNotNull(
    column1: String,
    column2: String,
    nullableGet: (column1: String, column2: String) -> T?,
): T =
    requireNotNull(nullableGet(column1, column2)) {
        "Value was null: type=${T::class.simpleName} column1=$column1 column2=$column2"
    }

inline fun <reified T> verifyType(value: Any?): T =
    value.let { v ->
        require(v is T) {
            "Value is of unexpected type: expected=${T::class.simpleName} actual=${v?.javaClass?.simpleName} value=${v}"
        }
        v
    }

fun <T : LayoutAsset<T>> ResultSet.getLayoutContextData(
    idName: String,
    designIdName: String,
    draftFlagName: String,
    versionName: String,
    designAssetStateName: String,
    originDesignIdName: String,
): LayoutContextData<T> {
    val designId = getIntIdOrNull<LayoutDesign>(designIdName)
    val isDraft = getBoolean(draftFlagName)
    val rowVersion = getLayoutRowVersion<T>(idName, designIdName, draftFlagName, versionName)
    val designAssetState = getEnumOrNull<DesignAssetState>(designAssetStateName)
    val originBranch = getLayoutBranch(originDesignIdName)
    return if (designId != null) {
        requireNotNull(designAssetState) { "Expected design asset state for $idName in design" }
        if (isDraft) {
            DesignDraftContextData(
                layoutAssetId = StoredAssetId(rowVersion),
                designId = designId,
                designAssetState = designAssetState,
            )
        } else {
            DesignOfficialContextData(
                layoutAssetId = StoredAssetId(rowVersion),
                designId = designId,
                designAssetState = designAssetState,
            )
        }
    } else if (isDraft) {
        MainDraftContextData(layoutAssetId = StoredAssetId(rowVersion), originBranch = originBranch)
    } else {
        MainOfficialContextData(layoutAssetId = StoredAssetId(rowVersion))
    }
}
