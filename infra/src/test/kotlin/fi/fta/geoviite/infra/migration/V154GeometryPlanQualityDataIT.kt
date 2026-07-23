package fi.fta.geoviite.infra.migration

import currentUser
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.infraModelFile
import fi.fta.geoviite.infra.geometry.plan
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

private const val IM_IMPORT = "IM_IMPORT"

@ActiveProfiles("dev", "test")
@SpringBootTest
class V154GeometryPlanQualityDataIT @Autowired constructor(val geometryDao: GeometryDao) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearGeometryTables()
    }

    @Test
    fun `GEOMETRIAPALVELU initial import gets quality PLAN and mm OFFICIALLY`() {
        val planId =
            insertPlan(
                source = PlanSource.GEOMETRIAPALVELU,
                mm = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                uploader = IM_IMPORT,
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOMETRIAPALVELU",
        )
        assertVersionResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOMETRIAPALVELU",
        )
    }

    @Test
    fun `GEOMETRIAPALVELU operator upload with OFFICIALLY gets quality PLAN and source GEOVIITE`() {
        val planId =
            insertPlan(
                source = PlanSource.GEOMETRIAPALVELU,
                mm = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                uploader = "operator",
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOVIITE",
        )
        assertVersionResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOVIITE",
        )
    }

    @Test
    fun `GEOMETRIAPALVELU operator upload with TRACK_INSPECTION gets quality UNRELIABLE_PLAN and mm preserved`() {
        val planId =
            insertPlan(
                source = PlanSource.GEOMETRIAPALVELU,
                mm = MeasurementMethod.TRACK_INSPECTION,
                uploader = "operator",
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "TRACK_INSPECTION",
            expectedSource = "GEOVIITE",
        )
        assertVersionResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "TRACK_INSPECTION",
            expectedSource = "GEOVIITE",
        )
    }

    @Test
    fun `GEOMETRIAPALVELU operator upload with null mm gets quality UNRELIABLE_PLAN and mm DIGITIZED`() {
        val planId = insertPlan(source = PlanSource.GEOMETRIAPALVELU, mm = null, uploader = "operator")

        runMigration()

        assertResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "DIGITIZED_AERIAL_IMAGE",
            expectedSource = "GEOVIITE",
        )
        assertVersionResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "DIGITIZED_AERIAL_IMAGE",
            expectedSource = "GEOVIITE",
        )
    }

    @Test
    fun `PAIKANNUSPALVELU initial import gets quality UNRELIABLE_PLAN even with OFFICIALLY`() {
        val planId =
            insertPlan(
                source = PlanSource.PAIKANNUSPALVELU,
                mm = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                uploader = IM_IMPORT,
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "PAIKANNUSPALVELU",
        )
        assertVersionResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "PAIKANNUSPALVELU",
        )
    }

    @Test
    fun `PAIKANNUSPALVELU operator upload with OFFICIALLY gets quality UNRELIABLE_PLAN and source GEOVIITE`() {
        val planId =
            insertPlan(
                source = PlanSource.PAIKANNUSPALVELU,
                mm = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                uploader = "operator",
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOVIITE",
        )
        assertVersionResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOVIITE",
        )
    }

    @Test
    fun `VERIFIED_DESIGNED_GEOMETRY in plan_version is migrated to OFFICIALLY`() {
        val planId =
            insertPlanWithLegacyVersionMm(
                source = PlanSource.GEOMETRIAPALVELU,
                legacyMm = "VERIFIED_DESIGNED_GEOMETRY",
                uploader = IM_IMPORT,
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOMETRIAPALVELU",
        )
        assertVersionResult(
            planId,
            expectedQuality = "PLAN",
            expectedMm = "OFFICIALLY_MEASURED_GEODETICALLY",
            expectedSource = "GEOMETRIAPALVELU",
        )
    }

    @Test
    fun `UNVERIFIED_DESIGNED_GEOMETRY in plan_version is migrated to DIGITIZED`() {
        val planId =
            insertPlanWithLegacyVersionMm(
                source = PlanSource.PAIKANNUSPALVELU,
                legacyMm = "UNVERIFIED_DESIGNED_GEOMETRY",
                uploader = IM_IMPORT,
            )

        runMigration()

        assertResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "DIGITIZED_AERIAL_IMAGE",
            expectedSource = "PAIKANNUSPALVELU",
        )
        assertVersionResult(
            planId,
            expectedQuality = "UNRELIABLE_PLAN",
            expectedMm = "DIGITIZED_AERIAL_IMAGE",
            expectedSource = "PAIKANNUSPALVELU",
        )
    }

    @Test
    fun `GEOVIITE source plans are not touched`() {
        val planId =
            insertPlan(
                source = PlanSource.GEOVIITE,
                mm = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                uploader = "operator",
            )

        runMigration()

        assertNull(fetchQuality(planId))
    }

    private fun insertPlanWithLegacyVersionMm(
        source: PlanSource,
        legacyMm: String,
        uploader: String,
    ): IntId<GeometryPlan> {
        val planId = insertPlan(source = source, mm = null, uploader = uploader)
        transactional {
            jdbc.update(
                "update geometry.plan_version set measurement_method = :mm::common.measurement_method where id = :id",
                mapOf("mm" to legacyMm, "id" to planId.intValue),
            )
        }
        return planId
    }

    private fun insertPlan(source: PlanSource, mm: MeasurementMethod?, uploader: String): IntId<GeometryPlan> {
        currentUser.set(UserName.of(uploader))
        return try {
            geometryDao
                .insertPlan(
                    plan(
                        source = source,
                        measurementMethod = mm ?: MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                        alignments = listOf(geometryAlignment()),
                    ),
                    infraModelFile(),
                    null,
                )
                .id as IntId<GeometryPlan>
        } finally {
            currentUser.set(UserName.of(TEST_USER))
        }
    }

    private fun runMigration() {
        val sql =
            javaClass.classLoader.getResource("db/migration/prod/V154__geometry_plan_quality_data.sql")!!.readText()
        val dataMigrationSql = sql.substringAfter("-- DATA MIGRATION")
        val statements =
            dataMigrationSql
                .split(";")
                .map { chunk -> chunk.lines().filterNot { it.trim().startsWith("--") }.joinToString("\n").trim() }
                .filter { it.isNotEmpty() }
        transactional { statements.forEach { jdbc.jdbcTemplate.update(it) } }
    }

    private fun assertResult(
        planId: IntId<GeometryPlan>,
        expectedQuality: String,
        expectedMm: String,
        expectedSource: String,
    ) {
        val row =
            jdbc
                .query(
                    "select quality::text, measurement_method::text, source::text from geometry.plan where id = :id",
                    mapOf("id" to planId.intValue),
                ) { rs, _ ->
                    Triple(rs.getString(1), rs.getString(2), rs.getString(3))
                }
                .single()
        assertEquals(expectedQuality, row.first)
        assertEquals(expectedMm, row.second)
        assertEquals(expectedSource, row.third)
    }

    private fun assertVersionResult(
        planId: IntId<GeometryPlan>,
        expectedQuality: String,
        expectedMm: String,
        expectedSource: String,
    ) {
        val rows =
            jdbc.query(
                "select quality::text, measurement_method::text, source::text from geometry.plan_version where id = :id",
                mapOf("id" to planId.intValue),
            ) { rs, _ ->
                Triple(rs.getString(1), rs.getString(2), rs.getString(3))
            }
        rows.forEach { (quality, mm, source) ->
            assertEquals(expectedQuality, quality)
            assertEquals(expectedMm, mm)
            assertEquals(expectedSource, source)
        }
    }

    private fun fetchQuality(planId: IntId<GeometryPlan>): String? =
        jdbc
            .query("select quality::text from geometry.plan where id = :id", mapOf("id" to planId.intValue)) { rs, _ ->
                rs.getString(1)
            }
            .single()
}
