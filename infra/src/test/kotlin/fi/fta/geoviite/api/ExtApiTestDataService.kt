package fi.fta.geoviite.api

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

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
    propertyNames.forEach { name -> assertNull(properties[name]) }
}

@Service
class ExtApiTestDataServiceV1
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val publicationTestSupportService: PublicationTestSupportService,
    private val publicationDao: PublicationDao,
) : DBTestBase() {

    fun insertGeocodableTrack(
        layoutContext: TestLayoutContext = mainOfficialContext,
        trackNumberId: IntId<LayoutTrackNumber> = mainOfficialContext.createLayoutTrackNumber().id,
        referenceLineId: IntId<ReferenceLine>? = null,
        locationTrackName: String = "Test track-${UUID.randomUUID()}",
        locationTrackType: LocationTrackType = LocationTrackType.MAIN,
        segments: List<LayoutSegment> = listOf(segment(Point(-10.0, 0.0), Point(10.0, 0.0))),
        state: LocationTrackState = LocationTrackState.IN_USE,
        owner: IntId<LocationTrackOwner> = IntId(1),
    ): GeocodableTrack {
        val usedReferenceLineId =
            referenceLineId
                ?: layoutContext
                    .saveReferenceLine(referenceLineAndAlignment(trackNumberId = trackNumberId, segments = segments))
                    .id

        val locationTrackId =
            layoutContext
                .saveLocationTrack(
                    locationTrackAndGeometry(
                        trackNumberId = trackNumberId,
                        name = locationTrackName,
                        type = locationTrackType,
                        segments = segments,
                        state = state,
                        ownerId = owner,
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

    fun insertTrackNumberAndReferenceLine(
        layoutContext: TestLayoutContext,
        trackNumberName: String = testDBService.getUnusedTrackNumber().value,
        segments: List<LayoutSegment>,
    ): Pair<IntId<LayoutTrackNumber>, IntId<ReferenceLine>> {
        val trackNumberId = layoutContext.createLayoutTrackNumber(TrackNumber(trackNumberName)).id

        val referenceLine =
            layoutContext.saveReferenceLine(
                referenceLineAndAlignment(trackNumberId = trackNumberId, segments = segments)
            )

        return trackNumberId to referenceLine.id
    }

    fun insertTrackNumberAndReferenceLineWithOid(
        layoutContext: TestLayoutContext,
        trackNumberName: String = testDBService.getUnusedTrackNumber().value,
        segments: List<LayoutSegment>,
    ): Triple<IntId<LayoutTrackNumber>, IntId<ReferenceLine>, Oid<LayoutTrackNumber>> {
        val (trackNumberId, referenceLineId) =
            insertTrackNumberAndReferenceLine(layoutContext, trackNumberName, segments)
        val oid =
            someOid<LayoutTrackNumber>().also { oid ->
                layoutTrackNumberDao.insertExternalId(trackNumberId, layoutContext.context.branch, oid)
            }

        return Triple(trackNumberId, referenceLineId, oid)
    }

    fun publishInMain(
        trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
        referenceLines: List<IntId<ReferenceLine>> = emptyList(),
        locationTracks: List<IntId<LocationTrack>> = emptyList(),
        switches: List<IntId<LayoutSwitch>> = emptyList(),
        kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    ): Publication {
        return publicationTestSupportService
            .publish(
                LayoutBranch.main,
                publicationRequestIds(
                    trackNumbers = trackNumbers,
                    referenceLines = referenceLines,
                    locationTracks = locationTracks,
                    switches = switches,
                    kmPosts = kmPosts,
                ),
            )
            .let { summary -> publicationDao.getPublication(requireNotNull(summary.publicationId)) }
    }
}
