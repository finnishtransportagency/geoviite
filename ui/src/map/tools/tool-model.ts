import OlMap from 'ol/Map';
import { OlLayerAdapter } from 'map/layers/layer-model';
import {
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';

export type DeactiveToolFun = () => void;

export type MapToolActivateOptions = {
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
};

export type MapTool = {
    icon: string;
    activate: (
        map: OlMap,
        layerAdapter: OlLayerAdapter[],
        options: MapToolActivateOptions,
    ) => DeactiveToolFun;
};
