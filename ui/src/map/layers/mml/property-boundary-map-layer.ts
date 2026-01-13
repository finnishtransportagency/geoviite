import { Tile } from 'ol/layer';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import TileSource from 'ol/source/Tile';
import { Config } from 'ol/source/TileJSON';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import { MVT } from 'ol/format';
import { Stroke, Style } from 'ol/style';

const vectorMetaPromise = fetch(
    '/location-map/kiinteisto-avoin/v3/kiinteistojaotus/ETRS-TM35FIN/tilejson.json',
).then(async (response) => {
    const body = await response.text();
    const refined = body.replaceAll(
        'https://api.testivaylapilvi.fi/rasteripalvelu-mml',
        '/location-map',
    );
    return JSON.parse(refined) as Config;
});

const vectorSource = new VectorTileSource({
    format: new MVT(),
    url: '/location-map/kiinteisto-avoin/tiles/wmts/1.0.0/kiinteistojaotus/default/v3/ETRS-TM35FIN/{z}/{y}/{x}.pbf',
    projection: LAYOUT_SRID,
    extent: [-548576, 6291456, 1548576, 8388608],
    maxZoom: 12, // do not try to load more accurate tiles, they don't exist
});

function createVectorLayer() {
    const layer = new VectorTileLayer({
        renderMode: 'vector',
        maxResolution: 4,
        opacity: 0.6,
        style: function (feature) {
            console.log('tyylifunc', feature);
            return feature.getProperties()?.layer === 'KiinteistorajanSijaintitiedot'
                ? new Style({
                      stroke: new Stroke({
                          width: 1,
                          color: '#b40a14',
                      }),
                  })
                : [];
            // new Style({
            //       stroke: new Stroke({
            //           width: 10,
            //           color: '#b40a14',
            //       }),
            //   });
        },
    });
    vectorSource.then((source) => layer.setSource(source));
    return layer;
}

/*
    new TileLayer({
      source: new TileJSON({
        url: 'https://maps.gnosis.earth/ogcapi/collections/NaturalEarth:raster:HYP_HR_SR_OB_DR/map/tiles/WebMercatorQuad?f=tilejson',
        crossOrigin: 'anonymous',
      }),
    })
     */

const emptyLayer = new VectorTileLayer();
// const vectorLayer = createVectorLayer()

export function createOrthographicMapLayer(
    existingOlLayer: Tile<TileSource>,
    resolution: number,
): MapLayer {
    console.log('resolution', resolution);
    const layer = resolution < 20000 ? existingOlLayer || createVectorLayer() : emptyLayer;
    return {
        name: 'orthographic-background-map-layer',
        layer: layer,
    };
}
