package fi.fta.geoviite.infra.geography

import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Rectangle
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import java.util.concurrent.ConcurrentHashMap
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.api.referencing.operation.MathTransform
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.springframework.beans.factory.annotation.Autowired

data class Transformation
private constructor(
    val sourceSrid: Srid,
    val targetSrid: Srid,
    val kkjToEtrsTriangulationNetwork: RTree<KkjTm35finTriangle, Rectangle>?,
    val etrsToKkjTriangulationNetwork: RTree<KkjTm35finTriangle, Rectangle>?,
) {
    private val sourceCrs: CoordinateReferenceSystem by lazy { crs(sourceSrid) }
    private val targetCrs: CoordinateReferenceSystem by lazy { crs(targetSrid) }
    private val math: MathTransform by lazy { CRS.findMathTransform(sourceCrs, targetCrs) }

    companion object {
        fun possiblyTriangulableTransform(
            sourceSrid: Srid,
            targetSrid: Srid,
            ykjEtrsTriangles: RTree<KkjTm35finTriangle, Rectangle>,
            etrsYkjTriangles: RTree<KkjTm35finTriangle, Rectangle>
        ) = Transformation(sourceSrid, targetSrid, ykjEtrsTriangles, etrsYkjTriangles)

        fun nonTriangulableTransform(sourceSrid: Srid, targetSrid: Srid) =
            Transformation(
                sourceSrid,
                targetSrid,
                kkjToEtrsTriangulationNetwork = null,
                etrsToKkjTriangulationNetwork = null)
    }

    init {
        require(
            (kkjToEtrsTriangulationNetwork != null && !kkjToEtrsTriangulationNetwork.isEmpty) ||
                !isKKJ(sourceSrid) ||
                targetSrid != LAYOUT_SRID) {
                "Trying to convert from KKJx ($sourceSrid) to $targetSrid without triangulation network"
            }
    }

    fun transform(point: IPoint): Point {
        try {
            // Intercept transforms from KKJx to ETRS
            return if (kkjToEtrsTriangulationNetwork != null &&
                !kkjToEtrsTriangulationNetwork.isEmpty &&
                isKKJ(sourceSrid) &&
                targetSrid == LAYOUT_SRID) {
                val ykjPoint = transformKkjToYkjAndNormalizeAxes(point)
                val triangle =
                    kkjToEtrsTriangulationNetwork
                        .search(Geometries.point(ykjPoint.x, ykjPoint.y))
                        .find { it.value().intersects(ykjPoint) }
                        ?.value()

                requireNotNull(triangle) {
                    "Point was not inside the triangulation network: point=$point ykjPoint=$ykjPoint"
                }
                transformPointInTriangle(ykjPoint, targetCrs, triangle)
            } else if (etrsToKkjTriangulationNetwork != null &&
                !etrsToKkjTriangulationNetwork.isEmpty &&
                sourceSrid == LAYOUT_SRID &&
                isKKJ(targetSrid)) {
                val jtsPoint = toJtsPoint(point, crs(sourceSrid))
                val triangle =
                    etrsToKkjTriangulationNetwork
                        .search(Geometries.point(point.x, point.y))
                        .find { it.value().intersects(jtsPoint) }
                        ?.value()

                requireNotNull(triangle) {
                    "Point was not inside the triangulation network: point=$point"
                }
                val ykjPoint = transformPointInTriangle(jtsPoint, YKJ_CRS, triangle)
                toGvtPoint(transformYkjToKkjAndNormalizeAxes(ykjPoint), targetCrs)
            } else {
                val jtsPoint = toJtsPoint(point, sourceCrs)
                val jtsPointTransformed =
                    JTS.transform(jtsPoint, math) as org.locationtech.jts.geom.Point
                toGvtPoint(jtsPointTransformed, targetCrs)
            }
        } catch (e: Exception) {
            throw CoordinateTransformationException(point, e)
        }
    }

    private fun transformKkjToYkjAndNormalizeAxes(point: IPoint): org.locationtech.jts.geom.Point {
        // Geotools is accurate enough for transformations between KKJx and YKJ, so use it for those
        val kkjToYkj = nonTriangulableTransform(sourceSrid, KKJ3_YKJ_SRID)
        return JTS.transform(toJtsPoint(point, sourceCrs), kkjToYkj.math)
            as org.locationtech.jts.geom.Point
    }

    private fun transformYkjToKkjAndNormalizeAxes(point: IPoint): org.locationtech.jts.geom.Point {
        // Geotools is accurate enough for transformations between KKJx and YKJ, so use it for those
        val ykjToKkj = nonTriangulableTransform(KKJ3_YKJ_SRID, targetSrid)
        return JTS.transform(toJtsPoint(point, YKJ_CRS), ykjToKkj.math)
            as org.locationtech.jts.geom.Point
    }
}

@GeoviiteService
class CoordinateTransformationService
@Autowired
constructor(private val kkjTm35FinTriangulationDao: KkjTm35finTriangulationDao) {
    private val transformations = ConcurrentHashMap<Pair<Srid, Srid>, Transformation>()

    fun getLayoutTransformation(sourceSrid: Srid) = getTransformation(sourceSrid, LAYOUT_SRID)

    fun getTransformation(sourceSrid: Srid, targetSrid: Srid): Transformation =
        transformations.computeIfAbsent(Pair(sourceSrid, targetSrid)) {
            Transformation.possiblyTriangulableTransform(
                sourceSrid,
                targetSrid,
                kkjTm35FinTriangulationDao.fetchTriangulationNetwork(
                    TriangulationDirection.KKJ_TO_TM35FIN),
                kkjTm35FinTriangulationDao.fetchTriangulationNetwork(
                    TriangulationDirection.TM35FIN_TO_KKJ),
            )
        }

    fun transformCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint) =
        getTransformation(sourceSrid, targetSrid).transform(point)
}

fun isKKJ(srid: Srid) =
    listOf(KKJ0_SRID, KKJ1_SRID, KKJ2_SRID, KKJ3_YKJ_SRID, KKJ4_SRID, KKJ5_SRID).contains(srid)
