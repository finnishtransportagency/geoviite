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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertFalse

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackDaoIT
@Autowired
constructor(
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
        val locationTrack =
            locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false)
                .copy(
                    name = AlignmentName("ORIG"),
                    descriptionBase = LocationTrackDescriptionBase("Oridinal location track"),
                    type = MAIN,
                    state = LocationTrackState.IN_USE,
                )

        val inserted = locationTrackDao.save(locationTrack, TmpLocationTrackGeometry.empty)
        assertEquals(inserted, locationTrackDao.fetchVersion(MainLayoutContext.official, inserted.id))
        assertEquals(inserted, locationTrackDao.fetchVersion(MainLayoutContext.draft, inserted.id))
        val fromDb = locationTrackDao.fetch(inserted)
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
        val updatedVersion = locationTrackDao.save(updatedTrack, TmpLocationTrackGeometry.empty)
        val updatedId = updatedVersion.id
        assertEquals(inserted.id, updatedId)
        assertEquals(inserted.rowId, updatedVersion.rowId)
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
            val deleteSql = "delete from layout.location_track_external_id where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val locationTrack1 =
            locationTrackDao
                .save(locationTrack(trackNumberId = trackNumberId, draft = false), TmpLocationTrackGeometry.empty)
                .id
        val locationTrack2 =
            locationTrackDao
                .save(locationTrack(trackNumberId = trackNumberId, draft = false), TmpLocationTrackGeometry.empty)
                .id

        locationTrackDao.insertExternalId(locationTrack1, LayoutBranch.main, oid)
        assertThrows<DuplicateKeyException> {
            locationTrackDao.insertExternalId(locationTrack2, LayoutBranch.main, oid)
        }
    }

    @Test
    fun locationTrackVersioningWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val tempGeometry = trackGeometryOfSegments(segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val tempTrack =
            locationTrack(trackNumberId = trackNumberId, geometry = tempGeometry, name = "test1", draft = false)
        val insertVersion = locationTrackDao.save(tempTrack, tempGeometry)
        val id = insertVersion.id
        val inserted = locationTrackDao.fetch(insertVersion)
        assertEquals(tempTrack.segmentCount, inserted.segmentCount)
        assertMatches(tempTrack, inserted)
        assertEquals(id, inserted.id)
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))
        assertMatches(tempGeometry, alignmentDao.fetch(insertVersion))

        val tempDraft1 = asMainDraft(inserted).copy(name = AlignmentName("test2"))
        val draftVersion1 = locationTrackDao.save(tempDraft1, tempGeometry)
        val draftId1 = draftVersion1.id
        val draft1 = locationTrackDao.fetch(draftVersion1)
        assertEquals(id, draftId1)
        assertMatches(tempDraft1, draft1)
        assertMatches(tempGeometry, alignmentDao.fetch(draftVersion1))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        val newTempGeometry = trackGeometryOfSegments(segment(Point(2.0, 2.0), Point(4.0, 4.0)))
        val draftVersion2 = locationTrackDao.save(draft1, newTempGeometry)
        val draftId2 = draftVersion2.id
        val draft2 = locationTrackDao.fetch(draftVersion2)
        assertEquals(id, draftId2)
        assertMatches(
            draft1.copy(length = newTempGeometry.length, segmentCount = newTempGeometry.segments.size),
            draft2,
        )
        assertMatches(newTempGeometry, alignmentDao.fetch(draftVersion2))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        locationTrackDao.deleteDraft(LayoutBranch.main, id)
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, locationTrackDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, locationTrackDao.fetch(insertVersion))
        assertEquals(draft1, locationTrackDao.fetch(draftVersion1))
        assertEquals(draft2, locationTrackDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { locationTrackDao.fetch(draftVersion2.next()) }
        assertThrows<NoSuchEntityException> { alignmentDao.fetch(draftVersion2.next()) }
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

        val track1MainV1 = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(tnId))
        val track2DesignV1 = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(tnId))
        val track3DesignV1 = designOfficialContext.saveLocationTrack(locationTrackAndGeometry(tnId))
        val track1Id = track1MainV1.id
        val track2Id = track2DesignV1.id
        val track3Id = track3DesignV1.id
        val v1Time = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        val track1MainV2 = testDBService.update(track1MainV1)
        val track1DesignV2 = designOfficialContext.copyFrom(track1MainV1)
        val track2DesignV2 = testDBService.update(track2DesignV1)
        locationTrackDao.deleteRow(track3DesignV1.rowId)
        val v2Time = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps

        locationTrackDao.deleteRow(track1DesignV2.rowId)
        // Fake publish: update the design as a main-official
        val track2MainV3 = mainOfficialContext.moveFrom(track2DesignV2)
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
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal, tnId2)
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
    fun `Fetching many shuffled official location tracks works`() {
        val tnId = mainOfficialContext.createLayoutTrackNumber().id
        val ids = List(10) { insertOfficialLocationTrack(tnId).id }.shuffled(Random(123))
        val versions = locationTrackDao.fetchVersions(MainLayoutContext.official, ids)
        assertEquals(ids, versions.map { it.id })
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
        val officialVersion =
            locationTrackDao.save(locationTrack(tnId, name = "official", draft = false), TmpLocationTrackGeometry.empty)
        val official = locationTrackDao.fetch(officialVersion)
        locationTrackDao.save(asMainDraft(official.copy(name = AlignmentName("draft"))), TmpLocationTrackGeometry.empty)
        val bothDesign = layoutDesignDao.insert(layoutDesign("both"))
        val bothDesignInitialDesignDraftFromOfficial =
            locationTrackDao.save(
                asDesignDraft(official.copy(name = AlignmentName("design-official both")), bothDesign),
                TmpLocationTrackGeometry.empty,
            )
        val updatedToDesignOfficial =
            locationTrackDao.save(
                asDesignOfficial(locationTrackDao.fetch(bothDesignInitialDesignDraftFromOfficial), bothDesign),
                TmpLocationTrackGeometry.empty,
            )
        val bothDesignDesignDraftVersion =
            locationTrackDao.save(
                asDesignDraft(
                    locationTrackDao.fetch(updatedToDesignOfficial).copy(name = AlignmentName("design-draft both")),
                    bothDesign,
                ),
                TmpLocationTrackGeometry.empty,
            )
        val onlyDraftDesign = layoutDesignDao.insert(layoutDesign("onlyDraft"))
        locationTrackDao.save(
            asDesignDraft(official.copy(name = AlignmentName("design-draft onlyDraft")), onlyDraftDesign),
            TmpLocationTrackGeometry.empty,
        )

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
            officialVersion,
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
        val bothDesign = layoutDesignDao.insert(layoutDesign("both"))
        val initialVersion =
            locationTrackDao.save(
                locationTrack(
                    tnId,
                    name = "design-official",
                    contextData = LayoutContextData.newDraft(DesignBranch.of(bothDesign), id = null),
                ),
                TmpLocationTrackGeometry.empty,
            )
        val bothDesignNowOfficial =
            locationTrackDao.save(
                asDesignOfficial(locationTrackDao.fetch(initialVersion), bothDesign),
                TmpLocationTrackGeometry.empty,
            )
        val lastDesignDraftVersion =
            locationTrackDao.save(
                asDesignDraft(
                    locationTrackDao.fetch(bothDesignNowOfficial).copy(name = AlignmentName("design-draft")),
                    bothDesign,
                ),
                TmpLocationTrackGeometry.empty,
            )

        fun contextTracks(layoutContext: LayoutContext) =
            getTrackNameSetByLayoutContextAndTrackNumber(layoutContext, tnId)

        assertEquals(setOf("design-official"), contextTracks(DesignLayoutContext.of(bothDesign, OFFICIAL)))
        assertEquals(setOf("design-draft"), contextTracks(DesignLayoutContext.of(bothDesign, PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(PublicationState.DRAFT)))
        assertEquals(setOf<String>(), contextTracks(MainLayoutContext.of(OFFICIAL)))
        assertChangeInfo(
            initialVersion,
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
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao.save(locationTrack(tnId, name = "official", draft = false), TmpLocationTrackGeometry.empty)

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
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao.save(locationTrack(tnId, name = "draft", draft = true), TmpLocationTrackGeometry.empty)

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
        val someDesign = layoutDesignDao.insert(layoutDesign("some design"))
        locationTrackDao.save(
            locationTrack(
                tnId,
                name = "design-draft",
                contextData = LayoutContextData.newDraft(DesignBranch.of(someDesign), id = null),
            ),
            TmpLocationTrackGeometry.empty,
        )

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
        val someDesignId = layoutDesignDao.insert(layoutDesign("some design"))
        val official =
            locationTrackDao.save(locationTrack(tnId, name = "official", draft = false), TmpLocationTrackGeometry.empty)
        val designDraftVersion =
            locationTrackDao.save(
                asDesignDraft(
                    locationTrackDao.fetch(official).copy(name = AlignmentName("design-official")),
                    someDesignId,
                ),
                TmpLocationTrackGeometry.empty,
            )
        locationTrackDao.save(
            asDesignOfficial(locationTrackDao.fetch(designDraftVersion), someDesignId),
            TmpLocationTrackGeometry.empty,
        )

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
            official,
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
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val officialTrackVersion =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val draftTrackVersion =
            mainDraftContext.save(
                asMainDraft(locationTrackDao.fetch(officialTrackVersion)),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
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
            mainOfficialContext.save(
                locationTrack(trackNumber, state = LocationTrackState.DELETED),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val anotherTrackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val onAnotherTrackNumber =
            mainOfficialContext.save(
                locationTrack(anotherTrackNumber),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
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
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val officialTrackVersion =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val draftTrackVersion =
            mainDraftContext.save(
                asMainDraft(locationTrackDao.fetch(officialTrackVersion)),
                trackGeometryOfSegments(segment(Point(0.0, 10.0), Point(10.0, 10.0))),
            )
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
            "select change_time from layout.location_track_version where id = :id and layout_context_id = :layout_context_id and version = :version",
            mapOf(
                "id" to version.id.intValue,
                "layout_context_id" to version.context.toSqlString(),
                "version" to version.version,
            ),
        ) { rs, _ ->
            rs.getInstant("change_time")
        }

    private fun getTrackNameSetByLayoutContextAndTrackNumber(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): Set<String> {
        val names =
            locationTrackDao.list(layoutContext, includeDeleted = false, trackNumberId = trackNumberId).map {
                it.name.toString()
            }
        val nameSet = names.toSet()
        assertEquals(names.size, nameSet.size)
        return nameSet
    }

    private fun insertOfficialLocationTrack(tnId: IntId<LayoutTrackNumber>): LayoutRowVersion<LocationTrack> {
        val (track, geometry) = locationTrackAndGeometry(tnId, draft = false)
        return locationTrackDao.save(track, geometry)
    }

    private fun insertDraftLocationTrack(
        tnId: IntId<LayoutTrackNumber>,
        state: LocationTrackState = LocationTrackState.IN_USE,
    ): LayoutRowVersion<LocationTrack> {
        val (track, geometry) = locationTrackAndGeometry(trackNumberId = tnId, state = state, draft = true)
        return locationTrackDao.save(track, geometry)
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: LayoutRowVersion<LocationTrack>,
        newTrackNumber: IntId<LayoutTrackNumber>,
    ): LayoutRowVersion<LocationTrack> {
        val track = locationTrackDao.fetch(trackVersion)
        assertFalse(track.isDraft)
        return locationTrackDao.save(
            asMainDraft(track).copy(trackNumberId = newTrackNumber),
            alignmentDao.fetch(trackVersion),
        )
    }
}
