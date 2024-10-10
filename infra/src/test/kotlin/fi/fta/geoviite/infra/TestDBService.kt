package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.CompanyName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.project
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.tracklayout.ContextIdHolder
import fi.fta.geoviite.infra.tracklayout.DesignDraftContextData
import fi.fta.geoviite.infra.tracklayout.DesignOfficialContextData
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutDaoResponse
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.MainDraftContextData
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.OverwritingContextIdHolder
import fi.fta.geoviite.infra.tracklayout.PolyLineLayoutAsset
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.UnstoredContextIdHolder
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

interface TestDB {
    val jdbc: NamedParameterJdbcTemplate
    val locationTrackDao: LocationTrackDao
    val switchDao: LayoutSwitchDao
    val trackNumberDao: LayoutTrackNumberDao
    val referenceLineDao: ReferenceLineDao
    val kmPostDao: LayoutKmPostDao
    val alignmentDao: LayoutAlignmentDao
    val geometryDao: GeometryDao

    fun getDbTime(): Instant =
        requireNotNull(
            jdbc.queryForObject("select now() as now", mapOf<String, Any>()) { rs, _ -> rs.getInstant("now") }
        )

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> getDao(clazz: KClass<T>): LayoutAssetDao<T> =
        when (clazz) {
            LocationTrack::class -> locationTrackDao
            TrackLayoutSwitch::class -> switchDao
            TrackLayoutTrackNumber::class -> trackNumberDao
            ReferenceLine::class -> referenceLineDao
            TrackLayoutKmPost::class -> kmPostDao
            else -> error("Unsupported asset type: ${clazz.simpleName}")
        }
            as LayoutAssetDao<T>

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> getDao(asset: LayoutAsset<T>): LayoutAssetDao<T> =
        when (asset) {
            is LocationTrack -> locationTrackDao
            is TrackLayoutSwitch -> switchDao
            is TrackLayoutTrackNumber -> trackNumberDao
            is ReferenceLine -> referenceLineDao
            is TrackLayoutKmPost -> kmPostDao
        }
            as LayoutAssetDao<T>
}

@GeoviiteService
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
    private val layoutDesignDao: LayoutDesignDao,
) : TestDB {

    override val jdbc by lazy { jdbcTemplate ?: error("JDBC not initialized") }

    val transaction by lazy { transactionTemplate ?: error("JDBC not initialized") }

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
            tables =
                arrayOf(
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
                ),
        )
    }

    fun clearGeometryTables() {
        deleteFromTables(
            schema = "geometry",
            tables =
                arrayOf(
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
                ),
        )
    }

    fun clearPublicationTables() {
        deleteFromTables(
            schema = "publication",
            tables =
                arrayOf(
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
            tables = arrayOf("split", "split_version", "split_relinked_switch", "split_target_location_track"),
        )
    }

    fun clearProjektivelhoTables() {
        deleteFromTables(
            schema = "projektivelho",
            tables =
                arrayOf(
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
        deleteFromTables(schema = "integrations", tables = arrayOf("ratko_push_content"))
    }

    fun getUnusedTrackNumber(): TrackNumber {
        return TrackNumber(getUniqueName(DbTable.LAYOUT_TRACK_NUMBER, TrackNumber.allowedLength.last))
    }

    fun getUnusedSwitchName(): SwitchName {
        return SwitchName(getUniqueName(DbTable.LAYOUT_SWITCH, SwitchName.allowedLength.last))
    }

    fun getUnusedDesignName() = getUniqueName(DbTable.LAYOUT_DESIGN, 50) // arbitrary length limit, fix in GVT-2719

    fun getUnusedProjectName(): ProjectName = ProjectName(getUniqueName(DbTable.GEOMETRY_PLAN_PROJECT, 100))

    fun getUnusedAuthorCompanyName(): CompanyName = CompanyName(getUniqueName(DbTable.GEOMETRY_PLAN_AUTHOR, 100))

    fun getUnusedBulkTransferId(): IntId<BulkTransfer> {
        return getUniqueId(DbTable.PUBLICATION_SPLIT, "bulk_transfer_id")
    }

    private fun getUniqueName(table: DbTable, maxLength: Int): String {
        val sql = "select max(id) max_id from ${table.versionTable}"
        val maxId = jdbc.queryForObject(sql, mapOf<String, Any>()) { rs, _ -> rs.getInt("max_id") }!!
        val baseNameLength = maxLength - 8 // allow 7 unique digits + space
        val baseName =
            this::class.simpleName!!.let { className ->
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

    final inline fun <reified T : LayoutAsset<T>> fetch(rowVersion: LayoutRowVersion<T>): T =
        getDao(T::class).fetch(rowVersion)

    final inline fun <reified T : PolyLineLayoutAsset<T>> fetchWithAlignment(
        rowVersion: LayoutRowVersion<T>
    ): Pair<T, LayoutAlignment> = fetch(rowVersion).let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun deleteFromTables(schema: String, vararg tables: String) {
        // We don't actually need transactionality, but we do need everything to be run in one
        // session
        transactional {
            // Temporarily disable all triggers
            jdbc.execute("set session_replication_role = replica") { it.execute() }
            try {
                tables.forEach { table -> jdbc.update("delete from ${schema}.${table};", emptyMap<String, Any>()) }
            } finally {
                jdbc.execute("set session_replication_role = DEFAULT") { it.execute() }
            }
        }
    }

    fun <T> transactional(op: () -> T): T =
        transaction.execute {
            jdbc.setUser()
            op()
        } ?: error("Transaction returned nothing")

    fun testContext(branch: LayoutBranch = LayoutBranch.main, state: PublicationState = OFFICIAL): TestLayoutContext =
        TestLayoutContext(LayoutContext.of(branch, state), this)

    fun <T : LayoutAsset<T>> updateContext(original: T, context: LayoutContext): T =
        original.takeIf { o ->
            o.contextData.designId == context.branch.designId && o.isDraft == (context.state == DRAFT)
        } ?: original.withContext(LayoutContextData.new(context))

    fun insertProject(): RowVersion<Project> = geometryDao.insertProject(project(getUnusedProjectName().toString()))

    fun insertAuthor(): RowVersion<Author> = geometryDao.insertAuthor(Author(getUnusedAuthorCompanyName()))

    final inline fun <reified T : LayoutAsset<T>> update(
        rowVersion: LayoutRowVersion<T>,
        mutate: (T) -> T = { it },
    ): LayoutDaoResponse<T> {
        val dao = getDao(T::class)
        return dao.update(mutate(dao.fetch(rowVersion)))
    }

    fun createLayoutDesign(): IntId<LayoutDesign> = layoutDesignDao.insert(layoutDesign(getUnusedDesignName()))

    fun createDesignBranch(): DesignBranch = LayoutBranch.design(createLayoutDesign())

    fun layoutChangeTime(): Instant =
        listOf(
                trackNumberDao.fetchChangeTime(),
                referenceLineDao.fetchChangeTime(),
                locationTrackDao.fetchChangeTime(),
                switchDao.fetchChangeTime(),
                kmPostDao.fetchChangeTime(),
            )
            .max()

    /**
     * This function can be used to create a draft-version of an official asset, creating the appropriate backwards
     * linking as necessary. Optionally, a target branch can be given to create the draft in a different branch
     * (main-official -> design-draft or design-official -> main-draft). If not given, the current branch is used.
     */
    final inline fun <reified S : LayoutAsset<S>> createDraft(
        officialVersion: LayoutRowVersion<S>,
        targetBranch: LayoutBranch? = null,
        mutate: (S) -> S = { it },
    ): LayoutDaoResponse<S> {
        val original = fetch(officialVersion)
        check(original.isOfficial) { "$original should be official" }
        val targetContext = testContext(targetBranch ?: original.branch, DRAFT)
        return targetContext.copyFrom(
            officialVersion,
            officialRowId = if (!original.isDesign) original.contextData.rowId else original.contextData.officialRowId,
            designRowId = if (original.isDesign) original.contextData.rowId else null,
            mutate = mutate,
        )
    }
}

data class TestLayoutContext(val context: LayoutContext, val testService: TestDBService) : TestDB by testService {

    inline fun <reified T : LayoutAsset<T>> fetch(id: IntId<T>): T? =
        getDao(T::class).let { dao -> dao.fetchVersion(context, id)?.let(dao::fetch) }

    inline fun <reified T : PolyLineLayoutAsset<T>> fetchWithAlignment(id: IntId<T>): Pair<T, LayoutAlignment>? =
        fetch(id)?.let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun <T : LayoutAsset<T>> insert(asset: T): LayoutDaoResponse<T> =
        testService.getDao(asset).insert(testService.updateContext(asset, context))

    fun <T : PolyLineLayoutAsset<T>> insert(
        assetAndAlignment: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>
    ): LayoutDaoResponse<T> = insert(assetAndAlignment.first, assetAndAlignment.second)

    inline fun <reified T : LayoutAsset<T>> assertContextVersionExists(id: IntId<T>) =
        assertEquals(context, getAssetOriginContext(id))

    inline fun <reified T : LayoutAsset<T>> assertContextVersionDoesntExist(id: IntId<T>) =
        assertNotEquals(context, getAssetOriginContext(id))

    inline fun <reified T : LayoutAsset<T>> getAssetOriginContext(id: IntId<T>): LayoutContext {
        val assetInContext = getDao(T::class).getOrThrow(context, id)
        return LayoutContext.of(assetInContext.branch, if (assetInContext.isDraft) DRAFT else OFFICIAL)
    }

    /**
     * Copies the asset identified by [rowVersion] to the current context. Note, that this does not create linking to
     * the original asset, so calling this for draft context on an official asset creates a new draft with same data,
     * not a draft of the official. You can provide [officialRowId] and [designRowId] to link the new asset if desired.
     *
     * <p>
     * If desired, you can also mutate the asset before moving it to the new context by providing a [mutate] function.
     * </p>
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : LayoutAsset<T>> copyFrom(
        rowVersion: LayoutRowVersion<T>,
        officialRowId: LayoutRowId<T>? = null,
        designRowId: LayoutRowId<T>? = null,
        mutate: (T) -> T = { it },
    ): LayoutDaoResponse<T> {
        val dao = getDao(T::class)
        val original = mutate(dao.fetch(rowVersion))
        val withNewContext =
            original.withContext(createContextData(UnstoredContextIdHolder(rowVersion), officialRowId, designRowId))
        return when (withNewContext) {
            // Also copy alignment: the types won't play nice unless we use the final ones, so this
            // duplicates
            is LocationTrack -> insert(withNewContext, alignmentDao.fetch(withNewContext.getAlignmentVersionOrThrow()))
            is ReferenceLine -> insert(withNewContext, alignmentDao.fetch(withNewContext.getAlignmentVersionOrThrow()))
            is PolyLineLayoutAsset<*> -> error("Unhandled PolyLineAsset type: ${T::class.simpleName}")
            else -> insert(withNewContext)
        }
            as LayoutDaoResponse<T>
    }

    /**
     * Moves the asset identified by [rowVersion] to the current context, maintaining the row itself. Links to
     * official/design row are kept where possible, noting the rules of which contexts actually have them.
     *
     * <p>
     * If desired, you can also mutate the asset before moving it to the new context by providing a [mutate] function.
     * </p>
     */
    inline fun <reified T : LayoutAsset<T>> moveFrom(
        rowVersion: LayoutRowVersion<T>,
        mutate: (T) -> T = { it },
    ): LayoutDaoResponse<T> {
        val dao = getDao(T::class)
        val original = mutate(dao.fetch(rowVersion))
        val withNewContext =
            original.withContext(
                original.contextData.let { origCtx ->
                    createContextData(
                        rowContextId = OverwritingContextIdHolder(rowVersion.rowId, rowVersion),
                        officialRowId = origCtx.officialRowId.takeIf { context !is MainLayoutContext },
                        designRowId = origCtx.designRowId.takeIf { context.state == DRAFT },
                    )
                }
            )
        return dao.update(withNewContext)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : PolyLineLayoutAsset<T>> insert(
        asset: PolyLineLayoutAsset<T>,
        alignment: LayoutAlignment,
    ): LayoutDaoResponse<T> =
        when (asset) {
            is LocationTrack -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
            is ReferenceLine -> insert(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))
        }
            as LayoutDaoResponse<T>

    fun <T : LayoutAsset<T>> insertMany(vararg asset: T): List<LayoutDaoResponse<T>> = asset.map(::insert)

    fun <T : PolyLineLayoutAsset<T>> insertMany(
        vararg assets: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>
    ): List<LayoutDaoResponse<T>> = assets.map(::insert)

    fun <T : LayoutAsset<T>> insertAndFetch(asset: T): T = getDao(asset).fetch(insert(asset).rowVersion)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetch(
        assetAndAlignment: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>
    ): Pair<T, LayoutAlignment> = insertAndFetch(assetAndAlignment.first, assetAndAlignment.second)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetch(
        asset: PolyLineLayoutAsset<T>,
        alignment: LayoutAlignment,
    ): Pair<T, LayoutAlignment> =
        getDao(asset).fetch(insert(asset, alignment).rowVersion).let { a ->
            a to alignmentDao.fetch(a.getAlignmentVersionOrThrow())
        }

    fun <T : LayoutAsset<T>> insertAndFetchMany(vararg asset: T): List<T> = asset.map(::insertAndFetch)

    fun <T : PolyLineLayoutAsset<T>> insertAndFetchMany(
        vararg assets: Pair<PolyLineLayoutAsset<T>, LayoutAlignment>
    ): List<Pair<T, LayoutAlignment>> = assets.map(::insertAndFetch)

    fun createLayoutTrackNumber(): LayoutDaoResponse<TrackLayoutTrackNumber> =
        insert(trackNumber(testService.getUnusedTrackNumber()))

    fun createAndFetchLayoutTrackNumber(): TrackLayoutTrackNumber =
        createLayoutTrackNumber().let { r -> trackNumberDao.fetch(r.rowVersion) }

    fun createLayoutTrackNumberAndReferenceLine(
        lineAlignment: LayoutAlignment = alignment()
    ): LayoutDaoResponse<TrackLayoutTrackNumber> =
        createLayoutTrackNumber().also { tnResponse ->
            insert(referenceLine(trackNumberId = tnResponse.id), lineAlignment)
        }

    fun createLayoutTrackNumbers(count: Int): List<LayoutDaoResponse<TrackLayoutTrackNumber>> =
        (1..count).map { createLayoutTrackNumber() }

    fun getOrCreateLayoutTrackNumber(trackNumber: TrackNumber): TrackLayoutTrackNumber {
        val response =
            trackNumberDao.fetchVersions(context, true, trackNumber).firstOrNull() ?: insert(trackNumber(trackNumber))
        return response.let { r -> trackNumberDao.fetch(r.rowVersion) }
    }

    fun createTrackNumberAndId(): Pair<TrackNumber, IntId<TrackLayoutTrackNumber>> =
        createAndFetchLayoutTrackNumber().let { tn -> tn.number to tn.id as IntId }

    fun createSwitch(
        stateCategory: LayoutStateCategory = EXISTING,
        joints: List<TrackLayoutSwitchJoint> = emptyList(),
    ): LayoutDaoResponse<TrackLayoutSwitch> =
        insert(
            switch(name = testService.getUnusedSwitchName().toString(), stateCategory = stateCategory, joints = joints)
        )

    fun createSwitchWithInnerTracks(
        name: String,
        vararg alignmentJointPositions: List<Pair<JointNumber, Point>>,
    ): Pair<IntId<TrackLayoutSwitch>, List<IntId<LocationTrack>>> {
        val switchId =
            insert(
                    switch(
                        name = name,
                        joints =
                            alignmentJointPositions
                                .flatMap { it }
                                .map { (jointNumber, position) ->
                                    TrackLayoutSwitchJoint(
                                        number = jointNumber,
                                        location = position,
                                        locationAccuracy = null,
                                    )
                                },
                        stateCategory = EXISTING,
                    )
                )
                .id
        val innerTrackIds =
            alignmentJointPositions.map { jointPositions ->
                insert(
                        locationTrackAndAlignment(
                            createLayoutTrackNumber().id,
                            segments =
                                jointPositions.zipWithNext().map { (from, to) ->
                                    segment(
                                        points = arrayOf(from.second, to.second),
                                        switchId = switchId,
                                        startJointNumber = from.first,
                                        endJointNumber = to.first,
                                    )
                                },
                        )
                    )
                    .id
            }
        return switchId to innerTrackIds
    }

    fun <T : LayoutAsset<T>> createContextData(
        rowContextId: ContextIdHolder<T>,
        officialRowId: LayoutRowId<T>? = null,
        designRowId: LayoutRowId<T>? = null,
    ): LayoutContextData<T> =
        context.branch.let { branch ->
            when (context.state) {
                OFFICIAL ->
                    when (branch) {
                        is MainBranch ->
                            MainOfficialContextData(rowContextId).also {
                                require(officialRowId == null) {
                                    "Can't set official row reference on official row itself"
                                }
                                require(designRowId == null) { "Can't set design row reference on main-official row" }
                            }
                        is DesignBranch ->
                            DesignOfficialContextData(rowContextId, officialRowId, branch.designId).also {
                                require(designRowId == null) {
                                    "Can't set design row reference on official design itself"
                                }
                            }
                    }
                DRAFT ->
                    when (branch) {
                        is MainBranch -> MainDraftContextData(rowContextId, officialRowId, designRowId)
                        is DesignBranch ->
                            DesignDraftContextData(rowContextId, designRowId, officialRowId, branch.designId)
                    }
            }
        }
}
