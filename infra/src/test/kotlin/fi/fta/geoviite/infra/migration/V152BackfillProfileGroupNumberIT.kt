package fi.fta.geoviite.infra.migration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryProfile
import fi.fta.geoviite.infra.geometry.VIPoint
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.util.FileName
import java.sql.Connection
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class V152BackfillProfileGroupNumberIT
@Autowired
constructor(
    val geometryDao: GeometryDao,
    val dataSource: DataSource,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearGeometryTables()
    }

    @Test
    fun `backfills profile_group_number from XML IM_ProfileGroup feature`() {
        val profileName = "alignment-with-profile"
        val planVersion = geometryDao.insertPlan(
            plan(alignments = listOf(geometryAlignment(profile = profileWithoutGroupNumber(profileName)))),
            infraModelFileWithProfileGroup(profileName, groupNumber = "42"),
            null,
        )
        val planId = planVersion.id as IntId<GeometryPlan>

        assertNull(fetchProfileGroupNumber(planId))

        runMigration()

        assertEquals("42", fetchProfileGroupNumber(planId))
    }

    @Test
    fun `does not touch alignments without a profile_name`() {
        val planVersion = geometryDao.insertPlan(
            plan(alignments = listOf(geometryAlignment(profile = null))),
            InfraModelFile(FileName("no_profile.xml"), "<LandXml/>"),
            null,
        )
        val planId = planVersion.id as IntId<GeometryPlan>

        runMigration()

        assertNull(fetchProfileGroupNumber(planId))
    }

    @Test
    fun `skips IM_ProfileGroup feature when ProfAlignGroupNumber is absent`() {
        val profileName = "profile-no-group-number"
        val xmlWithoutGroupNumber = """
            <?xml version="1.0" encoding="UTF-8"?>
            <LandXml>
              <Feature code="IM_ProfileGroup">
                <Property label="ProfAlignName" value="$profileName"/>
              </Feature>
            </LandXml>
        """.trimIndent()
        val planVersion = geometryDao.insertPlan(
            plan(alignments = listOf(geometryAlignment(profile = profileWithoutGroupNumber(profileName)))),
            InfraModelFile(FileName("missing_group_number.xml"), xmlWithoutGroupNumber),
            null,
        )
        val planId = planVersion.id as IntId<GeometryPlan>

        runMigration()

        assertNull(fetchProfileGroupNumber(planId))
    }

    private fun runMigration() {
        dataSource.connection.use { connection ->
            V152__backfill_profile_group_number().migrate(SimpleFlywayContext(connection))
        }
    }

    private fun fetchProfileGroupNumber(planId: IntId<GeometryPlan>): String? =
        jdbc.query(
            "select profile_group_number from geometry.alignment where plan_id = :planId",
            mapOf("planId" to planId.intValue),
        ) { rs, _ -> rs.getString("profile_group_number") }.single()

    private fun profileWithoutGroupNumber(name: String) = GeometryProfile(
        name = PlanElementName(name),
        elements = listOf(
            VIPoint(PlanElementName("start"), Point(0.0, 10.0)),
            VIPoint(PlanElementName("end"), Point(100.0, 20.0)),
        ),
        groupNumber = null,
    )

    private fun infraModelFileWithProfileGroup(profileName: String, groupNumber: String) =
        InfraModelFile(
            name = FileName("profile_group_${profileName}.xml"),
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <LandXml>
                  <Feature code="IM_ProfileGroup">
                    <Property label="ProfAlignName" value="$profileName"/>
                    <Property label="ProfAlignGroupNumber" value="$groupNumber"/>
                  </Feature>
                </LandXml>
            """.trimIndent(),
        )
}

private class SimpleFlywayContext(private val connection: Connection) : Context {
    override fun getConfiguration(): Configuration = error("not needed")
    override fun getConnection(): Connection = connection
}
