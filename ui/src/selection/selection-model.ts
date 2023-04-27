import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutSegmentId,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { GeometryPlanId, GeometryPlanLayoutId } from 'geometry/geometry-model';
import {
    ClusterPoint,
    LinkPoint,
    LinkPointId,
    LocationTrackEndpoint,
    SuggestedSwitch,
    SuggestedSwitchId,
} from 'linking/linking-model';
import { ensureAllKeys } from 'utils/type-utils';
import { Point } from 'model/geometry';
import { PublicationId } from 'publication/publication-model';
import { AlignmentHeader } from 'track-layout/layout-map-api';

export type SelectionMode = 'alignment' | 'segment' | 'point' | 'switch' | 'trackNumber';

export type ItemCollections = {
    locationTracks: LocationTrackId[];
    kmPosts: LayoutKmPostId[];
    geometryKmPosts: SelectedGeometryItem<LayoutKmPost>[];
    switches: LayoutSwitchId[];
    geometrySwitches: SelectedGeometryItem<LayoutSwitch>[];
    trackNumbers: LayoutTrackNumberId[];
    geometryAlignments: SelectedGeometryItem<AlignmentHeader>[];
    layoutLinkPoints: LinkPoint[];
    geometryLinkPoints: LinkPoint[];
    clusterPoints: ClusterPoint[];
    suggestedSwitches: SuggestedSwitch[];
    locationTrackEndPoints: LocationTrackEndpoint[];
    geometryPlans: GeometryPlanId[];
};

export type UnselectableItemCollections = {
    segments: LayoutSegmentId[];
    locationTracks: LocationTrackId[];
    referenceLines: ReferenceLineId[];
    kmPosts: LayoutKmPostId[];
    geometryKmPosts: LayoutKmPostId[];
    switches: LayoutSwitchId[];
    geometrySwitches: LayoutSwitchId[];
    trackNumbers: LayoutTrackNumberId[];
    geometryAlignments: LocationTrackId[];
    geometrySegments: LayoutSegmentId[];
    layoutLinkPoints: LinkPointId[];
    geometryLinkPoints: LinkPointId[];
    suggestedSwitches: SuggestedSwitchId[];
    locationTrackEndPoints: string[];
    geometryPlans: GeometryPlanId[];
};

export type OptionalUnselectableItemCollections = Partial<UnselectableItemCollections>;

export type SelectedGeometryItem<T> = {
    planId: GeometryPlanId;
    geometryItem: T;
};

export type SelectableItemType = keyof ItemCollections;

export const allSelectableItemTypes: SelectableItemType[] = ensureAllKeys<SelectableItemType>()([
    'locationTracks',
    'kmPosts',
    'geometryKmPosts',
    'switches',
    'geometrySwitches',
    'trackNumbers',
    'geometryAlignments',
    'layoutLinkPoints',
    'geometryLinkPoints',
    'clusterPoints',
    'suggestedSwitches',
    'locationTrackEndPoints',
    'geometryPlans',
]);

export type OptionalItemCollections = Partial<ItemCollections>;

export type Selection = {
    selectionModes: SelectionMode[];
    selectedItems: ItemCollections;
    highlightedItems: ItemCollections;
    /**
     * GeometryPlanLayout can be used to provide a plan object manually,
     * e.g. when plan is not yet in DB and therefore there is no ID.
     */
    planLayouts: GeometryPlanLayout[];
    openedPlanLayouts: OpenedPlanLayout[];
    publication: PublicationId | undefined;
};

export type OpenedPlanLayout = {
    id: GeometryPlanLayoutId;
    isSwitchesOpen: boolean;
    isKmPostsOpen: boolean;
    isAlignmentsOpen: boolean;
};

export type OnSelectFlags = {
    isIncremental?: boolean;
    isToggle?: boolean;
};

export type OnSelectOptions = OnSelectFlags & OptionalItemCollections;

export type OnSelectFunction = (options: OnSelectOptions) => void;

export type OnHighlightItemsOptions = {
    some?: string;
} & OptionalItemCollections;

export type OnHighlightItemsFunction = (options: OnHighlightItemsOptions) => void;

export type OnHoverLocationFunction = (location: Point) => void;

export type OnClickLocationFunction = (location: Point) => void;
