package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.mainDraft
import fi.fta.geoviite.infra.common.mainOfficial
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.MAIN
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.SIDE
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertFalse

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackDaoIT @Autowired constructor(
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    @Test
    fun locationTrackSaveAndLoadWorks() {
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val locationTrack = locationTrack(insertOfficialTrackNumber(), alignment, draft = false).copy(
            name = AlignmentName("ORIG"),
            descriptionBase = FreeText("Oridinal location track"),
            type = MAIN,
            state = LocationTrackState.IN_USE,
            alignmentVersion = alignmentVersion,
        )

        val (id, version) = locationTrackDao.insert(locationTrack)
        assertEquals(version, locationTrackDao.fetchVersion(id, PublicationState.OFFICIAL))
        assertEquals(version, locationTrackDao.fetchVersion(id, PublicationState.DRAFT))
        val fromDb = locationTrackDao.fetch(version)
        assertMatches(locationTrack, fromDb, contextMatch = false)
        assertEquals(id, fromDb.id)

        val updatedTrack = fromDb.copy(
            name = AlignmentName("UPD"),
            descriptionBase = FreeText("Updated location track"),
            type = SIDE,
            state = LocationTrackState.NOT_IN_USE,
            topologicalConnectivity = TopologicalConnectivityType.END
        )
        val (updatedId, updatedVersion) = locationTrackDao.update(updatedTrack)
        assertEquals(id, updatedId)
        assertEquals(version.id, updatedVersion.id)
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, PublicationState.OFFICIAL))
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, PublicationState.DRAFT))
        val updatedFromDb = locationTrackDao.fetch(updatedVersion)
        assertMatches(updatedTrack, updatedFromDb, contextMatch = false)
        assertEquals(id, updatedFromDb.id)
    }

    @Test
    fun locationTrackExternalIdIsUnique() {
        val oid = Oid<LocationTrack>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.location_track where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val trackNumberId = insertOfficialTrackNumber()
        val alignmentVersion1 = alignmentDao.insert(alignment())
        val locationTrack1 = locationTrack(
            trackNumberId = trackNumberId,
            externalId = oid,
            alignmentVersion = alignmentVersion1,
            draft = false,
        )
        val alignmentVersion2 = alignmentDao.insert(alignment())
        val locationTrack2 = locationTrack(
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
        val trackNumberId = insertOfficialTrackNumber()
        val tempAlignment = alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val alignmentVersion = alignmentDao.insert(tempAlignment)
        val tempTrack = locationTrack(
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
        assertEquals(VersionPair(insertVersion, null), locationTrackDao.fetchVersionPair(id))

        val tempDraft1 = asMainDraft(inserted).copy(name = AlignmentName("test2"))
        val (draftId1, draftVersion1) = locationTrackDao.insert(tempDraft1)
        val draft1 = locationTrackDao.fetch(draftVersion1)
        assertEquals(id, draftId1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), locationTrackDao.fetchVersionPair(id))

        val newTempAlignment = alignment(segment(Point(2.0, 2.0), Point(4.0, 4.0)))
        val newAlignmentVersion = alignmentDao.insert(newTempAlignment)
        val tempDraft2 = draft1.copy(alignmentVersion = newAlignmentVersion, length = newTempAlignment.length)
        val (draftId2, draftVersion2) = locationTrackDao.update(tempDraft2)
        val draft2 = locationTrackDao.fetch(draftVersion2)
        assertEquals(id, draftId2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), locationTrackDao.fetchVersionPair(id))

        locationTrackDao.deleteDraft(id)
        alignmentDao.deleteOrphanedAlignments()
        assertEquals(VersionPair(insertVersion, null), locationTrackDao.fetchVersionPair(id))

        assertEquals(inserted, locationTrackDao.fetch(insertVersion))
        assertEquals(draft1, locationTrackDao.fetch(draftVersion1))
        assertEquals(draft2, locationTrackDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { locationTrackDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun listingLocationTrackVersionsWorks() {
        val tnId = insertOfficialTrackNumber()
        val officialVersion = insertOfficialLocationTrack(tnId).rowVersion
        val undeletedDraftVersion = insertDraftLocationTrack(tnId).rowVersion
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, LocationTrackState.DELETED).rowVersion
        val (deletedDraftId, deletedDraftVersion) = insertDraftLocationTrack(tnId)
        locationTrackDao.deleteDraft(deletedDraftId)

        val official = locationTrackDao.fetchVersions(mainOfficial, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = locationTrackDao.fetchVersions(mainDraft, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = locationTrackDao.fetchVersions(mainDraft, true)
        assertContains(draftWithDeleted, undeletedDraftVersion)
        assertContains(draftWithDeleted, deleteStateDraftVersion)
        assertFalse(draftWithDeleted.contains(deletedDraftVersion))
    }

    @Test
    fun fetchOfficialVersionByMomentWorks() {
        val tnId = insertOfficialTrackNumber()
        val beforeCreationTime = locationTrackDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val (id, firstVersion) = insertOfficialLocationTrack(tnId)
        val firstVersionTime = locationTrackDao.fetchChangeTime()

        Thread.sleep(1) // Ensure that they get different timestamps
        val updatedVersion = updateOfficial(firstVersion).rowVersion
        val updatedVersionTime = locationTrackDao.fetchChangeTime()

        assertEquals(null, locationTrackDao.fetchOfficialVersionAtMoment(id, beforeCreationTime))
        assertEquals(firstVersion, locationTrackDao.fetchOfficialVersionAtMoment(id, firstVersionTime))
        assertEquals(updatedVersion, locationTrackDao.fetchOfficialVersionAtMoment(id, updatedVersionTime))
    }

    @Test
    fun findingLocationTracksByTrackNumberWorksForOfficial() {
        val tnId = insertOfficialTrackNumber()
        val officialTrackVersion1 = insertOfficialLocationTrack(tnId).rowVersion
        val officialTrackVersion2 = insertOfficialLocationTrack(tnId).rowVersion
        val draftTrackVersion = insertDraftLocationTrack(tnId).rowVersion

        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2).toSet(),
            locationTrackDao.fetchVersions(mainOfficial, false, tnId).toSet(),
        )
        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2, draftTrackVersion).toSet(),
            locationTrackDao.fetchVersions(mainDraft, false, tnId).toSet(),
        )
    }

    @Test
    fun findingLocationTracksByTrackNumberWorksForDraft() {
        val tnId = insertOfficialTrackNumber()
        val tnId2 = insertOfficialTrackNumber()
        val undeletedDraftVersion = insertDraftLocationTrack(tnId).rowVersion
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, LocationTrackState.DELETED).rowVersion
        val changeTrackNumberOriginal = insertOfficialLocationTrack(tnId).rowVersion
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal, tnId2).rowVersion
        val deletedDraftId = insertDraftLocationTrack(tnId).id
        locationTrackDao.deleteDraft(deletedDraftId)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            locationTrackDao.fetchVersions(mainOfficial, false, tnId),
        )
        assertEquals(
            listOf(undeletedDraftVersion),
            locationTrackDao.fetchVersions(mainDraft, false, tnId),
        )

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            locationTrackDao.fetchVersions(mainDraft, true, tnId).toSet(),
        )
        assertEquals(
            listOf(changeTrackNumberChanged),
            locationTrackDao.fetchVersions(mainDraft, true, tnId2),
        )
    }

    @Test
    fun `Fetching official location tracks with empty id list works`() {
        val expected = locationTrackDao.fetchOfficialVersions(emptyList())
        assertEquals(expected.size, 0)
    }

    @Test
    fun `Fetching multiple official location tracks works`() {
        val tnId = insertOfficialTrackNumber()
        val locationTrack1 = insertOfficialLocationTrack(tnId).rowVersion
        val locationTrack2 = insertOfficialLocationTrack(tnId).rowVersion

        val expected = locationTrackDao.fetchOfficialVersions(listOf(locationTrack1.id, locationTrack2.id))
        assertEquals(expected.size, 2)
        assertContains(expected, locationTrack1)
        assertContains(expected, locationTrack2)
    }

    @Test
    fun `Fetching draft location tracks with empty id list works`() {
        val expected = locationTrackDao.fetchDraftVersions(emptyList())
        assertEquals(expected.size, 0)
    }

    @Test
    fun `Fetching multiple draft location tracks works`() {
        val tnId = insertOfficialTrackNumber()
        val locationTrack1 = insertDraftLocationTrack(tnId).rowVersion
        val locationTrack2 = insertDraftLocationTrack(tnId).rowVersion

        val expected = locationTrackDao.fetchDraftVersions(listOf(locationTrack1.id, locationTrack2.id))
        assertEquals(expected.size, 2)
        assertContains(expected, locationTrack1)
        assertContains(expected, locationTrack2)
    }

    @Test
    fun `Fetching missing location tracks only returns those that exist`() {
        val tnId = insertOfficialTrackNumber()
        val locationTrack1 = insertOfficialLocationTrack(tnId).rowVersion
        val locationTrack2 = insertOfficialLocationTrack(tnId).rowVersion
        val draftOnly = insertDraftLocationTrack(tnId).rowVersion
        val entirelyMissing = IntId<LocationTrack>(0)

        val res = locationTrackDao.fetchOfficialVersions(
            listOf(locationTrack1.id, locationTrack2.id, draftOnly.id, entirelyMissing)
        )
        assertEquals(res.size, 2)
        assertContains(res, locationTrack1)
        assertContains(res, locationTrack2)
    }

    private fun insertOfficialLocationTrack(tnId: IntId<TrackLayoutTrackNumber>): DaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(tnId, draft = false)
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(track.copy(alignmentVersion = alignmentVersion))
    }

    private fun insertDraftLocationTrack(
        tnId: IntId<TrackLayoutTrackNumber>,
        state: LocationTrackState = LocationTrackState.IN_USE,
    ): DaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(
            trackNumberId = tnId,
            state = state,
            draft = true,
        )
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(track.copy(alignmentVersion = alignmentVersion))
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: RowVersion<LocationTrack>,
        newTrackNumber: IntId<TrackLayoutTrackNumber>,
    ): DaoResponse<LocationTrack> {
        val track = locationTrackDao.fetch(trackVersion)
        assertFalse(track.isDraft)
        val alignmentVersion = alignmentService.duplicate(track.alignmentVersion!!)
        return locationTrackDao.insert(
            asMainDraft(track).copy(
                alignmentVersion = alignmentVersion,
                trackNumberId = newTrackNumber,
            )
        )
    }

    private fun updateOfficial(originalVersion: RowVersion<LocationTrack>): DaoResponse<LocationTrack> {
        val original = locationTrackDao.fetch(originalVersion)
        assertFalse(original.isDraft)
        return locationTrackDao.update(original.copy(descriptionBase = original.descriptionBase + "_update"))
    }
}
