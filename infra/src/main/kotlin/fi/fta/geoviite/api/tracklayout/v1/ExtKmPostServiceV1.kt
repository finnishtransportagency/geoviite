package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtKmPostServiceV1
@Autowired
constructor(
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
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

        return kmPostDao
            .getOfficialAtMoment(publication.layoutBranch.branch, kmPostId, publication.publicationTime)
            ?.let { kmp ->
                ExtKmPostResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = coordinateSystem,
                    kmPost =
                        getExtKmPost(
                            oid,
                            kmp,
                            publication.layoutBranch.branch,
                            publication.publicationTime,
                            coordinateSystem,
                        ),
                )
            }
    }

    fun getExtKmPost(
        oid: Oid<LayoutKmPost>,
        kmPost: LayoutKmPost,
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtKmPostV1 {
        val trackNumberId =
            requireNotNull(kmPost.trackNumberId) {
                "A published km-post must always have a track number: kmPost=${kmPost.id}"
            }
        val trackNumber = layoutTrackNumberDao.getOfficialAtMoment(branch, trackNumberId, moment)
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, trackNumberId, moment)
        //        kmp.trackNumberId
        //            ?.let { tnId ->
        //                geocodingDao.getLayoutGeocodingContextCacheKey(
        //                    publication.layoutBranch.branch,
        //                    tnId,
        //                    publication.publicationTime,
        //                )
        //            }
        //            ?.let { kmp to it }
        //    }
        //    ?.let
        //    {
        //        (kmp, geocodingContext) ->
        //    }
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
