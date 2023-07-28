import { PayloadAction } from '@reduxjs/toolkit';
import {
    GeometryItemId,
    ItemCollections,
    OnHighlightItemsOptions,
    OnSelectFlags,
    OnSelectOptions,
    OptionalUnselectableItemCollections,
    SelectedGeometryItemId,
    Selection,
    UnselectableItemCollections,
    VisiblePlanLayout,
} from 'selection/selection-model';
import { GeometryPlanLayout } from 'track-layout/track-layout-model';
import { deduplicate, filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { ValueOf } from 'utils/type-utils';
import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanId,
    GeometryPlanLayoutId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { PublicationId } from 'publication/publication-model';

export function createEmptyItemCollections(): ItemCollections {
    return {
        locationTracks: [],
        kmPosts: [],
        geometryKmPostIds: [],
        switches: [],
        geometrySwitchIds: [],
        trackNumbers: [],
        geometryAlignmentIds: [],
        layoutLinkPoints: [],
        geometryLinkPoints: [],
        clusterPoints: [],
        suggestedSwitches: [],
        locationTrackEndPoints: [],
        geometryPlans: [],
    };
}

export const initialSelectionState: Selection = {
    selectionModes: ['alignment', 'switch', 'segment', 'trackNumber'],
    selectedItems: createEmptyItemCollections(),
    highlightedItems: createEmptyItemCollections(),
    openedPlanLayouts: [],
    visiblePlans: [],
    publication: undefined,
};

function getNewIdCollection<TId extends string>(
    ids: TId[],
    newIds: TId[] | undefined,
    flags: OnSelectFlags,
): TId[] {
    // Default to not modifying selection if newItems isn't provided.
    // The isExactSelection flag being set is a special case. Empty selections should be respected then
    if (newIds == undefined) return ids;

    if (flags.isIncremental) {
        return deduplicate([...newIds, ...ids]);
    } else if (flags.isToggle) {
        return deduplicate(newIds.filter((newId) => !ids.includes(newId)));
    } else {
        return deduplicate(newIds);
    }
}

function getNewItemCollection<TEntity extends { id: unknown }>(
    items: TEntity[],
    newItems: TEntity[] | undefined,
    flags: OnSelectFlags,
): TEntity[] {
    return getNewItemCollectionUsingCustomId(items, newItems, flags, (item) => item.id);
}

function filterIdCollection<T extends string>(
    ids: T[],
    unselectItemIds: ValueOf<UnselectableItemCollections> | undefined,
) {
    if (unselectItemIds === undefined) {
        return ids;
    }

    return ids.filter((i) => !unselectItemIds.includes(i));
}

function filterItemCollection<T extends { id: unknown }>(
    items: T[],
    unselectItemIds: ValueOf<UnselectableItemCollections> | undefined,
) {
    if (unselectItemIds === undefined) {
        return items;
    }

    return items.filter((i) => !unselectItemIds.some((u) => u === i.id));
}

function getNewGeometryItemIdCollection<T extends GeometryItemId>(
    items: SelectedGeometryItemId<T>[],
    newItems: SelectedGeometryItemId<T>[] | undefined,
    flags: OnSelectFlags,
): SelectedGeometryItemId<T>[] {
    return getNewItemCollectionUsingCustomId(items, newItems, flags, (item) => item.geometryId);
}

function filterGeometryItemIdCollection<T extends GeometryItemId>(
    items: SelectedGeometryItemId<T>[],
    unselectItemIds: ValueOf<UnselectableItemCollections> | undefined,
) {
    if (unselectItemIds === undefined) {
        return items;
    }

    return items.filter((i) => !unselectItemIds.some((u) => u === i.geometryId));
}

function getNewItemCollectionUsingCustomId<TEntity, TId>(
    items: TEntity[],
    newItems: TEntity[] | undefined,
    flags: OnSelectFlags,
    getId: (item: TEntity) => TId,
): TEntity[] {
    if (newItems == undefined) {
        return items;
    }

    if (flags.isIncremental) {
        return [...newItems, ...items].filter(filterUniqueById(getId));
    } else if (flags.isToggle) {
        return newItems.filter((newItem) => !items.find((item) => getId(item) == getId(newItem)));
    } else {
        return newItems;
    }
}

function updateItemCollectionsByOptions(
    itemCollections: ItemCollections,
    options: OnSelectOptions,
) {
    // Repetitive code, but seems that there is no way do make typescript to accept this in a loop
    const flags = options as OnSelectFlags;
    itemCollections['locationTracks'] = getNewIdCollection(
        itemCollections['locationTracks'],
        options['locationTracks'],
        flags,
    );
    itemCollections['kmPosts'] = getNewIdCollection(
        itemCollections['kmPosts'],
        options['kmPosts'],
        flags,
    );
    itemCollections['geometryKmPostIds'] = getNewGeometryItemIdCollection(
        itemCollections['geometryKmPostIds'],
        options['geometryKmPostIds'],
        flags,
    );
    itemCollections['switches'] = getNewIdCollection(
        itemCollections['switches'],
        options['switches'],
        flags,
    );
    itemCollections['geometrySwitchIds'] = getNewGeometryItemIdCollection(
        itemCollections['geometrySwitchIds'],
        options['geometrySwitchIds'],
        flags,
    );
    itemCollections['trackNumbers'] = getNewIdCollection(
        itemCollections['trackNumbers'],
        options['trackNumbers'],
        flags,
    );
    itemCollections['geometryAlignmentIds'] = getNewGeometryItemIdCollection(
        itemCollections['geometryAlignmentIds'],
        options['geometryAlignmentIds'],
        flags,
    );
    itemCollections['layoutLinkPoints'] = getNewItemCollection(
        itemCollections['layoutLinkPoints'],
        options['layoutLinkPoints'],
        flags,
    );
    itemCollections['geometryLinkPoints'] = getNewItemCollection(
        itemCollections['geometryLinkPoints'],
        options['geometryLinkPoints'],
        flags,
    );
    itemCollections['clusterPoints'] = getNewItemCollection(
        itemCollections['clusterPoints'],
        options['clusterPoints'],
        flags,
    );
    itemCollections['suggestedSwitches'] = getNewItemCollection(
        itemCollections['suggestedSwitches'],
        options['suggestedSwitches'],
        flags,
    );
    itemCollections['locationTrackEndPoints'] = getNewItemCollection(
        itemCollections['locationTrackEndPoints'],
        options['locationTrackEndPoints'],
        flags,
    );
    itemCollections['geometryPlans'] = getNewIdCollection(
        itemCollections['geometryPlans'],
        options['geometryPlans'],
        flags,
    );
}

function updateItemCollectionsByUnselecting(
    itemCollections: ItemCollections,
    unselectItemCollections: OptionalUnselectableItemCollections,
) {
    itemCollections['locationTracks'] = filterIdCollection(
        itemCollections['locationTracks'],
        unselectItemCollections['locationTracks'],
    );
    itemCollections['kmPosts'] = filterIdCollection(
        itemCollections['kmPosts'],
        unselectItemCollections['kmPosts'],
    );
    itemCollections['geometryKmPostIds'] = filterGeometryItemIdCollection(
        itemCollections['geometryKmPostIds'],
        unselectItemCollections['geometryKmPosts'],
    );
    itemCollections['switches'] = filterIdCollection(
        itemCollections['switches'],
        unselectItemCollections['switches'],
    );
    itemCollections['geometrySwitchIds'] = filterGeometryItemIdCollection(
        itemCollections['geometrySwitchIds'],
        unselectItemCollections['geometrySwitches'],
    );
    itemCollections['trackNumbers'] = filterIdCollection(
        itemCollections['trackNumbers'],
        unselectItemCollections['trackNumbers'],
    );
    itemCollections['geometryAlignmentIds'] = filterGeometryItemIdCollection(
        itemCollections['geometryAlignmentIds'],
        unselectItemCollections['geometryAlignments'],
    );
    itemCollections['layoutLinkPoints'] = filterItemCollection(
        itemCollections['layoutLinkPoints'],
        unselectItemCollections['layoutLinkPoints'],
    );
    itemCollections['geometryLinkPoints'] = filterItemCollection(
        itemCollections['geometryLinkPoints'],
        unselectItemCollections['geometryLinkPoints'],
    );
    itemCollections['suggestedSwitches'] = filterItemCollection(
        itemCollections['suggestedSwitches'],
        unselectItemCollections['suggestedSwitches'],
    );
    itemCollections['locationTrackEndPoints'] = filterItemCollection(
        itemCollections['locationTrackEndPoints'],
        unselectItemCollections['locationTrackEndPoints'],
    );
    itemCollections['geometryPlans'] = filterIdCollection(
        itemCollections['geometryPlans'],
        unselectItemCollections['geometryPlans'],
    );
}

export type ToggleAlignmentPayload = {
    planId: GeometryPlanId;
    alignmentId: GeometryAlignmentId;
    keepAlignmentVisible?: boolean;
};

export type ToggleSwitchPayload = {
    planId: GeometryPlanId;
    switchId: GeometrySwitchId;
    keepSwitchesVisible?: boolean;
};

export type ToggleKmPostPayload = {
    planId: GeometryPlanId;
    kmPostId: GeometryKmPostId;
    keepKmPostsVisible?: boolean;
};

export type ToggleAccordionOpenPayload = {
    id: GeometryPlanLayoutId;
    isOpen: boolean;
};

export type TogglePlanWithSubItemsOpenPayload = {
    isKmPostsOpen: boolean;
    isAlignmentsOpen: boolean;
    isSwitchesOpen: boolean;
} & ToggleAccordionOpenPayload;

export const selectionReducers = {
    onSelect: function (
        state: Selection,
        { payload: options }: PayloadAction<OnSelectOptions>,
    ): void {
        updateItemCollectionsByOptions(state.selectedItems, options);
    },
    onHighlightItems: function (
        state: Selection,
        { payload: options }: PayloadAction<OnHighlightItemsOptions>,
    ): void {
        updateItemCollectionsByOptions(state.highlightedItems, options);
    },
    onUnselect: function (
        state: Selection,
        { payload }: PayloadAction<OptionalUnselectableItemCollections>,
    ) {
        updateItemCollectionsByUnselecting(state.selectedItems, payload);
    },
    onSelectedPublicationChanged: (
        state: Selection,
        { payload: publication }: PayloadAction<PublicationId | undefined>,
    ) => {
        state.publication = publication;
    },
    togglePlanVisibility: (
        state: Selection,
        { payload: plan }: PayloadAction<GeometryPlanLayout | null>,
    ): void => {
        const isPlanVisible = state.visiblePlans.some((p) => p.id === plan?.planId);

        if (isPlanVisible) {
            const selectedItems = state.selectedItems;

            state.visiblePlans = [...state.visiblePlans.filter((p) => p.id !== plan?.planId)];

            selectedItems.geometryKmPostIds = [
                ...selectedItems.geometryKmPostIds.filter(
                    ({ geometryId }) =>
                        !plan?.kmPosts.some((kmPost) => kmPost.sourceId === geometryId),
                ),
            ];
            selectedItems.geometrySwitchIds = [
                ...selectedItems.geometrySwitchIds.filter(
                    ({ geometryId }) => !plan?.switches.some((s) => s.sourceId === geometryId),
                ),
            ];
            selectedItems.geometryAlignmentIds = [
                ...selectedItems.geometryAlignmentIds.filter(
                    (ga) => !plan?.alignments.some((a) => a.header.id === ga.geometryId),
                ),
            ];
        } else {
            const newVisiblePlan = plan
                ? [
                      {
                          id: plan.planId,
                          switches: plan.switches.map((s) => s.sourceId).filter(filterNotEmpty),
                          kmPosts: plan.kmPosts.map((s) => s.sourceId).filter(filterNotEmpty),
                          alignments: plan.alignments.map((a) => a.header.id),
                      },
                  ]
                : [];
            state.visiblePlans = [...state.visiblePlans, ...newVisiblePlan];
        }
    },
    toggleAlignmentVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleAlignmentPayload>,
    ): void => {
        const { planId, alignmentId, keepAlignmentVisible: keepVisible } = payload;
        toggleVisibility(state, 'alignments', keepVisible ?? false, planId, alignmentId);

        // const visiblePlan = state.visiblePlans.find((p) => p.id === planId);
        // const alignmentVisible = visiblePlan?.alignments?.includes(alignmentId) ?? false;
        // if (visiblePlan && alignmentVisible) {
        //     if (!keepVisible) {
        //         visiblePlan.alignments = visiblePlan.alignments.filter((id) => id !== alignmentId);
        //         if (!arePlanPartsVisible(visiblePlan)) {
        //             state.visiblePlans = state.visiblePlans.filter((p) => p.id !== planId);
        //         }
        //     }
        // } else if (visiblePlan) {
        //     visiblePlan.alignments = [...visiblePlan.alignments, alignmentId];
        // } else {
        //     state.visiblePlans = [
        //         ...state.visiblePlans,
        //         {
        //             id: planId,
        //             switches: [],
        //             kmPosts: [],
        //             alignments: [alignmentId],
        //         },
        //     ];
        // }
    },
    toggleSwitchVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleSwitchPayload>,
    ): void => {
        const { planId, switchId, keepSwitchesVisible: keepVisible } = payload;
        toggleVisibility(state, 'switches', keepVisible ?? false, planId, switchId);
    },
    toggleKmPostsVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleKmPostPayload>,
    ): void => {
        const { planId, kmPostId, keepKmPostsVisible: keepVisible } = payload;
        toggleVisibility(state, 'kmPosts', keepVisible ?? false, planId, kmPostId);
    },
    togglePlanOpen: (
        state: Selection,
        { payload }: PayloadAction<TogglePlanWithSubItemsOpenPayload>,
    ): void => {
        if (payload.isOpen) {
            state.openedPlanLayouts = [...state.openedPlanLayouts, payload];
        } else {
            state.openedPlanLayouts = state.openedPlanLayouts.filter((p) => p.id !== payload.id);
        }
    },
    togglePlanKmPostsOpen: (
        state: Selection,
        { payload }: PayloadAction<ToggleAccordionOpenPayload>,
    ): void => {
        const { id, isOpen } = payload;
        state.openedPlanLayouts = state.openedPlanLayouts.map((p) => {
            if (p.id === id) return { ...p, isKmPostsOpen: isOpen };
            else return p;
        });
    },
    togglePlanAlignmentsOpen: (
        state: Selection,
        { payload }: PayloadAction<ToggleAccordionOpenPayload>,
    ): void => {
        const { id, isOpen } = payload;
        state.openedPlanLayouts = state.openedPlanLayouts.map((p) => {
            if (p.id === id) return { ...p, isAlignmentsOpen: isOpen };
            else return p;
        });
    },
    togglePlanSwitchesOpen: (
        state: Selection,
        { payload }: PayloadAction<ToggleAccordionOpenPayload>,
    ): void => {
        const { id, isOpen } = payload;
        state.openedPlanLayouts = state.openedPlanLayouts.map((p) => {
            if (p.id === id) return { ...p, isSwitchesOpen: isOpen };
            else return p;
        });
    },
};

function toggleVisibility(
    state: Selection,
    type: 'alignments' | 'switches' | 'kmPosts',
    keepVisible: boolean,
    planId: GeometryPlanId,
    itemId: GeometryAlignmentId | GeometrySwitchId | GeometryKmPostId,
) {
    const visiblePlan = state.visiblePlans.find((p) => p.id === planId);
    const itemVisible = visiblePlan?.[type]?.includes(itemId) ?? false;
    if (visiblePlan && itemVisible) {
        if (!keepVisible) {
            visiblePlan[type] = visiblePlan[type].filter((id) => id !== itemId);
            if (!arePlanPartsVisible(visiblePlan)) {
                state.visiblePlans = state.visiblePlans.filter((p) => p.id !== planId);
            }
        }
    } else if (visiblePlan) {
        visiblePlan[type] = [...visiblePlan[type], itemId];
    } else {
        state.visiblePlans = [
            ...state.visiblePlans,
            {
                ...{
                    id: planId,
                    switches: [],
                    kmPosts: [],
                    alignments: [],
                },
                [type]: [itemId],
            },
        ];
    }
}

function arePlanPartsVisible(plan: VisiblePlanLayout): boolean {
    return plan.kmPosts.length > 0 || plan.switches.length > 0 || plan.alignments.length > 0;
}
