package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

const val TEST_ROLE_CODE = "it_tst"

@ActiveProfiles("dev", "test")
@SpringBootTest
class MetaDataIT: ITTestBase() {

    private data class VersionData(
        val name: String,
        val changeUser: String,
        val deleted: Boolean,
    )

    @BeforeEach
    fun setUp() {
        cleanupRole(TEST_ROLE_CODE)
    }

    @Test
    fun versioningWorks() {
        assertEquals(null, getName(TEST_ROLE_CODE))

        insertRole(TEST_ROLE_CODE, "IT_test_role")
        assertEquals("IT_test_role", getName(TEST_ROLE_CODE))

        updateRole(TEST_ROLE_CODE, "IT_test_name_2")
        assertEquals("IT_test_name_2", getName(TEST_ROLE_CODE))

        MDC.put(USER_HEADER, "TST_USER_2")
        updateRole(TEST_ROLE_CODE, "IT_test_name_3")
        assertEquals("IT_test_name_3", getName(TEST_ROLE_CODE))

        MDC.put(USER_HEADER, "TST_USER_3")
        deleteRole(TEST_ROLE_CODE)
        assertEquals(null, getName(TEST_ROLE_CODE))

        MDC.put(USER_HEADER, "TST_USER_4")
        insertRole(TEST_ROLE_CODE, "IT_test_restored")
        assertEquals("IT_test_restored", getName(TEST_ROLE_CODE))

        assertEquals(
            mapOf(
                1 to VersionData("IT_test_role", TEST_USER, false),
                2 to VersionData("IT_test_name_2", TEST_USER, false),
                3 to VersionData("IT_test_name_3", "TST_USER_2", false),
                4 to VersionData("IT_test_name_3", "TST_USER_3", true),
                5 to VersionData("IT_test_restored", "TST_USER_4", false),
            ),
            getRoleVersionData(TEST_ROLE_CODE)
        )
    }

    private fun getName(code: String): String? {
        val sql = "select name from common.role where code = :code"
        val names = jdbc.query(sql, mapOf("code" to code)) { rs, _ -> rs.getString("name") }
        assertTrue(names.size <= 1)
        return names.firstOrNull()
    }

    private fun getRoleVersionData(code: String): Map<Int, VersionData> {
        val sql = "select version, name, change_user, deleted from common.role_version where code = :code"
        return jdbc.query(sql, mapOf("code" to code)) { rs, _ ->
            rs.getInt("version") to VersionData(
                name = rs.getString("name"),
                changeUser = rs.getString("change_user"),
                deleted = rs.getBoolean("deleted"),
            )
        }.associate { it }
    }

    private fun insertRole(code: String, name: String) {
        transaction.execute {
            jdbc.setUser()
            val sql = "insert into common.role(code, name, user_group) values (:code, :name, 'tst_group')"
            jdbc.update(sql, mapOf("code" to code, "name" to name))
        }
    }

    private fun updateRole(code: String, name: String) {
        transaction.execute {
            jdbc.setUser()
            val sql = "update common.role set name=:name where code=:code"
            jdbc.update(sql, mapOf("code" to code, "name" to name))
        }
    }

    private fun deleteRole(code: String) {
        transaction.execute {
            jdbc.setUser()
            val sql = "delete from common.role where code=:code"
            jdbc.update(sql, mapOf("code" to code))
        }
    }

    private fun cleanupRole(code: String) {
        transaction.execute {
            jdbc.setUser()
            deleteRole(code)
            val sql = "delete from common.role_version where code=:code"
            jdbc.update(sql, mapOf("code" to code))
        }
    }
}
