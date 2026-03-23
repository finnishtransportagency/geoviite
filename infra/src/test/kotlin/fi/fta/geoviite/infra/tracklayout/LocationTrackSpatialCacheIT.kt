package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationInMain
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackSpatialCacheIT
@Autowired
constructor(
    private val locationTrackService: LocationTrackService,
    private val spatialCache: LocationTrackSpatialCache,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `get() on publication transition filters correctly by publication set`() {
        val trackNumberId = mainDraftContext.save(trackNumber()).id

        val geometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val draftOnly = mainDraftContext.save(locationTrack(trackNumberId), geometry)
        val mainA = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val draftEditA = mainDraftContext.copyFrom(mainA)
        val mainB = mainOfficialContext.save(locationTrack(trackNumberId), geometry)
        val draftEditB = mainDraftContext.copyFrom(mainB)

        assertEquals(
            setOf(draftEditA, mainB),
            spatialCache
                .get(PublicationInMain, listOf(draftEditA))
                .getWithinBoundingBox(geometry.boundingBox!!)
                .map { (track) -> track.version!! }
                .toSet(),
        )
        assertEquals(
            setOf(draftOnly, mainA, draftEditB),
            spatialCache
                .get(PublicationInMain, listOf(draftOnly, draftEditB))
                .getWithinBoundingBox(geometry.boundingBox!!)
                .map { (track) -> track.version!! }
                .toSet(),
        )
    }

    @Test
    fun `get() updates cache as tracks are inserted, updated, and deleted`() {
        val trackNumberId = mainDraftContext.save(trackNumber()).id
        val context = LayoutBranch.main.draft

        val geometryA = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        val geometryB = trackGeometryOfSegments(segment(Point(20.0, 0.0), Point(30.0, 0.0)))

        assertEquals(0, spatialCache.get(context).size)

        val trackA = mainDraftContext.save(locationTrack(trackNumberId), geometryA)
        assertEquals(setOf(trackA.id), spatialCache.get(context).items.keys)

        val trackB = mainDraftContext.save(locationTrack(trackNumberId), geometryB)
        assertEquals(setOf(trackA.id, trackB.id), spatialCache.get(context).items.keys)

        val fetchedA = mainDraftContext.fetch(trackA.id)!!
        locationTrackService.saveDraft(LayoutBranch.main, fetchedA, geometryB)
        assertEquals(setOf(trackA.id, trackB.id), spatialCache.get(context).items.keys)

        locationTrackService.deleteDraft(LayoutBranch.main, trackB.id)
        assertEquals(setOf(trackA.id), spatialCache.get(context).items.keys)
    }
}
