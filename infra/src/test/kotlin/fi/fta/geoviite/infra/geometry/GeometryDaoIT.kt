package fi.fta.geoviite.infra.geometry

import assertPlansMatch
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertNotNull

const val TEST_NAME_PREFIX = "GEOM_DAO_IT_"

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeometryDaoIT @Autowired constructor(
    val geometryDao: GeometryDao,
    val locationTrackService: LocationTrackService,
): ITTestBase() {

    @BeforeEach
    fun init() {
        transactional {
            val selectPlanIds = """
                select plan.id 
                from geometry.plan 
                  left join geometry.plan_project project on plan.plan_project_id = project.id
                  left join geometry.plan_author author on plan.plan_author_id = author.id
                  left join geometry.plan_application application on plan.plan_application_id = application.id
                where project.name like '$TEST_NAME_PREFIX%'
                   or author.company_name like '$TEST_NAME_PREFIX%'
                   or application.name like '$TEST_NAME_PREFIX%'
            """.trimIndent()
            val ids = jdbc.query(selectPlanIds, mapOf<String, Unit>()) { rs, _ -> rs.getInt("id") }
            val idReferenceCondition = if (ids.isEmpty()) "0=1" else "plan_id in (:plan_ids)"
            val idCondition = if (ids.isEmpty()) "0=1" else "id in (:plan_ids)"

            val deleteSql = """
                delete from geometry.alignment where $idReferenceCondition;
                delete from geometry.km_post where $idReferenceCondition;
                delete from geometry.switch where $idReferenceCondition;
                delete from geometry.plan where $idCondition;
                delete from geometry.plan_project where name like '$TEST_NAME_PREFIX%';
                delete from geometry.plan_author where company_name like '$TEST_NAME_PREFIX%';
                delete from geometry.plan_application where name like '$TEST_NAME_PREFIX%';
            """.trimIndent()

            jdbc.update(deleteSql, mapOf("plan_ids" to ids))
        }
    }

    @Test
    fun findsProjectById() {
        val project1 = project(name = "${TEST_NAME_PREFIX}Project name 1")
        val project2 = project(name = "${TEST_NAME_PREFIX}Project name 2")

        val planId = geometryDao.insertProject(project1)
        geometryDao.insertProject(project2)

        val fetchedProject = geometryDao.getProject(planId.id)

        assertEquals(ProjectName("${TEST_NAME_PREFIX}Project name 1"), fetchedProject.name)
    }

    @Test
    fun findsProjectBySimilarName() {
        val project1 = project(name = "${TEST_NAME_PREFIX}Project match")
        val project2 = project(name = "${TEST_NAME_PREFIX}Project nomatch")

        geometryDao.insertProject(project1)
        geometryDao.insertProject(project2)

        val fetchedProject = geometryDao.findProject(ProjectName("${TEST_NAME_PREFIX}pRojEcT           MaTCh"))

        assertNotNull(fetchedProject)
        assertEquals(ProjectName("${TEST_NAME_PREFIX}Project match"), fetchedProject.name)
    }

    @Test
    fun findsAuthorById() {
        val author1 = author(companyName = "${TEST_NAME_PREFIX}Company 1")
        val author2 = author(companyName = "${TEST_NAME_PREFIX}Company 2")

        val authorId = geometryDao.insertAuthor(author1)
        geometryDao.insertAuthor(author2)

        val fetchedAuthor = geometryDao.getAuthor(authorId.id)

        assertEquals(MetaDataName("${TEST_NAME_PREFIX}Company 1"), fetchedAuthor.companyName)
    }

    @Test
    fun findsAuthorBySimilarName() {
        val author1 = author("${TEST_NAME_PREFIX}Company match")
        val author2 = author("${TEST_NAME_PREFIX}Company nomatch")

        geometryDao.insertAuthor(author1)
        geometryDao.insertAuthor(author2)

        val author = geometryDao.findAuthor(MetaDataName("${TEST_NAME_PREFIX}COMPANY            mAtCH"))

        assertNotNull(author)
        assertEquals(MetaDataName("${TEST_NAME_PREFIX}Company match"), author.companyName)
    }

    @Test
    fun findsApplicationById() {
        val application1 = application(name = "${TEST_NAME_PREFIX}Application 1", manufacturer = "Solita Ab/Oy", version = "0.1")
        val application2 = application(name = "${TEST_NAME_PREFIX}Application 2", manufacturer = "Solita Ab/Oy", version = "0.2")

        val applicationId = geometryDao.insertApplication(application1)
        geometryDao.insertApplication(application2)

        val fetchedApplication = geometryDao.getApplication(applicationId.id)

        assertEquals(MetaDataName("${TEST_NAME_PREFIX}Application 1"), fetchedApplication.name)
    }

    @Test
    fun throwsExceptionWhenInsertingAuthorWithSameCompanyName() {
        assertThrows(DuplicateKeyException::class.java) {
            val author = author("${TEST_NAME_PREFIX}Company 1")
            geometryDao.insertAuthor(author)
            geometryDao.insertAuthor(author)
        }
    }

    @Test
    fun throwsExceptionWhenInsertingProjectWithSameName() {
        assertThrows(DuplicateKeyException::class.java) {
            val project = project(name = "${TEST_NAME_PREFIX}Project name 1")
            geometryDao.insertProject(project)
            geometryDao.insertProject(project)
        }
    }

    @Test
    fun insertPlanWorks() {
        val plan = plan(insertOfficialTrackNumber())
        val fileContent = "<a></a>"
        val id = geometryDao.insertPlan(plan, InfraModelFile(plan.fileName, fileContent))
        val fetchedPlan = geometryDao.fetchPlan(id)
        val file = geometryDao.getPlanFile(id.id)
        assertPlansMatch(plan, fetchedPlan)
        assertEquals(fileContent, file.content)
        assertEquals(plan.fileName, file.name)
    }

    @Test
    fun minimalPlanInsertWorks() {
        val file = infraModelFile("${TEST_NAME_PREFIX}_file_min.xml")
        val plan = minimalPlan(fileName = file.name)
        val version = geometryDao.insertPlan(plan, file)
        assertPlansMatch(plan, geometryDao.fetchPlan(version))
    }

    @Test
    fun minimalElementInsertsWork() {
        val file = infraModelFile("${TEST_NAME_PREFIX}_file_min_elem.xml")
        val plan = plan(
            trackNumberId = insertOfficialTrackNumber(),
            fileName = file.name,
            alignments = listOf(geometryAlignment(
                elements = listOf(
                    minimalLine(),
                    minimalCurve(),
                    minimalClothoid(),
                ),
            )),
        )
        val version = geometryDao.insertPlan(plan, file)
        assertPlansMatch(plan, geometryDao.fetchPlan(version))
    }

    @Test
    fun getLinkingSummariesHappyCase() {
        val file = infraModelFile("${TEST_NAME_PREFIX}_file_min_elem.xml")
        val trackNumberId = insertOfficialTrackNumber()
        val plan = plan(
            trackNumberId = trackNumberId,
            fileName = file.name,
            alignments = listOf(geometryAlignment(
                elements = listOf(
                    minimalLine(),
                ),
            )),
        )
        val planVersion = geometryDao.insertPlan(plan, file)
        val element = geometryDao.fetchPlan(planVersion).alignments[0].elements[0]
        val track = locationTrackAndAlignment(trackNumberId,
            segment(Point(0.0, 0.0), Point(1.0, 1.0)).copy(sourceId = element.id)
        )
        val trackVersion = locationTrackService.saveDraft(track.first, track.second)
        locationTrackService.publish(PublicationVersion(trackVersion.id, trackVersion.rowVersion))
        val trackChangeTime = locationTrackService.getChangeTimes(trackVersion.id).officialChanged!!

        val expectedSummary = GeometryPlanLinkingSummary(trackChangeTime, "TEST_USER")
        val summaries = geometryDao.getLinkingSummaries(listOf(planVersion.id))
        val allSummaries = geometryDao.getLinkingSummaries(null)
        assertEquals(mapOf(planVersion.id to expectedSummary), summaries)
        assertContains(allSummaries.values, expectedSummary)
    }
}
