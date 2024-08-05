import { LayoutContext } from 'common/common-model';
import { API_URI } from 'api/api-fetch';

type LayoutDataType =
    | 'track-numbers'
    | 'km-posts'
    | 'switches'
    | 'location-tracks'
    | 'reference-lines';

export const TRACK_LAYOUT_URI = `${API_URI}/track-layout`;

export function changeInfoUri(
    dataType: LayoutDataType,
    id: string,
    layoutContext: LayoutContext,
): string {
    return `${TRACK_LAYOUT_URI}/${dataType}/${contextInUri(layoutContext)}/${id}/change-info`;
}

export function layoutUri(
    dataType: LayoutDataType,
    layoutContext: LayoutContext,
    id?: string,
): string {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${contextInUri(layoutContext)}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

export function contextInUri(layoutContext: LayoutContext): string {
    return `${layoutContext.branch.toLowerCase()}/${layoutContext.publicationState.toLowerCase()}`;
}
