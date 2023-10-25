import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { PublishType } from 'common/common-model';
import { getNonNull, queryParams } from 'api/api-fetch';
import { layoutUri } from 'track-layout/track-layout-api';

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
    const params = queryParams({
        searchTerm: searchTerm,
        limitPerResultType: limitPerResultType,
    });

    return await getNonNull<LayoutSearchResult>(`${layoutUri('search', publishType)}${params}`);
}
