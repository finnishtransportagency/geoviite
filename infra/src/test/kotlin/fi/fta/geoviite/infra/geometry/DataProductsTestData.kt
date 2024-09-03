package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.Point

fun createAlignment(vararg elementTypes: GeometryElementType) =
    geometryAlignment(id = IntId(1), elements = createElements(1, *elementTypes))

fun createElements(parentId: Int, vararg types: GeometryElementType) =
    types.mapIndexed { index, t ->
        when (t) {
            GeometryElementType.LINE ->
                minimalLine(
                    id = IndexedId(parentId, index),
                    start = Point(index.toDouble(), index.toDouble()),
                    end = Point((index + 1).toDouble(), (index + 1).toDouble()),
                )

            GeometryElementType.CURVE ->
                minimalCurve(
                    id = IndexedId(parentId, index),
                    start = Point(index.toDouble(), index.toDouble()),
                    end = Point((index + 1).toDouble(), (index + 1).toDouble()),
                )

            GeometryElementType.CLOTHOID ->
                minimalClothoid(
                    id = IndexedId(parentId, index),
                    start = Point(index.toDouble(), index.toDouble()),
                    end = Point((index + 1).toDouble(), (index + 1).toDouble()),
                )

            else -> throw IllegalStateException("element $t not supported by this test method")
        }
    }
