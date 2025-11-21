package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.SplitSourceLocationTrackUpdateException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.NodeReplacementTarget
import fi.fta.geoviite.infra.linking.mergeNodeCombinations
import fi.fta.geoviite.infra.linking.mergeNodeConnections
import fi.fta.geoviite.infra.linking.resolveNodeCombinations
import fi.fta.geoviite.infra.linking.switches.cropAlignment
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.map.toPolygon
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.MultiPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitDuplicateTrack
import fi.fta.geoviite.infra.split.SplittingInitializationParameters
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.DuplicateEndPointType.END
import fi.fta.geoviite.infra.tracklayout.DuplicateEndPointType.START
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.mapNonNullValues
import fi.fta.geoviite.infra.util.processFlattened
import java.time.Instant
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

const val TRACK_SEARCH_AREA_SIZE = 2.0
const val OPERATIONAL_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE = 1000.0
const val TOPOLOGY_CALC_DISTANCE = 1.0

@GeoviiteService
class LocationTrackService(
    val locationTrackDao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val splitDao: SplitDao,
    private val operationalPointDao: OperationalPointDao,
    private val localizationService: LocalizationService,
    private val transactionTemplate: TransactionTemplate,
    private val trackNumberDao: LayoutTrackNumberDao,
) : LayoutAssetService<LocationTrack, LocationTrackGeometry, LocationTrackDao>(locationTrackDao) {
    val descriptionTranslation = localizationService.getLocalization(LocalizationLanguage.FI)

    @Transactional
    fun insert(branch: LayoutBranch, request: LocationTrackSaveRequest): LayoutRowVersion<LocationTrack> {
        val locationTrack =
            LocationTrack(
                nameStructure = request.nameStructure,
                name =
                    request.nameStructure.reify(
                        trackNumber = trackNumberDao.getOrThrow(branch.draft, request.trackNumberId),
                        startSwitch = null,
                        endSwitch = null,
                    ),
                descriptionStructure = request.descriptionStructure,
                description =
                    request.descriptionStructure.reify(
                        translation = descriptionTranslation,
                        startSwitch = null,
                        endSwitch = null,
                    ),
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                sourceId = null,
                length = LineM(0.0),
                segmentCount = 0,
                boundingBox = null,
                duplicateOf = request.duplicateOf,
                startSwitchId = null,
                endSwitchId = null,
                topologicalConnectivity = request.topologicalConnectivity,
                ownerId = request.ownerId,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            )
        return saveDraft(branch, locationTrack, TmpLocationTrackGeometry.empty)
    }

    fun update(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutRowVersion<LocationTrack> {
        try {
            return requireNotNull(transactionTemplate.execute { updateLocationTrackTransaction(branch, id, request) })
        } catch (dataIntegrityException: DataIntegrityViolationException) {
            throw if (isSplitSourceReferenceError(dataIntegrityException)) {
                SplitSourceLocationTrackUpdateException(
                    AlignmentName(request.nameFreeText?.toString() ?: ""),
                    dataIntegrityException,
                )
            } else {
                dataIntegrityException
            }
        }
    }

    @Suppress("SpringTransactionalMethodCallsInspection")
    private fun updateLocationTrackTransaction(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalGeometry) = getWithGeometryOrThrow(branch.draft, id)
        val locationTrack =
            originalTrack.copy(
                nameStructure = request.nameStructure,
                descriptionStructure = request.descriptionStructure,
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                duplicateOf = request.duplicateOf,
                topologicalConnectivity = request.topologicalConnectivity,
                ownerId = request.ownerId,
            )
        if (request.state == LocationTrackState.DELETED) clearDuplicateReferences(branch, id)
        return saveDraft(branch, locationTrack, originalGeometry)
    }

    @Transactional
    fun updateState(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        state: LocationTrackState,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalGeometry) = getWithGeometryOrThrow(branch.draft, id)
        val locationTrack = originalTrack.copy(state = state)
        if (state == LocationTrackState.DELETED) clearDuplicateReferences(branch, id)
        return saveDraft(branch, locationTrack, originalGeometry)
    }

    @Transactional(readOnly = true)
    fun getStartAndEnd(
        context: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEnd<LocationTrack>> {
        val tracksAndGeometries = getManyWithGeometries(context, ids)
        val getGeocodingContext = geocodingService.getLazyGeocodingContexts(context)
        return tracksAndGeometries.map { (track, geometry) ->
            AlignmentStartAndEnd.of(track.id as IntId, geometry, getGeocodingContext(track.trackNumberId))
        }
    }

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        track: LocationTrack,
        params: LocationTrackGeometry,
    ): LayoutRowVersion<LocationTrack> {
        val versions =
            dao.fetchTrackDependencyVersions(
                context = branch.draft,
                trackNumberId = track.trackNumberId,
                startSwitchId = params.startSwitchLink?.id,
                endSwitchId = params.endSwitchLink?.id,
            )
        return saveDraftInternal(branch, asDraft(branch, recalculateDependencies(track, versions)), params)
    }

    @Transactional
    fun insertExternalId(branch: LayoutBranch, id: IntId<LocationTrack>, oid: Oid<LocationTrack>) =
        dao.insertExternalId(id, branch, oid)

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<LocationTrack>,
    ): PublicationResultVersions<LocationTrack> = publishInternal(branch, version)

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LocationTrack>): LayoutRowVersion<LocationTrack> {
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearDuplicateReferences(branch, id)
        }
        return super.deleteDraft(branch, id)
    }

    @Transactional
    fun fetchDuplicates(layoutContext: LayoutContext, id: IntId<LocationTrack>): List<LocationTrack> =
        dao.fetchDuplicates(layoutContext, id)

    @Transactional
    fun clearDuplicateReferences(branch: LayoutBranch, id: IntId<LocationTrack>) =
        associateWithGeometries(dao.fetchDuplicates(branch.draft, id, includeDeleted = true)).forEach { (dup, geom) ->
            saveDraft(branch, asDraft(branch, dup).copy(duplicateOf = null), geom)
        }

    fun listNonLinked(branch: LayoutBranch): List<LocationTrack> {
        return dao.list(branch.draft, false).filter { a -> a.segmentCount == 0 }
    }

    fun list(layoutContext: LayoutContext, bbox: BoundingBox): List<LocationTrack> {
        return dao.list(layoutContext, false).filter { tn -> bbox.intersects(tn.boundingBox) }
    }

    fun list(
        layoutContext: LayoutContext,
        includeDeleted: Boolean,
        trackNumberId: IntId<LayoutTrackNumber>,
        names: List<AlignmentName>,
    ): List<LocationTrack> {
        return dao.list(layoutContext, includeDeleted, trackNumberId, names)
    }

    fun idMatches(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        onlyIds: Collection<IntId<LocationTrack>>? = null,
    ): ((term: String, item: LocationTrack) -> Boolean) = idMatches(dao, layoutContext, searchTerm, onlyIds)

    override fun contentMatches(term: String, item: LocationTrack, includeDeleted: Boolean) =
        (includeDeleted || item.exists) && (item.name.contains(term, true) || item.description.contains(term, true))

    fun listNear(layoutContext: LayoutContext, bbox: BoundingBox): List<LocationTrack> {
        return dao.listNear(layoutContext, bbox).filter(LocationTrack::exists)
    }

    @Transactional(readOnly = true)
    fun listWithGeometries(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        includeDeleted: Boolean = false,
        boundingBox: BoundingBox? = null,
        minLength: Double? = null,
        locationTrackIds: Set<IntId<LocationTrack>>? = null,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        val versions =
            if (boundingBox == null) dao.fetchVersions(layoutContext, includeDeleted, trackNumberId)
            else dao.fetchVersionsNear(layoutContext, boundingBox, includeDeleted, trackNumberId, minLength)
        val filteredVersions =
            if (locationTrackIds == null) versions
            else versions.filter { version -> locationTrackIds.contains(version.id) }
        val tracks = filterByBoundingBox(dao.fetchMany(filteredVersions), boundingBox)
        return associateWithGeometries(tracks)
    }

    @Transactional(readOnly = true)
    fun getManyWithGeometries(
        layoutContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return dao.getMany(layoutContext, ids).let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun getManyWithGeometries(
        versions: List<LayoutRowVersion<LocationTrack>>
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return dao.fetchMany(versions).let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun getWithGeometryOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, DbLocationTrackGeometry> {
        return getWithGeometryInternalOrThrow(layoutContext, id)
    }

    @Transactional(readOnly = true)
    fun getWithGeometry(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, DbLocationTrackGeometry>? {
        return dao.fetchVersion(layoutContext, id)?.let(::getWithGeometryInternal)
    }

    @Transactional(readOnly = true)
    fun getTrackPoint(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        address: TrackMeter,
    ): AddressPoint<LocationTrackM>? {
        val locationTrackAndGeometry = getWithGeometry(layoutContext, locationTrackId)
        return locationTrackAndGeometry?.let { (locationTrack, geometry) ->
            geocodingService.getTrackLocation(layoutContext, locationTrack, geometry, address)
        }
    }

    @Transactional(readOnly = true)
    fun getOfficialWithGeometryAtMoment(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        moment: Instant,
    ): Pair<LocationTrack, DbLocationTrackGeometry>? {
        return dao.fetchOfficialVersionAtMoment(branch, id, moment)?.let(::getWithGeometryInternal)
    }

    @Transactional(readOnly = true)
    fun listOfficialWithGeometryAtMoment(
        branch: LayoutBranch,
        moment: Instant,
        includeDeleted: Boolean = false,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return dao.fetchManyOfficialVersionsAtMoment(branch, null, moment).let(::getManyWithGeometries).let { list ->
            if (includeDeleted) list else list.filter { (track, _) -> track.exists }
        }
    }

    @Transactional(readOnly = true)
    fun getWithGeometry(version: LayoutRowVersion<LocationTrack>): Pair<LocationTrack, DbLocationTrackGeometry> {
        return getWithGeometryInternal(version)
    }

    @Transactional(readOnly = true)
    fun listNearWithGeometries(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> =
        dao.listNear(layoutContext, bbox).let(::associateWithGeometries).filter { (_, geometry) ->
            geometry.segments.any { segment ->
                bbox.intersects(segment.boundingBox) && segment.segmentPoints.any(bbox::contains)
            }
        }

    @Transactional(readOnly = true)
    fun getLocationTracksNear(
        layoutContext: LayoutContext,
        location: IPoint,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> =
        listNearWithGeometries(
            layoutContext,
            BoundingBox(Point(0.0, 0.0), Point(TRACK_SEARCH_AREA_SIZE, TRACK_SEARCH_AREA_SIZE)).centerAt(location),
        )

    @Transactional(readOnly = true)
    fun getMetadataSections(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection<LocationTrackM>> {
        val locationTrack = get(layoutContext, locationTrackId)
        val geocodingContext =
            locationTrack?.let { geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId) }

        return geocodingContext?.let { context ->
            alignmentService.getGeometryMetadataSections(
                locationTrack.getVersionOrThrow(),
                dao.fetchExternalId(layoutContext.branch, locationTrackId)?.oid,
                boundingBox,
                context,
            )
        } ?: listOf()
    }

    @Transactional(readOnly = true)
    fun getTrackPolygon(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        cropStart: KmNumber?,
        cropEnd: KmNumber?,
        bufferSize: Double,
    ): Polygon? {
        val locationTrack = get(layoutContext, locationTrackId)
        val context =
            locationTrack?.trackNumberId?.let { tnId -> geocodingService.getGeocodingContext(layoutContext, tnId) }
        val geometry = locationTrack?.version?.let(alignmentDao::fetch)
        if (context == null || geometry == null) {
            return null
        }

        val startAndEnd = getStartAndEnd(layoutContext, listOf(locationTrackId)).first()
        val trackStart = startAndEnd.start?.address
        val trackEnd = startAndEnd.end?.address

        return if (trackStart != null && trackEnd != null && cropIsWithinReferenceLine(cropStart, cropEnd, context)) {
            getMRange(geometry, context, trackStart, trackEnd, cropStart, cropEnd)
                ?.let { cropRange -> cropAlignment(geometry.segmentsWithM, cropRange) }
                ?.let { croppedAlignment -> toPolygon(croppedAlignment, bufferSize) }
        } else {
            null
        }
    }

    fun getMRange(
        geometry: LocationTrackGeometry,
        geocodingContext: GeocodingContext<*>,
        trackStart: TrackMeter,
        trackEnd: TrackMeter,
        cropStartKm: KmNumber?,
        cropEndKm: KmNumber?,
    ): Range<LineM<LocationTrackM>>? =
        getAddressRange(geocodingContext, trackStart, trackEnd, cropStartKm, cropEndKm)?.let { range ->
            val start = geocodingContext.getTrackLocation(geometry, range.min)?.point?.m
            val end = geocodingContext.getTrackLocation(geometry, range.max)?.point?.m
            if (start != null && end != null) Range(start, end) else null
        }

    fun getAddressRange(
        geocodingContext: GeocodingContext<*>,
        trackStart: TrackMeter,
        trackEnd: TrackMeter,
        startKm: KmNumber?,
        endKm: KmNumber?,
    ): Range<TrackMeter>? {
        val rangeStart =
            startKm
                ?.let { km -> geocodingContext.kms.find { it.kmNumber >= km }?.startAddress ?: trackEnd }
                ?.coerceAtLeast(trackStart) ?: trackStart
        val rangeEnd =
            endKm?.let { km -> geocodingContext.kms.find { it.kmNumber > km }?.startAddress }?.coerceAtMost(trackEnd)
                ?: trackEnd
        return if (rangeStart < rangeEnd) Range(rangeStart, rangeEnd) else null
    }

    private fun getWithGeometryInternalOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, DbLocationTrackGeometry> {
        return getWithGeometryInternal(dao.fetchVersionOrThrow(layoutContext, id))
    }

    private fun getWithGeometryInternal(
        version: LayoutRowVersion<LocationTrack>
    ): Pair<LocationTrack, DbLocationTrackGeometry> = locationTrackWithGeometry(dao, alignmentDao, version)

    private fun associateWithGeometries(
        lines: Collection<LocationTrack>
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in
        // alignmentDao.fetch
        val alignments = alignmentDao.getMany(lines.map(LocationTrack::getVersionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.getVersionOrThrow()) }
    }

    fun fillTrackAddress(splitPoint: SplitPoint, geocodingContext: GeocodingContext<*>): SplitPoint {
        val address = geocodingContext.getAddress(splitPoint.location)?.first
        return when (splitPoint) {
            is SwitchSplitPoint -> splitPoint.copy(address = address)
            is EndpointSplitPoint -> splitPoint.copy(address = address)
        }
    }

    fun fillTrackAddresses(
        duplicates: List<LocationTrackDuplicate>,
        geocodingContext: GeocodingContext<*>,
    ): List<LocationTrackDuplicate> {
        return duplicates.map { duplicate ->
            duplicate.copy(
                duplicateStatus =
                    duplicate.duplicateStatus.copy(
                        startSplitPoint =
                            duplicate.duplicateStatus.startSplitPoint?.let { splitPoint ->
                                fillTrackAddress(splitPoint, geocodingContext)
                            },
                        endSplitPoint =
                            duplicate.duplicateStatus.endSplitPoint?.let { splitPoint ->
                                fillTrackAddress(splitPoint, geocodingContext)
                            },
                    )
            )
        }
    }

    @Transactional(readOnly = true)
    fun getInfoboxExtras(layoutContext: LayoutContext, id: IntId<LocationTrack>): LocationTrackInfoboxExtras? {
        return getWithGeometry(layoutContext, id)?.let { (track, geometry) ->
            val geocodingContext = geocodingService.getGeocodingContext(layoutContext, track.trackNumberId)
            val start = geometry.start
            val end = geometry.end

            val duplicateOf = getDuplicateTrackParent(layoutContext, track)
            val duplicates =
                getLocationTrackDuplicates(layoutContext, track, geometry)
                    .let { dups -> geocodingContext?.let { gc -> fillTrackAddresses(dups, gc) } ?: dups }
                    .sortedBy { dup -> dup.duplicateStatus.startSplitPoint?.address }

            val startSwitchLink = geometry.startSwitchLink
            val endSwitchLink = geometry.endSwitchLink

            val startSplitPoint = createSplitPoint(start, startSwitchLink?.id, START, geocodingContext)
            val endSplitPoint = createSplitPoint(end, endSwitchLink?.id, END, geocodingContext)

            val partOfUnfinishedSplit =
                splitDao.locationTracksPartOfAnyUnfinishedSplit(layoutContext.branch, listOf(id)).isNotEmpty()

            LocationTrackInfoboxExtras(duplicateOf, duplicates, partOfUnfinishedSplit, startSplitPoint, endSplitPoint)
        }
    }

    private fun createSplitPoint(
        point: AlignmentPoint<LocationTrackM>?,
        switchId: IntId<LayoutSwitch>?,
        endPointType: DuplicateEndPointType,
        geocodingContext: GeocodingContext<*>?,
    ): SplitPoint? {
        val address = point?.let { p -> geocodingContext?.getAddress(p)?.first }
        return when {
            (point == null || address == null) -> null
            (switchId != null) -> SwitchSplitPoint(point, address, switchId, JointNumber(0))
            else -> EndpointSplitPoint(point, address, endPointType)
        }
    }

    @Transactional(readOnly = true)
    fun getRelinkableSwitchesCount(layoutContext: LayoutContext, id: IntId<LocationTrack>): Int? =
        get(layoutContext, id)?.let { track -> countRelinkableSwitches(layoutContext.branch, track) }

    private fun countRelinkableSwitches(branch: LayoutBranch, locationTrack: LocationTrack): Int =
        (locationTrack.switchIds + switchDao.findSwitchesNearTrack(branch, locationTrack.getVersionOrThrow()))
            .distinct()
            .size

    @Transactional(readOnly = true)
    fun getLocationTrackDuplicates(
        layoutContext: LayoutContext,
        track: LocationTrack,
        geometry: LocationTrackGeometry,
    ): List<LocationTrackDuplicate> {
        val markedDuplicateVersions = dao.fetchDuplicateVersions(layoutContext, track.id as IntId)
        val tracksLinkedThroughSwitch =
            if (track.state != LocationTrackState.DELETED) {
                switchDao
                    .findLocationTracksLinkedToSwitches(layoutContext, track.switchIds)
                    .values
                    .flatten()
                    .map(LayoutSwitchDao.LocationTrackIdentifiers::rowVersion)
            } else {
                emptyList()
            }

        val duplicateTracksAndGeometries =
            (markedDuplicateVersions + tracksLinkedThroughSwitch)
                .distinct()
                .filter { dup -> dup.id != track.id && dup.id != track.duplicateOf }
                .map(::getWithGeometryInternal)
        return getLocationTrackDuplicatesBySplitPoints(track, geometry, duplicateTracksAndGeometries)
    }

    private fun getDuplicateTrackParent(
        layoutContext: LayoutContext,
        childTrack: LocationTrack,
    ): LocationTrackDuplicate? =
        childTrack.duplicateOf?.let { parentId ->
            getWithGeometry(layoutContext, parentId)?.let { (parentTrack, parentGeometry) ->
                val childGeometry = alignmentDao.fetch(childTrack.getVersionOrThrow())
                getDuplicateTrackParentStatus(parentTrack, parentGeometry, childTrack, childGeometry)
            }
        }

    @Transactional
    fun recalculateTopology(
        layoutContext: LayoutContext,
        changedTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
        switchId: IntId<LayoutSwitch>,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        val jointLocations: List<MultiPoint> = getTopologicallyLinkableJointLocations(changedTracks, switchId)
        return recalculateTopology(layoutContext, changedTracks, jointLocations)
    }

    @Transactional
    fun recalculateTopologies(
        layoutContext: LayoutContext,
        requests: List<TopologyRecalculationRequest>,
    ): List<List<Pair<LocationTrack, LocationTrackGeometry>>> {
        val changedTracksByRequestIx =
            requests.map { request ->
                request.changedTracks.map { (track, geometry) ->
                    val trackId =
                        requireNotNull(track.id as? IntId) { "A track must have a stored ID for node combining." }
                    track to geometry.withLocationTrackId(trackId)
                }
            }

        val dbNodeConnectionsByTargetIxByRequestIx =
            processFlattened(requests.map { it.jointLocations }) { target ->
                alignmentDao.getNodeConnectionsNearPoints(layoutContext, target, TOPOLOGY_CALC_DISTANCE)
            }

        val nearbyConnections =
            requests.mapIndexed { index, request ->
                val changedTracks = request.changedTracks
                val changedTrackIds = changedTracks.mapNotNull { (t, _) -> t.id as? IntId }.toSet()

                val dbConnectionsByTargetIx =
                    dbNodeConnectionsByTargetIxByRequestIx[index].map { targetConnections ->
                        targetConnections
                            .mapNotNull { c -> c.filterOut(changedTrackIds) }
                            .map { c -> NodeReplacementTarget(c.node, c.trackVersions.map(::getWithGeometry)) }
                    }

                val changedTrackConnectionsByTargetIx =
                    request.jointLocations.map { target ->
                        changedTracks.flatMap { (track, geometry) ->
                            geometry.nodesWithLocation
                                .filter { (_, location) -> target.isWithinDistance(location, TOPOLOGY_CALC_DISTANCE) }
                                .map { (node, _) -> NodeReplacementTarget(node, track, geometry) }
                        }
                    }

                dbConnectionsByTargetIx.zip(changedTrackConnectionsByTargetIx) { dbConnections, changedTrackConnections
                    ->
                    mergeNodeConnections(dbConnections + changedTrackConnections)
                }
            }

        return requests.mapIndexed { index, request ->
            recalculateTopology(nearbyConnections[index], changedTracksByRequestIx[index])
        }
    }

    @Transactional
    fun recalculateTopology(
        layoutContext: LayoutContext,
        changedTracksTmp: List<Pair<LocationTrack, LocationTrackGeometry>>,
        locations: List<MultiPoint>,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        val changedTracks =
            changedTracksTmp.map { (track, geometry) ->
                val trackId = requireNotNull(track.id as? IntId) { "A track must have a stored ID for node combining." }
                track to geometry.withLocationTrackId(trackId)
            }
        val request = TopologyRecalculationRequest(changedTracks, locations)
        return recalculateTopologies(layoutContext, listOf(request)).first()
    }

    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        return dao.fetchLocationTrackOwners()
    }

    fun getLocationTrackOwner(id: IntId<LocationTrackOwner>): LocationTrackOwner {
        return getLocationTrackOwners().find { owner -> owner.id == id }
            ?: throw NoSuchEntityException(LocationTrackOwner::class, id)
    }

    @Transactional(readOnly = true)
    fun getSplittingInitializationParameters(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
    ): SplittingInitializationParameters? {
        val getGeocodingContext = geocodingService.getLazyGeocodingContexts(layoutContext)
        return getWithGeometry(layoutContext, trackId)?.let { (locationTrack, geometry) ->
            val switches =
                getSwitchesForLocationTrack(layoutContext, trackId)
                    .mapNotNull { id -> switchDao.get(layoutContext, id) }
                    .mapNotNull { switch ->
                        switchLibraryService.getSwitchStructure(switch.switchStructureId).let { structure ->
                            val presentationJointLocation = switch.getJoint(structure.presentationJointNumber)?.location
                            if (presentationJointLocation != null) {
                                switch to presentationJointLocation
                            } else {
                                null
                            }
                        }
                    }
                    .map { (switch, location) ->
                        val address = getGeocodingContext(locationTrack.trackNumberId)?.getAddressAndM(location)
                        val mAlongAlignment = geometry.getClosestPointM(location)?.first
                        SwitchOnLocationTrack(
                            switch.id as IntId,
                            switch.nameParts,
                            switch.name,
                            address?.address,
                            location,
                            mAlongAlignment,
                            getNearestOperationalPoint(layoutContext, location),
                        )
                    }

            val duplicateTracks =
                getLocationTrackDuplicates(layoutContext, locationTrack, geometry).map { duplicate ->
                    SplitDuplicateTrack(
                        duplicate.id,
                        duplicate.nameStructure,
                        duplicate.descriptionStructure,
                        duplicate.name,
                        duplicate.length,
                        duplicate.duplicateStatus,
                    )
                }

            SplittingInitializationParameters(trackId, switches, duplicateTracks)
        }
    }

    private fun getNearestOperationalPoint(layoutContext: LayoutContext, location: Point) =
        operationalPointDao
            .fetchVersions(
                layoutContext,
                false,
                ids = null,
                locationBbox = boundingBoxAroundPoint(location, OPERATIONAL_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE),
            )
            .let(operationalPointDao::fetchMany)
            .filter { op ->
                op.raideType == OperationalPointRaideType.LPO || op.raideType == OperationalPointRaideType.LP
            }
            .minByOrNull { operatingPoint -> lineLength(requireNotNull(operatingPoint.location), location) }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<IntId<LayoutSwitch>> {
        return get(layoutContext, locationTrackId)?.switchIds ?: emptyList()
    }

    @Transactional(readOnly = true)
    fun getAlignmentsForTracks(tracks: List<LocationTrack>): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return tracks.map { track -> track to alignmentDao.fetch(track.getVersionOrThrow()) }
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<LocationTrack>,
    ): LayoutRowVersion<LocationTrack> {
        val (track, geometry) = fetchAndCheckForMerging(fromBranch, id)
        return dao.save(asMainDraft(track), geometry)
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LocationTrack>): Map<LayoutBranch, Oid<LocationTrack>> {
        return mapNonNullValues(locationTrackDao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }
    }

    @Transactional
    fun updateDependencies(
        branch: LayoutBranch,
        noUpdateLocationTracks: Set<IntId<LocationTrack>>,
        trackNumberId: IntId<LayoutTrackNumber>? = null,
        switchId: IntId<LayoutSwitch>? = null,
    ): List<LayoutRowVersion<LocationTrack>> =
        dao.fetchAffectedTrackDependencyVersions(branch.draft, trackNumberId, switchId).mapNotNull {
            (trackVersion, dependencyVersions) ->
            if (noUpdateLocationTracks.contains(trackVersion.id)) null
            else {
                val (track, trackGeometry) = getWithGeometryInternal(trackVersion)
                recalculateDependencies(track, dependencyVersions)
                    .takeIf { updatedTrack -> updatedTrack != track }
                    ?.let { updatedTrack -> saveDraftInternal(branch, updatedTrack, trackGeometry) }
            }
        }

    private fun recalculateDependencies(
        track: LocationTrack,
        versions: LocationTrackDependencyVersions,
    ): LocationTrack {
        val startSwitch = versions.startSwitchVersion?.let(switchDao::fetch)
        val endSwitch = versions.endSwitchVersion?.let(switchDao::fetch)
        val trackNumber = trackNumberDao.fetch(versions.trackNumberVersion)
        return recalculateDependencies(descriptionTranslation, track, trackNumber, startSwitch, endSwitch)
    }
}

fun getTopologicallyLinkableJointLocations(
    changedTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>,
): List<MultiPoint> =
    changedTracks
        .flatMap { (_, geometry) -> geometry.getSwitchLocations(switchId) }
        .filter { (link, _) -> link.jointRole != SwitchJointRole.MATH }
        .groupBy({ (link, _) -> link }, { (_, location) -> location.toPoint() })
        .map { (_, locations) -> MultiPoint(locations.distinct()) }

fun recalculateDependencies(
    translation: Translation,
    track: LocationTrack,
    trackNumber: LayoutTrackNumber,
    startSwitch: LayoutSwitch?,
    endSwitch: LayoutSwitch?,
): LocationTrack {
    val newName = track.nameStructure.reify(trackNumber, startSwitch, endSwitch)
    val newDescription = track.descriptionStructure.reify(translation, startSwitch, endSwitch)
    return track.takeIf { t -> newName == t.name && newDescription == t.description }
        ?: track.copy(name = newName, description = newDescription)
}

fun locationTrackWithGeometry(
    locationTrackDao: LocationTrackDao,
    alignmentDao: LayoutAlignmentDao,
    rowVersion: LayoutRowVersion<LocationTrack>,
) = locationTrackDao.fetch(rowVersion) to alignmentDao.fetch(rowVersion)

fun filterByBoundingBox(list: List<LocationTrack>, boundingBox: BoundingBox?): List<LocationTrack> =
    if (boundingBox != null) list.filter { t -> boundingBox.intersects(t.boundingBox) } else list

fun isSplitSourceReferenceError(exception: DataIntegrityViolationException): Boolean {
    val constraint = "split_source_location_track_fkey"
    val trackIsSplitSourceTrackError = "is still referenced from table \"split\""

    return (exception.cause as? PSQLException)?.serverErrorMessage.let { msg ->
        when {
            msg == null -> false
            msg.constraint == constraint && msg.detail?.contains(trackIsSplitSourceTrackError) == true -> true

            else -> false
        }
    }
}

data class TopologyRecalculationRequest(
    val changedTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    val jointLocations: List<MultiPoint>,
)

private fun recalculateTopology(
    nearbyConnections: List<List<NodeReplacementTarget>>,
    changedTracksTmp: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    val changedTracks =
        changedTracksTmp.map { (track, geometry) ->
            val trackId = requireNotNull(track.id as? IntId) { "A track must have a stored ID for node combining." }
            track to geometry.withLocationTrackId(trackId)
        }
    val combinations =
        nearbyConnections.map { connections -> resolveNodeCombinations(connections) }.let(::mergeNodeCombinations)

    // Include the replacements on changedTracks, even if they have the node in a different location
    // This also ensures that all argument tracks are also in the result list for easier saving
    val allTracks = (combinations.targetTracks + changedTracks).distinctBy { it.first.id }
    return allTracks.map { (track, geom) -> track to geom.withNodeReplacements(combinations.replacements) }
}
