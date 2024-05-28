package fi.fta.geoviite.infra

import currentUser
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackNumber
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
import fi.fta.geoviite.infra.util.setUser
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

const val TEST_USER = "TEST_USER"

abstract class DBTestBase(val testUser: String = TEST_USER) {

    @Autowired
    var testDBServiceIn: TestDBService? = null

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

    val testDBService by lazy { testDBServiceIn ?: error { "Test data service not initialized" } }
    val jdbc: NamedParameterJdbcTemplate get() = testDBService.jdbc
    val transaction: TransactionTemplate get() = testDBService.transaction
    val mainDraftContext by lazy { testDBService.testContext(LayoutBranch.main, PublicationState.DRAFT) }
    val mainOfficialContext by lazy { testDBService.testContext(LayoutBranch.main, PublicationState.OFFICIAL) }

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

    fun insertNewTrackNumber(
        trackNumber: TrackNumber,
        draft: Boolean,
        state: LayoutState = LayoutState.IN_USE,
    ): DaoResponse<TrackLayoutTrackNumber> = transactional {
        jdbc.setUser()
        testDBService.trackNumberDao.insert(
            trackNumber(
                number = trackNumber,
                description = trackNumberDescription,
                draft = draft,
                state = state,
            )
        )
    }

    fun getUnusedBulkTransferId(): IntId<BulkTransfer> {
        return getUniqueId(DbTable.PUBLICATION_SPLIT, "bulk_transfer_id")
    }

    private fun<T> getUniqueId(table: DbTable, column: String): IntId<T> {
        val sql = "select max($column) max_id from ${table.fullName}"
        val maxId = jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("max_id") }

        return when {
            maxId == null -> IntId(0)
            else -> IntId(maxId + 1)
        }
    }

    fun insertUniqueSwitch(): DaoResponse<TrackLayoutSwitch> =
        insertSwitch(switch(name = testDBService.getUnusedSwitchName().toString(), draft = false))

    fun insertUniqueDraftSwitch(): DaoResponse<TrackLayoutSwitch> =
        insertSwitch(asMainDraft(switch(name = testDBService.getUnusedSwitchName().toString(), draft = true)))

    fun insertSwitch(switch: TrackLayoutSwitch): DaoResponse<TrackLayoutSwitch> = transactional {
        jdbc.setUser()
        testDBService.switchDao.insert(switch)
    }

    fun insertReferenceLine(referenceLine: ReferenceLine, alignment: LayoutAlignment): DaoResponse<ReferenceLine> {
        return testDBService.alignmentDao.insert(alignment).let { alignmentVersion ->
            testDBService.referenceLineDao.insert(referenceLine.copy(alignmentVersion = alignmentVersion))
        }
    }

    fun insertLocationTrack(trackAndAlignment: Pair<LocationTrack, LayoutAlignment>) =
        insertLocationTrack(trackAndAlignment.first, trackAndAlignment.second)

    fun insertLocationTrack(track: LocationTrack, alignment: LayoutAlignment): DaoResponse<LocationTrack> =
        testDBService.alignmentDao.insert(alignment).let { alignmentVersion ->
            testDBService.locationTrackDao.insert(track.copy(alignmentVersion = alignmentVersion))
        }
}
