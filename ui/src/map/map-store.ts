import { PayloadAction } from '@reduxjs/toolkit';
import {
    HELSINKI_RAILWAY_STATION_COORDS,
    Map,
    MapLayerMenuChange,
    MapLayerMenuItem,
    MapLayerMenuItemName,
    MapLayerMenuGroups,
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
    'operational-points-placing-layer': ['operational-points-icon-layer'],
    'operational-points-area-placing-layer': [
        'operational-points-icon-layer',
        'operational-points-area-layer',
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
    'operational-points-icon-layer': ['operational-points-badge-layer'],
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

export const layerMenuItemMapLayers: Record<MapLayerMenuItemName, MapLayerName[]> = {
    'background-map': ['background-map-layer'],
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
    'operational-points': ['operational-points-icon-layer', 'operational-points-badge-layer'],
    'operational-point-areas': ['operational-points-area-layer'],
    'signal-asset': ['signal-asset-layer'],
    'debug-1m': ['debug-1m-points-layer'],
    'debug-projection-lines': ['debug-projection-lines-layer'],
    'debug': ['debug-layer'],
    'debug-layout-graph': ['debug-geometry-graph-layer'],
    'debug-layout-graph-nano': [], // This is technically a setting, not a map layer by itself.
};

export const initialMapState: Map = {
    forcedVisibleLayers: [],
    layerMenu: {
        layout: [
            {
                name: 'background-map',
                selected: true,
                subMenu: [
                    {
                        name: 'orthographic-background-map',
                        selected: false,
                    },
                ],
            },
            {
                name: 'reference-line',
                selected: true,
                subMenu: [
                    {
                        name: 'reference-line-hide-when-zoomed-close',
                        selected: false,
                    },
                    {
                        name: 'track-number-diagram',
                        selected: false,
                    },
                ],
            },
            {
                name: 'location-track',
                selected: true,
                subMenu: [
                    {
                        name: 'missing-vertical-geometry',
                        selected: false,
                    },
                    { name: 'missing-linking', selected: false },
                    { name: 'duplicate-tracks', selected: false },
                ],
            },
            { name: 'switch', selected: true },
            { name: 'km-post', selected: true },
            {
                name: 'operational-points',
                selected: true,
                subMenu: [
                    {
                        name: 'operational-point-areas',
                        selected: false,
                    },
                ],
            },
            { name: 'signal-asset', selected: false },
        ],
        geometry: [
            { name: 'geometry-alignment', selected: true },
            { name: 'geometry-switch', selected: true },
            { name: 'geometry-km-post', selected: true },
            { name: 'plan-area', selected: false },
        ],
        debug: [
            { name: 'debug-1m', selected: false },
            { name: 'debug-projection-lines', selected: false },
            { name: 'debug', selected: false },
            {
                name: 'debug-layout-graph',
                selected: false,
                subMenu: [
                    {
                        name: 'debug-layout-graph-nano',
                        selected: false,
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
    addForcedVisibleLayer(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        state.forcedVisibleLayers = deduplicate([...state.forcedVisibleLayers, ...layers]);
    },
    removeForcedVisibleLayer(state: Map, { payload: layers }: PayloadAction<MapLayerName[]>) {
        state.forcedVisibleLayers = state.forcedVisibleLayers.filter((l) => !layers.includes(l));
    },
    onLayerMenuItemChange(state: Map, { payload: change }: PayloadAction<MapLayerMenuChange>) {
        state.layerMenu.layout = updateMenuItem(state.layerMenu.layout, change);
        state.layerMenu.geometry = updateMenuItem(state.layerMenu.geometry, change);
        state.layerMenu.debug = updateMenuItem(state.layerMenu.debug, change);
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

function collectVisibleLayers(items: MapLayerMenuItem[]): MapLayerName[] {
    return items.flatMap((i) =>
        i.selected
            ? [...layerMenuItemMapLayers[i.name], ...collectVisibleLayers(i.subMenu ?? [])]
            : [],
    );
}

const collectLayersHiddenByProxy = (items: MapLayerName[]) =>
    deduplicate(items.flatMap((i) => layersToHideByProxy[i]).filter(filterNotEmpty));

export function collectRelatedLayers(layers: MapLayerName[]): MapLayerName[] {
    const allRelatedMapLayers = { ...layersToShowByProxy, ...relatedMapLayers };
    const relatedLayers = layers.flatMap((l) => allRelatedMapLayers[l]).filter(filterNotEmpty);

    return relatedLayers.length > 0
        ? [...relatedLayers, ...collectRelatedLayers(relatedLayers)]
        : [];
}

function updateMenuItem(items: MapLayerMenuItem[], change: MapLayerMenuChange): MapLayerMenuItem[] {
    return items.map((i) => ({
        name: i.name,
        selected: i.name === change.name ? change.selected : i.selected,
        subMenu: i.subMenu ? updateMenuItem(i.subMenu, change) : undefined,
    }));
}

export function selectVisibleLayers(
    layerMenu: MapLayerMenuGroups,
    forcedVisibleLayers: MapLayerName[],
): MapLayerName[] {
    const menuLayers = collectVisibleLayers([
        ...layerMenu.layout,
        ...layerMenu.geometry,
        ...layerMenu.debug,
    ]);
    const allLayers = [...alwaysOnLayers, ...menuLayers, ...forcedVisibleLayers];
    const related = collectRelatedLayers(allLayers);
    const visible = deduplicate([...allLayers, ...related]);
    const hidden = collectLayersHiddenByProxy(visible);
    return visible.filter((layer) => !hidden.includes(layer));
}

export type MapContextState = 'track-layout' | 'infra-model';
export const MapContext = createContext<MapContextState>('track-layout');
