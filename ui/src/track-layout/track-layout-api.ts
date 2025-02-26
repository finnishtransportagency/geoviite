import { LayoutBranch, LayoutContext } from 'common/common-model';
import { API_URI, getNonNull } from 'api/api-fetch';
import { BoundingBox } from 'model/geometry';
import { bboxString } from 'common/common-api';
import { LayoutGraph, LayoutGraphLevel } from 'track-layout/track-layout-model';

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

export function layoutUriByBranch(
    dataType: LayoutDataType,
    layoutBranch: LayoutBranch,
    id?: string,
): string {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}/${layoutBranch.toLowerCase()}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

export function layoutUriWithoutContext(dataType: LayoutDataType, id?: string): string {
    const baseUri = `${TRACK_LAYOUT_URI}/${dataType}`;
    return id ? `${baseUri}/${id}` : baseUri;
}

export function contextInUri(layoutContext: LayoutContext): string {
    return `${layoutContext.branch.toLowerCase()}/${layoutContext.publicationState.toLowerCase()}`;
}

export const getLayoutGraph = (
    context: LayoutContext,
    bbox: BoundingBox,
    detailLevel: LayoutGraphLevel,
): Promise<LayoutGraph> =>
    getNonNull<LayoutGraph>(
        `${TRACK_LAYOUT_URI}/layout-graph/${contextInUri(context)}?bbox=${bboxString(bbox)}&detailLevel=${detailLevel}`,
    );
