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
        locationTrackSearchScope: IntId<LocationTrack>?,
    ): TrackLayoutSearchResult {
        logger.serviceCall(
            "searchAssets",
            PUBLICATION_STATE to publicationState,
            "searchTerm" to searchTerm,
            "limitPerResultType" to limitPerResultType,
            "locationTrackSearchScope" to locationTrackSearchScope,
        )

        return if (locationTrackSearchScope != null) {
            searchByLocationTrackSearchScope(locationTrackSearchScope, publicationState, searchTerm, limitPerResultType)
        } else {
            searchFromEntireRailwayNetwork(publicationState, searchTerm, limitPerResultType)
        }
    }

    private fun searchFromEntireRailwayNetwork(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limitPerResultType: Int,
    ) = TrackLayoutSearchResult(
        switches = searchAllSwitches(publicationState, searchTerm, limitPerResultType),
        locationTracks = searchAllLocationTracks(publicationState, searchTerm, limitPerResultType),
        trackNumbers = searchAllTrackNumbers(publicationState, searchTerm, limitPerResultType),
    )

    private fun searchByLocationTrackSearchScope(
        locationTrackSearchScope: IntId<LocationTrack>,
        publicationState: PublicationState,
        searchTerm: FreeText,
        limitPerResultType: Int,
    ): TrackLayoutSearchResult {
        val switches = locationTrackService
            .getSwitchesForLocationTrack(locationTrackSearchScope, publicationState)
            .let { ids -> switchService.getMany(publicationState, ids) }
        val locationTracks = locationTrackService.getWithAlignmentOrThrow(publicationState, locationTrackSearchScope).let { (lt, alignment) ->
                val duplicates = locationTrackService
                    .getLocationTrackDuplicates(lt, alignment, publicationState)
                    .map { it.id }
                    .let { ids -> locationTrackService.getMany(publicationState, ids) }

                listOf(lt) + duplicates
            }
        val trackNumbers = emptyList<TrackLayoutTrackNumber>()

        return TrackLayoutSearchResult(
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
    }

    fun searchAllLocationTracks(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int?,
    ): List<LocationTrack> {
        logger.serviceCall(
            "searchAllLocationTracks",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return locationTrackService.list(publicationState, true)
            .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm) }
            .let { list -> locationTrackService.sortSearchResult(list) }
            .let { list -> if (limit != null) list.take(limit) else list }
    }

    fun searchAllSwitches(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int?,
    ): List<TrackLayoutSwitch> {
        logger.serviceCall(
            "searchAllLayoutSwitches",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return switchService.list(publicationState, true)
            .let { list -> switchService.filterBySearchTerm(list, searchTerm) }
            .let { list -> switchService.sortSearchResult(list) }
            .let { list -> if (limit != null) list.take(limit) else list }
    }

    fun searchAllTrackNumbers(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int?,
    ): List<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "searchAllTrackNumbers",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return trackNumberService.list(publicationState, true)
            .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm) }
            .let { list -> trackNumberService.sortSearchResult(list) }
            .let { list -> if (limit != null) list.take(limit) else list }
    }
}
