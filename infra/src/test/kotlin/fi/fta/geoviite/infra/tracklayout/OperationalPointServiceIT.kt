package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
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
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class OperationalPointServiceIT
@Autowired
constructor(
    private val operationalPointService: OperationalPointService,
    private val operationalPointDao: OperationalPointDao,
    private val ratkoTestService: RatkoTestService,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.deleteFromTables(
            "layout",
            "operational_point_id",
            "operational_point_external_id",
            "operational_point_version",
            "operational_point",
            "track_number_id",
            "track_number_external_id",
            "track_number",
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
                    internalPointSaveRequest(
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
            operationalPointService.update(LayoutBranch.main, external, internalPointSaveRequest("updated"))
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
        operationalPointService.update(LayoutBranch.main, firstDraft.id, internalPointSaveRequest("b"))
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
    fun `deleting a drafted update to an external operational point draft instead resets the point`() {
        val externalPointId =
            ratkoTestService.setupRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "external"))[0]

        val point =
            testDBService
                .save(
                    operationalPoint(
                        contextData = createMainContext(externalPointId, false),
                        origin = OperationalPointOrigin.RATKO,
                        ratkoVersion = 1,
                    )
                )
                .id
        ratkoTestService.updateRatkoOperationalPoints(ratkoOperationalPoint("1.2.3.4.5", name = "changed external"))
        operationalPointService.update(
            LayoutBranch.main,
            point,
            ExternalOperationalPointSaveRequest(OperationalPointRinfType.SMALL_STATION),
        )

        // initially we have a pending update of the operational point's Ratko version to publish; as well as the
        // RINF type assignment above
        assertEquals(1, mainOfficialContext.fetch(point)?.ratkoVersion)
        assertEquals(2, mainDraftContext.fetch(point)?.ratkoVersion)

        operationalPointService.deleteDraft(LayoutBranch.main, point)

        // after deleteDraft, the RINF type assignment has been cleared, but the Ratko version update is still pending
        assertEquals(null, mainDraftContext.fetch(point)?.rinfType)
        assertEquals(1, mainOfficialContext.fetch(point)?.ratkoVersion)
        assertEquals(2, mainDraftContext.fetch(point)?.ratkoVersion)
    }

    private fun internalPointSaveRequest(
        name: String = "name",
        abbreviation: String = name,
        rinfType: OperationalPointRinfType = OperationalPointRinfType.SMALL_STATION,
        state: OperationalPointState = OperationalPointState.IN_USE,
        uicCode: String = "10101",
    ) =
        InternalOperationalPointSaveRequest(
            OperationalPointInputName(name),
            OperationalPointInputAbbreviation(abbreviation),
            rinfType,
            state,
            UicCode(uicCode),
        )

    private fun externalPointSaveRequest(rinfType: OperationalPointRinfType = OperationalPointRinfType.SMALL_STATION) =
        ExternalOperationalPointSaveRequest(rinfType)
}
