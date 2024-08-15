package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

data class GeocodableTrack(
    val layoutContext: LayoutContext,
    val trackNumber: TrackLayoutTrackNumber,
    val referenceLine: ReferenceLine,
    val locationTrack: LocationTrack,
)

@Service
class FrameConverterTestDataServiceV1 @Autowired constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<TrackLayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        referenceLineId: IntId<ReferenceLine>? = null,
        locationTrackName: String = "Test track-${UUID.randomUUID()}",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
    ): GeocodableTrack {
        val usedReferenceLineId = referenceLineId ?: layoutContext.insert(
            referenceLineAndAlignment(
                trackNumberId = trackNumberId,
                segments = segments,
            )
        ).id

        val locationTrackId = layoutContext.insert(
            locationTrackAndAlignment(
                trackNumberId = trackNumberId,
                name = locationTrackName,
                type = locationTrackType,
                segments = segments,
            )
        ).id

        return GeocodableTrack(
            layoutContext = layoutContext.context,
            trackNumber = trackNumberDao.get(layoutContext.context, trackNumberId)!!,
            referenceLine = referenceLineDao.get(layoutContext.context, usedReferenceLineId)!!,
            locationTrack = locationTrackDao.get(layoutContext.context, locationTrackId)!!,
        )
    }
}
