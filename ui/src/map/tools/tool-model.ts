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

export type DeactivateToolFn = () => void;

export type MapToolActivateOptions = {
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
};

export type MapToolProps = {
    isActive: boolean;
    setActiveTool: (tool: MapTool) => void;
    disabled?: boolean;
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
};
