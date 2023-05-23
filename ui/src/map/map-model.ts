import { BoundingBox, CoordinateSystem, Point } from 'model/geometry';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { ValueOf } from 'utils/type-utils';
import { TrackNumberColorKey } from 'selection-panel/track-number-panel/color-selector/color-selector';

export type MapLayerName =
    | 'background-map-layer'
    | 'location-track-background-layer'
    | 'reference-line-background-layer'
    | 'track-number-diagram-layer'
    | 'location-track-alignment-layer'
    | 'reference-line-alignment-layer'
    | 'missing-profile-highlight-layer'
    | 'missing-linking-highlight-layer'
    | 'duplicate-tracks-highlight-layer'
    | 'location-track-badge-layer'
    | 'reference-line-badge-layer'
    | 'km-post-layer'
    | 'switch-layer'
    | 'geometry-alignment-layer'
    | 'geometry-km-post-layer'
    | 'geometry-switch-layer'
    | 'linking-layer'
    | 'linking-switch-layer'
    | 'manual-linking-switch-layer'
    | 'plan-area-layer'
    | 'debug-1m-points-layer'
    | 'debug-layer';

export const shownItemsByLayer: { [key in MapLayerName]?: keyof ShownItems } = {
    'switch-layer': 'switches',
    'km-post-layer': 'kmPosts',
    'location-track-alignment-layer': 'locationTracks',
    'reference-line-alignment-layer': 'referenceLines',
};

export type MapViewportSource = 'Map';

export type MapViewport = {
    coordinateSystem?: CoordinateSystem;
    center: Point;
    resolution: number;

    /**
     * Who has created this viewport. Can be undefined and this means "other" source.
     */
    source?: MapViewportSource;

    /**
     * Area is known only after map component is rendered, because it
     * depends on the size of the map component. Some default size can be
     * used to load data if it is necessary to do that before the map is rendered.
     */
    area?: BoundingBox;
};

export type OptionalShownItems = {
    referenceLines?: ReferenceLineId[];
    locationTracks?: LocationTrackId[];
    kmPosts?: LayoutKmPostId[];
    switches?: LayoutSwitchId[];
};
export type ShownItems = {
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    kmPosts: LayoutKmPostId[];
    switches: LayoutSwitchId[];
};

export type MapLayerMenuItem = {
    name: MapLayerMenuItemName;
    visible: boolean;
    subMenu?: MapLayerMenuItem[];
};

export type MapLayerMenuItemName =
    | 'map'
    | 'location-track'
    | 'reference-line'
    | 'track-number-diagram'
    | 'missing-vertical-geometry'
    | 'missing-linking'
    | 'duplicate-tracks'
    | 'km-post'
    | 'switch'
    | 'geometry-alignment'
    | 'geometry-switch'
    | 'plan-area'
    | 'geometry-km-post'
    | 'debug-1m'
    | 'debug';

export type TrackNumberDiagramLayerSetting = {
    [key: LayoutTrackNumberId]: {
        selected?: boolean;
        color?: TrackNumberColorKey | undefined;
    };
};

export type MapLayerSettings = {
    'track-number-diagram-layer': TrackNumberDiagramLayerSetting;
};

export type MapLayerSettingChange = {
    name: keyof MapLayerSettings;
    settings: ValueOf<MapLayerSettings>;
};

export type Map = {
    layerMenu: {
        layout: MapLayerMenuItem[];
        geometry: MapLayerMenuItem[];
        debug: MapLayerMenuItem[];
    };
    layerSettings: MapLayerSettings;
    visibleLayers: MapLayerName[];
    viewport: MapViewport;
    shownItems: ShownItems;
    hoveredLocation: Point | null;
    clickLocation: Point | null;
    verticalGeometryDiagramVisible: boolean;
};

export type MapTile = {
    id: string;
    area: BoundingBox;
    resolution: number;
};

export type AlignmentHighlight = {
    id: string;
    type: MapAlignmentType;
    ranges: { min: number; max: number }[];
};
