import { getNullable, queryParams } from 'api/api-fetch';
import { LayoutContext } from 'common/common-model';
import { Point } from 'model/geometry';
import { contextInUri, TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { AlignmentPoint, LocationTrackId } from 'track-layout/track-layout-model';

export type ClosestTrackPoint = {
    locationTrackId: LocationTrackId;
    requestedLocation: Point;
    trackLocation: AlignmentPoint;
    distance: number;
};

export type RouteResult = {
    startConnection: ClosestTrackPoint;
    endConnection: ClosestTrackPoint;
    route: Route | null;
    totalLength: number | null;
};

export type Route = {
    sections: TrackSection[];
    totalLength: number;
};

export type TrackSection = {
    id: LocationTrackId;
    mRange: MRange;
    length: number;
};

export type MRange = {
    min: LineM;
    max: LineM;
};

export type LineM = {
    distance: number;
};

const ROUTING_URI = `${TRACK_LAYOUT_URI}/route`;

export async function getClosestTrackPoint(
    layoutContext: LayoutContext,
    location: Point,
    maxDistance?: number,
): Promise<ClosestTrackPoint | undefined> {
    const params = queryParams({
        x: location.x,
        y: location.y,
        maxDistance: maxDistance,
    });
    return getNullable<ClosestTrackPoint>(
        `${ROUTING_URI}/${contextInUri(layoutContext)}/closest-track-point${params}`,
    );
}

export async function getRoute(
    layoutContext: LayoutContext,
    startLocation: Point,
    endLocation: Point,
    maxDistance?: number,
): Promise<RouteResult | undefined> {
    const params = queryParams({
        startX: startLocation.x,
        startY: startLocation.y,
        endX: endLocation.x,
        endY: endLocation.y,
        maxDistance: maxDistance,
    });
    return getNullable<RouteResult>(`${ROUTING_URI}/${contextInUri(layoutContext)}${params}`);
}
