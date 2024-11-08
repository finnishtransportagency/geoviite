package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutContextDataIT
@Autowired
constructor(
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
        testDBService.clearLayoutTables()
    }

    @Test
    fun draftAndOfficialReferenceLinesHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyLine(createReferenceLineAndAlignment(false))
        val (dbDraft, _) = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertFalse(dbAlignment.first.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.contextData.rowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialLocationTracksHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyTrack(createLocationTrackAndAlignment(false))
        val (dbDraft, _) = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbAlignment)))

        assertNotEquals(dbAlignment, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertFalse(dbAlignment.first.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.contextData.rowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialSwitchesHaveSameOfficialId() {
        val dbSwitch = insertAndVerify(switch(draft = false))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        assertNotEquals(dbSwitch, dbDraft)
        assertEquals(dbSwitch.id, dbDraft.id)
        assertFalse(dbSwitch.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.contextData.rowId, dbDraft.id)
    }

    @Test
    fun draftAndOfficialKmPostsHaveSameOfficialId() {
        val dbKmPost =
            insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        assertNotEquals(dbKmPost, dbDraft)
        assertEquals(dbKmPost.id, dbDraft.id)
        assertFalse(dbKmPost.isDraft)
        assertTrue(dbDraft.isDraft)
        assertNotEquals(dbDraft.contextData.rowId, dbDraft.id)
    }

    @Test
    fun draftReferenceLinesAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyLine(createReferenceLineAndAlignment(false))
        val dbDraft = insertAndVerifyLine(alterLine(createAndVerifyDraftLine(dbOfficial)))

        val officials = referenceLineService.list(MainLayoutContext.official)
        val drafts = referenceLineService.list(MainLayoutContext.draft)

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftLocationTracksAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyTrack(createLocationTrackAndAlignment(false))
        val dbDraft = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbOfficial)))

        val officials = locationTrackService.list(MainLayoutContext.official)
        val drafts = locationTrackService.list(MainLayoutContext.draft)

        assertFalse(officials.contains(dbDraft.first))
        assertFalse(drafts.contains(dbOfficial.first))

        assertTrue(officials.contains(dbOfficial.first))
        assertTrue(drafts.contains(dbDraft.first))
    }

    @Test
    fun draftSwitchesAreIncludedInDraftListingsOnly() {
        val dbSwitch = insertAndVerify(switch(draft = false))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        val officials = switchService.list(MainLayoutContext.official)
        val drafts = switchService.list(MainLayoutContext.draft)

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbSwitch))

        assertTrue(officials.contains(dbSwitch))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun draftKmPostsAreIncludedInDraftListingsOnly() {
        val dbKmPost =
            insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbKmPost)))

        val officials = kmPostService.list(MainLayoutContext.official)
        val drafts = kmPostService.list(MainLayoutContext.draft)

        assertFalse(officials.contains(dbDraft))
        assertFalse(drafts.contains(dbKmPost))

        assertTrue(officials.contains(dbKmPost))
        assertTrue(drafts.contains(dbDraft))
    }

    @Test
    fun referenceLineCanOnlyHaveOneDraft() {
        val (line, _) = insertAndVerifyLine(createReferenceLineAndAlignment(false))

        val draft1 = asMainDraft(line)
        val draft2 = asMainDraft(line)

        referenceLineDao.insert(draft1)
        assertThrows<DuplicateKeyException> { referenceLineDao.insert(draft2) }
    }

    @Test
    fun locationTrackCanOnlyHaveOneDraft() {
        val (track, _) = insertAndVerifyTrack(createLocationTrackAndAlignment(false))

        val draft1 = asMainDraft(track)
        val draft2 = asMainDraft(track)

        locationTrackDao.insert(draft1)
        assertThrows<DuplicateKeyException> { locationTrackDao.insert(draft2) }
    }

    @Test
    fun switchCanOnlyHaveOneDraft() {
        val switch = insertAndVerify(switch(draft = false))

        val draft1 = asMainDraft(switch)
        val draft2 = asMainDraft(switch)

        switchDao.insert(draft1)
        assertThrows<DuplicateKeyException> { switchDao.insert(draft2) }
    }

    @Test
    fun kmPostCanOnlyHaveOneDraft() {
        val kmPost =
            insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))

        val draft1 = asMainDraft(kmPost)
        val draft2 = asMainDraft(kmPost)

        kmPostDao.insert(draft1)
        assertThrows<DuplicateKeyException> { kmPostDao.insert(draft2) }
    }

    @Test
    fun trackNumberCanOnlyHaveOneDraft() {
        val trackNumber = insertAndVerify(trackNumber(testDBService.getUnusedTrackNumber(), draft = false))

        val draft1 = asMainDraft(trackNumber)
        val draft2 = asMainDraft(trackNumber)

        trackNumberDao.insert(draft1)
        assertThrows<DuplicateKeyException> { trackNumberDao.insert(draft2) }
    }

    @Test
    fun editStateOfNewDraftIsReturnedCorrectly() {
        val draft = kmPost(null, someKmNumber(), draft = true)
        assertEquals(draft.editState, EditState.CREATED)
        assertFalse(draft.isOfficial)
        assertTrue(draft.isDraft)
    }

    @Test
    fun editStateOfOfficialIsReturnedCorrectly() {
        val official =
            insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))
        assertEquals(official.editState, EditState.UNEDITED)
        assertTrue(official.isOfficial)
        assertFalse(official.isDraft)
    }

    @Test
    fun editStateOfChangedDraftIsReturnedCorrectly() {
        val edited =
            asMainDraft(
                insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))
            )
        assertEquals(edited.editState, EditState.EDITED)
        assertFalse(edited.isOfficial)
        assertTrue(edited.isDraft)
    }

    private fun createReferenceLineAndAlignment(draft: Boolean): Pair<ReferenceLine, LayoutAlignment> =
        referenceLineAndAlignment(
            mainOfficialContext.createLayoutTrackNumber().id,
            segment(Point(10.0, 10.0), Point(11.0, 11.0)),
            draft = draft,
        )

    private fun createLocationTrackAndAlignment(draft: Boolean): Pair<LocationTrack, LayoutAlignment> =
        locationTrackAndAlignment(
            mainOfficialContext.createLayoutTrackNumber().id,
            segment(Point(10.0, 10.0), Point(11.0, 11.0)),
            draft = draft,
        )

    private fun createAndVerifyDraftLine(
        dbLineAndAlignment: Pair<ReferenceLine, LayoutAlignment>
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (dbLine, dbAlignment) = dbLineAndAlignment
        assertTrue(dbLine.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbLine.alignmentVersion?.id)
        assertFalse(dbLine.isDraft)
        val draft = asMainDraft(dbLine)
        assertTrue(draft.isDraft)
        assertEquals(dbLine.id, draft.id)
        assertNotEquals(dbLine.id, draft.contextData.rowId)
        assertMatches(dbLine, draft, contextMatch = false)
        return draft to dbAlignment
    }

    private fun createAndVerifyDraftTrack(
        dbTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>
    ): Pair<LocationTrack, LayoutAlignment> {
        val (dbTrack, dbAlignment) = dbTrackAndAlignment
        assertTrue(dbTrack.id is IntId)
        assertTrue(dbAlignment.id is IntId)
        assertEquals(dbAlignment.id, dbTrack.alignmentVersion?.id)
        assertFalse(dbTrack.isDraft)
        val draft = asMainDraft(dbTrack)
        assertTrue(draft.isDraft)
        assertEquals(dbTrack.id, draft.id)
        assertNotEquals(dbTrack.id, draft.contextData.rowId)
        assertMatches(dbTrack, draft, contextMatch = false)
        return draft to dbAlignment
    }

    private fun createAndVerifyDraft(dbSwitch: TrackLayoutSwitch): TrackLayoutSwitch {
        assertTrue(dbSwitch.id is IntId)
        assertFalse(dbSwitch.isDraft)
        val draft = asMainDraft(dbSwitch)
        assertTrue(draft.isDraft)
        assertEquals(dbSwitch.id, draft.id)
        assertNotEquals(dbSwitch.id, draft.contextData.rowId)
        assertMatches(dbSwitch, draft, contextMatch = false)
        return draft
    }

    private fun createAndVerifyDraft(dbKmPost: TrackLayoutKmPost): TrackLayoutKmPost {
        assertTrue(dbKmPost.id is IntId)
        assertFalse(dbKmPost.isDraft)
        val draft = asMainDraft(dbKmPost)
        assertTrue(draft.isDraft)
        assertEquals(dbKmPost.id, draft.id)
        assertNotEquals(dbKmPost.id, draft.contextData.rowId)
        assertMatches(dbKmPost, draft, contextMatch = false)
        return draft
    }

    private fun alterLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) =
        lineAndAlignment.first.copy(startAddress = lineAndAlignment.first.startAddress + 10.0) to
            lineAndAlignment.second

    private fun alterTrack(trackAndAlignment: Pair<LocationTrack, LayoutAlignment>) =
        trackAndAlignment.first.copy(name = AlignmentName("${trackAndAlignment.first.name}-D")) to
            trackAndAlignment.second

    private fun alter(switch: TrackLayoutSwitch): TrackLayoutSwitch = switch.copy(name = SwitchName("${switch.name}-D"))

    private fun alter(kmPost: TrackLayoutKmPost): TrackLayoutKmPost =
        kmPost.copy(kmNumber = KmNumber(kmPost.kmNumber.number, (kmPost.kmNumber.extension ?: "") + "B"))

    private fun insertAndVerifyLine(
        lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>
    ): Pair<ReferenceLine, LayoutAlignment> {
        val (line, alignment) = lineAndAlignment
        assertEquals(DataType.TEMP, line.dataType)
        val alignmentVersion = alignmentDao.insert(alignment)
        val lineWithAlignment = line.copy(alignmentVersion = alignmentVersion)
        val lineResponse = referenceLineDao.insert(lineWithAlignment)
        val alignmentFromDb = alignmentDao.fetch(alignmentVersion)
        assertMatches(alignment, alignmentFromDb)
        val lineFromDb = referenceLineDao.fetch(lineResponse.rowVersion)
        assertEquals(DataType.STORED, lineFromDb.dataType)
        assertEquals(lineResponse.id, lineFromDb.id)
        assertMatches(lineWithAlignment, lineFromDb, contextMatch = false)
        return lineFromDb to alignmentFromDb
    }

    private fun insertAndVerifyTrack(
        trackAndAlignment: Pair<LocationTrack, LayoutAlignment>
    ): Pair<LocationTrack, LayoutAlignment> {
        val (track, alignment) = trackAndAlignment
        assertEquals(DataType.TEMP, track.dataType)
        val alignmentVersion = alignmentDao.insert(alignment)
        val trackWithAlignment = track.copy(alignmentVersion = alignmentVersion)
        val trackResponse = locationTrackDao.insert(trackWithAlignment)
        val alignmentFromDb = alignmentDao.fetch(alignmentVersion)
        assertMatches(alignment, alignmentFromDb)
        val trackFromDb = locationTrackDao.fetch(trackResponse.rowVersion)
        assertEquals(DataType.STORED, trackFromDb.dataType)
        assertEquals(trackResponse.id, trackFromDb.id)
        assertMatches(trackWithAlignment, trackFromDb, contextMatch = false)
        return trackFromDb to alignmentFromDb
    }

    private fun insertAndVerify(switch: TrackLayoutSwitch): TrackLayoutSwitch {
        assertEquals(DataType.TEMP, switch.dataType)
        val response = switchDao.insert(switch)
        val fromDb = switchDao.fetch(response.rowVersion)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(switch, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerify(kmPost: TrackLayoutKmPost): TrackLayoutKmPost {
        assertEquals(DataType.TEMP, kmPost.dataType)
        val response = kmPostDao.insert(kmPost)
        val fromDb = kmPostDao.fetch(response.rowVersion)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(kmPost, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerify(trackNumber: TrackLayoutTrackNumber): TrackLayoutTrackNumber {
        assertEquals(DataType.TEMP, trackNumber.dataType)
        val response = trackNumberDao.insert(trackNumber)
        val fromDb = trackNumberDao.fetch(response.rowVersion)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(trackNumber, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }
}
