import {
    AlignmentPoint,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { PublishType, TrackMeter } from 'common/common-model';
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

function geocodingUri(publishType: PublishType) {
    return `${GEOCODING_URI}/${publishType.toLowerCase()}`;
}

export async function getAddress(
    trackNumberId: LayoutTrackNumberId,
    coordinate: Point,
    publishType: PublishType,
): Promise<TrackMeter | undefined> {
    const params = queryParams({
        coordinate: pointString(coordinate),
    });
    return getNullable<TrackMeter>(
        `${geocodingUri(publishType)}/address/${trackNumberId}${params}`,
    );
}

export async function getAddressPoints(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
): Promise<AlignmentAddresses | undefined> {
    return getNullable(`${geocodingUri(publishType)}/address-pointlist/${locationTrackId}`).then(
        (data: AlignmentAddresses | undefined) => data,
    );
}
