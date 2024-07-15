package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.geometry.jts.JTS
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.referencing.CRS
import org.geotools.referencing.GeodeticCalculator
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import kotlin.concurrent.getOrSet
import kotlin.math.round

val ETRS89_SRID = Srid(4258)
val FINNISH_GK_LONGITUDE_RANGE = 19..31
val FIN_GK19_SRID = Srid(3873)

fun isGkFinSrid(srid: Srid) = srid.code in 3873..3885

fun transformNonKKJCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint): Point {
    return Transformation.nonTriangulableTransform(sourceSrid, targetSrid).transform(point)
}


fun getFinnishGKCoordinateProjectionByLongitude(longitude:Double): Srid {
    val nearestLongitude = round(longitude).toInt()
    if (!FINNISH_GK_LONGITUDE_RANGE.contains(nearestLongitude))
        throw IllegalArgumentException("Cannot get Finnish GK coordinate projection by longitude $nearestLongitude")
    val longitudeRelativeToGk19 = nearestLongitude - 19
    return Srid(FIN_GK19_SRID.code + longitudeRelativeToGk19)
}

fun transformToGKCoordinate(sourceSrid: Srid, point: IPoint): GeometryPoint {
    if (isGkFinSrid(sourceSrid)) {
        return GeometryPoint(point, sourceSrid)
    }
    val etrs89Coord = transformNonKKJCoordinate(sourceSrid, ETRS89_SRID, point)
    val gkSrid = getFinnishGKCoordinateProjectionByLongitude(etrs89Coord.x)
    val gkPoint = transformNonKKJCoordinate(sourceSrid, gkSrid, point)
    return GeometryPoint(
        gkPoint.x,
        gkPoint.y,
        gkSrid
    )
}

fun calculateDistance(points: List<IPoint>, srid: Srid): Double = calculateDistance(points, crs(srid))

fun calculateDistance(srid: Srid, vararg points: IPoint): Double = calculateDistance(points.toList(), crs(srid))

private object GeodeticCalculatorCache {
    val cache: ThreadLocal<Map<CoordinateReferenceSystem, GeodeticCalculator>> = ThreadLocal()
    fun get(crs: CoordinateReferenceSystem) = cache.getOrSet { HashMap() }.getOrElse(crs) { GeodeticCalculator(crs) }
}

fun calculateDistance(points: List<IPoint>, ref: CoordinateReferenceSystem): Double {
    val gc = GeodeticCalculatorCache.get(ref)

    val coordinates = points.map { toCoordinate(it, ref) }
    return coordinates
        .mapIndexedNotNull { index, coordinate -> if (index == 0) null else listOf(coordinates[index - 1], coordinate) }
        .fold(0.0) { sum, coordinate ->
            gc.startingPosition = JTS.toDirectPosition(coordinate[0], ref)
            gc.destinationPosition = JTS.toDirectPosition(coordinate[1], ref)
            sum + gc.orthodromicDistance
        }
}

private val crsCache: MutableMap<Srid, CoordinateReferenceSystem> = mutableMapOf()
fun crs(srid: Srid): CoordinateReferenceSystem = crsCache.getOrPut(srid) { CRS.decode(srid.toString()) }

private val geometryFactory = JTSFactoryFinder.getGeometryFactory()
fun toJtsPolygon(polygonPoints: List<IPoint>, ref: CoordinateReferenceSystem): Polygon? {
    val geometryPointList = polygonPoints.map { point -> toJtsPoint(point, ref) }
    val geometryCollection = geometryFactory.createGeometryCollection(geometryPointList.toTypedArray()).coordinates
    return geometryFactory.createPolygon(geometryCollection)
}

fun toJtsPoint(point: IPoint, ref: CoordinateReferenceSystem): org.locationtech.jts.geom.Point {
    val coordinate = toCoordinate(point, ref)
    return geometryFactory.createPoint(coordinate)
}

fun toGvtPoint(point: org.locationtech.jts.geom.Point, ref: CoordinateReferenceSystem): Point {
    return when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> Point(point.x, point.y)
        CRS.AxisOrder.NORTH_EAST -> Point(point.y, point.x)
        else -> throw CoordinateTransformationException(order, point.x, point.y, ref.name.code)
    }
}

fun toPoint(coordinate: Coordinate, ref: CoordinateReferenceSystem): Point {
    return when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> Point(coordinate.x, coordinate.y)
        CRS.AxisOrder.NORTH_EAST -> Point(coordinate.y, coordinate.x)
        else -> throw CoordinateTransformationException(order, coordinate.x, coordinate.y, ref.name.code)
    }
}

private fun toCoordinate(point: IPoint, ref: CoordinateReferenceSystem): Coordinate =
    when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> Coordinate(point.x, point.y)
        CRS.AxisOrder.NORTH_EAST -> Coordinate(point.y, point.x)
        else -> throw CoordinateTransformationException(order, point.x, point.y, ref.name.code)
    }

fun boundingPolygonPointsByConvexHull(points: List<IPoint>, crs: CoordinateReferenceSystem): List<Point> {
    val coordinates = points.map { p -> toCoordinate(p, crs) }.toTypedArray()
    val geometryFactory = GeometryFactory(PrecisionModel(PrecisionModel.FLOATING), CRS.lookupEpsgCode(crs, false))
    val convexHull = ConvexHull(coordinates, geometryFactory).convexHull
    return convexHull.coordinates.map { c -> toPoint(c, crs) }
}

class CoordinateTransformationException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    constructor(point: IPoint, cause: Throwable? = null) :
            this("Could not transform coordinate $point", cause)

    constructor(order: CRS.AxisOrder, x: Double, y: Double, crs: String) :
            this("Cannot determine coordinate axis order x=$x y=$y crs=$crs order=$order")
}
