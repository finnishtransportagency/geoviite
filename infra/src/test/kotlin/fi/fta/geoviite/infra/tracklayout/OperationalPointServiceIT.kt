package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.error.SavingFailureException
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.ratko.RatkoTestService
import kotlin.test.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class OperationalPointServiceIT
@Autowired
constructor(
    private val operationalPointService: OperationalPointService,
    private val operationalPointDao: OperationalPointDao,
    private val ratkoTestService: RatkoTestService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
        testDBService.deleteFromTables(
            "layout",
            "operational_point_id",
            "operational_point_external_id",
            "track_number_id",
        )
        testDBService.deleteFromTables("integrations", "ratko_operational_point", "ratko_operational_point_version")
    }

    @Test
    fun `list() can find points by id or bbox or both`() {
        val a = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("a"))
        val b = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("b"))
        operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("c"))
        assertEquals(
            listOf(a, b),
            operationalPointService
                .list(mainDraftContext.context, ids = listOf(a.id, b.id))
                .sortedBy { it.name.toString() }
                .map { it.version },
        )
        operationalPointService.updateLocation(LayoutBranch.main, a.id, Point(123.0, 456.0))
        operationalPointService.updateLocation(LayoutBranch.main, b.id, Point(223.0, 456.0))

        assertEquals(
            listOf(a.id),
            operationalPointService
                .list(mainDraftContext.context, locationBbox = BoundingBox(100.0..200.0, 400.0..500.0))
                .sortedBy { it.name.toString() }
                .map { it.id },
        )

        assertEquals(
            listOf(a.id),
            operationalPointService
                .list(
                    mainDraftContext.context,
                    ids = listOf(a.id, b.id),
                    locationBbox = BoundingBox(100.0..200.0, 400.0..500.0),
                )
                .sortedBy { it.name.toString() }
                .map { it.id },
        )

        assertEquals(
            listOf<DomainId<OperationalPoint>>(),
            operationalPointService
                .list(
                    mainDraftContext.context,
                    ids = listOf(b.id),
                    locationBbox = BoundingBox(100.0..200.0, 400.0..500.0),
                )
                .sortedBy { it.name.toString() }
                .map { it.id },
        )
    }

    @Test
    fun `can update internal operational point`() {
        val original = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("original"))
        val updated =
            operationalPointService
                .update(
                    LayoutBranch.main,
                    original.id,
                    internalPointUpdateRequest(
                        name = "updated",
                        abbreviation = "upd",
                        rinfType = OperationalPointRinfType.PASSENGER_TERMINAL,
                        state = OperationalPointState.DELETED,
                        uicCode = "20202",
                    ),
                )
                .let { version -> operationalPointService.getOrThrow(mainDraftContext.context, version.id) }
        assertEquals("updated", updated.name.toString())
        assertEquals("upd", updated.abbreviation.toString())
        assertEquals(OperationalPointRinfType.PASSENGER_TERMINAL, updated.rinfType)
        assertEquals(OperationalPointState.DELETED, updated.state)
        assertEquals("20202", updated.uicCode.toString())
    }

    @Test
    fun `cannot update external operational point with internal request`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val external =
            testDBService
                .save(
                    operationalPoint(
                        contextData = createMainContext(externalPointId, true),
                        origin = OperationalPointOrigin.RATKO,
                        ratkoVersion = 1,
                    )
                )
                .id

        assertThrows<SavingFailureException> {
            operationalPointService.update(LayoutBranch.main, external, internalPointUpdateRequest("updated"))
        }
    }

    @Test
    fun `can update external operational point`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val original =
            testDBService.save(
                operationalPoint(
                    contextData = createMainContext(externalPointId, true),
                    origin = OperationalPointOrigin.RATKO,
                    ratkoVersion = 1,
                )
            )
        val updated =
            operationalPointService
                .update(
                    LayoutBranch.main,
                    original.id,
                    externalPointSaveRequest(rinfType = OperationalPointRinfType.FREIGHT_TERMINAL),
                )
                .let { version -> operationalPointService.get(mainDraftContext.context, version.id)!! }
        assertEquals(OperationalPointRinfType.FREIGHT_TERMINAL, updated.rinfType)
    }

    @Test
    fun `cannot update internal operational point with external request`() {
        val internal = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("internal")).id

        assertThrows<SavingFailureException> {
            operationalPointService.update(
                LayoutBranch.main,
                internal,
                externalPointSaveRequest(rinfType = OperationalPointRinfType.FREIGHT_TERMINAL),
            )
        }
    }

    @Test
    fun `locations and areas can be saved`() {
        val a = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("a")).id
        val location = Point(123.4, 567.8)
        val area =
            Polygon(
                Point(100.0, 550.0),
                Point(130.0, 550.0),
                Point(130.0, 580.0),
                Point(100.0, 580.0),
                Point(100.0, 550.0),
            )
        operationalPointService.updateLocation(LayoutBranch.main, a, location)
        operationalPointService.updatePolygon(LayoutBranch.main, a, area)
        val updated = operationalPointService.get(mainDraftContext.context, a)!!
        assertEquals(location, updated.location)
        assertEquals(area, updated.polygon)
    }

    @Test
    fun `publication assigns an oid to not-yet-oided operational points`() {
        val firstDraft = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("a"))
        assertNull(operationalPointDao.fetchExternalId(LayoutBranch.main, firstDraft.id))
        operationalPointService.publish(LayoutBranch.main, firstDraft)
        val assignedOid = operationalPointDao.fetchExternalId(LayoutBranch.main, firstDraft.id)
        assertNotNull(assignedOid)
        operationalPointService.update(LayoutBranch.main, firstDraft.id, internalPointUpdateRequest("b"))
        operationalPointService.publish(LayoutBranch.main, firstDraft)
        val oidAfterSecondPublication = operationalPointDao.fetchExternalId(LayoutBranch.main, firstDraft.id)
        assertEquals(assignedOid.oid, oidAfterSecondPublication?.oid)
    }

    @Test
    fun `can list operational points by polygon`() {
        val a =
            mainDraftContext.save(
                operationalPoint(
                    location = Point(7.0, 7.0),
                    polygon =
                        Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0)),
                )
            )
        assertEquals(
            listOf<OperationalPoint>(),
            operationalPointService.list(mainDraftContext.context, locationBbox = BoundingBox(0.0..5.0, 0.0..5.0)),
        )
        assertEquals(
            listOf(a.id),
            operationalPointService.list(mainDraftContext.context, polygonBbox = BoundingBox(0.0..5.0, 0.0..5.0)).map {
                it.id
            },
        )
        // it's arbitrary that we consider this a match when the bounding box only meets the polygon, but let's pick a
        // behavior to fix
        assertEquals(
            listOf(a.id),
            operationalPointService
                .list(mainDraftContext.context, polygonBbox = BoundingBox(10.0..20.0, 10.0..20.0))
                .map { it.id },
        )
        assertEquals(
            listOf<OperationalPoint>(),
            operationalPointService.list(mainDraftContext.context, polygonBbox = BoundingBox(15.0..20.0, 15.0..20.0)),
        )
    }

    @Test
    fun `deleting an external operational point draft with no pending Ratko version update does delete the draft`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val officialPointVersion =
            testDBService.save(
                operationalPoint(
                    contextData = createMainContext(externalPointId, false),
                    origin = OperationalPointOrigin.RATKO,
                    ratkoVersion = 1,
                )
            )
        val draftPointVersion = mainDraftContext.copyFrom(officialPointVersion)
        operationalPointService.deleteDraft(LayoutBranch.main, draftPointVersion.id)
        assertEquals(officialPointVersion, mainDraftContext.fetchVersion(officialPointVersion.id))
    }

    @Test
    fun `deleting a draft-only external operational point draft instead resets the point`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val point =
            testDBService
                .save(
                    operationalPoint(
                        contextData = createMainContext(externalPointId, true),
                        origin = OperationalPointOrigin.RATKO,
                        ratkoVersion = 1,
                    )
                )
                .id
        operationalPointService.deleteDraft(LayoutBranch.main, point)

        assertEquals(null, mainDraftContext.fetch(point)?.rinfType)
        assertEquals(1, mainDraftContext.fetch(point)?.ratkoVersion)
    }

    @Test
    fun `deleting an external operational point draft retains state from the integration`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val officialVersion =
            testDBService.save(
                operationalPoint(
                    contextData = createMainContext(externalPointId, false),
                    origin = OperationalPointOrigin.RATKO,
                    ratkoVersion = 1,
                )
            )
        ratkoTestService.updateRatkoOperationalPoints()

        operationalPointService.deleteDraft(LayoutBranch.main, officialVersion.id)
        assertEquals(OperationalPointState.DELETED, mainDraftContext.fetch(officialVersion.id)?.state)
    }

    @Test
    fun `deleting a draft-only external operational point draft retains state from the integration`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val draftVersion =
            testDBService.save(
                operationalPoint(
                    contextData = createMainContext(externalPointId, true),
                    origin = OperationalPointOrigin.RATKO,
                    ratkoVersion = 1,
                )
            )
        ratkoTestService.updateRatkoOperationalPoints()

        operationalPointService.deleteDraft(LayoutBranch.main, draftVersion.id)
        assertEquals(OperationalPointState.DELETED, mainDraftContext.fetch(draftVersion.id)?.state)
    }

    @Test
    fun `deleting a drafted update to an external operational point draft instead resets the point to the official state`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val polygon = Polygon(Point(0.0, 0.0), Point(11.0, 0.0), Point(11.0, 11.0), Point(0.0, 11.0), Point(0.0, 0.0))
        val point =
            testDBService
                .save(
                    operationalPoint(
                        contextData = createMainContext(externalPointId, false),
                        origin = OperationalPointOrigin.RATKO,
                        ratkoVersion = 1,
                        rinfType = OperationalPointRinfType.DEPOT_OR_WORKSHOP,
                        polygon = polygon,
                    )
                )
                .id
        ratkoTestService.updateRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "changed external"))
        operationalPointService.update(
            LayoutBranch.main,
            point,
            ExternalOperationalPointSaveRequest(OperationalPointRinfType.SMALL_STATION, null),
        )

        // initially we have a pending update of the operational point's Ratko version to publish; as well as the
        // RINF type assignment above
        assertEquals(1, mainOfficialContext.fetch(point)?.ratkoVersion)
        assertEquals(2, mainDraftContext.fetch(point)?.ratkoVersion)

        operationalPointService.deleteDraft(LayoutBranch.main, point)

        // after deleteDraft, the RINF type assignment has been reset, but the Ratko version update is still pending
        assertEquals(OperationalPointRinfType.DEPOT_OR_WORKSHOP, mainDraftContext.fetch(point)?.rinfType)
        assertEquals(polygon, mainDraftContext.fetch(point)?.polygon)
        assertEquals(1, mainOfficialContext.fetch(point)?.ratkoVersion)
        assertEquals(2, mainDraftContext.fetch(point)?.ratkoVersion)
    }

    @Test
    fun `deleteDraft clears references from location tracks and switches when operational point is draft-only`() {
        val opId = mainDraftContext.save(operationalPoint()).id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val locationTrack = mainDraftContext.save(locationTrack(trackNumberId, operationalPointIds = setOf(opId))).id
        val switch = mainDraftContext.save(switch(operationalPointId = opId)).id

        operationalPointService.deleteDraft(LayoutBranch.main, opId)

        val trackAfter = locationTrackService.getOrThrow(mainDraftContext.context, locationTrack)
        assertEquals(emptySet<Nothing>(), trackAfter.operationalPointIds)
        val switchAfter = switchService.getOrThrow(mainDraftContext.context, switch)
        assertNull(switchAfter.operationalPointId)
    }

    @Test
    fun `deleteDraft does not clear references when operational point also has an official version`() {
        val officialOp = mainOfficialContext.save(operationalPoint())
        mainDraftContext.copyFrom(officialOp)
        val opId = officialOp.id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val locationTrackVersion =
            mainDraftContext.save(locationTrack(trackNumberId, operationalPointIds = setOf(opId)))
        val switchVersion = mainDraftContext.save(switch(operationalPointId = opId))

        operationalPointService.deleteDraft(LayoutBranch.main, opId)

        // After deletion: references remain because the official OP still exists
        val trackAfter = locationTrackService.getOrThrow(mainDraftContext.context, locationTrackVersion.id)
        assertEquals(setOf(opId), trackAfter.operationalPointIds)
        val switchAfter = switchService.getOrThrow(mainDraftContext.context, switchVersion.id)
        assertEquals(opId, switchAfter.operationalPointId)
    }

    @Test
    fun `mergeToMainBranch syncs all fields from design branch for Geoviite points`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val officialVersion =
            mainOfficialContext.save(
                operationalPoint(name = "original", state = OperationalPointState.IN_USE, draft = false)
            )

        mainDraftContext.save(
            asMainDraft(mainOfficialContext.fetch(officialVersion.id)!!).copy(state = OperationalPointState.DELETED)
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext
                        .fetch(officialVersion.id)!!
                        .copy(
                            name = OperationalPointName("edited in design"),
                            rinfType = OperationalPointRinfType.FREIGHT_TERMINAL,
                            state = OperationalPointState.IN_USE,
                        ),
                    designBranch.designId,
                )
            )
        )

        operationalPointService.mergeToMainBranch(designBranch, officialVersion.id)

        val merged = mainDraftContext.fetch(officialVersion.id)
        assertNotNull(merged)
        assertEquals("edited in design", merged.name.toString())
        assertEquals(OperationalPointRinfType.FREIGHT_TERMINAL, merged.rinfType)
        assertEquals(OperationalPointState.IN_USE, merged.state)
        assertNull(merged.ratkoVersion)
    }

    @Test
    fun `mergeToMainBranch preserves ratkoVersion and state from existing main-draft for Ratko points`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(designBranch, PublicationState.OFFICIAL)
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "ratko op"))[0]

        testDBService.save(
            operationalPoint(
                contextData = createMainContext(externalPointId, false),
                origin = OperationalPointOrigin.RATKO,
                ratkoVersion = 1,
                rinfType = OperationalPointRinfType.STATION,
            )
        )

        designOfficialContext.moveFrom(
            designDraftContext.save(
                asDesignDraft(
                    mainOfficialContext
                        .fetch(externalPointId)!!
                        .copy(rinfType = OperationalPointRinfType.FREIGHT_TERMINAL),
                    designBranch.designId,
                )
            )
        )

        // Delete operational point from Ratko
        ratkoTestService.updateRatkoOperationalPoints()
        val mainDraftPointAfterDelete = mainDraftContext.fetch(externalPointId)!!
        assertEquals(2, mainDraftPointAfterDelete.ratkoVersion)
        assertEquals(OperationalPointState.DELETED, mainDraftPointAfterDelete.state)

        // Make sure the operational point still exists in design
        val designDraftAfterMainDelete = designDraftContext.fetch(externalPointId)!!
        assertEquals(OperationalPointState.IN_USE, designDraftAfterMainDelete.state)
        assertEquals(1, designDraftAfterMainDelete.ratkoVersion)

        // Merge to main
        operationalPointService.mergeToMainBranch(designBranch, externalPointId)

        // Make sure that the operational poin in main draft is an unholy union of the design point's user modifiable
        // fields and the Ratko version's state and Ratko version
        val merged = mainDraftContext.fetch(externalPointId)
        assertNotNull(merged)
        assertEquals(OperationalPointRinfType.FREIGHT_TERMINAL, merged.rinfType)
        assertEquals(OperationalPointState.DELETED, merged.state)
        assertEquals(2, merged.ratkoVersion)
    }

    private fun internalPointSaveRequest(
        name: String = "name",
        abbreviation: String = name,
        rinfType: OperationalPointRinfType = OperationalPointRinfType.SMALL_STATION,
        state: OperationalPointState = OperationalPointState.IN_USE,
        uicCode: String = "10101",
        rinfIdOverride: RinfId? = null,
    ) =
        InternalOperationalPointSaveRequest(
            OperationalPointInputName(name),
            OperationalPointInputAbbreviation(abbreviation),
            rinfType,
            state,
            UicCode(uicCode),
            rinfIdOverride,
        )

    private fun internalPointUpdateRequest(
        name: String = "name",
        abbreviation: String = name,
        rinfType: OperationalPointRinfType = OperationalPointRinfType.SMALL_STATION,
        state: OperationalPointState = OperationalPointState.IN_USE,
        uicCode: String = "10101",
        rinfIdOverride: RinfId? = null,
        severLinks: Boolean = false,
    ) =
        InternalOperationalPointUpdateRequest(
            OperationalPointInputName(name),
            OperationalPointInputAbbreviation(abbreviation),
            rinfType,
            state,
            UicCode(uicCode),
            rinfIdOverride,
            severLinks,
        )

    private fun externalPointSaveRequest(
        rinfType: OperationalPointRinfType = OperationalPointRinfType.SMALL_STATION,
        rinfIdOverride: RinfId? = null,
    ) = ExternalOperationalPointSaveRequest(rinfType, rinfIdOverride)

    @Test
    fun `update with severLinks = true clears references from location tracks and switches`() {
        val opId = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("op")).id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val locationTrack = mainDraftContext.save(locationTrack(trackNumberId, operationalPointIds = setOf(opId))).id
        val switch = mainDraftContext.save(switch(operationalPointId = opId)).id

        operationalPointService.update(
            LayoutBranch.main,
            opId,
            internalPointUpdateRequest(name = "op", state = OperationalPointState.DELETED, severLinks = true),
        )

        val trackAfter = locationTrackService.getOrThrow(mainDraftContext.context, locationTrack)
        assertEquals(emptySet<Nothing>(), trackAfter.operationalPointIds)
        val switchAfter = switchService.getOrThrow(mainDraftContext.context, switch)
        assertNull(switchAfter.operationalPointId)
    }

    @Test
    fun `update with severLinks = false preserves references on location tracks and switches`() {
        val opId = operationalPointService.insert(LayoutBranch.main, internalPointSaveRequest("op")).id
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id

        val locationTrack = mainDraftContext.save(locationTrack(trackNumberId, operationalPointIds = setOf(opId))).id
        val switch = mainDraftContext.save(switch(operationalPointId = opId)).id

        operationalPointService.update(
            LayoutBranch.main,
            opId,
            internalPointUpdateRequest(name = "op", state = OperationalPointState.DELETED, severLinks = false),
        )

        val trackAfter = locationTrackService.getOrThrow(mainDraftContext.context, locationTrack)
        assertEquals(setOf(opId), trackAfter.operationalPointIds)
        val switchAfter = switchService.getOrThrow(mainDraftContext.context, switch)
        assertEquals(opId, switchAfter.operationalPointId)
    }

    @Test
    fun `should reject setting rinf_id_generated once set`() {
        val id = mainDraftContext.save(operationalPoint(name = "op1")).id
        operationalPointDao.setRinfIdGenerated(id, operationalPointDao.generateRinfId())
        assertThrows<IllegalArgumentException> {
            operationalPointDao.setRinfIdGenerated(id, operationalPointDao.generateRinfId())
        }
    }

    @Test
    fun `should reject duplicate rinf_id_generated values`() {
        val op1Id = mainOfficialContext.save(operationalPoint(name = "op1")).id
        val op2Id = mainOfficialContext.save(operationalPoint(name = "op1")).id
        operationalPointDao.setRinfIdGenerated(op1Id, RinfId("FI999999"))
        assertThrows<DataIntegrityViolationException> {
            operationalPointDao.setRinfIdGenerated(op2Id, RinfId("FI999999"))
        }
    }

    @Test
    fun `should allow multiple operational points with null rinf_id_generated`() {
        mainDraftContext.save(operationalPoint(name = "op1"))
        mainDraftContext.save(operationalPoint(name = "op2"))
        val points = operationalPointService.list(mainDraftContext.context)
        assertEquals(2, points.size)
        assertEquals(listOf(null, null), points.map { it.rinfIdGenerated })
    }
}
