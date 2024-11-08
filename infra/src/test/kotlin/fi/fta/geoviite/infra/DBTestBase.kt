package fi.fta.geoviite.infra

import currentUser
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

const val TEST_USER = "TEST_USER"

abstract class DBTestBase(val testUser: String = TEST_USER) {

    @Autowired var testDBServiceIn: TestDBService? = null

    val testDBService by lazy { testDBServiceIn ?: error("Test data service not initialized") }
    val jdbc: NamedParameterJdbcTemplate
        get() = testDBService.jdbc

    val transaction: TransactionTemplate
        get() = testDBService.transaction

    val mainDraftContext by lazy { testDBService.testContext(LayoutBranch.main, PublicationState.DRAFT) }
    val mainOfficialContext by lazy { testDBService.testContext(LayoutBranch.main, PublicationState.OFFICIAL) }

    val trackNumberDescription by lazy { "Test track number ${this::class.simpleName}" }

    @BeforeEach
    fun initUser() {
        currentUser.set(UserName.of(testUser))
    }

    fun <T> transactional(op: () -> T): T = testDBService.transactional(op)
}
