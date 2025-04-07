package fi.fta.geoviite.infra.inframodel

import assertPlansMatch
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.geometry.PlanDecisionPhase
import fi.fta.geoviite.infra.geometry.PlanName
import fi.fta.geoviite.infra.geometry.PlanPhase
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.PlanState
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.minimalLine
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.someBoundingPolygon
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class InfraModelServiceIT
@Autowired
constructor(val infraModelService: InfraModelService, val geometryDao: GeometryDao) : DBTestBase() {

    @Autowired private lateinit var geometryService: GeometryService

    @BeforeEach
    fun clearPlanTables() {
        testDBService.clearGeometryTables()
    }

    @Test
    fun simpleFileIsWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun differentSpiralsAreWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_CLOTHOID_AND_PARABOLA)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun duplicatePlanCausesParsingException() {
        val file = getMockedMultipartFile(TESTFILE_CLOTHOID_AND_PARABOLA)

        infraModelService.saveInfraModel(file, null, null)
        val exception = assertThrows<InframodelParsingException> { infraModelService.saveInfraModel(file, null, null) }
        assertTrue(exception.message?.contains("InfraModel file exists already") ?: false)
    }

    @Test
    fun `InfraModel with empty rotationPoint value in the Cant-field can be saved to and fetched from the database`() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE_WITHOUT_CANT_ROTATION_POINT)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun `InfraModel update works`() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE)

        val overrides1 =
            OverrideParameters(
                encoding = null,
                coordinateSystemSrid = null,
                verticalCoordinateSystem = VerticalCoordinateSystem.N2000,
                projectId = testDBService.insertProject().id,
                authorId = testDBService.insertAuthor().id,
                trackNumber = mainDraftContext.createAndFetchLayoutTrackNumber().number,
                createdDate = Instant.now().minusSeconds(Duration.ofDays(5L).toSeconds()),
                source = PlanSource.GEOMETRIAPALVELU,
            )
        val extraInfo1 =
            ExtraInfoParameters(
                planPhase = PlanPhase.RAILWAY_PLAN,
                decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
                measurementMethod = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                elevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_RAIL,
                message = FreeTextWithNewLines.of("test message 1"),
                name = PlanName("test name 1"),
                planApplicability = PlanApplicability.PLANNING,
            )

        val overrides2 =
            OverrideParameters(
                encoding = null,
                coordinateSystemSrid = null,
                verticalCoordinateSystem = VerticalCoordinateSystem.N60,
                projectId = testDBService.insertProject().id,
                authorId = testDBService.insertAuthor().id,
                trackNumber = mainDraftContext.createAndFetchLayoutTrackNumber().number,
                createdDate = Instant.now(),
                source = PlanSource.PAIKANNUSPALVELU,
            )
        val extraInfo2 =
            ExtraInfoParameters(
                planPhase = PlanPhase.RENOVATION_PLAN,
                decisionPhase = PlanDecisionPhase.UNDER_CONSTRUCTION,
                measurementMethod = MeasurementMethod.DIGITIZED_AERIAL_IMAGE,
                elevationMeasurementMethod = ElevationMeasurementMethod.TOP_OF_SLEEPER,
                message = FreeTextWithNewLines.of("test message 2"),
                name = PlanName("test name 2"),
                planApplicability = null,
            )

        val planId = infraModelService.saveInfraModel(file, overrides1, extraInfo1).id
        assertOverrides(planId, overrides1, extraInfo1)
        infraModelService.updateInfraModel(planId, overrides2, extraInfo2)
        assertOverrides(planId, overrides2, extraInfo2)
    }

    @Test
    fun `Setting Applicability only sets plan applicability`() {
        val file = testFile()
        val plan = plan(testDBService.getUnusedTrackNumber(), fileName = file.name, planApplicability = null)
        val polygon = someBoundingPolygon()
        val planId = geometryDao.insertPlan(plan, file, polygon).id
        val headerBeforeUpdate = geometryService.getPlanHeader(planId)

        infraModelService.setPlanApplicability(planId, PlanApplicability.PLANNING)
        val headerAfterUpdate = geometryService.getPlanHeader(planId)

        assertEquals(
            headerBeforeUpdate.copy(
                planApplicability = PlanApplicability.PLANNING,
                version = headerAfterUpdate.version,
            ),
            headerAfterUpdate,
        )
    }

    @Test
    fun `plan with only a straight alignment gets a sensible bounding polygon`() {
        val file = testFile()
        val plan =
            plan(
                source = PlanSource.GEOMETRIAPALVELU,
                srid = LAYOUT_SRID,
                kmPosts = listOf(),
                alignments =
                    listOf(geometryAlignment(minimalLine(start = Point(100.0, 100.0), end = Point(100.0, 101.0)))),
            )
        val savedVersion = geometryDao.insertPlan(plan, file, infraModelService.getBoundingPolygon(plan))

        assertEquals(
            listOf(savedVersion),
            geometryDao.fetchPlanVersions(
                listOf(PlanSource.GEOMETRIAPALVELU),
                boundingBoxAroundPoint(Point(100.0, 100.0), 1.0),
            ),
        )
    }

    @Test
    fun `plan with only a single km post gets a sensible bounding polygon`() {
        val file = testFile()
        val plan =
            plan(
                srid = LAYOUT_SRID,
                kmPosts =
                    listOf(
                        GeometryKmPost(
                            staBack = BigDecimal("1003.440894"),
                            staAhead = BigDecimal("854.711894"),
                            staInternal = BigDecimal("854.711894"),
                            kmNumber = KmNumber(1),
                            description = PlanElementName("1"),
                            state = PlanState.PROPOSED,
                            location = Point(x = 100.0, 100.0),
                        )
                    ),
                alignments = listOf(),
            )
        val savedVersion = geometryDao.insertPlan(plan, file, infraModelService.getBoundingPolygon(plan))

        assertEquals(
            listOf(savedVersion),
            geometryDao.fetchPlanVersions(
                listOf(PlanSource.GEOMETRIAPALVELU),
                boundingBoxAroundPoint(Point(100.0, 100.0), 1.0),
            ),
        )
    }

    fun getMockedMultipartFile(fileLocation: String): MockMultipartFile =
        MockMultipartFile(
            "file",
            "file.xml",
            MediaType.TEXT_XML_VALUE,
            classpathResourceToString(fileLocation).byteInputStream(),
        )

    private fun assertOverrides(
        planId: IntId<GeometryPlan>,
        overrides: OverrideParameters,
        extraInfo: ExtraInfoParameters,
    ) {
        val plan = geometryDao.fetchPlan(geometryDao.fetchPlanVersion(planId))

        assertEquals(overrides.verticalCoordinateSystem, plan.units.verticalCoordinateSystem)
        assertEquals(overrides.projectId, plan.project.id as IntId)
        assertEquals(overrides.authorId, plan.author?.id as IntId)
        assertEquals(overrides.trackNumber, plan.trackNumber)
        assertEquals(overrides.createdDate?.toEpochMilli(), plan.planTime?.toEpochMilli())
        assertEquals(overrides.source, plan.source)

        assertEquals(extraInfo.planPhase, plan.planPhase)
        assertEquals(extraInfo.decisionPhase, plan.decisionPhase)
        assertEquals(extraInfo.measurementMethod, plan.measurementMethod)
        assertEquals(extraInfo.elevationMeasurementMethod, plan.elevationMeasurementMethod)
        assertEquals(extraInfo.message, plan.message)
        assertEquals(extraInfo.planApplicability, plan.planApplicability)
    }
}
