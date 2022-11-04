import { MapLayer, MapLayerType } from 'map/map-model';
import { LayerAdapterInfo } from 'map/layers/layer-model';

export type MapSelectItemsOptions = {
    selectMode: 'single' | 'multiple';
};

/**
 * @deprecated Adapter register should not be used anymore!
 * "switch-linking-layer.tsx" can be used as an example how to use layers without registry.
 *
 *
 * Adapter register works as a decoupler between map component and layer
 * adapters. Might be a bit overkill but it's very simple though.
 */
export const adapterInfoRegister = {
    adapterInfos: {},

    /**
     * @deprecated Adapter register should not be used anymore!
     * "switch-linking-layer.tsx" can be used as an example how to use layers without registry.
     */
    add: function (layerType: MapLayerType, adapterInfo: LayerAdapterInfo): void {
        this.adapterInfos[layerType] = adapterInfo;
    },

    /**
     * @deprecated Adapter register should not be used anymore!
     * "switch-linking-layer.tsx" can be used as an example how to use layers without registry.
     */
    get: function (mapLayer: MapLayer): LayerAdapterInfo | undefined {
        return this.adapterInfos[mapLayer.type];
    },
};
