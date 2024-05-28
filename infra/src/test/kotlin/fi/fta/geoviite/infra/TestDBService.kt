package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.switchNameLength
import fi.fta.geoviite.infra.common.trackNumberLength
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.CompanyName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.project
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LinearGeometryAsset
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.reflect.KClass

interface TestDB {
    val jdbc: NamedParameterJdbcTemplate
    val locationTrackDao: LocationTrackDao
    val switchDao: LayoutSwitchDao
    val trackNumberDao: LayoutTrackNumberDao
    val referenceLineDao: ReferenceLineDao
    val kmPostDao: LayoutKmPostDao
    val alignmentDao: LayoutAlignmentDao
    val geometryDao: GeometryDao

    fun getDbTime(): Instant = requireNotNull(
        jdbc.queryForObject("select now() as now", mapOf<String, Any>()) { rs, _ -> rs.getInstant("now") }
    )

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> getDao(clazz: KClass<T>): LayoutAssetDao<T> = when (clazz) {
        LocationTrack::class -> locationTrackDao
        TrackLayoutSwitch::class -> switchDao
        TrackLayoutTrackNumber::class -> trackNumberDao
        ReferenceLine::class -> referenceLineDao
        TrackLayoutKmPost::class -> kmPostDao
        else -> error { "Unsupported asset type: ${clazz.simpleName}" }
    } as LayoutAssetDao<T>

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> getDao(asset: LayoutAsset<T>): LayoutAssetDao<T> = when (asset) {
        is LocationTrack -> locationTrackDao
        is TrackLayoutSwitch -> switchDao
        is TrackLayoutTrackNumber -> trackNumberDao
        is ReferenceLine -> referenceLineDao
        is TrackLayoutKmPost -> kmPostDao
    } as LayoutAssetDao<T>
}

@Service
class TestDBService(
    private val jdbcTemplate: NamedParameterJdbcTemplate?,
    private val transactionTemplate: TransactionTemplate?,
    override val locationTrackDao: LocationTrackDao,
    override val switchDao: LayoutSwitchDao,
    override val trackNumberDao: LayoutTrackNumberDao,
    override val referenceLineDao: ReferenceLineDao,
    override val kmPostDao: LayoutKmPostDao,
    override val alignmentDao: LayoutAlignmentDao,
    override val geometryDao: GeometryDao,
) : TestDB {

    override val jdbc by lazy { jdbcTemplate ?: error { "JDBC not initialized" } }

    val transaction by lazy { transactionTemplate ?: error { "JDBC not initialized" } }

    fun clearAllTables() {
        clearRatkoTables()
        clearPublicationTables()
        clearSplitTables()
        clearLayoutTables()
        clearProjektivelhoTables()
        clearGeometryTables()
    }

    // TODO: GVT-2612 Do we actually ever need to clear version tables? They reference nothing after all.
    fun clearLayoutTables() {
        deleteFromTables(
            schema = "layout",
            tables = arrayOf(
                "design",
                "design_version",
                "alignment",
                "alignment_version",
                "km_post",
                "km_post_version",
                "location_track",
                "location_track_version",
                "reference_line",
                "reference_line_version",
                "switch",
                "switch_version",
                "switch_joint",
                "switch_joint_version",
                "track_number",
                "track_number_version",
                "segment_version",
                "segment_geometry",
            ),
        )
    }

    fun clearGeometryTables() {
        deleteFromTables(
            schema = "geometry",
            tables = arrayOf(
                "alignment",
                "cant_point",
                "element",
                "plan",
                "plan_application",
                "plan_application_version",
                "plan_file",
                "plan_project",
                "plan_project_version",
                "plan_author",
                "plan_author_version",
                "plan_version",
                "switch",
                "switch_joint",
                "vertical_intersection",
            )
        )
    }

    fun clearPublicationTables() {
        deleteFromTables(
            schema = "publication",
            tables = arrayOf(
                "km_post",
                "location_track",
                "location_track_km",
                "publication",
                "reference_line",
                "switch",
                "switch_joint",
                "switch_location_tracks",
                "track_number",
                "track_number_km",
            ),
        )
    }

    fun clearSplitTables() {
        deleteFromTables(
            schema = "publication",
            tables = arrayOf(
                "split",
                "split_version",
                "split_relinked_switch",
                "split_relinked_switch_version",
                "split_target_location_track",
                "split_Target_location_track_version",
            ),
        )
    }

    fun clearProjektivelhoTables() {
        deleteFromTables(
            schema = "projektivelho",
            tables = arrayOf(
                "document_content",
                "document_rejection",
                "document",
                "document_type",
                "material_category",
                "material_group",
                "material_state",
                "technics_field",
                "project_state",
                "assignment",
                "project",
                "project_group",
                "search",
            ),
        )
    }

    fun clearRatkoTables() {
        deleteFromTables(
            schema = "integrations",
            tables = arrayOf(
                "ratko_push_content",
            ),
        )
    }

    fun getUnusedTrackNumber(): TrackNumber {
        return TrackNumber(getUniqueName(DbTable.LAYOUT_TRACK_NUMBER, trackNumberLength.last))
    }

    fun getUnusedSwitchName(): SwitchName {
        return SwitchName(getUniqueName(DbTable.LAYOUT_SWITCH, switchNameLength.last))
    }

    fun getUnusedProjectName(): ProjectName = ProjectName(
        getUniqueName(DbTable.GEOMETRY_PLAN_PROJECT, 100)
    )

    fun getUnusedAuthorCompanyName(): CompanyName = CompanyName(
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
            if (className.length > baseNameLength) className.substring(0, baseNameLength) else className
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

    final inline fun <reified T : LayoutAsset<T>> fetch(rowVersion: RowVersion<T>): T =
        getDao(T::class).fetch(rowVersion)

    fun deleteFromTables(schema: String, vararg tables: String) {
        // We don't actually need transactionality, but we do need everything to be run in one session
        transactional {
            // Temporarily disable all triggers
            jdbc.execute("set session_replication_role = replica") { it.execute() }
            try {
                tables.forEach { table ->
                    jdbc.update("delete from ${schema}.${table};", emptyMap<String, Any>())
                }
            } finally {
                jdbc.execute("set session_replication_role = DEFAULT") { it.execute() }
            }
        }
    }

    fun <T> transactional(op: () -> T): T = transaction.execute {
        jdbc.setUser()
        op()
    } ?: error("Transaction returned nothing")

    fun testContext(branch: LayoutBranch = LayoutBranch.main, state: PublicationState = OFFICIAL): TestLayoutContext =
        TestLayoutContext(LayoutContext.of(branch, state), this)

    fun <T : LayoutAsset<T>> updateContext(original: T, context: LayoutContext): T = original
        .takeIf { o -> o.contextData.designId == context.branch.designId && o.isDraft == (context.state == DRAFT) }
        ?: original.withContext(LayoutContextData.new(context, original.contextData.rowId))

    fun insertProject(): RowVersion<Project> = geometryDao.insertProject(project(getUnusedProjectName().toString()))

    fun insertAuthor(): RowVersion<Author> = geometryDao.insertAuthor(Author(getUnusedAuthorCompanyName()))
}

data class TestLayoutContext(
    val context: LayoutContext,
    val testService: TestDBService,
) : TestDB by testService {

    inline fun <reified T : LayoutAsset<T>> fetch(id: IntId<T>): T? =
        getDao(T::class).let { dao -> dao.fetchVersion(context, id)?.let(dao::fetch) }

    fun <T : LayoutAsset<T>> insert(asset: T): DaoResponse<T> = testService.getDao(asset)
        .insert(testService.updateContext(asset, context))

    fun <T : LinearGeometryAsset<T>> insert(
        assetAndAlignment: Pair<LinearGeometryAsset<T>, LayoutAlignment>
    ): DaoResponse<T> = insert(assetAndAlignment.first, assetAndAlignment.second)

    @Suppress("UNCHECKED_CAST")
    fun <T : LinearGeometryAsset<T>> insert(
        asset: LinearGeometryAsset<T>,
        alignment: LayoutAlignment,
    ): DaoResponse<T> = when (asset) {
        is LocationTrack -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
        is ReferenceLine -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
    } as DaoResponse<T>

    fun <T : LayoutAsset<T>> insertMany(vararg asset: T): List<DaoResponse<T>> = asset.map(::insert)
    fun <T : LinearGeometryAsset<T>> insertMany(
        vararg assets: Pair<LinearGeometryAsset<T>, LayoutAlignment>,
    ): List<DaoResponse<T>> = assets.map(::insert)

    fun <T : LayoutAsset<T>> insertAndFetch(asset: T): T = getDao(asset).fetch(insert(asset).rowVersion)

    fun <T : LinearGeometryAsset<T>> insertAndFetch(
        assetAndAlignment: Pair<LinearGeometryAsset<T>, LayoutAlignment>,
    ): Pair<T, LayoutAlignment> = insertAndFetch(assetAndAlignment.first, assetAndAlignment.second)

    fun <T : LinearGeometryAsset<T>> insertAndFetch(
        asset: LinearGeometryAsset<T>,
        alignment: LayoutAlignment,
    ): Pair<T, LayoutAlignment> = getDao(asset)
        .fetch(insert(asset, alignment).rowVersion)
        .let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun <T : LayoutAsset<T>> insertAndFetchMany(vararg asset: T): List<T> = asset.map(::insertAndFetch)

    fun <T : LinearGeometryAsset<T>> insertAndFetchMany(
        vararg assets: Pair<LinearGeometryAsset<T>, LayoutAlignment>,
    ): List<Pair<T, LayoutAlignment>> = assets.map(::insertAndFetch)

    fun insertAndFetchTrackNumber(): TrackLayoutTrackNumber =
        insertTrackNumber().let { r -> trackNumberDao.fetch(r.rowVersion) }

    fun getNewTrackNumberAndId(): Pair<TrackNumber, IntId<TrackLayoutTrackNumber>> =
        insertAndFetchTrackNumber().let { tn -> tn.number to tn.id as IntId }

    fun insertTrackNumber(): DaoResponse<TrackLayoutTrackNumber> =
        insert(trackNumber(testService.getUnusedTrackNumber()))

    fun insertTrackNumbers(count: Int): List<DaoResponse<TrackLayoutTrackNumber>> =
        (1..count).map { insertTrackNumber() }

    fun getOrCreateTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val version = trackNumberDao.fetchVersions(context, true, trackNumber).firstOrNull()
            ?: insert(trackNumber(trackNumber)).rowVersion
        return version.let(trackNumberDao::fetch)
    }

    fun insertSwitch(): DaoResponse<TrackLayoutSwitch> =
        insert(switch(name = testService.getUnusedSwitchName().toString()))
}
