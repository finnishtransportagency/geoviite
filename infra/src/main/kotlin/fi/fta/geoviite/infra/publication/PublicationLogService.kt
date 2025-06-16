package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.integration.RatkoPush
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitHeader
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackNumberAndChangeTime
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsFirstComparator
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

const val DISTANCE_CHANGE_THRESHOLD = 0.0005

@GeoviiteService
class PublicationLogService
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val ratkoPushDao: RatkoPushDao,
    private val splitService: SplitService,
    private val localizationService: LocalizationService,
    private val geographyService: GeographyService,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val layoutAlignmentDao: LayoutAlignmentDao,
    private val layoutSwitchDao: LayoutSwitchDao,
) {

    @Transactional(readOnly = true)
    fun fetchPublications(layoutBranch: LayoutBranch, from: Instant? = null, to: Instant? = null): List<Publication> {
        return publicationDao.fetchPublicationsBetween(layoutBranch, from, to)
    }

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        val publication = publicationDao.getPublication(id)
        val ratkoStatus = ratkoPushDao.getRatkoStatus(id).sortedByDescending { it.endTime }.firstOrNull()

        val publishedReferenceLines = publicationDao.fetchPublishedReferenceLines(id)
        val publishedKmPosts = publicationDao.fetchPublishedKmPosts(id)
        val (publishedDirectTrackNumbers, publishedIndirectTrackNumbers) = publicationDao.fetchPublishedTrackNumbers(id)
        val (publishedDirectTracks, publishedIndirectTracks) = publicationDao.fetchPublishedLocationTracks(id)
        val (publishedDirectSwitches, publishedIndirectSwitches) = publicationDao.fetchPublishedSwitches(id)
        val split = splitService.getSplitIdByPublicationId(id)?.let(splitService::get)

        return PublicationDetails(
            publication,
            trackNumbers = publishedDirectTrackNumbers,
            referenceLines = publishedReferenceLines,
            locationTracks = publishedDirectTracks,
            switches = publishedDirectSwitches,
            kmPosts = publishedKmPosts,
            ratkoPushStatus = ratkoStatus?.status,
            ratkoPushTime = ratkoStatus?.endTime,
            indirectChanges =
                PublishedIndirectChanges(
                    trackNumbers = publishedIndirectTrackNumbers,
                    locationTracks = publishedIndirectTracks,
                    switches = publishedIndirectSwitches,
                ),
            split = split?.let(::SplitHeader),
        )
    }

    @Transactional(readOnly = true)
    fun getPublicationDetailsAsTableItems(
        id: IntId<Publication>,
        translation: Translation,
    ): List<PublicationTableItem> {
        val geocodingContextCache =
            ConcurrentHashMap<
                Instant,
                MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext<ReferenceLineM>>>,
            >()
        return getPublicationDetails(id).let { publication ->
            val previousPublication =
                publicationDao
                    .fetchPublicationTimes(publication.layoutBranch.branch)
                    .entries
                    .sortedByDescending { it.key }
                    .find { it.key < publication.publicationTime }
            mapToPublicationTableItems(
                translation,
                publication = publication,
                versions = ,
                switchLinkChanges = publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(publication.id),
                previousComparisonTime = previousPublication?.key ?: publication.publicationTime.minusMillis(1),
                geocodingContextGetter = { trackNumberId: IntId<LayoutTrackNumber>, timestamp: Instant ->
                    getOrPutGeocodingContext(
                        geocodingContextCache,
                        publication.layoutBranch.branch,
                        trackNumberId,
                        timestamp,
                    )
                },
            )
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationDetailsBetweenInstants(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
    ): List<PublicationDetails> {
        return publicationDao.fetchPublicationsBetween(layoutBranch, from, to).map { getPublicationDetails(it.id) }
    }

    @Transactional(readOnly = true)
    fun fetchLatestPublicationDetails(branchType: LayoutBranchType, count: Int): Page<PublicationDetails> =
        publicationDao.list(branchType).let {
            Page(it.size, it.take(count), 0).map { item -> getPublicationDetails(item.id) }
        }

    @Transactional(readOnly = true)
    fun fetchPublicationDetails(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        translation: Translation,
    ): List<PublicationTableItem> {
        val switchLinkChanges =
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(null, layoutBranch, from, to)

        return fetchPublicationDetailsBetweenInstants(layoutBranch, from, to)
            .sortedBy { it.publicationTime }
            .let { publications ->
                val geocodingContextCache =
                    ConcurrentHashMap<
                        Instant,
                        MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext<ReferenceLineM>>>,
                    >()
                val trackNumbersCache = trackNumberDao.fetchTrackNumberNames()
                val getGeocodingContextOrNull = { trackNumberId: IntId<LayoutTrackNumber>, timestamp: Instant ->
                    getOrPutGeocodingContext(geocodingContextCache, layoutBranch, trackNumberId, timestamp)
                }

                publications
                    .mapIndexed { index, publicationDetails ->
                        val previousPublication = publications.getOrNull(index - 1)
                        publicationDetails to
                            (previousPublication?.publicationTime ?: publicationDetails.publicationTime.minusMillis(1))
                    }
                    .flatMap { (publicationDetails, timeDiff) ->
                        mapToPublicationTableItems(
                            translation,
                            publicationDetails,
                            switchLinkChanges[publicationDetails.id] ?: mapOf(),
                            timeDiff,
                            getGeocodingContextOrNull,
                            trackNumbersCache,
                        )
                    }
            }
            .let { publications ->
                if (sortBy == null) publications else publications.sortedWith(getComparator(sortBy, order))
            }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublication(id: IntId<Publication>): SplitInPublication? {
        return publicationDao.getPublication(id).let { publication ->
            splitService.getSplitIdByPublicationId(id)?.let { splitId ->
                val split = splitService.getOrThrow(splitId)
                val (sourceLocationTrack, sourceGeometry) =
                    locationTrackService.getWithGeometry(split.sourceLocationTrackVersion)
                val oid =
                    requireNotNull(
                        locationTrackDao
                            .fetchExternalId(publication.layoutBranch.branch, sourceLocationTrack.id as IntId)
                            ?.oid
                    ) {
                        "expected to find oid for published location track ${sourceLocationTrack.id} in publication $id"
                    }
                val targetLocationTracks =
                    publicationDao
                        .fetchPublishedLocationTracks(id)
                        .let { changes -> (changes.indirectChanges + changes.directChanges).map { c -> c.version } }
                        .distinct()
                        .mapNotNull { v ->
                            createSplitTargetInPublication(
                                sourceGeometry = sourceGeometry,
                                rowVersion = v,
                                publicationBranch = publication.layoutBranch.branch,
                                publicationTime = publication.publicationTime,
                                split = split,
                            )
                        }
                        .sortedWith { a, b -> nullsFirstComparator(a.startAddress, b.startAddress) }
                SplitInPublication(
                    id = publication.id,
                    splitId = split.id,
                    locationTrack = sourceLocationTrack,
                    locationTrackOid = oid,
                    targetLocationTracks = targetLocationTracks,
                )
            }
        }
    }

    private fun createSplitTargetInPublication(
        sourceGeometry: LocationTrackGeometry,
        rowVersion: LayoutRowVersion<LocationTrack>,
        publicationBranch: LayoutBranch,
        publicationTime: Instant,
        split: Split,
    ): SplitTargetInPublication? {
        val (track, geometry) = locationTrackService.getWithGeometry(rowVersion)
        return split.getTargetLocationTrack(track.id as IntId)?.let { target ->
            val ctx =
                requireNotNull(
                    geocodingService.getGeocodingContextAtMoment(
                        publicationBranch,
                        track.trackNumberId,
                        publicationTime,
                    )
                )

            val (sourceStart, sourceEnd) = sourceGeometry.getEdgeStartAndEnd(target.edgeIndices)
            val startBySegments = requireNotNull(ctx.getAddress(sourceStart)).first
            val endBySegments = requireNotNull(ctx.getAddress(sourceEnd)).first
            val startByTarget = requireNotNull(geometry.start?.let { point -> ctx.getAddress(point)?.first })
            val endByTarget = requireNotNull(geometry.end?.let { point -> ctx.getAddress(point)?.first })
            val startAddress = listOf(startBySegments, startByTarget).maxOrNull()
            val endAddress = listOf(endBySegments, endByTarget).minOrNull()

            return SplitTargetInPublication(
                id = track.id,
                name = track.name,
                oid = locationTrackDao.fetchExternalId(publicationBranch, track.id)?.oid,
                startAddress = startAddress,
                endAddress = endAddress,
                operation = target.operation,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublicationCsv(id: IntId<Publication>, lang: LocalizationLanguage): Pair<String, AlignmentName?> {
        return getSplitInPublication(id).let { splitInPublication ->
            val data = splitInPublication?.targetLocationTracks?.map { lt -> splitInPublication to lt } ?: emptyList()
            printCsv(splitCsvColumns(localizationService.getLocalization(lang)), data) to
                splitInPublication?.locationTrack?.name
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationsAsCsv(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        timeZone: ZoneId? = null,
        translation: Translation,
    ): String {
        val orderedPublishedItems =
            fetchPublicationDetails(
                layoutBranch = layoutBranch,
                from = from,
                to = to,
                sortBy = sortBy,
                order = order,
                translation = translation,
            )

        return asCsvFile(orderedPublishedItems, timeZone ?: ZoneId.of("UTC"), translation)
    }

    fun diffTrackNumber(
        translation: Translation,
        trackNumberChanges: TrackNumberChange,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    ): List<PublicationChange<*>> {
        val oldEndAddress =
            trackNumberChanges.endPoint.old?.let { point ->
                geocodingContextGetter(trackNumberChanges.id, oldTimestamp)?.getAddress(point)?.first
            }
        val newEndAddress =
            trackNumberChanges.endPoint.new?.let { point ->
                geocodingContextGetter(trackNumberChanges.id, newTimestamp)?.getAddress(point)?.first
            }

        return listOfNotNull(
            compareChangeValues(trackNumberChanges.trackNumber, { it }, PropKey("track-number")),
            compareChangeValues(trackNumberChanges.state, { it }, PropKey("state"), null, "LayoutState"),
            compareChangeValues(trackNumberChanges.description, { it }, PropKey("description")),
            compareChangeValues(
                trackNumberChanges.startAddress,
                { it.toString() },
                PropKey("start-address"),
                remark =
                    getAddressMovedRemarkOrNull(
                        translation,
                        trackNumberChanges.startAddress.old,
                        trackNumberChanges.startAddress.new,
                    ),
            ),
            compareChange(
                { oldEndAddress != newEndAddress },
                oldEndAddress,
                newEndAddress,
                { it.toString() },
                PropKey("end-address"),
                remark = getAddressMovedRemarkOrNull(translation, oldEndAddress, newEndAddress),
            ),
        )
    }

    fun diffLocationTrack(
        translation: Translation,
        locationTrackChanges: LocationTrackChange,
        switchLinkChanges: LocationTrackPublicationSwitchLinkChanges?,
        branch: LayoutBranch,
        publicationTime: Instant,
        previousPublicationTime: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        changedKmNumbers: Set<KmNumber>,
        getGeocodingContext: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    ): List<PublicationChange<*>> {
        val oldAndTime = locationTrackChanges.duplicateOf.old to previousPublicationTime
        val newAndTime = locationTrackChanges.duplicateOf.new to publicationTime
        val oldStartPointAndM =
            locationTrackChanges.startPoint.old?.let { oldStart ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldStart)
                }
            }
        val oldEndPointAndM =
            locationTrackChanges.endPoint.old?.let { oldEnd ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldEnd)
                }
            }
        val newStartPointAndM =
            locationTrackChanges.startPoint.new?.let { newStart ->
                locationTrackChanges.trackNumberId.new?.let {
                    getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newStart)
                }
            }
        val newEndPointAndM =
            locationTrackChanges.endPoint.new?.let { newEnd ->
                locationTrackChanges.trackNumberId.new?.let {
                    getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newEnd)
                }
            }

        return listOfNotNull(
            compareChangeValues(
                locationTrackChanges.trackNumberId,
                { tnIdFromChange ->
                    trackNumberCache
                        .findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= publicationTime }
                        ?.number
                },
                PropKey("track-number"),
            ),
            compareChangeValues(locationTrackChanges.name, { it }, PropKey("location-track")),
            compareChangeValues(locationTrackChanges.state, { it }, PropKey("state"), null, "LocationTrackState"),
            compareChangeValues(
                locationTrackChanges.type,
                { it },
                PropKey("location-track-type"),
                null,
                "LocationTrackType",
            ),
            compareChangeValues(locationTrackChanges.descriptionBase, { it }, PropKey("description-base")),
            compareChangeValues(
                locationTrackChanges.descriptionSuffix,
                { it },
                PropKey("description-suffix"),
                enumLocalizationKey = "LocationTrackDescriptionSuffix",
            ),
            compareChangeValues(
                locationTrackChanges.owner,
                { locationTrackService.getLocationTrackOwners().find { owner -> owner.id == it }?.name },
                PropKey("owner"),
            ),
            compareChange(
                { oldAndTime.first != newAndTime.first },
                oldAndTime,
                newAndTime,
                { (duplicateOf, timestamp) ->
                    duplicateOf?.let { locationTrackService.getOfficialAtMoment(branch, it, timestamp)?.name }
                },
                PropKey("duplicate-of"),
            ),
            compareLength(
                locationTrackChanges.length.old,
                locationTrackChanges.length.new,
                DISTANCE_CHANGE_THRESHOLD,
                ::roundTo1Decimal,
                PropKey("length"),
                getLengthChangedRemarkOrNull(
                    translation,
                    locationTrackChanges.length.old,
                    locationTrackChanges.length.new,
                ),
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.startPoint.old, locationTrackChanges.startPoint.new) },
                locationTrackChanges.startPoint.old,
                locationTrackChanges.startPoint.new,
                ::formatLocation,
                PropKey("start-location"),
                getPointMovedRemarkOrNull(
                    translation,
                    locationTrackChanges.startPoint.old,
                    locationTrackChanges.startPoint.new,
                ),
            ),
            compareChange(
                { oldStartPointAndM?.address != newStartPointAndM?.address },
                oldStartPointAndM?.address,
                newStartPointAndM?.address,
                { it.toString() },
                PropKey("start-address"),
                null,
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new) },
                locationTrackChanges.endPoint.old,
                locationTrackChanges.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(
                    translation,
                    locationTrackChanges.endPoint.old,
                    locationTrackChanges.endPoint.new,
                ),
            ),
            compareChange(
                { oldEndPointAndM?.address != newEndPointAndM?.address },
                oldEndPointAndM?.address,
                newEndPointAndM?.address,
                { it.toString() },
                PropKey("end-address"),
                null,
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"),
                    ChangeValue(null, null),
                    getKmNumbersChangedRemarkOrNull(
                        translation,
                        changedKmNumbers,
                        locationTrackChanges.geometryChangeSummaries,
                    ),
                )
            } else {
                null
            },
            if (switchLinkChanges == null) {
                null
            } else {
                compareChange(
                    { switchLinkChanges.old != switchLinkChanges.new },
                    null,
                    null,
                    { it },
                    PropKey("linked-switches"),
                    getSwitchLinksChangedRemark(translation, switchLinkChanges),
                )
            },
            // TODO owner
        )
    }

    fun diffReferenceLine(
        translation: Translation,
        changes: ReferenceLineChange,
        changedKmNumbers: Set<KmNumber>,
    ): List<PublicationChange<*>> {
        return listOfNotNull(
            compareLength(
                changes.length.old,
                changes.length.new,
                DISTANCE_CHANGE_THRESHOLD,
                ::roundTo1Decimal,
                PropKey("length"),
                getLengthChangedRemarkOrNull(translation, changes.length.old, changes.length.new),
            ),
            compareChange(
                { !pointsAreSame(changes.startPoint.old, changes.startPoint.new) },
                changes.startPoint.old,
                changes.startPoint.new,
                ::formatLocation,
                PropKey("start-location"),
                getPointMovedRemarkOrNull(translation, changes.startPoint.old, changes.startPoint.new),
            ),
            compareChange(
                { !pointsAreSame(changes.endPoint.old, changes.endPoint.new) },
                changes.endPoint.old,
                changes.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(translation, changes.endPoint.old, changes.endPoint.new),
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"),
                    ChangeValue(null, null),
                    publicationChangeRemark(
                        translation,
                        if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
                        formatChangedKmNumbers(changedKmNumbers.toList()),
                    ),
                )
            } else {
                null
            },
        )
    }

    fun diffKmPost(
        translation: Translation,
        changes: KmPostChange,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
        crsNameGetter: (srid: Srid) -> String,
    ) =
        listOfNotNull(
            compareChangeValues(
                changes.trackNumberId,
                { tnIdFromChange ->
                    trackNumberCache.findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= newTimestamp }?.number
                },
                PropKey("track-number"),
            ),
            compareChangeValues(changes.kmNumber, { it }, PropKey("km-post")),
            compareChangeValues(changes.state, { it }, PropKey("state"), null, "LayoutState"),
            compareChangeValues(
                changes.location,
                ::formatLocation,
                PropKey("layout-location"),
                remark =
                    getPointMovedRemarkOrNull(
                        translation,
                        projectPointToReferenceLineAtTime(
                            oldTimestamp,
                            changes.location.old,
                            changes.trackNumberId.old,
                            geocodingContextGetter,
                        ),
                        projectPointToReferenceLineAtTime(
                            newTimestamp,
                            changes.location.new,
                            changes.trackNumberId.new,
                            geocodingContextGetter,
                        ),
                        "moved-x-meters-on-reference-line",
                    ),
            ),
            compareChangeValues(changes.gkLocation, { formatGkLocation(it, crsNameGetter) }, PropKey("gk-location")),
            compareChangeValues(changes.gkSrid, { crsNameGetter(it) }, PropKey("gk-srid")),
            compareChangeValues(
                changes.gkLocationSource,
                { it },
                PropKey("gk-location-source"),
                enumLocalizationKey = "KmPostGkLocationSource",
            ),
            compareChangeValues(changes.gkLocationConfirmed, { it }, PropKey("gk-location-confirmed")),
        )

    private fun projectPointToReferenceLineAtTime(
        timestamp: Instant,
        location: Point?,
        trackNumberId: IntId<LayoutTrackNumber>?,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    ) =
        location?.let {
            trackNumberId?.let {
                geocodingContextGetter(trackNumberId, timestamp)?.let { context ->
                    context.getM(location)?.let { (m) -> context.referenceLineGeometry.getPointAtM(m)?.toPoint() }
                }
            }
        }

    fun diffSwitch(
        translation: Translation,
        changes: SwitchChange,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    ): List<PublicationChange<*>> {
        val relatedJoints = changes.joints.filterNot { it.removed }.distinctBy { it.trackNumberId }

        val oldLinkedLocationTracks =
            changes.locationTracks.associate { lt ->
                lt.locationTrackId to locationTrackService.getWithGeometry(lt.oldTrackVersion)
            }
        val newLinkedLocationTracks =
            changes.locationTracks.associate { lt ->
                lt.locationTrackId to locationTrackService.getAugWithGeometry(lt.newTrackCacheKey)
            }

        val jointLocationChanges =
            relatedJoints
                .flatMap { joint ->
                    val oldLocation =
                        oldLinkedLocationTracks[joint.locationTrackId]
                            ?.let { (_, geometry) -> geometry.getSwitchLocation(changes.id, joint.jointNumber) }
                            ?.toPoint()
                    val newLocation =
                        newLinkedLocationTracks[joint.locationTrackId]
                            ?.let { (_, geometry) -> geometry.getSwitchLocation(changes.id, joint.jointNumber) }
                            ?.toPoint()
                    val distance =
                        if (oldLocation != null && newLocation != null && !pointsAreSame(oldLocation, newLocation)) {
                            calculateDistance(listOf(oldLocation, newLocation), LAYOUT_SRID)
                        } else {
                            0.0
                        }
                    val jointPropKeyParams =
                        localizationParams(
                            "trackNumber" to
                                trackNumberCache
                                    .findLast { it.id == joint.trackNumberId && it.changeTime <= newTimestamp }
                                    ?.number
                                    ?.value,
                            "switchType" to
                                changes.type.new?.parts?.baseType?.let { switchBaseTypeToProp(translation, it) },
                        )
                    val oldAddress =
                        oldLocation?.let {
                            geocodingContextGetter(joint.trackNumberId, oldTimestamp)?.getAddress(it)?.first
                        }
                    val newAddress =
                        newLocation?.let {
                            geocodingContextGetter(joint.trackNumberId, newTimestamp)?.getAddress(it)?.first
                        }

                    val list =
                        listOfNotNull(
                            compareChange(
                                { distance > DISTANCE_CHANGE_THRESHOLD },
                                oldLocation,
                                newLocation,
                                ::formatLocation,
                                PropKey("switch-joint-location", jointPropKeyParams),
                                getPointMovedRemarkOrNull(translation, oldLocation, newLocation),
                                null,
                            ),
                            compareChange(
                                { oldAddress != newAddress },
                                oldAddress,
                                newAddress,
                                { it.toString() },
                                PropKey("switch-track-address", jointPropKeyParams),
                                getAddressMovedRemarkOrNull(translation, oldAddress, newAddress),
                            ),
                        )
                    list
                }
                .sortedBy { it.propKey.key }

        val oldLinkedTrackNames = oldLinkedLocationTracks.values.map { (lt, _) -> lt.name }.sorted()
        val newLinkedTrackNames = newLinkedLocationTracks.values.map { (lt, _) -> lt.name }.sorted()

        return listOfNotNull(
            compareChangeValues(changes.name, { it }, PropKey("switch")),
            compareChangeValues(changes.state, { it }, PropKey("state-category"), null, "LayoutStateCategory"),
            compareChangeValues(changes.type, { it.typeName }, PropKey("switch-type")),
            compareChangeValues(changes.trapPoint, { it }, PropKey("trap-point"), enumLocalizationKey = "TrapPoint"),
            compareChangeValues(changes.owner, { it }, PropKey("owner")),
            compareChange(
                { oldLinkedTrackNames != newLinkedTrackNames },
                oldLinkedTrackNames,
                newLinkedTrackNames,
                { list -> list.joinToString(", ") { it } },
                PropKey("location-track-connectivity"),
            ),
            compareChangeValues(
                changes.measurementMethod,
                { it.name },
                PropKey("measurement-method"),
                null,
                "MeasurementMethod",
            ),
        ) + jointLocationChanges
    }

    private fun getOrPutGeocodingContext(
        caches: MutableMap<Instant, MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext<ReferenceLineM>>>>,
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        timestamp: Instant,
    ) =
        caches
            .getOrPut(timestamp) { ConcurrentHashMap() }
            .getOrPut(trackNumberId) {
                Optional.ofNullable(geocodingService.getGeocodingContextAtMoment(branch, trackNumberId, timestamp))
            }
            .orElse(null)

    private fun latestTrackNumberNamesAtMoment(
        trackNumberNames: List<TrackNumberAndChangeTime>,
        trackNumberIds: Set<IntId<LayoutTrackNumber>>,
        publicationTime: Instant,
    ) =
        trackNumberNames
            .filter { tn -> trackNumberIds.contains(tn.id) && tn.changeTime <= publicationTime }
            .groupBy { it.id }
            .map { it.value.last().number }
            .toSet()

    class NameHistory<T,S>(private val history: MutableMap<IntId<T>, Map<Instant, S>>) {
        fun get(id: IntId<T>, moment: Instant): S {
            val idNames = requireNotNull(history[id]){"Requested ID has no name mapping pre-resolved: $id"}
            return requireNotNull(idNames[moment]) { "ID $id has no name at time $moment" }
        }
    }

    private fun mapToPublicationTableItems(
        translation: Translation,
        publication: Publication,
        ratkoPush: RatkoPush?,
        versions: PublicationVersions,
        switchLinkChanges: Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>,
        previousComparisonTime: Instant,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
        trackNumberNamesCache: List<TrackNumberAndChangeTime> = trackNumberDao.fetchTrackNumberNames(),
    ): List<PublicationTableItem> {
        //        val publicationLocationTrackChanges =
        // publicationDao.fetchPublicationLocationTrackChanges(publication.id)

        // TODO: GVT-3080 combine the fetch into a single op that fetches object only once
        //  Even for multi-publication fetch
        //  Even locationtracks, though they're fetched for switches as well
        // TODO: GVT-3080 Do something about that trackNubmerNamesCache thingie - surely we can do better
        val trackNumberChanges = getChanges(trackNumberDao, versions.trackNumbers, ::TrackNumberChange)
        val referenceLineChanges = getChanges(referenceLineDao, versions.referenceLines, ::ReferenceLineChange)
        val kmPostChanges = getChanges(kmPostDao, versions.kmPosts, ::KmPostChange)
        val locationTrackChanges: List<LocationTrackChange> = getChanges(locationTrackDao, versions.locationTracks, ::LocationTrackChange)
        val switchChanges = getChanges(layoutSwitchDao, versions.switches, ::SwitchChange)
        val indirectLocationTrackChanges =
            getChanges(locationTrackDao, versions.indirectLocationTrackChanges, ::LocationTrackChange)
        val indirectSwitchChanges = getChanges(layoutSwitchDao, versions.indirectSwitchChanges, ::SwitchChange)

        val trackNumbers =
            trackNumberChanges.map { tn ->
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.track-number-long")} ${tn.new.number}",
                    trackNumbers = setOf(tn.new.number),
                    changedKmNumbers = tn.changeVersions.changedKmNumbers,
                    operation = tn.changeVersions.operation,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffTrackNumber(
                            translation,
                            tn,
                            publication.publicationTime,
                            previousComparisonTime,
                            geocodingContextGetter,
                        ),
                )
            }

        val referenceLines =
            referenceLineChanges.map { rl ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == rl.new.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number

                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.reference-line")} $tn",
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = rl.changeVersions.changedKmNumbers,
                    operation = rl.changeVersions.operation,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges = diffReferenceLine(translation, rl, rl.changeVersions.changedKmNumbers),
                )
            }

        val locationTracks =
            locationTrackChanges.map { lt ->
                val trackNumber =
                    trackNumberNamesCache
                        .findLast { it.id == lt.new.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.location-track")} ${lt.newAug.name}",
                    trackNumbers = setOfNotNull(trackNumber),
                    changedKmNumbers = lt.changeVersions.changedKmNumbers,
                    operation = lt.changeVersions.operation,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            lt,
                            switchLinkChanges[lt.id],
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changeVersions.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val switches =
            switchChanges.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(
                        trackNumberNamesCache,
                        s.changeVersions.trackNumberIds,
                        publication.publicationTime,
                    )
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.switch")} ${s.name}",
                    trackNumbers = tns,
                    operation = s.changeVersions.operation,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffSwitch(
                            translation,
                            s,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                        ),
                )
            }

        val kmPosts =
            kmPostChanges.map { kp ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == kp.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.km-post")} ${kp.kmNumber}",
                    trackNumbers = setOfNotNull(tn),
                    operation = kp.changeVersions.operation,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffKmPost(
                            translation,
                            kp,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                            crsNameGetter = { srid -> geographyService.getCoordinateSystem(srid).name.toString() },
                        ),
                )
            }

        val calculatedLocationTracks =
            indirectLocationTrackChanges.map { lt ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == lt.new.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.location-track")} ${lt.name}",
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = lt.changeVersions.changedKmNumbers,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            lt,
                            switchLinkChanges[lt.id],
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changeVersions.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val calculatedSwitches =
            indirectSwitchChanges.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(
                        trackNumberNamesCache,
                        s.changeVersions.trackNumberIds,
                        publication.publicationTime,
                    )
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.switch")} ${s.name}",
                    trackNumbers = tns,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    ratkoPush = ratkoPush,
                    propChanges =
                        diffSwitch(
                            translation,
                            s,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                        ),
                )
            }

        return listOf(
                trackNumbers,
                referenceLines,
                locationTracks,
                switches,
                kmPosts,
                calculatedLocationTracks,
                calculatedSwitches,
            )
            .flatten()
            .map { publicationTableItem ->
                addOperationClarificationsToPublicationTableItem(translation, publicationTableItem)
            }
    }

    private fun mapToPublicationTableItem(
        name: String,
        trackNumbers: Set<TrackNumber>,
        operation: Operation,
        publication: Publication,
        ratkoPush: RatkoPush?,
        changedKmNumbers: Set<KmNumber>? = null,
        propChanges: List<PublicationChange<*>>,
    ) =
        PublicationTableItem(
            name = FreeText(name),
            trackNumbers = trackNumbers.sorted(),
            changedKmNumbers =
                changedKmNumbers?.let { groupChangedKmNumbers(changedKmNumbers.toList()) } ?: emptyList(),
            operation = operation,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            message = publication.message,
            ratkoPushTime =
                ratkoPush?.takeIf { it.status == RatkoPushStatus.SUCCESSFUL}?.let(RatkoPush::endTime),
            propChanges = propChanges,
        )

    private fun splitCsvColumns(
        translation: Translation
    ): List<CsvEntry<Pair<SplitInPublication, SplitTargetInPublication>>> =
        mapOf<String, (item: Pair<SplitInPublication, SplitTargetInPublication>) -> Any?>(
                "split-details-csv.source-name" to { (split, _) -> split.locationTrack.name },
                "split-details-csv.source-oid" to { (split, _) -> split.locationTrackOid },
                "split-details-csv.target-name" to { (_, split) -> split.name },
                "split-details-csv.target-oid" to { (_, split) -> split.oid },
                "split-details-csv.operation" to { (_, split) -> translation.enum(split.operation) },
                "split-details-csv.start-address" to { (_, split) -> split.startAddress },
                "split-details-csv.end-address" to { (_, split) -> split.endAddress },
            )
            .map { (key, fn) -> CsvEntry(translation.t(key), fn) }
}

private fun <
    AssetType : LayoutAsset<AssetType>,
    VersionType : PublishedVersion<AssetType>,
    ChangeType : AssetChange<AssetType>,
> getChanges(
    dao: LayoutAssetDao<AssetType, *>,
    versions: List<VersionType>,
    constructor: (VersionType, old: AssetType?, new: AssetType) -> ChangeType,
): List<ChangeType> {
    // TODO: GVT-3080 bypass caches since we're fetching history data
    val assets = dao.fetchMany(versions.flatMap { rl -> listOfNotNull(rl.oldVersion, rl.newVersion) }.toSet())
    return versions.map { v ->
        constructor(
            v,
            v.oldVersion?.let(assets::get),
            requireNotNull(assets.get(v.newVersion)) { "The changed asset version must exist" },
        )
    }
}
