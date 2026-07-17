import { LayoutAlignmentTypeAndId } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';
import { getReferenceLineStartAndEnd } from 'track-layout/layout-track-number-api';
import {
    AlignmentStartAndEnd,
    EndpointType,
    MapAlignmentType,
} from 'track-layout/track-layout-model';
import { createPoint, Point } from 'model/geometry';
import { filterNotEmpty, minimumIndexBy } from 'utils/array-utils';

export type AlignmentEnd = {
    end: EndpointType;
    location: Point;
    // Unnormalized direction in radians that this end of the alignment points in
    outwardDirection: number;
};

export const getAlignmentStartAndEnd = (
    alignment: LayoutAlignmentTypeAndId,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<AlignmentStartAndEnd | undefined> =>
    alignment.type === MapAlignmentType.LocationTrack
        ? getLocationTrackStartAndEnd(alignment.id, layoutContext, changeTimes.layoutLocationTrack)
        : getReferenceLineStartAndEnd(alignment.id, layoutContext);

const alignmentEnds = (startAndEnd: AlignmentStartAndEnd | undefined): AlignmentEnd[] =>
    [
        startAndEnd?.start && {
            end: 'START' as const,
            location: startAndEnd.start.point,
            outwardDirection: startAndEnd.start.direction + Math.PI,
        },
        startAndEnd?.end && {
            end: 'END' as const,
            location: startAndEnd.end.point,
            outwardDirection: startAndEnd.end.direction,
        },
    ].filter(filterNotEmpty);

export const endLocation = (
    startAndEnd: AlignmentStartAndEnd | undefined,
    end: EndpointType,
): Point | undefined => alignmentEnds(startAndEnd).find((e) => e.end === end)?.location;

const squaredDistance = (a: Point, b: Point): number => (a.x - b.x) ** 2 + (a.y - b.y) ** 2;

export const nearestAlignmentEnd = (
    startAndEnd: AlignmentStartAndEnd | undefined,
    to: Point,
): AlignmentEnd | undefined => {
    const ends = alignmentEnds(startAndEnd);
    const nearest = minimumIndexBy(ends, (e) => squaredDistance(e.location, to));
    return nearest === undefined ? undefined : ends[nearest];
};

/**
 * Where an extension towards `cursor` actually ends. Without direction snap that is the cursor
 * itself; with it, the cursor is projected onto the ray leaving the alignment end in its outward
 * direction, clamped so the extension can only grow away from the alignment rather than fold back
 * over it.
 */
export const extensionLocation = (
    end: AlignmentEnd,
    cursor: Point,
    directionSnap: boolean,
): Point => {
    if (!directionSnap) {
        return cursor;
    }
    const dirX = Math.cos(end.outwardDirection);
    const dirY = Math.sin(end.outwardDirection);
    const projected = (cursor.x - end.location.x) * dirX + (cursor.y - end.location.y) * dirY;
    const distance = Math.max(0, projected);
    return createPoint(end.location.x + distance * dirX, end.location.y + distance * dirY);
};
