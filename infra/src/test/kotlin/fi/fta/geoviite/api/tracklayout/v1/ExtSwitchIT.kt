package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.linkedTrackGeometry
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtSwitchIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val switchLibrary: SwitchLibraryService,
    private val switchService: LayoutSwitchService,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Switch APIs should return correct object versions`() {
        val switch1 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val switch2 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 20.0)) to switchJoint(2, Point(10.0, 20.0))),
            )
        val baseVersion = extTestDataService.publishInMain(listOf(switch1, switch2)).uuid

        val switch1Oid = switch1.switch.oid
        val switch2Oid = switch2.switch.oid
        val switch1BeforeUpdate = mainOfficialContext.fetch(switch1.switch.id)!!
        val switch2BeforeUpdate = mainOfficialContext.fetch(switch2.switch.id)!!
        assertNotEquals(switch1BeforeUpdate, switch2BeforeUpdate)

        // Verify initial state is shown as latest
        assertLatestStateInApi(baseVersion, switch1Oid to switch1BeforeUpdate, switch2Oid to switch2BeforeUpdate)

        // Update switch 1
        initUser()
        mainDraftContext.mutate(switch1.switch.id) { s -> s.copy(name = SwitchName(s.name.toString() + "-EDIT")) }
        val updatedVersion = extTestDataService.publishInMain(switches = listOf(switch1.switch.id)).uuid
        val switch1AfterUpdate = mainOfficialContext.fetch(switch1.switch.id)!!
        assertNotEquals(switch1BeforeUpdate, switch1AfterUpdate)
        assertEquals(switch2BeforeUpdate, mainOfficialContext.fetch(switch2.switch.id)!!)

        // Verify all fetches show the new publication but only switch 1 is updated
        assertLatestStateInApi(updatedVersion, switch1Oid to switch1AfterUpdate, switch2Oid to switch2BeforeUpdate)

        // Verify that fetching at specific versions also show the same results
        assertVersionStateInApi(baseVersion, switch1Oid to switch1BeforeUpdate, switch2Oid to switch2BeforeUpdate)
        assertVersionStateInApi(updatedVersion, switch1Oid to switch1AfterUpdate, switch2Oid to switch2BeforeUpdate)
    }

    @Test
    fun `Switch API does not contain draft tracks or changes`() {
        val switch1 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val switch2 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 20.0)) to switchJoint(2, Point(10.0, 20.0))),
            )
        // Only publish switch 1 at first
        val baseVersion = extTestDataService.publishInMain(listOf(switch1)).uuid

        val switch1Oid = switch1.switch.oid
        val switch1Id = switch1.switch.id
        val switch2Oid = switch2.switch.oid
        val switch2Id = switch2.switch.id

        val switch1Base = mainOfficialContext.fetch(switch1Id)!!
        assertNull(mainOfficialContext.fetch(switch2Id))

        assertLatestStateInApi(baseVersion, switch1Oid to switch1Base)

        initUser()

        // Update both, but now only publish switch 2 (wasn't published before)
        mainDraftContext.mutate(switch1Id) { s -> s.copy(name = SwitchName(s.name.toString() + "-EDIT1")) }
        mainDraftContext.mutate(switch2Id) { s -> s.copy(name = SwitchName(s.name.toString() + "-EDIT2")) }
        val updatedVersion = extTestDataService.publishInMain(switches = listOf(switch2.switch.id)).uuid

        assertEquals(switch1Base, mainOfficialContext.fetch(switch1Id))
        val switch2Updated = mainOfficialContext.fetch(switch2Id)!!

        // Verify latest data shows switch 1 as before and switch 2 as new
        assertLatestStateInApi(updatedVersion, switch1Oid to switch1Base, switch2Oid to switch2Updated)

        // Verify fetching at specific versions also gives correct results
        assertVersionStateInApi(baseVersion, switch1Oid to switch1Base)
        assertVersionStateInApi(updatedVersion, switch1Oid to switch1Base, switch2Oid to switch2Updated)
    }

    @Test
    fun `Deleted switch should be returned in single-fetch API but not collection API`() {
        val switch =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val id = switch.switch.id
        val oid = switch.switch.oid

        val baseVersion = extTestDataService.publishInMain(listOf(switch)).uuid
        val baseSwitch =
            mainOfficialContext.fetch(id)!!.also { assertEquals(LayoutStateCategory.EXISTING, it.stateCategory) }
        assertLatestStateInApi(baseVersion, oid to baseSwitch)

        initUser()
        switchService.clearSwitchInformationFromTracks(LayoutBranch.main, switch.switch.id)
        mainDraftContext.mutate(switch.switch.id) { s -> s.copy(stateCategory = LayoutStateCategory.NOT_EXISTING) }

        val deletedVersion =
            extTestDataService
                .publishInMain(switches = listOf(switch.switch.id), locationTracks = switch.tracks.map { it.id })
                .uuid
        val deletedSwitch =
            mainOfficialContext.fetch(id)!!.also { assertEquals(LayoutStateCategory.NOT_EXISTING, it.stateCategory) }

        assertLatestState(deletedVersion, oid, deletedSwitch)
        assertLatestCollectionState(deletedVersion)
        assertVersionState(baseVersion, oid, baseSwitch)
        assertVersionCollectionState(baseVersion, oid to baseSwitch)
        assertVersionState(deletedVersion, oid, deletedSwitch)
        assertVersionCollectionState(deletedVersion)
    }

    @Test
    fun `Switch modification APIs should return correct versions`() {
        val switch1 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val switch2 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 20.0)) to switchJoint(2, Point(10.0, 20.0))),
            )
        val (_, switch1Oid) = switch1.switch
        val (switch2Id, switch2Oid) = switch2.switch
        val baseVersion = extTestDataService.publishInMain(listOf(switch1, switch2)).uuid

        // First publication -> no changes (verify as both since and between fetches)
        assertChangesSince(baseVersion, baseVersion, listOf(switch1Oid, switch2Oid), emptyList())
        assertChangesBetween(baseVersion, baseVersion, listOf(switch1Oid, switch2Oid), emptyList())

        // Update switch 2 and add switch 3
        initUser()
        mainDraftContext.mutate(switch2Id) { s -> s.copy(name = SwitchName(s.name.toString() + "-EDIT")) }
        val switch3 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 30.0)) to switchJoint(2, Point(10.0, 30.0))),
                switchStructureYV60_300_1_9(),
            )
        val (switch3Id, switch3Oid) = switch3.switch
        val update1Version =
            extTestDataService
                .publishInMain(
                    switches = listOf(switch2Id, switch3Id),
                    locationTracks = switch3.tracks.map { it.id },
                    trackNumbers = listOf(switch3.trackNumber.id),
                    referenceLines = listOf(switch3.referenceLineId),
                )
                .uuid
        val switch2Update1 = mainOfficialContext.fetch(switch2Id)!!
        val switch3Update1 = mainOfficialContext.fetch(switch3Id)!!

        // Changes since base version (verify as both since and between fetches)
        assertChangesSince(
            baseVersion,
            update1Version,
            listOf(switch1Oid),
            listOf(switch2Oid to switch2Update1, switch3Oid to switch3Update1),
        )
        assertChangesBetween(
            baseVersion,
            update1Version,
            listOf(switch1Oid),
            listOf(switch2Oid to switch2Update1, switch3Oid to switch3Update1),
        )

        // Update switch 2 again
        initUser()
        mainDraftContext.mutate(switch2.switch.id) { s -> s.copy(name = SwitchName(s.name.toString() + "-EDIT2")) }
        val update2Version = extTestDataService.publishInMain(switches = listOf(switch2Id)).uuid
        val switch2Update2 = mainOfficialContext.fetch(switch2.switch.id)!!

        // Changes since base version: s1 unchanged, s2 changed twice, s3 added
        assertChangesSince(
            baseVersion,
            update2Version,
            listOf(switch1Oid),
            listOf(switch2Oid to switch2Update2, switch3Oid to switch3Update1),
        )
        assertChangesBetween(
            baseVersion,
            update2Version,
            listOf(switch1Oid),
            listOf(switch2Oid to switch2Update2, switch3Oid to switch3Update1),
        )

        // Changes since update1 version: s1 & s3 unchanged, s2 changed once
        assertChangesSince(
            update1Version,
            update2Version,
            listOf(switch1Oid, switch3Oid),
            listOf(switch2Oid to switch2Update2),
        )
        assertChangesBetween(
            update1Version,
            update2Version,
            listOf(switch1Oid, switch3Oid),
            listOf(switch2Oid to switch2Update2),
        )

        // Changes between base and update1: s1 unchanged, s2 changed once, s3 added
        assertChangesBetween(
            baseVersion,
            update1Version,
            listOf(switch1Oid),
            listOf(switch2Oid to switch2Update1, switch3Oid to switch3Update1),
        )
    }

    @Test
    fun `Switch APIs should display correct track link locations and addresses`() {
        val joint1 = switchJoint(1, Point(0.0, 0.0))
        val joint2 = switchJoint(2, Point(10.0, 0.0))
        val joint3 = switchJoint(3, Point(10.0, 5.0))
        val structure = switchStructureYV60_300_1_9()
        val switchId = mainDraftContext.save(switch(structure.id, joints = listOf(joint1, joint2, joint3))).id
        val switchOid = mainDraftContext.generateOid(switchId)
        val switch = mainDraftContext.fetch(switchId)!!

        val segment1to2 = segment(joint1.location, joint2.location)
        val (tn1Id, rl1Id) =
            extTestDataService.insertTrackNumberAndReferenceLine(
                mainDraftContext,
                startAddress = TrackMeter("0001+0100.000"),
                segments = listOf(segment1to2),
            )
        val track1Geom = linkedTrackGeometry(switch, joint1.number, joint2.number, structure)
        val track1Id = mainDraftContext.save(locationTrack(tn1Id), track1Geom).id
        val track1Oid = mainDraftContext.generateOid(track1Id)

        // Intentionally offset track 2 & it's reference line a bit: the link points be the points-on-track
        val segment1to3 = segment(joint1.location + 0.5, joint3.location + 0.5)
        val (tn2Id, rl2Id) =
            extTestDataService.insertTrackNumberAndReferenceLine(
                mainDraftContext,
                startAddress = TrackMeter("0002+0200.000"),
                segments = listOf(segment1to3),
            )
        val track2Geom =
            trackGeometry(
                edge(
                    segments = listOf(segment1to3),
                    startInnerSwitch = switchLinkYV(switchId, 1),
                    endInnerSwitch = switchLinkYV(switchId, 3),
                )
            )
        val track2Id = mainDraftContext.save(locationTrack(tn2Id), track2Geom).id
        val track2Oid = mainDraftContext.generateOid(track2Id)

        extTestDataService.publishInMain(
            switches = listOf(switchId),
            locationTracks = listOf(track1Id, track2Id),
            trackNumbers = listOf(tn1Id, tn2Id),
            referenceLines = listOf(rl1Id, rl2Id),
        )

        api.switch.get(switchOid).vaihde.raidelinkit.also { links ->
            assertEquals(setOf(track1Oid.toString(), track2Oid.toString()), links.map { it.sijaintiraide_oid }.toSet())
            assertTrackLinkMatch(
                links.find { it.sijaintiraide_oid == track1Oid.toString() }!!,
                ExpectedJoint(1, track1Geom.start!!, TrackMeter("0001+0100.000")),
                ExpectedJoint(2, track1Geom.end!!, TrackMeter("0001+0100.000") + track1Geom.length.distance),
            )
            assertTrackLinkMatch(
                links.find { it.sijaintiraide_oid == track2Oid.toString() }!!,
                ExpectedJoint(1, track2Geom.start!!, TrackMeter("0002+0200.000")),
                ExpectedJoint(3, track2Geom.end!!, TrackMeter("0002+0200.000") + track2Geom.length.distance),
            )
        }
    }

    @Test
    fun `Switch modification APIs should show modifications for calculated change`() {
        val structure = switchStructureYV60_300_1_9()

        val s1Joint1 = switchJoint(1, Point(0.0, 0.0))
        val s1Joint2 = switchJoint(2, Point(10.0, 0.0))
        val switch1Id = mainDraftContext.save(switch(structure.id, joints = listOf(s1Joint1, s1Joint2))).id
        val switch1Oid = mainDraftContext.generateOid(switch1Id)
        val switch1 = mainDraftContext.fetch(switch1Id)!!

        val s2Joint1 = switchJoint(1, Point(0.0, 0.0))
        val s2Joint2 = switchJoint(2, Point(10.0, 0.0))
        val switch2Id = mainDraftContext.save(switch(structure.id, joints = listOf(s2Joint1, s2Joint2))).id
        val switch2Oid = mainDraftContext.generateOid(switch2Id)
        val switch2 = mainDraftContext.fetch(switch2Id)!!

        val (tn1Id, rl1Id) =
            extTestDataService.insertTrackNumberAndReferenceLine(
                mainDraftContext,
                startAddress = TrackMeter("0001+0100.000"),
                segments = listOf(segment(s1Joint1.location, s1Joint2.location)),
            )
        val track1Geom = linkedTrackGeometry(switch1, s1Joint1.number, s1Joint2.number, structure)
        val track1Id = mainDraftContext.save(locationTrack(tn1Id), track1Geom).id
        mainDraftContext.generateOid(track1Id)

        val (tn2Id, rl2Id) =
            extTestDataService.insertTrackNumberAndReferenceLine(
                mainDraftContext,
                startAddress = TrackMeter("0002+0200.000"),
                segments = listOf(segment(s2Joint1.location, s2Joint2.location)),
            )
        val track2Geom = linkedTrackGeometry(switch2, s2Joint1.number, s2Joint2.number, structure)
        val track2Id = mainDraftContext.save(locationTrack(tn2Id), track2Geom).id
        val track2Oid = mainDraftContext.generateOid(track2Id)

        val baseVersion =
            extTestDataService
                .publishInMain(
                    switches = listOf(switch1Id, switch2Id),
                    locationTracks = listOf(track1Id, track2Id),
                    trackNumbers = listOf(tn1Id, tn2Id),
                    referenceLines = listOf(rl1Id, rl2Id),
                )
                .uuid

        // Verify base state
        api.switch.assertNoModificationSince(switch1Oid, baseVersion)
        api.switch.assertNoModificationSince(switch2Oid, baseVersion)
        api.switch.get(switch2Oid).let { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            val links = response.vaihde.raidelinkit
            assertEquals(setOf(track2Oid.toString()), links.map { it.sijaintiraide_oid }.toSet())
            assertTrackLinkMatch(
                links[0],
                ExpectedJoint(1, track2Geom.start!!, TrackMeter("0002+0200.000")),
                ExpectedJoint(2, track2Geom.end!!, TrackMeter("0002+0200.000") + track2Geom.length.distance),
            )
        }

        // Update reference line 2 start -> should produce calculated change for track 2, affecting switch2 links
        initUser()
        mainDraftContext.mutate(rl2Id) { rl -> rl.copy(startAddress = TrackMeter("0003+0300.000")) }
        val updateVersion = extTestDataService.publishInMain(referenceLines = listOf(rl2Id)).uuid

        // Different track number -> should not affect switch1
        api.switch.assertNoModificationSince(switch1Oid, baseVersion)

        api.switch.getModifiedSince(switch2Oid, baseVersion).let { response ->
            assertEquals(baseVersion.toString(), response.alkuversio)
            assertEquals(updateVersion.toString(), response.loppuversio)
            val links = response.vaihde.raidelinkit
            assertEquals(setOf(track2Oid.toString()), links.map { it.sijaintiraide_oid }.toSet())
            assertTrackLinkMatch(
                links[0],
                ExpectedJoint(1, track2Geom.start!!, TrackMeter("0003+0300.000")),
                ExpectedJoint(2, track2Geom.end!!, TrackMeter("0003+0300.000") + track2Geom.length.distance),
            )
        }

        // Ensure base state still resolves as before
        api.switch.getAtVersion(switch2Oid, baseVersion).let { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            val links = response.vaihde.raidelinkit
            assertEquals(setOf(track2Oid.toString()), links.map { it.sijaintiraide_oid }.toSet())
            assertTrackLinkMatch(
                links[0],
                ExpectedJoint(1, track2Geom.start!!, TrackMeter("0002+0200.000")),
                ExpectedJoint(2, track2Geom.end!!, TrackMeter("0002+0200.000") + track2Geom.length.distance),
            )
        }
    }

    data class ExpectedJoint(val number: Int, val location: IPoint, val address: TrackMeter)

    private fun assertTrackLinkMatch(actualLink: ExtTestSwitchTrackLinkV1, vararg expectedJoints: ExpectedJoint) {
        assertEquals(expectedJoints.map { it.number }, actualLink.pisteet.map { it.numero })
        actualLink.pisteet.forEachIndexed { index, actual ->
            val expected = expectedJoints[index]
            assertEquals(expected.location.x, actual.sijainti.x, LAYOUT_M_DELTA)
            assertEquals(expected.location.y, actual.sijainti.y, LAYOUT_M_DELTA)
            assertEquals(expected.address.toString(), actual.sijainti.rataosoite)
        }
    }

    @Test
    fun `Switch APIs respect the coordinate system argument`() {
        val joint1Location =
            mapOf(LAYOUT_SRID to Point(385782.89, 6672277.83), Srid(4326) to Point(24.9414003, 60.1713788))
        val joint2Location =
            mapOf(LAYOUT_SRID to Point(385882.89, 6672277.83), Srid(4326) to Point(24.9432013, 60.1714068))
        val switch =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, joint1Location[LAYOUT_SRID]!!) to switchJoint(2, joint2Location[LAYOUT_SRID]!!)),
            )
        val switchOid = switch.switch.oid
        extTestDataService.publishInMain(listOf(switch)).uuid

        api.switch.get(switchOid).let { response ->
            assertEquals("EPSG:3067", response.koordinaatisto)
            assertLocations(1, joint1Location[LAYOUT_SRID]!!, response.vaihde)
            assertLocations(2, joint2Location[LAYOUT_SRID]!!, response.vaihde)
            api.switchCollection.get().let { collectionResponse ->
                assertEquals("EPSG:3067", collectionResponse.koordinaatisto)
                assertEquals(response.vaihde, collectionResponse.vaihteet[0])
            }
        }
        api.switch.get(switchOid, "koordinaatisto" to "EPSG:4326").let { response ->
            assertEquals("EPSG:4326", response.koordinaatisto)
            assertLocations(1, joint1Location[Srid(4326)]!!, response.vaihde)
            assertLocations(2, joint2Location[Srid(4326)]!!, response.vaihde)
            api.switchCollection.get("koordinaatisto" to "EPSG:4326").let { collectionResponse ->
                assertEquals("EPSG:4326", collectionResponse.koordinaatisto)
                assertEquals(response.vaihde, collectionResponse.vaihteet[0])
            }
        }
    }

    @Test
    fun `Deleted tracks don't show up as part of a switch`() {
        val switch =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val switchOid = switch.switch.oid
        val baseVersion = extTestDataService.publishInMain(listOf(switch)).uuid
        api.switch.get(switchOid).let { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            assertEquals(
                switch.tracks.map { it.oid.toString() }.toSet(),
                response.vaihde.raidelinkit.map { it.sijaintiraide_oid }.toSet(),
            )
        }

        initUser()
        mainDraftContext.mutate(switch.tracks[0].id) { lt -> lt.copy(state = LocationTrackState.DELETED) }
        val updateVersion = extTestDataService.publishInMain(locationTracks = switch.tracks.map { it.id }).uuid

        api.switch.get(switchOid).let { response ->
            assertEquals(updateVersion.toString(), response.rataverkon_versio)
            assertEquals(emptyList<String>(), response.vaihde.raidelinkit.map { it.sijaintiraide_oid })
        }
        api.switch.getAtVersion(switchOid, baseVersion).let { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            assertEquals(
                switch.tracks.map { it.oid.toString() }.toSet(),
                response.vaihde.raidelinkit.map { it.sijaintiraide_oid }.toSet(),
            )
        }
    }

    // TODO: GVT-3404 Enable test when track deletion produces a calculated change in switch
    @Disabled("GVT-3404 - track deletion does not produce calculated change in switch")
    @Test
    fun `Track deletion shows up as a (calculated) change in ext-api switches`() {
        val switch =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
            )
        val switchOid = switch.switch.oid
        val baseVersion = extTestDataService.publishInMain(listOf(switch)).uuid

        initUser()
        mainDraftContext.mutate(switch.tracks[0].id) { lt -> lt.copy(state = LocationTrackState.DELETED) }
        val updateVersion = extTestDataService.publishInMain(locationTracks = switch.tracks.map { it.id }).uuid

        api.switch.getModifiedSince(switchOid, baseVersion).let { response ->
            assertEquals(baseVersion.toString(), response.alkuversio)
            assertEquals(updateVersion.toString(), response.loppuversio)
            assertEquals(emptyList<String>(), response.vaihde.raidelinkit.map { it.sijaintiraide_oid })
        }
        api.switch.assertNoModificationSince(switchOid, updateVersion)
    }

    private fun assertChangesSince(
        baseVersion: Uuid<Publication>,
        currentVersion: Uuid<Publication>,
        notChanged: List<Oid<LayoutSwitch>>,
        changed: List<Pair<Oid<LayoutSwitch>, LayoutSwitch>>,
    ) {
        for (oid in notChanged) api.switch.assertNoModificationSince(oid, baseVersion)
        for ((oid, switch) in changed) {
            val response = api.switch.getModifiedSince(oid, baseVersion)
            assertEquals(baseVersion.toString(), response.alkuversio)
            assertEquals(currentVersion.toString(), response.loppuversio)
            assertMatches(oid, switch, response.vaihde)
        }
        if (changed.isEmpty()) api.switchCollection.assertNoModificationSince(baseVersion)
        else
            api.switchCollection.getModifiedSince(baseVersion).also { response ->
                assertEquals(baseVersion.toString(), response.alkuversio)
                assertEquals(currentVersion.toString(), response.loppuversio)
                assertCollectionMatches(response.vaihteet, *(changed.toTypedArray()))
            }
    }

    private fun assertChangesBetween(
        from: Uuid<Publication>,
        to: Uuid<Publication>,
        notChanged: List<Oid<LayoutSwitch>>,
        changed: List<Pair<Oid<LayoutSwitch>, LayoutSwitch>>,
    ) {
        for (oid in notChanged) api.switch.assertNoModificationBetween(oid, from, to)
        for ((oid, switch) in changed) {
            val response = api.switch.getModifiedBetween(oid, from, to)
            assertEquals(from.toString(), response.alkuversio)
            assertEquals(to.toString(), response.loppuversio)
            assertMatches(oid, switch, response.vaihde)
        }
        if (changed.isEmpty()) api.switchCollection.assertNoModificationBetween(from, to)
        else
            api.switchCollection.getModifiedBetween(from, to).also { response ->
                assertEquals(from.toString(), response.alkuversio)
                assertEquals(to.toString(), response.loppuversio)
                assertCollectionMatches(response.vaihteet, *(changed.toTypedArray()))
            }
    }

    private fun assertLatestStateInApi(
        layoutVersion: Uuid<Publication>,
        vararg switches: Pair<Oid<LayoutSwitch>, LayoutSwitch>,
    ) {
        for ((oid, switch) in switches) assertLatestState(layoutVersion, oid, switch)
        assertLatestCollectionState(layoutVersion, *switches)
    }

    private fun assertVersionStateInApi(
        layoutVersion: Uuid<Publication>,
        vararg switches: Pair<Oid<LayoutSwitch>, LayoutSwitch>,
    ) {
        for ((oid, switch) in switches) assertVersionState(layoutVersion, oid, switch)
        assertVersionCollectionState(layoutVersion, *switches)
    }

    private fun assertLatestState(layoutVersion: Uuid<Publication>, oid: Oid<LayoutSwitch>, switch: LayoutSwitch) {
        val response = api.switch.get(oid)
        assertEquals(layoutVersion.toString(), response.rataverkon_versio)
        assertMatches(oid, switch, response.vaihde)
    }

    private fun assertVersionState(layoutVersion: Uuid<Publication>, oid: Oid<LayoutSwitch>, switch: LayoutSwitch) {
        val response = api.switch.getAtVersion(oid, layoutVersion)
        assertEquals(layoutVersion.toString(), response.rataverkon_versio)
        assertMatches(oid, switch, response.vaihde)
    }

    private fun assertLatestCollectionState(
        layoutVersion: Uuid<Publication>,
        vararg switches: Pair<Oid<LayoutSwitch>, LayoutSwitch>,
    ) {
        val collectionResponse = api.switchCollection.get()
        assertEquals(layoutVersion.toString(), collectionResponse.rataverkon_versio)
        assertCollectionMatches(collectionResponse.vaihteet, *switches)
    }

    private fun assertVersionCollectionState(
        layoutVersion: Uuid<Publication>,
        vararg switches: Pair<Oid<LayoutSwitch>, LayoutSwitch>,
    ) {
        val collectionResponse = api.switchCollection.getAtVersion(layoutVersion)
        assertEquals(layoutVersion.toString(), collectionResponse.rataverkon_versio)
        assertCollectionMatches(collectionResponse.vaihteet, *switches)
    }

    private fun assertCollectionMatches(
        resultSwitches: List<ExtTestSwitchV1>,
        vararg switches: Pair<Oid<LayoutSwitch>, LayoutSwitch>,
    ) {
        assertEquals(switches.map { it.first.toString() }.toSet(), resultSwitches.map { it.vaihde_oid }.toSet())
        for ((oid, switch) in switches) {
            assertCollectionItemMatches(oid, switch, resultSwitches)
        }
    }

    private fun assertCollectionItemMatches(
        oid: Oid<LayoutSwitch>,
        switch: LayoutSwitch,
        items: List<ExtTestSwitchV1>,
    ) = assertMatches(oid, switch, items.find { it.vaihde_oid == oid.toString() })

    private fun assertMatches(oid: Oid<LayoutSwitch>, switch: LayoutSwitch, actual: ExtTestSwitchV1?) {
        requireNotNull(actual) { "Expected switch $oid not found in response" }
        assertEquals(oid.toString(), actual.vaihde_oid)
        assertEquals(switch.name.toString(), actual.vaihdetunnus)
        assertEquals(
            when (switch.stateCategory) {
                LayoutStateCategory.NOT_EXISTING -> "poistunut kohde"
                LayoutStateCategory.EXISTING -> "olemassa oleva kohde"
            },
            actual.tilakategoria,
        )
        assertEquals(
            when (switch.trapPoint) {
                null -> "ei tiedossa"
                true -> "kyllä"
                false -> "ei"
            },
            actual.turvavaihde,
        )

        assertEquals(switchLibrary.getSwitchOwner(switch.ownerId)!!.name.toString(), actual.omistaja)

        val structure = switchLibrary.getSwitchStructure(switch.switchStructureId)
        assertEquals(structure.type.toString(), actual.tyyppi)
        assertEquals(structure.presentationJointNumber.intValue, actual.esityspisteen_numero)
        assertEquals(
            when (structure.hand) {
                SwitchHand.LEFT -> "vasen"
                SwitchHand.RIGHT -> "oikea"
                SwitchHand.NONE -> "ei määritelty"
            },
            actual.katisyys,
        )

        assertEquals(switch.joints.map { it.number.intValue }.toSet(), actual.pisteet.map { it.numero }.toSet())
        actual.pisteet.forEach { actualJoint ->
            val expectedJoint = switch.getJoint(JointNumber(actualJoint.numero))!!
            assertEquals(expectedJoint.location.x, actualJoint.sijainti.x, LAYOUT_M_DELTA)
            assertEquals(expectedJoint.location.y, actualJoint.sijainti.y, LAYOUT_M_DELTA)
        }
        // Note: this does not verify track links: that has separate tests/asserts
    }

    fun assertLocations(jointNumber: Int, expected: IPoint, actual: ExtTestSwitchV1) {
        val joint = actual.pisteet.find { it.numero == jointNumber }!!
        assertEquals(expected.x, joint.sijainti.x, LAYOUT_M_DELTA)
        assertEquals(expected.y, joint.sijainti.y, LAYOUT_M_DELTA)
        actual.raidelinkit
            .flatMap { it.pisteet }
            .filter { it.numero == jointNumber }
            .forEach { trackJoint ->
                assertEquals(expected.x, trackJoint.sijainti.x, LAYOUT_M_DELTA)
                assertEquals(expected.y, trackJoint.sijainti.y, LAYOUT_M_DELTA)
            }
    }
}
