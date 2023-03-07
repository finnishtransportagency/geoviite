package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.ui.testdata.createGeometryKmPost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingIT @Autowired constructor(
    private val geocodingService: GeocodingService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geometryDao: GeometryDao,
) : ITTestBase() {

    @Test
    fun happyPath() {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val plan = minimalPlan()
            .copy(
                units = GeometryUnits(
                    coordinateSystemSrid = LAYOUT_SRID,
                    coordinateSystemName = null,
                    verticalCoordinateSystem = null,
                    directionUnit = AngularUnit.GRADS,
                    linearUnit = LinearUnit.METER,
                ),
                alignments = listOf(
                    geometryAlignment(
                        trackNumberId,
                        listOf(
                            // reference line goes straight up
                            line(Point(450000.0, 7000000.0), Point(450000.0, 7000020.0)),
                        ),
                        featureTypeCode = FeatureTypeCode("111")
                    ),
                    geometryAlignment(
                        trackNumberId,
                        listOf(
                            // location track goes somewhere that doesn't matter (but actually just goes diagonally)
                            line(Point(450000.0, 7000000.0), Point(450020.0, 7000020.0)),
                        ),
                        featureTypeCode = FeatureTypeCode("121")
                    )
                ),
                kmPosts = listOf(
                    createGeometryKmPost(trackNumberId, null, "0140", staInternal = BigDecimal.valueOf(-100L)),
                    createGeometryKmPost(trackNumberId, Point(450000.0, 7000010.0), "0141")
                )
            )
        val planVersion = geometryDao.insertPlan(
            plan, InfraModelFile(plan.fileName, "<plan />"),
            listOf(
                Point(450000.0, 7000000.0),
                Point(460000.0, 7000000.0),
                Point(460000.0, 7100000.0),
                Point(450000.0, 7100000.0),
                Point(450000.0, 7000000.0),
            )
        )
        val context = geocodingService.getGeocodingContext(trackNumberId, planVersion)
        assertNotNull(context)
        context!!
        assertEquals(
            // alignment started 100 meters in per the preceding km post, and we're 5 meters up of the start
            TrackMeter(KmNumber("0140"), BigDecimal.valueOf(105)),
            context.getAddress(Point(450000.0, 7000005.0), 0)!!.first
        )
        assertEquals(
            TrackMeter(KmNumber("0141"), BigDecimal.valueOf(5)),
            context.getAddress(Point(450000.0, 7000015.0), 0)!!.first
        )
    }
}
