package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.FreeText
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

    @Test
    fun updatingExternalIdWorks() {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(), FreeText("description"), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.getDraft(id)!!

        assertNull(trackNumber.externalId)

        trackNumberService.updateExternalId(trackNumber.id as IntId, externalIdForTrackNumber())

        val updatedTrackNumber = trackNumberService.getDraft(id)!!
        assertNotNull(updatedTrackNumber.externalId)
    }

    @Test
    fun deletingDraftOnlyTrackNumberDeletesItAndReferenceLineAndAlignment() {
        val (trackNumber, referenceLine, alignment) = createTrackNumberAndReferenceLineAndAlignment()
        assertEquals(referenceLine.alignmentVersion?.id, alignment.id as IntId)
        val trackNumberId = trackNumber.id as IntId

        assertDoesNotThrow { trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumberId) }
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
            trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(trackNumber.id as IntId)
        }
    }

    @Test
    fun `should return correct lengths for km posts`() {
        val trackNumber = trackNumberDao.fetch(trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).rowVersion)
        referenceLineAndAlignment(
            trackNumberId = trackNumber.id as IntId, segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(1.0, 0.0),
                    Point(2.0, 0.0),
                    Point(3.0, 0.0),
                    Point(4.0, 0.0),
                )
            ), startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5))
        ).let { (referenceLine, alignment) ->
            val referenceLineVersion =
                referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))
            referenceLineDao.fetch(referenceLineVersion.rowVersion)
        }

        listOf(
            kmPost(trackNumberId = trackNumber.id as IntId, km = KmNumber(2), location = Point(1.0, 0.0)),
            kmPost(trackNumberId = trackNumber.id as IntId, km = KmNumber(3), location = Point(3.0, 0.0))
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

    fun createTrackNumberAndReferenceLineAndAlignment(): Triple<TrackLayoutTrackNumber, ReferenceLine, LayoutAlignment> {
        val saveRequest = TrackNumberSaveRequest(
            getUnusedTrackNumber(), FreeText(trackNumberDescription), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(saveRequest)
        val trackNumber = trackNumberService.getDraft(id)!!

        val (referenceLine, alignment) = referenceLineService.getByTrackNumberWithAlignment(
            DRAFT, trackNumber.id as IntId<TrackLayoutTrackNumber>
        )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }

    private fun publishTrackNumber(id: IntId<TrackLayoutTrackNumber>) =
        trackNumberDao.fetchPublicationVersions(listOf(id))
            .first()
            .let { version -> trackNumberService.publish(version) }

    private fun publishReferenceLine(id: IntId<ReferenceLine>) = referenceLineDao.fetchPublicationVersions(listOf(id))
        .first()
        .let { version -> referenceLineService.publish(version) }
}
