import { MapLayerName, MapTile } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { getOperationalPointsByLocation } from 'track-layout/layout-operational-point-api';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import OlView from 'ol/View';
import { ChangeTimes } from 'common/common-slice';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';
import {
    findMatchingOperationalPoints,
    operationalPointFeatureModeBySelection,
    renderOperationalPointCircleFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState, LinkingType } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-layer';

export function createOperationalPointLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true, true);
    const resolution = olView.getResolution() || 0;

    const showOperationalPoints = resolution <= Limits.OPERATIONAL_POINTS_SMALL;

    const getOperationalPointsFromApi = async () => {
        return (
            await Promise.all(
                mapTiles.map((tile) =>
                    getOperationalPointsByLocation(
                        tile,
                        layoutContext,
                        changeTimes.operationalPoints,
                    ),
                ),
            )
        )
            .flat()
            .filter(filterUniqueById((point) => point.id));
    };
    const onLoadingChange = () => {};

    const isBeingMoved = (id: OperationalPointId) =>
        linkingState &&
        linkingState.type === LinkingType.PlacingOperationalPoint &&
        linkingState.operationalPoint.id === id &&
        !!linkingState.location;

    const createFeatures = (points: OperationalPoint[]) =>
        showOperationalPoints
            ? points
                  .filter((point) => !isBeingMoved(point.id))
                  .map((point) =>
                      renderOperationalPointCircleFeature(
                          point,
                          operationalPointFeatureModeBySelection(point.id, selection),
                      ),
                  )
                  .filter(filterNotEmpty)
            : [];
    loadLayerData(source, isLatest, onLoadingChange, getOperationalPointsFromApi(), createFeatures);

    return {
        name: LAYER_NAME,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => ({
            operationalPoints: findMatchingOperationalPoints(hitArea, source, options).map(
                (operationalPoint) => operationalPoint.id,
            ),
        }),
    };
}
