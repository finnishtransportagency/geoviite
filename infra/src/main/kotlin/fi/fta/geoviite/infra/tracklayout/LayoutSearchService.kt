package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LayoutSearchService @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val trackNumberService: LayoutTrackNumberService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun searchAssets(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limitPerResultType: Int,
        contextLocationTrackId: IntId<LocationTrack>?,
    ): TrackLayoutSearchResult {
        logger.serviceCall(
            "searchAssets",
            PUBLICATION_STATE to publicationState,
            "searchTerm" to searchTerm,
            "limitPerResultType" to limitPerResultType,
            "contextLocationTrackId" to contextLocationTrackId,
        )

        return if (contextLocationTrackId != null) {
            val switches = locationTrackService.getSwitchesForLocationTrack(contextLocationTrackId, publicationState)
                .let { ids -> switchService.getMany(publicationState, ids) }
            val locationTracks = locationTrackService
                .getWithAlignmentOrThrow(publicationState, contextLocationTrackId)
                .let { (lt, alignment) ->
                    val duplicates = locationTrackService
                        .getLocationTrackDuplicates(lt, alignment, publicationState)
                        .map { it.id }
                        .let { ids -> locationTrackService.getMany(publicationState, ids) }

                    listOf(lt) + duplicates
                }
            val trackNumbers = emptyList<TrackLayoutTrackNumber>()

            TrackLayoutSearchResult(
                switches = switches
                    .let { list -> switchService.filterBySearchTerm(list, searchTerm) }
                    .take(limitPerResultType),
                locationTracks = locationTracks
                    .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm) }
                    .take(limitPerResultType),
                trackNumbers = trackNumbers
                    .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm) }
                    .take(limitPerResultType)
            )
        } else {
            TrackLayoutSearchResult(
                switches = switchService.list(publicationState, searchTerm, limitPerResultType),
                locationTracks = locationTrackService.list(publicationState, searchTerm, limitPerResultType),
                trackNumbers = trackNumberService.list(publicationState, searchTerm, limitPerResultType),
            )
        }
    }
}
