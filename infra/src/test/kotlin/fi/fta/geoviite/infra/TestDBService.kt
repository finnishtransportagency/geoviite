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
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.PolyLineLayoutAsset
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
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

    fun clearLayoutTables() {
        deleteFromTables(
            schema = "layout",
            tables = arrayOf(
                "design",
                "alignment",
                "km_post",
                "location_track",
                "reference_line",
                "switch",
                "switch_version",
                "switch_joint",
                "track_number",
                "segment_version",
                "segment_geometry",
                "design",
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
                "plan_file",
                "plan_project",
                "plan_author",
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
                "split_target_location_track",
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

    private fun <T> getUniqueId(table: DbTable, column: String): IntId<T> {
        val sql = "select max($column) max_id from ${table.fullName}"
        val maxId = jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("max_id") }

        return when {
            maxId == null -> IntId(0)
            else -> IntId(maxId + 1)
        }
    }

    final inline fun <reified T : LayoutAsset<T>> fetch(rowVersion: RowVersion<T>): T =
        getDao(T::class).fetch(rowVersion)

    final inline fun <reified T : PolyLineLayoutAsset<T>> fetchWithAlignment(
        rowVersion: RowVersion<T>,
    ): Pair<T, LayoutAlignment> =
        fetch(rowVersion).let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

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

    inline fun <reified T : PolyLineLayoutAsset<T>> fetchWithAlignment(id: IntId<T>): Pair<T, LayoutAlignment>? =
        fetch(id)?.let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun <T : LayoutAsset<T>> insert(asset: T): DaoResponse<T> = testService.getDao(asset)
        .insert(testService.updateContext(asset, context))

    fun <T : PolyLineLayoutAsset<T>> insert(
        assetAndAlignment: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>
    ): DaoResponse<T> = insert(assetAndAlignment.first, assetAndAlignment.second)

    @Suppress("UNCHECKED_CAST")
    fun <T : PolyLineLayoutAsset<T>> insert(
        asset: PolyLineLayoutAsset<T>,
        alignment: LayoutAlignment,
    ): DaoResponse<T> = when (asset) {
        is LocationTrack -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
        is ReferenceLine -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
    } as DaoResponse<T>

    fun <T : LayoutAsset<T>> insertMany(vararg asset: T): List<DaoResponse<T>> = asset.map(::insert)
    fun <T : PolyLineLayoutAsset<T>> insertMany(
        vararg assets: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>,
    ): List<DaoResponse<T>> = assets.map(::insert)

    fun <T : LayoutAsset<T>> insertAndFetch(asset: T): T = getDao(asset).fetch(insert(asset).rowVersion)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetch(
        assetAndAlignment: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>,
    ): Pair<T, LayoutAlignment> = insertAndFetch(assetAndAlignment.first, assetAndAlignment.second)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetch(
        asset: PolyLineLayoutAsset<T>,
        alignment: LayoutAlignment,
    ): Pair<T, LayoutAlignment> = getDao(asset)
        .fetch(insert(asset, alignment).rowVersion)
        .let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun <T : LayoutAsset<T>> insertAndFetchMany(vararg asset: T): List<T> = asset.map(::insertAndFetch)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetchMany(
        vararg assets: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>,
    ): List<Pair<T, LayoutAlignment>> = assets.map(::insertAndFetch)

    fun createLayoutTrackNumber(): DaoResponse<TrackLayoutTrackNumber> =
        insert(trackNumber(testService.getUnusedTrackNumber()))

    fun createAndFetchLayoutTrackNumber(): TrackLayoutTrackNumber =
        createLayoutTrackNumber().let { r -> trackNumberDao.fetch(r.rowVersion) }

    fun createLayoutTrackNumberAndReferenceLine(
        lineAlignment: LayoutAlignment = alignment()
    ): DaoResponse<TrackLayoutTrackNumber> = createLayoutTrackNumber()
        .also { tnResponse -> insert(referenceLine(trackNumberId = tnResponse.id), lineAlignment) }

    fun createLayoutTrackNumbers(count: Int): List<DaoResponse<TrackLayoutTrackNumber>> =
        (1..count).map { createLayoutTrackNumber() }

    fun getOrCreateLayoutTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val version = trackNumberDao.fetchVersions(context, true, trackNumber).firstOrNull()
            ?: insert(trackNumber(trackNumber)).rowVersion
        return version.let(trackNumberDao::fetch)
    }

    fun createTrackNumberAndId(): Pair<TrackNumber, IntId<TrackLayoutTrackNumber>> =
        createAndFetchLayoutTrackNumber().let { tn -> tn.number to tn.id as IntId }

    fun createSwitch(): DaoResponse<TrackLayoutSwitch> =
        insert(switch(name = testService.getUnusedSwitchName().toString()))
}
