package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService

@GeoviiteService
class ExtDesignServiceV1
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val layoutDesignDao: LayoutDesignDao,
    private val layoutDesignService: LayoutDesignService,
) {

    fun getExtDesignCollection(): ExtDesignCollectionResponseV1 =
        layoutDesignDao
            .list(includeCompleted = false, includeDeleted = false)
            .map(ExtDesignV1::of)
            .let(::ExtDesignCollectionResponseV1)

    fun getExtDesign(designOid: ExtOidV1<LayoutDesign>): ExtDesignResponseV1 =
        ExtDesignResponseV1(ExtDesignV1.of(designByOid(designOid.value)))

    fun getExtDesignModifications(
        designOid: ExtOidV1<LayoutDesign>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ExtModifiedDesignResponseV1? {
        val designId = designByOid(designOid.value).id as IntId
        return publicationService
            .getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value, branchType = null)
            .let { publications ->
                if (publications.areDifferent()) {
                    createDesignModificationResponse(designId, publications)
                } else {
                    publicationsAreTheSame(layoutVersionFrom.value)
                }
            }
    }

    fun getExtDesignCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ExtModifiedDesignCollectionResponseV1? =
        publicationService
            .getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value, branchType = null)
            .let { publications ->
                if (publications.areDifferent()) {
                    createDesignCollectionModificationResponse(publications)
                } else {
                    publicationsAreTheSame(layoutVersionFrom.value)
                }
            }

    private fun createDesignModificationResponse(
        designId: IntId<LayoutDesign>,
        publications: PublicationComparison,
    ): ExtModifiedDesignResponseV1? =
        publicationDao
            .fetchDesignModificationsBetween(
                publications.from.publicationTime,
                publications.to.publicationTime,
                designId,
            )
            .maxByOrNull { designVersion -> designVersion.version }
            ?.let(layoutDesignDao::fetchVersion)
            ?.let { design ->
                ExtModifiedDesignResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    design = ExtDesignV1.of(design),
                )
            } ?: layoutAssetWasUnmodified(designId, publications)

    private fun createDesignCollectionModificationResponse(
        publications: PublicationComparison
    ): ExtModifiedDesignCollectionResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchDesignModificationsBetween(startMoment, endMoment, null)
            // Several modifications of one design within the compared range map to a single reported design state
            .groupBy { designVersion -> designVersion.id }
            .map { (_, designVersions) -> designVersions.maxBy { it.version } }
            .map(layoutDesignDao::fetchVersion)
            .map(ExtDesignV1::of)
            .takeIf { it.isNotEmpty() }
            ?.let { extDesigns ->
                ExtModifiedDesignCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    designCollection = extDesigns,
                )
            } ?: layoutAssetCollectionWasUnmodified<LayoutDesign>(publications)
    }

    // Design metadata remains fetchable by oid even for completed and deleted designs: the design state field
    // exists to communicate exactly those states.
    private fun designByOid(oid: Oid<LayoutDesign>): LayoutDesign =
        layoutDesignService.getByOid(oid) ?: throwOidTargetNotFound(oid)
}
