import { PayloadAction } from '@reduxjs/toolkit';
import {
    Map,
    MapLayerMenuChange,
    MapLayerMenuItem,
    MapLayerMenuItemName,
    MapLayerName,
    MapLayerSettingChange,
    MapViewport,
    OptionalShownItems,
    ShownItems,
} from 'map/map-model';
import { createContext } from 'react';
import { BoundingBox, boundingBoxScale, centerForBoundingBox, Point } from 'model/geometry';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';

export function createEmptyShownItems(): ShownItems {
    return {
        referenceLines: [],
        locationTracks: [],
        kmPosts: [],
        switches: [],
    };
}

const relatedMapLayers: { [key in MapLayerName]?: MapLayerName[] } = {
    'track-number-diagram-layer': ['reference-line-badge-layer', 'track-number-addresses-layer'],
    'switch-linking-layer': ['switch-layer'],
    'alignment-linking-layer': ['location-track-alignment-layer', 'geometry-alignment-layer'],
    'location-track-alignment-layer': [
        'location-track-background-layer',
        'location-track-badge-layer',
    ],
    'reference-line-alignment-layer': [
        'reference-line-background-layer',
        'reference-line-badge-layer',
    ],
};

const layerMenuItemMapLayers: Record<MapLayerMenuItemName, MapLayerName[]> = {
    'map': ['background-map-layer'],
    'location-track': ['location-track-alignment-layer', 'location-track-badge-layer'],
    'reference-line': ['reference-line-alignment-layer', 'reference-line-badge-layer'],
    'missing-vertical-geometry': ['missing-profile-highlight-layer'],
    'missing-linking': ['missing-linking-highlight-layer'],
    'duplicate-tracks': ['duplicate-tracks-highlight-layer'],
    'plan-section': ['plan-section-highlight-layer'],
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
    visibleLayers: [
        'background-map-layer',
        'location-track-alignment-layer',
        'plan-section-highlight-layer',
        'reference-line-alignment-layer',
        'km-post-layer',
        'switch-layer',
        'geometry-alignment-layer',
        'geometry-switch-layer',
        'geometry-km-post-layer',
    ],
    layerMenu: {
        layout: [
            { name: 'map', visible: true },
            { name: 'reference-line', visible: true },
            {
                name: 'location-track',
                visible: true,
                subMenu: [
                    { name: 'missing-vertical-geometry', visible: false },
                    { name: 'missing-linking', visible: false },
                    { name: 'duplicate-tracks', visible: false },
                    { name: 'plan-section', visible: true },
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
    layerSettings: {
        'track-number-diagram-layer': {},
    },
    shownItems: createEmptyShownItems(),
    viewport: {
        center: {
            x: 385782.89,
            y: 6672277.83,
        },
        resolution: 20,
    },
    clickLocation: null,
    verticalGeometryDiagramVisible: false,
};

export const mapReducers = {
    onShownItemsChange: ({ shownItems }: Map, { payload }: PayloadAction<OptionalShownItems>) => {
        if ('referenceLines' in payload) shownItems.referenceLines = payload.referenceLines ?? [];
        if ('locationTracks' in payload) shownItems.locationTracks = payload.locationTracks ?? [];
        if ('kmPosts' in payload) shownItems.kmPosts = payload.kmPosts ?? [];
        if ('switches' in payload) shownItems.switches = payload.switches ?? [];
    },
    onViewportChange: (state: Map, { payload: viewPort }: PayloadAction<MapViewport>) => {
        state.viewport = viewPort;
    },
    showArea: (state: Map, { payload: boundingBox }: PayloadAction<BoundingBox>) => {
        state.viewport = {
            center: centerForBoundingBox(boundingBox),
            // Calculate new resolution by comparing previous and new bounding boxes.
            // Also zoom out a bit to make it look more natural (hence the "* 1.2").
            resolution: state.viewport.area
                ? state.viewport.resolution *
                  boundingBoxScale(state.viewport.area, boundingBox) *
                  1.2
                : state.viewport.resolution,
        };
    },
    showLayers(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        state.visibleLayers = deduplicate([
            ...state.visibleLayers,
            ...layers,
            ...collectRelatedLayers(layers),
        ]);
    },
    hideLayers(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        const relatedLayers = collectRelatedLayers(layers);
        const layersByMenu = collectVisibleLayers([
            ...state.layerMenu.layout,
            ...state.layerMenu.geometry,
            ...state.layerMenu.debug,
        ]);

        const visibleLayers = state.visibleLayers
            .filter((l) => !relatedLayers.includes(l) && !layers.includes(l))
            .concat(layersByMenu);

        state.visibleLayers = deduplicate([
            ...visibleLayers,
            ...collectRelatedLayers(visibleLayers),
        ]);
    },
    onLayerMenuItemChange(state: Map, { payload: change }: PayloadAction<MapLayerMenuChange>) {
        state.layerMenu.layout = updateMenuItem(state.layerMenu.layout, change);
        state.layerMenu.geometry = updateMenuItem(state.layerMenu.geometry, change);
        state.layerMenu.debug = updateMenuItem(state.layerMenu.debug, change);
        const allMenuItems = [
            ...state.layerMenu.layout,
            ...state.layerMenu.geometry,
            ...state.layerMenu.debug,
        ];

        const changedLayers = collectChangedLayers(allMenuItems, change);

        if (change.visible) {
            this.showLayers(state, { payload: changedLayers, type: 'showLayers' });
        } else {
            this.hideLayers(state, { payload: changedLayers, type: 'hideLayers' });
        }
    },
    onLayerSettingChange: (
        state: Map,
        { payload: change }: PayloadAction<MapLayerSettingChange>,
    ) => {
        state.layerSettings[change.name] = change.settings;
    },
    onClickLocation: (state: Map, { payload: clickLocation }: PayloadAction<Point>) => {
        state.clickLocation = clickLocation;
    },
    onVerticalGeometryDiagramVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<boolean>,
    ): void => {
        state.verticalGeometryDiagramVisible = visibilitySetting;
    },
};

function collectChangedLayers(
    items: MapLayerMenuItem[],
    change: MapLayerMenuChange,
    isChild = false,
): MapLayerName[] {
    return items.flatMap(({ name, subMenu }) => {
        if (name === change.name) {
            if (change.visible) {
                return [
                    ...layerMenuItemMapLayers[name],
                    ...collectChangedLayers(subMenu?.filter((i) => i.visible) ?? [], change, true),
                ];
            } else {
                return [
                    ...layerMenuItemMapLayers[name],
                    ...collectChangedLayers(subMenu ?? [], change, true),
                ];
            }
        } else {
            return [
                ...(isChild ? layerMenuItemMapLayers[name] : []),
                ...collectChangedLayers(subMenu ?? [], change, isChild),
            ];
        }
    });
}

function collectVisibleLayers(items: MapLayerMenuItem[]): MapLayerName[] {
    return items.flatMap((i) =>
        i.visible
            ? [...layerMenuItemMapLayers[i.name], ...collectVisibleLayers(i.subMenu ?? [])]
            : [],
    );
}

function collectRelatedLayers(layers: MapLayerName[]): MapLayerName[] {
    const relatedLayers = layers.flatMap((l) => relatedMapLayers[l]).filter(filterNotEmpty);

    return relatedLayers.length > 0
        ? [...relatedLayers, ...collectRelatedLayers(relatedLayers)]
        : [];
}

function updateMenuItem(items: MapLayerMenuItem[], change: MapLayerMenuChange): MapLayerMenuItem[] {
    return items.map((i) => ({
        name: i.name,
        visible: i.name === change.name ? change.visible : i.visible,
        subMenu: i.subMenu ? updateMenuItem(i.subMenu, change) : undefined,
    }));
}

type MapContextState = 'trackLayout' | 'infra-model' | 'preview';
export const MapContext = createContext<MapContextState>('trackLayout');
