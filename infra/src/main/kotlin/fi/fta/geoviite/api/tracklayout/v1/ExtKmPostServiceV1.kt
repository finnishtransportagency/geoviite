package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtKmPostServiceV1
@Autowired
constructor(
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val publicationDao: PublicationDao,
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
        return publicationDao
            .fetchPublishedLocationTrackBetween(id, publications.from.publicationTime, publications.to.publicationTime)
        TODO()
    }

    fun createKmPostCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedKmPostCollectionResponseV1? {
        TODO()
    }

    private fun getExtKmPost(
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
        val officialLocation =
            requireNotNull(kmPost.gkLocation?.let { gkLocation -> ExtSridCoordinateV1(gkLocation.location) }) {
                "A published km-post must always have an official location: kmPost=${kmPost.id}"
            }
        val location =
            requireNotNull(kmPost.layoutLocation?.let { location -> toExtCoordinate(location, coordinateSystem) }) {
                "A published km-post must always have a layout location: kmPost=${kmPost.id}"
            }
        val trackNumber =
            layoutTrackNumberDao.getOfficialAtMoment(branch, trackNumberId, moment)
                ?: throw ExtTrackNumberNotFoundV1(
                    "track number was not found for branch=$branch, trackNumberId=$trackNumberId, moment=$moment"
                )
        val trackNumberOid =
            layoutTrackNumberDao.fetchExternalId(branch, trackNumberId)?.oid
                ?: throw ExtOidNotFoundExceptionV1(
                    "track number oid was not found, branch=$branch, trackNumberId=$trackNumberId"
                )
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, trackNumberId, moment)
        val geocodingKm = geocodingContext?.kms?.find { km -> km.kmNumber == kmPost.kmNumber }

        return ExtKmPostV1(
            kmPostOid = oid,
            trackNumber = trackNumber.number,
            trackNumberOid = trackNumberOid,
            kmNumber = kmPost.kmNumber,
            state = ExtKmPostStateV1.of(kmPost.state),
            kmLength = geocodingKm?.length?.let(::roundTo3Decimals),
            officialLocation = officialLocation,
            location = location,
        )
    }
}
