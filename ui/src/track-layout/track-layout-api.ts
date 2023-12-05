import { PublishType } from 'common/common-model';
import { API_URI } from 'api/api-fetch';

type LayoutDataType =
    | 'track-numbers'
    | 'km-posts'
    | 'switches'
    | 'location-tracks'
    | 'reference-lines';

export const TRACK_LAYOUT_URI = `${API_URI}/track-layout`;

export function changeTimeUri(
    dataType: LayoutDataType,
    id: string,
    publishType: PublishType,
): string {
    return `${TRACK_LAYOUT_URI}/${dataType}/${publishType}/${id}/change-times`;
}

export function layoutUri(dataType: LayoutDataType, publishType: PublishType, id?: string): string {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${publishType.toLowerCase()}`;
    return id ? `${baseUri}/${id}` : baseUri;
}
