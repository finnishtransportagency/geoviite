package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.getOrSet
import kotlin.math.round
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.api.referencing.operation.MathTransform
import org.geotools.geometry.jts.GeometryBuilder
import org.geotools.geometry.jts.JTS
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.referencing.CRS
import org.geotools.referencing.GeodeticCalculator
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate as JtsCoordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString as JtsLineString
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel

val FINNISH_GK_LONGITUDE_RANGE = 19..31

private val geotoolsTransformations: MutableMap<Pair<Srid, Srid>, Transformation> = ConcurrentHashMap()

fun geotoolsTransformation(sourceSrid: Srid, targetSrid: Srid): Transformation =
    geotoolsTransformations.computeIfAbsent(Pair(sourceSrid, targetSrid)) {
        require(!isKKJ(sourceSrid)) { "KKJ ($sourceSrid) cannot be accurately transformed with GeoTools" }
        require(!isKKJ(targetSrid)) { "KKJ ($targetSrid) cannot be accurately transformed with GeoTools" }
        GeotoolsTransformation(sourceSrid, targetSrid)
    }

/**
 * GeoTools will lazily intialize some classes, which can be an issue if the first invocation comes from a thread in
 * ForkJoinPool as they have a different classloader. To ensure that the classes are properly loaded, call this method
 * from the main thread before launching Geoviite.
 *
 * For details, see Spring issue: https://github.com/spring-projects/spring-boot/issues/39843 Also
 * https://extranet.vayla.fi/jira/browse/GVT-2698 for more specifics on geoviite
 */
fun initGeotools() {
    logger.info("Initializing GeoTools (preload default crs)")
    geoviiteDefaultSrids.forEach { srid -> crs(srid) }
}

fun transformNonKKJCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint): Point {
    return geotoolsTransformation(sourceSrid, targetSrid).transform(point)
}

fun getFinnishGKCoordinateProjectionByLongitude(longitude: Double): Srid {
    val nearestLongitude = round(longitude).toInt()
    require(FINNISH_GK_LONGITUDE_RANGE.contains(nearestLongitude)) {
        "Cannot get Finnish GK coordinate projection by longitude $nearestLongitude"
    }
    val longitudeRelativeToGk19 = nearestLongitude - 19
    return Srid(FIN_GK19_SRID.code + longitudeRelativeToGk19)
}

fun transformFromLayoutToGKCoordinate(point: IPoint): GeometryPoint {
    val etrs89Coord = transformNonKKJCoordinate(LAYOUT_SRID, ETRS89_SRID, point)
    val gkSrid = getFinnishGKCoordinateProjectionByLongitude(etrs89Coord.x)
    val gkPoint = transformNonKKJCoordinate(LAYOUT_SRID, gkSrid, point)
    return GeometryPoint(gkPoint.x, gkPoint.y, gkSrid)
}

fun calculateDistance(points: List<IPoint>, srid: Srid): Double = calculateDistance(points, crs(srid))

fun calculateDistance(srid: Srid, vararg points: IPoint): Double = calculateDistance(points.toList(), crs(srid))

private object GeodeticCalculatorCache {
    val cache: ThreadLocal<Map<CoordinateReferenceSystem, GeodeticCalculator>> = ThreadLocal()

    fun get(crs: CoordinateReferenceSystem) = cache.getOrSet { HashMap() }.getOrElse(crs) { GeodeticCalculator(crs) }
}

fun calculateDistance(points: List<IPoint>, ref: CoordinateReferenceSystem): Double {
    val gc = GeodeticCalculatorCache.get(ref)

    val coordinates = points.map { toJtsCoordinate(it, ref) }
    return coordinates
        .mapIndexedNotNull { index, coordinate -> if (index == 0) null else listOf(coordinates[index - 1], coordinate) }
        .fold(0.0) { sum, coordinate ->
            gc.startingPosition = JTS.toDirectPosition(coordinate[0], ref)
            gc.destinationPosition = JTS.toDirectPosition(coordinate[1], ref)
            sum + gc.orthodromicDistance
        }
}

private val crsCache: MutableMap<Srid, CoordinateReferenceSystem> = ConcurrentHashMap()

fun crs(srid: Srid): CoordinateReferenceSystem = crsCache.getOrPut(srid) { CRS.decode(srid.toString()) }

private val geometryFactory = JTSFactoryFinder.getGeometryFactory()

internal fun toJtsGeoPolygon(points: List<IPoint>, srid: Srid): JtsPolygon {
    val jtsPoints = points.map { point -> toJtsGeoPoint(point, crs(srid)) }.toTypedArray()
    val geometryCollection = geometryFactory.createGeometryCollection(jtsPoints).coordinates
    return requireNotNull(geometryFactory.createPolygon(geometryCollection)) {
        "Failed to create JTS polygon: jtsPoints=$jtsPoints"
    }
}

internal fun toJtsGeoPoint(point: IPoint, srid: Srid): JtsPoint = toJtsGeoPoint(toJtsCoordinate(point, crs(srid)))

private fun toJtsGeoPoint(point: IPoint, ref: CoordinateReferenceSystem): JtsPoint =
    toJtsGeoPoint(toJtsCoordinate(point, ref))

internal fun toJtsGeoPoint(coordinate: JtsCoordinate): JtsPoint {
    return requireNotNull(geometryFactory.createPoint(coordinate)) {
        "Failed to create JTS coordinate: coordinate=$coordinate"
    }
}

private val jtsBuilder = GeometryBuilder()

internal fun toJtsBox(x: Range<Double>, y: Range<Double>): JtsPolygon = jtsBuilder.box(x.min, y.min, x.max, y.max)

internal fun toJtsLineString(points: List<IPoint>): JtsLineString = jtsBuilder.lineString(*pointArray(points))

internal fun toJtsPolygon(points: List<IPoint>): JtsPolygon = jtsBuilder.polygon(*pointArray(points))

private fun pointArray(points: List<IPoint>): DoubleArray = points.flatMap { p -> listOf(p.x, p.y) }.toDoubleArray()

private fun toGvtPoint(point: JtsPoint, ref: CoordinateReferenceSystem): Point {
    return when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> Point(point.x, point.y)
        CRS.AxisOrder.NORTH_EAST -> Point(point.y, point.x)
        else -> throw CoordinateTransformationException(order, point.x, point.y, ref.name.code)
    }
}

fun toGvtPoint(coordinate: JtsCoordinate, ref: CoordinateReferenceSystem): Point {
    return when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> Point(coordinate.x, coordinate.y)
        CRS.AxisOrder.NORTH_EAST -> Point(coordinate.y, coordinate.x)
        else -> throw CoordinateTransformationException(order, coordinate.x, coordinate.y, ref.name.code)
    }
}

private fun toJtsCoordinate(point: IPoint, ref: CoordinateReferenceSystem): JtsCoordinate =
    when (val order = CRS.getAxisOrder(ref)) {
        CRS.AxisOrder.EAST_NORTH -> JtsCoordinate(point.x, point.y)
        CRS.AxisOrder.NORTH_EAST -> JtsCoordinate(point.y, point.x)
        else -> throw CoordinateTransformationException(order, point.x, point.y, ref.name.code)
    }

fun boundingPolygonPointsByConvexHull(points: List<IPoint>, srid: Srid): List<Point> {
    val crs = crs(srid)
    val coordinates = points.map { p -> toJtsCoordinate(p, crs) }.toTypedArray()
    val geometryFactory = GeometryFactory(PrecisionModel(PrecisionModel.FLOATING), CRS.lookupEpsgCode(crs, false))
    val convexHull = ConvexHull(coordinates, geometryFactory).convexHull
    return convexHull.coordinates.map { c -> toGvtPoint(c, crs) }
}

fun bufferedPolygonForLineStringPoints(points: List<IPoint>, buffer: Double, srid: Srid): Polygon {
    val crs = crs(srid)
    val lineString = toJtsLineString(points)
    val buffered = lineString.buffer(buffer)
    return toJtsPolygon(buffered.coordinates.map { c -> toGvtPoint(c, crs) }.toList())
}

class CoordinateTransformationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    constructor(
        point: JtsPoint,
        sourceSrid: Srid,
        targetSrid: Srid,
        cause: Throwable? = null,
    ) : this("Could not transform coordinate: x=${point.x} y=${point.y} source=$sourceSrid target=$targetSrid", cause)

    constructor(
        order: CRS.AxisOrder,
        x: Double,
        y: Double,
        crs: String,
    ) : this("Cannot determine coordinate axis order x=$x y=$y crs=$crs order=$order")
}

sealed class Transformation {
    abstract val sourceSrid: Srid
    abstract val targetSrid: Srid
    protected val sourceCrs: CoordinateReferenceSystem by lazy { crs(sourceSrid) }
    protected val targetCrs: CoordinateReferenceSystem by lazy { crs(targetSrid) }

    fun transform(point: IPoint): Point =
        if (sourceSrid == targetSrid) point.toPoint()
        else toGvtPoint(transformJts(toJtsGeoPoint(point, sourceCrs)), targetCrs)

    fun transformJts(point: JtsPoint): JtsPoint =
        try {
            if (sourceSrid == targetSrid) point else transformJtsInternal(point)
        } catch (e: Exception) {
            throw CoordinateTransformationException(point, sourceSrid, targetSrid, e)
        }

    protected abstract fun transformJtsInternal(point: JtsPoint): JtsPoint
}

data class GeotoolsTransformation(override val sourceSrid: Srid, override val targetSrid: Srid) : Transformation() {
    private val math: MathTransform = CRS.findMathTransform(sourceCrs, targetCrs)

    override fun transformJtsInternal(point: JtsPoint): JtsPoint = JTS.transform(point, math) as JtsPoint
}

data class KKJToTM35FINTransformation(
    override val sourceSrid: Srid,
    private val kkjToTm35FinTriangulation: KkjTm35FinTriangulationNetwork,
) : Transformation() {
    override val targetSrid: Srid = LAYOUT_SRID
    private val kkjToYkj = GeotoolsTransformation(sourceSrid, KKJ3_YKJ_SRID)

    init {
        require(isKKJ(sourceSrid)) { "This transformation is only for KKJ coordinates: sourceSrid=$sourceSrid" }
        val expected = KKJ3_YKJ_SRID to ETRS89_TM35FIN_SRID
        val actual = kkjToTm35FinTriangulation.sourceSrid to kkjToTm35FinTriangulation.targetSrid
        require(expected == actual) {
            "${this::class.simpleName} built with wrong triangulation network: expected=$expected actual=$actual"
        }
    }

    override fun transformJtsInternal(point: JtsPoint): JtsPoint =
        point.let(kkjToYkj::transformJts).let(kkjToTm35FinTriangulation::transformJts)
}

data class TM35FINToKKJTransformation(
    override val targetSrid: Srid,
    private val tm35FinToYkjTriangulation: KkjTm35FinTriangulationNetwork,
) : Transformation() {
    override val sourceSrid: Srid = LAYOUT_SRID
    private val ykjToKkj = GeotoolsTransformation(KKJ3_YKJ_SRID, targetSrid)

    init {
        require(isKKJ(targetSrid)) { "This transformation is only for KKJ coordinates: targetSrid=$targetSrid" }
        val expected = ETRS89_TM35FIN_SRID to KKJ3_YKJ_SRID
        val actual = tm35FinToYkjTriangulation.sourceSrid to tm35FinToYkjTriangulation.targetSrid
        require(expected == actual) {
            "${this::class.simpleName} built with wrong triangulation network: expected=$expected actual=$actual"
        }
    }

    override fun transformJtsInternal(point: JtsPoint): JtsPoint =
        point.let(tm35FinToYkjTriangulation::transformJts).let(ykjToKkj::transformJts)
}

fun interface ToGkFinTransformation {
    fun transform(point: Point): GeometryPoint
}
