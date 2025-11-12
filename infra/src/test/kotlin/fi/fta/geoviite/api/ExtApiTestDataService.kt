package fi.fta.geoviite.api

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.linkedTrackGeometry
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
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
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val switchDao: LayoutSwitchDao,
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
        startAddress: TrackMeter = TrackMeter.ZERO,
    ): Pair<IntId<LayoutTrackNumber>, IntId<ReferenceLine>> {
        val trackNumberId = layoutContext.createLayoutTrackNumber(TrackNumber(trackNumberName)).id

        val referenceLine =
            layoutContext.saveReferenceLine(
                referenceLineAndAlignment(
                    trackNumberId = trackNumberId,
                    segments = segments,
                    startAddress = startAddress,
                )
            )

        return trackNumberId to referenceLine.id
    }

    fun insertTrackNumberAndReferenceLineWithOid(
        layoutContext: TestLayoutContext,
        trackNumberName: String = testDBService.getUnusedTrackNumber().value,
        segments: List<LayoutSegment>,
        startAddress: TrackMeter = TrackMeter.ZERO,
    ): Triple<IntId<LayoutTrackNumber>, IntId<ReferenceLine>, Oid<LayoutTrackNumber>> {
        val (trackNumberId, referenceLineId) =
            insertTrackNumberAndReferenceLine(layoutContext, trackNumberName, segments, startAddress)
        val oid =
            someOid<LayoutTrackNumber>().also { oid ->
                trackNumberDao.insertExternalId(trackNumberId, layoutContext.context.branch, oid)
            }

        return Triple(trackNumberId, referenceLineId, oid)
    }

    data class IdAndOid<T>(val id: IntId<T>, val oid: Oid<T>)

    data class SwitchAndTrackIds(
        val switch: IdAndOid<LayoutSwitch>,
        val tracks: List<IdAndOid<LocationTrack>>,
        val trackNumber: IdAndOid<LayoutTrackNumber>,
        val referenceLineId: IntId<ReferenceLine>,
    )

    fun insertSwitchAndTracks(
        layoutContext: TestLayoutContext,
        joints: List<Pair<LayoutSwitchJoint, LayoutSwitchJoint>> =
            listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
        structure: SwitchStructure = switchStructureYV60_300_1_9(),
    ): SwitchAndTrackIds {
        val allJoints = joints.flatMap { listOf(it.first, it.second) }.distinctBy { it.number }
        val switchId = layoutContext.save(switch(structure.id, allJoints)).id
        val savedSwitch = layoutContext.fetch(switchId)!!

        val segment =
            segment(
                Point(allJoints.minOf { it.location.x }, allJoints.minOf { it.location.y }),
                Point(allJoints.maxOf { it.location.x }, allJoints.maxOf { it.location.y }),
            )
        val (trackNumberId, referenceLineId) =
            insertTrackNumberAndReferenceLine(layoutContext, segments = listOf(segment))
        val trackIds =
            joints.map { (start, end) ->
                val geom = linkedTrackGeometry(savedSwitch, start.number, end.number, structure)
                layoutContext.save(locationTrack(trackNumberId), geom).id
            }

        return SwitchAndTrackIds(
            switch = IdAndOid(switchId, layoutContext.generateOid(switchId)),
            tracks = trackIds.map { id -> IdAndOid(id, layoutContext.generateOid(id)) },
            trackNumber = IdAndOid(trackNumberId, layoutContext.generateOid(trackNumberId)),
            referenceLineId = referenceLineId,
        )
    }

    fun publishInMain(switchAndTrackIds: List<SwitchAndTrackIds>): Publication =
        publishInMain(
            trackNumbers = switchAndTrackIds.map { it.trackNumber.id },
            referenceLines = switchAndTrackIds.map { it.referenceLineId },
            locationTracks = switchAndTrackIds.flatMap { it.tracks.map { track -> track.id } },
            switches = switchAndTrackIds.map { it.switch.id },
        )

    fun publishInMain(
        trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
        referenceLines: List<IntId<ReferenceLine>> = emptyList(),
        locationTracks: List<IntId<LocationTrack>> = emptyList(),
        switches: List<IntId<LayoutSwitch>> = emptyList(),
        kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    ): Publication =
        publishInBranch(
            LayoutBranch.main,
            trackNumbers = trackNumbers,
            referenceLines = referenceLines,
            locationTracks = locationTracks,
            switches = switches,
            kmPosts = kmPosts,
        )

    fun publishInBranch(
        branch: LayoutBranch,
        trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
        referenceLines: List<IntId<ReferenceLine>> = emptyList(),
        locationTracks: List<IntId<LocationTrack>> = emptyList(),
        switches: List<IntId<LayoutSwitch>> = emptyList(),
        kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    ): Publication {
        return publicationTestSupportService
            .publish(
                branch,
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
