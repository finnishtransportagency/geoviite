package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
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
        testDBService.clearLayoutTables()
    }

    @Test
    fun updatingExternalIdWorks() {
        val saveRequest = TrackNumberSaveRequest(
            testDBService.getUnusedTrackNumber(), FreeText("description"), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest)
        val trackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!

        assertNull(trackNumber.externalId)

        trackNumberService.updateExternalId(LayoutBranch.main, trackNumber.id as IntId, externalIdForTrackNumber())

        val updatedTrackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!
        assertNotNull(updatedTrackNumber.externalId)
    }

    @Test
    fun deletingDraftOnlyTrackNumberDeletesItAndReferenceLineAndAlignment() {
        val (trackNumber, referenceLine, alignment) = createTrackNumberAndReferenceLineAndAlignment()
        assertEquals(referenceLine.alignmentVersion?.id, alignment.id as IntId)
        val trackNumberId = trackNumber.id as IntId

        assertDoesNotThrow { trackNumberService.deleteDraftAndReferenceLine(LayoutBranch.main, trackNumberId) }
        assertThrows<NoSuchEntityException> {
            referenceLineService.getOrThrow(MainLayoutContext.draft, referenceLine.id as IntId)
        }
        assertThrows<NoSuchEntityException> {
            trackNumberService.getOrThrow(MainLayoutContext.draft, trackNumberId)
        }
        assertFalse(alignmentDao.fetchVersions().map { rv -> rv.id }.contains(alignment.id))
    }

    @Test
    fun tryingToDeletePublishedTrackNumberThrows() {
        val (trackNumber, referenceLine, _) = createTrackNumberAndReferenceLineAndAlignment()
        publishTrackNumber(trackNumber.id as IntId)
        publishReferenceLine(referenceLine.id as IntId)

        assertThrows<DeletingFailureException> {
            trackNumberService.deleteDraft(LayoutBranch.main, trackNumber.id as IntId)
        }
    }

    @Test
    fun `should return correct lengths for km posts`() {
        val trackNumber = trackNumberDao.fetch(
            trackNumberDao.insert(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).rowVersion
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

        val kmPostVersions = listOf(
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(2),
                roughLayoutLocation = Point(1.0, 0.0),
                draft = false,
            ),
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(3),
                roughLayoutLocation = Point(3.0, 0.0),
                draft = false,
            ),
        ).map(kmPostDao::insert).map(LayoutDaoResponse<TrackLayoutKmPost>::rowVersion)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
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
                location = kmPostDao.fetch(kmPostVersions[0]).layoutLocation,
            ), kmLengths[1]
        )

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(3),
                startM = BigDecimal(3).setScale(3),
                endM = BigDecimal(4).setScale(3),
                locationSource = GeometrySource.IMPORTED,
                location = kmPostDao.fetch(kmPostVersions[1]).layoutLocation,
            ), kmLengths[2]
        )
    }

    @Test
    fun `should ignore km posts without location when calculating lengths between km posts`() {
        val trackNumber = trackNumberDao.fetch(
            trackNumberDao.insert(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).rowVersion
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

        val kmPostVersions = listOf(
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(2),
                roughLayoutLocation = Point(1.0, 0.0),
                draft = false,
            ),
            kmPost(
                trackNumberId = trackNumber.id as IntId,
                km = KmNumber(3),
                roughLayoutLocation = null,
                draft = false,
            ),
        ).map(kmPostDao::insert).map(LayoutDaoResponse<TrackLayoutKmPost>::rowVersion)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
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
                location = kmPostDao.fetch(kmPostVersions[0]).layoutLocation,
            ),
            kmLengths.last(),
        )
    }

    fun createTrackNumberAndReferenceLineAndAlignment(): Triple<TrackLayoutTrackNumber, ReferenceLine, LayoutAlignment> {
        val saveRequest = TrackNumberSaveRequest(
            testDBService.getUnusedTrackNumber(), FreeText(trackNumberDescription), LayoutState.IN_USE, TrackMeter(
                KmNumber(5555), 5.5, 1
            )
        )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest)
        val trackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!

        val (referenceLine, alignment) = referenceLineService.getByTrackNumberWithAlignment(
            MainLayoutContext.draft, trackNumber.id as IntId<TrackLayoutTrackNumber>
        )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }

    private fun publishTrackNumber(id: IntId<TrackLayoutTrackNumber>) =
        trackNumberDao.fetchPublicationVersions(LayoutBranch.main, listOf(id))
            .first()
            .let { version -> trackNumberService.publish(LayoutBranch.main, version) }

    private fun publishReferenceLine(id: IntId<ReferenceLine>): LayoutDaoResponse<ReferenceLine> = referenceLineDao
        .fetchPublicationVersions(LayoutBranch.main, listOf(id))
        .first()
        .let { version -> referenceLineService.publish(LayoutBranch.main, version) }
}
