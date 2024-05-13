import { LayoutContext, LayoutDesignId } from 'common/common-model';
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
    layoutContext: LayoutContext,
): string {
    return `${TRACK_LAYOUT_URI}/${dataType}/${layoutContext.publicationState}/${id}/change-times`;
}

export function layoutUri(
    dataType: LayoutDataType,
    layoutContext: LayoutContext,
    id?: string,
): string {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${layoutContext.publicationState.toLowerCase()}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

// TODO: GVT-2401 This is a temporary solution until all APIs are changed to the new format (including branch)
export function contextAwareChangeInfoUri(
    dataType: LayoutDataType,
    id: string,
    layoutContext: LayoutContext,
): string {
    const branch = toBranchName(layoutContext.designId).toLowerCase();
    const publicationState = layoutContext.publicationState.toLowerCase();
    return `${TRACK_LAYOUT_URI}/${dataType}/${branch}/${publicationState}/${id}/change-info`;
}

// TODO: GVT-2401 This is a temporary solution until all APIs are changed to the new format (including branch)
export function contextAwareLayoutUri(
    dataType: LayoutDataType,
    layoutContext: LayoutContext,
    id?: string,
): string {
    const branch = toBranchName(layoutContext.designId).toLowerCase();
    const publicationState = layoutContext.publicationState.toLowerCase();
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${branch}/${publicationState}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

function toBranchName(designId?: LayoutDesignId): string {
    return designId ? `DESIGN_${designId}` : 'MAIN';
}
