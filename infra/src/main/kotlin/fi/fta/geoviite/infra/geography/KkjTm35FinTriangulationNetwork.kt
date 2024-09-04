package fi.fta.geoviite.infra.geography

import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Rectangle
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.Point
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon

data class KkjTm35finTriangle(
    val corner1: Point,
    val corner2: Point,
    val corner3: Point,
    val a1: Double,
    val a2: Double,
    val deltaE: Double,
    val b1: Double,
    val b2: Double,
    val deltaN: Double,
    val sourceSrid: Srid,
) {

    val polygon by lazy { toJtsGeoPolygon(listOf(corner1, corner2, corner3, corner1), sourceSrid) }

    fun intersects(point: JtsPoint): Boolean = polygon.intersects(point)

    fun intersects(poly: JtsPolygon): Boolean = polygon.intersects(poly)
}

data class KkjTm35FinTriangulationNetwork(
    private val network: RTree<KkjTm35finTriangle, Rectangle>,
    val sourceSrid: Srid,
    val targetSrid: Srid,
) {
    constructor(
        triangles: List<KkjTm35finTriangle>,
        sourceSrid: Srid,
        targetSrid: Srid,
    ) : this(triangulationNetworkToRTree(triangles), sourceSrid, targetSrid)

    init {
        require(!network.isEmpty) { "Trying to build empty KKJx ($sourceSrid) to $targetSrid triangulation network" }
    }

    fun transformJts(point: JtsPoint): JtsPoint = transformPointInTriangle(point, findTriangle(point))

    fun findTriangle(jtsPoint: JtsPoint): KkjTm35finTriangle =
        requireNotNull(
            network
                .search(Geometries.point(jtsPoint.x, jtsPoint.y))
                .find { triangle -> triangle.value().intersects(jtsPoint) }
                ?.value()
        ) {
            "Point was not inside the triangulation network: jtsPoint=$jtsPoint"
        }

    private fun transformPointInTriangle(point: JtsPoint, triangle: KkjTm35finTriangle): JtsPoint {
        val x = (triangle.a2 * point.y) + (triangle.a1 * point.x) + triangle.deltaE
        val y = (triangle.b2 * point.y) + (triangle.b1 * point.x) + triangle.deltaN
        return toJtsGeoPoint(Coordinate(x, y))
    }
}

private fun triangulationNetworkToRTree(triangulationNetwork: List<KkjTm35finTriangle>) =
    triangulationNetwork.fold(RTree.star().create<KkjTm35finTriangle, Rectangle>()) { tree, triangle ->
        val envelope = triangle.polygon.envelopeInternal
        tree.add(triangle, Geometries.rectangle(envelope.minX, envelope.minY, envelope.maxX, envelope.maxY))
    }
