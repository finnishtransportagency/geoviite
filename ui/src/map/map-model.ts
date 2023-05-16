import { BoundingBox, CoordinateSystem, Point } from 'model/geometry';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LocationTrackId,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';

export type MapLayerName =
    | 'background-map-layer'
    | 'location-track-background-layer'
    | 'reference-line-background-layer'
    | 'track-number-diagram-layer'
    | 'location-track-alignment-layer'
    | 'reference-line-alignment-layer'
    | 'missing-vertical-geometry-highlight-layer'
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

export type MapLayerSetting = {
    name: MapLayerSettingName;
    visible: boolean;
    subSettings?: MapLayerSetting[];
};

export type MapLayerSettingName =
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

export type Map = {
    settingsMenu: {
        layout: MapLayerSetting[];
        geometry: MapLayerSetting[];
        debug: MapLayerSetting[];
    };
    layers: MapLayerName[];
    viewport: MapViewport;
    shownItems: ShownItems;
    hoveredLocation: Point | null;
    clickLocation: Point | null;
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
