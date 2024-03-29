package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberServiceIT @Autowired constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val kmPostDao: LayoutKmPostDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "track_number", "reference_line")
    }

    @Test
    fun updatingExternalIdWorks() {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(), FreeText("description"), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.get(DRAFT, id)!!

        assertNull(trackNumber.externalId)

        trackNumberService.updateExternalId(trackNumber.id as IntId, externalIdForTrackNumber())

        val updatedTrackNumber = trackNumberService.get(DRAFT, id)!!
        assertNotNull(updatedTrackNumber.externalId)
    }

    @Test
    fun deletingDraftOnlyTrackNumberDeletesItAndReferenceLineAndAlignment() {
        val (trackNumber, referenceLine, alignment) = createTrackNumberAndReferenceLineAndAlignment()
        assertEquals(referenceLine.alignmentVersion?.id, alignment.id as IntId)
        val trackNumberId = trackNumber.id as IntId

        assertDoesNotThrow { trackNumberService.deleteDraftAndReferenceLine(trackNumberId) }
        assertThrows<NoSuchEntityException> {
            referenceLineService.getOrThrow(DRAFT, referenceLine.id as IntId)
        }
        assertThrows<NoSuchEntityException> {
            trackNumberService.getOrThrow(DRAFT, trackNumberId)
        }
        assertFalse(alignmentDao.fetchVersions().map { rv -> rv.id }.contains(alignment.id))
    }

    @Test
    fun tryingToDeletePublishedTrackNumberThrows() {
        val (trackNumber, referenceLine, _) = createTrackNumberAndReferenceLineAndAlignment()
        publishTrackNumber(trackNumber.id as IntId)
        publishReferenceLine(referenceLine.id as IntId)

        assertThrows<DeletingFailureException> {
            trackNumberService.deleteDraft(trackNumber.id as IntId)
        }
    }

    @Test
    fun `should return correct lengths for km posts`() {
        val trackNumber = trackNumberDao.fetch(
            trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false)).rowVersion
        )
        referenceLineAndAlignment(
            trackNumberId = trackNumber.id as IntId, segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(1.0, 0.0),
                    Point(2.0, 0.0),
                    Point(3.0, 0.0),
                    Point(4.0, 0.0),
                )
            ),
            startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
            draft = false,
        ).let { (referenceLine, alignment) ->
            val referenceLineVersion =
                referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))
            referenceLineDao.fetch(referenceLineVersion.rowVersion)
        }

        listOf(
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(2),
                location = Point(1.0, 0.0),
                draft = false,
            ),
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(3),
                location = Point(3.0, 0.0),
                draft = false,
            ),
        ).map(kmPostDao::insert)

        val kmLengths = trackNumberService.getKmLengths(OFFICIAL, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(3, kmLengths.size)

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(1),
                startM = BigDecimal(-0.5).setScale(3),
                endM = BigDecimal(1).setScale(3),
                locationSource = GeometrySource.GENERATED,
                location = Point(0.0, 0.0)
            ), kmLengths.first()
        )

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(2),
                startM = BigDecimal(1).setScale(3),
                endM = BigDecimal(3).setScale(3),
                locationSource = GeometrySource.IMPORTED,
                location = Point(1.0, 0.0)
            ), kmLengths[1]
        )

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(3),
                startM = BigDecimal(3).setScale(3),
                endM = BigDecimal(4).setScale(3),
                locationSource = GeometrySource.IMPORTED,
                location = Point(3.0, 0.0),
            ), kmLengths[2]
        )
    }

    @Test
    fun `should ignore km posts without location when calculating lengths lengths between km posts`() {
        val trackNumber = trackNumberDao.fetch(
            trackNumberDao.insert(trackNumber(getUnusedTrackNumber(), draft = false)).rowVersion
        )

        referenceLineAndAlignment(
            trackNumberId = trackNumber.id as IntId,
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(1.0, 0.0),
                    Point(2.0, 0.0),
                    Point(3.0, 0.0),
                    Point(4.0, 0.0),
                )
            ),
            startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
            draft = false,
        ).let { (referenceLine, alignment) ->
            val referenceLineVersion = referenceLineDao
                .insert(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))

            referenceLineDao.fetch(referenceLineVersion.rowVersion)
        }

        listOf(
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(2),
                location = Point(1.0, 0.0),
                draft = false,
            ),
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(3),
                location = null,
                draft = false,
            ),
        ).map(kmPostDao::insert)

        val kmLengths = trackNumberService.getKmLengths(OFFICIAL, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(2, kmLengths.size)

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(1),
                startM = BigDecimal(-0.5).setScale(3),
                endM = BigDecimal(1).setScale(3),
                locationSource = GeometrySource.GENERATED,
                location = Point(0.0, 0.0)
            ),
            kmLengths.first(),
        )

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(2),
                startM = BigDecimal(1).setScale(3),
                endM = BigDecimal(4).setScale(3),
                locationSource = GeometrySource.IMPORTED,
                location = Point(1.0, 0.0)
            ),
            kmLengths.last(),
        )
    }

    @Test
    fun `free text search should find multiple matching track numbers, and not the ones that did not match`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
            TrackNumber("number 111"),
            TrackNumber("number111 111")
        )

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.IN_USE,
        )

        assertEquals(2, trackNumberService.list(DRAFT, FreeText("tRaCk number"), null).size)
        assertEquals(3, trackNumberService.list(DRAFT, FreeText("11"), null).size)
        assertEquals(4, trackNumberService.list(DRAFT, FreeText("1"), null).size)
    }

    @Test
    fun `free text search should limit the amount of returned track numbers as specified`() {
        val trackNumbers = (0..15).map { number ->
            TrackNumber("some track $number")
        }

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.IN_USE,
        )

        assertEquals(5, trackNumberService.list(DRAFT, FreeText("some track"), 5).size)
        assertEquals(10, trackNumberService.list(DRAFT, FreeText("some track"), 10).size)
        assertEquals(15, trackNumberService.list(DRAFT, FreeText("some track"), 15).size)
    }

    @Test
    fun `free text search should not return deleted track numbers when its domain id or external id does not match the search term`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
        )

        saveTrackNumbersWithSaveRequests(trackNumbers, LayoutState.DELETED)
        assertEquals(0, trackNumberService.list(DRAFT, FreeText("tRaCk number"), null).size)
    }

    @Test
    fun `free text search should return deleted track number when its domain id or external id matches`() {
        val trackNumbers = listOf(
            TrackNumber("track number 1"),
            TrackNumber("track number 11"),
        )

        saveTrackNumbersWithSaveRequests(
            trackNumbers,
            LayoutState.DELETED,
        ).forEachIndexed { index, trackNumberId ->
            val oid = Oid<TrackLayoutTrackNumber>("1.2.3.4.5.6.$index")
            trackNumberService.updateExternalId(trackNumberId, oid)

            assertEquals(1, trackNumberService.list(DRAFT, FreeText(oid.toString()), null).size)
            assertEquals(1, trackNumberService.list(DRAFT, FreeText(trackNumberId.toString()), null).size)
        }

        // LayoutState was set to DELETED, meaning that these track numbers should not be found by free text.
        assertEquals(0, trackNumberService.list(DRAFT, FreeText("tRaCk number"), null).size)
    }

    fun createTrackNumberAndReferenceLineAndAlignment(): Triple<TrackLayoutTrackNumber, ReferenceLine, LayoutAlignment> {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(), FreeText(trackNumberDescription), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.get(DRAFT, id)!!

        val (referenceLine, alignment) = referenceLineService.getByTrackNumberWithAlignment(
            DRAFT, trackNumber.id as IntId<TrackLayoutTrackNumber>
        )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }

    private fun saveTrackNumbersWithSaveRequests(
        trackNumbers: List<TrackNumber>,
        layoutState: LayoutState,
    ): List<IntId<TrackLayoutTrackNumber>> {
        return trackNumbers.map { trackNumber ->
            TrackNumberSaveRequest(
                trackNumber, FreeText("some description"), layoutState, TrackMeter(
                    KmNumber(5555), 5.5, 1
                )
            )
        }.map { saveRequest ->
            trackNumberService.insert(saveRequest)
        }
    }

    private fun publishTrackNumber(id: IntId<TrackLayoutTrackNumber>) =
        trackNumberDao.fetchPublicationVersions(listOf(id))
            .first()
            .let { version -> trackNumberService.publish(version) }

    private fun publishReferenceLine(id: IntId<ReferenceLine>) = referenceLineDao.fetchPublicationVersions(listOf(id))
        .first()
        .let { version -> referenceLineService.publish(version) }
}
