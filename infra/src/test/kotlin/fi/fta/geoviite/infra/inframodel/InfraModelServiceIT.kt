package fi.fta.geoviite.infra.inframodel

import assertPlansMatch
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.geometry.PlanDecisionPhase
import fi.fta.geoviite.infra.geometry.PlanName
import fi.fta.geoviite.infra.geometry.PlanPhase
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
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

    @BeforeEach
    fun clearPlanFiles() {
        jdbc.execute("delete from geometry.plan_file where true") { it.execute() }
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
                planApplicability = PlanApplicability.MAINTENANCE,
            )

        val planId = infraModelService.saveInfraModel(file, overrides1, extraInfo1).id
        assertOverrides(planId, overrides1, extraInfo1)
        infraModelService.updateInfraModel(planId, overrides2, extraInfo2)
        assertOverrides(planId, overrides2, extraInfo2)
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
    }
}
