import { Tile } from 'ol/layer';
import WMTS, { optionsFromCapabilities } from 'ol/source/WMTS';
import WMTSCapabilities from 'ol/format/WMTSCapabilities';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import TileSource from 'ol/source/Tile';

const parser = new WMTSCapabilities();

const MMTLCapabilitiesPromise = fetch(
    '/location-map/wmts/maasto?service=WMTS&request=GetCapabilities&version=1.0.0',
).then(async (response) => {
    const body = await response.text();
    const parse = parser.read(body);
    return (
        (parse['Contents'] !== undefined &&
            optionsFromCapabilities(parse, {
                layer: 'taustakartta',
                matrixSet: 'ETRS-TM35FIN',
                projection: LAYOUT_SRID,
                requestEncoding: 'REST',
            })) ||
        undefined
    );
});

const makeMMLTileSourcePromise = (sourceType: BackgroundMapLayerSourceType) =>
    MMTLCapabilitiesPromise.then((options) => {
        if (options) {
            options.urls = [
                `/location-map/wmts/maasto/1.0.0/${sourceType}/default/ETRS-TM35FIN/{TileMatrix}/{TileRow}/{TileCol}.png`,
            ];
            return new WMTS(options);
        } else {
            return undefined;
        }
    });

export type BackgroundMapLayerSourceType = 'taustakartta' | 'ortokuva';

function createLayer(sourceType: BackgroundMapLayerSourceType, opacity: number) {
    const layer = new Tile({ opacity });
    makeMMLTileSourcePromise(sourceType).then((source) => source && layer.setSource(source));
    return layer;
}

export function createBackgroundMapLayer(existingOlLayer: Tile<TileSource>): MapLayer {
    const layer = existingOlLayer || createLayer('taustakartta', 0.5);
    return {
        name: 'background-map-layer',
        layer: layer,
    };
}

export function createOrthographicMapLayer(existingOlLayer: Tile<TileSource>): MapLayer {
    const layer = existingOlLayer || createLayer('ortokuva', 1.0);
    return {
        name: 'orthographic-background-map-layer',
        layer: layer,
    };
}
