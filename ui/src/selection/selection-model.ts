import {
    LayoutKmPostId,
    LayoutSegmentId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import {
    ClusterPoint,
    LinkPoint,
    LinkPointId,
    SuggestedSwitch,
    SuggestedSwitchId,
} from 'linking/linking-model';
import { ensureAllKeys } from 'utils/type-utils';
import { Point } from 'model/geometry';
import { PublicationId, PublicationSearch } from 'publication/publication-model';
import { ScreenPoint } from 'map/map-view';

export type SelectionMode = 'alignment' | 'segment' | 'point' | 'switch' | 'trackNumber';

export type ItemCollections = {
    locationTracks: LocationTrackId[];
    kmPosts: LayoutKmPostId[];
    geometryKmPostIds: SelectedGeometryItem<GeometryKmPostId>[];
    switches: LayoutSwitchId[];
    geometrySwitchIds: SelectedGeometryItem<GeometrySwitchId>[];
    trackNumbers: LayoutTrackNumberId[];
    geometryAlignmentIds: SelectedGeometryItem<GeometryAlignmentId>[];
    layoutLinkPoints: LinkPoint[];
    geometryLinkPoints: LinkPoint[];
    clusterPoints: ClusterPoint[];
    suggestedSwitches: SuggestedSwitch[];
    geometryPlans: GeometryPlanId[];
};

export type UnselectableItemCollections = {
    segments: LayoutSegmentId[];
    locationTracks: LocationTrackId[];
    referenceLines: ReferenceLineId[];
    kmPosts: LayoutKmPostId[];
    geometryKmPosts: LayoutKmPostId[];
    switches: LayoutSwitchId[];
    geometrySwitches: GeometrySwitchId[];
    trackNumbers: LayoutTrackNumberId[];
    geometryAlignments: LocationTrackId[];
    geometrySegments: LayoutSegmentId[];
    layoutLinkPoints: LinkPointId[];
    geometryLinkPoints: LinkPointId[];
    suggestedSwitches: SuggestedSwitchId[];
    geometryPlans: GeometryPlanId[];
};

export type OptionalUnselectableItemCollections = Partial<UnselectableItemCollections>;

export type GeometryItemId = GeometryKmPostId | GeometrySwitchId | GeometryAlignmentId;
export type SelectedGeometryItem<T extends GeometryItemId> = {
    planId: GeometryPlanId;
    geometryId: T;
};

export type SelectableItemType = keyof ItemCollections;

export const allSelectableItemTypes: SelectableItemType[] = ensureAllKeys<SelectableItemType>()([
    'locationTracks',
    'kmPosts',
    'geometryKmPostIds',
    'switches',
    'geometrySwitchIds',
    'trackNumbers',
    'geometryAlignmentIds',
    'layoutLinkPoints',
    'geometryLinkPoints',
    'clusterPoints',
    'suggestedSwitches',
    'geometryPlans',
]);

export type OptionalItemCollections = Partial<ItemCollections>;

export type Selection = {
    selectionModes: SelectionMode[];
    selectedItems: ItemCollections;
    highlightedItems: ItemCollections;
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    publicationId: PublicationId | undefined;
    publicationSearch: PublicationSearch | undefined;
};

export type VisiblePlanLayout = {
    id: GeometryPlanId;
    switches: GeometrySwitchId[];
    kmPosts: GeometryKmPostId[];
    alignments: GeometryAlignmentId[];
};
export type OpenPlanLayout = {
    id: GeometryPlanId;
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

export type OnHoverLocationFunction = (location: Point, pixelPosition: ScreenPoint) => void;

export type OnClickLocationFunction = (location: Point) => void;
