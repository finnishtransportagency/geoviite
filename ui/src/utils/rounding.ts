import { exhaustiveMatchingGuard } from 'utils/type-utils';

export enum Precision {
    distanceKilometers,
    distanceMeters,
    distanceEvenMeters,
    cantMillimeters,
    radiusMeters,
    coordinateMeters,
    profileMeters,
    alignmentLengthMeters,
    alignmentM,
    measurementMeterDistance,
    measurementKmDistance,
    angle6Decimals,
    profileTangent,
    profileRadiusMeters,
}

function precisionToFractionDigits(precision: Precision): number {
    switch (precision) {
        case Precision.distanceKilometers:
            return 0;
        case Precision.distanceMeters:
            return 3;
        case Precision.distanceEvenMeters:
            return 0;
        case Precision.cantMillimeters:
            return 3;
        case Precision.radiusMeters:
            return 3;
        case Precision.coordinateMeters:
            return 3;
        case Precision.profileMeters:
            return 3;
        case Precision.alignmentLengthMeters:
            return 1;
        case Precision.alignmentM:
            return 3;
        case Precision.measurementKmDistance:
            return 1;
        case Precision.measurementMeterDistance:
            return 3;
        case Precision.angle6Decimals:
            return 6;
        case Precision.profileTangent:
            return 3;
        case Precision.profileRadiusMeters:
            return 0;
        default:
            return exhaustiveMatchingGuard(precision);
    }
}

export function roundToPrecision(n: number, precision: Precision): string {
    return n.toFixed(precisionToFractionDigits(precision));
}

export function roundToPrecisionNumber(n: number, precision: Precision): number {
    return Number.parseFloat(n.toFixed(precisionToFractionDigits(precision)));
}
