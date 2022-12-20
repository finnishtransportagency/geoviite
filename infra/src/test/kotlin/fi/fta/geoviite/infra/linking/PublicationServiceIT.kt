package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.*

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationServiceIT @Autowired constructor(
    val publicationService: PublishService,
    val locationTrackService: LocationTrackService,
    val referenceLineService: ReferenceLineService,
    val switchService: LayoutSwitchService,
    val switchDao: LayoutSwitchDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val trackNumberService: LayoutTrackNumberService,
    val kmPostService: LayoutKmPostService,
    val calculatedChangesService: CalculatedChangesService,
    val publicationDao: PublicationDao,
): ITTestBase() {

    @Test
    fun publicationChangeSetIsStoredAndLoadedCorrectly() {
        val trackNumbers = listOf(
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber())),
            trackNumberService.saveDraft(trackNumber(getUnusedTrackNumber())),
        )
        val switches = listOf(
            switchService.saveDraft(switch(111)),
            switchService.saveDraft(switch(112)),
        )
        val trackNumberId = someTrackNumber()
        val referenceLines = listOf(
            referenceLineAndAlignment(trackNumberId),
            referenceLineAndAlignment(trackNumbers[0].id, segment(Point(1.0, 1.0), Point(2.0, 2.0))),
            referenceLineAndAlignment(trackNumbers[1].id, segment(Point(5.0, 5.0), Point(6.0, 6.0)))
        ).map { (line, alignment) ->
            referenceLineService.saveDraft(line.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val locationTracks = listOf(
            locationTrackAndAlignment(trackNumbers[0].id),
            locationTrackAndAlignment(trackNumbers[0].id),
        ).map { (track, alignment) ->
            locationTrackService.saveDraft(track.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
        val kmPosts = listOf(
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(1))),
            kmPostService.saveDraft(kmPost(trackNumbers[0].id, KmNumber(2)))
        )

        val beforeInsert = getDbTime()
        val publishRequest = PublishRequest(
            trackNumbers.map { it.id },
            locationTracks.map { it.id },
            referenceLines.map { it.id },
            switches.map { it.id },
            kmPosts.map { it.id }
        )

        val draftCalculatedChanges = getCalculatedChangesInRequest(publishRequest)
        val publishResult = publicationService.publishChanges(publishRequest)
        val afterInsert = getDbTime()
        assertNotNull(publishResult.publishId)
        val publish = publicationService.getPublication(publishResult.publishId!!)

        assertTrue(publish.publishTime in beforeInsert..afterInsert)
        assertEquals(trackNumbers.map { it.id }, publish.trackNumbers.map { it.id })
        assertEquals(switches.map { it.id }, publish.switches.map { it.id })
        assertEquals(referenceLines.map { it.id }, publish.referenceLines.map { it.id })
        assertEquals(locationTracks.map { it.id }, publish.locationTracks.map { it.id })
        assertEquals(kmPosts.map { it.id }, publish.kmPosts.map { it.id })

        val restoredCalculatedChanges = publicationDao.fetchCalculatedChangesInPublish(publishResult.publishId!!)
        assertEquals(draftCalculatedChanges, restoredCalculatedChanges)
    }

    @Test
    fun fetchSwitchTrackNumberLinksFromPublication() {
        val switch = switchService.saveDraft(switch(123))
        val trackNumberIds = listOf(
            insertOfficialTrackNumber(),
            insertOfficialTrackNumber(),
        )
        val locationTracks = trackNumberIds.map { trackNumberId ->
            val (t, a) = locationTrackAndAlignment(trackNumberId, segment(Point(0.0, 0.0), Point(1.0, 1.0)))
            locationTrackService.saveDraft(t.copy(alignmentVersion =
                alignmentDao.insert(a.copy(segments = listOf(a.segments[0].copy(switchId = switch.id))))))
        }

        val publishRequest = PublishRequest(
            listOf(),
            locationTracks.map { it.id },
            listOf(),
            listOf(switch.id),
            listOf(),
        )
        val publishResult = publicationService.publishChanges(publishRequest)
        val publish = publicationService.getPublication(publishResult.publishId!!)
        assertEquals(trackNumberIds.sortedBy { it.intValue }, publish.switches[0].trackNumberIds.sortedBy { it.intValue })
    }

    @Test
    fun publishingNewReferenceLineWorks() {
        val (line, alignment) = referenceLineAndAlignment(someTrackNumber())
        val draftId = referenceLineService.saveDraft(line, alignment).id
        assertThrows<NoSuchEntityException> { referenceLineService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, referenceLineService.getDraft(draftId).id)

        val publishRequest = PublishRequest(
            trackNumbers = listOf(),
            kmPosts = listOf(),
            switches = listOf(),
            referenceLines = listOf(draftId),
            locationTracks = listOf(),
        )
        val draftCalculatedChanges = getCalculatedChangesInRequest(publishRequest)
        val publishResult = publicationService.publishChanges(publishRequest)
        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(1, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            referenceLineService.getOfficial(draftId)!!.startAddress,
            referenceLineService.getDraft(draftId).startAddress,
        )
        val fetchedCalculatedChanges = publicationDao.fetchCalculatedChangesInPublish(publishResult.publishId!!)
        assertEquals(draftCalculatedChanges, fetchedCalculatedChanges)
    }

    @Test
    fun publishingNewLocationTrackWorks() {
        val (track, alignment) = locationTrackAndAlignment(
            insertOfficialTrackNumber(),
            segment(Point(0.0, 0.0), Point(1.0, 1.0))
        )
        referenceLineService.saveDraft(referenceLine(track.trackNumberId), alignment)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.getDraft(draftId).id)

        val publishResult = publicationService.publishChanges(
            PublishRequest(
                trackNumbers = listOf(),
                kmPosts = listOf(),
                switches = listOf(),
                referenceLines = listOf(),
                locationTracks = listOf(draftId),
            )
        )

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(1, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            locationTrackService.getOfficial(draftId)!!.name,
            locationTrackService.getDraft(draftId).name,
        )
    }


    @Test
    fun publishingReferenceLineChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val line = referenceLine(someTrackNumber(), alignmentDao.fetch(alignmentVersion), startAddress = TrackMeter("0001", 10))
            .copy(alignmentVersion = alignmentVersion)
        val officialId = referenceLineDao.insert(line).id

        val (tmpLine, tmpAlignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId)
        referenceLineService.saveDraft(
            tmpLine.copy(startAddress = TrackMeter("0002", 20)),
            tmpAlignment.copy(
                segments = fixStartDistances(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            referenceLineService.getOfficial(officialId)!!.startAddress,
            referenceLineService.getDraft(officialId).startAddress,
        )


        assertEquals(1, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        val publishRequest = PublishRequest(
            trackNumbers = listOf(),
            kmPosts = listOf(),
            switches = listOf(),
            referenceLines = listOf(officialId),
            locationTracks = listOf(),
        )
        val draftCalculatedChanges = getCalculatedChangesInRequest(publishRequest)
        val publishResult = publicationService.publishChanges(publishRequest)

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(1, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            referenceLineService.getOfficial(officialId)!!.startAddress,
            referenceLineService.getDraft(officialId).startAddress,
        )
        assertEquals(2, referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(
            referenceLineService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments,
            referenceLineService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments,
        )
        val fetchedCalculatedChanges = publicationDao.fetchCalculatedChangesInPublish(publishResult.publishId!!)
        assertEquals(draftCalculatedChanges, fetchedCalculatedChanges)
    }

    @Test
    fun publishingLocationTrackChangesWorks() {
        val alignmentVersion = alignmentDao.insert(alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0))))
        val referenceAlignment = alignment(segment(Point(0.0, 0.0), Point(4.0, 4.0)))
        val track = locationTrack(insertOfficialTrackNumber(), alignmentDao.fetch(alignmentVersion), name = "test 01")
            .copy(alignmentVersion = alignmentVersion)

        referenceLineService.publish(referenceLineService.saveDraft(referenceLine(track.trackNumberId), referenceAlignment).id)

        val officialId = locationTrackDao.insert(track).id

        val (tmpTrack, tmpAlignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId)
        locationTrackService.saveDraft(
            tmpTrack.copy(name = AlignmentName("DRAFT test 01")),
            tmpAlignment.copy(
                segments = fixStartDistances(
                    listOf(
                        segment(Point(1.0, 1.0), Point(2.0, 2.0)),
                        segment(Point(2.0, 2.0), Point(3.0, 3.0)),
                    )
                )
            )
        )
        assertNotEquals(
            locationTrackService.getOfficial(officialId)!!.name,
            locationTrackService.getDraft(officialId).name,
        )
        assertEquals(1, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments.size)

        val publishRequest = PublishRequest(
            trackNumbers = listOf(),
            kmPosts = listOf(),
            switches = listOf(),
            referenceLines = listOf(),
            locationTracks = listOf(officialId),
        )
        val draftCalculatedChanges = getCalculatedChangesInRequest(publishRequest)
        val publishResult = publicationService.publishChanges(publishRequest)

        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(1, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            locationTrackService.getOfficial(officialId)!!.name,
            locationTrackService.getDraft(officialId).name,
        )
        assertEquals(2, locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments.size)
        assertEquals(
            locationTrackService.getWithAlignmentOrThrow(OFFICIAL, officialId).second.segments,
            locationTrackService.getWithAlignmentOrThrow(DRAFT, officialId).second.segments,
        )

        val fetchedCalculatedChanges = publicationDao.fetchCalculatedChangesInPublish(publishResult.publishId!!)
        assertEquals(draftCalculatedChanges, fetchedCalculatedChanges)
    }

    @Test
    fun publishingNewSwitchWorks() {
        val draftId = switchService.saveDraft(switch(123)).id
        assertNull(switchService.getOfficial(draftId))
        assertEquals(draftId, switchService.getDraft(draftId).id)

        val publishResult = publicationService.publishChanges(
            PublishRequest(
                trackNumbers = listOf(),
                kmPosts = listOf(),
                switches = listOf(draftId),
                referenceLines = listOf(),
                locationTracks = listOf(),
            )
        )
        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(1, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            switchService.getOfficial(draftId)!!.name,
            switchService.getDraft(draftId).name,
        )
    }

    @Test
    fun publishingSwitchChangesWorks() {
        val officialId = switchDao.insert(
            switch(55).copy(
                name = SwitchName("TST 001"),
                joints = listOf(switchJoint(1), switchJoint(3)),
            )
        ).id

        switchService.saveDraft(
            switchService.getDraft(officialId).copy(
                name = SwitchName("DRAFT TST 001"),
                joints = listOf(switchJoint(2), switchJoint(3), switchJoint(4)),
            )
        )
        assertNotEquals(
            switchService.getOfficial(officialId)!!.name,
            switchService.getDraft(officialId).name,
        )
        assertEquals(2, switchService.getOfficial(officialId)!!.joints.size)
        assertEquals(3, switchService.getDraft(officialId).joints.size)

        val publishRequest = PublishRequest(
            trackNumbers = listOf(),
            kmPosts = listOf(),
            switches = listOf(officialId),
            referenceLines = listOf(),
            locationTracks = listOf(),
        )

        val draftCalculatedChanges = getCalculatedChangesInRequest(publishRequest)
        val publishResult = publicationService.publishChanges(publishRequest)
        assertNotNull(publishResult.publishId)
        assertEquals(0, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(1, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            switchService.getOfficial(officialId)!!.name,
            switchService.getDraft(officialId).name,
        )
        assertEquals(3, switchService.getOfficial(officialId)!!.joints.size)
        assertEquals(
            switchService.getOfficial(officialId)!!.joints,
            switchService.getDraft(officialId).joints,
        )
        val fetchedCalculatedChanges = publicationDao.fetchCalculatedChangesInPublish(publishResult.publishId!!)
        assertEquals(draftCalculatedChanges, fetchedCalculatedChanges)
    }

    @Test
    fun publishingNewTrackNumberWorks() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val draftId = trackNumberService.saveDraft(trackNumber).id
        assertNull(trackNumberService.getOfficial(draftId))
        assertEquals(draftId, trackNumberService.getDraft(draftId).id)

        val publishResult = publicationService.publishChanges(
            PublishRequest(
                trackNumbers = listOf(draftId),
                kmPosts = listOf(),
                switches = listOf(),
                referenceLines = listOf(),
                locationTracks = listOf(),
            )
        )

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            trackNumberService.getOfficial(draftId)!!.number,
            trackNumberService.getDraft(draftId).number,
        )
    }

    @Test
    fun publishingTrackNumberChangesWorks() {
        val officialId = trackNumberDao.insert(
            trackNumber().copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 1"),
            )
        ).id

        trackNumberService.saveDraft(
            trackNumberService.getDraft(officialId).copy(
                number = getUnusedTrackNumber(),
                description = FreeText("Test 2"),
            )
        )

        assertNotEquals(
            trackNumberService.getOfficial(officialId)!!.number,
            trackNumberService.getDraft(officialId).number
        )

        assertEquals(FreeText("Test 1"), trackNumberService.getOfficial(officialId)!!.description)
        assertEquals(FreeText("Test 2"), trackNumberService.getDraft(officialId).description)

        val publishResult = publicationService.publishChanges(
            PublishRequest(
                trackNumbers = listOf(officialId),
                kmPosts = listOf(),
                switches = listOf(),
                referenceLines = listOf(),
                locationTracks = listOf(),
            )
        )

        assertNotNull(publishResult.publishId)
        assertEquals(1, publishResult.trackNumbers)
        assertEquals(0, publishResult.referenceLines)
        assertEquals(0, publishResult.locationTracks)
        assertEquals(0, publishResult.switches)
        assertEquals(0, publishResult.kmPosts)

        assertEquals(
            trackNumberService.getOfficial(officialId)!!.number,
            trackNumberService.getDraft(officialId).number,
        )

        assertEquals(
            trackNumberService.getOfficial(officialId)!!.description,
            trackNumberService.getDraft(officialId).description,
        )
    }


    @Test
    fun fetchingPublicationListingWorks() {
        val trackNumberId = insertDraftTrackNumber()
        referenceLineService.saveDraft(
            referenceLine(trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(1.0, 1.0))))
        val (track, alignment) = locationTrackAndAlignment(trackNumberId)
        val draftId = locationTrackService.saveDraft(track, alignment).id
        assertThrows<NoSuchEntityException> { locationTrackService.getWithAlignmentOrThrow(OFFICIAL, draftId) }
        assertEquals(draftId, locationTrackService.getDraft(draftId).id)

        val publicationCountBeforePublishing = publicationService.getPublicationListing().size

        val publishResult = publicationService.publishChanges(
            PublishRequest(
                trackNumbers = listOf(trackNumberId),
                kmPosts = listOf(),
                switches = listOf(),
                referenceLines = listOf(),
                locationTracks = listOf(draftId),
            )
        )

        val publicationCountAfterPublishing = publicationService.getPublicationListing()

        assertEquals(publicationCountBeforePublishing + 1, publicationCountAfterPublishing.size)
        assertEquals(publishResult.publishId, publicationCountAfterPublishing.last().id)
    }

    @Test
    fun revertingOnlyGivenChangesWorks() {
        val switch1 = switchService.saveDraft(switch(123)).id
        val switch2 = switchService.saveDraft(switch(234)).id

        val revertResult = publicationService.revertPublishCandidates(
            PublishRequest(listOf(), listOf(), listOf(), listOf(switch1), listOf())
        )

        assertEquals(revertResult.switches, 1)
        assertThrows<NoSuchEntityException> { switchService.getDraft(switch1) }
        assertDoesNotThrow { switchService.getDraft(switch2) }
    }

    private fun someTrackNumber() = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id

    private fun getCalculatedChangesInRequest(req: PublishRequest): CalculatedChanges =
        calculatedChangesService.getCalculatedChangesInDraft(
            req.trackNumbers,
            req.referenceLines,
            req.kmPosts,
            req.locationTracks,
            req.switches
        )
}
