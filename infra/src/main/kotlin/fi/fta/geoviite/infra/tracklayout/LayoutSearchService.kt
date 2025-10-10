package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.ratko.RatkoLocalService
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.beans.factory.annotation.Autowired

data class AssetSearchParameters(
    val layoutContext: LayoutContext,
    val searchTerm: FreeText,
    val limitPerResultType: Int,
    val includeDeleted: Boolean = false,
)

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
        locationTrackSearchScope: IntId<LocationTrack>?,
        types: List<TrackLayoutSearchedAssetType>,
        params: AssetSearchParameters,
    ): TrackLayoutSearchResult {
        return if (locationTrackSearchScope != null) {
            searchByLocationTrackSearchScope(locationTrackSearchScope, types, params)
        } else {
            searchFromEntireRailwayNetwork(types, params)
        }
    }

    fun searchAllLocationTracks(params: AssetSearchParameters): List<LocationTrack> {
        return locationTrackService
            .list(params.layoutContext, true)
            .let { list ->
                locationTrackService.filterBySearchTerm(
                    list,
                    params.searchTerm,
                    locationTrackService.idMatches(params.layoutContext, params.searchTerm),
                    params.includeDeleted,
                )
            }
            .sortedBy(LocationTrack::name)
            .take(params.limitPerResultType)
    }

    fun searchAllSwitches(params: AssetSearchParameters): List<LayoutSwitch> {
        return switchService
            .list(params.layoutContext, true)
            .let { list ->
                switchService.filterBySearchTerm(
                    list,
                    params.searchTerm,
                    switchService.idMatches(params.layoutContext, params.searchTerm),
                    params.includeDeleted,
                )
            }
            .sortedBy(LayoutSwitch::name)
            .take(params.limitPerResultType)
    }

    fun searchAllTrackNumbers(params: AssetSearchParameters): List<LayoutTrackNumber> {
        return trackNumberService
            .list(params.layoutContext, true)
            .let { list ->
                trackNumberService.filterBySearchTerm(
                    list,
                    params.searchTerm,
                    trackNumberService.idMatches(params.layoutContext, params.searchTerm),
                    params.includeDeleted,
                )
            }
            .sortedBy(LayoutTrackNumber::number)
            .take(params.limitPerResultType)
    }

    fun searchAllKmPosts(params: AssetSearchParameters): List<LayoutKmPost> =
        kmPostService
            .list(params.layoutContext, true)
            .let { list ->
                kmPostService.filterBySearchTerm(
                    list,
                    params.searchTerm,
                    kmPostService.idMatches(),
                    params.includeDeleted,
                )
            }
            .sortedBy(LayoutKmPost::kmNumber)
            .take(params.limitPerResultType)

    private fun searchFromEntireRailwayNetwork(
        types: List<TrackLayoutSearchedAssetType>,
        params: AssetSearchParameters,
    ) =
        TrackLayoutSearchResult(
            switches =
                if (types.contains(TrackLayoutSearchedAssetType.SWITCH)) searchAllSwitches(params) else emptyList(),
            locationTracks =
                if (types.contains(TrackLayoutSearchedAssetType.LOCATION_TRACK)) searchAllLocationTracks(params)
                else emptyList(),
            trackNumbers =
                if (types.contains(TrackLayoutSearchedAssetType.TRACK_NUMBER)) searchAllTrackNumbers(params)
                else emptyList(),
            kmPosts =
                if (types.contains(TrackLayoutSearchedAssetType.KM_POST)) searchAllKmPosts(params) else emptyList(),
            operationalPoints =
                if (types.contains(TrackLayoutSearchedAssetType.OPERATIONAL_POINT))
                    ratkoLocalService.searchOperationalPoints(params.searchTerm, params.limitPerResultType)
                else emptyList(),
        )

    private fun searchByLocationTrackSearchScope(
        locationTrackSearchScope: IntId<LocationTrack>,
        types: List<TrackLayoutSearchedAssetType>,
        params: AssetSearchParameters,
    ): TrackLayoutSearchResult {
        val switches =
            if (types.contains(TrackLayoutSearchedAssetType.SWITCH))
                locationTrackService.getSwitchesForLocationTrack(params.layoutContext, locationTrackSearchScope).let {
                    ids ->
                    switchService.getMany(params.layoutContext, ids)
                }
            else emptyList()
        val locationTracks =
            if (types.contains(TrackLayoutSearchedAssetType.LOCATION_TRACK))
                getLocationTrackAndDuplicatesByScope(params.layoutContext, locationTrackSearchScope)
            else emptyList()

        val switchIdMatch =
            switchService.idMatches(params.layoutContext, params.searchTerm, switches.map { it.id as IntId })
        val ltIdMatch =
            locationTrackService.idMatches(
                params.layoutContext,
                params.searchTerm,
                locationTracks.map { it.id as IntId },
            )

        return TrackLayoutSearchResult(
            switches =
                switches
                    .let { list ->
                        switchService.filterBySearchTerm(list, params.searchTerm, switchIdMatch, params.includeDeleted)
                    }
                    .take(params.limitPerResultType),
            locationTracks =
                locationTracks
                    .let { list ->
                        locationTrackService.filterBySearchTerm(
                            list,
                            params.searchTerm,
                            ltIdMatch,
                            params.includeDeleted,
                        )
                    }
                    .take(params.limitPerResultType),
            trackNumbers = emptyList(),
            kmPosts = emptyList(),
            operationalPoints =
                if (types.contains(TrackLayoutSearchedAssetType.OPERATIONAL_POINT))
                    ratkoLocalService.searchOperationalPoints(params.searchTerm, params.limitPerResultType)
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
