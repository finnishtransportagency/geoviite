package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.tracklayout.LayoutState.DELETED
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import kotlin.test.assertContains
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberDaoIT
@Autowired
constructor(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val trackNumberService: LayoutTrackNumberService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun trackNumberIsStoredAndLoadedOk() {
        val original =
            LayoutTrackNumber(
                number = testDBService.getUnusedTrackNumber(),
                description = TrackNumberDescription("empty-test-track-number"),
                state = IN_USE,
                contextData = LayoutContextData.newDraft(LayoutBranch.main, id = null),
            )
        val version = trackNumberDao.save(original)
        val fromDb = trackNumberDao.fetch(version)
        assertEquals(version.id, fromDb.id)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(original, fromDb, contextMatch = false)
    }

    @Test
    fun trackNumberExternalIdIsUnique() {
        val oid = Oid<LayoutTrackNumber>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.track_number_external_id where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val tn1 = trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = false))
        val tn2 = trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = false))
        trackNumberDao.insertExternalId(tn1.id, LayoutBranch.main, oid)
        assertThrows<DuplicateKeyException> { trackNumberDao.insertExternalId(tn2.id, LayoutBranch.main, oid) }
    }

    @Test
    fun trackNumberVersioningWorks() {
        val tempTrackNumber = trackNumber(testDBService.getUnusedTrackNumber(), description = "test 1", draft = false)
        val insertVersion = trackNumberDao.save(tempTrackNumber)
        val id = insertVersion.id
        val inserted = trackNumberDao.fetch(insertVersion)
        assertMatches(tempTrackNumber, inserted, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft1 = asMainDraft(inserted).copy(description = TrackNumberDescription("test 2"))
        val draftVersion1 = trackNumberDao.save(tempDraft1)
        val draft1 = trackNumberDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft2 = draft1.copy(description = TrackNumberDescription("test 3"))
        val draftVersion2 = trackNumberDao.save(tempDraft2)
        val draft2 = trackNumberDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2, contextMatch = false)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        trackNumberDao.deleteDraft(LayoutBranch.main, id)
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, trackNumberDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, trackNumberDao.fetch(insertVersion))
        assertEquals(draft1, trackNumberDao.fetch(draftVersion1))
        assertEquals(draft2, trackNumberDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { trackNumberDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun listingTrackNumberVersionsWorks() {
        val officialVersion = mainOfficialContext.createLayoutTrackNumber()
        val undeletedDraftVersion = mainDraftContext.createLayoutTrackNumber()
        val deleteStateDraftVersion =
            mainDraftContext.save(trackNumber(number = testDBService.getUnusedTrackNumber(), state = DELETED))
        val deletedDraftId = mainDraftContext.createLayoutTrackNumber().id
        trackNumberDao.deleteDraft(LayoutBranch.main, deletedDraftId)

        val official = trackNumberDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.any { r -> r.id == deletedDraftId })

        val draftWithoutDeleted = trackNumberDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.any { r -> r.id == deletedDraftId })

        val draftWithDeleted = trackNumberDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.any { r -> r.id == deletedDraftId })
    }

    @Test
    fun `Finding versions by moment works across designs and main branch`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)

        val v0Time = trackNumberDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val tn1MainV1 = mainOfficialContext.createLayoutTrackNumber()
        val tn2DesignV1 = designOfficialContext.createLayoutTrackNumber()
        val tn3DesignV1 = designOfficialContext.createLayoutTrackNumber()
        val tn1Id = tn1MainV1.id
        val tn2Id = tn2DesignV1.id
        val tn3Id = tn3DesignV1.id
        val v1Time = trackNumberDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val tn1MainV2 = testDBService.update(tn1MainV1)
        val tn1DesignV2 = designOfficialContext.copyFrom(tn1MainV1)
        val tn2DesignV2 = testDBService.update(tn2DesignV1)
        trackNumberDao.deleteRow(tn3DesignV1.rowId)
        val v2Time = trackNumberDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        trackNumberDao.deleteRow(tn1DesignV2.rowId)
        // Fake publish: update the design as a main-official
        val tn2MainV3 = mainOfficialContext.moveFrom(tn2DesignV2)
        val v3Time = trackNumberDao.fetchChangeTime()

        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn1Id, v0Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn2Id, v0Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn3Id, v0Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn1Id, v0Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn2Id, v0Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn3Id, v0Time))

        assertEquals(tn1MainV1, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn1Id, v1Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn2Id, v1Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn3Id, v1Time))
        assertEquals(tn1MainV1, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn1Id, v1Time))
        assertEquals(tn2DesignV1, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn2Id, v1Time))
        assertEquals(tn3DesignV1, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn3Id, v1Time))

        assertEquals(tn1MainV2, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn1Id, v2Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn2Id, v2Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn3Id, v2Time))
        assertEquals(tn1DesignV2, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn1Id, v2Time))
        assertEquals(tn2DesignV2, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn2Id, v2Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn3Id, v2Time))

        assertEquals(tn1MainV2, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn1Id, v3Time))
        assertEquals(tn2MainV3, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn2Id, v3Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(LayoutBranch.main, tn3Id, v3Time))
        assertEquals(tn1MainV2, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn1Id, v3Time))
        assertEquals(tn2MainV3, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn2Id, v3Time))
        assertEquals(null, trackNumberDao.fetchOfficialVersionAtMoment(designBranch, tn3Id, v3Time))
    }

    @Test
    fun `fetchTrackNumberNames() returns name change history for a given design branch`() {
        val design1 = testDBService.createDesignBranch()
        val design2 = testDBService.createDesignBranch()
        val design1OfficialContext = testDBService.testContext(design1, OFFICIAL)
        val design1DraftContext = testDBService.testContext(design1, PublicationState.DRAFT)
        val design2OfficialContext = testDBService.testContext(design2, OFFICIAL)
        val design2DraftContext = testDBService.testContext(design2, PublicationState.DRAFT)

        // tn1 is created in main, has its name edited in both designs, then 1 is cancelled and 2 merged
        val tn1 = mainOfficialContext.save(trackNumber(TrackNumber("tn1 original"))).id
        design1OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("edited in d1")))
        design2OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("edited in d2")))
        val cancellation = trackNumberService.cancel(design1, tn1)!!
        trackNumberService.publish(design1, cancellation)
        trackNumberService.mergeToMainBranch(design2, tn1)
        trackNumberService.publish(LayoutBranch.main, mainDraftContext.fetchVersion(tn1)!!)
        trackNumberService.publish(design2, design2DraftContext.fetchVersion(tn1)!!) // finish merge
        // -> end result = only version edited in d1 remains live

        // tn2 is created in a design, then merged
        val tn2 = design1OfficialContext.save(trackNumber(TrackNumber("tn2 original"))).id
        trackNumberService.mergeToMainBranch(design1, tn2)
        mainDraftContext.save(mainDraftContext.fetch(tn2)!!.copy(number = TrackNumber("edited mid merge")))
        trackNumberService.publish(LayoutBranch.main, mainDraftContext.fetchVersion(tn2)!!)
        trackNumberService.publish(design1, design1DraftContext.fetchVersion(tn2)!!) // finish merge
        // -> end result: only merged version remains live

        val mainHistory = trackNumberDao.fetchTrackNumberNames(LayoutBranch.main)
        val design1History = trackNumberDao.fetchTrackNumberNames(design1)
        val design2History = trackNumberDao.fetchTrackNumberNames(design2)
        // for each design branch, we're only interested in the minimal list of name changes in that branch, and
        // hence list name updates by the moment they occurred.

        assertEquals(
            listOf("tn1 original", "edited in d2"),
            mainHistory.filter { it.id == tn1 }.map { it.number.toString() },
        )
        // the cancellation in d1 happened before the merge from d2, so we see main's state in the interim
        assertEquals(
            listOf("tn1 original", "edited in d1", "tn1 original", "edited in d2"),
            design1History.filter { it.id == tn1 }.map { it.number.toString() },
        )
        assertEquals(
            listOf("tn1 original", "edited in d2"),
            design2History.filter { it.id == tn1 }.map { it.number.toString() },
        )

        assertEquals(listOf("edited mid merge"), mainHistory.filter { it.id == tn2 }.map { it.number.toString() })
        assertEquals(
            listOf("tn2 original", "edited mid merge"),
            design1History.filter { it.id == tn2 }.map { it.number.toString() },
        )
        assertEquals(listOf("edited mid merge"), design2History.filter { it.id == tn2 }.map { it.number.toString() })
    }

    @Test
    fun `fetchTrackNumberNames() can deal with confusers`() {
        val design1 = testDBService.createDesignBranch()
        val design1OfficialContext = testDBService.testContext(design1, OFFICIAL)
        val design1DraftContext = testDBService.testContext(design1, PublicationState.DRAFT)

        // tn1 and tn2 are created in main, and edited multiple times both in main and in a design
        val tn1 = mainOfficialContext.save(trackNumber(TrackNumber("tn1 original"))).id
        design1OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in d1 n1")))
        val tn2 = mainOfficialContext.save(trackNumber(TrackNumber("tn2 original"))).id
        design1OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in d1 n2")))
        design1OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in d1 n3")))
        design1OfficialContext.save(mainOfficialContext.fetch(tn2)!!.copy(number = TrackNumber("tn2 edited in d1 n1")))
        mainOfficialContext.save(mainOfficialContext.fetch(tn2)!!.copy(number = TrackNumber("tn2 edited in main n1")))
        mainOfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in main n1")))
        design1OfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in d1 n4")))
        design1OfficialContext.save(mainOfficialContext.fetch(tn2)!!.copy(number = TrackNumber("tn2 edited in d1 n2")))
        mainOfficialContext.save(mainOfficialContext.fetch(tn1)!!.copy(number = TrackNumber("tn1 edited in main n2")))

        val mainHistory = trackNumberDao.fetchTrackNumberNames(LayoutBranch.main)
        val design1History = trackNumberDao.fetchTrackNumberNames(design1)

        assertEquals(
            listOf("tn1 original", "tn1 edited in main n1", "tn1 edited in main n2"),
            mainHistory.filter { it.id == tn1 }.map { it.number.toString() },
        )
        assertEquals(
            listOf("tn2 original", "tn2 edited in main n1"),
            mainHistory.filter { it.id == tn2 }.map { it.number.toString() },
        )
        assertEquals(
            listOf(
                "tn1 original",
                "tn1 edited in d1 n1",
                "tn1 edited in d1 n2",
                "tn1 edited in d1 n3",
                "tn1 edited in d1 n4",
            ),
            design1History.filter { it.id == tn1 }.map { it.number.toString() },
        )
        assertEquals(
            listOf("tn2 original", "tn2 edited in d1 n1", "tn2 edited in d1 n2"),
            design1History.filter { it.id == tn2 }.map { it.number.toString() },
        )
    }
}
