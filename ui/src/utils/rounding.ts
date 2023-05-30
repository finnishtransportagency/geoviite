export enum Precision {
    distanceKilometers,
    distanceMeters,
    distanceEvenMeters,
    cantMillimeters,
    radiusMeters,
    coordinateMeters,
    profileMeters,
    alignmentLengthMeters,
    measurementMeterDistance,
    measurementKmDistance,
    angle6Decimals,
    profileTangent,
    profileRadiusMeters,
}

export function roundToPrecision(n: number, precision: Precision): string {
    switch (precision) {
        case Precision.distanceKilometers:
            return n.toFixed(0);
        case Precision.distanceMeters:
            return n.toFixed(3);
        case Precision.distanceEvenMeters:
            return n.toFixed(0);
        case Precision.cantMillimeters:
            return n.toFixed(3);
        case Precision.radiusMeters:
            return n.toFixed(3);
        case Precision.coordinateMeters:
            return n.toFixed(3);
        case Precision.profileMeters:
            return n.toFixed(3);
        case Precision.alignmentLengthMeters:
            return n.toFixed(1);
        case Precision.measurementKmDistance:
            return n.toFixed(1);
        case Precision.measurementMeterDistance:
            return n.toFixed(3);
        case Precision.angle6Decimals:
            return n.toFixed(6);
        case Precision.profileTangent:
            return n.toFixed(3);
        case Precision.profileRadiusMeters:
            return n.toFixed(0);
    }
}
