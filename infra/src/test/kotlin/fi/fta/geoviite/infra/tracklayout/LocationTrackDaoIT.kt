package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DesignLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.MAIN
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.SIDE
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.queryOne
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackDaoIT
@Autowired
constructor(
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val locationTrackDao: LocationTrackDao,
    private val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun locationTrackSaveAndLoadWorks() {
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val locationTrack =
            locationTrack(mainOfficialContext.createLayoutTrackNumber().id, alignment, draft = false)
                .copy(
                    name = AlignmentName("ORIG"),
                    descriptionBase = LocationTrackDescriptionBase("Oridinal location track"),
                    type = MAIN,
                    state = LocationTrackState.IN_USE,
                    alignmentVersion = alignmentVersion,
                )

        val inserted = locationTrackDao.insert(locationTrack)
        assertEquals(inserted.rowVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, inserted.id))
        assertEquals(inserted.rowVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, inserted.id))
        val fromDb = locationTrackDao.fetch(inserted.rowVersion)
        assertMatches(locationTrack, fromDb, contextMatch = false)
        assertEquals(inserted.id, fromDb.id)

        val updatedTrack =
            fromDb.copy(
                name = AlignmentName("UPD"),
                descriptionBase = LocationTrackDescriptionBase("Updated location track"),
                type = SIDE,
                state = LocationTrackState.NOT_IN_USE,
                topologicalConnectivity = TopologicalConnectivityType.END,
            )
        val (updatedId, updatedVersion) = locationTrackDao.update(updatedTrack)
        assertEquals(inserted.id, updatedId)
        assertEquals(inserted.rowVersion.rowId, updatedVersion.rowId)
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, inserted.id))
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, inserted.id))
        val updatedFromDb = locationTrackDao.fetch(updatedVersion)
        assertMatches(updatedTrack, updatedFromDb, contextMatch = false)
        assertEquals(inserted.id, updatedFromDb.id)
    }

    @Test
    fun locationTrackExternalIdIsUnique() {
        val oid = Oid<LocationTrack>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.location_track where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion1 = alignmentDao.insert(alignment())
        val locationTrack1 =
            locationTrack(
                trackNumberId = trackNumberId,
                externalId = oid,
                alignmentVersion = alignmentVersion1,
                draft = false,
            )
        val alignmentVersion2 = alignmentDao.insert(alignment())
        val locationTrack2 =
            locationTrack(
                trackNumberId = trackNumberId,
                externalId = oid,
                alignmentVersion = alignmentVersion2,
                draft = false,
            )

        locationTrackDao.insert(locationTrack1)
        assertThrows<DuplicateKeyException> { locationTrackDao.insert(locationTrack2) }
    }

    @Test
    fun locationTrackVersioningWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val tempAlignment = alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val alignmentVersion = alignmentDao.insert(tempAlignment)
        val tempTrack =
            locationTrack(
                trackNumberId = trackNumberId,
                name = "test1",
                alignment = tempAlignment,
                alignmentVersion = alignmentVersion,
                draft = false,
            )
        val (id, insertVersion) = locationTrackDao.insert(tempTrack)
        val inserted = locationTrackDao.fetch(insertVersion)
        assertMatches(tempTrack, inserted)
        assertEquals(id, inserted.id)
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft1 = asMainDraft(inserted).copy(name = AlignmentName("test2"))
        val (draftId1, draftVersion1) = locationTrackDao.insert(tempDraft1)
        val draft1 = locationTrackDao.fetch(draftVersion1)
        assertEquals(id, draftId1)
        assertMatches(tempDraft1, draft1)
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        val newTempAlignment = alignment(segment(Point(2.0, 2.0), Point(4.0, 4.0)))
        val newAlignmentVersion = alignmentDao.insert(newTempAlignment)
        val tempDraft2 = draft1.copy(alignmentVersion = newAlignmentVersion, length = newTempAlignment.length)
        val (draftId2, draftVersion2) = locationTrackDao.update(tempDraft2)
        val draft2 = locationTrackDao.fetch(draftVersion2)
        assertEquals(id, draftId2)
        assertMatches(tempDraft2, draft2)
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        locationTrackDao.deleteDraft(LayoutBranch.main, id)
        alignmentDao.deleteOrphanedAlignments()
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, locationTrackDao.fetch(insertVersion))
        assertEquals(draft1, locationTrackDao.fetch(draftVersion1))
        assertEquals(draft2, locationTrackDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { locationTrackDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun listingLocationTrackVersionsWorks() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val officialVersion = insertOfficialLocationTrack(tnId)
        val undeletedDraftVersion = insertDraftLocationTrack(tnId)
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, LocationTrackState.DELETED)
        val deletedDraftId = insertDraftLocationTrack(tnId).id
        locationTrackDao.deleteDraft(LayoutBranch.main, deletedDraftId)

        val official = locationTrackDao.fetchVersions(MainLayoutContext.official, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.any { r -> r.id == deletedDraftId })

        val draftWithoutDeleted = locationTrackDao.fetchVersions(MainLayoutContext.draft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.any { r -> r.id == deletedDraftId })

        val draftWithDeleted = locationTrackDao.fetchVersions(MainLayoutContext.draft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.any { r -> r.id == deletedDraftId })
    }

    @Test
    fun `Finding LocationTrack versions by moment works across designs and main branch`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, OFFICIAL)

        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val v0Time = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val (track1Id, track1MainV1) = mainOfficialContext.insert(locationTrackAndAlignment(tnId))
        val (track2Id, track2DesignV1) = designOfficialContext.insert(locationTrackAndAlignment(tnId))
        val (track3Id, track3DesignV1) = designOfficialContext.insert(locationTrackAndAlignment(tnId))
        val v1Time = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val track1MainV2 = testDBService.update(track1MainV1).rowVersion
        val track1DesignV2 = designOfficialContext.copyFrom(track1MainV1, officialRowId = track1MainV1.rowId).rowVersion
        val track2DesignV2 = testDBService.update(track2DesignV1).rowVersion
        locationTrackDao.deleteRow(track3DesignV1.rowId)
        val v2Time = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        locationTrackDao.deleteRow(track1DesignV2.rowId)
        // Fake publish: update the design as a main-official
        val track2MainV3 = mainOfficialContext.moveFrom(track2DesignV2).rowVersion
        val v3Time = locationTrackDao.fetchChangeTime()

        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track1Id, v0Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track2Id, v0Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track3Id, v0Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track1Id, v0Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track2Id, v0Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track3Id, v0Time))

        assertEquals(track1MainV1, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track1Id, v1Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track2Id, v1Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track3Id, v1Time))
        assertEquals(track1MainV1, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track1Id, v1Time))
        assertEquals(track2DesignV1, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track2Id, v1Time))
        assertEquals(track3DesignV1, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track3Id, v1Time))

        assertEquals(track1MainV2, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track1Id, v2Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track2Id, v2Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track3Id, v2Time))
        assertEquals(track1DesignV2, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track1Id, v2Time))
        assertEquals(track2DesignV2, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track2Id, v2Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track3Id, v2Time))

        assertEquals(track1MainV2, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track1Id, v3Time))
        assertEquals(track2MainV3, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track2Id, v3Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(LayoutBranch.main, track3Id, v3Time))
        assertEquals(track1MainV2, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track1Id, v3Time))
        assertEquals(track2MainV3, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track2Id, v3Time))
        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(designBranch, track3Id, v3Time))
    }

    @Test
    fun findingLocationTracksByTrackNumberWorksForOfficial() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val officialTrackVersion1 = insertOfficialLocationTrack(tnId)
        val officialTrackVersion2 = insertOfficialLocationTrack(tnId)
        val draftTrackVersion = insertDraftLocationTrack(tnId)

        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2).toSet(),
            locationTrackDao.fetchVersions(MainLayoutContext.official, false, tnId).toSet(),
        )
        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2, draftTrackVersion).toSet(),
            locationTrackDao.fetchVersions(MainLayoutContext.draft, false, tnId).toSet(),
        )
    }

    @Test
    fun findingLocationTracksByTrackNumberWorksForDraft() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val tnId2 = mainOfficialContext.createLayoutTrackNumber().id
        val undeletedDraftVersion = insertDraftLocationTrack(tnId)
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, LocationTrackState.DELETED)
        val changeTrackNumberOriginal = insertOfficialLocationTrack(tnId)
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal.rowVersion, tnId2)
        val deletedDraftId = insertDraftLocationTrack(tnId).id
        locationTrackDao.deleteDraft(LayoutBranch.main, deletedDraftId)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            locationTrackDao.fetchVersions(MainLayoutContext.official, false, tnId),
        )
        assertEquals(
            listOf(undeletedDraftVersion),
            locationTrackDao.fetchVersions(MainLayoutContext.draft, false, tnId),
        )

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            locationTrackDao.fetchVersions(MainLayoutContext.draft, true, tnId).toSet(),
        )
        assertEquals(
            listOf(changeTrackNumberChanged),
            locationTrackDao.fetchVersions(MainLayoutContext.draft, true, tnId2),
        )
    }

    @Test
    fun `Fetching official location tracks with empty id list works`() {
        val expected = locationTrackDao.fetchVersions(MainLayoutContext.official, emptyList())
        assertEquals(expected.size, 0)
    }

    @Test
    fun `Fetching multiple official location tracks works`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrack1 = insertOfficialLocationTrack(tnId)
        val locationTrack2 = insertOfficialLocationTrack(tnId)

        val expected =
            locationTrackDao.fetchVersions(MainLayoutContext.official, listOf(locationTrack1.id, locationTrack2.id))
        assertEquals(expected.size, 2)
        assertContains(expected, locationTrack1)
        assertContains(expected, locationTrack2)
    }

    @Test
    fun `Fetching draft location tracks with empty id list works`() {
        val expected = locationTrackDao.fetchVersions(MainLayoutContext.draft, emptyList())
        assertEquals(expected.size, 0)
    }

    @Test
    fun `Fetching multiple draft location tracks works`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrack1 = insertDraftLocationTrack(tnId)
        val locationTrack2 = insertDraftLocationTrack(tnId)

        val expected =
            locationTrackDao.fetchVersions(MainLayoutContext.draft, listOf(locationTrack1.id, locationTrack2.id))
        assertEquals(expected.size, 2)
        assertContains(expected, locationTrack1)
        assertContains(expected, locationTrack2)
    }

    @Test
    fun `Fetching missing location tracks only returns those that exist`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrack1 = insertOfficialLocationTrack(tnId)
        val locationTrack2 = insertOfficialLocationTrack(tnId)
        val draftOnly = insertDraftLocationTrack(tnId)
        val entirelyMissing = IntId<LocationTrack>(0)

        val res =
            locationTrackDao.fetchVersions(
                MainLayoutContext.official,
                listOf(locationTrack1.id, locationTrack2.id, draftOnly.id, entirelyMissing),
            )
        assertEquals(res.size, 2)
        assertContains(res, locationTrack1)
        assertContains(res, locationTrack2)
    }

    @Test
    fun `different layout contexts work for objects initialized in official context`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val officialVersion =
            locationTrackDao.insert(
                locationTrack(tnId, name = "official", draft = false, alignmentVersion = alignmentVersion)
            )
        val official = locationTrackDao.fetch(officialVersion.rowVersion)
        locationTrackDao.insert(asMainDraft(official.copy(name = AlignmentName("draft"))))
        val bothDesign = layoutDesignDao.insert(layoutDesign("both"))
        val bothDesignInitialDesignDraftFromOfficial =
            locationTrackDao
                .insert(asDesignDraft(official.copy(name = AlignmentName("design-official both")), bothDesign))
                .rowVersion
        val updatedToDesignOfficial =
            locationTrackDao
                .update(asDesignOfficial(locationTrackDao.fetch(bothDesignInitialDesignDraftFromOfficial), bothDesign))
                .rowVersion
        val bothDesignDesignDraftVersion =
            locationTrackDao
                .insert(
                    asDesignDraft(
                        locationTrackDao.fetch(updatedToDesignOfficial).copy(name = AlignmentName("design-draft both")),
                        bothDesign,
                    )
                )
                .rowVersion
        val onlyDraftDesign = layoutDesignDao.insert(layoutDesign("onlyDraft"))
        locationTrackDao
            .insert(asDesignDraft(official.copy(name = AlignmentName("design-draft onlyDraft")), onlyDraftDesign))
            .rowVersion

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf("official"), contextTracks(MainLayoutContext.official))
        assertEquals(setOf("draft"), contextTracks(MainLayoutContext.draft))
        assertEquals(setOf("design-official both"), contextTracks(DesignLayoutContext.of(bothDesign, OFFICIAL)))
        assertEquals(
            setOf("design-draft both"),
            contextTracks(DesignLayoutContext.of(bothDesign, PublicationState.DRAFT)),
        )

        assertEquals(
            setOf("design-draft onlyDraft"),
            contextTracks(DesignLayoutContext.of(onlyDraftDesign, PublicationState.DRAFT)),
        )

        assertChangeInfo(
            officialVersion.rowVersion,
            bothDesignDesignDraftVersion,
            locationTrackDao.fetchLayoutAssetChangeInfo(
                DesignLayoutContext.of(bothDesign, PublicationState.DRAFT),
                officialVersion.id,
            )!!,
        )
    }

    @Test
    fun `different layout contexts work for objects initialized in design context`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val bothDesign = layoutDesignDao.insert(layoutDesign("both"))
        val initialVersion =
            locationTrackDao.insert(
                locationTrack(
                    tnId,
                    name = "design-official",
                    contextData = LayoutContextData.newDraft(DesignBranch.of(bothDesign)),
                    alignmentVersion = alignmentVersion,
                )
            )
        val bothDesignNowOfficial =
            locationTrackDao
                .update(asDesignOfficial(locationTrackDao.fetch(initialVersion.rowVersion), bothDesign))
                .rowVersion
        val lastDesignDraftVersion =
            locationTrackDao
                .insert(
                    asDesignDraft(
                        locationTrackDao.fetch(bothDesignNowOfficial).copy(name = AlignmentName("design-draft")),
                        bothDesign,
                    )
                )
                .rowVersion

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf("design-official"), contextTracks(DesignLayoutContext.of(bothDesign, OFFICIAL)))
        assertEquals(setOf("design-draft"), contextTracks(DesignLayoutContext.of(bothDesign, PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertChangeInfo(
            initialVersion.rowVersion,
            lastDesignDraftVersion,
            locationTrackDao.fetchLayoutAssetChangeInfo(
                DesignLayoutContext.of(bothDesign, PublicationState.DRAFT),
                initialVersion.id,
            )!!,
        )
    }

    @Test
    fun `different layout contexts work with only an official row`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao
            .insert(locationTrack(tnId, name = "official", draft = false, alignmentVersion = alignmentVersion))
            .rowVersion

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf("official"), contextTracks(DesignLayoutContext.of(someDesign, OFFICIAL)))
        assertEquals(setOf("official"), contextTracks(DesignLayoutContext.of(someDesign, PublicationState.DRAFT)))
        assertEquals(setOf("official"), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertEquals(setOf("official"), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
    }

    @Test
    fun `different layout contexts work with only a draft row`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao
            .insert(locationTrack(tnId, name = "draft", draft = true, alignmentVersion = alignmentVersion))
            .rowVersion

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf<String>(), contextTracks(DesignLayoutContext.of(someDesign, OFFICIAL)))
        assertEquals(setOf<String>(), contextTracks(DesignLayoutContext.of(someDesign, PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertEquals(setOf("draft"), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
    }

    @Test
    fun `different layout contexts work with only a design-draft row`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao
            .insert(
                locationTrack(
                    tnId,
                    name = "design-draft",
                    contextData = LayoutContextData.newDraft(DesignBranch.of(someDesign)),
                    alignmentVersion = alignmentVersion,
                )
            )
            .rowVersion

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf<String>(), contextTracks(DesignLayoutContext.of(someDesign, OFFICIAL)))
        assertEquals(setOf("design-draft"), contextTracks(DesignLayoutContext.of(someDesign, PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
    }

    @Test
    fun `different layout contexts work with only a main-official and design-official row`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val alignmentVersion = alignmentDao.insert(alignment())
        val someDesignId = layoutDesignDao.insert(layoutDesign("some design"))
        val official =
            locationTrackDao.insert(
                locationTrack(tnId, name = "official", draft = false, alignmentVersion = alignmentVersion)
            )
        val designDraftVersion =
            locationTrackDao
                .insert(
                    asDesignDraft(
                        locationTrackDao.fetch(official.rowVersion).copy(name = AlignmentName("design-official")),
                        someDesignId,
                    )
                )
                .rowVersion
        locationTrackDao.update(asDesignOfficial(locationTrackDao.fetch(designDraftVersion), someDesignId))

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf("design-official"), contextTracks(DesignLayoutContext.of(someDesignId, OFFICIAL)))
        assertEquals(
            setOf("design-official"),
            contextTracks(DesignLayoutContext.of(someDesignId, PublicationState.DRAFT)),
        )
        assertEquals(setOf("official"), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertEquals(setOf("official"), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
        assertChangeInfo(
            official.rowVersion,
            designDraftVersion,
            locationTrackDao.fetchLayoutAssetChangeInfo(
                DesignLayoutContext.of(someDesignId, PublicationState.DRAFT),
                official.id,
            )!!,
        )
    }

    @Test
    fun `fetchVersionsNear() returns only the correct context's version when multiple are visible`() {
        testDBService.clearLayoutTables()
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val officialTrackVersion =
            mainOfficialContext
                .insert(locationTrack(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .rowVersion
        val draftTrackVersion =
            mainDraftContext
                .insert(
                    asMainDraft(locationTrackDao.fetch(officialTrackVersion)),
                    alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                )
                .rowVersion
        assertEquals(
            listOf(draftTrackVersion),
            locationTrackDao.fetchVersionsNear(MainLayoutContext.draft, boundingBoxAroundPoint(Point(0.0, 0.0), 1.0)),
        )
        assertEquals(
            listOf(officialTrackVersion),
            locationTrackDao.fetchVersionsNear(MainLayoutContext.official, boundingBoxAroundPoint(Point(0.0, 0.0), 1.0)),
        )
    }

    @Test
    fun `fetchVersionsNear() can return deleted and filter by trackNumberId`() {
        testDBService.clearLayoutTables()
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val deletedTrack =
            mainOfficialContext
                .insert(
                    locationTrack(trackNumber, state = LocationTrackState.DELETED),
                    alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                )
                .rowVersion
        val anotherTrackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val onAnotherTrackNumber =
            mainOfficialContext
                .insert(locationTrack(anotherTrackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .rowVersion
        assertEquals(
            emptyList<LayoutRowVersion<LocationTrack>>(),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.official,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                trackNumberId = trackNumber,
            ),
        )
        assertEquals(
            listOf(deletedTrack),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.official,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                includeDeleted = true,
                trackNumberId = trackNumber,
            ),
        )
        assertEquals(
            listOf(onAnotherTrackNumber),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.official,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                trackNumberId = anotherTrackNumber,
            ),
        )
    }

    @Test
    fun `fetchVersionsNear() returns only the correct context's version when track is moved`() {
        testDBService.clearLayoutTables()
        val trackNumber = mainOfficialContext.insert(trackNumber()).id
        val officialTrackVersion =
            mainOfficialContext
                .insert(locationTrack(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .rowVersion
        val draftTrackVersion =
            mainDraftContext
                .insert(
                    asMainDraft(locationTrackDao.fetch(officialTrackVersion)),
                    alignment(segment(Point(0.0, 10.0), Point(10.0, 10.0))),
                )
                .rowVersion
        assertEquals(
            listOf<LayoutRowVersion<LocationTrack>>(),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.official,
                boundingBoxAroundPoint(Point(0.0, 10.0), 1.0),
                false,
                null,
            ),
        )
        assertEquals(
            listOf(draftTrackVersion),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.draft,
                boundingBoxAroundPoint(Point(0.0, 10.0), 1.0),
                false,
                null,
            ),
        )
        assertEquals(
            listOf(officialTrackVersion),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.official,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                false,
                null,
            ),
        )
        assertEquals(
            listOf<LayoutRowVersion<LocationTrack>>(),
            locationTrackDao.fetchVersionsNear(
                MainLayoutContext.draft,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                false,
                null,
            ),
        )
    }

    private fun assertChangeInfo(
        originalVersion: LayoutRowVersion<LocationTrack>,
        contextVersion: LayoutRowVersion<LocationTrack>?,
        changeInfo: LayoutAssetChangeInfo,
    ) {
        val originalVersionCreationTime = getChangeTime(originalVersion)
        val contextVersionChangeTime = contextVersion?.let(::getChangeTime)
        assertEquals(originalVersionCreationTime, changeInfo.created, "created time")
        assertEquals(contextVersionChangeTime, changeInfo.changed, "changed time")
    }

    private fun getChangeTime(version: LayoutRowVersion<LocationTrack>) =
        jdbc.queryOne(
            "select change_time from layout.location_track_version where id = :id and version = :version",
            mapOf("id" to version.rowId.intValue, "version" to version.version),
        ) { rs, _ ->
            rs.getInstant("change_time")
        }

    private fun getTrackNameSetByLayoutContextAndTrackNumber(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): Set<String> {
        val names =
            locationTrackDao.list(layoutContext, includeDeleted = false, trackNumberId = trackNumberId).map {
                it.name.toString()
            }
        val nameSet = names.toSet()
        assertEquals(names.size, nameSet.size)
        return nameSet
    }

    private fun insertOfficialLocationTrack(tnId: IntId<TrackLayoutTrackNumber>): LayoutDaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(tnId, draft = false)
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(track.copy(alignmentVersion = alignmentVersion))
    }

    private fun insertDraftLocationTrack(
        tnId: IntId<TrackLayoutTrackNumber>,
        state: LocationTrackState = LocationTrackState.IN_USE,
    ): LayoutDaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(trackNumberId = tnId, state = state, draft = true)
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(track.copy(alignmentVersion = alignmentVersion))
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: LayoutRowVersion<LocationTrack>,
        newTrackNumber: IntId<TrackLayoutTrackNumber>,
    ): LayoutDaoResponse<LocationTrack> {
        val track = locationTrackDao.fetch(trackVersion)
        assertFalse(track.isDraft)
        val alignmentVersion = alignmentService.duplicate(track.alignmentVersion!!)
        return locationTrackDao.insert(
            asMainDraft(track).copy(alignmentVersion = alignmentVersion, trackNumberId = newTrackNumber)
        )
    }
}
