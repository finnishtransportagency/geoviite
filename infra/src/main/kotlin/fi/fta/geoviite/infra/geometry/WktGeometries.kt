package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IPoint3DM
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import net.postgis.jdbc.geometry.LineString


private const val POINT_SEPARATOR = ","
private const val COORDINATE_SEPARATOR = " "

private const val POINT_TYPE_2D = "POINT"
private const val LINESTRING_TYPE_2D = "LINESTRING"
private const val LINESTRING_TYPE_3DM = "LINESTRING M"
private const val POLYGON_TYPE_2D = "POLYGON"


fun parse2DPoint(point: String): Point =
    parse2DPointValues(dropParenthesis(dropTypeInfo(POINT_TYPE_2D, point)))

fun parse2DLineString(lineString: String): List<Point> =
    split2DPointValues(dropParenthesis(dropTypeInfo(LINESTRING_TYPE_2D, lineString)))

fun parse3DMLineString(lineString: String): List<Point3DM> =
    split3DMPointValues(dropParenthesis(dropTypeInfo(LINESTRING_TYPE_3DM, lineString)))

fun parse2DPolygon(polygon: String): List<Point> =
    split2DPointValues(dropParenthesis(dropTypeInfo(POLYGON_TYPE_2D, polygon), 2))

fun create2DPoint(coordinate: IPoint): String {
    val content = point2DToString(coordinate)
    return "$POINT_TYPE_2D${addParenthesis(content)}"
}

fun create2DLineString(coordinates: List<IPoint>): String {
    val content = coordinates.joinToString(POINT_SEPARATOR) { c -> point2DToString(c) }
    return "$LINESTRING_TYPE_2D${addParenthesis(content)}"
}

fun create3DMLineString(coordinates: List<IPoint3DM>): String {
    val content = coordinates.joinToString(POINT_SEPARATOR) { c -> point3DMToString(c) }
    return "$LINESTRING_TYPE_3DM${addParenthesis(content)}"
}

fun createPostgis3DMLineString(coordinates: List<IPoint3DM>): LineString {
    return LineString(coordinates.map { c ->
        val point = net.postgis.jdbc.geometry.Point(c.x, c.y)
        point.setM(c.m)
        point
    }.toTypedArray())
}

fun create2DPolygonString(coordinates: List<IPoint>): String {
    val content = coordinates.joinToString(POINT_SEPARATOR) { c -> point2DToString(c) }
    return "$POLYGON_TYPE_2D${addParenthesis(content, 2)}"
}

private fun point2DToString(coordinate: IPoint): String = "${coordinate.x}$COORDINATE_SEPARATOR${coordinate.y}"

private fun point3DMToString(coordinate: IPoint3DM): String =
    "${coordinate.x}$COORDINATE_SEPARATOR${coordinate.y}$COORDINATE_SEPARATOR${coordinate.m}"

fun split2DPointValues(valuesString: String): List<Point> {
    return valuesString.split(POINT_SEPARATOR).map { s -> parse2DPointValues(s) }
}

fun split3DMPointValues(valuesString: String): List<Point3DM> {
    return valuesString.split(POINT_SEPARATOR).map { s -> parse3DMPointValues(s) }
}

fun parse2DPointValues(pointString: String): Point {
    val values = pointString.split(COORDINATE_SEPARATOR)
    if (values.size != 2) throw IllegalArgumentException("2D geometry should contain X/Y values: ${values.size} <> 2")
    return Point(values[0].toDouble(), values[1].toDouble())
}

fun parse3DMPointValues(pointString: String): Point3DM {
    val values = pointString.split(COORDINATE_SEPARATOR)
    if (values.size != 3) throw IllegalArgumentException("3D geometry should contain X/Y/m values: ${values.size} <> 3")
    return Point3DM(values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
}

private fun dropTypeInfo(typeString: String, wktString: String): String {
    val actualTypeString = wktString.substringBefore("(")
    return if (typeString == actualTypeString.trim()) wktString.drop(actualTypeString.length)
    else throw IllegalStateException("WKT String not the expected type: expected=$typeString actual=$actualTypeString")
}

private fun addParenthesis(wktString: String, count: Int = 1): String =
    "${"(".repeat(count)}$wktString${")".repeat(count)}"

private fun dropParenthesis(wktString: String, count: Int = 1): String {
    val start = "(".repeat(count)
    val end = ")".repeat(count)
    return if (wktString.startsWith(start) && wktString.endsWith(end)) wktString.drop(count).dropLast(count)
    else throw IllegalArgumentException("WKT String didn't carry expected paranthesis: count=$count wkt=$wktString")
}
