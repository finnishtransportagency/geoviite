import OlMap from 'ol/Map';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';
import { MapLayer } from 'map/layers/layer-model';

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
        layers?: MapLayer[],
        options?: MapToolActivateOptions,
    ) => DeactiveToolFun;
};
