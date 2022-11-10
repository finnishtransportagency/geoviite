import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { LayoutAlignmentsLayer, Map, MapLayerType, MapViewport, OptionalShownItems, ShownItems } from 'map/map-model';
import { createContext } from 'react';
import { BoundingBox, boundingBoxScale, centerForBoundingBox, Point } from 'model/geometry';

export type LayerVisibility = {
    layerId: string;
    visible: boolean;
};

export function createEmptyShownItems(): ShownItems {
    return {
        trackNumbers: [],
        referenceLines: [],
        locationTracks: [],
        kmPosts: [],
        switches: [],
    };
}

export const initialMapState: Map = {
    mapLayers: [
        {
            type: 'tile',
            name: 'Taustakartta',
            id: 'tile-1',
            visible: true,
            url: 'OSM',
        },
        {
            type: 'alignment',
            name: 'Sijaintiraiteet',
            id: 'trackLayout-1',
            visible: true,
            showTrackNumbers: true,
        },
        {
            type: 'geometry',
            name: 'Suunnitelmat',
            id: 'geometry-1',
            visible: true,
            planIds: [],
            planLayout: null,
        },
        {
            type: 'kmPosts',
            name: 'Kilometripylväät',
            id: 'kmposts-1',
            visible: true,
        },
        {
            type: 'switches',
            name: 'Vaihteet',
            id: 'switches-1',
            visible: true,
        },
        {
            type: 'geometrySwitches',
            name: 'Suunnitelman vaihteet',
            id: 'geom-switches-1',
            visible: true,
        },
        {
            type: 'planAreas',
            name: 'Suunnitelman alueet',
            id: 'geom-plan-areas-1',
            visible: false,
        },
        {
            type: 'geometryKmPosts',
            name: 'Suunnitelman kilometripylväät',
            id: 'geom-kmposts-1',
            visible: true,
        },
        {
            type: 'linking',
            name: 'Linkitys',
            id: 'linking-1',
            visible: true,
        },
        {
            type: 'switchLinking',
            name: 'Vaihteiden linkitys',
            id: 'switchLinking',
            visible: true,
        },
        {
            type: 'manualSwitchLinking',
            name: 'Manuaalinen vaihteiden linkitys',
            id: 'manualSwitchLinking',
            visible: false,
        },
        {
            type: 'debug1mPoints',
            name: 'Kehittäjien työkalut: 1m-pisteet',
            id: 'debug1mPoints',
            visible: false,
        },
        {
            type: 'debug',
            name: 'Kehittäjien työkalut',
            id: 'debug',
            visible: true,
            data: [],
        },
    ],
    shownItems: createEmptyShownItems(),
    viewport: {
        center: {
            x: 385782.89,
            y: 6672277.83,
        },
        resolution: 20,
    },
    settingsVisible: false,
    hoveredLocation: null,
    clickLocation: null,
};

export const mapReducers = {
    onShownItemsChange: (
        {shownItems}: Map,
        {payload}: PayloadAction<OptionalShownItems>,
    ): void => {
        if (payload.trackNumbers != null) {
            shownItems.trackNumbers = payload.trackNumbers;
        }
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
    onLayerVisibilityChange: (
        state: Map,
        {payload: visibilitySetting}: PayloadAction<LayerVisibility>,
    ): void => {
        state.mapLayers.forEach((layer) => {
            if (layer.id == visibilitySetting.layerId) {
                layer.visible = visibilitySetting.visible;
                if (!visibilitySetting.visible) {
                    const shownItemsToHide = shownItemsByLayer(layer.type);
                    if (shownItemsToHide) {
                        state.shownItems[shownItemsToHide] = [];
                    }
                }
            }
        });
    },

    onTrackNumberVisibilityChange: (
        state: Map,
        {payload: visibilitySetting}: PayloadAction<LayerVisibility>,
    ): void => {
        state.mapLayers.forEach((layer) => {
            if (layer.id == visibilitySetting.layerId) {
                (<LayoutAlignmentsLayer>layer).showTrackNumbers = visibilitySetting.visible;
            }
        });
    },
    onMapSettingsVisibilityChange: (
        state: Map,
        {payload: visible}: PayloadAction<boolean>,
    ): void => {
        state.settingsVisible = visible;
    },
    onHoverLocation: (state: Map, {payload: hoverLocation}: PayloadAction<Point>): void => {
        state.hoveredLocation = hoverLocation;
    },
    onClickLocation: (state: Map, {payload: clickLocation}: PayloadAction<Point>): void => {
        state.clickLocation = clickLocation;
    },
};

function shownItemsByLayer(layerType: MapLayerType): keyof ShownItems | undefined {
    switch (layerType) {
        case 'switches':
            return 'switches';
        case 'kmPosts':
            return 'kmPosts';
        case 'alignment':
            return 'locationTracks';
        default:
            return undefined;
    }
}

const mapSlice = createSlice({
    name: 'map',
    initialState: initialMapState,
    reducers: mapReducers,
});

type MapContextState = 'trackLayout';
export const MapContext = createContext<MapContextState>('trackLayout');

export const mapReducer = mapSlice.reducer;
export const {
    onViewportChange,
    onHoverLocation,
    onLayerVisibilityChange,
    onTrackNumberVisibilityChange,
} = mapSlice.actions;
