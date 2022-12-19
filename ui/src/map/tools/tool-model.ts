import OlMap from 'ol/Map';
import { OlLayerAdapter } from 'map/layers/layer-model';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';

export type DeactiveToolFun = () => void;

export type MapToolActivateOptions = {
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
};

export type MapTool = {
    activate: (
        map: OlMap,
        layerAdapter?: OlLayerAdapter[],
        options?: MapToolActivateOptions,
    ) => DeactiveToolFun;
};
