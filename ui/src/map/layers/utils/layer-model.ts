import { Polygon } from 'ol/geom';
import BaseLayer from 'ol/layer/Base';
import { MapLayerName } from 'map/map-model';
import { OptionalItemCollections } from 'selection/selection-model';

export type LayerItemSearchResult = OptionalItemCollections;

export type SearchItemsOptions = {
    limit?: number;
};

export type MapLayer = {
    name: MapLayerName;
    layer: BaseLayer;
    searchItems?: (hitArea: Polygon, options: SearchItemsOptions) => LayerItemSearchResult;
    onRemove?: () => void;
};
