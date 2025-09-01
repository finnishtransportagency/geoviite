package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.error.TrackLayoutVersionNotFound
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtTrackNumberServiceV1
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val publicationService: PublicationService,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackLayoutVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw TrackLayoutVersionNotFound("trackLayoutVersion=${trackLayoutVersion}")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionAtMoment(layoutContext.branch, trackNumberId, publication.publicationTime)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                ExtTrackNumberResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            trackNumber,
                            layoutContext,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun createTrackNumberModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        coordinateSystem: Srid,
        publications: PublicationComparison,
    ): ExtModifiedTrackNumberResponseV1? {
        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed, oid=$oid")

        return layoutTrackNumberDao
            .fetchOfficialVersionComparison(
                MainLayoutContext.official.branch,
                trackNumberId,
                publications.fromPublication.publicationTime,
                publications.toPublication.publicationTime,
            )
            .takeIf { assetVersions -> assetVersions.areDifferent() }
            ?.let { assetVersions ->
                ExtModifiedTrackNumberResponseV1(
                    modificationsFromVersion = publications.fromPublication.uuid,
                    trackLayoutVersion = publications.toPublication.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber =
                        getExtTrackNumber(
                            oid,
                            layoutTrackNumberDao.fetch(assetVersions.toVersion.let(::requireNotNull)),
                            MainLayoutContext.official,
                            publications.toPublication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
            ?: layoutAssetVersionsAreTheSame(
                trackNumberId,
                publications,
            )
    }

    fun getExtTrackNumber(
        oid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        layoutContext: LayoutContext,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtTrackNumberV1 {
        val (startLocation, endLocation) =
            referenceLineService
                .getStartAndEndAtMoment(layoutContext, listOf(trackNumber.referenceLineId as IntId), moment)
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
