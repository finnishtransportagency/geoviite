import { Point } from 'model/geometry';
import { Precision, roundToPrecision } from 'utils/rounding';
import { CoordinateSystem, KmNumber, TrackMeter } from 'common/common-model';
import { ADDRESS_REGEX } from 'tool-panel/track-number/dialog/track-number-edit-store';
import { isNil } from './type-utils';

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

export function parseTrackMeter(address: string): TrackMeter | undefined {
    const trimmed = address.trim();
    if (!trimmed.match(ADDRESS_REGEX)) return undefined;
    const parts = address.split('+');
    if (parts.length !== 2 || isNil(parts[0]) || isNil(parts[1])) return undefined;
    return {
        kmNumber: padKmNumberStart(parts[0]),
        meters: parseFloat(parts[1]),
    };
}

export function formatWithSrid(coordinateSystem: CoordinateSystem): string {
    return `${coordinateSystem.name} ${coordinateSystem.srid}`;
}

export function padKmNumberStart(km: KmNumber): KmNumber {
    const kmParts = km.match(/^\d+/);
    const numbersInKm = kmParts && kmParts.length >= 1 ? kmParts[0].length : 0;
    const lettersInKm = km.length - numbersInKm;
    return km.padStart(4 + lettersInKm, '0');
}
