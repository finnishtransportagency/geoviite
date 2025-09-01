package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtTrackNumberCollectionServiceV1
@Autowired
constructor(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val publicationDao: PublicationDao,
    private val referenceLineService: ReferenceLineService,
) {
    fun createTrackNumberCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberCollectionResponseV1 {
        return ExtTrackNumberCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            trackNumberCollection =
                extGetTrackNumberCollection(
                    LayoutContext.of(publication.layoutBranch.branch, PublicationState.OFFICIAL),
                    layoutTrackNumberDao.listOfficialAtMoment(
                        publication.layoutBranch.branch,
                        publication.publicationTime,
                    ),
                    coordinateSystem,
                    publication.publicationTime,
                ),
        )
    }

    fun createTrackNumberCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        return publicationDao
            .fetchPublishedTrackNumbersAfterMoment(
                publications.from.publicationTime,
                publications.to.publicationTime,
            )
            .let { changedIds ->
                layoutTrackNumberDao.getManyOfficialAtMoment(
                    LayoutBranch.main,
                    changedIds,
                    publications.to.publicationTime,
                )
            }
            .takeIf { modifiedTrackNumbers -> modifiedTrackNumbers.isNotEmpty() }
            ?.let { modifiedTrackNumbers ->
                ExtModifiedTrackNumberCollectionResponseV1(
                    modificationsFromVersion = publications.from.uuid,
                    trackLayoutVersion = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumberCollection =
                        extGetTrackNumberCollection(
                            MainLayoutContext.official,
                            modifiedTrackNumbers,
                            coordinateSystem,
                            publications.to.publicationTime,
                        ),
                )
            }
    }

    fun extGetTrackNumberCollection(
        layoutContext: LayoutContext,
        trackNumbers: List<LayoutTrackNumber>,
        coordinateSystem: Srid,
        moment: Instant,
    ): List<ExtTrackNumberV1> {
        val trackNumberIds = trackNumbers.map { trackNumber -> trackNumber.id as IntId }

        val referenceLineStartsAndEnds =
            referenceLineService
                .getStartAndEndAtMoment(
                    layoutContext,
                    trackNumbers.mapNotNull { trackNumber -> trackNumber.referenceLineId },
                    moment,
                )
                .associateBy { it.id }

        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(layoutContext.branch, trackNumberIds)

        return trackNumbers.map { trackNumber ->
            val (startLocation, endLocation) =
                referenceLineStartsAndEnds[trackNumber.referenceLineId]?.let { startAndEnd ->
                    layoutAlignmentStartAndEndToCoordinateSystem(coordinateSystem, startAndEnd).let {
                        convertedStartAndEnd ->
                        convertedStartAndEnd.start to convertedStartAndEnd.end
                    }
                } ?: (null to null)

            val trackNumberOid =
                externalTrackNumberIds[trackNumber.id]?.oid
                    ?: throw ExtOidNotFoundExceptionV1(
                        "track number oid not found, layoutTrackNumberId=${trackNumber.id}"
                    )

            ExtTrackNumberV1(
                trackNumberOid = trackNumberOid,
                trackNumber = trackNumber.number,
                trackNumberDescription = trackNumber.description,
                trackNumberState = trackNumber.state.let(ExtTrackNumberStateV1::of),
                startLocation = startLocation?.let(::ExtAddressPointV1),
                endLocation = endLocation?.let(::ExtAddressPointV1),
            )
        }
    }
}
