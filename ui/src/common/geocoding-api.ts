import {
    AlignmentPoint,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { API_URI, getNullable, queryParams } from 'api/api-fetch';
import { pointString } from 'common/common-api';

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

function geocodingUri(layoutContext: LayoutContext) {
    return `${GEOCODING_URI}/${layoutContext.publicationState.toLowerCase()}`;
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

export async function getAddressPoints(
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<AlignmentAddresses | undefined> {
    return getNullable(`${geocodingUri(layoutContext)}/address-pointlist/${locationTrackId}`).then(
        (data: AlignmentAddresses | undefined) => data,
    );
}
