package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
    fun `Switch APIs return correct object versions`() {
        val switch1 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(10.0, 0.0))),
                switchStructureYV60_300_1_9(),
            )
        val switch2 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 20.0)) to switchJoint(2, Point(10.0, 20.0))),
                switchStructureYV60_300_1_9(),
            )
        val baseVersion = extTestDataService.publishInMain(listOf(switch1, switch2)).uuid

        val switch1Oid = switch1.switch.oid
        val switch2Oid = switch2.switch.oid
        val switch1BeforeUpdate = mainOfficialContext.fetch(switch1.switch.id)!!
        val switch2BeforeUpdate = mainOfficialContext.fetch(switch2.switch.id)!!
        assertNotEquals(switch1BeforeUpdate, switch2BeforeUpdate)

        // Verify initial state is shown as latest
        assertLatestStateInApi(baseVersion, switch1Oid to switch1BeforeUpdate, switch2Oid to switch2BeforeUpdate)

        initUser()

        // Udate switch 1
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
                switchStructureYV60_300_1_9(),
            )
        val switch2 =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 20.0)) to switchJoint(2, Point(10.0, 20.0))),
                switchStructureYV60_300_1_9(),
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
                switchStructureYV60_300_1_9(),
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
    fun `Switch API respects the coordinate system argument`() {
        TODO()
        // Single + collection fetch
    }

    @Test
    fun `Single switch api should return switch information regardless of its state`() {
        TODO()
    }

    @Test
    fun `Switch modifications listing only lists modified switches`() {
        TODO()
    }

    @Test
    fun `Switch modification APIs should use the newest track layout version if end version is not supplied`() {
        TODO()
        // Single + collection fetch
    }

    @Test
    fun `Switch modification APIs should show modifications for calculated change`() {
        TODO()
        // Single + collection fetch
    }

    @Test
    fun `Deleted switches have no addresses exposed through the API`() {
        TODO()
        // Single + collection fetch
    }

    @Test
    fun `Switch modification APIs should respect start & end track layout version arguments`() {
        TODO()
        // Single + collection fetch
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
                LayoutStateCategory.EXISTING -> "olemassaoleva kohde"
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

        // TODO: verify joints
        // TODO: verify addresses (track links)
    }
}
