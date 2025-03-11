package fi.fta.geoviite.api

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import java.util.*
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class GeocodableTrack(
    val layoutContext: LayoutContext,
    val trackNumber: LayoutTrackNumber,
    val referenceLine: ReferenceLine,
    val locationTrack: LocationTrack,
)

fun assertContainsErrorMessage(expectedErrorMessage: String, errorMessages: Any?, contextMessage: String = "") {
    assert((errorMessages as List<*>).contains(expectedErrorMessage)) {
        "$expectedErrorMessage is not in the list of errors $contextMessage"
    }
}

fun assertNullSimpleProperties(properties: Map<String, Any>) {
    assertNullProperties(properties, "x", "y", "valimatka")
}

fun assertNullDetailedProperties(properties: Map<String, Any>) {
    assertNullProperties(
        properties,
        "ratanumero",
        "sijaintiraide",
        "sijaintiraide_kuvaus",
        "sijaintiraide_tyyppi",
        "ratakilometri",
        "ratametri",
        "ratemetri_desimaalit",
    )
}

private fun assertNullProperties(properties: Map<String, Any>, vararg propertyNames: String) {
    propertyNames.forEach { name -> assertEquals(null, properties[name]) }
}

@Service
class ExtApiTestDataServiceV1
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<LayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        referenceLineId: IntId<ReferenceLine>? = null,
        locationTrackName: String = "Test track-${UUID.randomUUID()}",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
        state: LocationTrackState = LocationTrackState.IN_USE,
    ): GeocodableTrack {
        val usedReferenceLineId =
            referenceLineId
                ?: layoutContext
                    .insert(referenceLineAndAlignment(trackNumberId = trackNumberId, segments = segments))
                    .id

        val locationTrackId =
            layoutContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumberId = trackNumberId,
                        name = locationTrackName,
                        type = locationTrackType,
                        segments = segments,
                        state = state,
                    )
                )
                .id

        return GeocodableTrack(
            layoutContext = layoutContext.context,
            trackNumber = trackNumberDao.get(layoutContext.context, trackNumberId)!!,
            referenceLine = referenceLineDao.get(layoutContext.context, usedReferenceLineId)!!,
            locationTrack = locationTrackDao.get(layoutContext.context, locationTrackId)!!,
        )
    }
}
