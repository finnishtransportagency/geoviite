package fi.fta.geoviite.api

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.linkedTrackGeometry
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.stereotype.Service

fun assertContainsErrorMessage(expectedErrorMessage: String, errorMessages: Any?, contextMessage: String = "") {
    assert((errorMessages as List<*>).contains(expectedErrorMessage)) {
        "$expectedErrorMessage is not in the list of errors ($errorMessages) $contextMessage"
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
class ExtApiTestDataServiceV1 : DBTestBase() {

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
        val (trackNumberId, trackNumberOid) =
            layoutContext.saveWithOid(trackNumber(testDBService.getUnusedTrackNumber()))
        val referenceLineId = layoutContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id
        val trackIds = joints.map { (start, end) ->
            val geom = linkedTrackGeometry(savedSwitch, start.number, end.number, structure)
            layoutContext.save(locationTrack(trackNumberId), geom).id
        }

        return SwitchAndTrackIds(
            switch = IdAndOid(switchId, layoutContext.generateOid(switchId)),
            tracks = trackIds.map { id -> IdAndOid(id, layoutContext.generateOid(id)) },
            trackNumber = IdAndOid(trackNumberId, trackNumberOid),
            referenceLineId = referenceLineId,
        )
    }

    fun publishInMain(switchAndTrackIds: List<SwitchAndTrackIds>): Publication =
        testDBService.publish(
            trackNumbers = switchAndTrackIds.map { it.trackNumber.id },
            referenceLines = switchAndTrackIds.map { it.referenceLineId },
            locationTracks = switchAndTrackIds.flatMap { it.tracks.map { track -> track.id } },
            switches = switchAndTrackIds.map { it.switch.id },
        )
}
