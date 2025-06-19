package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import withUser
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val TEST_ROLE_CODE = "it_tst"

@ActiveProfiles("dev", "test")
@SpringBootTest
class MetaDataIT : DBTestBase() {

    private data class VersionData(
        val changeUser: String,
        val deleted: Boolean,
        val changeTime: Instant,
        val expiryTime: Instant?,
    )

    @BeforeEach
    fun setUp() {
        cleanupRole(TEST_ROLE_CODE)
    }

    @Test
    fun `Versioning works`() {
        assertEquals(null, getUserGroup(TEST_ROLE_CODE))

        val time0 = testDBService.getDbTime()
        insertRole(TEST_ROLE_CODE, "IT_test_group")
        assertEquals("IT_test_group", getUserGroup(TEST_ROLE_CODE))

        val time1 = testDBService.getDbTime()
        updateRole(TEST_ROLE_CODE, "IT_test_group_2")
        assertEquals("IT_test_group_2", getUserGroup(TEST_ROLE_CODE))

        val time2 = testDBService.getDbTime()
        withUser(UserName.of("TST_USER_2")) { updateRole(TEST_ROLE_CODE, "IT_test_group_3") }
        assertEquals("IT_test_group_3", getUserGroup(TEST_ROLE_CODE))

        val time3 = testDBService.getDbTime()
        withUser(UserName.of("TST_USER_3")) { deleteRole(TEST_ROLE_CODE) }
        assertEquals(null, getUserGroup(TEST_ROLE_CODE))

        val time4 = testDBService.getDbTime()
        withUser(UserName.of("TST_USER_4")) { insertRole(TEST_ROLE_CODE, "IT_test_restored") }
        assertEquals("IT_test_restored", getUserGroup(TEST_ROLE_CODE))
        val time5 = testDBService.getDbTime()

        val versionData = getRoleVersionData(TEST_ROLE_CODE)
        assertEquals(5, versionData.size)
        assertVersionDataMatches(versionData[1], TEST_USER, false, Range(time0, time1), versionData[2]?.changeTime)
        assertVersionDataMatches(versionData[2], TEST_USER, false, Range(time1, time2), versionData[3]?.changeTime)
        assertVersionDataMatches(versionData[3], "TST_USER_2", false, Range(time2, time3), versionData[4]?.changeTime)
        assertVersionDataMatches(versionData[4], "TST_USER_3", true, Range(time3, time4), versionData[5]?.changeTime)
        assertVersionDataMatches(versionData[5], "TST_USER_4", false, Range(time4, time5), null)
    }

    private fun assertVersionDataMatches(
        data: VersionData?,
        changeUser: String,
        deleted: Boolean,
        changeTimeInterval: Range<Instant>,
        expiryTime: Instant?,
    ) {
        assertNotNull(data)
        assertEquals(changeUser, data.changeUser)
        assertEquals(deleted, data.deleted)
        assertTrue { changeTimeInterval.contains(data.changeTime) }
        assertEquals(expiryTime, data.expiryTime)
    }

    private fun getUserGroup(code: String): String? {
        val sql = "select user_group from common.role where code = :code"
        val userGroups = jdbc.query(sql, mapOf("code" to code)) { rs, _ -> rs.getString("user_group") }
        assertTrue(userGroups.size <= 1)
        return userGroups.firstOrNull()
    }

    private fun getRoleVersionData(code: String): Map<Int, VersionData> {
        val sql =
            "select version, change_user, deleted, change_time, expiry_time from common.role_version where code = :code"
        return jdbc
            .query(sql, mapOf("code" to code)) { rs, _ ->
                rs.getInt("version") to
                    VersionData(
                        changeUser = rs.getString("change_user"),
                        deleted = rs.getBoolean("deleted"),
                        changeTime = rs.getInstant("change_time"),
                        expiryTime = rs.getInstantOrNull("expiry_time"),
                    )
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
