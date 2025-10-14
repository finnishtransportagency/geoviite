package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtTrackNumberKmServiceV1
@Autowired
constructor(private val geocodingService: GeocodingService, private val layoutTrackNumberDao: LayoutTrackNumberDao) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createTrackKmResponse(
        trackNumberOid: Oid<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackKmsResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumberId =
            layoutTrackNumberDao.lookupByExternalId(trackNumberOid)?.id
                ?: throw ExtOidNotFoundExceptionV1("track number lookup failed for oid=$trackNumberOid")
        return layoutTrackNumberDao.getOfficialAtMoment(branch, trackNumberId, moment)?.let { trackNumber ->
            ExtTrackKmsResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = coordinateSystem,
                trackNumberKms = getExtTrackKms(trackNumberOid, trackNumber, branch, moment, coordinateSystem),
            )
        }
    }

    fun createTrackNumberKmsCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackKmsCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumbers =
            layoutTrackNumberDao
                .listOfficialAtMoment(publication.layoutBranch.branch, publication.publicationTime)
                .filter { trackNumber -> trackNumber.state != LayoutState.DELETED }
        val trackNumberIds = trackNumbers.map { trackNumber -> trackNumber.id as IntId }
        val externalTrackNumberIds = layoutTrackNumberDao.fetchExternalIds(branch, trackNumberIds)
        return ExtTrackKmsCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = coordinateSystem,
            trackNumberKms =
                trackNumbers.map { trackNumber ->
                    val trackNumberOid =
                        externalTrackNumberIds[trackNumber.id as IntId]?.oid
                            ?: throw ExtOidNotFoundExceptionV1(
                                "track number oid not found: layoutTrackNumberId=${trackNumber.id}"
                            )
                    getExtTrackKms(trackNumberOid, trackNumber, branch, moment, coordinateSystem)
                },
        )
    }

    private fun getExtTrackKms(
        trackNumberOid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
    ): ExtTrackNumberKmsV1 {
        val trackNumberId = trackNumber.id as IntId
        val geocodingContext =
            trackNumberId
                .takeIf { trackNumber.exists }
                ?.let { id -> geocodingService.getGeocodingContextAtMoment(branch, id, moment) }
        return ExtTrackNumberKmsV1(
            trackNumber = trackNumber.number,
            trackNumberOid = trackNumberOid,
            trackKms =
                geocodingContext?.kms?.map { km ->
                    val kmPost = geocodingContext.kmPosts.find { kmp -> kmp.kmNumber == km.kmNumber }
                    ExtTrackKmV1(
                        type = if (kmPost == null) ExtTrackKmTypeV1.TRACK_NUMBER_START else ExtTrackKmTypeV1.KM_POST,
                        kmNumber = km.kmNumber,
                        kmLength = roundTo3Decimals(km.length),
                        officialLocation = kmPost?.let { kmp -> getOfficialLocation(trackNumberId, kmp) },
                        location = getKmStart(trackNumberId, kmPost, geocodingContext, coordinateSystem),
                    )
                } ?: emptyList(),
        )
    }

    private fun getOfficialLocation(
        trackNumberId: IntId<LayoutTrackNumber>,
        kmPost: LayoutKmPost,
    ): ExtSridCoordinateV1 =
        requireNotNull(kmPost.gkLocation?.location?.let(::ExtSridCoordinateV1)) {
            "An active km post in official layout must have an official GK location: id=${kmPost.id} kmNumber=${kmPost.kmNumber} trackNumberId=$trackNumberId"
        }

    private fun getKmStart(
        trackNumberId: IntId<LayoutTrackNumber>,
        kmPost: LayoutKmPost?,
        geocodingContext: GeocodingContext<ReferenceLineM>,
        coordinateSystem: Srid,
    ): ExtCoordinateV1 =
        when {
            kmPost != null ->
                requireNotNull(kmPost.layoutLocation) {
                    "An active km post in official layout must have a location: id=${kmPost.id} kmNumber=${kmPost.kmNumber} trackNumberId=$trackNumberId"
                }
            else ->
                requireNotNull(geocodingContext.referenceLineGeometry.start) {
                    "A reference line in official layout must have a start location: trackNumberId=$trackNumberId"
                }
        }.let { layoutLocation -> toExtCoordinate(layoutLocation, coordinateSystem) }
}
