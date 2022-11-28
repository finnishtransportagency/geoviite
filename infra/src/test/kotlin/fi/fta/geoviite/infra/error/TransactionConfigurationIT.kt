package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.ITTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@Service
class RollbackTestService {
    @Transactional
    fun <T> run(operation: () -> T): T = operation()
}

@ActiveProfiles("dev", "test")
@SpringBootTest
class TransactionConfigurationIT @Autowired constructor(val rollbackTestService: RollbackTestService): ITTestBase() {

    @BeforeEach
    fun setup() {
        rollbackTestService.run {
            val sql = """
                drop table if exists common.test_table;
                create table common.test_table(
                    id int generated always as identity,
                    value varchar not null
                );
            """.trimIndent()
            jdbc.update(sql, mapOf<String, Any>())
        }
    }

    @Test
    fun transactionSeesOwnChanges() {
        val allValues = rollbackTestService.run {
            insert("test 1")
            insert("test 2")
            fetchAll()
        }
        allValues.contains(1 to "test 1")
        allValues.contains(2 to "test 2")
    }

    @Test
    fun transactionRollsBackOnUncheckedException() {
        assertThrows<IllegalStateException> {
            rollbackTestService.run {
                insert("test 1")
                throw IllegalStateException("Test")
            }
        }
        val allValues = rollbackTestService.run { fetchAll() }
        assertTrue(allValues.isEmpty())
    }

    @Test
    fun transactionRollsBackOnCheckedException() {
        assertThrows<java.lang.Exception> {
            rollbackTestService.run {
                insert("test 1")
                throw Exception("Test")
            }
        }
        val allValues = rollbackTestService.run { fetchAll() }
        assertTrue(allValues.isEmpty())
    }

    fun fetchAll(): List<Pair<Int, String>> {
        val sql = "select id, value from common.test_table;"
        return jdbc.query(sql, mapOf<String,Any>()) { rs, _ ->
            rs.getInt("id") to rs.getString("value")
        }
    }

    fun insert(value: String): Int {
        val sql = "insert into common.test_table(value) values (:value) returning id"
        return jdbc.queryForObject(sql, mapOf("value" to value)) { rs, _ -> rs.getInt("id") }
            ?: throw IllegalStateException("Test value insert failed")
    }
}
