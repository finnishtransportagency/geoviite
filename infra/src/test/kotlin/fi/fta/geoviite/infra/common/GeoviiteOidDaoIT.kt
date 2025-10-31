package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.util.getOid
import fi.fta.geoviite.infra.util.queryOne
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeoviiteOidDaoIT @Autowired constructor(val geoviiteOidDao: GeoviiteOidDao) : DBTestBase() {

    @Test
    fun `create a new oid sequence and generate oids from it`() {
        transactionalForcingRollback {
            jdbc.execute("""insert into common.oid_type values('abc')""") { it.execute() }
            jdbc.execute(
                """
                    insert into common.oid_sequence(type, service_oid, group_number, number, state)
                    values ('abc', '1.2.246.578.13', 123, 456, 'ACTIVE')
                """
                    .trimIndent()
            ) {
                it.execute()
            }
            val a =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            val b =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            val c =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            assertEquals("1.2.246.578.13.123.456.1", a.toString())
            assertEquals("1.2.246.578.13.123.456.2", b.toString())
            assertEquals("1.2.246.578.13.123.456.3", c.toString())
        }
    }

    @Test
    fun `passivate an oid sequence and generate oids from a replacement`() {
        transactionalForcingRollback {
            jdbc.execute("""insert into common.oid_type values('abc')""") { it.execute() }
            jdbc.execute(
                """
                    insert into common.oid_sequence(type, service_oid, group_number, number, state)
                    values ('abc', '1.2.246.578.13', 123, 456, 'ACTIVE')
                """
                    .trimIndent()
            ) {
                it.execute()
            }
            jdbc.execute("update common.oid_sequence set state = 'DEPRECATED' where type = 'abc'") { it.execute() }
            jdbc.execute(
                """
                    insert into common.oid_sequence(type, service_oid, group_number, number, state)
                    values ('abc', '1.2.246.578.13', 789, 456, 'ACTIVE')
                """
                    .trimIndent()
            ) {
                it.execute()
            }

            val a =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            val b =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            val c =
                jdbc.queryOne("select common.generate_oid(:type) as oid", mapOf("type" to "abc")) { rs, _ ->
                    rs.getOid<LayoutKmPost>("oid")
                }
            assertEquals("1.2.246.578.13.789.456.1", a.toString())
            assertEquals("1.2.246.578.13.789.456.2", b.toString())
            assertEquals("1.2.246.578.13.789.456.3", c.toString())
        }
    }
}
