package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtPublicationServiceV1 @Autowired constructor(private val publicationDao: PublicationDao) {

    fun getPublicationsToCompare(
        modificationsFromVersion: Uuid<Publication>,
        trackLayoutVersion: Uuid<Publication>?,
    ): Pair<Publication, Publication> {
        val fromPublication =
            publicationDao.fetchPublicationByUuid(modificationsFromVersion)
                ?: throw ExtTrackLayoutVersionNotFound("modificationsFromVersion=${modificationsFromVersion}")

        val toPublication =
            trackLayoutVersion?.let { uuid ->
                publicationDao.fetchPublicationByUuid(uuid)
                    ?: throw ExtTrackLayoutVersionNotFound("trackLayoutVersion=${uuid}")
            } ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        if (fromPublication.id.intValue > toPublication.id.intValue) {
            throw ExtWrongTrackLayoutVersionOrder(
                "fromPublication=${fromPublication} is strictly newer than toPublication=${toPublication}"
            )
        }

        return fromPublication to toPublication
    }
}
