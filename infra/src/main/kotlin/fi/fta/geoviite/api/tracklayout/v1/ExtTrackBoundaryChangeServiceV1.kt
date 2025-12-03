package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackBoundaryChangeServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackBoundaryChangeCollection(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ExtTrackBoundaryChangeResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        return if (publications.areDifferent()) {
            val splits =
                getSplitData(
                    branch = publications.to.layoutBranch.branch,
                    startMoment = publications.from.publicationTime,
                    endMoment = publications.to.publicationTime,
                )
            val boundaryChanges = splits.map(::createBoundaryChange)
            ExtTrackBoundaryChangeResponseV1(
                layoutVersionFrom = ExtLayoutVersionV1(publications.from.uuid),
                layoutVersionTo = ExtLayoutVersionV1(publications.to.uuid),
                boundaryChanges = boundaryChanges,
            )
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private data class BoundaryChangeSegmentData(
        val sourceTrackOid: Oid<LocationTrack>,
        val sourceTrack: LocationTrack,
        val sourceGeometry: LocationTrackGeometry,
        val targetTrackOid: Oid<LocationTrack>,
        val targetTrack: LocationTrack,
        val splitOperation: SplitTargetOperation?,
        val sourceEdgeIndices: IntRange,
    )

    private data class BoundaryChangeData(
        val publication: Publication,
        val type: ExtTrackBoundaryChangeTypeV1,
        val trackNumber: LayoutTrackNumber,
        val trackNumberOid: Oid<LayoutTrackNumber>,
        val geocodingContext: GeocodingContext<ReferenceLineM>,
        val segments: List<BoundaryChangeSegmentData>,
    )

    private fun getSplitData(branch: LayoutBranch, startMoment: Instant, endMoment: Instant): List<BoundaryChangeData> {
        val splits = publicationDao.fetchPublishedSplitsBetween(startMoment, endMoment)
        val locationTrackOids = locationTrackDao.fetchExternalIds(branch, splits.flatMap { it.allTrackIds }.distinct())
        val publications = publicationDao.getPublications(splits.map { it.publicationId }.toSet())
        val getTrackOid = { id: DomainId<LocationTrack> -> locationTrackOids[id]?.oid ?: throwOidNotFound(branch, id) }
        val sourceTracks: Map<LayoutRowVersion<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
            locationTrackService
                .getManyWithGeometries(splits.flatMap { s -> s.segments.map { t -> t.sourceTrackVersion } }.distinct())
                .associateBy { it.first.getVersionOrThrow() }
        val targetTracks =
            locationTrackDao.fetchManyByVersion(splits.flatMap { s -> s.segments.map { t -> t.targetTrackVersion } })
        val geocodingContexts = geocodingService.getLazyGeocodingContextsAtMultiMoment(branch)
        return splits.map { split ->
            val publication =
                requireNotNull(publications[split.publicationId]) { "Publication not found: ${split.publicationId}" }
            val moment = publication.publicationTime
            val changeTargets =
                split.segments.map { target ->
                    val (sourceTrack, sourceGeometry) =
                        sourceTracks[target.sourceTrackVersion] ?: throwLocationTrackNotFound(target.sourceTrackVersion)
                    val targetTrack =
                        targetTracks[target.targetTrackVersion] ?: throwLocationTrackNotFound(target.targetTrackVersion)
                    BoundaryChangeSegmentData(
                        sourceTrackOid = getTrackOid(sourceTrack.id),
                        sourceTrack = sourceTrack,
                        sourceGeometry = sourceGeometry,
                        targetTrackOid = getTrackOid(targetTrack.id),
                        targetTrack = targetTrack,
                        splitOperation = target.operation,
                        sourceEdgeIndices = target.sourceEdgeIndices,
                    )
                }
            val trackNumberId = changeTargets.first().sourceTrack.trackNumberId
            val trackNumber =
                trackNumberDao.getOfficialAtMoment(branch, trackNumberId, moment)
                    ?: throwTrackNumberNotFound(branch, moment, trackNumberId)
            val geocodingContext =
                geocodingContexts(trackNumberId, moment) ?: throwGeocodingContextNotFound(branch, moment, trackNumberId)
            val trackNumberOid = oidLookup(trackNumberDao, branch, trackNumberId)
            BoundaryChangeData(
                publication = publication,
                type = ExtTrackBoundaryChangeTypeV1.SPLIT,
                trackNumber = trackNumber,
                trackNumberOid = trackNumberOid,
                geocodingContext = geocodingContext,
                segments = changeTargets,
            )
        }
    }

    private fun createBoundaryChange(data: BoundaryChangeData): ExtTrackBoundaryChangeOperationV1 {
        val changes: List<ExtTrackBoundaryChangeV1> =
            data.segments.map { change ->
                val (startPoint, endPoint) = change.sourceGeometry.getEdgeStartAndEnd(change.sourceEdgeIndices)
                ExtTrackBoundaryChangeV1(
                    sourceLocationTrackOid = ExtOidV1(change.sourceTrackOid),
                    sourceLocationTrackName = change.sourceTrack.name,
                    targetLocationTrackOid = ExtOidV1(change.targetTrackOid),
                    targetLocationTrackName = change.targetTrack.name,
                    startAddress = getAddress(startPoint, data.geocodingContext).toString(),
                    endAddress = getAddress(endPoint, data.geocodingContext).toString(),
                    geometryChange =
                        change.splitOperation?.let(ExtTrackBoundaryGeometryChangeTypeV1::of)
                            ?: ExtTrackBoundaryGeometryChangeTypeV1.TRANSFER_GEOMETRY,
                )
            }
        return ExtTrackBoundaryChangeOperationV1(
            trackLayoutVersion = ExtLayoutVersionV1(data.publication),
            changeType = data.type,
            trackNumber = data.trackNumber.number,
            trackNumberOid = ExtOidV1(data.trackNumberOid),
            changes = changes,
        )
    }

    private fun getAddress(point: IPoint, geocodingContext: GeocodingContext<ReferenceLineM>): TrackMeter =
        geocodingContext.getAddress(point, 3)?.first
            ?: error("Could not geocode address: trackNumber=${geocodingContext.trackNumber} point=$point")
}
