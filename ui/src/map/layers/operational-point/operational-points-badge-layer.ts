import { MapLayerName } from 'map/map-model';
import { Point as OlPoint } from 'ol/geom';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { OperationalPoint } from 'track-layout/track-layout-model';
import OlView from 'ol/View';
import { filterNotEmpty } from 'utils/array-utils';
import { LayoutContext } from 'common/common-model';
import { Selection } from 'selection/selection-model';
import { Rectangle } from 'model/geometry';
import {
    filterByResolution,
    findMatchingOperationalPoints,
    getOperationalPointsFromApi,
    isBeingMoved,
    operationalPointFeatureModeBySelection,
    renderOperationalPointTextFeature,
} from 'map/layers/operational-point/operational-points-layer-utils';
import { LinkingState } from 'linking/linking-model';

const LAYER_NAME: MapLayerName = 'operational-points-badge-layer';

export function createOperationalPointBadgeLayer(
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    olView: OlView,
    selection: Selection,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
): MapLayer {
    const { layer, source, isLatest } = createLayer(LAYER_NAME, existingOlLayer, true, true);
    const resolution = olView.getResolution() || 0;
    const onLoadingChange = () => {};

    const createFeatures = (points: OperationalPoint[]) =>
        points
            .filter(
                (point) =>
                    !isBeingMoved(linkingState, point.id) && filterByResolution(point, resolution),
            )
            .map((point) =>
                renderOperationalPointTextFeature(
                    point,
                    operationalPointFeatureModeBySelection(point.id, selection),
                ),
            )
            .filter(filterNotEmpty);
    loadLayerData(
        source,
        isLatest,
        onLoadingChange,
        getOperationalPointsFromApi(layoutContext),
        createFeatures,
    );

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
