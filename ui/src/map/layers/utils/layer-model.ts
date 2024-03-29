import BaseLayer from 'ol/layer/Base';
import { MapLayerName } from 'map/map-model';
import { OptionalItemCollections } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';

export type LayerItemSearchResult = OptionalItemCollections;

export type SearchItemsOptions = {
    limit?: number;
};

export type MapLayer = {
    name: MapLayerName;
    layer: BaseLayer;
    searchItems?: (hitArea: Rectangle, options: SearchItemsOptions) => LayerItemSearchResult;
    onRemove?: () => void;
};
