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
import { LayoutMode, PublishType, TimeStamp } from 'common/common-model';
import { toDate } from 'utils/date-utils';
import {
    GeometryPlanLayout,
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';

export type SelectedPublishChanges = {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
};

export type SelectedPublishChange = {
    trackNumber: LayoutTrackNumberId | undefined;
    referenceLine: ReferenceLineId | undefined;
    locationTrack: LocationTrackId | undefined;
    switch: LayoutSwitchId | undefined;
    kmPost: LayoutKmPostId | undefined;
};

export type ChangeTimes = {
    layoutTrackNumber: TimeStamp;
    layoutLocationTrack: TimeStamp;
    layoutReferenceLine: TimeStamp;
    layoutSwitch: TimeStamp;
    layoutKmPost: TimeStamp;
    geometryPlan: TimeStamp;
};

export const initialSelectedPublishCandidateIdsState: SelectedPublishChanges = {
    trackNumbers: [],
    referenceLines: [],
    locationTracks: [],
    switches: [],
    kmPosts: [],
};

export const initialChangeTime: TimeStamp = '1970-01-01T00:00:00.000Z';
export const initialChangeTimes: ChangeTimes = {
    layoutTrackNumber: initialChangeTime,
    layoutLocationTrack: initialChangeTime,
    layoutReferenceLine: initialChangeTime,
    layoutSwitch: initialChangeTime,
    layoutKmPost: initialChangeTime,
    geometryPlan: initialChangeTime,
};

export type TrackLayoutState = {
    publishType: PublishType;
    layoutMode: LayoutMode;
    map: Map;
    selection: Selection;
    selectedPublishCandidateIds: SelectedPublishChanges;
    linkingState?: LinkingState;
    changeTimes: ChangeTimes;
    linkingIssuesSelectedBeforeLinking: boolean;
    switchLinkingSelectedBeforeLinking: boolean;
    selectedToolPanelTabId: string | undefined;
};

export const initialTrackLayoutState: TrackLayoutState = {
    publishType: 'OFFICIAL',
    layoutMode: 'DEFAULT',
    map: initialMapState,
    selection: initialSelectionState,
    selectedPublishCandidateIds: initialSelectedPublishCandidateIdsState,
    changeTimes: initialChangeTimes,
    linkingIssuesSelectedBeforeLinking: false,
    switchLinkingSelectedBeforeLinking: false,
    selectedToolPanelTabId: undefined,
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

function removeFromPublishCandidates<T>(changeIds: T[], id: T) {
    return changeIds.filter((changeId) => changeId != id);
}

const trackLayoutSlice = createSlice({
    name: 'trackLayout',
    initialState: initialTrackLayoutState,
    reducers: {
        ...wrapReducers((state: TrackLayoutState) => state.map, mapReducers),
        ...wrapReducers((state: TrackLayoutState) => state.selection, selectionReducers),
        ...wrapReducers((state: TrackLayoutState) => state, linkingReducers),

        onClickLocation: (state: TrackLayoutState, action: PayloadAction<Point>): void => {
            if (state.linkingState?.type == LinkingType.PlacingSwitch) {
                state.linkingState.location = action.payload;
            } else {
                mapReducers.onClickLocation(state.map, action);
            }
        },

        // Intercept select/highlight reducers to modify options
        onSelect: function(state: TrackLayoutState, action: PayloadAction<OnSelectOptions>): void {
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
                        payload: selectedSwitch?.id,
                    });
                    break;
                }
            }
        },
        onPreviewSelect: function(
            state: TrackLayoutState,
            action: PayloadAction<SelectedPublishChange>,
        ): void {
            const trackNumbers = action.payload.trackNumber
                ? [...state.selectedPublishCandidateIds.trackNumbers, action.payload.trackNumber]
                : [...state.selectedPublishCandidateIds.trackNumbers];
            const referenceLines = action.payload.referenceLine
                ? [
                    ...state.selectedPublishCandidateIds.referenceLines,
                    action.payload.referenceLine,
                ]
                : [...state.selectedPublishCandidateIds.referenceLines];
            const locationTracks = action.payload.locationTrack
                ? [
                    ...state.selectedPublishCandidateIds.locationTracks,
                    action.payload.locationTrack,
                ]
                : [...state.selectedPublishCandidateIds.locationTracks];
            const switches = action.payload.switch
                ? [...state.selectedPublishCandidateIds.switches, action.payload.switch]
                : [...state.selectedPublishCandidateIds.switches];
            const kmPosts = action.payload.kmPost
                ? [...state.selectedPublishCandidateIds.kmPosts, action.payload.kmPost]
                : [...state.selectedPublishCandidateIds.kmPosts];

            state.selectedPublishCandidateIds = {
                trackNumbers: trackNumbers,
                referenceLines: referenceLines,
                locationTracks: locationTracks,
                switches: switches,
                kmPosts: kmPosts,
            };
        },
        onPublishPreviewRemove: function(
            state: TrackLayoutState,
            action: PayloadAction<SelectedPublishChange>,
        ): void {
            const trackNumbers = action.payload.trackNumber
                ? removeFromPublishCandidates(
                    [...state.selectedPublishCandidateIds.trackNumbers],
                    action.payload.trackNumber,
                )
                : [...state.selectedPublishCandidateIds.trackNumbers];
            const referenceLines = action.payload.referenceLine
                ? removeFromPublishCandidates(
                    [...state.selectedPublishCandidateIds.referenceLines],
                    action.payload.referenceLine,
                )
                : [...state.selectedPublishCandidateIds.referenceLines];
            const locationTracks = action.payload.locationTrack
                ? removeFromPublishCandidates(
                    [...state.selectedPublishCandidateIds.locationTracks],
                    action.payload.locationTrack,
                )
                : [...state.selectedPublishCandidateIds.locationTracks];
            const switches = action.payload.switch
                ? removeFromPublishCandidates(
                    [...state.selectedPublishCandidateIds.switches],
                    action.payload.switch,
                )
                : [...state.selectedPublishCandidateIds.switches];
            const kmPosts = action.payload.kmPost
                ? removeFromPublishCandidates(
                    [...state.selectedPublishCandidateIds.kmPosts],
                    action.payload.kmPost,
                )
                : [...state.selectedPublishCandidateIds.kmPosts];

            state.selectedPublishCandidateIds = {
                trackNumbers: trackNumbers,
                referenceLines: referenceLines,
                locationTracks: locationTracks,
                switches: switches,
                kmPosts: kmPosts,
            };
        },
        // TODO when Hylkää muutokset -button is removed from Preview-view, this reducer will become obsolete
        onPublishPreviewRevert: function(state: TrackLayoutState): void {
            state.selectedPublishCandidateIds = {
                trackNumbers: [],
                referenceLines: [],
                locationTracks: [],
                switches: [],
                kmPosts: [],
            };
        },
        onHighlightItems: function(
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
        setChangeTimes: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<ChangeTimes>,
        ) {
            if (toDate(changeTimes.layoutTrackNumber) < toDate(payload.layoutTrackNumber)) {
                changeTimes.layoutTrackNumber = payload.layoutTrackNumber;
            }
            if (toDate(changeTimes.layoutLocationTrack) < toDate(payload.layoutLocationTrack)) {
                changeTimes.layoutLocationTrack = payload.layoutLocationTrack;
            }
            if (toDate(changeTimes.layoutReferenceLine) < toDate(payload.layoutReferenceLine)) {
                changeTimes.layoutReferenceLine = payload.layoutReferenceLine;
            }
            if (toDate(changeTimes.layoutSwitch) < toDate(payload.layoutSwitch)) {
                changeTimes.layoutSwitch = payload.layoutSwitch;
            }
            if (toDate(changeTimes.layoutKmPost) < toDate(payload.layoutKmPost)) {
                changeTimes.layoutKmPost = payload.layoutKmPost;
            }
            if (toDate(changeTimes.geometryPlan) < toDate(payload.geometryPlan)) {
                changeTimes.geometryPlan = payload.geometryPlan;
            }
        },
        setLayoutTrackNumberChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutTrackNumber) < toDate(payload))
                changeTimes.layoutTrackNumber = payload;
        },
        setLayoutLocationTrackChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutLocationTrack) < toDate(payload))
                changeTimes.layoutLocationTrack = payload;
        },
        setLayoutReferenceLineChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutReferenceLine) < toDate(payload))
                changeTimes.layoutReferenceLine = payload;
        },
        setLayoutSwitchChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutSwitch) < toDate(payload))
                changeTimes.layoutSwitch = payload;
        },
        setLayoutKmPostChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.layoutKmPost) < toDate(payload))
                changeTimes.layoutKmPost = payload;
        },
        setGeometryPlanChangeTime: function(
            { changeTimes }: TrackLayoutState,
            { payload }: PayloadAction<TimeStamp>,
        ) {
            if (toDate(changeTimes.geometryPlan) < toDate(payload))
                changeTimes.geometryPlan = payload;
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
        setToolPanelTab: (
            state: TrackLayoutState,
            { payload }: PayloadAction<string | undefined>,
        ): void => {
            state.selectedToolPanelTabId = payload;
        },
    },
});

export const trackLayoutReducer = trackLayoutSlice.reducer;
export const actionCreators = trackLayoutSlice.actions;
