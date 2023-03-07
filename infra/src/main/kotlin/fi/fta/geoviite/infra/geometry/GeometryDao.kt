package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_GEOMETRY_PLAN
import fi.fta.geoviite.infra.configuration.CACHE_GEOMETRY_PLAN_HEADER
import fi.fta.geoviite.infra.configuration.CACHE_GEOMETRY_SWITCH
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.geometry.GeometryElementType.*
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.inframodel.InfraModelFileWithSource
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.linking.RemovedTrackNumberReferenceIds
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.toAngle
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.DbTable.GEOMETRY_PLAN
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant


enum class VerticalIntersectionType {
    POINT,
    CIRCULAR_CURVE,
}

@Transactional(readOnly = true)
@Component
class GeometryDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun insertPlan(
        plan: GeometryPlan,
        file: InfraModelFile,
        boundingBoxInLayoutCoordinates: List<Point>?,
    ): RowVersion<GeometryPlan> {
        jdbcTemplate.setUser()

        val projectId: IntId<Project> =
            if (plan.project.id is IntId) plan.project.id
            else findProject(plan.project.name)?.id as IntId? ?: insertProjectInternal(plan.project).id

        val authorId: IntId<Author>? = plan.author?.let { author: Author ->
            if (author.id is IntId) author.id
            else findAuthor(author.companyName)?.id as IntId? ?: insertAuthorInternal(author).id
        }
        val applicationId: IntId<Application> =
            if (plan.application.id is IntId) plan.application.id
            else findApplication(plan.application.name, plan.application.version)?.id as IntId?
                ?: insertApplicationInternal(plan.application).id

        val sql = """
            insert into geometry.plan(
              track_number_id,
              track_number_description,
              plan_project_id,
              plan_author_id,
              plan_application_id,
              plan_time,
              upload_time,
              linear_unit,
              direction_unit,
              srid,
              coordinate_system_name,
              bounding_polygon,
              vertical_coordinate_system,
              source,
              oid,
              plan_phase,
              plan_decision,
              measurement_method,
              message
              )
            values(
              :track_number_id,
              :track_number_description,
              :plan_project_id,
              :plan_author_id,
              :plan_application_id,
              :plan_time,
              now(),
              :linear_unit::common.linear_unit,
              :direction_unit::common.angular_unit,
              :srid,
              :coordinate_system_name,
              postgis.st_polygonfromtext(:polygon_string,:mapSrid),
              :vertical_coordinate_system::common.vertical_coordinate_system,
              :source::geometry.plan_source,
              :oid,
              :plan_phase::geometry.plan_phase,
              :plan_decision::geometry.plan_decision,
              :measurement_method::common.measurement_method,
              :message
              )
            returning id, version
        """.trimIndent()

        val params = mapOf(
            "track_number_id" to plan.trackNumberId?.intValue,
            "track_number_description" to plan.trackNumberDescription,
            "plan_project_id" to projectId.intValue,
            "plan_author_id" to authorId?.intValue,
            "plan_application_id" to applicationId.intValue,
            "plan_time" to plan.planTime?.let { instant -> Timestamp.from(instant) },
            "linear_unit" to plan.units.linearUnit.name,
            "direction_unit" to plan.units.directionUnit.name,
            "srid" to plan.units.coordinateSystemSrid?.code,
            "coordinate_system_name" to plan.units.coordinateSystemName,
            "polygon_string" to
                    if (!boundingBoxInLayoutCoordinates.isNullOrEmpty())
                        create2DPolygonString(boundingBoxInLayoutCoordinates)
                    else null,
            "vertical_coordinate_system" to plan.units.verticalCoordinateSystem?.name,
            "mapSrid" to LAYOUT_SRID.code,
            "source" to plan.source.name,
            "oid" to plan.oid?.toString(),
            "plan_phase" to plan.planPhase?.name,
            "plan_decision" to plan.decisionPhase?.name,
            "measurement_method" to plan.measurementMethod?.name,
            "message" to plan.message,
        )

        val planId: RowVersion<GeometryPlan> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to get generated ID for new plan")
        insertPlanFile(planId.id, file)
        val switchDatabaseIds = insertSwitches(planId.id, plan.switches)
        insertAlignments(planId.id, plan.alignments, switchDatabaseIds)
        insertKmPosts(getKmPostParams(planId.id, plan.kmPosts))

        logger.daoAccess(INSERT, GeometryPlan::class, planId)
        return planId
    }

    private fun insertPlanFile(planId: IntId<GeometryPlan>, file: InfraModelFile): IntId<InfraModelFile> {
        val sql = """
           insert into geometry.plan_file(plan_id, name, content) 
           values (:plan_id, :file_name, xmlparse(document :file_content))
           returning id, hash
       """.trimIndent()
        val params = mapOf(
            "plan_id" to planId.intValue,
            "file_name" to file.name,
            "file_content" to file.content,
        )
        val (fileId, hash) = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getIntId<InfraModelFile>("id") to rs.getString("hash")
        } ?: throw IllegalStateException("Failed to insert plan file into DB")
        require(hash == file.hash) {
            "Backend hash calculation should match the automatic DB-generated: file=${file.name} db=$hash backend=${file.hash}"
        }

        logger.daoAccess(INSERT, InfraModelFile::class, fileId)
        return fileId
    }

    fun getPlanFile(planId: IntId<GeometryPlan>): InfraModelFileWithSource {
        val sql = """
          select name as file_name, plan.source, xmlserialize(document content as varchar) as file_content
            from geometry.plan_file
            inner join geometry.plan 
            on plan_id = plan.id
          where plan_id = :plan_id
        """.trimIndent()
        val params = mapOf("plan_id" to planId.intValue)
        return getOne(planId, jdbcTemplate.query(sql, params) { rs, _ ->
            InfraModelFileWithSource(
                file = InfraModelFile(
                    name = rs.getFileName("file_name"),
                    content = rs.getString("file_content"),
                ),
                source = rs.getEnum("source")
            )
        })
    }

    fun fetchDuplicateGeometryPlanVersion(newFile: InfraModelFile): RowVersion<GeometryPlan>? {
        //language=SQL
        val sql = """
            select
              plan.id, plan.version
            from geometry.plan left join geometry.plan_file on plan.id = plan_file.plan_id
              where plan_file.hash = :hash
        """.trimIndent()
        val params = mapOf("hash" to newFile.hash)

        logger.daoAccess(FETCH, InfraModelFile::class, params)
        return jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        }
    }

    @Transactional
    fun updatePlan(
        planId: IntId<GeometryPlan>,
        geometryPlan: GeometryPlan,
    ): GeometryPlan {
        jdbcTemplate.setUser()
        val sql = """
            update geometry.plan
            set
              track_number_id = :track_number_id,
              track_number_description = :track_number_description,
              plan_project_id = :plan_project_id,
              plan_author_id = :plan_author_id,
              plan_time = :plan_time,
              srid = :srid,
              coordinate_system_name = :coordinate_system_name,
              vertical_coordinate_system = :vertical_coordinate_system::common.vertical_coordinate_system,
              oid = :oid,
              plan_phase = :plan_phase::geometry.plan_phase,
              plan_decision = :plan_decision::geometry.plan_decision,
              measurement_method = :measurement_method::common.measurement_method,
              message = :message,
              source = :source::geometry.plan_source
            where id = :id
        """.trimIndent()

        val params = mapOf(
            "id" to planId.intValue,
            "track_number_id" to geometryPlan.trackNumberId?.intValue,
            "track_number_description" to geometryPlan.trackNumberDescription,
            "plan_project_id" to (geometryPlan.project.id as IntId).intValue,
            "plan_author_id" to (geometryPlan.author?.id as IntId?)?.intValue,
            "plan_time" to geometryPlan.planTime?.let { instant -> Timestamp.from(instant) },
            "srid" to geometryPlan.units.coordinateSystemSrid?.code,
            "coordinate_system_name" to geometryPlan.units.coordinateSystemName,
            "vertical_coordinate_system" to geometryPlan.units.verticalCoordinateSystem?.name,
            "oid" to geometryPlan.oid?.toString(),
            "plan_phase" to geometryPlan.planPhase?.name,
            "plan_decision" to geometryPlan.decisionPhase?.name,
            "measurement_method" to geometryPlan.measurementMethod?.name,
            "message" to geometryPlan.message,
            "source" to geometryPlan.source.name,
        )

        check(jdbcTemplate.update(sql, params) > 0)

        logger.daoAccess(UPDATE, GeometryPlan::class, planId)

        return geometryPlan
    }

    @Transactional
    fun insertProject(project: Project): RowVersion<Project> {
        jdbcTemplate.setUser()
        return insertProjectInternal(project)
    }

    private fun insertProjectInternal(project: Project): RowVersion<Project> {
        val sql = """
            insert into geometry.plan_project(name, description) 
            values (:name, :description) 
            returning id, version
        """.trimIndent()
        val params = mapOf(
            "name" to project.name,
            "description" to project.description
        )
        val projectVersion: RowVersion<Project> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get generated ID for new project")
        logger.daoAccess(INSERT, Project::class, projectVersion)
        return projectVersion
    }

    @Transactional
    fun insertAuthor(author: Author): RowVersion<Author> {
        jdbcTemplate.setUser()
        return insertAuthorInternal(author)
    }

    private fun insertAuthorInternal(author: Author): RowVersion<Author> {
        val sql = """
            insert into geometry.plan_author(company_name)
            values (:company_name)
            returning id, version
        """.trimIndent()

        val params = mapOf("company_name" to author.companyName)
        val authorVersion: RowVersion<Author> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get generated ID for new author")
        logger.daoAccess(INSERT, Author::class, authorVersion)
        return authorVersion
    }

    @Transactional
    fun insertApplication(application: Application): RowVersion<Application> {
        jdbcTemplate.setUser()
        return insertApplicationInternal(application)
    }

    private fun insertApplicationInternal(application: Application): RowVersion<Application> {
        val sql = """
            insert into geometry.plan_application(name, manufacturer, application_version)
            values (:name, :manufacturer, :application_version) 
            returning id, version
            """
        val params = mapOf(
            "name" to application.name,
            "manufacturer" to application.manufacturer,
            "application_version" to application.version,
        )
        val appId: RowVersion<Application> = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get generated ID for new application")
        logger.daoAccess(INSERT, Application::class, appId)
        return appId
    }

    fun findApplication(name: MetaDataName, version: MetaDataName): Application? {
        val sql = """
            select
              id, name, manufacturer, application_version
            from geometry.plan_application
            where unique_name = common.nospace_lowercase(:name) || ' ' || common.nospace_lowercase(:application_version)
        """.trimIndent()

        val params = mapOf("name" to name, "application_version" to version)
        val application = jdbcTemplate.query(sql, params) { rs, _ ->
            Application(
                id = rs.getIntId("id"),
                name = MetaDataName(rs.getString("name")),
                manufacturer = MetaDataName(rs.getString("manufacturer")),
                version = MetaDataName(rs.getString("application_version")),
            )
        }.firstOrNull()

        application?.also { logger.daoAccess(FETCH, Application::class, it.id) }
        return application
    }

    fun getApplication(applicationId: IntId<Application>): Application {
        val sql = """
            select 
            id,
            name,
            manufacturer,
            application_version
            from geometry.plan_application
            where id = :id
        """.trimIndent()

        val params = mapOf("id" to applicationId.intValue)

        val application = jdbcTemplate.query(sql, params) { rs, _ ->
            Application(
                id = rs.getIntId("id"),
                name = MetaDataName(rs.getString("name")),
                manufacturer = MetaDataName(rs.getString("manufacturer")),
                version = MetaDataName(rs.getString("application_version")),
            )
        }.firstOrNull() ?: throw NoSuchEntityException(Project::class, applicationId)
        logger.daoAccess(FETCH, Application::class, application.id)

        return application
    }

    private fun insertSwitches(
        planId: IntId<GeometryPlan>,
        switches: List<GeometrySwitch>,
    ): Map<DomainId<GeometrySwitch>, IntId<GeometrySwitch>> {
        return switches.map { switch -> switch.id to insertSwitch(planId, switch) }.associate { it }
    }

    private fun insertSwitch(plandId: IntId<GeometryPlan>, switch: GeometrySwitch): IntId<GeometrySwitch> {
        val sql = """
            insert into geometry.switch(
              plan_id, 
              name,
              switch_structure_id,
              type_name,
              state
            )
            values(
              :plan_id, 
              :name,
              :switch_structure_id,
              :type_name,
              :state::geometry.plan_state
            )
            returning id
        """.trimIndent()
        val params = mapOf(
            "plan_id" to plandId.intValue,
            "name" to switch.name,
            "switch_structure_id" to switch.switchStructureId?.intValue,
            "type_name" to switch.typeName,
            "state" to switch.state?.name
        )
        val id = jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getIntId<GeometrySwitch>("id") }
            ?: throw IllegalStateException("Failed to get generated ID for new switch")
        insertSwitchJoints(id, switch.joints)
        return id
    }

    private fun insertSwitchJoints(switchId: IntId<GeometrySwitch>, joints: List<GeometrySwitchJoint>) {
        val sql = """
            insert into geometry.switch_joint(switch_id, number, location)
            values (:switch_id, :number, postgis.st_point(:x, :y))
            """
        val params = joints.map { joint ->
            mapOf(
                "switch_id" to switchId.intValue,
                "number" to joint.number.intValue,
                "x" to joint.location.x,
                "y" to joint.location.y,
            )
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, params)
    }

    private fun insertAlignments(
        planId: IntId<GeometryPlan>,
        alignments: List<GeometryAlignment>,
        switchDatabaseIds: Map<DomainId<GeometrySwitch>, IntId<GeometrySwitch>>,
    ) {
        val idToAlignment: List<Pair<IntId<GeometryAlignment>, GeometryAlignment>> = alignments.map { alignment ->
            insertAlignment(planId, alignment).id to alignment
        }

        val elementParams = idToAlignment.flatMap { (alignmentId, alignment) ->
            getGeometryElementSqlParams(alignmentId, alignment.elements, switchDatabaseIds)
        }
        insertGeometryElements(elementParams)

        val viParams = idToAlignment.flatMap { (alignmentId, alignment) ->
            alignment.profile
                ?.let { profile -> getVerticalIntersectionSqlParams(alignmentId, profile.elements) }
                ?: listOf()
        }
        if (viParams.isNotEmpty()) {
            insertVerticalIntersections(viParams)
        }

        val cantParams = idToAlignment.flatMap { (alignmentId, alignment) ->
            alignment.cant
                ?.let { cant -> getCantPointSqlParams(alignmentId, cant.points) }
                ?: listOf()
        }
        if (cantParams.isNotEmpty()) {
            insertCantPoints(cantParams)
        }
    }

    private fun insertKmPosts(kmPostParams: List<Map<String, Any?>>) {
        val sql = """
            insert into geometry.km_post(
              track_number_id, 
              km_post_index,
              plan_id,
              sta_back, 
              sta_ahead, 
              sta_internal, 
              km_number, 
              description,
              location,
              state)
            values (
             :track_number_id, 
             :km_post_index, 
             :plan_id,
             :sta_back, 
             :sta_ahead, 
             :sta_internal, 
             :km_number, 
             :description,
             postgis.st_point(:x, :y),
             :state::geometry.plan_state)
            """
        jdbcTemplate.batchUpdate(sql, kmPostParams.toTypedArray())
    }

    private fun getKmPostParams(
        planId: IntId<GeometryPlan>,
        kmPosts: List<GeometryKmPost>,
    ): List<Map<String, Any?>> {
        return kmPosts.mapIndexed { index, kmPost ->
            mapOf(
                "track_number_id" to kmPost.trackNumberId?.intValue,
                "km_post_index" to index,
                "plan_id" to planId.intValue,
                "sta_back" to kmPost.staBack,
                "sta_ahead" to kmPost.staAhead,
                "sta_internal" to kmPost.staInternal,
                "km_number" to kmPost.kmNumber?.toString(),
                "description" to kmPost.description,
                "x" to kmPost.location?.x,
                "y" to kmPost.location?.y,
                "state" to kmPost.state?.name,
            )
        }
    }

    private fun insertAlignment(
        planId: IntId<GeometryPlan>,
        alignment: GeometryAlignment,
    ): RowVersion<GeometryAlignment> {
        val sql = """ 
            insert into geometry.alignment(
              plan_id,
              name,
              description,
              state,
              sta_start,
              profile_name,
              cant_name,
              cant_description,
              cant_gauge,
              cant_rotation_point,
              feature_type_code)
            values(
              :plan_id,
              :name,
              :description,
              :state::geometry.plan_state,
              :sta_start,
              :profile_name,
              :cant_name,
              :cant_description,
              :cant_gauge,
              :cant_rotation_point::geometry.cant_rotation_point,
              :feature_type_code)
            returning id --, version
        """.trimIndent()
        val params = mapOf(
            "plan_id" to planId.intValue,
            "name" to alignment.name,
            "description" to alignment.description,
            "state" to alignment.state?.name,
            "sta_start" to alignment.staStart,
            "profile_name" to alignment.profile?.name,
            "cant_name" to alignment.cant?.name,
            "cant_description" to alignment.cant?.description,
            "cant_gauge" to alignment.cant?.gauge,
            "cant_rotation_point" to alignment.cant?.rotationPoint?.name,
            "feature_type_code" to alignment.featureTypeCode
        )
        return jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            RowVersion(rs.getIntId("id"), 1)//rs.getRowVersion("id", "version")
        } ?: throw IllegalStateException("Failed to get generated ID for new alignment")
    }

    @Cacheable(CACHE_GEOMETRY_PLAN_HEADER, sync = true)
    fun fetchPlanHeader(rowVersion: RowVersion<GeometryPlan>): GeometryPlanHeader {
        val sql = """
            select 
              plan.id as plan_id, 
              plan_file.name as file_name, 
              plan.source,
              plan.message, 
              plan.plan_time, 
              plan.upload_time, 
              plan.measurement_method,
              plan.plan_phase, 
              plan.plan_decision,
              plan.linear_unit,
              plan.direction_unit,
              plan.srid,
              plan.coordinate_system_name,
              plan.vertical_coordinate_system,
              plan.linked_as_plan_id,
              project.id as project_id, 
              project.name as project_name,
              project.description as project_description,
              plan.track_number_id,
              (select min(km_post.km_number) from geometry.km_post where km_post.plan_id = plan.id) as min_km_number,
              (select max(km_post.km_number) from geometry.km_post where km_post.plan_id = plan.id) as max_km_number,
              author.company_name as author,
              has_profile,
              has_cant
            from geometry.plan
              left join geometry.plan_file on plan_file.plan_id = plan.id
              left join geometry.plan_project project on project.id = plan.plan_project_id
              left join geometry.plan_author author on plan.plan_author_id = author.id
              left join lateral
                (select bool_or(profile_name is not null) as has_profile,
                        bool_or(cant_name is not null) as has_cant
                 from geometry.alignment
                 where plan_id = plan.id) alignments on (true)
            where :plan_id = plan.id
        """.trimIndent()
        val params = mapOf(
            "plan_id" to rowVersion.id.intValue,
        )
        val header = jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            val project = Project(
                id = rs.getIntId("project_id"),
                name = ProjectName(rs.getString("project_name")),
                description = rs.getFreeTextOrNull("project_description"),
            )

            val minKm = rs.getKmNumberOrNull("min_km_number")
            val maxKm = rs.getKmNumberOrNull("max_km_number")
            val range = if (minKm != null && maxKm != null) Range(minKm, maxKm) else null
            GeometryPlanHeader(
                id = rs.getIntId("plan_id"),
                project = project,
                fileName = rs.getFileName("file_name"),
                source = rs.getEnum("source"),
                kmNumberRange = range,
                planTime = rs.getInstantOrNull("plan_time"),
                trackNumberId = rs.getIntIdOrNull("track_number_id"),
                measurementMethod = rs.getEnumOrNull<MeasurementMethod>("measurement_method"),
                decisionPhase = rs.getEnumOrNull<PlanDecisionPhase>("plan_decision"),
                planPhase = rs.getEnumOrNull<PlanPhase>("plan_phase"),
                message = rs.getFreeTextOrNull("message"),
                linkedAsPlanId = rs.getIntIdOrNull("linked_as_plan_id"),
                uploadTime = rs.getInstant("upload_time"),
                units = GeometryUnits(
                    coordinateSystemSrid = rs.getSridOrNull("srid"),
                    coordinateSystemName = rs.getString("coordinate_system_name")?.let(::CoordinateSystemName),
                    verticalCoordinateSystem = rs.getEnumOrNull<VerticalCoordinateSystem>("vertical_coordinate_system"),
                    directionUnit = rs.getEnum("direction_unit"),
                    linearUnit = rs.getEnum("linear_unit"),
                ),
                author = rs.getString("author"),
                hasProfile = rs.getBoolean("has_profile"),
                hasCant = rs.getBoolean("has_cant"),
            )
        } ?: throw NoSuchEntityException(GeometryPlanHeader::class, rowVersion.id)
        logger.daoAccess(FETCH, GeometryPlanHeader::class, rowVersion.id)
        return header
    }

    fun fetchPlanVersions(
        sources: List<PlanSource>,
        bbox: BoundingBox?,
    ): List<RowVersion<GeometryPlan>> {
        val sql = """
            select id, version
            from geometry.plan
            where source::text in (:sources)
              and (:polygon_wkt::varchar is null or postgis.st_intersects(
                plan.bounding_polygon_simple,
                postgis.st_polygonfromtext(:polygon_wkt::varchar, :map_srid)
              ))
        """.trimIndent()
        val params = mapOf(
            "sources" to (sources.ifEmpty { PlanSource.values().toList() }).map(PlanSource::name),
            "polygon_wkt" to bbox?.let { b -> create2DPolygonString(b.polygonFromCorners) },
            "map_srid" to LAYOUT_SRID.code,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("id", "version")
        }
    }

    fun fetchPlanVersions() = fetchRowVersions<GeometryPlan>(GEOMETRY_PLAN)

    fun fetchPlanVersion(id: IntId<GeometryPlan>) = fetchRowVersion(id, GEOMETRY_PLAN)

    fun fetchManyPlanVersions(ids: List<IntId<GeometryPlan>>) = fetchManyRowVersions(ids, GEOMETRY_PLAN)

    fun fetchAlignmentPlanVersion(alignmentId: IntId<GeometryAlignment>): RowVersion<GeometryPlan> {
        //language=SQL
        val sql = """
            select plan.id, plan.version
            from geometry.alignment 
              left join geometry.plan on plan.id = alignment.plan_id
            where alignment.id = :alignment_id
        """.trimIndent()
        val params = mapOf(
            "alignment_id" to alignmentId.intValue,
        )
        return jdbcTemplate.queryOne(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
    }


    fun fetchPlanChangeTime(): Instant = fetchLatestChangeTime(GEOMETRY_PLAN)

    fun fetchPlanAreas(mapBoundingBox: BoundingBox): List<GeometryPlanArea> {
        val searchPolygonWkt = create2DPolygonString(mapBoundingBox.polygonFromCorners)
        val sql = """
          select 
            plan.id,
            plan_file.name as file_name,
            postgis.st_astext(plan.bounding_polygon_simple) as bounding_polygon
          from geometry.plan
            left join geometry.plan_file on plan.id = plan_file.plan_id
            where postgis.st_intersects(
                  plan.bounding_polygon_simple, 
                  postgis.st_polygonfromtext(:polygon_wkt, :map_srid)
              )
        """.trimIndent()
        val params = mapOf(
            "polygon_wkt" to searchPolygonWkt,
            "map_srid" to LAYOUT_SRID.code,
        )
        val result = jdbcTemplate.query(sql, params) { rs, _ ->
            val area = GeometryPlanArea(
                id = rs.getIntId("id"),
                fileName = rs.getFileName("file_name"),
                polygon = rs.getPolygonPointList("bounding_polygon"),
            )
            area
        }.filterNotNull()
        logger.daoAccess(FETCH, GeometryPlanArea::class, result.map { a -> a.id })
        return result
    }

    @Cacheable(CACHE_GEOMETRY_PLAN, sync = true)
    fun fetchPlan(planVersion: RowVersion<GeometryPlan>): GeometryPlan {
        val sql = """
            select
              plan.id,
              plan.source,
              plan.linear_unit,
              plan.direction_unit,
              plan.track_number_id,
              plan.track_number_description,
              plan.srid,
              plan.coordinate_system_name,
              plan.vertical_coordinate_system,
              plan.plan_time,
              plan.upload_time,
              plan.plan_project_id,
              plan_author.id as author_id,
              plan_author.company_name as author_company_name,
              plan_application.id as application_id,
              plan_application.name as application_name,
              plan_application.manufacturer as application_manufacturer,
              plan_application.application_version as application_version,
              plan_file.name as file_name,
              plan.oid,
              plan.plan_phase,
              plan.plan_decision,
              plan.measurement_method,
              plan.message
            from geometry.plan 
              left join geometry.plan_file on plan_file.plan_id = plan.id
              left join geometry.plan_author on plan.plan_author_id = plan_author.id
              left join geometry.plan_application on plan.plan_application_id = plan_application.id
            where plan.id = :plan_id
        """.trimIndent()
        val params = mapOf("plan_id" to planVersion.id.intValue)

        val plan = jdbcTemplate.query(sql, params) { rs, i ->
            val units = GeometryUnits(
                coordinateSystemSrid = rs.getSridOrNull("srid"),
                coordinateSystemName = rs.getString("coordinate_system_name")?.let(::CoordinateSystemName),
                verticalCoordinateSystem = rs.getEnumOrNull<VerticalCoordinateSystem>("vertical_coordinate_system"),
                directionUnit = rs.getEnum("direction_unit"),
                linearUnit = rs.getEnum("linear_unit"),
            )
            val authorId = rs.getIntIdOrNull<Author>("author_id")
            val geometryPlan = GeometryPlan(
                id = rs.getIntId("id"),
                source = rs.getEnum<PlanSource>("source"),
                project = getProject(rs.getIntId("plan_project_id")),
                author = authorId?.let { id ->
                    Author(id = id, companyName = MetaDataName(rs.getString("author_company_name")))
                },
                application = Application(
                    id = rs.getIntId("application_id"),
                    name = MetaDataName(rs.getString("application_name")),
                    manufacturer = MetaDataName(rs.getString("application_manufacturer")),
                    version = MetaDataName(rs.getString("application_version")),
                ),
                planTime = rs.getInstantOrNull("plan_time"),
                units = units,
                trackNumberId = rs.getIntIdOrNull("track_number_id"),
                trackNumberDescription = PlanElementName(rs.getString("track_number_description")),
                alignments = fetchAlignments(units, planVersion.id),
                switches = fetchSwitches(planId = planVersion.id, switchId = null),
                kmPosts = fetchKmPosts(planVersion.id),
                fileName = rs.getFileName("file_name"),
                oid = rs.getOidOrNull("oid"),
                planPhase = rs.getEnumOrNull<PlanPhase>("plan_phase"),
                decisionPhase = rs.getEnumOrNull<PlanDecisionPhase>("plan_decision"),
                measurementMethod = rs.getEnumOrNull<MeasurementMethod>("measurement_method"),
                dataType = DataType.STORED,
                message = rs.getFreeTextOrNull("message"),
                uploadTime = rs.getInstant("upload_time"),
            )
            geometryPlan
        }.firstOrNull() ?: throw NoSuchEntityException(GeometryPlan::class, planVersion.id)
        logger.daoAccess(FETCH, GeometryPlan::class, planVersion)
        return plan
    }

    fun getProject(projectId: IntId<Project>): Project {
        val sql = """
            select
              id
            , name
            , description
            from geometry.plan_project
            where id = :id
        """.trimIndent()
        val params = mapOf("id" to projectId.intValue)

        val project = jdbcTemplate.query(sql, params) { rs, _ ->
            Project(
                id = rs.getIntId("id"),
                name = ProjectName(rs.getString("name")),
                description = rs.getFreeTextOrNull("description"),
            )
        }.firstOrNull() ?: throw NoSuchEntityException(Project::class, projectId)
        logger.daoAccess(FETCH, Project::class, project.id)
        return project
    }

    fun findProject(projectName: ProjectName): Project? {
        val sql = """
            select
              id, name, description
            from geometry.plan_project
            where unique_name = common.nospace_lowercase(:name)
        """.trimIndent()

        val params = mapOf("name" to projectName)
        val project = jdbcTemplate.query(sql, params) { rs, _ ->
            Project(
                id = rs.getIntId("id"),
                name = ProjectName(rs.getString("name")),
                description = rs.getFreeTextOrNull("description"),
            )
        }.firstOrNull()

        project?.also { logger.daoAccess(FETCH, Project::class, it.id) }
        return project
    }

    fun fetchProjects(): List<Project> {
        val sql = """
            select
              id
            , name
            , description
            from geometry.plan_project
        """.trimIndent()
        val projects = jdbcTemplate.query(sql) { rs, _ ->
            Project(
                id = rs.getIntId("id"),
                name = ProjectName(rs.getString("name")),
                description = rs.getFreeTextOrNull("description"),
            )
        }

        logger.daoAccess(FETCH, Project::class)
        return projects
    }

    fun findAuthor(companyName: MetaDataName): Author? {
        val sql = """
            select
              id
            , company_name
            from geometry.plan_author
            where unique_company_name = common.nospace_lowercase(:company_name)
        """.trimIndent()

        val params = mapOf("company_name" to companyName)
        val author = jdbcTemplate.query(sql, params) { rs, _ ->
            Author(
                id = rs.getIntId("id"),
                companyName = MetaDataName(rs.getString("company_name")),
            )
        }.firstOrNull()

        author?.also { logger.daoAccess(FETCH, Author::class, it.id) }
        return author
    }

    fun getAuthor(authorId: IntId<Author>): Author {
        val sql = """
            select
              id
            , company_name
            from geometry.plan_author
            where id = :id
        """.trimIndent()
        val params = mapOf("id" to authorId.intValue)

        val author = jdbcTemplate.query(sql, params) { rs, _ ->
            Author(
                id = rs.getIntId("id"),
                companyName = MetaDataName(rs.getString("company_name")),
            )
        }.firstOrNull() ?: throw NoSuchEntityException(Project::class, authorId)
        logger.daoAccess(FETCH, Author::class, author.id)
        return author
    }

    fun fetchAuthors(): List<Author> {
        val sql = """
            select
              id
            , company_name
            from geometry.plan_author
        """.trimIndent()
        val authors = jdbcTemplate.query(sql) { rs, _ ->
            Author(
                id = rs.getIntId("id"),
                companyName = MetaDataName(rs.getString("company_name")),
            )
        }

        logger.daoAccess(FETCH, Author::class, authors.map { it.id })
        return authors
    }

    fun fetchAlignments(
        units: GeometryUnits,
        planId: IntId<GeometryPlan>? = null,
        geometryAlignmentId: IntId<GeometryAlignment>? = null,
    ): List<GeometryAlignment> {

        val sql = """
            select 
              alignment.id, alignment.track_number_id, alignment.oid_part, 
              alignment.name, alignment.state, alignment.description,
              alignment.sta_start,
              alignment.profile_name,
              alignment.cant_name, alignment.cant_description, 
              alignment.cant_gauge, alignment.cant_rotation_point,
              alignment.feature_type_code
            from geometry.alignment 
            where (:plan_id::int is null or alignment.plan_id = :plan_id)
              and (:alignment_id::int is null or alignment.id = :alignment_id)
            order by alignment.track_number_id, alignment.id
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("plan_id" to planId?.intValue, "alignment_id" to geometryAlignmentId?.intValue)
        ) { rs, _ ->
            val alignmentId = rs.getIntId<GeometryAlignment>("id")
            val profileName = rs.getString("profile_name")
            val cantName = rs.getString("cant_name")
            val featureTypeCode = rs.getFeatureTypeCodeOrNull("feature_type_code")
            GeometryAlignment(
                id = alignmentId,
                trackNumberId = rs.getIntIdOrNull("track_number_id"),
                name = AlignmentName(rs.getString("name")),
                description = rs.getFreeTextOrNull("description"),
                oidPart = rs.getFreeTextOrNull("oid_part"),
                state = rs.getEnumOrNull<PlanState>("state"),
                staStart = rs.getBigDecimal("sta_start"),
                elements = fetchElements(alignmentId, units),
                profile = profileName?.let { name ->
                    GeometryProfile(
                        name = PlanElementName(name),
                        elements = fetchProfileElements(alignmentId)
                    )
                },
                cant = cantName?.let { name ->
                    GeometryCant(
                        name = PlanElementName(name),
                        description = PlanElementName(rs.getString("cant_description")),
                        gauge = rs.getBigDecimal("cant_gauge"),
                        rotationPoint = rs.getEnum("cant_rotation_point"),
                        points = fetchCantPoints(alignmentId),
                    )
                },
                featureTypeCode = featureTypeCode,
            )
        }
    }

    @Cacheable(CACHE_GEOMETRY_SWITCH, sync = true)
    fun getSwitch(id: IntId<GeometrySwitch>) = getOne(id, fetchSwitches(planId = null, switchId = id))

    private fun fetchSwitches(planId: IntId<GeometryPlan>?, switchId: IntId<GeometrySwitch>?): List<GeometrySwitch> {
        val sql = """
            select
              id,
              name,
              switch_structure_id,
              type_name,
              state
            from geometry.switch
            where (:plan_id::int is null or plan_id = :plan_id) 
              and (:switch_id::int is null or id = :switch_id)
        """.trimIndent()
        val params = mapOf("plan_id" to planId?.intValue, "switch_id" to switchId?.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            val id: IntId<GeometrySwitch> = rs.getIntId("id")
            GeometrySwitch(
                id = id,
                name = SwitchName(rs.getString("name")),
                state = rs.getEnumOrNull<PlanState>("state"),
                joints = getSwitchJoints(id),
                switchStructureId = rs.getIntIdOrNull("switch_structure_id"),
                typeName = GeometrySwitchTypeName(rs.getString("type_name")),
            )
        }
    }

    private fun getSwitchJoints(switchId: IntId<GeometrySwitch>): List<GeometrySwitchJoint> {
        val sql = """
            select 
              number,
              postgis.st_x(location) as location_x,
              postgis.st_y(location) as location_y
            from geometry.switch_joint
            where switch_id = :switch_id
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("switch_id" to switchId.intValue)) { rs, _ ->
            GeometrySwitchJoint(
                number = rs.getJointNumber("number"),
                location = rs.getPoint("location_x", "location_y"),
            )
        }.sortedBy { j -> j.number }
    }

    fun getSwitchSrid(id: IntId<GeometrySwitch>): Srid? {
        val sql = """
            select
                plan.srid
            from geometry.switch
                left join geometry.plan plan on switch.plan_id = plan.id
            where switch.id = :switch_id
        """.trimIndent()
        val params = mapOf(
            "switch_id" to id.intValue
        )
        logger.daoAccess(FETCH, GeometrySwitch::class, params)
        return jdbcTemplate.queryOne(sql, params, id.toString()) { rs, _ -> rs.getSridOrNull("srid") }
    }

    fun getKmPost(id: IntId<GeometryKmPost>) = getOne(id, fetchKmPosts(planId = null, kmPostId = id))

    private fun fetchKmPosts(
        planId: IntId<GeometryPlan>? = null,
        kmPostId: IntId<GeometryKmPost>? = null,
    ): List<GeometryKmPost> {
        val sql = """
            select
              km_post.id,
              km_post.track_number_id, 
              km_post.km_post_index, 
              km_post.sta_back, 
              km_post.sta_ahead,
              km_post.sta_internal, 
              km_post.km_number,
              km_post.description,
              km_post.state,
              postgis.st_x(km_post.location) as location_x,
              postgis.st_y(km_post.location) as location_y
            from geometry.km_post
            where (:plan_id::int is null or plan_id = :plan_id) 
              and (:km_post_id::int is null or id = :km_post_id)
        """.trimIndent()
        val params = mapOf(
            "plan_id" to planId?.intValue,
            "km_post_id" to kmPostId?.intValue
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            GeometryKmPost(
                id = rs.getIntId("id"),
                trackNumberId = rs.getIntIdOrNull("track_number_id"),
                staBack = rs.getBigDecimal("sta_back"),
                staAhead = rs.getBigDecimal("sta_ahead"),
                staInternal = rs.getBigDecimal("sta_internal"),
                kmNumber = rs.getKmNumberOrNull("km_number"),
                description = PlanElementName(rs.getString("description")),
                location = rs.getPointOrNull("location_x", "location_y"),
                state = rs.getEnumOrNull<PlanState>("state"),
            )
        }
    }

    fun getKmPostSrid(id: IntId<GeometryKmPost>): Srid? {
        val sql = """
            select
                plan.srid
            from geometry.km_post
                left join geometry.plan plan on km_post.plan_id = plan.id
            where km_post.id = :km_post_id
        """.trimIndent()
        val params = mapOf(
            "km_post_id" to id.intValue
        )
        return jdbcTemplate.queryOne(sql, params, id.toString()) { rs, _ -> rs.getSridOrNull("srid") }
    }

    fun fetchElement(geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        val alignmentId = IntId<GeometryAlignment>(geometryElementId.parentId)
        val planUnits = fetchPlanUnits(alignmentId)
        val element = fetchElements(alignmentId, planUnits.units, geometryElementId).firstOrNull()
        logger.daoAccess(FETCH, GeometryElement::class, geometryElementId)
        return element ?: throw NoSuchEntityException(GeometryElement::class, geometryElementId)
    }

    fun fetchPlanUnits(alignmentId: IntId<GeometryAlignment>): GeometryPlanUnits {
        val sql = """
            select 
                plan.id, 
                plan.direction_unit, 
                plan.linear_unit, 
                plan.srid, 
                plan.coordinate_system_name,
                plan.vertical_coordinate_system
            from geometry.alignment alignment 
              left join geometry.plan plan on alignment.plan_id = plan.id
            where alignment.id = :alignment_id
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("alignment_id" to alignmentId.intValue)) { rs, _ ->
            GeometryPlanUnits(
                id = rs.getIntId("id"),
                units = GeometryUnits(
                    coordinateSystemSrid = rs.getSridOrNull("srid"),
                    coordinateSystemName = rs.getString("coordinate_system_name")?.let(::CoordinateSystemName),
                    verticalCoordinateSystem = rs.getEnumOrNull<VerticalCoordinateSystem>("vertical_coordinate_system"),
                    directionUnit = rs.getEnum("direction_unit"),
                    linearUnit = rs.getEnum("linear_unit"),
                ),
            )
        }.firstOrNull() ?: throw NoSuchEntityException(GeometryAlignment::class, alignmentId)
    }

    fun fetchElements(
        alignmentId: IntId<GeometryAlignment>,
        units: GeometryUnits,
        geometryElementId: IndexedId<GeometryElement>? = null,
    ): List<GeometryElement> {
        val sql = """
            select
              alignment_id,
              element_index,
              name,
              oid_part,
              type,
              sta_start,
              length,
              postgis.st_x(start_point) as start_x,
              postgis.st_y(start_point) as start_y,
              postgis.st_x(end_point) as end_x,
              postgis.st_y(end_point) as end_y,
              rotation,
              curve_radius,
              curve_chord,
              postgis.st_x(curve_center_point) as curve_center_x,
              postgis.st_y(curve_center_point) as curve_center_y,
              spiral_dir_start,
              spiral_dir_end,
              spiral_radius_start,
              spiral_radius_end,
              postgis.st_x(spiral_pi_point) as spiral_pi_x,
              postgis.st_y(spiral_pi_point) as spiral_pi_y,
              clothoid_constant,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number
            from geometry.element
            where alignment_id = :alignment_id
              and (:element_index::int is null or element_index = :element_index)
            order by element_index
        """.trimIndent()

        val params = mapOf(
            "alignment_id" to alignmentId.intValue,
            "element_index" to geometryElementId?.index
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val id = rs.getIndexedId<GeometryElement>("alignment_id", "element_index")
            when (rs.getEnum<GeometryElementType>("type")) {
                LINE -> GeometryLine(
                    elementData = getElementData(rs),
                    switchData = getSwitchData(rs),
                    id = id,
                )
                CURVE -> GeometryCurve(
                    elementData = getElementData(rs),
                    curveData = getCurveData(rs),
                    switchData = getSwitchData(rs),
                    id = id,
                )
                CLOTHOID -> GeometryClothoid(
                    elementData = getElementData(rs),
                    spiralData = getSpiralData(rs, units),
                    switchData = getSwitchData(rs),
                    constant = rs.getBigDecimal("clothoid_constant"),
                    id = id,
                )
                BIQUADRATIC_PARABOLA -> BiquadraticParabola(
                    elementData = getElementData(rs),
                    spiralData = getSpiralData(rs, units),
                    switchData = getSwitchData(rs),
                    id = id,
                )
            }
        }
    }

    @Transactional
    fun removeReferencesToTrackNumber(id: IntId<TrackLayoutTrackNumber>): RemovedTrackNumberReferenceIds {
        return RemovedTrackNumberReferenceIds(
            kmPostIds = removeKmPostReferenceToTrackNumber(id),
            alignmentIds = removeAlignmentReferenceToTrackNumber(id),
            planIds = removePlanReferenceToTrackNumber(id)
        )
    }

    private fun removeKmPostReferenceToTrackNumber(id: IntId<TrackLayoutTrackNumber>): List<IntId<GeometryKmPost>> {
        val sql = """
          update geometry.km_post
          set track_number_id = null 
          where track_number_id = :id
          returning id as km_post_id
        """.trimIndent()

        return jdbcTemplate
            .query<IntId<GeometryKmPost>>(sql, mapOf("id" to id.intValue)) { rs, _ -> IntId(rs.getInt("km_post_id")) }
            .also { ids -> logger.daoAccess(UPDATE, GeometryKmPost::class, ids) }
    }


    private fun removeAlignmentReferenceToTrackNumber(id: IntId<TrackLayoutTrackNumber>): List<IntId<GeometryAlignment>> {
        val sql = """
          update geometry.alignment
          set track_number_id = null 
          where track_number_id = :id
          returning id as alignment_id
        """.trimIndent()

        return jdbcTemplate
            .query<IntId<GeometryAlignment>>(sql, mapOf("id" to id.intValue)) { rs, _ -> IntId(rs.getInt("alignment_id")) }
            .also { ids -> logger.daoAccess(UPDATE, GeometryAlignment::class, ids)}

    }

    private fun removePlanReferenceToTrackNumber(id: IntId<TrackLayoutTrackNumber>): List<IntId<GeometryPlan>> {
        val sql = """
          update geometry.plan
          set track_number_id = null 
          where track_number_id = :id
          returning id as plan_id
        """.trimIndent()

        return jdbcTemplate
            .query<IntId<GeometryPlan>>(sql, mapOf("id" to id.intValue)) { rs, _ -> IntId(rs.getInt("plan_id")) }
            .also {  ids -> logger.daoAccess(UPDATE, GeometryPlan::class, ids)}
    }

    private fun getElementData(rs: ResultSet): ElementData {
        return ElementData(
            name = rs.getString("name")?.let(::PlanElementName),
            oidPart = rs.getString("oid_part")?.let(::PlanElementName),
            start = rs.getPoint("start_x", "start_y"),
            end = rs.getPoint("end_x", "end_y"),
            staStart = rs.getBigDecimal("sta_start"),
            length = rs.getBigDecimal("length"),
        )
    }

    private fun getSwitchData(rs: ResultSet): SwitchData = SwitchData(
        switchId = rs.getIntIdOrNull("switch_id"),
        startJointNumber = rs.getJointNumberOrNull("switch_start_joint_number"),
        endJointNumber = rs.getJointNumberOrNull("switch_end_joint_number"),
    )

    private fun getCurveData(rs: ResultSet): CurveData = CurveData(
        rotation = rs.getEnum("rotation"),
        radius = rs.getBigDecimal("curve_radius"),
        chord = rs.getBigDecimal("curve_chord"),
        center = rs.getPoint("curve_center_x", "curve_center_y"),
    )

    private fun getSpiralData(rs: ResultSet, units: GeometryUnits): SpiralData = SpiralData(
        rotation = rs.getEnum("rotation"),
        directionStart = rs.getBigDecimal("spiral_dir_start")?.let { bd -> toAngle(bd, units.directionUnit) },
        directionEnd = rs.getBigDecimal("spiral_dir_end")?.let { bd -> toAngle(bd, units.directionUnit) },
        radiusStart = rs.getBigDecimal("spiral_radius_start"),
        radiusEnd = rs.getBigDecimal("spiral_radius_end"),
        pi = rs.getPoint("spiral_pi_x", "spiral_pi_y"),
    )

    private fun fetchProfileElements(alignmentId: IntId<GeometryAlignment>): List<VerticalIntersection> {
        val sql = """
            select type, description, postgis.st_x(point) as point_x, postgis.st_y(point) as point_y, circular_radius, circular_length
            from geometry.vertical_intersection
            where alignment_id = :alignment_id
            order by intersection_index
        """.trimIndent()
        val params = mapOf("alignment_id" to alignmentId.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            when (rs.getEnum<VerticalIntersectionType>("type")) {
                VerticalIntersectionType.POINT -> VIPoint(
                    description = PlanElementName(rs.getString("description")),
                    point = rs.getPoint("point_x", "point_y"),
                )
                VerticalIntersectionType.CIRCULAR_CURVE -> VICircularCurve(
                    description = PlanElementName(rs.getString("description")),
                    point = rs.getPoint("point_x", "point_y"),
                    radius = rs.getBigDecimal("circular_radius"),
                    length = rs.getBigDecimal("circular_length"),
                )
            }
        }
    }

    private fun fetchCantPoints(alignmentId: IntId<GeometryAlignment>): List<GeometryCantPoint> {
        val sql = """
            select station, applied_cant, curvature, transition_type
            from geometry.cant_point
            where alignment_id = :alignment_id
            order by cant_point_index
        """.trimIndent()
        val params = mapOf("alignment_id" to alignmentId.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            GeometryCantPoint(
                station = rs.getBigDecimal("station"),
                appliedCant = rs.getBigDecimal("applied_cant"),
                curvature = rs.getEnum("curvature"),
                transitionType = rs.getEnum("transition_type")
            )
        }
    }

    private fun insertGeometryElements(elementSqlParams: List<Map<String, Any?>>) {
        val sql = """
            insert into geometry.element(
              alignment_id,
              element_index,
              oid_part,
              type,
              name,
              length,
              sta_start,
              start_point,
              end_point,
              rotation,
              curve_radius,
              curve_chord,
              curve_center_point,
              spiral_dir_start, spiral_dir_end,
              spiral_radius_start, spiral_radius_end,
              spiral_pi_point,
              clothoid_constant,
              switch_id,
              switch_start_joint_number,
              switch_end_joint_number
            )
            values(
              :alignment_id,
              :element_index,
              :oid_part,
              :type::geometry.element_type,
              :name,
              :length,
              :sta_start,
              postgis.st_point(:start_point_x, :start_point_y),
              postgis.st_point(:end_point_x, :end_point_y),
              :rotation::common.rotation_direction,
              :curve_radius,
              :curve_chord,
              postgis.st_point(:curve_center_point_x, :curve_center_point_y),
              :spiral_dir_start, :spiral_dir_end,
              :spiral_radius_start, :spiral_radius_end,
              postgis.st_point(:spiral_pi_point_x, :spiral_pi_point_y),
              :clothoid_constant,
              :switch_id,
              :switch_start_joint_number,
              :switch_end_joint_number
            )
        """.trimIndent()
        jdbcTemplate.batchUpdate(sql, elementSqlParams.toTypedArray())
    }

    private fun getGeometryElementSqlParams(
        alignmentId: IntId<GeometryAlignment>,
        elements: List<GeometryElement>,
        switchIds: Map<DomainId<GeometrySwitch>, IntId<GeometrySwitch>>,
    ): List<Map<String, Any?>> {
        return elements.mapIndexed { index, element ->
            val common = mapOf(
                "alignment_id" to alignmentId.intValue,
                "element_index" to index,
                "type" to element.type.name,
                "oid_part" to element.oidPart,
                "name" to element.name,
                "sta_start" to element.staStart,
                "length" to element.length,
                "rotation" to null,
                "curve_radius" to null,
                "curve_chord" to null,
                "clothoid_constant" to null,
                "spiral_dir_start" to null,
                "spiral_dir_end" to null,
                "spiral_radius_start" to null,
                "spiral_radius_end" to null,
                "spiral_dir_end" to null,
            )
                .plus(getPointSqlParams("start_point", element.start))
                .plus(getPointSqlParams("end_point", element.end))
                .plus(getPointSqlParams("curve_center_point", null))
                .plus(getPointSqlParams("spiral_pi_point", null))
            val specific = when (element) {
                is GeometryLine -> mapOf()
                is GeometryCurve -> mapOf(
                    "rotation" to element.rotation.name,
                    "curve_radius" to element.radius,
                    "curve_chord" to element.chord,
                )
                    .plus(getPointSqlParams("curve_center_point", element.center))
                is GeometrySpiral -> mapOf(
                    "rotation" to element.rotation.name,
                    "spiral_dir_start" to element.directionStart?.original,
                    "spiral_dir_end" to element.directionEnd?.original,
                    "spiral_radius_start" to element.radiusStart,
                    "spiral_radius_end" to element.radiusEnd,
                )
                    .plus(getPointSqlParams("spiral_pi_point", element.pi))
                    .plus(
                        when (element) {
                            is GeometryClothoid -> mapOf("clothoid_constant" to element.constant)
                            is BiquadraticParabola -> mapOf()
                        }
                    )
            }
            val switch = mapOf(
                "switch_id" to element.switchId?.let { tempId -> switchIds[tempId]?.intValue },
                "switch_start_joint_number" to element.startJointNumber?.intValue,
                "switch_end_joint_number" to element.endJointNumber?.intValue,
            )

            common + specific + switch
        }
    }

    private fun insertVerticalIntersections(viParams: List<Map<String, Any?>>) {
        val sql = """
            insert into geometry.vertical_intersection(
              alignment_id,
              intersection_index,
              type,
              description,
              point,
              circular_radius,
              circular_length
            ) 
            values (
              :alignment_id,
              :intersection_index,
              :type::geometry.vertical_intersection_type,
              :description,
              postgis.st_point(:point_x, :point_y),
              :circular_radius,
              :circular_length
            )
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, viParams.toTypedArray())
    }

    private fun getVerticalIntersectionSqlParams(
        alignmentId: IntId<GeometryAlignment>,
        vis: List<VerticalIntersection>,
    ): List<Map<String, Any?>> {
        return vis.mapIndexed { index, vi ->
            val common = mapOf(
                "alignment_id" to alignmentId.intValue,
                "intersection_index" to index,
                "description" to vi.description,
                "point_x" to vi.point.x,
                "point_y" to vi.point.y,
                "circular_radius" to null,
                "circular_length" to null,
            )
            val typed = when (vi) {
                is VIPoint -> mapOf("type" to VerticalIntersectionType.POINT.name)
                is VICircularCurve -> mapOf(
                    "type" to VerticalIntersectionType.CIRCULAR_CURVE.name,
                    "circular_radius" to vi.radius,
                    "circular_length" to vi.length,
                )
            }
            common + typed
        }
    }

    private fun insertCantPoints(cantPointParams: List<Map<String, Any?>>) {
        val sql = """
            insert into geometry.cant_point(
              alignment_id, 
              cant_point_index, 
              station, 
              applied_cant, 
              curvature, 
              transition_type
            ) 
            values (
              :alignment_id, 
              :cant_point_index, 
              :station, 
              :applied_cant, 
              :curvature::common.rotation_direction, 
              :transition_type::geometry.cant_transition_type
            )
        """.trimIndent()
        jdbcTemplate.batchUpdate(sql, cantPointParams.toTypedArray())
    }

    private fun getCantPointSqlParams(
        alignmentId: IntId<GeometryAlignment>,
        points: List<GeometryCantPoint>,
    ): List<Map<String, Any?>> {
        return points.mapIndexed { index, cantPoint ->
            mapOf(
                "alignment_id" to alignmentId.intValue,
                "cant_point_index" to index,
                "station" to cantPoint.station,
                "applied_cant" to cantPoint.appliedCant,
                "curvature" to cantPoint.curvature.name,
                "transition_type" to cantPoint.transitionType.name,
            )
        }
    }

    private fun getPointSqlParams(name: String, point: Point?): Map<String, Any?> {
        return mapOf("${name}_x" to point?.x, "${name}_y" to point?.y)
    }

    fun getMeasurementMethodForSwitch(id: IntId<GeometrySwitch>): MeasurementMethod? {
        val sql = """
            select plan.measurement_method
            from geometry.plan
                join geometry.switch on plan.id = switch.plan_id
            where switch.id = :switch_id
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            mapOf(
                "switch_id" to id.intValue
            )
        ) { rs, _ ->
            rs.getEnumOrNull<MeasurementMethod>("measurement_method")
        }.firstOrNull()
    }

    /**
     * If planIds is null, returns all plans' linking summaries
     */
    fun getLinkingSummaries(planIds: List<IntId<GeometryPlan>>?): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        if (planIds?.isEmpty() == true) {
            return mapOf()
        }

        // For a given linkable object, treat the first version of it that was linked to a given plan as the one where
        // linking happened, hence taking that version's change_time and change_user as the linking time and user.
        // For objects composed of parts where it's the parts that get linked (i.e. location tracks and reference lines,
        // made of segments), versioning still goes based on the segment, but the change time and change user come
        // from the actual publishable unit.
        val sql = """
            select
              plan_id,
              min(change_time) as linked_at,
              string_agg(distinct change_user, ', ' order by change_user) as linked_by_users
              from (
                select plan_id, segment.change_user, segment.change_time
                  from geometry.alignment
                    join lateral
                    (select track_object.change_time, track_object.change_user
                       from layout.alignment_version 
                       inner join layout.segment_version on alignment_version.id = segment_version.alignment_id
                           and alignment_version.version = segment_version.alignment_version
                       join lateral (
                         select change_time, change_user
                         from layout.location_track_version
                         where location_track_version.alignment_id = alignment_version.id
                           and location_track_version.alignment_version = alignment_version.version
                           and not draft
                         union all
                         select change_time, change_user
                         from layout.reference_line_version
                         where reference_line_version.alignment_id = alignment_version.id
                           and reference_line_version.alignment_version = alignment_version.version
                           and not draft
                       ) track_object on (true)
                       where segment_version.geometry_alignment_id = alignment.id
                       order by alignment_version asc
                       limit 1
                    ) segment on (true)
                union all
                (
                  select geometry_switch.plan_id, layout_switch.change_user, layout_switch.change_time
                    from geometry.switch geometry_switch
                      join lateral
                      (select change_time, change_user
                         from layout.switch_version
                         where switch_version.geometry_switch_id = geometry_switch.id
                           and not draft
                         order by version asc
                         limit 1) layout_switch on (true)
                )
                union all
                (
                  select geometry_km_post.plan_id, layout_km_post.change_user, layout_km_post.change_time
                    from geometry.km_post geometry_km_post
                      join lateral
                      (select change_time, change_user
                         from layout.km_post_version
                         where km_post_version.geometry_km_post_id = geometry_km_post.id
                           and not draft
                         order by version asc
                         limit 1) layout_km_post on (true)
                )
              ) as linked_layout_object
              where plan_id in (:plan_ids) or :return_all
              group by plan_id
            """.trimIndent()

        return jdbcTemplate.query(
            sql,
            mapOf(
                "plan_ids" to (planIds?.map { it.intValue } ?: listOf(null)),
                "return_all" to (planIds == null)
            )
        ) { rs, _ ->
            val linkedAt = rs.getInstant("linked_at")
            val linkedByUsers = rs.getString("linked_by_users")
            rs.getIntId<GeometryPlan>("plan_id") to GeometryPlanLinkingSummary(linkedAt, linkedByUsers)
        }.associate { it }
    }
}
