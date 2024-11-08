package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.util.setUser
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import withUser

const val TEST_ROLE_CODE = "it_tst"

@ActiveProfiles("dev", "test")
@SpringBootTest
class MetaDataIT : DBTestBase() {

    private data class VersionData(val changeUser: String, val deleted: Boolean)

    @BeforeEach
    fun setUp() {
        cleanupRole(TEST_ROLE_CODE)
    }

    @Test
    fun `Versioning works`() {
        assertEquals(null, getUserGroup(TEST_ROLE_CODE))

        insertRole(TEST_ROLE_CODE, "IT_test_group")
        assertEquals("IT_test_group", getUserGroup(TEST_ROLE_CODE))

        updateRole(TEST_ROLE_CODE, "IT_test_group_2")
        assertEquals("IT_test_group_2", getUserGroup(TEST_ROLE_CODE))

        withUser(UserName.of("TST_USER_2")) { updateRole(TEST_ROLE_CODE, "IT_test_group_3") }
        assertEquals("IT_test_group_3", getUserGroup(TEST_ROLE_CODE))

        withUser(UserName.of("TST_USER_3")) { deleteRole(TEST_ROLE_CODE) }
        assertEquals(null, getUserGroup(TEST_ROLE_CODE))

        withUser(UserName.of("TST_USER_4")) { insertRole(TEST_ROLE_CODE, "IT_test_restored") }
        assertEquals("IT_test_restored", getUserGroup(TEST_ROLE_CODE))

        assertEquals(
            mapOf(
                1 to VersionData(TEST_USER, false),
                2 to VersionData(TEST_USER, false),
                3 to VersionData("TST_USER_2", false),
                4 to VersionData("TST_USER_3", true),
                5 to VersionData("TST_USER_4", false),
            ),
            getRoleVersionData(TEST_ROLE_CODE),
        )
    }

    private fun getUserGroup(code: String): String? {
        val sql = "select user_group from common.role where code = :code"
        val userGroups = jdbc.query(sql, mapOf("code" to code)) { rs, _ -> rs.getString("user_group") }
        assertTrue(userGroups.size <= 1)
        return userGroups.firstOrNull()
    }

    private fun getRoleVersionData(code: String): Map<Int, VersionData> {
        val sql = "select version, change_user, deleted from common.role_version where code = :code"
        return jdbc
            .query(sql, mapOf("code" to code)) { rs, _ ->
                rs.getInt("version") to
                    VersionData(changeUser = rs.getString("change_user"), deleted = rs.getBoolean("deleted"))
            }
            .associate { it }
    }

    private fun insertRole(code: String, userGroup: String) {
        transaction.execute {
            jdbc.setUser()
            val sql = "insert into common.role(code, user_group) values (:code, :user_group)"
            jdbc.update(sql, mapOf("code" to code, "user_group" to userGroup))
        }
    }

    private fun updateRole(code: String, userGroup: String) {
        transaction.execute {
            jdbc.setUser()
            val sql = "update common.role set user_group=:user_group where code=:code"
            jdbc.update(sql, mapOf("code" to code, "user_group" to userGroup))
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
