import { MapLayer } from 'map/layers/utils/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import MVT from 'ol/format/MVT';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import { Stroke, Style } from 'ol/style';
import { FeatureLike } from 'ol/Feature';
import { BackgroundMapLayerSourceType } from 'map/layers/background-map-layer';

type PropertyBoundaryStyle = {
    opacity: number;
    strokeWidth: number;
};

const layerExtent = [-548576, 6291456, 1548576, 8388608];
const PROPERTY_BOUNDARY_STYLES: { [key in BackgroundMapLayerSourceType]: PropertyBoundaryStyle } = {
    ortokuva: {
        opacity: 0.8,
        strokeWidth: 3,
    },
    taustakartta: {
        opacity: 0.5,
        strokeWidth: 1,
    },
};

const getPropertyBoundaryStyle = (orthoMapVisible: boolean) =>
    orthoMapVisible
        ? PROPERTY_BOUNDARY_STYLES['ortokuva']
        : PROPERTY_BOUNDARY_STYLES['taustakartta'];

const PROPERTY_BOUNDARY_LOCATIONS = 'KiinteistorajanSijaintitiedot';

const styleFunc = (feature: FeatureLike, boundaryStyle: PropertyBoundaryStyle): Style | undefined =>
    // Only visibly draw actual property boundaries, not the other features in the same layer
    feature.getProperties()?.layer === PROPERTY_BOUNDARY_LOCATIONS
        ? new Style({
              stroke: new Stroke({
                  width: boundaryStyle.strokeWidth,
                  color: '#b40a14',
              }),
          })
        : undefined;

function createLayer(onLoadingData: (loading: boolean) => void, opacity: number) {
    const source = new VectorTileSource({
        format: new MVT(),
        url: '/location-map/kiinteisto-avoin/tiles/wmts/1.0.0/kiinteistojaotus/default/v3/ETRS-TM35FIN/{z}/{y}/{x}.pbf',
        projection: LAYOUT_SRID,
        extent: layerExtent,
        maxZoom: 12,
    });
    source.on('tileloadstart', () => onLoadingData(true));
    source.on(['tileloadend', 'tileloaderror'], () => onLoadingData(false));

    return new VectorTileLayer({
        source: source,
        renderMode: 'vector',
        maxResolution: 4,
        opacity,
    });
}

export function createPropertyBoundaryLayer(
    existingOlLayer: VectorTileLayer<VectorTileSource<never>, never>,
    onLoadingData: (loading: boolean) => void,
    orthoMapVisible: boolean,
): MapLayer {
    const propertyBoundaryStyle = getPropertyBoundaryStyle(orthoMapVisible);
    const layer = !existingOlLayer
        ? createLayer(onLoadingData, propertyBoundaryStyle.opacity)
        : existingOlLayer;

    layer.set('orthoMapVisible', orthoMapVisible);
    layer.setStyle((feature) => styleFunc(feature, propertyBoundaryStyle));
    return {
        name: 'property-boundary-layer',
        layer: layer,
    };
}
