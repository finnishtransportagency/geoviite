package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtKmPostServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createKmPostResponse(
        oid: Oid<LayoutKmPost>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtKmPostResponseV1? {
        val kmPostId =
            kmPostDao.lookupByExternalId(oid.toString())?.id
                ?: throw ExtOidNotFoundExceptionV1("km post lookup failed for oid=$oid")
        kmPostDao.
        kmPostService.by
        kmPostDao.getOfficialAtMoment(LayoutBranch.main,publication.publicationTime)
        TODO()
    }

    fun createKmPostCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtKmPostCollectionResponseV1 {
        TODO()
    }

    fun createKmPostModificationResponse(
        oid: Oid<LayoutKmPost>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedKmPostResponseV1? {
        TODO()
    }

    fun createKmPostCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedKmPostCollectionResponseV1? {
        TODO()
    }
}
