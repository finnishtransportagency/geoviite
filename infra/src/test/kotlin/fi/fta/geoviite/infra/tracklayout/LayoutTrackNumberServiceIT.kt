package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.publication.ValidationVersion
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutTrackNumberServiceIT
@Autowired
constructor(
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
        val saveRequest =
            TrackNumberSaveRequest(
                testDBService.getUnusedTrackNumber(),
                TrackNumberDescription("description"),
                LayoutState.IN_USE,
                TrackMeter(KmNumber(5555), 5.5, 1),
            )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest).id
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
        assertThrows<NoSuchEntityException> { trackNumberService.getOrThrow(MainLayoutContext.draft, trackNumberId) }
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
        val trackNumber =
            trackNumberDao.fetch(
                trackNumberDao.insert(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).rowVersion
            )
        referenceLineAndAlignment(
                trackNumberId = trackNumber.id as IntId,
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
                    ),
                startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
                draft = false,
            )
            .let { (referenceLine, alignment) ->
                val referenceLineVersion =
                    referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))
                referenceLineDao.fetch(referenceLineVersion.rowVersion)
            }

        val kmPostVersions =
            listOf(
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
                )
                .map(kmPostDao::insert)
                .map(LayoutDaoResponse<TrackLayoutKmPost>::rowVersion)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(3, kmLengths.size)

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(1),
                startM = BigDecimal(-0.5).setScale(3),
                endM = BigDecimal(1).setScale(3),
                layoutGeometrySource = GeometrySource.GENERATED,
                layoutLocation = Point(0.0, 0.0),
                gkLocation = null,
                gkLocationLinkedFromGeometry = false,
            ),
            kmLengths.first(),
        )

        val kmPostLocation1 = kmPostDao.fetch(kmPostVersions[0]).layoutLocation
        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(2),
                startM = BigDecimal(1).setScale(3),
                endM = BigDecimal(3).setScale(3),
                layoutGeometrySource = GeometrySource.IMPORTED,
                layoutLocation = kmPostLocation1,
                gkLocation = null,
                gkLocationLinkedFromGeometry = false,
            ),
            kmLengths[1].copy(gkLocation = null),
        )
        assertApproximatelyEquals(
            transformFromLayoutToGKCoordinate(kmPostLocation1!!),
            kmLengths[1].gkLocation!!.location,
            0.01,
        )

        val kmPostLocation2 = kmPostDao.fetch(kmPostVersions[1]).layoutLocation
        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(3),
                startM = BigDecimal(3).setScale(3),
                endM = BigDecimal(4).setScale(3),
                layoutGeometrySource = GeometrySource.IMPORTED,
                layoutLocation = kmPostLocation2,
                gkLocation = null,
                gkLocationLinkedFromGeometry = false,
            ),
            kmLengths[2].copy(gkLocation = null),
        )
        assertApproximatelyEquals(
            transformFromLayoutToGKCoordinate(kmPostLocation2!!),
            kmLengths[2].gkLocation!!.location,
            0.01,
        )
    }

    @Test
    fun `should ignore km posts without location when calculating lengths between km posts`() {
        val trackNumber =
            trackNumberDao.fetch(
                trackNumberDao.insert(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).rowVersion
            )

        referenceLineAndAlignment(
                trackNumberId = trackNumber.id as IntId,
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
                    ),
                startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
                draft = false,
            )
            .let { (referenceLine, alignment) ->
                val referenceLineVersion =
                    referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))

                referenceLineDao.fetch(referenceLineVersion.rowVersion)
            }

        val kmPostVersions =
            listOf(
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
                )
                .map(kmPostDao::insert)
                .map(LayoutDaoResponse<TrackLayoutKmPost>::rowVersion)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(2, kmLengths.size)

        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(1),
                startM = BigDecimal(-0.5).setScale(3),
                endM = BigDecimal(1).setScale(3),
                layoutGeometrySource = GeometrySource.GENERATED,
                layoutLocation = Point(0.0, 0.0),
                gkLocation = null,
                gkLocationLinkedFromGeometry = false,
            ),
            kmLengths.first(),
        )

        val kmPostLocation = kmPostDao.fetch(kmPostVersions[0]).layoutLocation
        assertEquals(
            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                kmNumber = KmNumber(2),
                startM = BigDecimal(1).setScale(3),
                endM = BigDecimal(4).setScale(3),
                layoutGeometrySource = GeometrySource.IMPORTED,
                layoutLocation = kmPostLocation,
                gkLocation = null,
                gkLocationLinkedFromGeometry = false,
            ),
            kmLengths.last().copy(gkLocation = null),
        )
        assertApproximatelyEquals(
            transformFromLayoutToGKCoordinate(kmPostLocation!!),
            kmLengths.last().gkLocation!!.location,
            0.01,
        )
    }

    @Test
    fun `Publishing a new track number from design with a draft leaves the correct rows and references`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = testDBService.getUnusedTrackNumber()

        val designDraft1 =
            trackNumberService.insert(designBranch, trackNumberSaveRequest(trackNumber, "$trackNumber v1"))
        val tnId = designDraft1.id
        assertVersionReferences(designBranch, tnId, designDraft = designDraft1.rowVersion)

        val designOfficial = trackNumberService.publish(designBranch, ValidationVersion(tnId, designDraft1.rowVersion))
        assertVersionReferences(designBranch, tnId, designOfficial = designOfficial.rowVersion)

        val mainDraft = trackNumberService.mergeToMainBranch(designBranch, tnId)
        assertVersionReferences(
            designBranch,
            tnId,
            mainDraft = mainDraft.rowVersion,
            designOfficial = designOfficial.rowVersion,
        )

        val designDraft2 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v2"))
        assertVersionReferences(
            designBranch,
            tnId,
            mainDraft = mainDraft.rowVersion,
            designOfficial = designOfficial.rowVersion,
            designDraft = designDraft2.rowVersion,
        )

        val mainOfficial = trackNumberService.publish(LayoutBranch.main, ValidationVersion(tnId, mainDraft.rowVersion))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial.rowVersion,
            // draft version is updated by the publication, fixing references
            designDraft = designDraft2.rowVersion.next(),
        )
    }

    @Test
    fun `Publishing an edited track number from design with a draft leaves the correct rows and references`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = testDBService.getUnusedTrackNumber()

        val mainDraft1 =
            trackNumberService.insert(LayoutBranch.main, trackNumberSaveRequest(trackNumber, "$trackNumber v1"))
        val tnId = mainDraft1.id
        assertVersionReferences(designBranch, tnId, mainDraft = mainDraft1.rowVersion)

        val mainOfficial1 =
            trackNumberService.publish(LayoutBranch.main, ValidationVersion(tnId, mainDraft1.rowVersion))
        referenceLineService.getByTrackNumber(MainLayoutContext.draft, tnId).let { rl ->
            referenceLineService.publish(LayoutBranch.main, ValidationVersion(rl!!.id as IntId, rl.version!!))
        }
        assertVersionReferences(designBranch, tnId, mainOfficial = mainOfficial1.rowVersion)

        val designDraft1 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v2"))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1.rowVersion,
            designDraft = designDraft1.rowVersion,
        )

        val designOfficial = trackNumberService.publish(designBranch, ValidationVersion(tnId, designDraft1.rowVersion))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1.rowVersion,
            designOfficial = designOfficial.rowVersion,
        )

        val mainDraft2 = trackNumberService.mergeToMainBranch(designBranch, tnId)
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1.rowVersion,
            mainDraft = mainDraft2.rowVersion,
            designOfficial = designOfficial.rowVersion,
        )

        val designDraft2 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v3"))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1.rowVersion,
            mainDraft = mainDraft2.rowVersion,
            designOfficial = designOfficial.rowVersion,
            designDraft = designDraft2.rowVersion,
        )

        val mainOfficial2 =
            trackNumberService.publish(LayoutBranch.main, ValidationVersion(tnId, mainDraft2.rowVersion))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial2.rowVersion,
            // draft version is updated by the publication, fixing references
            designDraft = designDraft2.rowVersion.next(),
        )
    }

    private fun assertVersionReferences(
        designBranch: DesignBranch,
        id: IntId<TrackLayoutTrackNumber>,
        mainOfficial: LayoutRowVersion<TrackLayoutTrackNumber>? = null,
        mainDraft: LayoutRowVersion<TrackLayoutTrackNumber>? = null,
        designOfficial: LayoutRowVersion<TrackLayoutTrackNumber>? = null,
        designDraft: LayoutRowVersion<TrackLayoutTrackNumber>? = null,
    ) {
        val description =
            "expectedVersions={mainOfficial=$mainOfficial mainDraft=$mainDraft designOfficial=$designOfficial designDraft=$designDraft}"
        assertVersionMatch(
            description = "Main official version incorrect: $description",
            expected = mainOfficial,
            actual = trackNumberDao.fetchVersion(LayoutBranch.main.official, id),
        )
        assertVersionMatch(
            description = "Main draft version incorrect: $description",
            expected = mainDraft ?: mainOfficial,
            actual = trackNumberDao.fetchVersion(LayoutBranch.main.draft, id),
        )
        assertVersionMatch(
            description = "Design official version incorrect: $description",
            expected = designOfficial ?: mainOfficial,
            actual = trackNumberDao.fetchVersion(designBranch.official, id),
        )
        assertVersionMatch(
            description = "Design draft version incorrect: $description",
            expected = designDraft ?: designOfficial ?: mainOfficial,
            actual = trackNumberDao.fetchVersion(designBranch.draft, id),
        )
        mainOfficial?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertFalse(trackNumber.isDraft)
            assertEquals(null, trackNumber.contextData.officialRowId)
            assertEquals(null, trackNumber.contextData.designRowId)
        }
        mainDraft?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertTrue(trackNumber.isDraft)
            assertEquals(mainOfficial?.rowId, trackNumber.contextData.officialRowId)
            assertEquals(designOfficial?.rowId, trackNumber.contextData.designRowId)
        }
        designOfficial?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertFalse(trackNumber.isDraft)
            assertEquals(mainOfficial?.rowId, trackNumber.contextData.officialRowId)
            assertEquals(null, trackNumber.contextData.designRowId)
        }
        designDraft?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertTrue(trackNumber.isDraft)
            assertEquals(mainOfficial?.rowId, trackNumber.contextData.officialRowId)
            assertEquals(designOfficial?.rowId, trackNumber.contextData.designRowId)
        }
    }

    fun <T> assertVersionMatch(description: String, expected: LayoutRowVersion<T>?, actual: LayoutRowVersion<T>?) {
        assertEquals(expected = expected, actual = actual, message = "$description expected=$expected actual=$actual")
    }

    fun createTrackNumberAndReferenceLineAndAlignment():
        Triple<TrackLayoutTrackNumber, ReferenceLine, LayoutAlignment> {
        val saveRequest =
            TrackNumberSaveRequest(
                testDBService.getUnusedTrackNumber(),
                TrackNumberDescription(trackNumberDescription),
                LayoutState.IN_USE,
                TrackMeter(KmNumber(5555), 5.5, 1),
            )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest).id
        val trackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!

        val (referenceLine, alignment) =
            referenceLineService.getByTrackNumberWithAlignment(
                MainLayoutContext.draft,
                trackNumber.id as IntId<TrackLayoutTrackNumber>,
            )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }

    private fun publishTrackNumber(id: IntId<TrackLayoutTrackNumber>) =
        trackNumberDao.fetchCandidateVersions(MainLayoutContext.draft, listOf(id)).first().let { version ->
            trackNumberService.publish(LayoutBranch.main, version)
        }

    private fun publishReferenceLine(id: IntId<ReferenceLine>): LayoutDaoResponse<ReferenceLine> =
        referenceLineDao.fetchCandidateVersions(MainLayoutContext.draft, listOf(id)).first().let { version ->
            referenceLineService.publish(LayoutBranch.main, version)
        }
}
