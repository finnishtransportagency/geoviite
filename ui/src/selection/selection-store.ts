import { PayloadAction } from '@reduxjs/toolkit';
import {
    ItemCollections,
    OnHighlightItemsOptions,
    OnSelectFlags,
    OnSelectOptions,
    OptionalUnselectableItemCollections,
    SelectedGeometryItem,
    Selection,
    UnselectableItemCollections,
} from 'selection/selection-model';
import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutSwitch,
    MapAlignment,
} from 'track-layout/track-layout-model';
import { LocationTrackBadgeStatus } from 'geoviite-design-lib/alignment/location-track-badge';
import { deduplicate, filterUniqueById } from 'utils/array-utils';
import { SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { ValueOf } from 'utils/type-utils';
import { GeometryPlanLayoutId } from 'geometry/geometry-model';
import { PublicationDetails } from 'publication/publication-model';

export function createEmptyItemCollections(): ItemCollections {
    return {
        segments: [],
        locationTracks: [],
        // referenceLines: [],
        kmPosts: [],
        geometryKmPosts: [],
        switches: [],
        geometrySwitches: [],
        trackNumbers: [],
        geometryAlignments: [],
        geometrySegments: [],
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
    planLayouts: [],
    openedPlanLayouts: [],
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

function getNewGeometryItemCollection<T extends { id: unknown }>(
    items: SelectedGeometryItem<T>[],
    newItems: SelectedGeometryItem<T>[] | undefined,
    flags: OnSelectFlags,
): SelectedGeometryItem<T>[] {
    return getNewItemCollectionUsingCustomId(
        items,
        newItems,
        flags,
        (item) => item.planId + item.geometryItem.id,
    );
}

function filterGeometryItemCollection<T extends { id: unknown }>(
    items: SelectedGeometryItem<T>[],
    unselectItemIds: ValueOf<UnselectableItemCollections> | undefined,
) {
    if (unselectItemIds === undefined) {
        return items;
    }

    return items.filter((i) => !unselectItemIds.some((u) => u === i.geometryItem.id));
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
    itemCollections['segments'] = getNewItemCollection(
        itemCollections['segments'],
        options['segments'],
        flags,
    );
    itemCollections['locationTracks'] = getNewIdCollection(
        itemCollections['locationTracks'],
        options['locationTracks'],
        flags,
    );
    itemCollections['kmPosts'] = getNewItemCollection(
        itemCollections['kmPosts'],
        options['kmPosts'],
        flags,
    );
    itemCollections['geometryKmPosts'] = getNewGeometryItemCollection(
        itemCollections['geometryKmPosts'],
        options['geometryKmPosts'],
        flags,
    );
    itemCollections['switches'] = getNewItemCollection(
        itemCollections['switches'],
        options['switches'],
        flags,
    );
    itemCollections['geometrySwitches'] = getNewGeometryItemCollection(
        itemCollections['geometrySwitches'],
        options['geometrySwitches'],
        flags,
    );
    itemCollections['trackNumbers'] = getNewIdCollection(
        itemCollections['trackNumbers'],
        options['trackNumbers'],
        flags,
    );
    itemCollections['geometryAlignments'] = getNewGeometryItemCollection(
        itemCollections['geometryAlignments'],
        options['geometryAlignments'],
        flags,
    );
    itemCollections['geometrySegments'] = getNewGeometryItemCollection(
        itemCollections['geometrySegments'],
        options['geometrySegments'],
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
    itemCollections['geometryPlans'] = getNewItemCollection(
        itemCollections['geometryPlans'],
        options['geometryPlans'],
        flags,
    );
}

function updateItemCollectionsByUnselecting(
    itemCollections: ItemCollections,
    unselectItemCollections: OptionalUnselectableItemCollections,
) {
    itemCollections['segments'] = filterItemCollection(
        itemCollections['segments'],
        unselectItemCollections['segments'],
    );
    itemCollections['locationTracks'] = filterIdCollection(
        itemCollections['locationTracks'],
        unselectItemCollections['locationTracks'],
    );
    itemCollections['kmPosts'] = filterItemCollection(
        itemCollections['kmPosts'],
        unselectItemCollections['kmPosts'],
    );
    itemCollections['geometryKmPosts'] = filterGeometryItemCollection(
        itemCollections['geometryKmPosts'],
        unselectItemCollections['geometryKmPosts'],
    );
    itemCollections['switches'] = filterItemCollection(
        itemCollections['switches'],
        unselectItemCollections['switches'],
    );
    itemCollections['geometrySwitches'] = filterGeometryItemCollection(
        itemCollections['geometrySwitches'],
        unselectItemCollections['geometrySwitches'],
    );
    itemCollections['trackNumbers'] = filterIdCollection(
        itemCollections['trackNumbers'],
        unselectItemCollections['trackNumbers'],
    );
    itemCollections['geometryAlignments'] = filterGeometryItemCollection(
        itemCollections['geometryAlignments'],
        unselectItemCollections['geometryAlignments'],
    );
    itemCollections['geometrySegments'] = filterGeometryItemCollection(
        itemCollections['geometrySegments'],
        unselectItemCollections['geometrySegments'],
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
    itemCollections['geometryPlans'] = filterItemCollection(
        itemCollections['geometryPlans'],
        unselectItemCollections['geometryPlans'],
    );
}

export type ToggleAlignmentPayload = {
    alignment: MapAlignment;
    status: LocationTrackBadgeStatus;
    planLayout: GeometryPlanLayout;
    keepAlignmentVisible?: boolean;
};

export type ToggleSwitchPayload = {
    switch: LayoutSwitch;
    status: SwitchBadgeStatus;
    planLayout: GeometryPlanLayout;
    keepSwitchesVisible?: boolean;
};

export type ToggleKmPostPayload = {
    kmPost: LayoutKmPost;
    status: KmPostBadgeStatus;
    planLayout: GeometryPlanLayout;
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
        { payload: publication }: PayloadAction<PublicationDetails | undefined>,
    ) => {
        state.publication = publication;
    },
    togglePlanVisibility: (
        state: Selection,
        { payload: planLayout }: PayloadAction<GeometryPlanLayout | null>,
    ): void => {
        const isPlanLayoutSelected = state.planLayouts.some((p) => p.planId == planLayout?.planId);

        if (isPlanLayoutSelected) {
            state.planLayouts = [
                ...state.planLayouts.filter((p) => p.planId !== planLayout?.planId),
            ];

            state.selectedItems.geometrySegments = [
                ...state.selectedItems.geometrySegments.filter(
                    (gs) =>
                        !planLayout?.alignments.some((a) =>
                            a.segments.some((s) => s.id === gs.geometryItem.id),
                        ),
                ),
            ];
            state.selectedItems.geometryKmPosts = [
                ...state.selectedItems.geometryKmPosts.filter(
                    (gKmPost) =>
                        !planLayout?.kmPosts.some(
                            (kmPost) => kmPost.id === gKmPost.geometryItem.id,
                        ),
                ),
            ];
            state.selectedItems.geometrySwitches = [
                ...state.selectedItems.geometrySwitches.filter(
                    (gs) => !planLayout?.switches.some((s) => s.id == gs.geometryItem.id),
                ),
            ];
            state.selectedItems.geometryAlignments = [
                ...state.selectedItems.geometryAlignments.filter(
                    (ga) => !planLayout?.alignments.some((a) => a.id === ga.geometryItem.id),
                ),
            ];
        } else {
            state.planLayouts = [...state.planLayouts, ...(planLayout ? [planLayout] : [])];
        }
    },
    toggleAlignmentVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleAlignmentPayload>,
    ): void => {
        const { planLayout, alignment } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const alignmentVisible = storePlanLayout.alignments.some((a) => a.id === alignment.id);

            if (alignmentVisible) {
                if (!payload.keepAlignmentVisible) {
                    state.planLayouts = [
                        ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                        {
                            ...storePlanLayout,
                            alignments: storePlanLayout.alignments.filter(
                                (a) => a.id !== alignment.id,
                            ),
                        },
                    ];
                }
            } else {
                state.planLayouts = [
                    ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                    {
                        ...storePlanLayout,
                        alignments: [...storePlanLayout.alignments, alignment],
                    },
                ];
            }
        } else {
            state.planLayouts = [
                ...state.planLayouts,
                {
                    ...planLayout,
                    alignments: [alignment],
                    switches: [],
                    kmPosts: [],
                },
            ];
        }
    },
    toggleSwitchVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleSwitchPayload>,
    ): void => {
        const { planLayout, switch: switchItem } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const switchVisible = storePlanLayout.switches.some((s) => s.id === switchItem.id);

            if (switchVisible) {
                if (!payload.keepSwitchesVisible) {
                    state.planLayouts = [
                        ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                        {
                            ...storePlanLayout,
                            switches: storePlanLayout.switches.filter(
                                (s) => s.id !== switchItem.id,
                            ),
                        },
                    ];
                }
            } else {
                state.planLayouts = [
                    ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                    {
                        ...storePlanLayout,
                        switches: [...storePlanLayout.switches, switchItem],
                    },
                ];
            }
        } else {
            state.planLayouts = [
                ...state.planLayouts,
                {
                    ...planLayout,
                    alignments: [],
                    switches: [switchItem],
                    kmPosts: [],
                },
            ];
        }
    },
    toggleKmPostsVisibility: (
        state: Selection,
        { payload }: PayloadAction<ToggleKmPostPayload>,
    ): void => {
        const { planLayout, kmPost: kmPost } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const kmPostVisible = storePlanLayout.kmPosts.some((k) => k.id === kmPost.id);

            if (kmPostVisible) {
                if (!payload.keepKmPostsVisible) {
                    state.planLayouts = [
                        ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                        {
                            ...storePlanLayout,
                            kmPosts: storePlanLayout.kmPosts.filter((k) => k.id !== kmPost.id),
                        },
                    ];
                }
            } else {
                state.planLayouts = [
                    ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                    {
                        ...storePlanLayout,
                        kmPosts: [...storePlanLayout.kmPosts, kmPost],
                    },
                ];
            }
        } else {
            state.planLayouts = [
                ...state.planLayouts,
                {
                    ...planLayout,
                    alignments: [],
                    switches: [],
                    kmPosts: [kmPost],
                },
            ];
        }
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
