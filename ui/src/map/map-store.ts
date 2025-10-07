import { PayloadAction } from '@reduxjs/toolkit';
import {
    HELSINKI_RAILWAY_STATION_COORDS,
    Map,
    MapLayerMenuChange,
    MapLayerMenuItem,
    MapLayerMenuItemName,
    MapLayerName,
    MapLayerSettingChange,
    MapViewport,
    OptionalShownItems,
    ShownItems,
    VerticalAlignmentVisibleExtentChange,
} from 'map/map-model';
import { createContext } from 'react';
import { BoundingBox, boundingBoxScale, centerForBoundingBox, Point } from 'model/geometry';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { initialVerticalGeometryDiagramState, planAlignmentKey } from 'vertical-geometry/store';

export function getEmptyShownItems(): ShownItems {
    return {
        referenceLines: [],
        locationTracks: [],
        kmPosts: [],
        switches: [],
    };
}

export const isLayerInProxyLayerCollection = (
    menuItemName: MapLayerMenuItemName,
    visibleLayers: MapLayerName[],
    proxyLayerCollection: LayerCollection,
): boolean => {
    const layersFromMenuItem = layerMenuItemMapLayers[menuItemName];
    const keys = Object.keys(proxyLayerCollection).filter((key) =>
        proxyLayerCollection[key as MapLayerName]?.find((layer) =>
            layersFromMenuItem.includes(layer),
        ),
    );
    return visibleLayers.some((layer) => keys.includes(layer));
};

const alwaysOnLayers: MapLayerName[] = ['plan-section-highlight-layer'];

type LayerCollection = { [key in MapLayerName]?: MapLayerName[] };

export const layersToShowByProxy: LayerCollection = {
    'track-number-diagram-layer': ['reference-line-badge-layer', 'track-number-addresses-layer'],
    'switch-linking-layer': ['switch-layer', 'geometry-switch-layer'],
    'alignment-linking-layer': ['location-track-alignment-layer', 'geometry-alignment-layer'],
    'virtual-km-post-linking-layer': ['km-post-layer', 'geometry-km-post-layer'],
    'location-track-split-location-layer': [
        'duplicate-split-section-highlight-layer',
        'location-track-duplicate-endpoint-address-layer',
        'location-track-split-badge-layer',
        'switch-layer',
    ],
};

export const layersToHideByProxy: LayerCollection = {
    'location-track-split-location-layer': [
        'location-track-badge-layer',
        'geometry-alignment-layer',
        'geometry-switch-layer',
        'geometry-km-post-layer',
        'plan-area-layer',
        'track-number-diagram-layer',
    ],
    'virtual-hide-geometry-layer': [
        'geometry-alignment-layer',
        'geometry-switch-layer',
        'geometry-km-post-layer',
        'plan-area-layer',
    ],
};

export const relatedMapLayers: LayerCollection = {
    'location-track-alignment-layer': [
        'location-track-background-layer',
        'location-track-badge-layer',
    ],
    'reference-line-alignment-layer': [
        'reference-line-background-layer',
        'reference-line-badge-layer',
    ],
};

// like hiding by proxy, except with no effect on the displayed layer list, only covering the given layers right at
// the end
export const layersCoveringLayers: LayerCollection = {
    'orthographic-background-map-layer': ['background-map-layer'],
};

const layerMenuItemMapLayers: Record<MapLayerMenuItemName, MapLayerName[]> = {
    'map': ['background-map-layer'],
    'orthographic-background-map': ['orthographic-background-map-layer'],
    'location-track': ['location-track-alignment-layer', 'location-track-badge-layer'],
    'reference-line': ['reference-line-alignment-layer', 'reference-line-badge-layer'],
    'reference-line-hide-when-zoomed-close': [], // This is technically a setting, not a map layer by itself.
    'missing-vertical-geometry': ['missing-profile-highlight-layer'],
    'missing-linking': ['missing-linking-highlight-layer'],
    'duplicate-tracks': ['duplicate-tracks-highlight-layer'],
    'track-number-diagram': ['track-number-diagram-layer'],
    'km-post': ['km-post-layer'],
    'switch': ['switch-layer'],
    'geometry-alignment': ['geometry-alignment-layer'],
    'geometry-switch': ['geometry-switch-layer'],
    'plan-area': ['plan-area-layer'],
    'geometry-km-post': ['geometry-km-post-layer'],
    'operating-points': ['operating-points-layer'],
    'debug-1m': ['debug-1m-points-layer'],
    'debug-projection-lines': ['debug-projection-lines-layer'],
    'debug': ['debug-layer'],
    'debug-layout-graph': ['debug-geometry-graph-layer'],
    'debug-layout-graph-nano': [], // This is technically a setting, not a map layer by itself.
};

export const initialMapState: Map = {
    visibleLayers: [
        'background-map-layer',
        'publication-candidate-layer',
        'deleted-publication-candidate-icon-layer',
        'location-track-background-layer',
        'reference-line-background-layer',
        'location-track-badge-layer',
        'reference-line-badge-layer',
        'location-track-alignment-layer',
        'reference-line-alignment-layer',
        'plan-section-highlight-layer',
        'km-post-layer',
        'switch-layer',
        'geometry-alignment-layer',
        'geometry-switch-layer',
        'geometry-km-post-layer',
        'location-track-selected-alignment-layer',
        'location-track-split-alignment-layer',
        'reference-line-selected-alignment-layer',
    ],
    layerMenu: {
        layout: [
            {
                name: 'map',
                visible: true,
                qaId: 'background-map-layer',
                subMenu: [
                    {
                        name: 'orthographic-background-map',
                        visible: false,
                        qaId: 'orthographic-background-map-layer',
                    },
                ],
            },
            {
                name: 'reference-line',
                visible: true,
                qaId: 'reference-line-layer',
                subMenu: [
                    {
                        name: 'reference-line-hide-when-zoomed-close',
                        visible: false,
                        qaId: 'reference-line-hide-when-zoomed-close',
                    },
                ],
            },
            {
                name: 'location-track',
                visible: true,
                qaId: 'location-track-layer',
                subMenu: [
                    {
                        name: 'missing-vertical-geometry',
                        visible: false,
                        qaId: 'missing-vertical-geometry-layer',
                    },
                    { name: 'missing-linking', visible: false, qaId: 'missing-linking-layer' },
                    { name: 'duplicate-tracks', visible: false, qaId: 'duplicate-tracks-layer' },
                ],
            },
            { name: 'switch', visible: true, qaId: 'switch-layer' },
            { name: 'km-post', visible: true, qaId: 'km-post-layer' },
            { name: 'track-number-diagram', visible: false, qaId: 'track-number-diagram-layer' },
            { name: 'operating-points', visible: false, qaId: 'operating-points-layer' },
        ],
        geometry: [
            { name: 'geometry-alignment', visible: true, qaId: 'geometry-alignment-layer' },
            { name: 'geometry-switch', visible: true, qaId: 'geometry-switch-layer' },
            { name: 'geometry-km-post', visible: true, qaId: 'geometry-km-post-layer' },
            { name: 'plan-area', visible: false, qaId: 'geometry-area-layer' },
        ],
        debug: [
            { name: 'debug-1m', visible: false },
            { name: 'debug-projection-lines', visible: false },
            { name: 'debug', visible: false },
            {
                name: 'debug-layout-graph',
                visible: false,
                qaId: 'debug-layout-graph-layer',
                subMenu: [
                    {
                        name: 'debug-layout-graph-nano',
                        visible: false,
                        qaId: 'debug-layout-graph-nano',
                    },
                ],
            },
        ],
    },
    layerSettings: {
        'track-number-diagram-layer': {},
    },
    shownItems: getEmptyShownItems(),
    viewport: {
        center: HELSINKI_RAILWAY_STATION_COORDS,
        resolution: 20,
    },
    verticalGeometryDiagramState: initialVerticalGeometryDiagramState,
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
        const newVisibleLayers = deduplicate([
            ...alwaysOnLayers,
            ...state.visibleLayers,
            ...layers,
            ...collectRelatedLayers(layers),
        ]);
        const layersHiddenByProxy = collectLayersHiddenByProxy(newVisibleLayers);
        state.visibleLayers = newVisibleLayers.filter(
            (layer) => !layersHiddenByProxy.includes(layer),
        );
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
        const layersHiddenByProxy = collectLayersHiddenByProxy(visibleLayers);

        state.visibleLayers = deduplicate([
            ...alwaysOnLayers,
            ...visibleLayers,
            ...collectRelatedLayers(visibleLayers),
        ]).filter((layer) => !layersHiddenByProxy.includes(layer));
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
        state.verticalGeometryDiagramState.visible = visibilitySetting;
    },
    onVerticalGeometryDiagramAlignmentVisibleExtentChange: (
        state: Map,
        { payload: action }: PayloadAction<VerticalAlignmentVisibleExtentChange>,
    ): void => {
        if ('planId' in action.alignmentId) {
            state.verticalGeometryDiagramState.visibleExtentLookup.plan[
                planAlignmentKey(action.alignmentId.planId, action.alignmentId.alignmentId)
            ] = action.extent;
        } else {
            state.verticalGeometryDiagramState.visibleExtentLookup.layout[
                action.alignmentId.locationTrackId
            ] = action.extent;
        }
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

const collectLayersHiddenByProxy = (items: MapLayerName[]) =>
    deduplicate(items.flatMap((i) => layersToHideByProxy[i]).filter(filterNotEmpty));

function collectRelatedLayers(layers: MapLayerName[]): MapLayerName[] {
    const allRelatedMapLayers = { ...layersToShowByProxy, ...relatedMapLayers };
    const relatedLayers = layers.flatMap((l) => allRelatedMapLayers[l]).filter(filterNotEmpty);

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

export type MapContextState = 'track-layout' | 'infra-model';
export const MapContext = createContext<MapContextState>('track-layout');
