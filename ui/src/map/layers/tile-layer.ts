import { Tile as OlTileLayer } from 'ol/layer';
import WMTS, { optionsFromCapabilities } from 'ol/source/WMTS';
import WMTSCapabilities from 'ol/format/WMTSCapabilities';
import { MapLayer, MapTile } from 'map/map-model';
import { adapterInfoRegister } from './register';
import { LayerItemSearchResult, OlLayerAdapter } from 'map/layers/layer-model';
import proj4 from 'proj4';
import { register } from 'ol/proj/proj4';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';

proj4.defs(LAYOUT_SRID, '+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs');
register(proj4);

const parser = new WMTSCapabilities();

const ignoreCacheHeaders = {
    'pragma': 'no-cache',
    'cache-control': 'no-cache',
};

const MMLTileSourcePromise = fetch(
    '/location-map/wmts/maasto?service=WMTS&request=GetCapabilities&version=1.0.0',
    { headers: ignoreCacheHeaders },
).then(async (response) => {
    const body = await response.text();
    const options = optionsFromCapabilities(parser.read(body), {
        layer: 'taustakartta',
        matrixSet: 'ETRS-TM35FIN',
        projection: LAYOUT_SRID,
        requestEncoding: 'REST',
    });

    if (options) {
        options.urls = [
            '/location-map/wmts/maasto/1.0.0/taustakartta/default/ETRS-TM35FIN/{TileMatrix}/{TileRow}/{TileCol}.png',
        ];
        return new WMTS(options);
    }
});

function createNewLayer() {
    const layer = new OlTileLayer({
        opacity: 0.5,
    });
    MMLTileSourcePromise.then((source) => source && layer.setSource(source));
    return layer;
}

adapterInfoRegister.add('tile', {
    createAdapter: function (
        _mapTiles: MapTile[],
        _existingOlLayer,
        mapLayer: MapLayer,
    ): OlLayerAdapter {
        const layer = _existingOlLayer || createNewLayer();
        layer.setVisible(mapLayer.visible);

        return {
            layer: layer,
            searchItems: (): LayerItemSearchResult => {
                return {};
            },
        };
    },
});
