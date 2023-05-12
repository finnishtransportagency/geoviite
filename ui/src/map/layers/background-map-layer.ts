import { Tile as OlTileLayer } from 'ol/layer';
import WMTS, { optionsFromCapabilities } from 'ol/source/WMTS';
import WMTSCapabilities from 'ol/format/WMTSCapabilities';
import { MapLayer } from 'map/layers/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import TileSource from 'ol/source/Tile';

const parser = new WMTSCapabilities();

const MMLTileSourcePromise = fetch(
    '/location-map/wmts/maasto?service=WMTS&request=GetCapabilities&version=1.0.0',
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

function createLayer() {
    const layer = new OlTileLayer({ opacity: 0.5 });
    MMLTileSourcePromise.then((source) => source && layer.setSource(source));
    return layer;
}

export function createBackgroundMapLayer(existingOlLayer: OlTileLayer<TileSource>): MapLayer {
    const layer = existingOlLayer || createLayer();
    return {
        name: 'background-map-layer',
        layer: layer,
    };
}
