import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import {
    LayoutAlignmentLayer,
    Map,
    MapLayerType,
    MapViewport,
    OptionalShownItems,
    ShownItems,
} from 'map/map-model';
import { createContext } from 'react';
import { BoundingBox, boundingBoxScale, centerForBoundingBox, Point } from 'model/geometry';

export type LayerVisibility = {
    type: string;
    visible: boolean;
};

export function createEmptyShownItems(): ShownItems {
    return {
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
            visible: true,
            url: 'OSM',
        },
        {
            type: 'alignment',
            name: 'Sijaintiraiteet',
            visible: true,
            showReferenceLines: true,
            showMissingVerticalGeometry: false,
            showSegmentsFromSelectedPlan: false,
            showMissingLinking: false,
            showDuplicateTracks: false,
        },
        {
            type: 'geometryAlignment',
            name: 'Suunnitelman raiteet',
            visible: true,
            planIds: [],
            planLayout: null,
        },
        {
            type: 'trackNumberDiagram',
            name: 'Pituusmittausraidekaavio',
            visible: false,
        },
        {
            type: 'kmPost',
            name: 'Tasakilometripisteet',
            visible: true,
        },
        {
            type: 'switch',
            name: 'Vaihteet',
            visible: true,
        },
        {
            type: 'geometrySwitch',
            name: 'Suunnitelman vaihteet',
            visible: true,
        },
        {
            type: 'planArea',
            name: 'Suunnitelman alueet',
            visible: false,
        },
        {
            type: 'geometryKmPost',
            name: 'Suunnitelman tasakilometripisteet',
            visible: true,
        },
        {
            type: 'linking',
            name: 'Linkitys',
            visible: true,
        },
        {
            type: 'switchLinking',
            name: 'Vaihteiden linkitys',
            visible: false,
        },
        {
            type: 'switchManualLinking',
            name: 'Manuaalinen vaihteiden linkitys',
            visible: false,
        },
        {
            type: 'debug1mPoints',
            name: 'Kehittäjien työkalut: 1m-pisteet',
            visible: false,
        },
        {
            type: 'debug',
            name: 'Kehittäjien työkalut',
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
    onLayerVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        state.mapLayers.forEach((layer) => {
            if (layer.type == visibilitySetting.type) {
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
    onReferencelineVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        onAlignmentLayerVisibilityChange(state, 'showReferenceLines', visibilitySetting);
    },
    onMissingVerticalGeometryVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        onAlignmentLayerVisibilityChange(state, 'showMissingVerticalGeometry', visibilitySetting);
    },
    onShowSegmentsFromSelectedPlanVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        onAlignmentLayerVisibilityChange(state, 'showSegmentsFromSelectedPlan', visibilitySetting);
    },
    onMissingLinkingVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        onAlignmentLayerVisibilityChange(state, 'showMissingLinking', visibilitySetting);
    },
    onDuplicateTracksVisibilityChange: (
        state: Map,
        { payload: visibilitySetting }: PayloadAction<LayerVisibility>,
    ): void => {
        onAlignmentLayerVisibilityChange(state, 'showDuplicateTracks', visibilitySetting);
    },
    onMapSettingsVisibilityChange: (
        state: Map,
        { payload: visible }: PayloadAction<boolean>,
    ): void => {
        state.settingsVisible = visible;
    },
    onHoverLocation: (state: Map, { payload: hoverLocation }: PayloadAction<Point>): void => {
        state.hoveredLocation = hoverLocation;
    },
    onClickLocation: (state: Map, { payload: clickLocation }: PayloadAction<Point>): void => {
        state.clickLocation = clickLocation;
    },
};

function onAlignmentLayerVisibilityChange(
    state: Map,
    property:
        | 'showReferenceLines'
        | 'showMissingVerticalGeometry'
        | 'showSegmentsFromSelectedPlan'
        | 'showMissingLinking'
        | 'showDuplicateTracks',
    layerVisibility: LayerVisibility,
) {
    state.mapLayers.forEach((layer) => {
        if (layer.type == layerVisibility.type) {
            (<LayoutAlignmentLayer>layer)[property] = layerVisibility.visible;
        }
    });
}

function shownItemsByLayer(layerType: MapLayerType): keyof ShownItems | undefined {
    switch (layerType) {
        case 'switch':
            return 'switches';
        case 'kmPost':
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
    onReferencelineVisibilityChange,
    onMissingLinkingVisibilityChange,
    onShowSegmentsFromSelectedPlanVisibilityChange,
    onDuplicateTracksVisibilityChange,
} = mapSlice.actions;
