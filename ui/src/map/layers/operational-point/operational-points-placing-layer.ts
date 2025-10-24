import { MapLayerName } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { PlacingOperationalPoint } from 'linking/linking-model';
import { OperationalPoint } from 'track-layout/track-layout-model';
import { renderOperationalPointFeature } from 'map/layers/operational-point/operational-points-layer-utils';
import { filterNotEmpty } from 'utils/array-utils';

const LAYER_NAME: MapLayerName = 'operational-points-placing-layer';

export const createOperationalPointsPlacingLayer = (
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    linkingState: PlacingOperationalPoint | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true);

    const dataPromise = Promise.resolve(linkingState?.operationalPoint);

    const createFeatures = (operationalPoint: OperationalPoint) =>
        operationalPoint === undefined || !linkingState?.location
            ? []
            : [
                  renderOperationalPointFeature(
                      operationalPoint,
                      'SELECTED',
                      linkingState?.location,
                  ),
              ].filter(filterNotEmpty);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return {
        name: LAYER_NAME,
        layer,
    };
};
