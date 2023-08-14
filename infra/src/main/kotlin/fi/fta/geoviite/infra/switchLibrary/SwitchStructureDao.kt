package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.configuration.CACHE_COMMON_SWITCH_STRUCTURE
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class SwitchStructureDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Cacheable(CACHE_COMMON_SWITCH_STRUCTURE, sync = true)
    fun fetchSwitchStructures(): List<SwitchStructure> {
        val sql = """
            select
                id,
                type,
                presentation_joint_number
            from common.switch_structure
        """.trimIndent()
        val switchStructures = jdbcTemplate.query(sql) { rs, _ ->
            val switchTypeId = rs.getIntId<SwitchStructure>("id")
            val switchType = SwitchStructure(
                id = switchTypeId,
                type = SwitchType(rs.getString("type")),
                presentationJointNumber = rs.getJointNumber("presentation_joint_number"),
                joints = fetchSwitchTypeJoints(switchTypeId),
                alignments = fetchSwitchTypeAlignments(switchTypeId)
            )
            switchType
        }
        logger.daoAccess(AccessType.FETCH, SwitchStructure::class, switchStructures.map { it.id })
        return switchStructures
    }

    @Cacheable(CACHE_COMMON_SWITCH_STRUCTURE, sync = true)
    fun fetchSwitchStructure(version: RowVersion<SwitchStructure>): SwitchStructure {
        val sql = """
            select
                id,
                type,
                presentation_joint_number
            from common.switch_structure
            where
              id = :id
        """.trimIndent()
        val params = mapOf(
            "id" to version.id.intValue
        )
        val switchStructure = jdbcTemplate.queryOne(sql, params, version.toString()) { rs, _ ->
            val switchTypeId = rs.getIntId<SwitchStructure>("id")
            val switchType = SwitchStructure(
                id = switchTypeId,
                type = SwitchType(rs.getString("type")),
                presentationJointNumber = rs.getJointNumber("presentation_joint_number"),
                joints = fetchSwitchTypeJoints(switchTypeId),
                alignments = fetchSwitchTypeAlignments(switchTypeId)
            )
            switchType
        }
        logger.daoAccess(AccessType.FETCH, SwitchStructure::class, switchStructure)
        return switchStructure

    }

    private fun fetchSwitchTypeJoints(switchStructureId: IntId<SwitchStructure>): List<SwitchJoint> {
        val sql = """
            select
              number,
              postgis.st_x(location) as x,
              postgis.st_y(location) as y
            from common.switch_joint
            where switch_structure_id = :switch_structure_id
        """.trimIndent()

        val params = mapOf("switch_structure_id" to switchStructureId.intValue)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val switchTypeJoint = SwitchJoint(
                number = rs.getJointNumber("number"),
                location = rs.getPoint("x", "y")
            )
            switchTypeJoint
        }
    }

    private fun fetchSwitchTypeElements(switchAlignmentId: IntId<SwitchAlignment>): List<SwitchElement> {
        val sql = """
            select
              alignment_id,
              element_index,
              type,
              postgis.st_x(start_point) as start_x,
              postgis.st_y(start_point) as start_y,
              postgis.st_x(end_point) as end_x,
              postgis.st_y(end_point) as end_y,
              curve_radius
            from common.switch_element
            where alignment_id = :alignment_id
            order by element_index
        """.trimIndent()

        val params = mapOf("alignment_id" to switchAlignmentId.intValue)

        return jdbcTemplate.query(sql, params) { rs, _ ->
            val elementId = rs.getIndexedId<SwitchElement>("alignment_id", "element_index")
            when (rs.getEnum<SwitchElementType>("type")) {
                SwitchElementType.LINE -> SwitchElementLine(
                    id = elementId,
                    start = rs.getPoint("start_x", "start_y"),
                    end = rs.getPoint("end_x", "end_y")
                )
                SwitchElementType.CURVE -> SwitchElementCurve(
                    id = elementId,
                    start = rs.getPoint("start_x", "start_y"),
                    end = rs.getPoint("end_x", "end_y"),
                    radius = rs.getDouble("curve_radius")
                )
            }
        }
    }

    private fun fetchSwitchTypeAlignments(switchStructureId: IntId<SwitchStructure>): List<SwitchAlignment> {
        val sql = """
            select
              id,
              joint_numbers
            from common.switch_alignment
            where switch_structure_id = :switch_structure_id
        """.trimIndent()
        val params = mapOf("switch_structure_id" to switchStructureId.intValue)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            val switchAlignmentId = rs.getIntId<SwitchAlignment>("id")
            val switchTypeAlignment = SwitchAlignment(
                jointNumbers = rs.getIntArray("joint_numbers").map { number -> JointNumber(number) },
                elements = fetchSwitchTypeElements(switchAlignmentId)
            )
            switchTypeAlignment
        }
    }

    @Transactional
    fun insertSwitchStructure(switchStructure: SwitchStructure): RowVersion<SwitchStructure> {
        val sql = """
            insert into common.switch_structure
                (
                  type,
                  presentation_joint_number
                )
            values
                (
                  :type,
                  :presentation_joint_number
                )
            returning id, version
        """.trimIndent()

        val params = mapOf(
            "type" to switchStructure.type.typeName,
            "presentation_joint_number" to switchStructure.presentationJointNumber.intValue
        )

        jdbcTemplate.setUser()
        val version: RowVersion<SwitchStructure> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new Location Track")
        insertJoints(version, switchStructure.joints)
        insertAlignment(version, switchStructure.alignments)

        logger.daoAccess(AccessType.INSERT, SwitchStructure::class, version)
        return version
    }

    @Transactional
    fun insertInframodelAlias(alias: String, type: String) {
        val sql = """
            insert into common.inframodel_switch_type_name_alias (alias, type)
            values (:alias, :type)
        """.trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("alias" to alias, "type" to type)
        jdbcTemplate.update(sql, params)
    }

    fun getInframodelAliases(): Map<String, String> {
        val sql = """
            select alias, type from common.inframodel_switch_type_name_alias
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            rs.getString("alias") to rs.getString("type")
        }.associate { it }
    }

    private fun insertJoints(switchStructureId: RowVersion<SwitchStructure>, joints: List<SwitchJoint>) {
        joints.forEach { joint -> insertJoint(switchStructureId, joint) }
    }

    private fun insertJoint(
        switchStructureId: RowVersion<SwitchStructure>,
        joint: SwitchJoint
    ) {
        val sql = """
            insert into common.switch_joint
                (
                  switch_structure_id,
                  number,
                  location
                )
            values
                (
                  :switch_structure_id,
                  :joint_number,
                  postgis.st_setsrid(
                    postgis.st_point(
                      :location_x, :location_y
                    ), 
                    :srid
                  )
                )
        """.trimIndent()

        val params = mapOf(
            "switch_structure_id" to switchStructureId.id.intValue,
            "joint_number" to joint.number.intValue,
            "location_x" to joint.location.x,
            "location_y" to joint.location.y,
            "srid" to LAYOUT_SRID.code
        )

        jdbcTemplate.update(sql, params)
    }

    private fun insertAlignment(switchStructureId: RowVersion<SwitchStructure>, alignments: List<SwitchAlignment>) {
        alignments.forEach { alignment -> insertAlignment(switchStructureId, alignment) }
    }

    private fun insertAlignment(
        switchStructureId: RowVersion<SwitchStructure>,
        alignment: SwitchAlignment
    ): RowVersion<SwitchAlignment> {
        val sql = """
            insert into common.switch_alignment
                (
                  switch_structure_id,
                  joint_numbers
                )
            values
                (
                  :switch_structure_id,
                  array[ :joint_numbers ]
                )
            returning id, version
        """.trimIndent()

        val params = mapOf(
            "switch_structure_id" to switchStructureId.id.intValue,
            "joint_numbers" to alignment.jointNumbers.map { it.intValue },
        )

        val version: RowVersion<SwitchAlignment> =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }
                ?: throw IllegalStateException("Failed to generate ID for new switch structure alignment")
        insertElements(version, alignment.elements)
        return version
    }

    private fun insertElements(switchAlignmentId: RowVersion<SwitchAlignment>, elements: List<SwitchElement>) {
        elements.forEachIndexed { index, element -> insertElement(switchAlignmentId, element, index) }
    }

    private fun insertElement(
        switchAlignmentId: RowVersion<SwitchAlignment>,
        element: SwitchElement,
        index: Int
    ) {
        val sql = """
            insert into common.switch_element
                (
                  alignment_id,
                  element_index,
                  type,
                  start_point,
                  end_point,
                  curve_radius
                )
            values
                (
                  :alignment_id,
                  :element_index,
                  :type::common.switch_element_type,
                  postgis.st_setsrid(
                    postgis.st_point(
                      :start_point_x, :start_point_y
                    ), 
                    :srid
                  ),
                  postgis.st_setsrid(
                    postgis.st_point(
                      :end_point_x, :end_point_y
                    ), 
                    :srid
                  ),
                  :curve_radius
                )
        """.trimIndent()

        val params = mapOf(
            "alignment_id" to switchAlignmentId.id.intValue,
            "element_index" to index,
            "type" to element.type.name,
            "start_point_x" to element.start.x,
            "start_point_y" to element.start.y,
            "end_point_x" to element.end.x,
            "end_point_y" to element.end.y,
            "curve_radius" to (element as? SwitchElementCurve)?.radius,
            "srid" to LAYOUT_SRID.code
        )

        jdbcTemplate.update(sql, params)
    }
}
