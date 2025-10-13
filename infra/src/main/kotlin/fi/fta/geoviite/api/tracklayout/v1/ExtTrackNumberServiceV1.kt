package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionAtMoment(publication.layoutBranch.branch, trackNumberId, publication.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                ExtTrackNumberResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            trackNumber,
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun createTrackNumberModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberResponseV1? {
        return publicationDao
            .fetchPublishedTrackNumberBetween(id, publications.from.publicationTime, publications.to.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                ExtModifiedTrackNumberResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            trackNumber,
                            LayoutBranch.main,
                            publications.to.publicationTime,
                            coordinateSystem,
                        ),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    fun getExtTrackNumber(
        oid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtTrackNumberV1 {
        val (startLocation, endLocation) =
            referenceLineService
                .getStartAndEndAtMoment(branch, listOf(trackNumber.referenceLineId as IntId), moment)
                .firstOrNull()
                ?.let { startAndEnd -> layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd) }
                ?.let { startAndEnd -> startAndEnd.start to startAndEnd.end } ?: (null to null)

        return ExtTrackNumberV1(
            trackNumberOid = oid,
            trackNumber = trackNumber.number,
            trackNumberDescription = trackNumber.description,
            trackNumberState = trackNumber.state.let(ExtTrackNumberStateV1::of),
            startLocation = startLocation?.let(::ExtAddressPointV1),
            endLocation = endLocation?.let(::ExtAddressPointV1),
        )
    }
}
