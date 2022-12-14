package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.MAIN
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.SIDE
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackDaoIT @Autowired constructor(
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val locationTrackDao: LocationTrackDao,
): ITTestBase() {

    @Test
    fun locationTrackSaveAndLoadWorks() {
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val locationTrack = locationTrack(insertOfficialTrackNumber(), alignment).copy(
            name = AlignmentName("ORIG"),
            description = FreeText("Oridinal location track"),
            type = MAIN,
            state = IN_USE,
            alignmentVersion = alignmentVersion,
        )

        val (id, version) = locationTrackDao.insert(locationTrack)
        assertEquals(version, locationTrackDao.fetchVersion(id, OFFICIAL))
        assertEquals(version, locationTrackDao.fetchVersion(id, DRAFT))
        val fromDb = locationTrackDao.fetch(version)
        assertMatches(locationTrack, fromDb)
        assertEquals(id, fromDb.id)

        val updatedTrack = fromDb.copy(
            name = AlignmentName("UPD"),
            description = FreeText("Updated location track"),
            type = SIDE,
            state = NOT_IN_USE,
            topologicalConnectivity = TopologicalConnectivityType.END
        )
        val (updatedId, updatedVersion) = locationTrackDao.update(updatedTrack)
        assertEquals(id, updatedId)
        assertEquals(version.id, updatedVersion.id)
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, OFFICIAL))
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, DRAFT))
        val updatedFromDb = locationTrackDao.fetch(updatedVersion)
        assertMatches(updatedTrack, updatedFromDb)
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
        val locationTrack1 = locationTrack(trackNumberId).copy(externalId = oid, alignmentVersion = alignmentVersion1)
        val alignmentVersion2 = alignmentDao.insert(alignment())
        val locationTrack2 = locationTrack(trackNumberId).copy(externalId = oid, alignmentVersion = alignmentVersion2)

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
        )
        val (id, insertVersion) = locationTrackDao.insert(tempTrack)
        val inserted = locationTrackDao.fetch(insertVersion)
        assertMatches(tempTrack, inserted)
        assertEquals(id, inserted.id)
        assertEquals(VersionPair(insertVersion, null), locationTrackDao.fetchVersionPair(id))

        val tempDraft1 = draft(inserted).copy(name = AlignmentName("test2"))
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
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, DELETED).rowVersion
        val (deletedDraftId, deletedDraftVersion) = insertDraftLocationTrack(tnId)
        locationTrackDao.deleteDraft(deletedDraftId)

        val official = locationTrackDao.fetchVersions(OFFICIAL, false)
        assertContains(official, officialVersion)
        assertFalse(official.contains(undeletedDraftVersion))
        assertFalse(official.contains(deleteStateDraftVersion))
        assertFalse(official.contains(deletedDraftVersion))

        val draftWithoutDeleted = locationTrackDao.fetchVersions(DRAFT, false)
        assertContains(draftWithoutDeleted, undeletedDraftVersion)
        assertFalse(draftWithoutDeleted.contains(deleteStateDraftVersion))
        assertFalse(draftWithoutDeleted.contains(deletedDraftVersion))

        val draftWithDeleted = locationTrackDao.fetchVersions(DRAFT, true)
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
            locationTrackDao.fetchVersions(OFFICIAL, false, tnId).toSet(),
        )
        assertEquals(
            listOf(officialTrackVersion1, officialTrackVersion2, draftTrackVersion),
            locationTrackDao.fetchVersions(DRAFT, false, tnId),
        )
    }

    @Test
    fun findingLocationTracksByTrackNumberWorksForDraft() {
        val tnId = insertOfficialTrackNumber()
        val tnId2 = insertOfficialTrackNumber()
        val undeletedDraftVersion = insertDraftLocationTrack(tnId).rowVersion
        val deleteStateDraftVersion = insertDraftLocationTrack(tnId, DELETED).rowVersion
        val changeTrackNumberOriginal = insertOfficialLocationTrack(tnId).rowVersion
        val changeTrackNumberChanged = createDraftWithNewTrackNumber(changeTrackNumberOriginal, tnId2).rowVersion
        val deletedDraftId = insertDraftLocationTrack(tnId).id
        locationTrackDao.deleteDraft(deletedDraftId)

        assertEquals(
            listOf(changeTrackNumberOriginal),
            locationTrackDao.fetchVersions(OFFICIAL, false, tnId),
        )
        assertEquals(
            listOf(undeletedDraftVersion),
            locationTrackDao.fetchVersions(DRAFT, false, tnId),
        )

        assertEquals(
            listOf(undeletedDraftVersion, deleteStateDraftVersion).toSet(),
            locationTrackDao.fetchVersions(DRAFT, true, tnId).toSet(),
        )
        assertEquals(
            listOf(changeTrackNumberChanged),
            locationTrackDao.fetchVersions(DRAFT, true, tnId2),
        )
    }

    @Test
    fun findingLocationTracksByTrackNumberAndMomentWorks() {
        val tnId = insertOfficialTrackNumber()

        val firstVersion = insertOfficialLocationTrack(tnId).rowVersion
        val firstVersionTime = locationTrackDao.fetchChangeTime()
        Thread.sleep(1) // Ensure that they get different timestamps
        val secondVersion = insertOfficialLocationTrack(tnId).rowVersion
        val secondVersionTime = locationTrackDao.fetchChangeTime()
        assertTrue { firstVersionTime < secondVersionTime }

        assertEquals(
            listOf(firstVersion),
            locationTrackDao.fetchOfficialVersionsAtMoment(tnId, firstVersionTime),
        )
        assertEquals(
            listOf(firstVersion, secondVersion).toSet(),
            locationTrackDao.fetchOfficialVersionsAtMoment(tnId, secondVersionTime).toSet(),
        )
    }

    private fun insertOfficialLocationTrack(tnId: IntId<TrackLayoutTrackNumber>): DaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(tnId)
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(track.copy(draft = null, alignmentVersion = alignmentVersion))
    }

    private fun insertDraftLocationTrack(
        tnId: IntId<TrackLayoutTrackNumber>,
        state: LayoutState = IN_USE,
    ): DaoResponse<LocationTrack> {
        val (track, alignment) = locationTrackAndAlignment(tnId)
        val alignmentVersion = alignmentDao.insert(alignment)
        return locationTrackDao.insert(draft(track).copy(state = state, alignmentVersion = alignmentVersion))
    }

    private fun createDraftWithNewTrackNumber(
        trackVersion: RowVersion<LocationTrack>,
        newTrackNumber: IntId<TrackLayoutTrackNumber>,
    ): DaoResponse<LocationTrack> {
        val track = locationTrackDao.fetch(trackVersion)
        assertNull(track.draft)
        val alignmentVersion = alignmentService.duplicate(track.alignmentVersion!!)
        return locationTrackDao.insert(draft(track).copy(
            alignmentVersion = alignmentVersion,
            trackNumberId = newTrackNumber,
        ))
    }

    private fun updateOfficial(originalVersion: RowVersion<LocationTrack>): DaoResponse<LocationTrack> {
        val original = locationTrackDao.fetch(originalVersion)
        assertNull(original.draft)
        return locationTrackDao.update(original.copy(description = original.description + "_update"))
    }
}
