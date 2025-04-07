package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
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
import fi.fta.geoviite.infra.linking.switches.TopologyLinkFindingSwitch
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.ratko.RatkoOperatingPointDao
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitDuplicateTrack
import fi.fta.geoviite.infra.split.SplittingInitializationParameters
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.DuplicateEndPointType.END
import fi.fta.geoviite.infra.tracklayout.DuplicateEndPointType.START
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.mapNonNullValues
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

const val TRACK_SEARCH_AREA_SIZE = 2.0
const val OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE = 1000.0

@GeoviiteService
class LocationTrackService(
    val locationTrackDao: LocationTrackDao,
    private val alignmentService: LayoutAlignmentService,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val splitDao: SplitDao,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
    private val localizationService: LocalizationService,
    private val transactionTemplate: TransactionTemplate,
) : LayoutAssetService<LocationTrack, LocationTrackGeometry, LocationTrackDao>(locationTrackDao) {

    @Transactional
    fun insert(branch: LayoutBranch, request: LocationTrackSaveRequest): LayoutRowVersion<LocationTrack> {
        val locationTrack =
            LocationTrack(
                name = request.name,
                descriptionBase = request.descriptionBase,
                descriptionSuffix = request.descriptionSuffix,
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                sourceId = null,
                length = 0.0,
                segmentCount = 0,
                boundingBox = null,
                duplicateOf = request.duplicateOf,
                topologicalConnectivity = request.topologicalConnectivity,
                topologyStartSwitch = null,
                topologyEndSwitch = null,
                ownerId = request.ownerId,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            )
        return saveDraft(branch, locationTrack, LocationTrackGeometry.empty)
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
                SplitSourceLocationTrackUpdateException(request.name, dataIntegrityException)
            } else {
                dataIntegrityException
            }
        }
    }

    private fun updateLocationTrackTransaction(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        request: LocationTrackSaveRequest,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalGeometry) = getWithGeometryOrThrow(branch.draft, id)
        val locationTrack =
            originalTrack.copy(
                name = request.name,
                descriptionBase = request.descriptionBase,
                descriptionSuffix = request.descriptionSuffix,
                type = request.type,
                state = request.state,
                trackNumberId = request.trackNumberId,
                duplicateOf = request.duplicateOf,
                topologicalConnectivity = request.topologicalConnectivity,
                ownerId = request.ownerId,
            )

        // TODO: GVT-2928 do we need to recalc topology? I think not, since:
        // a) the data on the track itself does not affect topology
        // b) we don't need to delete track-switch links on track delete, right?
        //   - they are now in the nodes, which don't maintain referential integrity: draft switch delete wont break it
        //   - deleted tracks can refer to whatever and that should be fine
        return saveDraft(branch, locationTrack, originalGeometry)
        //        return if (locationTrack.state != LocationTrackState.DELETED) {
        //            saveDraft(
        //                branch,
        //                fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack,
        // originalAlignment),
        //            )
        //        } else {
        //            clearDuplicateReferences(branch, id)
        //            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
        //            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
        //            val newTrack = fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack,
        // newAlignment)
        //            saveDraft(branch, newTrack, newAlignment)
        //        }
    }

    @Transactional
    fun updateState(
        branch: LayoutBranch,
        id: IntId<LocationTrack>,
        state: LocationTrackState,
    ): LayoutRowVersion<LocationTrack> {
        val (originalTrack, originalGeometry) = getWithGeometryOrThrow(branch.draft, id)
        val locationTrack = originalTrack.copy(state = state)
        // TODO: GVT-2928 do we need to recalc topology? I think not, (see above)
        return saveDraft(branch, locationTrack, originalGeometry)
        //
        //        return if (locationTrack.state != LocationTrackState.DELETED) {
        //            saveDraft(branch, locationTrack)
        //        } else {
        //            clearDuplicateReferences(branch, id)
        //            val segmentsWithoutSwitch = originalAlignment.segments.map(LayoutSegment::withoutSwitch)
        //            val newAlignment = originalAlignment.withSegments(segmentsWithoutSwitch)
        //            val newTrack = fetchNearbyTracksAndCalculateLocationTrackTopology(branch.draft, locationTrack,
        // newAlignment)
        //            saveDraft(branch, newTrack, newAlignment)
        //        }
    }

    @Transactional(readOnly = true)
    fun getStartAndEnd(
        context: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEnd<LocationTrack>> {
        val tracksAndAlignments = getManyWithGeometries(context, ids)
        val getGeocodingContext = geocodingService.getLazyGeocodingContexts(context)
        return tracksAndAlignments.map { (track, alignment) ->
            AlignmentStartAndEnd.of(track.id as IntId, alignment, getGeocodingContext(track.trackNumberId))
        }
    }

    @Transactional
    fun saveDraft(
        branch: LayoutBranch,
        track: LocationTrack,
        params: LocationTrackGeometry,
    ): LayoutRowVersion<LocationTrack> = saveDraftInternal(branch, asDraft(branch, track), params)

    // TODO: GVT-1926 Is this needed? temporarily private until we know
    //    @Transactional
    private fun saveDraft(branch: LayoutBranch, draftAsset: LocationTrack): LayoutRowVersion<LocationTrack> =
        saveDraft(
            branch,
            draftAsset,
            getSourceVersion(draftAsset)?.let(alignmentDao::fetch) ?: LocationTrackGeometry.empty,
        )

    private fun getSourceVersion(track: LocationTrack): LayoutRowVersion<LocationTrack>? =
        track.contextData.layoutAssetId.let { id ->
            when (id) {
                // Edited from a row that is already a draft
                is StoredAssetId -> id.version
                // Edited by creating the draft from another context
                is EditedAssetId -> id.sourceRowVersion
                // Completely new item without geometry
                is TemporaryAssetId -> null
                is IdentifiedAssetId -> error("Unexpected layout asset ID type when saving location track: $id")
            }
        }

    //    @Transactional
    //    fun saveDraft(
    //        branch: LayoutBranch,
    //        draftAsset: LocationTrack,
    //        geometry: LocationTrackGeometry,
    //    ): LayoutRowVersion<LocationTrack> {
    //        return saveDraftInternal(branch, draftAsset, geometry)
    //    }

    //    @Deprecated("")
    //    @Transactional
    //    fun saveDraft(
    //        branch: LayoutBranch,
    //        draftAsset: LocationTrack,
    //        alignment: LayoutAlignment,
    //    ): LayoutRowVersion<LocationTrack> {
    //        val alignmentVersion =
    //            // If we're creating a new row or starting a draft, we duplicate the alignment to not
    //            // edit any original
    //            if (draftAsset.dataType == TEMP || draftAsset.isOfficial) {
    //                alignmentService.saveAsNew(alignment)
    //            }
    //            // Ensure that we update the correct one.
    //            else if (draftAsset.getAlignmentVersionOrThrow().id != alignment.id) {
    //                alignmentService.save(
    //                    alignment.copy(id = draftAsset.getAlignmentVersionOrThrow().id, dataType = STORED)
    //                )
    //            } else {
    //                alignmentService.save(alignment)
    //            }
    //        return saveDraftInternal(branch, draftAsset.copy(alignmentVersion = alignmentVersion))
    //    }
    //
    @Transactional
    fun insertExternalId(branch: LayoutBranch, id: IntId<LocationTrack>, oid: Oid<LocationTrack>) =
        dao.insertExternalId(id, branch, oid)

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<LocationTrack>,
    ): PublicationResultVersions<LocationTrack> {
        val publishedVersion = publishInternal(branch, version)
        // Some of the versions may get deleted in publication -> delete any alignments they left
        // behind
        alignmentDao.deleteOrphanedAlignments()
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LocationTrack>): LayoutRowVersion<LocationTrack> {
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearDuplicateReferences(branch, id)
        }
        return super.deleteDraft(branch, id)
    }

    @Transactional
    fun fetchDuplicates(layoutContext: LayoutContext, id: IntId<LocationTrack>): List<LocationTrack> {
        return dao.fetchDuplicateVersions(layoutContext, id).map(dao::fetch)
    }

    @Transactional
    fun clearDuplicateReferences(branch: LayoutBranch, id: IntId<LocationTrack>) =
        dao.fetchDuplicateVersions(branch.draft, id, includeDeleted = true)
            .map { version -> asDraft(branch, dao.fetch(version)) to alignmentDao.fetch(version) }
            .forEach { (dup, geom) -> saveDraft(branch, dup.copy(duplicateOf = null), geom) }

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
        possibleIds: List<IntId<LocationTrack>>? = null,
    ): ((term: String, item: LocationTrack) -> Boolean) =
        dao.fetchExternalIds(layoutContext.branch, possibleIds).let { externalIds ->
            return { term, item -> externalIds[item.id]?.oid?.toString() == term || item.id.toString() == term }
        }

    override fun contentMatches(term: String, item: LocationTrack) =
        item.exists && (item.name.contains(term, true) || item.descriptionBase.contains(term, true))

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
        return if (boundingBox == null) {
                dao.list(layoutContext, includeDeleted, trackNumberId)
            } else {
                dao.fetchVersionsNear(layoutContext, boundingBox, includeDeleted, trackNumberId, minLength)
                    .map(dao::fetch)
            }
            .let { list ->
                if (locationTrackIds == null) list
                else
                    list.filter { locationTrack -> locationTrackIds.contains(locationTrack.id as IntId<LocationTrack>) }
            }
            .let { list -> filterByBoundingBox(list, boundingBox) }
            .let(::associateWithGeometries)
    }

    @Transactional(readOnly = true)
    fun getManyWithGeometries(
        layoutContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return dao.getMany(layoutContext, ids).let(::associateWithGeometries)
    }

    //    @Deprecated("")
    //    @Transactional(readOnly = true)
    //    fun getManyWithAlignments(
    //        layoutContext: LayoutContext,
    //        ids: List<IntId<LocationTrack>>,
    //    ): List<Pair<LocationTrack, LayoutAlignment>> {
    //        return dao.getMany(layoutContext, ids).let(::associateWithAlignments)
    //    }
    //
    @Transactional(readOnly = true)
    fun getWithGeometryOrThrow(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, DbLocationTrackGeometry> {
        return getWithGeometryInternalOrThrow(layoutContext, id)
    }

    //    @Deprecated("")
    //    @Transactional(readOnly = true)
    //    fun getWithAlignmentOrThrow(
    //        layoutContext: LayoutContext,
    //        id: IntId<LocationTrack>,
    //    ): Pair<LocationTrack, LayoutAlignment> {
    //        return getWithAlignmentInternalOrThrow(layoutContext, id)
    //    }
    //
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
    ): AddressPoint? {
        val locationTrackAndAlignment = getWithGeometry(layoutContext, locationTrackId)
        return locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getTrackLocation(layoutContext, locationTrack, alignment, address)
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
    fun getWithGeometry(version: LayoutRowVersion<LocationTrack>): Pair<LocationTrack, DbLocationTrackGeometry> {
        return getWithGeometryInternal(version)
    }

    @Transactional(readOnly = true)
    fun listNearWithGeometries(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> =
        dao.listNear(layoutContext, bbox).let(::associateWithGeometries).filter { (_, alignment) ->
            alignment.segments.any { segment ->
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
    ): List<AlignmentPlanSection> {
        val locationTrack = get(layoutContext, locationTrackId)
        val geocodingContext =
            locationTrack?.let { geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId) }

        return geocodingContext?.let { context ->
            alignmentService.getGeometryMetadataSections(
                locationTrack.versionOrThrow,
                dao.fetchExternalId(layoutContext.branch, locationTrackId)?.oid,
                boundingBox,
                context,
            )
        } ?: listOf()
    }

    @Transactional(readOnly = true)
    fun getFullDescriptions(
        layoutContext: LayoutContext,
        locationTracks: List<LocationTrack>,
        lang: LocalizationLanguage,
    ): List<FreeText> {
        val startAndEndSwitchIds =
            locationTracks.map { locationTrack ->
                alignmentDao.fetch(locationTrack.versionOrThrow).let { geom ->
                    geom.startSwitchLink?.id to geom.endSwitchLink?.id
                }
            }
        val switches =
            switchDao
                .getMany(layoutContext, startAndEndSwitchIds.flatMap { listOfNotNull(it.first, it.second) })
                .associateBy { switch -> switch.id }

        fun getSwitchShortName(switchId: IntId<LayoutSwitch>) = switches[switchId]?.shortName
        val translation = localizationService.getLocalization(lang)

        return locationTracks.zip(startAndEndSwitchIds) { locationTrack, startAndEndSwitch ->
            val startSwitchName = startAndEndSwitch.first?.let(::getSwitchShortName)
            val endSwitchName = startAndEndSwitch.second?.let(::getSwitchShortName)

            when (locationTrack.descriptionSuffix) {
                LocationTrackDescriptionSuffix.NONE -> FreeText(locationTrack.descriptionBase.toString())

                LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                    FreeText(
                        "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.buffer")}"
                    )

                LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                    FreeText("${locationTrack.descriptionBase} ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}")

                LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                    FreeText(
                        "${locationTrack.descriptionBase} ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}"
                    )
            }
        }
    }

    fun getFullDescription(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        lang: LocalizationLanguage,
    ): FreeText = getFullDescriptions(layoutContext, listOf(locationTrack), lang).first()

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
        lines: List<LocationTrack>
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        // This is a little convoluted to avoid extra passes of transaction annotation handling in
        // alignmentDao.fetch
        val alignments = alignmentDao.getMany(lines.map(LocationTrack::versionOrThrow))
        return lines.map { line -> line to alignments.getValue(line.versionOrThrow) }
    }

    fun fillTrackAddress(splitPoint: SplitPoint, geocodingContext: GeocodingContext): SplitPoint {
        val address = geocodingContext.getAddress(splitPoint.location)?.first
        return when (splitPoint) {
            is SwitchSplitPoint -> splitPoint.copy(address = address)
            is EndpointSplitPoint -> splitPoint.copy(address = address)
        }
    }

    fun fillTrackAddresses(
        duplicates: List<LocationTrackDuplicate>,
        geocodingContext: GeocodingContext,
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

            val startSwitch = endSwitchLink?.id?.let { id -> fetchSwitchAtEndById(layoutContext, id) }
            val endSwitch = endSwitchLink?.id?.let { id -> fetchSwitchAtEndById(layoutContext, id) }

            val partOfUnfinishedSplit =
                splitDao.locationTracksPartOfAnyUnfinishedSplit(layoutContext.branch, listOf(id)).isNotEmpty()

            LocationTrackInfoboxExtras(
                duplicateOf,
                duplicates,
                startSwitch,
                endSwitch,
                partOfUnfinishedSplit,
                startSplitPoint,
                endSplitPoint,
            )
        }
    }

    private fun createSplitPoint(
        point: AlignmentPoint?,
        switchId: IntId<LayoutSwitch>?,
        endPointType: DuplicateEndPointType,
        geocodingContext: GeocodingContext?,
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
        (locationTrack.switchIds + switchDao.findSwitchesNearTrack(branch, locationTrack.versionOrThrow))
            .distinct()
            .size

    @Transactional(readOnly = true)
    fun getLocationTrackDuplicates(
        layoutContext: LayoutContext,
        track: LocationTrack,
        alignment: LocationTrackGeometry,
    ): List<LocationTrackDuplicate> {
        val markedDuplicateVersions = dao.fetchDuplicateVersions(layoutContext, track.id as IntId)
        val tracksLinkedThroughSwitch =
            switchDao
                .findLocationTracksLinkedToSwitches(layoutContext, track.switchIds)
                .values
                .flatten()
                .map(LayoutSwitchDao.LocationTrackIdentifiers::rowVersion)
        val duplicateTracksAndAlignments =
            (markedDuplicateVersions + tracksLinkedThroughSwitch).distinct().map(::getWithGeometryInternal).filter {
                (duplicateTrack, _) ->
                duplicateTrack.id != track.id && duplicateTrack.id != track.duplicateOf
            }
        return getLocationTrackDuplicatesBySplitPoints(track, alignment, duplicateTracksAndAlignments)
    }

    private fun getDuplicateTrackParent(
        layoutContext: LayoutContext,
        childTrack: LocationTrack,
    ): LocationTrackDuplicate? =
        childTrack.duplicateOf?.let { parentId ->
            getWithGeometry(layoutContext, parentId)?.let { (parentTrack, parentTrackAlignment) ->
                val childAlignment = alignmentDao.fetch(childTrack.versionOrThrow)
                getDuplicateTrackParentStatus(parentTrack, parentTrackAlignment, childTrack, childAlignment)
            }
        }

    private fun fetchSwitchAtEndById(layoutContext: LayoutContext, id: IntId<LayoutSwitch>): LayoutSwitchIdAndName? =
        switchDao.get(layoutContext, id)?.let { switch -> LayoutSwitchIdAndName(id, switch.name) }

    @Transactional
    fun recalculateTopology(switch: LayoutSwitch, layoutContext: LayoutContext): List<LayoutRowVersion<LocationTrack>> {
        val jointLocations = switch.joints.filter { j -> j.role != SwitchJointRole.MATH }.map { j -> j.location }
        return recalculateTopology(jointLocations, layoutContext)
    }

    @Transactional
    fun recalculateTopology(
        locations: List<IPoint>,
        layoutContext: LayoutContext,
    ): List<LayoutRowVersion<LocationTrack>> =
        locations
            // Combine the nodes in each location separately
            .map { location -> resolveNodeReplacementsAt(location, layoutContext) }
            // Combine all node-swaps into a single map to generate only one change per track
            .let { replacements ->
                replacements
                    .flatMap { map -> map.entries }
                    .associate { e -> e.toPair() }
                    // TODO: GVT-2928 Do we need to worry about the same node being found replaced multiple times?
                    .also { combined -> require(replacements.sumOf { r -> r.size } == combined.size) }
            }
            .let(::replaceNodesOnTracks)
            .map { (newTrack, newGeometry) -> dao.save(newTrack, newGeometry) }

    fun resolveNodeReplacementsAt(location: IPoint, layoutContext: LayoutContext): Map<NodeConnection, LayoutNode> {
        val connections = alignmentDao.getNodeConnectionsNear(location, layoutContext)
        val replacements = combineEligibleNodes(connections.map { c -> c.node })
        return connections
            .mapNotNull { connection ->
                replacements[connection.node]?.let { replacement -> connection to replacement }
            }
            .associate { it }
    }

    fun replaceNodesOnTracks(
        reconnected: Map<NodeConnection, LayoutNode>
    ): List<Pair<LocationTrack, LocationTrackGeometry>> =
        // Resolve by-track to handle cases where multiple nodes are swapped at once
        reconnected
            .flatMap { (c, _) -> c.trackVersions }
            .distinct()
            .map { version ->
                val (track, geometry) = getWithGeometry(version)
                val nodeChanges: Map<LayoutNode, LayoutNode> =
                    reconnected
                        .filter { (connection, _) -> connection.trackVersions.contains(version) }
                        .map { (connection, replacementNode) -> connection.node to replacementNode }
                        .associate { it }
                val newGeometry = geometry.withCombinationNodes(nodeChanges)
                track to newGeometry
            }

    private fun fetchNearbyLocationTracksWithGeometries(
        layoutContext: LayoutContext,
        targetPoint: LayoutPoint,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        return dao.fetchVersionsNear(layoutContext, boundingBoxAroundPoint(targetPoint, 1.0))
            .map { version -> getWithGeometryInternal(version) }
            .filter { (track, geometry) -> geometry.isNotEmpty && track.exists }
    }

    //    private fun fetchNearbyLocationTracksWithAlignments(
    //        layoutContext: LayoutContext,
    //        targetPoint: LayoutPoint,
    //    ): List<Pair<LocationTrack, LayoutAlignment>> {
    //        return dao.fetchVersionsNear(layoutContext, boundingBoxAroundPoint(targetPoint, 1.0))
    //            .map { version -> getWithAlignmentInternal(version) }
    //            .filter { (track, alignment) -> alignment.segments.isNotEmpty() && track.exists }
    //    }

    //    // TODO: GVT-2928 Topology calculation in node-edge model
    //    // This should only check for topology links: switch-links for continuing tracks at start & end
    //    // In the new model, that is written into the locationtrackgeometry first/last node
    //    @Deprecated("Topology calculation must change due to node model")
    //    @Transactional
    //    fun fetchNearbyTracksAndCalculateLocationTrackTopology(
    //        layoutContext: LayoutContext,
    //        track: LocationTrack,
    //        alignment: LayoutAlignment,
    //        startChanged: Boolean = false,
    //        endChanged: Boolean = false,
    //    ): LocationTrack {
    //        val nearbyTracksAroundStart =
    //            alignment.start
    //                ?.let { point -> fetchNearbyLocationTracksWithAlignments(layoutContext, point) }
    //                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id } ?: listOf()
    //        val nearbyTracksAroundEnd =
    //            alignment.end
    //                ?.let { point -> fetchNearbyLocationTracksWithAlignments(layoutContext, point) }
    //                ?.filter { (nearbyLocationTrack, _) -> nearbyLocationTrack.id != track.id } ?: listOf()
    //        return calculateLocationTrackTopology(
    //            track = track,
    //            alignment = alignment,
    //            startChanged = startChanged,
    //            endChanged = endChanged,
    //            nearbyTracks = NearbyTracks(aroundStart = nearbyTracksAroundStart, aroundEnd = nearbyTracksAroundEnd),
    //        )
    //    }
    //
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
        return getWithGeometry(layoutContext, trackId)?.let { (locationTrack, alignment) ->
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
                        val mAlongAlignment = alignment.getClosestPointM(location)?.first
                        SwitchOnLocationTrack(
                            switch.id as IntId,
                            switch.name,
                            address?.address,
                            location,
                            mAlongAlignment,
                            getNearestOperatingPoint(location),
                        )
                    }

            val duplicateTracks =
                getLocationTrackDuplicates(layoutContext, locationTrack, alignment).map { duplicate ->
                    SplitDuplicateTrack(duplicate.id, duplicate.name, duplicate.length, duplicate.duplicateStatus)
                }

            SplittingInitializationParameters(trackId, switches, duplicateTracks)
        }
    }

    private fun getNearestOperatingPoint(location: Point) =
        ratkoOperatingPointDao
            .getOperatingPoints(boundingBoxAroundPoint(location, OPERATING_POINT_AROUND_SWITCH_SEARCH_AREA_SIZE))
            .filter { op -> op.type == OperationalPointType.LPO || op.type == OperationalPointType.LP }
            .minByOrNull { operatingPoint -> lineLength(operatingPoint.location, location) }

    @Transactional(readOnly = true)
    fun getSwitchesForLocationTrack(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<IntId<LayoutSwitch>> {
        return get(layoutContext, locationTrackId)?.switchIds ?: emptyList()
    }

    @Transactional(readOnly = true)
    fun getAlignmentsForTracks(tracks: List<LocationTrack>): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        return tracks.map { track -> track to alignmentDao.fetch(track.versionOrThrow) }
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<LocationTrack>,
    ): LayoutRowVersion<LocationTrack> {
        val (track, geometry) = fetchAndCheckForMerging(fromBranch, id)
        return dao.save(asMainDraft(track), geometry)
        //        .also { savedVersion ->
        //            alignmentDao.copyLocationTrackGeometry(track.versionOrThrow, savedVersion)
        //        }
        //        return dao.save(
        //            asMainDraft(track.copy(alignmentVersion =
        // alignmentService.duplicate(track.getAlignmentVersionOrThrow())))
        //        )
    }

    //    override fun cancelInternal(asset: LocationTrack, designBranch: DesignBranch) =
    //        cancelled(
    //            asset.copy(alignmentVersion = alignmentService.duplicate(asset.getAlignmentVersionOrThrow())),
    //            designBranch.designId,
    //        )

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LocationTrack>): Map<LayoutBranch, Oid<LocationTrack>> {
        return mapNonNullValues(locationTrackDao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }
    }
}

@Deprecated("use track.switchIds")
fun collectAllSwitches(locationTrack: LocationTrack, alignment: LayoutAlignment): List<IntId<LayoutSwitch>> {
    val topologySwitches =
        listOfNotNull(locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId)
    val segmentSwitches = alignment.segments.mapNotNull { segment -> segment.switchId }
    return (topologySwitches + segmentSwitches).distinct()
}

data class NearbyTracks(
    val aroundStart: List<Pair<LocationTrack, LayoutAlignment>>,
    val aroundEnd: List<Pair<LocationTrack, LayoutAlignment>>,
)

@Deprecated("Topology calculation must change due to node model")
fun calculateLocationTrackTopology(
    track: LocationTrack,
    alignment: LayoutAlignment,
    startChanged: Boolean = false,
    endChanged: Boolean = false,
    nearbyTracks: NearbyTracks,
    newSwitch: TopologyLinkFindingSwitch? = null,
): LocationTrack {
    val startPoint = alignment.firstSegmentStart
    val endPoint = alignment.lastSegmentEnd
    val ownSwitches = alignment.segments.mapNotNull { segment -> segment.switchId }.toSet()

    val startSwitch =
        if (!track.exists || startPoint == null) null
        else if (startChanged) {
            findBestTopologySwitchMatch(startPoint, ownSwitches, nearbyTracks.aroundStart, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                startPoint,
                ownSwitches,
                nearbyTracks.aroundStart,
                track.topologyStartSwitch,
                newSwitch,
            )
        }

    val endSwitch =
        if (!track.exists || endPoint == null) {
            null
        } else if (endChanged) {
            findBestTopologySwitchMatch(endPoint, ownSwitches, nearbyTracks.aroundEnd, null, newSwitch)
        } else {
            findBestTopologySwitchMatch(
                endPoint,
                ownSwitches,
                nearbyTracks.aroundEnd,
                track.topologyEndSwitch,
                newSwitch,
            )
        }

    return if (track.topologyStartSwitch == startSwitch && track.topologyEndSwitch == endSwitch) {
        track
    } else if (startSwitch?.switchId != null && startSwitch.switchId == endSwitch?.switchId) {
        // Remove topology links if both ends would connect to the same switch.
        // In this case, the alignment should be part of the internal switch geometry
        track.copy(topologyStartSwitch = null, topologyEndSwitch = null)
    } else {
        track.copy(topologyStartSwitch = startSwitch, topologyEndSwitch = endSwitch)
    }
}

@Deprecated("")
fun findBestTopologySwitchMatch(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracksForSearch: List<Pair<LocationTrack, LayoutAlignment>>,
    currentTopologySwitch: TopologyLocationTrackSwitch?,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? {
    val defaultSwitch =
        if (currentTopologySwitch?.switchId?.let(ownSwitches::contains) != false) {
            null
        } else {
            currentTopologySwitch
        }
    return findBestTopologySwitchFromSegments(target, ownSwitches, nearbyTracksForSearch, newSwitch)
        ?: defaultSwitch
        ?: findBestTopologySwitchFromOtherTopology(target, ownSwitches, nearbyTracksForSearch)
}

private fun findBestTopologySwitchFromSegments(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    newSwitch: TopologyLinkFindingSwitch?,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (_, otherAlignment) ->
            otherAlignment.segments.flatMap { segment ->
                if (
                    segment.switchId !is IntId ||
                        ownSwitches.contains(segment.switchId) ||
                        segment.switchId == newSwitch?.id
                ) {
                    listOf()
                } else {
                    listOfNotNull(
                        segment.startJointNumber?.let { number ->
                            pickIfClose(segment.switchId, number, target, segment.segmentStart)
                        },
                        segment.endJointNumber?.let { number ->
                            pickIfClose(segment.switchId, number, target, segment.segmentEnd)
                        },
                    )
                }
            } +
                (newSwitch?.joints?.mapNotNull { sj -> pickIfClose(newSwitch.id, sj.number, target, sj.location) }
                    ?: listOf())
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun findBestTopologySwitchFromOtherTopology(
    target: IPoint,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
    nearbyTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): TopologyLocationTrackSwitch? =
    nearbyTracks
        .flatMap { (otherTrack, otherAlignment) ->
            listOfNotNull(
                pickIfClose(otherTrack.topologyStartSwitch, target, otherAlignment.firstSegmentStart, ownSwitches),
                pickIfClose(otherTrack.topologyEndSwitch, target, otherAlignment.lastSegmentEnd, ownSwitches),
            )
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

private fun pickIfClose(switchId: IntId<LayoutSwitch>, number: JointNumber, target: IPoint, reference: IPoint?) =
    pickIfClose(TopologyLocationTrackSwitch(switchId, number), target, reference, setOf())

private fun pickIfClose(
    topologyMatch: TopologyLocationTrackSwitch?,
    target: IPoint,
    reference: IPoint?,
    ownSwitches: Set<DomainId<LayoutSwitch>>,
): Pair<TopologyLocationTrackSwitch, Double>? =
    if (reference != null && topologyMatch != null && !ownSwitches.contains(topologyMatch.switchId)) {
        val distance = lineLength(target, reference)
        if (distance < 1.0) topologyMatch to distance else null
    } else {
        null
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
