import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { Map, MapLayerName } from 'map/map-model';
import { initialMapState, mapReducers } from 'map/map-store';
import {
    allSelectableItemTypes,
    OnSelectOptions,
    SelectableItemType,
    Selection,
    VisiblePlanLayout,
} from 'selection/selection-model';
import { wrapReducers } from 'store/store-utils';
import {
    initialSelectionState,
    selectionReducers,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    ToggleSwitchPayload,
} from 'selection/selection-store';
import { linkingReducers } from 'linking/linking-store';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { compareTrackMeterStrings, LayoutMode, PublishType, TrackMeter } from 'common/common-model';
import {
    GeometryPlanLayout,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackSplittingState,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { addIfExists, subtract } from 'utils/array-utils';
import { PublishRequestIds } from 'publication/publication-model';
import { ToolPanelAsset } from 'tool-panel/tool-panel';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { formatTrackMeter } from 'utils/geography-utils';

export type SelectedPublishChange = {
    trackNumber: LayoutTrackNumberId | undefined;
    referenceLine: ReferenceLineId | undefined;
    locationTrack: LocationTrackId | undefined;
    switch: LayoutSwitchId | undefined;
    kmPost: LayoutKmPostId | undefined;
};

export type InfoboxVisibilities = {
    trackNumber: TrackNumberInfoboxVisibilities;
    switch: SwitchInfoboxVisibilities;
    locationTrack: LocationTrackInfoboxVisibilities;
    kmPost: KmPostInfoboxVisibilities;
    geometryAlignment: GeometryAlignmentInfoboxVisibilities;
    geometryPlan: GeometryPlanInfoboxVisibilities;
    geometryKmPost: GeometryKmPostInfoboxVisibilities;
    geometrySwitch: GeometrySwitchInfoboxVisibilities;
};

export type TrackNumberInfoboxVisibilities = {
    basic: boolean;
    referenceLine: boolean;
    log: boolean;
    validation: boolean;
    geometry: boolean;
};

export type SwitchInfoboxVisibilities = {
    basic: boolean;
    structure: boolean;
    location: boolean;
    additionalInfo: boolean;
    log: boolean;
    validation: boolean;
};

export type LocationTrackInfoboxVisibilities = {
    basic: boolean;
    splitting: boolean;
    location: boolean;
    log: boolean;
    validation: boolean;
    ratkoPush: boolean;
    geometry: boolean;
    verticalGeometry: boolean;
};

export type KmPostInfoboxVisibilities = {
    basic: boolean;
    location: boolean;
    log: boolean;
    validation: boolean;
};

export type GeometryAlignmentInfoboxVisibilities = {
    basic: boolean;
    linking: boolean;
    geometry: boolean;
    verticalGeometry: boolean;
} & GeometryPlanInfoboxVisibilities;

export type GeometryKmPostInfoboxVisibilities = {
    basic: boolean;
    linking: boolean;
} & GeometryPlanInfoboxVisibilities;

export type GeometrySwitchInfoboxVisibilities = {
    basic: boolean;
} & GeometryPlanInfoboxVisibilities &
    GeometrySwitchLinkingInfoboxVisibilities;

export type GeometrySwitchLinkingInfoboxVisibilities = {
    linking: boolean;
    suggestedSwitch: boolean;
};

export type GeometryPlanInfoboxVisibilities = {
    plan: boolean;
    planQuality: boolean;
};

const initialInfoboxVisibilities: InfoboxVisibilities = {
    trackNumber: {
        basic: true,
        referenceLine: true,
        log: true,
        validation: true,
        geometry: true,
    },
    switch: {
        basic: true,
        structure: true,
        location: true,
        additionalInfo: true,
        log: true,
        validation: true,
    },
    locationTrack: {
        basic: true,
        splitting: true,
        location: true,
        log: true,
        validation: true,
        ratkoPush: true,
        geometry: true,
        verticalGeometry: true,
    },
    kmPost: {
        basic: true,
        location: true,
        log: true,
        validation: true,
    },
    geometryAlignment: {
        basic: true,
        linking: true,
        plan: true,
        planQuality: true,
        geometry: true,
        verticalGeometry: true,
    },
    geometryPlan: {
        plan: true,
        planQuality: true,
    },
    geometryKmPost: {
        plan: true,
        planQuality: true,
        basic: true,
        linking: true,
    },
    geometrySwitch: {
        plan: true,
        planQuality: true,
        basic: true,
        suggestedSwitch: true,
        linking: true,
    },
};

export const initialPublicationRequestIds: PublishRequestIds = {
    trackNumbers: [],
    referenceLines: [],
    locationTracks: [],
    switches: [],
    kmPosts: [],
};

export type TrackLayoutState = {
    publishType: PublishType;
    layoutMode: LayoutMode;
    map: Map;
    selection: Selection;
    stagedPublicationRequestIds: PublishRequestIds;
    linkingState?: LinkingState;
    splittingState?: LocationTrackSplittingState;
    linkingIssuesSelectedBeforeLinking: boolean;
    switchLinkingSelectedBeforeLinking: boolean;
    selectedToolPanelTab: ToolPanelAsset | undefined;
    infoboxVisibilities: InfoboxVisibilities;
};

export const initialTrackLayoutState: TrackLayoutState = {
    publishType: 'OFFICIAL',
    layoutMode: 'DEFAULT',
    map: initialMapState,
    selection: initialSelectionState,
    stagedPublicationRequestIds: initialPublicationRequestIds,
    linkingIssuesSelectedBeforeLinking: false,
    switchLinkingSelectedBeforeLinking: false,
    selectedToolPanelTab: undefined,
    infoboxVisibilities: initialInfoboxVisibilities,
};

export function getSelectableItemTypes(
    splittingState?: LocationTrackSplittingState | undefined,
    linkingState?: LinkingState | undefined,
): SelectableItemType[] {
    if (splittingState) return ['switches'];

    switch (linkingState?.type) {
        case LinkingType.UnknownAlignment:
            return ['locationTracks', 'trackNumbers'];
        case LinkingType.LinkingGeometryWithAlignment:
            return ['layoutLinkPoints', 'geometryLinkPoints', 'clusterPoints'];
        case LinkingType.LinkingGeometryWithEmptyAlignment:
            return ['geometryLinkPoints', 'clusterPoints'];
        case LinkingType.LinkingAlignment:
            return ['layoutLinkPoints', 'clusterPoints'];
        case LinkingType.PlacingSwitch:
            return [];
        case LinkingType.LinkingSwitch:
            return ['switches', 'suggestedSwitches'];
        case LinkingType.LinkingKmPost:
            return ['kmPosts'];
        case undefined:
            return allSelectableItemTypes;
        default:
            return exhaustiveMatchingGuard(linkingState);
    }
}

function filterItemSelectOptions(
    state: TrackLayoutState,
    options: OnSelectOptions,
): OnSelectOptions {
    const selectableItemTypes = getSelectableItemTypes(state.splittingState, state.linkingState);

    if (state.linkingState?.type == LinkingType.LinkingSwitch) {
        if (options.suggestedSwitches?.length == 0) {
            options.suggestedSwitches = undefined;
        }
    }

    return {
        ...options,

        // Set non-selectable items type collections to undefined
        ...allSelectableItemTypes.reduce((memo, itemType) => {
            return {
                ...memo,
                [itemType]: selectableItemTypes.includes(itemType) ? memo[itemType] : undefined,
            };
        }, options),
    };
}

const trackLayoutSlice = createSlice({
    name: 'trackLayout',
    initialState: initialTrackLayoutState,
    reducers: {
        ...wrapReducers((state: TrackLayoutState) => state.map, mapReducers),
        ...wrapReducers((state: TrackLayoutState) => state.selection, selectionReducers),
        ...wrapReducers((state: TrackLayoutState) => state, linkingReducers),

        onInfoboxVisibilityChange: (
            state: TrackLayoutState,
            action: PayloadAction<InfoboxVisibilities>,
        ): void => {
            state.infoboxVisibilities = action.payload;
        },

        onClickLocation: (state: TrackLayoutState, action: PayloadAction<Point>): void => {
            if (state.linkingState?.type == LinkingType.PlacingSwitch) {
                state.linkingState.location = action.payload;
            } else {
                mapReducers.onClickLocation(state.map, action);
            }
        },

        // Intercept select/highlight reducers to modify options
        onSelect: function (state: TrackLayoutState, action: PayloadAction<OnSelectOptions>): void {
            // Handle selection
            const options = filterItemSelectOptions(state, action.payload);
            selectionReducers.onSelect(state.selection, {
                ...action,
                payload: options,
            });

            // Set linking information
            switch (state.linkingState?.type) {
                case LinkingType.LinkingGeometryWithAlignment:
                case LinkingType.LinkingGeometryWithEmptyAlignment:
                    if (options.layoutLinkPoints?.length === 1) {
                        linkingReducers.setLayoutLinkPoint(state, {
                            type: '',
                            payload: options.layoutLinkPoints[0],
                        });
                    }
                    if (options.geometryLinkPoints?.length === 1) {
                        linkingReducers.setGeometryLinkPoint(state, {
                            type: '',
                            payload: options.geometryLinkPoints[0],
                        });
                    }
                    break;

                case LinkingType.LinkingAlignment:
                    if (options.layoutLinkPoints?.length === 1) {
                        linkingReducers.setLayoutLinkPoint(state, {
                            type: '',
                            payload: options.layoutLinkPoints[0],
                        });
                    }
                    break;
                case LinkingType.LinkingSwitch: {
                    const selectedSwitch = state.selection.selectedItems.switches[0];
                    linkingReducers.lockSwitchSelection(state, {
                        type: '',
                        payload: selectedSwitch,
                    });
                    break;
                }
            }
        },
        onPreviewSelect: function (
            state: TrackLayoutState,
            action: PayloadAction<SelectedPublishChange>,
        ): void {
            const trackNumbers = addIfExists(
                state.stagedPublicationRequestIds.trackNumbers,
                action.payload.trackNumber,
            );
            const referenceLines = addIfExists(
                state.stagedPublicationRequestIds.referenceLines,
                action.payload.referenceLine,
            );
            const locationTracks = addIfExists(
                state.stagedPublicationRequestIds.locationTracks,
                action.payload.locationTrack,
            );
            const switches = addIfExists(
                state.stagedPublicationRequestIds.switches,
                action.payload.switch,
            );
            const kmPosts = addIfExists(
                state.stagedPublicationRequestIds.kmPosts,
                action.payload.kmPost,
            );

            state.stagedPublicationRequestIds = {
                trackNumbers: trackNumbers,
                referenceLines: referenceLines,
                locationTracks: locationTracks,
                switches: switches,
                kmPosts: kmPosts,
            };
        },

        onPublishPreviewRemove: function (
            state: TrackLayoutState,
            action: PayloadAction<PublishRequestIds>,
        ): void {
            const stateCandidates = state.stagedPublicationRequestIds;
            const toRemove = action.payload;
            const trackNumbers = subtract(stateCandidates.trackNumbers, toRemove.trackNumbers);
            const referenceLines = subtract(
                stateCandidates.referenceLines,
                toRemove.referenceLines,
            );
            const locationTracks = subtract(
                stateCandidates.locationTracks,
                toRemove.locationTracks,
            );
            const switches = subtract(stateCandidates.switches, toRemove.switches);
            const kmPosts = subtract(stateCandidates.kmPosts, toRemove.kmPosts);
            state.stagedPublicationRequestIds = {
                trackNumbers,
                referenceLines,
                locationTracks,
                switches,
                kmPosts,
            };
        },

        onHighlightItems: function (
            state: TrackLayoutState,
            action: PayloadAction<OnSelectOptions>,
        ): void {
            selectionReducers.onHighlightItems(state.selection, {
                ...action,
                payload: filterItemSelectOptions(state, action.payload),
            });
        },
        togglePlanVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<VisiblePlanLayout>,
        ): void => {
            if (!state.linkingState) {
                const isPlanVisible = state.selection.visiblePlans.some(
                    (p) => p.id === action.payload?.id,
                );

                updateMapLayerVisibilities(state.map, isPlanVisible, [
                    'geometry-alignment-layer',
                    'geometry-switch-layer',
                    'geometry-km-post-layer',
                ]);

                selectionReducers.togglePlanVisibility(state.selection, action);
            }
        },
        toggleAlignmentVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<ToggleAlignmentPayload>,
        ) => {
            const { alignmentId, keepAlignmentVisible } = action.payload;
            const hideLayer = shouldHideMapLayer(
                state,
                'alignments',
                alignmentId,
                keepAlignmentVisible,
            );

            updateMapLayerVisibilities(state.map, hideLayer, ['geometry-alignment-layer']);

            selectionReducers.toggleAlignmentVisibility(state.selection, action);
        },
        toggleSwitchVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<ToggleSwitchPayload>,
        ) => {
            const { switchId, keepSwitchesVisible } = action.payload;

            const hideLayer = shouldHideMapLayer(state, 'switches', switchId, keepSwitchesVisible);

            updateMapLayerVisibilities(state.map, hideLayer, ['geometry-switch-layer']);

            selectionReducers.toggleSwitchVisibility(state.selection, action);
        },
        toggleKmPostsVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<ToggleKmPostPayload>,
        ) => {
            const { kmPostId, keepKmPostsVisible } = action.payload;

            const hideLayer = shouldHideMapLayer(state, 'kmPosts', kmPostId, keepKmPostsVisible);

            updateMapLayerVisibilities(state.map, hideLayer, ['geometry-km-post-layer']);

            selectionReducers.toggleKmPostsVisibility(state.selection, action);
        },
        onPublishTypeChange: (
            state: TrackLayoutState,
            { payload: publishType }: PayloadAction<PublishType>,
        ): void => {
            state.publishType = publishType;
            if (publishType == 'OFFICIAL') linkingReducers.stopLinking(state);
        },
        onLayoutModeChange: (
            state: TrackLayoutState,
            { payload: layoutMode }: PayloadAction<LayoutMode>,
        ): void => {
            state.layoutMode = layoutMode;
        },
        onPublish: (state: TrackLayoutState): void => {
            state.layoutMode = 'DEFAULT';
            state.stagedPublicationRequestIds = initialPublicationRequestIds;
        },
        setToolPanelTab: (
            state: TrackLayoutState,
            { payload }: PayloadAction<ToolPanelAsset | undefined>,
        ): void => {
            state.selectedToolPanelTab = payload;
        },
        onStartSplitting: (
            state: TrackLayoutState,
            {
                payload,
            }: PayloadAction<{
                start: { point: Point; address: TrackMeter };
                end: { point: Point; address: TrackMeter };
                locationTrack: LayoutLocationTrack;
            }>,
        ): void => {
            state.splittingState = {
                originLocationTrack: payload.locationTrack,
                splits: [
                    {
                        location: payload.start.address,
                        name: '',
                        descriptionBase: '',
                        suffixMode: 'NONE',
                    },
                ],
                endpoint: payload.end,
            };
        },
        cancelSplitting: (state: TrackLayoutState): void => {
            state.splittingState = undefined;
        },
        addSplit: (
            state: TrackLayoutState,
            { payload }: PayloadAction<{ point: Point; address: TrackMeter }>,
        ): void => {
            if (state.splittingState) {
                const splits = (state.splittingState.splits || []).concat([
                    {
                        location: payload.address,
                        name: '',
                        descriptionBase: '',
                        suffixMode: 'NONE',
                    },
                ]);
                splits.sort((a, b) =>
                    compareTrackMeterStrings(
                        formatTrackMeter(a.location),
                        formatTrackMeter(b.location),
                    ),
                );
                state.splittingState.splits = splits;
            }
        },
        removeSplit: (
            state: TrackLayoutState,
            { payload }: PayloadAction<{ address: TrackMeter }>,
        ): void => {
            if (state.splittingState) {
                state.splittingState.splits = (state.splittingState.splits || []).filter(
                    (split) =>
                        !(
                            split.location.meters == payload.address.meters &&
                            split.location.kmNumber == payload.address.kmNumber
                        ),
                );
            }
        },
        setSplitDuplicate: (
            state: TrackLayoutState,
            { payload }: PayloadAction<{ splitStart: TrackMeter; duplicateOf: LocationTrackId }>,
        ): void => {
            if (state.splittingState) {
                state.splittingState.splits.find(
                    (split) =>
                        formatTrackMeter(split.location) === formatTrackMeter(payload.splitStart),
                )!.duplicateOf = payload.duplicateOf;
            }
        },
    },
});

export const trackLayoutReducer = trackLayoutSlice.reducer;
export const trackLayoutActionCreators = trackLayoutSlice.actions;

const updateMapLayerVisibilities = (state: Map, shouldHide: boolean, layers: MapLayerName[]) => {
    const mapAction = shouldHide ? mapReducers.hideLayers : mapReducers.showLayers;
    mapAction(state, { payload: layers, type: shouldHide ? 'hideLayers' : 'showLayers' });
};

type GeometryItemType = Pick<GeometryPlanLayout, 'alignments' | 'kmPosts' | 'switches'>;

const shouldHideMapLayer = <T>(
    state: TrackLayoutState,
    key: keyof GeometryItemType,
    id: T,
    keepVisible: boolean | undefined,
) => {
    return keepVisible || !state.selection.visiblePlans.some((p) => p[key].some((i) => i !== id));
};
