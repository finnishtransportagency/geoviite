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
import { LinkingState } from 'linking/linking-model';
import { Polygon } from 'model/geometry';

export type DeactivateToolFn = () => void;

export type MapToolActivateOptions = {
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onSetOperationalPointPolygon: (polygon: Polygon) => void;
    linkingState: LinkingState | undefined;
};

export type MapToolProps = {
    isActive: boolean;
    setActiveTool: (tool: MapTool) => void;
    disabled?: boolean;
    hidden?: boolean;
};

export type MapTool = {
    activate: (map: OlMap, layers: MapLayer[], options: MapToolActivateOptions) => DeactivateToolFn;
    customCursor?: CssType.Property.Cursor;
    component?: React.ComponentType<MapToolProps>;
    id: string;
};

export type MapToolWithButton = MapTool & {
    component: React.ComponentType<MapToolProps>;
    id: string;
    disabled?: boolean;
    hidden?: boolean;
};
