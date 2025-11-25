package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackNumberKmServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val trackNumberDao: LayoutTrackNumberDao,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackNumberKmsCollection(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackKmsCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackNumberKmsCollectionResponse(publication, coordinateSystem ?: LAYOUT_SRID)
    }

    fun getExtTrackNumberKms(
        oid: Oid<LayoutTrackNumber>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtTrackKmsResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        return createTrackKmResponse(oid, publication, coordinateSystem ?: LAYOUT_SRID)
    }

    private fun createTrackKmResponse(
        trackNumberOid: Oid<LayoutTrackNumber>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackKmsResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumberId = idLookup(trackNumberDao, trackNumberOid)
        return trackNumberDao
            .getOfficialAtMoment(branch, trackNumberId, moment)
            ?.takeIf { it.exists }
            ?.let { trackNumber ->
                val geocodingContext =
                    geocodingService.getGeocodingContextAtMoment(branch, trackNumber.id as IntId, moment)
                ExtTrackKmsResponseV1(
                    trackLayoutVersion = publication.uuid,
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackNumberKms = getExtTrackKms(trackNumberOid, trackNumber, geocodingContext, coordinateSystem),
                )
            }
    }

    private fun createTrackNumberKmsCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtTrackKmsCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val trackNumbers =
            trackNumberDao.listOfficialAtMoment(publication.layoutBranch.branch, publication.publicationTime).filter {
                it.exists
            }
        val trackNumberIds = trackNumbers.map { trackNumber -> trackNumber.id as IntId }
        val trackNumberExtIds = trackNumberDao.fetchExternalIds(branch, trackNumberIds)
        val geocodingContexts =
            trackNumbers.associate {
                it.id to geocodingService.getGeocodingContextAtMoment(branch, it.id as IntId, moment)
            }
        return ExtTrackKmsCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = ExtSridV1(coordinateSystem),
            trackNumberKms =
                trackNumbers.map { tn ->
                    val oid = trackNumberExtIds[tn.id as IntId]?.oid ?: throwOidNotFound(branch, tn.id)
                    getExtTrackKms(oid, tn, geocodingContexts[tn.id], coordinateSystem)
                },
        )
    }

    private fun getExtTrackKms(
        trackNumberOid: Oid<LayoutTrackNumber>,
        trackNumber: LayoutTrackNumber,
        geocodingContext: GeocodingContext<ReferenceLineM>?,
        coordinateSystem: Srid,
    ): ExtTrackNumberKmsV1 {
        return ExtTrackNumberKmsV1(
            trackNumber = trackNumber.number,
            trackNumberOid = trackNumberOid,
            trackKms =
                geocodingContext?.kms?.map { km ->
                    val kmPost = geocodingContext.kmPosts.find { kmp -> kmp.kmNumber == km.kmNumber }
                    val officialLocation = kmPost?.let { kmp -> getOfficialLocation(geocodingContext.trackNumber, kmp) }
                    ExtTrackKmV1(
                        type = if (kmPost == null) ExtTrackKmTypeV1.TRACK_NUMBER_START else ExtTrackKmTypeV1.KM_POST,
                        kmNumber = km.kmNumber,
                        // The first KM might not start at 0 meters -> the km start is on the previous TrackNumber
                        startM = roundTo3Decimals(km.referenceLineM.min) - roundTo3Decimals(km.startMeters),
                        endM = roundTo3Decimals(km.referenceLineM.max),
                        officialLocation = officialLocation,
                        location = getKmStart(kmPost, geocodingContext, coordinateSystem),
                    )
                } ?: emptyList(),
        )
    }

    private fun getOfficialLocation(trackNumber: TrackNumber, kmPost: LayoutKmPost): ExtKmPostOfficialLocationV1 =
        requireNotNull(kmPost.gkLocation?.let(::ExtKmPostOfficialLocationV1)) {
            "An active km post in official layout must have an official GK location: id=${kmPost.id} kmNumber=${kmPost.kmNumber} trackNumber=$trackNumber"
        }

    private fun getKmStart(
        kmPost: LayoutKmPost?,
        geocodingContext: GeocodingContext<ReferenceLineM>,
        coordinateSystem: Srid,
    ): ExtCoordinateV1 =
        when {
            kmPost != null ->
                requireNotNull(kmPost.layoutLocation) {
                    "An active km post in official layout must have a location: kmPostId=${kmPost.id} kmNumber=${kmPost.kmNumber} trackNumber=${geocodingContext.trackNumber}"
                }
            else ->
                requireNotNull(geocodingContext.referenceLineGeometry.start) {
                    "A reference line in official layout must have a start location: trackNumber=${geocodingContext.trackNumber}"
                }
        }.let { layoutLocation -> toExtCoordinate(layoutLocation, coordinateSystem) }
}
