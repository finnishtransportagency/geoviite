package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.CREATE_NEW
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.REPLACE_DUPLICATE
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackBoundaryGeometryChangeTypeV1.REPLACE_DUPLICATE_PARTIAL
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitRequest
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.OVERWRITE
import fi.fta.geoviite.infra.split.SplitTargetDuplicateOperation.TRANSFER
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.split.targetRequest
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
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
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val splitService: SplitService,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `LocationTrack split shows up a boundary change`() {
        val tn = testDBService.getUnusedTrackNumber()
        val tnId = mainDraftContext.createLayoutTrackNumber(tn).id
        val tnOid = mainDraftContext.generateOid(tnId)
        val rlGeom = alignment(segment(Point(0.0, 0.0), Point(500.0, 0.0)))
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
        val sourceId = mainDraftContext.save(locationTrack(tnId, name = "SourceTrack"), sourceTrackGeom).id
        val sourceOid = mainDraftContext.generateOid(sourceId)

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
        val target2Id = mainDraftContext.save(locationTrack(tnId, name = "PartialDuplicate"), target2Geom).id
        val target2Oid = mainDraftContext.generateOid(target2Id)

        // Third target is a duplicate to be fully overridden: original geom doesn't matter
        val target3Geom = trackGeometryOfSegments(segment(Point(10.0, 10.0), Point(20.0, 10.0)))
        val target3Id = mainDraftContext.save(locationTrack(tnId, name = "FullDuplicate"), target3Geom).id
        val target3Oid = mainDraftContext.generateOid(target3Id)

        // Base publication before the split
        val basePublication =
            extTestDataService.publishInMain(
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
            extTestDataService.publishInMain(locationTracks = split.locationTracks, switches = split.relinkedSwitches)

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
}
