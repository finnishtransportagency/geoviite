// Alignments
export const ALL_ALIGNMENTS = 5.0;
export const SEPARATE_SEGMENTS = 60.0;
export const LINKING_DOTS = 0.19;
export const DEBUG_1M_POINTS = 0.06;

// Geometry
export const GEOMETRY_TICKS = 5.0;

// Track numbers
export const TRACK_NUMER_NAMES = 0.4;
const TRACK_NUMBER_DRAW_DISTANCES = [
    [0.1, 40],
    [0.2, 80],
    [0.4, 160],
    [0.8, 160],
    [1.6, 320],
    [3.2, 640],
];

export function getTrackNumberDrawDistance(resolution: number): number | null {
    const distance = TRACK_NUMBER_DRAW_DISTANCES.find((d) => resolution < d[0]);
    return distance ? distance[1] : null;
}

// Switches
export const SWITCH_SHOW = 3.2;
export const SWITCH_LARGE_SYMBOLS = 0.4;
export const SWITCH_LABELS = 0.8;

export const SUGGESTED_SWITCH_SHOW = 100.0;
export const MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION = 100.0;

// Map resolution is ~ meters/pixel -> line points will have this many pixels between them
export const MAP_RESOLUTION_MULTIPLIER = 10;
