import {
    AlignmentPoint,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { Line, Point } from 'model/geometry';
import { LayoutContext, TimeStamp, TrackMeter } from 'common/common-model';
import { API_URI, getNullable, queryParams } from 'api/api-fetch';
import { pointString } from 'common/common-api';
import { contextInUri } from 'track-layout/track-layout-api';
import { asyncCache } from 'cache/cache';

export const GEOCODING_URI = `${API_URI}/geocoding`;

export type AddressPoint = {
    point: AlignmentPoint;
    address: TrackMeter;
    distance: number;
};

export type IntersectType = 'BEFORE' | 'AFTER' | 'WITHIN';

export type AlignmentAddresses = {
    startPoint: AddressPoint;
    endPoint: AddressPoint;
    startIntersect: IntersectType;
    endIntersect: IntersectType;
    midPoints: AddressPoint[];
};

export type ProjectionLine = {
    address: TrackMeter;
    projection: Line;
    referenceLineM: number;
    referenceDirection: number;
};

const projectionLinesCache = asyncCache<LayoutTrackNumberId, ProjectionLine[] | undefined>();

function geocodingUri(layoutContext: LayoutContext) {
    return `${GEOCODING_URI}/${contextInUri(layoutContext)}`;
}

export async function getAddress(
    trackNumberId: LayoutTrackNumberId,
    coordinate: Point,
    layoutContext: LayoutContext,
): Promise<TrackMeter | undefined> {
    const params = queryParams({
        coordinate: pointString(coordinate),
    });
    return getNullable<TrackMeter>(
        `${geocodingUri(layoutContext)}/address/${trackNumberId}${params}`,
    );
}

export async function getProjectionLines(
    trackNumberId: LayoutTrackNumberId,
    layoutContext: LayoutContext,
    changeTime: TimeStamp,
): Promise<ProjectionLine[] | undefined> {
    return projectionLinesCache.get(changeTime, trackNumberId, () =>
        getNullable(`${geocodingUri(layoutContext)}/projection-lines/${trackNumberId}`).then(
            (data: ProjectionLine[] | undefined) => data,
        ),
    );
}

export async function getAddressPoints(
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<AlignmentAddresses | undefined> {
    return getNullable(`${geocodingUri(layoutContext)}/address-pointlist/${locationTrackId}`).then(
        (data: AlignmentAddresses | undefined) => data,
    );
}
