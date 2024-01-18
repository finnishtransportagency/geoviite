package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

const val TEST_USER = "TEST_USER"

abstract class DBTestBase(val testUser: String = TEST_USER) {
    @Autowired
    var jdbcTemplate: NamedParameterJdbcTemplate? = null
    @Autowired
    var transactionTemplate: TransactionTemplate? = null

    private var trackNumberDaoParam: LayoutTrackNumberDao? = null
    private var trackDaoParam: LocationTrackDao? = null
    private var alignmentDaoParam: LayoutAlignmentDao? = null
    private var referenceLineDaoParam: ReferenceLineDao? = null

    @Autowired
    fun setTrackNumberDao(tnDao: LayoutTrackNumberDao) {
        trackNumberDaoParam = tnDao
    }

    @Autowired
    fun setLocationTrackDao(trackDao: LocationTrackDao) {
        trackDaoParam = trackDao
    }

    @Autowired
    fun setTrackNumberDao(alignmentDao: LayoutAlignmentDao) {
        alignmentDaoParam = alignmentDao
    }

    @Autowired
    fun setReferenceLineDao(referenceLineDao: ReferenceLineDao) {
        referenceLineDaoParam = referenceLineDao
    }

    val jdbc by lazy { jdbcTemplate ?: throw IllegalStateException("JDBC not initialized") }
    val transaction by lazy { transactionTemplate ?: throw IllegalStateException("JDBC not initialized") }
    private val trackNumberDao by lazy { trackNumberDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val trackDao by lazy { trackDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val alignmentDao by lazy { alignmentDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val referenceLineDao by lazy {
        referenceLineDaoParam ?: throw java.lang.IllegalStateException("JDBC not initialized")
    }

    val trackNumberDescription by lazy { "Test track number ${this::class.simpleName}" }

    @BeforeEach
    fun initUserMdc() {
        MDC.put(USER_HEADER, testUser)
    }

    fun <T> transactional(op: () -> T): T = transaction.execute {
        initUserMdc()
        jdbc.setUser()
        op()
    } ?: throw IllegalStateException("Transaction returned nothing")

    fun getUnusedTrackNumberId() =
        getOrCreateTrackNumber(getUnusedTrackNumber()).id as IntId

    fun getOrCreateTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val version = trackNumberDao.fetchVersions(DRAFT, false, trackNumber).firstOrNull()
            ?: insertNewTrackNumber(trackNumber, false).rowVersion
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
    ): DaoResponse<TrackLayoutTrackNumber> =
        transactional {
            jdbc.setUser()
            trackNumberDao.insert(
                trackNumber(
                    number = trackNumber,
                    description = trackNumberDescription,
                    draft = draft,
                    state = state,
                )
            )
        }

    fun insertReferenceLine(referenceLine: ReferenceLine, alignment: LayoutAlignment): DaoResponse<ReferenceLine> {
        return alignmentDao.insert(alignment).let { alignmentVersion ->
            referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentVersion))
        }
    }

    fun insertLocationTrack(trackAndAlignment: Pair<LocationTrack, LayoutAlignment>) =
        insertLocationTrack(trackAndAlignment.first, trackAndAlignment.second)

    fun insertLocationTrack(track: LocationTrack, alignment: LayoutAlignment): DaoResponse<LocationTrack> =
        alignmentDao.insert(alignment).let { alignmentVersion ->
            trackDao.insert(track.copy(alignmentVersion = alignmentVersion))
        }

    fun deleteFromTables(schema: String, vararg tables: String) {
        // We don't actually need transactionality, but we do need everything to be run in one session
        transactional {
            // Temporarily disable all triggers
            jdbc.execute("set session_replication_role = replica") { it.execute() }
            try {
                tables.forEach { table ->
                    jdbc.update(
                        "DELETE FROM ${schema}.${table};", mapOf("table_name" to table, "schema_name" to schema)
                    )
                }
            } finally {
                jdbc.execute("set session_replication_role = DEFAULT") { it.execute() }
            }
        }
    }
}
