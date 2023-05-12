import { Polygon } from 'ol/geom';
import BaseLayer from 'ol/layer/Base';
import { OptionalShownItems } from 'map/map-model';
import { OptionalItemCollections } from 'selection/selection-model';

export type LayerItemSearchResult = OptionalItemCollections;

export type SearchItemsOptions = {
    limit?: number;
};

/**
 * Layer adapter works as a converter or adapter between domain layer model
 * and OpenLayers.
 */
export type OlLayerAdapter = {
    layer: BaseLayer;
    searchItems?: (hitArea: Polygon, options: SearchItemsOptions) => LayerItemSearchResult;
    searchShownItems?: (hitArea: Polygon, options: SearchItemsOptions) => OptionalShownItems;
};
