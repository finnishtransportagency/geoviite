package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.queryOne
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchDaoIT
@Autowired
constructor(private val switchDao: LayoutSwitchDao, private val locationTrackDao: LocationTrackDao) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun switchExternalIdIsUnique() {
        val oid = Oid<LayoutSwitch>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.switch_external_id where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val switch1 = switchDao.save(switch())
        val switch2 = switchDao.save(switch())
        switchDao.insertExternalId(switch1.id, LayoutBranch.main, oid)
        assertThrows<DuplicateKeyException> { switchDao.insertExternalId(switch2.id, LayoutBranch.main, oid) }
    }

    @Test
    fun switchesAreStoredAndLoadedOk() {
        (1..10)
            .map { switch(draft = false) }
            .forEach { switch ->
                val rowVersion = switchDao.save(switch)
                assertMatches(switch, switchDao.fetch(rowVersion))
            }
    }

    @Test
    fun switchVersioningWorks() {
        val tempSwitch = switch(name = "TST001", joints = joints(3, 5), draft = false)
        val insertVersion = switchDao.save(tempSwitch)
        val insertId = insertVersion.id
        val inserted = switchDao.fetch(insertVersion)
        assertMatches(tempSwitch, inserted)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertId))
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.draft, insertId))

        val tempDraft1 = asMainDraft(inserted).copy(name = SwitchName("TST002"))
        val draftVersion1 = switchDao.save(tempDraft1)
        val draft1 = switchDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertId))
        assertEquals(draftVersion1, switchDao.fetchVersion(MainLayoutContext.draft, insertId))

        val tempDraft2 = draft1.copy(joints = joints(5, 4))
        val draftVersion2 = switchDao.save(tempDraft2)
        val draft2 = switchDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertId))
        assertEquals(draftVersion2, switchDao.fetchVersion(MainLayoutContext.draft, insertId))

        switchDao.deleteDraft(LayoutBranch.main, insertId)
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.official, insertId))
        assertEquals(insertVersion, switchDao.fetchVersion(MainLayoutContext.draft, insertId))

        assertEquals(inserted, switchDao.fetch(insertVersion))
        assertEquals(draft1, switchDao.fetch(draftVersion1))
        assertEquals(draft2, switchDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { switchDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun shouldSuccessfullyDeleteDraftSwitches() {
        val draftSwitch = switch(draft = true)
        val insertedVersion = switchDao.save(draftSwitch)
        val insertedId = insertedVersion.id
        val insertedSwitch = switchDao.fetch(insertedVersion)

        val deletedId = switchDao.deleteDraft(LayoutBranch.main, insertedId)
        assertEquals(insertedId, deletedId.id)
        assertNull(switchDao.fetchVersion(MainLayoutContext.draft, insertedId))

        // Verify that we can still fetch the deleted row with version
        assertEquals(insertedSwitch, switchDao.fetch(insertedVersion))
    }

    @Test
    fun shouldThrowExceptionWhenDeletingNormalSwitch() {
        val switch = switch(draft = false)
        val insertedSwitch = switchDao.save(switch)

        assertThrows<DeletingFailureException> { switchDao.deleteDraft(LayoutBranch.main, insertedSwitch.id) }
    }

    @Test
    fun listingSwitchVersionsWorks() {
        val officialVersion = mainOfficialContext.createSwitch()
        val undeletedDraft = mainDraftContext.createSwitch(LayoutStateCategory.EXISTING)
        val deleteStateDraft = mainDraftContext.createSwitch(LayoutStateCategory.NOT_EXISTING)
        val deletedDraft = mainDraftContext.createSwitch(LayoutStateCategory.EXISTING)
        assertEquals(deletedDraft, switchDao.deleteDraft(LayoutBranch.main, deletedDraft.id))

        val official = switchDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraft))
        assertFalse(official.contains(deleteStateDraft))
        assertFalse(official.contains(deletedDraft))

        val draftWithoutDeleted = switchDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraft)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraft))
        assertFalse(draftWithoutDeleted.any { r -> r.id == deletedDraft.id })

        val draftWithDeleted = switchDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraft)
        assertContains(draftWithDeleted, deleteStateDraft)
        assertFalse(draftWithDeleted.contains(deletedDraft))
    }

    @Test
    fun `Finding Switch versions by moment works across designs and main branch`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)

        val v0Time = switchDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val switch1MainV1 = mainOfficialContext.createSwitch()
        val switch2DesignV1 = designOfficialContext.createSwitch()
        val switch3DesignV1 = designOfficialContext.createSwitch()
        val switch1Id = switch1MainV1.id
        val switch2Id = switch2DesignV1.id
        val switch3Id = switch3DesignV1.id
        val v1Time = switchDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val switch1MainV2 = testDBService.update(switch1MainV1)
        val switch1DesignV2 = designOfficialContext.copyFrom(switch1MainV1)
        val switch2DesignV2 = testDBService.update(switch2DesignV1)
        switchDao.deleteRow(switch3DesignV1.rowId)
        val v2Time = switchDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        switchDao.deleteRow(switch1DesignV2.rowId)
        // Fake publish: update the design as a main-official
        val switch2MainV3 = mainOfficialContext.moveFrom(switch2DesignV2)
        val v3Time = switchDao.fetchChangeTime()

        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch1Id, v0Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch2Id, v0Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch3Id, v0Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(designBranch, switch1Id, v0Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(designBranch, switch2Id, v0Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(designBranch, switch3Id, v0Time))

        assertEquals(switch1MainV1, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch1Id, v1Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch2Id, v1Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch3Id, v1Time))
        assertEquals(switch1MainV1, switchDao.fetchOfficialVersionAtMoment(designBranch, switch1Id, v1Time))
        assertEquals(switch2DesignV1, switchDao.fetchOfficialVersionAtMoment(designBranch, switch2Id, v1Time))
        assertEquals(switch3DesignV1, switchDao.fetchOfficialVersionAtMoment(designBranch, switch3Id, v1Time))

        assertEquals(switch1MainV2, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch1Id, v2Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch2Id, v2Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch3Id, v2Time))
        assertEquals(switch1DesignV2, switchDao.fetchOfficialVersionAtMoment(designBranch, switch1Id, v2Time))
        assertEquals(switch2DesignV2, switchDao.fetchOfficialVersionAtMoment(designBranch, switch2Id, v2Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(designBranch, switch3Id, v2Time))

        assertEquals(switch1MainV2, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch1Id, v3Time))
        assertEquals(switch2MainV3, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch2Id, v3Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(LayoutBranch.main, switch3Id, v3Time))
        assertEquals(switch1MainV2, switchDao.fetchOfficialVersionAtMoment(designBranch, switch1Id, v3Time))
        assertEquals(switch2MainV3, switchDao.fetchOfficialVersionAtMoment(designBranch, switch2Id, v3Time))
        assertEquals(null, switchDao.fetchOfficialVersionAtMoment(designBranch, switch3Id, v3Time))
    }

    @Test
    fun `findLocationTracksLinkedToSwitches() does not return a draft whose link was removed`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.save(switch()).id
        val oid = Oid<LocationTrack>("1.2.3.4.5")
        val officialTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = switchLinkYV(switch, 1), outer = null),
                        endNode = PlaceHolderNodeConnection,
                        segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                    )
                ),
            )
        locationTrackDao.insertExternalId(officialTrack.id, LayoutBranch.main, oid)
        mainDraftContext.save(
            asMainDraft(mainOfficialContext.fetch(officialTrack.id)!!),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
        )
        assertEquals(
            listOf(LayoutSwitchDao.LocationTrackIdentifiers(officialTrack, oid)),
            switchDao.findLocationTracksLinkedToSwitches(MainLayoutContext.official, listOf(switch))[switch],
        )
        assertEquals(
            null,
            switchDao.findLocationTracksLinkedToSwitches(MainLayoutContext.draft, listOf(switch))[switch],
        )
    }

    @Test
    fun `findLocationTracksLinkedToSwitchAtMoment handles changing connectivity over time`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.save(switch()).id
        val connectedAlignment =
            trackGeometry(
                TmpLayoutEdge(
                    startNode = NodeConnection.switch(inner = switchLinkYV(switch, 1), outer = null),
                    endNode = PlaceHolderNodeConnection,
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                )
            )
        val disconnectedAlignment = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(1.0, 1.0)))

        val lt1o1 = mainOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        val lt2o1 = mainOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        val lt3o1 = mainOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        val lt1d1 = mainDraftContext.copyFrom(lt1o1)
        val lt1o2 = mainOfficialContext.save(locationTrackDao.fetch(lt1o1), disconnectedAlignment)
        val lt1o3 = mainOfficialContext.save(locationTrackDao.fetch(lt1o1), connectedAlignment)

        fun tracksLinkedAtVersionSaveTime(version: LayoutRowVersion<LocationTrack>) =
            switchDao
                .findLocationTracksLinkedToSwitchAtMoment(
                    LayoutBranch.main,
                    switch,
                    locationTrackVersionChangeTime(version),
                )
                .map { it.id }
                .toSet()

        assertEquals(setOf(lt1o1.id), tracksLinkedAtVersionSaveTime(lt1o1))
        assertEquals(setOf(lt1o1.id, lt2o1.id), tracksLinkedAtVersionSaveTime(lt2o1))
        assertEquals(setOf(lt1o1.id, lt2o1.id, lt3o1.id), tracksLinkedAtVersionSaveTime(lt3o1))
        assertEquals(setOf(lt1o1.id, lt2o1.id, lt3o1.id), tracksLinkedAtVersionSaveTime(lt1d1))
        assertEquals(setOf(lt2o1.id, lt3o1.id), tracksLinkedAtVersionSaveTime(lt1o2))
        assertEquals(setOf(lt1o1.id, lt2o1.id, lt3o1.id), tracksLinkedAtVersionSaveTime(lt1o3))
    }

    @Test
    fun `findLocationTracksLinkedToSwitchAtMoment handles branch inheritance and deletions`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.save(switch()).id
        val connectedAlignment =
            trackGeometry(
                TmpLayoutEdge(
                    startNode = NodeConnection.switch(inner = switchLinkYV(switch, 1), outer = null),
                    endNode = PlaceHolderNodeConnection,
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0))),
                )
            )
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)

        fun tracksLinkedAtVersionSaveTime(version: LayoutRowVersion<LocationTrack>) =
            switchDao
                .findLocationTracksLinkedToSwitchAtMoment(designBranch, switch, locationTrackVersionChangeTime(version))
                .map { it.rowVersion }
                .toSet()

        val lt1 = designOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        val lt1MainCopy = mainOfficialContext.copyFrom(lt1)
        val lt2 = mainOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        val lt2DesignCopy = designOfficialContext.copyFrom(lt2)
        locationTrackDao.deleteRow(lt2DesignCopy.rowId)
        val lt3 = designOfficialContext.save(locationTrack(trackNumber), connectedAlignment)
        locationTrackDao.deleteRow(lt3.rowId)
        val lt2DesignRecopy = designOfficialContext.copyFrom(lt2)

        assertEquals(setOf(lt1), tracksLinkedAtVersionSaveTime(lt1))
        assertEquals(setOf(lt1), tracksLinkedAtVersionSaveTime(lt1MainCopy))
        assertEquals(setOf(lt1, lt2), tracksLinkedAtVersionSaveTime(lt2))
        assertEquals(setOf(lt1, lt2DesignCopy), tracksLinkedAtVersionSaveTime(lt2DesignCopy))
        assertEquals(setOf(lt1, lt2, lt3), tracksLinkedAtVersionSaveTime(lt3))
        assertEquals(setOf(lt1, lt2DesignRecopy), tracksLinkedAtVersionSaveTime(lt2DesignRecopy))
    }

    private fun locationTrackVersionChangeTime(version: LayoutRowVersion<LocationTrack>): Instant {
        val sql =
            """
            select change_time from layout.location_track_version
            where id = :id and layout_context_id = :layout_context_id and version = :version
        """
                .trimIndent()
        return jdbc.queryOne(
            sql,
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            ),
        ) { rs, _ ->
            rs.getInstant("change_time")
        }
    }
}
