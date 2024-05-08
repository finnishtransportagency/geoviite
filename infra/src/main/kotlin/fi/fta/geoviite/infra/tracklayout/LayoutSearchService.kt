package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
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
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
        locationTrackSearchScope: IntId<LocationTrack>?,
    ): TrackLayoutSearchResult {
        logger.serviceCall(
            "searchAssets",
            "layoutContext" to layoutContext,
            "searchTerm" to searchTerm,
            "limitPerResultType" to limitPerResultType,
            "locationTrackSearchScope" to locationTrackSearchScope,
        )

        return if (locationTrackSearchScope != null) {
            searchByLocationTrackSearchScope(layoutContext, locationTrackSearchScope, searchTerm, limitPerResultType)
        } else {
            searchFromEntireRailwayNetwork(layoutContext, searchTerm, limitPerResultType)
        }
    }

    fun searchAllLocationTracks(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LocationTrack> {
        logger.serviceCall(
            "searchAllLocationTracks",
            "layoutContext" to layoutContext,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return locationTrackService.list(layoutContext, true)
            .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(LocationTrack::name)
            .take(limit)
    }

    fun searchAllSwitches(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limit: Int,
    ): List<TrackLayoutSwitch> {
        logger.serviceCall(
            "searchAllLayoutSwitches",
            "layoutContext" to layoutContext,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return switchService.list(layoutContext, true)
            .let { list -> switchService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(TrackLayoutSwitch::name)
            .take(limit)
    }

    fun searchAllTrackNumbers(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "searchAllTrackNumbers",
            "layoutContext" to layoutContext,
            "searchTerm" to searchTerm,
            "limit" to limit,
        )

        return trackNumberService.list(layoutContext, true)
            .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm) }
            .sortedBy(TrackLayoutTrackNumber::number)
            .take(limit)
    }

    private fun searchFromEntireRailwayNetwork(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
    ) = TrackLayoutSearchResult(
        switches = searchAllSwitches(layoutContext, searchTerm, limitPerResultType),
        locationTracks = searchAllLocationTracks(layoutContext, searchTerm, limitPerResultType),
        trackNumbers = searchAllTrackNumbers(layoutContext, searchTerm, limitPerResultType),
    )

    private fun searchByLocationTrackSearchScope(
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
        searchTerm: FreeText,
        limit: Int,
    ): TrackLayoutSearchResult {
        val switches = locationTrackService
            .getSwitchesForLocationTrack(layoutContext, locationTrackSearchScope)
            .let { ids -> switchService.getMany(layoutContext, ids) }
        val locationTracks = getLocationTrackAndDuplicatesByScope(layoutContext, locationTrackSearchScope)
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
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
    ): List<LocationTrack> = locationTrackService
        .getWithAlignmentOrThrow(layoutContext, locationTrackSearchScope)
        .let { (lt, alignment) ->
            lt to locationTrackService.getLocationTrackDuplicates(layoutContext, lt, alignment)
        }
        .let { (locationTrack, duplicates) ->
            listOf(locationTrack) + locationTrackService.getMany(layoutContext, duplicates.map { d -> d.id })
        }
}
