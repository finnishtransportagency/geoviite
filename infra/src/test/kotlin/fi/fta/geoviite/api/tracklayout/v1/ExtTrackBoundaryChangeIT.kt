package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.CREATE_NEW
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.REPLACE_DUPLICATE
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.REPLACE_DUPLICATE_PARTIAL
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.TRANSFER_GEOMETRY
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitRequest
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.OVERWRITE
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.TRANSFER
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.split.targetRequest
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveDirection
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveService
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTrackBoundaryChangeIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val splitService: SplitService,
    private var trackBoundaryMoveService: TrackBoundaryMoveService,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `LocationTrack split shows up a boundary change`() {
        val tn = testDBService.getUnusedTrackNumber()
        val (tnId, tnOid) = mainDraftContext.saveWithOid(trackNumber(tn))
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(500.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        // Switches with main (split-point) joints at 200 and 300
        val structureId = switchStructureYV60_300_1_9().id
        val switch1Joints = listOf(switchJoint(1, Point(200.0, 0.0)), switchJoint(2, Point(210.0, 10.0)))
        val switch1Id = mainDraftContext.save(switch(structureId, switch1Joints)).id
        val switch2Joints = listOf(switchJoint(1, Point(300.0, 0.0)), switchJoint(2, Point(310.0, 10.0)))
        val switch2Id = mainDraftContext.save(switch(structureId, switch2Joints)).id

        // Source track geometry from 100 to 400, having the split-points at 200 and 300
        val sourceTrackGeom =
            trackGeometry(
                edge(
                    segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
                    endOuterSwitch = switchLinkYV(switch1Id, 1),
                ),
                edge(
                    segments = listOf(segment(Point(200.0, 0.0), Point(210.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch1Id, 1),
                    endInnerSwitch = switchLinkYV(switch1Id, 2),
                ),
                edge(
                    segments = listOf(segment(Point(210.0, 0.0), Point(300.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch1Id, 2),
                    endOuterSwitch = switchLinkYV(switch2Id, 1),
                ),
                edge(
                    segments = listOf(segment(Point(300.0, 0.0), Point(310.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch2Id, 1),
                    endInnerSwitch = switchLinkYV(switch2Id, 2),
                ),
                edge(
                    segments = listOf(segment(Point(310.0, 0.0), Point(400.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch2Id, 2),
                ),
            )
        val (sourceId, sourceOid) =
            mainDraftContext.saveWithOid(locationTrack(tnId, name = "SourceTrack"), sourceTrackGeom)

        // Split target tracks:

        // First target will be created as new on split -> no track yet

        // Second target is a duplicate to be overridden partially: make sure the overridden part fits the final product
        val target2Geom =
            trackGeometry(
                // First edges get replaced: Make them similar-ish with the same split-point joints
                edge(
                    segments = listOf(segment(Point(200.5, 0.5), Point(210.5, 0.5))),
                    startInnerSwitch = switchLinkYV(switch1Id, 1),
                    endInnerSwitch = switchLinkYV(switch1Id, 2),
                ),
                edge(
                    segments = listOf(segment(Point(210.5, 0.5), Point(300.5, 0.5))),
                    startOuterSwitch = switchLinkYV(switch1Id, 2),
                    endOuterSwitch = switchLinkYV(switch2Id, 1),
                ),
                // The rest can veer off and is not replaced
                edge(
                    segments = listOf(segment(Point(300.5, 0.5), Point(400.0, 50.0))),
                    startInnerSwitch = switchLinkYV(switch2Id, 1),
                    endInnerSwitch = switchLinkYV(switch2Id, 2),
                ),
            )
        val (target2Id, target2Oid) =
            mainDraftContext.saveWithOid(locationTrack(tnId, name = "PartialDuplicate"), target2Geom)

        // Third target is a duplicate to be fully overridden: original geom doesn't matter
        val target3Geom = trackGeometryOfSegments(segment(Point(10.0, 10.0), Point(20.0, 10.0)))
        val (target3Id, target3Oid) =
            mainDraftContext.saveWithOid(locationTrack(tnId, name = "FullDuplicate"), target3Geom)

        // Base publication before the split
        val basePublication =
            testDBService.publish(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                locationTracks = listOf(sourceId, target2Id, target3Id),
                switches = listOf(switch1Id, switch2Id),
            )

        // Do the actual split
        val splitTargets =
            listOf(
                targetRequest(null, "NewTrack"),
                targetRequest(switch1Id, name = "PartialDuplicate", duplicateTrackId = target2Id, operation = TRANSFER),
                targetRequest(switch2Id, name = "FullDuplicate", duplicateTrackId = target3Id, operation = OVERWRITE),
            )
        val split = splitService.split(LayoutBranch.main, SplitRequest(sourceId, splitTargets)).let(splitService::get)!!
        // The split created targetTrack1 - add an Oid for it
        val target1Oid =
            split.targetLocationTracks
                .single { it.operation == SplitTargetOperation.CREATE }
                .let { mainDraftContext.generateOid(it.locationTrackId) }

        // Publication for the split should carry the new boundary changes
        val splitPublication =
            testDBService.publish(locationTracks = split.locationTracks, switches = split.relinkedSwitches)

        // Verify results
        api.trackBoundaryCollection.getModifiedBetween(basePublication.uuid, splitPublication.uuid).let { response ->
            assertEquals(basePublication.uuid.toString(), response.alkuversio)
            assertEquals(splitPublication.uuid.toString(), response.loppuversio)
            assertEquals(1, response.rajojen_muutokset.size)
            response.rajojen_muutokset[0].let { changeOperation ->
                assertEquals(splitPublication.uuid.toString(), changeOperation.rataverkon_versio)
                assertEquals(tnOid.toString(), changeOperation.ratanumero_oid)
                assertEquals(tn.toString(), changeOperation.ratanumero)
                assertEquals("raiteen_jakaminen", changeOperation.tyyppi)
                assertEquals(3, changeOperation.muutokset.size)
                changeOperation.muutokset.forEach { change ->
                    assertEquals(sourceOid.toString(), change.geometrian_lahderaide_oid)
                    assertEquals("SourceTrack", change.geometrian_lahderaide_tunnus)
                }
                changeOperation.muutokset[0].also { change ->
                    assertEquals(target1Oid.toString(), change.geometrian_kohderaide_oid)
                    assertEquals("NewTrack", change.geometrian_kohderaide_tunnus)
                    assertEquals("0000+0100.000", change.alkuosoite)
                    assertEquals("0000+0200.000", change.loppuosoite)
                    assertEquals(CREATE_NEW.value, change.geometrian_muutos)
                }
                changeOperation.muutokset[1].also { change ->
                    assertEquals(target2Oid.toString(), change.geometrian_kohderaide_oid)
                    assertEquals("PartialDuplicate", change.geometrian_kohderaide_tunnus)
                    assertEquals("0000+0200.000", change.alkuosoite)
                    assertEquals("0000+0300.000", change.loppuosoite)
                    assertEquals(REPLACE_DUPLICATE_PARTIAL.value, change.geometrian_muutos)
                }
                changeOperation.muutokset[2].also { change ->
                    assertEquals(target3Oid.toString(), change.geometrian_kohderaide_oid)
                    assertEquals("FullDuplicate", change.geometrian_kohderaide_tunnus)
                    assertEquals("0000+0300.000", change.alkuosoite)
                    assertEquals("0000+0400.000", change.loppuosoite)
                    assertEquals(REPLACE_DUPLICATE.value, change.geometrian_muutos)
                }
            }
        }
    }

    @Test
    fun `Track boundary change shows up with vaihtumiskohdan_siirto type`() {
        val tn = testDBService.getUnusedTrackNumber()
        val (tnId, tnOid) = mainDraftContext.saveWithOid(trackNumber(tn))
        val rlGeom = referenceLineGeometry(segment(Point(0.0, 0.0), Point(500.0, 0.0)))
        val rlId = mainDraftContext.save(referenceLine(tnId), rlGeom).id

        // Switch1 at 100: current boundary between tracks
        // Switch2 at 200: new boundary point (on shortening track)
        val structureId = switchStructureYV60_300_1_9().id
        val switch1Joints = listOf(switchJoint(1, Point(100.0, 0.0)), switchJoint(2, Point(110.0, 10.0)))
        val switch1Id = mainDraftContext.save(switch(structureId, switch1Joints)).id
        val switch2Joints = listOf(switchJoint(1, Point(200.0, 0.0)), switchJoint(2, Point(210.0, 10.0)))
        val switch2Id = mainDraftContext.save(switch(structureId, switch2Joints)).id

        // Lengthening track: 0 to 110, ending at switch1
        val lengtheningTrackGeom =
            trackGeometry(
                edge(
                    segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
                    endOuterSwitch = switchLinkYV(switch1Id, 1),
                ),
                edge(
                    segments = listOf(segment(Point(100.0, 0.0), Point(110.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch1Id, 1),
                    endInnerSwitch = switchLinkYV(switch1Id, 2),
                ),
            )
        val (lengtheningId, lengtheningOid) =
            mainDraftContext.saveWithOid(locationTrack(tnId, name = "LengtheningTrack"), lengtheningTrackGeom)

        // Shortening track: 110 to 400, starts at switch1, has switch2
        val shorteningTrackGeom =
            trackGeometry(
                edge(
                    segments = listOf(segment(Point(110.0, 0.0), Point(200.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch1Id, 2),
                    endOuterSwitch = switchLinkYV(switch2Id, 1),
                ),
                edge(
                    segments = listOf(segment(Point(200.0, 0.0), Point(210.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch2Id, 1),
                    endInnerSwitch = switchLinkYV(switch2Id, 2),
                ),
                edge(
                    segments = listOf(segment(Point(210.0, 0.0), Point(400.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch2Id, 2),
                ),
            )
        val (shorteningId, shorteningOid) =
            mainDraftContext.saveWithOid(locationTrack(tnId, name = "ShorteningTrack"), shorteningTrackGeom)

        val basePublication =
            testDBService.publish(
                trackNumbers = listOf(tnId),
                referenceLines = listOf(rlId),
                locationTracks = listOf(shorteningId, lengtheningId),
                switches = listOf(switch1Id, switch2Id),
            )

        val trackBoundaryChangeId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = shorteningId,
                lengtheningTrackId = lengtheningId,
                switch = switch2Id,
                switchJoint = JointNumber(1),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )

        val trackBoundaryChange = trackBoundaryMoveService.get(trackBoundaryChangeId)!!

        val changePublication = testDBService.publish(locationTracks = trackBoundaryChange.locationTracks.map { it.id })

        api.trackBoundaryCollection.getModifiedBetween(basePublication.uuid, changePublication.uuid).let { response ->
            assertEquals(basePublication.uuid.toString(), response.alkuversio)
            assertEquals(changePublication.uuid.toString(), response.loppuversio)
            assertEquals(1, response.rajojen_muutokset.size)
            response.rajojen_muutokset[0].let { changeOperation ->
                assertEquals(changePublication.uuid.toString(), changeOperation.rataverkon_versio)
                assertEquals(tnOid.toString(), changeOperation.ratanumero_oid)
                assertEquals(tn.toString(), changeOperation.ratanumero)
                assertEquals("vaihtumiskohdan_siirto", changeOperation.tyyppi)
                assertEquals(1, changeOperation.muutokset.size)
                changeOperation.muutokset[0].also { change ->
                    assertEquals(shorteningOid.toString(), change.geometrian_lahderaide_oid)
                    assertEquals("ShorteningTrack", change.geometrian_lahderaide_tunnus)
                    assertEquals(lengtheningOid.toString(), change.geometrian_kohderaide_oid)
                    assertEquals("LengtheningTrack", change.geometrian_kohderaide_tunnus)
                    assertEquals("0000+0110.000", change.alkuosoite)
                    assertEquals("0000+0200.000", change.loppuosoite)
                    assertEquals(TRANSFER_GEOMETRY.value, change.geometrian_muutos)
                }
            }
        }
    }
}
