import { Polygon } from 'ol/geom';
import BaseLayer from 'ol/layer/Base';
import OlView from 'ol/View';
import { MapLayer, MapTile, OptionalShownItems } from 'map/map-model';
import { OptionalItemCollections, Selection } from 'selection/selection-model';
import { LinkingState } from 'linking/linking-model';
import { PublishType } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';

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

export type CreateLayerAdapterFunc = (
    mapTiles: MapTile[],
    existingOlLayer: BaseLayer | undefined,
    mapLayer: MapLayer,
    selection: Selection,
    publishType: PublishType,
    linkingState: LinkingState | undefined,
    changeTimes: ChangeTimes,
    olView: OlView,
    onViewContentChanged?: (items: OptionalShownItems) => void,
) => OlLayerAdapter;

export type LayerAdapterInfo = {
    createAdapter: CreateLayerAdapterFunc;
    mapTileSizePx?: number;
};
