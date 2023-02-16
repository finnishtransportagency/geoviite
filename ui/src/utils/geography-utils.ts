import { Point } from 'model/geometry';
import { Precision, roundToPrecision } from 'utils/rounding';
import { TrackMeter } from 'common/common-model';

export function formatToTM35FINString(gvtPoint: Point): string {
    const longitude = roundToPrecision(gvtPoint.x, Precision.TM35FIN);
    const latitude = roundToPrecision(gvtPoint.y, Precision.TM35FIN);
    return `${longitude} E, ${latitude} N`;
}

// End result is something like "0185+0667.567"
export function formatTrackMeter(address: TrackMeter): string {
    return `${address.kmNumber}+${roundToPrecision(
        address.meters,
        Precision.distanceMeters,
    ).padStart(8, '0')}`;
}

// End result is something like "0185+0667"
export function formatTrackMeterWithoutMeters(address: TrackMeter): string {
    return `${address.kmNumber}+${roundToPrecision(
        Math.floor(address.meters),
        Precision.distanceKilometers,
    ).padStart(4, '0')}`;
}
