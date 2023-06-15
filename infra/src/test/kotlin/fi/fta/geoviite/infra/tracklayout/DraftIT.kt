package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class DraftIT @Autowired constructor(
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
): DBTestBase() {

    @Test
    fun tempReferenceLineDraftDoesntChangeId() {
        val (track, _) = createLocationTrackAndAlignment()
        val draft = draft(track)
        assertEquals(track.id, draft.id)
        assertNull(track.draft)
        assertEquals(draft.id, draft.draft!!.draftRowId)
    }

    @Test
    fun tempLocationTrackDraftDoesntChangeId() {
        val (track, _) = createLocationTrackAndAlignment()
        val draft = draft(track)
        assertEquals(track.id, draft.id)
        assertNull(track.draft)
        assertEquals(draft.id, draft.draft!!.draftRowId)
    }

    @Test
    fun tempSwitchDraftDoesntChangeId() {
        val switch = switch(987)
        val draft = draft(switch)
        assertEquals(switch.id, draft.id)
        assertNull(switch.draft)
        assertEquals(draft.id, draft.draft!!.draftRowId)
    }

    @Test
    fun tempKmPostDraftDoesntChangeId() {
        val kmPost = kmPost(null, someKmNumber())
        val draft = draft(kmPost)
        assertEquals(kmPost.id, draft.id)
        assertNull(kmPost.draft)
        assertEquals(kmPost.id, draft.draft!!.draftRowId)
    }

    @Test
    fun draftAndOfficialReferenceLinesAreFoundWithEachOthersIds() {
        val dbLineAndAlignment = insertAndVerifyLine(createReferenceLineAndAlignment())
        val dbDraft = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbLineAndAlignment)))

        assertNotEquals(dbLineAndAlignment.first, dbDraft.first)
        assertEquals(dbLineAndAlignment.first.id, dbDraft.first.id)
        assertNull(dbLineAndAlignment.first.draft)
        assertNotNull(dbDraft.first.draft)
        assertNotEquals(dbDraft.first.draft?.draftRowId, dbDraft.first.id)

        assertMatches(
            dbLineAndAlignment.first,
            referenceLineService.getOfficial(dbLineAndAlignment.first.id as IntId)!!,
            true
        )
        assertMatches(
            dbLineAndAlignment.first,
            referenceLineService.getOfficial(dbDraft.first.draft!!.draftRowId as IntId)!!,
            true
        )

        assertMatches(dbDraft.first, referenceLineService.getDraft(dbDraft.first.id as IntId), true)
        assertMatches(dbDraft.first, referenceLineService.getDraft(dbDraft.first.draft!!.draftRowId as IntId), true)
    }

    @Test
    fun draftAndOfficialLocationTracksAreFoundWithEachOthersIds() {
        val dbTrackAndAlignment = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val dbDraft = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbTrackAndAlignment)))

        assertNotEquals(dbTrackAndAlignment.first, dbDraft.first)
        assertEquals(dbTrackAndAlignment.first.id, dbDraft.first.id)
        assertNull(dbTrackAndAlignment.first.draft)
        assertNotNull(dbDraft.first.draft)
        assertNotEquals(dbDraft.first.draft?.draftRowId, dbDraft.first.id)

        assertMatches(
            dbTrackAndAlignment.first,
            locationTrackService.getOfficial(dbTrackAndAlignment.first.id as IntId)!!,
            true
        )
        assertMatches(
            dbTrackAndAlignment.first,
            locationTrackService.getOfficial(dbDraft.first.draft!!.draftRowId as IntId)!!,
            true
        )

        assertMatches(dbDraft.first, locationTrackService.getDraft(dbDraft.first.id as IntId), true)
        assertMatches(dbDraft.first, locationTrackService.getDraft(dbDraft.first.draft!!.draftRowId as IntId), true)
    }

    @Test
    fun draftAndOfficialSwitchesAreFoundWithEachOthersIds() {
        val dbSwitch = insertAndVerify(switch(123))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))
        assertNotEquals(dbSwitch, dbDraft)

        assertMatches(dbSwitch, switchService.getOfficial(dbSwitch.id as IntId)!!, true)
        assertMatches(dbSwitch, switchService.getOfficial(dbDraft.draft!!.draftRowId as IntId)!!, true)

        assertMatches(dbDraft, switchService.getDraft(dbDraft.id as IntId), true)
        assertMatches(dbDraft, switchService.getDraft(dbDraft.draft!!.draftRowId as IntId), true)
    }


    @Test
    fun draftAndOfficialKmPostsAreFoundWithEachOthersIds() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))
        assertNotEquals(dbKmPost, dbDraft)

        assertMatches(dbKmPost, kmPostService.getOfficial(dbKmPost.id as IntId)!!, true)
        assertMatches(dbKmPost, kmPostService.getOfficial(dbDraft.draft!!.draftRowId as IntId)!!, true)

        assertMatches(dbDraft, kmPostService.getDraft(dbDraft.id as IntId), true)
        assertMatches(dbDraft, kmPostService.getDraft(dbDraft.draft!!.draftRowId as IntId), true)
    }

    @Test
    fun draftAndOfficialReferenceLinesHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyLine(createReferenceLineAndAlignment())
        val (dbDraft, _) = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertNull(dbAlignment.first.draft)
        assertNotNull(dbDraft.draft)
        assertNotEquals(dbDraft.draft?.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialLocationTracksHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val (dbDraft, _) = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertNull(dbAlignment.first.draft)
        assertNotNull(dbDraft.draft)
        assertNotEquals(dbDraft.draft?.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialSwitchesHaveSameOfficialId() {
        val dbSwitch = insertAndVerify(switch(123))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        assertNotEquals(dbSwitch, dbDraft)
        assertEquals(dbSwitch.id, dbDraft.id)
        assertNull(dbSwitch.draft)
        assertNotNull(dbDraft.draft)
        assertNotEquals(dbDraft.draft?.draftRowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialKmPostsHaveSameOfficialId() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        assertNotEquals(dbKmPost, dbDraft)
        assertEquals(dbKmPost.id, dbDraft.id)
        assertNull(dbKmPost.draft)
        assertNotNull(dbDraft.draft)
        assertNotEquals(dbDraft.draft?.draftRowId, dbDraft.id)
    }

    @Test
    fun draftReferenceLinesAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyLine(createReferenceLineAndAlignment())
        val dbDraft = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbOfficial)))

        val officials = referenceLineService.listOfficial()
        val drafts = referenceLineService.listDraft()

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftLocationTracksAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyTrack(createLocationTrackAndAlignment())
        val dbDraft = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbOfficial)))

        val officials = locationTrackService.listOfficial()
        val drafts = locationTrackService.listDraft()

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftSwitchesAreIncludedInDraftListingsOnly() {
        val dbSwitch = insertAndVerify(switch(456))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        val officials = switchService.listOfficial()
        val drafts = switchService.listDraft()

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbSwitch))

        assertTrue(officials.contains(dbSwitch))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun draftKmPostsAreIncludedInDraftListingsOnly() {
        val dbKmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        val officials = kmPostService.listOfficial()
        val drafts = kmPostService.listDraft()

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbKmPost))

        assertTrue(officials.contains(dbKmPost))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun referenceLineCanOnlyHaveOneDraft() {
        val (line, _) = insertAndVerifyLine(createReferenceLineAndAlignment())

        val draft1 = draft(line)
        val draft2 = draft(line)

        referenceLineDao.insert(draft1)
        assertThrows<DuplicateKeyException> { referenceLineDao.insert(draft2) }
    }

    @Test
    fun locationTrackCanOnlyHaveOneDraft() {
        val (track, _) = insertAndVerifyTrack(createLocationTrackAndAlignment())

        val draft1 = draft(track)
        val draft2 = draft(track)

        locationTrackDao.insert(draft1)
        assertThrows<DuplicateKeyException> { locationTrackDao.insert(draft2) }
    }

    @Test
    fun switchCanOnlyHaveOneDraft() {
        val switch = insertAndVerify(switch(9))

        val draft1 = draft(switch)
        val draft2 = draft(switch)

        switchDao.insert(draft1)
        assertThrows<DuplicateKeyException> { switchDao.insert(draft2) }
    }

    @Test
    fun kmPostCanOnlyHaveOneDraft() {
        val kmPost = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))

        val draft1 = draft(kmPost)
        val draft2 = draft(kmPost)

        kmPostDao.insert(draft1)
        assertThrows<DuplicateKeyException> { kmPostDao.insert(draft2) }
    }

    @Test
    fun trackNumberCanOnlyHaveOneDraft() {
        val trackNumber = insertAndVerify(trackNumber(getUnusedTrackNumber()))

        val draft1 = draft(trackNumber)
        val draft2 = draft(trackNumber)

        trackNumberDao.insert(draft1)
        assertThrows<DuplicateKeyException> { trackNumberDao.insert(draft2) }
    }

    @Test
    fun draftTypeOfNewDraftIsReturnedCorrectly() {
        val draft = draft(kmPost(null, someKmNumber()))
        assertEquals(draft.getDraftType(), DraftType.NEW_DRAFT)
    }

    @Test
    fun draftTypeOfOfficialIsReturnedCorrectly() {
        val official = insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber()))
        assertEquals(official.getDraftType(), DraftType.OFFICIAL)
    }

    @Test
    fun draftTypeOfChangedDraftIsReturnedCorrectly() {
        val edited = draft(insertAndVerify(kmPost(insertOfficialTrackNumber(), someKmNumber())))
        assertEquals(edited.getDraftType(), DraftType.EDITED_DRAFT)
    }

    private fun createReferenceLineAndAlignment(): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineAndAlignment(insertOfficialTrackNumber(), segment(Point(10.0, 10.0), Point(11.0, 11.0)))

    private fun createLocationTrackAndAlignment(): Pair<LocationTrack, LayoutAlignment> =
        locationTrackAndAlignment(insertOfficialTrackNumber(), segment(Point(10.0, 10.0), Point(11.0, 11.0)))

    private fun createAndVerifyDraftLine(
        dbLineAndAlignment: Pair<ReferenceLine, LayoutAlignment>
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (dbLine, dbAlignment) = dbLineAndAlignment
        assertTrue(dbLine.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbLine.alignmentVersion?.id)
        assertNull(dbLine.draft)
        val draft = draft(dbLine)
        assertEquals(dbLine.id, draft.id)
        assertNotEquals(dbLine.id, draft.draft?.draftRowId)
        assertMatches(dbLine, draft.copy(draft = null))
        return draft to dbAlignment
    }

    private fun createAndVerifyDraftTrack(
        dbTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>
    ): Pair<LocationTrack, LayoutAlignment> {
        val (dbTrack, dbAlignment) = dbTrackAndAlignment
        assertTrue(dbTrack.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbTrack.alignmentVersion?.id)
        assertNull(dbTrack.draft)
        val draft = draft(dbTrack)
        assertEquals(dbTrack.id, draft.id)
        assertNotEquals(dbTrack.id, draft.draft?.draftRowId)
        assertMatches(dbTrack, draft.copy(draft = null))
        return draft to dbAlignment
    }

    private fun createAndVerifyDraft(dbSwitch: TrackLayoutSwitch): TrackLayoutSwitch {
        assertTrue(dbSwitch.id is IntId)
        assertNull(dbSwitch.draft)
        val draft = draft(dbSwitch)
        assertEquals(dbSwitch.id, draft.id)
        assertNotEquals(dbSwitch.id, draft.draft?.draftRowId)
        assertMatches(dbSwitch, draft.copy(draft = null))
        return draft
    }

    private fun createAndVerifyDraft(dbKmPost: TrackLayoutKmPost): TrackLayoutKmPost {
        assertTrue(dbKmPost.id is IntId)
        assertNull(dbKmPost.draft)
        val draft = draft(dbKmPost)
        assertEquals(dbKmPost.id, draft.id)
        assertNotEquals(dbKmPost.id, draft.draft?.draftRowId)
        assertMatches(dbKmPost, draft.copy(draft = null))
        return draft
    }

    private fun alterLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) =
        lineAndAlignment.first.copy(
            startAddress = lineAndAlignment.first.startAddress + 10.0
        ) to lineAndAlignment.second

    private fun alterTrack(trackAndAlignment: Pair<LocationTrack, LayoutAlignment>) =
        trackAndAlignment.first.copy(
            name = AlignmentName("${trackAndAlignment.first.name}-D")
        ) to trackAndAlignment.second

    private fun alter(switch: TrackLayoutSwitch): TrackLayoutSwitch =
        switch.copy(name = SwitchName("${switch.name}-D"))

    private fun alter(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
        kmPost.copy(kmNumber = KmNumber(kmPost.kmNumber.number, (kmPost.kmNumber.extension ?: "") + "B"))


    private fun insertAndVerifyLine(
        lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>
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
        trackAndAlignment: Pair<LocationTrack, LayoutAlignment>
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
