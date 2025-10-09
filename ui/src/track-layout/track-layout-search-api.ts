import {
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
    OperationalPoint,
} from 'track-layout/track-layout-model';
import { getNonNull, queryParams } from 'api/api-fetch';
import { TRACK_LAYOUT_URI, contextInUri } from 'track-layout/track-layout-api';
import { LayoutContext } from 'common/common-model';
import { SearchItemType } from 'asset-search/search-dropdown';

export interface LayoutSearchResult {
    switches: LayoutSwitch[];
    locationTracks: LayoutLocationTrack[];
    trackNumbers: LayoutTrackNumber[];
    kmPosts: LayoutKmPost[];
    operatingPoints: OperationalPoint[];
}

export async function getBySearchTerm(
    searchTerm: string,
    layoutContext: LayoutContext,
    types: SearchItemType[],
    includeDeleted: boolean,
    locationTrackSearchScope?: LocationTrackId,
    limitPerResultType: number = 10,
): Promise<LayoutSearchResult> {
    const uri = `${TRACK_LAYOUT_URI}/search/${contextInUri(layoutContext)}`;

    const params = queryParams({
        searchTerm: searchTerm,
        locationTrackSearchScope: locationTrackSearchScope,
        limitPerResultType: limitPerResultType,
        types: types,
        includeDeleted: includeDeleted,
    });

    return await getNonNull<LayoutSearchResult>(`${uri}${params}`);
}
