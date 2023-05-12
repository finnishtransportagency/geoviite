import { BoundingBox, CoordinateSystem, Point } from 'model/geometry';
import {
    GeometryPlanLayout,
    LayoutKmPostId,
    LayoutSwitchId,
    LocationTrackId,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { DebugLayerData } from 'map/layers/debug-layer';

export type MapLayerBase = {
    name: string;
    visible: boolean;
};

export type TileMapLayer = MapLayerBase & {
    type: 'tile';
    url: string;
};

export type LayoutAlignmentLayer = MapLayerBase & {
    type: 'alignment';
    showReferenceLines: boolean;
    showMissingVerticalGeometry: boolean;
    showSegmentsFromSelectedPlan: boolean;
    showMissingLinking: boolean;
    showDuplicateTracks: boolean;
};

export type GeometryAlignmentLayer = MapLayerBase & {
    type: 'geometryAlignment';
    planIds: GeometryPlanId[];

    /**
     * This can be used to provide a plan manually, e.g. when plan is not yet in DB.
     */
    planLayout: GeometryPlanLayout | null;
};

export type LinkingLayer = MapLayerBase & {
    type: 'linking';
};

export type KmPostLayer = MapLayerBase & {
    type: 'kmPost';
};

export type GeometryKmPostLayer = MapLayerBase & {
    type: 'geometryKmPost';
};

export type SwitchLayer = MapLayerBase & {
    type: 'switch';
};

export type PlanAreaLayer = MapLayerBase & {
    type: 'planArea';
};

export type GeometrySwitchLayer = MapLayerBase & {
    type: 'geometrySwitch';
};

export type SwitchLinkingLayer = MapLayerBase & {
    type: 'switchLinking';
};

export type SwitchManualLinkingLayer = MapLayerBase & {
    type: 'switchManualLinking';
};

export type Debug1mPointsLayer = MapLayerBase & {
    type: 'debug1mPoints';
};

export type DebugLayer = MapLayerBase & {
    type: 'debug';
    data: DebugLayerData;
};

export type TrackNumberDiagramLayer = MapLayerBase & {
    type: 'trackNumberDiagram';
};

export type MapLayer =
    | LayoutAlignmentLayer
    | TileMapLayer
    | GeometryAlignmentLayer
    | KmPostLayer
    | GeometryKmPostLayer
    | SwitchLayer
    | PlanAreaLayer
    | GeometrySwitchLayer
    | LinkingLayer
    | SwitchLinkingLayer
    | SwitchManualLinkingLayer
    | Debug1mPointsLayer
    | DebugLayer
    | TrackNumberDiagramLayer;
export type MapLayerType = MapLayer['type'];

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

export type Map = {
    mapLayers: MapLayer[];
    viewport: MapViewport;
    settingsVisible: boolean;
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
