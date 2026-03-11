package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.parseLayoutContextSqlString

private const val ID_SEPARATOR = "__"
private const val VERSION_SEPARATOR = "_v"

data class LayoutRowId<T : LayoutAsset<T>>
@JsonCreator(mode = DISABLED)
constructor(val id: IntId<T>, val context: LayoutContext)

data class LayoutRowVersion<T : LayoutAsset<T>>
@JsonCreator(mode = DISABLED)
constructor(val rowId: LayoutRowId<T>, val version: Int) {
    constructor(
        id: IntId<T>,
        layoutContext: LayoutContext,
        version: Int,
    ) : this(LayoutRowId(id, layoutContext), version)

    private constructor(
        versionTriple: Triple<IntId<T>, LayoutContext, Int>
    ) : this(versionTriple.first, versionTriple.second, versionTriple.third)

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parseLayoutRowVersionValues(value))

    init {
        require(version > 0) { "Version numbers start at 1: version=$version" }
    }

    val id: IntId<T>
        get() = rowId.id

    val context: LayoutContext
        get() = rowId.context

    @JsonValue
    override fun toString(): String = "${id.intValue}$ID_SEPARATOR${context.toSqlString()}$VERSION_SEPARATOR$version"

    fun next() = LayoutRowVersion(rowId, version + 1)

    fun previous() = if (version > 1) LayoutRowVersion(rowId, version - 1) else null
}

fun <T : LayoutAsset<T>> parseLayoutRowVersionValues(text: String): Triple<IntId<T>, LayoutContext, Int> {
    val idSplit = text.split(ID_SEPARATOR)
    require(idSplit.size == 2) {
        "LayoutRowVersion text must consist of two parts split by $ID_SEPARATOR, but was $text"
    }
    val versionSplit = idSplit[1].split(VERSION_SEPARATOR)
    require(versionSplit.size == 2) {
        "LayoutRowVersion version text must consist of two parts split by $VERSION_SEPARATOR, but was $versionSplit"
    }
    return Triple(IntId(idSplit[0].toInt()), parseLayoutContextSqlString(versionSplit[0]), versionSplit[1].toInt())
}
