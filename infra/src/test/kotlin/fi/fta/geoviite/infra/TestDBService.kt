package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.CompanyName
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.project
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.DesignDraftContextData
import fi.fta.geoviite.infra.tracklayout.DesignOfficialContextData
import fi.fta.geoviite.infra.tracklayout.EditedAssetId
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetId
import fi.fta.geoviite.infra.tracklayout.LayoutAssetReader
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.MainDraftContextData
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.PolyLineLayoutAsset
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.toSegmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.setUser
import java.time.Instant
import kotlin.reflect.KClass
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
    fun <T : LayoutAsset<T>> getReader(clazz: KClass<T>): LayoutAssetReader<T> =
        when (clazz) {
            LocationTrack::class -> locationTrackDao
            LayoutSwitch::class -> switchDao
            LayoutTrackNumber::class -> trackNumberDao
            ReferenceLine::class -> referenceLineDao
            LayoutKmPost::class -> kmPostDao
            else -> error("Unsupported asset type: ${clazz.simpleName}")
        }
            as LayoutAssetReader<T>

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> getReader(asset: LayoutAsset<T>): LayoutAssetReader<T> =
        when (asset) {
            is LocationTrack -> locationTrackDao
            is LayoutSwitch -> switchDao
            is LayoutTrackNumber -> trackNumberDao
            is ReferenceLine -> referenceLineDao
            is LayoutKmPost -> kmPostDao
        }
            as LayoutAssetReader<T>
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
                    "location_track_external_id",
                    "reference_line",
                    "switch",
                    "switch_external_id",
                    "switch_version",
                    "switch_version_joint",
                    "track_number",
                    "track_number_external_id",
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
        getReader(T::class).fetch(rowVersion)

    final fun fetchWithAlignment(rowVersion: LayoutRowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> =
        fetch(rowVersion).let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    final fun fetchWithGeometry(
        rowVersion: LayoutRowVersion<LocationTrack>
    ): Pair<LocationTrack, DbLocationTrackGeometry> =
        fetch(rowVersion).let { a -> a to alignmentDao.fetch(a.versionOrThrow) }

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
        original.takeIf { o -> o.layoutContext == context }
            ?: original.withContext(LayoutContextData.new(context, original.id as? IntId))

    fun insertProject(): RowVersion<Project> = geometryDao.insertProject(project(getUnusedProjectName().toString()))

    fun insertAuthor(): RowVersion<Author> = geometryDao.insertAuthor(Author(getUnusedAuthorCompanyName()))

    @Suppress("UNCHECKED_CAST")
    fun <T : LayoutAsset<T>> save(
        asset: LayoutAsset<T>,
        originVersion: LayoutRowVersion<T>? = asset.version,
    ): LayoutRowVersion<T> =
        when (asset) {
            is LayoutTrackNumber -> trackNumberDao.save(asset)
            is LocationTrack ->
                locationTrackDao.save(asset, asset.version?.let(alignmentDao::fetch) ?: TmpLocationTrackGeometry.empty)
            is ReferenceLine ->
                referenceLineDao.save(
                    asset.takeIf { it.alignmentVersion != null }
                        ?: asset.copy(alignmentVersion = alignmentDao.insert(alignment()))
                )
            is LayoutKmPost -> kmPostDao.save(asset)
            is LayoutSwitch -> switchDao.save(asset)
        }
            as LayoutRowVersion<T>

    fun save(asset: LocationTrack, geometry: LocationTrackGeometry): LayoutRowVersion<LocationTrack> =
        locationTrackDao.save(asset, geometry)

    fun save(asset: ReferenceLine, alignment: LayoutAlignment): LayoutRowVersion<ReferenceLine> =
        referenceLineDao.save(asset.copy(alignmentVersion = alignmentDao.insert(alignment)))

    final inline fun <reified T : LayoutAsset<T>> update(
        rowVersion: LayoutRowVersion<T>,
        mutate: (T) -> T = { it },
    ): LayoutRowVersion<T> = save(mutate(fetch(rowVersion)))

    @Suppress("UNCHECKED_CAST")
    final inline fun <reified T : LayoutAsset<T>> delete(asset: LayoutRowVersion<T>) =
        when (T::class) {
            LocationTrack::class -> locationTrackDao.deleteRow(asset.rowId as LayoutRowId<LocationTrack>)
            ReferenceLine::class -> referenceLineDao.deleteRow(asset.rowId as LayoutRowId<ReferenceLine>)
            LayoutSwitch::class -> switchDao.deleteRow(asset.rowId as LayoutRowId<LayoutSwitch>)
            LayoutKmPost::class -> kmPostDao.deleteRow(asset.rowId as LayoutRowId<LayoutKmPost>)
            LayoutTrackNumber::class -> trackNumberDao.deleteRow(asset.rowId as LayoutRowId<LayoutTrackNumber>)
            else -> error("Unknown asset type: ${T::class.simpleName}")
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
     * This function can be used to create a draft-version of an official asset. Optionally, a target branch can be
     * given to create the draft in a different branch (main-official -> design-draft or design-official -> main-draft).
     * If not given, the current branch is used.
     */
    final inline fun <reified S : LayoutAsset<S>> createDraft(
        officialVersion: LayoutRowVersion<S>,
        targetBranch: LayoutBranch? = null,
        mutate: (S) -> S = { it },
    ): LayoutRowVersion<S> {
        val original = fetch(officialVersion)
        check(original.isOfficial) { "$original should be official" }
        val targetContext = testContext(targetBranch ?: original.branch, DRAFT)
        return targetContext.copyFrom(officialVersion, mutate = mutate)
    }
}

data class TestLayoutContext(val context: LayoutContext, val testService: TestDBService) : TestDB by testService {

    inline fun <reified T : LayoutAsset<T>> fetchVersion(id: IntId<T>): LayoutRowVersion<T>? =
        getReader(T::class).fetchVersion(context, id)

    inline fun <reified T : LayoutAsset<T>> fetch(id: IntId<T>): T? = getReader(T::class).get(context, id)

    fun fetchWithAlignment(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? =
        fetch(id)?.let { a -> a to alignmentDao.fetch(a.getAlignmentVersionOrThrow()) }

    fun fetchWithGeometry(id: IntId<LocationTrack>): Pair<LocationTrack, DbLocationTrackGeometry>? =
        locationTrackDao.get(context, id)?.let { track -> track to alignmentDao.fetch(track.versionOrThrow) }

    fun <T : LayoutAsset<T>> save(asset: T): LayoutRowVersion<T> =
        testService.save(testService.updateContext(asset, context))

    fun saveLocationTrack(asset: Pair<LocationTrack, LocationTrackGeometry>): LayoutRowVersion<LocationTrack> =
        save(asset.first, asset.second)

    fun save(asset: LocationTrack, geometry: LocationTrackGeometry): LayoutRowVersion<LocationTrack> =
        testService.save(testService.updateContext(asset, context), geometry)

    fun saveReferenceLine(asset: Pair<ReferenceLine, LayoutAlignment>): LayoutRowVersion<ReferenceLine> =
        save(asset.first, asset.second)

    fun save(asset: ReferenceLine, alignment: LayoutAlignment): LayoutRowVersion<ReferenceLine> =
        testService.save(testService.updateContext(asset, context), alignment)

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
        mutate: (T) -> T = { it },
    ): LayoutRowVersion<T> {
        val dao = getReader(T::class)
        val original = mutate(dao.fetch(rowVersion))
        val withNewContext = original.withContext(createContextData(EditedAssetId(rowVersion)))
        return when (withNewContext) {
            // Also copy alignment for polyline assets
            is LocationTrack -> save(withNewContext, alignmentDao.fetch(rowVersion as LayoutRowVersion<LocationTrack>))
            is ReferenceLine -> save(withNewContext, alignmentDao.fetch(withNewContext.getAlignmentVersionOrThrow()))
            is PolyLineLayoutAsset<*> -> error("Unhandled PolyLineAsset type: ${T::class.simpleName}")
            else -> save(withNewContext)
        }
            as LayoutRowVersion<T>
    }

    /**
     * Copies the asset identified by [rowVersion] to the current context and deletes the original row.
     *
     * <p>
     * If desired, you can also mutate the asset before moving it to the new context by providing a [mutate] function.
     * </p>
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : LayoutAsset<T>> moveFrom(
        rowVersion: LayoutRowVersion<T>,
        mutate: (T) -> T = { it },
    ): LayoutRowVersion<T> {
        val original = testService.fetch(rowVersion)
        val mutated = mutate(original)
        val withNewContext = mutated.withContext(createContextData(rowContextId = EditedAssetId(rowVersion)))
        testService.delete<T>(original.version!!)
        return when (withNewContext) {
            // Also move alignment for polyline assets
            is LocationTrack -> save(withNewContext, alignmentDao.fetch(rowVersion as LayoutRowVersion<LocationTrack>))
            is ReferenceLine -> save(withNewContext, alignmentDao.fetch(withNewContext.getAlignmentVersionOrThrow()))
            is PolyLineLayoutAsset<*> -> error("Unhandled PolyLineAsset type: ${T::class.simpleName}")
            else -> save(withNewContext)
        }
            as LayoutRowVersion<T>
    }

    fun <T : LayoutAsset<T>> saveMany(vararg asset: T): List<LayoutRowVersion<T>> = asset.map(::save)

    fun saveManyLocationTracks(
        vararg assets: Pair<LocationTrack, LocationTrackGeometry>
    ): List<LayoutRowVersion<LocationTrack>> = assets.map(::saveLocationTrack)

    fun saveManyReferenceLines(
        vararg assets: Pair<ReferenceLine, LayoutAlignment>
    ): List<LayoutRowVersion<ReferenceLine>> = assets.map(::saveReferenceLine)

    fun <T : LayoutAsset<T>> saveAndFetch(asset: T): T = getReader(asset).fetch(save(asset))

    fun saveAndFetchReferenceLine(
        assetAndAlignment: Pair<ReferenceLine, LayoutAlignment>
    ): Pair<ReferenceLine, LayoutAlignment> = saveAndFetch(assetAndAlignment.first, assetAndAlignment.second)

    fun saveAndFetch(asset: ReferenceLine, alignment: LayoutAlignment): Pair<ReferenceLine, LayoutAlignment> {
        val alignmentVersion = alignmentDao.insert(alignment)
        val referenceLineVersion = referenceLineDao.save(asset.copy(alignmentVersion = alignmentVersion))
        return referenceLineDao.fetch(referenceLineVersion) to alignmentDao.fetch(alignmentVersion)
    }

    fun saveAndFetchLocationTrack(
        assetAndAlignment: Pair<LocationTrack, LocationTrackGeometry>
    ): Pair<LocationTrack, LocationTrackGeometry> = saveAndFetch(assetAndAlignment.first, assetAndAlignment.second)

    fun saveAndFetch(
        asset: LocationTrack,
        geometry: LocationTrackGeometry,
    ): Pair<LocationTrack, DbLocationTrackGeometry> =
        locationTrackDao.save(asset, geometry).let { v -> locationTrackDao.fetch(v) to alignmentDao.fetch(v) }

    fun createLayoutTrackNumber(
        trackNumber: TrackNumber = testService.getUnusedTrackNumber()
    ): LayoutRowVersion<LayoutTrackNumber> = save(trackNumber(trackNumber))

    fun createLayoutTrackNumberWithOid(oid: Oid<LayoutTrackNumber>): LayoutRowVersion<LayoutTrackNumber> {
        return save(trackNumber(testService.getUnusedTrackNumber())).also { trackNumber ->
            trackNumberDao.insertExternalId(trackNumber.id, context.branch, oid)
        }
    }

    fun createAndFetchLayoutTrackNumber(): LayoutTrackNumber = trackNumberDao.fetch(createLayoutTrackNumber())

    fun createLocationTrack(geometry: LocationTrackGeometry): LayoutRowVersion<LocationTrack> {
        return save(locationTrack(createLayoutTrackNumber().id), geometry)
    }

    fun createLocationTrackWithReferenceLine(geometry: LocationTrackGeometry): LayoutRowVersion<LocationTrack> {
        val trackNumberId = createLayoutTrackNumberAndReferenceLine(alignment(geometry.segments)).id
        return save(locationTrack(trackNumberId), geometry)
    }

    fun createLayoutTrackNumberAndReferenceLine(
        lineAlignment: LayoutAlignment = alignment(),
        trackNumber: TrackNumber = testService.getUnusedTrackNumber(),
        startAddress: TrackMeter = TrackMeter.ZERO,
    ): LayoutRowVersion<LayoutTrackNumber> =
        createLayoutTrackNumber(trackNumber).also { tnResponse ->
            save(referenceLine(trackNumberId = tnResponse.id, startAddress = startAddress), lineAlignment)
        }

    fun createLayoutTrackNumbers(count: Int): List<LayoutRowVersion<LayoutTrackNumber>> =
        (1..count).map { createLayoutTrackNumber() }

    fun getOrCreateLayoutTrackNumber(trackNumber: TrackNumber): LayoutTrackNumber {
        val version =
            trackNumberDao.fetchVersions(context, true, trackNumber).firstOrNull() ?: save(trackNumber(trackNumber))
        return trackNumberDao.fetch(version)
    }

    fun createTrackNumberAndId(): Pair<TrackNumber, IntId<LayoutTrackNumber>> =
        createAndFetchLayoutTrackNumber().let { tn -> tn.number to tn.id as IntId }

    fun createSwitch(
        stateCategory: LayoutStateCategory = EXISTING,
        joints: List<LayoutSwitchJoint> = emptyList(),
    ): LayoutRowVersion<LayoutSwitch> =
        save(
            switch(name = testService.getUnusedSwitchName().toString(), stateCategory = stateCategory, joints = joints)
        )

    fun createSwitchWithInnerTracks(
        name: String,
        vararg alignmentJointPositions: List<Pair<JointNumber, Point>>,
    ): Pair<IntId<LayoutSwitch>, List<IntId<LocationTrack>>> {
        val structure = switchStructureYV60_300_1_9()
        val switchId =
            save(
                    switch(
                        name = name,
                        structureId = structure.id,
                        joints =
                            alignmentJointPositions
                                .flatMap { it }
                                .map { (jointNumber, position) ->
                                    LayoutSwitchJoint(
                                        number = jointNumber,
                                        role = SwitchJointRole.of(structure, jointNumber),
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
                save(
                        locationTrack(createLayoutTrackNumber().id),
                        trackGeometry(
                            combineEdges(
                                jointPositions.zipWithNext().map { (from, to) ->
                                    edge(
                                        startInnerSwitch = switchLinkYV(switchId, from.first.intValue),
                                        endInnerSwitch = switchLinkYV(switchId, to.first.intValue),
                                        segments = listOf(segment(toSegmentPoints(from.second, to.second))),
                                    )
                                }
                            )
                        ),
                    )
                    .id
            }
        return switchId to innerTrackIds
    }

    fun <T : LayoutAsset<T>> createContextData(rowContextId: LayoutAssetId<T>): LayoutContextData<T> =
        context.branch.let { branch ->
            when (context.state) {
                OFFICIAL ->
                    when (branch) {
                        is MainBranch -> MainOfficialContextData(rowContextId)
                        is DesignBranch ->
                            DesignOfficialContextData(rowContextId, branch.designId, DesignAssetState.OPEN)
                    }
                DRAFT ->
                    when (branch) {
                        is MainBranch -> MainDraftContextData(rowContextId, LayoutBranch.main)
                        is DesignBranch -> DesignDraftContextData(rowContextId, branch.designId, DesignAssetState.OPEN)
                    }
            }
        }
}
