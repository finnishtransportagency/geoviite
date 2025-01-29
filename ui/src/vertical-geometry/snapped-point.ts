import { TrackMeter } from 'common/common-model';
import {
    findTrackMeterIndexContainingM,
    getTrackAddressAtSingleIndex,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';
import { StationPoint, VerticalGeometryDiagramDisplayItem } from 'geometry/geometry-model';
import { filterNotEmpty, minimumIndexBy } from 'utils/array-utils';
import { Coordinates, heightToY, mToX, xToM } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import { approximateHeightAt } from 'vertical-geometry/util';
import { expectDefined } from 'utils/type-utils';

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
    fileName?: string;
}

function getSnapOverRuler(
    xCoordinateM: number,
    trackKmHeights: TrackKmHeights[],
    onScreen: (snappedM: number) => boolean,
    approximatedPoint: (
        maybeApproximateM: number,
    ) => undefined | { address: TrackMeter | undefined; height: number | undefined },
): {
    address: TrackMeter | undefined;
    m: number;
    snapTarget: SnapTarget;
    height: number | undefined;
    fileName?: string;
} {
    const closest = closestRulerTickM(xCoordinateM, trackKmHeights);
    if (!closest || !onScreen(closest.m)) {
        const approximated = approximatedPoint(xCoordinateM);
        return {
            snapTarget: 'didNotSnap',
            m: xCoordinateM,
            height: approximated?.height,
            address: approximated?.address,
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
    geometry: VerticalGeometryDiagramDisplayItem[],
    withinSnapDistance: (snappedM: number) => boolean,
    approximatedPoint: (
        maybeApproximateM: number,
    ) => undefined | { address: TrackMeter | undefined; height: number | undefined },
    drawTangents: boolean,
): {
    address: TrackMeter | undefined;
    m: number;
    snapTarget: SnapTarget;
    height: number | undefined;
    fileName?: string;
} {
    const closest = closestGeometrySnapPoint(xCoordinateM, geometry, drawTangents);
    if (closest === undefined || !withinSnapDistance(closest.m)) {
        const approximated = approximatedPoint(xCoordinateM);
        return {
            snapTarget: 'didNotSnap',
            m: xCoordinateM,
            height: approximated?.height,
            address: approximated?.address,
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
        fileName: closest.fileName,
    };
}

export function getSnappedPoint(
    mousePositionInElement: [number, number] | undefined,
    trackKmHeights: TrackKmHeights[],
    geometry: VerticalGeometryDiagramDisplayItem[],
    coordinates: Coordinates,
    drawTangentArrows: boolean,
): SnappedPoint | undefined {
    if (mousePositionInElement === undefined) {
        return undefined;
    }

    const [mouseX, mouseY] = mousePositionInElement;
    const mouseCursorOverArea = mouseY > coordinates.chartHeightPx ? 'ruler' : 'chart';
    const xCoordinateM = xToM(coordinates, mouseX);
    const maxSnapDistanceOnChartM = coordinates.horizontalTickLengthMeters / 2;

    const approximatedPoint = (maybeApproximateM: number) => {
        const kmIndex = findTrackMeterIndexContainingM(maybeApproximateM, trackKmHeights);
        if (kmIndex === undefined) {
            return undefined;
        }
        const height = approximateHeightAt(maybeApproximateM, kmIndex, trackKmHeights);
        const address = approximateTrackAddressAt(maybeApproximateM, kmIndex, trackKmHeights);

        return { height, address };
    };

    const onScreen = (snappedM: number) =>
        snappedM >= coordinates.startM && snappedM <= coordinates.endM;
    const withinSnapDistance = (snappedM: number) =>
        Math.abs(snappedM - xCoordinateM) <= maxSnapDistanceOnChartM && onScreen(snappedM);

    const { snapTarget, height, address, m, fileName } =
        mouseCursorOverArea === 'ruler'
            ? getSnapOverRuler(xCoordinateM, trackKmHeights, onScreen, approximatedPoint)
            : getSnapOverChart(
                  xCoordinateM,
                  geometry,
                  withinSnapDistance,
                  approximatedPoint,
                  drawTangentArrows,
              );

    if (height === undefined || address === undefined) {
        return undefined;
    }

    const x = mToX(coordinates, m);
    const y = heightToY(coordinates, height);

    return {
        snapTarget,
        m,
        xPositionPx: x,
        yPositionPx: y,
        height,
        address,
        fileName: fileName,
    };
}

function toGeometrySnapPoint(
    fileName: string,
    stationPoint: StationPoint,
    type: 'intersectionPoint' | 'endPoint',
) {
    return stationPoint.address === undefined
        ? undefined
        : {
              m: stationPoint.station,
              height: stationPoint.height,
              address: stationPoint.address,
              fileName,
              type,
          };
}

function closestGeometrySnapPoint(
    m: number,
    geometry: VerticalGeometryDiagramDisplayItem[],
    drawTangents: boolean,
) {
    const allGeometryPoints = geometry.flatMap((geom) =>
        [
            geom.point
                ? toGeometrySnapPoint(geom.fileName, geom.point, 'intersectionPoint')
                : undefined,
            drawTangents && geom.start
                ? toGeometrySnapPoint(geom.fileName, geom.start, 'endPoint')
                : undefined,
            drawTangents && geom.end
                ? toGeometrySnapPoint(geom.fileName, geom.end, 'endPoint')
                : undefined,
        ].filter(filterNotEmpty),
    );
    const minIndex = minimumIndexBy(allGeometryPoints, (snapPoint) => Math.abs(m - snapPoint.m));
    return minIndex === undefined ? undefined : allGeometryPoints[minIndex];
}

function closestRulerTickM(m: number, trackKmHeights: TrackKmHeights[]) {
    const index = findTrackMeterIndexContainingM(m, trackKmHeights);
    if (!index) {
        return undefined;
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
): TrackMeter | undefined {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    const leftKm = expectDefined(kmHeights[index.left.kmIndex]);
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
