import { MapLayerName } from 'map/map-model';
// Alignments
export const ALL_ALIGNMENTS = 10.0;
export const LINKING_DOTS = 0.19;
export const DEBUG_1M_POINTS = 0.06;

export const HIGHLIGHTS_SHOW = 100.0;

export const mapLayerZIndexes = [
    'background-map-layer',
    'location-track-background-layer',
    'reference-line-background-layer',
    'track-number-diagram-layer',
    'plan-section-highlight-layer',
    'missing-profile-highlight-layer',
    'missing-linking-highlight-layer',
    'duplicate-tracks-highlight-layer',
    'reference-line-alignment-layer',
    'location-track-alignment-layer',
    'geometry-alignment-layer',
    'location-track-badge-layer',
    'reference-line-badge-layer',
    'km-post-layer',
    'switch-layer',
    'track-number-addresses-layer',
    'geometry-km-post-layer',
    'geometry-switch-layer',
    'alignment-linking-layer',
    'switch-linking-layer',
    'plan-area-layer',
    'debug-1m-points-layer',
    'debug-layer',
].reduce((acc, layer, idx) => {
    acc[layer as MapLayerName] = idx;

    return acc;
}, {} as Record<MapLayerName, number>);

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

// Map resolution is ~ meters/pixel -> line points will have this many pixels between them
export const MAP_RESOLUTION_MULTIPLIER = 10;
