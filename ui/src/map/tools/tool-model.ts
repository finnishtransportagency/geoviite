import * as React from 'react';
import OlMap from 'ol/Map';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import type * as CssType from 'csstype';
import { AlignmentExtension, LinkingState } from 'linking/linking-model';
import { Polygon } from 'model/geometry';
import { LayoutContext } from 'common/common-model';

export type DeactivateToolFn = () => void;

export type MapToolId =
    | 'select'
    | 'highlight'
    | 'select-or-highlight'
    | 'measure'
    | 'point-location'
    | 'area-select'
    | 'route-finding'
    | 'operational-point-area'
    | 'alignment-extension';

export type MapToolActivateOptions = {
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onSetOperationalPointPolygon: (polygon: Polygon) => void;
    onSetAlignmentExtension: (extension: AlignmentExtension) => void;
    onStopExtendingAlignment: () => void;
    linkingState: LinkingState | undefined;
    layoutContext: LayoutContext;
};

// Returned by activate() so map-view can push updates without re-activating the tool.
export type MapToolHandle = {
    deactivate: DeactivateToolFn;
    onLayersChanged?: (layers: MapLayer[]) => void;
    onLinkingStateChanged?: (linkingState: LinkingState | undefined) => void;
};

export type MapToolProps = {
    isActive: boolean;
    setActiveTool: (id: MapToolId) => void;
    disabled: boolean;
    hidden: boolean;
};

export type MapTool = {
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => MapToolHandle;
    customCursor?: (linkingState: LinkingState | undefined) => CssType.Property.Cursor | undefined;
    component?: React.ComponentType<MapToolProps>;
    id: MapToolId;
};

export type MapToolWithButton = MapTool & {
    component: React.ComponentType<MapToolProps>;
};

export type MapToolMenuItem = MapToolWithButton & {
    component: React.ComponentType<MapToolProps>;
    disabled: boolean;
    hidden: boolean;
};

export const alwaysSelectableMapToolMenuItem = (tool: MapToolWithButton): MapToolMenuItem => ({
    ...tool,
    disabled: false,
    hidden: false,
});
