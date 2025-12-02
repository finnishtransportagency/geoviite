package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.publication.PublicationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtTrackBoundaryChangeServiceV1 @Autowired constructor(private val publicationService: PublicationService) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtTrackBoundaryChangeCollection(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
    ): ExtTrackBoundaryChangeResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        TODO("Not implemented")
    }
}
