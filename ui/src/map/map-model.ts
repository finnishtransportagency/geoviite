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
import { TrackNumberColorKey } from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import {
    VerticalGeometryDiagramAlignmentId,
    VerticalGeometryDiagramState,
} from 'vertical-geometry/store';

export type MapLayerName =
    | 'background-map-layer'
    | 'orthographic-background-map-layer'
    | 'location-track-background-layer'
    | 'reference-line-background-layer'
    | 'track-number-diagram-layer'
    | 'track-number-addresses-layer'
    | 'location-track-alignment-layer'
    | 'reference-line-alignment-layer'
    | 'missing-profile-highlight-layer'
    | 'missing-linking-highlight-layer'
    | 'plan-section-highlight-layer'
    | 'duplicate-tracks-highlight-layer'
    | 'duplicate-split-section-highlight-layer'
    | 'location-track-selected-alignment-layer'
    | 'location-track-split-alignment-layer'
    | 'reference-line-selected-alignment-layer'
    | 'location-track-badge-layer'
    | 'location-track-split-badge-layer'
    | 'reference-line-badge-layer'
    | 'km-post-layer'
    | 'switch-layer'
    | 'geometry-alignment-layer'
    | 'geometry-km-post-layer'
    | 'geometry-switch-layer'
    | 'alignment-linking-layer'
    | 'switch-linking-layer'
    | 'location-track-duplicate-endpoint-address-layer'
    | 'location-track-split-location-layer'
    | 'plan-area-layer'
    | 'operating-points-layer'
    | 'debug-1m-points-layer'
    | 'debug-layer'
    | 'virtual-km-post-linking-layer'
    | 'virtual-hide-geometry-layer'
    | 'publication-candidate-layer';

export type MapViewportSource = 'Map';

export type MapViewport = {
    coordinateSystem?: CoordinateSystem;
    center: Point | undefined;
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
    qaId?: string;
    subMenu?: MapLayerMenuItem[];
};

export type MapLayerMenuItemName =
    | 'map'
    | 'orthographic-background-map'
    | 'location-track'
    | 'reference-line'
    | 'reference-line-hide-when-zoomed-close'
    | 'missing-vertical-geometry'
    | 'missing-linking'
    | 'duplicate-tracks'
    | 'track-number-diagram'
    | 'km-post'
    | 'switch'
    | 'geometry-alignment'
    | 'geometry-switch'
    | 'plan-area'
    | 'geometry-km-post'
    | 'operating-points'
    | 'debug-1m'
    | 'debug';

export type TrackNumberDiagramLayerSetting = {
    [key: LayoutTrackNumberId]: {
        selected?: boolean;
        color?: TrackNumberColorKey;
    };
};

export type MapLayerSettings = {
    'track-number-diagram-layer': TrackNumberDiagramLayerSetting;
};

export type MapLayerSettingChange = {
    name: keyof MapLayerSettings;
    settings: ValueOf<MapLayerSettings>;
};

export type MapLayerMenuGroups = {
    layout: MapLayerMenuItem[];
    geometry: MapLayerMenuItem[];
    debug: MapLayerMenuItem[];
};

export type Map = {
    layerMenu: MapLayerMenuGroups;
    layerSettings: MapLayerSettings;
    visibleLayers: MapLayerName[];
    viewport: MapViewport;
    shownItems: ShownItems;
    clickLocation?: Point;
    verticalGeometryDiagramState: VerticalGeometryDiagramState;
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

export type MapLayerMenuChange = {
    name: MapLayerMenuItemName;
    visible: boolean;
};

export type VerticalAlignmentVisibleExtentChange = {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    extent: [number, number];
};

export const HELSINKI_RAILWAY_STATION_COORDS: Point = { x: 385782.89, y: 6672277.83 };
