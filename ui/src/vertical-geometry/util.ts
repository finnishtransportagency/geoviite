import { TrackKmHeights, TrackMeterHeight } from 'geometry/geometry-api';
import {
    findTrackMeterIndexContainingM,
    getTrackMeterPairAroundIndex,
    TrackMeterIndex,
} from 'vertical-geometry/track-meter-index';

export function approximateHeightAtM(m: number, kmHeights: TrackKmHeights[]): number | null {
    const index = findTrackMeterIndexContainingM(m, kmHeights);
    if (index == null) {
        return null;
    }
    return approximateHeightAt(m, index, kmHeights);
}

export function approximateHeightAt(
    m: number,
    index: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): number | null {
    const [leftMeter, rightMeter] = getTrackMeterPairAroundIndex(index, kmHeights);
    // We don't try to extrapolate heights; this is why the back-end puts in some extra effort to make sure to send
    // heights to cover all intervals where we might want to display heights (and hence can always interpolate)
    if (rightMeter.height == null) {
        return leftMeter.height;
    }
    if (leftMeter.height == null) {
        return rightMeter.height;
    }
    const proportion = (m - leftMeter.m) / (rightMeter.m - leftMeter.m);
    return (1 - proportion) * leftMeter.height + proportion * rightMeter.height;
}

export function polylinePoints(points: readonly (readonly [number, number])[]): string {
    return points.map(([x, y]) => `${x},${y}`).join(' ');
}
