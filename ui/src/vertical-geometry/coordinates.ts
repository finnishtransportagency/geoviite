export interface Coordinates {
    startM: number;
    endM: number;
    meterHeightPx: number;
    mMeterLengthPxOverM: number;
    fullDiagramHeightPx: number;
    diagramWidthPx: number;
    horizontalTickLengthMeters: number;

    bottomHeightPaddingPx: number;
    bottomHeightTick: number;
    topHeightTick: number;
    chartHeightPx: number;
}

export function mToX(coordinates: Coordinates, m: number): number {
    return (m - coordinates.startM) * coordinates.mMeterLengthPxOverM;
}

export function xToM(coordinates: Coordinates, x: number): number {
    return x / coordinates.mMeterLengthPxOverM + coordinates.startM;
}

export function heightToY(coordinates: Coordinates, height: number): number {
    return (
        coordinates.chartHeightPx -
        coordinates.bottomHeightPaddingPx +
        (coordinates.bottomHeightTick - height) * coordinates.meterHeightPx
    );
}
