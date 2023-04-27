import { TrackMeter } from 'common/common-model';
import {
    findTrackMeterIndexContainingM,
    getTrackAddressAtSingleIndex,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { StationPoint, VerticalGeometryItem } from 'geometry/geometry-model';
import { filterNotEmpty, minimumIndexBy } from 'utils/array-utils';
import { Coordinates, heightToY, mToX, xToM } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import { approximateHeightAt } from 'vertical-geometry/util';

export type SnapTarget =
    | 'trackMeterRulerTick'
    | 'segmentBoundary'
    | 'geometryElement'
    | 'didNotSnap';

export interface SnappedPoint {
    snapTarget: SnapTarget;
    m: number;
    xPositionPx: number;
    yPositionPx: number;
    height: number;
    address: TrackMeter;
}

function getSnapOverRuler(
    xCoordinateM: number,
    trackKmHeights: TrackKmHeights[],
    onScreen: (snappedM: number) => boolean,
    approximatedPoint: (
        maybeApproximateM: number,
    ) => null | { address: TrackMeter | null; height: number | null },
): { address: TrackMeter | null; m: number; snapTarget: SnapTarget; height: number | null } {
    const closest = closestRulerTickM(xCoordinateM, trackKmHeights);
    if (closest == null || !onScreen(closest.m)) {
        const approximated = approximatedPoint(xCoordinateM);
        return {
            snapTarget: 'didNotSnap',
            m: xCoordinateM,
            height: approximated?.height ?? null,
            address: approximated?.address ?? null,
        };
    } else {
        const address = getTrackAddressAtSingleIndex(closest.index, trackKmHeights);
        return {
            snapTarget:
                address.meters === Math.floor(address.meters)
                    ? 'trackMeterRulerTick'
                    : 'segmentBoundary',
            m: closest.m,
            address: getTrackAddressAtSingleIndex(closest.index, trackKmHeights),
            height: approximateHeightAt(closest.m, closest.interval, trackKmHeights),
        };
    }
}

function getSnapOverChart(
    xCoordinateM: number,
    geometry: VerticalGeometryItem[],
    withinSnapDistance: (snappedM: number) => boolean,
    approximatedPoint: (
        maybeApproximateM: number,
    ) => null | { address: TrackMeter | null; height: number | null },
    drawTangents: boolean,
): { address: TrackMeter | null; m: number; snapTarget: SnapTarget; height: number | null } {
    const closest = closestGeometrySnapPoint(xCoordinateM, geometry, drawTangents);
    if (closest == null || !withinSnapDistance(closest.m)) {
        const approximated = approximatedPoint(xCoordinateM);
        return {
            snapTarget: 'didNotSnap',
            m: xCoordinateM,
            height: approximated?.height ?? null,
            address: approximated?.address ?? null,
        };
    }

    const height =
        closest.type === 'intersectionPoint'
            ? approximatedPoint(closest.m)?.height ?? closest.height
            : closest.height;
    return {
        snapTarget: 'geometryElement',
        height,
        address: closest.address,
        m: closest.m,
    };
}

export function getSnappedPoint(
    mousePositionInElement: [number, number] | null,
    trackKmHeights: TrackKmHeights[],
    geometry: VerticalGeometryItem[],
    coordinates: Coordinates,
    drawTangentArrows: boolean,
): SnappedPoint | null {
    if (mousePositionInElement == null) {
        return null;
    }
    const [mouseX, mouseY] = mousePositionInElement;
    const mouseCursorOverArea = mouseY > coordinates.chartHeightPx ? 'ruler' : 'chart';
    const xCoordinateM = xToM(coordinates, mouseX);
    const maxSnapDistanceOnChartM = coordinates.horizontalTickLengthMeters / 2;

    const approximatedPoint = (maybeApproximateM: number) => {
        const kmIndex = findTrackMeterIndexContainingM(maybeApproximateM, trackKmHeights);
        if (kmIndex == null) {
            return null;
        }
        const height = approximateHeightAt(maybeApproximateM, kmIndex, trackKmHeights);
        const address = approximateTrackAddressAt(maybeApproximateM, kmIndex, trackKmHeights);

        return { height, address };
    };

    const onScreen = (snappedM: number) =>
        snappedM >= coordinates.startM && snappedM <= coordinates.endM;
    const withinSnapDistance = (snappedM: number) =>
        Math.abs(snappedM - xCoordinateM) <= maxSnapDistanceOnChartM && onScreen(snappedM);

    const { snapTarget, height, address, m } =
        mouseCursorOverArea === 'ruler'
            ? getSnapOverRuler(xCoordinateM, trackKmHeights, onScreen, approximatedPoint)
            : (() => {
                  return getSnapOverChart(
                      xCoordinateM,
                      geometry,
                      withinSnapDistance,
                      approximatedPoint,
                      drawTangentArrows,
                  );
              })();

    if (height == null || address == null) {
        return null;
    }

    const x = mToX(coordinates, m);
    const y = heightToY(coordinates, height);

    return { snapTarget, m, xPositionPx: x, yPositionPx: y, height, address };
}

function toGeometrySnapPoint(stationPoint: StationPoint, type: 'intersectionPoint' | 'endPoint') {
    return stationPoint.address == null
        ? null
        : {
              m: stationPoint.station,
              height: stationPoint.height,
              address: stationPoint.address,
              type,
          };
}
function closestGeometrySnapPoint(
    m: number,
    geometry: VerticalGeometryItem[],
    drawTangents: boolean,
) {
    const allGeometryPoints = geometry.flatMap((geom) =>
        [
            toGeometrySnapPoint(geom.point, 'intersectionPoint'),
            drawTangents ? toGeometrySnapPoint(geom.start, 'endPoint') : null,
            drawTangents ? toGeometrySnapPoint(geom.end, 'endPoint') : null,
        ].filter(filterNotEmpty),
    );
    const minIndex = minimumIndexBy(allGeometryPoints, (snapPoint) => Math.abs(m - snapPoint.m));
    return minIndex == null ? null : allGeometryPoints[minIndex];
}

function closestRulerTickM(m: number, trackKmHeights: TrackKmHeights[]) {
    const index = findTrackMeterIndexContainingM(m, trackKmHeights);
    if (index == null) {
        return null;
    }
    const [left, right] = getTrackMeterPairAroundIndex(index, trackKmHeights);
    return Math.abs(m - left.m) < Math.abs(m - right.m)
        ? { m: left.m, index: index.left, interval: index }
        : { m: right.m, index: index.right, interval: index };
}

function approximateTrackAddressAt(
    m: number,
    index: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): TrackMeter | null {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    const leftKm = kmHeights[index.left.kmIndex];
    const proportion = (m - leftMeter.m) / (rightMeter.m - leftMeter.m);
    return {
        kmNumber: leftKm.kmNumber,
        meters:
            index.left.kmIndex === index.right.kmIndex
                ? leftMeter.meter + proportion * (rightMeter.meter - leftMeter.meter)
                : // we can't accurately know the length of the last track meter in track address space, so let's just
                  // assume it doesn't turn very hard here
                  leftMeter.meter + proportion * (rightMeter.m - leftMeter.m),
    };
}
