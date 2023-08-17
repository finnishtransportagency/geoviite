package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import org.geotools.geometry.jts.JTS
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.referencing.CRS
import org.geotools.referencing.GeodeticCalculator
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.opengis.referencing.crs.CoordinateReferenceSystem
import java.util.concurrent.ConcurrentHashMap

fun transformNonKKJCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint): Point {
    return Transformation.nonTriangulableTransform(sourceSrid, targetSrid).transform(point)
}

fun calculateDistance(points: List<IPoint>, srid: Srid): Double = calculateDistance(points, crs(srid))

fun calculateDistance(srid: Srid, vararg points: IPoint): Double = calculateDistance(points.toList(), crs(srid))

private object GeodeticCalculatorCache {
    val cache: MutableMap<CoordinateReferenceSystem, GeodeticCalculator> = ConcurrentHashMap()
    fun get(crs: CoordinateReferenceSystem) = cache.computeIfAbsent(crs, ::GeodeticCalculator)
}

fun calculateDistance(points: List<IPoint>, ref: CoordinateReferenceSystem): Double {
    val gc = GeodeticCalculatorCache.get(ref)

    val coordinates = points.map { toCoordinate(it, ref) }
    return coordinates
        .mapIndexedNotNull { index, coordinate -> if (index == 0) null else listOf(coordinates[index - 1], coordinate) }
        .fold(0.0) { sum, coordinate ->
            gc.setStartingPosition(JTS.toDirectPosition(coordinate[0], ref))
            gc.setDestinationPosition(JTS.toDirectPosition(coordinate[1], ref))
            sum + gc.orthodromicDistance
        }
}

private val crsCache: MutableMap<Srid, CoordinateReferenceSystem> = mutableMapOf()
fun crs(srid: Srid): CoordinateReferenceSystem = crsCache.getOrPut(srid) { CRS.decode(srid.toString()) }

fun toJtsPolygon(polygonPoints: List<IPoint>, ref: CoordinateReferenceSystem): Polygon? {
    val geometryPointList = polygonPoints.map { point -> toJtsPoint(point, ref) }
    val geometryFactory = JTSFactoryFinder.getGeometryFactory()
    val geometryCollection = geometryFactory.createGeometryCollection(geometryPointList.toTypedArray()).coordinates
    return geometryFactory.createPolygon(geometryCollection)
}

fun toJtsPoint(point: IPoint, ref: CoordinateReferenceSystem): org.locationtech.jts.geom.Point {
    val coordinate = toCoordinate(point, ref)
    return JTSFactoryFinder.getGeometryFactory().createPoint(coordinate)
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
