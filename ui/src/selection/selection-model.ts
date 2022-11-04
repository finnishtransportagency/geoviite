import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutSegmentId,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapSegment,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { GeometryPlanHeader, GeometryPlanId, GeometryPlanLayoutId } from 'geometry/geometry-model';
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
import { PublicationListingItem } from 'publication/publication-model';

export type SelectionMode = 'alignment' | 'segment' | 'point' | 'switch' | 'trackNumber';

export type ItemCollections = {
    segments: MapSegment[];
    locationTracks: LocationTrackId[];
    referenceLines: ReferenceLineId[];
    kmPosts: LayoutKmPost[];
    geometryKmPosts: SelectedGeometryItem<LayoutKmPost>[];
    switches: LayoutSwitch[];
    geometrySwitches: SelectedGeometryItem<LayoutSwitch>[];
    trackNumbers: LayoutTrackNumberId[];
    geometryAlignments: SelectedGeometryItem<MapAlignment>[];
    geometrySegments: SelectedGeometryItem<MapSegment>[];
    layoutLinkPoints: LinkPoint[];
    geometryLinkPoints: LinkPoint[];
    clusterPoints: ClusterPoint[];
    suggestedSwitches: SuggestedSwitch[];
    locationTrackEndPoints: LocationTrackEndpoint[];
    geometryPlans: GeometryPlanHeader[];
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
    'segments',
    'locationTracks',
    'referenceLines',
    'kmPosts',
    'geometryKmPosts',
    'switches',
    'geometrySwitches',
    'trackNumbers',
    'geometryAlignments',
    'geometrySegments',
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
    publication: PublicationListingItem | undefined;
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
