package fi.fta.geoviite.infra.util

import java.math.BigDecimal
import java.sql.JDBCType
import java.sql.PreparedStatement

fun PreparedStatement.setNullInt(index: Int) = setNull(index, JDBCType.INTEGER.vendorTypeNumber)

fun PreparedStatement.setNullDouble(index: Int) = setNull(index, JDBCType.DOUBLE.vendorTypeNumber)

fun PreparedStatement.setNullBigDecimal(index: Int) = setNull(index, JDBCType.DECIMAL.vendorTypeNumber)

fun PreparedStatement.setNullableInt(index: Int, getter: () -> Int?) = setNullableInt(index, getter())

fun PreparedStatement.setNullableDouble(index: Int, getter: () -> Double?) = setNullableDouble(index, getter())

fun PreparedStatement.setNullableInt(index: Int, value: Int?) =
    if (value != null) setInt(index, value) else setNullInt(index)

fun PreparedStatement.setNullableDouble(index: Int, value: Double?) =
    if (value != null) setDouble(index, value) else setNullDouble(index)

fun PreparedStatement.setNullableBigDecimal(index: Int, value: BigDecimal?) =
    if (value != null) setBigDecimal(index, value) else setNullBigDecimal(index)
