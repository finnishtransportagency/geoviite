package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getIntArray
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getJointNumber
import fi.fta.geoviite.infra.util.getOne
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private data class ElementFetchData(
    val alignmentIndex: Int,
    val elementIndex: Int,
    val element: SwitchStructureElement,
)

@Transactional(readOnly = true)
@Component
class SwitchStructureDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchSwitchStructureVersion(id: IntId<SwitchStructure>) = fetchRowVersion(id, DbTable.COMMON_SWITCH_STRUCTURE)

    fun fetchSwitchStructures(): List<SwitchStructure> {
        val sql =
            """
            select
                id,
                version,
                type,
                presentation_joint_number
            from common.switch_structure
        """
                .trimIndent()
        val switchStructures =
            jdbcTemplate.query(sql) { rs, _ ->
                val structureVersion = rs.getRowVersion<SwitchStructure>("id", "version")
                SwitchStructure(
                    version = structureVersion,
                    data =
                        SwitchStructureData(
                            type = SwitchType.of(rs.getString("type")),
                            presentationJointNumber = rs.getJointNumber("presentation_joint_number"),
                            joints = fetchSwitchStructureJoints(structureVersion),
                            alignments = fetchSwitchStructureAlignments(structureVersion),
                        ),
                )
            }
        logger.daoAccess(FETCH, SwitchStructure::class, switchStructures.map { it.id })
        return switchStructures
    }

    fun fetchSwitchStructure(version: RowVersion<SwitchStructure>): SwitchStructure {
        val sql =
            """
            select
                id,
                version,
                type,
                presentation_joint_number
            from common.switch_structure_version
            where id = :id
              and version = :version
        """
                .trimIndent()
        val params = mapOf("id" to version.id.intValue, "version" to version.version)
        val switchStructure =
            jdbcTemplate.queryOne(sql, params, version.toString()) { rs, _ ->
                val structureVersion = rs.getRowVersion<SwitchStructure>("id", "version")
                SwitchStructure(
                    version = structureVersion,
                    data =
                        SwitchStructureData(
                            type = SwitchType.of(rs.getString("type")),
                            presentationJointNumber = rs.getJointNumber("presentation_joint_number"),
                            joints = fetchSwitchStructureJoints(structureVersion),
                            alignments = fetchSwitchStructureAlignments(structureVersion),
                        ),
                )
            }
        logger.daoAccess(FETCH, SwitchStructure::class, version)
        return switchStructure
    }

    private fun fetchSwitchStructureJoints(structureVersion: RowVersion<SwitchStructure>): Set<SwitchStructureJoint> {
        val sql =
            """
            select
              number,
              postgis.st_x(location) as x,
              postgis.st_y(location) as y
            from common.switch_structure_version_joint
            where switch_structure_id = :switch_structure_id
              and switch_structure_version = :switch_structure_version
        """
                .trimIndent()

        val params =
            mapOf(
                "switch_structure_id" to structureVersion.id.intValue,
                "switch_structure_version" to structureVersion.version,
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                SwitchStructureJoint(number = rs.getJointNumber("number"), location = rs.getPoint("x", "y"))
            }
            .toSet()
    }

    private fun fetchSwitchStructureElements(
        structureVersion: RowVersion<SwitchStructure>
    ): Map<Int, List<SwitchStructureElement>> {
        val sql =
            """
            select
              alignment_index,
              element_index,
              type,
              postgis.st_x(start_point) as start_x,
              postgis.st_y(start_point) as start_y,
              postgis.st_x(end_point) as end_x,
              postgis.st_y(end_point) as end_y,
              curve_radius
            from common.switch_structure_version_element
            where switch_structure_id = :switch_structure_id
              and switch_structure_version = :switch_structure_version
        """
                .trimIndent()

        val params =
            mapOf(
                "switch_structure_id" to structureVersion.id.intValue,
                "switch_structure_version" to structureVersion.version,
            )

        val data =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val element =
                    when (rs.getEnum<SwitchStructureElementType>("type")) {
                        SwitchStructureElementType.LINE ->
                            SwitchStructureLine(
                                start = rs.getPoint("start_x", "start_y"),
                                end = rs.getPoint("end_x", "end_y"),
                            )
                        SwitchStructureElementType.CURVE ->
                            SwitchStructureCurve(
                                start = rs.getPoint("start_x", "start_y"),
                                end = rs.getPoint("end_x", "end_y"),
                                radius = rs.getDouble("curve_radius"),
                            )
                    }
                ElementFetchData(
                    alignmentIndex = rs.getInt("alignment_index"),
                    elementIndex = rs.getInt("element_index"),
                    element = element,
                )
            }

        return data
            .groupBy { d -> d.alignmentIndex }
            .mapValues { (_, elements) -> elements.sortedBy { e -> e.elementIndex }.map(ElementFetchData::element) }
    }

    private fun fetchSwitchStructureAlignments(
        structureVersion: RowVersion<SwitchStructure>
    ): List<SwitchStructureAlignment> {
        val elements = fetchSwitchStructureElements(structureVersion)
        val sql =
            """
            select alignment_index, joint_numbers
            from common.switch_structure_version_alignment
            where switch_structure_id = :switch_structure_id
              and switch_structure_version = :switch_structure_version
            order by alignment_index
        """
                .trimIndent()
        val params =
            mapOf(
                "switch_structure_id" to structureVersion.id.intValue,
                "switch_structure_version" to structureVersion.version,
            )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            val alignmentIndex = rs.getInt("alignment_index")
            SwitchStructureAlignment(
                jointNumbers = rs.getIntArray("joint_numbers").map { number -> JointNumber(number) },
                elements =
                    requireNotNull(elements[alignmentIndex]) {
                        "Missing elements for structure: structureVersion=$structureVersion alignment=$alignmentIndex"
                    },
            )
        }
    }

    @Transactional
    fun upsertSwitchStructure(switchStructure: SwitchStructureData): RowVersion<SwitchStructure> {
        val sql =
            """
            insert into common.switch_structure(
              type,
              presentation_joint_number
            )
            values
            (
              :type,
              :presentation_joint_number
            )
            on conflict (type) do update
              set presentation_joint_number = excluded.presentation_joint_number
            returning id, version
        """
                .trimIndent()

        val params =
            mapOf(
                "type" to switchStructure.type.typeName,
                "presentation_joint_number" to switchStructure.presentationJointNumber.intValue,
            )

        jdbcTemplate.setUser()
        val version: RowVersion<SwitchStructure> =
            requireNotNull(jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion("id", "version") }) {
                "Failed to generate ID/version for new Switch Structure"
            }
        insertJoints(version, switchStructure.joints)
        insertAlignments(version, switchStructure.alignments)

        logger.daoAccess(AccessType.UPSERT, SwitchStructure::class, version)
        return version
    }

    @Transactional
    fun upsertInfraModelAlias(alias: String, type: String) {
        val sql =
            """
            insert into common.inframodel_switch_type_name_alias (alias, type)
            values (:alias, :type)
            on conflict (alias) do update set type = excluded.type
        """
                .trimIndent()
        jdbcTemplate.setUser()
        val params = mapOf("alias" to alias, "type" to type)
        jdbcTemplate.update(sql, params)
    }

    @Transactional
    fun deleteInfraModelAlias(alias: String) {
        val sql = "delete from common.inframodel_switch_type_name_alias where alias = :alias"
        jdbcTemplate.setUser()
        val params = mapOf("alias" to alias)
        jdbcTemplate.update(sql, params)
    }

    fun getInfraModelAliases(): Map<String, String> {
        val sql = "select alias, type from common.inframodel_switch_type_name_alias"
        return jdbcTemplate.query(sql) { rs, _ -> rs.getString("alias") to rs.getString("type") }.associate { it }
    }

    private fun insertJoints(switchStructureVersion: RowVersion<SwitchStructure>, joints: Set<SwitchStructureJoint>) {
        val sql =
            """
            insert into common.switch_structure_version_joint
                (
                  switch_structure_id,
                  switch_structure_version,
                  number,
                  location
                )
            values
                (
                  :switch_structure_id,
                  :switch_structure_version,
                  :joint_number,
                  postgis.st_setsrid(
                    postgis.st_point(
                      :location_x, :location_y
                    ), 
                    :srid
                  )
                )
        """
                .trimIndent()

        val params =
            joints
                .map { joint ->
                    mapOf(
                        "switch_structure_id" to switchStructureVersion.id.intValue,
                        "switch_structure_version" to switchStructureVersion.version,
                        "joint_number" to joint.number.intValue,
                        "location_x" to joint.location.x,
                        "location_y" to joint.location.y,
                        "srid" to LAYOUT_SRID.code,
                    )
                }
                .toTypedArray()

        jdbcTemplate.batchUpdate(sql, params)
    }

    private fun insertAlignments(
        structureVersion: RowVersion<SwitchStructure>,
        alignments: List<SwitchStructureAlignment>,
    ) {
        val sql =
            """
            insert into common.switch_structure_version_alignment
                (
                  switch_structure_id,
                  switch_structure_version,
                  alignment_index,
                  joint_numbers
                )
            values
                (
                  :switch_structure_id,
                  :switch_structure_version,
                  :alignment_index,
                  string_to_array(:joint_numbers, ',')::int[]
                )
        """
                .trimIndent()

        val params =
            alignments
                .mapIndexed { index, alignment ->
                    mapOf(
                        "switch_structure_id" to structureVersion.id.intValue,
                        "switch_structure_version" to structureVersion.version,
                        "alignment_index" to index,
                        "joint_numbers" to alignment.jointNumbers.joinToString(",") { it.intValue.toString() },
                    )
                }
                .toTypedArray()

        jdbcTemplate.batchUpdate(sql, params)
        insertElements(structureVersion, alignments.map { alignment -> alignment.elements })
    }

    private fun insertElements(
        structureVersion: RowVersion<SwitchStructure>,
        alignmentElements: List<List<SwitchStructureElement>>,
    ) {
        val sql =
            """
            insert into common.switch_structure_version_element
                (
                  switch_structure_id,
                  switch_structure_version,
                  alignment_index,
                  element_index,
                  type,
                  start_point,
                  end_point,
                  curve_radius
                )
            values
                (
                  :switch_structure_id,
                  :switch_structure_version,
                  :alignment_index,
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
        """
                .trimIndent()

        val params =
            alignmentElements
                .flatMapIndexed { alignmentIndex, elements ->
                    elements.mapIndexed { elementIndex, element ->
                        mapOf(
                            "switch_structure_id" to structureVersion.id.intValue,
                            "switch_structure_version" to structureVersion.version,
                            "alignment_index" to alignmentIndex,
                            "element_index" to elementIndex,
                            "type" to element.type.name,
                            "start_point_x" to element.start.x,
                            "start_point_y" to element.start.y,
                            "end_point_x" to element.end.x,
                            "end_point_y" to element.end.y,
                            "curve_radius" to (element as? SwitchStructureCurve)?.radius,
                            "srid" to LAYOUT_SRID.code,
                        )
                    }
                }
                .toTypedArray()

        jdbcTemplate.batchUpdate(sql, params)
    }

    @Transactional
    fun delete(id: IntId<SwitchStructure>): IntId<SwitchStructure> {
        jdbcTemplate.setUser()

        val sql = "delete from common.switch_structure where id = :id returning id"
        val params = mapOf("id" to id.intValue)
        val deletedRowId = getOne(id, jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId<SwitchStructure>("id") })
        logger.daoAccess(AccessType.DELETE, SwitchStructure::class, deletedRowId)
        return deletedRowId
    }
}
