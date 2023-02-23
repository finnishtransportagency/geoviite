package fi.fta.geoviite.infra.geography

import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Rectangle
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.opengis.referencing.operation.MathTransform
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class Transformation private constructor(
    val sourceSrid: Srid,
    val targetSrid: Srid,
    val triangulationNetwork: RTree<KKJtoETRSTriangle, Rectangle>?
) {
    private val sourceCrs: CoordinateReferenceSystem by lazy { crs(sourceSrid) }
    private val targetCrs: CoordinateReferenceSystem by lazy { crs(targetSrid) }
    private val math: MathTransform by lazy { CRS.findMathTransform(sourceCrs, targetCrs) }

    companion object {
        fun possiblyKKJToETRSTransform(sourceSrid: Srid, targetSrid: Srid, triangleTree: RTree<KKJtoETRSTriangle, Rectangle>) =
            Transformation(sourceSrid, targetSrid, triangleTree)

        fun nonKKJToETRSTransform(sourceSrid: Srid, targetSrid: Srid) =
            Transformation(sourceSrid, targetSrid, triangulationNetwork = null)
    }

    init {
        require((triangulationNetwork != null && !triangulationNetwork.isEmpty) || !isKKJ(sourceSrid) || targetSrid != LAYOUT_SRID) {
            "Trying to convert from KKJx (${sourceSrid}) to ${targetSrid} without triangulation network"
        }
    }

    fun transform(point: IPoint): Point {
        try {
            // Intercept transforms from KKJx to ETRS
            return if (triangulationNetwork != null && !triangulationNetwork.isEmpty && isKKJ(sourceSrid) && targetSrid == LAYOUT_SRID) {
                val ykjPoint = transformKkjToYkjAndNormalizeAxes(point)
                val triangle = triangulationNetwork
                    .search(Geometries.point(ykjPoint.x, ykjPoint.y))
                    .find { it.value().intersects(ykjPoint) }
                    ?.value()

                requireNotNull(triangle) {
                    "Point was not inside the triangulation network: point=$point ykjPoint=$ykjPoint"
                }
                transformYkjPointToEtrs(ykjPoint, triangle)
            } else {
                val jtsPoint = toJtsPoint(point, sourceCrs)
                val jtsPointTransformed = JTS.transform(jtsPoint, math) as org.locationtech.jts.geom.Point
                toGvtPoint(jtsPointTransformed, targetCrs)
            }
        } catch (e: Exception) {
            throw CoordinateTransformationException(point, e)
        }
    }

    private fun transformKkjToYkjAndNormalizeAxes(point: IPoint): org.locationtech.jts.geom.Point {
        // Geotools is accurate enough for transformations between KKJx and YKJ, so use it for those
        val kkjToYkj = nonKKJToETRSTransform(sourceSrid, KKJ3_YKJ)
        return JTS.transform(toJtsPoint(point, sourceCrs), kkjToYkj.math) as org.locationtech.jts.geom.Point
    }
}

@Service
class CoordinateTransformationService @Autowired constructor(
    private val kkJtoETRSTriangulationDao: KKJtoETRSTriangulationDao
) {
    private val transformations = mutableMapOf<Pair<Srid, Srid>, Transformation>()

    fun getLayoutTransformation(sourceSrid: Srid) = getTransformation(sourceSrid, LAYOUT_SRID)

    fun getTransformation(sourceSrid: Srid, targetSrid: Srid): Transformation =
        transformations.getOrPut(Pair(sourceSrid, targetSrid)) {
            Transformation.possiblyKKJToETRSTransform(
                sourceSrid,
                targetSrid,
                kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
            )
        }

    fun transformCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint) =
        getTransformation(sourceSrid, targetSrid).transform(point)
}

fun isKKJ(srid: Srid) = listOf(KKJ0, KKJ1, KKJ2, KKJ3_YKJ, KKJ4, KKJ5).contains(srid)
