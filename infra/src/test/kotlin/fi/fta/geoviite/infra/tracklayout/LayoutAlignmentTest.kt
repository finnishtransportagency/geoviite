package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LayoutAlignmentTest {
    @Test
    fun mergesSegmentsCorrectlyTest() {
        val segmentsAndMetadatas = listOf(
            segmentGeometryAndMetadata(fileName = FileName("test"), source = GeometrySource.PLAN, planId = IntId(1)),
            segmentGeometryAndMetadata(fileName = FileName("test"), source = GeometrySource.PLAN, planId = IntId(1)),
            segmentGeometryAndMetadata(fileName = FileName("test"), source = GeometrySource.PLAN, planId = IntId(2)),
            segmentGeometryAndMetadata(fileName = FileName("test"), source = GeometrySource.IMPORTED),
            segmentGeometryAndMetadata(fileName = FileName("test2"), source = GeometrySource.IMPORTED),
            segmentGeometryAndMetadata(),
            segmentGeometryAndMetadata(),
            segmentGeometryAndMetadata(fileName = FileName("test"), source = GeometrySource.PLAN, planId = IntId(1)),
        )

        val actual = foldSegmentsByPlan(segmentsAndMetadatas)
        assertEquals(actual.size, 6)
        assertEquals(actual[0].planId, IntId(1))
        assertEquals(actual[0].source, GeometrySource.PLAN)
        assertEquals(actual[1].planId, IntId(2))
        assertEquals(actual[1].source, GeometrySource.PLAN)
        assertEquals(actual[2].fileName, FileName("test"))
        assertEquals(actual[2].source, GeometrySource.IMPORTED)
        assertEquals(actual[3].fileName, FileName("test2"))
        assertEquals(actual[4].source, GeometrySource.GENERATED)
        assertEquals(actual[5].planId, IntId(1))
        assertEquals(actual[5].source, GeometrySource.PLAN)
    }

    private fun segmentGeometryAndMetadata(
        planId: IntId<GeometryPlan>? = null,
        fileName: FileName? = null,
        source: GeometrySource = GeometrySource.GENERATED,
        startPoint: IPoint? = null,
        endPoint: IPoint? = null,
        segmentId: IndexedId<LayoutSegment> = IndexedId(1, 1)
    ) =
        SegmentGeometryAndMetadata(planId, fileName, startPoint, endPoint, source, segmentId)
}
