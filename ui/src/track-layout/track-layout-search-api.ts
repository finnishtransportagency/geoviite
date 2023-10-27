import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { PublishType } from 'common/common-model';
import { getNonNull, queryParams } from 'api/api-fetch';
import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';

export interface LayoutSearchResult {
    switches: LayoutSwitch[];
    locationTracks: LayoutLocationTrack[];
    trackNumbers: LayoutTrackNumber[];
}

export async function getBySearchTerm(
    searchTerm: string,
    publishType: PublishType,
    limitPerResultType: number = 10,
): Promise<LayoutSearchResult> {
    const uri = `${TRACK_LAYOUT_URI}/search/${publishType.toLowerCase()}`;

    const params = queryParams({
        searchTerm: searchTerm,
        limitPerResultType: limitPerResultType,
    });

    return await getNonNull<LayoutSearchResult>(`${uri}${params}`);
}
