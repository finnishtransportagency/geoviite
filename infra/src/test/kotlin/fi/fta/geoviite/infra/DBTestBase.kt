package fi.fta.geoviite.infra

import currentUser
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.switchNameLength
import fi.fta.geoviite.infra.common.trackNumberLength
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.BeforeEach
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
    private var switchDaoParam: LayoutSwitchDao? = null

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

    @Autowired
    fun setSwitchDao(switchDao: LayoutSwitchDao) {
        switchDaoParam = switchDao
    }

    val jdbc by lazy { jdbcTemplate ?: throw IllegalStateException("JDBC not initialized") }
    val transaction by lazy { transactionTemplate ?: throw IllegalStateException("JDBC not initialized") }
    private val trackNumberDao by lazy { trackNumberDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val trackDao by lazy { trackDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val alignmentDao by lazy { alignmentDaoParam ?: throw IllegalStateException("JDBC not initialized") }
    private val referenceLineDao by lazy {
        referenceLineDaoParam ?: throw java.lang.IllegalStateException("JDBC not initialized")
    }
    private val switchDao by lazy { switchDaoParam ?: throw IllegalStateException("JDBC not initialized") }

    val trackNumberDescription by lazy { "Test track number ${this::class.simpleName}" }

    @BeforeEach
    fun initUser() {
        currentUser.set(UserName.of(testUser))
    }

    fun <T> transactional(op: () -> T): T = transaction.execute {
        initUser()
        jdbc.setUser()
        op()
    } ?: error("Transaction returned nothing")

    fun getUnusedTrackNumberId(): IntId<TrackLayoutTrackNumber> =
        getOrCreateTrackNumber(getUnusedTrackNumber()).id as IntId

    fun getOrCreateTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val version = trackNumberDao.fetchVersions(MainLayoutContext.draft, false, trackNumber).firstOrNull()
            ?: insertNewTrackNumber(trackNumber, false).rowVersion
        return version.let(trackNumberDao::fetch)
    }

    fun insertDraftTrackNumber(number: TrackNumber = getUnusedTrackNumber()): IntId<TrackLayoutTrackNumber> =
        insertNewTrackNumber(number, true).id

    fun insertOfficialTrackNumber(number: TrackNumber = getUnusedTrackNumber()): IntId<TrackLayoutTrackNumber> =
        insertNewTrackNumber(number, false).id

    fun getDbTime(): Instant = requireNotNull(
        jdbc.queryForObject("select now() as now", mapOf<String, Any>()) { rs, _ -> rs.getInstant("now") }
    )

    fun getUnusedTrackNumber(): TrackNumber {
        return TrackNumber(getUniqueName(DbTable.LAYOUT_TRACK_NUMBER, trackNumberLength.last))
    }

    fun getUnusedSwitchName(): SwitchName {
        return SwitchName(getUniqueName(DbTable.LAYOUT_SWITCH, switchNameLength.last))
    }

    fun getUnusedProjectName(): ProjectName = ProjectName(
        getUniqueName(DbTable.GEOMETRY_PLAN_PROJECT, 100)
    )

    fun getUnusedAuthorCompanyName(): MetaDataName = MetaDataName(
        getUniqueName(DbTable.GEOMETRY_PLAN_AUTHOR, 100)
    )

    fun getUnusedBulkTransferId(): IntId<BulkTransfer> {
        return getUniqueId(DbTable.PUBLICATION_SPLIT, "bulk_transfer_id")
    }

    private fun getUniqueName(table: DbTable, maxLength: Int): String {
        val sql = "select max(id) max_id from ${table.versionTable}"
        val maxId = jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("max_id") }!!
        val baseNameLength = maxLength - 8 // allow 7 unique digits + space
        val baseName = this::class.simpleName!!.let { className ->
            if (className.length > baseNameLength) className.substring(0, baseNameLength)
            else className
        }
        return "$baseName ${maxId + 1}"
    }

    private fun<T> getUniqueId(table: DbTable, column: String): IntId<T> {
        val sql = "select max($column) max_id from ${table.fullName}"
        val maxId = jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("max_id") }

        return when {
            maxId == null -> IntId(0)
            else -> IntId(maxId + 1)
        }
    }

    fun insertNewTrackNumber(
        trackNumber: TrackNumber,
        draft: Boolean,
        state: LayoutState = LayoutState.IN_USE,
    ): DaoResponse<TrackLayoutTrackNumber> = transactional {
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

    fun insertUniqueSwitch(): DaoResponse<TrackLayoutSwitch> =
        insertSwitch(switch(name = getUnusedSwitchName().toString(), draft = false))

    fun insertUniqueDraftSwitch(): DaoResponse<TrackLayoutSwitch> =
        insertSwitch(asMainDraft(switch(name = getUnusedSwitchName().toString(), draft = true)))

    fun insertSwitch(switch: TrackLayoutSwitch): DaoResponse<TrackLayoutSwitch> = transactional {
        jdbc.setUser()
        switchDao.insert(switch)
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
