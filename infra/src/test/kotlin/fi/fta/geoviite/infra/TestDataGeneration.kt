package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import kotlin.math.abs

inline fun <reified T : Enum<T>> getSomeValue(index: Int): T {
    val values = enumValues<T>()
    return values[index % values.size]
}

inline fun <reified T : Enum<T>> getSomeNullableValue(index: Int): T? {
    val values: List<T?> = enumValues<T>().toList() + null
    return values[index % values.size]
}

fun <T> getSomeOid(seed: Int): Oid<T> = Oid("${abs(seed % 1000)}.${abs(seed * 2 % 1000)}.${abs(seed * 3 % 1000)}")

inline fun <reified T> someIntId() = IntId<T>(1)

inline fun <reified T> someVersion() = RowVersion(someIntId<T>(), 1)

fun asSwitchStructure(data: SwitchStructureData) = SwitchStructure(someVersion(), data)
