package fi.fta.geoviite.infra.migration

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.w3c.dom.Element
import org.w3c.dom.NodeList

@Suppress("unused", "ClassName")
class V152__backfill_profile_group_number : BaseJavaMigration() {

    override fun migrate(context: Context?) {
        val connection = requireNotNull(context?.connection) { "Can't run migrations without DB connection" }
        val jdbcTemplate = NamedParameterJdbcTemplate(SingleConnectionDataSource(connection, true))

        val planFiles =
            jdbcTemplate.query(
                """
                select pf.plan_id, pf.content::text as content
                from geometry.plan_file pf
                where pf.plan_id in (
                    select distinct plan_id from geometry.alignment where profile_name is not null
                )
                """
                    .trimIndent(),
                emptyMap<String, Any>(),
            ) { rs, _ ->
                rs.getInt("plan_id") to rs.getString("content")
            }

        val dbFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val xpathFactory = XPathFactory.newInstance()

        for ((planId, content) in planFiles) {
            val doc = dbFactory.newDocumentBuilder().parse(content.byteInputStream())
            val xpath = xpathFactory.newXPath()
            val nodes = xpath.evaluate("//*[local-name()='ProfAlign']", doc, XPathConstants.NODESET) as NodeList

            val updates = mutableListOf<Map<String, Any?>>()
            @Suppress("LoopWithTooManyJumpStatements")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                val profileName = node.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
                val groupNumber = node.getAttribute("ProfAlignGroupNumber").takeIf { it.isNotEmpty() } ?: continue
                updates.add(mapOf("plan_id" to planId, "profile_name" to profileName, "group_number" to groupNumber))
            }

            if (updates.isNotEmpty()) {
                jdbcTemplate.batchUpdate(
                    """
                    update geometry.alignment
                    set profile_group_number = :group_number
                    where plan_id = :plan_id
                      and profile_name = :profile_name
                    """
                        .trimIndent(),
                    updates.toTypedArray(),
                )
            }
        }
    }
}
