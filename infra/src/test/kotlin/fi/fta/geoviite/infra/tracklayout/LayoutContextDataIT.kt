package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutContextDataIT @Autowired constructor(
    private val switchDao: LayoutSwitchDao,
    private val switchService: LayoutSwitchService,
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val referenceLineService: ReferenceLineService,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val trackNumberDao: LayoutTrackNumberDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "switch")
    }

    @Test
    fun tempReferenceLineDraftDoesntChangeId() {
        val (track, _) = createReferenceLineAndAlignment(draft = false)
        val draft = asMainDraft(track)
        assertEquals(track.id, draft.id)
        assertFalse(track.isDraft)
        assertTrue(draft.isDraft)
        assertEquals(draft.id, draft.rowId)
    }

    @Test
    fun tempLocationTrackDraftDoesntChangeId() {
        val (track, _) = createLocationTrackAndAlignment(draft = false)
        val draft = asMainDraft(track)
        assertEquals(track.id, draft.id)
        assertFalse(track.isDraft)
        assertTrue(draft.isDraft)
        assertEquals(draft.id, draft.rowId)
    }

    @Test
    fun tempSwitchDraftDoesntChangeId() {
        val switch = switch(987, draft = false)
        val draft = asMainDraft(switch)
        assertEquals(switch.id, draft.id)
        assertFalse(switch.isDraft)
        assertTrue(draft.isDraft)
        assertEquals(draft.id, draft.rowId)
    }

    @Test
    fun tempKmPostDraftDoesntChangeId() {
        val kmPost = kmPost(null, someKmNumber(), draft = false)
        val draft = asMainDraft(kmPost)
        assertEquals(kmPost.id, draft.id)
        assertFalse(kmPost.isDraft)
        assertTrue(draft.isDraft)
        assertEquals(kmPost.id, draft.rowId)
    }

    @Test
    fun draftAndOfficialReferenceLinesAreFoundWithEachOthersIds() {
        val dbLineAndAlignment = insertAndVerifyLine(createReferenceLineAndAlignment())
        val dbDraft = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbLineAndAlignment)))

        assertNotEquals(dbLineAndAlignment.first, dbDraft.first)
        assertEquals(dbLineAndAlignment.first.id, dbDraft.first.id)
        assertFalse(dbLineAndAlignment.first.isDraft)
        assertTrue(dbDraft.first.isDraft)
        assertNotEquals(dbDraft.first.isDraft.draftRowId, dbDraft.first.id)

        assertMatches(
            dbLineAndAlignment.first, referenceLineService.get(OFFICIAL, dbLineAndAlignment.first.id as IntId)!!, true
        )
        assertMatches(
            dbLineAndAlignment.first,
            referenceLineService.get(OFFICIAL, dbDraft.first.isDraft.draftRowId as IntId)!!,
            true
        )

        assertMatches(dbDraft.first, referenceLineService.get(DRAFT, dbDraft.first.id as IntId)!!, true)
        assertMatches(dbDraft.first, referenceLineService.get(DRAFT, dbDraft.first.isDraft.draftRowId as IntId)!!, true)
    }

    @Test
    fun draftAndOfficialLocationTracksAreFoundWithEachOthersIds() {
        val dbTrackAndAlignment = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val dbDraft = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbTrackAndAlignment)))

        assertNotEquals(dbTrackAndAlignment.first, dbDraft.first)
        assertEquals(dbTrackAndAlignment.first.id, dbDraft.first.id)
        assertFalse(dbTrackAndAlignment.first.isDraft)
        assertTrue(dbDraft.first.isDraft)
        assertNotEquals(dbDraft.first.isDraft.draftRowId, dbDraft.first.id)

        assertMatches(
            dbTrackAndAlignment.first, locationTrackService.get(OFFICIAL, dbTrackAndAlignment.first.id as IntId)!!, true
        )
        assertMatches(
            dbTrackAndAlignment.first,
            locationTrackService.get(OFFICIAL, dbDraft.first.isDraft.draftRowId as IntId)!!,
            true
        )

        assertMatches(dbDraft.first, locationTrackService.get(DRAFT, dbDraft.first.id as IntId)!!, true)
        assertMatches(dbDraft.first, locationTrackService.get(DRAFT, dbDraft.first.isDraft.draftRowId as IntId)!!, true)
    }

    @Test
    fun draftAndOfficialSwitchesAreFoundWithEachOthersIds() {
        val dbSwitch = insertAndVerify(switch(123))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))
        assertNotEquals(dbSwitch, dbDraft)

        assertMatches(dbSwitch, switchService.get(OFFICIAL, dbSwitch.id as IntId)!!, true)
        assertMatches(dbSwitch, switchService.get(OFFICIAL, dbDraft.isDraft.draftRowId as IntId)!!, true)

        assertMatches(dbDraft, switchService.get(DRAFT, dbDraft.id as IntId)!!, true)
        assertMatches(dbDraft, switchService.get(DRAFT, dbDraft.isDraft.draftRowId as IntId)!!, true)
    }


    @Test
    fun draftAndOfficialKmPostsAreFoundWithEachOthersIds() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))
        assertNotEquals(dbKmPost, dbDraft)

        assertMatches(dbKmPost, kmPostService.get(OFFICIAL, dbKmPost.id as IntId)!!, true)
        assertMatches(dbKmPost, kmPostService.get(OFFICIAL, dbDraft.isDraft.draftRowId as IntId)!!, true)

        assertMatches(dbDraft, kmPostService.get(DRAFT, dbDraft.id as IntId)!!, true)
        assertMatches(dbDraft, kmPostService.get(DRAFT, dbDraft.isDraft.draftRowId as IntId)!!, true)
    }

    @Test
    fun draftAndOfficialReferenceLinesHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyLine(createReferenceLineAndAlignment())
        val (dbDraft, _) = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertFalse(dbAlignment.first.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.isDraft.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialLocationTracksHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val (dbDraft, _) = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertFalse(dbAlignment.first.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.isDraft.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialSwitchesHaveSameOfficialId() {
        val dbSwitch = insertAndVerify(switch(123))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        assertNotEquals(dbSwitch, dbDraft)
        assertEquals(dbSwitch.id, dbDraft.id)
        assertFalse(dbSwitch.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.isDraft.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialKmPostsHaveSameOfficialId() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        assertNotEquals(dbKmPost, dbDraft)
        assertEquals(dbKmPost.id, dbDraft.id)
        assertFalse(dbKmPost.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.isDraft.draftRowId, dbDraft.id)
    }

    @Test
    fun draftReferenceLinesAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyLine(createReferenceLineAndAlignment())
        val dbDraft = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbOfficial)))

        val officials = referenceLineService.list(OFFICIAL)
        val drafts = referenceLineService.list(DRAFT)

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftLocationTracksAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val dbDraft = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbOfficial)))

        val officials = locationTrackService.list(OFFICIAL)
        val drafts = locationTrackService.list(DRAFT)

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftSwitchesAreIncludedInDraftListingsOnly() {
        val dbSwitch = insertAndVerify(switch(456))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        val officials = switchService.list(OFFICIAL)
        val drafts = switchService.list(DRAFT)

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbSwitch))

        assertTrue(officials.contains(dbSwitch))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun draftKmPostsAreIncludedInDraftListingsOnly() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        val officials = kmPostService.list(OFFICIAL)
        val drafts = kmPostService.list(DRAFT)

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbKmPost))

        assertTrue(officials.contains(dbKmPost))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun referenceLineCanOnlyHaveOneDraft() {
        val (line, _) = insertAndVerifyLine(createReferenceLineAndAlignment())

        val draft1 = asMainDraft(line)
        val draft2 = asMainDraft(line)

        referenceLineDao.insert(draft1)
        assertThrows<DuplicateKeyException> { referenceLineDao.insert(draft2) }
    }

    @Test
    fun locationTrackCanOnlyHaveOneDraft() {
        val (track, _) = insertAndVerifyTrack(createLocationTrackAndAlignment())

        val draft1 = asMainDraft(track)
        val draft2 = asMainDraft(track)

        locationTrackDao.insert(draft1)
        assertThrows<DuplicateKeyException> { locationTrackDao.insert(draft2) }
    }

    @Test
    fun switchCanOnlyHaveOneDraft() {
        val switch = insertAndVerify(switch(9))

        val draft1 = asMainDraft(switch)
        val draft2 = asMainDraft(switch)

        switchDao.insert(draft1)
        assertThrows<DuplicateKeyException> { switchDao.insert(draft2) }
    }

    @Test
    fun kmPostCanOnlyHaveOneDraft() {
        val kmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))

        val draft1 = asMainDraft(kmPost)
        val draft2 = asMainDraft(kmPost)

        kmPostDao.insert(draft1)
        assertThrows<DuplicateKeyException> { kmPostDao.insert(draft2) }
    }

    @Test
    fun trackNumberCanOnlyHaveOneDraft() {
        val trackNumber = insertAndVerify(trackNumber(getUnusedTrackNumber()))

        val draft1 = asMainDraft(trackNumber)
        val draft2 = asMainDraft(trackNumber)

        trackNumberDao.insert(draft1)
        assertThrows<DuplicateKeyException> { trackNumberDao.insert(draft2) }
    }

    @Test
    fun draftTypeOfNewDraftIsReturnedCorrectly() {
        val draft = asMainDraft(kmPost(null, someKmNumber()))
        assertEquals(draft.getDraftType(), DraftType.NEW_DRAFT)
        assertFalse(draft.isOfficial())
        assertTrue(draft.isDraft())
        assertTrue(draft.isNewDraft())
        assertFalse(draft.isEditedDraft())
    }

    @Test
    fun draftTypeOfOfficialIsReturnedCorrectly() {
        val official = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        assertEquals(official.getDraftType(), DraftType.OFFICIAL)
        assertTrue(official.isOfficial())
        assertFalse(official.isDraft())
        assertFalse(official.isNewDraft())
        assertFalse(official.isEditedDraft())
    }

    @Test
    fun draftTypeOfChangedDraftIsReturnedCorrectly() {
        val edited = asMainDraft(insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber())))
        assertEquals(edited.getDraftType(), DraftType.EDITED_DRAFT)
        assertFalse(edited.isOfficial())
        assertTrue(edited.isDraft())
        assertFalse(edited.isNewDraft())
        assertTrue(edited.isEditedDraft())
    }

    private fun createReferenceLineAndAlignment(draft: Boolean): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineAndAlignment(
            insertOfficialTrackNumber(),
            segment(Point(10.0, 10.0), Point(11.0, 11.0)),
            draft = draft,
        )

    private fun createLocationTrackAndAlignment(draft: Boolean): Pair<LocationTrack, LayoutAlignment> =
        locationTrackAndAlignment(
            insertOfficialTrackNumber(),
            segment(Point(10.0, 10.0), Point(11.0, 11.0)),
            draft = draft,
        )

    private fun createAndVerifyDraftLine(
        dbLineAndAlignment: Pair<ReferenceLine, LayoutAlignment>,
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (dbLine, dbAlignment) = dbLineAndAlignment
        assertTrue(dbLine.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbLine.alignmentVersion?.id)
        assertFalse(dbLine.isDraft)
        val draft = asMainDraft(dbLine)
        assertEquals(dbLine.id, draft.id)
        assertNotEquals(dbLine.id, draft.isDraft.draftRowId)
        assertMatches(dbLine, draft.copy(draft = null))
        return draft to dbAlignment
    }

    private fun createAndVerifyDraftTrack(
        dbTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    ): Pair<LocationTrack, LayoutAlignment> {
        val (dbTrack, dbAlignment) = dbTrackAndAlignment
        assertTrue(dbTrack.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbTrack.alignmentVersion?.id)
        assertFalse(dbTrack.isDraft)
        val draft = asMainDraft(dbTrack)
        assertEquals(dbTrack.id, draft.id)
        assertNotEquals(dbTrack.id, draft.isDraft.draftRowId)
        assertMatches(dbTrack, draft.copy(draft = null))
        return draft to dbAlignment
    }

    private fun createAndVerifyDraft(dbSwitch: TrackLayoutSwitch): TrackLayoutSwitch {
        assertTrue(dbSwitch.id is IntId)
        assertFalse(dbSwitch.isDraft)
        val draft = asMainDraft(dbSwitch)
        assertEquals(dbSwitch.id, draft.id)
        assertNotEquals(dbSwitch.id, draft.isDraft.draftRowId)
        assertMatches(dbSwitch, draft.copy(draft = null))
        return draft
    }

    private fun createAndVerifyDraft(dbKmPost: TrackLayoutKmPost): TrackLayoutKmPost {
        assertTrue(dbKmPost.id is IntId)
        assertFalse(dbKmPost.isDraft)
        val draft = asMainDraft(dbKmPost)
        assertEquals(dbKmPost.id, draft.id)
        assertNotEquals(dbKmPost.id, draft.isDraft.draftRowId)
        assertMatches(dbKmPost, draft.copy(draft = null))
        return draft
    }

    private fun alterLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) = lineAndAlignment.first.copy(
        startAddress = lineAndAlignment.first.startAddress + 10.0
    ) to lineAndAlignment.second

    private fun alterTrack(trackAndAlignment: Pair<LocationTrack, LayoutAlignment>) = trackAndAlignment.first.copy(
        name = AlignmentName("${trackAndAlignment.first.name}-D")
    ) to trackAndAlignment.second

    private fun alter(switch: TrackLayoutSwitch): TrackLayoutSwitch = switch.copy(name = SwitchName("${switch.name}-D"))

    private fun alter(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
        kmPost.copy(kmNumber = KmNumber(kmPost.kmNumber.number, (kmPost.kmNumber.extension ?: "") + "B"))


    private fun insertAndVerifyLine(
        lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>,
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (line, alignment) = lineAndAlignment
        val alignmentVersion = alignmentDao.insert(alignment)
        val lineWithAlignment = line.copy(alignmentVersion = alignmentVersion)
        val lineResponse = referenceLineDao.insert(lineWithAlignment)
        val alignmentFromDb = alignmentDao.fetch(alignmentVersion)
        assertMatches(alignment, alignmentFromDb)
        val lineFromDb = referenceLineDao.fetch(lineResponse.rowVersion)
        assertEquals(lineResponse.id, lineFromDb.id)
        assertMatches(lineWithAlignment, lineFromDb)
        return lineFromDb to alignmentFromDb
    }

    private fun insertAndVerifyTrack(
        trackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    ): Pair<LocationTrack, LayoutAlignment> {
        val (track, alignment) = trackAndAlignment
        val alignmentVersion = alignmentDao.insert(alignment)
        val trackWithAlignment = track.copy(alignmentVersion = alignmentVersion)
        val trackResponse = locationTrackDao.insert(trackWithAlignment)
        val alignmentFromDb = alignmentDao.fetch(alignmentVersion)
        assertMatches(alignment, alignmentFromDb)
        val trackFromDb = locationTrackDao.fetch(trackResponse.rowVersion)
        assertEquals(trackResponse.id, trackFromDb.id)
        assertMatches(trackWithAlignment, trackFromDb)
        return trackFromDb to alignmentFromDb
    }

    private fun insertAndVerify(switch: TrackLayoutSwitch): TrackLayoutSwitch {
        val response = switchDao.insert(switch)
        val fromDb = switchDao.fetch(response.rowVersion)
        assertMatches(switch, fromDb)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerify(kmPost: TrackLayoutKmPost): TrackLayoutKmPost {
        val response = kmPostDao.insert(kmPost)
        val fromDb = kmPostDao.fetch(response.rowVersion)
        assertMatches(kmPost, fromDb)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerify(trackNumber: TrackLayoutTrackNumber): TrackLayoutTrackNumber {
        val response = trackNumberDao.insert(trackNumber)
        val fromDb = trackNumberDao.fetch(response.rowVersion)
        assertMatches(trackNumber, fromDb)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }
}
