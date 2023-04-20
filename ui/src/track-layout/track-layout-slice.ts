import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { Map } from 'map/map-model';
import { initialMapState, mapReducers } from 'map/map-store';
import {
    allSelectableItemTypes,
    OnSelectOptions,
    SelectableItemType,
    Selection,
} from 'selection/selection-model';
import { wrapReducers } from 'store/store-utils';
import { initialSelectionState, selectionReducers } from 'selection/selection-store';
import { linkingReducers } from 'linking/linking-store';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { LayoutMode, PublishType } from 'common/common-model';
import {
    GeometryPlanLayout,
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { addIfExists, subtract } from 'utils/array-utils';
import { PublishRequestIds } from 'publication/publication-model';

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
    location: boolean;
    log: boolean;
    validation: boolean;
    ratkoPush: boolean;
    geometry: boolean;
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
        location: true,
        log: true,
        validation: true,
        ratkoPush: true,
        geometry: true,
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
    linkingIssuesSelectedBeforeLinking: boolean;
    switchLinkingSelectedBeforeLinking: boolean;
    selectedToolPanelTabId: string | undefined;
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
    selectedToolPanelTabId: undefined,
    infoboxVisibilities: initialInfoboxVisibilities,
};

export function getSelectableItemTypes(
    linkingState?: LinkingState | undefined,
): SelectableItemType[] {
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
        default:
            return allSelectableItemTypes;
    }
}

function filterItemSelectOptions(
    state: TrackLayoutState,
    options: OnSelectOptions,
): OnSelectOptions {
    const selectableItemTypes = getSelectableItemTypes(state.linkingState);

    if (state.linkingState?.type == LinkingType.LinkingSwitch) {
        if (options.suggestedSwitches?.length == 0) {
            options.suggestedSwitches = undefined;
        }
    }

    return {
        ...options,

        // Set non selectable items type collections to undefined
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
            action: PayloadAction<GeometryPlanLayout | null>,
        ): void => {
            if (!state.linkingState) {
                selectionReducers.togglePlanVisibility(state.selection, action);
            }
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
            { payload }: PayloadAction<string | undefined>,
        ): void => {
            state.selectedToolPanelTabId = payload;
        },
    },
});

export const trackLayoutReducer = trackLayoutSlice.reducer;
export const trackLayoutActionCreators = trackLayoutSlice.actions;
