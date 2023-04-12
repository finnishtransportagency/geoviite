import { TrackKmHeights, TrackMeterHeight } from 'vertical-geometry/vertical-geometry-diagram';

export interface SingleTrackMeterIndex {
    kmIndex: number;
    meterIndex: number;
}

// left side definitely exists; but right side doesn't for the last track meter in view
export interface TrackMeterIndex {
    left: SingleTrackMeterIndex;
    right: SingleTrackMeterIndex | null;
}

export function findTrackMeterIndexContainingM(
    m: number,
    kmHeights: TrackKmHeights[],
): TrackMeterIndex | null {
    const nextKmIndex = kmHeights.findIndex(({ trackMeterHeights }) => trackMeterHeights[0].m > m);
    // todo: If we run off the right end instead, should check (in a way that's not too susceptible to floating-point
    // roundoff) whether we're running off the last track meter
    if (nextKmIndex === 0) {
        return null;
    }
    const kmIndex = nextKmIndex === -1 ? kmHeights.length - 1 : nextKmIndex - 1;
    if (kmIndex == -1) {
        return null;
    }
    const km = kmHeights[kmIndex];
    const nextMeterIndex = km.trackMeterHeights.findIndex((trackM) => trackM.m >= m);
    const left = {
        kmIndex,
        meterIndex:
            nextMeterIndex == 0
                ? 0
                : nextMeterIndex == -1
                ? km.trackMeterHeights.length - 1
                : nextMeterIndex - 1,
    };
    return { left, right: nextSingleTrackMeterIndex(left, kmHeights) };
}

function nextSingleTrackMeterIndex(
    { kmIndex, meterIndex }: SingleTrackMeterIndex,
    kmHeights: TrackKmHeights[],
): SingleTrackMeterIndex | null {
    const km = kmHeights[kmIndex];
    const lastMeter = meterIndex == km.trackMeterHeights.length - 1;
    const lastKm = kmIndex == kmHeights.length - 1;
    if (lastMeter && lastKm) {
        return null;
    } else if (lastMeter) {
        return { kmIndex: kmIndex + 1, meterIndex: 0 };
    } else {
        return { kmIndex, meterIndex: meterIndex + 1 };
    }
}

export function getTrackMeterAtSingleIndex(
    { kmIndex, meterIndex }: SingleTrackMeterIndex,
    kmHeights: TrackKmHeights[],
) {
    return kmHeights[kmIndex].trackMeterHeights[meterIndex];
}

export function getTrackMeterPairAroundIndex(
    { left, right }: TrackMeterIndex,
    kmHeights: TrackKmHeights[],
): [TrackMeterHeight, TrackMeterHeight | null] {
    return [
        getTrackMeterAtSingleIndex(left, kmHeights),
        right == null ? null : getTrackMeterAtSingleIndex(right, kmHeights),
    ];
}
