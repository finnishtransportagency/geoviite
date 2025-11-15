package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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
    private val publicationService: PublicationService,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackNumberCollection(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackNumberCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackNumberCollectionResponse(publication, coordinateSystem = coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtTrackNumberCollectionModifications(
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        return if (publications.areDifferent()) {
            createTrackNumberCollectionModificationResponse(publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    fun getExtTrackNumber(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackNumberResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackNumberResponse(oid, idLookup(oid), publication, coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtTrackNumberModifications(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedTrackNumberResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        val id = idLookup(oid) // Lookup before change check to produce consistent error if oid is not found
        return if (publications.areDifferent()) {
            createTrackNumberModificationResponse(oid, id, publications, coordinateSystem ?: LAYOUT_SRID)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    private fun idLookup(oid: Oid<LayoutTrackNumber>): IntId<LayoutTrackNumber> =
        layoutTrackNumberDao.lookupByExternalId(oid)?.id
            ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$oid")

    private fun createTrackNumberResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return layoutTrackNumberDao.getOfficialAtMoment(branch, id, moment)?.let { trackNumber ->
            ExtTrackNumberResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = coordinateSystem,
                trackNumber = createExtTrackNumber(oid, trackNumber, branch, moment, coordinateSystem),
            )
        }
    }

    private fun createTrackNumberModificationResponse(
        oid: Oid<LayoutTrackNumber>,
        id: IntId<LayoutTrackNumber>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumberBetween(id, startMoment, endMoment)
            ?.let(layoutTrackNumberDao::fetch)
            ?.let { trackNumber ->
                ExtModifiedTrackNumberResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumber = createExtTrackNumber(oid, trackNumber, branch, endMoment, coordinateSystem),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createTrackNumberCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackNumberCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumbers = layoutTrackNumberDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        return ExtTrackNumberCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            trackNumberCollection = extGetTrackNumberCollection(branch, moment, trackNumbers, coordinateSystem),
        )
    }

    private fun createTrackNumberCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedTrackNumberCollectionResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedTrackNumbersBetween(startMoment, endMoment)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(layoutTrackNumberDao::fetchMany)
            ?.let { modifiedTrackNumbers ->
                ExtModifiedTrackNumberCollectionResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    coordinateSystem = coordinateSystem,
                    trackNumberCollection =
                        extGetTrackNumberCollection(branch, endMoment, modifiedTrackNumbers, coordinateSystem),
                )
            } ?: layoutAssetCollectionWasUnmodified<LayoutTrackNumber>(publications)
    }

    fun extGetTrackNumberCollection(
        branch: LayoutBranch,
        moment: Instant,
        trackNumbers: List<LayoutTrackNumber>,
        coordinateSystem: Srid,
    ): List<ExtTrackNumberV1> {
        val trackNumberIds = trackNumbers.map { trackNumber -> trackNumber.id as IntId }

        val referenceLineStartsAndEnds =
            referenceLineService
                .getStartAndEndAtMoment(
                    branch,
                    trackNumbers.mapNotNull { trackNumber -> trackNumber.referenceLineId },
                    moment,
                )
                .associateBy { it.id }

        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(branch, trackNumberIds)

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

    fun createExtTrackNumber(
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
