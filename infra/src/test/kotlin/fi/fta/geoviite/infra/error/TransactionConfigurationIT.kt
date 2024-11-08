package fi.fta.geoviite.infra.error

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.aspects.GeoviiteService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class TransactionTestService {
    @Transactional fun <T> run(operation: () -> T): T = operation()

    @Transactional(readOnly = true) fun <T> runReadOnly(operation: () -> T): T = operation()
}

@ActiveProfiles("dev", "test")
@SpringBootTest
class TransactionConfigurationIT @Autowired constructor(val transactionTestService: TransactionTestService) :
    DBTestBase() {

    @BeforeEach
    fun setup() {
        transactionTestService.run {
            val sql =
                """
                drop table if exists common.test_table;
                create table common.test_table(
                    id int generated always as identity,
                    value varchar not null
                );
            """
                    .trimIndent()
            jdbc.update(sql, mapOf<String, Any>())
        }
    }

    @Test
    fun transactionSeesOwnChanges() {
        val allValues =
            transactionTestService.run {
                insert("test 1")
                insert("test 2")
                fetchAll()
            }
        assertEquals(allValues, listOf(1 to "test 1", 2 to "test 2"))
    }

    @Test
    fun transactionRollsBackOnUncheckedException() {
        assertThrows<IllegalStateException> {
            transactionTestService.run {
                insert("test 1")
                throw IllegalStateException("Test")
            }
        }
        val allValues = transactionTestService.run { fetchAll() }
        assertTrue(allValues.isEmpty())
    }

    @Test
    fun transactionRollsBackOnCheckedException() {
        assertThrows<java.lang.Exception> {
            transactionTestService.run {
                insert("test 1")
                throw Exception("Test")
            }
        }
        val allValues = transactionTestService.run { fetchAll() }
        assertTrue(allValues.isEmpty())
    }

    @Test
    fun readOnlyTransactionDoesRead() {
        transactionTestService.run { insert("test 1") }
        val allValues = transactionTestService.runReadOnly { fetchAll() }
        assertEquals(allValues, listOf(1 to "test 1"))
    }

    @Test
    fun readOnlyTransactionDoesntWrite() {
        assertThrows<DataAccessException> { transactionTestService.runReadOnly { insert("test 1") } }
        val allValues = transactionTestService.runReadOnly { fetchAll() }
        assertTrue(allValues.isEmpty())
    }

    fun fetchAll(): List<Pair<Int, String>> {
        val sql = "select id, value from common.test_table order by id;"
        return jdbc.query(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("id") to rs.getString("value") }
    }

    fun insert(value: String): Int {
        val sql = "insert into common.test_table(value) values (:value) returning id"
        return jdbc.queryForObject(sql, mapOf("value" to value)) { rs, _ -> rs.getInt("id") }
            ?: throw IllegalStateException("Test value insert failed")
    }
}
