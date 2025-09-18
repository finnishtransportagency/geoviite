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
    private val kmPostService: LayoutKmPostService,
    private val ratkoLocalService: RatkoLocalService,
) {

    fun searchAssets(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
        locationTrackSearchScope: IntId<LocationTrack>?,
        searchedAssetTypes: List<TrackLayoutSearchedAssetType>,
    ): TrackLayoutSearchResult {
        return if (locationTrackSearchScope != null) {
            searchByLocationTrackSearchScope(
                layoutContext,
                locationTrackSearchScope,
                searchTerm,
                limitPerResultType,
                searchedAssetTypes,
            )
        } else {
            searchFromEntireRailwayNetwork(layoutContext, searchTerm, limitPerResultType, searchedAssetTypes)
        }
    }

    fun searchAllLocationTracks(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LocationTrack> {
        return locationTrackService
            .list(layoutContext, true)
            .let { list ->
                locationTrackService.filterBySearchTerm(
                    list,
                    searchTerm,
                    locationTrackService.idMatches(layoutContext, searchTerm),
                )
            }
            .sortedBy(LocationTrack::name)
            .take(limit)
    }

    fun searchAllSwitches(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LayoutSwitch> {
        return switchService
            .list(layoutContext, true)
            .let { list ->
                switchService.filterBySearchTerm(list, searchTerm, switchService.idMatches(layoutContext, searchTerm))
            }
            .sortedBy(LayoutSwitch::name)
            .take(limit)
    }

    fun searchAllTrackNumbers(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LayoutTrackNumber> {
        return trackNumberService
            .list(layoutContext, true)
            .let { list ->
                trackNumberService.filterBySearchTerm(
                    list,
                    searchTerm,
                    trackNumberService.idMatches(layoutContext, searchTerm),
                )
            }
            .sortedBy(LayoutTrackNumber::number)
            .take(limit)
    }

    fun searchAllKmPosts(layoutContext: LayoutContext, searchTerm: FreeText, limit: Int): List<LayoutKmPost> =
        kmPostService
            .list(layoutContext, true)
            .let { list -> kmPostService.filterBySearchTerm(list, searchTerm, kmPostService.idMatches()) }
            .sortedBy(LayoutKmPost::kmNumber)
            .take(limit)

    private fun searchFromEntireRailwayNetwork(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        limitPerResultType: Int,
        searchedAssetTypes: List<TrackLayoutSearchedAssetType>,
    ) =
        TrackLayoutSearchResult(
            switches =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.SWITCH))
                    searchAllSwitches(layoutContext, searchTerm, limitPerResultType)
                else emptyList(),
            locationTracks =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.LOCATION_TRACK))
                    searchAllLocationTracks(layoutContext, searchTerm, limitPerResultType)
                else emptyList(),
            trackNumbers =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.TRACK_NUMBER))
                    searchAllTrackNumbers(layoutContext, searchTerm, limitPerResultType)
                else emptyList(),
            kmPosts =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.KM_POST))
                    searchAllKmPosts(layoutContext, searchTerm, limitPerResultType)
                else emptyList(),
            operatingPoints =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.OPERATING_POINT))
                    ratkoLocalService.searchOperatingPoints(searchTerm, limitPerResultType)
                else emptyList(),
        )

    private fun searchByLocationTrackSearchScope(
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
        searchTerm: FreeText,
        limit: Int,
        searchedAssetTypes: List<TrackLayoutSearchedAssetType>,
    ): TrackLayoutSearchResult {
        val switches =
            if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.SWITCH))
                locationTrackService.getSwitchesForLocationTrack(layoutContext, locationTrackSearchScope).let { ids ->
                    switchService.getMany(layoutContext, ids)
                }
            else emptyList()
        val locationTracks =
            if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.LOCATION_TRACK))
                getLocationTrackAndDuplicatesByScope(layoutContext, locationTrackSearchScope)
            else emptyList()

        val switchIdMatch = switchService.idMatches(layoutContext, searchTerm, switches.map { it.id as IntId })
        val ltIdMatch = locationTrackService.idMatches(layoutContext, searchTerm, locationTracks.map { it.id as IntId })

        return TrackLayoutSearchResult(
            switches =
                switches.let { list -> switchService.filterBySearchTerm(list, searchTerm, switchIdMatch) }.take(limit),
            locationTracks =
                locationTracks
                    .let { list -> locationTrackService.filterBySearchTerm(list, searchTerm, ltIdMatch) }
                    .take(limit),
            trackNumbers = emptyList(),
            kmPosts = emptyList(),
            operatingPoints =
                if (searchedAssetTypes.contains(TrackLayoutSearchedAssetType.OPERATING_POINT))
                    ratkoLocalService.searchOperatingPoints(searchTerm, limit)
                else emptyList(),
        )
    }

    private fun getLocationTrackAndDuplicatesByScope(
        layoutContext: LayoutContext,
        locationTrackSearchScope: IntId<LocationTrack>,
    ): List<LocationTrack> =
        locationTrackService
            .getWithGeometryOrThrow(layoutContext, locationTrackSearchScope)
            .let { (lt, geometry) ->
                lt to locationTrackService.getLocationTrackDuplicates(layoutContext, lt, geometry)
            }
            .let { (locationTrack, duplicates) ->
                listOf(locationTrack) + locationTrackService.getMany(layoutContext, duplicates.map { d -> d.id })
            }
}
