import { Point } from 'model/geometry';
import { Precision, roundToPrecision } from 'utils/rounding';
import { CoordinateSystem, TrackMeter } from 'common/common-model';

export function formatToTM35FINString(gvtPoint: Point): string {
    const longitude = roundToPrecision(gvtPoint.x, Precision.coordinateMeters);
    const latitude = roundToPrecision(gvtPoint.y, Precision.coordinateMeters);
    return `${longitude} E, ${latitude} N`;
}

export function formatToGkFinString(gkPoint: Point): string {
    const easting = roundToPrecision(gkPoint.x, Precision.coordinateMeters);
    const northing = roundToPrecision(gkPoint.y, Precision.coordinateMeters);
    return `${northing} N, ${easting} E`;
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
        Precision.distanceEvenMeters,
    ).padStart(4, '0')}`;
}

export function formatWithSrid(coordinateSystem: CoordinateSystem): string {
    return `${coordinateSystem.name} ${coordinateSystem.srid}`;
}
