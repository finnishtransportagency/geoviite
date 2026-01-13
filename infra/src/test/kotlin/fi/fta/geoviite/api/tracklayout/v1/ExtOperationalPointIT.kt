package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtOperationalPointIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val operationalPointDao: OperationalPointDao,
) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Operational point collection API returns operational points`() {
        val op1 = createAndPublishOperationalPoint("Test Point 1", OperationalPointAbbreviation("TP1"))
        val op2 = createAndPublishOperationalPoint("Test Point 2", OperationalPointAbbreviation("TP2"))

        val collectionResponse = api.operationalPointCollection.get()

        val opNames = collectionResponse.toiminnalliset_pisteet.map { it.nimi }
        assertEquals(listOf("Test Point 1", "Test Point 2"), opNames)
    }

    @Test
    fun `Single operational point API returns correct operational point`() {
        val op = createAndPublishOperationalPoint("Test Point", OperationalPointAbbreviation("TP"))
        val oid = operationalPointDao.fetchExternalId(LayoutBranch.main, op)?.oid
        assertNotNull(oid)

        val response = api.operationalPoint.get(oid!!)

        assertEquals("Test Point", response.toiminnallinen_piste.nimi)
        assertEquals("TP", response.toiminnallinen_piste.lyhenne)
    }

    @Test
    fun `Operational point modification API returns changes`() {
        val op = createAndPublishOperationalPoint("Test Point", OperationalPointAbbreviation("TP"))
        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(op)).uuid

        // Update the operational point
        initUser()
        mainDraftContext.mutate(op) { point ->
            point.copy(name = OperationalPointName("Test Point Updated"))
        }
        extTestDataService.publishInMain(operationalPoints = listOf(op))

        val oid = operationalPointDao.fetchExternalId(LayoutBranch.main, op)?.oid
        assertNotNull(oid)

        // Check single point modifications
        val modification = api.operationalPoint.getModifiedSince(oid!!, baseVersion)
        assertEquals("Test Point Updated", modification.toiminnallinen_piste.nimi)

        // Check collection modifications
        val collectionModifications = api.operationalPointCollection.getModifiedSince(baseVersion)
        assertNotNull(collectionModifications)
        assertEquals(listOf("Test Point Updated"), collectionModifications.toiminnalliset_pisteet.map { it.nimi })
    }

    @Test
    fun `Deleted operational point should be returned in single-fetch API but not collection API`() {
        val op = createAndPublishOperationalPoint("Test Point", OperationalPointAbbreviation("TP"))
        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(op)).uuid

        val oid = operationalPointDao.fetchExternalId(LayoutBranch.main, op)?.oid
        assertNotNull(oid)

        // Verify initial state
        val baseResponse = api.operationalPoint.get(oid!!)
        assertNotNull(baseResponse)

        // Delete the operational point
        initUser()
        mainDraftContext.mutate(op) { point -> point.copy(state = OperationalPointState.DELETED) }
        extTestDataService.publishInMain(operationalPoints = listOf(op))

        // Single fetch should return the deleted point
        val deletedResponse = api.operationalPoint.get(oid)
        assertEquals("poistettu", deletedResponse.toiminnallinen_piste.tila)

        // Collection should not include deleted points
        val collectionResponse = api.operationalPointCollection.get()
        assertEquals(emptyList<ExtTestOperationalPointV1>(), collectionResponse.toiminnalliset_pisteet)
    }

    @Test
    fun `Operational point API returns no changes when versions are the same`() {
        val op = createAndPublishOperationalPoint("Test Point", OperationalPointAbbreviation("TP"))
        val version = extTestDataService.publishInMain(operationalPoints = listOf(op)).uuid

        val oid = operationalPointDao.fetchExternalId(LayoutBranch.main, op)?.oid
        assertNotNull(oid)

        // Query for changes with same version
        api.operationalPoint.assertNoModificationBetween(oid!!, version, version)
        api.operationalPointCollection.assertNoModificationBetween(version, version)
    }

    private fun createAndPublishOperationalPoint(
        name: String,
        abbreviation: OperationalPointAbbreviation
    ): IntId<OperationalPoint> {
        initUser()
        val opId = mainDraftContext.save(
            OperationalPoint(
                name = OperationalPointName(name),
                abbreviation = abbreviation,
                uicCode = UicCode("1"),
                rinfType = OperationalPointRinfType.STATION,
                raideType = null,
                polygon = Polygon(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0), Point(0.0, 1.0))),
                location = Point(0.5, 0.5),
                state = OperationalPointState.IN_USE,
                origin = OperationalPointOrigin.GEOVIITE,
                ratkoVersion = null,
                contextData = LayoutContextData.newDraft(LayoutBranch.main, null),
            )
        ).id

        extTestDataService.publishInMain(operationalPoints = listOf(opId))
        return opId
    }
}
