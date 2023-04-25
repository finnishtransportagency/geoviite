import { TrackMeter } from 'common/common-model';
import { TrackKmHeights, TrackMeterHeight } from 'geometry/geometry-api';

export interface SingleTrackMeterIndex {
    kmIndex: number;
    meterIndex: number;
}

// Track meter indices turn out to be used for interpolation between adjacent points almost constantly; so we bite the
// bullet and look up both the left and right side of the two adjacent points we might see
export interface TrackMeterIndex {
    left: SingleTrackMeterIndex;
    right: SingleTrackMeterIndex;
}

export function findTrackMeterIndexContainingM(
    m: number,
    kmHeights: TrackKmHeights[],
): TrackMeterIndex | null {
    const nextKmIndex = kmHeights.findIndex(({ trackMeterHeights }) => trackMeterHeights[0].m > m);
    if (nextKmIndex === 0) {
        return null;
    }
    const kmIndex = nextKmIndex === -1 ? kmHeights.length - 1 : nextKmIndex - 1;
    const km = kmHeights[kmIndex];
    const meterIndex = km.trackMeterHeights.findIndex((trackM) => trackM.m >= m);
    // if this the last track km on the alignment, then the last track meter is a sentinel sent by the backend to mark
    // the alignment's end, and can be considered to have zero length; hence, if we're past it, we've fallen past the
    // alignment
    if (kmIndex == kmHeights.length - 1 && meterIndex == -1) {
        return null;
    }
    // otherwise, meterIndex will be -1 exactly if we were on a track km's last meter
    const right =
        meterIndex == -1 ? { kmIndex: kmIndex + 1, meterIndex: 0 } : { kmIndex, meterIndex };

    const left = previousSingleTrackMeterIndex(right, kmHeights);
    return left == null ? null : { left, right };
}

function previousSingleTrackMeterIndex(
    { kmIndex, meterIndex }: SingleTrackMeterIndex,
    kmHeights: TrackKmHeights[],
): SingleTrackMeterIndex | null {
    if (meterIndex == 0 && kmIndex == 0) {
        return null;
    } else if (meterIndex == 0) {
        return {
            kmIndex: kmIndex - 1,
            meterIndex: kmHeights[kmIndex - 1].trackMeterHeights.length - 1,
        };
    } else {
        return { kmIndex, meterIndex: meterIndex - 1 };
    }
}

export function getTrackAddressAtSingleIndex(
    index: SingleTrackMeterIndex,
    kmHeights: TrackKmHeights[],
): TrackMeter {
    const kmNumber = kmHeights[index.kmIndex].kmNumber;
    const meters = getTrackMeterAtSingleIndex(index, kmHeights).meter;
    return { kmNumber, meters };
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
): [TrackMeterHeight, TrackMeterHeight] {
    return [
        getTrackMeterAtSingleIndex(left, kmHeights),
        getTrackMeterAtSingleIndex(right, kmHeights),
    ];
}
