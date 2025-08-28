import { ActionReducerMapBuilder, createSlice, PayloadAction } from '@reduxjs/toolkit';
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
import { inferLayoutContextMode, linkingReducers } from 'linking/linking-store';
import { LinkingState, LinkingType } from 'linking/linking-model';
import {
    draftDesignLayoutContext,
    draftMainLayoutContext,
    LayoutBranch,
    LayoutContext,
    LayoutContextMode,
    LayoutDesignId,
    LayoutMode,
    officialMainLayoutContext,
    PublicationState,
} from 'common/common-model';
import {
    GeometryPlanLayout,
    LocationTrackId,
    SwitchSplitPoint,
} from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { first } from 'utils/array-utils';
import { ToolPanelAsset, ToolPanelAssetType } from 'tool-panel/tool-panel';
import { exhaustiveMatchingGuard, ifDefined } from 'utils/type-utils';
import { splitReducers, SplittingState } from 'tool-panel/location-track/split-store';
import { PURGE } from 'redux-persist';
import { previewReducers, PreviewState } from 'preview/preview-store';
import { PlanSource } from 'geometry/geometry-model';
import { brand } from 'common/brand';
import {
    initialPlanDownloadState,
    initialPlanDownloadStateFromSelection,
    PlanDownloadAssetId,
    planDownloadReducers,
    PlanDownloadState,
} from 'map/plan-download/plan-download-store';

export const SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID = 'SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID';

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
    SwitchLinkingInfoboxVisibilities;

export type SwitchLinkingInfoboxVisibilities = {
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

export enum LocationTrackTaskListType {
    RELINKING_SWITCH_VALIDATION,
}

export type SwitchRelinkingValidationTaskList = {
    type: LocationTrackTaskListType.RELINKING_SWITCH_VALIDATION;
    locationTrackId: LocationTrackId;
    branch: LayoutBranch;
};

export enum GeometryPlanGrouping {
    None,
    ByProject,
}

export type GeometryPlanViewSettings = {
    grouping: GeometryPlanGrouping;
    visibleSources: PlanSource[];
};

export type TrackLayoutState = {
    layoutContext: LayoutContext;
    layoutContextMode: LayoutContextMode;
    designId: LayoutDesignId | undefined;
    layoutMode: LayoutMode;
    map: Map;
    selection: Selection;
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    linkingIssuesSelectedBeforeLinking: boolean;
    switchLinkingSelectedBeforeLinking: boolean;
    selectedToolPanelTab: ToolPanelAsset | undefined;
    infoboxVisibilities: InfoboxVisibilities;
    locationTrackTaskList?: SwitchRelinkingValidationTaskList;
    previewState: PreviewState;
    geometryPlanViewSettings: GeometryPlanViewSettings;
    planDownloadState?: PlanDownloadState;
};

export const initialTrackLayoutState: TrackLayoutState = {
    layoutContext: officialMainLayoutContext(),
    layoutContextMode: 'MAIN_OFFICIAL',
    designId: undefined,
    layoutMode: 'DEFAULT',
    map: initialMapState,
    selection: initialSelectionState,
    linkingIssuesSelectedBeforeLinking: false,
    switchLinkingSelectedBeforeLinking: false,
    selectedToolPanelTab: undefined,
    infoboxVisibilities: initialInfoboxVisibilities,
    locationTrackTaskList: undefined,
    previewState: {
        showOnlyOwnUnstagedChanges: false,
        stagedPublicationCandidateReferences: [],
    },
    geometryPlanViewSettings: {
        grouping: GeometryPlanGrouping.ByProject,
        visibleSources: ['GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'],
    },
    planDownloadState: undefined,
};

export function getSelectableItemTypes(
    splittingState?: SplittingState | undefined,
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
        case LinkingType.PlacingLayoutSwitch:
        case LinkingType.LinkingLayoutSwitch:
            return [];
        case LinkingType.LinkingGeometrySwitch:
            return ['switches'];
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
    extraReducers: (builder: ActionReducerMapBuilder<TrackLayoutState>) => {
        builder.addCase(PURGE, (_state, _action) => {
            return initialTrackLayoutState;
        });
    },
    reducers: {
        ...wrapReducers((state: TrackLayoutState) => state.map, mapReducers),
        ...wrapReducers((state: TrackLayoutState) => state.selection, selectionReducers),
        ...wrapReducers((state: TrackLayoutState) => state, linkingReducers),
        ...wrapReducers((state: TrackLayoutState) => state, splitReducers),
        ...wrapReducers((state: TrackLayoutState) => state.previewState, previewReducers),
        ...wrapReducers((state: TrackLayoutState) => state.planDownloadState, planDownloadReducers),

        onInfoboxVisibilityChange: (
            state: TrackLayoutState,
            action: PayloadAction<InfoboxVisibilities>,
        ): void => {
            state.infoboxVisibilities = action.payload;
        },

        onClickLocation: (state: TrackLayoutState, action: PayloadAction<Point>): void => {
            if (state.linkingState?.type === LinkingType.PlacingLayoutSwitch) {
                state.linkingState.location = action.payload;
            } else {
                mapReducers.onClickLocation(state.map, action);
            }
        },

        // Intercept select/highlight reducers to modify options
        onSelect: function (state: TrackLayoutState, action: PayloadAction<OnSelectOptions>): void {
            const firstSwitchId = ifDefined(action.payload.switches, first);
            if (state.splittingState && firstSwitchId) {
                const allowedSwitch = state.splittingState.trackSwitches.find(
                    (sw) => sw.switchId === firstSwitchId,
                );

                if (allowedSwitch) {
                    const switchSplitPoint = SwitchSplitPoint(
                        allowedSwitch.switchId,
                        allowedSwitch.name,
                        {
                            x: allowedSwitch.location?.x || 0,
                            y: allowedSwitch.location?.y || 0,
                            m: 0,
                        },
                        allowedSwitch.address,
                    );
                    if (state.splittingState.state === 'SETUP') {
                        splitReducers.addSplit(state, {
                            ...action,
                            payload: switchSplitPoint,
                        });
                    }
                }
                return;
            }

            // Handle selection
            const options = filterItemSelectOptions(state, action.payload);
            selectionReducers.onSelect(state.selection, {
                ...action,
                payload: options,
            });
            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );

            const onlyLayoutLinkPoint =
                options.layoutLinkPoints?.length === 1 &&
                ifDefined(options.layoutLinkPoints, first);
            const onlyGeometryLinkPoint =
                options.geometryLinkPoints?.length === 1 &&
                ifDefined(options.geometryLinkPoints, first);

            // Set linking information
            switch (state.linkingState?.type) {
                case LinkingType.LinkingGeometryWithAlignment:
                case LinkingType.LinkingGeometryWithEmptyAlignment:
                    if (onlyLayoutLinkPoint) {
                        linkingReducers.setLayoutLinkPoint(state, {
                            type: '',
                            payload: onlyLayoutLinkPoint,
                        });
                    }
                    if (onlyGeometryLinkPoint) {
                        linkingReducers.setGeometryLinkPoint(state, {
                            type: '',
                            payload: onlyGeometryLinkPoint,
                        });
                    }
                    break;

                case LinkingType.LinkingAlignment:
                    if (onlyLayoutLinkPoint) {
                        linkingReducers.setLayoutLinkPoint(state, {
                            type: '',
                            payload: onlyLayoutLinkPoint,
                        });
                    }
                    break;
                case LinkingType.LinkingGeometrySwitch: {
                    const layoutSwitchId = first(state.selection.selectedItems.switches);
                    if (layoutSwitchId) {
                        linkingReducers.selectOnlyLayoutSwitchForGeometrySwitchLinking(state, {
                            type: '',
                            payload: { layoutSwitchId },
                        });
                    }
                    break;
                }
            }
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
                state.selectedToolPanelTab = updateSelectedToolPanelTab(
                    state.selection,
                    state.linkingState,
                    state.selectedToolPanelTab,
                );
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
            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        toggleSwitchVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<ToggleSwitchPayload>,
        ) => {
            const { switchId, keepSwitchesVisible } = action.payload;

            const hideLayer = shouldHideMapLayer(state, 'switches', switchId, keepSwitchesVisible);

            updateMapLayerVisibilities(state.map, hideLayer, ['geometry-switch-layer']);

            selectionReducers.toggleSwitchVisibility(state.selection, action);
            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        toggleKmPostsVisibility: (
            state: TrackLayoutState,
            action: PayloadAction<ToggleKmPostPayload>,
        ) => {
            const { kmPostId, keepKmPostsVisible } = action.payload;

            const hideLayer = shouldHideMapLayer(state, 'kmPosts', kmPostId, keepKmPostsVisible);

            updateMapLayerVisibilities(state.map, hideLayer, ['geometry-km-post-layer']);

            selectionReducers.toggleKmPostsVisibility(state.selection, action);
            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        onPublicationStateChange: (
            state: TrackLayoutState,
            { payload: publicationState }: PayloadAction<PublicationState>,
        ): void => {
            const newLayoutContext = {
                publicationState: publicationState,
                branch: state.layoutContext.branch,
            };
            state.layoutContext = newLayoutContext;
            state.layoutContextMode = inferLayoutContextMode(newLayoutContext);

            if (publicationState === 'OFFICIAL') linkingReducers.stopLinking(state);

            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        onLayoutContextModeChange: function (
            state: TrackLayoutState,
            { payload: layoutContextMode }: PayloadAction<LayoutContextMode>,
        ) {
            state.layoutContextMode = layoutContextMode;
            state.layoutContext = getLayoutContext(state.layoutContextMode, state.designId);

            if (state.layoutContext.publicationState === 'OFFICIAL')
                linkingReducers.stopLinking(state);

            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        onDesignIdChange: function (
            state: TrackLayoutState,
            { payload: designId }: PayloadAction<LayoutDesignId>,
        ) {
            state.designId = designId;
            state.layoutContext = getLayoutContext(state.layoutContextMode, state.designId);
            state.layoutContextMode =
                designId !== undefined
                    ? 'DESIGN'
                    : state.layoutContext.publicationState === 'OFFICIAL'
                      ? 'MAIN_OFFICIAL'
                      : 'MAIN_DRAFT';

            state.selectedToolPanelTab = updateSelectedToolPanelTab(
                state.selection,
                state.linkingState,
                state.selectedToolPanelTab,
            );
        },
        onLayoutModeChange: (
            state: TrackLayoutState,
            { payload: layoutMode }: PayloadAction<LayoutMode>,
        ): void => {
            state.layoutMode = layoutMode;
        },
        onPublish: (state: TrackLayoutState): void => {
            state.layoutMode = 'DEFAULT';
        },
        setToolPanelTab: (
            state: TrackLayoutState,
            { payload }: PayloadAction<ToolPanelAsset | undefined>,
        ): void => {
            state.selectedToolPanelTab = payload;
        },
        showLocationTrackTaskList: (
            state: TrackLayoutState,
            { payload }: PayloadAction<TrackLayoutState['locationTrackTaskList']>,
        ) => {
            state.locationTrackTaskList = payload;
        },
        hideLocationTrackTaskList: (state: TrackLayoutState) => {
            state.locationTrackTaskList = undefined;
        },
        updateGeometryPlanGrouping: (
            state: TrackLayoutState,
            { payload: grouping }: PayloadAction<GeometryPlanGrouping>,
        ) => {
            state.geometryPlanViewSettings.grouping = grouping;
        },
        updateGeometryPlanVisibleSources: (
            state: TrackLayoutState,
            { payload: sources }: PayloadAction<PlanSource[]>,
        ) => {
            state.geometryPlanViewSettings.visibleSources = sources;
        },
        onOpenPlanDownloadPopup: (
            state: TrackLayoutState,
            { payload: asset }: PayloadAction<PlanDownloadAssetId | undefined>,
        ): void => {
            if (!asset) {
                state.planDownloadState = initialPlanDownloadState;
            } else {
                state.planDownloadState = initialPlanDownloadStateFromSelection(asset);
            }
        },
        onClosePlanDownloadPopup: (state: TrackLayoutState): void => {
            state.planDownloadState = undefined;
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

function getLayoutContext(
    layoutContextMode: LayoutContextMode,
    designId: LayoutDesignId | undefined,
): LayoutContext {
    if (layoutContextMode === 'DESIGN' && designId) {
        return draftDesignLayoutContext(designId);
    } else if (layoutContextMode === 'MAIN_DRAFT') {
        return draftMainLayoutContext();
    } else {
        return officialMainLayoutContext();
    }
}

export const toolPanelAssetExists = (
    selection: Selection,
    linkingState: LinkingState | undefined,
    asset: ToolPanelAsset,
): boolean => {
    switch (asset.type) {
        case 'GEOMETRY_PLAN':
            return selection.selectedItems.geometryPlans.includes(asset.id);
        case 'TRACK_NUMBER':
            return selection.selectedItems.trackNumbers.includes(brand(asset.id));
        case 'KM_POST':
            return selection.selectedItems.kmPosts.includes(brand(asset.id));
        case 'GEOMETRY_KM_POST':
            return selection.selectedItems.geometryKmPostIds.some((g) => g.geometryId === asset.id);
        case 'SWITCH':
            return selection.selectedItems.switches.includes(brand(asset.id));
        case 'SUGGESTED_SWITCH':
            return linkingState?.type === LinkingType.LinkingGeometrySwitch;
        case 'GEOMETRY_SWITCH':
            return selection.selectedItems.geometrySwitchIds.some(
                (sw) => sw.geometryId === asset.id,
            );
        case 'LOCATION_TRACK':
            return selection.selectedItems.locationTracks.includes(brand(asset.id));
        case 'GEOMETRY_ALIGNMENT':
            return selection.selectedItems.geometryAlignmentIds.some(
                (g) => g.geometryId === asset.id,
            );
        default:
            return false;
    }
};

export const TOOL_PANEL_ASSET_ORDER: ToolPanelAssetType[] = [
    'GEOMETRY_KM_POST',
    'KM_POST',
    'SUGGESTED_SWITCH',
    'GEOMETRY_SWITCH',
    'SWITCH',
    'GEOMETRY_ALIGNMENT',
    'LOCATION_TRACK',
    'TRACK_NUMBER',
    'GEOMETRY_PLAN',
];

export const getFirstOfTypeInSelection = (
    selection: Selection,
    linkingState: LinkingState | undefined,
    type: ToolPanelAssetType,
): ToolPanelAsset | undefined => {
    const { selectedItems } = selection;

    const switchLinkingActive =
        linkingState?.type === LinkingType.LinkingLayoutSwitch ||
        linkingState?.type === LinkingType.LinkingGeometrySwitch;

    const assetGetters: Record<ToolPanelAssetType, () => string | undefined> = {
        GEOMETRY_PLAN: () => first(selectedItems.geometryPlans),
        TRACK_NUMBER: () => first(selectedItems.trackNumbers),
        KM_POST: () => first(selectedItems.kmPosts),
        GEOMETRY_KM_POST: () => first(selectedItems.geometryKmPostIds)?.geometryId,
        SWITCH: () => first(selectedItems.switches),
        SUGGESTED_SWITCH: () =>
            switchLinkingActive ? SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID : undefined,
        GEOMETRY_SWITCH: () => first(selectedItems.switches),
        LOCATION_TRACK: () => first(selectedItems.locationTracks),
        GEOMETRY_ALIGNMENT: () => first(selectedItems.geometryAlignmentIds)?.geometryId,
    };
    const id = assetGetters[type]();
    return id ? { id, type } : undefined;
};

export const getFirstToolPanelAsset = (
    selection: Selection,
    linkingState: LinkingState | undefined,
): ToolPanelAsset | undefined => {
    const firstAssetType = TOOL_PANEL_ASSET_ORDER.find(
        (type) => getFirstOfTypeInSelection(selection, linkingState, type) !== undefined,
    );

    return firstAssetType
        ? getFirstOfTypeInSelection(selection, linkingState, firstAssetType)
        : undefined;
};

const updateSelectedToolPanelTab = (
    selection: Selection,
    linkingState: LinkingState | undefined,
    currentlySelectedTab: ToolPanelAsset | undefined,
): ToolPanelAsset | undefined => {
    if (
        !currentlySelectedTab ||
        !toolPanelAssetExists(selection, linkingState, currentlySelectedTab)
    ) {
        return getFirstToolPanelAsset(selection, linkingState);
    }

    return currentlySelectedTab;
};
