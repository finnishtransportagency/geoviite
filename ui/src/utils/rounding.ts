export enum Precision {
    distanceMeters,
    cantMillimeters,
    radiusMeters,
    TM35FIN,
    profileMeters,
    alignmentLengthMeters,
    measurementMeterDistance,
    measurementKmDistance,
    curveMeters,
    angleFractions,
    distanceMetersInMicrometers,
}

export function roundToPrecision(n: number, precision: Precision): string {
    switch (precision) {
        case Precision.distanceMeters:
            return n.toFixed(3);
        case Precision.distanceMetersInMicrometers:
            return n.toFixed(6);
        case Precision.cantMillimeters:
            return n.toFixed(3);
        case Precision.radiusMeters:
            return n.toFixed(3);
        case Precision.TM35FIN:
            return n.toFixed(3);
        case Precision.profileMeters:
            return n.toFixed(3);
        case Precision.curveMeters:
            return n.toFixed(6);
        case Precision.angleFractions:
            return n.toFixed(6);
        case Precision.alignmentLengthMeters:
            return n.toFixed(1);
        case Precision.measurementKmDistance:
            return n.toFixed(1);
        case Precision.measurementMeterDistance:
            return n.toFixed(3);
    }
}
