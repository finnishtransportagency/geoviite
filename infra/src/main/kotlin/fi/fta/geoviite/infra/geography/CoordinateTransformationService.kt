package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.TriangulationDirection.KKJ_TO_TM35FIN
import fi.fta.geoviite.infra.geography.TriangulationDirection.TM35FIN_TO_KKJ
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class CoordinateTransformationService
@Autowired
constructor(private val kkjTm35FinTriangulationDao: KkjTm35finTriangulationDao) {
    private val transformations = ConcurrentHashMap<Pair<Srid, Srid>, Transformation>()

    fun getLayoutTransformation(sourceSrid: Srid) = getTransformation(sourceSrid, LAYOUT_SRID)

    fun getTransformation(sourceSrid: Srid, targetSrid: Srid): Transformation =
        transformations.computeIfAbsent(Pair(sourceSrid, targetSrid)) {
            when {
                isKKJ(sourceSrid) -> {
                    require(targetSrid == LAYOUT_SRID) {
                        "KKJ ($sourceSrid) can only be transformed to layout coordinates ($LAYOUT_SRID), not $targetSrid"
                    }
                    val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(KKJ_TO_TM35FIN)
                    KKJToTM35FINTransformation(sourceSrid, network)
                }
                isKKJ(targetSrid) -> {
                    require(sourceSrid == LAYOUT_SRID) {
                        "Only layout coordinates ($LAYOUT_SRID) can be transformed to KKJ ($sourceSrid), not $targetSrid"
                    }
                    val network = kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TM35FIN_TO_KKJ)
                    TM35FINToKKJTransformation(targetSrid, network)
                }
                else -> geotoolsTransformation(sourceSrid, targetSrid)
            }
        }

    fun transformCoordinate(sourceSrid: Srid, targetSrid: Srid, point: IPoint) =
        getTransformation(sourceSrid, targetSrid).transform(point)

    fun getTransformationToGkFin(sourceSrid: Srid): ToGkFinTransformation {
        val toLayout = getTransformation(sourceSrid, LAYOUT_SRID)
        return ToGkFinTransformation { point: Point ->
            val layoutCoord = toLayout.transform(point)
            val etrs89Coord = transformNonKKJCoordinate(LAYOUT_SRID, ETRS89_SRID, layoutCoord)
            val gkSrid = getFinnishGKCoordinateProjectionByLongitude(etrs89Coord.x)

            if (gkSrid == sourceSrid) {
                GeometryPoint(point, sourceSrid)
            } else {
                GeometryPoint(transformNonKKJCoordinate(LAYOUT_SRID, gkSrid, layoutCoord), gkSrid)
            }
        }
    }
}
