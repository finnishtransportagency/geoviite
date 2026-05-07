import { Tile } from 'ol/layer';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import TileSource from 'ol/source/Tile';
import MVT from 'ol/format/MVT';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import { Stroke, Style } from 'ol/style';
import { FeatureLike } from 'ol/Feature';
import { BackgroundMapLayerSourceType } from 'map/layers/background-map-layer';

type PropertyBoundaryStyle = {
    opacity: number;
    style: Style;
};

const layerExtent = [-548576, 6291456, 1548576, 8388608];
const PROPERTY_BORDER_COLOR = '#b40a14';
const PROPERTY_BOUNDARY_STYLES: { [key in BackgroundMapLayerSourceType]: PropertyBoundaryStyle } = {
    ortokuva: {
        opacity: 0.8,
        style: new Style({
            stroke: new Stroke({
                width: 3,
                color: PROPERTY_BORDER_COLOR,
            }),
        }),
    },
    taustakartta: {
        opacity: 0.5,
        style: new Style({
            stroke: new Stroke({
                width: 1,
                color: PROPERTY_BORDER_COLOR,
            }),
        }),
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
        ? boundaryStyle.style
        : undefined;

function createLayer(
    onLoadingData: (loading: boolean) => void,
    propertyBoundaryStyle: PropertyBoundaryStyle,
) {
    const source = new VectorTileSource({
        format: new MVT(),
        url: '/location-map/kiinteisto-avoin/tiles/wmts/1.0.0/kiinteistojaotus/default/v3/ETRS-TM35FIN/{z}/{y}/{x}.pbf',
        projection: LAYOUT_SRID,
        extent: layerExtent,
        maxZoom: 12,
    });
    let inFlightTileLoads = 0;

    source.on('tileloadstart', () => {
        inFlightTileLoads += 1;
        if (inFlightTileLoads === 1) {
            onLoadingData(true);
        }
    });
    source.on(['tileloadend', 'tileloaderror'], () => {
        if (inFlightTileLoads === 0) {
            return;
        }

        inFlightTileLoads -= 1;
        if (inFlightTileLoads === 0) {
            onLoadingData(false);
        }
    });

    return new VectorTileLayer({
        source: source,
        renderMode: 'vector',
        maxResolution: 4,
        opacity: propertyBoundaryStyle.opacity,
        style: (feature) => styleFunc(feature, propertyBoundaryStyle),
    });
}

export function createPropertyBoundaryLayer(
    existingOlLayer: Tile<TileSource>,
    onLoadingData: (loading: boolean) => void,
    orthoMapVisible: boolean,
): MapLayer {
    const layer =
        !existingOlLayer || existingOlLayer?.get('orthoMapVisible') !== orthoMapVisible
            ? createLayer(onLoadingData, getPropertyBoundaryStyle(orthoMapVisible))
            : existingOlLayer;

    layer.set('orthoMapVisible', orthoMapVisible);
    return {
        name: 'property-boundary-layer',
        layer: layer,
    };
}
