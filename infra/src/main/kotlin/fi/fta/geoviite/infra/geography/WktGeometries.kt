package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IPoint3DM
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.util.logger


private const val POINT_SEPARATOR = ","
private const val COORDINATE_SEPARATOR = " "

private const val POINT_TYPE_2D = "POINT"
private const val LINESTRING_TYPE_2D = "LINESTRING"
private const val LINESTRING_TYPE_3DM = "LINESTRING M"
private const val POLYGON_TYPE_2D = "POLYGON"


fun parse2DPoint(point: String): Point = parse2DPointValues(dropWktType(point, POINT_TYPE_2D))

fun parse2DLineString(lineString: String): List<Point> = split2DPointValues(dropWktType(lineString, LINESTRING_TYPE_2D))

fun parse3DMLineString(lineString: String): List<Point3DM> =
    split3DMPointValues(dropWktType(lineString, LINESTRING_TYPE_3DM))

fun parse2DPolygon(polygon: String): List<Point> = split2DPointValues(dropWktType(polygon, POLYGON_TYPE_2D, 2))

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

fun split3DMPointValues(valuesString: String): List<Point3DM> = try {
    valuesString.split(POINT_SEPARATOR).map { s -> parse3DMPointValue(s) }
} catch (e: NumberFormatException) {
    logger.error("tried=$valuesString")
    throw e
}

fun parse2DPointValues(pointString: String): Point {
    val values = pointString.split(COORDINATE_SEPARATOR)
    if (values.size != 2) throw IllegalArgumentException("2D geometry should contain X/Y values: ${values.size} <> 2")
    return Point(values[0].toDouble(), values[1].toDouble())
}

fun parse3DMPointValue(pointString: String): Point3DM {
    val values = pointString.split(COORDINATE_SEPARATOR).also { values ->
        require(values.size == 3) { "3D geometry should contain X/Y/m values: ${values.size} <> 3" }
    }
    return Point3DM(values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
}

private fun dropWktType(wkt: String, typeString: String, parenthesis: Int = 1): String {
    val actualTypeString = wkt.substringBefore("(")
    require(typeString == actualTypeString.trim()) { "WKT type does not match: expected=$typeString actual=$actualTypeString" }
    return wkt.substring(actualTypeString.length + parenthesis, wkt.length - parenthesis)
}

private fun addParenthesis(wktString: String, count: Int = 1): String =
    "${"(".repeat(count)}$wktString${")".repeat(count)}"
