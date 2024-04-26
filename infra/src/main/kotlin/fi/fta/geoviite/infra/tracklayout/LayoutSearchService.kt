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

    fun searchAllLocationTracks(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int,
    ): List<LocationTrack> {
        logger.serviceCall(
            "searchAllLocationTracks",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return locationTrackService.list(publicationState, true)
            .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(LocationTrack::name)
            .take(limit)
    }

    fun searchAllSwitches(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int,
    ): List<TrackLayoutSwitch> {
        logger.serviceCall(
            "searchAllLayoutSwitches",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return switchService.list(publicationState, true)
            .let { list -> switchService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(TrackLayoutSwitch::name)
            .take(limit)
    }

    fun searchAllTrackNumbers(
        publicationState: PublicationState,
        searchTerm: FreeText,
        limit: Int,
    ): List<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "searchAllTrackNumbers",
            "publicationState" to publicationState,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return trackNumberService.list(publicationState, true)
            .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(TrackLayoutTrackNumber::number)
            .take(limit)
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
        limit: Int,
    ): TrackLayoutSearchResult {
        val switches = locationTrackService
            .getSwitchesForLocationTrack(locationTrackSearchScope, publicationState)
            .let { ids -> switchService.getMany(publicationState, ids) }
        val locationTracks = getLocationTrackAndDuplicatesByScope(locationTrackSearchScope, publicationState)
        val trackNumbers = emptyList<TrackLayoutTrackNumber>()

        return TrackLayoutSearchResult(
            switches = switches
                .let { list -> switchService.filterBySearchTerm(list, searchTerm) }
                .take(limit),
            locationTracks = locationTracks
                .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm) }
                .take(limit),
            trackNumbers = trackNumbers
                .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm) }
                .take(limit)
        )
    }

    private fun getLocationTrackAndDuplicatesByScope(
        locationTrackSearchScope: IntId<LocationTrack>,
        publicationState: PublicationState,
    ): List<LocationTrack> = locationTrackService
        .getWithAlignmentOrThrow(publicationState, locationTrackSearchScope)
        .let { (lt, alignment) ->
            lt to locationTrackService.getLocationTrackDuplicates(
                lt,
                alignment,
                publicationState
            )
        }
        .let { (locationTrack, duplicates) ->
            listOf(locationTrack) + locationTrackService.getMany(
                publicationState,
                duplicates.map { d -> d.id })
        }
}
