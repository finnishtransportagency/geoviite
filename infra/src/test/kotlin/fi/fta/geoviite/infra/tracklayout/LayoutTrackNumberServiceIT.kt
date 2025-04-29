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
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.getBoundingPolygonPointsFromAlignments
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions
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
    private val coordinateTransformationService: CoordinateTransformationService,
    private val geometryDao: GeometryDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
        testDBService.clearGeometryTables()
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

        assertNull(trackNumberDao.fetchExternalId(LayoutBranch.main, trackNumber.id as IntId))

        val newExternalId = externalIdForTrackNumber()
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumber.id, newExternalId)

        assertEquals(newExternalId, trackNumberDao.fetchExternalId(LayoutBranch.main, trackNumber.id)?.oid)
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
            trackNumberDao.fetch(trackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)))
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
                    referenceLineDao.save(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))
                referenceLineDao.fetch(referenceLineVersion)
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
                .map(kmPostDao::save)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(3, kmLengths.size)

        assertEquals(
            LayoutKmLengthDetails(
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
            LayoutKmLengthDetails(
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
            LayoutKmLengthDetails(
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
                    referenceLineDao.save(referenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))

                referenceLineDao.fetch(referenceLineVersion)
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
                .map(kmPostDao::save)

        val kmLengths = trackNumberService.getKmLengths(MainLayoutContext.official, trackNumber.id as IntId)
        assertNotNull(kmLengths)
        assertEquals(2, kmLengths.size)

        assertEquals(
            LayoutKmLengthDetails(
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
            LayoutKmLengthDetails(
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

        val alignment = alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0)))

        val tn1 = designDraftContext.insert(trackNumber(number = TrackNumber("asdf"))).id
        val rl1 = designOfficialContext.insert(referenceLine(tn1), alignment).id
        assertEquals(rl1, designDraftContext.fetch(tn1)!!.referenceLineId)

        val tn2 = designDraftContext.insert(trackNumber(number = TrackNumber("aoeu"))).id
        val rl2 = mainOfficialContext.insert(referenceLine(tn2), alignment).id
        assertEquals(rl2, designDraftContext.fetch(tn2)!!.referenceLineId)

        val tn3 = mainDraftContext.insert(trackNumber(number = TrackNumber("arst"))).id
        val rl3 = mainOfficialContext.insert(referenceLine(tn3), alignment).id
        assertEquals(rl3, mainDraftContext.fetch(tn3)!!.referenceLineId)
    }

    @Test
    fun `overlapping plan search finds plans that are within 10m of alignment`() {
        val tn = TrackNumber("001")

        val a1 = geometryAlignment(line(Point(0.0, 0.0), Point(10.0, 0.0)))
        val a2 = geometryAlignment(line(Point(20.0, 0.0), Point(30.0, 0.0)))
        val a3 = geometryAlignment(line(Point(40.0, 0.0), Point(50.0, 0.0)))
        val a4 = geometryAlignment(line(Point(60.0, 0.0), Point(70.0, 0.0)))
        val a5 = geometryAlignment(line(Point(80.0, 0.0), Point(90.0, 0.0)))
        val a6 = geometryAlignment(line(Point(40.0, 20.0), Point(50.0, 20.0)))
        val a7 = geometryAlignment(line(Point(40.0, -10.0), Point(50.0, -10.0)))

        val tf = coordinateTransformationService.getLayoutTransformation(LAYOUT_SRID)

        val plan1EndsBeforeAlignment =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a1),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a1), tf),
            )
        val plan2EndsWithinAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a2), tf),
            )
        val plan3CompletelyWithin =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a3),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a3), tf),
            )
        val plan4TouchesEndOfAlignmentBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4), tf),
            )
        val plan5StartsAfterAlignmentEnd =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a5),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a5), tf),
            )
        val plan6TooFarToTheSide =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a6),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a6), tf),
            )
        val plan7TouchesBufferFromSide =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a7),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a7), tf),
            )
        val plan8Hidden =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4), tf),
            )
        geometryDao.setPlanHidden(plan8Hidden.id, true)

        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    alignment(segment(Point(32.0, 0.0), Point(50.0, 0.0))),
                    TrackMeter(KmNumber(0), BigDecimal(32.0)),
                )
                .id

        val overlapping =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, null, null)
                .map { it.id }

        assertEquals(4, overlapping.size)
        assertContains(overlapping, plan2EndsWithinAlignmentBuffer.id)
        assertContains(overlapping, plan3CompletelyWithin.id)
        assertContains(overlapping, plan4TouchesEndOfAlignmentBuffer.id)
        assertContains(overlapping, plan7TouchesBufferFromSide.id)
    }

    @Test
    fun `overlapping plan search cropping works correctly in a happy case`() {
        val tn = TrackNumber("001")

        val a1 = geometryAlignment(line(Point(0.0, 0.0), Point(900.0, 0.0)))
        val a2 = geometryAlignment(line(Point(500.0, 0.0), Point(995.0, 0.0)))
        val a3 = geometryAlignment(line(Point(1200.0, 0.0), Point(1500.0, 0.0)))
        val a4 = geometryAlignment(line(Point(1800.0, 0.0), Point(3200.0, 0.0)))
        val a5 = geometryAlignment(line(Point(3010.0, 0.0), Point(4000.0, 0.0)))
        val a6 = geometryAlignment(line(Point(3500.0, 0.0), Point(4000.0, 0.0)))

        val tf = coordinateTransformationService.getLayoutTransformation(LAYOUT_SRID)

        val plan1EndsBeforeStartKm =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a1),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a1), tf),
            )
        val plan2EndsBeforeStartKmButWithinBuffer =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a2), tf),
            )
        val plan3IsCompletelyWithinKmRange =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a3),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a3), tf),
            )
        val plan4StartsWithinKmRangeButEndsAfter =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a4),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a4), tf),
            )
        val plan5TouchesEndKmWhenBufferIsIncluded =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a5),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a5), tf),
            )
        val plan6IsPastEndOfEndKm =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a6),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a6), tf),
            )

        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(4000.0, 0.0))))
                .id

        val kmPost1 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(1),
                        roughLayoutLocation = Point(0.0, 0.0),
                        draft = false,
                    )
                )
            )
        val kmPost2 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(2),
                        roughLayoutLocation = Point(1000.0, 0.0),
                        draft = false,
                    )
                )
            )
        val kmPost3 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(3),
                        roughLayoutLocation = Point(2000.0, 0.0),
                        draft = false,
                    )
                )
            )
        val kmPost4 =
            kmPostDao.fetch(
                kmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumberId,
                        km = KmNumber(4),
                        roughLayoutLocation = Point(3000.0, 0.0),
                        draft = false,
                    )
                )
            )

        val overlapping =
            trackNumberService
                .getOverlappingPlanHeaders(
                    mainOfficialContext.context,
                    trackNumberId,
                    10.0,
                    kmPost2.kmNumber,
                    kmPost3.kmNumber,
                )
                .map { it.id }
        assertEquals(4, overlapping.size)
        assertContains(overlapping, plan2EndsBeforeStartKmButWithinBuffer.id)
        assertContains(overlapping, plan3IsCompletelyWithinKmRange.id)
        assertContains(overlapping, plan4StartsWithinKmRangeButEndsAfter.id)
        assertContains(overlapping, plan5TouchesEndKmWhenBufferIsIncluded.id)
    }

    @Test
    fun `overlapping plan search cropping works correctly in different edge cases`() {
        val tn = TrackNumber("001")

        val a1 = geometryAlignment(line(Point(0.0, 0.0), Point(500.0, 0.0)))
        val a2 = geometryAlignment(line(Point(1000.0, 0.0), Point(4000.0, 0.0)))
        val a3 = geometryAlignment(line(Point(5000.0, 0.0), Point(7000.0, 0.0)))

        val tf = coordinateTransformationService.getLayoutTransformation(LAYOUT_SRID)

        val plan1EndsBeforeLocationTrackStart =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a1),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a1), tf),
            )
        val plan2IsWithinLocationTrack =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a2),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a2), tf),
            )
        val plan3IsPastEndOfEndKm =
            geometryDao.insertPlan(
                plan(tn, LAYOUT_SRID, a3),
                testFile(),
                getBoundingPolygonPointsFromAlignments(listOf(a3), tf),
            )

        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    alignment(segment(Point(1500.0, 0.0), Point(4000.0, 0.0))),
                    TrackMeter(KmNumber(1), BigDecimal(500.0)),
                )
                .id

        kmPostDao.fetch(
            kmPostDao.save(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(1),
                    roughLayoutLocation = Point(1000.0, 0.0),
                    draft = false,
                )
            )
        )
        kmPostDao.fetch(
            kmPostDao.save(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(2),
                    roughLayoutLocation = Point(2000.0, 0.0),
                    draft = false,
                )
            )
        )
        kmPostDao.fetch(
            kmPostDao.save(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(3),
                    roughLayoutLocation = Point(3000.0, 0.0),
                    draft = false,
                )
            )
        )
        kmPostDao.fetch(
            kmPostDao.save(
                kmPost(
                    trackNumberId = trackNumberId,
                    km = KmNumber(4),
                    roughLayoutLocation = Point(4000.0, 0.0),
                    draft = false,
                )
            )
        )

        val overlappingEntireTrackNumber =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, KmNumber(0), KmNumber(6))
                .map { it.id }
        Assertions.assertEquals(1, overlappingEntireTrackNumber.size)
        assertContains(overlappingEntireTrackNumber, plan2IsWithinLocationTrack.id)

        val withinPlanAreaButNotWithinTrackNumber =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, KmNumber(0), KmNumber(0))
                .map { it.id }
        Assertions.assertEquals(0, withinPlanAreaButNotWithinTrackNumber.size)

        val startIsBeforeTrackNumberAndEndIsNull =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, KmNumber(1), null)
                .map { it.id }
        Assertions.assertEquals(1, startIsBeforeTrackNumberAndEndIsNull.size)

        val endIsAfterTrackNumberEndAndStartIsNull =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, null, KmNumber(5))
                .map { it.id }
        Assertions.assertEquals(1, endIsAfterTrackNumberEndAndStartIsNull.size)

        val startIsAfterTrackNumberEndAndEndIsNull =
            trackNumberService
                .getOverlappingPlanHeaders(mainOfficialContext.context, trackNumberId, 10.0, KmNumber(5), null)
                .map { it.id }
        Assertions.assertEquals(0, startIsAfterTrackNumberEndAndEndIsNull.size)
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
        assertEquals(expected = expected, actual = actual, message = "$description expected=$expected actual=$actual")
    }

    fun createTrackNumberAndReferenceLineAndAlignment(): Triple<LayoutTrackNumber, ReferenceLine, LayoutAlignment> {
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
