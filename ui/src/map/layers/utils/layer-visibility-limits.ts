import { MapLayerName } from 'map/map-model';
// Alignments
export const ALL_ALIGNMENTS = 10.0;
export const LINKING_DOTS = 0.19;
export const DEBUG_1M_POINTS = 0.06;

export const HIGHLIGHTS_SHOW = 100.0;

export const layerZIndexes: Record<MapLayerName, number> = {
    'background-map-layer': 0,
    'location-track-background-layer': 1,
    'reference-line-background-layer': 2,
    'track-number-diagram-layer': 3,
    'location-track-alignment-layer': 4,
    'reference-line-alignment-layer': 5,
    'missing-profile-highlight-layer': 6,
    'missing-linking-highlight-layer': 7,
    'duplicate-tracks-highlight-layer': 8,
    'location-track-badge-layer': 9,
    'reference-line-badge-layer': 10,
    'km-post-layer': 11,
    'switch-layer': 12,
    'geometry-alignment-layer': 13,
    'geometry-km-post-layer': 14,
    'geometry-switch-layer': 15,
    'linking-layer': 16,
    'linking-switch-layer': 17,
    'manual-linking-switch-layer': 18,
    'plan-area-layer': 19,
    'debug-1m-points-layer': 20,
    'debug-layer': 21,
};

// Geometry
export const GEOMETRY_TICKS = 5.0;

// Track numbers
export const SHOW_LOCATION_TRACK_BADGES = 0.4;

export const BADGE_DRAW_DISTANCES = [
    [0.1, 20],
    [0.15, 40],
    [0.25, 80],
    [1, 160],
    [2, 320],
    [4, 640],
    [7, 1280],
    [11, 2560],
    [16, 5120],
    [24, 10240],
];

// Switches
export const SWITCH_SHOW = 3.2;
export const SWITCH_LARGE_SYMBOLS = 0.4;
export const SWITCH_LABELS = 0.8;

export const SUGGESTED_SWITCH_SHOW = 100.0;
export const MANUAL_SWITCH_LINKING_ENDPOINT_SELECTION_RESOLUTION = 100.0;

// Map resolution is ~ meters/pixel -> line points will have this many pixels between them
export const MAP_RESOLUTION_MULTIPLIER = 10;
