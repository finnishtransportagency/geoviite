package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.ratko.RatkoLocalService
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class LayoutSearchService
@Autowired
constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val trackNumberService: LayoutTrackNumberService,
    private val ratkoLocalService: RatkoLocalService,
) {

    fun searchAssets(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
        locationTrackSearchScope: IntId<LocationTrack>?,
    ): TrackLayoutSearchResult {
        return if (locationTrackSearchScope != null) {
            searchByLocationTrackSearchScope(layoutContext, locationTrackSearchScope, searchTerm, limitPerResultType)
        } else {
            searchFromEntireRailwayNetwork(layoutContext, searchTerm, limitPerResultType)
        }
    }

    fun searchAllLocationTracks(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LocationTrack> {
        return locationTrackService
            .list(layoutContext, true)
            .let { list ->
                locationTrackService.filterBySearchTerm(list, searchTerm, locationTrackService.idMatches(layoutContext))
            }
            .sortedBy(LocationTrack::name)
            .take(limit)
    }

    fun searchAllSwitches(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<TrackLayoutSwitch> {
        return switchService
            .list(layoutContext, true)
            .let { list -> switchService.filterBySearchTerm(list, searchTerm, switchService.idMatches(layoutContext)) }
            .sortedBy(TrackLayoutSwitch::name)
            .take(limit)
    }

    fun searchAllTrackNumbers(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limit: Int,
    ): List<TrackLayoutTrackNumber> {
        return trackNumberService
            .list(layoutContext, true)
            .let { list ->
                trackNumberService.filterBySearchTerm(list, searchTerm, trackNumberService.idMatches(layoutContext))
            }
            .sortedBy(TrackLayoutTrackNumber::number)
            .take(limit)
    }

    private fun searchFromEntireRailwayNetwork(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
    ) =
        TrackLayoutSearchResult(
            switches = searchAllSwitches(layoutContext, searchTerm, limitPerResultType),
            locationTracks = searchAllLocationTracks(layoutContext, searchTerm, limitPerResultType),
            trackNumbers = searchAllTrackNumbers(layoutContext, searchTerm, limitPerResultType),
            operatingPoints = ratkoLocalService.searchOperatingPoints(searchTerm, limitPerResultType),
        )

    private fun searchByLocationTrackSearchScope(
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
        searchTerm: FreeText,
        limit: Int,
    ): TrackLayoutSearchResult {
        val switches =
            locationTrackService.getSwitchesForLocationTrack(layoutContext, locationTrackSearchScope).let { ids ->
                switchService.getMany(layoutContext, ids)
            }
        val locationTracks = getLocationTrackAndDuplicatesByScope(layoutContext, locationTrackSearchScope)
        val trackNumbers = emptyList<TrackLayoutTrackNumber>()
        val switchIdMatch = switchService.idMatches(layoutContext, switches.map { it.id as IntId })
        val ltIdMatch = locationTrackService.idMatches(layoutContext, locationTracks.map { it.id as IntId })
        val trackNumberIdMatch = trackNumberService.idMatches(layoutContext, trackNumbers.map { it.id as IntId })
        return TrackLayoutSearchResult(
            switches =
                switches.let { list -> switchService.filterBySearchTerm(list, searchTerm, switchIdMatch) }.take(limit),
            locationTracks =
                locationTracks
                    .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm, ltIdMatch) }
                    .take(limit),
            trackNumbers =
                trackNumbers
                    .let { list -> trackNumberService.filterBySearchTerm(list, searchTerm, trackNumberIdMatch) }
                    .take(limit),
            operatingPoints = ratkoLocalService.searchOperatingPoints(searchTerm, limit),
        )
    }

    private fun getLocationTrackAndDuplicatesByScope(
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
    ): List<LocationTrack> =
        locationTrackService
            .getWithGeometryOrThrow(layoutContext, locationTrackSearchScope)
            .let { (lt, alignment) ->
                lt to locationTrackService.getLocationTrackDuplicates(layoutContext, lt, alignment)
            }
            .let { (locationTrack, duplicates) ->
                listOf(locationTrack) + locationTrackService.getMany(layoutContext, duplicates.map { d -> d.id })
            }
}
