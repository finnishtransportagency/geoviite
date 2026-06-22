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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
    private val alignmentDao: LayoutAlignmentDao,
    private val trackNumberDao: LayoutTrackNumberDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun draftAndOfficialTrackNumbersHaveSameOfficialId() {
        val dbAlignment = insertAndVerifyTrackNumber(trackNumberAndGeometry(testDBService.getUnusedTrackNumber()))
        val (dbDraft, _) = insertAndVerifyTrackNumber(alterLine(createAndVerifyDraftTrackNumber(dbAlignment)))

        assertNotEquals(dbAlignment.first, dbDraft)
        assertEquals(dbAlignment.first.id, dbDraft.id)
        assertFalse(dbAlignment.first.isDraft)
        assertTrue(dbDraft.isDraft)
    }

    @Test
    fun draftAndOfficialLocationTracksHaveSameOfficialId() {
        val dbTrackAndGeometry = insertAndVerifyTrack(createLocationTrackAndGeometry(false))
        val (dbDraft, _) = insertAndVerifyTrack(alterTrack(createAndVerifyDraftTrack(dbTrackAndGeometry)))

        assertNotEquals(dbTrackAndGeometry.first, dbDraft)
        assertEquals(dbTrackAndGeometry.first.id, dbDraft.id)
        assertFalse(dbTrackAndGeometry.first.isDraft)
        assertTrue(dbDraft.isDraft)
    }

    @Test
    fun draftAndOfficialSwitchesHaveSameOfficialId() {
        val dbSwitch = insertAndVerify(switch(draft = false))
        val dbDraft = insertAndVerify(alter(createAndVerifyDraft(dbSwitch)))

        assertNotEquals(dbSwitch, dbDraft)
        assertEquals(dbSwitch.id, dbDraft.id)
        assertFalse(dbSwitch.isDraft)
        assertTrue(dbDraft.isDraft)
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
    }

    @Test
    fun draftLocationTracksAreIncludedInDraftListingsOnly() {
        val dbOfficial = insertAndVerifyTrack(createLocationTrackAndGeometry(false))
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
    fun `location track save is an upsert`() {
        val (track, geometry) = insertAndVerifyTrack(createLocationTrackAndGeometry(false))

        val draft1 = locationTrackDao.save(asMainDraft(track), geometry)
        val draft2 = locationTrackDao.save(asMainDraft(track), geometry)

        assertEquals(draft2, locationTrackDao.fetchVersion(MainLayoutContext.draft, draft1.id))
    }

    @Test
    fun `switch save is an upsert`() {
        val switch = insertAndVerify(switch(draft = false))

        val draft1 = switchDao.save(asMainDraft(switch))
        val draft2 = switchDao.save(asMainDraft(switch))

        assertEquals(draft2, switchDao.fetchVersion(MainLayoutContext.draft, draft1.id))
    }

    @Test
    fun `km post save is an upsert`() {
        val kmPost =
            insertAndVerify(kmPost(mainOfficialContext.createLayoutTrackNumber().id, someKmNumber(), draft = false))

        val draft1 = kmPostDao.save(asMainDraft(kmPost))
        val draft2 = kmPostDao.save(asMainDraft(kmPost))

        assertEquals(draft2, kmPostDao.fetchVersion(MainLayoutContext.draft, draft1.id))
    }

    @Test
    fun `track number save is an upsert`() {
        val (trackNumber, geom) =
            insertAndVerifyTrackNumber(trackNumberAndGeometry(testDBService.getUnusedTrackNumber(), someSegment()))

        val draft1 = trackNumberDao.save(asMainDraft(trackNumber), geom)
        val draft2 = trackNumberDao.save(asMainDraft(trackNumber), geom)

        assertEquals(draft2, trackNumberDao.fetchVersion(MainLayoutContext.draft, draft1.id))
    }

    private fun createLocationTrackAndGeometry(draft: Boolean): Pair<LocationTrack, LocationTrackGeometry> =
        locationTrackAndGeometry(
            mainOfficialContext.createLayoutTrackNumber().id,
            segment(Point(10.0, 10.0), Point(11.0, 11.0)),
            draft = draft,
        )

    private fun createAndVerifyDraftTrackNumber(
        dbTnAndGeometry: Pair<LayoutTrackNumber, ReferenceLineGeometry>
    ): Pair<LayoutTrackNumber, ReferenceLineGeometry> {
        val (dbTrackNumber, dbGeometry) = dbTnAndGeometry
        assertTrue(dbTrackNumber.id is IntId)
        assertTrue(dbGeometry.trackNumberId is IntId)
        assertFalse(dbTrackNumber.isDraft)
        val draft = asMainDraft(dbTrackNumber)
        assertTrue(draft.isDraft)
        assertEquals(dbTrackNumber.id, draft.id)
        assertMatches(dbTrackNumber, draft, contextMatch = false)
        return draft to dbGeometry
    }

    private fun createAndVerifyDraftTrack(
        dbTrackAndGeometry: Pair<LocationTrack, DbLocationTrackGeometry>
    ): Pair<LocationTrack, LocationTrackGeometry> {
        val (dbTrack, dbGeometry) = dbTrackAndGeometry
        assertTrue(dbTrack.id is IntId)
        assertEquals(dbGeometry.trackRowVersion, dbTrack.version)
        assertFalse(dbTrack.isDraft)
        val draft = asMainDraft(dbTrack)
        assertTrue(draft.isDraft)
        assertEquals(dbTrack.id, draft.id)
        assertMatches(dbTrack, draft, contextMatch = false)
        return draft to dbGeometry
    }

    private fun createAndVerifyDraft(dbSwitch: LayoutSwitch): LayoutSwitch {
        assertTrue(dbSwitch.id is IntId)
        assertFalse(dbSwitch.isDraft)
        val draft = asMainDraft(dbSwitch)
        assertTrue(draft.isDraft)
        assertEquals(dbSwitch.id, draft.id)
        assertMatches(dbSwitch, draft, contextMatch = false)
        return draft
    }

    private fun createAndVerifyDraft(dbKmPost: LayoutKmPost): LayoutKmPost {
        assertTrue(dbKmPost.id is IntId)
        assertFalse(dbKmPost.isDraft)
        val draft = asMainDraft(dbKmPost)
        assertTrue(draft.isDraft)
        assertEquals(dbKmPost.id, draft.id)
        assertMatches(dbKmPost, draft, contextMatch = false)
        return draft
    }

    private fun alterLine(lineAndAlignment: Pair<LayoutTrackNumber, ReferenceLineGeometry>) =
        lineAndAlignment.first.copy(startAddress = lineAndAlignment.first.startAddress + 10.0) to
            lineAndAlignment.second

    private fun alterTrack(trackAndGeometry: Pair<LocationTrack, LocationTrackGeometry>) =
        trackAndGeometry.first.copy(name = AlignmentName("${trackAndGeometry.first.name}-D")) to trackAndGeometry.second

    private fun alter(switch: LayoutSwitch): LayoutSwitch = switch.copy(name = SwitchName("${switch.name}-D"))

    private fun alter(kmPost: LayoutKmPost): LayoutKmPost =
        kmPost.copy(kmNumber = KmNumber(kmPost.kmNumber.number, (kmPost.kmNumber.extension ?: "") + "B"))

    private fun insertAndVerifyTrack(
        trackAndGeometry: Pair<LocationTrack, LocationTrackGeometry>
    ): Pair<LocationTrack, DbLocationTrackGeometry> {
        val (track, geometry) = trackAndGeometry
        assertEquals(DataType.TEMP, track.dataType)
        val trackResponse = locationTrackDao.save(track, geometry)
        val geometryFromDb = alignmentDao.fetch(trackResponse)
        assertMatches(geometry, geometryFromDb)
        val trackFromDb = locationTrackDao.fetch(trackResponse)
        assertEquals(DataType.STORED, trackFromDb.dataType)
        assertEquals(trackResponse.id, trackFromDb.id)
        assertMatches(track, trackFromDb, contextMatch = false)
        return trackFromDb to geometryFromDb
    }

    private fun insertAndVerify(switch: LayoutSwitch): LayoutSwitch {
        assertEquals(DataType.TEMP, switch.dataType)
        val response = switchDao.save(switch)
        val fromDb = switchDao.fetch(response)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(switch, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerify(kmPost: LayoutKmPost): LayoutKmPost {
        assertEquals(DataType.TEMP, kmPost.dataType)
        val response = kmPostDao.save(kmPost)
        val fromDb = kmPostDao.fetch(response)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(kmPost, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        return fromDb
    }

    private fun insertAndVerifyTrackNumber(
        trackNumberAndGeometry: Pair<LayoutTrackNumber, ReferenceLineGeometry>
    ): Pair<LayoutTrackNumber, ReferenceLineGeometry> {
        val (trackNumber, geometry) = trackNumberAndGeometry
        assertEquals(DataType.TEMP, trackNumber.dataType)
        val response = trackNumberDao.save(trackNumber, geometry)
        val fromDb = trackNumberDao.fetch(response)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(trackNumber, fromDb, contextMatch = false)
        assertEquals(response.id, fromDb.id)
        val geometryFromDb = alignmentDao.fetch(response)
        assertMatches(geometry, geometryFromDb, false)
        return fromDb to geometryFromDb
    }
}
