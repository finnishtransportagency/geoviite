package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
                TrackMeter(KmNumber(5555), 5.5, 3),
            )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest).id
        val trackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!

        assertNull(trackNumberDao.fetchExternalId(LayoutBranch.main, trackNumber.id as IntId))

        val newExternalId = externalIdForTrackNumber()
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumber.id, newExternalId)

        assertEquals(newExternalId, trackNumberDao.fetchExternalId(LayoutBranch.main, trackNumber.id)?.oid)
    }

    @Test
    fun `deleting draft only TrackNumber deletes it and ReferenceLine with geometry`() {
        val (trackNumber, referenceLine, geometry) = createTrackNumberAndReferenceLineAndAlignment()
        assertEquals(referenceLine.geometryVersion?.id, geometry.id as IntId)
        val trackNumberId = trackNumber.id as IntId

        assertDoesNotThrow { trackNumberService.deleteDraft(LayoutBranch.main, trackNumberId) }
        assertThrows<NoSuchEntityException> {
            referenceLineService.getOrThrow(MainLayoutContext.draft, referenceLine.id as IntId)
        }
        assertThrows<NoSuchEntityException> { trackNumberService.getOrThrow(MainLayoutContext.draft, trackNumberId) }
        assertFalse(alignmentDao.fetchVersions().map { rv -> rv.id }.contains(geometry.id))
    }

    @Test
    fun tryingToDeletePublishedTrackNumberThrows() {
        val (trackNumber, referenceLine, _) = createTrackNumberAndReferenceLineAndAlignment()
        publishTrackNumber(trackNumber.id as IntId)
        publishReferenceLine(referenceLine.id as IntId)

        assertThrows<DeletingFailureException> { trackNumberService.deleteDraft(LayoutBranch.main, trackNumber.id) }
    }

    @Test
    fun `should return correct lengths for km posts`() {
        val trackNumber =
            trackNumberDao.fetch(trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)))
        val trackOid = externalIdForTrackNumber()
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumber.id as IntId, trackOid)

        referenceLineAndGeometry(
                trackNumberId = trackNumber.id as IntId,
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
                    ),
                startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
                draft = false,
            )
            .let { (referenceLine, geometry) ->
                val referenceLineVersion =
                    referenceLineDao.save(referenceLine.copy(geometryVersion = alignmentDao.insert(geometry)))
                referenceLineDao.fetch(referenceLineVersion)
            }

        val kmPostVersions =
            listOf(
                    kmPost(
                        trackNumberId = trackNumber.id,
                        km = KmNumber(2),
                        gkLocation = kmPostGkLocation(1.0, 0.0),
                        draft = false,
                    ),
                    kmPost(
                        trackNumberId = trackNumber.id,
                        km = KmNumber(3),
                        gkLocation = kmPostGkLocation(3.0, 0.0),
                        draft = false,
                    ),
                )
                .map(kmPostDao::save)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id)
        assertNotNull(kmLengths)
        assertEquals(3, kmLengths.size)

        assertEquals(
            LayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                trackNumberOid = trackOid,
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
            LayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                trackNumberOid = trackOid,
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
            LayoutKmLengthDetails(
                trackNumberOid = trackOid,
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
            trackNumberDao.fetch(trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)))

        referenceLineAndGeometry(
                trackNumberId = trackNumber.id as IntId,
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0))
                    ),
                startAddress = TrackMeter(KmNumber(1), BigDecimal(0.5)),
                draft = false,
            )
            .let { (referenceLine, geometry) ->
                val referenceLineVersion =
                    referenceLineDao.save(referenceLine.copy(geometryVersion = alignmentDao.insert(geometry)))

                referenceLineDao.fetch(referenceLineVersion)
            }

        val kmPostVersions =
            listOf(
                    kmPost(
                        trackNumberId = trackNumber.id,
                        km = KmNumber(2),
                        gkLocation = kmPostGkLocation(1.0, 0.0),
                        draft = false,
                    ),
                    kmPost(trackNumberId = trackNumber.id, km = KmNumber(3), gkLocation = null, draft = false),
                )
                .map(kmPostDao::save)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id)
        assertNotNull(kmLengths)
        assertEquals(2, kmLengths.size)

        assertEquals(
            LayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                trackNumberOid = null,
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
            LayoutKmLengthDetails(
                trackNumber = trackNumber.number,
                trackNumberOid = null,
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
        assertVersionReferences(designBranch, tnId, designDraft = designDraft1)

        val designOfficial = trackNumberService.publish(designBranch, designDraft1).published
        assertVersionReferences(designBranch, tnId, designOfficial = designOfficial)

        val mainDraft = trackNumberService.mergeToMainBranch(designBranch, tnId)
        assertVersionReferences(designBranch, tnId, mainDraft = mainDraft, designOfficial = designOfficial)

        val designDraft2 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v2"))
        assertVersionReferences(
            designBranch,
            tnId,
            mainDraft = mainDraft,
            designOfficial = designOfficial,
            designDraft = designDraft2,
        )

        val mainPublication = trackNumberService.publish(LayoutBranch.main, mainDraft)
        val mainOfficial = mainPublication.published
        val designMergerDraft = mainPublication.completed!!.second
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial,
            designOfficial = designOfficial,
            designDraft = designMergerDraft,
        )
    }

    @Test
    fun `Publishing an edited track number from design with a draft leaves the correct rows and references`() {
        val designBranch = testDBService.createDesignBranch()
        val trackNumber = testDBService.getUnusedTrackNumber()

        val mainDraft1 =
            trackNumberService.insert(LayoutBranch.main, trackNumberSaveRequest(trackNumber, "$trackNumber v1"))
        val tnId = mainDraft1.id
        assertVersionReferences(designBranch, tnId, mainDraft = mainDraft1)

        val mainOfficial1 = trackNumberService.publish(LayoutBranch.main, mainDraft1).published
        referenceLineService.getByTrackNumber(MainLayoutContext.draft, tnId).let { rl ->
            referenceLineService.publish(LayoutBranch.main, rl!!.version!!)
        }
        assertVersionReferences(designBranch, tnId, mainOfficial = mainOfficial1)

        val designDraft1 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v2"))
        assertVersionReferences(designBranch, tnId, mainOfficial = mainOfficial1, designDraft = designDraft1)

        val designOfficial = trackNumberService.publish(designBranch, designDraft1).published
        assertVersionReferences(designBranch, tnId, mainOfficial = mainOfficial1, designOfficial = designOfficial)

        val mainDraft2 = trackNumberService.mergeToMainBranch(designBranch, tnId)
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1,
            mainDraft = mainDraft2,
            designOfficial = designOfficial,
        )

        val designDraft2 =
            trackNumberService.update(designBranch, tnId, trackNumberSaveRequest(trackNumber, "$trackNumber v3"))
        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial1,
            mainDraft = mainDraft2,
            designOfficial = designOfficial,
            designDraft = designDraft2,
        )

        val afterMainPublication2 = trackNumberService.publish(LayoutBranch.main, mainDraft2)
        val mainOfficial2 = afterMainPublication2.published
        val designMergerDraft = afterMainPublication2.completed!!.second

        assertVersionReferences(
            designBranch,
            tnId,
            mainOfficial = mainOfficial2,
            designOfficial = designOfficial,
            designDraft = designMergerDraft,
        )
    }

    @Test
    fun `draft track number can find reference line in any above context`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val designOfficialContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val geometry = referenceLineGeometry(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val tn1 = designDraftContext.save(trackNumber(number = TrackNumber("asdf"))).id
        val rl1 = designOfficialContext.save(referenceLine(tn1), geometry).id
        assertEquals(rl1, designDraftContext.fetch(tn1)!!.referenceLineId)

        val tn2 = designDraftContext.save(trackNumber(number = TrackNumber("aoeu"))).id
        val rl2 = mainOfficialContext.save(referenceLine(tn2), geometry).id
        assertEquals(rl2, designDraftContext.fetch(tn2)!!.referenceLineId)

        val tn3 = mainDraftContext.save(trackNumber(number = TrackNumber("arst"))).id
        val rl3 = mainOfficialContext.save(referenceLine(tn3), geometry).id
        assertEquals(rl3, mainDraftContext.fetch(tn3)!!.referenceLineId)
    }

    @Test
    fun `ReferenceLine polygon is simplified to reduce point count`() {
        val referenceLineSegment = segment(Point(0.0, 0.0), Point(2000.0, 0.0))
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    referenceLineGeometry = referenceLineGeometry(referenceLineSegment),
                    startAddress = TrackMeter(KmNumber(0), BigDecimal(0.0)),
                )
                .id
        assertTrue(referenceLineSegment.segmentPoints.size > 900)
        val polygon =
            trackNumberService.getReferenceLinePolygon(MainLayoutContext.official, trackNumberId, null, null, 10.0)!!
        assertTrue(polygon.points.size < 50)
    }

    @Test
    fun `ReferenceLine polygon is resolved correctly without cropping`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    referenceLineGeometry(segment(Point(32.0, 0.0), Point(50.0, 0.0))),
                    startAddress = TrackMeter(KmNumber(0), BigDecimal(32.0)),
                )
                .id

        val polygon =
            trackNumberService.getReferenceLinePolygon(mainOfficialContext.context, trackNumberId, null, null, 10.0)!!

        // Should be inside the buffered (+10m) polygon
        assertContains(
            polygon,
            // Start and end points
            Point(32.0, 0.0),
            Point(50.0, 0.0),
            // Diagonally beyond end points but inside the buffer
            Point(27.0, -5.0),
            Point(27.0, 5.0),
            Point(55.0, -5.0),
            Point(55.0, 5.0),
            // To the sides but inside the buffer
            Point(40.0, -5.0),
            Point(40.0, 5.0),
        )

        // Should not be inside the buffered polygon
        assertDoesntContain(
            polygon,
            Point(20.0, 0.0),
            Point(61.0, 0.0),
            Point(23.0, -8.0),
            Point(23.0, 8.0),
            Point(58.0, -8.0),
            Point(58.0, 8.0),
            Point(40.0, -11.0),
            Point(40.0, 11.0),
        )
    }

    @Test
    fun `ReferenceLine polygon is resolved correctly with cropping`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(4000.0, 0.0)))
                )
                .id

        mainOfficialContext.saveAndFetch(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(1), gkLocation = kmPostGkLocation(0.0, 0.0))
        )
        val kmPost2 =
            mainOfficialContext.saveAndFetch(
                kmPost(trackNumberId = trackNumberId, km = KmNumber(2), gkLocation = kmPostGkLocation(1000.0, 0.0))
            )
        val kmPost3 =
            mainOfficialContext.saveAndFetch(
                kmPost(trackNumberId = trackNumberId, km = KmNumber(3), gkLocation = kmPostGkLocation(2000.0, 0.0))
            )
        mainOfficialContext.saveAndFetch(
            kmPost(trackNumberId = trackNumberId, km = KmNumber(4), gkLocation = kmPostGkLocation(3000.0, 0.0))
        )

        val polygon =
            trackNumberService.getReferenceLinePolygon(
                mainOfficialContext.context,
                trackNumberId,
                kmPost2.kmNumber,
                kmPost3.kmNumber,
                10.0,
            )!!

        // Should be inside the buffered (+10m) polygon: the cropped area includes km 1 & 2 -> x=[1000..3000]
        assertContains(
            polygon,
            // The cropping km locations
            Point(1000.0, 0.0),
            Point(3000.0, 0.0),
            // Something in the middle and to the sides within buffer
            Point(1100.0, 8.0),
            Point(2900.0, -8.0),
            // Still inside buffer
            Point(995.0, -5.0),
            Point(995.0, 5.0),
            Point(3005.0, -5.0),
            Point(3005.0, 5.0),
        )

        // Should not be inside the buffered polygon
        assertDoesntContain(polygon, Point(980.0, 0.0), Point(3020.0, 0.0), Point(2000.0, 20.0), Point(2000.0, -20.0))
    }

    @Test
    fun `overlapping plan search cropping works correctly in different edge cases`() {
        val id =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    referenceLineGeometry(segment(Point(1500.0, 0.0), Point(3500.0, 0.0))),
                    startAddress = TrackMeter(KmNumber(1), BigDecimal(500.0)),
                )
                .id

        mainOfficialContext.save(
            kmPost(trackNumberId = id, km = KmNumber(1), gkLocation = kmPostGkLocation(1000.0, 0.0))
        )
        mainOfficialContext.save(
            kmPost(trackNumberId = id, km = KmNumber(2), gkLocation = kmPostGkLocation(2000.0, 0.0))
        )
        mainOfficialContext.save(
            kmPost(trackNumberId = id, km = KmNumber(3), gkLocation = kmPostGkLocation(3000.0, 0.0))
        )
        mainOfficialContext.save(
            kmPost(trackNumberId = id, km = KmNumber(4), gkLocation = kmPostGkLocation(4000.0, 0.0))
        )

        fun getPolygon(startKm: KmNumber?, endKm: KmNumber?) =
            trackNumberService.getReferenceLinePolygon(MainLayoutContext.official, id, startKm, endKm, 1.0)

        // Full polygon without cropping
        val fullTrackPolygon =
            getPolygon(null, null)!!.also { polygon ->
                // Include whole reference line
                assertContains(polygon, Point(1500.0, 0.0), Point(3500.0, 0.0))
                // Don't include outside reference line
                assertDoesntContain(polygon, Point(1490.0, 0.0), Point(3510.0, 0.0))
            }

        // Crops with entire reference line -> full polygon
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(0), KmNumber(6)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(1), KmNumber(5)))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(0), null))
        assertEquals(fullTrackPolygon, getPolygon(KmNumber(1), null))
        assertEquals(fullTrackPolygon, getPolygon(null, KmNumber(4)))
        assertEquals(fullTrackPolygon, getPolygon(null, KmNumber(6)))

        // Crops outside reference line -> no polygon
        assertNull(getPolygon(null, KmNumber(0)))
        assertNull(getPolygon(KmNumber(5), null))
        assertNull(getPolygon(KmNumber(0), KmNumber(0)))
        assertNull(getPolygon(KmNumber(5), KmNumber(5)))
    }

    @Test
    fun `idMatches finds track numbers even if ids or oids need trimming`() {
        val tn1 = mainOfficialContext.save(trackNumber(TrackNumber("001"))).let { mainOfficialContext.fetch(it.id) }!!
        val tn2 = mainOfficialContext.save(trackNumber(TrackNumber("002"))).let { mainOfficialContext.fetch(it.id) }!!
        val track2oid = externalIdForTrackNumber()
        trackNumberService.insertExternalId(LayoutBranch.main, tn2.id as IntId, track2oid)

        val intIdTerm = FreeText(" ${tn1.id} ")
        val intIdMatchFunction = trackNumberService.idMatches(MainLayoutContext.official, intIdTerm, null)

        val oidTerm = FreeText(" $track2oid ")
        val oidMatchFunction = trackNumberService.idMatches(MainLayoutContext.official, oidTerm, null)

        assertTrue(intIdMatchFunction(intIdTerm.toString(), tn1))
        assertTrue(oidMatchFunction(oidTerm.toString(), tn2))
    }

    private fun assertVersionReferences(
        designBranch: DesignBranch,
        id: IntId<LayoutTrackNumber>,
        mainOfficial: LayoutRowVersion<LayoutTrackNumber>? = null,
        mainDraft: LayoutRowVersion<LayoutTrackNumber>? = null,
        designOfficial: LayoutRowVersion<LayoutTrackNumber>? = null,
        designDraft: LayoutRowVersion<LayoutTrackNumber>? = null,
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
        }
        mainDraft?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertTrue(trackNumber.isDraft)
        }
        designOfficial?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertFalse(trackNumber.isDraft)
        }
        designDraft?.let(trackNumberDao::fetch)?.let { trackNumber ->
            assertEquals(id, trackNumber.id)
            assertTrue(trackNumber.isDraft)
        }
    }

    fun <T : LayoutAsset<T>> assertVersionMatch(
        description: String,
        expected: LayoutRowVersion<T>?,
        actual: LayoutRowVersion<T>?,
    ) {
        assertEquals(expected, actual, "$description expected=$expected actual=$actual")
    }

    fun createTrackNumberAndReferenceLineAndAlignment():
        Triple<LayoutTrackNumber, ReferenceLine, ReferenceLineGeometry> {
        val saveRequest =
            TrackNumberSaveRequest(
                testDBService.getUnusedTrackNumber(),
                TrackNumberDescription(trackNumberDescription),
                LayoutState.IN_USE,
                TrackMeter(KmNumber(5555), 5.5, 3),
            )
        val id = trackNumberService.insert(LayoutBranch.main, saveRequest).id
        val trackNumber = trackNumberService.get(MainLayoutContext.draft, id)!!

        val (referenceLine, alignment) =
            referenceLineService.getByTrackNumberWithGeometry(
                MainLayoutContext.draft,
                trackNumber.id as IntId<LayoutTrackNumber>,
            )!! // Always exists, since we just created it

        return Triple(trackNumber, referenceLine, alignment)
    }

    private fun publishTrackNumber(id: IntId<LayoutTrackNumber>) =
        trackNumberDao.fetchCandidateVersions(MainLayoutContext.draft, listOf(id)).first().let { version ->
            trackNumberService.publish(LayoutBranch.main, version)
        }

    private fun publishReferenceLine(id: IntId<ReferenceLine>): LayoutRowVersion<ReferenceLine> =
        referenceLineDao.fetchCandidateVersions(MainLayoutContext.draft, listOf(id)).first().let { version ->
            referenceLineService.publish(LayoutBranch.main, version).published
        }
}
