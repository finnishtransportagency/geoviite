package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitHeader
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
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
    private val referenceLineDao: ReferenceLineDao,
    private val switchDao: LayoutSwitchDao,
    private val kmPostDao: LayoutKmPostDao,
) {

    @Transactional(readOnly = true)
    fun fetchPublications(layoutBranch: LayoutBranch, from: Instant? = null, to: Instant? = null): List<Publication> {
        return publicationDao.fetchPublicationsBetween(layoutBranch, from, to)
    }

    @Transactional(readOnly = true)
    fun getPublicationDetails(ids: Set<IntId<Publication>>): Map<IntId<Publication>, PublicationDetails> {
        val publications = publicationDao.getPublications(ids)
        val ratkoStatuses =
            ratkoPushDao.getRatkoStatuses(ids).mapValues { (_, statuses) ->
                statuses.sortedByDescending { it.endTime }.firstOrNull()
            }

        val publishedReferenceLines = publicationDao.fetchPublishedReferenceLines(ids)
        val publishedKmPosts = publicationDao.fetchPublishedKmPosts(ids)
        val publishedTrackNumbers = publicationDao.fetchPublishedTrackNumbers(ids)
        val publishedLocationTracks = publicationDao.fetchPublishedLocationTracks(ids)
        val publishedSwitches = publicationDao.fetchPublishedSwitches(ids)
        val splits = splitService.getSplitIdsByPublication(ids).mapValues { (_, splitId) -> splitService.get(splitId) }

        return ids.associateWith { id ->
            val publication = publications.getValue(id)
            val referenceLines = publishedReferenceLines[id] ?: listOf()
            val kmPosts = publishedKmPosts[id] ?: listOf()
            val (directTrackNumbers, indirectTrackNumbers) =
                publishedTrackNumbers[id] ?: PublishedItemListing(listOf(), listOf())
            val (directLocationTracks, indirectLocationTracks) =
                publishedLocationTracks[id] ?: PublishedItemListing(listOf(), listOf())
            val (directSwitches, indirectSwitches) = publishedSwitches[id] ?: PublishedItemListing(listOf(), listOf())
            val ratkoStatus = ratkoStatuses[id]
            val split = splits[id]

            PublicationDetails(
                id = publication.id,
                uuid = publication.uuid,
                publicationTime = publication.publicationTime,
                publicationUser = publication.publicationUser,
                message = publication.message,
                trackNumbers = directTrackNumbers,
                referenceLines = referenceLines,
                locationTracks = directLocationTracks,
                switches = directSwitches,
                kmPosts = kmPosts,
                ratkoPushStatus = ratkoStatus?.status,
                ratkoPushTime = ratkoStatus?.endTime,
                indirectChanges =
                    PublishedIndirectChanges(
                        trackNumbers = indirectTrackNumbers,
                        locationTracks = indirectLocationTracks,
                        switches = indirectSwitches,
                    ),
                split = split?.let(::SplitHeader),
                layoutBranch = publication.layoutBranch,
                cause = publication.cause,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails =
        getPublicationDetails(setOf(id)).getValue(id)

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
                publication,
                specificObjectId = null,
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(publication.id),
                previousPublication?.key ?: publication.publicationTime.minusMillis(1),
                { trackNumberId: IntId<LayoutTrackNumber>, timestamp: Instant ->
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
        val publications = publicationDao.fetchPublicationsBetween(layoutBranch, from, to)
        val ids = publications.map { it.id }
        val details = getPublicationDetails(ids.toSet())
        return ids.map(details::getValue)
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
        specificId: PublishableObjectIdAndType? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        translation: Translation,
    ): List<PublicationTableItem> {
        val switchLinkChanges =
            if (specificId != null && specificId.type != PublishableObjectType.LOCATION_TRACK) mapOf()
            else publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(null, layoutBranch, from, to, specificId)

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
                            specificObjectId = specificId,
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
                        .fetchPublishedLocationTracks(setOf(id))
                        .getOrDefault(id, PublishedItemListing(listOf(), listOf()))
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
        specificId: PublishableObjectIdAndType? = null,
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
                specificId = specificId,
                sortBy = sortBy,
                order = order,
                translation = translation,
            )

        return asCsvFile(orderedPublishedItems, timeZone ?: ZoneId.of("UTC"), translation)
    }

    fun diffTrackNumber(
        translation: Translation,
        trackNumberChanges: TrackNumberChanges,
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
                remark = getAddressMovedRemarkOrNull(translation, trackNumberChanges.startAddress),
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
        locationTrackChanges: LocationTrackChanges,
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
                locationTrackChanges.trackNumberId.new.let {
                    getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newStart)
                }
            }
        val newEndPointAndM =
            locationTrackChanges.endPoint.new?.let { newEnd ->
                locationTrackChanges.trackNumberId.new.let {
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
        changes: ReferenceLineChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        changedKmNumbers: Set<KmNumber>,
        getGeocodingContext: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
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
        changes: KmPostChanges,
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

    fun jointsString(jointNumbers: List<JointNumber>): String =
        jointNumbers.joinToString("-") { j -> j.intValue.toString() }

    fun diffSwitch(
        translation: Translation,
        changes: SwitchChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    ): List<PublicationChange<*>> {
        val getTrackNumber = { tnId: IntId<LayoutTrackNumber> ->
            // If the track number name changed in the same publication, it has it's own change row
            // -> just use the new name here for clarity, even for old address
            requireNotNull(trackNumberCache.findLast { tn -> tn.id == tnId && tn.changeTime <= newTimestamp }?.number)
        }
        val getAddressChange = { pointChange: Change<Point?>, trackNumberId: IntId<LayoutTrackNumber> ->
            val oldCtx = pointChange.old?.let { geocodingContextGetter(trackNumberId, oldTimestamp) }
            val newCtx = pointChange.new?.let { geocodingContextGetter(trackNumberId, newTimestamp) }
            val pointChanged =
                when {
                    pointChange.old == null && pointChange.new == null -> false
                    pointChange.old == null || pointChange.new == null -> true
                    else -> !pointChange.old.isSame(pointChange.new, LAYOUT_COORDINATE_DELTA)
                }
            if (oldCtx != newCtx || pointChanged) {
                Change(
                    old = pointChange.old?.let { p -> oldCtx?.getAddress(p)?.first },
                    new = pointChange.new?.let { p -> newCtx?.getAddress(p)?.first },
                )
            } else null
        }

        val connectedTrackChanges =
            if (changes.state.new != LayoutStateCategory.NOT_EXISTING) {
                changes.trackJoints.mapNotNull { track ->
                    val localizationParams = localizationParams("locationTrack" to track.name)
                    compareChange(
                        change = track.joints,
                        valueTransform = {
                            translation.t(
                                "publication-details-table.track-linked",
                                localizationParams("joints" to jointsString(it)),
                            )
                        },
                        propKey = PropKey("location-track-link", localizationParams),
                        nullReplacement = translation.t("publication-details-table.track-not-linked"),
                    )
                }
            } else emptyList()

        val jointLocationChanges =
            if (changes.state.new != LayoutStateCategory.NOT_EXISTING) {
                changes.trackConnections
                    .map { tracks -> tracks.flatMap { t -> t.joints } }
                    // Transform to list of per-joint changes
                    .itemize { j -> j.jointNumber }
                    .sortedBy { (j, _) -> j.intValue }
                    // Joint addition/removal is visible in connectedTrackChanges so we only care about location changes
                    // TODO: GVT-3128 Do we want to show coordinates in addition/removal cases?
                    // .filter { (_, change) -> change.old != null && change.new != null }
                    .mapNotNull { (jointNumber: JointNumber, change: Change<PublicationSwitchJoint?>) ->
                        val distance =
                            if (change.old != null && change.new != null) {
                                // TODO: GVT-3232 How useful is this?
                                lineLength(change.old.location, change.new.location)
                                //                                calculateDistance(listOf(change.old.location,
                                // change.new.location), LAYOUT_SRID)
                            } else {
                                0.0
                            }
                        val jointPropKeyParams = localizationParams("jointNumber" to jointNumber.intValue.toString())
                        compareChange(
                            change = change.map { c -> c?.location },
                            isSame = { old, new -> distance < DISTANCE_CHANGE_THRESHOLD },
                            valueTransform = ::formatLocation,
                            propKey = PropKey("switch-joint-location", jointPropKeyParams),
                            remark = getPointMovedRemarkOrNull(translation, change.old?.location, change.new?.location),
                            nullReplacement = translation.t("publication-details-table.no-location"),
                        )
                    }
            } else emptyList()

        val jointAddressChanges =
            if (changes.state.new != LayoutStateCategory.NOT_EXISTING) {
                changes.trackNumberJointLocations
                    .groupBy { change -> change.jointNumber }
                    .toList()
                    .sortedBy { (j, _) -> j.intValue }
                    .flatMap { (joint: JointNumber, changes: List<TrackNumberJointLocationChange>) ->
                        changes
                            .sortedBy { c -> c.trackNumberId.intValue }
                            .mapNotNull { change ->
                                getAddressChange(change.location, change.trackNumberId)
                                    // TODO: GVT-3128 Do we want to show addresses in addition/removal cases?
                                    // ?.takeIf { change -> change.old != null && change.new != null }
                                    ?.let { addressChange ->
                                        val jointPropKeyParams =
                                            localizationParams(
                                                "jointNumber" to joint.intValue.toString(),
                                                "trackNumber" to getTrackNumber(change.trackNumberId),
                                            )
                                        compareChange(
                                            change = addressChange,
                                            valueTransform = { it.toString() },
                                            propKey = PropKey("switch-track-address", jointPropKeyParams),
                                            remark = getAddressMovedRemarkOrNull(translation, addressChange),
                                            nullReplacement = translation.t("publication-details-table.no-location"),
                                        )
                                    }
                            }
                    }
            } else emptyList()

        return listOfNotNull(
            compareChangeValues(changes.name, { it }, PropKey("switch")),
            compareChangeValues(changes.state, { it }, PropKey("state-category"), null, "LayoutStateCategory"),
            compareChangeValues(changes.type, { it.typeName }, PropKey("switch-type")),
            compareChangeValues(changes.trapPoint, { it }, PropKey("trap-point"), enumLocalizationKey = "TrapPoint"),
            compareChangeValues(changes.owner, { it }, PropKey("owner")),
            compareChangeValues(
                changes.measurementMethod,
                { it?.name },
                PropKey("measurement-method"),
                null,
                "MeasurementMethod",
            ),
        ) + connectedTrackChanges + jointLocationChanges + jointAddressChanges
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

    private fun canSkipLoadingChanges(
        publication: PublicationDetails,
        specificObject: PublishableObjectIdAndType?,
        type: PublishableObjectType,
    ) =
        if (specificObject == null) false
        else
            type != specificObject.type ||
                when (specificObject.type) {
                    PublishableObjectType.TRACK_NUMBER ->
                        publication.allPublishedTrackNumbers.none { it.id == specificObject.id }
                    PublishableObjectType.LOCATION_TRACK ->
                        publication.allPublishedLocationTracks.none { it.id == specificObject.id }
                    PublishableObjectType.REFERENCE_LINE ->
                        publication.referenceLines.none { it.id == specificObject.id }
                    PublishableObjectType.SWITCH -> publication.allPublishedSwitches.none { it.id == specificObject.id }
                    PublishableObjectType.KM_POST -> publication.kmPosts.none { it.id == specificObject.id }
                }

    private fun mapToPublicationTableItems(
        translation: Translation,
        publication: PublicationDetails,
        specificObjectId: PublishableObjectIdAndType?,
        switchLinkChanges: Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>,
        previousComparisonTime: Instant,
        geocodingContextGetter: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
        trackNumberNamesCache: List<TrackNumberAndChangeTime> = trackNumberDao.fetchTrackNumberNames(),
    ): List<PublicationTableItem> {
        publication.locationTracks
        val publicationLocationTrackChanges =
            if (canSkipLoadingChanges(publication, specificObjectId, PublishableObjectType.LOCATION_TRACK)) {
                mapOf()
            } else publicationDao.fetchPublicationLocationTrackChanges(publication.id)
        val publicationTrackNumberChanges =
            if (canSkipLoadingChanges(publication, specificObjectId, PublishableObjectType.TRACK_NUMBER)) {
                mapOf()
            } else
                publicationDao.fetchPublicationTrackNumberChanges(
                    publication.layoutBranch.branch,
                    publication.id,
                    previousComparisonTime,
                )
        val publicationKmPostChanges =
            if (canSkipLoadingChanges(publication, specificObjectId, PublishableObjectType.KM_POST)) mapOf()
            else publicationDao.fetchPublicationKmPostChanges(publication.id)

        val publicationReferenceLineChanges =
            if (canSkipLoadingChanges(publication, specificObjectId, PublishableObjectType.REFERENCE_LINE)) mapOf()
            else publicationDao.fetchPublicationReferenceLineChanges(publication.id)
        val publicationSwitchChanges =
            if (canSkipLoadingChanges(publication, specificObjectId, PublishableObjectType.SWITCH)) mapOf()
            else publicationDao.fetchPublicationSwitchChanges(publication.id)

        val trackNumbersToDiff =
            publication.trackNumbers.filter { tn -> specificObjectId == null || specificObjectId.isTrackNumber(tn.id) }
        val referenceLinesToDiff =
            publication.referenceLines.filter { rl ->
                specificObjectId == null || specificObjectId.isReferenceLine(rl.id)
            }
        val kmPostsToDiff =
            publication.kmPosts.filter { kp -> specificObjectId == null || specificObjectId.isKmPost(kp.id) }
        val switchesToDiff =
            publication.switches.filter { sw -> specificObjectId == null || specificObjectId.isSwitch(sw.id) }
        val indirectSwitchesToDiff =
            publication.indirectChanges.switches.filter { s ->
                specificObjectId == null || specificObjectId.isSwitch(s.id)
            }
        val locationTracksToDiff =
            publication.locationTracks.filter { lt ->
                specificObjectId == null || specificObjectId.isLocationTrack(lt.id)
            }
        val indirectLocationTracksToDiff =
            publication.indirectChanges.locationTracks.filter { lt ->
                specificObjectId == null || specificObjectId.isLocationTrack(lt.id)
            }

        // Multi-fetch the actual objects to avoid extra round-trips to DB
        val trackNumberVersions = trackNumberDao.fetchManyByVersion(trackNumbersToDiff.map { it.version })
        val referenceLineVersions = referenceLineDao.fetchManyByVersion(referenceLinesToDiff.map { it.version })
        val kmPostVersions = kmPostDao.fetchManyByVersion(kmPostsToDiff.map { it.version })
        val locationTrackVersions =
            locationTrackDao.fetchManyByVersion(
                (locationTracksToDiff + indirectLocationTracksToDiff).map { it.version }.distinct()
            )
        val switchVersions =
            switchDao.fetchManyByVersion((switchesToDiff + indirectSwitchesToDiff).map { it.version }.distinct())

        val trackNumbers =
            trackNumbersToDiff.map { tn ->
                mapToPublicationTableItem(
                    name =
                        translation.t(
                            "publication-table.track-number-long",
                            localizationParams("trackNumber" to tn.number),
                        ),
                    asset = PublishedAssetTrackNumber(trackNumberVersions.getValue(tn.version)),
                    trackNumbers = setOf(tn.number),
                    changedKmNumbers = tn.changedKmNumbers,
                    operation = tn.operation,
                    publication = publication,
                    propChanges =
                        diffTrackNumber(
                            translation,
                            publicationTrackNumberChanges.getOrElse(tn.id) {
                                error("Track number changes not found: id=${tn.id} version=${tn.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            geocodingContextGetter,
                        ),
                )
            }

        val referenceLines =
            referenceLinesToDiff.map { rl ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == rl.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number

                mapToPublicationTableItem(
                    name = translation.t("publication-table.reference-line", localizationParams("trackNumber" to tn)),
                    asset = PublishedAssetReferenceLine(referenceLineVersions.getValue(rl.version)),
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = rl.changedKmNumbers,
                    operation = rl.operation,
                    publication = publication,
                    propChanges =
                        diffReferenceLine(
                            translation,
                            publicationReferenceLineChanges.getOrElse(rl.id) {
                                error("Reference line changes not found: id=${rl.id} version=${rl.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            rl.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val locationTracks =
            locationTracksToDiff.map { lt ->
                val trackNumber =
                    trackNumberNamesCache
                        .findLast { it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name =
                        translation.t(
                            "publication-table.location-track",
                            localizationParams("locationTrack" to lt.name),
                        ),
                    asset = PublishedAssetLocationTrack(locationTrackVersions.getValue(lt.version)),
                    trackNumbers = setOfNotNull(trackNumber),
                    changedKmNumbers = lt.changedKmNumbers,
                    operation = lt.operation,
                    publication = publication,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            publicationLocationTrackChanges.getOrElse(lt.id) {
                                error("Location track changes not found: id=${lt.id} version=${lt.version}")
                            },
                            switchLinkChanges[lt.id],
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val switches =
            switchesToDiff.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
                mapToPublicationTableItem(
                    name = translation.t("publication-table.switch", localizationParams("switch" to s.name)),
                    asset = PublishedAssetSwitch(switchVersions.getValue(s.version)),
                    trackNumbers = tns,
                    operation = s.operation,
                    publication = publication,
                    propChanges =
                        diffSwitch(
                            translation,
                            publicationSwitchChanges.getOrElse(s.id) {
                                error("Switch changes not found: id=${s.id} version=${s.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                        ),
                )
            }

        val kmPosts =
            kmPostsToDiff.map { kp ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == kp.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = translation.t("publication-table.km-post", localizationParams("kmNumber" to kp.kmNumber)),
                    asset = PublishedAssetKmPost(kmPostVersions.getValue(kp.version)),
                    trackNumbers = setOfNotNull(tn),
                    operation = kp.operation,
                    publication = publication,
                    propChanges =
                        diffKmPost(
                            translation,
                            publicationKmPostChanges.getOrElse(kp.id) {
                                error("KM Post changes not found: id=${kp.id} version=${kp.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                            crsNameGetter = { srid -> geographyService.getCoordinateSystem(srid).name.toString() },
                        ),
                )
            }

        val calculatedLocationTracks =
            indirectLocationTracksToDiff.map { lt ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name =
                        translation.t(
                            "publication-table.location-track",
                            localizationParams("locationTrack" to lt.name),
                        ),
                    asset = PublishedAssetLocationTrack(locationTrackVersions.getValue(lt.version)),
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = lt.changedKmNumbers,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            publicationLocationTrackChanges.getOrElse(lt.id) {
                                error("Location track changes not found: id=${lt.id} version=${lt.version}")
                            },
                            switchLinkChanges[lt.id],
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val calculatedSwitches =
            indirectSwitchesToDiff.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
                mapToPublicationTableItem(
                    name = translation.t("publication-table.switch", localizationParams("switch" to s.name)),
                    asset = PublishedAssetSwitch(switchVersions.getValue(s.version)),
                    trackNumbers = tns,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    propChanges =
                        diffSwitch(
                            translation,
                            publicationSwitchChanges.getOrElse(s.id) {
                                error("Switch changes not found: id=${s.id} version=${s.version}")
                            },
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
        asset: PublishedAsset,
        trackNumbers: Set<TrackNumber>,
        operation: Operation,
        publication: PublicationDetails,
        changedKmNumbers: Set<KmNumber>? = null,
        propChanges: List<PublicationChange<*>>,
    ) =
        PublicationTableItem(
            name = FreeText(name),
            asset = asset,
            publicationId = publication.id,
            trackNumbers = trackNumbers.sorted(),
            changedKmNumbers =
                changedKmNumbers?.let { groupChangedKmNumbers(changedKmNumbers.toList()) } ?: emptyList(),
            operation = operation,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            message = publication.message,
            ratkoPushTime =
                if (publication.ratkoPushStatus == RatkoPushStatus.SUCCESSFUL) publication.ratkoPushTime else null,
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
