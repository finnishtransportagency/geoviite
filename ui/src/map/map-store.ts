import { PayloadAction } from '@reduxjs/toolkit';
import {
    Map,
    MapLayerName,
    MapLayerSetting,
    MapLayerSettingName,
    MapViewport,
    OptionalShownItems,
    ShownItems,
    shownItemsByLayer,
} from 'map/map-model';
import { createContext } from 'react';
import { BoundingBox, boundingBoxScale, centerForBoundingBox, Point } from 'model/geometry';
import { deduplicate } from 'utils/array-utils';

export function createEmptyShownItems(): ShownItems {
    return {
        referenceLines: [],
        locationTracks: [],
        kmPosts: [],
        switches: [],
    };
}

export type MapLayerSettingChange = {
    name: MapLayerSettingName;
    visible: boolean;
};

const layerSettingMapLayers: Record<MapLayerSettingName, MapLayerName[]> = {
    'map': ['background-map-layer'],
    'location-track': [
        'location-track-alignment-layer',
        'location-track-background-layer',
        'location-track-badge-layer',
    ],
    'reference-line': [
        'reference-line-alignment-layer',
        'reference-line-background-layer',
        'reference-line-badge-layer',
    ],
    'track-number-diagram': ['track-number-diagram-layer', 'reference-line-badge-layer'],
    'missing-vertical-geometry': ['missing-profile-highlight-layer'],
    'missing-linking': ['missing-linking-highlight-layer'],
    'duplicate-tracks': ['duplicate-tracks-highlight-layer'],
    'km-post': ['km-post-layer'],
    'switch': ['switch-layer'],
    'geometry-alignment': ['geometry-alignment-layer'],
    'geometry-switch': ['geometry-switch-layer'],
    'plan-area': ['plan-area-layer'],
    'geometry-km-post': ['geometry-km-post-layer'],
    'debug-1m': ['debug-1m-points-layer'],
    'debug': ['debug-layer'],
};

export const initialMapState: Map = {
    layers: [
        'background-map-layer',
        'location-track-alignment-layer',
        'reference-line-alignment-layer',
        'km-post-layer',
        'switch-layer',
        'geometry-alignment-layer',
        'geometry-switch-layer',
        'geometry-km-post-layer',
    ],
    settingsMenu: {
        layout: [
            { name: 'map', visible: true },
            {
                name: 'location-track',
                visible: true,
                subSettings: [
                    { name: 'reference-line', visible: true },
                    { name: 'track-number-diagram', visible: false },
                    { name: 'missing-vertical-geometry', visible: false },
                    { name: 'missing-linking', visible: false },
                    { name: 'duplicate-tracks', visible: false },
                ],
            },
            { name: 'switch', visible: true },
            { name: 'km-post', visible: true },
        ],
        geometry: [
            { name: 'geometry-alignment', visible: true },
            { name: 'geometry-switch', visible: true },
            { name: 'geometry-km-post', visible: true },
            { name: 'plan-area', visible: false },
        ],
        debug: [
            { name: 'debug-1m', visible: false },
            { name: 'debug', visible: false },
        ],
    },
    shownItems: createEmptyShownItems(),
    viewport: {
        center: {
            x: 385782.89,
            y: 6672277.83,
        },
        resolution: 20,
    },
    hoveredLocation: null,
    clickLocation: null,
};

export const mapReducers = {
    onShownItemsChange: (
        { shownItems }: Map,
        { payload }: PayloadAction<OptionalShownItems>,
    ): void => {
        if (payload.referenceLines != null) {
            shownItems.referenceLines = payload.referenceLines;
        }
        if (payload.locationTracks != null) {
            shownItems.locationTracks = payload.locationTracks;
        }
        if (payload.kmPosts != null) {
            shownItems.kmPosts = payload.kmPosts;
        }
        if (payload.switches != null) {
            shownItems.switches = payload.switches;
        }
    },
    onViewportChange: (state: Map, action: PayloadAction<MapViewport>): void => {
        state.viewport = action.payload;
    },
    showArea: (state: Map, action: PayloadAction<BoundingBox>): void => {
        state.viewport = {
            center: centerForBoundingBox(action.payload),
            // Calculate new resolution by comparing previous and new bounding boxes.
            // Also zoom out a bit to make it look more natural (hence the "* 1.2").
            resolution: state.viewport.area
                ? state.viewport.resolution *
                  boundingBoxScale(state.viewport.area, action.payload) *
                  1.2
                : state.viewport.resolution,
        };
    },
    showLayers(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        state.layers = deduplicate([...state.layers, ...layers]);
    },
    hideLayers(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        state.layers = state.layers.filter((l) => !layers.some((p) => p === l));
    },
    onSettingsChange(state: Map, { payload }: PayloadAction<MapLayerSettingChange>) {
        state.settingsMenu.layout = updateSettings(state.settingsMenu.layout, payload);
        state.settingsMenu.geometry = updateSettings(state.settingsMenu.geometry, payload);
        state.settingsMenu.debug = updateSettings(state.settingsMenu.debug, payload);
        const allSettings = [
            ...state.settingsMenu.layout,
            ...state.settingsMenu.geometry,
            ...state.settingsMenu.debug,
        ];

        const changedLayers = collectChangedLayers(allSettings, payload);

        if (payload.visible) {
            this.showLayers(state, { payload: changedLayers, type: 'showLayers' });
        } else {
            const visibleLayers = collectVisibleLayers(allSettings, payload);
            const hiddenLayers = changedLayers.filter((l) => !visibleLayers.some((v) => v === l));

            hiddenLayers.forEach((l) => {
                const shownItemsToHide = shownItemsByLayer[l];
                if (shownItemsToHide) {
                    state.shownItems[shownItemsToHide] = [];
                }
            });

            this.hideLayers(state, { payload: hiddenLayers, type: 'hideLayers' });
        }
    },
    onHoverLocation: (state: Map, { payload: hoverLocation }: PayloadAction<Point>): void => {
        state.hoveredLocation = hoverLocation;
    },
    onClickLocation: (state: Map, { payload: clickLocation }: PayloadAction<Point>): void => {
        state.clickLocation = clickLocation;
    },
};

function collectVisibleLayers(
    settings: MapLayerSetting[],
    change: MapLayerSettingChange,
): MapLayerName[] {
    return settings.flatMap((s) => {
        if (s.visible && s.name !== change.name) {
            return [
                ...layerSettingMapLayers[s.name],
                ...collectVisibleLayers(s.subSettings ?? [], change),
            ];
        }

        return [];
    });
}

function collectChangedLayers(
    settings: MapLayerSetting[],
    change: MapLayerSettingChange,
    isChild = false,
): MapLayerName[] {
    return settings.flatMap(({ name, subSettings }) => {
        if (name === change.name) {
            if (change.visible) {
                return [
                    ...layerSettingMapLayers[name],
                    ...collectChangedLayers(
                        subSettings?.filter((s) => s.visible) ?? [],
                        change,
                        true,
                    ),
                ];
            } else {
                return [
                    ...layerSettingMapLayers[name],
                    ...collectChangedLayers(subSettings ?? [], change, true),
                ];
            }
        } else {
            return [
                ...(isChild ? layerSettingMapLayers[name] : []),
                ...collectChangedLayers(subSettings ?? [], change, isChild),
            ];
        }
    });
}

function updateSettings(
    settings: MapLayerSetting[],
    change: MapLayerSettingChange,
): MapLayerSetting[] {
    return settings.map((s) => ({
        name: s.name,
        visible: s.name === change.name ? change.visible : s.visible,
        subSettings: s.subSettings ? updateSettings(s.subSettings, change) : undefined,
    }));
}

type MapContextState = 'trackLayout' | 'infra-model' | 'preview';
export const MapContext = createContext<MapContextState>('trackLayout');
