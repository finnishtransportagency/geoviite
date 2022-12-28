package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

const val TEST_USER = "TEST_USER"

abstract class ITTestBase {
    @Autowired
    var jdbcTemplate: NamedParameterJdbcTemplate? = null
    @Autowired
    var transactionTemplate: TransactionTemplate? = null

    private var trackNumberDaoParam: LayoutTrackNumberDao? = null

    @Autowired
    fun setTrackNumberDao(tnDao: LayoutTrackNumberDao) {
        trackNumberDaoParam = tnDao
    }

    val jdbc by lazy { jdbcTemplate ?: throw IllegalStateException("JDBC not initialized") }
    val transaction by lazy { transactionTemplate ?: throw IllegalStateException("JDBC not initialized") }
    private val trackNumberDao by lazy { trackNumberDaoParam ?: throw IllegalStateException("JDBC not initialized") }

    val trackNumberDescription by lazy { "Test track number ${this::class.simpleName}" }

    @BeforeEach
    fun initUserMdc() {
        MDC.put(USER_HEADER, TEST_USER)
    }

    fun <T> transactional(op: () -> T): T = transaction.execute {
        jdbc.setUser()
        op()
    } ?: throw IllegalStateException("Transaction returned nothing")

    fun getUnusedTrackNumberId() =
        getOrCreateTrackNumber(getUnusedTrackNumber()).id as IntId

    fun getOrCreateTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val version = trackNumberDao.fetchVersions(DRAFT, false, trackNumber).firstOrNull()
            ?: insertNewTrackNumber(trackNumber, false)
        return version.let(trackNumberDao::fetch)
    }

    fun insertDraftTrackNumber(): IntId<TrackLayoutTrackNumber> =
        insertNewTrackNumber(getUnusedTrackNumber(), true).id

    fun insertOfficialTrackNumber(): IntId<TrackLayoutTrackNumber> =
        insertNewTrackNumber(getUnusedTrackNumber(), false).id

    fun getDbTime(): Instant = requireNotNull(
        jdbc.queryForObject("select now() as now", mapOf<String, Any>()) { rs, _ -> rs.getInstant("now") }
    )

    fun getUnusedTrackNumber(): TrackNumber {
        val sql = "select max(id) max_id from layout.track_number_version"
        val maxId = jdbc.queryForObject(sql, mapOf<String,Any>()) { rs, _ -> rs.getInt("max_id") }!!
        val baseName = this::class.simpleName!!.let { className ->
            if (className.length > 24) className.substring(0, 24)
            else className
        }
        return TrackNumber("$baseName ${maxId+1}")
    }

    fun insertNewTrackNumber(
        trackNumber: TrackNumber,
        draft: Boolean,
        state: LayoutState = LayoutState.IN_USE,
    ): RowVersion<TrackLayoutTrackNumber> =
        transactional {
            jdbc.setUser()
            trackNumberDao.insert(trackNumber(
                number = trackNumber,
                description = trackNumberDescription,
                draft = draft,
                state = state,
            ))
        }

}
