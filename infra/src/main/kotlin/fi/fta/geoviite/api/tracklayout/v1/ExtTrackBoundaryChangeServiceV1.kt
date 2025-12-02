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
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.FreeText
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

    private data class SplitData(
        val publication: Publication,
        val split: Split,
        val trackNumber: LayoutTrackNumber,
        val trackNumberOid: Oid<LayoutTrackNumber>,
        val geocodingContext: GeocodingContext<ReferenceLineM>,
        val sourceTrack: LocationTrack,
        val sourceTrackOid: Oid<LocationTrack>,
        val sourceGeometry: LocationTrackGeometry,
        val targetTracks: List<Pair<Oid<LocationTrack>, LocationTrack>>,
    )

    private fun getSplitData(branch: LayoutBranch, startMoment: Instant, endMoment: Instant): List<SplitData> {
        val splits = publicationDao.fetchPublishedSplitsBetween(startMoment, endMoment)
        val allTrackIds = splits.flatMap { it.second.locationTracks }.distinct()
        val locationTrackOids = locationTrackDao.fetchExternalIds(branch, allTrackIds)
        val getTrackOid = { id: DomainId<LocationTrack> -> locationTrackOids[id]?.oid ?: throwOidNotFound(branch, id) }
        return splits.map { (publication, split) ->
            val moment = publication.publicationTime
            val (sourceTrack, sourceGeometry) = locationTrackService.getWithGeometry(split.sourceLocationTrackVersion)
            val trackNumber =
                trackNumberDao.getOfficialAtMoment(branch, sourceTrack.trackNumberId, moment)
                    ?: throwTrackNumberNotFound(branch, moment, sourceTrack.trackNumberId)
            val geocodingContext =
                geocodingService.getGeocodingContextAtMoment(branch, sourceTrack.trackNumberId, moment)
                    ?: throwGeocodingContextNotFound(branch, moment, sourceTrack.trackNumberId)
            val trackNumberOid = oidLookup(trackNumberDao, branch, sourceTrack.trackNumberId)
            val targetTrackIds = split.targetLocationTracks.map { it.locationTrackId }
            val targetTracks =
                locationTrackDao.getManyOfficialAtMoment(branch, targetTrackIds, moment).map { track ->
                    getTrackOid(track.id) to track
                }
            SplitData(
                publication = publication,
                split = split,
                trackNumber = trackNumber,
                trackNumberOid = trackNumberOid,
                geocodingContext = geocodingContext,
                sourceTrack = sourceTrack,
                sourceTrackOid = getTrackOid(split.sourceLocationTrackId),
                sourceGeometry = sourceGeometry,
                targetTracks = targetTracks,
            )
        }
    }

    private fun createBoundaryChange(data: SplitData): ExtTrackBoundaryChangeOperationV1 {
        val changes: List<ExtTrackBoundaryChangeV1> =
            data.targetTracks.map { (targetOid, targetTrack) ->
                val targetSplit =
                    data.split.targetLocationTracks.find { it.locationTrackId == targetTrack.id }
                        ?: error("Target track ${targetTrack.id} not found in split ${data.split.id}")
                val changeDescription =
                    when (targetSplit.operation) {
                        SplitTargetOperation.CREATE -> "Luotu uutena raiteena"
                        SplitTargetOperation.OVERWRITE -> "Duplikaattiraiteen geometria korvattu"
                        SplitTargetOperation.TRANSFER -> "Duplikaattiraiteen geometria osittain korvattu"
                    }
                val (startPoint, endPoint) = data.sourceGeometry.getEdgeStartAndEnd(targetSplit.edgeIndices)
                ExtTrackBoundaryChangeV1(
                    sourceLocationTrackOid = ExtOidV1(data.sourceTrackOid),
                    sourceLocationTrackName = data.sourceTrack.name,
                    targetLocationTrackOid = ExtOidV1(targetOid),
                    targetLocationTrackName = targetTrack.name,
                    startAddress = getAddress(startPoint, data.geocodingContext).toString(),
                    endAddress = getAddress(endPoint, data.geocodingContext).toString(),
                    description = FreeText(changeDescription),
                )
            }
        return ExtTrackBoundaryChangeOperationV1(
            trackLayoutVersion = ExtLayoutVersionV1(data.publication.uuid),
            changeType = ExtTrackBoundaryChangeTypeV1.SPLIT,
            trackNumber = data.trackNumber.number,
            trackNumberOid = ExtOidV1(data.trackNumberOid),
            changes = changes,
        )
    }

    private fun getAddress(point: IPoint, geocodingContext: GeocodingContext<ReferenceLineM>): TrackMeter =
        geocodingContext.getAddress(point, 3)?.first
            ?: error("Could not geocode address: trackNumber=${geocodingContext.trackNumber} point=$point")
}
