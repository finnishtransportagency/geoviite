import { PayloadAction } from '@reduxjs/toolkit';
import {
    GeometryItemId,
    ItemCollections,
    OnHighlightItemsOptions,
    OnSelectFlags,
    OnSelectOptions,
    OptionalUnselectableItemCollections,
    SelectedGeometryItem,
    SelectedGeometryItemId,
    Selection,
    UnselectableItemCollections,
} from 'selection/selection-model';
import { GeometryPlanLayout, LayoutKmPost, LayoutSwitch } from 'track-layout/track-layout-model';
import { LocationTrackBadgeStatus } from 'geoviite-design-lib/alignment/location-track-badge';
import { deduplicate, filterUniqueById } from 'utils/array-utils';
import { SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { ValueOf } from 'utils/type-utils';
import { GeometryPlanLayoutId } from 'geometry/geometry-model';
import { PublicationId } from 'publication/publication-model';
import { AlignmentHeader } from 'track-layout/layout-map-api';

export function createEmptyItemCollections(): ItemCollections {
    return {
        locationTracks: [],
        kmPosts: [],
        geometryKmPostIds: [],
        switches: [],
        geometrySwitchIds: [],
        trackNumbers: [],
        geometryAlignments: [],
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

function getNewGeometryItemIdCollection<T extends GeometryItemId>(
    items: SelectedGeometryItemId<T>[],
    newItems: SelectedGeometryItemId<T>[] | undefined,
    flags: OnSelectFlags,
): SelectedGeometryItemId<T>[] {
    return getNewItemCollectionUsingCustomId(
        items,
        newItems,
        flags,
        // TODO: GVT-826 Why this custom ID? geometryId is just as unique as planId+geometryId
        (item) => item.planId + item.geometryId,
    );
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
    itemCollections['geometryAlignments'] = getNewGeometryItemCollection(
        itemCollections['geometryAlignments'],
        options['geometryAlignments'],
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
    itemCollections['geometryAlignments'] = filterGeometryItemCollection(
        itemCollections['geometryAlignments'],
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
    alignment: AlignmentHeader;
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
        { payload: publication }: PayloadAction<PublicationId | undefined>,
    ) => {
        state.publication = publication;
    },
    togglePlanVisibility: (
        state: Selection,
        { payload: planLayout }: PayloadAction<GeometryPlanLayout | null>,
    ): void => {
        const isPlanLayoutSelected = state.planLayouts.some((p) => p.planId === planLayout?.planId);

        if (isPlanLayoutSelected) {
            const selectedItems = state.selectedItems;

            state.planLayouts = [
                ...state.planLayouts.filter((p) => p.planId !== planLayout?.planId),
            ];

            selectedItems.geometryKmPostIds = [
                ...selectedItems.geometryKmPostIds.filter(
                    ({ geometryId }) =>
                        !planLayout?.kmPosts.some((kmPost) => kmPost.sourceId === geometryId),
                ),
            ];
            selectedItems.geometrySwitchIds = [
                ...selectedItems.geometrySwitchIds.filter(
                    ({ geometryId }) =>
                        !planLayout?.switches.some((s) => s.sourceId === geometryId),
                ),
            ];
            selectedItems.geometryAlignments = [
                ...selectedItems.geometryAlignments.filter(
                    (ga) => !planLayout?.alignments.some((a) => a.header.id === ga.geometryItem.id),
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
        const { planLayout, alignment, keepAlignmentVisible: keepVisible } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const alignmentVisible = storePlanLayout.alignments.some(
                (a) => a.header.id === alignment.id,
            );

            if (alignmentVisible) {
                if (!keepVisible) {
                    state.planLayouts = [
                        ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                        {
                            ...storePlanLayout,
                            alignments: storePlanLayout.alignments.filter(
                                (a) => a.header.id !== alignment.id,
                            ),
                        },
                    ];
                }
            } else {
                state.planLayouts = [
                    ...state.planLayouts.filter((p) => p.planId !== planLayout.planId),
                    {
                        ...storePlanLayout,
                        alignments: [
                            ...storePlanLayout.alignments,
                            {
                                header: alignment,
                                polyLine: null,
                                segmentMValues: [],
                            },
                        ],
                    },
                ];
            }
        } else {
            state.planLayouts = [
                ...state.planLayouts,
                {
                    ...planLayout,
                    alignments: [
                        {
                            header: alignment,
                            polyLine: null,
                            segmentMValues: [],
                        },
                    ],
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
        const { planLayout, switch: switchItem, keepSwitchesVisible: keepVisible } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const switchVisible = storePlanLayout.switches.some((s) => s.id === switchItem.id);

            if (switchVisible) {
                if (!keepVisible) {
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
        const { planLayout, kmPost, keepKmPostsVisible: keepVisible } = payload;

        const storePlanLayout = state.planLayouts.find((p) => p.planId === planLayout.planId);
        if (storePlanLayout) {
            const kmPostVisible = storePlanLayout.kmPosts.some((k) => k.id === kmPost.id);

            if (kmPostVisible) {
                if (!keepVisible) {
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
