package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryUnits
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.minimalPlan
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.geocodingContextCacheKey
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.referenceLineAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.ui.testdata.createGeometryKmPost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeocodingServiceIT
@Autowired
constructor(private val geocodingService: GeocodingService, private val geometryDao: GeometryDao) : DBTestBase() {

    @Test
    fun `geocoding context can be generated from geometry plan`() {
        val plan =
            minimalPlan()
                .copy(
                    units =
                        GeometryUnits(
                            coordinateSystemSrid = LAYOUT_SRID,
                            coordinateSystemName = null,
                            verticalCoordinateSystem = null,
                            directionUnit = AngularUnit.GRADS,
                            linearUnit = LinearUnit.METER,
                        ),
                    alignments =
                        listOf(
                            geometryAlignment(
                                listOf(
                                    // reference line goes straight up
                                    line(Point(450000.0, 7000000.0), Point(450000.0, 7000020.0))
                                ),
                                featureTypeCode = FeatureTypeCode("111"),
                            ),
                            geometryAlignment(
                                listOf(
                                    // location track goes somewhere that doesn't matter (but
                                    // actually just goes diagonally)
                                    line(Point(450000.0, 7000000.0), Point(450020.0, 7000020.0))
                                ),
                                featureTypeCode = FeatureTypeCode("121"),
                            ),
                        ),
                    kmPosts =
                        listOf(
                            createGeometryKmPost(null, "0140", staInternal = BigDecimal.valueOf(-100L)),
                            createGeometryKmPost(Point(450000.0, 7000010.0), "0141"),
                        ),
                )
        val planVersion =
            geometryDao.insertPlan(
                plan,
                InfraModelFile(plan.fileName, "<plan />"),
                Polygon(
                    Point(450000.0, 7000000.0),
                    Point(460000.0, 7000000.0),
                    Point(460000.0, 7100000.0),
                    Point(450000.0, 7100000.0),
                    Point(450000.0, 7000000.0),
                ),
            )
        val context = geocodingService.getGeocodingContext(TrackNumber("foo"), planVersion)
        assertNotNull(context)
        context!!
        assertEquals(
            // alignment started 100 meters in per the preceding km post, and we're 5 meters up of
            // the start
            TrackMeter(KmNumber("0140"), BigDecimal.valueOf(105)),
            context.getAddress(Point(450000.0, 7000005.0), 0)!!.first,
        )
        assertEquals(
            TrackMeter(KmNumber("0141"), BigDecimal.valueOf(5)),
            context.getAddress(Point(450000.0, 7000015.0), 0)!!.first,
        )
    }

    // A deleted track number is likely borked for geocoding and should never be used.
    // The issues are (at least) that:
    // - DELETED track numbers are not validated in publication to be intact
    // - There is no way to differentiate between km-posts that are deleted because the track number was deleted and
    //   ones that were already deleted before that
    @Test
    fun `No geocoding contexts are returned for deleted TrackNumbers`() {
        val tnV1 = mainOfficialContext.createLayoutTrackNumber()
        val tnId = tnV1.id
        val rlV1 =
            mainOfficialContext.saveReferenceLine(
                referenceLineAndGeometry(tnId, segment(Point(0.0, 0.0), Point(10.0, 0.0)))
            )
        val kmpV1 = mainOfficialContext.save(kmPost(tnId, KmNumber(1)))
        val initKey = geocodingContextCacheKey(tnId, tnV1, rlV1, kmpV1)
        assertNotNull(geocodingService.getGeocodingContext(initKey))
        val tnV2 = testDBService.update(tnV1) { tn -> tn.copy(state = LayoutState.DELETED) }
        val deleteKey = geocodingContextCacheKey(tnId, tnV2, rlV1, kmpV1)
        assertNull(geocodingService.getGeocodingContext(deleteKey))
        assertNotNull(geocodingService.getGeocodingContext(initKey))
    }
}
